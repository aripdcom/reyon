#!/usr/bin/env python3
"""Çok dilli metin denetimi. Depo kökünden koşar, CI her itmede çağırır.

Düzen:
  res/values/strings.xml            varsayılan (İngilizce) — tüm anahtarlar
  res/values-<dil>/strings.xml      çeviri; eksik anahtar bırakamaz

Denetimler:
  1. AppLocale.TAGS, locales_config.xml ve values-* klasörleri birbirini tutar:
     listelenen bir dilin çevirisi yoksa sistem seçicisi o dili önerir ve
     kullanıcı İngilizce alır
  2. Her çeviri varsayılandaki tüm anahtarları içerir
  3. Çeviride fazladan anahtar yok
  4. Aynı dilde yinelenen anahtar yok
  5. Biçim belirteçleri (%1$s, %d) çeviride birebir aynı; kaçırılmamış % yok
  6. Kotlin'deki her R.string.X bir kaynakta var
  7. Kullanılmayan kaynak anahtarı uyarı verir
  8. Kaçışsız çift tırnak yok: aapt2 tırnağı sessizce atar
  9. Argümansız okunan metinde %% yok: ekranda iki işaret görünür
 10. Argümanlı metin appString/appText ile okunur: rakamlar Latin kalsın
 11. Hiçbir metin %f ya da %,d taşımaz: appText Locale.ROOT ile biçimler
"""
import os
import re
import sys
from collections import Counter

RES = 'app/src/main/res'
LOCALES_CONFIG = 'app/src/main/res/xml/locales_config.xml'
APP_LOCALE = 'app/src/main/kotlin/com/aripd/reyon/platform/AppLocale.kt'
DEFAULT_LOCALE = 'en'  # res/values içeriğinin dili
KOTLIN_ROOTS = ['app/src/main/kotlin', 'app/src/test/kotlin']

STRING_RE = re.compile(r'<string name="([^"]+)"[^>]*>(.*?)</string>', re.S)
FMT_RE = re.compile(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]|%%')

errors = []
warnings = []


def err(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


def parse(path):
    """{anahtar: metin} ve yinelenen anahtarların listesi."""
    src = open(path, encoding='utf-8').read()
    pairs = STRING_RE.findall(src)
    dupes = [k for k, n in Counter(k for k, _ in pairs).items() if n > 1]
    return dict(pairs), dupes


def locale_of(dirname):
    """values → '', values-tr → 'tr', values-pt-rBR → 'pt-rBR'."""
    return dirname[len('values-'):] if dirname.startswith('values-') else ''


def fmts(text):
    """Argüman belirteçleri (%1$s, %d), sırasız karşılaştırma için sayımlı.

    Kaçırılmış yüzde (%%) sayıma girmez: İngilizce "85%%" yazarken Türkçe
    "yüzde 85" yazıyor, bu bilinçli bir üslup farkı. Çökme riski olan şey
    argüman sayısı ve türü.
    """
    return Counter(f for f in FMT_RE.findall(text) if f != '%%')


def bad_percent(text):
    """Kaçırılmamış yüzde: getString(id, ...) çağrısında çökme sebebi.

    Metni baştan tarar ve eşleşen belirteçleri tüketir; tek tek bakmak
    "%%" çiftinin ikinci işaretini yanlışlıkla kaçırılmış sayardı.
    """
    covered = set()
    for m in FMT_RE.finditer(text):
        covered.update(range(m.start(), m.end()))
    return [i for i, ch in enumerate(text) if ch == '%' and i not in covered]


def raw_format_calls():
    """Argümanlı ham çağrılar: stringResource(id, ...) ve x.getString(id, ...).

    Bunlar metni cihazın yerel ayarıyla biçimler; Arapça'da %d Hint-Arap
    rakamı basar. Latin rakam garantisi appString/appText'te (bkz. ZaText.kt).
    """
    pattern = re.compile(r'(?:\bstringResource|\.getString)\(\s*R\.string\.\w+\s*,')
    found = []
    for root in KOTLIN_ROOTS:
        for dirpath, _, names in os.walk(root):
            for n in names:
                if not n.endswith('.kt') or n == 'AppText.kt':
                    continue
                path = os.path.join(dirpath, n)
                src = open(path, encoding='utf-8').read()
                for line_no, line in enumerate(src.split('\n'), 1):
                    if pattern.search(line):
                        found.append(f'{path}:{line_no}')
    return found


def bare_quotes(text):
    """Kaçışsız çift tırnakların yeri.

    aapt2 kaçışsız " karakterini tırnak aç/kapa sayar ve atar; metin
    sessizce tırnaksız çıkar. Derleme kırılmadığı için tek yakalayan bu.
    Kesme işaretini aapt2 kendisi hata sayar, o yüzden burada aranmaz.
    """
    out, i = [], 0
    while i < len(text):
        if text[i] == '\\':
            i += 2
            continue
        if text[i] == '"':
            out.append(i)
        i += 1
    return out


def string_usage():
    """(argümanla okunan anahtarlar, argümansız okunanlar).

    getString(id) ve stringResource(id) String.format çalıştırmaz: içindeki
    %% ekrana iki işaret olarak gelir. Argüman verilen çağrı çalıştırır.
    Anahtarın hemen ardındaki işaret ayırt eder: ')' çağrı bitti, ','
    argüman geliyor. Başka bir şey gelirse (listeye konmuş kimlik gibi)
    sayıma girmez — yanlış hata vermemek için.
    """
    formatted, plain = set(), set()
    for root in KOTLIN_ROOTS:
        for dirpath, _, names in os.walk(root):
            for n in names:
                if not n.endswith('.kt'):
                    continue
                src = open(os.path.join(dirpath, n), encoding='utf-8').read()
                for k, sep in re.findall(r'R\.string\.([A-Za-z0-9_]+)\s*([,)])', src):
                    (formatted if sep == ',' else plain).add(k)
    return formatted, plain


def declared_tags():
    """AppLocale.TAGS içindeki etiketler, kaynak sırasıyla."""
    src = open(APP_LOCALE, encoding='utf-8').read()
    block = re.search(r'val TAGS: List<String> = listOf\((.*?)\)', src, re.S)
    if not block:
        return None
    return re.findall(r'"([^"]+)"', block.group(1))


def configured_tags():
    """locales_config.xml içindeki etiketler, dosya sırasıyla."""
    src = open(LOCALES_CONFIG, encoding='utf-8').read()
    return re.findall(r'<locale android:name="([^"]+)"', src)


def check_language_lists(present):
    """Üç liste birbirini tutmalı: Kotlin, locales_config, values-* klasörleri."""
    declared = declared_tags()
    configured = configured_tags()
    if declared is None:
        err(f'{APP_LOCALE}: TAGS listesi okunamadı')
        return
    if declared != configured:
        err(f'AppLocale.TAGS ile locales_config.xml ayrışmış:\n'
            f'    Kotlin : {declared}\n'
            f'    XML    : {configured}')
    for tag in declared:
        if tag == DEFAULT_LOCALE:
            continue
        if tag not in present:
            err(f'"{tag}" dil listesinde ama values-{tag}/strings.xml yok — '
                f'sistem seçicisi bu dili önerir, kullanıcı İngilizce görür')
    for tag in present:
        if tag not in declared:
            warn(f'values-{tag} var ama dil listesinde yok; seçicide çıkmaz')


def main():
    if not os.path.isdir(RES):
        sys.exit('app/src/main/res bulunamadı — depo kökünden koşturun')

    files = {}   # (locale, dosya adı) -> {anahtar: metin}
    for d in sorted(os.listdir(RES)):
        if not d.startswith('values'):
            continue
        loc = locale_of(d)
        name = 'strings.xml'
        p = os.path.join(RES, d, name)
        if not os.path.exists(p):
            continue
        keys, dupes = parse(p)
        for k in dupes:
            err(f'{d}/{name}: yinelenen anahtar "{k}"')
        files[(loc, name)] = keys

    base = files.get(('', 'strings.xml'))
    if base is None:
        sys.exit(f'{RES}/values/strings.xml yok — varsayılan dil eksik')
    # Çeviriler
    translations = sorted(loc for (loc, n) in files if n == 'strings.xml' and loc)
    check_language_lists(set(translations))
    print(f'varsayılan ({DEFAULT_LOCALE}): {len(base)} metin')
    for loc in translations:
        keys = files[(loc, 'strings.xml')]
        missing = sorted(set(base) - set(keys))
        extra = sorted(set(keys) - set(base))
        bad_fmt = [k for k in keys if k in base and fmts(base[k]) != fmts(keys[k])]
        loose = [k for k, v in keys.items() if fmts(v) and bad_percent(v)]
        empty = [k for k, v in keys.items() if not v.strip()]
        status = 'TAM' if not (missing or extra or bad_fmt or loose or empty) else 'EKSİK'
        print(f'  values-{loc:<6} {len(keys):>4}/{len(base)} {status}')
        for k in extra:
            err(f'values-{loc}: varsayılanda olmayan anahtar "{k}"')
        for k in bad_fmt:
            err(f'values-{loc}: "{k}" biçim belirteçleri uyuşmuyor '
                f'({sorted(fmts(base[k]).elements())} ≠ {sorted(fmts(keys[k]).elements())})')
        for k in loose:
            err(f'values-{loc}: "{k}" kaçırılmamış % içeriyor (%% olmalı)')
        for k in empty:
            err(f'values-{loc}: "{k}" boş')
        if missing:
            head = ', '.join(missing[:6])
            more = f' … (+{len(missing) - 6})' if len(missing) > 6 else ''
            warn(f'values-{loc}: {len(missing)} metin çevrilmemiş: {head}{more}')

    # 8-9. Her dosya tek tek: kaçışsız tırnak ve hiç biçimlenmeyen %%
    #
    # İkisi de derlemeyi kırmaz, yalnızca kullanıcı görür: tırnak kaybolur,
    # %% olduğu gibi kalır. Hakkında'daki lisans satırında sürümler boyunca
    # öyle durmuştu.
    formatted_keys, plain_keys = string_usage()
    for (loc, name), keys in sorted(files.items()):
        where = f'values{"-" + loc if loc else ""}/{name}'
        for k, v in sorted(keys.items()):
            if bare_quotes(v):
                err(f'{where}: "{k}" kaçışsız çift tırnak içeriyor '
                    f'(\\" olmalı, yoksa aapt2 tırnağı atar)')
            if '%%' in v and k in plain_keys and k not in formatted_keys:
                err(f'{where}: "{k}" %% içeriyor ama argümansız okunuyor '
                    f'(ekranda %% görünür; tek % ve formatted="false" gerekir)')

    # 10-11. Rakamlar her dilde Latin kalsın
    #
    # Arapça'da getString(id, sayı) %d'yi Hint-Arap rakamına çevirir; stok
    # sayaçları "٩/١٢" çıkıyordu. appString/appText Locale.ROOT ile biçimler.
    # Locale.ROOT ondalık ayırıcıyı da Latinleştirir, bu yüzden %f ve %,d
    # taşıyan metin olmamalı — bugün yok, kural onu korur.
    for where in raw_format_calls():
        err(f'{where}: argümanlı metin ham okunuyor; appString/appText kullan '
            f'(Arapça\'da rakamlar Hint-Arap çıkar)')
    for (loc, name), keys in sorted(files.items()):
        for k, v in sorted(keys.items()):
            if re.search(r'%[0-9]*\$?[,]?f|%,d', v):
                err(f'values{"-" + loc if loc else ""}/{name}: "{k}" %f ya da %,d '
                    f'taşıyor; appText Locale.ROOT ile biçimlediği için ayırıcı yanlış çıkar')

    # 6-7. Kotlin referansları
    known = set(base)
    used = set()
    for root in KOTLIN_ROOTS:
        for dirpath, _, names in os.walk(root):
            for n in names:
                if not n.endswith('.kt'):
                    continue
                p = os.path.join(dirpath, n)
                src = open(p, encoding='utf-8').read()
                used |= set(re.findall(r'R\.string\.([A-Za-z0-9_]+)', src))
                # süslü parantez dengesi (kaba ama gerçek hataları yakalar)
                stripped = re.sub(r'"(?:\\.|[^"\\])*"', '""', src)
                stripped = re.sub(r'//[^\n]*', '', stripped)
                if stripped.count('{') != stripped.count('}'):
                    err(f'{p}: süslü parantez dengesiz '
                        f'({stripped.count("{")} açık, {stripped.count("}")} kapalı)')

    for k in sorted(used - known):
        err(f'R.string.{k} hiçbir kaynakta yok')
    unused = sorted(known - used)
    if unused:
        warn(f'{len(unused)} kullanılmayan anahtar: {", ".join(unused[:8])}'
             + (f' … (+{len(unused) - 8})' if len(unused) > 8 else ''))

    print()
    for w in warnings:
        print(f'UYARI  {w}')
    for e in errors:
        print(f'HATA   {e}')
    print()
    if errors:
        print(f'✗ {len(errors)} hata, {len(warnings)} uyarı')
        return 1
    print(f'✓ hata yok, {len(warnings)} uyarı')
    return 0


if __name__ == '__main__':
    sys.exit(main())
