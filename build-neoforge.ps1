param(
    [ValidateSet('1.21.11', '26.1', '26.2')]
    [string]$Target = '26.2'
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$mcRoot = 'D:\game\Minecraft\.minecraft'
$devRoot = 'D:\game\Minecraft\dev'
$jdk25 = 'C:\Users\QiaoSylveon\AppData\Roaming\.minecraft\runtime\java-runtime-epsilon'
$jdk21 = Join-Path $devRoot 'jdk21'

$configs = @{
    '1.21.11' = @{
        GameDir = Join-Path $mcRoot 'versions\1.21.11-NeoForge_21.11.48'
        Json = '1.21.11-NeoForge_21.11.48.json'
        Patched = Join-Path $mcRoot 'libraries\net\neoforged\minecraft-client-patched\21.11.48\minecraft-client-patched-21.11.48.jar'
        Universal = Join-Path $mcRoot 'libraries\net\neoforged\neoforge\21.11.48\neoforge-21.11.48-universal.jar'
        Jdk = $jdk21
        VersionDir = 'versions\1.21.11'
        Project = 'neoforge-1.21.11'
        Label = '1.21.11'
        Out = 'AFKFarm-1.0.2-neoforge-1.21.11.jar'
    }
    '26.1' = @{
        GameDir = Join-Path $mcRoot 'versions\26.1.2-NeoForge_26.1.2.122'
        Json = '26.1.2-NeoForge_26.1.2.122.json'
        Patched = Join-Path $mcRoot 'libraries\net\neoforged\minecraft-client-patched\26.1.2.122\minecraft-client-patched-26.1.2.122.jar'
        Universal = Join-Path $mcRoot 'libraries\net\neoforged\neoforge\26.1.2.122\neoforge-26.1.2.122-universal.jar'
        Jdk = $jdk25
        VersionDir = 'versions\26.1'
        Project = 'neoforge-26.1'
        Label = '26.1.2'
        Out = 'AFKFarm-1.0.2-neoforge-26.1.2.jar'
    }
    '26.2' = @{
        GameDir = Join-Path $mcRoot 'versions\26.2-NeoForge_26.2.0.88'
        Json = '26.2-NeoForge_26.2.0.88.json'
        Patched = Join-Path $mcRoot 'libraries\net\neoforged\minecraft-client-patched\26.2.0.88\minecraft-client-patched-26.2.0.88.jar'
        Universal = Join-Path $mcRoot 'libraries\net\neoforged\neoforge\26.2.0.88\neoforge-26.2.0.88-universal.jar'
        Jdk = $jdk25
        VersionDir = 'versions\26.2'
        Project = 'neoforge-26.2'
        Label = '26.2'
        Out = 'AFKFarm-1.0.2-neoforge-26.2.jar'
    }
}

$cfg = $configs[$Target]
$javac = Join-Path $cfg.Jdk 'bin\javac.exe'
$jarTool = Join-Path $cfg.Jdk 'bin\jar.exe'
$jsonPath = Join-Path $cfg.GameDir $cfg.Json
if (-not (Test-Path $jsonPath)) { throw "Version JSON missing: $jsonPath" }
if (-not (Test-Path $cfg.Patched)) { throw "Patched client jar missing: $($cfg.Patched)" }
if (-not (Test-Path $cfg.Universal)) { throw "NeoForge universal jar missing: $($cfg.Universal)" }

$buildDir = Join-Path $root "build\neoforge-$($cfg.Label)"
$classes = Join-Path $buildDir 'classes'
$dist = Join-Path $root 'dist'
Remove-Item $buildDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classes, $dist | Out-Null

$json = Get-Content $jsonPath -Raw | ConvertFrom-Json
$cp = [System.Collections.Generic.List[string]]::new()
$cp.Add($cfg.Patched)
$cp.Add($cfg.Universal)
$librariesRoot = Join-Path $mcRoot 'libraries'
foreach ($lib in $json.libraries) {
    $rel = $lib.downloads.artifact.path
    if ([string]::IsNullOrWhiteSpace($rel)) { continue }
    $p = Join-Path $librariesRoot $rel
    if (Test-Path $p) { $cp.Add($p) }
}

$sourceDirs = @(
    (Join-Path $root 'common\src\main\java'),
    (Join-Path (Join-Path $root $cfg.VersionDir) 'src\main\java'),
    (Join-Path $root 'loaders\neoforge\src\main\java')
)
$sources = foreach ($dir in $sourceDirs) {
    Get-ChildItem $dir -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
}

Write-Host "Compiling NeoForge target $($cfg.Label) with $javac"
$argFile = Join-Path $buildDir 'javac.args'
$argLines = @(
    '-encoding', 'UTF-8',
    '-proc:none',
    '-Xlint:deprecation',
    '-d', "`"$($classes.Replace('\','/'))`"",
    '-classpath', "`"$(($cp -join ';').Replace('\','/'))`""
)
$argLines += $sources | ForEach-Object { "`"$($_.Replace('\','/'))`"" }
[System.IO.File]::WriteAllLines($argFile, $argLines, [System.Text.Encoding]::ASCII)
Write-Host "Classpath entries: $($cp.Count); argfile: $argFile"
& $javac "@$argFile"
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

Copy-Item -Path (Join-Path $root 'common\src\main\resources\*') -Destination $classes -Recurse -Force
Copy-Item -Path (Join-Path $root "projects\$($cfg.Project)\resources\*") -Destination $classes -Recurse -Force

$out = Join-Path $dist $cfg.Out
& $jarTool --create --file $out -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed with exit code $LASTEXITCODE" }

Write-Host ""
Write-Host "Built: $out"
& $jarTool tf $out
