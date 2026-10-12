import type {ShotDef, BgSpec, FootageSpec} from '../../common';
import SC01 from './SC01';
import SC02 from './SC02';

// 组 G1：镜头表 / 背景覆写 / 实拍层。数组顺序即层序（后者在上）。帧区间见 分镜表.md 与 script/timeline.md。
// 镜头组件：const N = useCurrentFrame() + F0（F0 = 本镜头 from）；图元 import from '../../ui'，缓动/共用层 from '../../common'。
export const SHOTS_G1: ShotDef[] = [
  {id: 'SC01', from: 78, to: 586, Comp: SC01},
  {id: 'SC02', from: 632, to: 1092, Comp: SC02},
];
export const BG_G1: BgSpec[] = [];
export const FOOTAGE_G1: FootageSpec[] = [];
