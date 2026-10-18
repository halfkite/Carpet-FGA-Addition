param(
    [Parameter(Mandatory=$true)][string]$ServerFixture,
    [Parameter(Mandatory=$true)][string]$FgaJar,
    [Parameter(Mandatory=$true)][string]$CarpetJar,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [string]$VmpJar
)

# Only binaries are reused; every invocation creates its own disposable world.
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$fixture=(Resolve-Path -LiteralPath $ServerFixture).Path
$jdk=Join-Path (Resolve-Path -LiteralPath $JavaHome).Path 'bin'
$fga=(Resolve-Path -LiteralPath $FgaJar).Path
$carpet=(Resolve-Path -LiteralPath $CarpetJar).Path
$run=Join-Path $root ('build\fake-player-preload-26.3-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $run,"$run\mods","$run\classes","$run\deps" | Out-Null
$api=[IO.Compression.ZipFile]::OpenRead("$fixture\mods\fabric-api.jar")
try {
    foreach ($entry in $api.Entries) {
        if ($entry.FullName -like 'META-INF/jars/*.jar') {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path "$run\deps" $entry.Name))
        }
    }
} finally { $api.Dispose() }
$jars=@(Get-ChildItem "$fixture\libraries" -Recurse -Filter '*.jar' | ForEach-Object FullName)
$jars+=@(Get-ChildItem "$run\deps" -Filter '*.jar' | ForEach-Object FullName)
$jars+=@("$fixture\versions\26.3\server-26.3.jar",$fga,$carpet)
& "$jdk\javac.exe" -encoding UTF-8 -cp ($jars -join ';') -d "$run\classes" "$root\scripts\tests\fake-player-preload\java\PreloadProbe.java"
if ($LASTEXITCODE -ne 0) {throw 'Probe compilation failed'}
@{schemaVersion=1;id='fga_preload_probe';version='1';environment='server';entrypoints=@{main=@('carpet.fga.probe.PreloadProbe')};depends=@{minecraft='26.3';'carpet-fga-addition'='*'}} | ConvertTo-Json -Depth 5 | Set-Content "$run\classes\fabric.mod.json" -Encoding utf8
& "$jdk\jar.exe" --create --file "$run\mods\probe.jar" -C "$run\classes" .
if ($LASTEXITCODE -ne 0) {throw 'Probe packaging failed'}
foreach ($file in @('server.jar','fabric-server-launch.jar')) {Copy-Item "$fixture\$file" "$run\$file"}
foreach ($dir in @('libraries','versions')) {New-Item -ItemType Junction -Path "$run\$dir" -Target "$fixture\$dir" | Out-Null}
Copy-Item $fga "$run\mods\fga.jar"
Copy-Item $carpet "$run\mods\carpet.jar"
Copy-Item "$fixture\mods\fabric-api.jar" "$run\mods\fabric-api.jar"
if ($VmpJar) {Copy-Item -LiteralPath (Resolve-Path -LiteralPath $VmpJar).Path -Destination "$run\mods\vmp.jar"}
Set-Content "$run\eula.txt" 'eula=true' -Encoding ascii
Set-Content "$run\server.properties" -Encoding ascii -Value @('online-mode=false','server-ip=127.0.0.1','server-port=0','view-distance=2','simulation-distance=2','level-name=world','level-type=minecraft:flat','generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1}]}','max-tick-time=60000','spawn-protection=0','white-list=false','pause-when-empty-seconds=0')
$proc=Start-Process "$jdk\java.exe" -ArgumentList @('-Xms512M','-Xmx1G','-jar','fabric-server-launch.jar','nogui') -WorkingDirectory $run -WindowStyle Hidden -PassThru -RedirectStandardOutput "$run\stdout.log" -RedirectStandardError "$run\stderr.log"
Write-Output "RUN=$run PID=$($proc.Id)"
$passed=$false
try {
    $deadline=(Get-Date).AddSeconds(240)
    while (-not $proc.HasExited -and (Get-Date) -lt $deadline) {Start-Sleep -Seconds 2}
    if (-not $proc.HasExited) {throw 'Probe server timeout'}
    $log=Get-Content "$run\stdout.log" -Raw
    $passed=$proc.ExitCode -eq 0 -and $log.Contains('PRELOAD_PROBE_COMPLETE') -and -not $log.Contains('PRELOAD_PROBE_ERROR')
    Select-String "$run\stdout.log" -Pattern 'PRELOAD_PROBE_(RESULT|ERROR|COMPLETE)' | ForEach-Object Line
    if (-not $passed) {throw "Preload regression failed; see $run\stdout.log"}
} finally {
    if (-not $proc.HasExited) {Stop-Process -Id $proc.Id -Force}
    @{minecraft='26.3';passed=$passed;fgaJar=$fga;vmpJar=$VmpJar;log="$run\stdout.log"} | ConvertTo-Json | Set-Content "$run\summary.json" -Encoding utf8
}
