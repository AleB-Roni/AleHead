# AleHead auf Handy und Uhr installieren (wird von "Installieren.bat" gestartet)
$ErrorActionPreference = 'Continue'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$apkDir = Join-Path $root 'apks'
$addrFile = Join-Path $PSScriptRoot 'uhr-adresse.txt'
New-Item -ItemType Directory -Force -Path $apkDir | Out-Null

function Fertig($code) {
    Write-Host ''
    Read-Host 'Enter druecken zum Schliessen'
    exit $code
}

Write-Host '=== AleHead installieren ===' -ForegroundColor Cyan

# --- adb finden oder herunterladen ---
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) {
    $local = Join-Path $root 'platform-tools\adb.exe'
    if (-not (Test-Path $local)) {
        Write-Host 'adb nicht gefunden - lade die offiziellen Android Platform-Tools von Google herunter ...'
        $zip = Join-Path $env:TEMP 'platform-tools.zip'
        Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip
        Expand-Archive $zip -DestinationPath $root -Force
        Remove-Item $zip
    }
    $adb = $local
}

# --- APKs besorgen ---
$remote = git remote get-url origin 2>$null
$gh = Get-Command gh -ErrorAction SilentlyContinue
if ($gh -and $remote) {
    $repo = ($remote -replace '^https://github.com/', '' -replace '\.git$', '')
    Write-Host "Lade neueste APKs aus $repo ..."
    gh release download latest --repo $repo --dir $apkDir --clobber --pattern '*.apk'
}
$phoneApk = Join-Path $apkDir 'AleHead-Handy.apk'
$watchApk = Join-Path $apkDir 'AleHead-Uhr.apk'
if (-not ((Test-Path $phoneApk) -and (Test-Path $watchApk))) {
    Write-Host ''
    Write-Host 'APKs nicht gefunden.' -ForegroundColor Yellow
    if ($remote) { Write-Host ('Lade sie hier herunter:  ' + ($remote -replace '\.git$', '') + '/releases/latest') }
    Write-Host "und lege AleHead-Handy.apk + AleHead-Uhr.apk in diesen Ordner:  $apkDir"
    Start-Process $apkDir
    Read-Host 'Wenn erledigt: Enter druecken'
}

Write-Host ''
Write-Host '[1] Handy (per USB-Kabel)'
Write-Host '[2] Uhr (per WLAN)'
Write-Host '[3] Beides'
$wahl = Read-Host 'Auswahl'

if ($wahl -eq '1' -or $wahl -eq '3') {
    Write-Host ''
    Write-Host 'Handy per USB anschliessen, USB-Debugging an, Abfrage am Handy erlauben ...'
    & $adb wait-for-usb-device
    & $adb -d install -r $phoneApk
    if ($LASTEXITCODE -eq 0) { Write-Host 'Handy-App installiert.' -ForegroundColor Green }
    else { Write-Host 'Installation auf dem Handy fehlgeschlagen.' -ForegroundColor Red }
}

if ($wahl -eq '2' -or $wahl -eq '3') {
    Write-Host ''
    $last = if (Test-Path $addrFile) { (Get-Content $addrFile -Raw).Trim() } else { '' }
    $addr = Read-Host "Adresse der Uhr aus den Entwickleroptionen (z. B. 192.168.178.50:5555) [$last]"
    if (-not $addr) { $addr = $last }
    if ($addr -notmatch ':') { $addr = "$addr`:5555" }
    Set-Content $addrFile $addr

    $code = Read-Host 'Kopplungscode (nur bei "Kabelloses Debugging" mit Kopplung, sonst leer lassen)'
    if ($code) {
        $pairAddr = Read-Host 'IP:Port fuer die KOPPLUNG (wird neben dem Code angezeigt)'
        & $adb pair $pairAddr $code
    }

    & $adb connect $addr
    Write-Host 'Falls auf der Uhr eine Abfrage erscheint: "Immer erlauben" antippen.'
    Start-Sleep -Seconds 3
    & $adb -s $addr install -r $watchApk
    if ($LASTEXITCODE -eq 0) { Write-Host 'Uhr-App installiert.' -ForegroundColor Green }
    else { Write-Host 'Installation auf der Uhr fehlgeschlagen - Adresse und ADB-Debugging pruefen.' -ForegroundColor Red }
    & $adb disconnect $addr | Out-Null
}

Fertig 0
