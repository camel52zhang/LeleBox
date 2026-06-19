param(
    [string]$AarCacheDir = "C:\Users\zhangz\.gradle\caches\modules-2\files-2.1\io.github.anilbeesetti\nextlib-media3ext\1.10.0-0.12.1",
    [string]$OutDir = "E:\qclaw\Lelebox\Lelebox_src\app\libs",
    [string]$TmpDir = "E:\qclaw\Lelebox\tmp\nextlib_v2"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

# Find AAR
$aarFiles = Get-ChildItem $AarCacheDir -Recurse -Filter "*.aar" -ErrorAction Stop
if (-not $aarFiles) { throw "AAR not found" }
$aarFile = $aarFiles[0].FullName
Write-Host "AAR: $aarFile"

# Clean tmp
if (Test-Path $TmpDir) { Remove-Item -Recurse -Force $TmpDir }
New-Item -ItemType Directory -Force -Path $TmpDir | Out-Null

# Step 1: Extract classes.jar from AAR
$classesJar = Join-Path $TmpDir "classes.jar"
$zip = [System.IO.Compression.ZipFile]::OpenRead($aarFile)
foreach ($entry in $zip.Entries) {
    if ($entry.Name -eq "classes.jar") {
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $classesJar, $true)
        break
    }
}
$zip.Dispose()
Write-Host "Extracted classes.jar: $((Get-Item $classesJar).Length) bytes"

# Step 2: Extract classes.jar entries
$jarExtractDir = Join-Path $TmpDir "extracted"
New-Item -ItemType Directory -Force -Path $jarExtractDir | Out-Null
$jzip = [System.IO.Compression.ZipFile]::OpenRead($classesJar)
foreach ($entry in $jzip.Entries) {
    $target = Join-Path $jarExtractDir $entry.FullName
    if ($entry.FullName.EndsWith('/')) {
        New-Item -ItemType Directory -Force -Path $target | Out-Null
    } else {
        $parent = Split-Path $target -Parent
        if (-not (Test-Path $parent)) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
    }
}
$jzip.Dispose()

# Step 3: Remove NextRenderersFactory
$removed = Get-ChildItem $jarExtractDir -Recurse -Filter "NextRenderersFactory*" -Force
$removed | ForEach-Object { Write-Host "  Removing: $($_.FullName)"; Remove-Item $_.FullName -Force }

# Step 4: Repack JAR - brute force approach: use PowerShell to create zip entries
$outJar = Join-Path $OutDir "nextlib-media3ext-patched.jar"
if (Test-Path $outJar) { Remove-Item $outJar -Force }

$allFiles = Get-ChildItem $jarExtractDir -Recurse -File
Write-Host "Repacking $($allFiles.Count) files..."

# Calculate base length
$basePath = $jarExtractDir.TrimEnd('\', '/')
$baseLen = $basePath.Length

# Use .NET ZipArchive
$writeZip = $null
$stream = $null
try {
    $stream = [System.IO.File]::Open($outJar, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
    $writeZip = [System.IO.Compression.ZipArchive]::new($stream, [System.IO.Compression.ZipArchiveMode]::Create)

    foreach ($file in $allFiles) {
        # Compute relative path: strip base dir, replace \ with /
        $relative = $file.FullName.Substring($baseLen + 1) -replace '\\', '/'
        
        Write-Debug "  $relative"
        $entry = $writeZip.CreateEntry($relative, [System.IO.Compression.CompressionLevel]::Optimal)
        $entryStream = $entry.Open()
        $fileStream = [System.IO.File]::OpenRead($file.FullName)
        $fileStream.CopyTo($entryStream)
        $fileStream.Dispose()
        $entryStream.Dispose()
    }
} finally {
    if ($writeZip) { $writeZip.Dispose() }
    if ($stream) { $stream.Dispose() }
}

Write-Host "Patched JAR: $outJar ($((Get-Item $outJar).Length) bytes)"

# Verify
$vzip = [System.IO.Compression.ZipFile]::OpenRead($outJar)
$hasNext = $false
$first3 = @()
foreach ($e in $vzip.Entries) {
    if ($first3.Count -lt 3) { $first3 += $e.FullName }
    if ($e.FullName -match "NextRenderersFactory") { $hasNext = $true }
}
$vzip.Dispose()
Write-Host "Sample entries: $($first3 -join ', ')"
if ($hasNext) { Write-Host "WARNING: NextRenderersFactory STILL in JAR!" } else { Write-Host "VERIFIED: No NextRenderersFactory" }