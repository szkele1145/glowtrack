$env:JAVA_HOME = 'D:\sfx\jdk\jdk-21.0.12.1+1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$gradle = Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-9.4.1-bin\arn2x92ynaizyzdaamcbpbhtj\gradle-9.4.1\bin\gradle.bat'
Set-Location D:\player-tracker
& $gradle runServer --no-daemon
exit $LASTEXITCODE
