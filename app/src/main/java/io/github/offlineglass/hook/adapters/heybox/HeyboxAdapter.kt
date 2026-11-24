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
 */
internal object HeyboxAdapter : TargetAdapter {
    override val key = "heybox"

    private const val BAR_ID = "rg_main"
    private const val MID_TAB_ID = "vg_mid_tab"
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

    override fun suppressNativeBottomChrome(navigation: ViewGroup) {
        stripChrome(navigation)
        navigation.post { runCatching { stripChrome(navigation) } }
    }

    override fun suppressNativeBottomChromeOnRebind(navigation: ViewGroup) {
        // 底栏热更新可能把 vg_mid_tab 重新挂回根布局, 重绑时复查。
        integrateMidTab(navigation)
        stripChrome(navigation)
    }

    private fun stripChrome(navigation: ViewGroup) {
        clearChrome(navigation)
        extendContentUnderBar(navigation)
        // 根布局里与底栏区域重叠的兄弟视图 (阴影层等) 一并清掉
        // 底色; 内容与底栏本体不在此列。
        runCatching {
            val parent = navigation.parent as? ViewGroup ?: return
            val barRect = android.graphics.Rect()
            navigation.getGlobalVisibleRect(barRect)
            for (index in 0 until parent.childCount) {
                val sibling = parent.getChildAt(index)
                if (sibling === navigation || entryName(sibling) == MID_TAB_ID) continue
                if (entryName(sibling) == "fl_container" || entryName(sibling) == "vg_tips" ||
                    entryName(sibling) == "vg_fullscreen_video_container"
                ) continue
                val rect = android.graphics.Rect()
                // 只清与底栏同高的薄层 (阴影/分隔层), 不碰内容级视图。
                if (sibling.getGlobalVisibleRect(rect) && rect.height() <= barRect.height() * 2 &&
                    android.graphics.Rect.intersects(rect, barRect)
                ) {
                    clearChrome(sibling)
                }
            }
        }
    }

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

    /** 放开 fl_container 的 ABOVE 规则, 让内容铺到屏幕底部、玻璃条直接浮在内容上 (华为健康适配同款处理)。 */
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
