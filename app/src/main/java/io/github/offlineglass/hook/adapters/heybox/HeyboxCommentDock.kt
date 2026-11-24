package io.github.offlineglass.hook.adapters.heybox

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import io.github.offlineglass.hook.compat.XC_MethodHook
import io.github.offlineglass.hook.compat.XposedBridge
import java.util.Collections
import java.util.WeakHashMap

/**
 * 评论输入框与键盘贴合 (2.1.7 恢复)。
 *
 * 原修复是 BetterHeybox 的 fork 定制 CommentInputDockHook
 * (d713f09 引入, 5858c1f 定版为「只铺背景」): 小黑盒评论页的
 * 回复输入框是带边距的悬浮圆角卡片, 键盘弹起时它浮在键盘上
 * 方、与输入法面板明显割裂 (卡片下留一条异色面板带)。修法
 * 不动任何布局参数 (v0.10.0 清边距曾把输入框文字顶到贴边
 * 裁切, 已废): 键盘弹起且页面里有评论输入框 (hint 含「来说
 * 点什么」) 时, 找到输入框的卡片容器 (首个带背景的祖先),
 * 取它的底色, 把卡片外层至多两层非全屏容器铺成同一颜色,
 * 让卡片沉进整块面板、视觉上与键盘连成一体。
 *
 * 工具箱 v1.10.0 摘除小黑盒底栏/玻璃功能时这份修复被连带
 * 丢失, 本版按原行为移植进 heybox 适配 (只搬行为, 不搬
 * BetterHeybox 的玻璃绘制): 经 installHooks 挂 Activity
 * .onResume, 对每个 Activity 的 decor 注册一次全局布局监听,
 * 节流 700ms 扫描一次。
 */
internal object HeyboxCommentDock {
    private val watched: MutableSet<Activity> =
        Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())
    private val lastScan: MutableMap<Activity, Long> =
        Collections.synchronizedMap(WeakHashMap<Activity, Long>())
    private val styled: MutableSet<View> =
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())

    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        runCatching {
            XposedBridge.hookAllMethods(
                Activity::class.java, "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as? Activity ?: return
                        runCatching { watch(activity) }
                    }
                },
            )
        }
    }

    private fun watch(activity: Activity) {
        if (!watched.add(activity)) return
        val decor = activity.window?.decorView ?: return
        decor.viewTreeObserver.addOnGlobalLayoutListener(
            ViewTreeObserver.OnGlobalLayoutListener {
                runCatching { onLayout(activity, decor) }
            },
        )
    }

    private fun onLayout(activity: Activity, decor: View) {
        val now = System.currentTimeMillis()
        if (now - (lastScan[activity] ?: 0L) < 700L) return
        val frame = Rect()
        decor.getWindowVisibleDisplayFrame(frame)
        val keyboardHeight = decor.height - frame.bottom
        if (keyboardHeight < decor.height * 0.15) return
        lastScan[activity] = now
        val input = findCommentInput(decor) ?: return
        if (!styled.contains(input)) {
            dock(input)
        }
    }

    private fun findCommentInput(root: View): EditText? {
        val stack = ArrayDeque<View>()
        stack.addLast(root)
        var guard = 0
        while (stack.isNotEmpty() && guard++ < 800) {
            val v = stack.removeLast()
            if (v is EditText) {
                val hint = v.hint?.toString().orEmpty()
                if (hint.contains("来说点什么")) return v
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) stack.addLast(v.getChildAt(i))
            }
        }
        return null
    }

    private fun dock(input: EditText) {
        // 只铺背景, 不动布局: 从输入框向上找首个带背景的祖先作
        // 卡片, 取其底色, 再给卡片外层至多两层 (限非全屏高度)
        // 容器铺同色, 让卡片沉进整块面板。
        var card: View? = null
        var current: View? = input
        var depth = 0
        while (current != null && depth++ < 8) {
            val parent = current.parent as? View ?: break
            if (parent.background != null) {
                card = parent
                break
            }
            current = parent
        }
        val target = card ?: (input.parent as? View) ?: return
        val color = backgroundColor(target.background, target)
        val rootHeight = input.rootView?.height ?: Int.MAX_VALUE
        var level: View? = target.parent as? View
        var painted = 0
        while (level != null && painted < 2) {
            if (level.height > rootHeight * 0.5) break
            level.background = ColorDrawable(color)
            level = level.parent as? View
            painted++
        }
        styled.add(input)
    }

    private fun backgroundColor(drawable: Drawable?, anchor: View): Int {
        if (drawable is ColorDrawable) return drawable.color
        val night = (anchor.context.resources.configuration.uiMode
            and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return if (night) Color.rgb(0x24, 0x24, 0x26) else Color.WHITE
    }
}
