# AleHead - Werkzeugkasten (wird von "AleHead.bat" gestartet)
# Hinweis: absichtlich ohne Umlaute, damit Windows PowerShell 5 die Datei korrekt liest.
$ErrorActionPreference = 'Continue'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$apkDir   = Join-Path $root 'apks'
$addrFile = Join-Path $PSScriptRoot 'uhr-adresse.txt'
$shotDir  = Join-Path $root 'screenshots'
$pkg      = 'de.roni.alehead'
$admin    = 'de.roni.alehead/.AdminReceiver'
$script:adb = $null
$script:watch = $null

# ---------------------------------------------------------------- Helfer

function Info($t)  { Write-Host $t }
function Ok($t)    { Write-Host $t -ForegroundColor Green }
function Warn($t)  { Write-Host $t -ForegroundColor Yellow }
function Fehler($t){ Write-Host $t -ForegroundColor Red }
function Pause-Menu { Write-Host ''; Read-Host 'Enter = zurueck zum Menue' | Out-Null }

function Have($cmd) { [bool](Get-Command $cmd -ErrorAction SilentlyContinue) }

function Get-Repo {
    $r = git remote get-url origin 2>$null
    if (-not $r) { return $null }
    $r = $r -replace '\.git$', '' -replace '^https://github.com/', '' -replace '^git@github.com:', ''
    return $r
}

function Get-Adb {
    if ($script:adb) { return $script:adb }
    $found = (Get-Command adb -ErrorAction SilentlyContinue).Source
    if (-not $found) {
        $local = Join-Path $root 'platform-tools\adb.exe'
        if (-not (Test-Path $local)) {
            Info 'adb nicht gefunden - lade die offiziellen Android Platform-Tools von Google ...'
            $zip = Join-Path $env:TEMP 'platform-tools.zip'
            Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip -UseBasicParsing
            Expand-Archive $zip -DestinationPath $root -Force
            Remove-Item $zip
        }
        $found = $local
    }
    $script:adb = $found
    return $found
}

function Get-Apks {
    New-Item -ItemType Directory -Force -Path $apkDir | Out-Null
    $repo = Get-Repo
    if (-not $repo) { Fehler 'Kein GitHub-Repository eingerichtet - zuerst "Hochladen" ausfuehren.'; return $false }
    Info "Lade neueste APKs von github.com/$repo ..."
    $ok = $true
    foreach ($n in 'AleHead-Handy.apk', 'AleHead-Uhr.apk') {
        try {
            Invoke-WebRequest "https://github.com/$repo/releases/latest/download/$n" -OutFile (Join-Path $apkDir $n) -UseBasicParsing
        } catch {
            if (Have 'gh') {
                gh release download latest --repo $repo --dir $apkDir --clobber --pattern $n
                if ($LASTEXITCODE -ne 0) { $ok = $false }
            } else { $ok = $false }
        }
    }
    if ($ok) { Ok 'APKs heruntergeladen.' } else { Fehler 'Download fehlgeschlagen - ist das Repository oeffentlich und der Build fertig?' }
    return $ok
}

function Connect-Watch {
    if ($script:watch) {
        $state = & (Get-Adb) -s $script:watch get-state 2>$null
        if ($state -eq 'device') { return $script:watch }
    }
    $adbExe = Get-Adb
    $last = if (Test-Path $addrFile) { (Get-Content $addrFile -Raw).Trim() } else { '' }
    Info ''
    Info 'Uhr: Einstellungen > Entwickleroptionen > ADB-Debugging + Debugging ueber WLAN an.'
    $addr = Read-Host "Adresse der Uhr (z. B. 192.168.178.50:5555) [$last]"
    if (-not $addr) { $addr = $last }
    if (-not $addr) { Fehler 'Keine Adresse angegeben.'; return $null }
    if ($addr -notmatch ':') { $addr = "$addr`:5555" }
    Set-Content $addrFile $addr

    & $adbExe connect $addr | Out-Host
    $state = & $adbExe -s $addr get-state 2>$null
    if ($state -ne 'device') {
        Warn 'Nicht verbunden. Neuere Uhren brauchen eine Kopplung ("Kabelloses Debugging" > "Neues Geraet koppeln").'
        $code = Read-Host 'Kopplungscode (leer = abbrechen)'
        if (-not $code) { return $null }
        $pairAddr = Read-Host 'IP:Port fuer die KOPPLUNG (steht neben dem Code)'
        & $adbExe pair $pairAddr $code | Out-Host
        & $adbExe connect $addr | Out-Host
        Start-Sleep -Seconds 2
        $state = & $adbExe -s $addr get-state 2>$null
    }
    if ($state -ne 'device') {
        Fehler 'Uhr nicht erreichbar. Gleiches WLAN? Auf der Uhr "Immer erlauben" angetippt?'
        return $null
    }
    Ok "Uhr verbunden ($addr)."
    $script:watch = $addr
    return $addr
}

function Wait-Phone {
    $adbExe = Get-Adb
    Info 'Handy per USB anschliessen, USB-Debugging an, Abfrage am Handy erlauben ...'
    & $adbExe wait-for-usb-device
}

# ---------------------------------------------------------------- Aktionen

function Do-Update {
    Info 'Hole neueste Version von GitHub ...'
    git pull --rebase origin main
    if ($LASTEXITCODE -eq 0) { Ok 'Projekt ist aktuell.' }
    else { Fehler 'Aktualisieren fehlgeschlagen. Melde dich bei Claude und schick die Meldung oben.' }
}

function Do-Upload {
    if (-not (Have 'git')) { Fehler 'Git fehlt. Installieren mit:  winget install Git.Git'; return $false }
    & (Join-Path $PSScriptRoot 'hochladen.ps1') -NoPause
    return ($LASTEXITCODE -eq 0)
}

function Wait-Build {
    $repo = Get-Repo
    if (-not $repo) { return $false }
    $sha = (git rev-parse HEAD).Trim()
    Info "Warte auf den GitHub-Build fuer $($sha.Substring(0,7)) (meist 2-4 Minuten) ..."
    $deadline = (Get-Date).AddMinutes(15)
    while ((Get-Date) -lt $deadline) {
        try {
            $runs = Invoke-RestMethod "https://api.github.com/repos/$repo/actions/runs?per_page=10" -UseBasicParsing
            $run = $runs.workflow_runs | Where-Object { $_.head_sha -eq $sha } | Select-Object -First 1
            if ($run) {
                if ($run.status -eq 'completed') {
                    if ($run.conclusion -eq 'success') { Ok 'Build erfolgreich.'; Start-Sleep -Seconds 5; return $true }
                    Fehler "Build fehlgeschlagen ($($run.conclusion)). Details: $($run.html_url)"
                    Start-Process $run.html_url
                    return $false
                }
                Write-Host "  ... laeuft ($($run.status))" -ForegroundColor DarkGray
            } else {
                Write-Host '  ... Build noch nicht gestartet' -ForegroundColor DarkGray
            }
        } catch {
            Write-Host '  ... GitHub nicht erreichbar, versuche erneut' -ForegroundColor DarkGray
        }
        Start-Sleep -Seconds 15
    }
    Fehler 'Zeitueberschreitung beim Warten auf den Build.'
    return $false
}

function Install-Phone {
    $apk = Join-Path $apkDir 'AleHead-Handy.apk'
    if (-not (Test-Path $apk)) { if (-not (Get-Apks)) { return } }
    Wait-Phone
    & (Get-Adb) -d install -r $apk
    if ($LASTEXITCODE -eq 0) { Ok 'Handy-App installiert.' } else { Fehler 'Installation auf dem Handy fehlgeschlagen.' }
}

function Install-Watch {
    $apk = Join-Path $apkDir 'AleHead-Uhr.apk'
    if (-not (Test-Path $apk)) { if (-not (Get-Apks)) { return } }
    $w = Connect-Watch
    if (-not $w) { return }
    $adbExe = Get-Adb
    & $adbExe -s $w install -r $apk
    if ($LASTEXITCODE -ne 0) { Fehler 'Installation auf der Uhr fehlgeschlagen.'; return }
    Ok 'Uhr-App installiert.'
    Enable-ScreenOff $w
}

function Enable-ScreenOff($w) {
    $out = & (Get-Adb) -s $w shell dpm set-active-admin $admin 2>&1
    $list = & (Get-Adb) -s $w shell dumpsys device_policy 2>&1 | Out-String
    if ($list -match 'AdminReceiver') { Ok 'Sofortige Display-Abschaltung ist aktiv.' }
    else { Warn "Sofortige Display-Abschaltung nicht moeglich ($out). Display geht dann nach der normalen Zeitueberschreitung aus." }
}

function Do-All {
    if (-not (Do-Upload)) { return }
    if (-not (Wait-Build)) { return }
    if (-not (Get-Apks)) { return }
    $w = Read-Host 'Was installieren? [1] Handy  [2] Uhr  [3] Beides (Standard)'
    if ($w -eq '1' -or $w -eq '3' -or -not $w) { Install-Phone }
    if ($w -eq '2' -or $w -eq '3' -or -not $w) { Install-Watch }
}

function Do-Screenshot {
    $w = Connect-Watch
    if (-not $w) { return }
    New-Item -ItemType Directory -Force -Path $shotDir | Out-Null
    $file = Join-Path $shotDir ('uhr-' + (Get-Date -Format 'yyyy-MM-dd_HH-mm-ss') + '.png')
    $adbExe = Get-Adb
    & $adbExe -s $w shell screencap -p /sdcard/alehead-shot.png
    & $adbExe -s $w pull /sdcard/alehead-shot.png $file | Out-Null
    & $adbExe -s $w shell rm /sdcard/alehead-shot.png
    if (Test-Path $file) { Ok "Gespeichert: $file"; Start-Process $file } else { Fehler 'Screenshot fehlgeschlagen.' }
}

function Do-AlwaysOn {
    $w = Connect-Watch
    if (-not $w) { return }
    Info 'Oeffne die Display-Einstellungen auf der Uhr ...'
    & (Get-Adb) -s $w shell am start -a android.settings.DISPLAY_SETTINGS | Out-Null
    Warn 'Auf der Uhr jetzt "Display immer an" (Always-on) ausschalten.'
}

function Do-TestWatch {
    $w = Connect-Watch
    if (-not $w) { return }
    & (Get-Adb) -s $w shell am start -n "$pkg/.HudActivity" | Out-Host
    Info 'Die Uhr-App ist offen. Am Handy in AleHead "Test an Uhr" antippen.'
}

function Do-Uninstall {
    Warn 'Entfernt AleHead von Uhr und/oder Handy.'
    $w = Read-Host '[1] Handy  [2] Uhr  [3] Beides  (leer = abbrechen)'
    if (-not $w) { return }
    if ($w -eq '2' -or $w -eq '3') {
        $addr = Connect-Watch
        if ($addr) {
            & (Get-Adb) -s $addr shell dpm remove-active-admin $admin 2>&1 | Out-Null
            & (Get-Adb) -s $addr uninstall $pkg
            if ($LASTEXITCODE -eq 0) { Ok 'Von der Uhr entfernt.' } else { Fehler 'Entfernen von der Uhr fehlgeschlagen.' }
        }
    }
    if ($w -eq '1' -or $w -eq '3') {
        Wait-Phone
        & (Get-Adb) -d uninstall $pkg
        if ($LASTEXITCODE -eq 0) { Ok 'Vom Handy entfernt.' } else { Fehler 'Entfernen vom Handy fehlgeschlagen.' }
    }
}

# ---------------------------------------------------------------- Menue

while ($true) {
    Clear-Host
    $ver = (Select-String -Path (Join-Path $root 'mobile\build.gradle.kts') -Pattern 'versionName = "(.+)"' -ErrorAction SilentlyContinue).Matches.Groups[1].Value
    Write-Host "=========================================" -ForegroundColor Cyan
    Write-Host "  AleHead $ver  -  Werkzeugkasten" -ForegroundColor Cyan
    Write-Host "=========================================" -ForegroundColor Cyan
    Write-Host ''
    Write-Host '  [1] ALLES: hochladen, Build abwarten, installieren'
    Write-Host ''
    Write-Host '  [2] Neueste Version von GitHub holen'
    Write-Host '  [3] Nur hochladen (GitHub baut die Apps)'
    Write-Host '  [4] Nur installieren: Handy (USB)'
    Write-Host '  [5] Nur installieren: Uhr (WLAN)'
    Write-Host ''
    Write-Host '  [6] Screenshot von der Uhr'
    Write-Host '  [7] Uhr-App starten (zum Testen)'
    Write-Host '  [8] "Display immer an" auf der Uhr ausschalten'
    Write-Host '  [9] Sofortige Display-Abschaltung erneut freischalten'
    Write-Host ''
    Write-Host '  [D] Deinstallieren'
    Write-Host '  [0] Beenden'
    Write-Host ''
    $c = (Read-Host 'Auswahl').Trim().ToUpper()
    Write-Host ''
    switch ($c) {
        '1' { Do-All; Pause-Menu }
        '2' { Do-Update; Pause-Menu }
        '3' { Do-Upload | Out-Null; Pause-Menu }
        '4' { if (Get-Apks) { Install-Phone }; Pause-Menu }
        '5' { if (Get-Apks) { Install-Watch }; Pause-Menu }
        '6' { Do-Screenshot; Pause-Menu }
        '7' { Do-TestWatch; Pause-Menu }
        '8' { Do-AlwaysOn; Pause-Menu }
        '9' { $w = Connect-Watch; if ($w) { Enable-ScreenOff $w }; Pause-Menu }
        'D' { Do-Uninstall; Pause-Menu }
        '0' { exit 0 }
        default { }
    }
}
