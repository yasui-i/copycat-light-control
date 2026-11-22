$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$javaRoot = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'D:/Program Files (x86)' }
$outputRoot = Join-Path $projectRoot 'build/enclosure-tests'
New-Item -ItemType Directory -Force $outputRoot | Out-Null
$sources = @(
    (Join-Path $projectRoot 'src/main/java/dev/local/copycatlight/client/EnclosureGrid.java'),
    (Join-Path $projectRoot 'src/enclosureTest/java/dev/local/copycatlight/client/EnclosureGridTest.java')
)
& (Join-Path $javaRoot 'bin/javac.exe') -encoding UTF-8 -d $outputRoot $sources
if ($LASTEXITCODE -ne 0) { throw 'Enclosure test compilation failed.' }
& (Join-Path $javaRoot 'bin/java.exe') -cp $outputRoot dev.local.copycatlight.client.EnclosureGridTest
if ($LASTEXITCODE -ne 0) { throw 'Enclosure grid tests failed.' }
