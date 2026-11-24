package io.github.offlineglass.hook.adapters.qtl

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.offlineglass.config.GlassConfig
import io.github.offlineglass.hook.GlassHostLayout
import io.github.offlineglass.hook.NavigationCandidate
import io.github.offlineglass.hook.adapters.AdapterBarLayout
import io.github.offlineglass.hook.adapters.AppNavigationState
import io.github.offlineglass.hook.adapters.TargetAdapter
import io.github.offlineglass.targets.TargetSpec

/**
 * 掌上英雄联盟 (com.tencent.qt.qtl) 专属适配。
 *
 * 宿主结构 (来自 PalmPurify 对 12.9.0 的定位, 12.10.0 沿用资源名):
 * 首页 GameHomeActivity; 底栏文字行 `tab_title_content`
 * (子项为各 tab, 中间留发布位), 中间加号按钮
 * `bottom_title_center_btn` 挂在更高层。
 *
 * 2.1.4 真机回归 (整页空白) 的教训: 旧 findNavigation 往上三层
 * 找「含加号按钮的容器」作源, 在 12.10.0 上命中的是页面级大
 * 容器。共享管线对源做两件事: 安装时把源 alpha 置 0, 前景按
 * 源宽等分切片投影——源一旦是页面容器, 整页被隐藏, 且每个
 * 槽位里画的是整页微缩图 (真机截图实证)。本版 (2.1.5):
 * 源严格限制为底栏尺寸的壳层 (高度与底边对齐双校验), 选不到
 * 就退回文字行本身; acceptsNavigationSource 再做高度兜底;
 * 内容布局 (RelativeLayout ABOVE 规则) 与祖先链背景一律不再
 * 触碰, 去黑带只靠清源自身底色 + 安装器对直接父壳层的处理。
 *
 * 2.1.6: 源外加号按钮的黑坨在 suppress 阶段从窗口根部找到
 * 并置 INVISIBLE (见 hideLeakedCenterButton); 点按改走捕获
 * 时记录的槽位身份绑定 + 强送达 (见 QtlSlotBindings 与
 * deliverClick), 不再按下标记数、也不落回宿主通用兜底。
 *
 * 2.1.7: ①玻璃条后的主题色带是原底栏容器背景 (浅色白/夜间
 * 黑), 在 suppress 时按几何守卫逐层清到那一层 (见
 * stripBarContainers); ②选中漂移与后两 tab 卡顿: 选中改由
 * QtlNavigationState.resolveSelectedIndex 以宿主真实选中态
 * 为准 (宿主通用判定按子视图下标打分、跳不过发布槽),
 * 点按加一级「只分发给文字行」的合成触摸快路径; ③独立圆钮
 * 摘掉本地光学三标志, 字段集与 xhs 对齐 (见 barLayout)。
 */
internal object QtlAdapter : TargetAdapter {
    override val key = "qtl"

    private const val TAB_ROW_ID = "tab_title_content"
    private const val CENTER_BUTTON_ID = "bottom_title_center_btn"
    private const val CENTER_INDEX = 2
    private const val SLOT_COUNT = 5

    override val sourceCenterActionIndex = CENTER_INDEX
    override val sourceCenterTranslationDp = -2f
    override val sourceCenterClipRatio = 0.7f

    // 独立发布键的圆钮没有任何通用路径绘制 artwork (xhs/贴吧
    // 均自绘), 故本适配自持绘制: 通用投影 + 原生加号按钮本体。
    override val ownsNavigationDrawing = true

    override fun createNavigationState(context: Context, host: GlassHostLayout): AppNavigationState =
        QtlNavigationState(host)

    override fun findNavigation(root: View, spec: TargetSpec): NavigationCandidate? {
        val row = findByEntryName(root, TAB_ROW_ID) as? ViewGroup ?: return null
        if (!row.isShown || row.width <= 0 || row.height <= 0) return null
        val density = root.resources.displayMetrics.density
        // 首选: 同时含加号按钮、且尺寸确实是底栏壳层的祖先。
        var parent = row.parent as? ViewGroup
        var depth = 0
        while (parent != null && depth < 3) {
            if (isBarShell(parent, row, density) &&
                findByEntryName(parent, CENTER_BUTTON_ID) != null
            ) {
                return NavigationCandidate(parent, SLOT_COUNT, 1_000)
            }
            parent = parent.parent as? ViewGroup
            depth++
        }
        // 次选: 文字行的直接父容器 (底栏壳层; 加号按钮在外层,
        // 由 prepareNavigationSource 移入)。
        val direct = row.parent as? ViewGroup
        if (direct != null && isBarShell(direct, row, density)) {
            return NavigationCandidate(direct, SLOT_COUNT, 1_000)
        }
        // 兜底: 文字行本身, 保证源永远只是底栏。
        return NavigationCandidate(row, SLOT_COUNT, 1_000)
    }

    override fun acceptsNavigationSource(navigation: ViewGroup): Boolean {
        // 兜底防线: 任何发现路径给出的源只要高得不像底栏就拒绝,
        // 杜绝页面级容器再被当源隐藏 (2.1.4 回归的直接原因)。
        if (navigation.height <= 0) return true // 未布局时不拦, 安装前已布局完成
        val density = navigation.resources.displayMetrics.density
        return navigation.height <= (200f * density).toInt()
    }

    /** 底栏壳层校验: 高度在底栏量级, 且底边与文字行底边基本对齐。 */
    private fun isBarShell(candidate: ViewGroup, row: ViewGroup, density: Float): Boolean {
        if (candidate === row) return false
        val maxHeight = (168f * density).toInt()
        if (candidate.height !in 1..maxHeight) return false
        if (candidate.height < row.height) return false
        if (candidate.width < row.width * 0.9f) return false
        val candidateLoc = IntArray(2).also(candidate::getLocationInWindow)
        val rowLoc = IntArray(2).also(row::getLocationInWindow)
        val bottomDelta = kotlin.math.abs(
            (candidateLoc[1] + candidate.height) - (rowLoc[1] + row.height),
        )
        return bottomDelta <= (12f * density).toInt()
    }

    override fun estimateSlotCount(group: ViewGroup, spec: TargetSpec): Int? {
        val name = entryName(group)
        return if (name == TAB_ROW_ID || findByEntryName(group, TAB_ROW_ID) != null) SLOT_COUNT else null
    }

    override fun barLayout(
        config: GlassConfig, width: Int, height: Int, density: Float, slots: Int,
    ): AdapterBarLayout? {
        // 与 tieba/xhs 相同的独立发布键几何: 四个页面在左,
        // 发布键以与栏高相等的圆形独立在右。
        val gap = 12f * density
        if (!config.detachedPublish || slots != SLOT_COUNT ||
            height <= 0 || width <= height + gap
        ) return null
        val navigation = listOf(0, 1, 3, 4).filter { config.hiddenMask and (1 shl it) == 0 }
        if (navigation.isEmpty()) return null
        val actionLeft = (width - height).toFloat()
        // 光学字段集与 xhs/小黑盒 (2.1.6) 对齐: 不开本地光学。
        // 本地光学的采样节点按 padded 区域录制, 圆钮周围会多出
        // 一圈浅色底板 (2.1.6 掌盟截图里圆钮外的大号白色圆盘),
        // 且主条底板横跨间隙与圆钮粘连; 走共享外层玻璃路径后
        // 两部分各自成形 (2.1.7)。
        return AdapterBarLayout(
            mainWidth = actionLeft - gap,
            actionLeft = actionLeft,
            actionSize = height.toFloat(),
            actionIndex = CENTER_INDEX,
            navigationIndices = navigation,
            actionHighlightScale = 0.35f,
        )
    }

    override fun prepareNavigationSource(navigation: ViewGroup, density: Float) {
        // 加号按钮若不在源内 (个别版本挂在根布局上), 移入底栏
        // 容器, 否则它会在玻璃条后面单独绘制透出来。源已经过
        // findNavigation 的底栏尺寸校验, 移入目标只会是底栏壳层。
        runCatching {
            val root = navigation.rootView ?: return
            val button = findByEntryName(root, CENTER_BUTTON_ID) ?: return
            if (isDescendant(button, navigation)) return
            // 只移入容器型源; 源退回文字行本身时不动, 免得把按钮
            // 塞进文字行变成第六个孩子。
            val row = findByEntryName(navigation, TAB_ROW_ID)
            if (row == null || row === navigation) return
            val oldParent = button.parent as? ViewGroup ?: return
            val params = button.layoutParams
            oldParent.removeView(button)
            if (navigation is FrameLayout && params is FrameLayout.LayoutParams) {
                navigation.addView(button, params)
            } else {
                navigation.addView(button)
            }
        }
    }

    override fun suppressNativeBottomChrome(navigation: ViewGroup) {
        stripChrome(navigation)
        // 宿主会在布局后重绘底色, 下一帧再清一次 (高德适配同款)。
        navigation.post { runCatching { stripChrome(navigation) } }
    }

    override fun suppressNativeBottomChromeOnRebind(navigation: ViewGroup) =
        stripChrome(navigation)

    private fun stripChrome(navigation: ViewGroup) {
        // 清底栏装配自身 (源/文字行/加号按钮) 的底色, 再清原底栏
        // 容器那一层 (见 stripBarContainers)。内容布局一律不碰。
        clearChrome(navigation)
        findByEntryName(navigation, TAB_ROW_ID)?.let { clearChrome(it) }
        findByEntryName(navigation, CENTER_BUTTON_ID)?.let { clearChrome(it) }
        hideLeakedCenterButton(navigation)
        stripBarContainers(navigation)
    }

    /**
     * 清原底栏容器的主题背景 (2.1.7)。
     *
     * 2.1.6 后真机仍报玻璃条后有一条背景带, 且经小乐定性: 浅色
     * 模式下是白色、夜间是黑色, 随主题变色——即宿主原底栏容器
     * 的背景本身, 不是任何黑色物件。它是全宽容器, 玻璃条比它
     * 窄、且位置偏高, 容器背景便从玻璃条右后方与间隙处露出来
     * (间隙里看到的「白色连接」也是它)。安装器的通用逻辑只清
     * 源的直接父层 (≤196dp), 这层容器在更外层, 一直漏网。
     *
     * 这里从源逐层向上 (至多 3 层), 只清同时满足「底边与文字
     * 行底边基本对齐 (≤20dp)、高度在底栏量级 (≤260dp)、宽度
     * 不小于文字行」的祖先——即底栏区域容器本身; 任一层不合
     * 格立即停 (页面级容器高度远超上限, 绝不会被清到), 不改
     * 任何布局参数, 界面完整优先的红线不变。
     */
    private fun stripBarContainers(navigation: ViewGroup) {
        runCatching {
            val row = findByEntryName(navigation, TAB_ROW_ID) ?: navigation
            val density = navigation.resources.displayMetrics.density
            val rowLoc = IntArray(2).also(row::getLocationInWindow)
            val rowBottom = rowLoc[1] + row.height
            var current = navigation.parent as? ViewGroup
            var depth = 0
            while (current != null && depth < 3) {
                val loc = IntArray(2).also(current::getLocationInWindow)
                val bottomDelta = kotlin.math.abs((loc[1] + current.height) - rowBottom)
                val qualifies = current.height in 1..(260f * density).toInt() &&
                    bottomDelta <= (20f * density).toInt() &&
                    current.width >= row.width
                if (!qualifies) break
                clearChrome(current)
                current = current.parent as? ViewGroup
                depth++
            }
        }
    }

    /**
     * 加号按钮挂在更高层、不在导航源内时 (源退回文字行本身的
     * 情形), 源的 alpha 隐藏罩不到它, 它的黑色圆形本体会透过
     * 玻璃条下方露出半截 (2.1.5 真机截图正下方居中的黑坨)。
     * 这里从窗口根部找到它并置 INVISIBLE: 只停它自身的绘制,
     * 不改任何布局; 捕获与圆钮绘制都是对视图本体直接 draw(),
     * 不受 visibility 影响, 点按绑定也照常。在源内时它已随源
     * 一起被隐藏, 不重复处理。
     */
    private fun hideLeakedCenterButton(navigation: ViewGroup) {
        runCatching {
            val root = navigation.rootView ?: return
            val button = findByEntryName(root, CENTER_BUTTON_ID) ?: return
            if (isDescendant(button, navigation)) return
            if (button.visibility != View.INVISIBLE) {
                button.visibility = View.INVISIBLE
            }
        }
    }

    override fun dispatchNavigationTap(source: ViewGroup?, index: Int, slotCount: Int): Boolean {
        val src = source ?: return false
        // 首选: 捕获时记录的身份绑定 (槽位 → tab 视图本体 /
        // 加号按钮本体), 与用户所见的 artwork 严格同源。
        val bound = QtlSlotBindings.lookup(src)?.get(index)
        if (bound != null) return deliverClick(bound)
        // 兜底: 尚未捕获成功时, 按与捕获完全相同的口径枚举
        // (非 GONE 且带文字的四个 tab, 序号与槽位同映射)。
        if (index == CENTER_INDEX) {
            val button = findByEntryName(src, CENTER_BUTTON_ID)
                ?: src.rootView?.let { findByEntryName(it, CENTER_BUTTON_ID) }
                ?: return false
            return deliverClick(button)
        }
        val row = findByEntryName(src, TAB_ROW_ID) as? ViewGroup ?: return false
        val tabs = (0 until row.childCount)
            .map(row::getChildAt)
            .filter { it.visibility != View.GONE && hasText(it) }
        if (tabs.size != SLOT_COUNT - 1) return false
        // 槽位 0/1/3/4 依次对应文字行的四个 tab。
        val ordinal = if (index < CENTER_INDEX) index else index - 1
        val target = tabs.getOrNull(ordinal) ?: return false
        return deliverClick(target)
    }

    /**
     * 把点按真正送达目标视图, 绝不落回宿主通用兜底路径 (它按
     * 原始下标取子视图, 正是发布槽后偏一位的来源)。四级递进:
     * 目标自身有监听 → performClick; 否则在目标中心合成一对
     * DOWN/UP 触摸、直接分发给目标的父容器 (文字行)——由宿主
     * 自己的命中分发决定落点, 与真实手指点按同路, 且分发范围
     * 只限一行, 不必全窗遍历 (2.1.7: 后两槽此前走到全窗根分发
     * 与 200 视图 DFS 两条慢路径, 切换明显卡顿); 再否则深度
     * 优先找它内部带监听的子视图 (闲鱼适配同款); 最后才从
     * 窗口根部分发合成触摸。任一级按目标本体坐标投递成功即
     * 视为送达, 返回 true 防止宿主再按下标重复处理。
     */
    private fun deliverClick(target: View): Boolean {
        if (runCatching { target.hasOnClickListeners() && target.performClick() }
                .getOrDefault(false)
        ) return true
        if (synthesizeTapOnParent(target)) return true
        if (clickDescendantWithListener(target)) return true
        return synthesizeTap(target)
    }

    /** 在目标中心合成触摸, 只分发给目标的直接父容器 (文字行)。 */
    private fun synthesizeTapOnParent(target: View): Boolean {
        val parent = target.parent as? ViewGroup ?: return false
        if (!target.isAttachedToWindow || target.width <= 0 || target.height <= 0) return false
        return runCatching {
            val targetLoc = IntArray(2).also(target::getLocationInWindow)
            val parentLoc = IntArray(2).also(parent::getLocationInWindow)
            val x = targetLoc[0] - parentLoc[0] + target.width / 2f
            val y = targetLoc[1] - parentLoc[1] + target.height / 2f
            dispatchTap(parent, x, y)
        }.getOrDefault(false)
    }

    private fun dispatchTap(recipient: View, x: Float, y: Float): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        val down = android.view.MotionEvent.obtain(
            now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0,
        )
        val up = android.view.MotionEvent.obtain(
            now, now + 48L, android.view.MotionEvent.ACTION_UP, x, y, 0,
        )
        return try {
            recipient.dispatchTouchEvent(down)
            recipient.dispatchTouchEvent(up)
            true
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private fun clickDescendantWithListener(root: View): Boolean {
        if (root !is ViewGroup) return false
        val stack = ArrayDeque<View>()
        for (index in root.childCount - 1 downTo 0) stack += root.getChildAt(index)
        var visited = 0
        while (stack.isNotEmpty() && visited++ < 200) {
            val view = stack.removeLast()
            if (view.isShown && runCatching {
                    view.hasOnClickListeners() && view.performClick()
                }.getOrDefault(false)
            ) return true
            if (view is ViewGroup) {
                for (index in view.childCount - 1 downTo 0) stack += view.getChildAt(index)
            }
        }
        return false
    }

    private fun synthesizeTap(target: View): Boolean {
        if (!target.isAttachedToWindow || target.width <= 0 || target.height <= 0) return false
        val root = target.rootView ?: return false
        return runCatching {
            val targetLoc = IntArray(2).also(target::getLocationInWindow)
            val rootLoc = IntArray(2).also(root::getLocationInWindow)
            val x = (targetLoc[0] - rootLoc[0] + target.width / 2f)
            val y = (targetLoc[1] - rootLoc[1] + target.height / 2f)
            dispatchTap(root, x, y)
        }.getOrDefault(false)
    }

    private fun clearChrome(view: View) {
        if (view.background != null) view.background = ColorDrawable(Color.TRANSPARENT)
        if (view.foreground != null) view.foreground = null
        if (view.elevation != 0f) view.elevation = 0f
        if (view.translationZ != 0f) view.translationZ = 0f
    }

    private fun hasText(view: View): Boolean {
        if (view is android.widget.TextView) return !view.text.isNullOrBlank()
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (hasText(view.getChildAt(index))) return true
            }
        }
        return false
    }

    private fun isDescendant(view: View, ancestor: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }

    private fun entryName(view: View): String? = runCatching {
        if (view.id == View.NO_ID) null else view.resources.getResourceEntryName(view.id)
    }.getOrNull()

    private fun findByEntryName(root: View, name: String): View? {
        val stack = ArrayDeque<View>()
        stack += root
        var visited = 0
        while (stack.isNotEmpty() && visited++ < 2_000) {
            val view = stack.removeLast()
            if (entryName(view) == name) return view
            if (view is ViewGroup) {
                for (index in view.childCount - 1 downTo 0) stack += view.getChildAt(index)
            }
        }
        return null
    }
}
