package io.github.offlineglass.hook.adapters.heybox

import android.graphics.Insets
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import io.github.offlineglass.hook.compat.XC_MethodHook
import io.github.offlineglass.hook.compat.XposedBridge
import java.util.WeakHashMap

/**
 * 小黑盒沉浸 (2.1.5): 共享安装器已给窗口加 LAYOUT_HIDE_NAVIGATION
 * 并把导航条颜色透明化, 但宿主仍按分发的 navigationBars inset
 * 给根布局留底, 内容到不了物理底部、玻璃条下方留一条实色带。
 * 这里照本仓 CainiaoInsets 的成法, 在 decor 的 insets 分发处把
 * 导航条底部 inset 清零; 且只对含 rg_main 的主界面窗口生效
 * (其余页面可能依赖该 inset 布局——BetterHeybox 在小黑盒上
 * 亦只对主界面做全量沉浸、其余页面用轻量版, 同一口径)。
 */
internal object HeyboxInsets {
    private val windows = WeakHashMap<View, Boolean>()
    private var installed = false

    @Volatile
    private var barId = 0

    fun configure(decor: View, enabled: Boolean) {
        if (enabled) windows[decor] = true else windows.remove(decor)
        if (!enabled || installed) return
        XposedBridge.hookAllMethods(
            ViewGroup::class.java, "dispatchApplyWindowInsets",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val decorView = param.thisObject as? View ?: return
                    if (windows[decorView] != true) return
                    if (!containsMainBar(decorView)) return
                    val original = param.args.firstOrNull() as? WindowInsets ?: return
                    val type = WindowInsets.Type.navigationBars()
                    val visible = original.getInsets(type)
                    val stable = original.getInsetsIgnoringVisibility(type)
                    if (visible.bottom == 0 && stable.bottom == 0) return
                    param.args[0] = WindowInsets.Builder(original)
                        .setInsets(type, Insets.of(visible.left, visible.top, visible.right, 0))
                        .setInsetsIgnoringVisibility(
                            type, Insets.of(stable.left, stable.top, stable.right, 0),
                        )
                        .build()
                }
            },
        )
        installed = true
    }

    private fun containsMainBar(decor: View): Boolean {
        val context = decor.context ?: return false
        if (barId == 0) {
            barId = runCatching {
                context.resources.getIdentifier("rg_main", "id", context.packageName)
            }.getOrDefault(0)
        }
        if (barId == 0) return false
        return runCatching { decor.findViewById<View>(barId) != null }.getOrDefault(false)
    }
}
