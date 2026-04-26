param(
    [string]$AppVersion = "1.2.6",
    [string]$AppName = "MT5 Backtester"
)

$ErrorActionPreference = "Stop"

Write-Host "Creating install directory..."
New-Item -ItemType Directory -Force -Path "install" | Out-Null

Write-Host "Building project via Maven (clean package)..."
$mavenOutput = mvn clean package 
if ($LASTEXITCODE -ne 0) {
    Write-Host "Maven build failed!" -ForegroundColor Red
    Write-Host $mavenOutput
    exit $LASTEXITCODE
}

$jarPath = "target\mt5-backtester-${AppVersion}.jar"
if (-Not (Test-Path $jarPath)) {
    Write-Host "JAR file not found: $jarPath" -ForegroundColor Red
    exit 1
}

Write-Host "Isolating JAR for packaging..."
New-Item -ItemType Directory -Force -Path "target\jpackage-input" | Out-Null
Copy-Item -Path $jarPath -Destination "target\jpackage-input\$($AppName).jar" -Force

$jpackagePath = "C:\Program Files\Java\jdk-21\bin\jpackage.exe"

Write-Host "Step 1: Generating App Image..."
if (Test-Path "target\app-image") { Remove-Item -Recurse -Force "target\app-image" }
& $jpackagePath --type app-image `
         --dest target\app-image `
         --name $AppName `
         --app-version $AppVersion `
         --input target\jpackage-input `
         --main-jar "$($AppName).jar" `
         --main-class com.backtester.Main `
         --icon "src\main\resources\app_icon.ico"

if ($LASTEXITCODE -ne 0) {
    Write-Host "App Image generation failed with code $LASTEXITCODE." -ForegroundColor Red
    exit 1
}

Write-Host "Copying documentation to the root of the App Image..."
Copy-Item -Path "doc" -Destination "target\app-image\$AppName\doc" -Recurse -Force

Write-Host "Cleaning up internal documentation..."
$linkedinFile = "target\app-image\$AppName\doc\linkedin_blogartikel.md"
if (Test-Path $linkedinFile) { Remove-Item -Force $linkedinFile }

Write-Host "Step 2: Building MSI from App Image..."
& $jpackagePath --type msi `
         --dest install `
         --name $AppName `
         --app-version $AppVersion `
         --app-image "target\app-image\$AppName" `
         --resource-dir "src\main\resources\installer" `
         --win-dir-chooser `
         --win-shortcut `
         --win-shortcut-prompt `
         --win-menu `
         --win-menu-group "AntiGravity Software" `
         --icon "src\main\resources\app_icon.ico"

if ($LASTEXITCODE -eq 0) {
    Write-Host "Success! The installer was placed in the 'install' directory." -ForegroundColor Green
} else {
    Write-Host "Packaging failed with code $LASTEXITCODE." -ForegroundColor Red
}
