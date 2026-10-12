import React from 'react';
import {useCurrentFrame} from 'remotion';
import {GlitchIn, clamp01, kf} from '../../common';
import {CText, PURPLE, WHITE, PURPLE_LIGHT, Svg, softOp, exitOp, glowOffK, glowPurpleS, SoftIn, abs} from '../../ui';

import {TrackLine} from '../G3/parts';
import {MountainRange} from './parts';

/**
 * SC04 穿墙 + 脉冲（1676–2025，分镜表 G2 行 SC04）
 * 三座山（三角简笔白边 2px，x250/640/1030，高 220/260/200，底对齐 y560）2 帧错峰 slideUp（Δ60）；
 * 一条彩虹 TrackLine（x80–1200, y380, 宽12）整条 draw-on 30 帧（1676–1706）；
 * 中段 500–800 叠 α0.55 虚线版示意「山后穿墙」+ 紫Pill「隔墙可见」挂 y300（本镜头唯一一处 GlitchIn）；
 * 「线宽 5 格」CText 28px (640,240) 淡入；辉光层同形宽 36 α0.18；
 * 亮度波：白光点（r6 + 70% 光晕）沿线左→右，kf 周期函数 24 帧循环（持续到离场灭光）；
 * 线宽 12→14→12 一轮起伏（kf 周期 40 帧）。
 * 持续：亮度波循环；落位后停 44 帧，2015 起 exitOp 8 帧归零 + 先灭光。
 */
const F0 = 1676, TO = 2025;
const B_MT = 1676;        // 山 slideUp（2 帧错峰）
const B_TL = 1676;        // 轨迹 draw-on 30 帧
const B_DASH = 1714;      // 中段虚线（穿墙示意）
const B_PILL = 1726;      // 隔墙可见 Pill（唯一 glitch）
const B_TEXT = 1740;      // 线宽 5 格
const B_WAVE = 1716;      // 亮度波起跑
const T_SETTLE = 1706;    // 轨迹到位
const T_EXIT = TO - 10;   // 2015：停 44 帧后 8 帧归零

const MTS = [{x: 250, h: 220, w: 260}, {x: 640, h: 260, w: 300}, {x: 1030, h: 200, w: 240}];
const TL_PTS: Array<[number, number]> = [[80, 380], [1200, 380]];
const DASH: Array<[number, number]> = [[500, 380], [800, 380]];

export const SC04: React.FC = () => {
  const N = useCurrentFrame() + F0;
  // ---- 离场：先灭光再归零（硬切前必须归零）----
  const gOff = glowOffK(N, TO, 6, 8);
  const opAll = exitOp(N, TO, 8);
  const exitDy = N >= T_EXIT ? Math.min(10, 0.2 * Math.pow(N - T_EXIT, 2)) : 0;

  // ---- 山：2 帧错峰 slideUp Δ60 ----
  const mtDy = (i: number) => 60 * Math.pow(1 - clamp01((N - (B_MT + i * 2)) / 22), 2.5);
  const mtOp = (i: number) => softOp(N - (B_MT + i * 2), 6);

  // ---- 轨迹：整条 draw-on 30 帧 ----
  const ntl = N - B_TL;
  const tlP = clamp01(ntl / 30);

  // ---- 中段虚线（山后穿墙示意，α0.55）----
  const nd = N - B_DASH;
  const dashP = clamp01(nd / 12);

  // ---- 亮度波：白光点沿线左→右，24 帧周期 kf 循环（持续到离场）----
  // kf 首末陷阱：首帧必须 [镜头起始, 0]，末值接到循环段首，保证任意 N 都落在周期内
  const wavePhase = ((N - B_WAVE) % 24 + 24) % 24; // 0..23
  const waveKf = kf(wavePhase, [[0, 0], [12, 0.5], [23.999, 1]]);
  const waveX = 80 + 1120 * waveKf;
  const waveOn = N >= B_WAVE && gOff > 0.05 ? 1 : 0;

  // ---- 线宽起伏 12→14→12（kf 周期 40 帧，从 draw-on 完成后开始）----
  const swK = kf(N, [[B_TL + 30, 12], [B_TL + 50, 14], [B_TL + 70, 12], [B_TL + 110, 12]]);

  return (
    <div style={{position: 'absolute', inset: 0, opacity: opAll, transform: exitDy ? `translateY(${exitDy.toFixed(1)}px)` : undefined}}>
      {/* ===== 三座山（2 帧错峰 slideUp） ===== */}
      <MountainRange spec={MTS.map((m, i) => ({...m, dy: mtDy(i), op: mtOp(i)}))} baseY={560} sw={2} />

      {/* ===== 轨迹：辉光层（宽36 α0.18）+ 主线（宽 kf 起伏） ===== */}
      {ntl >= 0 ? (
        <Svg bloom={true}>
          <polyline points={TL_PTS.map((q) => q.join(',')).join(' ')} fill="none" stroke={PURPLE_LIGHT} strokeWidth={36} opacity={0.18 * gOff} strokeDasharray={1120} strokeDashoffset={1120 * (1 - tlP)} strokeLinecap="round" />
        </Svg>
      ) : null}
      <div style={{position: 'absolute', inset: 0, opacity: ntl >= 0 ? 1 : 0}}>
        <TrackLine pts={TL_PTS} p={tlP} sw={swK} heat="rainbow" glowW={0} />
      </div>

      {/* ===== 中段虚线（山后穿墙示意，α0.55） ===== */}
      {nd >= 0 ? (
        <Svg bloom={false}>
          <line x1={500} y1={380} x2={500 + 300 * dashP} y2={380} stroke={WHITE} strokeWidth={3} strokeDasharray="10 9" opacity={0.55} strokeLinecap="butt" />
        </Svg>
      ) : null}

      {/* ===== 亮度波：白光点 r6 + 70% 光晕沿线上跑 ===== */}
      {waveOn ? (
        <Svg bloom={true}>
          <circle cx={waveX} cy={380} r={6} fill={WHITE} opacity={0.95} />
          <circle cx={waveX} cy={380} r={16} fill="none" stroke={WHITE} strokeWidth={2} opacity={0.7 * gOff} />
        </Svg>
      ) : null}

      {/* ===== 紫Pill「隔墙可见」（本镜头唯一一处 GlitchIn，挂 y300） ===== */}
      <GlitchIn N={N} f0={B_PILL}>
        <div style={{...abs(640 - 96, 300 - 26, 192, 52), borderRadius: 26, background: PURPLE, border: `2.5px solid ${WHITE}`, boxShadow: gOff > 0.01 ? glowPurpleS(0.6 * gOff) : undefined}}>
          <div style={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 900, fontSize: 28, color: WHITE, transform: 'scaleX(0.85)', letterSpacing: 2}}>隔墙可见</div>
        </div>
      </GlitchIn>
      <SoftIn N={N} f0={B_PILL + 4}>
        <Svg bloom={false}>
          <line x1={640} y1={326} x2={640} y2={366} stroke={PURPLE_LIGHT} strokeWidth={2} strokeDasharray="5 6" opacity={0.8} />
        </Svg>
      </SoftIn>

      {/* ===== 「线宽 5 格」CText 28px (640,240) 淡入 ===== */}
      <SoftIn N={N} f0={B_TEXT}>
        <CText cx={640} cy={240} size={28} weight={700} color={WHITE}>线宽 5 格</CText>
      </SoftIn>
    </div>
  );
};
