$ErrorActionPreference = 'Stop'
$bs = [char]92
$jar = 'carpet-fga-addition-1.4.10+v2608162109-mc1.21.1.jar'
$dir = 'D:/ai/carpet-fga/mod-builds/20260816-211006'
$m = [ordered]@{
    mod_name = 'Carpet FGA Addition'
    game_version = '1.21.1'
    created_at = '2026-08-16T21:10:07+08:00'
    build_command = ".${bs}gradlew.bat :1.21.1:build --no-daemon"
    feature = 'shulkerBedrockDuplication - Bedrock-style shulker respawn on shulker-bullet kill (baseline 1.21.1 only)'
    source_artifacts = @("D:${bs}ai${bs}carpet-fga${bs}versions${bs}1.21.1${bs}build${bs}libs${bs}$jar")
    files = @(@{
        path = $jar
        size_bytes = (Get-Item "$dir/$jar").Length
        sha256 = (Get-FileHash "$dir/$jar" -Algorithm SHA256).Hash.ToLower()
    })
}
$m | ConvertTo-Json -Depth 4 | Set-Content "$dir/build-manifest.json" -Encoding UTF8
Get-Content "$dir/build-manifest.json"
Write-Output ("PARSE-CHECK: " + (Get-Content "$dir/build-manifest.json" -Raw | ConvertFrom-Json).files[0].sha256)
