# qtl（掌上英雄联盟）适配工作说明

- 目标应用：掌上英雄联盟，包名 `com.tencent.qt.qtl`，目标版本 12.10.0。
- 接入方式：纯配置（`TargetSpec.kt`，`GENERIC_FALLBACK`），无专属适配器类；
  运行时由注册表自动落到通用 `MetadataOnlyAdapter` + 通用导航发现。
- 已知宿主结构（来自 PalmPurify 项目，12.9.0 时代定位成果）：
  - 首页 Activity：`com.tencent.zone.main.zm.GameHomeActivity`；
  - 底栏文字行资源名 `tab_title_content`（四个文字槽：首个「首页」、末个「我的」）；
  - 中间加号按钮资源名 `bottom_title_center_btn`，合计 5 槽位。
- 注意：12.10.0 上 PalmPurify 旧的「同时包含 tab_title_content 与中间加号的
  父容器」特征已失效，因此本 spec 只把上述资源名作打分提示，不作唯一命中条件。
- 中间两个 tab 的文字、强调色尚未真机确认，`entryLabels` 与
  `defaultAccentColor` 暂不写死，待真机探针后再补。
- 状态：曾随 fork 2.0.1 发布并真机初验——通用发现命中、玻璃条生成，
  但有黑背景、按钮偏移、发布按钮样式、设置页无入口开关四项已知问题，
  均待后续版本处理；现已按 2.1.2 契约原样移植到新基线（fork 2.1.3）。
