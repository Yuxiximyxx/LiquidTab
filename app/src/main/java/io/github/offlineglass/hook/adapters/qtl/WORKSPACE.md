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
- 状态：第一版，未真机验证。若通用发现在 12.10.0 上未命中，下一步是探针版
  定位底栏结构后再收紧提示或升级为专属适配器。
