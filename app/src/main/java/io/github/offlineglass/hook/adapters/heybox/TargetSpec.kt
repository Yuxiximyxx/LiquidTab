package io.github.offlineglass.hook.adapters.heybox

import io.github.offlineglass.targets.AdapterSource
import io.github.offlineglass.targets.TargetSpec


internal val targetSpec: TargetSpec = TargetSpec("heybox", "com.max.xiaoheihe", "小黑盒", AdapterSource.GENERIC_FALLBACK,
            // 小黑盒 1.3.402, first-pass generic 接入 (fork 新增, 未真机验证).
            // 底栏定位知识来自 BetterHeybox 的液态玻璃底栏实现 (只借
            // 发现信号与页面政策, 渲染走本模块共享核心):
            // 主页 com.max.xiaoheihe.MainActivity; 底栏是 RadioGroup
            // 资源名 rg_main, 中间 tab 容器 vg_mid_tab, 合计 5 槽位、
            // 中间为发布动作. 底栏标签/图标可由服务端热更新下发
            // (BetterHeybox 另有冻结热更新的 hook), 故文字只取首尾
            // 两个稳定标签作提示, 不写死全量标签.
            activityHints = setOf(
                "com.max.xiaoheihe.MainActivity",
            ),
            idHints = setOf("rg_main", "vg_mid_tab"),
            classHints = setOf("MainActivity"),
            textHints = setOf("首页", "我的"),
            preferredSlots = 5..5, supportsPostButton = true)
