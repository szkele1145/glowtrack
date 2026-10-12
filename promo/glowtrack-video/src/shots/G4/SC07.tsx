import React from 'react';
import {useCurrentFrame} from 'remotion';
import {clamp01, kf, rnd, emphasisPulse, FONT_ORB} from '../../common';
import {CText, MonoText, WHITE, GREY, GREY_LINE, GREEN, PURPLE_LIGHT, abs, softOp, fadeIn, slideUp} from '../../ui';
import {TrackLine} from '../G3/parts';

/**
 * SC07 存档导出 + 读数（3309–3782）：全 SoftIn/fadeIn，无 glitch。
 * 3317 三张日期卡（白 2px 边 200×64 圆角，左上角 x=300、y=300/370/440）「10-09/10-10/10-11」
 *   MonoText 28px WHITE，2 帧错峰 slideUp（Δ30）；
 * 3340 卡下方「自动保留 7 天」22px GREY fadeIn（列中心对齐）；
 * 3459 「⬇ 导出」按钮 (300,570) 220×70 白 2px 边黑底 CText 28px + emphasisPulse 1.08 一次；
 * 3462 文件卡 160×90（白边 + 右下绿字 JSON 20px MonoText + 三条文档线）从按钮中心 (410,605)
 *   弧线飞向右上、中心落 (640,300)（kf 20f easeOutCubic + 中段抬弧）+ 紫柔光；持续呼吸 ±3%。
 * 3527 读数卡（中心 x920、顶 y360 → 560×280）白 2px 圆角 #0D1117：三行 MonoText 34px
 *   画布 y=420/490/560，label 紫 + 值 #D29922 / #58A6FF / #C9D1D9，打字机每 2f 1 字符、
 *   行间隔 12f 起步；3560 三行首 12px 简笔图标（时钟/方块/定位针）fadeIn；
 * 3664 读数卡 emphasisPulse 1.05 同帧一次（中心缩放）；
 * 3675 背景 5 条竖向波浪灰路（GREY_LINE 2.5px α.55，rnd 相位）+ 各一条彩虹 TrackLine w2
 *   1 帧错峰 draw-on 20f；3700 坐标行每 30f 微变 ±0.1（rnd）；
 * 3750 起 exitOp 归零（opacity = 1−(n/10)^1.5 包裹整个内容 div）。
 */
const F0 = 3309;
const T_CARDS = 3317;
const T_KEEP = 3340;
const T_BTN = 3459;
const T_FILE = 3462;
const T_CARD = 3527;
const T_ICONS = 3560;
const T_PULSE = 3664;
const T_ROADS = 3675;
const T_TICK = 3700;
const T_EXIT = 3750;
const ROAD_X = [260, 460, 660, 860, 1060];
// 读数三行：label + 值 + 值色（行 y 为画布绝对坐标）
const ROWS: Array<{label: string; value: string; color: string}> = [
  {label: '时刻', value: '15:07:33', color: '#D29922'},
  {label: '维度', value: '主世界', color: '#58A6FF'},
  {label: '坐标', value: '128 64 -256', color: '#C9D1D9'},
];
// 日期卡（左上角定位）
const CARDS = [
  {x: 300, y: 300, label: '10-09'},
  {x: 300, y: 370, label: '10-10'},
  {x: 300, y: 440, label: '10-11'},
];
// 读数卡几何：x 中心 920 → 左缘 640；顶 360 → 底 640（三行 y 420/490/560 全部内含）
const CARD = {x: 640, y: 360, w: 560, h: 280};
// 文件卡：起点 = 按钮中心 (410,605) 的左上角 (330,560)；终点 = 中心 (640,300) 的左上角 (560,255)
const FILE = {w: 160, h: 90, x0: 330, y0: 560, x1: 560, y1: 255};

/** 第 i 条微缩灰路：x 固定、竖向轻波浪（y150–560，幅度/相位 rnd 区分）。 */
const roadPts = (i: number): Array<[number, number]> => {
  const x = ROAD_X[i];
  const amp = 10 + rnd(41, i, 3) * 11;
  const ph = rnd(41, i, 4) * 6.283;
  const pts: Array<[number, number]> = [];
  for (let y = 150; y <= 560; y += 22) pts.push([x + amp * Math.sin(y * 0.02 + ph), y]);
  return pts;
};
const ROADS = ROAD_X.map((_, i) => roadPts(i));

export const SC07: React.FC = () => {
  const N = useCurrentFrame() + F0;
  // ---- 日期卡（2f 错峰 slideUp） / 说明行 / 导出按钮（pulse 1.08 一次）----
  const cardOps = CARDS.map((_, i) => softOp(N - (T_CARDS + i * 2), 8));
  const keepOp = fadeIn(N - T_KEEP, 10);
  const btnOp = softOp(N - T_BTN, 8);
  const btnPulse = emphasisPulse(N - T_BTN, {peak: 1.08});
  // ---- 文件卡：20f 弧线飞行 + 持续呼吸 + 紫柔光 ----
  const fp = kf(N - T_FILE, [[0, 0], [20, 1]], (u) => 1 - Math.pow(1 - clamp01(u), 3)); // easeOutCubic
  const fx = kf(fp, [[0, FILE.x0], [1, FILE.x1]]);
  const fy = kf(fp, [[0, FILE.y0], [0.5, 380], [1, FILE.y1]]); // 中段抬高 → 弧线
  const fileOp = N >= T_FILE ? softOp(N - T_FILE, 6) : 0;
  const fileBreath = 1 + 0.03 * Math.sin(N * 0.22);
  // ---- 读数卡 + 打字机（每 2f 1 字符，行间隔 12f 起步）----
  const cardOp = softOp(N - T_CARD, 8);
  const typed = ROWS.map((r, i) => {
    const start = T_CARD + 12 + i * 12;
    return Math.min(r.value.length, Math.max(0, Math.floor((N - start) / 2)));
  });
  // ---- 图标 / pulse / 坐标微变 / exit ----
  const iconsOp = fadeIn(N - T_ICONS, 12);
  const pulse = emphasisPulse(N - T_PULSE, {peak: 1.05});
  const tick = Math.floor(Math.max(0, N - T_TICK) / 30);
  const wob = (i: number) => (tick > 0 ? (rnd(47, tick, i) - 0.5) * 0.2 : 0);
  const posStr = tick > 0 ? `${(128 + wob(1)).toFixed(1)} ${(64 + wob(2)).toFixed(1)} ${(-256 + wob(3)).toFixed(1)}` : ROWS[2].value;
  const exitN = N - T_EXIT;
  const exitK = exitN <= 0 ? 1 : 1 - Math.pow(Math.min(1, exitN / 10), 1.5);
  const roadSoft = (n: number) => softOp(n, 8);

  return (
    <div style={{position: 'absolute', inset: 0, opacity: exitK}}>
      {/* 背景：5 条竖向波浪灰路 + 彩虹细线（1f 错峰 draw-on 20f），压在所有卡片之下 */}
      {ROADS.map((pts, i) => {
        const n = N - (T_ROADS + i);
        if (n < 0) return null;
        return (
          <React.Fragment key={i}>
            <svg width={1280} height={720} viewBox="0 0 1280 720" style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', opacity: 0.55 * roadSoft(n)}}>
              <polyline points={pts.map((q) => q.join(',')).join(' ')} fill="none" stroke={GREY_LINE} strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" />
            </svg>
            <TrackLine pts={pts} p={clamp01(n / 20)} sw={2} opacity={0.92 * roadSoft(n)} />
          </React.Fragment>
        );
      })}

      {/* 左列：三张日期卡（白 2px 边 200×64 圆角，2f 错峰 slideUp） */}
      {CARDS.map((c, i) => {
        const op = cardOps[i];
        if (op <= 0.01) return null;
        const sdy = slideUp(N - (T_CARDS + i * 2), 30, 18);
        return (
          <div key={i} style={{...abs(c.x, c.y + sdy, 200, 64), opacity: op, boxSizing: 'border-box', border: `2px solid ${WHITE}`, borderRadius: 10, background: '#000'}}>
            <MonoText x={20} y={14} size={28} color={WHITE}>{c.label}</MonoText>
          </div>
        );
      })}
      {/* 卡下方说明（列中心 400，y 530） */}
      <CText cx={400} cy={530} size={22} weight={600} color={GREY} opacity={keepOp}>自动保留 7 天</CText>

      {/* 导出按钮 220×70 @(300,570) + emphasisPulse 1.08 一次 */}
      {btnOp > 0.01 ? (
        <div style={{...abs(300, 570, 220, 70), opacity: btnOp, transform: btnPulse === 1 ? undefined : `scale(${btnPulse.toFixed(4)})`, transformOrigin: '110px 35px'}}>
          <div style={{position: 'absolute', inset: 0, boxSizing: 'border-box', border: `2px solid ${WHITE}`, background: '#000', borderRadius: 10}} />
          <CText cx={110} cy={37} size={28} weight={800}>⬇ 导出</CText>
        </div>
      ) : null}

      {/* 文件卡 160×90：按钮 → 右上弧线飞行 + 呼吸 + 紫柔光，右下绿字 JSON */}
      {fileOp > 0.01 ? (
        <div style={{...abs(fx, fy, FILE.w, FILE.h), opacity: fileOp, transform: `scale(${fileBreath.toFixed(3)})`, transformOrigin: '80px 45px', filter: `drop-shadow(0 0 14px rgba(102,45,248,${(0.45 * fileOp).toFixed(3)}))`}}>
          <div style={{position: 'absolute', inset: 0, boxSizing: 'border-box', border: `2px solid ${WHITE}`, background: '#000', borderRadius: 8}} />
          <div style={{position: 'absolute', top: 14, left: 16, right: 16, height: 3, background: WHITE, opacity: 0.55}} />
          <div style={{position: 'absolute', top: 26, left: 16, right: 36, height: 3, background: WHITE, opacity: 0.35}} />
          <div style={{position: 'absolute', top: 38, left: 16, right: 56, height: 3, background: WHITE, opacity: 0.35}} />
          <MonoText x={FILE.w - 62} y={FILE.h - 32} size={20} color={GREEN}>JSON</MonoText>
        </div>
      ) : null}

      {/* 右：读数卡 560×280（左上角 (640,360)），三行打字机 + 3664 同帧 pulse */}
      {cardOp > 0.01 ? (
        <div style={{...abs(CARD.x, CARD.y, CARD.w, CARD.h), opacity: cardOp, transform: pulse === 1 ? undefined : `scale(${pulse.toFixed(4)})`, transformOrigin: '280px 140px'}}>
          <div style={{position: 'absolute', inset: 0, boxSizing: 'border-box', border: `2px solid ${WHITE}`, background: '#0D1117', borderRadius: 14}} />
          {ROWS.map((r, i) => {
            const shown = i === 2 ? posStr.slice(0, Math.max(typed[2], posStr === ROWS[2].value ? typed[2] : r.value.length)) : r.value.slice(0, typed[i]);
            return (
              <MonoText key={i} x={60} y={60 + i * 70} size={34} color={r.color} style={{fontWeight: 700}}>
                <span style={{color: PURPLE_LIGHT, marginRight: 14}}>{r.label}</span>
                <span style={{color: r.color}}>{shown}</span>
              </MonoText>
            );
          })}
          {/* 三行首 12px 简笔图标（时钟/方块/定位针）fadeIn */}
          {iconsOp > 0.01 ? (
            <svg width={CARD.w} height={CARD.h} viewBox={`0 0 ${CARD.w} ${CARD.h}`} style={{position: 'absolute', left: 0, top: 0, overflow: 'visible', opacity: iconsOp}}>
              <circle cx={40} cy={82} r={6} fill="none" stroke={WHITE} strokeWidth={1.8} />
              <line x1={40} y1={82} x2={40} y2={78} stroke={WHITE} strokeWidth={1.8} />
              <line x1={40} y1={82} x2={43} y2={82} stroke={WHITE} strokeWidth={1.8} />
              <rect x={34} y={147} width={11} height={11} fill="none" stroke={WHITE} strokeWidth={1.8} />
              <circle cx={40} cy={218} r={5} fill="none" stroke={WHITE} strokeWidth={1.8} />
              <path d="M36 221 L40 230 L44 221" fill="none" stroke={WHITE} strokeWidth={1.8} strokeLinejoin="round" />
            </svg>
          ) : null}
        </div>
      ) : null}
    </div>
  );
};
