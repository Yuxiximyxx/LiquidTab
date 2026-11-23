package io.github.offlineglass.hook.adapters.qtl

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import io.github.offlineglass.config.GlassConfig
import io.github.offlineglass.hook.NavigationCandidate
import io.github.offlineglass.hook.adapters.AdapterBarLayout
import io.github.offlineglass.hook.adapters.TargetAdapter
import io.github.offlineglass.targets.TargetSpec

/**
 * 掌上英雄联盟 (com.tencent.qt.qtl) 专属适配。
 *
 * 宿主结构 (来自 PalmPurify 对 12.9.0 的定位, 12.10.0 沿用资源名):
 * 首页 GameHomeActivity; 底栏容器内含文字行 `tab_title_content`
 * (LinearLayout, 子项为各 tab, 中间留发布位) 与中间加号按钮
 * `bottom_title_center_btn`。通用发现时代的问题: 选中的源不稳定、
 * 加号按钮在源外单独绘制透过半透明玻璃发糊、容器黑背景留在
 * 玻璃条后面。本适配把源钉死为底栏容器, 并在 suppress 阶段
 * 清掉整条底栏装配的原生底色。
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

    override fun findNavigation(root: View, spec: TargetSpec): NavigationCandidate? {
        val row = findByEntryName(root, TAB_ROW_ID) as? ViewGroup ?: return null
        if (!row.isShown || row.width <= 0 || row.height <= 0) return null
        // 优先取同时包含加号按钮的底栏容器作为源, 使五个槽位的
        //  artwork 都在源内; 找不到时退回文字行本身。
        var candidate: ViewGroup = row
        var parent = row.parent as? ViewGroup
        var depth = 0
        while (parent != null && depth < 3) {
            if (findByEntryName(parent, CENTER_BUTTON_ID) != null) {
                candidate = parent
                break
            }
            parent = parent.parent as? ViewGroup
            depth++
        }
        return NavigationCandidate(candidate, SLOT_COUNT, 1_000)
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
        // 容器, 否则它会在玻璃条后面单独绘制透出来。
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
        clearChrome(navigation)
        findByEntryName(navigation, TAB_ROW_ID)?.let { clearChrome(it) }
        findByEntryName(navigation, CENTER_BUTTON_ID)?.let { clearChrome(it) }
        // 底栏容器往上的壳层 (高度不大的底部容器) 一并清掉底色,
        // 避免玻璃条后面留一条宿主黑带。
        var parent = navigation.parent as? View
        var depth = 0
        val density = navigation.resources.displayMetrics.density
        while (parent != null && depth < 3) {
            if (parent.height in 1..(196f * density).toInt()) clearChrome(parent)
            parent = parent.parent as? View
            depth++
        }
        extendContentUnderBar(navigation)
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

    /** 内容若被 RelativeLayout 的 ABOVE 规则顶在底栏上方, 放开规则让内容铺到屏幕底部 (华为健康适配同款处理)。 */
    private fun extendContentUnderBar(navigation: ViewGroup) {
        runCatching {
            val parent = navigation.parent as? ViewGroup ?: return
            if (parent !is RelativeLayout || navigation.id == View.NO_ID) return
            for (index in 0 until parent.childCount) {
                val sibling = parent.getChildAt(index)
                if (sibling === navigation) continue
                val params = sibling.layoutParams as? RelativeLayout.LayoutParams ?: continue
                if (params.rules[RelativeLayout.ABOVE] == navigation.id) {
                    params.removeRule(RelativeLayout.ABOVE)
                    sibling.layoutParams = params
                }
            }
        }
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
