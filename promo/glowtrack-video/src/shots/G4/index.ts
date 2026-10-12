import type {ShotDef, BgSpec, FootageSpec} from '../../common';
import {SC06} from './SC06';
import {SC07} from './SC07';
// 组 G4：镜头表 / 背景覆写 / 实拍层。数组顺序即层序（后者在上）。帧区间见 分镜表.md 与 script/timeline.md。
// 镜头组件：const N = useCurrentFrame() + F0（F0 = 本镜头 from）；图元 import from '../../ui'，缓动/共用层 from '../../common'。
export const SHOTS_G4: ShotDef[] = [
  {id: 'G4-SC06', from: 2808, to: 3288, Comp: SC06},
  {id: 'G4-SC07', from: 3309, to: 3782, Comp: SC07},
];
export const BG_G4: BgSpec[] = [];
export const FOOTAGE_G4: FootageSpec[] = [];
