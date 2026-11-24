package io.github.offlineglass.hook.adapters.heybox

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
import android.widget.RadioButton
import io.github.offlineglass.hook.GlassHostLayout
import io.github.offlineglass.hook.adapters.AdapterNavigationFrame
import io.github.offlineglass.hook.adapters.AppNavigationState
import kotlin.math.min

/**
 * 小黑盒的导航绘制状态。
 *
 * artwork 口径 (2.1.5): 与掌盟同理走逐槽叶子捕获——普通槽的
 * 图标是 RadioButton 的顶部 compound drawable (BetterHeybox
 * 构建自有底栏时即取此 drawable), 文字是按钮文本; 中间槽是
 * vg_mid_tab 内部的图标叶子。整行 source.draw() 切片对复合
 * drawable 的状态/热更新图标不可靠, 叶子捕获最忠实。
 *
 * 独立发布键圆钮: 通用路径从不往圆钮里画 artwork, 由 draw()
 * 在通用投影之后把 vg_mid_tab 的图标叶子画进圆钮; 找不到
 * 图标时手绘加号兜底, 圆钮永不空白。
 */
internal class HeyboxNavigationState(private val host: GlassHostLayout) : AppNavigationState {

    override fun captureNavigation(source: ViewGroup, target: Bitmap): Boolean =
        HeyboxArtworkCapture.capture(source, target)

    override fun draw(canvas: Canvas, frame: AdapterNavigationFrame) {
        host.drawOriginalNavigationForAdapter(canvas, frame.extraScale)
        drawPublish(canvas, frame)
    }

    private fun drawPublish(canvas: Canvas, frame: AdapterNavigationFrame) {
        val layout = frame.barLayout ?: return
        val cx = layout.actionLeft + layout.actionSize / 2f
        val cy = frame.height / 2f
        val radius = layout.actionSize / 2f
        val source = host.navigationSource ?: return
        // vg_mid_tab 移入后就是源的直接子视图, 先直查子层,
        // 免得每帧绘制都重跑按资源名的子树查找 (2.1.8)。
        val mid = (0 until source.childCount).map(source::getChildAt)
            .firstOrNull { HeyboxViews.entryName(it) == HeyboxViews.MID_TAB_ID }
            ?: HeyboxViews.findByEntryName(source, HeyboxViews.MID_TAB_ID)
            ?: source.rootView?.let { HeyboxViews.findByEntryName(it, HeyboxViews.MID_TAB_ID) }
        var drawn = false
        if (mid != null && mid.width > 0 && mid.height > 0) {
            canvas.save()
            canvas.clipPath(Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) })
            // 优先只画图标叶子 (mid 本体可能带原生圆形底板,
            // 整画会与玻璃圆钮叠出双层底); 无图标叶子才画本体。
            val icon = HeyboxViews.findIconLeaf(mid)
            drawn = if (icon != null) {
                HeyboxViews.drawLeafCentered(canvas, icon, cx, cy, radius * 1.3f)
            } else {
                HeyboxViews.drawLeafCentered(canvas, mid, cx, cy, radius * 1.84f)
            }
            canvas.restore()
        }
        if (!drawn) {
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

/** heybox 适配内共用的视图查找/叶子绘制小工具。 */
internal object HeyboxViews {
    const val BAR_ID = "rg_main"
    const val MID_TAB_ID = "vg_mid_tab"

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

    /** 深度优先找第一个带 drawable 的 ImageView (图标叶子)。 */
    fun findIconLeaf(root: View): ImageView? {
        val stack = ArrayDeque<View>()
        stack += root
        var visited = 0
        while (stack.isNotEmpty() && visited++ < 2_000) {
            val view = stack.removeLast()
            if (view is ImageView && view.drawable != null && view.width > 0 && view.height > 0) {
                return view
            }
            if (view is ViewGroup) {
                for (index in view.childCount - 1 downTo 0) stack += view.getChildAt(index)
            }
        }
        return null
    }

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

/** 逐槽叶子捕获: 见 HeyboxNavigationState 的类注释。 */
internal object HeyboxArtworkCapture {
    private const val SLOT_COUNT = 5
    private const val CENTER_INDEX = 2

    fun capture(source: ViewGroup, target: Bitmap): Boolean {
        if (HeyboxViews.entryName(source) != HeyboxViews.BAR_ID) return false
        if (source.childCount != SLOT_COUNT) return false
        val children = (0 until source.childCount).map(source::getChildAt)
        val canvas = Canvas(target)
        canvas.drawColor(Color.TRANSPARENT, BlendMode.CLEAR)
        val slotWidth = target.width / SLOT_COUNT.toFloat()
        val density = source.resources.displayMetrics.density
        var drewAny = false
        children.forEachIndexed { index, child ->
            val centerX = (index + 0.5f) * slotWidth
            val ok = if (child is RadioButton) {
                drawRadioTab(canvas, child, centerX, slotWidth, target.height, density)
            } else {
                // 中间槽 (vg_mid_tab 已被移入顶替占位): 图标叶子优先,
                // 其次本体; 两者都无则视为捕获失败回退通用路径。
                val icon = HeyboxViews.findIconLeaf(child)
                when {
                    icon != null -> HeyboxViews.drawLeafCentered(
                        canvas, icon, centerX, target.height / 2f,
                        min(slotWidth, target.height.toFloat()) * 0.8f,
                    )
                    child.width > 0 && child.height > 0 -> HeyboxViews.drawLeafCentered(
                        canvas, child, centerX, target.height / 2f,
                        min(slotWidth, target.height.toFloat()) * 0.92f,
                    )
                    else -> false
                }
            }
            if (index != CENTER_INDEX && !ok) return false
            drewAny = drewAny || ok
        }
        return drewAny
    }

    private fun drawRadioTab(
        canvas: Canvas, button: RadioButton, centerX: Float, slotWidth: Float,
        targetHeight: Int, density: Float,
    ): Boolean {
        val drawable: Drawable = button.compoundDrawables.getOrNull(1)
            ?: button.compoundDrawablesRelative.getOrNull(1)
            ?: return false
        val iconWidth = (drawable.intrinsicWidth.takeIf { it > 0 }
            ?: drawable.bounds.width()).toFloat()
        val iconHeight = (drawable.intrinsicHeight.takeIf { it > 0 }
            ?: drawable.bounds.height()).toFloat()
        if (iconWidth <= 0f || iconHeight <= 0f) return false
        val paint = TextPaint(button.paint).apply {
            color = button.currentTextColor
            textAlign = Paint.Align.CENTER
        }
        val metrics = paint.fontMetrics
        val labelHeight = metrics.descent - metrics.ascent
        val gap = 2f * density
        val total = iconHeight + gap + labelHeight
        var top = (targetHeight - total) / 2f
        val savedBounds = android.graphics.Rect(drawable.bounds)
        val savedState = drawable.state
        drawable.state = button.drawableState
        canvas.save()
        canvas.clipRect(centerX - slotWidth / 2f, 0f, centerX + slotWidth / 2f, targetHeight.toFloat())
        drawable.setBounds(
            (centerX - iconWidth / 2f).toInt(), top.toInt(),
            (centerX + iconWidth / 2f).toInt(), (top + iconHeight).toInt(),
        )
        drawable.draw(canvas)
        canvas.restore()
        drawable.bounds = savedBounds
        drawable.state = savedState
        top += iconHeight + gap
        val text = button.text?.toString().orEmpty()
        if (text.isNotEmpty()) {
            canvas.drawText(text, centerX, top - metrics.ascent, paint)
        }
        return true
    }
}
