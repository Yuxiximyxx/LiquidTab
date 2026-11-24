# qtl（掌上英雄联盟）适配工作说明

- 目标应用：掌上英雄联盟，包名 `com.tencent.qt.qtl`，目标版本 12.10.0。
- 接入方式：专属适配器 `QtlAdapter`（2.1.4 起，`DEDICATED`）；
  发现/布局/抑制均由适配器钉死，渲染走共享核心。
- 已知宿主结构（来自 PalmPurify 项目，12.9.0 时代定位成果）：
  - 首页 Activity：`com.tencent.zone.main.zm.GameHomeActivity`；
  - 底栏文字行资源名 `tab_title_content`（子项为各 tab，中间留发布位）；
  - 中间加号按钮资源名 `bottom_title_center_btn`，合计 5 槽位。
- 2.1.4 的处理（针对 2.0.x 真机初验的四项问题）：
  - 源钉死为「同时含文字行与加号按钮的底栏容器」（找不到退回文字行），
    消除通用发现选源不稳带来的偏移；
  - `suppressNativeBottomChrome` 清掉容器/文字行/加号按钮及向上三层
    底部壳层的底色与前景，内容若被 RelativeLayout ABOVE 规则顶住
    则放开规则铺底，去掉玻璃条后面的黑带；
  - 加号按钮若不在源内（个别版本挂在根布局），`prepareNavigationSource`
    会把它移入底栏容器，避免在玻璃条后单独绘制透出；
  - 中间槽位走 `sourceCenterActionIndex` + 0.7 裁剪比（美团同款参数）；
    点按由 `dispatchNavigationTap` 按槽位→真实视图映射转发；
  - 独立发布键复用上游机制：spec `defaultDetachedPublish = true` +
    适配器 `barLayout` 给出 tieba/xhs 同款几何；逐项入口开关复用
    上游机制：spec `entryLabels`（首页/社区/发布/商城/我的）生成
    设置页开关。注意「社区」为第二个 tab 的推测文字，未真机确认；
    管理器运行时学到的宿主标签会覆盖 spec 标签。
- 2.1.5 的修正（2.1.4 真机回归：整页空白，只剩坏掉的玻璃条）：
  - 根因：旧 `findNavigation` 往上三层找「含加号按钮的容器」，在
    12.10.0 上命中页面级大容器；安装器对源置 alpha=0 且管线按
    源宽等分切片投影，整页被隐藏、整页微缩图被画进槽位。
  - 源改为严格底栏壳层校验（高度 ≤168dp 且与文字行底边对齐
    ≤12dp），选不到退回文字行本身；`acceptsNavigationSource`
    加 200dp 高度兜底，任何发现路径都选不出页面级容器；
  - 删除放开内容 ABOVE 规则与清祖先链背景的做法，界面完整
    优先；去黑带只清底栏装配自身 + 安装器对直接父壳层的处理；
  - artwork 改逐 tab 叶子捕获（`QtlArtworkCapture`）：每 tab 取
    内部真实图标（ImageView / 顶部 compound drawable）与文字，
    中间槽画加号按钮本体；独立发布键由 `QtlNavigationState`
    自持绘制把原生按钮本体画进圆钮（`ownsNavigationDrawing`），
    兜底手绘加号。
- 状态：2.1.5 已发布待验，以上均为代码层修复，未经真机验证。
- 2.1.6：真机验 2.1.5 后两修——①玻璃条正下方居中的黑坨是
  源外加号按钮的黑色圆形本体（源退回文字行时 alpha 罩不到
  它），suppress 时从窗口根部找到并置 INVISIBLE（只停绘制、
  不动布局，捕获/绘制/点按均对本体操作不受影响）；
  ②点按在发布槽后偏一位（槽 3→我的、槽 4 无反应，前两槽
  正常）：旧 dispatch 在 tab 单元上 performClick 落空后落回
  宿主通用兜底、按原始下标取文字行子视图（行内只有 4 个
  页面 tab）。改走捕获时记录的槽位身份绑定（QtlSlotBindings）
  + deliverClick 强送达（自身监听→子视图监听→目标中心合成
  触摸），不再落回通用路径。entryLabels 第二项修正为「好友」。
- 状态：2.1.6 已发布待验，以上均为代码层修复，未经真机验证。
- 2.1.7：①底栏后的主题色带（浅色白/夜间黑）是原底栏容器
  背景，安装器只清源的直接父层、这层在更外层漏网；
  stripChrome 新增 stripBarContainers：从源向上至多 3 层，
  只清底边与文字行对齐（≤20dp）、高 ≤260dp、宽 ≥ 文字行
  的祖先，只清背景不动布局。②选中漂移：宿主通用判定按槽位
  容器子下标打分，跳不过发布槽（「我的」被当槽位 3=商城）；
  QtlNavigationState 新增 resolveSelectedIndex，按捕获同口径
  枚举四 tab、查子树真实选中信号（isSelected/isActivated/
  state_selected），序号映射回槽位（发布槽后 +1）。点按加
  一级快路径：合成触摸只分发给文字行（父容器），后两槽不再
  走全窗根分发与 200 视图 DFS 慢路径，治切换卡顿。③间隙
  粘连：barLayout 摘掉本地光学三标志（localMainOptics/
  localActionOptics/actionFillUsesOnlySurfaceOpacity），字段
  集与 xhs 对齐——本地光学曾在圆钮外录出大号浅色底板；同
  时①清掉容器背景后，间隙露出的是真实内容而非主题条带。
- 状态：2.1.7 已发布待验，以上均为代码层修复，未经真机验证。
- 2.1.8：①带子第三次定位（换方法，先与微信/xhs 做完整
  diff）：带子不是任何一层背景，而是内容被 RelativeLayout
  的 ABOVE 规则顶在底栏装配上方、没铺进装配占位区，占位
  区透出根布局/窗口主题背景（浅色白/夜间黑，录屏像素实
  测内容在约 91% 屏高处全宽齐断、下方约 80dp 纯主题色）；
  间隙里的「白色连接」是同一条带露出。2.1.5–2.1.7 只清背
  景从未动约束，故三版皆在；2.1.4 的放开系被整页空白回
  归（选源问题）连带回退。本版按仓内华为健康已验证配方
  放开：逐层向上在每个 RelativeLayout 祖先里，把以 ABOVE
  顶在底栏链/底带锚点上方、高度占父高四成以上的内容摘
  ABOVE + 高度设 MATCH_PARENT（releaseContentAbove，
  一次性、进缓存每帧复断言，带 XposedBridge 日志可查是
  否命中）。②卡顿：宿主每 pre-draw 帧调 suppress、每
  dispatchDraw 帧调 resolveSelectedIndex，旧实现每帧多
  趟全树 DFS（hideLeakedCenterButton 从窗口根按资源名全
  窗找）+ 逐视图 drawableState 分配；本版 suppress 全量
  解析每源一次并缓存（ChromeCache，失效/重绑才重跑），
  每帧只复清缓存视图；resolveSelectedIndex 改走
  QtlSlotBindings 身份绑定、strongScore 命中即停、
  drawableScore 仅无强信号时跑；drawPublish 的圆钮本体
  也先查绑定。点击对位与选中映射口径未动。
- 状态：2.1.8 已发布待验，以上均为代码层修复，未经真机验证。
- 2.1.9：小乐真机验 2.1.8——小黑盒确认修完（本版不碰
  heybox）；掌盟首页已好，但切到商城页后整条变不透明黑底、
  商城槽位是占满槽位的纯平蓝椭圆且文字消失、其余槽位发暗、
  药丸颜色也变了。逐像素定案：蓝形尺寸与首页药丸逐像素
  相同（151×120px），但根因不在药丸——商城页快照落到了
  宿主通用兜底整树绘制：黑底是带方形直角的矩形、内嵌在
  圆角玻璃面板轮廓内（位图矩形边缘），逐叶捕获先清全透明
  再逐叶画、画不出整幅黑底，只有 captureNavigation 返回
  false 时的 source.draw() 兜底能产生。商城页真实构成：
  与首页同一行同一层级（GameHomeActivity 内页签页），
  差异在商城单元——软件绘制下拿不到图标叶子与文字，只剩
  选中态椭圆背景视图；旧捕获按 hasText 过滤会把它排除致
  tab 数不足，或因「任一 tab 素材全空」整次失败。药丸变
  色是透镜采样了兜底位图里商城槽的蓝椭圆，属同一根因；
  药丸填充口径不变、不按页采样。三层修：①捕获加固——行
  枚举改普查制（censusTabs：非 GONE 孩子四个即 tab、五个
  时中间发布占位按「是/含加号按钮或无文字」剔除），选中
  兜底（fallbackResolve）与点按兜底（dispatchNavigationTap）
  同口径；单 tab 素材全空只留空该槽、四个全空才失败；
  图标叶子跳过填满单元的背景型 ImageView（fillsUnit，
  宽高同超单元六成），图标布局/绘制等比钳制到 32dp；
  普查异常带 5 秒节流日志。②普查推迟——已有快照时
  inspectNavigationCaptureLabels 普查到任一 tab 无素材即
  推迟 300ms，保住上一张完整快照（商城槽继续显示首页
  捕获的商城图标文字），无快照时不推迟。③staged+校验——
  QtlAdapter.stagedNavigationSnapshot=true，
  acceptAndCommitNavigationSnapshot 按不透明占比（步进 4
  采样、alpha≥128）校验，超 0.45 拒收（逐叶位图约三成
  以内、兜底位图近全不透明），拒收不调 commit、由宿主
  staged 交换块负责发布，节流日志可查。
- 状态：2.1.9 已发布待验，以上均为代码层修复，未经真机验证。
- 2.1.10：小乐真机验 2.1.9 只回「黑底还是没解决」+ 活动
  页截图（发现→活动子页）：商城槽位与药丸已正常，但整条
  仍近不透明黑。本版先取证再修。已由代码核实+截图裁图
  确定的：①黑不在 artwork 链——截图中图标/文字/药丸均
  为逐叶快照形态，逐叶快照已正常提交；staged 拒收只保旧
  图、也产生不了这块填满面板轮廓的黑。②条身填充者是宿
  主 drawGlassPanel 的实时背景场景：录制先铺场景底色
  （liveSceneBackgroundColor：找不透明 ColorDrawable
  背景→主题 colorBackground→夜间纯黑），再回放兄弟视图
  RenderNode，之后叠半透明 surface 填充；场景内容覆盖
  不到的区域只剩不透明底色+填充=近不透明黑。③裁图实证：
  条身上部约 1/3 有紧邻上方卡片的折射内容，下部约 2/3
  平黑、与身后卡片下半图文无关，即该子页场景内容覆盖不
  到条身下部。待日志证实的：该页内容为何覆盖不到（疑为
  合成层回放不可靠的页面构成；backdrop 日志带 webView
  探查位）。修法：QtlNavigationState.resolveBackdropScene
  供给场景（宿主父层下含导航源的应用根布局，采样集合与
  默认清扫一致），宿主随即对场景底色改用
  backdropSceneBaseColor 接口默认（全透明），本适配不覆
  写该方法——覆盖不到处条身保持透明玻璃，最坏退化为只
  画图标文字的清玻璃，永不落回不透明黑。定案日志两条：
  「snapshot commit: result=committed-leaf/committed-
  generic/rejected-opaque/empty-fallback …」（分类变化
  立即、否则 30 秒节流）与「backdrop: live/displayList/
  scene/webView/ctx」（状态变化时打）。真机若仍黑，导出
  LSPosed 日志看这两行在黑底时段的取值即可定案。
- 状态：2.1.10 已发布待验，以上均为代码层修复，未经真机验证。
- 2.1.11：小乐按约导出 2.1.10 的 LSPosed 日志（包
  LSPosed_20261124_170136），两段会话（PID 13264 /
  14387）同形态，定案：①artwork 链健康——snapshot
  commit 恒 committed-leaf、opaque=0.111、装后立即提
  交；②scene=true（2.1.10 的场景供给命中）、
  webView=false（活动页 WebView 构成猜测未获支持）；
  ③断点在实时场景——每次冷启约 8 秒后 backdrop 翻为
  live=true / displayList=false 且不再恢复，
  hasUsableBackdrop() 恒 false、条身只剩 surface 填
  充（夜间近不透明黑）。模块内三处 discardDisplayList
  均同时清 liveBackdropActive 或为其他包/适配专用，
  无一能产生该状态；宿主在场景视图自身节点无 list
  且视图未 dirty 时清扫于 nodes.isEmpty() 处原样返
  回、永不重录——判定丢失属框架层、宿主无自愈（原生
  触发点日志不可见，不冒充定案）。同包无 qtl 崩溃/
  异常、无 census 失败，CfgDiag 约 1 秒一次的后台
  配置轮询为全天恒在的既有行为，未见卡顿复发迹象。
  修法只补自愈一环：QtlNavigationState.onHostFrame
  检测 live && !displayList 持续超 400ms 后，以
  1.5 秒节流把场景视图 invalidate（逼框架重录其
  display list、打破死锁）+ 调宿主
  adapterScheduleBackdropRefresh(120ms) 标脏场景签
  名迫使重录包装节点；backdrop 日志加 repairs= 计
  数，恢复行（displayList=true 带 repairs）即自愈闭
  合的证据。其余链路（捕获/点击/选中/场景供给/透
  明底色）一律未动。
- 状态：2.1.11 已发布待验，以上均为代码层修复，未经真机验证。
