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
