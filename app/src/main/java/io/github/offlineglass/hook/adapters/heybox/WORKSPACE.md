# heybox（小黑盒）适配工作说明

- 目标应用：小黑盒，包名 `com.max.xiaoheihe`，目标版本 1.3.402。
- 接入方式：专属适配器 `HeyboxAdapter`（2.1.4 起，`DEDICATED`）；
  渲染一律走 LiquidTab 共享核心，不移植 BetterHeybox 的玻璃绘制代码。
- 发现信号（来自 BetterHeybox「液态玻璃（底栏）」实现的定位成果）：
  - 主页 Activity：`com.max.xiaoheihe.MainActivity`；
  - 根 `vg_main_root`（RelativeLayout），内容 `fl_container` 以
    ABOVE 规则顶在底栏上方；
  - 底栏 RadioGroup 资源名 `rg_main`，子项 `rb_1` 起的 RadioButton
    序列（rb_3 为中间占位，原生文字「推荐」）；
  - 加号按钮 `vg_mid_tab` 独立于 RadioGroup，盖在 rb_3 占位上；
  - 底栏上方有提示条 `vg_tips`。
- 2.1.4 的处理（针对 2.0.x 真机初验的四项问题）：
  - 源钉死为 `rg_main`；`prepareNavigationSource` 把 `vg_mid_tab`
    移入 rg_main 顶替 rb_3 占位并继承其布局参数（重绑时复查，
    防底栏热更新把它挂回根布局），使五个槽位的 artwork/点按/
    显隐回到同一索引空间，消除加号在源外绘制透过玻璃发糊错位；
  - `suppressNativeBottomChrome` 清 rg_main 底色，放开
    `fl_container` 的 ABOVE 规则让内容铺到屏幕底部（华为健康
    适配同款），并清掉与底栏同高且重叠的兄弟薄层（阴影层）底色，
    去掉玻璃条后面的黑带；
  - 独立发布键与逐项入口开关复用上游机制：spec
    `defaultDetachedPublish = true` + 适配器 `barLayout`（tieba/xhs
    同款几何）+ spec `entryLabels`（首页/热点/发布/游戏库/我；
    中间槽位原生占位文字是「推荐」，实际动作是发布，故标「发布」）。
- 页面政策/例外（沿用旧记录）：全屏视频容器
  `vg_fullscreen_video_container` 出现时底栏应隐藏（靠通用页面
  可见性处理）；底栏标签/图标可由服务端热更新下发。
- 冲突提醒：启用本模块的小黑盒玻璃后，需关掉 BetterHeybox（独立版或
  荣耀工具箱内「小黑盒增强」）自带的液态玻璃底栏开关，避免同一底栏
  被处理两次。注意工具箱 v1.10.0 起已移除其小黑盒底栏功能。
- 2.1.5 的修正（2.1.4 真机复验：发布键无图标、导航条不沉浸）：
  - 发布键空白根因：通用绘制路径从不往独立圆钮画 artwork
    （页面槽之外的发布槽不在绘制列表；xhs/贴吧均适配器自绘）。
    本版 `HeyboxNavigationState` 自持绘制（`ownsNavigationDrawing`），
    通用投影后把 `vg_mid_tab` 的图标叶子画进圆钮，兜底手绘加号；
  - 快照改逐槽叶子捕获（`HeyboxArtworkCapture`）：普通槽取
    RadioButton 顶部 compound drawable（带按钮 drawableState）
    + 文本，中间槽取 `vg_mid_tab` 图标叶子；
  - 沉浸：`configureNavigationInsets` 接 `HeyboxInsets`（照本仓
    CainiaoInsets 成法），对含 `rg_main` 的主界面窗口在 insets
    分发处清零导航条底部 inset，内容铺到物理底部；其余页面
    不动（BetterHeybox 亦只在主界面全量沉浸）。
  - 附带核对：2.1.4 截图四槽图标为三角/圆/叉/方块等几何形；
    本版已改为直接取 compound drawable 这一权威来源，若真机
    仍为几何形，则宿主当前下发的 drawable 本身即该组图形
    （服务端热更新图标未到达/回退默认），需关作用域对照原生
    底栏确认，不要再从模块侧猜。
- 状态：2.1.5 已发布待验，以上均为代码层修复，未经真机验证。
- 2.1.6：独立发布键与主条粘连（主条底板横跨到圆钮下方）。
  barLayout 改对齐 xhs 口径：摘掉照 tieba 开的本地光学三
  标志（localMainOptics/localActionOptics/
  actionFillUsesOnlySurfaceOpacity）——本地光学的主条采样
  节点按 padded 全宽输入录制，浅色底板会越过主条右界伸进
  间隙与圆钮区域；几何（主条在圆钮前 12dp 收住）不变，两
  部分走共享外层玻璃路径、各自 clip 与阴影分别成形。
- 状态：2.1.6 已发布待验，以上均为代码层修复，未经真机验证。
- 2.1.7：①间隙白色连接：stripChrome 的兄弟薄层清理从「与
  rg_main 矩形相交」放宽到整条底栏带（左右到根布局两边、
  向下到根布局底边，高度上限 3 倍栏高），并新增与掌盟同
  口径的 stripBarContainer（从 rg_main 向上至多 3 层清底栏
  容器背景，几何守卫同 qtl）——原底栏背景条若只挂在底栏
  下方或间隙正后方，旧判定够不着，会把主条与圆钮视觉上连
  成一片。②恢复评论输入框贴合：新增 HeyboxCommentDock，
  按行为移植 BetterHeybox 的 fork 定制 CommentInputDockHook
  （d713f09 引入、5858c1f 定版「只铺背景」；工具箱 v1.10.0
  摘底栏时连带丢失）：挂 Activity.onResume 给每个 decor 注册
  全局布局监听，键盘高 > 屏高 15% 且页面有 hint 含「来说点
  什么」的 EditText 时，取输入框卡片容器（首个带背景祖先）
  的底色，给其外层至多两层非全屏容器铺同色，使输入区与键
  盘连成整块；不动任何布局参数。
- 状态：2.1.7 已发布待验，以上均为代码层修复，未经真机验证。
