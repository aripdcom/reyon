#!/usr/bin/env python3
"""site/gizlilik.html'i 14 dilde üretir.

Gizlilik politikası tek adreste durur (Play bir URL ister) ve her dil kendi
bölümünde. Metin burada, üretilen sayfa depoda: `tools/check_site.py` ikisinin
ayrışmadığını denetler.

Kullanım: python3 tools/gen_privacy.py
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "site", "gizlilik.html")
APP_LOCALE = os.path.join(ROOT, "app", "src", "main", "kotlin", "com", "aripd",
                          "reyon", "platform", "AppLocale.kt")
MAIL = "reyon@aripd.com"
ISSUES = "https://github.com/aripdcom/reyon/issues/new"
RTL = {"ar"}

# Her dil aynı beş başlığı ve iletişim bölümünü taşır. Metinler HTML olarak
# yazılır (içlerinde <code> geçiyor), kaçışlanmaz.
POLICY = {}


def add(tag, name, title, meta, short_label, short, sections, contact, contact_intro):
    POLICY[tag] = dict(name=name, title=title, meta=meta, short_label=short_label,
                       short=short, sections=sections, contact=contact,
                       contact_intro=contact_intro)


add("tr", "Türkçe", "Gizlilik politikası",
    "Reyon (Android) · Yürürlük: 21 Eylül 2026 · Geliştirici: aripdcom",
    "Kısaca:",
    "Reyon hiçbir kişisel veri toplamaz, saklamaz, paylaşmaz ya da satmaz. Uygulama hiçbir izin "
    "istemez, reklam ya da izleme kütüphanesi içermez ve ağa bağlanmaz. Skorlar ve ayarlar yalnızca "
    "sizin cihazınızda durur.",
    [("Toplanan veriler",
      "Hiçbiri. Reyon ad, e-posta, konum, cihaz kimliği, kullanım istatistiği, çökme raporu ya da "
      "başka bir veri toplamaz. Uygulamada analitik, reklam ya da üçüncü taraf SDK'sı yoktur."),
     ("İzinler ve ağ",
      "Uygulama manifestosunda tek bir <code>uses-permission</code> bile yoktur; İNTERNET izni dahil. "
      "Reyon hiçbir sunucuya bağlanmaz; günlük bulmacalar cihaz tarihinden çevrimdışı üretilir."),
     ("Cihazda kalanlar",
      "Rekorlar, günlük bulmaca ilerlemesi ve ayarlar uygulamanın özel alanında saklanır; başka "
      "uygulamalar erişemez. Uygulamayı kaldırdığınızda ya da verisini temizlediğinizde silinir. "
      "Android'in cihaz yedeklemesi açıksa bu ayarlar standart uygulama yedeğine dahil olabilir; o yedek "
      "sizin Google hesabınıza aittir ve Reyon ona erişemez."),
     ("Sonuç paylaşımı",
      "Bitiş kartındaki &quot;Paylaş&quot; düğmesi bir sonuç görseli ve kısa bir metin üretir, sonra "
      "Android'in paylaşım sayfasını açar. Görsel yalnızca sizin seçtiğiniz uygulamaya, yalnızca okuma "
      "için verilir; Reyon'un kendisi hiçbir şey göndermez ve kimin ne paylaştığını bilmez."),
     ("Bağlantılar, çocuklar, değişiklikler",
      "&quot;Hakkında&quot; ekranındaki bağlantılar cihazınızdaki tarayıcıda açılır; açılan sitelerin "
      "kendi politikaları geçerlidir. Veri toplanmadığı için uygulama her yaş için uygundur. Bu politika "
      "değişirse yeni sürüm bu sayfada yayımlanır.")],
    "İletişim", "Sorular ve bildirimler için:")

add("en", "English", "Privacy policy",
    "Reyon for Android · Effective 21 September 2026 · Developer: aripdcom",
    "In short:",
    "Reyon collects, stores, shares or sells no personal data. The app requests no permissions, "
    "contains no ads or tracking libraries, and never connects to the network. Scores and settings stay "
    "on your device only.",
    [("Data collected",
      "None. No names, e-mails, location, device identifiers, usage statistics or crash reports. There "
      "are no analytics, advertising or third-party SDKs in the app."),
     ("Permissions and network",
      "The app manifest declares no <code>uses-permission</code> at all, not even INTERNET. Reyon "
      "connects to no server; daily puzzles are generated offline from the device date."),
     ("What stays on your device",
      "High scores, daily-puzzle progress and settings live in the app's private storage, where "
      "no other app can reach them. They are removed when you uninstall the app or clear its data. If "
      "Android device backup is on, the settings may be included in the standard app backup owned by "
      "your Google account; Reyon cannot access it."),
     ("Sharing results",
      "The &quot;Share&quot; button renders a result image and a short text, then opens the Android "
      "share sheet. The image is handed read-only to the app you choose; Reyon itself sends nothing "
      "and does not know what was shared."),
     ("Links, children, changes",
      "Links in the About screen open in your browser, where the target site's policy applies. Since no "
      "data is collected, the app is suitable for all ages. Any change to this policy will be published "
      "on this page.")],
    "Contact", "Questions and reports:")

add("de", "Deutsch", "Datenschutzerklärung",
    "Reyon für Android · Gültig ab 21. September 2026 · Entwickler: aripdcom",
    "Kurz gesagt:",
    "Reyon erhebt, speichert, teilt und verkauft keine personenbezogenen Daten. Die App verlangt keine "
    "Berechtigungen, enthält weder Werbung noch Tracking-Bibliotheken und verbindet sich nie mit dem Netz. "
    "Punkte und Einstellungen bleiben nur auf deinem Gerät.",
    [("Erhobene Daten",
      "Keine. Keine Namen, E-Mail-Adressen, Standorte, Gerätekennungen, Nutzungsstatistiken oder "
      "Absturzberichte. In der App gibt es keine Analyse-, Werbe- oder Drittanbieter-SDKs."),
     ("Berechtigungen und Netz",
      "Das App-Manifest enthält keine einzige <code>uses-permission</code>, nicht einmal INTERNET. "
      "Reyon verbindet sich mit keinem Server; die täglichen Rätsel entstehen offline aus dem "
      "Gerätedatum."),
     ("Was auf dem Gerät bleibt",
      "Rekorde, der Fortschritt im Tagesrätsel und die Einstellungen liegen im privaten Speicher "
      "der App, an den keine andere App kommt. Sie verschwinden, wenn du die App deinstallierst oder ihre "
      "Daten löschst. Ist die Gerätesicherung von Android aktiv, können die Einstellungen in der "
      "Standardsicherung landen, die deinem Google-Konto gehört; Reyon hat darauf keinen Zugriff."),
     ("Ergebnisse teilen",
      "Die Schaltfläche &quot;Teilen&quot; erzeugt ein Ergebnisbild und einen kurzen Text und öffnet dann "
      "das Teilen-Menü von Android. Das Bild geht nur lesend an die App, die du auswählst; Reyon "
      "selbst sendet nichts und weiß nicht, was geteilt wurde."),
     ("Links, Kinder, Änderungen",
      "Links im Info-Bildschirm öffnen sich in deinem Browser, wo die Richtlinie der Zielseite gilt. Da "
      "keine Daten erhoben werden, ist die App für jedes Alter geeignet. Jede Änderung dieser Erklärung "
      "wird auf dieser Seite veröffentlicht.")],
    "Kontakt", "Fragen und Meldungen:")

add("fr", "Français", "Politique de confidentialité",
    "Reyon pour Android · En vigueur le 21 septembre 2026 · Développeur : aripdcom",
    "En bref :",
    "Reyon ne collecte, ne conserve, ne partage ni ne vend aucune donnée personnelle. L'appli ne "
    "demande aucune autorisation, ne contient ni publicité ni bibliothèque de suivi, et ne se connecte "
    "jamais au réseau. Les scores et les réglages restent uniquement sur votre appareil.",
    [("Données collectées",
      "Aucune. Ni nom, ni e-mail, ni position, ni identifiant d'appareil, ni statistique d'usage, ni "
      "rapport de plantage. L'appli ne contient aucun SDK d'analyse, de publicité ou tiers."),
     ("Autorisations et réseau",
      "Le manifeste de l'appli ne déclare aucune <code>uses-permission</code>, pas même INTERNET. "
      "Reyon ne se connecte à aucun serveur ; les énigmes du jour sont générées hors ligne à partir "
      "de la date de l'appareil."),
     ("Ce qui reste sur votre appareil",
      "Records, progression de l'énigme du jour et réglages vivent dans le stockage privé de "
      "l'appli, inaccessible aux autres applis. Ils disparaissent quand vous désinstallez l'appli ou "
      "effacez ses données. Si la sauvegarde Android est activée, les réglages peuvent figurer dans la "
      "sauvegarde standard qui appartient à votre compte Google ; Reyon n'y a pas accès."),
     ("Partage des résultats",
      "Le bouton &quot;Partager&quot; produit une image de résultat et un court texte, puis ouvre le "
      "panneau de partage d'Android. L'image est transmise en lecture seule à l'appli que vous "
      "choisissez ; Reyon n'envoie rien et ignore ce qui a été partagé."),
     ("Liens, enfants, modifications",
      "Les liens de l'écran À propos s'ouvrent dans votre navigateur, où s'applique la politique du site "
      "visité. Comme aucune donnée n'est collectée, l'appli convient à tous les âges. Toute modification "
      "de cette politique sera publiée sur cette page.")],
    "Contact", "Questions et signalements :")

add("nl", "Nederlands", "Privacybeleid",
    "Reyon voor Android · Geldig vanaf 21 september 2026 · Ontwikkelaar: aripdcom",
    "Kort gezegd:",
    "Reyon verzamelt, bewaart, deelt of verkoopt geen persoonsgegevens. De app vraagt geen rechten, "
    "bevat geen advertenties of trackingbibliotheken en maakt nooit verbinding met het netwerk. Scores en "
    "instellingen blijven alleen op je toestel.",
    [("Verzamelde gegevens",
      "Geen. Geen namen, e-mailadressen, locatie, apparaat-id's, gebruiksstatistieken of crashrapporten. "
      "Er zitten geen analyse-, advertentie- of externe SDK's in de app."),
     ("Rechten en netwerk",
      "Het app-manifest bevat geen enkele <code>uses-permission</code>, zelfs INTERNET niet. Reyon "
      "maakt met geen enkele server verbinding; de dagpuzzels worden offline uit de datum van het toestel "
      "afgeleid."),
     ("Wat op je toestel blijft",
      "Records, de voortgang van de dagpuzzel en de instellingen staan in de privéopslag van de "
      "app, waar geen andere app bij kan. Ze verdwijnen zodra je de app verwijdert of de gegevens wist. "
      "Staat de back-up van Android aan, dan kunnen de instellingen in de standaardback-up terechtkomen "
      "die bij je Google-account hoort; Reyon kan daar niet bij."),
     ("Resultaten delen",
      "De knop &quot;Delen&quot; maakt een resultaatafbeelding en een korte tekst en opent daarna het "
      "deelvenster van Android. De afbeelding gaat alleen-lezen naar de app die jij kiest; Reyon zelf "
      "stuurt niets en weet niet wat er gedeeld is."),
     ("Links, kinderen, wijzigingen",
      "Links in het scherm Over openen in je browser, waar het beleid van die site geldt. Omdat er geen "
      "gegevens worden verzameld, is de app geschikt voor alle leeftijden. Wijzigingen in dit beleid "
      "worden op deze pagina gepubliceerd.")],
    "Contact", "Vragen en meldingen:")

add("es", "Español", "Política de privacidad",
    "Reyon para Android · En vigor el 21 de septiembre de 2026 · Desarrollador: aripdcom",
    "En resumen:",
    "Reyon no recopila, guarda, comparte ni vende ningún dato personal. La app no pide permisos, no "
    "contiene anuncios ni bibliotecas de seguimiento y nunca se conecta a la red. Las puntuaciones y los "
    "ajustes se quedan solo en tu dispositivo.",
    [("Datos recopilados",
      "Ninguno. Ni nombres, ni correos, ni ubicación, ni identificadores del dispositivo, ni estadísticas "
      "de uso, ni informes de fallos. La app no lleva SDK de analítica, publicidad ni de terceros."),
     ("Permisos y red",
      "El manifiesto de la app no declara ni un solo <code>uses-permission</code>, ni siquiera INTERNET. "
      "Reyon no se conecta a ningún servidor; los puzles diarios se generan sin conexión a partir de "
      "la fecha del dispositivo."),
     ("Lo que se queda en tu dispositivo",
      "Récords, el progreso del puzle diario y los ajustes viven en el almacenamiento privado de "
      "la app, al que ninguna otra app llega. Desaparecen cuando desinstalas la app o borras sus datos. "
      "Si tienes activada la copia de seguridad de Android, los ajustes pueden incluirse en la copia "
      "estándar que pertenece a tu cuenta de Google; Reyon no puede acceder a ella."),
     ("Compartir resultados",
      "El botón &quot;Compartir&quot; genera una imagen de resultado y un texto breve, y luego abre el "
      "panel de compartir de Android. La imagen se entrega en modo solo lectura a la app que elijas; "
      "Reyon no envía nada y no sabe qué se compartió."),
     ("Enlaces, menores, cambios",
      "Los enlaces de la pantalla Acerca de se abren en tu navegador, donde rige la política del sitio de "
      "destino. Como no se recopilan datos, la app es apta para todas las edades. Cualquier cambio en "
      "esta política se publicará en esta página.")],
    "Contacto", "Preguntas y avisos:")

add("pt", "Português", "Política de privacidade",
    "Reyon para Android · Em vigor em 21 de setembro de 2026 · Desenvolvedor: aripdcom",
    "Em resumo:",
    "O Reyon não coleta, guarda, compartilha nem vende nenhum dado pessoal. O app não pede permissões, "
    "não tem anúncios nem bibliotecas de rastreamento e nunca se conecta à rede. Os recordes e as "
    "configurações ficam só no seu aparelho.",
    [("Dados coletados",
      "Nenhum. Nem nomes, nem e-mails, nem localização, nem identificadores do aparelho, nem estatísticas "
      "de uso, nem relatórios de falha. O app não tem SDK de análise, publicidade ou de terceiros."),
     ("Permissões e rede",
      "O manifesto do app não declara uma única <code>uses-permission</code>, nem INTERNET. O Reyon "
      "não se conecta a nenhum servidor; os desafios diários são gerados offline a partir da data do "
      "aparelho."),
     ("O que fica no seu aparelho",
      "Recordes, o progresso do desafio diário e as configurações ficam no armazenamento "
      "privado do app, onde nenhum outro app chega. Somem quando você desinstala o app ou limpa os dados. "
      "Se o backup do Android estiver ligado, as configurações podem entrar no backup padrão que pertence "
      "à sua conta do Google; o Reyon não consegue acessá-lo."),
     ("Compartilhar resultados",
      "O botão &quot;Compartilhar&quot; gera uma imagem de resultado e um texto curto e abre a tela de "
      "compartilhamento do Android. A imagem vai somente para leitura ao app que você escolher; o "
      "Reyon não envia nada e não sabe o que foi compartilhado."),
     ("Links, crianças, mudanças",
      "Os links da tela Sobre abrem no seu navegador, onde vale a política do site de destino. Como "
      "nenhum dado é coletado, o app serve para todas as idades. Qualquer mudança nesta política será "
      "publicada nesta página.")],
    "Contato", "Dúvidas e avisos:")

add("it", "Italiano", "Informativa sulla privacy",
    "Reyon per Android · In vigore dal 21 settembre 2026 · Sviluppatore: aripdcom",
    "In breve:",
    "Reyon non raccoglie, conserva, condivide né vende alcun dato personale. L'app non chiede "
    "permessi, non contiene pubblicità né librerie di tracciamento e non si collega mai alla rete. "
    "Punteggi e impostazioni restano solo sul tuo dispositivo.",
    [("Dati raccolti",
      "Nessuno. Né nomi, né e-mail, né posizione, né identificativi del dispositivo, né statistiche "
      "d'uso, né rapporti di arresto. Nell'app non ci sono SDK di analisi, pubblicitari o di terze parti."),
     ("Permessi e rete",
      "Il manifesto dell'app non dichiara nemmeno un <code>uses-permission</code>, neppure INTERNET. "
      "Reyon non si collega ad alcun server; i rompicapi del giorno nascono offline dalla data del "
      "dispositivo."),
     ("Ciò che resta sul dispositivo",
      "Record, avanzamento del rompicapo del giorno e impostazioni stanno nella memoria privata "
      "dell'app, dove nessun'altra app arriva. Spariscono quando disinstalli l'app o ne cancelli i dati. "
      "Se il backup di Android è attivo, le impostazioni possono finire nel backup standard che "
      "appartiene al tuo account Google; Reyon non può accedervi."),
     ("Condividere i risultati",
      "Il pulsante &quot;Condividi&quot; genera un'immagine del risultato e un breve testo, poi apre il "
      "pannello di condivisione di Android. L'immagine è passata in sola lettura all'app che scegli; "
      "Reyon non invia nulla e non sa cosa è stato condiviso."),
     ("Link, bambini, modifiche",
      "I link nella schermata Informazioni si aprono nel tuo browser, dove vale l'informativa del sito di "
      "destinazione. Poiché non si raccolgono dati, l'app è adatta a ogni età. Ogni modifica a questa "
      "informativa sarà pubblicata su questa pagina.")],
    "Contatti", "Domande e segnalazioni:")

add("da", "Dansk", "Privatlivspolitik",
    "Reyon til Android · Gælder fra 21. september 2026 · Udvikler: aripdcom",
    "Kort sagt:",
    "Reyon indsamler, gemmer, deler eller sælger ingen personlige data. Appen beder ikke om "
    "tilladelser, indeholder hverken reklamer eller sporingsbiblioteker og forbinder aldrig til nettet. "
    "Rekorder og indstillinger bliver kun på din enhed.",
    [("Indsamlede data",
      "Ingen. Hverken navne, e-mails, placering, enheds-id'er, brugsstatistik eller nedbrudsrapporter. "
      "Der er hverken analyse-, reklame- eller tredjeparts-SDK'er i appen."),
     ("Tilladelser og netværk",
      "Appens manifest erklærer ikke en eneste <code>uses-permission</code>, heller ikke INTERNET. "
      "Reyon forbinder til ingen server; de daglige opgaver dannes offline ud fra enhedens dato."),
     ("Det, der bliver på enheden",
      "Rekorder, fremdrift i dagens opgave og indstillinger ligger i appens private lager, hvor "
      "ingen anden app kan nå dem. De forsvinder, når du afinstallerer appen eller rydder dens data. Er "
      "Androids sikkerhedskopiering slået til, kan indstillingerne indgå i den standardsikkerhedskopi, "
      "der hører til din Google-konto; Reyon har ikke adgang til den."),
     ("Deling af resultater",
      "Knappen &quot;Del&quot; laver et resultatbillede og en kort tekst og åbner derefter Androids "
      "deleark. Billedet gives skrivebeskyttet til den app, du vælger; Reyon sender selv intet og ved "
      "ikke, hvad der blev delt."),
     ("Links, børn, ændringer",
      "Links på Om-skærmen åbner i din browser, hvor målsidens politik gælder. Da der ikke indsamles "
      "data, er appen egnet til alle aldre. Enhver ændring af denne politik offentliggøres på denne "
      "side.")],
    "Kontakt", "Spørgsmål og henvendelser:")

add("sv", "Svenska", "Integritetspolicy",
    "Reyon för Android · Gäller från 21 september 2026 · Utvecklare: aripdcom",
    "Kort sagt:",
    "Reyon samlar inte in, lagrar, delar eller säljer några personuppgifter. Appen begär inga "
    "behörigheter, innehåller varken annonser eller spårningsbibliotek och ansluter aldrig till nätet. "
    "Rekord och inställningar stannar bara på din enhet.",
    [("Insamlade uppgifter",
      "Inga. Varken namn, e-post, plats, enhets-id, användningsstatistik eller kraschrapporter. Det finns "
      "inga analys-, annons- eller tredjeparts-SDK:er i appen."),
     ("Behörigheter och nätverk",
      "Appens manifest deklarerar inte en enda <code>uses-permission</code>, inte ens INTERNET. Reyon "
      "ansluter till ingen server; dagens pussel skapas offline utifrån enhetens datum."),
     ("Det som stannar på din enhet",
      "Rekord, framsteg i dagens pussel och inställningar ligger i appens privata lagring, dit "
      "ingen annan app når. De försvinner när du avinstallerar appen eller rensar dess data. Om Androids "
      "säkerhetskopiering är på kan inställningarna ingå i standardkopian som tillhör ditt Google-konto; "
      "Reyon kommer inte åt den."),
     ("Dela resultat",
      "Knappen &quot;Dela&quot; skapar en resultatbild och en kort text och öppnar sedan Androids "
      "delningsruta. Bilden lämnas skrivskyddad till appen du väljer; Reyon skickar ingenting själv "
      "och vet inte vad som delades."),
     ("Länkar, barn, ändringar",
      "Länkar i Om-skärmen öppnas i din webbläsare, där målsidans policy gäller. Eftersom inga uppgifter "
      "samlas in passar appen alla åldrar. Ändringar i denna policy publiceras på den här sidan.")],
    "Kontakt", "Frågor och synpunkter:")

add("nb", "Norsk bokmål", "Personvernerklæring",
    "Reyon for Android · Gjelder fra 21. september 2026 · Utvikler: aripdcom",
    "Kort sagt:",
    "Reyon samler ikke inn, lagrer, deler eller selger personopplysninger. Appen ber ikke om "
    "tillatelser, inneholder verken reklame eller sporingsbiblioteker og kobler seg aldri til nettet. "
    "Rekorder og innstillinger blir bare på enheten din.",
    [("Innsamlede data",
      "Ingen. Verken navn, e-post, posisjon, enhets-ID-er, bruksstatistikk eller krasjrapporter. Det "
      "finnes ingen analyse-, annonse- eller tredjeparts-SDK-er i appen."),
     ("Tillatelser og nett",
      "Appmanifestet erklærer ikke én eneste <code>uses-permission</code>, ikke engang INTERNETT. "
      "Reyon kobler seg til ingen server; dagens oppgaver lages offline ut fra datoen på enheten."),
     ("Det som blir igjen på enheten",
      "Rekorder, fremdrift i dagens oppgave og innstillinger ligger i appens private lager, der "
      "ingen annen app når dem. De forsvinner når du avinstallerer appen eller tømmer dataene. Er "
      "Androids sikkerhetskopiering på, kan innstillingene havne i standardkopien som hører til "
      "Google-kontoen din; Reyon har ikke tilgang til den."),
     ("Deling av resultater",
      "Knappen &quot;Del&quot; lager et resultatbilde og en kort tekst, og åpner så delingsvinduet i "
      "Android. Bildet gis skrivebeskyttet til appen du velger; Reyon sender ingenting selv og vet "
      "ikke hva som ble delt."),
     ("Lenker, barn, endringer",
      "Lenker i Om-skjermen åpnes i nettleseren din, der målsidens erklæring gjelder. Siden ingen data "
      "samles inn, passer appen for alle aldre. Enhver endring i denne erklæringen publiseres på denne "
      "siden.")],
    "Kontakt", "Spørsmål og meldinger:")

add("fi", "Suomi", "Tietosuojakäytäntö",
    "Reyon Androidille · Voimassa 21. syyskuuta 2026 alkaen · Kehittäjä: aripdcom",
    "Lyhyesti:",
    "Reyon ei kerää, säilytä, jaa eikä myy henkilötietoja. Sovellus ei pyydä käyttöoikeuksia, siinä ei "
    "ole mainoksia eikä seurantakirjastoja, eikä se ota koskaan yhteyttä verkkoon. Ennätykset ja "
    "asetukset pysyvät vain laitteessasi.",
    [("Kerätyt tiedot",
      "Ei mitään. Ei nimiä, sähköposteja, sijaintia, laitetunnisteita, käyttötilastoja eikä "
      "kaatumisraportteja. Sovelluksessa ei ole analytiikka-, mainos- eikä kolmannen osapuolen SDK:ita."),
     ("Käyttöoikeudet ja verkko",
      "Sovelluksen manifestissa ei ole yhtään <code>uses-permission</code>-riviä, ei edes INTERNET. "
      "Reyon ei ota yhteyttä yhteenkään palvelimeen; päivän pulmat syntyvät offline laitteen "
      "päivämäärästä."),
     ("Mitä laitteeseen jää",
      "Ennätykset, päivän pulman edistyminen ja asetukset ovat sovelluksen omassa "
      "tallennustilassa, johon mikään muu sovellus ei pääse. Ne katoavat, kun poistat sovelluksen tai "
      "tyhjennät sen tiedot. Jos Androidin varmuuskopiointi on päällä, asetukset voivat päätyä "
      "vakiovarmuuskopioon, joka kuuluu Google-tilillesi; Reyon ei pääse siihen käsiksi."),
     ("Tulosten jakaminen",
      "&quot;Jaa&quot;-painike luo tuloskuvan ja lyhyen tekstin ja avaa sitten Androidin jakoikkunan. "
      "Kuva annetaan vain luettavaksi valitsemallesi sovellukselle; Reyon ei lähetä itse mitään eikä "
      "tiedä, mitä jaettiin."),
     ("Linkit, lapset, muutokset",
      "Tietoja-näytön linkit avautuvat selaimessasi, jossa kohdesivuston käytäntö pätee. Koska tietoja ei "
      "kerätä, sovellus sopii kaikenikäisille. Muutokset tähän käytäntöön julkaistaan tällä sivulla.")],
    "Yhteystiedot", "Kysymykset ja ilmoitukset:")

add("ru", "Русский", "Политика конфиденциальности",
    "Reyon для Android · Действует с 21 сентября 2026 года · Разработчик: aripdcom",
    "Коротко:",
    "Reyon не собирает, не хранит, не передаёт и не продаёт персональные данные. Приложение не "
    "запрашивает разрешений, не содержит рекламы и библиотек слежения и никогда не выходит в сеть. "
    "Рекорды и настройки остаются только на вашем устройстве.",
    [("Какие данные собираются",
      "Никакие. Ни имена, ни адреса почты, ни местоположение, ни идентификаторы устройства, ни "
      "статистика использования, ни отчёты о сбоях. В приложении нет ни аналитических, ни рекламных, ни "
      "сторонних SDK."),
     ("Разрешения и сеть",
      "В манифесте приложения нет ни одной строки <code>uses-permission</code>, даже INTERNET. Reyon "
      "не подключается ни к одному серверу; ежедневные задачи создаются офлайн из даты устройства."),
     ("Что остаётся на устройстве",
      "Рекорды, прогресс ежедневной задачи и настройки лежат в личном хранилище приложения, куда "
      "не дотянется другое приложение. Они исчезают, когда вы удаляете приложение или очищаете его "
      "данные. Если включено резервное копирование Android, настройки могут попасть в стандартную копию, "
      "принадлежащую вашему аккаунту Google; Reyon не имеет к ней доступа."),
     ("Обмен результатами",
      "Кнопка &quot;Поделиться&quot; рисует картинку с результатом и короткий текст, а затем открывает "
      "окно обмена Android. Картинка передаётся только для чтения тому приложению, которое вы выберете; "
      "сам Reyon ничего не отправляет и не знает, чем поделились."),
     ("Ссылки, дети, изменения",
      "Ссылки на экране «О приложении» открываются в вашем браузере, где действует политика целевого "
      "сайта. Поскольку данные не собираются, приложение подходит для любого возраста. Любое изменение "
      "этой политики публикуется на этой странице.")],
    "Связь", "Вопросы и сообщения:")

add("ar", "العربية", "سياسة الخصوصية",
    "Reyon لأندرويد · سارية من 21 سبتمبر 2026 · المطوّر: aripdcom",
    "باختصار:",
    "لا يجمع Reyon أي بيانات شخصية ولا يخزّنها ولا يشاركها ولا يبيعها. لا يطلب التطبيق أي أذونات، ولا "
    "يحتوي إعلانات ولا مكتبات تتبّع، ولا يتصل بالشبكة أبدًا. تبقى النتائج والإعدادات على جهازك وحده.",
    [("البيانات المجموعة",
      "لا شيء. لا أسماء ولا بريد إلكتروني ولا موقع ولا معرّفات جهاز ولا إحصاءات استخدام ولا تقارير "
      "أعطال. لا توجد في التطبيق حزم تحليلات ولا إعلانات ولا حزم طرف ثالث."),
     ("الأذونات والشبكة",
      "لا يعلن بيان التطبيق أي <code>uses-permission</code> على الإطلاق، ولا حتى INTERNET. لا يتصل "
      "Reyon بأي خادم؛ تُولَّد ألغاز اليوم دون اتصال من تاريخ الجهاز."),
     ("ما يبقى على جهازك",
      "الأرقام القياسية وتقدّم لغز اليوم والإعدادات تعيش في التخزين الخاص بالتطبيق حيث لا يصل "
      "إليها تطبيق آخر. تختفي عند إزالة التطبيق أو مسح بياناته. وإذا كان النسخ الاحتياطي في أندرويد "
      "مفعّلًا فقد تُدرج الإعدادات في النسخة القياسية التي تخصّ حسابك في Google؛ ولا يستطيع Reyon "
      "الوصول إليها."),
     ("مشاركة النتائج",
      "زر &quot;مشاركة&quot; يرسم صورة للنتيجة ونصًّا قصيرًا ثم يفتح لوحة المشاركة في أندرويد. تُسلَّم "
      "الصورة للقراءة فقط إلى التطبيق الذي تختاره؛ ولا يرسل Reyon نفسه شيئًا ولا يعرف ما جرت مشاركته."),
     ("الروابط والأطفال والتغييرات",
      "تفتح روابط شاشة «حول» في متصفّحك حيث تسري سياسة الموقع المقصود. ولأن لا بيانات تُجمع فالتطبيق "
      "مناسب لكل الأعمار. وسيُنشر أي تغيير في هذه السياسة على هذه الصفحة.")],
    "التواصل", "للأسئلة والبلاغات:")


ICON = ("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'%3E%3Crect width"
        "='32' height='32' rx='7' fill='%230B0F1A'/%3E%3Cg fill='%2360A5FA'%3E%3Crect x='5.92' y='4.54' w"
        "idth='4.58' height='4.58' rx='0.6'/%3E%3Crect x='11.42' y='4.54' width='4.58' height='4.58' rx='"
        "0.6'/%3E%3Crect x='16.92' y='4.54' width='4.58' height='4.58' rx='0.6'/%3E%3Crect x='5.00' y='9."
        "13' width='22.00' height='1.83' rx='0.6'/%3E%3Crect x='5.92' y='12.79' width='4.58' height='4.58"
        "' rx='0.6'/%3E%3Crect x='16.92' y='12.79' width='4.58' height='4.58' rx='0.6'/%3E%3Crect x='22.4"
        "2' y='12.79' width='4.58' height='4.58' rx='0.6'/%3E%3Crect x='5.00' y='17.38' width='22.00' hei"
        "ght='1.83' rx='0.6'/%3E%3Crect x='5.92' y='21.04' width='4.58' height='4.58' rx='0.6'/%3E%3Crect"
        " x='11.42' y='21.04' width='4.58' height='4.58' rx='0.6'/%3E%3Crect x='16.92' y='21.04' width='4"
        ".58' height='4.58' rx='0.6'/%3E%3Crect x='22.42' y='21.04' width='4.58' height='4.58' rx='0.6'/%"
        "3E%3Crect x='5.00' y='25.62' width='22.00' height='1.83' rx='0.6'/%3E%3C/g%3E%3C/svg%3E")

STYLE = """  :root {
    color-scheme: light dark;
    --bg: #F3F6FB; --surface: #FFFFFF; --surface-2: #E8EDF5; --text: #0F1828; --muted: #5A6577; --title: #0891B2;
    --card-border: rgba(15, 24, 40, 0.06);
  }
  @media (prefers-color-scheme: dark) {
    :root { --bg: #0B0F1A; --surface: #111827; --surface-2: #1B2437; --text: #E4EAF5; --muted: #AAB4C8; --title: #4DE1FF; --card-border: rgba(255, 255, 255, 0.05); }
  }
  * { box-sizing: border-box; margin: 0; }
  body { background: var(--bg); color: var(--text); font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; line-height: 1.6; }
  .wrap { max-width: 720px; margin: 0 auto; padding: 40px 20px 64px; }
  a { color: var(--title); }
  .back { display: inline-block; text-decoration: none; font-weight: 700; margin-bottom: 20px; }
  h1 { font-size: 34px; font-weight: 900; line-height: 1.15; }
  h2 { font-size: 24px; margin-top: 8px; }
  h3 { font-size: 18px; margin-top: 22px; }
  .meta { color: var(--muted); font-size: 14px; margin-top: 6px; }
  p, li { margin-top: 10px; }
  code { background: var(--surface-2); border-radius: 5px; padding: 1px 5px; font-size: 0.92em; }
  .summary {
    margin-top: 18px; background: var(--surface); border: 1px solid var(--card-border); border-radius: 16px; padding: 16px 18px;
  }
  .summary strong { color: var(--title); }
  /* Dil şeridi: 14 bölümün tamamı tek adreste, her biri kendi çapasında. */
  .langs { margin-top: 22px; display: flex; flex-wrap: wrap; gap: 6px 10px; font-size: 15px; }
  .langs a { text-decoration: none; background: var(--surface); border: 1px solid var(--card-border); border-radius: 999px; padding: 4px 12px; }
  section { margin-top: 44px; padding-top: 12px; border-top: 1px solid var(--card-border); }
  section:first-of-type { border-top: 0; }
  section[dir="rtl"] { text-align: right; }
  footer { margin-top: 48px; color: var(--muted); font-size: 14px; display: flex; flex-wrap: wrap; gap: 6px 18px; }
  footer a { text-decoration: none; }"""


def app_tags():
    """AppLocale.TAGS: uygulamanın dilleri, kaynak sırasıyla."""
    src = open(APP_LOCALE, encoding="utf-8").read()
    block = re.search(r"val TAGS: List<String> = listOf\((.*?)\)", src, re.S)
    return re.findall(r'"([^"]+)"', block.group(1))


def section(tag):
    policy = POLICY[tag]
    rtl = ' dir="rtl"' if tag in RTL else ""
    out = [f'  <section id="{tag}" lang="{tag}"{rtl}>',
           f'    <h2>{policy["name"]} — {policy["title"]}</h2>',
           f'    <p class="meta">{policy["meta"]}</p>',
           '    <div class="summary">',
           f'      <strong>{policy["short_label"]}</strong> {policy["short"]}',
           '    </div>']
    for heading, body in policy["sections"]:
        out.append(f'    <h3>{heading}</h3>')
        out.append(f'    <p>{body}</p>')
    out.append(f'    <h3>{policy["contact"]}</h3>')
    out.append(f'    <p>{policy["contact_intro"]} <a href="mailto:{MAIL}">{MAIL}</a> · '
               f'<a href="{ISSUES}">github.com/aripdcom/reyon/issues</a></p>')
    out.append('  </section>')
    return "\n".join(out)


def page(tags):
    nav = " ".join(f'<a href="#{t}">{POLICY[t]["name"]}</a>' for t in tags)
    body = "\n".join(section(t) for t in tags)
    return f"""<!doctype html>
<html lang="tr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="theme-color" content="#F3F6FB" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#0B0F1A" media="(prefers-color-scheme: dark)">
<title>Reyon · Gizlilik politikası · Privacy policy</title>
<meta name="description" content="Reyon gizlilik politikası, 14 dilde: uygulama hiçbir veri toplamaz, hiçbir izin istemez, ağa bağlanmaz.">
<link rel="icon" href="{ICON}">
<style>
{STYLE}
</style>
</head>
<body>
<div class="wrap">
  <a class="back" href="./">← Reyon</a>
  <h1>Gizlilik politikası · Privacy policy</h1>
  <p class="meta">Aynı politika, {len(tags)} dilde. / The same policy, in {len(tags)} languages.</p>
  <nav class="langs">{nav}</nav>

{body}

  <footer>
    <span>Reyon · zero-ads, zero-permission app</span>
    <a href="./">Ana sayfa</a>
    <a href="https://github.com/aripdcom/reyon">GitHub</a>
  </footer>
</div>
</body>
</html>
"""


def main():
    tags = app_tags()
    missing = [t for t in tags if t not in POLICY]
    if missing:
        sys.exit(f"politika metni olmayan dil: {', '.join(missing)}")
    extra = [t for t in POLICY if t not in tags]
    if extra:
        sys.exit(f"AppLocale.TAGS içinde olmayan dil: {', '.join(extra)}")
    open(OUT, "w", encoding="utf-8").write(page(tags))
    print(f"{OUT}: {len(tags)} dil ({' '.join(tags)})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
