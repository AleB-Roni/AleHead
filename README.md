# AleHead

Minimalistisches Auto-HUD auf einer alten Wear-OS-Uhr: Abbiege-Pfeil und Distanz aus Google Maps, Straße, aktuelles Tempo und das Tempolimit aus OpenStreetMap – während Android Auto ganz normal weiterläuft.

```
Google Maps (Handy) ──Benachrichtigung──┐
GPS (Handy) ──Tempo────────────────────┼─► AleHead-Handy-App ──Wear Data Layer──► AleHead-Uhr-App
OpenStreetMap ──Tempolimit─────────────┘
```

## Einmalige Einrichtung

**Uhr (Fossil)**
1. Einstellungen → System → Info → 7× auf „Build-Nummer" tippen → Entwickleroptionen sind an.
2. Entwickleroptionen → **ADB-Debugging** an, dann **Debugging über WLAN** an. Die Uhr muss im selben WLAN wie der PC sein. Notiere die angezeigte Adresse (z. B. `192.168.178.50:5555`).

**Handy (S24 Ultra)**
1. Einstellungen → Telefoninfo → Softwareinformationen → 7× auf „Buildnummer" → Entwickleroptionen.
2. Entwickleroptionen → **USB-Debugging** an.

**Windows-PC**
- Git installieren: `winget install Git.Git`
- Empfohlen: GitHub CLI `winget install GitHub.cli`, danach einmal `gh auth login`. Dann legen die Skripte das Repo selbst an und laden die APKs automatisch herunter.

## Bauen & installieren

**`AleHead.bat`** doppelklicken → Menü:

| Taste | Funktion |
|---|---|
| **1** | **Alles:** hochladen, auf den GitHub-Build warten, Handy und/oder Uhr installieren |
| 2 | Neueste Version von GitHub holen (z. B. nachdem Claude etwas geändert hat) |
| 3 | Nur hochladen |
| 4 / 5 | Nur installieren: Handy (USB) / Uhr (WLAN) |
| 6 | Screenshot von der Uhr (landet im Ordner `screenshots`) |
| 7 | Uhr-App starten (zum Testen) |
| 8 | „Display immer an“ auf der Uhr ausschalten (öffnet die Einstellung auf der Uhr) |
| 9 | Sofortige Display-Abschaltung erneut freischalten |
| D | Deinstallieren (entfernt vorher automatisch das Admin-Recht auf der Uhr) |

adb wird bei Bedarf automatisch von Google heruntergeladen, die Adresse der Uhr wird gespeichert.

## Erster Start

1. Handy-App **AleHead** öffnen. Die Karte **Einrichtung** zeigt, was noch fehlt – bei jedem Punkt auf „Erlauben" tippen:
   Benachrichtigungszugriff, Standort, Standort „Immer erlauben", Bluetooth, Akku nicht einschränken.
   Sobald alles erledigt ist, blendet sich die Karte aus.
2. Unter **Automatik** die **Head Unit** auswählen (das Bluetooth-Gerät deiner Android-Auto-Box).
3. **„Test an Uhr"** antippen → die Uhr-App öffnet sich und zeigt 15 s lang eine Fake-Navigation.

## Automatik: läuft nur mit Android Auto

- Verbindet sich die Head Unit per Bluetooth, startet AleHead im Hintergrund und wartet bis zu 2 Minuten auf Android Auto.
- Sobald Android Auto verbunden ist: GPS, Tempolimit und Maps-Auslesen starten, die Uhr-App öffnet sich von selbst.
- Wird Android Auto getrennt: alles stoppt, das Maps-Auslesen wird vom System komplett entbunden, die Uhr-App blendet sich aus und schließt sich.
- Kommt nach 2 Minuten kein Android Auto (z. B. nur Kopfhörer verbunden), beendet sich der Dienst wieder.
- Ohne Fahrt läuft also nichts – kein GPS, kein Dienst, keine Benachrichtigung.
- Zum Testen ohne Auto gibt es weiterhin **„Jetzt starten"**.

### Display der Uhr nur bei laufendem Auto

- **Fahrtbeginn:** Die Uhr-App öffnet sich und schaltet das Display ein, auch wenn die Uhr gerade aus war.
- **Android Auto getrennt:** Die Anzeige blendet sich aus, die App schließt sich, das Display geht aus.
- **Handy außer Reichweite** (Bluetooth-Verbindung weg): nach ca. 10 s aus.
- **Keine Daten mehr vom Handy** (z. B. Handy-Akku leer, App beendet): nach 20 s aus.
- Die Uhr-Installation in `AleHead.bat` schaltet AleHead als **Geräteadministrator** frei, nur mit dem Recht „Display sperren“. Dann geht das Display sofort aus. Klappt das bei deiner Uhr nicht, geht es nach der normalen Display-Zeitüberschreitung aus.
- Auf der Uhr unter Einstellungen → Display **„Display immer an“ ausschalten**, sonst bleibt danach das gedimmte Zifferblatt sichtbar.
- Uhr-App deinstallieren: in `AleHead.bat` Taste **D** (entfernt das Admin-Recht automatisch).

## Der wichtigste Test: Funktioniert es mit Android Auto?

Unklar ist, ob Google Maps die Navigations-Benachrichtigung auch postet, während es auf Android Auto läuft. Prüfen:
1. Mit dem Auto verbinden, Navigation in Android Auto starten.
2. Am Handy AleHead öffnen → Bereich **„Debug"** ansehen.

Steht dort eine aktuelle Uhrzeit mit `title`/`text` von Maps, klappt alles. Bleibt es leer, kommen die Daten bei Android Auto nicht über die Benachrichtigung – dann schick mir den Debug-Text, und wir gehen über die Head Unit.

## Hinweise

- **Tempolimit** kommt aus OpenStreetMap (Overpass-API, ca. alle 150 m bzw. 30 s). Baustellen und Wechselverkehrszeichen fehlen dort. Schild mit Streifen = kein Limit.
- **Spiegel-Modus** (Handy-App) spiegelt die Anzeige für die Reflexion in der Scheibe. Ohne Reflexionsfolie gibt es ein Doppelbild.
- Die Uhr-App hält das Display an und auf voller Helligkeit. Wegwischen ist deaktiviert – zum Beenden die Seitentaste drücken.
- **Akku/Hitze:** Die Uhr im Sommer nicht in der prallen Sonne hinter der Scheibe liegen lassen.
- Handy- und Uhr-App müssen mit demselben Schlüssel (`alehead.keystore`) signiert sein – sonst finden sie sich nicht. Die Datei nicht löschen.

## Projektaufbau

| Pfad | Inhalt |
|---|---|
| `mobile/` | Handy-App: `BtReceiver` (Bluetooth-Trigger), `HudService` (Android-Auto-Erkennung, GPS, Tempolimit), `NavListenerService` (Maps auslesen), `WatchLink` (Senden an Uhr), `MainActivity`/`Ui.kt`/`HudPreview.kt` (Oberfläche) |
| `wear/` | Uhr-App: `HudActivity` (Empfang), `HudView` (animierte Anzeige), `OpenListenerService` (öffnet die App bei Fahrtbeginn), `AdminReceiver` (Display aus) |
| `.github/workflows/build.yml` | Baut beide APKs und veröffentlicht sie als Release „latest" |
| `AleHead.bat` → `tools/alehead.ps1` | Menü für Windows (hochladen, installieren, Screenshot, Deinstallieren …) |
