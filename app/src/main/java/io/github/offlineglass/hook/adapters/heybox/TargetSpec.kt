package io.github.offlineglass.hook.adapters.heybox

import io.github.offlineglass.targets.AdapterSource
import io.github.offlineglass.targets.TargetSpec


internal val targetSpec: TargetSpec = TargetSpec("heybox", "com.max.xiaoheihe", "小黑盒", AdapterSource.DEDICATED,
            // 小黑盒 1.3.402, 专属适配器 HeyboxAdapter (fork 新增,
            // 2.1.4 起)。底栏定位知识来自 BetterHeybox 的液态玻璃
            // 底栏实现 (只借发现信号与页面政策, 渲染走本模块共享
            // 核心): 主页 com.max.xiaoheihe.MainActivity; 底栏是
            // RadioGroup 资源名 rg_main (rb_1..rb_5, rb_3 为中间
            // 占位), 加号按钮 vg_mid_tab 独立于 RadioGroup 盖在
            // 占位上, 合计 5 槽位、中间为发布动作。entryLabels
            // 与宿主底栏文字对应 (中间槽位原生占位文字为「推荐」,
            // 实际动作是发布, 故标「发布」), 设置页据此生成逐项
            // 入口开关; 独立发布键默认开启 (上游现成机制)。
            activityHints = setOf(
                "com.max.xiaoheihe.MainActivity",
            ),
            idHints = setOf("rg_main", "vg_mid_tab"),
            classHints = setOf("MainActivity"),
            textHints = setOf("首页", "我的"),
            preferredSlots = 5..5, supportsPostButton = true,
            entryLabels = listOf("首页", "热点", "发布", "游戏库", "我"),
            defaultDetachedPublish = true)
