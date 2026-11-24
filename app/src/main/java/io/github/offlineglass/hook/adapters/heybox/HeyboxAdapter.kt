package io.github.offlineglass.hook.adapters.heybox

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import io.github.offlineglass.config.GlassConfig
import io.github.offlineglass.hook.GlassHostLayout
import io.github.offlineglass.hook.NavigationCandidate
import io.github.offlineglass.hook.adapters.AdapterBarLayout
import io.github.offlineglass.hook.adapters.AppNavigationState
import io.github.offlineglass.hook.adapters.TargetAdapter
import io.github.offlineglass.hook.compat.XposedBridge
import io.github.offlineglass.hook.compat.callbacks.XC_LoadPackage
import io.github.offlineglass.targets.TargetSpec

/**
 * 小黑盒 (com.max.xiaoheihe) 专属适配。
 *
 * 宿主结构 (来自 BetterHeybox 的定位成果, 1.3.402):
 * 根 `vg_main_root` (RelativeLayout) 内有内容 `fl_container`
 * (以 ABOVE 规则顶在底栏上方)、底栏 RadioGroup `rg_main`
 * (子项 rb_1..rb_5, 其中 rb_3 是中间占位)、以及独立于
 * RadioGroup 的加号按钮 `vg_mid_tab` (对齐盖在 rb_3 上)。
 * 通用发现时代的问题: vg_mid_tab 在源外单独绘制, 透过半透明
 * 玻璃发糊错位; fl_container 不铺底, 玻璃条后面是根背景黑带。
 * 本适配把 vg_mid_tab 移入 rg_main 占位处, 使五个槽位的
 * artwork/点按/显隐都回到同一索引空间, 并放开 ABOVE 规则
 * 让内容铺到屏幕底部。
 *
 * 2.1.8: 带子定位修正——旧放开只摘 ABOVE、漏了高度设
 * MATCH_PARENT 一步 (华为健康已验证配方是两步), 内容没
 * 真正伸到底, 占位区透出根背景成带; 本版两步齐做, 并按
 * BetterHeybox 当年口径按名放开 fl_container (见
 * releaseContentAbove / releaseNamedContent)。suppress
 * 同步改每源一次全量解析 + 缓存复清 (见 ChromeCache)。
 */
internal object HeyboxAdapter : TargetAdapter {
    override val key = "heybox"

    private const val BAR_ID = "rg_main"
    private const val MID_TAB_ID = "vg_mid_tab"
    private const val CONTENT_ID = "fl_container"
    private const val PLACEHOLDER_ID = "rb_3"
    private const val CENTER_INDEX = 2
    private const val SLOT_COUNT = 5

    override val sourceCenterActionIndex = CENTER_INDEX
    override val sourceCenterTranslationDp = -2f
    override val sourceCenterClipRatio = 0.7f

    // 独立发布键的圆钮没有任何通用路径绘制 artwork (xhs/贴吧
    // 均自绘), 故本适配自持绘制: 通用投影 + vg_mid_tab 图标叶子。
    override val ownsNavigationDrawing = true

    override fun createNavigationState(context: Context, host: GlassHostLayout): AppNavigationState =
        HeyboxNavigationState(host)

    // 评论输入框与键盘贴合 (2.1.7 恢复 BetterHeybox 时代的
    // fork 定制 CommentInputDockHook, 见 HeyboxCommentDock):
    // 与玻璃/底栏无关的独立行为, 随适配 hooks 一并装入。
    override fun installHooks(lpparam: XC_LoadPackage.LoadPackageParam, spec: TargetSpec) {
        HeyboxCommentDock.install()
    }

    // 沉浸: 共享安装器只做到窗口标志与导航条透明, 宿主根布局仍
    // 按 navigationBars inset 留底; 在此把主界面窗口的导航条
    // 底部 inset 清零, 内容才真正铺到物理底部 (见 HeyboxInsets)。
    override fun configureNavigationInsets(decor: View, enabled: Boolean) =
        HeyboxInsets.configure(decor, enabled)

    override fun findNavigation(root: View, spec: TargetSpec): NavigationCandidate? {
        val bar = findByEntryName(root, BAR_ID) as? ViewGroup ?: return null
        if (!bar.isShown || bar.width <= 0 || bar.height <= 0) return null
        return NavigationCandidate(bar, SLOT_COUNT, 1_000)
    }

    override fun estimateSlotCount(group: ViewGroup, spec: TargetSpec): Int? =
        if (entryName(group) == BAR_ID) SLOT_COUNT else null

    override fun barLayout(
        config: GlassConfig, width: Int, height: Int, density: Float, slots: Int,
    ): AdapterBarLayout? {
        // 独立发布键几何与光学口径对齐 xhs (2.1.6): 主条在圆钮
        // 前一个 gap 处收住, 两部分走共享外层玻璃路径、各自
        // clip 与阴影分别成形。此前照 tieba 开的本地光学
        // (localMainOptics/localActionOptics) 会让主条的采样
        // 节点按 padded 全宽输入录制, 浅色底板横跨到圆钮区域
        // 与主条粘连 (2.1.5 真机截图橙框), 故摘掉这三个标志,
        // 与 xhs 的字段集完全一致。
        val gap = 12f * density
        if (!config.detachedPublish || slots != SLOT_COUNT ||
            height <= 0 || width <= height + gap
        ) return null
        val navigation = listOf(0, 1, 3, 4).filter { config.hiddenMask and (1 shl it) == 0 }
        if (navigation.isEmpty()) return null
        val actionLeft = (width - height).toFloat()
        return AdapterBarLayout(
            mainWidth = actionLeft - gap,
            actionLeft = actionLeft,
            actionSize = height.toFloat(),
            actionIndex = CENTER_INDEX,
            navigationIndices = navigation,
            actionHighlightScale = 0.35f,
        )
    }

    override fun prepareNavigationSource(navigation: ViewGroup, density: Float) =
        integrateMidTab(navigation)

    /**
     * suppress 的解析结果缓存 (2.1.8, 与掌盟同口径): 宿主每个
     * pre-draw 帧都会调 suppress, 旧实现每帧重跑兄弟矩形扫描、
     * getLocationInWindow 与规则遍历。现改为每个源只做一次
     * 全量解析, 之后每帧只对缓存引用做 O(1) 复清/复断言;
     * 父层子视图数变化 (底栏热更新增删视图) 或重绑时才重解析。
     */
    private class ChromeCache(
        val parent: ViewGroup?,
        val parentChildCount: Int,
        val containers: List<View>,
        val thinSiblings: List<View>,
        val released: List<View>,
        val guards: Map<ViewGroup, Int>,
    )

    private val chromeCaches = java.util.WeakHashMap<ViewGroup, ChromeCache>()

    override fun suppressNativeBottomChrome(navigation: ViewGroup) =
        stripChrome(navigation, force = false)

    override fun suppressNativeBottomChromeOnRebind(navigation: ViewGroup) {
        // 底栏热更新可能把 vg_mid_tab 重新挂回根布局, 重绑时复查。
        integrateMidTab(navigation)
        synchronized(chromeCaches) { chromeCaches.remove(navigation) }
        stripChrome(navigation, force = true)
    }

    private fun stripChrome(navigation: ViewGroup, force: Boolean) {
        val cached = synchronized(chromeCaches) { chromeCaches[navigation] }
        if (!force && cached != null && cacheValid(navigation, cached)) {
            // 轻量复清: 只碰缓存视图本身, 不遍历任何树。
            clearChrome(navigation)
            cached.containers.forEach { clearChrome(it) }
            cached.thinSiblings.forEach { clearChrome(it) }
            cached.released.forEach { reassertReleased(it) }
            return
        }
        // 全量解析 (每源一次, 或缓存失效/重绑时)。
        clearChrome(navigation)
        val (released, guards) = releaseContentAbove(navigation)
        val named = releaseNamedContent(navigation)
        val containers = stripBarContainer(navigation)
        val thinSiblings = sweepThinSiblings(navigation)
        val parent = navigation.parent as? ViewGroup
        synchronized(chromeCaches) {
            chromeCaches[navigation] = ChromeCache(
                parent,
                parent?.childCount ?: -1,
                containers,
                thinSiblings,
                released + listOfNotNull(named),
                guards,
            )
        }
    }

    private fun cacheValid(navigation: ViewGroup, cache: ChromeCache): Boolean {
        val parent = navigation.parent as? ViewGroup
        if (parent !== cache.parent) return false
        if (parent != null && parent.childCount != cache.parentChildCount) return false
        if (cache.containers.any { !isDescendant(navigation, it) }) return false
        return cache.guards.all { (p, count) -> p.childCount == count }
    }

    /** 内容的 ABOVE 若被宿主重设, 在轻量复清里直接复断言。 */
    private fun reassertReleased(child: View) {
        runCatching {
            val params = child.layoutParams as? RelativeLayout.LayoutParams ?: return
            if (params.getRule(RelativeLayout.ABOVE) != 0 ||
                params.height != ViewGroup.LayoutParams.MATCH_PARENT
            ) {
                params.removeRule(RelativeLayout.ABOVE)
                params.height = ViewGroup.LayoutParams.MATCH_PARENT
                child.layoutParams = params
            }
        }
    }

    /**
     * 根布局里落在底栏带内的兄弟薄层 (阴影层/背景条等) 清掉
     * 底色, 返回清过的视图供缓存复清; 内容与底栏本体不在此
     * 列。2.1.7: 判定带从「与 rg_main 矩形相交」放宽为「整条
     * 底栏带」(左右到根布局两边、向下到根布局底边), 高度
     * 上限 3 倍栏高, 仍只清薄层, 不碰内容级视图。
     */
    private fun sweepThinSiblings(navigation: ViewGroup): List<View> {
        val cleared = ArrayList<View>()
        runCatching {
            val parent = navigation.parent as? ViewGroup ?: return@runCatching
            val barRect = android.graphics.Rect()
            navigation.getGlobalVisibleRect(barRect)
            val stripRect = android.graphics.Rect(barRect)
            val parentRect = android.graphics.Rect()
            if (parent.getGlobalVisibleRect(parentRect)) {
                stripRect.left = minOf(stripRect.left, parentRect.left)
                stripRect.right = maxOf(stripRect.right, parentRect.right)
                stripRect.bottom = maxOf(stripRect.bottom, parentRect.bottom)
            }
            for (index in 0 until parent.childCount) {
                val sibling = parent.getChildAt(index)
                if (sibling === navigation || entryName(sibling) == MID_TAB_ID) continue
                if (entryName(sibling) == CONTENT_ID || entryName(sibling) == "vg_tips" ||
                    entryName(sibling) == "vg_fullscreen_video_container"
                ) continue
                val rect = android.graphics.Rect()
                if (sibling.getGlobalVisibleRect(rect) && rect.height() <= barRect.height() * 3 &&
                    android.graphics.Rect.intersects(rect, stripRect)
                ) {
                    clearChrome(sibling)
                    cleared += sibling
                }
            }
        }
        return cleared
    }

    /**
     * 清原底栏容器的主题背景 (2.1.7, 与掌盟同口径): 从 rg_main
     * 逐层向上 (至多 3 层), 只清底边与底栏对齐、高度在底栏
     * 量级 (≤260dp)、宽度不小于底栏的祖先; vg_main_root 是
     * 全屏根布局, 高度远超上限, 天然跳过。只清背景, 不动布局。
     */
    private fun stripBarContainer(navigation: ViewGroup): List<View> {
        val cleared = ArrayList<View>()
        runCatching {
            val density = navigation.resources.displayMetrics.density
            val navLoc = IntArray(2).also(navigation::getLocationInWindow)
            val navBottom = navLoc[1] + navigation.height
            var current = navigation.parent as? ViewGroup
            var depth = 0
            while (current != null && depth < 3) {
                val loc = IntArray(2).also(current::getLocationInWindow)
                val bottomDelta = kotlin.math.abs((loc[1] + current.height) - navBottom)
                val qualifies = current.height in 1..(260f * density).toInt() &&
                    bottomDelta <= (20f * density).toInt() &&
                    current.width >= navigation.width
                if (!qualifies) break
                clearChrome(current)
                cleared += current
                current = current.parent as? ViewGroup
                depth++
            }
        }
        return cleared
    }

    /**
     * 放开内容在底栏上方的布局预留 (2.1.8, 与掌盟同版同口径,
     * 落点分析见 QtlAdapter.releaseContentAbove 的注释): 玻璃
     * 条下方的主题色带不是背景没清干净, 而是 fl_container 被
     * ABOVE 规则顶在底栏上方、没铺进底栏占位区, 占位区透出
     * 根布局主题背景; 间隙里的「白色连接」是同一条带露出。
     *
     * 旧 extendContentUnderBar 只摘 ABOVE 规则、漏了华为健康
     * 已验证配方里的另一步——把内容高度设为 MATCH_PARENT:
     * 只摘规则时, 高度不是满高的内容在 RelativeLayout 里不
     * 会自己伸到父层底部, 带子因此在 2.1.4–2.1.7 一直都在。
     * 本版两步齐做, 并按 BetterHeybox 当年可用实现的口径,
     * 另行按名找到 fl_container 无条件放开 (见
     * releaseNamedContent), 不再依赖锚点 id 猜对。
     * 返回 (被放开的视图, 守卫父层→子视图数)。
     */
    private fun releaseContentAbove(
        navigation: ViewGroup,
    ): Pair<List<View>, Map<ViewGroup, Int>> {
        val released = ArrayList<View>()
        val guards = HashMap<ViewGroup, Int>()
        runCatching {
            val density = navigation.resources.displayMetrics.density
            var chainChild: View = navigation
            var ancestor = navigation.parent as? ViewGroup
            var depth = 0
            while (ancestor != null && depth < 4) {
                if (ancestor is RelativeLayout) {
                    guards[ancestor] = ancestor.childCount
                    releaseAboveIn(ancestor, chainChild, navigation, density, released)
                }
                chainChild = ancestor
                ancestor = ancestor.parent as? ViewGroup
                depth++
            }
        }
        return released to guards
    }

    private fun releaseAboveIn(
        layout: RelativeLayout,
        chainChild: View,
        source: ViewGroup,
        density: Float,
        released: MutableList<View>,
    ) {
        val layoutLoc = IntArray(2).also(layout::getLocationInWindow)
        val layoutBottom = layoutLoc[1] + layout.height
        val bandSlack = source.height + (64f * density).toInt()
        for (index in 0 until layout.childCount) {
            val child = layout.getChildAt(index) ?: continue
            if (child === chainChild) continue
            val params = child.layoutParams as? RelativeLayout.LayoutParams ?: continue
            val anchorId = params.getRule(RelativeLayout.ABOVE)
            if (anchorId == 0) continue
            // 内容判别: 自身已布局高度占父高四成以上, 小配件
            // (提示条/阴影) 天然够不着, 不会被误放。
            if (layout.height <= 0 || child.height < layout.height * 0.4f) continue
            val anchor = layout.findViewById<View>(anchorId) ?: continue
            if (anchor.parent !== layout) continue
            val qualifies = anchor === chainChild || run {
                val anchorLoc = IntArray(2).also(anchor::getLocationInWindow)
                val anchorBottom = anchorLoc[1] + anchor.height
                layoutBottom - anchorBottom in 0..bandSlack
            }
            if (!qualifies) continue
            params.removeRule(RelativeLayout.ABOVE)
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            child.layoutParams = params
            released += child
            XposedBridge.log(
                "[OfflineGlass][Heybox] released content ABOVE: child=" +
                    "${entryName(child)} anchor=${entryName(anchor)}",
            )
        }
    }

    /**
     * 按名放开 fl_container (BetterHeybox 可用实现同口径: 它
     * 按名找到内容, 把 ABOVE 直接清零, 不猜锚点)。本版再补
     * 华为配方里的高度一步。返回内容视图供缓存复断言。
     */
    private fun releaseNamedContent(navigation: ViewGroup): View? =
        runCatching {
            var top: View = navigation
            var depth = 0
            while (top.parent is ViewGroup && depth < 6) {
                top = top.parent as ViewGroup
                depth++
            }
            val content = findByEntryName(top, CONTENT_ID)
            val parent = content?.parent as? RelativeLayout
            val params = content?.layoutParams as? RelativeLayout.LayoutParams
            if (content == null || parent == null || params == null) {
                null
            } else {
                if (params.getRule(RelativeLayout.ABOVE) != 0 ||
                    params.height != ViewGroup.LayoutParams.MATCH_PARENT
                ) {
                    params.removeRule(RelativeLayout.ABOVE)
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    content.layoutParams = params
                    XposedBridge.log(
                        "[OfflineGlass][Heybox] released fl_container ABOVE by name",
                    )
                }
                content
            }
        }.getOrNull()

    /** 把独立的加号按钮 vg_mid_tab 移入 rg_main, 顶替 rb_3 占位并继承其布局参数。幂等。 */
    private fun integrateMidTab(navigation: ViewGroup) {
        runCatching {
            if (entryName(navigation) != BAR_ID) return
            val root = navigation.rootView ?: return
            val mid = findByEntryName(root, MID_TAB_ID) ?: return
            if (mid.parent === navigation) return
            val oldParent = mid.parent as? ViewGroup ?: return
            val placeholder = findByEntryName(navigation, PLACEHOLDER_ID)
            oldParent.removeView(mid)
            if (placeholder != null && placeholder.parent === navigation) {
                val index = navigation.indexOfChild(placeholder)
                val params = placeholder.layoutParams
                navigation.removeView(placeholder)
                navigation.addView(mid, index, params)
            } else {
                navigation.addView(mid, CENTER_INDEX.coerceAtMost(navigation.childCount))
            }
        }
    }

    private fun isDescendant(view: View, ancestor: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }

    private fun clearChrome(view: View) {
        if (view.background != null) view.background = ColorDrawable(Color.TRANSPARENT)
        if (view.foreground != null) view.foreground = null
        if (view.elevation != 0f) view.elevation = 0f
        if (view.translationZ != 0f) view.translationZ = 0f
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
