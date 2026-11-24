package io.github.offlineglass.hook.adapters.qtl

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import io.github.offlineglass.hook.GlassHostLayout
import io.github.offlineglass.hook.adapters.AdapterNavigationFrame
import io.github.offlineglass.hook.adapters.AppNavigationState
import kotlin.math.min

/**
 * 掌盟的导航绘制状态。
 *
 * artwork 口径 (2.1.5): 共享管线把源位图按宽度等分切片投影到
 * 槽位, 源里任何大视图都会被缩进槽位 (2.1.4 槽位里的整页微缩
 * 图即此机制)。故快照改走逐 tab 叶子捕获: 每个 tab 只取它内部
 * 真实的图标 (ImageView 或 TextView 顶部 compound drawable) 与
 * 文字, 按原生尺寸摆进对应槽位; 中间槽画加号按钮本体。
 *
 * 独立发布键圆钮同理: 通用路径从不往圆钮里画 artwork
 * (enabledNavigationIndices 只含四个页面槽), 由 draw() 在通用
 * 投影之后把原生加号按钮本体画进圆钮, 找不到时手绘加号兜底
 * (贴吧同款), 保证圆钮永不空白。
 */
internal class QtlNavigationState(private val host: GlassHostLayout) : AppNavigationState {

    override fun captureNavigation(source: ViewGroup, target: Bitmap): Boolean =
        QtlArtworkCapture.capture(source, target)

    /**
     * 选中槽位以宿主真实选中状态为准 (2.1.7)。
     *
     * 宿主通用判定 (GlassHostLayout.resolveSelectedFromViewState)
     * 按「槽位容器第 i 个子视图」打分: 文字行里只有四个页面
     * tab (加号按钮不在行内), 于是第 4 个 tab (我的, 子下标 3)
     * 被直接当成槽位 3 (商城), 第 3 个 tab (商城, 子下标 2)
     * 又恰与发布槽同号被跳过——真机表现为切到「我的」后高亮
     * 自己滑到「商城」, 且判定反复纠正引发切换卡顿。这里按与
     * 捕获/点按完全相同的口径枚举四个 tab, 逐个查其子树的真实
     * 选中信号 (isSelected / isActivated / drawable 的
     * state_selected), 再把 tab 序号映射回槽位序号 (发布槽
     * 之后 +1); 查不到明确选中态时返回 null, 交回宿主判定。
     */
    override fun resolveSelectedIndex(source: ViewGroup?, slotCount: Int): Int? {
        val src = source ?: return null
        if (slotCount != QtlViews.SLOT_COUNT) return null
        // 首选 (2.1.8): 捕获时记录的身份绑定 (槽位 → tab 本体),
        // 与 artwork/点按同源。本判定被宿主在每个 dispatchDraw
        // 帧调用, 旧实现每帧重跑「按资源名找文字行 + 四个 tab
        // 子树全量打分 (含逐视图 drawableState 数组分配)」, 是
        // 切换卡顿的另一半来源; 现只读绑定里的四个 tab 本体。
        val bound = QtlSlotBindings.lookup(src)
        if (bound != null) {
            val tabs = ArrayList<View>(QtlViews.SLOT_COUNT - 1)
            var complete = true
            for (slot in intArrayOf(0, 1, 3, 4)) {
                val view = bound[slot]
                if (view == null || !view.isAttachedToWindow) {
                    complete = false
                    break
                }
                tabs += view
            }
            if (complete) return scoreTabs(tabs)
        }
        return fallbackResolve(src)
    }

    /** 绑定缺失/失效时的旧口径枚举 (捕获前或源重建时)。 */
    private fun fallbackResolve(src: ViewGroup): Int? {
        val row = QtlViews.findByEntryName(src, QtlViews.TAB_ROW_ID) as? ViewGroup
            ?: return null
        val tabs = (0 until row.childCount).map(row::getChildAt)
            .filter { it.visibility != View.GONE && QtlViews.hasText(it) }
        if (tabs.size != QtlViews.SLOT_COUNT - 1) return null
        return scoreTabs(tabs)
    }

    private fun scoreTabs(tabs: List<View>): Int? {
        val scores = IntArray(tabs.size)
        var anyStrong = false
        tabs.forEachIndexed { ordinal, tab ->
            scores[ordinal] = strongScore(tab)
            if (scores[ordinal] > 0) anyStrong = true
        }
        if (!anyStrong) {
            // 没有强信号时才退到 drawable 状态打分: drawableState
            // 每视图都新分配一个状态数组, 只在必要时才付这笔钱。
            tabs.forEachIndexed { ordinal, tab ->
                scores[ordinal] = drawableScore(tab)
            }
        }
        var bestOrdinal = -1
        var bestScore = 0
        scores.forEachIndexed { ordinal, score ->
            if (score > bestScore) {
                bestScore = score
                bestOrdinal = ordinal
            }
        }
        if (bestOrdinal < 0) return null
        return if (bestOrdinal < QtlViews.CENTER_INDEX) bestOrdinal else bestOrdinal + 1
    }

    /** 强信号: isSelected 子树内命中即停; isActivated 次之。 */
    private fun strongScore(root: View): Int {
        var score = 0
        val stack = ArrayDeque<Pair<View, Int>>()
        stack += root to 0
        while (stack.isNotEmpty()) {
            val (view, depth) = stack.removeLast()
            if (view.isShown) {
                if (view.isSelected) return 100
                if (view.isActivated) score = maxOf(score, 80)
            }
            if (view is ViewGroup && depth < 6) {
                for (index in 0 until view.childCount) {
                    stack += view.getChildAt(index) to (depth + 1)
                }
            }
        }
        return score
    }

    private fun drawableScore(root: View): Int {
        var score = 0
        val stack = ArrayDeque<Pair<View, Int>>()
        stack += root to 0
        while (stack.isNotEmpty()) {
            val (view, depth) = stack.removeLast()
            if (view.isShown &&
                view.drawableState.contains(android.R.attr.state_selected)
            ) score += 60
            if (view is ViewGroup && depth < 6) {
                for (index in 0 until view.childCount) {
                    stack += view.getChildAt(index) to (depth + 1)
                }
            }
        }
        return score
    }

    override fun draw(canvas: Canvas, frame: AdapterNavigationFrame) {
        host.drawOriginalNavigationForAdapter(canvas, frame.extraScale)
        drawPublish(canvas, frame)
    }

    private fun drawPublish(canvas: Canvas, frame: AdapterNavigationFrame) {
        val layout = frame.barLayout ?: return
        val cx = layout.actionLeft + layout.actionSize / 2f
        val cy = frame.height / 2f
        val radius = layout.actionSize / 2f
        // 圆钮本体优先取身份绑定 (捕获时记录), 免得每帧绘制
        // 都重跑按资源名的子树/全窗查找 (2.1.8)。
        val button = QtlSlotBindings.lookup(host.navigationSource)
            ?.get(QtlViews.CENTER_INDEX)
            ?: host.navigationSource?.let { QtlViews.findCenterButton(it) }
        var drawn = false
        if (button != null && button.width > 0 && button.height > 0) {
            canvas.save()
            canvas.clipPath(Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) })
            drawn = QtlViews.drawLeafCentered(
                canvas, button, cx, cy, radius * 1.84f,
            )
            canvas.restore()
        }
        if (!drawn) {
            // 兜底手绘加号, 圆钮永不空白。
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeWidth = 2.2f * frame.density
                color = if (frame.darkGlass) Color.WHITE else Color.rgb(40, 40, 40)
            }
            val arm = 7f * frame.density
            canvas.drawLine(cx - arm, cy, cx + arm, cy, paint)
            canvas.drawLine(cx, cy - arm, cx, cy + arm, paint)
        }
    }
}

/** qtl 适配内共用的视图查找/叶子绘制小工具。 */
internal object QtlViews {
    const val TAB_ROW_ID = "tab_title_content"
    const val CENTER_BUTTON_ID = "bottom_title_center_btn"
    const val SLOT_COUNT = 5
    const val CENTER_INDEX = 2

    fun hasText(view: View): Boolean {
        if (view is TextView) return !view.text.isNullOrBlank()
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (hasText(view.getChildAt(index))) return true
            }
        }
        return false
    }

    fun entryName(view: View): String? = runCatching {
        if (view.id == View.NO_ID) null else view.resources.getResourceEntryName(view.id)
    }.getOrNull()

    fun findByEntryName(root: View, name: String): View? {
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

    fun findCenterButton(source: ViewGroup): View? =
        findByEntryName(source, CENTER_BUTTON_ID)
            ?: source.rootView?.let { findByEntryName(it, CENTER_BUTTON_ID) }

    /**
     * 把叶子视图按自身尺寸 (必要时等比缩小到 maxSize) 画到
     * (cx, cy) 中心。返回是否真的画出内容。
     */
    fun drawLeafCentered(canvas: Canvas, view: View, cx: Float, cy: Float, maxSize: Float): Boolean {
        if (view.width <= 0 || view.height <= 0) return false
        val scale = min(1f, maxSize / maxOf(view.width, view.height).toFloat())
        val savedAlpha = view.alpha
        canvas.save()
        canvas.translate(cx - view.width * scale / 2f, cy - view.height * scale / 2f)
        canvas.scale(scale, scale)
        // 叶子自身 alpha 多为 1; 宿主把源 alpha 置 0 不影响叶子
        // 本地绘制。极少数叶子 alpha 近 0 时临时置 1 再恢复。
        if (savedAlpha < 0.01f) view.alpha = 1f
        runCatching { view.draw(canvas) }
        if (savedAlpha < 0.01f) view.alpha = savedAlpha
        canvas.restore()
        return true
    }
}

/**
 * 槽位 → 真实视图的身份绑定 (2.1.6)。
 *
 * 点按错位的教训: 适配器旧 dispatch 按下标算术 (槽位序号减一)
 * 在运行时重新枚举文字行子视图, 一旦 performClick 在 tab 单元
 * 上落空 (单元自身无监听、点击由行级触摸处理), 宿主会落回通用
 * 兜底路径——它按槽位下标直接取文字行第 index 个孩子。文字行
 * 里只有四个页面 tab (加号按钮在更高层), 于是发布槽之后整体
 * 偏一位: 槽 3 取到第四个 tab (我的), 槽 4 越界无反应 (真机
 * 实证)。本绑定在捕获 (artwork 与用户所见槽位同源) 时把每个
 * 槽位绑到它捕获到的那个 tab 视图本体, dispatch 只认绑定、
 * 不再算下标。
 */
internal object QtlSlotBindings {
    private val bindings = java.util.WeakHashMap<ViewGroup, Map<Int, View>>()

    fun record(source: ViewGroup, slots: Map<Int, View>) {
        synchronized(bindings) { bindings[source] = slots }
    }

    fun lookup(source: ViewGroup?): Map<Int, View>? {
        if (source == null) return null
        return synchronized(bindings) { bindings[source] }
    }
}

/** 逐 tab 叶子捕获: 见 QtlNavigationState 的类注释。 */
internal object QtlArtworkCapture {
    private const val SLOT_COUNT = 5
    private const val CENTER_INDEX = 2

    private class TabArt(
        val iconView: ImageView?,
        val iconDrawable: Drawable?,
        val stateView: View?,
        val label: TextView?,
    )

    fun capture(source: ViewGroup, target: Bitmap): Boolean {
        val row = QtlViews.findByEntryName(source, QtlViews.TAB_ROW_ID) as? ViewGroup
            ?: return false
        val tabs = (0 until row.childCount).map(row::getChildAt)
            .filter { it.visibility != View.GONE && hasText(it) }
        if (tabs.size != SLOT_COUNT - 1) return false
        val button = QtlViews.findCenterButton(source) ?: return false
        if (button.width <= 0 || button.height <= 0) return false
        val arts = tabs.map(::extractTabArt)
        if (arts.any { it.label == null && it.iconView == null && it.iconDrawable == null }) {
            return false
        }
        // 身份绑定: 槽位 → 本次捕获所用的 tab 视图本体 (中间槽
        // 绑加号按钮本体), 供 dispatchNavigationTap 按身份转发。
        val bound = HashMap<Int, View>(SLOT_COUNT)
        tabs.forEachIndexed { ordinal, tab ->
            bound[if (ordinal < CENTER_INDEX) ordinal else ordinal + 1] = tab
        }
        bound[CENTER_INDEX] = button
        QtlSlotBindings.record(source, bound)
        val canvas = Canvas(target)
        canvas.drawColor(Color.TRANSPARENT, BlendMode.CLEAR)
        val slotWidth = target.width / SLOT_COUNT.toFloat()
        val density = source.resources.displayMetrics.density
        arts.forEachIndexed { ordinal, art ->
            val slot = if (ordinal < CENTER_INDEX) ordinal else ordinal + 1
            drawTab(canvas, art, (slot + 0.5f) * slotWidth, slotWidth, target.height, density)
        }
        QtlViews.drawLeafCentered(
            canvas, button, (CENTER_INDEX + 0.5f) * slotWidth, target.height / 2f,
            min(slotWidth, target.height.toFloat()) * 0.92f,
        )
        return true
    }

    private fun extractTabArt(unit: View): TabArt {
        var label: TextView? = null
        var iconView: ImageView? = null
        var iconDrawable: Drawable? = null
        var stateView: View? = null
        if (unit is TextView) {
            if (!unit.text.isNullOrBlank()) label = unit
            val top = unit.compoundDrawables.getOrNull(1)
                ?: unit.compoundDrawablesRelative.getOrNull(1)
            if (top != null) {
                iconDrawable = top
                stateView = unit
            }
        }
        // 深度优先找第一个带 drawable 的 ImageView 作图标叶子。
        if (iconView == null && iconDrawable == null) {
            val stack = ArrayDeque<View>()
            stack += unit
            while (stack.isNotEmpty()) {
                val view = stack.removeLast()
                if (view is ImageView && view.drawable != null && view.width > 0) {
                    iconView = view
                    break
                }
                if (view is ViewGroup) {
                    for (index in view.childCount - 1 downTo 0) stack += view.getChildAt(index)
                }
            }
        }
        if (label == null) {
            val stack = ArrayDeque<View>()
            stack += unit
            while (stack.isNotEmpty()) {
                val view = stack.removeLast()
                if (view is TextView && !view.text.isNullOrBlank()) {
                    label = view
                    break
                }
                if (view is ViewGroup) {
                    for (index in view.childCount - 1 downTo 0) stack += view.getChildAt(index)
                }
            }
        }
        return TabArt(iconView, iconDrawable, stateView, label)
    }

    private fun drawTab(
        canvas: Canvas, art: TabArt, centerX: Float, slotWidth: Float,
        targetHeight: Int, density: Float,
    ) {
        val iconHeight = when {
            art.iconView != null -> art.iconView.height.toFloat()
            art.iconDrawable != null -> (art.iconDrawable.intrinsicHeight.takeIf { it > 0 }
                ?: art.iconDrawable.bounds.height()).toFloat()
            else -> 0f
        }
        val label = art.label
        val paint = label?.let {
            TextPaint(it.paint).apply {
                color = it.currentTextColor
                textAlign = Paint.Align.CENTER
            }
        }
        val metrics = paint?.fontMetrics
        val labelHeight = if (paint != null && metrics != null) metrics.descent - metrics.ascent else 0f
        val gap = if (iconHeight > 0f && labelHeight > 0f) 2f * density else 0f
        val total = iconHeight + gap + labelHeight
        var top = (targetHeight - total) / 2f
        // 图标
        when {
            art.iconView != null -> {
                QtlViews.drawLeafCentered(
                    canvas, art.iconView, centerX, top + iconHeight / 2f, slotWidth * 0.92f,
                )
            }
            art.iconDrawable != null -> {
                val drawable = art.iconDrawable
                val width = (drawable.intrinsicWidth.takeIf { it > 0 }
                    ?: drawable.bounds.width()).toFloat()
                val savedBounds = android.graphics.Rect(drawable.bounds)
                val savedState = drawable.state
                if (art.stateView != null) drawable.state = art.stateView.drawableState
                canvas.save()
                canvas.clipRect(centerX - slotWidth / 2f, 0f, centerX + slotWidth / 2f, targetHeight.toFloat())
                drawable.setBounds(
                    (centerX - width / 2f).toInt(), top.toInt(),
                    (centerX + width / 2f).toInt(), (top + iconHeight).toInt(),
                )
                drawable.draw(canvas)
                canvas.restore()
                drawable.bounds = savedBounds
                drawable.state = savedState
            }
        }
        top += iconHeight + gap
        // 文字: 只画文本本身, 不画 TextView 的背景/复合图。
        if (label != null && paint != null && metrics != null) {
            val text = label.text?.toString().orEmpty()
            if (text.isNotEmpty()) {
                canvas.drawText(text, centerX, top - metrics.ascent, paint)
            }
        }
    }

    private fun hasText(view: View): Boolean {
        if (view is TextView) return !view.text.isNullOrBlank()
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (hasText(view.getChildAt(index))) return true
            }
        }
        return false
    }

}
