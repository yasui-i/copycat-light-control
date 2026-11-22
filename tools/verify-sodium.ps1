$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sourceJar = Join-Path $projectRoot 'sodium-libs/sodium-neoforge-0.8.13+mc1.21.1.jar'
if (-not (Test-Path -LiteralPath $sourceJar)) { throw 'Place the original Sodium 0.8.13 NeoForge JAR in sodium-libs first.' }
$fixtureRoot = Join-Path $projectRoot 'sodium-libs/extracted'
New-Item -ItemType Directory -Force $fixtureRoot | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$outer = [System.IO.Compression.ZipFile]::OpenRead($sourceJar)
try {
    foreach ($entry in $outer.Entries) {
        if ($entry.FullName -like 'META-INF/jarjar/*.jar') {
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $fixtureRoot $entry.Name), $true)
        }
    }
    $core = Join-Path $fixtureRoot 'net.caffeinemc.sodium-neoforge-0.8.13+mc1.21.1-mod.jar'
    $fixture = [System.IO.Compression.ZipFile]::Open($core, [System.IO.Compression.ZipArchiveMode]::Update)
    try {
        # Sodium's outer service locator is not usable as an ordinary dev mod.
        # Add only its support classes to the unchanged nested mod for this isolated test.
        foreach ($entry in $outer.Entries) {
            if ($entry.FullName.StartsWith('net/') -and $entry.FullName.EndsWith('.class') -and -not $fixture.GetEntry($entry.FullName)) {
                $targetEntry = $fixture.CreateEntry($entry.FullName)
                $sourceStream = $entry.Open(); $targetStream = $targetEntry.Open()
                try { $sourceStream.CopyTo($targetStream) } finally { $sourceStream.Dispose(); $targetStream.Dispose() }
            }
        }
    } finally { $fixture.Dispose() }
} finally { $outer.Dispose() }
$runtimeRoot = Join-Path $projectRoot 'sodium-libs/test-runtime'
New-Item -ItemType Directory -Force $runtimeRoot | Out-Null
Copy-Item -LiteralPath $core -Destination (Join-Path $runtimeRoot 'sodium-dev-fixture.jar') -Force
Get-ChildItem -LiteralPath $fixtureRoot -Filter 'fabric-*.jar' -File | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $runtimeRoot $_.Name) -Force
}
$runRoot = Join-Path $projectRoot 'run-client-smoke'
New-Item -ItemType Directory -Force (Join-Path $runRoot 'config') | Out-Null
[System.IO.File]::WriteAllText((Join-Path $runRoot 'config/fml.toml'), "earlyWindowControl = false`nversionCheck = false`n")
[System.IO.File]::WriteAllText((Join-Path $runRoot 'options.txt'), "fullscreen:false`nonboardAccessibility:false`nsoundCategory_master:0.0`npauseOnLostFocus:false`nmaxFps:30`n")
Push-Location $projectRoot
try {
    & .\gradlew.bat --offline --no-daemon -I smoke.init.gradle -I tools/client-smoke.init.gradle -I tools/sodium-smoke.init.gradle -PclientSmoke -PsodiumSmoke runClient *> verification-sodium-client.log
    if ($LASTEXITCODE -ne 0 -or -not (Select-String -LiteralPath verification-sodium-client.log -SimpleMatch 'COPYCAT SODIUM LIGHT SMOKE PASSED')) {
        throw 'Sodium renderer tests failed. See verification-sodium-client.log.'
    }
    Write-Host 'Hidden Sodium flat/smooth renderer tests passed.'
} finally { Pop-Location }
