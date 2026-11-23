# Builds the backend into a self-contained app-image (bundled JRE 21, no Java required on the
# target machine). Produces jpackage-output\HarmonieDevBackend\HarmonieDevBackend.exe.
$ErrorActionPreference = "Stop"

$JDK21 = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory |
	Where-Object { $_.Name -like "jdk-21*" } |
	Select-Object -First 1 -ExpandProperty FullName
if (-not $JDK21) {
	throw "Eclipse Temurin JDK 21 not found under C:\Program Files\Eclipse Adoptium. Install it first (winget install --id EclipseAdoptium.Temurin.21.JDK)."
}

$env:JAVA_HOME = $JDK21
$env:PATH = "$JDK21\bin;" + $env:PATH

$root = $PSScriptRoot
Set-Location $root

Write-Host "Building backend jar with JDK 21..."
& "$root\mvnw.cmd" -q -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }

$jar = Get-ChildItem "$root\target\*.jar" | Where-Object { $_.Name -notlike "*sources*" } | Select-Object -First 1

Remove-Item -Recurse -Force "$root\jpackage-input" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$root\jpackage-input" | Out-Null
Copy-Item $jar.FullName "$root\jpackage-input\app.jar" -Force

Remove-Item -Recurse -Force "$root\jpackage-output" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$root\jpackage-output" | Out-Null

Write-Host "Running jpackage..."
& "$JDK21\bin\jpackage.exe" `
	--type app-image `
	--input "$root\jpackage-input" `
	--main-jar app.jar `
	--name HarmonieDevBackend `
	--app-version 1.0.0 `
	--runtime-image "$JDK21" `
	--dest "$root\jpackage-output" `
	--vendor "Harmonie-dev"
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

# Strip the JDK's dev-tool launchers (javac, jshell, keytool, ...) — nothing at runtime
# spawns them (the native launcher loads the JVM in-process via jli.dll/jvm.dll), and their
# presence as plain-copied .exe files breaks desktop packaging in some sandboxed environments.
$runtimeBin = "$root\jpackage-output\HarmonieDevBackend\runtime\bin"
Get-ChildItem "$runtimeBin\*.exe" | Remove-Item -Force

# Mirror the app-image into the Tauri project itself (no ".." in the path Tauri globs), so its
# bundle.resources glob preserves the app/ and runtime/ subfolder structure the native launcher
# needs, instead of flattening everything into one directory.
$desktopCopy = "$root\..\desktop\src-tauri\backend-app"
Remove-Item -Recurse -Force $desktopCopy -ErrorAction SilentlyContinue
Copy-Item "$root\jpackage-output\HarmonieDevBackend" $desktopCopy -Recurse -Force

Write-Host "Done: $root\jpackage-output\HarmonieDevBackend\HarmonieDevBackend.exe"
Write-Host "Mirrored to: $desktopCopy"
