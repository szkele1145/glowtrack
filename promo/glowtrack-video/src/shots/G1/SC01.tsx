import React from 'react';
import {useCurrentFrame} from 'remotion';
import {FONT_MONO, GLITCH_SEQ, clamp01, powOutRemain, keyframes, rnd} from '../../common';
import {CText, Cross, CORAL, GREY, WHITE, abs, exitAccel, exitOp} from '../../ui';
import {HeroGlow} from '../../fx';

/**
 * SC01 处境：举报 · 追不上 · 日志没用（78–586，S01–S04）。
 * 节拍（subs.ts）：86 机架缩放+道路 draw-on / 133 举报气泡 / 181 小房子+碎块 / 250 道路变暗·气泡退场
 *   / 360 两小人+间距拉大+红「?」glitch（本镜头唯一闪烁，白名单）/ 455 日志卡 / 496 打字机 / 546 坐标?+红叉。
 * 主角 = 日志卡（300×360，亮起 =「日志没用」的空）；机架/小人配角不发光。
 * 停留：红叉 556 落位 → 578 离场（22 帧 ≥30）；578–586 exitOp(7) + 限幅 10px 下摇。
 */
const F0 = 78;
const TO = 586;
const EXIT_LEN = 7;

/** 「?」glitch 容器：12 帧透明度模板（白名单：SC01 只给「?」）。 */
const GlitchWrap: React.FC<{N: number; f0: number; children: React.ReactNode}> = ({N, f0, children}) => {
  const n = N - f0;
  if (n < 0) return null;
  const op = n >= GLITCH_SEQ.length ? 1 : GLITCH_SEQ[n];
  if (op <= 0) return null;
  return <div style={{position: 'absolute', inset: 0, opacity: op}}>{children}</div>;
};

/** 淡入（easeOut，n<0 → 0）。 */
const opFade = (n: number, len = 8) => (n < 0 ? 0 : clamp01(n / len));

const SC01: React.FC = () => {
  const N = useCurrentFrame() + F0;
  const ex = N - (TO - EXIT_LEN);
  const exOp = exitOp(N, TO, EXIT_LEN);
  const exDy = ex > 0 ? Math.min(10, exitAccel(ex, 0.15)) : 0;

  // ---- 86：机架缩放 21f + 道路 draw-on 30f（250 变暗）----
  const rn = N - 86;
  const rackS = clamp01(rn / 21);
  const roadOp = keyframes(N, [[78, 0], [86, 0.5], [249, 0.5], [262, 0.15]]);
  const roadP = clamp01((N - 86) / 30);

  // ---- 133：举报气泡（slideUp 40px / 16f），250–270 退场 ----
  const bn = N - 133;
  const bOp = bn < 0 ? 0 : opFade(bn, 8) * (1 - clamp01((N - 250) / 20));

  // ---- 181：小房子（12f 入场），205 起碎裂 3 片 rnd 飞散，250 后压暗 ----
  const hn = N - 181;
  const hOp = hn < 0 ? 0 : opFade(hn, 8);
  const crackK = clamp01((N - 205) / 35);
  const hDim = 1 - 0.35 * clamp01((N - 250) / 30);

  // ---- 360：两小人 slideUp，间距 60→200 / 30f（追不上）----
  const pn = N - 360;
  const pDy = pn < 0 ? 0 : 55 * powOutRemain(pn, 18, 2.5);
  const pOp = pn < 0 ? 0 : opFade(pn, 6);
  const gap = 60 + 140 * (1 - powOutRemain(pn, 30, 2));
  const x1 = 575, x2 = 575 + gap;

  // ---- 455：日志卡（主角）+ 496 打字机 + 546 坐标? + 红叉 ----
  const ln = N - 455;
  const lDy = ln < 0 ? 0 : 70 * powOutRemain(ln, 20, 2.5);
  const lOp = ln < 0 ? 0 : opFade(ln, 6);
  const reveal = clamp01((N - 496) / 14) * 7;
  const cn = N - 546;
  const crossP = clamp01(cn / 10);
  const glowK = clamp01((N - 478) / 14) * (1 - 0.5 * clamp01((N - 556) / 20));

  return (
    <div style={{...abs(0, 0, 1280, 720), opacity: exOp, transform: exDy ? `translateY(${exDy.toFixed(2)}px)` : undefined}}>
      {/* 灰虚线道路（贯穿背景，250 变暗） */}
      <svg width={1280} height={720} viewBox="0 0 1280 720" style={{position: 'absolute', left: 0, top: 0, opacity: roadOp}}>
        <line x1={100} y1={560} x2={1180} y2={560} stroke="#747474" strokeWidth={2.5} pathLength={100} strokeDasharray="100" strokeDashoffset={100 * (1 - roadP)} />
      </svg>

      {/* 服务器机架（86 缩放入场 + 指示灯红绿交替） */}
      {rn >= 0 ? (
        <div style={{...abs(0, 0, 1280, 720), transformOrigin: '300px 360px', transform: `scale(${(0.55 + 0.45 * rackS).toFixed(3)})`, opacity: clamp01(0.15 + 1.4 * rackS)}}>
          <ServerBox N={N} />
        </div>
      ) : null}

      {/* 举报气泡 */}
      {bOp > 0 ? (
        <div style={{...abs(430, 180 + 40 * powOutRemain(bn, 16, 2.5), 252, 76), opacity: bOp}}>
          <div style={{position: 'absolute', inset: 0, boxSizing: 'border-box', border: '2px solid #FFF', borderRadius: 14, background: '#000'}} />
          <div style={{position: 'absolute', left: 26, top: 60, width: 0, height: 0, borderLeft: '12px solid transparent', borderRight: '12px solid transparent', borderTop: '13px solid #FFF'}} />
          <div style={{position: 'absolute', left: 28, top: 62, width: 0, height: 0, borderLeft: '10px solid transparent', borderRight: '10px solid transparent', borderTop: '11px solid #000'}} />
          <CText cx={126} cy={33} size={26} weight={700} color={WHITE}>有人拆了我家！</CText>
        </div>
      ) : null}

      {/* 小房子 + 碎块 */}
      {hOp > 0 ? <HouseBox N={N} op={hOp * hDim} crackK={crackK} /> : null}

      {/* 两小人（间距拉大）+ 红 ?（glitch 白名单） */}
      {pOp > 0 ? (
        <>
          <RunnerBox cx={x1} cy={560 - pDy} N={N} op={pOp} />
          <RunnerBox cx={x2} cy={560 - pDy - 6 * powOutRemain(pn, 18, 2)} N={N} op={pOp} speed={1.18} />
          {qnGlitch(N) > 0 ? (
            <GlitchWrap N={N} f0={360}>
              <CText cx={x2} cy={426} size={46} weight={900} color={CORAL} shadow="0 0 14px rgba(243,95,69,.55)">?</CText>
            </GlitchWrap>
          ) : null}
        </>
      ) : null}

      {/* 日志卡（主角）：白边黑底 + [聊天] 记录 + 行条打字机 + 「坐标?」被红叉划掉 */}
      {lOp > 0 ? (
        <div style={{...abs(830, 200 + lDy, 300, 360), opacity: lOp}}>
          <HeroGlow x={830} y={200} w={300} h={360} k={glowK} />
          <div style={{position: 'absolute', inset: 0, boxSizing: 'border-box', border: '2.5px solid #FFF', borderRadius: 8, background: '#000'}} />
          <CText cx={150} cy={32} size={24} weight={600} color={GREY} family={FONT_MONO} opacity={opFade(N - 496, 8)}>[聊天] 哈哈哈</CText>
          {Array.from({length: 7}, (_, i) => {
            const k = clamp01(reveal - i);
            if (k <= 0) return null;
            const lw = (0.42 + 0.4 * (((i * 7 + 3) % 10) / 10)) * 252;
            return <div key={i} style={{position: 'absolute', left: 24, top: 58 + i * 38, width: lw * k, height: 4, borderRadius: 2, background: '#4A4A4A', opacity: 0.9}} />;
          })}
          {cn >= 0 ? (
            <div style={{position: 'absolute', inset: 0, opacity: opFade(cn, 8)}}>
              <CText cx={233} cy={303} size={26} weight={700} color={GREY}>坐标?</CText>
            </div>
          ) : null}
          {crossP > 0 ? (
            <svg width={300} height={360} viewBox="0 0 300 360" style={{position: 'absolute', left: 0, top: 0, overflow: 'visible'}}>
              <Cross cx={233} cy={301} size={52} sw={6} p={crossP} />
            </svg>
          ) : null}
        </div>
      ) : null}
    </div>
  );
};

const qnGlitch = (N: number) => (N < 360 ? 0 : 1);

/** 机架：三格 + 红绿灯 stepKf 交替（持续动作）。 */
const ServerBox: React.FC<{N: number}> = ({N}) => {
  const w = 220, h = 280, sw = 3, units = 3;
  const uh = (h - 2 * sw - 10) / units;
  return (
    <div style={{...abs(190, 220, w, h)}}>
      <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', filter: 'drop-shadow(0 0 2px rgba(255,255,255,.35))'}}>
        <rect x={sw / 2} y={sw / 2} width={w - sw} height={h - sw} rx={9} fill="#000" stroke="#FFF" strokeWidth={sw} />
        {Array.from({length: units}, (_, i) => {
          const y = sw + 6 + i * uh;
          const phase = Math.floor((N + i * 13) / 15) % 2 === 0;
          return (
            <g key={i}>
              <rect x={sw + 5} y={y} width={w - 2 * sw - 10} height={uh - 6} rx={4} fill="#0a0a0a" stroke="#747474" strokeWidth={1.5} />
              <circle cx={sw + 20} cy={y + (uh - 6) / 2} r={5} fill={phase ? '#8FF740' : '#16300f'} />
              <circle cx={sw + 40} cy={y + (uh - 6) / 2} r={5} fill={phase ? '#381016' : '#EC081F'} />
              {[0, 1, 2, 3].map((k) => (
                <rect key={k} x={w - sw - 40 + k * 9} y={y + (uh - 6) / 2 - 5} width={3.5} height={10} fill="#4A4A4A" />
              ))}
            </g>
          );
        })}
      </svg>
    </div>
  );
};

/** 小房子 + 3 片碎块（rnd 方向飞散）。 */
const HouseBox: React.FC<{N: number; op: number; crackK: number}> = ({N, op, crackK}) => {
  const cx = 490, cy = 505, w = 112, h = 100;
  const wallH = 62;
  return (
    <svg width={280} height={240} viewBox={`${cx - 140} ${cy - 200} 280 240`} style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', opacity: op, filter: 'drop-shadow(0 0 2px rgba(255,255,255,.35))'}}>
      <rect x={cx - w / 2} y={cy - wallH} width={w} height={wallH} fill="#000" stroke="#FFF" strokeWidth={2.5} />
      <polygon points={`${cx - w / 2 - 6},${cy - wallH} ${cx},${cy - h} ${cx + w / 2 + 6},${cy - wallH}`} fill="#000" stroke="#FFF" strokeWidth={2.5} strokeLinejoin="round" />
      <rect x={cx - w * 0.14} y={cy - wallH * 0.55} width={w * 0.28} height={wallH * 0.55} fill="#000" stroke="#FFF" strokeWidth={2} />
      {crackK > 0.02 ? <path d={`M${cx - w * 0.1},${cy - wallH} L${cx - w * 0.02},${cy - wallH * 0.62} L${cx - w * 0.16},${cy - wallH * 0.36}`} fill="none" stroke="#FFF" strokeWidth={2} opacity={0.9} /> : null}
      {[0, 1, 2].map((i) => {
        if (crackK <= 0.01) return null;
        const ang = -Math.PI * (0.2 + rnd(51, i) * 0.55);
        const dist = (26 + rnd(52, i) * 40) * crackK;
        const x = cx - w * 0.28 + i * w * 0.28 + Math.cos(ang) * dist;
        const y = cy - wallH - 8 + Math.sin(ang) * dist;
        const s = 7 + rnd(53, i) * 5;
        return <rect key={i} x={x - s / 2} y={y - s / 2} width={s} height={s} fill="#000" stroke="#FFF" strokeWidth={2} opacity={1 - crackK} transform={`rotate(${(crackK * 130 * (rnd(54, i) > 0.5 ? 1 : -1)).toFixed(1)} ${x} ${y})`} />;
      })}
    </svg>
  );
};

/** 跑动小人：腿臂 sin 摆 + 跑步起伏（持续动作）。 */
const RunnerBox: React.FC<{cx: number; cy: number; N: number; op: number; speed?: number}> = ({cx, cy, N, op, speed = 1}) => {
  const h = 104, u = h / 96;
  const ph = N * 0.42 * speed;
  const legA = Math.sin(ph), legB = Math.sin(ph + Math.PI);
  const bob = Math.abs(Math.cos(ph)) * 3 * u;
  const ax = h * 0.45;
  const hipY = h - 34 * u, shY = h - 58 * u, headY = h - 78 * u;
  return (
    <svg width={h * 0.9} height={h} viewBox={`0 0 ${h * 0.9} ${h}`} style={{position: 'absolute', left: cx - ax, top: cy - h, overflow: 'visible', opacity: op, filter: 'drop-shadow(0 0 2px rgba(255,255,255,.35))'}}>
      <g stroke="#FFF" strokeWidth={3 * u} strokeLinecap="round" fill="none">
        <line x1={ax} y1={hipY - bob} x2={ax + legA * 18 * u} y2={h - Math.max(0, legA) * 9 * u - bob} />
        <line x1={ax} y1={hipY - bob} x2={ax + legB * 18 * u} y2={h - Math.max(0, legB) * 9 * u - bob} />
        <line x1={ax} y1={shY - bob} x2={ax} y2={hipY - bob} />
        <line x1={ax} y1={shY + 4 * u - bob} x2={ax + legB * 15 * u} y2={shY + 24 * u - bob} />
        <line x1={ax} y1={shY + 4 * u - bob} x2={ax + legA * 15 * u} y2={shY + 24 * u - bob} />
      </g>
      <circle cx={ax} cy={headY - bob} r={11 * u} fill="#000" stroke="#FFF" strokeWidth={3 * u} />
    </svg>
  );
};

export default SC01;
