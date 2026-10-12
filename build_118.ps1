$env:JAVA_HOME = "$env:APPDATA\.minecraft\runtime\java-runtime-delta"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
Set-Location D:\player-tracker
$g = Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-9.4.1-bin\arn2x92ynaizyzdaamcbpbhtj\gradle-9.4.1\bin\gradle.bat'
& $g build --daemon 2>&1 | Out-File D:\build_118.txt -Encoding utf8
Get-Content D:\build_118.txt -Tail 6 | Out-File D:\build_118_tail.txt -Encoding utf8
