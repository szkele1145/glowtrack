import type {ShotDef, BgSpec, FootageSpec} from '../../common';
import {SC03} from './SC03';
import {SC04} from './SC04';
// 组 G2：镜头表 / 背景覆写 / 实拍层。数组顺序即层序（后者在上）。帧区间见 分镜表.md 与 script/timeline.md。
// 镜头组件：const N = useCurrentFrame() + F0（F0 = 本镜头 from）；图元 import from '../../ui'，缓动/共用层 from '../../common'。
export const SHOTS_G2: ShotDef[] = [
  {id: 'G2-SC03', from: 1113, to: 1655, Comp: SC03},
  {id: 'G2-SC04', from: 1676, to: 2025, Comp: SC04},
];
export const BG_G2: BgSpec[] = [];
export const FOOTAGE_G2: FootageSpec[] = [];
