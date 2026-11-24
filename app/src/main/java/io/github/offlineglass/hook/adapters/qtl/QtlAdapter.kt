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
        return AdapterBarLayout(
            mainWidth = actionLeft - gap,
            actionLeft = actionLeft,
            actionSize = height.toFloat(),
            actionIndex = CENTER_INDEX,
            navigationIndices = navigation,
            actionHighlightScale = 0.35f,
            localActionOptics = true,
            localMainOptics = true,
            actionFillUsesOnlySurfaceOpacity = true,
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
        // 只清底栏装配自身 (源/文字行/加号按钮) 的底色。祖先链
        // 背景与内容布局不再触碰: 直接父壳层的底色由安装器的
        // 通用逻辑处理 (父高 ≤196dp 时清背景), 足够去黑带,
        // 且不会误伤页面 (2.1.5 口径: 界面完整优先于去黑带)。
        clearChrome(navigation)
        findByEntryName(navigation, TAB_ROW_ID)?.let { clearChrome(it) }
        findByEntryName(navigation, CENTER_BUTTON_ID)?.let { clearChrome(it) }
    }

    override fun dispatchNavigationTap(source: ViewGroup?, index: Int, slotCount: Int): Boolean {
        val src = source ?: return false
        if (index == CENTER_INDEX) {
            val button = findByEntryName(src, CENTER_BUTTON_ID)
                ?: src.rootView?.let { findByEntryName(it, CENTER_BUTTON_ID) }
                ?: return false
            return runCatching { button.performClick() }.getOrDefault(false)
        }
        val row = findByEntryName(src, TAB_ROW_ID) as? ViewGroup ?: return false
        val visible = (0 until row.childCount)
            .map(row::getChildAt)
            .filter { it.visibility != View.GONE }
        // 真 tab 带文字标签; 中间的占位/按钮没有, 据此排除,
        // 避免占位挤占序号造成点按错位。
        val labeled = visible.filter { hasText(it) }
        val tabs = if (labeled.size >= 4) labeled else visible
        // 槽位 0/1/3/4 依次对应文字行的四个 tab。
        val ordinal = if (index < CENTER_INDEX) index else index - 1
        val target = tabs.getOrNull(ordinal) ?: return false
        return runCatching { target.performClick() }.getOrDefault(false)
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
