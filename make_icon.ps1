# 生成 GlowTrack 模组图标 (512x512)：深色底 + 彩虹发光轨迹线
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

$size = 512
$bmp = New-Object System.Drawing.Bitmap($size, $size)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias

# 圆角矩形背景 #0D1117
$r = 100
$path = New-Object System.Drawing.Drawing2D.GraphicsPath
$path.AddArc(0, 0, 2 * $r, 2 * $r, 180, 90)
$path.AddArc($size - 2 * $r, 0, 2 * $r, 2 * $r, 270, 90)
$path.AddArc($size - 2 * $r, $size - 2 * $r, 2 * $r, 2 * $r, 0, 90)
$path.AddArc(0, $size - 2 * $r, 2 * $r, 2 * $r, 90, 90)
$path.CloseFigure()
$g.FillPath((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 13, 17, 23))), $path)

# 内边框 #30363D
$g.DrawPath((New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 48, 54, 61), 10)), $path)

# 轨迹采样点：从左下到右上的 S 形曲线
$pts = New-Object System.Collections.Generic.List[System.Drawing.PointF]
for ($i = 0; $i -le 200; $i++) {
    $t = $i / 200.0
    $x = 70 + 372 * $t
    $y = 400 - 250 * $t + 55 * [math]::Sin($t * [math]::PI * 2.2 + 0.6)
    $pts.Add((New-Object System.Drawing.PointF([float]$x, [float]$y)))
}

# 三层发光描边（彩虹色随路径推进）
foreach ($layer in @(@{ w = 40; a = 26 }, @{ w = 20; a = 55 }, @{ w = 8; a = 255 })) {
    $pen = New-Object System.Drawing.Pen([System.Drawing.Color]::Black)
    $pen.Width = $layer.w
    for ($i = 0; $i -lt $pts.Count - 1; $i++) {
        $t = $i / ($pts.Count - 1.0)
        $pen.Color = HSVColor (360 * $t) 0.85 1.0 $layer.a
        $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
        $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
        $g.DrawLine($pen, $pts[$i], $pts[$i + 1])
    }
    $pen.Dispose()
}

# 脉冲节点：三个白心圆点 + 色环
foreach ($t in @(0.22, 0.52, 0.82)) {
    $idx = [int]($t * ($pts.Count - 1))
    $p = $pts[$idx]
    $ring = HSVColor (360 * $t) 0.85 1.0 120
    $g.FillEllipse((New-Object System.Drawing.SolidBrush($ring)), $p.X - 22, $p.Y - 22, 44, 44)
    $g.FillEllipse((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)), $p.X - 9, $p.Y - 9, 18, 18)
}

# 起点（绿）终点（蓝）标记
$p0 = $pts[0]; $p1 = $pts[$pts.Count - 1]
$g.FillEllipse((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 63, 185, 80))), $p0.X - 16, $p0.Y - 16, 32, 32)
$g.FillEllipse((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)), $p0.X - 5, $p0.Y - 5, 10, 10)
$g.FillEllipse((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 88, 166, 255))), $p1.X - 16, $p1.Y - 16, 32, 32)
$g.FillEllipse((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)), $p1.X - 5, $p1.Y - 5, 10, 10)

$g.Dispose()
$out = "D:\player-tracker\icon.png"
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
"saved: $out ($((Get-Item $out).Length) bytes)"
