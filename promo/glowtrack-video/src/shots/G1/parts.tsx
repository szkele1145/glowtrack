import React from 'react';
import {FONT_HEAVY, FONT_MONO, clamp01, rnd} from '../../common';
import {Box, CText, WHITE, PURPLE, PURPLE_LIGHT, GREY_MID, GREY_LINE, RED_DEEP, GREEN, BLOOM_SOFT, abs} from '../../ui';

/**
 * G1 组内图元（GlowTrack 主题：服务器机架 / 跑动小人 / 房子 / 聊天气泡 / 日志纸 / 灰虚线道路 /
 * 彩虹轨迹线 / 白心彩虹环光点 / 时钟 / 眼睛）。全部 N 的纯函数，随机只用 rnd。
 * 注：分镜表「全局约束 3」建议的 TrackLine / ServerIcon / PlayerDot / ClockBadge 等新图元
 * 按协议 §1 落在组目录（不改 ui.tsx）；若后续组复用，建议主会话择机上移共用层。
 */

// ---- 彩虹渐变（本片语义色：GlowTrack 轨迹 / 光点；依据 调研.md「彩虹每 3 格一轮」与分镜表「彩虹曲线」）----
const RAINBOW = ['#FF5A5A', '#FFB13D', '#FFE14D', '#7BFF5A', '#58FFEE', '#6A8CFF', '#C06AFF'];
export const RB_GRAD = 'gtRainbowGrad';
const rbStops = () =>
  RAINBOW.map((c, i) => <stop key={i} offset={`${(i / (RAINBOW.length - 1)) * 100}%`} stopColor={c} />);

/** 彩虹 S 曲线（draw-on 用 pathLength=100 归一化，p 0→1 自路径起点生长）；放全幅 SVG 坐标系。 */
export const TrackLine: React.FC<{d: string; w?: number; p?: number; opacity?: number; glowK?: number}> = ({d, w = 8, p = 1, opacity = 1, glowK = 0.5}) => (
  <svg width={1280} height={720} viewBox="0 0 1280 720" style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', opacity}}>
    <defs>
      <linearGradient id={RB_GRAD} x1="0" y1="0" x2="1" y2="0">{rbStops()}</linearGradient>
    </defs>
    <path
      d={d} pathLength={100} fill="none" stroke={`url(#${RB_GRAD})`} strokeWidth={w}
      strokeLinecap="round" strokeLinejoin="round"
      strokeDasharray={100} strokeDashoffset={100 * (1 - clamp01(p))}
      style={{filter: `drop-shadow(0 0 ${(6 * glowK).toFixed(1)}px rgba(255,255,255,${(0.35 * glowK).toFixed(2)})) drop-shadow(0 0 ${(14 * glowK).toFixed(1)}px rgba(102,45,248,${(0.4 * glowK).toFixed(2)}))`}}
    />
  </svg>
);

/** 光点（白心彩虹环，调研「像一路撒下的光点」）：glowK 为柔光强度 0–1（白心光 + 紫晕）。 */
export const PlayerDot: React.FC<{cx: number; cy: number; r?: number; opacity?: number; glowK?: number; breathe?: number}> = ({cx, cy, r = 13, opacity = 1, glowK = 0.35, breathe = 1}) => {
  const rr = r * breathe;
  return (
    <div style={{position: 'absolute', left: cx - r - 10, top: cy - r - 10, width: 2 * r + 20, height: 2 * r + 20, opacity}}>
      {glowK > 0.005 ? (
        <div style={{position: 'absolute', inset: 4, borderRadius: '50%', boxShadow: `0 0 ${(8 + 12 * glowK).toFixed(0)}px ${(2 + 4 * glowK).toFixed(0)}px rgba(255,255,255,${(0.32 * glowK).toFixed(3)}), 0 0 ${(18 * glowK).toFixed(0)}px ${(5 * glowK).toFixed(0)}px rgba(102,45,248,${(0.5 * glowK).toFixed(3)})`}} />
      ) : null}
      <svg width={2 * r + 20} height={2 * r + 20} viewBox={`${-r - 10} ${-r - 10} ${2 * r + 20} ${2 * r + 20}`} style={{position: 'absolute', left: 0, top: 0, overflow: 'visible'}}>
        <defs>
          <linearGradient id={RB_GRAD} x1="0" y1="0" x2="1" y2="0">{rbStops()}</linearGradient>
        </defs>
        <circle cx={0} cy={0} r={rr} fill="none" stroke={`url(#${RB_GRAD})`} strokeWidth={4} />
        <circle cx={0} cy={0} r={rr * 0.42} fill={WHITE} />
      </svg>
    </div>
  );
};

/** 服务器机架（主角）：三格横条 + 每格红绿双指示灯按 stepKf 交替（持续动作），右缘通风线。 */
export const ServerIcon: React.FC<{cx: number; cy: number; w?: number; h?: number; units?: number; N?: number; opacity?: number; sw?: number}> = ({cx, cy, w = 130, h = 250, units = 3, N = 0, opacity = 1, sw = 3}) => {
  const uh = (h - 2 * sw - 10) / units;
  return (
    <div style={{...abs(cx - w / 2, cy - h / 2, w, h), opacity}}>
      <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', filter: BLOOM_SOFT}}>
        <rect x={sw / 2} y={sw / 2} width={w - sw} height={h - sw} rx={9} fill="#000" stroke={WHITE} strokeWidth={sw} />
        {Array.from({length: units}, (_, i) => {
          const y = sw + 6 + i * uh;
          const phase = Math.floor((N + i * 13) / 15) % 2 === 0;
          return (
            <g key={i}>
              <rect x={sw + 5} y={y} width={w - 2 * sw - 10} height={uh - 6} rx={4} fill="#0a0a0a" stroke={GREY_MID} strokeWidth={1.5} />
              <circle cx={sw + 18} cy={y + (uh - 6) / 2} r={4.5} fill={phase ? GREEN : '#16300f'} />
              <circle cx={sw + 34} cy={y + (uh - 6) / 2} r={4.5} fill={phase ? '#381016' : RED_DEEP} />
              {[0, 1, 2].map((k) => (
                <rect key={k} x={w - sw - 30 + k * 8} y={y + (uh - 6) / 2 - 4} width={3} height={8} fill={GREY_LINE} />
              ))}
            </g>
          );
        })}
      </svg>
    </div>
  );
};

/** 跑动小人（简笔）：腿臂 sin 摆 + 跑步起伏，持续到离场；cx 为身体中轴、cy 为脚底。 */
export const Runner: React.FC<{cx: number; cy: number; h?: number; N: number; color?: string; opacity?: number; speed?: number}> = ({cx, cy, h = 96, N, color = WHITE, opacity = 1, speed = 1}) => {
  const u = h / 96;
  const ph = N * 0.42 * speed;
  const legA = Math.sin(ph), legB = Math.sin(ph + Math.PI);
  const bob = Math.abs(Math.cos(ph)) * 3 * u;
  const ax = h * 0.45; // 中轴
  const hipY = h - 34 * u, shY = h - 58 * u, headY = h - 78 * u;
  return (
    <svg width={h * 0.9} height={h} viewBox={`0 0 ${h * 0.9} ${h}`} style={{position: 'absolute', left: cx - ax, top: cy - h, overflow: 'visible', opacity, filter: BLOOM_SOFT}}>
      <g stroke={color} strokeWidth={3 * u} strokeLinecap="round" fill="none">
        {/* 腿 ×2（前摆脚略抬起） */}
        <line x1={ax} y1={hipY - bob} x2={ax + legA * 18 * u} y2={h - Math.max(0, legA) * 9 * u - bob} />
        <line x1={ax} y1={hipY - bob} x2={ax + legB * 18 * u} y2={h - Math.max(0, legB) * 9 * u - bob} />
        {/* 躯干 */}
        <line x1={ax} y1={shY - bob} x2={ax} y2={hipY - bob} />
        {/* 臂 ×2（与腿反相） */}
        <line x1={ax} y1={shY + 4 * u - bob} x2={ax + legB * 15 * u} y2={shY + 24 * u - bob} />
        <line x1={ax} y1={shY + 4 * u - bob} x2={ax + legA * 15 * u} y2={shY + 24 * u - bob} />
      </g>
      <circle cx={ax} cy={headY - bob} r={11 * u} fill="#000" stroke={color} strokeWidth={3 * u} />
    </svg>
  );
};

/** 小房子简笔（拆建筑事件）：crackK 0→1 时 3 片碎块沿 rnd 方向飞散消失。cy 为底边。 */
export const HouseIcon: React.FC<{cx: number; cy: number; w?: number; h?: number; opacity?: number; crackK?: number}> = ({cx, cy, w = 112, h = 100, opacity = 1, crackK = 0}) => {
  const wallH = h * 0.62, roofH = h - wallH;
  const k = clamp01(crackK);
  return (
    <div style={{...abs(cx - w / 2 - 30, cy - h - 30, w + 60, h + 60), opacity}}>
      <svg width={w + 60} height={h + 60} viewBox={`${-30} ${-30} ${w + 60} ${h + 60}`} style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', filter: BLOOM_SOFT}}>
        <g transform={`translate(${cx} ${cy})`}>
          <rect x={-w / 2} y={-wallH} width={w} height={wallH} fill="#000" stroke={WHITE} strokeWidth={2.5} />
          <polygon points={`${-w / 2 - 6},${-wallH} 0,${-h} ${w / 2 + 6},${-wallH}`} fill="#000" stroke={WHITE} strokeWidth={2.5} strokeLinejoin="round" />
          <rect x={-w * 0.14} y={-wallH * 0.52} width={w * 0.28} height={wallH * 0.52} fill="#000" stroke={WHITE} strokeWidth={2} />
          {/* 裂缝随 crackK 加深 */}
          {k > 0.02 ? <path d={`M${-w * 0.1},${-wallH} L${-w * 0.02},${-wallH * 0.66} L${-w * 0.14},${-wallH * 0.42}`} fill="none" stroke={WHITE} strokeWidth={2} opacity={0.9} /> : null}
          {/* 3 片碎块飞散 */}
          {[0, 1, 2].map((i) => {
            const ang = -Math.PI * (0.25 + rnd(51, i) * 0.5);
            const dist = (30 + rnd(52, i) * 34) * k;
            const x = -w * 0.3 + i * w * 0.3 + Math.cos(ang) * dist;
            const y = -wallH - 6 + Math.sin(ang) * dist;
            const s = 7 + rnd(53, i) * 5;
            return <rect key={i} x={x - s / 2} y={y - s / 2} width={s} height={s} fill="#000" stroke={WHITE} strokeWidth={2} opacity={1 - k} transform={`rotate(${k * 120 * (rnd(54, i) > 0.5 ? 1 : -1)} ${x} ${y})`} />;
          })}
        </g>
      </svg>
    </div>
  );
};

/** 聊天气泡（举报）：白边圆角 + 左下尾巴 + 文字。x,y 为外框左上。 */
export const Bubble: React.FC<{x: number; y: number; w?: number; h?: number; text: string; opacity?: number; fontSize?: number}> = ({x, y, w = 252, h = 62, text, opacity = 1, fontSize = 26}) => (
  <div style={{...abs(x, y, w, h + 14), opacity}}>
    <Box x={0} y={0} w={w} h={h} r={14} sw={2} />
    <div style={{position: 'absolute', left: 26, top: h - 2, width: 0, height: 0, borderLeft: '12px solid transparent', borderRight: '12px solid transparent', borderTop: `13px solid ${WHITE}`}} />
    <div style={{position: 'absolute', left: 28, top: h + 2, width: 0, height: 0, borderLeft: '10px solid transparent', borderRight: '10px solid transparent', borderTop: '11px solid #000'}} />
    <CText cx={w / 2} cy={h / 2} size={fontSize} weight={700} color={WHITE} style={{position: 'absolute'}}>{text}</CText>
  </div>
);

/** 服务器日志纸卡：白边黑底 + 行条（reveal 0→rows 打字机逐行出现）；首行留白给文字。 */
export const LogSheet: React.FC<{x: number; y: number; w?: number; h?: number; rows?: number; reveal?: number; opacity?: number}> = ({x, y, w = 290, h = 330, rows = 7, reveal = rows, opacity = 1}) => (
  <Box x={x} y={y} w={w} h={h} r={8} sw={2.5} opacity={opacity}>
    <div style={{position: 'absolute', left: 0, top: 44, right: 0, bottom: 14}}>
      {Array.from({length: rows}, (_, i) => {
        const shown = clamp01(reveal - i);
        if (shown <= 0) return null;
        const lw = (0.42 + 0.4 * (((i * 7 + 3) % 10) / 10)) * (w - 48);
        return (
          <div key={i} style={{position: 'absolute', left: 24, top: 8 + i * ((h - 70) / rows), width: lw * shown, height: 4, borderRadius: 2, background: GREY_LINE, opacity: 0.9}} />
        );
      })}
    </div>
  </Box>
);

/** 灰虚线道路（处境段贯穿元素）：p 为 draw-on，opacity 由镜头按剧情压暗。 */
export const Road: React.FC<{x0: number; x1: number; y: number; opacity?: number; p?: number}> = ({x0, x1, y, opacity = 1, p = 1}) => (
  <svg width={1280} height={720} viewBox="0 0 1280 720" style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', opacity}}>
    <line x1={x0} y1={y} x2={x1} y2={y} stroke={GREY_MID} strokeWidth={2.5} pathLength={100} strokeDasharray="100" strokeDashoffset={100 * (1 - clamp01(p))} opacity={0.9} />
  </svg>
);

/** 时钟（采样间隔示意；调研.md 数字清单无采样间隔数值 → 不标数字，只画指针扫描）。秒针 9°/帧。 */
export const ClockBadge: React.FC<{cx: number; cy: number; r?: number; N: number; opacity?: number}> = ({cx, cy, r = 42, N, opacity = 1}) => {
  const sec = (N * 9) % 360;
  return (
    <svg width={2 * r + 16} height={2 * r + 16} viewBox={`${-r - 8} ${-r - 8} ${2 * r + 16} ${2 * r + 16}`} style={{position: 'absolute', left: cx - r - 8, top: cy - r - 8, overflow: 'visible', opacity, filter: BLOOM_SOFT}}>
      <circle cx={0} cy={0} r={r} fill="#000" stroke={WHITE} strokeWidth={3} />
      {Array.from({length: 12}, (_, i) => {
        const a = (i * 30 * Math.PI) / 180;
        return <line key={i} x1={Math.sin(a) * (r - 6)} y1={-Math.cos(a) * (r - 6)} x2={Math.sin(a) * (r - 13)} y2={-Math.cos(a) * (r - 13)} stroke={GREY_MID} strokeWidth={2} />;
      })}
      <line x1={0} y1={0} x2={r * 0.34} y2={-r * 0.30} stroke={WHITE} strokeWidth={3} strokeLinecap="round" />
      <line x1={0} y1={0} x2={Math.sin((sec * Math.PI) / 180) * r * 0.72} y2={-Math.cos((sec * Math.PI) / 180) * r * 0.72} stroke={PURPLE_LIGHT} strokeWidth={2.5} strokeLinecap="round" />
      <circle cx={0} cy={0} r={3.5} fill={WHITE} />
    </svg>
  );
};

/** 眼睛简笔（「装上一双眼睛」）：白边眼眶 + 紫瞳 + 紫柔光 + 高光点。 */
export const EyeIcon: React.FC<{cx: number; cy: number; w?: number; opacity?: number}> = ({cx, cy, w = 96, opacity = 1}) => {
  const h = w * 0.58;
  return (
    <svg width={w + 30} height={h + 40} viewBox={`${-w / 2 - 15} ${-h / 2 - 20} ${w + 30} ${h + 40}`} style={{position: 'absolute', left: cx - w / 2 - 15, top: cy - h / 2 - 20, overflow: 'visible', opacity, filter: `drop-shadow(0 0 10px rgba(102,45,248,.65))`}}>
      <path d={`M${-w / 2},0 Q0,${-h * 0.62} ${w / 2},0`} fill="none" stroke={WHITE} strokeWidth={3} strokeLinecap="round" />
      <path d={`M${-w / 2},0 Q0,${h * 0.5} ${w / 2},0`} fill="none" stroke={WHITE} strokeWidth={3} strokeLinecap="round" />
      <circle cx={0} cy={-h * 0.02} r={w * 0.155} fill={PURPLE} stroke={PURPLE_LIGHT} strokeWidth={2} />
      <circle cx={w * 0.05} cy={-h * 0.14} r={w * 0.038} fill={WHITE} opacity={0.95} />
    </svg>
  );
};

/** 组内大标签（FONT_HEAVY 引用保持 tree-shaking 明确） */
export const PartLabel = CText;
export const PartMono = FONT_MONO;
