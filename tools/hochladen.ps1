# AleHead nach GitHub hochladen (wird von "Hochladen.bat" gestartet)
$ErrorActionPreference = 'Continue'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$repoName = 'AleHead'

function Fertig($code) {
    Write-Host ''
    Read-Host 'Enter druecken zum Schliessen'
    exit $code
}

Write-Host '=== AleHead -> GitHub ===' -ForegroundColor Cyan

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Host 'Git ist nicht installiert.' -ForegroundColor Red
    Write-Host 'Installieren mit:   winget install Git.Git   (danach dieses Fenster neu starten)'
    Fertig 1
}

if (-not (Test-Path (Join-Path $root '.git'))) {
    git init -b main | Out-Null
    Write-Host 'Neues Git-Repository angelegt.'
}

# Name/E-Mail fuer Commits
if (-not (git config user.email)) {
    $mail = Read-Host 'Deine GitHub-E-Mail-Adresse'
    git config user.email $mail
}
if (-not (git config user.name)) {
    $name = Read-Host 'Dein Name fuer Commits'
    git config user.name $name
}

# Remote einrichten
$remote = git remote get-url origin 2>$null
if (-not $remote) {
    $gh = Get-Command gh -ErrorAction SilentlyContinue
    $ghOk = $false
    if ($gh) { gh auth status *> $null; $ghOk = ($LASTEXITCODE -eq 0) }

    if ($ghOk) {
        Write-Host "Lege privates Repository '$repoName' auf GitHub an ..."
        gh repo create $repoName --private --source . --remote origin
        if ($LASTEXITCODE -ne 0) {
            Write-Host 'Konnte das Repository nicht anlegen (existiert es schon?).' -ForegroundColor Yellow
            $user = gh api user --jq .login
            git remote add origin "https://github.com/$user/$repoName.git"
        }
    } else {
        $user = Read-Host 'GitHub-Benutzername [AleB-Roni]'
        if (-not $user) { $user = 'AleB-Roni' }
        Write-Host ''
        Write-Host "Bitte lege jetzt auf GitHub ein LEERES Repository '$repoName' an:" -ForegroundColor Yellow
        Write-Host '   https://github.com/new   (ohne README, privat ist okay)'
        Start-Process 'https://github.com/new'
        Read-Host 'Wenn erledigt: Enter druecken'
        git remote add origin "https://github.com/$user/$repoName.git"
    }
    $remote = git remote get-url origin
}

# Commit + Push
git add -A
git diff --cached --quiet
if ($LASTEXITCODE -ne 0) {
    git commit -m ("Update " + (Get-Date -Format 'yyyy-MM-dd HH:mm')) | Out-Null
    Write-Host 'Aenderungen committet.'
} else {
    Write-Host 'Keine neuen Aenderungen - pushe trotzdem.'
}

# Erst Aenderungen von GitHub holen (z. B. wenn Claude direkt gepusht hat)
git pull --rebase origin main
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Konnte Aenderungen von GitHub nicht zusammenfuehren. Bitte melde dich bei Claude.' -ForegroundColor Red
    Fertig 1
}

git push -u origin main
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Push fehlgeschlagen. Beim ersten Mal oeffnet sich evtl. ein GitHub-Login-Fenster - danach nochmal starten.' -ForegroundColor Red
    Fertig 1
}

$web = $remote -replace '\.git$', ''
Write-Host ''
Write-Host 'Hochgeladen! GitHub baut die Apps jetzt (dauert ca. 3-5 Minuten):' -ForegroundColor Green
Write-Host "   $web/actions"
Write-Host 'Danach "Installieren.bat" starten.'
Start-Process "$web/actions"
Fertig 0
