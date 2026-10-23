# Modrinth 上传脚本（PowerShell + curl 直连，绕过本地代理 65532 的 Content-Type 改写）
# 用法: powershell -ExecutionPolicy Bypass -File .\upload_modrinth.ps1 [-Version 1.0.1] [-Changelog "更新说明"]
# 已建成项目: glowtrack (ID mpdIYgGq)，后续发版只需 POST /v2/version
param(
    [string]$Version = "1.0.0",
    [string]$Changelog = "Update",
    [string]$ProjectId = "mpdIYgGq",
    [string]$JarPath = "D:\player-tracker\build\libs\glowtrack-1.0.0.jar"
)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$tok = $env:MODRINTH_TOKEN
if (-not $tok) { throw "缺少环境变量 MODRINTH_TOKEN" }
$jarName = Split-Path $JarPath -Leaf
$sha1 = (Get-FileHash $JarPath -Algorithm SHA1).Hash.ToLower()

$data = [ordered]@{
    project_id     = $ProjectId
    version_number = $Version
    version_title  = "GlowTrack $Version"
    version_type   = "release"
    game_versions  = @("1.21.1")
    loaders        = @("neoforge")
    featured       = $true
    changelog      = $Changelog
    file_parts     = @($jarName)
    dependencies   = @(
        @{ project_id = "NcUtCpym"; dependency_type = "optional" },  # Xaero's World Map
        @{ project_id = "1bokaNcj"; dependency_type = "optional" }   # Xaero's Minimap
    )
}
$dataJson = $data | ConvertTo-Json -Depth 8 -Compress

$boundary = "----dsh$([guid]::NewGuid().ToString('N'))"
$CRLF = "`r`n"
$ms = New-Object System.IO.MemoryStream
$w = New-Object System.IO.BinaryWriter($ms)
$w.Write([System.Text.Encoding]::UTF8.GetBytes(
    "--$boundary$CRLF" +
    "Content-Disposition: form-data; name=`"data`"$CRLF" +
    "Content-Type: application/json$CRLF$CRLF" +
    $dataJson + $CRLF))
$w.Write([System.Text.Encoding]::UTF8.GetBytes(
    "--$boundary$CRLF" +
    "Content-Disposition: form-data; name=`"$jarName`"; filename=`"$jarName`"$CRLF" +
    "Content-Type: application/java-archive$CRLF$CRLF"))
$w.Write([System.IO.File]::ReadAllBytes($JarPath))
$w.Write([System.Text.Encoding]::UTF8.GetBytes("$CRLF--$boundary--$CRLF"))
$w.Flush()

$tmp = Join-Path $env:TEMP "mr_upload_$([guid]::NewGuid().ToString('N')).bin"
[System.IO.File]::WriteAllBytes($tmp, $ms.ToArray())

$r = curl.exe -s --noproxy '*' -X POST `
    -H "Authorization: $tok" -H "User-Agent: glowtrack/1.0.0" `
    -H "Content-Type: multipart/form-data; boundary=$boundary" `
    --data-binary "@$tmp" `
    "https://api.modrinth.com/v2/version" -w "`nHTTP:%{http_code}"
Remove-Item $tmp -Force -EA SilentlyContinue

$out = $r -join "`n"
if ($out -match 'HTTP:200') {
    Write-Host "✅ 版本 $Version 上传成功: https://modrinth.com/mod/glowtrack/version/$Version"
} else {
    Write-Host "❌ 上传失败:"; $out
}
