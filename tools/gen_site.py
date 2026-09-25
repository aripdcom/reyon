#!/usr/bin/env python3
"""reyon.aripd.com'u uygulamanın 14 dilinde üretir.

Site, uygulamanın ve Play kaydının söylediğini söyler; metnin çoğu oradan gelir:

  - store/play/<dil>/{title,short,full}.txt: başlık, dört modun anlatımı,
    sonuç, gizlilik ve lisans cümleleri (Play'de yayımlanan metnin aynısı)
  - app/src/main/res/values*/strings.xml: mod adları, formatlar, düzeyler ve
    telefon maketindeki Görevler ekranı (uygulamada görünen metnin aynısı)
  - store/play/release-notes/<en yeni sürüm>.txt: "Yenilikler" bölümü

Yalnızca siteye özgü cümleler (giriş, bölüm başlıkları, indirme) aşağıdaki
COPY sözlüğünde 14 dilde yazılı. Gizlilik metni tools/gen_privacy.py'de durur,
sayfası burada üretilir.

Çıktılar (site/):
  index.html          İngilizce, x-default. İlk ziyarette tarayıcının dili
                      destekleniyorsa o dilin sayfasına geçer; seçicide bir
                      dile tıklayan bir daha yönlendirilmez
  <dil>/index.html    Diğer 13 dil; ar sağdan sola
  en/index.html       Köke yönlendirir (adres elle yazılırsa boşa düşmesin)
  gizlilik.html       Gizlilik politikası, 14 dil tek adreste (Play'deki URL)
  404.html, sitemap.xml, robots.txt
  assets/icon.svg     Uygulama simgesinin işareti (sekme simgesi)
  assets/og.png, assets/icon-512.png   store/graphics'ten kopya

Elle yazılanlar: assets/site.css ve assets/site.js. Yazı tipleri
tools/gen_site_fonts.py ile üretilir (fontTools ister, CI'da koşmaz).
tools/check_site.py üretilen her dosyanın depodakiyle aynı olduğunu denetler.

Kullanım: python3 tools/gen_site.py
"""
import html
import os
import re
import sys
import unicodedata
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_privacy  # noqa: E402

ROOT = gen_privacy.ROOT
SITE = os.path.join(ROOT, "site")
STORE = os.path.join(ROOT, "store")
RES = os.path.join(ROOT, "app", "src", "main", "res")
LINKS_KT = os.path.join(ROOT, "app", "src", "main", "kotlin", "com", "aripd", "reyon",
                        "platform", "Links.kt")

RTL = gen_privacy.RTL
MAIL = gen_privacy.MAIL
RELEASES = "https://github.com/aripdcom/reyon/releases"
APK = RELEASES + "/latest/download/reyon.apk"

# Open Graph yerel ayarı; Facebook Arapça için "ar_AR" bekler.
OG_LOCALE = {
    "en": "en_US", "tr": "tr_TR", "de": "de_DE", "fr": "fr_FR", "nl": "nl_NL",
    "es": "es_ES", "pt": "pt_BR", "it": "it_IT", "da": "da_DK", "sv": "sv_SE",
    "nb": "nb_NO", "fi": "fi_FI", "ru": "ru_RU", "ar": "ar_AR",
}
# Sürüm notlarında Play'in yerel ayar kodu (tools/check_store.py ile aynı eşleme).
NOTE_LOCALE = {
    "en": "en-US", "tr": "tr-TR", "de": "de-DE", "fr": "fr-FR", "nl": "nl-NL",
    "es": "es-ES", "pt": "pt-BR", "it": "it-IT", "da": "da-DK", "sv": "sv-SE",
    "nb": "no-NO", "fi": "fi-FI", "ru": "ru-RU", "ar": "ar",
}
# Ondalık ayırıcı; rakamlar uygulamadaki gibi her dilde Latin (AppLocale.decimal).
DECIMAL_POINT = {"en", "ar"}

# Siteye özgü metin. Ton, uygulamanın ve Play metninin tonu: okura "sen" diye
# seslenir (pt'de "você"). {v} sürüm numarasıdır.
COPY = {
    "en": dict(
        meta="Reyon is a free Android app for FMCG shelf work: planograms, shelf audits, shelf yield "
             "and weekly ordering. 14 languages, no ads, no permissions, fully offline.",
        lead="Build the planogram, audit the shelf, raise the shelf yield and manage a week of stock. "
             "Every result is measured against an expert’s.",
        download="Download APK",
        download_short="Download",
        releases="All releases and SHA-256 checksums",
        requires="Android 8.0 or newer · free · no account",
        install="Android may ask you to allow installs from your browser; that is the usual step for an "
                "APK from outside Google Play.",
        modes_h="Four modes",
        modes_p="Each mode is one part of the job: placing, checking, selling, ordering.",
        report_h="A report, not a score",
        bands="Levels, as a share of the expert’s result",
        device_h="Stays on your device",
        langs_h="14 languages",
        langs_p="The app follows your phone’s language, and you can choose another one in the settings. "
                "This site speaks the same 14.",
        new_h="What’s new in {v}",
        skip="Skip to content",
        mockup="Reyon’s Tasks screen: the cases of the day in four modes, then practice by store format.",
        home="Reyon home page",
        notfound="This page does not exist.",
    ),
    "tr": dict(
        meta="Reyon, FMCG raf işi için ücretsiz bir Android uygulaması: planogram, raf denetimi, raf "
             "verimi ve haftalık sipariş. 14 dil, reklamsız, izinsiz, tamamen çevrimdışı.",
        lead="Planogramı kur, rafı denetle, raf verimini artır, bir haftalık stoğu yönet. Her sonuç bir "
             "uzmanınkiyle ölçülür.",
        download="APK indir",
        download_short="İndir",
        releases="Bütün sürümler ve SHA-256 özetleri",
        requires="Android 8.0 ve üstü · ücretsiz · hesap gerekmez",
        install="Android, tarayıcından uygulama yüklemeye izin vermeni isteyebilir; Google Play dışından "
                "gelen bir APK için olağan adım budur.",
        modes_h="Dört mod",
        modes_p="Her mod işin bir parçası: yerleştirmek, denetlemek, satmak, sipariş vermek.",
        report_h="Puan değil, rapor",
        bands="Düzeyler, uzmanın sonucuna oranla",
        device_h="Cihazında kalır",
        langs_h="14 dil",
        langs_p="Uygulama telefonunun dilini izler; ayarlardan başka bir dil de seçebilirsin. Bu site de "
                "aynı 14 dilde.",
        new_h="{v} sürümünde yenilikler",
        skip="İçeriğe geç",
        mockup="Reyon'un Görevler ekranı: dört modda günün vakaları, ardından mağaza formatına göre "
               "alıştırma.",
        home="Reyon ana sayfası",
        notfound="Bu sayfa yok.",
    ),
    "de": dict(
        meta="Reyon ist eine kostenlose Android-App für die Regalarbeit im FMCG-Handel: Planogramme, "
             "Regalprüfung, Regalleistung und Wochenbestellung. 14 Sprachen, keine Werbung, keine "
             "Berechtigungen, komplett offline.",
        lead="Planogramm aufbauen, Regal prüfen, Regalleistung steigern, eine Woche Bestand steuern. "
             "Jedes Ergebnis wird an einem Profi gemessen.",
        download="APK herunterladen",
        download_short="Download",
        releases="Alle Versionen und SHA-256-Prüfsummen",
        requires="Android 8.0 oder neuer · kostenlos · ohne Konto",
        install="Android fragt eventuell, ob dein Browser Apps installieren darf; das ist der übliche "
                "Schritt für eine APK außerhalb von Google Play.",
        modes_h="Vier Modi",
        modes_p="Jeder Modus ist ein Teil der Arbeit: einräumen, prüfen, verkaufen, bestellen.",
        report_h="Ein Bericht statt Punkte",
        bands="Stufen, im Verhältnis zum Ergebnis des Profis",
        device_h="Bleibt auf deinem Gerät",
        langs_h="14 Sprachen",
        langs_p="Die App folgt der Sprache deines Telefons; in den Einstellungen kannst du auch eine "
                "andere wählen. Diese Website gibt es in denselben 14 Sprachen.",
        new_h="Neu in {v}",
        skip="Zum Inhalt springen",
        mockup="Der Aufgaben-Bildschirm von Reyon: die Fälle des Tages in vier Modi, darunter die Übung "
               "nach Ladenformat.",
        home="Reyon-Startseite",
        notfound="Diese Seite gibt es nicht.",
    ),
    "fr": dict(
        meta="Reyon est une appli Android gratuite pour le travail en rayon PGC : planogrammes, contrôle "
             "de rayon, rendement du rayon et commandes de la semaine. 14 langues, sans pub, sans "
             "autorisation, entièrement hors ligne.",
        lead="Monte le planogramme, contrôle le rayon, augmente son rendement et gère une semaine de "
             "stock. Chaque résultat est mesuré à celui d’un expert.",
        download="Télécharger l’APK",
        download_short="Télécharger",
        releases="Toutes les versions et sommes SHA-256",
        requires="Android 8.0 ou plus récent · gratuit · sans compte",
        install="Android peut te demander d’autoriser ton navigateur à installer des applis ; c’est "
                "l’étape habituelle pour un APK hors Google Play.",
        modes_h="Quatre modes",
        modes_p="Chaque mode est une partie du métier : ranger, contrôler, vendre, commander.",
        report_h="Un rapport, pas un score",
        bands="Niveaux, en part du résultat de l’expert",
        device_h="Tout reste sur ton appareil",
        langs_h="14 langues",
        langs_p="L’appli suit la langue de ton téléphone, et tu peux en choisir une autre dans les "
                "paramètres. Ce site existe dans les mêmes 14 langues.",
        new_h="Nouveautés de la version {v}",
        skip="Aller au contenu",
        mockup="L’écran Tâches de Reyon : les cas du jour dans les quatre modes, puis l’entraînement par "
               "format de magasin.",
        home="Accueil de Reyon",
        notfound="Cette page n’existe pas.",
    ),
    "nl": dict(
        meta="Reyon is een gratis Android-app voor schapwerk in FMCG: planogrammen, schapcontrole, "
             "schaprendement en weekbestellingen. 14 talen, geen advertenties, geen rechten, volledig "
             "offline.",
        lead="Bouw het planogram, controleer het schap, verhoog het schaprendement en beheer een week "
             "voorraad. Elk resultaat wordt gemeten aan dat van een expert.",
        download="APK downloaden",
        download_short="Downloaden",
        releases="Alle versies en SHA-256-controlesommen",
        requires="Android 8.0 of nieuwer · gratis · geen account nodig",
        install="Android kan vragen of je browser apps mag installeren; dat is de gewone stap voor een "
                "APK van buiten Google Play.",
        modes_h="Vier modi",
        modes_p="Elke modus is een deel van het vak: vullen, controleren, verkopen, bestellen.",
        report_h="Een rapport, geen score",
        bands="Niveaus, als deel van het resultaat van de expert",
        device_h="Blijft op je toestel",
        langs_h="14 talen",
        langs_p="De app volgt de taal van je telefoon, en in de instellingen kun je een andere kiezen. "
                "Deze site is er in dezelfde 14 talen.",
        new_h="Nieuw in {v}",
        skip="Naar de inhoud",
        mockup="Het scherm Taken van Reyon: de casussen van de dag in vier modi, daaronder oefenen per "
               "winkelformaat.",
        home="Startpagina van Reyon",
        notfound="Deze pagina bestaat niet.",
    ),
    "es": dict(
        meta="Reyon es una app gratuita para Android sobre el trabajo en estantes de gran consumo: "
             "planogramas, auditoría, rendimiento del estante y pedidos semanales. 14 idiomas, sin "
             "anuncios, sin permisos y sin conexión.",
        lead="Arma el planograma, audita el estante, sube su rendimiento y gestiona una semana de stock. "
             "Cada resultado se mide con el de un experto.",
        download="Descargar APK",
        download_short="Descargar",
        releases="Todas las versiones y sumas SHA-256",
        requires="Android 8.0 o posterior · gratis · sin cuenta",
        install="Android puede pedirte que permitas instalar apps desde el navegador; es el paso "
                "habitual para un APK de fuera de Google Play.",
        modes_h="Cuatro modos",
        modes_p="Cada modo es una parte del oficio: colocar, auditar, vender, pedir.",
        report_h="Un informe, no una puntuación",
        bands="Niveles, según el porcentaje del resultado del experto",
        device_h="Se queda en tu dispositivo",
        langs_h="14 idiomas",
        langs_p="La app sigue el idioma de tu teléfono y en los ajustes puedes elegir otro. Este sitio "
                "está en los mismos 14 idiomas.",
        new_h="Novedades de la versión {v}",
        skip="Ir al contenido",
        mockup="La pantalla Tareas de Reyon: los casos del día en los cuatro modos y, debajo, la "
               "práctica por formato de tienda.",
        home="Página principal de Reyon",
        notfound="Esta página no existe.",
    ),
    "pt": dict(
        meta="Reyon é um app gratuito para Android sobre o trabalho na gôndola de bens de consumo: "
             "planogramas, auditoria, rendimento da prateleira e pedidos da semana. 14 idiomas, sem "
             "anúncios, sem permissões, totalmente offline.",
        lead="Monte o planograma, audite a prateleira, aumente o rendimento e gerencie uma semana de "
             "estoque. Cada resultado é medido pelo de um especialista.",
        download="Baixar APK",
        download_short="Baixar",
        releases="Todas as versões e somas SHA-256",
        requires="Android 8.0 ou mais recente · grátis · sem conta",
        install="O Android pode pedir que você permita instalar apps pelo navegador; é o passo normal "
                "para um APK de fora do Google Play.",
        modes_h="Quatro modos",
        modes_p="Cada modo é uma parte do trabalho: arrumar, auditar, vender, pedir.",
        report_h="Um relatório, não uma pontuação",
        bands="Níveis, pela porcentagem do resultado do especialista",
        device_h="Fica no seu aparelho",
        langs_h="14 idiomas",
        langs_p="O app segue o idioma do seu telefone, e nas configurações você pode escolher outro. "
                "Este site está nos mesmos 14 idiomas.",
        new_h="Novidades da versão {v}",
        skip="Ir para o conteúdo",
        mockup="A tela Tarefas do Reyon: os casos do dia nos quatro modos e, abaixo, a prática por "
               "formato de loja.",
        home="Página inicial do Reyon",
        notfound="Esta página não existe.",
    ),
    "it": dict(
        meta="Reyon è un’app Android gratuita per il lavoro sullo scaffale nel largo consumo: "
             "planogrammi, controllo dello scaffale, resa e ordini settimanali. 14 lingue, niente "
             "pubblicità, nessun permesso, completamente offline.",
        lead="Costruisci il planogramma, controlla lo scaffale, aumenta la resa e gestisci una settimana "
             "di scorte. Ogni risultato è misurato su quello di un esperto.",
        download="Scarica l’APK",
        download_short="Scarica",
        releases="Tutte le versioni e i checksum SHA-256",
        requires="Android 8.0 o successivo · gratis · senza account",
        install="Android potrebbe chiederti di consentire al browser di installare app; è il passaggio "
                "normale per un APK fuori da Google Play.",
        modes_h="Quattro modalità",
        modes_p="Ogni modalità è una parte del mestiere: sistemare, controllare, vendere, ordinare.",
        report_h="Un resoconto, non un punteggio",
        bands="Livelli, in percentuale sul risultato dell’esperto",
        device_h="Resta sul tuo dispositivo",
        langs_h="14 lingue",
        langs_p="L’app segue la lingua del telefono e nelle impostazioni puoi sceglierne un’altra. "
                "Questo sito è nelle stesse 14 lingue.",
        new_h="Novità della versione {v}",
        skip="Vai al contenuto",
        mockup="La schermata Compiti di Reyon: i casi del giorno nelle quattro modalità e, sotto, "
               "l’esercizio per formato del negozio.",
        home="Pagina iniziale di Reyon",
        notfound="Questa pagina non esiste.",
    ),
    "da": dict(
        meta="Reyon er en gratis Android-app til hyldearbejde i dagligvarehandlen: planogrammer, "
             "hyldekontrol, hyldeafkast og ugens bestilling. 14 sprog, ingen reklamer, ingen "
             "tilladelser, helt offline.",
        lead="Byg planogrammet, kontrollér hylden, øg hyldeafkastet og styr en uges lager. Hvert "
             "resultat måles mod en eksperts.",
        download="Hent APK",
        download_short="Hent",
        releases="Alle udgivelser og SHA-256-kontrolsummer",
        requires="Android 8.0 eller nyere · gratis · ingen konto",
        install="Android kan bede dig om at tillade installationer fra din browser; det er det normale "
                "trin for en APK uden for Google Play.",
        modes_h="Fire tilstande",
        modes_p="Hver tilstand er en del af jobbet: stille op, kontrollere, sælge, bestille.",
        report_h="En rapport, ikke point",
        bands="Niveauer i forhold til ekspertens resultat",
        device_h="Bliver på din enhed",
        langs_h="14 sprog",
        langs_p="Appen følger telefonens sprog, og i indstillingerne kan du vælge et andet. Denne side "
                "findes på de samme 14 sprog.",
        new_h="Nyt i {v}",
        skip="Gå til indholdet",
        mockup="Reyons skærm Opgaver: dagens cases i fire tilstande og derunder øvelse efter "
               "butiksformat.",
        home="Reyons forside",
        notfound="Siden findes ikke.",
    ),
    "sv": dict(
        meta="Reyon är en gratis Android-app för hyllarbete i dagligvaruhandeln: planogram, "
             "hyllkontroll, hyllavkastning och veckans beställning. 14 språk, inga annonser, inga "
             "behörigheter, helt offline.",
        lead="Bygg planogrammet, kontrollera hyllan, höj hyllavkastningen och styr en veckas lager. "
             "Varje resultat mäts mot en experts.",
        download="Ladda ned APK",
        download_short="Ladda ned",
        releases="Alla versioner och SHA-256-kontrollsummor",
        requires="Android 8.0 eller senare · gratis · inget konto",
        install="Android kan be dig tillåta installationer från webbläsaren; det är det vanliga steget "
                "för en APK utanför Google Play.",
        modes_h="Fyra lägen",
        modes_p="Varje läge är en del av jobbet: ställa upp, kontrollera, sälja, beställa.",
        report_h="En rapport, inte poäng",
        bands="Nivåer i förhållande till expertens resultat",
        device_h="Stannar på din enhet",
        langs_h="14 språk",
        langs_p="Appen följer telefonens språk, och i inställningarna kan du välja ett annat. Den här "
                "webbplatsen finns på samma 14 språk.",
        new_h="Nytt i {v}",
        skip="Hoppa till innehållet",
        mockup="Reyons skärm Uppgifter: dagens fall i fyra lägen och under dem övning efter "
               "butiksformat.",
        home="Reyons startsida",
        notfound="Sidan finns inte.",
    ),
    "nb": dict(
        meta="Reyon er en gratis Android-app for hyllearbeid i dagligvarehandelen: planogram, "
             "hyllekontroll, hylleavkastning og ukens bestilling. 14 språk, ingen annonser, ingen "
             "tillatelser, helt uten nett.",
        lead="Bygg planogrammet, kontroller hylla, øk hylleavkastningen og styr en ukes lager. Hvert "
             "resultat måles mot en eksperts.",
        download="Last ned APK",
        download_short="Last ned",
        releases="Alle versjoner og SHA-256-sjekksummer",
        requires="Android 8.0 eller nyere · gratis · ingen konto",
        install="Android kan be deg tillate installasjoner fra nettleseren; det er det vanlige steget "
                "for en APK utenfor Google Play.",
        modes_h="Fire moduser",
        modes_p="Hver modus er en del av jobben: stille opp, kontrollere, selge, bestille.",
        report_h="En rapport, ikke poeng",
        bands="Nivåer i forhold til ekspertens resultat",
        device_h="Blir på enheten din",
        langs_h="14 språk",
        langs_p="Appen følger språket på telefonen, og i innstillingene kan du velge et annet. Dette "
                "nettstedet finnes på de samme 14 språkene.",
        new_h="Nytt i {v}",
        skip="Gå til innholdet",
        mockup="Reyons skjerm Oppgaver: dagens case i fire moduser og under dem øving etter "
               "butikkformat.",
        home="Reyons forside",
        notfound="Siden finnes ikke.",
    ),
    "fi": dict(
        meta="Reyon on ilmainen Android-sovellus päivittäistavarakaupan hyllytyöhön: planogrammit, "
             "hyllyn tarkastus, hyllytuotto ja viikon tilaukset. 14 kieltä, ei mainoksia, ei lupia, "
             "täysin ilman verkkoa.",
        lead="Rakenna planogrammi, tarkasta hylly, nosta hyllytuottoa ja hallitse viikon varastoa. "
             "Jokaista tulosta verrataan asiantuntijan tulokseen.",
        download="Lataa APK",
        download_short="Lataa",
        releases="Kaikki versiot ja SHA-256-tarkistussummat",
        requires="Android 8.0 tai uudempi · ilmainen · ei tiliä",
        install="Android voi pyytää sallimaan asennukset selaimesta; se on tavallinen vaihe Google "
                "Playn ulkopuolisille APK-tiedostoille.",
        modes_h="Neljä tilaa",
        modes_p="Jokainen tila on osa työtä: järjestäminen, tarkastus, myynti, tilaus.",
        report_h="Raportti, ei pisteitä",
        bands="Tasot suhteessa asiantuntijan tulokseen",
        device_h="Pysyy laitteessasi",
        langs_h="14 kieltä",
        langs_p="Sovellus seuraa puhelimen kieltä, ja asetuksista voit valita toisen. Tämä sivusto on "
                "samoilla 14 kielellä.",
        new_h="Uutta versiossa {v}",
        skip="Siirry sisältöön",
        mockup="Reyonin Tehtävät-näkymä: päivän tapaukset neljässä tilassa ja niiden alla harjoitus "
               "myymäläformaatin mukaan.",
        home="Reyonin etusivu",
        notfound="Sivua ei ole.",
    ),
    "ru": dict(
        meta="Reyon — бесплатное приложение для Android о работе с полкой в FMCG: планограммы, аудит "
             "полки, отдача полки и заказы на неделю. 14 языков, без рекламы, без разрешений, "
             "полностью офлайн.",
        lead="Собери планограмму, проверь полку, подними её отдачу и управляй запасом на неделю. "
             "Каждый результат сравнивается с результатом эксперта.",
        download="Скачать APK",
        download_short="Скачать",
        releases="Все версии и контрольные суммы SHA-256",
        requires="Android 8.0 или новее · бесплатно · без аккаунта",
        install="Android может попросить разрешить установку из браузера — это обычный шаг для APK не "
                "из Google Play.",
        modes_h="Четыре режима",
        modes_p="Каждый режим — часть работы: раскладка, проверка, продажи, заказы.",
        report_h="Отчёт, а не очки",
        bands="Уровни — доля от результата эксперта",
        device_h="Остаётся на устройстве",
        langs_h="14 языков",
        langs_p="Приложение следует языку телефона, а в настройках можно выбрать другой. Этот сайт "
                "есть на тех же 14 языках.",
        new_h="Что нового в версии {v}",
        skip="Перейти к содержанию",
        mockup="Экран «Задачи» в Reyon: кейсы дня в четырёх режимах, ниже — тренировка по формату "
               "магазина.",
        home="Главная страница Reyon",
        notfound="Такой страницы нет.",
    ),
    "ar": dict(
        meta="Reyon تطبيق Android مجاني للعمل على رفوف السلع الاستهلاكية: مخطط الرف، وتدقيق الرف، "
             "وعائد الرف، وطلبيات الأسبوع. 14 لغة، بلا إعلانات ولا أذونات، ويعمل دون اتصال بالكامل.",
        lead="ابنِ مخطط الرف، ودقّق الرف، وارفع عائده، وأدِر مخزون أسبوع كامل. تُقاس كل نتيجة بنتيجة "
             "خبير.",
        download="تنزيل APK",
        download_short="تنزيل",
        releases="كل الإصدارات وبصمات SHA-256",
        requires="Android 8.0 أو أحدث · مجاني · بلا حساب",
        install="قد يطلب منك Android السماح بالتثبيت من المتصفح؛ هذه هي الخطوة المعتادة لملف APK من "
                "خارج Google Play.",
        modes_h="أربعة أوضاع",
        modes_p="كل وضع جزء من العمل: الترتيب، والتدقيق، والبيع، والطلب.",
        report_h="تقرير لا نقاط",
        bands="المستويات نسبةً إلى نتيجة الخبير",
        device_h="يبقى على جهازك",
        langs_h="14 لغة",
        langs_p="يتبع التطبيق لغة هاتفك، ويمكنك اختيار لغة أخرى من الإعدادات. وهذا الموقع متاح "
                "باللغات الأربع عشرة نفسها.",
        new_h="الجديد في الإصدار {v}",
        skip="انتقل إلى المحتوى",
        mockup="شاشة المهام في Reyon: حالات اليوم في الأوضاع الأربعة، وتحتها التدريب حسب صيغة المتجر.",
        home="الصفحة الرئيسية لـ Reyon",
        notfound="هذه الصفحة غير موجودة.",
    ),
}

# Başlıktaki uzun bileşik sözcüklerin bölünebileceği yerler (yumuşak tire).
# 320 px'lik ekranda "Päivittäistavarakaupan" tek satıra sığmaz; tarayıcının
# kendi heceleme sözlüğü her yerde yok, bölme yeri burada elle yazılı.
SOFT_HYPHENS = {
    "de": ("Regal|management",),
    "da": ("Hylde|simulator", "dagligvare|handlen"),
    "sv": ("Hyll|simulator", "dagligvaru|handeln"),
    "nb": ("Hylle|simulator", "dagligvare|handelen"),
    "fi": ("Päivittäis|tavara|kaupan", "hylly|simulaattori"),
}


def hyphenate(tag, text):
    for word in SOFT_HYPHENS.get(tag, ()):
        text = text.replace(word.replace("|", ""), word.replace("|", "\u00ad"))
    return text


KINDS = ("puzzle", "audit", "sales", "order")
FORMATS = (("market", 3, 4), ("supermarket", 4, 5), ("hypermarket", 4, 6))


# --------------------------------------------------------------------------
# Kaynaklar


def links():
    """Links.kt'deki adresler; site alan adı ve depo bağlantıları oradan."""
    src = open(LINKS_KT, encoding="utf-8").read()
    return dict(re.findall(r'const val (\w+) = "([^"]+)"', src))


def endonyms():
    """AppLocale.ENDONYMS: seçicide her dil kendi adıyla."""
    src = open(gen_privacy.APP_LOCALE, encoding="utf-8").read()
    block = re.search(r"ENDONYMS: Map<String, String> = mapOf\((.*?)\n    \)", src, re.S).group(1)
    return dict(re.findall(r'"([^"]+)" to "([^"]+)"', block))


def by_name(names):
    """Görünen dil listelerinin sırası: dilin kendi adına göre alfabetik (Dansk, Deutsch,
    English … Русский, العربية). Aksan sırayı bozmaz; Latin dışı yazılar Unicode
    sırasıyla sona düşer. Uygulamanın kendi sırası (AppLocale.TAGS) değişmez."""
    def key(tag):
        name = unicodedata.normalize("NFD", names[tag])
        return "".join(ch for ch in name if not unicodedata.combining(ch)).casefold()
    return sorted(TAGS, key=key)


def android_strings(tag):
    """values(-tag)/strings.xml; Android kaçışları çözülmüş hâliyle."""
    folder = "values" if tag == "en" else f"values-{tag}"
    tree = ET.parse(os.path.join(RES, folder, "strings.xml"))
    out = {}
    for el in tree.getroot().iter("string"):
        text = "".join(el.itertext())
        out[el.get("name")] = re.sub(r"\\(['\"\\])", r"\1", text).replace("\\n", "\n")
    return out


def fmt(pattern, *args):
    """Android biçim dizesi: %1$s, %2$d ve %%."""
    def sub(m):
        return "%" if m.group(0) == "%%" else str(args[int(m.group(1)) - 1])
    return re.sub(r"%%|%(\d+)\$[sd]", sub, pattern)


def store(tag):
    """Play kaydı: başlık, kısa açıklama ve full.txt'nin bölümleri."""
    base = os.path.join(STORE, "play", tag)
    read = lambda name: open(os.path.join(base, name), encoding="utf-8").read().strip()  # noqa: E731
    paras = read("full.txt").split("\n\n")
    if len(paras) != 8 or len(paras[2].split("\n")) != 4:
        sys.exit(f"store/play/{tag}/full.txt beklenen düzende değil (8 paragraf, 4 mod satırı)")
    tasks_label, tasks = paras[1].split(" — ", 1)
    modes = [line.split(" — ", 1) for line in paras[2].split("\n")]
    return dict(
        title=read("title.txt"),
        short=read("short.txt"),
        tasks_label=tasks_label,
        tasks=tasks,
        modes=modes,
        results=paras[3],
        theme=paras[4].split(" · ")[0],
        privacy=paras[6],
        license=paras[7],
    )


def release_notes():
    """En yeni sürüm notu: (sürüm, {Play yerel ayarı: [madde, ...]})."""
    folder = os.path.join(STORE, "play", "release-notes")
    versions = [f[:-4] for f in os.listdir(folder) if re.fullmatch(r"\d+\.\d+\.\d+\.txt", f)]
    latest = max(versions, key=lambda v: tuple(int(p) for p in v.split(".")))
    src = open(os.path.join(folder, latest + ".txt"), encoding="utf-8").read()
    notes = {}
    for loc, body in re.findall(r"<([A-Za-z-]+)>\n(.*?)\n</\1>", src, re.S):
        notes[loc] = [line.lstrip("•").strip() for line in body.split("\n") if line.strip()]
    return latest, notes


def load(tag):
    """Bir dilin sayfası için gereken her şey; kaynaklar birbirini tutmazsa durur."""
    s = android_strings(tag)
    st = store(tag)
    kinds = [s[f"reyon_kind_{k}"] for k in KINDS]
    labels = [label for label, _ in st["modes"]]
    if labels != kinds:
        sys.exit(f"{tag}: full.txt'deki mod adları {labels} uygulamadakilerle {kinds} aynı değil")
    if st["tasks_label"] != s["nav_tasks"]:
        sys.exit(f"{tag}: full.txt '{st['tasks_label']}' ile başlıyor, uygulamada '{s['nav_tasks']}'")
    return s, st


# --------------------------------------------------------------------------
# Yardımcılar


def typo(tag, text):
    """Fransızca noktalamada boşluk kırılmasın: iki nokta, noktalı virgül, tırnak."""
    if tag == "fr":
        text = re.sub(r" ([:;?!»])", "\u00a0\\1", text).replace("« ", "«\u00a0")
    return text


def t(tag, text):
    """Metin düğümü: kaçış ve dil tipografisi."""
    return html.escape(typo(tag, text), quote=False)


def a(tag, text):
    """Öznitelik değeri."""
    return html.escape(typo(tag, text), quote=True)


def percent(s, value):
    return fmt(s["percent_fmt"], value)


def decimal(tag, value):
    text = f"{value:.1f}"
    return text if tag in DECIMAL_POINT else text.replace(".", ",")


def home_href(prefix, tag):
    return (prefix + ("" if tag == "en" else f"{tag}/")) or "./"


def page_url(base, tag):
    return base if tag == "en" else f"{base}{tag}/"


# --------------------------------------------------------------------------
# Çizimler. Renkler CSS sınıflarından gelir (site.css), iki temada da uyar.

ICONS = {
    "planogram": '<path d="M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM3 10h18M3 15h18M10 4v6M14 10v5"/>',
    "audit": '<path d="M7 4h10a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM9 3h6v3H9zM9 13l2 2 4-4"/>',
    "sales": '<path d="M3 17l6-6 4 4 8-8M15 7h6v6"/>',
    "order": '<path d="M3 7.5l9-4.5 9 4.5v9l-9 4.5-9-4.5zM3 7.5l9 4.5 9-4.5M12 12v9"/>',
    "globe": '<path d="M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM3.6 9h16.8M3.6 15h16.8M12 3c-2.4 2.6-3.6 5.6-3.6 9s1.2 6.4 3.6 9M12 3c2.4 2.6 3.6 5.6 3.6 9s-1.2 6.4-3.6 9"/>',
    "download": '<path d="M12 4v11M7 10.5l5 5 5-5M5 20h14"/>',
    "down": '<path d="M6 9l6 6 6-6"/>',
    "arrow": '<path d="M5 12h14M13 6l6 6-6 6"/>',
    "check": '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
    "chevron": '<path d="M9 6l6 6-6 6"/>',
    "sliders": '<path d="M4 8h9M17 8h3M4 16h3M11 16h9"/><circle cx="15" cy="8" r="2"/><circle cx="9" cy="16" r="2"/>',
    "mail": '<path d="M4 6h16v12H4zM4 7l8 6 8-6"/>',
}
MODE_ICON = dict(zip(KINDS, ("planogram", "audit", "sales", "order")))


def sprite():
    symbols = "".join(f'<symbol id="i-{name}" viewBox="0 0 24 24">{body}</symbol>'
                      for name, body in ICONS.items())
    return f'<svg class="sprite" aria-hidden="true" focusable="false"><defs>{symbols}</defs></svg>'


def icon(name, cls="ic"):
    return f'<svg class="{cls}" aria-hidden="true" focusable="false"><use href="#i-{name}"/></svg>'


# Uygulama simgesinin işareti (ic_launcher_foreground.xml'deki "mark" grubu):
# 36 birimlik kutuda üç raf çizgisi, sekiz ambalaj; üçüncüsü dikkat sarısında.
MARK_PACKS = ((6, 4, 7, 7), (15, 6, 6, 5), (23, 3, 7, 8), (6, 15, 9, 6),
              (24, 14, 6, 7), (6, 25, 6, 6), (14, 24, 7, 7), (23, 26, 7, 5))


def mark_body(ink, accent):
    """ink/accent: öznitelik parçaları (sınıf ya da doğrudan renk)."""
    lines = f'<path d="M4 12h28M4 22h28M4 32h28" {ink.replace("fill", "stroke")} stroke-width="2.5" stroke-linecap="round"/>'
    packs = "".join(
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="1.5" {accent if i == 2 else ink}/>'
        for i, (x, y, w, h) in enumerate(MARK_PACKS))
    return lines + packs


def attr(name, value, when=True):
    """Koşullu öznitelik: when yanlışsa boş."""
    return f' {name}="{value}"' if when else ""


def mark(cls="mark"):
    """İşaret; renkleri CSS verir (mürekkep currentColor, ambalaj dikkat sarısı)."""
    body = mark_body(ink='class="mk-ink" fill="currentColor"', accent='class="mk-acc"')
    return f'<svg class="{cls}" viewBox="0 0 36 36" aria-hidden="true" focusable="false">{body}</svg>'


def mark_box():
    """Marka rengindeki kutuda işaret: üst şerit, altbilgi, telefon maketi."""
    return f'<span class="mark-box">{mark()}</span>'


def hero_stage():
    """Telefonun arkasındaki sahne: simgenin raf çizgileri ve ambalajları, telefonun
    iki yanında görünecek genişlikte. Biri dikkat sarısında, simgedeki gibi."""
    packs = ((9, 28, 7, 12), (17.5, 28, 6, 8), (96, 28, 7, 14), (104.5, 28, 7, 9),
             (8.5, 56, 10, 9), (20, 56, 5, 12), (96, 56, 6, 10), (103.5, 56, 9, 7),
             (9, 84, 6, 8), (16.5, 84, 8, 11), (95.5, 84, 8, 12), (105, 84, 6, 7))
    body = "".join(
        f'<rect x="{x}" y="{bottom - h - 1.6}" width="{w}" height="{h}" rx="1.5" '
        f'class="{"mk-acc" if i == 2 else "hm-ink"}"/>' for i, (x, bottom, w, h) in enumerate(packs))
    return ('<svg class="hero-stage" viewBox="0 0 120 100" aria-hidden="true" focusable="false">'
            '<rect width="120" height="100" rx="22" class="hm-bg"/>'
            '<path d="M6 28H114M6 56H114M6 84H114" class="hm-line"/>'
            f'{body}</svg>')


def icon_svg():
    """Sekme simgesi: petrol zemin, beyaz işaret, sarı ambalaj."""
    body = mark_body(ink='fill="#FFFFFF"', accent='fill="#FFD23F"')
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 36 36">'
            '<rect width="36" height="36" rx="8" fill="#0F4C5C"/>'
            f'<g transform="translate(5 5) scale(0.7222)">{body}</g></svg>\n')


def pack(x, bottom, w, h, cls, kind="box"):
    """Rafta duran bir ambalaj; alt kenarı `bottom`."""
    top = bottom - h
    if kind == "bottle":
        body_h = round(h * 0.76, 1)
        neck_w = round(w * 0.38, 1)
        nx = round(x + (w - neck_w) / 2, 1)
        return (f'<rect x="{nx}" y="{top}" width="{neck_w}" height="{h - body_h + 4}" rx="2" class="{cls}"/>'
                f'<rect x="{nx}" y="{top}" width="{neck_w}" height="4" rx="1.5" class="s-cap"/>'
                f'<rect x="{x}" y="{bottom - body_h}" width="{w}" height="{body_h}" rx="{round(w * 0.3, 1)}" class="{cls}"/>'
                f'<rect x="{x + 3}" y="{round(bottom - body_h * 0.62, 1)}" width="{w - 6}" height="{round(body_h * 0.3, 1)}" rx="2" class="s-hi"/>')
    if kind == "can":
        return (f'<rect x="{x}" y="{top}" width="{w}" height="{h}" rx="5" class="{cls}"/>'
                f'<rect x="{x}" y="{top + 4}" width="{w}" height="2" class="s-cap"/>'
                f'<rect x="{x}" y="{bottom - 6}" width="{w}" height="2" class="s-cap"/>'
                f'<rect x="{x + 3}" y="{round(top + h * 0.34, 1)}" width="{w - 6}" height="{round(h * 0.28, 1)}" rx="2" class="s-hi"/>')
    return (f'<rect x="{x}" y="{top}" width="{w}" height="{h}" rx="3" class="{cls}"/>'
            f'<rect x="{x}" y="{top}" width="{w}" height="4" rx="2" class="s-cap"/>'
            f'<rect x="{x + 4}" y="{round(top + h * 0.4, 1)}" width="{w - 8}" height="{round(h * 0.3, 1)}" rx="2" class="s-hi"/>')


def shelf(x, y, w, rail=12):
    """Raf çıtası ve altındaki etiket rayı; ambalajlar y'ye oturur."""
    return (f'<rect x="{x}" y="{y}" width="{w}" height="5" class="s-plank"/>'
            f'<rect x="{x}" y="{y + 5}" width="{w}" height="{rail}" class="s-rail"/>')


def label(x, y, w, cat, value=None):
    """Raf etiketi: kategori noktası, talep çubukları, istenirse katkı (+16)."""
    bars = "".join(
        f'<rect x="{x + 11 + i * 3.4}" y="{y + 2.5}" width="2.2" height="6" rx="0.6" class="{"s-ink" if i < 2 else "s-dim"}"/>'
        for i in range(3))
    out = (f'<rect x="{x}" y="{y}" width="{w}" height="11" rx="2" class="s-label"/>'
           f'<circle cx="{x + 5.5}" cy="{y + 5.5}" r="2.6" class="{cat}"/>{bars}')
    if value:
        out += f'<text x="{x + w - 4}" y="{y + 8.6}" text-anchor="end" class="s-val">{value}</text>'
    return out


def art(body, view="0 0 320 180"):
    return (f'<svg class="art" viewBox="{view}" aria-hidden="true" focusable="false" '
            f'preserveAspectRatio="xMidYMid meet">{body}</svg>')


def art_puzzle():
    """Diziliş: tepsideki ürün boş göze gider."""
    body = [
        pack(20, 70, 40, 48, "pk-a"), pack(64, 70, 40, 48, "pk-a"),
        '<rect x="109" y="25" width="42" height="44" rx="4" class="s-empty"/>',
        pack(156, 70, 22, 34, "pk-d", "can"), pack(182, 70, 22, 34, "pk-d", "can"),
        shelf(12, 70, 196),
        label(20, 76.5, 84, "c1"), label(108, 76.5, 44, "c2"), label(156, 76.5, 48, "c5"),
        pack(20, 146, 24, 62, "pk-b", "bottle"), pack(48, 146, 24, 62, "pk-b", "bottle"),
        pack(76, 146, 24, 62, "pk-b", "bottle"), pack(106, 146, 50, 40, "pk-c"),
        pack(160, 146, 44, 52, "c4"),
        shelf(12, 146, 196),
        label(20, 152.5, 80, "c1"), label(106, 152.5, 50, "c2"), label(160, 152.5, 44, "c4"),
        '<rect x="222" y="14" width="86" height="152" rx="10" class="s-card"/>',
    ]
    for i, (cls, w1, w2) in enumerate((("pk-b", 40, 26), ("c2", 36, 30), ("pk-d", 42, 22), ("c4", 34, 28))):
        y = 26 + i * 34
        body.append(f'<rect x="232" y="{y}" width="18" height="24" rx="3" class="{cls}"/>'
                    f'<rect x="256" y="{y + 6}" width="{w1}" height="4" rx="2" class="s-line"/>'
                    f'<rect x="256" y="{y + 14}" width="{w2}" height="4" rx="2" class="s-line2"/>')
    body.append('<rect x="227" y="55" width="76" height="34" rx="8" class="s-ring"/>')
    body.append('<path d="M227 72 C 196 72, 162 10, 131 20" class="s-path"/>'
                '<path d="M138 13 L130 20.5 L140 24" class="s-path s-head"/>')
    return art("".join(body))


def art_audit():
    """Denetim: üstte planogram, altta raf; sapmalarda bayrak."""
    plan = ("pk-a", "pk-b", "pk-c", "pk-d", "c4")
    shelf_row = ("pk-a", "pk-c", "pk-b", None, "c4")
    body = ['<rect x="16" y="10" width="288" height="66" rx="8" class="s-card"/>']
    for i, cls in enumerate(plan):
        x = 28 + i * 56
        body.append(f'<rect x="{x}" y="22" width="44" height="42" rx="4" class="s-plan"/>'
                    f'<rect x="{x + 6}" y="28" width="32" height="6" rx="2" class="{cls}"/>'
                    f'<rect x="{x + 6}" y="40" width="22" height="4" rx="2" class="s-line2"/>')
    for i, cls in enumerate(shelf_row):
        x = 28 + i * 56
        if cls is None:
            body.append(f'<rect x="{x + 1}" y="109" width="42" height="40" rx="4" class="s-empty"/>')
        else:
            body.append(pack(x, 150, 44, 42, cls))
    body.append(shelf(16, 150, 288))
    for i, cat in enumerate(("c1", "c1", "c2", "c5", "c4")):
        body.append(label(28 + i * 56, 156.5, 44, cat))
    for i, cls in ((1, "s-warn"), (2, "s-warn"), (3, "s-bad")):
        cx = 28 + i * 56 + 22
        body.append(f'<rect x="{cx - 1}" y="84" width="2" height="22" rx="1" class="{cls}"/>'
                    f'<path d="M{cx + 1} 84 L{cx + 15} 89.5 L{cx + 1} 95 Z" class="{cls}"/>')
    return art("".join(body))


def art_sales():
    """Satış: raf verimi çubuğu, etiketlerde katkılar."""
    body = [
        '<rect x="16" y="14" width="288" height="10" rx="5" class="s-track"/>',
        '<rect x="16" y="14" width="239" height="10" rx="5" class="s-brand"/>',
        '<rect x="302" y="9" width="2.5" height="20" rx="1" class="s-ink-muted"/>',
        pack(20, 86, 60, 46, "pk-d"), pack(84, 86, 64, 46, "pk-d"),
        pack(152, 86, 70, 40, "pk-b"),
        pack(226, 86, 24, 54, "pk-a", "bottle"), pack(254, 86, 24, 54, "pk-a", "bottle"),
        pack(282, 86, 22, 54, "pk-a", "bottle"),
        shelf(12, 86, 296, rail=14),
        label(20, 92.5, 128, "c5", "+43"), label(152, 92.5, 70, "c4", "+16"), label(226, 92.5, 78, "c1", "+8"),
        pack(20, 160, 90, 40, "pk-d"), pack(114, 160, 44, 48, "pk-c"), pack(162, 160, 44, 48, "pk-c"),
        pack(210, 160, 30, 36, "pk-b", "can"), pack(244, 160, 30, 36, "pk-b", "can"),
        pack(278, 160, 26, 36, "pk-b", "can"),
        shelf(12, 160, 296, rail=14),
        label(20, 166.5, 90, "c3", "+11"), label(114, 166.5, 92, "c2", "+12"), label(210, 166.5, 94, "c1", "+5"),
    ]
    return art("".join(body))


def art_order():
    """Sipariş: günlük kâr çubukları, devir çizgisi, uzmanın kesikli çizgisi."""
    heights = (92, 86, 98, 102, 72)
    body = ['<path d="M24 150H296" class="s-axis"/>']
    points = []
    for i, h in enumerate(heights):
        x = 40 + i * 52
        body.append(f'<rect x="{x}" y="{150 - h}" width="30" height="{h}" rx="4" class="s-brand"/>')
        points.append((x + 15, (138, 122, 104, 90, 78)[i]))
    body.append('<path d="M24 64H296" class="s-expert"/>')
    body.append('<polyline points="' + " ".join(f"{x},{y}" for x, y in points) + '" class="s-turn"/>')
    body.extend(f'<circle cx="{x}" cy="{y}" r="4.5" class="s-turn-dot"/>' for x, y in points)
    return art("".join(body))


ARTS = dict(puzzle=art_puzzle, audit=art_audit, sales=art_sales, order=art_order)


def format_grid(rows, cols):
    """Mağaza formatının rafı: satır × sütun göz, sağ üstte bir sarı ambalaj."""
    colors = ("pk-a", "pk-b", "pk-c", "pk-d", "c4", "c2")
    left, top, width, height = 8, 6, 104, 76
    sh = height / rows
    gw = width / cols
    body = []
    for r in range(rows):
        base = top + (r + 1) * sh - 3
        for c in range(cols):
            cls = "s-att" if (r == 0 and c == cols - 1) else colors[(r * 2 + c) % len(colors)]
            ph = round(sh * (0.62 if (r + c) % 3 else 0.72), 1)
            x = round(left + c * gw + 1.5, 1)
            body.append(f'<rect x="{x}" y="{round(base - ph, 1)}" width="{round(gw - 3, 1)}" '
                        f'height="{ph}" rx="1.6" class="{cls}"/>')
        body.append(f'<rect x="{left - 2}" y="{round(base, 1)}" width="{width + 4}" height="3" class="s-plank"/>')
    return art("".join(body), view="0 0 120 86")


def shelf_band():
    """Kahramanın altındaki raf şeridi: 360 px'lik tekrar eden bir gondol dilimi."""
    packs = [
        pack(10, 100, 34, 62, "pk-a"), pack(48, 100, 34, 62, "pk-a"),
        pack(90, 100, 22, 70, "pk-b", "bottle"), pack(116, 100, 22, 70, "pk-b", "bottle"),
        pack(142, 100, 22, 70, "pk-b", "bottle"),
        pack(172, 100, 44, 44, "pk-c"), pack(222, 100, 30, 56, "s-att"),
        pack(258, 100, 26, 40, "pk-d", "can"), pack(288, 100, 26, 40, "pk-d", "can"),
        pack(318, 100, 34, 58, "c4"),
        '<rect x="0" y="100" width="360" height="6" class="s-plank"/>',
        '<rect x="0" y="106" width="360" height="26" class="s-rail"/>',
        label(10, 110, 72, "c1"), label(90, 110, 74, "c1"), label(172, 110, 44, "c2"),
        label(222, 110, 30, "c3"), label(258, 110, 56, "c5"), label(318, 110, 34, "c4"),
    ]
    return ('<div class="band" aria-hidden="true"><svg class="art" width="100%" height="132" '
            'focusable="false"><defs><pattern id="gondola" width="360" height="132" '
            'patternUnits="userSpaceOnUse"><rect width="360" height="132" class="s-back"/>'
            + "".join(packs) + '</pattern></defs><rect width="100%" height="132" fill="url(#gondola)"/>'
            '</svg></div>')


# --------------------------------------------------------------------------
# Sayfa parçaları


def head(tag, title, desc, base, prefix, canonical, alternates=True, extra=""):
    rtl = tag in RTL
    font = {"ar": "reyon-sans-arabic-400.woff2", "ru": "reyon-sans-400-cyrillic.woff2"}.get(
        tag, "reyon-sans-400-latin.woff2")
    out = [
        "<!doctype html>",
        f'<html lang="{tag}"{attr("dir", "rtl", rtl)}>',
        "<head>",
        '<meta charset="utf-8">',
        '<meta name="viewport" content="width=device-width, initial-scale=1">',
        f"<title>{t(tag, title)}</title>",
        f'<meta name="description" content="{a(tag, desc)}">',
        '<meta name="color-scheme" content="light dark">',
        '<meta name="theme-color" content="#F3F2EE" media="(prefers-color-scheme: light)">',
        '<meta name="theme-color" content="#111513" media="(prefers-color-scheme: dark)">',
        f'<link rel="canonical" href="{canonical}">',
    ]
    if alternates:
        out += [f'<link rel="alternate" hreflang="{x}" href="{page_url(base, x)}">' for x in TAGS]
        out.append(f'<link rel="alternate" hreflang="x-default" href="{base}">')
    out += [
        f'<link rel="icon" href="{prefix}assets/icon.svg" type="image/svg+xml">',
        f'<link rel="apple-touch-icon" href="{prefix}assets/icon-512.png">',
        f'<link rel="preload" href="{prefix}assets/fonts/{font}" as="font" type="font/woff2" crossorigin>',
        f'<link rel="stylesheet" href="{prefix}assets/site.css">',
        f'<script src="{prefix}assets/site.js" defer></script>',
    ]
    return "\n".join(out) + extra


def og(tag, title, desc, base, url):
    out = [
        '<meta property="og:type" content="website">',
        '<meta property="og:site_name" content="Reyon">',
        f'<meta property="og:title" content="{a(tag, title)}">',
        f'<meta property="og:description" content="{a(tag, desc)}">',
        f'<meta property="og:url" content="{url}">',
        f'<meta property="og:image" content="{base}assets/og.png">',
        '<meta property="og:image:width" content="1024">',
        '<meta property="og:image:height" content="500">',
        f'<meta property="og:locale" content="{OG_LOCALE[tag]}">',
    ]
    out += [f'<meta property="og:locale:alternate" content="{OG_LOCALE[x]}">' for x in TAGS if x != tag]
    out.append('<meta name="twitter:card" content="summary_large_image">')
    return "\n".join(out)


# Kök sayfa: ilk ziyarette tarayıcının dili destekleniyorsa o dilin sayfası.
# Seçicide bir dile tıklayan (site.js hatırlar) bir daha yönlendirilmez;
# betik çalışmazsa sayfa İngilizce kalır. Çizimden önce koşsun diye satır içi.
REDIRECT = """<script>
(function () {
  var tags = %s, key = "reyon.lang", pick = null;
  try { pick = localStorage.getItem(key); } catch (e) {}
  if (!pick) {
    var prefs = navigator.languages || [navigator.language || ""];
    for (var i = 0; i < prefs.length && !pick; i++) {
      var p = String(prefs[i]).toLowerCase().split("-")[0];
      if (p === "no" || p === "nn") p = "nb";
      if (p === "en" || tags.indexOf(p) >= 0) pick = p;
    }
  }
  if (pick && pick !== "en" && tags.indexOf(pick) >= 0) location.replace(pick + "/");
})();
</script>"""


def topbar(tag, s, c, names, prefix):
    items = []
    for x in by_name(names):
        current = ' aria-current="page"' if x == tag else ""
        items.append(f'<li><a href="{home_href(prefix, x)}" hreflang="{x}" lang="{x}" '
                     f'data-lang="{x}"{current}>{html.escape(names[x])}'
                     f'{icon("check", "ic ic-sm") if x == tag else ""}</a></li>')
    return f"""<header class="top">
  <div class="wrap top-in">
    <a class="brand" href="{home_href(prefix, tag)}">{mark_box()}<span class="brand-name">Reyon</span></a>
    <div class="top-actions">
      <details class="lang">
        <summary aria-label="{a(tag, s['language_title'])}: {html.escape(names[tag])}">{icon("globe")}<span>{html.escape(names[tag])}</span>{icon("down", "ic ic-sm")}</summary>
        <ul class="lang-menu">
          {"".join(items)}
        </ul>
      </details>
      <a class="btn btn-sm" href="{APK}">{icon("download", "ic ic-sm")}<span>{t(tag, c['download_short'])}</span></a>
    </div>
  </div>
</header>"""


def phone(tag, s, c):
    """Görevler ekranının maketi; metin uygulamanın kendi metni."""
    names = [s[f"reyon_kind_{k}"] for k in KINDS]
    fmt_names = [s[f"format_{f}"] for f, _, _ in FORMATS]
    today_lines = "".join(
        f'<p class="m-fmt" data-format="{i}" data-today-only{"" if i == 1 else " hidden"}>'
        f'{t(tag, fmt(s["home_daily_format_fmt"], fmt_names[i], r, cols))}</p>'
        for i, (_, r, cols) in enumerate(FORMATS))
    tiles = []
    for i, name in enumerate(names):
        done = i in (0, 3)
        value = ("0:47", None, None, "402/558")[i]
        tiles.append(
            f'<span class="m-tile{" done" if done else ""}">'
            f'{icon("check", "ic m-check") if done else ""}<span class="m-nr"><b>{t(tag, name)}</b>'
            f'<i{attr("class", "m-mono", done)}>{value if done else t(tag, s["home_start"])}</i></span></span>')
    segs = "".join(
        f'<span{attr("class", "on", i == 0)}><b>{t(tag, fmt_names[i])}</b>'
        f'<small>{t(tag, fmt(s["format_shelf_fmt"], r, cols))}</small></span>'
        for i, (_, r, cols) in enumerate(FORMATS))
    items = []
    for i, k in enumerate(KINDS):
        value = '<i class="m-mono">0:30</i>' if i == 0 else ""
        items.append(
            f'<span class="m-item"><span class="m-ic">{icon(MODE_ICON[k])}</span>'
            f'<span class="m-it"><b>{t(tag, names[i])}</b><small>{t(tag, s[f"home_{k}_desc"])}</small></span>'
            f'{value}{icon("chevron", "ic m-chev")}</span>')
    return f"""<div class="phone" role="img" aria-label="{a(tag, c['mockup'])}">
        <div class="phone-screen">
          <div class="m-top">
            <span class="m-icon">{mark("mark")}</span>
            <span class="m-title"><b>{t(tag, s['home_title'])}</b><small>{t(tag, s['home_subtitle'])}</small></span>
            {icon("sliders", "ic m-set")}
          </div>
          <div class="m-daily">
            <div class="m-head"><span class="m-eyebrow">{t(tag, s['home_daily_title'])}</span><span class="m-mono"><span data-date></span>2/4</span></div>
            <p class="m-desc">{t(tag, s['home_daily_desc'])}</p>
            {today_lines}
            <div class="m-tiles">{"".join(tiles)}</div>
          </div>
          <div class="m-row"><b>{t(tag, s['home_format_title'])}</b><small>{t(tag, s['home_format_hint'])}</small></div>
          <div class="m-seg">{segs}</div>
          <div class="m-row"><b>{t(tag, s['home_practice_title'])}</b><small>{t(tag, s['home_last_result'])}</small></div>
          <div class="m-list">{"".join(items)}</div>
        </div>
      </div>"""


def report_card(tag, s):
    """Sipariş'in haftalık raporundan bir kesit; metin uygulamanın."""
    pts = fmt(s["report_points_fmt"], "−10")
    return f"""<div class="rep-wrap" aria-hidden="true">
        <div class="rep">
          <div class="rep-head"><span class="rep-label">{t(tag, s['report_week_profit'])}</span><span class="badge b-expert">{t(tag, s['band_expert'])}</span></div>
          <div class="rep-value"><b class="mono">301</b><span>{t(tag, fmt(s['report_per_expert_fmt'], 327))}</span></div>
          <div class="rep-bar"><i style="inline-size:92%"></i></div>
          <div class="rep-foot"><span>{t(tag, fmt(s['report_of_expert_fmt'], 92))}</span><span>{t(tag, fmt(s['report_expert_is_fmt'], 100))}</span></div>
          <span class="badge b-expert">{t(tag, s['report_personal_best'])}</span>
        </div>
        <div class="rep-kpis">
          <div class="rep kpi"><span class="rep-label">{t(tag, s['report_service_level'])}</span><b class="mono">{t(tag, percent(s, 86))}</b><small>{t(tag, fmt(s['report_expert_value_fmt'], percent(s, 96)))} · <em class="neg">{t(tag, pts)}</em></small></div>
          <div class="rep kpi"><span class="rep-label">{t(tag, s['report_turnover'])}</span><b class="mono">{decimal(tag, 9.6)}</b><small>{t(tag, fmt(s['report_expert_value_fmt'], decimal(tag, 9.0)))} · <em class="pos">+{decimal(tag, 0.6)}</em></small></div>
        </div>
      </div>"""


def bands(tag, s, c):
    rows = (("b-expert", s["band_expert"], "≥ " + percent(s, 90)),
            ("b-good", s["band_good"], percent(s, "75–89")),
            ("b-dev", s["band_developing"], percent(s, "50–74")),
            ("b-weak", s["band_weak"], "< " + percent(s, 50)))
    items = "".join(f'<li class="{cls}"><span>{t(tag, name)}</span>'
                    f'<b class="mono" dir="ltr">{html.escape(value)}</b></li>' for cls, name, value in rows)
    return f'<ul class="bands" aria-label="{a(tag, c["bands"])}">{items}</ul>'


def footer(tag, s, st, prefix, lnk):
    return f"""<footer class="foot">
  <div class="wrap foot-in">
    <div class="foot-brand">{mark_box()}<div><b>Reyon</b><span>{t(tag, s['app_tagline'])}</span></div></div>
    <ul class="foot-links">
      <li><a href="{prefix}gizlilik.html#{tag}">{t(tag, s['about_privacy'])}</a></li>
      <li><a href="{lnk['SOURCE']}">{t(tag, s['about_source'])}</a></li>
      <li><a href="{lnk['REPORT']}">{t(tag, s['about_report'])}</a></li>
      <li><a href="mailto:{MAIL}">{t(tag, s['about_contact'])}</a></li>
    </ul>
    <p class="foot-legal">{t(tag, st['license'])}</p>
  </div>
</footer>"""


def home_page(tag, ctx):
    s, st = load(tag)
    c = COPY[tag]
    base, names, lnk = ctx["base"], ctx["names"], ctx["links"]
    version, notes = ctx["notes"]
    prefix = "" if tag == "en" else "../"
    url = page_url(base, tag)
    extra = "\n" + og(tag, st["title"], c["meta"], base, url)
    if tag == "en":
        extra += "\n" + REDIRECT % ("[" + ", ".join(f'"{x}"' for x in TAGS if x != "en") + "]")
    kinds = [s[f"reyon_kind_{k}"] for k in KINDS]
    fmt_names = [s[f"format_{f}"] for f, _, _ in FORMATS]

    formats = "".join(
        f'<li data-format="{i}">{format_grid(r, cols)}<b>{t(tag, fmt_names[i])}</b>'
        f'<span class="mono">{t(tag, fmt(s["format_shelf_fmt"], r, cols))}</span></li>'
        for i, (_, r, cols) in enumerate(FORMATS))
    today = "".join(
        f'<p class="today" data-format="{i}" data-today-only hidden><i></i>'
        f'{t(tag, fmt(s["home_daily_format_fmt"], fmt_names[i], r, cols))}</p>'
        for i, (_, r, cols) in enumerate(FORMATS))
    modes = "".join(
        f"""<article class="mode">
          <div class="mode-art">{ARTS[k]()}</div>
          <div class="mode-body">
            <h3><span class="mode-ic">{icon(MODE_ICON[k])}</span>{t(tag, kinds[i])}</h3>
            <p class="mode-sub">{t(tag, s[f'home_{k}_desc'])}</p>
            <p>{t(tag, st['modes'][i][1])}</p>
          </div>
        </article>"""
        for i, k in enumerate(KINDS))
    zeros = "".join(
        f'<li><b class="mono">0</b><span>{t(tag, s[key].split(" ", 1)[1])}</span></li>'
        for key in ("chip_no_ads", "chip_no_trackers", "chip_no_permissions"))
    note_items = "".join(f"<li>{t(tag, n)}</li>" for n in notes[NOTE_LOCALE[tag]])
    lang_items = "".join(
        f'<li><a href="{home_href(prefix, x)}" hreflang="{x}" lang="{x}" data-lang="{x}"'
        f'{attr("aria-current", "page", x == tag)}>{html.escape(names[x])}</a></li>' for x in by_name(names))
    chips = "".join(f"<li>{t(tag, s[k])}</li>" for k in ("chip_no_ads", "chip_no_trackers", "chip_no_permissions"))
    chips += f"<li>{t(tag, st['theme'])}</li>"
    arrow = icon("arrow", "ic ic-sm ic-flip")

    body = f"""
</head>
<body>
{sprite()}
<a class="skip" href="#main">{t(tag, c['skip'])}</a>
{topbar(tag, s, c, names, prefix)}
<main id="main">
  <section class="hero">
    <div class="wrap hero-in">
      <div class="hero-text">
        <p class="eyebrow">{t(tag, s['home_subtitle'])}</p>
        <h1>{t(tag, hyphenate(tag, s['app_tagline']))}</h1>
        <p class="lead">{t(tag, c['lead'])}</p>
        <div class="cta">
          <a class="btn" href="{APK}">{icon("download")}<span>{t(tag, c['download'])}</span></a>
          <a class="more" href="{RELEASES}">{t(tag, c['releases'])}{arrow}</a>
        </div>
        <p class="fine">{t(tag, c['requires'])}</p>
        <ul class="chips">{chips}</ul>
      </div>
      <div class="hero-art">
        {hero_stage()}
        {phone(tag, s, c)}
      </div>
    </div>
    {shelf_band()}
  </section>

  <section class="section" id="daily">
    <div class="wrap split">
      <div class="split-text">
        <h2>{t(tag, s['home_daily_title'])}</h2>
        <p>{t(tag, st['tasks'])}</p>
        {today}
      </div>
      <ol class="formats" aria-label="{a(tag, s['home_format_title'])}">{formats}</ol>
    </div>
  </section>

  <section class="section" id="modes">
    <div class="wrap">
      <h2>{t(tag, c['modes_h'])}</h2>
      <p class="intro">{t(tag, c['modes_p'])}</p>
      <div class="modes">
        {modes}
      </div>
    </div>
  </section>

  <section class="section alt" id="report">
    <div class="wrap split">
      <div class="split-text">
        <h2>{t(tag, c['report_h'])}</h2>
        <p>{t(tag, st['results'])}</p>
        {bands(tag, s, c)}
      </div>
      {report_card(tag, s)}
    </div>
  </section>

  <section class="section dark" id="device">
    <div class="wrap">
      <h2>{t(tag, c['device_h'])}</h2>
      <ul class="zeros">{zeros}</ul>
      <p class="device-p">{t(tag, st['privacy'])}</p>
      <a class="more more-inv" href="{prefix}gizlilik.html#{tag}">{t(tag, s['about_privacy'])}{arrow}</a>
    </div>
  </section>

  <section class="section" id="new">
    <div class="wrap narrow">
      <h2>{t(tag, c['new_h'].format(v=version))}</h2>
      <ul class="notes">{note_items}</ul>
      <p class="fine">{t(tag, c['install'])}</p>
      <div class="cta">
        <a class="btn" href="{APK}">{icon("download")}<span>{t(tag, c['download'])}</span></a>
        <a class="more" href="{RELEASES}">{t(tag, c['releases'])}{arrow}</a>
      </div>
    </div>
  </section>

  <section class="section" id="languages">
    <div class="wrap narrow">
      <h2>{t(tag, c['langs_h'])}</h2>
      <p>{t(tag, c['langs_p'])}</p>
      <ul class="lang-grid">{lang_items}</ul>
    </div>
  </section>
</main>
{footer(tag, s, st, prefix, lnk)}
</body>
</html>
"""
    return head(tag, st["title"], c["meta"], base, prefix, url, extra=extra) + body


def privacy_page(ctx):
    """Gizlilik politikası: 14 dil tek adreste; her bölüm kendi çapasında."""
    base, names = ctx["base"], ctx["names"]
    policy = gen_privacy.POLICY
    nav = "".join(f'<li><a href="#{x}" hreflang="{x}" lang="{x}">{html.escape(names[x])}</a></li>'
                  for x in by_name(names))
    sections = []
    for x in by_name(names):
        p = policy[x]
        rtl = ' dir="rtl"' if x in RTL else ""
        parts = [f'  <section id="{x}" lang="{x}"{rtl} class="policy">',
                 f'    <h2>{p["name"]} — {p["title"]}</h2>',
                 f'    <p class="meta">{p["meta"]}</p>',
                 f'    <div class="summary"><strong>{p["short_label"]}</strong> {p["short"]}</div>']
        for heading, body in p["sections"]:
            parts.append(f"    <h3>{heading}</h3>")
            parts.append(f"    <p>{body}</p>")
        parts.append(f'    <h3>{p["contact"]}</h3>')
        parts.append(f'    <p>{p["contact_intro"]} <a href="mailto:{MAIL}">{MAIL}</a> · '
                     f'<a href="{gen_privacy.ISSUES}">github.com/aripdcom/reyon/issues</a></p>')
        parts.append(f'    <p class="policy-home"><a href="{home_href("", x)}" data-lang="{x}">'
                     f'{t(x, COPY[x]["home"])}{icon("arrow", "ic ic-sm ic-flip")}</a></p>')
        parts.append("  </section>")
        sections.append("\n".join(parts))
    url = base + "gizlilik.html"
    title = "Reyon · Gizlilik politikası · Privacy policy"
    desc = ("Reyon gizlilik politikası, 14 dilde: uygulama hiçbir veri toplamaz, hiçbir izin istemez, "
            "ağa bağlanmaz.")
    return head("tr", title, desc, base, "", url, alternates=False) + f"""
</head>
<body class="doc">
{sprite()}
<a class="skip" href="#main">İçeriğe geç · <span lang="en">Skip to content</span></a>
<header class="top">
  <div class="wrap top-in">
    <a class="brand" href="./">{mark_box()}<span class="brand-name">Reyon</span></a>
  </div>
</header>
<main id="main" class="wrap doc-main" data-policy>
  <h1>Gizlilik politikası · <span lang="en">Privacy policy</span></h1>
  <p class="meta">Aynı politika, {len(TAGS)} dilde. / <span lang="en">The same policy, in {len(TAGS)} languages.</span></p>
  <nav aria-label="Dil · Language"><ul class="doc-langs">{nav}</ul></nav>

{chr(10).join(sections)}
</main>
<footer class="foot">
  <div class="wrap foot-in">
    <div class="foot-brand">{mark_box()}<div><b>Reyon</b><span lang="en">FMCG shelf management simulator</span></div></div>
    <ul class="foot-links">
      <li><a href="./">reyon.aripd.com</a></li>
      <li><a href="{ctx['links']['SOURCE']}">GitHub</a></li>
      <li><a href="mailto:{MAIL}">{MAIL}</a></li>
    </ul>
  </div>
</footer>
</body>
</html>
"""


def en_redirect(ctx):
    base = ctx["base"]
    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Reyon</title>
<link rel="canonical" href="{base}">
<meta name="robots" content="noindex">
<script>try {{ localStorage.setItem("reyon.lang", "en"); }} catch (e) {{}} location.replace("../");</script>
<meta http-equiv="refresh" content="0; url=../">
</head>
<body><a href="../">Reyon</a></body>
</html>
"""


def not_found(ctx):
    """GitHub Pages her eksik adreste bunu verir; bağlantılar bu yüzden kökten."""
    names = ctx["names"]
    items = "".join(
        f'<li lang="{x}"{attr("dir", "rtl", x in RTL)}><span>{t(x, COPY[x]["notfound"])}</span>'
        f'<a href="/{"" if x == "en" else x + "/"}" data-lang="{x}">{t(x, COPY[x]["home"])}</a>'
        f'<small>{html.escape(names[x])}</small></li>' for x in by_name(names))
    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>404 · Reyon</title>
<meta name="robots" content="noindex">
<meta name="color-scheme" content="light dark">
<link rel="icon" href="/assets/icon.svg" type="image/svg+xml">
<link rel="stylesheet" href="/assets/site.css">
<script src="/assets/site.js" defer></script>
</head>
<body class="doc">
<header class="top">
  <div class="wrap top-in">
    <a class="brand" href="/">{mark_box()}<span class="brand-name">Reyon</span></a>
  </div>
</header>
<main id="main" class="wrap doc-main">
  <h1 class="mono">404</h1>
  <ul class="nf">{items}</ul>
</main>
</body>
</html>
"""


def sitemap(ctx):
    base = ctx["base"]
    alts = "".join(f'\n    <xhtml:link rel="alternate" hreflang="{x}" href="{page_url(base, x)}"/>' for x in TAGS)
    alts += f'\n    <xhtml:link rel="alternate" hreflang="x-default" href="{base}"/>'
    urls = "".join(f"\n  <url>\n    <loc>{page_url(base, x)}</loc>{alts}\n  </url>" for x in TAGS)
    urls += f"\n  <url>\n    <loc>{base}gizlilik.html</loc>\n  </url>"
    return ('<?xml version="1.0" encoding="UTF-8"?>\n'
            '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" '
            f'xmlns:xhtml="http://www.w3.org/1999/xhtml">{urls}\n</urlset>\n')


def robots(ctx):
    return f"User-agent: *\nAllow: /\n\nSitemap: {ctx['base']}sitemap.xml\n"


# --------------------------------------------------------------------------

TAGS = gen_privacy.app_tags()
COPIES = {
    "assets/og.png": os.path.join(STORE, "graphics", "feature-1024.png"),
    "assets/icon-512.png": os.path.join(STORE, "graphics", "icon-512.png"),
}


def context():
    lnk = links()
    missing = [x for x in TAGS if x not in COPY]
    if missing:
        sys.exit(f"COPY'de metni olmayan dil: {', '.join(missing)}")
    extra = [x for x in COPY if x not in TAGS]
    if extra:
        sys.exit(f"AppLocale.TAGS içinde olmayan dil: {', '.join(extra)}")
    return dict(base=lnk["SITE"].rstrip("/") + "/", names=endonyms(), links=lnk, notes=release_notes())


def build():
    """Üretilen her dosya: site/ altındaki yol → içerik (str ya da bytes)."""
    ctx = context()
    out = {}
    for tag in TAGS:
        out["index.html" if tag == "en" else f"{tag}/index.html"] = home_page(tag, ctx)
    out["en/index.html"] = en_redirect(ctx)
    out["gizlilik.html"] = privacy_page(ctx)
    out["404.html"] = not_found(ctx)
    out["sitemap.xml"] = sitemap(ctx)
    out["robots.txt"] = robots(ctx)
    out["assets/icon.svg"] = icon_svg()
    for path, src in COPIES.items():
        out[path] = open(src, "rb").read()
    return out


def main():
    files = build()
    for path, content in files.items():
        full = os.path.join(SITE, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        mode = "wb" if isinstance(content, bytes) else "w"
        with open(full, mode, **({} if mode == "wb" else {"encoding": "utf-8"})) as f:
            f.write(content)
    print(f"site/: {len(files)} dosya, {len(TAGS)} dil ({' '.join(TAGS)})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
