$env:JAVA_HOME = "$env:APPDATA\.minecraft\runtime\java-runtime-delta"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location D:\player-tracker
$g = Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-9.4.1-bin\arn2x92ynaizyzdaamcbpbhtj\gradle-9.4.1\bin\gradle.bat'
& $g classes --daemon 2>&1 | Select-String 'BUILD SUCCESSFUL|error|错误' | Select-Object -First 5 | Out-File D:\bc.txt
Get-Content D:\bc.txt
