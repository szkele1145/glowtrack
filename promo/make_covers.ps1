# 生成 GlowTrack 宣传封面 (16:9 与 4:3) - anything2explainer 黑底MG视觉体系
# 黑色画布 + 点阵波幕底 + 白线条图形 + 紫色重点 + 超粗黑体 + 彩虹轨迹主角
Add-Type -AssemblyName System.Drawing

function HSVColor([double]$h, [double]$s, [double]$v, [int]$a = 255) {
    $c = $v * $s
    $x = $c * (1 - [math]::Abs((($h / 60) % 2) - 1))
    $m = $v - $c
    switch ([int]([math]::Floor($h / 60) % 6)) {
        0 { $r = $c; $g = $x; $b = 0 }
        1 { $r = $x; $g = $c; $b = 0 }
        2 { $r = 0; $g = $c; $b = $x }
        3 { $r = 0; $g = $x; $b = $c }
        4 { $r = $x; $g = 0; $b = $c }
        5 { $r = $c; $g = 0; $b = $x }
    }
    return [System.Drawing.Color]::FromArgb($a, [int](255 * ($r + $m)), [int](255 * ($g + $m)), [int](255 * ($b + $m)))
}

function New-Cover([int]$W, [int]$H, [string]$outPath, [double]$titleScale, [double]$subScale) {
    $bmp = New-Object System.Drawing.Bitmap($W, $H)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

    # 1. 纯黑画布
    $g.Clear([System.Drawing.Color]::FromArgb(255, 5, 5, 8))

    # 2. 点阵波幕底（整齐点阵，远处淡）
    $dot = 28
    for ($gy = 0; $gy -lt $H; $gy += $dot) {
        for ($gx = 0; $gx -lt $W; $gx += $dot) {
            # 底部波浪高度调制透明度，模拟点阵波
            $wave = [math]::Sin($gx * 0.012) * 30 + [math]::Sin($gy * 0.02 + 1.2) * 18
            $distFromTrackY = [math]::Abs($gy - ($H * 0.42 + $wave))
            $fade = [math]::Max(0.06, 0.30 - $distFromTrackY / ($H * 1.6))
            $alpha = [int](255 * $fade)
            if ($alpha -gt 2) {
                $b = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb($alpha, 120, 130, 150))
                $g.FillRectangle($b, $gx, $gy, 2, 2)
                $b.Dispose()
            }
        }
    }

    # 3. 主角轨迹：S形彩虹发光线（左下→右上，带三层辉光）
    $pts = New-Object 'System.Collections.Generic.List[System.Drawing.PointF]'
    $n = 240
    for ($i = 0; $i -le $n; $i++) {
        $t = $i / $n
        $x = $W * (0.06 + 0.86 * $t)
        $y = $H * (0.78 - 0.42 * $t) + $H * 0.055 * [math]::Sin($t * [math]::PI * 2.4 + 0.7)
        $pts.Add((New-Object System.Drawing.PointF([float]$x, [float]$y)))
    }
    foreach ($layer in @(@(1.0, 18, 24), @(0.5, 9, 60), @(0.22, 4, 255))) {
        $wFactor = $layer[0]; $aw = [int]$layer[2]
        $pen = New-Object System.Drawing.Pen([System.Drawing.Color]::Black)
        $pen.Width = [math]::Max(2.0, $H * 0.010 * $wFactor)
        $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
        $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
        for ($i = 0; $i -lt $pts.Count - 1; $i++) {
            $t = $i / ($pts.Count - 1.0)
            $pen.Color = HSVColor (330 * $t) 0.85 1.0 $aw
            $g.DrawLine($pen, $pts[$i], $pts[$i + 1])
        }
        $pen.Dispose()
    }

    # 4. 轨迹末端脉冲节点（紫光环 + 白心）
    $pEnd = $pts[$pts.Count - 1]
    $g.DrawEllipse((New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(160, 155, 120, 255), 3)), $pEnd.X - 26, $pEnd.Y - 26, 52, 52)
    $g.FillEllipse((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(200, 155, 120, 255))), $pEnd.X - 10, $pEnd.Y - 10, 20, 20)
    $g.FillEllipse((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)), $pEnd.X - 4, $pEnd.Y - 4, 8, 8)

    # 5. 时间刻度点（沿轨迹散布小白点，呼应时间标签）
    foreach ($t in @(0.18, 0.42, 0.66)) {
        $p = $pts[[int]($t * $n)]
        $g.FillEllipse((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(220, 255, 255, 255))), $p.X - 4, $p.Y - 4, 8, 8)
        $g.DrawEllipse((New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(120, 255, 255, 255), 1.5)), $p.X - 9, $p.Y - 9, 18, 18)
    }

    # 6. 标题 GlowTrack（超粗，白色 + 紫柔光描边）
    $titleSize = [float]($H * 0.16 * $titleScale)
    $titleFont = New-Object System.Drawing.Font("Arial Black", $titleSize, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $titleText = "GLOWTRACK"
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = [System.Drawing.StringAlignment]::Center
    $titleRect = New-Object System.Drawing.RectangleF(0, ($H * 0.30), $W, ($H * 0.22))
    # 紫柔光：多层半透明紫（直接多层绘制，不用 Pen）
    foreach ($glow in @(26, 14, 6)) {
        $gb = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb($glow, 155, 120, 255))
        $g.DrawString($titleText, $titleFont, $gb, $titleRect, $fmt)
        $gb.Dispose()
    }
    # 主字白
    $g.DrawString($titleText, $titleFont, (New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)), $titleRect, $fmt)
    $titleFont.Dispose()

    # 7. 中文副题
    $subSize = [float]($H * 0.050 * $subScale)
    $cnFont = New-Object System.Drawing.Font("Microsoft YaHei UI", $subSize, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $subRect = New-Object System.Drawing.RectangleF(0, ($H * 0.545), $W, ($H * 0.09))
    $g.DrawString("让每一个脚印都发光", $cnFont, (New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(230, 200, 205, 220))), $subRect, $fmt)

    # 8. 底部小字（特性条：白点分隔）
    $featSize = [float]($H * 0.028)
    $featFont = New-Object System.Drawing.Font("Microsoft YaHei UI", $featSize, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
    $featText = "3D轨迹  ·  速度热力  ·  穿墙可见  ·  时间回放  ·  地图联动"
    $featRect = New-Object System.Drawing.RectangleF(0, ($H * 0.88), $W, ($H * 0.05))
    $g.DrawString($featText, $featFont, (New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(150, 140, 145, 160))), $featRect, $fmt)

    # 9. 顶部小字：NeoForge 1.21.1
    $verText = "NeoForge 1.21.1"
    $verFont = New-Object System.Drawing.Font("Consolas", [float]($H * 0.026), [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
    $sz = $g.MeasureString($verText, $verFont)
    $verX = $W - $sz.Width - [float]($H * 0.04)
    $verY = [float]($H * 0.035)
    $g.DrawString($verText, $verFont, (New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(120, 140, 145, 160))), $verX, $verY)

    $g.Dispose()
    $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    "saved: $outPath ($W x $H, $((Get-Item $outPath).Length) bytes)"
}

New-Cover 1920 1080 "D:\player-tracker\promo\cover_16x9.png" 1.0 1.0
New-Cover 1440 1080 "D:\player-tracker\promo\cover_4x3.png" 1.15 1.1
