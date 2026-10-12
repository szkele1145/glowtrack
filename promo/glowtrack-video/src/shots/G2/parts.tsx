import React from 'react';
import {clamp01} from '../../common';
import {WHITE, GREY_LINE} from '../../ui';

/**
 * G2 组内共用图元（本组特有；不改 ui.tsx）。全部纯函数、绝对定位（画布 1280×720）。
 * TrackLine / ServerIcon / PlayerDot 复用 G3/parts（几何参数同分镜表，两处描述一致）。
 */

// ---------- 显示器简笔（白边 260×180 + 底座） ----------
export const MonitorIcon: React.FC<{
  cx: number; cy: number; w?: number; h?: number; sw?: number; opacity?: number;
  children?: React.ReactNode; // 屏幕内容（画布坐标绝对定位，屏幕内区 cx−w/2+10 … cy−h/2+10）
}> = ({cx, cy, w = 260, h = 180, sw = 2.5, opacity = 1, children}) => {
  const sx = cx - w / 2, sy = cy - h / 2;
  return (
    <div style={{position: 'absolute', inset: 0, opacity, pointerEvents: 'none'}}>
      <svg width={w + 16} height={h + 62} viewBox={`-8 -8 ${w + 16} ${h + 62}`} style={{position: 'absolute', left: sx - 8, top: sy - 8, overflow: 'visible', filter: 'drop-shadow(0 0 2px rgba(255,255,255,.35))'}}>
        <rect x={0} y={0} width={w} height={h} rx={10} fill="#000" stroke={WHITE} strokeWidth={sw} />
        <line x1={w / 2} y1={h} x2={w / 2} y2={h + 26} stroke={WHITE} strokeWidth={sw} />
        <rect x={w / 2 - 44} y={h + 26} width={88} height={10} rx={4} fill="#000" stroke={WHITE} strokeWidth={2} />
      </svg>
      {children}
    </div>
  );
};

// ---------- 三张光点卡（叠放：黑底白边小卡 + 一行光点） ----------
export const DotCard: React.FC<{x: number; y: number; w?: number; h?: number; opacity?: number; seed?: number; dim?: number; rot?: number}> = ({x, y, w = 300, h = 72, opacity = 1, seed = 1, dim = 0, rot = 0}) => {
  const n = 5;
  return (
    <div style={{position: 'absolute', left: x, top: y, width: w, height: h, opacity, transform: rot ? `rotate(${rot}deg)` : undefined}}>
      <div style={{position: 'absolute', inset: 0, boxSizing: 'border-box', background: '#000', border: `2.5px solid ${WHITE}`, borderRadius: 10, boxShadow: '0 0 18px 4px rgba(102,45,248,.35)'}} />
      <div style={{position: 'absolute', left: 18, top: (h - 18) / 2, width: w - 36, height: 18, display: 'flex', gap: 16, alignItems: 'center'}}>
        {Array.from({length: n}, (_, i) => {
          const lit = 0.4 + 0.6 * clamp01(dim === 0 ? 1 - 0.15 * ((i + seed) % 3) : 1 - dim);
          return <div key={i} style={{width: 12, height: 12, borderRadius: '50%', background: WHITE, opacity: lit, boxShadow: `0 0 8px 2px rgba(255,255,255,${(0.45 * lit).toFixed(2)})`}} />;
        })}
      </div>
    </div>
  );
};

// ---------- 弧形箭头（draw-on）：自服务器右肩越过 Pill 上方，收进显示器顶边 ----------
export const ArcArrow: React.FC<{p?: number; opacity?: number; color?: string}> = ({p = 1, opacity = 1, color = WHITE}) => {
  if (p <= 0 || opacity <= 0) return null;
  const d = 'M 368 242 C 480 128, 700 128, 800 300 C 840 372, 806 428, 766 450';
  return (
    <svg width={1280} height={720} viewBox="0 0 1280 720" style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', filter: 'drop-shadow(0 0 2px rgba(255,255,255,.4))', opacity}}>
      <path d={d} fill="none" stroke={color} strokeWidth={4} strokeLinecap="round" strokeDasharray={700} strokeDashoffset={700 * (1 - clamp01(p))} />
      {p > 0.96 ? <polygon points="752,458 762,440 771,456" fill={color} /> : null}
    </svg>
    );
};

// ---------- 三角简笔山脉（白边 stroke 2px，底对齐；逐峰 dy/op 供错峰 slideUp） ----------
export const MountainRange: React.FC<{spec: Array<{x: number; h: number; w?: number; dy?: number; op?: number}>, baseY?: number, sw?: number, opacity?: number}> = ({spec, baseY = 560, sw = 2, opacity = 1}) => (
  <svg width={1280} height={720} viewBox="0 0 1280 720" style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', filter: 'drop-shadow(0 0 2px rgba(255,255,255,.3))', opacity}}>
    {spec.map((m, i) => {
      const hw = (m.w ?? 200) / 2;
      const dy = m.dy ?? 0;
      return (
        <g key={i} opacity={m.op ?? 1} transform={dy ? `translateY(${dy.toFixed(1)}px)` : undefined}>
          <polygon points={`${m.x - hw},${baseY} ${m.x},${baseY - m.h} ${m.x + hw},${baseY}`} fill="#000" stroke={WHITE} strokeWidth={sw} strokeLinejoin="round" />
          <line x1={m.x} y1={baseY - m.h} x2={m.x} y2={baseY - m.h + m.h * 0.55} stroke={GREY_LINE} strokeWidth={sw - 0.5} />
        </g>
      );
    })}
  </svg>
);
