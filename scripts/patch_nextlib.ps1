# Extract nextlib-media3ext AAR, remove NextRenderersFactory, repackage as JAR
param(
    [string]$AarPath = "E:\qclaw\Lelebox\Lelebox_src\third_party\maven\io\github\anilbeesetti\nextlib-media3ext\1.10.0-0.12.1\nextlib-media3ext-1.10.0-0.12.1.aar",
    [string]$OutputDir = "E:\qclaw\Lelebox\Lelebox_src\app\libs",
    [string]$WorkDir = "E:\qclaw\Lelebox\tmp\aar_patch"
)

Add-Type -AssemblyName System.IO.Compression.FileSystem

# Clean work dir
if (Test-Path $WorkDir) { Remove-Item -Recurse -Force $WorkDir }
New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null

# Extract AAR
$zip = [System.IO.Compression.ZipFile]::OpenRead($AarPath)
$entries = $zip.Entries
foreach ($entry in $entries) {
    $targetPath = Join-Path $WorkDir $entry.FullName
    $parentDir = Split-Path $targetPath -Parent
    if (-not (Test-Path $parentDir)) { New-Item -ItemType Directory -Force -Path $parentDir | Out-Null }
    if (-not $entry.FullName.EndsWith('/')) {
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $targetPath, $true)
    }
}
$zip.Dispose()

# Extract classes.jar
$classesJar = Join-Path $WorkDir "classes.jar"
$jarDir = Join-Path $WorkDir "jar_content"
New-Item -ItemType Directory -Force -Path $jarDir | Out-Null

$jarZip = [System.IO.Compression.ZipFile]::OpenRead($classesJar)
foreach ($entry in $jarZip.Entries) {
    if (-not $entry.FullName.EndsWith('/')) {
        $targetPath = Join-Path $jarDir $entry.FullName
        $parentDir = Split-Path $targetPath -Parent
        if (-not (Test-Path $parentDir)) { New-Item -ItemType Directory -Force -Path $parentDir | Out-Null }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $targetPath, $true)
    }
}
$jarZip.Dispose()

# Remove NextRenderersFactory classes
$toRemove = @(
    "io/github/anilbeesetti/nextlib/media3ext/ffdecoder/NextRenderersFactory.class",
    "io/github/anilbeesetti/nextlib/media3ext/ffdecoder/NextRenderersFactory`$Companion.class"
)
foreach ($f in $toRemove) {
    $fp = Join-Path $jarDir $f
    if (Test-Path $fp) { Remove-Item -Force $fp; Write-Host "Removed: $f" }
    else { Write-Host "Not found: $f" }
}

# Repackage as JAR (using .NET ZIP)
$patchedJar = Join-Path $jarDir "patched.jar"
$jarFiles = Get-ChildItem -Recurse -File $jarDir | Where-Object { $_.FullName -ne $patchedJar }
$newZip = [System.IO.Compression.ZipFile]::Open($patchedJar, [System.IO.Compression.ZipArchiveMode]::Create)
$baseLen = $jarDir.Length + 1
foreach ($f in $jarFiles) {
    $relPath = $f.FullName.Substring($baseLen) -replace '\\', '/'
    if ($relPath -eq 'patched.jar') { continue }
    $entry = $newZip.CreateEntry($relPath, [System.IO.Compression.CompressionLevel]::Optimal)
    $entryStream = $entry.Open()
    $fileStream = [System.IO.File]::OpenRead($f.FullName)
    $fileStream.CopyTo($entryStream)
    $fileStream.Dispose()
    $entryStream.Dispose()
}
$newZip.Dispose()

# Copy patched JAR to libs
$destJar = Join-Path $OutputDir "nextlib-patched-runtime.jar"
Copy-Item $patchedJar $destJar -Force
Write-Host "Patched JAR created: $destJar"

# Also copy the AAR metadata (POM) - not needed for runtime, but for reference
Write-Host "Done! Use compileOnly for original nextlib and runtimeOnly for patched JAR"
