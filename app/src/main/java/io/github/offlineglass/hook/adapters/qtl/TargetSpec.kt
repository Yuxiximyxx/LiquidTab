package io.github.offlineglass.hook.adapters.qtl

import io.github.offlineglass.targets.AdapterSource
import io.github.offlineglass.targets.TargetSpec


internal val targetSpec: TargetSpec = TargetSpec("qtl", "com.tencent.qt.qtl", "掌上英雄联盟", AdapterSource.DEDICATED,
            // 掌上英雄联盟 (掌盟) 12.10.0, 专属适配器 QtlAdapter (fork 新增,
            // 2.1.4 起)。已知结构 (来自 PalmPurify 项目对 12.9.0 的定位,
            // 12.10.0 上父容器特征已变, 故不写死任何单一特征):
            // 首页 GameHomeActivity; 底栏文字行资源名 tab_title_content,
            // 中间加号按钮资源名 bottom_title_center_btn, 合计 5 个槽位、
            // 中间为发布动作。entryLabels 与宿主底栏文字对应, 设置页
            // 据此生成逐项入口开关; 独立发布键默认开启 (上游现成机制)。
            activityHints = setOf(
                "com.tencent.zone.main.zm.GameHomeActivity",
            ),
            idHints = setOf("tab_title_content", "bottom_title_center_btn"),
            classHints = setOf("GameHomeActivity"),
            textHints = setOf("首页", "我的"),
            preferredSlots = 5..5, supportsPostButton = true,
            entryLabels = listOf("首页", "社区", "发布", "商城", "我的"),
            defaultDetachedPublish = true)
