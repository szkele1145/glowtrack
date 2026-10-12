import React from 'react';
import {useCurrentFrame} from 'remotion';
import {GlitchIn, clamp01, kf, rnd, FONT_HEAVY} from '../../common';
import {CText, WHITE, GREY, PURPLE, abs, softOp, fadeIn, LineArrow} from '../../ui';
import {TrackLine, PlayerDot} from '../G3/parts';

/**
 * SC06 2D 巡逻 + Xaero 联动（2808–3288）：
 * 2816 左 (320,380) 紫 α.7 三段折线（白 stroke 3 叠芯）+ 灰 Pill「3D」(320,300)；
 *   同时 (480,380)→(600,380) 白箭头 draw-on 18f（LineArrow，SVG line+三角头）。
 * 2830 右 (760,380) 彩虹 TrackLine S 线 w8 draw-on（kf 绝对帧 2830→2870）+ 紫 Pill「2D」(760,300)，
 *   外包 GlitchIn 12f（全镜头唯一 glitch）。
 * 3082–3110 世界地图框 420×280 中心 (280,300)：白 2px 边 + #1a2030 填充 + 每 40px 网格 #223048
 *   + 3 块 rnd 地形色块 #223048 + 白 Pill「世界地图」(280,220)；左组（折线/3D/箭头）18f 淡出让位。
 * 3110–3140 小地图圆框 r100 @(980,320)（#1a2030）+ 白 Pill「小地图」(980,200)。
 * 3140 两框内各一条小号彩虹 TrackLine w5 draw-on 20f。
 * 3180–3220 小地图内 PlayerDot 沿 pts 移动 + 3 渐隐残影（滞后 8/16/24 帧，α .4/.25/.1）。
 * 3200 「走到哪都看得见」CText 30px GREY (640,560) fadeIn 10f（字幕带 y637+ 之上，无遮挡）。
 * 3252 起 exitOp 归零：opacity = 1−(n/10)^1.5 包裹整个内容 div。
 * 持续：PlayerDot 呼吸（r ±10% sin）。
 */
const F0 = 2808;
const T_L = 2816;
const T_2D = 2830;
const T_MAP = 3082, T_MINI = 3110, T_LINES = 3140, LINE_LEN = 20;
const T_DOT = 3180, DOT_LEN = 40;
const T_CAP = 3200;
const T_EXIT = 3252;
const LEFT_FADE = 3082, LEFT_FADE_LEN = 18;

// 左：3D 折线（3 段，围绕 (320,380)）；右：2D 彩虹 S 线（围绕 (760,380)）
const L_PTS: Array<[number, number]> = [[235, 420], [290, 345], [350, 410], [415, 340]];
const R_PTS: Array<[number, number]> = [[660, 430], [710, 330], [760, 430], [810, 330], [860, 430]];
// 世界地图框（中心） / 小地图圆（圆心）
const MAP = {x: 280, y: 300, w: 420, h: 280};
const MINI = {x: 980, y: 320, r: 100};
// 两框内小号彩虹线（均在内域，不压边框）
const MAP_PTS: Array<[number, number]> = [[110, 390], [170, 330], [230, 390], [290, 330], [350, 390]];
const MINI_PTS: Array<[number, number]> = [[920, 360], [950, 300], [980, 360], [1010, 300], [1040, 360]];
// 3 块随机地形（rnd 定位，框内域 84–478 × 174–426）
const BLOCKS = [0, 1, 2].map((i) => ({
  x: Math.round(84 + rnd(31, i, 1) * 330),
  y: Math.round(174 + rnd(31, i, 2) * 186),
  w: Math.round(58 + rnd(31, i, 3) * 44),
  h: Math.round(38 + rnd(31, i, 4) * 32),
}));

/** 折线上 t(0..1) 处坐标（按弧长插值，纯函数）。 */
const polyAt = (pts: Array<[number, number]>, t: number): [number, number] => {
  const seg: number[] = [0];
  for (let i = 1; i < pts.length; i++) seg.push(seg[i - 1] + Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]));
  const L = seg[seg.length - 1] * clamp01(t);
  for (let i = 1; i < pts.length; i++) {
    if (seg[i] >= L) {
      const u = (L - seg[i - 1]) / Math.max(1e-6, seg[i] - seg[i - 1]);
      return [pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * u, pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * u];
    }
  }
  return pts[pts.length - 1];
};

/** 自绘小 Pill（中心定位；G3 SC05 同款做法，便于带透明度/进出场）。 */
const Tag: React.FC<{cx: number; cy: number; text: string; bg: string; fg: string; w?: number; h?: number; size?: number; opacity?: number}> = ({cx, cy, text, bg, fg, w = 92, h = 46, size = 26, opacity = 1}) => (
  <div style={{...abs(cx - w / 2, cy - h / 2, w, h), opacity, borderRadius: h / 2, background: bg, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT_HEAVY, fontWeight: 800, fontSize: size, color: fg, letterSpacing: 1, whiteSpace: 'nowrap'}}>{text}</div>
);

export const SC06: React.FC = () => {
  const N = useCurrentFrame() + F0;
  // ---- 左组（3D 折线 + Pill + 箭头）：2816 入场，3082 随世界地图让位淡出 ----
  const leftOp = softOp(N - T_L, 8) * (1 - clamp01((N - LEFT_FADE) / LEFT_FADE_LEN));
  const arrowP = clamp01((N - T_L) / 18);
  // ---- 右组：2830 起 GlitchIn，TrackLine draw-on 用 kf（绝对帧） ----
  const trackP = kf(N, [[T_2D, 0], [T_2D + 40, 1]]);
  // ---- 框体 / 框内彩虹线 / PlayerDot ----
  const mapOp = softOp(N - T_MAP, 10);
  const miniOp = softOp(N - T_MINI, 10);
  const lineP = clamp01((N - T_LINES) / LINE_LEN);
  const dotN = N - T_DOT;
  const dotP = clamp01(dotN / DOT_LEN);
  const [dx, dy] = polyAt(MINI_PTS, dotP);
  const breath = 1 + 0.1 * Math.sin(N * 0.2); // 持续：呼吸 r ±10%
  // ---- 3252 起 exitOp 归零：1−(n/10)^1.5 ----
  const exitN = N - T_EXIT;
  const exitK = exitN <= 0 ? 1 : 1 - Math.pow(Math.min(1, exitN / 10), 1.5);

  return (
    <div style={{position: 'absolute', inset: 0, opacity: exitK}}>
      {/* 左：3D 折线（紫 α.7 底 + 白 3px 芯）+ 灰 Pill「3D」+ 白箭头 draw-on 18f */}
      {leftOp > 0.01 ? (
        <>
          <svg width={1280} height={720} viewBox="0 0 1280 720" style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', opacity: leftOp}}>
            <polyline points={L_PTS.map((q) => q.join(',')).join(' ')} fill="none" stroke={PURPLE} strokeWidth={7} strokeOpacity={0.7} strokeLinecap="round" strokeLinejoin="round" />
            <polyline points={L_PTS.map((q) => q.join(',')).join(' ')} fill="none" stroke={WHITE} strokeWidth={3} strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          <div style={{opacity: leftOp}}>
            <Tag cx={320} cy={300} text="3D" bg={GREY} fg="#101010" />
          </div>
          <LineArrow x0={480} y0={380} x1={600} y1={380} p={arrowP} rodW={3} headL={18} headW={18} color={WHITE} opacity={leftOp} />
        </>
      ) : null}

      {/* 右：2D 彩虹 S 线 w8 draw-on + 紫 Pill「2D」——GlitchIn 12f（全镜头唯一 glitch） */}
      <GlitchIn N={N} f0={T_2D} seed={6}>
        <TrackLine pts={R_PTS} p={trackP} sw={8} />
        <Tag cx={760} cy={300} text="2D" bg={PURPLE} fg={WHITE} />
      </GlitchIn>

      {/* 世界地图框 420×280 中心 (280,300)：白 2px 边 + #1a2030 + 每 40px 网格 + 3 rnd 地形块 + 白 Pill */}
      {mapOp > 0.01 ? (
        <>
          <svg width={1280} height={720} viewBox="0 0 1280 720" style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', opacity: mapOp}}>
            <rect x={MAP.x - MAP.w / 2} y={MAP.y - MAP.h / 2} width={MAP.w} height={MAP.h} fill="#1a2030" stroke={WHITE} strokeWidth={2} />
            {Array.from({length: Math.floor(MAP.w / 40) - 1}, (_, i) => (
              <line key={'v' + i} x1={MAP.x - MAP.w / 2 + (i + 1) * 40} y1={MAP.y - MAP.h / 2} x2={MAP.x - MAP.w / 2 + (i + 1) * 40} y2={MAP.y + MAP.h / 2} stroke="#223048" strokeWidth={1.5} />
            ))}
            {Array.from({length: Math.floor(MAP.h / 40) - 1}, (_, i) => (
              <line key={'h' + i} x1={MAP.x - MAP.w / 2} y1={MAP.y - MAP.h / 2 + (i + 1) * 40} x2={MAP.x + MAP.w / 2} y2={MAP.y - MAP.h / 2 + (i + 1) * 40} stroke="#223048" strokeWidth={1.5} />
            ))}
            {BLOCKS.map((b, i) => (
              <rect key={i} x={b.x} y={b.y} width={b.w} height={b.h} rx={4} fill="#223048" />
            ))}
          </svg>
          <div style={{opacity: mapOp}}>
            <Tag cx={280} cy={220} text="世界地图" bg={WHITE} fg="#101010" w={150} />
          </div>
          <TrackLine pts={MAP_PTS} p={lineP} sw={5} opacity={mapOp} />
        </>
      ) : null}

      {/* 小地图圆框 r100 @(980,320) + 白 Pill「小地图」+ 框内彩虹线 */}
      {miniOp > 0.01 ? (
        <>
          <svg width={1280} height={720} viewBox="0 0 1280 720" style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', opacity: miniOp}}>
            <circle cx={MINI.x} cy={MINI.y} r={MINI.r} fill="#1a2030" stroke={WHITE} strokeWidth={2} />
          </svg>
          <div style={{opacity: miniOp}}>
            <Tag cx={980} cy={200} text="小地图" bg={WHITE} fg="#101010" w={132} />
          </div>
          <TrackLine pts={MINI_PTS} p={lineP} sw={5} opacity={miniOp} />
        </>
      ) : null}

      {/* 小地图内 PlayerDot 沿 pts 移动 + 3 渐隐残影（滞后 8/16/24f，α .4/.25/.1）；持续呼吸 */}
      {dotN >= 0 ? (
        <>
          {[8, 16, 24].map((lag, i) => {
            const [gx, gy] = polyAt(MINI_PTS, clamp01((dotN - lag) / DOT_LEN));
            return <PlayerDot key={i} cx={gx} cy={gy} r={12 * breath} opacity={[0.4, 0.25, 0.1][i]} />;
          })}
          <PlayerDot cx={dx} cy={dy} r={12 * breath} opacity={softOp(dotN, 6)} />
        </>
      ) : null}

      {/* 字幕上方说明行 */}
      <CText cx={640} cy={560} size={30} weight={700} color={GREY} opacity={fadeIn(N - T_CAP, 10)}>走到哪都看得见</CText>
    </div>
  );
};
