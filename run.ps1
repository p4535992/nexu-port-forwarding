$ErrorActionPreference = 'Stop'
$jar = Join-Path $PSScriptRoot 'target\app\nexu-port-forwarding.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Run build.bat first (JDK 21 and Maven 3.9+).' }
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\javaw.exe' } else { (Get-Command javaw.exe -ErrorAction Stop).Source }
if (-not (Test-Path -LiteralPath $java)) { throw 'javaw.exe not found; check JAVA_HOME and JDK 21.' }
Start-Process -FilePath $java -ArgumentList @('-jar', ('"' + $jar + '"')) -WorkingDirectory $PSScriptRoot
