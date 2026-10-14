# Minifig Scanner

Scannt den Data-Matrix-Code auf der Unterseite von LEGO-Minifiguren-Schachteln (Serie 25 bis Shrek 71053) und zeigt das Bild der Figur. Eine einzige Datei (`index.html`), läuft komplett im Browser.

## Als App auf Android nutzen

1. Im Repository unter **Settings → Pages** als Quelle **Deploy from a branch**, Branch `main`, Ordner `/ (root)` wählen.
2. Die angezeigte Adresse in Chrome auf dem Handy öffnen und den Kamerazugriff erlauben.
3. Im Chrome-Menü **Zum Startbildschirm hinzufügen** wählen.

Über HTTPS startet der Live-Scan automatisch: Code in den Rahmen halten, die Figur erscheint ohne Auslösen.

## Als APK installieren

Bei jedem Push baut GitHub Actions eine APK und legt sie unter **Releases → latest** ab:
<https://github.com/mikewa1981-hub/Collectibles-scan/releases/latest/download/MinifigScanner.apk>

Am Handy herunterladen, öffnen und die Installation aus dieser Quelle einmal erlauben. Die APK ist mit dem Test-Schlüssel `app/debug.keystore` signiert und nicht für den Play Store gedacht. In der App läuft die Kamera dauerhaft im oberen Teil; jede neue Schachtel im Rahmen wechselt das angezeigte Ergebnis. Tippen aufs Kamerabild schaltet die Taschenlampe.

## Quellen

Code-Listen und Figurenbilder stammen aus [L-Scan](https://github.com/le0pard/lego-scanner) (MIT-Lizenz, siehe `THIRD_PARTY_LICENSE_L-Scan.txt`). Die Codes kommen aus der Sammler-Community und sind nicht von LEGO bestätigt. Inoffizielles Werkzeug ohne Verbindung zur LEGO Gruppe.
