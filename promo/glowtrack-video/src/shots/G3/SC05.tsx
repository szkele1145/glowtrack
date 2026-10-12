import React from 'react';
import {useCurrentFrame} from 'remotion';
import {GlitchIn, clamp01, kf, emphasisPulse, powOutRemain, easeInOutPow, FONT_HEAVY, FONT_ORB} from '../../common';
import {Svg, LineArrow, CText, WHITE, GREY, GREY_LINE, PURPLE, PURPLE_LIGHT, ORANGE, abs, fadeIn, slideUp, scaleIn, softOp, firstOp, exitOp, glowOffK, glowPurpleS, SoftIn} from '../../ui';
import {TrackLine, PlayerDot, ReadoutCard, PortalIcon, READOUT} from './parts';

/**
 * SC05 时间轴回放 + 传送（2026–2787，S15–S20，12 节拍长镜头）
 * 节拍（分镜表 G3 行）：
 *  2034 路亮/何时走的（彩虹轨迹 + 三枚灰问号挂线 slideUp）
 *  2161 靠时间轴（时间轴 0.72→1 弹入 + 热力 wipe 18 帧 +「时间轴」Pill glitch＝本镜头唯一一处）
 *  2256 按K（键帽 K GlitchIn 12 帧后 22 帧 fade，按下下沉 4px）
 *  2306 胶卷（轴上方上下两排齿孔条 wipe，2500 隐去给读数卡让位）
 *  2404 鼠标简笔右滑入按住游标（持续：颤动）
 *  2416/2431 游标拖左 x900→420（22 帧）+ 读数 16:42:08→15:07:33 倒退；停止 + 光圈脉冲
 *  2545 时间点竖线 1 帧错峰亮起；2587 三标签 14:02:11 / 15:07:33 / 16:42:08 slideUp
 *  2643 点击紫圈 draw-on（读数卡前挂 2639 入场）→ 2668 三行打字机（时刻黄/维度蓝/坐标白）
 *  2699 传送门 emphasisPulse（不用 glitch）+ 点击点→传送门弧线 draw-on + PlayerDot 落位
 * 持续：游标颤动、传送门波纹、时间轴紫光呼吸。停留：落位 2729 → 2781 归零离场（52 帧稳定期，末 6 帧淡出）。
 */
const F0 = 2026, TO = 2787;
const B_Q = 2034, B_TL = 2161, B_K = 2256, B_FILM = 2306, B_MOUSE = 2404;
const B_DRAG = 2416, B_STOP = 2431, B_POINT = 2545, B_LABEL = 2587;
const B_CLICK = 2643, B_CARD = 2639, B_TYPE = 2668, B_PORTAL = 2699;
const T_SETTLE = B_PORTAL + 30; // 2729 落位
const T_EXIT = 2781;            // 离场起点（末 6 帧归零，2787 末帧 = 0；落位→离场 52 帧）
// 时间轴（主角 ≥170px：880×26 横贯 + 紫光 + 游标/齿孔/标签群总高 ≈200）
const TL = {x: 200, y: 452, w: 880, h: 26};
const TL_IN = 18;
const TRACK_PTS: Array<[number, number]> = [[170, 258], [300, 216], [440, 258], [580, 214], [720, 252], [860, 218], [1000, 252], [1110, 236]];
const Q_X = [260, 640, 1020];
const Q_LIFT = 88;
const easeDrag = easeInOutPow(2.5);
const CURSOR_A = 900, CURSOR_B = 420;
const POINT_X = [292, 438, 584, 730, 876];
const LABELS = ['14:02:11', '15:07:33', '16:42:08'];
const CARD = {x: 830, y: 252, w: 380, h: 200};

export const SC05: React.FC = () => {
  const N = useCurrentFrame() + F0;
  // ---- 离场：先灭光再归零（末 6 帧 1−(n/6)^1.5，2787 末帧 = 0）----
  const gOff = glowOffK(N, TO, 4, 6);
  const opAll = exitOp(N, TO, 6);
  const exitDy = N >= T_EXIT ? Math.min(10, 0.2 * Math.pow(N - T_EXIT, 2)) : 0;

  // ---- 节拍 1：轨迹 + 问号挂线（2034）----
  const nq = N - (B_Q - 6);
  const trackP = clamp01(nq / 30);
  const qOp = firstOp(N - B_Q, 6);
  const qShake = (i: number) => (N - B_Q < 4 ? 0 : 1.6 * Math.sin((N - B_Q) * 0.14 + i * 2.1) * powOutRemain(N - B_Q - 4, 26, 1.6));
  const qDim = clamp01((N - (B_TL + 10)) / 14);   // 时间轴接管后问号压暗

  // ---- 节拍 2：时间轴弹入 + 热力 wipe（2161）----
  const nt = N - (B_TL - 5);
  const tlS = 1 - 0.28 * powOutRemain(Math.max(0, nt), 12, 2.2) + (nt >= 12 && nt < 27 ? 0.05 * Math.sin(((nt - 12) / 15) * Math.PI) : 0);
  const tlOp = softOp(Math.max(-1, nt), 8);
  const wipe = clamp01((N - B_TL) / TL_IN);
  const tlGlow = glowPurpleS(clamp01((N - B_TL) / 8) * 0.9);

  // ---- 节拍 3：键帽 K（2256）----
  const nk = N - B_K;
  const kOp = nk < 0 ? 0 : nk < 12 ? 1 : Math.max(0, 1 - (nk - 12) / 22);
  const kSink = nk >= 6 && nk < 14 ? 4 : 0;

  // ---- 节拍 4：胶卷齿孔（2306，2500 隐去）----
  const nf = N - B_FILM;
  const filmW = clamp01(nf / 16);
  const filmOp = softOp(nf, 6) * (1 - clamp01((N - 2490) / 20));
  const filmShift = nf > 22 ? Math.sin((N - B_FILM - 22) * 0.05) * 2 : 0;

  // ---- 节拍 5–7：鼠标滑入 / 拖动 / 停止 ----
  const nm = N - B_MOUSE;
  const mIn = 150 - 150 * clamp01(nm / 16);
  const mOp = softOp(nm, 6);
  const dragK = easeDrag(clamp01((N - B_DRAG) / 22));
  // 游标轨迹：弹入停在 900 → 拖左到 420 → 停 → 「试滑」两段（S18 任何一段路停下来）→ 停到 15:07:33 时间点(438)
  const cursorX = kf(N, [
    [B_TL, CURSOR_A], [B_DRAG, CURSOR_A], [B_DRAG + 22, CURSOR_B],
    [2448, CURSOR_B], [2480, 560], [2496, 560], [2520, 438],
  ], easeInOutPow(1.6));
  const wob = (i: number) => (N < B_DRAG ? 1.5 : 2.5) * Math.sin(N * 0.33 + i * 1.7);
  const stopPulse = emphasisPulse(N - (B_STOP + 2), {peak: 1.16, up: 7, hold: 3, down: 11});
  const ringR = N >= B_STOP ? 14 + 26 * clamp01((N - B_STOP) / 16) : 0;
  const ringOp = N >= B_STOP ? 0.85 * (1 - clamp01((N - B_STOP) / 16)) : 0;
  const curGlowK = N < B_DRAG ? clamp01(nm / 8) * 0.6 : N < B_STOP ? 1 : 0.55 + 0.45 * Math.sin((N - B_STOP) * 0.16);
  const dragSecs = 16 * 3600 + 42 * 60 + 8 - 555 * dragK;
  const hh = String(Math.floor(dragSecs / 3600)).padStart(2, '0');
  const mm = String(Math.floor((dragSecs % 3600) / 60)).padStart(2, '0');
  const ss = String(dragSecs % 60).padStart(2, '0');
  // 读数块：拖动倒退 → 2520 定格 15:07:33（试滑终点），2576 淡出（S19 前）
  const backT = N >= B_DRAG && N < 2576;
  const backOp = N >= B_DRAG ? (N < B_DRAG + 22 ? 1 : N < 2576 ? 1 : Math.max(0, 1 - (N - 2576) / 10)) : 0;

  // ---- 节拍 8–9：时间点 / 标签 ----
  const ptGlow = (i: number) => softOp(N - (B_POINT + i), 3);
  const lbDy = (i: number) => slideUp(N - (B_LABEL + i * 3), 46, 18);
  const lbOp = (i: number) => fadeIn(N - (B_LABEL + i * 3), 7);
  const lbHot = clamp01((N - B_CLICK) / 11);

  // ---- 节拍 10–11：点击 + 读数卡（前挂 2639）----
  const nc = N - B_CLICK;
  const circP = clamp01(nc / 12);
  const na = N - B_CARD;
  const cardDy = slideUp(na, 110, 20);
  const cardOp = softOp(na, 8);
  const cardPulse = emphasisPulse(N - (B_PORTAL + 2), {peak: 1.05, up: 9, hold: 3, down: 12});
  const rowT = [B_TYPE, B_TYPE + 12, B_TYPE + 24];
  const tcnt = (t0: number, len: number) => (N < t0 ? 0 : Math.min(len, Math.floor((N - t0) / 2) + 1));
  const typed: [number, number, number] = [tcnt(rowT[0], READOUT.time.length), tcnt(rowT[1], READOUT.dim.length), tcnt(rowT[2], READOUT.pos.length)];
  const curRow: -1 | 0 | 1 | 2 = N < rowT[0] + READOUT.time.length * 2 ? 0 : N < rowT[1] + READOUT.dim.length * 2 ? 1 : N < rowT[2] + READOUT.pos.length * 2 ? 2 : -1;

  // ---- 节拍 12：传送门 + 弧线（2699）----
  const np = N - B_PORTAL;
  const portalS = np >= 0 ? scaleIn(np, 16) : 0;
  const portalPulse = emphasisPulse(np, {peak: 1.1, up: 12, hold: 4, down: 14});
  // 持续：波纹循环（慢速连续 sin，不再相位跳变；hold 统计友好）
  const portalPhase = Math.max(0, np) * 0.22;
  const arcP = clamp01(np / 24);
  const dotS = clamp01((np - 18) / 6);
  // ---- 2438–2584「停下来」段持续动作（motion_check：静止段加大幅度）----
  const rippleN = N - B_STOP;                       // 点击涟漪循环（2 圈/44 帧，落位段唯一持续动作，加大到可测量）
  const rip = (k: number) => {
    const t = ((rippleN + 22 * k) % 44) / 44;
    return {r: 14 + 52 * t, op: 0.85 * (1 - t)};
  };
  // PlayerDot 呼吸（落位段持续：±18% 半径正弦，永不完全静止）
  const dotK = 1 + 0.18 * Math.sin(N * 0.21);

  return (
    <div style={{position: 'absolute', inset: 0, opacity: opAll, transform: exitDy ? `translateY(${exitDy.toFixed(1)}px)` : undefined}}>
      {/* ===== 节拍 1：彩虹轨迹 + 问号挂线 ===== */}
      <div style={{position: 'absolute', inset: 0, opacity: 1 - 0.55 * qDim}}>
        <TrackLine pts={TRACK_PTS} p={trackP} sw={9} heat="rainbow" />
      </div>
      <Svg bloom={false} opacity={qOp * (1 - 0.72 * qDim)}>
        {Q_X.map((x, i) => (
          <g key={i}>
            <line x1={x} y1={196} x2={x} y2={222 - Q_LIFT + 22} stroke={GREY} strokeWidth={2} strokeDasharray="5 6" opacity={0.85} />
            <circle cx={x} cy={196} r={3} fill={GREY} />
          </g>
        ))}
      </Svg>
      {Q_X.map((x, i) => (
        <div key={i} style={{...abs(x - 26, 222 - Q_LIFT - 34 + qShake(i), 52, 58), opacity: qOp * (1 - 0.72 * qDim)}}>
          <CText cx={26} cy={30} size={54} weight={900} color={GREY} dy={0}>?</CText>
        </div>
      ))}

      {/* ===== 节拍 2：时间轴（主角）弹入 + 热力 wipe + glitch「时间轴」 ===== */}
      {nt >= 0 ? (
        <div style={{position: 'absolute', inset: 0, transform: `translate(640px,465px) scale(${tlS.toFixed(4)}) translate(-640px,-465px)`, transformOrigin: '0 0', opacity: tlOp}}>
          <div style={{...abs(TL.x, TL.y, TL.w, TL.h), boxSizing: 'border-box', background: '#000', border: `2.5px solid ${WHITE}`, borderRadius: 13, boxShadow: gOff > 0.01 ? `0 0 ${Math.round(42 * gOff)}px ${Math.round(14 * gOff)}px rgba(102,45,248,${(0.3 * gOff).toFixed(3)})` : undefined}}>
            <div style={{position: 'absolute', left: 3, top: 3, width: (TL.w - 6) * wipe, height: TL.h - 6, borderRadius: 10, overflow: 'hidden'}}>
              <div style={{position: 'absolute', inset: 0, background: 'linear-gradient(90deg, #3E8EE8 0%, #4FD08A 34%, #E8D24A 62%, #F05F41 100%)', opacity: 0.9}} />
            </div>
            {[0.25, 0.45, 0.65, 0.85].map((t, i) => (
              <div key={i} style={{...abs(TL.x + TL.w * t - 1.5, TL.y - 5, 3, TL.h + 10), background: WHITE, opacity: 0.55 * softOp(N - (B_TL + 6 + i * 2), 4)}} />
            ))}
            {/* 游标（白，持续颤动） */}
            <div style={{...abs(cursorX - 7 + wob(0), TL.y - 16 + wob(1), 14, TL.h + 32), background: WHITE, borderRadius: 3, boxShadow: curGlowK > 0.01 ? `0 0 14px 4px rgba(255,255,255,${(0.55 * curGlowK).toFixed(2)})` : undefined, transform: `scale(${stopPulse.toFixed(3)})`, transformOrigin: '50% 50%'}} />
            {ringOp > 0 ? <div style={{...abs(cursorX - ringR + wob(2), TL.y + TL.h / 2 - ringR + wob(3), ringR * 2, ringR * 2), borderRadius: '50%', border: `3px solid ${PURPLE_LIGHT}`, opacity: ringOp}} /> : null}
            {/* 停止后持续：点击涟漪循环（2 圈错相，落点在游标上） */}
            {[0, 1].map((k) => {
              const r = rip(k);
              return r.op > 0.01 ? <div key={k} style={{...abs(cursorX - r.r + wob(4), TL.y + TL.h / 2 - r.r + wob(5), r.r * 2, r.r * 2), borderRadius: '50%', border: `4px solid ${PURPLE_LIGHT}`, opacity: r.op, boxShadow: `0 0 12px 2px rgba(161,117,241,${(0.5 * r.op).toFixed(2)})`}} /> : null;
            })}
          </div>
          <GlitchIn N={N} f0={B_TL}>
            <div style={{...abs(640 - 88, TL.y - 76, 176, 48), borderRadius: 24, background: PURPLE, border: `2.5px solid ${WHITE}`, boxShadow: `${tlGlow}, 0 0 2px rgba(200,180,255,.6)`}}>
              <div style={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT_HEAVY, fontWeight: 900, fontSize: 30, color: WHITE, transform: 'scaleX(0.85) translateY(-2px)', letterSpacing: 2}}>时间轴</div>
            </div>
          </GlitchIn>
        </div>
      ) : null}

      {/* ===== 节拍 3：键帽 K ===== */}
      {nk >= 0 && kOp > 0 ? (
        <div style={{...abs(560, 316 + kSink, 160, 84), opacity: kOp}}>
          <div style={{position: 'absolute', inset: 0, boxSizing: 'border-box', background: '#000', border: `3px solid ${WHITE}`, borderRadius: 12, boxShadow: '0 6px 0 rgba(255,255,255,.28)'}} />
          <CText cx={80} cy={40} size={46} weight={900} family={FONT_ORB} dy={0} scaleX={1}>K</CText>
        </div>
      ) : null}
      <SoftIn N={N} f0={B_K + 10}>
        <CText cx={778} cy={358} size={26} weight={600} color={GREY} opacity={kOp}>打开面板</CText>
      </SoftIn>

      {/* ===== 节拍 4：胶卷齿孔条 ===== */}
      {filmOp > 0.01 ? (
        <div style={{...abs(TL.x, TL.y - 58, TL.w, 34), opacity: filmOp, transform: `translateX(${filmShift.toFixed(1)}px)`}}>
          {[0, 20].map((oy) => (
            <div key={oy} style={{...abs(0, oy, TL.w * filmW, 14), display: 'flex', gap: 10, overflow: 'hidden'}}>
              {Array.from({length: 40}, (_, i) => <div key={i} style={{flex: '0 0 12px', height: 12, background: '#0c0c0c', border: `1.5px solid ${GREY_LINE}`, borderRadius: 2}} />)}
            </div>
          ))}
        </div>
      ) : null}

      {/* ===== 节拍 5：鼠标简笔滑入按住游标 ===== */}
      {nm >= 0 && mOp > 0.01 ? (
        <div style={{...abs(cursorX - 24 + mIn, TL.y - 88, 48, 74), opacity: mOp}}>
          <svg width={48} height={74} viewBox="0 0 48 74" style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', filter: 'drop-shadow(0 0 2px rgba(255,255,255,.4))'}}>
            <path d="M10 6 Q10 2 16 2 H32 Q38 2 38 8 V30 H10 Z" fill="#000" stroke={WHITE} strokeWidth={2.5} />
            <line x1={24} y1={6} x2={24} y2={16} stroke={WHITE} strokeWidth={2} />
            <line x1={24} y1={30} x2={24} y2={48} stroke={WHITE} strokeWidth={3} strokeLinecap="round" />
          </svg>
          <div style={{...abs(12, 40, 24, 24), borderRadius: '50%', background: PURPLE, opacity: 0.85 * curGlowK, boxShadow: curGlowK > 0.01 ? glowPurpleS(curGlowK) : undefined}} />
        </div>
      ) : null}

      {/* ===== 读数倒退（拖动期间，游标上方） ===== */}
      {backOp > 0.01 ? (
        <div style={{...abs(cursorX - 78, TL.y - 168, 156, 40), opacity: backOp}}>
          <div style={{position: 'absolute', inset: 0, background: '#000', border: `2px solid ${WHITE}`, borderRadius: 8, opacity: 0.85}} />
          <CText cx={78} cy={20} size={28} weight={700} family={FONT_ORB} color={ORANGE} dy={0} scaleX={1}>{hh}:{mm}:{ss}</CText>
        </div>
      ) : null}
      <SoftIn N={N} f0={B_DRAG + 4}>
        <div style={{...abs(280, TL.y - 132, 200, 34), opacity: backOp}}>
          <LineArrow x0={60} y0={17} x1={150} y1={17} p={clamp01((N - B_DRAG - 4) / 10)} rodW={3.5} headL={16} headW={20} color={ORANGE} />
          <CText cx={22} cy={17} size={24} weight={700} color={ORANGE} dy={0}>回</CText>
        </div>
      </SoftIn>

      {/* ===== 节拍 8：时间点竖线（1 帧错峰亮起） ===== */}
      {POINT_X.map((x, i) => (
        <div key={i} style={{...abs(x - 2, TL.y - 10, 4, TL.h + 20), background: WHITE, opacity: ptGlow(i), boxShadow: ptGlow(i) > 0.5 ? '0 0 10px 2px rgba(255,255,255,.6)' : undefined}} />
      ))}

      {/* ===== 节拍 9：时分秒标签 ===== */}
      {POINT_X.slice(0, 3).map((x, i) => {
        const o = lbOp(i);
        if (o <= 0) return null;
        const hot = i === 1;
        const col = hot ? mixWhitePurple(lbHot) : WHITE;
        return (
          <div key={i} style={{...abs(x - 66, TL.y + 56 + lbDy(i), 132, 34), opacity: o}}>
            <div style={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT_ORB, fontWeight: 700, fontSize: 26, color: col, textShadow: hot && lbHot > 0.3 ? glowPurpleS(lbHot) : undefined, fontVariantNumeric: 'tabular-nums'}}>{LABELS[i]}</div>
          </div>
        );
      })}

      {/* ===== 节拍 10：点击紫圈（draw-on） ===== */}
      {nc >= 0 ? (
        <Svg bloom={true}>
          <circle cx={POINT_X[1]} cy={TL.y + 13} r={22} fill="none" stroke={PURPLE_LIGHT} strokeWidth={4} strokeDasharray={138} strokeDashoffset={138 * (1 - circP)} opacity={0.95} />
          {nc >= 10 ? <circle cx={POINT_X[1]} cy={TL.y + 13} r={10 * clamp01((nc - 10) / 6)} fill={PURPLE} opacity={0.9} /> : null}
        </Svg>
      ) : null}

      {/* ===== 节拍 11：传送读数卡（前挂入场 + 三行打字机，第二节拍高潮） ===== */}
      {na >= 0 ? (
        <div style={{position: 'absolute', inset: 0, transform: `translate(1020px,352px) scale(${cardPulse.toFixed(4)}) translate(-1020px,-352px) translateY(${cardDy.toFixed(2)}px)`, opacity: cardOp}}>
          <ReadoutCard x={CARD.x} y={CARD.y} w={CARD.w} h={CARD.h} typed={typed} cursorRow={curRow} />
        </div>
      ) : null}

      {/* ===== 节拍 12：传送门 emphasisPulse + 弧线 + PlayerDot 落位 ===== */}
      {np >= 0 && portalS > 0 ? (
        <div style={{position: 'absolute', inset: 0, transform: `translate(340px,574px) scale(${(portalS * portalPulse).toFixed(4)}) translate(-340px,-574px)`, opacity: fadeIn(np, 6)}}>
          <PortalIcon cx={340} cy={574} w={96} h={136} phase={portalPhase} />
        </div>
      ) : null}
      {np >= 0 ? (
        <Svg bloom={true}>
          <path d="M 452 500 Q 400 596 356 560" fill="none" stroke={PURPLE_LIGHT} strokeWidth={3.5} strokeDasharray="10 9" pathLength={100} strokeDashoffset={100 * (1 - easeInOutPow(1.8)(arcP))} opacity={arcP > 0 ? 0.95 : 0} />
        </Svg>
      ) : null}
      {dotS > 0 ? (
        <div style={{position: 'absolute', inset: 0, transform: `scale(${dotS.toFixed(3)})`, transformOrigin: '438px 465px'}}>
          <PlayerDot cx={438} cy={465} r={16} t={0.42} k={dotK} label="回到 15:07:33" labelSize={24} />
        </div>
      ) : null}
    </div>
  );
};

/** 标签白→紫亮 11 帧变色（mix） */
const mixWhitePurple = (k: number) => {
  const t = clamp01(k);
  return `rgb(${Math.round(255 - (255 - 161) * t)},${Math.round(255 - (255 - 117) * t)},${Math.round(255 - (255 - 241) * t)})`;
};
