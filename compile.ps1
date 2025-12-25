$ErrorActionPreference = "Continue"

# Setup directories
$root = "C:\Users\senpa\Documents\Plugin Manager Front & Backend"
$libs = "$root\libs"
$build = "$root\build_manual"
$classesCore = "$build\classes\core"
$classesPaper = "$build\classes\paper"
$jarFile = "$root\aegistick-paper-1.0.0.jar"
$zipFile = "$root\aegistick-paper-1.0.0.zip"

if (Test-Path $build) { Remove-Item -Recurse -Force $build }
if (Test-Path $jarFile) { Remove-Item -Force $jarFile }

New-Item -ItemType Directory -Force -Path $libs | Out-Null
New-Item -ItemType Directory -Force -Path $classesCore | Out-Null
New-Item -ItemType Directory -Force -Path $classesPaper | Out-Null

# Function to download file
function Download-File($url, $output) {
    Write-Host "Downloading $output from $url..."
    try {
        Invoke-WebRequest -Uri $url -OutFile $output -UserAgent "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
        if (Test-Path $output) {
            $len = (Get-Item $output).Length
            if ($len -lt 5000) {
                Write-Warning "File too small ($len bytes)."
                Remove-Item $output
                return $false
            }
            Write-Host "Download success ($len bytes)."
            return $true
        }
    } catch {
        Write-Warning "Failed to download $url : $_"
    }
    return $false
}

# Download Spigot 1.16.5 (Legacy - No Adventure dependency hell)
$success = $false
if (Test-Path "$libs/server.jar") { Remove-Item "$libs/server.jar" }

# Attempt 1: GetBukkit CDN (Spigot 1.16.5)
$success = Download-File "https://cdn.getbukkit.org/spigot/spigot-1.16.5.jar" "$libs/server.jar"

if (-not $success) {
    # Attempt 2: Api SpigotMC Legacy
    $success = Download-File "https://api.spigotmc.org/legacy/spigot-1.16.5.jar" "$libs/server.jar"
}

if (-not $success) {
    Write-Error "Could not download any server JAR. Aborting."
    exit 1
}

# Gson
if (-not (Test-Path "$libs/gson.jar")) {
    Download-File "https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar" "$libs/gson.jar"
}

# Compile Core
Write-Host "Compiling Core..."
$coreSources = Get-ChildItem -Recurse "$root\aegistick-core\src\main\java" -Filter "*.java" | Select-Object -ExpandProperty FullName
javac -d $classesCore $coreSources
if ($LASTEXITCODE -ne 0) { exit 1 }

# Compile Paper
Write-Host "Compiling Paper..."
$paperSources = Get-ChildItem -Recurse "$root\aegistick-paper\src\main\java" -Filter "*.java" | Select-Object -ExpandProperty FullName
$classpath = "$classesCore;$libs\server.jar;$libs\gson.jar"

javac -cp $classpath -d $classesPaper $paperSources
if ($LASTEXITCODE -ne 0) { 
    Write-Error "Compilation failed."
    exit 1 
}

# Copy Resources
Copy-Item "$root\aegistick-paper\src\main\resources\*" $classesPaper

# Package JAR
Write-Host "Packaging JAR..."
# Merge core classes into paper classes for shading
Copy-Item -Recurse "$classesCore\*" $classesPaper

# Create Manifest
$manifest = @"
Manifest-Version: 1.0
Main-Class: dev.aegistick.paper.AegisTickPlugin
"@
Set-Content -Path "$build\MANIFEST.MF" -Value $manifest

# Zip it up
Compress-Archive -Path "$classesPaper\*" -DestinationPath $zipFile -Force
Rename-Item -Path $zipFile -NewName $jarFile -Force

Write-Host "Build Complete: $jarFile"
