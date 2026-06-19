# Patch nextlib AAR - remove NextRenderersFactory to avoid R8 duplicate class error
param(
    [string]$AarPath = "E:\qclaw\Lelebox\Lelebox_src\third_party\maven\io\github\anilbeesetti\nextlib-media3ext\1.10.0-0.12.1\nextlib-media3ext-1.10.0-0.12.1.aar",
    [string]$TmpDir = "E:\qclaw\Lelebox\tmp\aar_patch"
)

Add-Type -AssemblyName System.IO.Compression.FileSystem

if (Test-Path $TmpDir) { Remove-Item -Recurse -Force $TmpDir }
New-Item -ItemType Directory -Force -Path $TmpDir | Out-Null

Write-Host "Extracting AAR..."
$zip = [System.IO.Compression.ZipFile]::OpenRead($AarPath)
foreach ($entry in $zip.Entries) {
    if (-not $entry.FullName.EndsWith('/')) {
        $target = Join-Path $TmpDir $entry.FullName
        $parent = Split-Path $target -Parent
        if (-not (Test-Path $parent)) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
    }
}
$zip.Dispose()

$classesJar = Join-Path $TmpDir "classes.jar"
$jarDir = Join-Path $TmpDir "jar_content"
New-Item -ItemType Directory -Force -Path $jarDir | Out-Null

Write-Host "Extracting classes.jar..."
$jzip = [System.IO.Compression.ZipFile]::OpenRead($classesJar)
foreach ($entry in $jzip.Entries) {
    if (-not $entry.FullName.EndsWith('/')) {
        $target = Join-Path $jarDir $entry.FullName
        $parent = Split-Path $target -Parent
        if (-not (Test-Path $parent)) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
    }
}
$jzip.Dispose()

Write-Host "Removing NextRenderersFactory classes..."
Get-ChildItem $jarDir -Recurse -Filter 'NextRenderersFactory*' | Remove-Item -Force

Write-Host "Repacking classes.jar..."
$newJar = Join-Path $TmpDir "classes_new.jar"
$jzip2 = [System.IO.Compression.ZipFile]::Open($newJar, [System.IO.Compression.ZipArchiveMode]::Create)
$baseLen = $jarDir.FullName.Length + 1
Get-ChildItem $jarDir -Recurse -File | ForEach-Object {
    $rel = $_.FullName.Substring($baseLen) -replace '\\', '/'
    $e = $jzip2.CreateEntry($rel)
    $s = $e.Open()
    $fs = [System.IO.File]::OpenRead($_.FullName)
    $fs.CopyTo($s)
    $fs.Dispose()
    $s.Dispose()
}
$jzip2.Dispose()
Move-Item $newJar $classesJar -Force

Write-Host "Repacking AAR (in-place)..."
Remove-Item $AarPath -Force
$azip = [System.IO.Compression.ZipFile]::Open($AarPath, [System.IO.Compression.ZipArchiveMode]::Create)
$tmpLen = $TmpDir.Length + 1
Get-ChildItem $TmpDir -Recurse -File | ForEach-Object {
    $rel = $_.FullName.Substring($tmpLen) -replace '\\', '/'
    $e = $azip.CreateEntry($rel)
    $s = $e.Open()
    $fs = [System.IO.File]::OpenRead($_.FullName)
    $fs.CopyTo($s)
    $fs.Dispose()
    $s.Dispose()
}
$azip.Dispose()

Write-Host "Done! Patched AAR: $AarPath"