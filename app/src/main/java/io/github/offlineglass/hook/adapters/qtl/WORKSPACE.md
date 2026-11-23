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
- 状态：2.1.4 已发布待验，以上均为代码层修复，未经真机验证。
