import type {ShotDef, BgSpec, FootageSpec} from '../../common';
import {SC08} from './SC08';
// 组 G5：镜头表 / 背景覆写 / 实拍层。数组顺序即层序（后者在上）。帧区间见 分镜表.md 与 script/timeline.md。
// 镜头组件：const N = useCurrentFrame() + F0（F0 = 本镜头 from）；图元 import from '../../ui'，缓动/共用层 from '../../common'。
export const SHOTS_G5: ShotDef[] = [{id: 'G5-SC08', from: 3828, to: 4070, Comp: SC08}];
export const BG_G5: BgSpec[] = [];
export const FOOTAGE_G5: FootageSpec[] = [];
