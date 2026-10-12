# 重建 Modrinth 1.1.3~1.1.7：按各版本对应提交构建内嵌正确版本号的 jar，删旧传新
$ErrorActionPreference = 'Continue'
$repo = 'D:\player-tracker'
$tok  = 'mrp_HhdbSQhqUdMNWM4Gl6nEpt6Oz2bIVVvXdQMnqgoayoVHdNGUaB1kbUv3Cn47'
$UA   = 'glowtrack/1.0.0'

# 版本 -> 对应提交
$map = [ordered]@{
  '1.1.3' = '512cc7e'
  '1.1.4' = 'd30662e'
  '1.1.5' = 'dc6297d'
  '1.1.6' = 'e208d95'
  '1.1.7' = '2faf8fd'
}

function ApiGet($url) {
  return curl.exe -s --noproxy '*' -H "Authorization: $tok" -H "User-Agent: $UA" $url
}

# 1) 拉取线上版本，取 id + changelog + dependencies
$raw = ApiGet 'https://api.modrinth.com/v2/project/glowtrack/version'
$raw | Out-File -FilePath "$repo\.ver_cache.json" -Encoding utf8
$all = Get-Content "$repo\.ver_cache.json" -Raw | ConvertFrom-Json
$ids = @{}
$changelogs = @{}
$deps = @{}
foreach($x in $all){
  if($map.Contains($x.version_number)){
    $ids[$x.version_number] = $x.id
    $changelogs[$x.version_number] = $x.changelog
    $deps[$x.version_number] = $x.dependencies
  }
}

# 2) 记录当前分支状态，逐版本 checkout 构建
$origBranch = 'main'
$built = @{}
foreach($ver in $map.Keys){
  $commit = $map[$ver]
  git -C $repo checkout $commit 2>&1 | Out-Null
  $bg = "D:\player-tracker\build.gradle"
  $bg = Join-Path D:\player-tracker 'build.gradle'
  $c = [System.IO.File]::ReadAllText($bg)
  $c = [regex]::Replace($c, "version = '[^']+'", "version = '$ver'")
  [System.IO.File]::WriteAllText($bg, $c, [System.Text.UTF8Encoding]::new($false))
  # 构建
  $out = powershell -ExecutionPolicy Bypass -File (Join-Path D:\player-tracker 'build_jar.ps1') 2>&1 | Out-String
  if($out -notmatch 'BUILD SUCCESSFUL'){ throw "build $ver failed" }
  $jar = Get-ChildItem (Join-Path D:\player-tracker 'build\libs') -Filter "glowtrack-$ver.jar" | Select-Object -First 1
  if(-not $jar){ throw "jar $ver not found" }
  $built[$ver] = $jar.FullName
  Write-Host "built $ver -> $($jar.Name)"
}
# 回到 main，恢复版本号为 1.1.7（最新）
git -C $repo checkout $origBranch 2>&1 | Out-Null
$bg = Join-Path D:\player-tracker 'build.gradle'
$c = [System.IO.File]::ReadAllText($bg)
$c = [regex]::Replace($c, "version = '[^']+'", "version = '1.1.7'")
[System.IO.File]::WriteAllText($bg, $c, [System.Text.UTF8Encoding]::new($false))

# 3) 删旧版本 + 重建
foreach($ver in $map.Keys){
  $id = $ids[$ver]
  if($id){
    $del = curl.exe -s --noproxy '*' -X DELETE -H "Authorization: $tok" -H "User-Agent: $UA" "https://api.modrinth.com/v2/version/$id"
    Write-Host "deleted $ver ($id): $del"
  }
  # multipart: data + file
  $dataObj = [ordered]@{
    project_id     = 'mpdIYgGq'
    version_number = $ver
    version_title  = "GlowTrack $ver"
    version_type   = 'release'
    game_versions  = @('1.21.1')
    loaders        = @('neoforge')
    featured       = ($ver -eq '1.1.7')
    changelog      = $changelogs[$ver]
    file_parts     = @("glowtrack-$ver.jar")
    dependencies   = @(
      @{ project_id = 'NcUtCpym'; dependency_type = 'optional' },
      @{ project_id = '1bokaNcj'; dependency_type = 'optional' }
    )
  }
  $dataJson = $dataObj | ConvertTo-Json -Depth 8 -Compress
  $boundary = '----dsh' + [guid]::NewGuid().ToString('N')
  $CRLF = "`r`n"
  $ms = New-Object System.IO.MemoryStream
  $w = New-Object System.IO.BinaryWriter($ms)
  $w.Write([System.Text.Encoding]::UTF8.GetBytes(
    "--$boundary$CRLF" +
    "Content-Disposition: form-data; name=`"data`"$CRLF" +
    "Content-Type: application/json$CRLF$CRLF" +
    $dataJson + $CRLF))
  $jarName = "glowtrack-$ver.jar"
  $w.Write([System.Text.Encoding]::UTF8.GetBytes(
    "--$boundary$CRLF" +
    "Content-Disposition: form-data; name=`"$jarName`"; filename=`"$jarName`"$CRLF" +
    "Content-Type: application/java-archive$CRLF$CRLF"))
  $w.Write([System.IO.File]::ReadAllBytes($built[$ver]))
  $w.Write([System.Text.Encoding]::UTF8.GetBytes("$CRLF--$boundary--$CRLF"))
  $w.Flush()
  $tmp = Join-Path $env:TEMP "mr_rebuild_$ver.bin"
  [System.IO.File]::WriteAllBytes($tmp, $ms.ToArray())
  $r = curl.exe -s --noproxy '*' -X POST `
    -H "Authorization: $tok" -H "User-Agent: $UA" `
    -H "Content-Type: multipart/form-data; boundary=$boundary" `
    --data-binary "@$tmp" `
    'https://api.modrinth.com/v2/version' -w "`nHTTP:%{http_code}"
  Remove-Item $tmp -Force
  if($r -match 'HTTP:200'){ Write-Host "re-uploaded $ver OK" }
  else { Write-Host "FAILED $ver : $($r -join ' ')" }
}
Write-Host 'REBUILD DONE'
