param([string]$Version = "1.2.0", [string]$Changelog = "Update", [string]$JarPath = "")
$ErrorActionPreference = 'Stop'
$repo = 'D:\player-tracker'
$tok  = 'mrp_HhdbSQhqUdMNWM4Gl6nEpt6Oz2bIVVvXdQMnqgoayoVHdNGUaB1kbUv3Cn47'
$UA   = 'glowtrack/1.0.0'
if (-not $JarPath) { $JarPath = "$repo\build\libs\glowtrack-$Version.jar" }

$dataObj = [ordered]@{
    project_id     = 'mpdIYgGq'
    version_number = $Version
    version_title  = "GlowTrack $Version"
    version_type   = 'release'
    game_versions  = @('1.21.1')
    loaders        = @('neoforge')
    featured       = $true
    changelog      = $Changelog
    file_parts     = @("glowtrack-$Version.jar")
    dependencies   = @(
        @{ project_id = 'NcUtCpym'; dependency_type = 'required' },
        @{ project_id = '1bokaNcj'; dependency_type = 'required' }
    )
}
$dataJson = $dataObj | ConvertTo-Json -Depth 8 -Compress
$jarName = "glowtrack-$Version.jar"
$boundary = '----dsh' + [guid]::NewGuid().ToString('N')
$CRLF = "`r`n"

$ms = New-Object System.IO.MemoryStream
$w = New-Object System.IO.BinaryWriter($ms)
$part1 = "--$boundary$CRLF" +
    "Content-Disposition: form-data; name=`"data`"$CRLF" +
    "Content-Type: application/json$CRLF$CRLF" +
    $dataJson + $CRLF
$part2 = "--$boundary$CRLF" +
    "Content-Disposition: form-data; name=`"$jarName`"; filename=`"$jarName`"$CRLF" +
    "Content-Type: application/java-archive$CRLF$CRLF"
$partEnd = "$CRLF--$boundary--$CRLF"

$w.Write([System.Text.Encoding]::UTF8.GetBytes($part1))
$w.Write([System.Text.Encoding]::UTF8.GetBytes($part2))
$w.Write([System.IO.File]::ReadAllBytes($JarPath))
$w.Write([System.Text.Encoding]::UTF8.GetBytes($partEnd))
$w.Flush()

$tmp = Join-Path $env:TEMP "mr_upload_$Version.bin"
[System.IO.File]::WriteAllBytes($tmp, $ms.ToArray())

$r = curl.exe -s --noproxy '*' -X POST `
    -H "Authorization: $tok" -H "User-Agent: $UA" `
    -H "Content-Type: multipart/form-data; boundary=$boundary" `
    --data-binary "@$tmp" `
    'https://api.modrinth.com/v2/version' -w "`nHTTP:%{http_code}"
Remove-Item $tmp -Force
$out = $r -join "`n"
if ($out -match 'HTTP:200') { Write-Host "OK: version $Version uploaded" }
else { Write-Host "FAILED: $out" }
