$env:JAVA_HOME = "$env:APPDATA\.minecraft\runtime\java-runtime-delta"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$gradle = Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-9.4.1-bin\arn2x92ynaizyzdaamcbpbhtj\gradle-9.4.1\bin\gradle.bat'
Set-Location D:\player-tracker
& $gradle runClient --daemon
exit $LASTEXITCODE
