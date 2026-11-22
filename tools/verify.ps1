param([switch]$Sable)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$cacheRoot = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
$userdevRoot = Join-Path $cacheRoot 'caches/modules-2/files-2.1/net.neoforged/neoforge/21.1.248'
$userdev = Get-ChildItem -LiteralPath $userdevRoot -Recurse -Filter '*-userdev.jar' -File | Select-Object -First 1
if (-not $userdev) { throw 'Cached NeoForge 21.1.248 userdev JAR was not found.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($userdev.FullName)
try {
    $reader = [System.IO.StreamReader]::new($archive.GetEntry('config.json').Open())
    try { $config = $reader.ReadToEnd() } finally { $reader.Dispose() }
} finally { $archive.Dispose() }
New-Item -ItemType Directory -Force (Join-Path $projectRoot 'build') | Out-Null
[System.IO.File]::WriteAllText((Join-Path $projectRoot 'build/moddev-config.json'), $config)
$runRoot = Join-Path $projectRoot 'run-smoke'
New-Item -ItemType Directory -Force $runRoot | Out-Null
if (-not (Test-Path -LiteralPath (Join-Path $runRoot 'eula.txt'))) {
    [System.IO.File]::WriteAllText((Join-Path $runRoot 'eula.txt'), "eula=true`n")
}
if (-not (Test-Path -LiteralPath (Join-Path $runRoot 'server.properties'))) {
    [System.IO.File]::WriteAllText((Join-Path $runRoot 'server.properties'), @"
online-mode=false
server-port=0
level-name=lighting-tests
level-type=minecraft:flat
generate-structures=false
spawn-protection=0
view-distance=2
simulation-distance=2
max-tick-time=120000
"@)
}
Push-Location $projectRoot
try {
    & .\gradlew.bat --offline --no-daemon -I smoke.init.gradle -PlightSmoke runServer *> verification-live.log
    if ($LASTEXITCODE -ne 0 -or -not (Select-String -LiteralPath verification-live.log -SimpleMatch 'COPYCAT LIGHT SMOKE PASSED')) {
        throw 'Live lighting tests failed. See verification-live.log.'
    }
    & .\gradlew.bat --offline --no-daemon -I smoke.init.gradle -PlightSmoke -PreloadSmoke runServer *> verification-reload.log
    if ($LASTEXITCODE -ne 0 -or -not (Select-String -LiteralPath verification-reload.log -SimpleMatch 'COPYCAT LIGHT RELOAD SMOKE PASSED')) {
        throw 'Save reload tests failed. See verification-reload.log.'
    }
    Write-Host 'Live lighting and save reload tests passed.'
    if ($Sable) {
        & .\gradlew.bat --offline --no-daemon -I smoke.init.gradle -PlightSmoke -PsableSmoke runServer *> verification-sable.log
        if ($LASTEXITCODE -ne 0 -or -not (Select-String -LiteralPath verification-sable.log -SimpleMatch 'COPYCAT SABLE LIGHT SMOKE PASSED')) {
            throw 'Sable plot tests failed. See verification-sable.log.'
        }
        Write-Host 'Sable plot lighting tests passed.'
    }
} finally { Pop-Location }
