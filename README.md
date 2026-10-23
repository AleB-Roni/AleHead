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

1. **`Hochladen.bat`** doppelklicken → lädt das Projekt nach GitHub, GitHub Actions baut beide Apps (3–5 Minuten).
2. **`Installieren.bat`** doppelklicken → lädt die APKs herunter, installiert die Handy-App per USB und die Uhr-App per WLAN. adb wird bei Bedarf automatisch heruntergeladen.

Nach Code-Änderungen einfach wieder 1. und 2.

## Erster Start

1. Handy-App **AleHead** öffnen → „Benachrichtigungszugriff erlauben" → AleHead aktivieren.
2. „Standort & Benachrichtigungen erlauben".
3. Uhr-App **AleHead** öffnen.
4. Am Handy **„Testanzeige an Uhr senden"** → auf der Uhr erscheint 15 s lang eine Fake-Navigation. Funktioniert das, steht die Verbindung.
5. **„HUD starten"** → ab jetzt Tempo + Tempolimit. Navigation in Google Maps starten → Pfeil und Distanz erscheinen.

**Samsung-Tipp:** Einstellungen → Apps → AleHead → Akku → **„Nicht eingeschränkt"**, sonst beendet One UI den Dienst im Hintergrund.

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
| `mobile/` | Handy-App: `NavListenerService` (Maps auslesen), `HudService` (GPS + Tempolimit), `WatchLink` (Senden an Uhr), `MainActivity` (Einrichtung + Debug) |
| `wear/` | Uhr-App: `HudActivity` (Empfang), `HudView` (Zeichnen) |
| `.github/workflows/build.yml` | Baut beide APKs und veröffentlicht sie als Release „latest" |
| `Hochladen.bat`, `Installieren.bat` | Doppelklick-Skripte für Windows |
