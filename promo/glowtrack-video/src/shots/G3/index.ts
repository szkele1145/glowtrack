import type {ShotDef, BgSpec, FootageSpec} from '../../common';
import {SC05} from './SC05';
// 组 G3：镜头表 / 背景覆写 / 实拍层。数组顺序即层序（后者在上）。帧区间见 分镜表.md 与 script/timeline.md。
// 镜头组件：const N = useCurrentFrame() + F0（F0 = 本镜头 from）；图元 import from '../../ui'，缓动/共用层 from '../../common'。
export const SHOTS_G3: ShotDef[] = [{id: 'G3-SC05', from: 2026, to: 2787, Comp: SC05}];
export const BG_G3: BgSpec[] = [];
export const FOOTAGE_G3: FootageSpec[] = [];
