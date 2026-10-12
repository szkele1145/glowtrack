import React from 'react';
import {useCurrentFrame} from 'remotion';
import {GlitchIn, clamp01, emphasisPulse, rnd, FONT_WIDE, FONT_HEAVY} from '../../common';
import {CText, WHITE, GREY, GREY_LINE, abs, softOp, firstOp, slideUp, SoftIn} from '../../ui';
import {LightSweep, HaloRing} from '../../fx';
import {TrackLine, PlayerDot} from '../G3/parts';

/**
 * SC08 收尾 payoff（3828–4070）：片头构图回扣 + 扫光白名单② + 世界微缩路。
 * 3828 左彩虹图标（TrackLine S 线 w≈200 + 节点光点 + 末端紫光环）与右侧「GlowTrack」
 *   Audiowide 110px 白 + 紫硬投影 6px 6px 0 rgba(102,45,248,1) 同帧淡入（构图收口）；
 * 3836/3844/3852 三轮 LightSweep（16f ×3 错峰 8f）——全片扫光白名单②，只此一处；
 * 3907 大字 GlitchIn rgbSplit 6（glitch 全镜头唯一一处）+ emphasisPulse 1.11；
 * 3920 副标「让每一个脚印都发光」34px GREY letterSpacing 6 SoftIn；
 * 3938 起（S30 节拍）背景点阵波上 5 条世界微缩灰路（竖向曲线 x=260/460/660/860/1060）
 *   1 帧错峰各亮一条彩虹细线（TrackLine sw2，draw-on 20f）；
 * 3966 白 Pill「Modrinth · GlowTrack」(640,560) slideUp + 3980 灰小字 SoftIn。
 * 持续：紫光环呼吸。不主动离场——4070 后由 G0 片尾层接管淡出，本镜头保持满态（无 exitOp）。
 */
const F0 = 3828;
const T_SWEEPS = [3836, 3844, 3852];
const T_BIG = 3907, T_SUB = 3920;
const T_ROADS = 3938;
const ROAD_X = [260, 460, 660, 860, 1060];
const T_PILL = 3966, T_PILLNOTE = 3980;
// 左侧彩虹图标：S 折线 w≈200（中心 320,360）+ 末端紫光环
const S_PTS: Array<[number, number]> = [[222, 374], [262, 352], [300, 372], [338, 350], [378, 370], [418, 352]];
const RING = {x: 418, y: 352};
const PILL = {x: 640, y: 560, w: 320, h: 52};
const BIG_SHADOW = '6px 6px 0 rgba(102,45,248,1)';

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

/** 第 i 条微缩灰路：x 固定、竖向轻波浪（y150–520，幅度/相位 rnd 区分）。 */
const roadPts = (i: number): Array<[number, number]> => {
  const x = ROAD_X[i];
  const amp = 11 + rnd(9, i, 3) * 12;
  const ph = rnd(9, i, 4) * 6.283;
  const pts: Array<[number, number]> = [];
  for (let y = 150; y <= 520; y += 24) pts.push([x + amp * Math.sin(y * 0.022 + ph), y]);
  return pts;
};
const ROADS = ROAD_X.map((_, i) => roadPts(i));

export const SC08: React.FC = () => {
  const N = useCurrentFrame() + F0;
  // ---- 开场：图标 + 大字淡入（首帧即有亮度）----
  const iconOp = firstOp(N - F0, 6);
  const lineP = clamp01((N - F0) / 26);
  const ringP = clamp01((N - (F0 + 20)) / 14);
  const ringBreath = 1 + 0.045 * Math.sin(N * 0.2); // 持续：紫光环呼吸
  const bigOp = softOp(N - F0, 8);
  // ---- 3907：大字 GlitchIn 强调 + pulse ----
  const rn = N - T_BIG;
  const pulse = emphasisPulse(N - T_BIG, {peak: 1.11});
  // ---- 白 Pill / 灰小字 ----
  const pillDy = slideUp(N - T_PILL, 70, 20);
  const pillOp = softOp(N - T_PILL, 8);

  return (
    <div style={{position: 'absolute', inset: 0}}>
      {/* 扫光白名单②：三轮 LightSweep（3836–3872，dy 对准大字行） */}
      <LightSweep N={N} rounds={T_SWEEPS} />

      {/* S30：5 条微缩灰路淡入 + 彩虹细线（1 帧错峰 draw-on 20f） */}
      {ROADS.map((pts, i) => {
        const n = N - (T_ROADS + i);
        if (n < 0) return null;
        const roadOp = softOp(n, 8);
        return (
          <React.Fragment key={i}>
            <svg width={1280} height={720} viewBox="0 0 1280 720" style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', opacity: 0.55 * roadOp}}>
              <polyline points={pts.map((q) => q.join(',')).join(' ')} fill="none" stroke={GREY_LINE} strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" />
            </svg>
            <TrackLine pts={pts} p={clamp01(n / 20)} sw={2} opacity={0.92 * roadOp} />
          </React.Fragment>
        );
      })}

      {/* 左：彩虹图标（S 线 draw-on + 节点光点 + 末端紫光环呼吸） */}
      <div style={{position: 'absolute', inset: 0, opacity: iconOp}}>
        <TrackLine pts={S_PTS} p={lineP} sw={7} />
        {[0.22, 0.62].map((t, i) => {
          if (lineP < t) return null;
          const s = 0.4 + 0.6 * clamp01((lineP - t) * 6);
          const [dx, dy] = polyAt(S_PTS, t);
          return <div key={i} style={{position: 'absolute', left: dx - 9 * s, top: dy - 9 * s, transform: `scale(${s.toFixed(3)})`}}><PlayerDot cx={9} cy={9} r={9} t={0.2 + i * 0.45} k={1 + 0.1 * Math.sin(N * 0.22 + i * 2)} /></div>;
        })}
        {ringP > 0 ? (
          <div style={{position: 'absolute', left: RING.x - 60, top: RING.y - 20, transform: `scale(${ringBreath.toFixed(3)})`, transformOrigin: '60px 20px'}}>
            <HaloRing cx={60} cy={20} rxo={46} ryo={13} rxi={31} ryi={8} p={ringP} fillOp={0.75} phase={N * 0.5} />
          </div>
        ) : null}
      </div>

      {/* 右：GlowTrack 大字（3907 前 plain 淡入常显 → 3907 GlitchIn rgbSplit6 接管；pulse 缩放原点 860,330） */}
      <div style={{position: 'absolute', inset: 0, transformOrigin: '860px 330px', transform: pulse === 1 ? undefined : `scale(${pulse.toFixed(4)})`}}>
        {rn >= 0 ? (
          <GlitchIn N={N} f0={T_BIG} rgbSplit={6} seed={5}>
            <CText cx={860} cy={330} size={110} weight={400} family={FONT_WIDE} letterSpacing={2} color={WHITE} dy={-4} shadow={BIG_SHADOW}>GlowTrack</CText>
          </GlitchIn>
        ) : (
          <CText cx={860} cy={330} size={110} weight={400} family={FONT_WIDE} letterSpacing={2} color={WHITE} dy={-4} opacity={bigOp} shadow={BIG_SHADOW}>GlowTrack</CText>
        )}
      </div>

      {/* 副标 */}
      <SoftIn N={N} f0={T_SUB} len={10} dy={12}>
        <CText cx={860} cy={412} size={34} weight={700} color={GREY} letterSpacing={6}>让每一个脚印都发光</CText>
      </SoftIn>

      {/* 白 Pill「Modrinth · GlowTrack」slideUp + 灰小字 */}
      {pillOp > 0.01 ? (
        <div style={{...abs(PILL.x - PILL.w / 2, PILL.y - PILL.h / 2 + pillDy, PILL.w, PILL.h), opacity: pillOp, borderRadius: PILL.h / 2, background: WHITE, boxShadow: '0 0 22px rgba(255,255,255,.32)'}}>
          <div style={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT_HEAVY, fontWeight: 800, fontSize: 27, color: '#101010', letterSpacing: 1, whiteSpace: 'nowrap'}}>Modrinth · GlowTrack</div>
        </div>
      ) : null}
      <SoftIn N={N} f0={T_PILLNOTE} len={10} dy={6}>
        <CText cx={640} cy={606} size={22} weight={600} color={GREY}>反馈与下载见 Modrinth 页面</CText>
      </SoftIn>
    </div>
  );
};
