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
$runRoot = Join-Path $projectRoot 'run-client-smoke'
New-Item -ItemType Directory -Force (Join-Path $runRoot 'config') | Out-Null
# Prevent FML from creating a visible early window before the test mixin can run.
[System.IO.File]::WriteAllText((Join-Path $runRoot 'config/fml.toml'), @"
earlyWindowControl = false
versionCheck = false
"@)
[System.IO.File]::WriteAllText((Join-Path $runRoot 'options.txt'), @"
fullscreen:false
onboardAccessibility:false
soundCategory_master:0.0
pauseOnLostFocus:false
maxFps:30
"@)
Push-Location $projectRoot
try {
    $smokeArguments = @('--offline', '--no-daemon', '-I', 'smoke.init.gradle', '-I', 'tools/client-smoke.init.gradle', '-PclientSmoke')
    $smokeLog = 'verification-client.log'
    if ($Sable) { $smokeArguments += '-PsableSmoke'; $smokeLog = 'verification-client-sable.log' }
    & .\gradlew.bat @smokeArguments runClient *> $smokeLog
    if ($LASTEXITCODE -ne 0 -or -not (Select-String -LiteralPath $smokeLog -SimpleMatch 'COPYCAT CLIENT LIGHT SMOKE PASSED')) {
        throw "Hidden client renderer tests failed. See $smokeLog."
    }
    if ($Sable -and -not (Select-String -LiteralPath $smokeLog -SimpleMatch 'COPYCAT SABLE SURFACE SMOKE PASSED')) { throw 'Sable surface tests did not pass.' }
    Write-Host 'Hidden client flat/AO renderer tests passed.'
} finally { Pop-Location }
