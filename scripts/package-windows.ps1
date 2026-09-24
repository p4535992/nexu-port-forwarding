param([ValidatePattern('^\d+\.\d+\.\d+$')][string]$Version = '1.2.1')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$inputDir = Join-Path $root 'target\app'
$out = Join-Path $root 'target\package'
$assets = Join-Path $root 'target\release-assets'
$image = Join-Path $out 'NexuPortForwarding'
if (-not (Test-Path "$inputDir\nexu-port-forwarding.jar")) { throw 'Run mvn clean verify first.' }
if (Test-Path $image) { throw 'Clean target/package before packaging again.' }
New-Item -ItemType Directory -Force $out,$assets | Out-Null
function Invoke-Jpackage([string[]]$Arguments) {
    & "$env:JAVA_HOME\bin\jpackage.exe" @Arguments
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed ($LASTEXITCODE). Check Java 21 and WiX 3.x." }
}
Invoke-Jpackage @('--type','app-image','--name','NexuPortForwarding','--app-version',$Version,
    '--vendor','Nexu Port Forwarding','--description','Desktop SSH TCP tunnel manager',
    '--input',$inputDir,'--dest',$out,'--main-jar','nexu-port-forwarding.jar',
    '--main-class','it.nexu.forwarding.Launcher','--icon',"$root\src\main\resources\app-icon.ico",
    '--java-options','-Dfile.encoding=UTF-8',
    '--add-modules','java.base,java.desktop,java.logging,java.naming,java.management,java.rmi,java.security.jgss,java.security.sasl,java.sql,java.xml,jdk.crypto.ec,jdk.unsupported,jdk.unsupported.desktop,jdk.charsets,jdk.zipfs')
Copy-Item docs/DATA-AND-LOGS.txt "$image\LOGS.txt"
Copy-Item THIRD_PARTY_NOTICES.md $image
Copy-Item LICENSE $image
Copy-Item docs "$image\docs" -Recurse
foreach ($type in @('exe','msi')) {
    Invoke-Jpackage @('--type',$type,'--app-image',$image,'--app-version',$Version,'--dest',$out,
        '--win-menu','--win-shortcut','--win-dir-chooser','--win-per-user-install',
        '--win-upgrade-uuid','841d8480-e263-46e9-940f-a125e3078122')
    $package = Get-ChildItem $out -File -Filter "*.$type" | Select-Object -First 1
    if (-not $package) { throw "Missing $type package" }
    Copy-Item $package.FullName "$assets\nexu-port-forwarding-$Version-windows-x64.$type"
}
# Add the marker after installer creation, before archiving the portable image.
Copy-Item scripts/portable.properties "$image\portable.properties"
@'
@echo off
setlocal
REM The application reads portable.properties; do not override the saved preference.
start "" "%~dp0NexuPortForwarding.exe" %*
'@ | Set-Content "$image\start-portable.bat" -Encoding ascii
Compress-Archive -Path $image -DestinationPath "$assets\nexu-port-forwarding-$Version-windows-x64.zip"
Compress-Archive -Path $inputDir -DestinationPath "$assets\nexu-port-forwarding-$Version-windows-java.zip"
Write-Host "Windows artifacts prepared in $assets"
