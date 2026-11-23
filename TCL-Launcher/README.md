# BroZam TV – bezpečný prototyp launcheru pro TCL 65C6K

Projekt Android Studio (Java, minSdk 26). Úvodní obrazovka běží v lokálním WebView. Soubor `app/src/main/assets/index.html` lze samostatně otevřít v prohlížeči PC pro kontrolu designu (v prohlížeči jsou některé televizní akce simulované).

## Funkce
- živé hodiny a české datum, fotka na pozadí (`assets/bg.jpg`);
- dlaždice aplikací ukazují skutečné bannery aplikací z TV (načítá je `MainActivity.loadAppImages`); nenainstalované aplikace jsou ztlumené;
- aktuální počasí a třídenní předpověď pro Ostrožskou Lhotu přes Open-Meteo, bez API klíče;
- spouštění známých aplikací, pokud jsou v TV nainstalované;
- vlastní jednoduchý webový prohlížeč s běžným stahováním do Downloads;
- přidávání vlastních webových záložek, ukládají se lokálně;
- systémový výběr souborů;
- LocalSend (spuštění nainstalované aplikace) a Disk Google (otevření webu).

## Sestavení
1. Nainstalovat Android Studio na Windows.
2. Otevřít adresář `TCL-Launcher` jako projekt (File > Open), nechat stáhnout Android SDK a Gradle.
3. Build > Build APK(s).
4. Po dokončení získat `app/build/outputs/apk/debug/app-debug.apk`.
5. V TV povolit vývojářské možnosti a ADB, potom nainstalovat přes `adb install -r app-debug.apk` nebo přenést APK jinou cestou.
6. Spustit `BroZam TV` ze seznamu aplikací a prověřit ovládání TV ovladačem / klávesnicí.

## Bezpečnost a omezení
- Aplikace úmyslně NEDEFINUJE systémovou kategorii HOME, takže automaticky nepřebírá tlačítko Home.
- Nemaže ani nedeaktivuje Google TV nebo systémové aplikace.
- Zkušební verze neumí přepnout HDMI sama, protože vstupy TCL nejsou běžně ovladatelné přes veřejné API třetích stran.
- Miracast je odkaz na použití systému TCL, ne úplná implementace zrcadlení.
- Google Disk pouze otevře webovou verzi; nahrávání z TV může vyžadovat další integraci, teď není zaručeno.
- LocalSend je samostatná aplikace, bez její instalace tlačítko přenos neprovede.
- ČT package ID se může lišit; pokud nespustí iVysílání, ověří se reálný balíček ADB.
- Prohlížeč je jednoduchý WebView. Mnohé komplexní stránky, DRM videa, přihlášení nebo stahování vyžadují další práci.
- Počasí vyžaduje internet a správný čas televize. Souřadnice jsou přibližné pro obec, ne adresa.
- Před vydáním na reálné TV je potřeba ověřit instalaci, package names, stahování, systémový výběr souborů a ADB rollback.

## Návrat
Odinstalovat `BroZam TV` v Nastavení > Aplikace. Původní launcher nebyl změněn.

## Automatické sestavení APK přes GitHub Actions (bez Android Studia)
Projekt nově obsahuje `.github/workflows/build-apk.yml`.
1. Nahraj **obsah** složky `TCL-Launcher` do vlastního nového GitHub repozitáře (kořen repozitáře musí obsahovat `settings.gradle`, `app/` a `.github/`).
2. GitHub > Actions > **Sestavit BroZam TV APK** > Run workflow.
3. Po úspěšném doběhnutí stáhni v konkrétním běhu artifact `BroZam-TV-APK`.
4. Rozbal ZIP s artifactem. Soubor `app-debug.apk` je testovací instalační balíček.
5. Instalaci proveď nejprve jako běžnou aplikaci, bez převzetí tlačítka Home.

Poznámka: sestavení nebylo v tomto prostředí spuštěno; chybí Android SDK/Gradle i síťový přístup k jejich stažení. Výsledek GitHub Actions je nutné zkontrolovat; úspěch není zaručen.
