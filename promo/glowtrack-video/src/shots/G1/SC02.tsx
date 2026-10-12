import React from 'react';
import {useCurrentFrame} from 'remotion';
import {FONT_WIDE, GLITCH_SEQ, clamp01, powOutRemain, easeInOutPow, keyframes, emphasisPulse} from '../../common';
import {CText, Box, WHITE, PURPLE, PURPLE_LIGHT, GREY, GLOW_PURPLE_S, abs, exitAccel, exitOp} from '../../ui';
import {LightSweep, StageLine, GhostText, ghostOpacity, HaloRing} from '../../fx';
import {TrackLine, PlayerDot} from './parts';

/**
 * SC02 模组登场（高光·登场型，扫光白名单①）+ 采样演示（632–1092，S05–S08）。
 * 前半（640–865 清场登场，SET_PIECE T0=640 / T1=690）：
 *   644/662/680 三轮 LightSweep；658 StageLine 展宽呼吸（690 白闪 3f 消失）；677 GhostText 10% 预示；
 *   690 「GlowTrack」Audiowide 132 白 + 紫硬投影 GlitchIn rgbSplit8 slices20（重口味只此一处）；
 *   702 emphasisPulse 1.11；714/716/718 三 Pill GlitchIn（普通模板，分镜表白名单授权）2 帧错峰；
 *   729 眼睛 slideUp；787–791 五光点 1 帧错峰亮起；819 S 曲线 draw-on 26f + 末端光环脉冲。
 * 后半（876–1090 采样演示，运镜 = 大字组缩小 0.55 上移 20f，承接位移不进末拍）：
 *   876 机架+小人 slideUp、地面线 draw-on；916 机架白名单绿灯亮 + 小人起跑；968 时钟 +「每隔几秒」+ 第 1 步；
 *   968/992/1016/1040 四步各落 1 光点（每步右移 80px，1047 末光点落位）；1045 四光点 1 帧错峰柔光脉冲。
 * 停留：末光点 1047 落位 → 1085 离场（38 帧）；1085–1092 exitOp(7)+下摇 ≤10px。
 * 注：分镜表的时钟「5s」数字无调研出处（调研.md 数字清单无采样间隔）→ 按协议 0.4 改标「每隔几秒」文字，BUILD_NOTES 已报。
 */
const F0 = 632;
const TO = 1092;
const EXIT_LEN = 7;
const T0 = 640, T1 = 690;
const SWEEPS = [T0 + 4, T0 + 22, T0 + 40];
const T_LINE = T0 + 18, T_GHOST = T0 + 37, T_PULSE = T1 + 12, T_PILLS = T1 + 24;
const T_EYE = 729, T_DOTS = 787, T_CURVE = 819;
const T_CAM = 876, T_RACK = 876, T_LIST = 916, T_CLOCK = 968;
const STEPS = [968, 992, 1016, 1040];
const STEP_DX = 80, X0 = 560, GROUND = 560;
const T_EXIT = TO - EXIT_LEN; // 1085

const SC02: React.FC = () => {
  const N = useCurrentFrame() + F0;
  const ex = N - T_EXIT;
  const exOp = exitOp(N, TO, EXIT_LEN);
  const exDy = ex > 0 ? Math.min(10, exitAccel(ex, 0.15)) : 0;

  // ---- 前半：光扫 / 舞台光线 / 幽灵轮廓 ----
  const ghostOp = ghostOpacity(N, T_GHOST, T_PULSE);
  const ln = N - T_LINE;

  // ---- 大字（690 glitch + 702 脉冲）----
  const rn = N - T1;
  const pulse = emphasisPulse(N - T_PULSE, {peak: 1.11});

  // ---- 三 Pill（714/716/718 GlitchIn 普通模板；876 随演示接管淡出）----
  const pillsOp = 1 - clamp01((N - T_CAM) / 16);

  // ---- 眼睛（729 slideUp；876 淡出）----
  const en = N - T_EYE;
  const eyeDy = en < 0 ? 0 : 60 * powOutRemain(en, 18, 2.5);
  const eyeOp = en < 0 ? 0 : clamp01(en / 8) * (1 - clamp01((N - T_CAM) / 12));

  // ---- 五光点（787–791）+ S 曲线（819 draw-on 26f）+ 末端光环 ----
  const S_D = 'M 430 556 C 510 520 560 560 640 545 C 720 530 760 560 850 522';
  const curveP = clamp01((N - T_CURVE) / 26);
  const dotsFade = 1 - clamp01((N - T_CAM) / 14);
  const ringP = clamp01((N - T_CURVE - 18) / 16);
  const ringPulse = N >= 855 ? emphasisPulse(N - 855, {peak: 1.14, up: 10, hold: 3, down: 11}) : 1;

  // ---- 运镜：大字组缩小 0.55 上移到 (640,190)，20f easeInOutPow(2.5) ----
  const cn = N - T_CAM;
  const camT = cn <= 0 ? 0 : easeInOutPow(2.5)(clamp01(cn / 20));
  const bigScale = 1 - 0.45 * camT;
  const bigDy = -140 * camT;

  // ---- 后半：机架 / 小人 / 地面线 / 时钟 ----
  const rackN = N - T_RACK;
  const rackDy = rackN < 0 ? 0 : 60 * powOutRemain(rackN, 18, 2.5);
  const rackOp = rackN < 0 ? 0 : clamp01(rackN / 8);
  const runN = N - T_LIST; // 916 起跑
  const stepIdx = STEPS.filter((t) => N >= t).length; // 已完成步数 0–4
  const px = X0 + STEP_DX * stepIdx;
  const roadP = clamp01((N - (T_RACK + 6)) / 24);
  const clockN = N - T_CLOCK;
  const clockOp = clockN < 0 ? 0 : clamp01(clockN / 10);

  return (
    <div style={{...abs(0, 0, 1280, 720), opacity: exOp, transform: exDy ? `translateY(${exDy.toFixed(2)}px)` : undefined}}>
      {/* 三轮紫光横扫 */}
      <LightSweep N={N} rounds={SWEEPS} />
      {/* 舞台光线（690 白闪 3f 后消失） */}
      <StageLine N={N} f0={T_LINE} flashAt={T1} cy={331} w={720} />

      {/* ===== 前半元素（876 起 16f 内全部让位） ===== */}
      {/* 大字组：Ghost + GlitchIn 大字（缩小上移 = 承接位移运镜） */}
      {rn >= 0 || ghostOp > 0 ? (
        <div style={{...abs(0, 0, 1280, 720), transformOrigin: '640px 330px', transform: bigScale < 1 || bigDy !== 0 ? `translate(0px, ${bigDy.toFixed(2)}px) scale(${bigScale.toFixed(4)})` : undefined}}>
          {ghostOp > 0 ? <GhostText cx={640} cy={330} size={132} family={FONT_WIDE} letterSpacing={4} opacity={ghostOp} dy={-4}>GlowTrack</GhostText> : null}
          {rn >= 0 ? (
            <div style={{position: 'absolute', inset: 0, transformOrigin: '640px 330px', transform: pulse === 1 ? undefined : `scale(${pulse.toFixed(4)})`}}>
              <BigGlitch N={N} f0={T1}>
                <CText cx={640} cy={330} size={132} weight={400} family={FONT_WIDE} letterSpacing={4} color={WHITE} dy={-4} shadow={`6px 6px 0 ${PURPLE}, 0 0 28px rgba(102,45,248,.45)`}>
                  GlowTrack
                </CText>
              </BigGlitch>
            </div>
          ) : null}
        </div>
      ) : null}

      {/* 眼睛（729） */}
      {eyeOp > 0 ? (
        <svg width={200} height={130} viewBox={`${-100} ${-65} 200 130`} style={{position: 'absolute', left: 540, top: 210 + eyeDy - 65, overflow: 'visible', opacity: eyeOp, filter: 'drop-shadow(0 0 10px rgba(102,45,248,.65))'}}>
          <path d="M-84,0 Q0,-52 84,0" fill="none" stroke={WHITE} strokeWidth={3.5} strokeLinecap="round" />
          <path d="M-84,0 Q0,44 84,0" fill="none" stroke={WHITE} strokeWidth={3.5} strokeLinecap="round" />
          <circle cx={0} cy={-2} r={21} fill={PURPLE} stroke={PURPLE_LIGHT} strokeWidth={2.5} />
          <circle cx={7} cy={-11} r={5.5} fill={WHITE} />
        </svg>
      ) : null}

      {/* 三 Pill（GlitchIn 2 帧错峰，白名单授权；876 淡出） */}
      {pillsOp > 0.01 ? (
        <>
          {(['3D 轨迹', '时间回放', '地图联动'] as const).map((t, i) => (
            <PillGlitch key={t} N={N} f0={T_PILLS + i * 2} op={pillsOp}>
              <Box x={420 + i * 220 - 95} y={468} w={190} h={46} r={23} sw={2} glow={i === 0 ? GLOW_PURPLE_S : undefined}>
                <CText cx={95} cy={21} size={26} weight={700} color={WHITE} style={{position: 'absolute'}}>{t}</CText>
              </Box>
            </PillGlitch>
          ))}
        </>
      ) : null}

      {/* 五光点 + S 曲线 + 末端光环（787 / 819；876 淡出） */}
      {dotsFade > 0.01 ? (
        <div style={{position: 'absolute', inset: 0, opacity: dotsFade}}>
          {[0.08, 0.29, 0.5, 0.71, 0.92].map((t, i) => {
            const n = N - (T_DOTS + i);
            if (n < 0) return null;
            const [dx, dy] = pointAt(S_D, t);
            const s = 0.4 + 0.6 * clamp01(n / 8);
            const br = 1 + 0.12 * Math.sin((N + i * 11) * 0.22);
            return <div key={i} style={{position: 'absolute', left: dx - 13 * s, top: dy - 13 * s, transform: `scale(${(s * br).toFixed(3)})`}}><PlayerDot cx={13} cy={13} r={13} glowK={0.55} /></div>;
          })}
          {curveP > 0 ? <TrackLine d={S_D} w={8} p={curveP} glowK={0.6} /> : null}
          {ringP > 0 ? (
            <div style={{position: 'absolute', left: 850 - 66, top: 522 - 22, transform: ringPulse === 1 ? undefined : `scale(${ringPulse.toFixed(3)})`, transformOrigin: '66px 22px'}}>
              <HaloRing cx={66} cy={22} rxo={62} ryo={19} rxi={44} ryi={11} p={ringP} fillOp={0.8} ripples={false} />
            </div>
          ) : null}
        </div>
      ) : null}

      {/* ===== 后半：采样演示（876 起） ===== */}
      {rackOp > 0 ? (
        <>
          {/* 地面线 */}
          <svg width={1280} height={720} viewBox="0 0 1280 720" style={{position: 'absolute', left: 0, top: 0, opacity: rackOp * 0.9}}>
            <line x1={400} y1={GROUND} x2={980} y2={GROUND} stroke={GREY} strokeWidth={2.5} pathLength={100} strokeDasharray="100" strokeDashoffset={100 * (1 - roadP)} />
          </svg>
          {/* 机架（三格 + 916 白名单绿灯） */}
          <div style={{...abs(170, 330 + rackDy, 120, 180), opacity: rackOp}}>
            <MiniRack N={N} list={runN >= 0} />
          </div>
          {/* 玩家小人（968 起 4 步右移，腿摆持续） */}
          {runN >= 0 ? (
            <div style={{position: 'absolute', inset: 0}}>
              <Runner2 cx={px} cy={GROUND} N={N} op={rackOp} moving={runN >= 0} />
              {/* 每步身后落 1 光点（969/993/1017/1041，6f 落位） */}
              {STEPS.map((t, i) => {
                const n = N - (t + 1);
                if (n < 0) return null;
                const s = 0.3 + 0.7 * (1 - powOutRemain(n, 6, 2.2));
                const drop = 26 * powOutRemain(n, 6, 2);
                const br = 1 + 0.12 * Math.sin((N + i * 9) * 0.22);
                // 1045 柔光脉冲（1 帧错峰强调「撒下的光点」）
                const hot = N >= 1045 + i && N < 1045 + i + 14 ? emphasisPulse(N - (1045 + i), {peak: 1.28, up: 5, hold: 2, down: 7}) : 1;
                return (
                  <div key={i} style={{position: 'absolute', left: X0 + STEP_DX * i - 13, top: GROUND - drop - 13, transform: `scale(${(s * br * hot).toFixed(3)})`}}>
                    <PlayerDot cx={13} cy={13} r={13} glowK={0.6} />
                  </div>
                );
              })}
            </div>
          ) : null}
          {/* 时钟 +「每隔几秒」（968；秒针扫描 = 采样间隔的持续动作） */}
          {clockOp > 0 ? (
            <>
              <Clock N={N} op={clockOp} cx={1046} cy={330} />
              <CText cx={1046} cy={412} size={26} weight={600} color={GREY} opacity={clockOp}>每隔几秒</CText>
            </>
          ) : null}
        </>
      ) : null}
    </div>
  );
};

/** S 曲线上 t 处的坐标（三次贝塞尔逐段采样，纯函数）。 */
const pointAt = (d: string, t: number): [number, number] => {
  // 与 S_D 一致的分段三次贝塞尔控制点
  const segs: Array<Array<[number, number]>> = [
    [[430, 556], [510, 520], [560, 560], [640, 545]],
    [[640, 545], [720, 530], [760, 560], [850, 522]],
  ];
  const tt = clamp01(t) * segs.length;
  const si = Math.min(segs.length - 1, Math.floor(tt));
  const u = tt - si;
  const [p0, p1, p2, p3] = segs[si];
  const w = (a: number, b: number, c: number, e: number) => a * (1 - u) ** 3 + 3 * b * u * (1 - u) ** 2 + 3 * c * u * u * (1 - u) + e * u ** 3;
  return [w(p0[0], p1[0], p2[0], p3[0]), w(p0[1], p1[1], p2[1], p3[1])];
};

/** 「GlowTrack」大字 GlitchIn（rgbSplit 8 / slices 20，重口味只此一处）。 */
const BigGlitch: React.FC<{N: number; f0: number; children: React.ReactNode}> = ({N, f0, children}) => {
  const n = N - f0;
  if (n < 0) return null;
  const op = n >= GLITCH_SEQ.length ? 1 : GLITCH_SEQ[n];
  const active = n < GLITCH_SEQ.length;
  if (op <= 0) return null;
  const seed = 8;
  const k = 8 / 6;
  const mOff: [number, number] = active ? [-(3 + 6 * ((Math.sin(N * 12.9898 + seed) * 43758.5453) % 1 + 1) % 1) * k, -(1 + 4 * 0.5) * k] : [-6 * k, -3 * k];
  const cOff: [number, number] = active ? [3 * k, (2 + 6 * 0.5) * k] : [3 * k, 5 * k];
  const cut = (dir: 1 | -1) => (
    <div key={dir} style={{position: 'absolute', inset: 0, filter: dir < 0 ? 'url(#g1tint-m)' : 'url(#g1tint-c)', transform: `translate(${(dir * mOff[0]).toFixed(1)}px, ${(dir * mOff[1]).toFixed(1)}px)`, opacity: op}}>{children}</div>
  );
  return (
    <div style={{position: 'absolute', inset: 0, opacity: op}}>
      <TintDefs />
      {active ? cut(-1) : null}
      {active ? cut(1) : null}
      <div style={{position: 'absolute', inset: 0}}>{children}</div>
    </div>
  );
};

const TintDefs: React.FC = () => (
  <svg width={0} height={0} style={{position: 'absolute'}}>
    <defs>
      <filter id="g1tint-m" colorInterpolationFilters="sRGB"><feColorMatrix type="matrix" values="0.174 0.586 0.059 0 0  0 0 0 0 0  0.178 0.600 0.061 0 0  0 0 0 1 0" /></filter>
      <filter id="g1tint-c" colorInterpolationFilters="sRGB"><feColorMatrix type="matrix" values="0.073 0.247 0.025 0 0  0.213 0.715 0.072 0 0  0.199 0.667 0.067 0 0  0 0 0 1 0" /></filter>
    </defs>
  </svg>
);

/** 三 Pill 的 GlitchIn（12 帧透明度模板，2 帧错峰；外层 op 由镜头控制淡出）。 */
const PillGlitch: React.FC<{N: number; f0: number; op: number; children: React.ReactNode}> = ({N, f0, op, children}) => {
  const n = N - f0;
  if (n < 0 || op <= 0.01) return null;
  const g = n >= GLITCH_SEQ.length ? 1 : GLITCH_SEQ[n];
  if (g <= 0) return null;
  return <div style={{position: 'absolute', inset: 0, opacity: g * op}}>{children}</div>;
};

/** 小机架（三格；list=true 时第 1 格绿灯常亮 = 白名单玩家在线）。 */
const MiniRack: React.FC<{N: number; list: boolean}> = ({N, list}) => {
  const w = 120, h = 180, sw = 2.5, units = 3;
  const uh = (h - 2 * sw - 8) / units;
  return (
    <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', filter: 'drop-shadow(0 0 2px rgba(255,255,255,.35))'}}>
      <rect x={sw / 2} y={sw / 2} width={w - sw} height={h - sw} rx={8} fill="#000" stroke={WHITE} strokeWidth={sw} />
      {Array.from({length: units}, (_, i) => {
        const y = sw + 5 + i * uh;
        const green = i === 0 && list;
        return (
          <g key={i}>
            <rect x={sw + 4} y={y} width={w - 2 * sw - 8} height={uh - 5} rx={3} fill="#0a0a0a" stroke="#747474" strokeWidth={1.3} />
            <circle cx={sw + 15} cy={y + (uh - 5) / 2} r={4} fill={green ? '#8FF740' : '#4A4A4A'} />
            <circle cx={sw + 30} cy={y + (uh - 5) / 2} r={4} fill={i === 1 && list ? '#EC081F' : '#381016'} />
            <rect x={w - sw - 26} y={y + (uh - 5) / 2 - 3} width={14} height={2.5} fill="#4A4A4A" />
          </g>
        );
      })}
    </svg>
  );
};

/** 跑动小人（968 起步跑，腿臂 sin 摆）。 */
const Runner2: React.FC<{cx: number; cy: number; N: number; op: number; moving: boolean}> = ({cx, cy, N, op, moving}) => {
  const h = 100, u = h / 96;
  const ph = moving ? N * 0.4 : 0;
  const legA = Math.sin(ph), legB = Math.sin(ph + Math.PI);
  const bob = Math.abs(Math.cos(ph)) * 3 * u;
  const ax = h * 0.45;
  const hipY = h - 34 * u, shY = h - 58 * u, headY = h - 78 * u;
  return (
    <svg width={h * 0.9} height={h} viewBox={`0 0 ${h * 0.9} ${h}`} style={{position: 'absolute', left: cx - ax, top: cy - h, overflow: 'visible', opacity: op, filter: 'drop-shadow(0 0 2px rgba(255,255,255,.35))'}}>
      <g stroke={WHITE} strokeWidth={3 * u} strokeLinecap="round" fill="none">
        <line x1={ax} y1={hipY - bob} x2={ax + legA * 17 * u} y2={h - Math.max(0, legA) * 8 * u - bob} />
        <line x1={ax} y1={hipY - bob} x2={ax + legB * 17 * u} y2={h - Math.max(0, legB) * 8 * u - bob} />
        <line x1={ax} y1={shY - bob} x2={ax} y2={hipY - bob} />
        <line x1={ax} y1={shY + 4 * u - bob} x2={ax + legB * 14 * u} y2={shY + 22 * u - bob} />
        <line x1={ax} y1={shY + 4 * u - bob} x2={ax + legA * 14 * u} y2={shY + 22 * u - bob} />
      </g>
      <circle cx={ax} cy={headY - bob} r={11 * u} fill="#000" stroke={WHITE} strokeWidth={3 * u} />
    </svg>
  );
};

/** 时钟（秒针 9°/帧扫描）。 */
const Clock: React.FC<{N: number; op: number; cx: number; cy: number}> = ({N, op, cx, cy}) => {
  const r = 42;
  const sec = (N * 9) % 360;
  return (
    <svg width={2 * r + 16} height={2 * r + 16} viewBox={`${-r - 8} ${-r - 8} ${2 * r + 16} ${2 * r + 16}`} style={{position: 'absolute', left: cx - r - 8, top: cy - r - 8, overflow: 'visible', opacity: op, filter: 'drop-shadow(0 0 2px rgba(255,255,255,.35))'}}>
      <circle cx={0} cy={0} r={r} fill="#000" stroke={WHITE} strokeWidth={3} />
      {Array.from({length: 12}, (_, i) => {
        const a = (i * 30 * Math.PI) / 180;
        return <line key={i} x1={Math.sin(a) * (r - 6)} y1={-Math.cos(a) * (r - 6)} x2={Math.sin(a) * (r - 13)} y2={-Math.cos(a) * (r - 13)} stroke="#747474" strokeWidth={2} />;
      })}
      <line x1={0} y1={0} x2={r * 0.34} y2={-r * 0.3} stroke={WHITE} strokeWidth={3} strokeLinecap="round" />
      <line x1={0} y1={0} x2={Math.sin((sec * Math.PI) / 180) * r * 0.72} y2={-Math.cos((sec * Math.PI) / 180) * r * 0.72} stroke={PURPLE_LIGHT} strokeWidth={2.5} strokeLinecap="round" />
      <circle cx={0} cy={0} r={3.5} fill={WHITE} />
    </svg>
  );
};

export default SC02;
