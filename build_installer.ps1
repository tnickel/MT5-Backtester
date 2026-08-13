param(
    [string]$AppVersion = "1.2.6",
    [string]$AppName = "MT5 Backtester",
    [string]$JdkPath = (Join-Path $env:USERPROFILE ".jdk\jdk-25")
)

$ErrorActionPreference = "Stop"

if (-Not (Test-Path -LiteralPath $JdkPath -PathType Container)) {
    throw "JDK 25 directory not found: $JdkPath"
}

$resolvedJdkPath = (Resolve-Path -LiteralPath $JdkPath).Path
$jdkBinPath = Join-Path $resolvedJdkPath "bin"
$javaPath = Join-Path $jdkBinPath "java.exe"
$javacPath = Join-Path $jdkBinPath "javac.exe"
$jpackagePath = Join-Path $jdkBinPath "jpackage.exe"

foreach ($requiredTool in @($javaPath, $javacPath, $jpackagePath)) {
    if (-Not (Test-Path -LiteralPath $requiredTool -PathType Leaf)) {
        throw "Required JDK 25 tool not found: $requiredTool"
    }
}

$javaVersionOutput = (& $javaPath -version 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0) {
    throw "Could not determine the Java version from '$javaPath'. Output: $javaVersionOutput"
}

$javaVersionMatch = [regex]::Match($javaVersionOutput, 'version\s+"(?<major>\d+)')
if (-Not $javaVersionMatch.Success) {
    throw "Could not parse the Java version from '$javaPath'. Output: $javaVersionOutput"
}

$javaMajorVersion = [int]$javaVersionMatch.Groups["major"].Value
if ($javaMajorVersion -lt 25) {
    throw "JDK 25 or newer is required, but '$resolvedJdkPath' provides Java $javaMajorVersion."
}

# Ensure Maven and every Java subprocess use the same JDK as jpackage.
$env:JAVA_HOME = $resolvedJdkPath
$env:Path = "$jdkBinPath;$env:Path"
Write-Host "Using JDK $javaMajorVersion from $resolvedJdkPath"

Write-Host "Creating install directory..."
New-Item -ItemType Directory -Force -Path "install" | Out-Null

Write-Host "Building project via Maven (clean package)..."
& mvn clean package
if ($LASTEXITCODE -ne 0) {
    $mavenExitCode = $LASTEXITCODE
    Write-Host "Maven build failed with code $mavenExitCode." -ForegroundColor Red
    exit $mavenExitCode
}

$jarPath = "target\mt5-backtester-${AppVersion}.jar"
if (-Not (Test-Path $jarPath)) {
    Write-Host "JAR file not found: $jarPath" -ForegroundColor Red
    exit 1
}

Write-Host "Isolating JAR for packaging..."
New-Item -ItemType Directory -Force -Path "target\jpackage-input" | Out-Null
Copy-Item -Path $jarPath -Destination "target\jpackage-input\$($AppName).jar" -Force

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
