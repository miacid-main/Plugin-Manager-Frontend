$ErrorActionPreference = "Stop"
$jdkBin = "C:\Program Files\Java\jdk-22\bin"
$root = Get-Location
$libs = "$root\libs"
$parentLibs = "..\libs"
$build = "$root\build_manual_chatcleaner"
$classes = "$build\classes"
$jarFile = "$build\libs\ChatCleaner-1.0.0.jar"

# Clean
if (Test-Path $build) { Remove-Item -Recurse -Force $build }
New-Item -ItemType Directory -Force -Path $libs | Out-Null
New-Item -ItemType Directory -Force -Path $classes | Out-Null
New-Item -ItemType Directory -Force -Path "$build\libs" | Out-Null

# Function to download
function Download-File($url, $output) {
    if (Test-Path $output) { return }
    Write-Host "Downloading $output..."
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $url -OutFile $output -UserAgent "Mozilla/5.0"
    } catch {
        Write-Warning "Failed to download $url"
        Write-Warning $_
    }
}

# Dependencies
# 1. Paper API
$paperJar = "$libs\paper-api.jar"
if (Test-Path "$parentLibs\paper-api.jar") { 
    $paperJar = "$parentLibs\paper-api.jar" 
} else {
    Download-File "https://api.papermc.io/v2/projects/paper/versions/1.21/builds/130/downloads/paper-1.21-130.jar" $paperJar
}

# 2. Adventure Libs & BungeeCord Chat
$advVersion = "4.17.0"
Download-File "https://repo1.maven.org/maven2/net/kyori/adventure-api/$advVersion/adventure-api-$advVersion.jar" "$libs\adventure-api.jar"
Download-File "https://repo1.maven.org/maven2/net/kyori/adventure-key/$advVersion/adventure-key-$advVersion.jar" "$libs\adventure-key.jar"
Download-File "https://repo1.maven.org/maven2/net/md-5/bungeecord-chat/1.16-R0.4/bungeecord-chat-1.16-R0.4.jar" "$libs\bungeecord-chat.jar"

# Construct Classpath
$cp = "$paperJar;$libs\adventure-api.jar;$libs\adventure-key.jar;$libs\bungeecord-chat.jar"

# Sources
$sources = Get-ChildItem -Recurse "src\main\java" -Filter "*.java" | Select-Object -ExpandProperty FullName
if (-not $sources) { Write-Error "No sources found"; exit 1 }
$sourcesFile = "$build\sources.txt"
# Quote paths and use forward slashes to avoid escape issues in @file
$sources | ForEach-Object { "`"$($_.Replace('\', '/'))`"" } | Set-Content $sourcesFile

# Compile
Write-Host "Compiling..."
Write-Host "Classpath: $cp"
# Force Java 21 (release 21) because server supports up to 65.0 (Java 21), but we are using JDK 22
& "$jdkBin\javac.exe" -d "$classes" -cp "$cp" -sourcepath "src\main\java" --release 21 "@$sourcesFile"
if ($LASTEXITCODE -ne 0) {  
    Write-Error "Compilation failed."
    exit 1 
}

# Resources
Copy-Item "src\main\resources\*" $classes

# Jar
Write-Host "Packaging..."
& "$jdkBin\jar.exe" cf $jarFile -C $classes .

Write-Host "Build Complete: $jarFile"
