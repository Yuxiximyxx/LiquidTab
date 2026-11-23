# heybox（小黑盒）适配工作说明

- 目标应用：小黑盒，包名 `com.max.xiaoheihe`，目标版本 1.3.402。
- 接入方式：纯配置（`TargetSpec.kt`，`GENERIC_FALLBACK`），无专属适配器类；
  渲染一律走 LiquidTab 共享核心，不移植 BetterHeybox 的玻璃绘制代码。
- 发现信号（来自 BetterHeybox「液态玻璃（底栏）」实现的定位成果）：
  - 主页 Activity：`com.max.xiaoheihe.MainActivity`；
  - 底栏容器：RadioGroup，资源名 `rg_main`，位于根 `vg_main_root` 内；
  - 中间 tab 容器资源名 `vg_mid_tab`（发布动作），单颗 tab 为 `rb_1` 起的
    RadioButton 序列，合计 5 槽位；
  - 内容区 `fl_container`；底栏上方有提示条 `vg_tips`。
- 页面政策/例外（同源记录，先记下，第一版靠通用页面可见性处理）：
  - 全屏视频容器 `vg_fullscreen_video_container` 出现时底栏应隐藏；
  - 底栏标签与图标可由服务端热更新下发（1.3.400/1.3.401 起有专门的
    底栏配置热更新通道），文字标签不稳定，故 spec 只用「首页/我的」
    两个稳定标签作打分提示。
- 冲突提醒：启用本模块的小黑盒玻璃后，需关掉 BetterHeybox（独立版或
  荣耀工具箱内「小黑盒增强」）自带的液态玻璃底栏开关，避免同一底栏
  被处理两次。
- 状态：曾随 fork 2.0.2 发布并真机初验——通用发现命中、玻璃条生成，
  但有黑背景、按钮偏移、发布按钮样式、设置页无入口开关四项已知问题，
  均待后续版本处理；现已按 2.1.2 契约原样移植到新基线（fork 2.1.3）。
