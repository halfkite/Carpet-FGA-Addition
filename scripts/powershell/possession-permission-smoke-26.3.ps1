param(
    [Parameter(Mandatory=$true)][string]$ServerFixture,
    [Parameter(Mandatory=$true)][string]$ClientGameDirectory,
    [Parameter(Mandatory=$true)][string]$TestWorldFixture,
    [Parameter(Mandatory=$true)][string]$FgaJar,
    [Parameter(Mandatory=$true)][string]$CarpetJar,
    [Parameter(Mandatory=$true)][string]$JavaHome
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$fixture=(Resolve-Path -LiteralPath $ServerFixture).Path
$game=(Resolve-Path -LiteralPath $ClientGameDirectory).Path
$worldFixture=(Resolve-Path -LiteralPath $TestWorldFixture).Path
$testRoot=[IO.Path]::GetFullPath((Join-Path $root 'build')) + [IO.Path]::DirectorySeparatorChar
if (-not $worldFixture.StartsWith($testRoot,[StringComparison]::OrdinalIgnoreCase)) {
    throw 'TestWorldFixture must be a disposable world under this repository build directory'
}
$jdk=Join-Path (Resolve-Path -LiteralPath $JavaHome).Path 'bin'
$run=Join-Path $root ('build\possession-permission-26.3-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$fga=(Resolve-Path -LiteralPath $FgaJar).Path
$carpet=(Resolve-Path -LiteralPath $CarpetJar).Path
New-Item -ItemType Directory -Path $run,"$run\mods","$run\classes","$run\natives","$run\saves","$run\deps" | Out-Null
Copy-Item -LiteralPath $worldFixture -Destination "$run\saves\world" -Recurse
$version=Get-Content "$game\versions\26.3\26.3.json" -Raw | ConvertFrom-Json
$classpath=@()
foreach ($library in $version.libraries) {
    $allowed=$true
    if ($library.rules) {
        $allowed=$false
        foreach ($rule in $library.rules) {
            $matches=(-not $rule.os.name -or $rule.os.name -eq 'windows') -and (-not $rule.os.arch -or $rule.os.arch -in @('x86_64','amd64')) -and (-not $rule.features)
            if ($matches) {$allowed=$rule.action -eq 'allow'}
        }
    }
    if ($allowed -and $library.downloads.artifact.path) {$classpath+=Join-Path "$game\libraries" $library.downloads.artifact.path}
}
$classpath+="$game\versions\26.3\26.3.jar"
$classpath+=@(Get-ChildItem "$fixture\libraries" -Recurse -Filter '*.jar' | ForEach-Object FullName)
$api=[IO.Compression.ZipFile]::OpenRead("$fixture\mods\fabric-api.jar")
try {
    foreach ($entry in $api.Entries) {
        if ($entry.FullName -like 'META-INF/jars/*.jar') {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path "$run\deps" $entry.Name))
        }
    }
} finally { $api.Dispose() }
$compile=@($classpath)+@(Get-ChildItem "$run\deps" -Filter '*.jar' | ForEach-Object FullName)+@($fga,$carpet)
& "$jdk\javac.exe" -encoding UTF-8 -cp ($compile -join ';') -d "$run\classes" "$root\scripts\tests\possession-permission\java\IntegratedPermissionProbe.java"
if ($LASTEXITCODE -ne 0) {throw 'Client probe compilation failed'}
@{schemaVersion=1;id='fga_solo_permission_probe';version='1';environment='client';entrypoints=@{client=@('carpet.fga.probe.IntegratedPermissionProbe')};depends=@{minecraft='26.3';'carpet-fga-addition'='*'}} | ConvertTo-Json -Depth 5 | Set-Content "$run\classes\fabric.mod.json" -Encoding utf8
& "$jdk\jar.exe" --create --file "$run\mods\probe.jar" -C "$run\classes" .
Copy-Item $fga "$run\mods\fga.jar"
Copy-Item $carpet "$run\mods\carpet.jar"
Copy-Item "$fixture\mods\fabric-api.jar" "$run\mods\fabric-api.jar"
Set-Content "$run\options.txt" -Encoding ascii -Value @('lang:en_us','pauseOnLostFocus:false','renderDistance:2','simulationDistance:5','soundCategory_master:0.0','maxFps:30','onboardAccessibility:false','guiScale:2','fullscreen:false')
$arguments=@('-Xint','-Xms512M','-Xmx2G','-XX:StackShadowPages=32','--enable-native-access=ALL-UNNAMED','--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED',"-Djava.library.path=$run\natives\java","-Djna.tmpdir=$run\natives\jna","-Dorg.lwjgl.system.SharedLibraryExtractPath=$run\natives\lwjgl","-Dio.netty.native.workdir=$run\natives\netty",'-cp',($classpath -join ';'),'net.fabricmc.loader.impl.launch.knot.KnotClient','--username','ReproA','--version','26.3','--gameDir',$run,'--assetsDir',"$game\assets",'--assetIndex',$version.assetIndex.id,'--uuid','52706e57fa04395ebf819de1b367f013','--accessToken','0','--userType','msa','--versionType','release','--width','960','--height','640','--quickPlaySingleplayer','world')
$argLines=$arguments | ForEach-Object {'"' + $_.Replace('\','\\').Replace('"','\"') + '"'}
[IO.File]::WriteAllLines("$run\client.args",$argLines,[Text.UTF8Encoding]::new($false))
$proc=Start-Process "$jdk\javaw.exe" -ArgumentList ('@' + $run + '\client.args') -WorkingDirectory $run -WindowStyle Hidden -PassThru -RedirectStandardOutput "$run\stdout.log" -RedirectStandardError "$run\stderr.log"
@{run=$run;pid=$proc.Id;fga=$fga} | ConvertTo-Json | Set-Content "$run\process.json" -Encoding utf8
Write-Output "RUN=$run PID=$($proc.Id)"

$passed=$false
try {
    $deadline=(Get-Date).AddSeconds(450)
    while (-not $proc.HasExited -and (Get-Date) -lt $deadline) {Start-Sleep -Seconds 2}
    if (-not $proc.HasExited) {throw 'Integrated client regression timed out'}
    $log=Get-Content "$run\stdout.log" -Raw
    $passed=$proc.ExitCode -eq 0 -and $log -match 'SOLO_PERMISSION_RESULT checks=\d+ failures=0'
    Select-String "$run\stdout.log" -Pattern 'SOLO_PERMISSION_(CHECK|SCENARIO|RESULT)' | ForEach-Object Line
    if (-not $passed) {throw "Singleplayer permission regression failed; see $run\stdout.log"}
} finally {
    if (-not $proc.HasExited) {Stop-Process -Id $proc.Id -Force}
    @{minecraft='26.3';passed=$passed;fgaJar=$fga;log="$run\stdout.log";mode='real-integrated-server-client-packets'} | ConvertTo-Json | Set-Content "$run\summary.json" -Encoding utf8
}
