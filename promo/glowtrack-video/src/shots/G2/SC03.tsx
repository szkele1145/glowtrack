import React from 'react';
import {useCurrentFrame} from 'remotion';
import {GlitchIn, clamp01, kf, powOutRemain, easeInOutPow, FONT_ORB} from '../../common';
import {CText, WHITE, PURPLE, GREY, abs, softOp, firstOp, glowOffK, glowPurpleS, SoftIn, mixHex, Svg} from '../../ui';
import {TrackLine, ServerIcon, PlayerDot} from '../G3/parts';
import {MonitorIcon, DotCard, ArcArrow} from './parts';

/**
 * SC03 推送亮起 + 速度热力（1113–1655，分镜表 G2 行 SC03）
 * 前半 1113–1390：ServerIcon + 三张光点卡叠放 +「攒批」紫Pill（SoftIn）→ 弧形箭头 draw-on 绕到右下
 *   MonitorIcon（白边 260×180+底座）→ 屏内 TrackLine draw-on 30 帧（线宽 10 彩虹）→ 末段幕底压暗。
 * 后半 1390–1655：演示区接管（显示器 kf 缩小移到左上角留语境）；横向轨迹 x200–1080, y440, 宽12：
 *   左半 #58A6FF 纯色 draw-on（1397–1512 节拍），右半 1523 起 draw-on 且颜色绿→红烧（色相 0.33→0 kf，持续到 1653）；
 *   三个小人位标 0.5/2/5 格/s（Orbitron 26px，1 帧错峰）；色带图例 800×14 + 慢/快 CText（「快」唯一一处 GlitchIn）。
 * 持续：三速度位 PlayerDot 缩小呼吸；落位 1597 后停 48 帧，1645 起 opacity=1−(n/10)^1.5 归零（N=TO 恰为 0）。
 */
const F0 = 1113, TO = 1655;
const B_PUSH = 1123;      // 服务器 + 光点卡
const B_PILL = 1173;      // 攒批 Pill
const B_ARC = 1216;       // 弧形箭头 draw-on
const B_MON = 1246;       // 显示器
const B_TL = 1286;        // 屏内轨迹 draw-on 30 帧
const B_DIM = 1362;       // 幕底压暗（12 帧）
const B_HALF = 1390;      // 后半接管
const B_L = 1397;         // 左半 draw-on（115 帧，1512 到位）
const B_R = 1523;         // 右半 draw-on（74 帧，1597 落位）
const B_SPD = 1520;       // 速度位标（1 帧错峰）
const B_LEG = 1546;       // 色带图例
const T_SETTLE = B_R + 74; // 1597 落位
const T_EXIT = TO - 10;    // 1645：停 48 帧后 1−(n/10)^1.5 收尾

// ---- 前半布景 ----
const SRV = {cx: 280, cy: 340};
const CARDS = [{x: 430, y: 250, rot: -2.5}, {x: 438, y: 318, rot: 1.5}, {x: 426, y: 388, rot: -1}];
const MON_A = {cx: 640, cy: 540};          // 前半右下
const MON_B = {cx: 160, cy: 170, s: 0.5};  // 后半左上缩小
const MON_TRACK: Array<[number, number]> = [[560, 512], [610, 494], [660, 510], [710, 492], [720, 502]];

// ---- 后半演示区 ----
const TRACK_Y = 440;
const SPEED_X = [320, 640, 960];
const SPEEDS = ['0.5', '2', '5'];
const LEG_Y = 560;
const GREEN_END = '#4FD08A'; // heatColor(0.34) 端色
const RED_END = '#F05F41';   // heatColor(1) 端色

export const SC03: React.FC = () => {
  const N = useCurrentFrame() + F0;
  // ---- 离场：末拍停 48 帧后 1−(n/10)^1.5 收尾（N=TO 时为 0）----
  const gOff = glowOffK(N, TO, 6, 10);
  const exitN = N - T_EXIT;
  const opAll = exitN <= 0 ? 1 : Math.max(0, 1 - Math.pow(exitN / 10, 1.5));

  // ---- 前半：推送 ----
  const np = N - B_PUSH;
  const srvOp = firstOp(np, 6);
  const cardOp = (i: number) => softOp(np - 8 - i * 5, 7);
  const cardDy = (i: number) => powOutRemain(np - 8 - i * 5, 20, 2.5) * 14;
  const na = N - B_ARC;
  const arcP = easeInOutPow(1.8)(clamp01(na / 24));
  const nm = N - B_MON;
  const monOp = softOp(nm, 8);
  const monDy = powOutRemain(nm, 18, 2.5) * 26;
  const nt = N - B_TL;
  const screenP = clamp01(nt / 30); // 屏内 draw-on 30 帧
  const scrGlow = nt >= 0 && nt < 22 ? glowPurpleS(clamp01(nt / 6)) : '';

  // ---- 幕底压暗（前半末段进，后半接管退回）----
  const dimOp = 0.35 * clamp01((N - B_DIM) / 12) * (1 - clamp01((N - B_HALF) / 14));

  // ---- 后半接管 ----
  const nh = N - B_HALF;
  const halfIn = clamp01(nh / 14);
  const monScale = kf(N, [[B_HALF, 1], [B_HALF + 22, MON_B.s]], easeInOutPow(2.2));
  const monX = kf(N, [[B_HALF, MON_A.cx], [B_HALF + 22, MON_B.cx]], easeInOutPow(2.2));
  const monY = kf(N, [[B_HALF, MON_A.cy], [B_HALF + 22, MON_B.cy]], easeInOutPow(2.2));
  const halfOp = clamp01((nh - 4) / 10);

  // 左半：#58A6FF 纯色 draw-on（115 帧）
  const pL = clamp01((N - B_L) / 115);
  const peL = 1 - Math.pow(1 - pL, 2);
  // 右半：1523 起 draw-on（74 帧），颜色绿→红烧（色相 0.33→0，130 帧持续到 1653）
  const nR = N - B_R;
  const pR = clamp01(nR / 74);
  const peR = 1 - Math.pow(1 - pR, 2);
  const burnK = clamp01(nR / 130);
  const heatC = mixHex(GREEN_END, RED_END, burnK);

  // 速度位标（1 帧错峰）+ 小灯呼吸
  const spdOp = (i: number) => softOp(N - (B_SPD + i), 6);
  const dotK = (i: number) => 0.55 + 0.45 * Math.sin(N * 0.11 + i * 1.4);

  // 色带图例（wipe 自左长出）
  const legOp = softOp(N - B_LEG, 8);
  const legWipe = clamp01((N - B_LEG) / 14);
  const legGlowK = clamp01((N - B_LEG - 6) / 8);

  return (
    <div style={{position: 'absolute', inset: 0, opacity: opAll}}>
      {/* ===== 幕底压暗层 ===== */}
      {dimOp > 0.005 ? <div style={{position: 'absolute', inset: 0, background: '#000', opacity: dimOp}} /> : null}

      {/* ===== 前半：服务器 + 光点卡 + 攒批 Pill（接管后淡出） ===== */}
      <div style={{position: 'absolute', inset: 0, opacity: 1 - halfIn}}>
        {srvOp > 0 ? (
          <div style={{position: 'absolute', inset: 0, transform: `translateY(${(powOutRemain(np, 16, 2.5) * -18).toFixed(1)}px)`, opacity: srvOp}}>
            <ServerIcon cx={SRV.cx} cy={SRV.cy} w={200} h={250} seed={3} />
          </div>
        ) : null}
        {CARDS.map((c, i) => {
          const o = cardOp(i);
          if (o <= 0) return null;
          return (
            <div key={i} style={{...abs(c.x, c.y + cardDy(i), 300, 72), opacity: o}}>
              <DotCard x={0} y={0} seed={i + 1} rot={c.rot} />
            </div>
          );
        })}
        <SoftIn N={N} f0={B_PILL}>
          <div style={{...abs(620 - 92, 208 - 27, 184, 54), borderRadius: 27, background: PURPLE, border: `2.5px solid ${WHITE}`, boxShadow: gOff > 0.01 ? glowPurpleS(0.55 * gOff) : undefined}}>
            <div style={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 900, fontSize: 30, color: WHITE, transform: 'scaleX(0.85)', letterSpacing: 2}}>攒批</div>
          </div>
        </SoftIn>
      </div>

      {/* ===== 弧形箭头（仅前半） ===== */}
      <ArcArrow p={arcP} opacity={srvOp * (1 - halfIn)} />

      {/* ===== 显示器（前半右下 → 后半左上缩小）+ 屏内轨迹 ===== */}
      {nm >= 0 ? (
        <div style={{position: 'absolute', inset: 0, transform: `translate(${monX.toFixed(1)}px,${monY.toFixed(1)}px) scale(${monScale.toFixed(4)}) translate(${-MON_A.cx}px,${-MON_A.cy}px) translateY(${monDy.toFixed(1)}px)`, opacity: monOp}}>
          <MonitorIcon cx={MON_A.cx} cy={MON_A.cy} />
          {scrGlow ? <div style={{position: 'absolute', inset: 0, boxShadow: scrGlow}} /> : null}
          {nt >= 0 ? (
            <div style={{position: 'absolute', inset: 0, clipPath: 'inset(462px 522px 102px 522px)'}}>
              <TrackLine pts={MON_TRACK} p={screenP} sw={10} heat="rainbow" />
            </div>
          ) : null}
          {nt >= 10 ? <PlayerDot cx={720} cy={502} r={9} t={0.62} k={dotK(2)} opacity={softOp(nt - 10, 6)} /> : null}
        </div>
      ) : null}

      {/* ===== 后半：演示区 ===== */}
      <div style={{position: 'absolute', inset: 0, opacity: halfOp}}>
        {/* 横向轨迹：左半纯蓝 + 右半绿烧红 */}
        <Svg bloom={true}>
          {pL > 0 ? <line x1={200} y1={TRACK_Y} x2={200 + 440 * peL} y2={TRACK_Y} stroke="#58A6FF" strokeWidth={12} strokeLinecap="butt" /> : null}
          {pR > 0 ? <line x1={640} y1={TRACK_Y} x2={640 + 440 * peR} y2={TRACK_Y} stroke={heatC} strokeWidth={12} strokeLinecap="butt" /> : null}
        </Svg>

        {/* 速度位标 + 小灯（PlayerDot 缩小 α≈0.5 呼吸）+ Orbitron 读数 */}
        {SPEED_X.map((x, i) => {
          const o = spdOp(i);
          if (o <= 0) return null;
          return (
            <React.Fragment key={i}>
              <div style={{position: 'absolute', inset: 0, opacity: o}}>
                <PlayerDot cx={x} cy={TRACK_Y} r={13} opacity={0.44 + 0.12 * dotK(i)} t={i === 0 ? 0.12 : i === 1 ? 0.45 : 0.95} k={dotK(i)} />
              </div>
              <div style={{...abs(x - 78, TRACK_Y + 26, 156, 32), opacity: o}}>
                <div style={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT_ORB, fontWeight: 700, fontSize: 26, color: WHITE, fontVariantNumeric: 'tabular-nums'}}>
                  {SPEEDS[i]}<span style={{fontSize: 20, opacity: 0.85, marginLeft: 4}}>格/s</span>
                </div>
              </div>
            </React.Fragment>
          );
        })}

        {/* 色带图例 800×14（x240–1040, y560，自左长出）+ 两端 CText */}
        <div style={{...abs(240, LEG_Y - 7, 800, 14), opacity: legOp}}>
          <div style={{position: 'absolute', inset: 0, borderRadius: 7, transform: `scaleX(${legWipe.toFixed(3)})`, transformOrigin: '0 50%', background: 'linear-gradient(90deg,#3E8EE8 0%,#4FD08A 45%,#E8D24A 72%,#F05F41 100%)', boxShadow: legGlowK > 0.01 && gOff > 0.01 ? `0 0 16px 3px rgba(240,95,65,${(0.4 * legGlowK * gOff).toFixed(2)})` : undefined}} />
        </div>
        <SoftIn N={N} f0={B_LEG + 6}>
          <CText cx={222} cy={LEG_Y + 1} size={26} weight={700} color={GREY}>慢</CText>
        </SoftIn>
        <GlitchIn N={N} f0={B_LEG + 12}>
          <CText cx={1058} cy={LEG_Y + 1} size={30} weight={900} color={WHITE}>快</CText>
        </GlitchIn>
      </div>
    </div>
  );
};
