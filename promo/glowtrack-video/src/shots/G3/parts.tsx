import React from 'react';
import {clamp01, rnd, FONT_HEAVY} from '../../common';
import {PURPLE, PURPLE_LIGHT, WHITE, GREY_LINE, abs} from '../../ui';

/**
 * G3 组内共用图元（本组特有图元；不改 ui.tsx）。
 * TrackLine / ServerIcon / PlayerDot 按《GlowTrack》分镜表描述建，命名与签名同 G1/parts 约定——
 * G1 完成后可切换 import 源，几何参数一致即可无缝替换。全部纯函数、绝对定位。
 */

// ---------- 彩虹轨迹线 ----------
/** 彩虹色相（按移动距离 t=0..1 循环）：HSL 色环 + 亮度固定。参照 TrackRenderer cumDist/3.0 每 3 格一轮，视觉取全谱。 */
const RAINBOW = ['#58C8EE', '#58FF7A', '#B7F04A', '#F0D24A', '#F0834A', '#F05F6B', '#C86BF0', '#7B6BF0'];
export const rainbowColor = (t: number) => {
  const i = Math.floor(((t % 1) + 1) * RAINBOW.length) % RAINBOW.length;
  return RAINBOW[i];
};
export type TrackLineProps = {
  /** 折线顶点（画布坐标） */
  pts: Array<[number, number]>;
  /** draw-on 进度 0..1（自 pts[0] 长出） */
  p?: number;
  /** 基线宽 px（速度热力可按 t 分段加粗） */
  sw?: number;
  /** 发光层宽（通体辉光，同形加粗低透明度；0 = 关） */
  glowW?: number;
  /** 速度热力：t=0..1 色相系数。'rainbow' 彩虹均分；'heat' 蓝(慢)→绿→红(快) */
  heat?: 'rainbow' | 'heat';
  /** 亮度波相位（0..1，沿线跑动的亮度起伏；undefined = 关） */
  pulse?: number;
  opacity?: number;
};
/** 轨迹线：逐段折线，每段按弧长均分彩虹色相；glow 层画在下（同形加粗 α.25）。 */
export const TrackLine: React.FC<TrackLineProps> = ({pts, p = 1, sw = 8, glowW = 0, heat = 'rainbow', pulse, opacity = 1}) => {
  if (p <= 0 || pts.length < 2 || opacity <= 0) return null;
  const seg: number[] = [0];
  for (let i = 1; i < pts.length; i++) seg.push(seg[i - 1] + Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]));
  const total = seg[seg.length - 1];
  const pe = 1 - Math.pow(1 - clamp01(p), 2);
  const L = total * pe;
  const strokes: React.ReactNode[] = [];
  for (let i = 1; i < pts.length; i++) {
    if (seg[i] <= 0) continue;
    const a = seg[i - 1], b = Math.min(seg[i], L);
    if (b <= a) break;
    const t0 = a / total, t1 = b / total;
    const lit = pulse === undefined ? 1 : 0.82 + 0.36 * Math.max(0, Math.cos(((t0 + t1) / 2 - pulse) * Math.PI * 2 * 2.5));
    strokes.push(
      <line key={i} x1={pts[i - 1][0]} y1={pts[i - 1][1]} x2={pts[i][0]} y2={pts[i][1]}
        stroke={heat === 'heat' ? heatColor((t0 + t1) / 2) : rainbowColor((t0 + t1) / 2)}
        strokeWidth={heat === 'heat' ? sw * (0.75 + 0.75 * clamp01((t0 + t1) / 2)) : sw}
        opacity={Math.min(1, lit)} strokeLinecap="butt" />,
    );
  }
  return (
    <svg width={1280} height={720} viewBox="0 0 1280 720" style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', opacity}}>
      {glowW > 0 ? (
        <polyline points={pts.map((q) => q.join(',')).join(' ')} fill="none" stroke={PURPLE_LIGHT} strokeWidth={glowW} opacity={0.22} strokeLinejoin="round" strokeLinecap="round" strokeDasharray={total} strokeDashoffset={total * (1 - pe)} />
      ) : null}
      {strokes}
    </svg>
  );
};
/** 速度热力色：蓝(慢)→青绿→绿→红(快)（TimelineWidget.heat） */
export const heatColor = (t: number) => {
  const k = clamp01(t);
  if (k < 0.34) return mixRgb('#3E8EE8', '#4FD08A', k / 0.34);
  if (k < 0.67) return mixRgb('#4FD08A', '#E8D24A', (k - 0.34) / 0.33);
  return mixRgb('#E8D24A', '#F05F41', (k - 0.67) / 0.33);
};
const hex2 = (h: string) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const mixRgb = (a: string, b: string, k: number) => {
  const A = hex2(a), B = hex2(b), t = clamp01(k);
  return `rgb(${A.map((v, i) => Math.round(v + (B[i] - v) * t)).join(',')})`;
};

// ---------- 服务器机架 ----------
/** 机架简笔（SC01 描述：白边 220×280 三格横条 + 指示灯）：宽 220 高 280，三格横条各带指示灯。 */
export const ServerIcon: React.FC<{cx: number; cy: number; w?: number; h?: number; sw?: number; opacity?: number; seed?: number; lights?: [string, string, string]}> = ({cx, cy, w = 220, h = 280, sw = 2.5, opacity = 1, seed = 1, lights = ['#4FD08A', '#4FD08A', '#4FD08A']}) => {
  const rows = 3, gap = 14, pad = 12;
  const rh = (h - sw * 2 - (rows + 1) * gap) / rows;
  return (
    <svg width={w + 16} height={h + 16} viewBox={`-8 -8 ${w + 16} ${h + 16}`} style={{position: 'absolute', left: cx - w / 2 - 8, top: cy - h / 2 - 8, overflow: 'visible', opacity, filter: 'drop-shadow(0 0 2px rgba(255,255,255,.35))'}}>
      <rect x={-sw / 2} y={-sw / 2} width={w} height={h} rx={10} fill="#000" stroke={WHITE} strokeWidth={sw} />
      {Array.from({length: rows}, (_, i) => {
        const y = gap + i * (rh + gap);
        return (
          <g key={i}>
            <rect x={pad} y={y} width={w - pad * 2} height={rh} rx={5} fill="#0a0a0a" stroke={GREY_LINE} strokeWidth={1.5} />
            <circle cx={pad + 16} cy={y + rh / 2} r={5} fill={lights[i] ?? GREY_LINE} />
            {[0, 1, 2].map((k) => (
              <rect key={k} x={pad + 34 + k * ((w - pad * 2 - 58) / 3)} y={y + rh / 2 - 2} width={(w - pad * 2 - 58) / 3 - 10} height={4} rx={2} fill={rnd(seed, i, k) > 0.4 ? WHITE : GREY_LINE} opacity={0.75} />
            ))}
          </g>
        );
      })}
    </svg>
  );
};

// ---------- 玩家光点 ----------
/** 玩家光点（白心彩虹环）：r 为外环半径；呼吸 k 可让 r 微涨。 */
export const PlayerDot: React.FC<{cx: number; cy: number; r?: number; opacity?: number; t?: number; k?: number; label?: string; labelSize?: number}> = ({cx, cy, r = 14, opacity = 1, t = 0.3, k = 1, label, labelSize = 22}) => {
  const rr = r * (0.92 + 0.08 * clamp01(k));
  return (
    <div style={{position: 'absolute', inset: 0, opacity, pointerEvents: 'none'}}>
      <svg width={1280} height={720} viewBox="0 0 1280 720" style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', filter: 'drop-shadow(0 0 6px rgba(161,117,241,.65))'}}>
        <circle cx={cx} cy={cy} r={rr} fill="none" stroke={rainbowColor(t)} strokeWidth={3.5} />
        <circle cx={cx} cy={cy} r={rr * 0.45} fill={WHITE} />
      </svg>
      {label ? (
        <div style={{...abs(cx - 90, cy + r + 6, 180, 26), fontFamily: FONT_HEAVY, fontWeight: 700, fontSize: labelSize, color: WHITE, textAlign: 'center', lineHeight: '26px'}}>{label}</div>
      ) : null}
    </div>
  );
};

// ---------- 传送读数卡（SC05 / SC07 同款读数样式） ----------
/** 三行读数：时刻(黄)/维度(蓝)/坐标(白)。typing 为各行动画态：undefined=全显，数字为已打出字符数。 */
export const READOUT = {
  time: '15:07:33', dim: '主世界', pos: '128 64 -256',
};
export const ReadoutCard: React.FC<{x: number; y: number; w?: number; h?: number; typed?: [number, number, number]; opacity?: number; cursorRow?: -1 | 0 | 1 | 2; showTitle?: boolean}> = ({x, y, w = 400, h = 210, typed, opacity = 1, cursorRow = -1, showTitle = true}) => {
  const rows: Array<{label: string; value: string; color: string}> = [
    {label: '时刻', value: READOUT.time, color: '#F0D24A'},
    {label: '维度', value: READOUT.dim, color: '#6FA8F0'},
    {label: '坐标', value: READOUT.pos, color: WHITE},
  ];
  const lineStyle: React.CSSProperties = {position: 'absolute', left: 24, fontFamily: FONT_HEAVY, fontWeight: 700, fontSize: 30, lineHeight: 1, whiteSpace: 'nowrap'};
  return (
    <div style={{...abs(x, y, w, h), opacity}}>
      <div style={{position: 'absolute', inset: 0, boxSizing: 'border-box', background: '#000', border: `2.5px solid ${WHITE}`, borderRadius: 12, boxShadow: '0 0 24px 8px rgba(102,45,248,.5)'}} />
      {showTitle ? (
        <div style={{...abs(24, 14, 160, 30), fontFamily: FONT_HEAVY, fontWeight: 800, fontSize: 22, color: WHITE, letterSpacing: 2}}>传送读数</div>
      ) : null}
      {rows.map((r, i) => {
        const full = typed ? typed[i] : r.value.length;
        const shown = r.value.slice(0, full);
        const cur = cursorRow === i ? <span style={{display: 'inline-block', width: 3, height: 30, background: WHITE, marginLeft: 3, verticalAlign: -3}} /> : null;
        return (
          <div key={i} style={{...lineStyle, top: (showTitle ? 56 : 34) + i * 50}}>
            <span style={{color: PURPLE_LIGHT, marginRight: 14}}>{r.label}</span>
            <span style={{color: r.color}}>{shown}</span>
            {cur}
          </div>
        );
      })}
    </div>
  );
};

// ---------- 传送门简笔（紫框 100×140 波纹） ----------
export const PortalIcon: React.FC<{cx: number; cy: number; w?: number; h?: number; phase?: number; opacity?: number; rippleOp?: number}> = ({cx, cy, w = 100, h = 140, phase = 0, opacity = 1, rippleOp = 1}) => (
  <svg width={w + 60} height={h + 40} viewBox={`-30 -20 ${w + 60} ${h + 40}`} style={{position: 'absolute', left: cx - w / 2 - 30, top: cy - h / 2 - 20, overflow: 'visible', opacity}}>
    <ellipse cx={0} cy={0} rx={w / 2} ry={h / 2} fill="rgba(102,45,248,.28)" stroke={PURPLE_LIGHT} strokeWidth={3} />
    <ellipse cx={0} cy={0} rx={w / 2 - 10} ry={h / 2 - 10} fill="none" stroke={PURPLE} strokeWidth={2} />
    {[0.5, 1, 1.5].map((k, i) => {
      const rr = (w / 2 + 8) * (1 + 0.05 * Math.sin((phase + k) * 2.4));
      return <ellipse key={i} cx={0} cy={0} rx={rr} ry={rr * (h / w) * 0.92} fill="none" stroke={PURPLE_LIGHT} strokeWidth={1.6} strokeDasharray="10 12" strokeDashoffset={-phase * 3 - i * 9} opacity={0.42 * rippleOp} />;
    })}
  </svg>
);
