package io.github.offlineglass.hook.adapters.qtl

import io.github.offlineglass.targets.AdapterSource
import io.github.offlineglass.targets.TargetSpec


internal val targetSpec: TargetSpec = TargetSpec("qtl", "com.tencent.qt.qtl", "掌上英雄联盟", AdapterSource.GENERIC_FALLBACK,
            // 掌上英雄联盟 (掌盟) 12.10.0, first-pass generic接入 (fork 新增,
            // 未真机验证). 已知结构 (来自 PalmPurify 项目对 12.9.0 的定位,
            // 12.10.0 上父容器特征已变, 故不写死任何单一特征):
            // 首页 GameHomeActivity; 底栏文字行资源名 tab_title_content
            // (四个文字 tab), 中间加号按钮资源名 bottom_title_center_btn,
            // 合计 5 个槽位、中间为发布类动作. 发现走通用几何 + id/文本
            // 提示打分, 不依赖上述任一资源名单独命中.
            activityHints = setOf(
                "com.tencent.zone.main.zm.GameHomeActivity",
            ),
            idHints = setOf("tab_title_content", "bottom_title_center_btn"),
            classHints = setOf("GameHomeActivity"),
            textHints = setOf("首页", "我的"),
            preferredSlots = 5..5, supportsPostButton = true)
