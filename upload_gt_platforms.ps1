# GlowTrack 多平台 Modrinth 上传（1.4.8 的 7 个平台）
#
# 与仓库根目录的 upload_modrinth_v2.ps1 的区别：
#   * 平台参数不写死 —— 每个平台一次调用，game_versions / loaders / 依赖各自不同
#   * 仓库路径不再硬编码到 D:\player-tracker（那是旧路径）
#   * Xaero 依赖标 optional（运行时可选：没装照常跑，装了才有地图叠加）
#     Fabric 平台额外依赖 fabric-api（required）
#   * jar 名用真实产物名（glowtrack-1.4.8-<mc>-<loader>.jar）
#
# 用法（先 -DryRun 看一眼要发什么，确认后再真发）：
#   .\upload_gt_platforms.ps1 -DryRun
#   .\upload_gt_platforms.ps1
#   .\upload_gt_platforms.ps1 -Only '1.19.4-fabric'      # 只发一个
#
# ⚠️ 这是公开发布动作。dry-run 不发请求。

param(
    [string]$Version = '1.4.8',
    [switch]$DryRun,
    [string[]]$Only = @(),
    [string]$Changelog = ''
)

$ErrorActionPreference = 'Stop'

$repo   = 'D:\tools\gt_src'
$dist   = 'D:\tools\gt_dist'
$tok = $env:MODRINTH_TOKEN
if (-not $tok) { throw "需要环境变量 MODRINTH_TOKEN（勿写进仓库）" }
$UA     = 'glowtrack/1.0.0'
$project = 'mpdIYgGq'

# Modrinth 上的依赖 project id
$XaeroWorldMap = 'NcUtCpym'
$XaeroMinimap  = '1bokaNcj'
$FabricApi     = 'P7dR8mSH'

if (-not $Changelog) {
    $Changelog = @"
GlowTrack $Version — 新增 7 个平台的移植版本。

新增平台：Fabric 1.18.2 / 1.19.2 / 1.19.4 / 1.21.8 / 1.21.11，Forge 1.19.4 / 1.20.1。

各平台按各自的 API 世代重写了网络层与 GUI：
- 网络：1.21.x 用 CustomPacketPayload + StreamCodec；1.19.4 用 FabricPacket + PacketType；
  1.19.2 / 1.18.2 用老通道模型；Forge 用 SimpleChannel + 信封封装。
- GUI：1.20+ 用 GuiGraphics；1.19.x / 1.18.2 用 PoseStack；1.18.2 另需 TextComponent。
- 1.20.1 及更早的 RenderStateShard 常量是包内可见，双层渲染退化为单层
  （轨迹照画，失去主线半透明 + 光晕的层次分离）。

Xaero 联动：小地图叠加在 1.18.2 / 1.19.2 / 1.19.4 / Forge 两版可用；
世界地图叠加在 1.19.x 可用；1.21.8 / 1.21.11 暂不支持（需按 Xaero 的 GPU-buffer
架构重写坐标变换）。Xaero 依赖为可选，不装照常运行。
"@
}

# 平台表：mc 版本 / loader / 产物名 / 依赖
$platforms = @(
    @{ key = '1.21.11-fabric'; mc = '1.21.11'; loader = 'fabric'; jar = "glowtrack-$Version-1.21.11-fabric.jar" },
    @{ key = '1.21.8-fabric';  mc = '1.21.8';  loader = 'fabric'; jar = "glowtrack-$Version-1.21.8-fabric.jar" },
    @{ key = '1.19.4-fabric';  mc = '1.19.4';  loader = 'fabric'; jar = "glowtrack-$Version-1.19.4-fabric.jar" },
    @{ key = '1.19.2-fabric';  mc = '1.19.2';  loader = 'fabric'; jar = "glowtrack-$Version-1.19.2-fabric.jar" },
    @{ key = '1.18.2-fabric';  mc = '1.18.2';  loader = 'fabric'; jar = "glowtrack-$Version-1.18.2-fabric.jar" },
    @{ key = '1.20.1-forge';   mc = '1.20.1';  loader = 'forge';  jar = "glowtrack-$Version-1.20.1-forge.jar" },
    @{ key = '1.19.4-forge';   mc = '1.19.4';  loader = 'forge';  jar = "glowtrack-$Version-1.19.4-forge.jar" }
)

function Upload-One($p) {
    $jarPath = Join-Path $dist $p.jar
    if (-not (Test-Path $jarPath)) {
        Write-Host "SKIP  $($p.key): 找不到 $jarPath"
        return $false
    }

    $versionNumber = "$Version-$($p.mc)-$($p.loader)"
    $title         = "GlowTrack $Version for MC $($p.mc) ($($p.loader))"

    # 依赖：fabric-api 必需；两个 Xaero 都是可选
    $deps = @()
    if ($p.loader -eq 'fabric') {
        $deps += @{ project_id = $FabricApi; dependency_type = 'required' }
    }
    $deps += @{ project_id = $XaeroWorldMap; dependency_type = 'optional' }
    $deps += @{ project_id = $XaeroMinimap;  dependency_type = 'optional' }

    $dataObj = [ordered]@{
        project_id     = $project
        version_number = $versionNumber
        version_title  = $title
        version_type   = 'release'
        game_versions  = @($p.mc)
        loaders        = @($p.loader)
        featured       = $false
        changelog      = $Changelog
        file_parts     = @($p.jar)
        dependencies   = $deps
    }
    $dataJson = $dataObj | ConvertTo-Json -Depth 8 -Compress

    if ($DryRun) {
        Write-Host "PLAN  $versionNumber"
        Write-Host "      jar    : $($p.jar)  ($([math]::Round((Get-Item $jarPath).Length/1KB)) KB)"
        Write-Host "      game   : $($p.mc)   loader: $($p.loader)"
        Write-Host "      deps   : $(($deps | ForEach-Object { "$($_.dependency_type):$($_.project_id)" }) -join ', ')"
        return $true
    }

    $boundary = '----dsh' + [guid]::NewGuid().ToString('N')
    $CRLF = "`r`n"
    $ms = New-Object System.IO.MemoryStream
    $w = New-Object System.IO.BinaryWriter($ms)

    $part1 = "--$boundary$CRLF" +
        "Content-Disposition: form-data; name=`"data`"$CRLF" +
        "Content-Type: application/json$CRLF$CRLF" +
        $dataJson + $CRLF
    $part2 = "--$boundary$CRLF" +
        "Content-Disposition: form-data; name=`"$($p.jar)`"; filename=`"$($p.jar)`"$CRLF" +
        "Content-Type: application/java-archive$CRLF$CRLF"
    $partEnd = "$CRLF--$boundary--$CRLF"

    $w.Write([System.Text.Encoding]::UTF8.GetBytes($part1))
    $w.Write([System.Text.Encoding]::UTF8.GetBytes($part2))
    $w.Write([System.IO.File]::ReadAllBytes($jarPath))
    $w.Write([System.Text.Encoding]::UTF8.GetBytes($partEnd))
    $w.Flush()

    $tmp = Join-Path $env:TEMP ("mr_" + [guid]::NewGuid().ToString('N') + ".bin")
    [System.IO.File]::WriteAllBytes($tmp, $ms.ToArray())

    try {
        $r = curl.exe -s --noproxy '*' --ssl-no-revoke -X POST `
            -H "Authorization: $tok" -H "User-Agent: $UA" `
            -H "Content-Type: multipart/form-data; boundary=$boundary" `
            --data-binary "@$tmp" `
            'https://api.modrinth.com/v2/version' -w "`nHTTP:%{http_code}"
        $out = $r -join "`n"
        if ($out -match 'HTTP:(200|201)') {
            $id = ([regex]::Match($out, '"id"\s*:\s*"([^"]+)"')).Groups[1].Value
            Write-Host "OK    $versionNumber  id=$id"
            return $true
        } else {
            Write-Host "FAIL  $versionNumber"
            Write-Host "      $($out -replace "`n", ' ')"
            return $false
        }
    } finally {
        Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    }
}

$targets = $platforms
if ($Only.Count -gt 0) {
    $targets = $platforms | Where-Object { $Only -contains $_.key }
    if ($targets.Count -eq 0) { throw "没有匹配的平台，可用: $(($platforms | ForEach-Object { $_.key }) -join ', ')" }
}

Write-Host "GlowTrack $Version -> project $project$(if ($DryRun) { '  [DRY RUN]' })"
Write-Host ""

$ok = 0; $fail = 0
foreach ($p in $targets) {
    if (Upload-One $p) { $ok++ } else { $fail++ }
    if (-not $DryRun) { Start-Sleep -Milliseconds 700 }   # 轻节流，避免触发限流
}

Write-Host ""
Write-Host "完成：成功 $ok / 失败 $fail$(if ($DryRun) { '（dry run，未真正上传）' })"
