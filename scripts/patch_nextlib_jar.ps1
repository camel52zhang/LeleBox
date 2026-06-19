param(
    [string]$AppLibs = "E:\qclaw\Lelebox\Lelebox_src\app\libs",
    [string]$tmpDir = "E:\qclaw\Lelebox\tmp\nextlib_patch3"
)

Add-Type -AssemblyName System.IO.Compression.FileSystem

if (Test-Path $tmpDir) { Remove-Item -Recurse -Force $tmpDir }
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

# Try to find cached AAR from Gradle or Maven Central
$candidates = @(
    "C:\Users\zhangz\.gradle\caches\modules-2\files-2.1\io.github.anilbeesetti\nextlib-media3ext\1.10.0-0.12.1\*\nextlib-media3ext-1.10.0-0.12.1.aar",
    "C:\Users\zhangz\.gradle\caches\8.*\transforms\*\transformed\nextlib-media3ext-1.10.0-0.12.1-runtime.jar",
    "C:\Users\zhangz\AppData\Local\Android\m2repository\*",
    "$env:USERPROFILE\.m2\repository\io\github\anilbeesetti\*"
)

$aarFile = $null
foreach ($pattern in $candidates) {
    $files = Resolve-Path $pattern -ErrorAction SilentlyContinue
    if ($files) {
        $aarFile = $files[0].Path
        Write-Host "Found: $aarFile"
        break
    }
}

if (-not $aarFile -or -not (Test-Path $aarFile)) {
    Write-Host "Cached AAR not found. Need download. Let me check if Gradle cached it somewhere else..."
    
    # Try broader search
    $files = Get-ChildItem "C:\Users\zhangz\.gradle\caches" -Recurse -Filter "nextlib-media3ext*.aar" -ErrorAction SilentlyContinue
    if ($files) { $aarFile = $files[0].FullName; Write-Host "Found: $aarFile" }
    
    # Try JAR too
    if (-not $aarFile) {
        $jarFiles = Get-ChildItem "C:\Users\zhangz\.gradle\caches" -Recurse -Filter "nextlib-media3ext*.jar" -ErrorAction SilentlyContinue
        if ($jarFiles) { $aarFile = $jarFiles[0].FullName; Write-Host "Found JAR: $aarFile" }
        else { 
            Write-Error "Cannot find nextlib-media3ext artifact. Build must download it first." 
            exit 1
        }
    }
}

$outJar = Join-Path $AppLibs "nextlib-media3ext-patched.jar"

if ($aarFile.EndsWith('.aar')) {
    Write-Host "Extracting AAR..."
    $zip = [System.IO.Compression.ZipFile]::OpenRead($aarFile)
    foreach ($entry in $zip.Entries) {
        if ($entry.Name -eq 'classes.jar') {
            $target = Join-Path $tmpDir "classes.jar"
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
            Write-Host "Extracted: classes.jar ($($entry.Length) bytes)"
        }
    }
    $zip.Dispose()
    $classesJar = Join-Path $tmpDir "classes.jar"
} else {
    # It's already a JAR
    $classesJar = $aarFile
}

# Extract classes.jar content
$jarDir = Join-Path $tmpDir "jar_content"
New-Item -ItemType Directory -Force -Path $jarDir | Out-Null

Write-Host "Extracting classes..."
$jzip = [System.IO.Compression.ZipFile]::OpenRead($classesJar)
$entryCount = 0
foreach ($entry in $jzip.Entries) {
    if (-not $entry.FullName.EndsWith('/')) {
        $target = Join-Path $jarDir $entry.FullName
        $parent = Split-Path $target -Parent
        if (-not (Test-Path $parent)) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
        $entryCount++
    }
}
$jzip.Dispose()
Write-Host "Extracted $entryCount entries"

# Remove NextRenderersFactory
Write-Host "Removing NextRenderersFactory classes..."
Get-ChildItem $jarDir -Recurse -Filter "NextRenderersFactory*" -Force | ForEach-Object {
    Write-Host "  Removing: $($_.Name)"
    Remove-Item $_.FullName -Force
}

# Repack as JAR directly (not as AAR)
Write-Host "Repacking patched JAR..."
$jzip2 = [System.IO.Compression.ZipFile]::Open($outJar, [System.IO.Compression.ZipArchiveMode]::Create)
$baseLen = $jarDir.FullName.Length + 1
Get-ChildItem $jarDir -Recurse -File | ForEach-Object {
    $rel = $_.FullName.Substring($baseLen) -replace '\\', '/'
    $e = $jzip2.CreateEntry($rel, [System.IO.Compression.CompressionLevel]::Optimal)
    $s = $e.Open()
    $fs = [System.IO.File]::OpenRead($_.FullName)
    $fs.CopyTo($s)
    $fs.Dispose()
    $s.Dispose()
}
$jzip2.Dispose()

Write-Host "Done! Patched JAR: $outJar"
Write-Host "Size: $((Get-Item $outJar).Length) bytes"