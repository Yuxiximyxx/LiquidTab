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
import io.github.offlineglass.hook.adapters.NavigationCaptureLabels
import io.github.offlineglass.hook.compat.XposedBridge
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
 *
 * 2.1.9 (商城页变样): 真机截图逐像素核对定案——切到商城页
 * 后整条变黑底、商城槽位是一个纯平蓝椭圆、其余槽位发暗。
 * 蓝形的尺寸与首页选中药丸逐像素相同 (151×120px), 但它不
 * 是药丸被染蓝: 商城页那张快照根本不是逐叶捕获的产物, 而
 * 是宿主通用兜底对源的整树绘制 (黑底矩形带方形直角、内嵌
 * 在圆角玻璃面板轮廓之内, 是位图矩形的边缘)。逐叶捕获先把
 * 目标清成全透明再逐叶绘制, 画不出整幅黑底; 只有通用兜底
 * (captureNavigation 返回 false 时宿主走 source.draw()) 能
 * 产生这种位图。商城单元在该位图里只剩一个蓝椭圆: 该页的
 * 商城单元在软件绘制下拿不到图标叶子/文字 (只剩选中态的
 * 椭圆背景视图), 旧捕获的「任一 tab 素材全空即整次失败」
 * 正是触发点 (或行枚举按 hasText 过滤把无文字的商城单元
 * 排除、tab 数凑不齐四)。药丸看着变蓝, 是药丸透镜采样了
 * 这张位图里商城槽位的蓝椭圆。本版三层修:
 * ①捕获加固 (见 QtlArtworkCapture): 行枚举改按行子视图
 * 普查 (五孩子时剔除中间发布占位), 素材全空只让该槽留空、
 * 不再整次失败; 图标叶子跳过填满单元的背景型 ImageView,
 * 图标布局/绘制尺寸钳制, 文字永远有位置;
 * ②已有好快照时, 普查不完整就推迟捕获 (inspectNavigation-
 * CaptureLabels), 保住上一张完整快照 (商城槽位继续显示
 * 首页捕获到的正常图标文字), 行结构恢复后自然重捕获;
 * ③staged 捕获 + 提交前校验 (QtlAdapter.stagedNavigation-
 * Snapshot 与下方 acceptAndCommitNavigationSnapshot):
 * 逐叶捕获的位图大面积透明, 通用兜底位图几乎全不透明,
 * 不透明占比超阈值直接拒收, 黑底位图永远上不了屏。
 *
 * 2.1.10 (活动页条身黑底): 2.1.9 真机复验——商城槽位与
 * 药丸已正常, 但「发现 → 活动」子页上整条仍是近不透明
 * 黑底。逐环核对代码 + 截图裁图取证后的判定链:
 * ①本页的黑不在 artwork 链: 截图里图标/文字/药丸都是逐叶
 * 快照的样子, 说明逐叶快照已被正常提交 (若是通用兜底位图
 * 上屏, 商城槽不会是正常图标; 若好快照被 staged 拒收, 在
 * 屏的仍是上一张逐叶快照)。②条身由宿主 drawGlassPanel 绘
 * 制: drawOuterGlass 画实时背景场景 (liveBackdropNode),
 * 再叠 surfaceContainerColor 的半透明填充。实时场景的录制
 * 是「先铺一层场景底色, 再回放宿主父层下各兄弟视图的
 * RenderNode」; 底色走 liveSceneBackgroundColor——逐层找
 * 不透明 ColorDrawable 背景, 找不到落主题 colorBackground,
 * 再落 isDarkGlass 时的纯黑。场景内容覆盖不到的区域, 条身
 * 就只剩这层不透明底色 + 半透明填充 = 近不透明黑。③截图
 * 裁图实证与此吻合: 条身上部约 1/3 能折射到紧邻上方的卡片
 * 内容 (透镜边缘折射), 下部约 2/3 是平黑、与身后卡片下半
 * 的图文无关——即该子页上下文里场景回放的内容覆盖不到条
 * 身下部 (该页内容为何覆盖不到: 疑为合成层回放不可靠的
 * 页面构成, 待本版日志证实, 见下方 backdrop 状态日志)。
 * 修法 (见 resolveBackdropScene): qtl 主动供给背景场景
 * (宿主父层下包含导航源的应用根布局), 宿主随即改用适配器
 * 的 backdropSceneBaseColor 接口默认 (全透明) 作场景底色,
 * 不再走 liveSceneBackgroundColor 的不透明黑; 场景内容覆
 * 盖不到之处条身保持透明玻璃 (只剩半透明填充), 最坏情况
 * 也只是「只画图标文字的清玻璃」, 永不落回不透明黑。
 * 同时补两条定案日志: 快照提交分类 (snapshot commit) 与
 * 背景状态变化 (backdrop), 真机若仍黑, 导 LSPosed 日志
 * 看这两行即可定案。
 */
internal class QtlNavigationState(private val host: GlassHostLayout) : AppNavigationState {

    private var lastRejectLogAt = 0L

    // ---- 2.1.10 定案日志状态 ----
    /** 最近一次 captureNavigation 是否逐叶成功 (false = 宿主走了通用兜底)。 */
    private var lastCaptureWasLeaf: Boolean? = null
    /** 是否已有任一快照成功提交过 (区分拒收时是保旧图还是无图可用)。 */
    private var everCommittedSnapshot = false
    private var lastCommitLogKind: String? = null
    private var lastCommitLogAt = 0L
    private var lastBackdropLogKey: String? = null
    /** resolveBackdropScene 的缓存 (校验失效才重找)。 */
    private var cachedScene: View? = null

    private companion object {
        /** 普查不完整时推迟捕获的间隔。 */
        const val CAPTURE_RETRY_DELAY_MS = 300L
        /** staged 校验: 不透明占比超此值视为通用兜底位图, 拒收。 */
        const val MAX_OPAQUE_FRACTION = 0.45f
        /** 拒收日志节流间隔 (拒收会随探针重试, 避免刷屏)。 */
        const val REJECT_LOG_INTERVAL_MS = 5_000L
        /** 提交分类日志: 同类结果最短间隔 (分类变化立即打)。 */
        const val COMMIT_LOG_INTERVAL_MS = 30_000L
    }

    override fun captureNavigation(source: ViewGroup, target: Bitmap): Boolean {
        val leaf = QtlArtworkCapture.capture(source, target)
        lastCaptureWasLeaf = leaf
        return leaf
    }

    /** 当前页上下文标识 (宿主 context 即 Activity), 供定案日志定位子页。 */
    private fun contextLabel(): String = runCatching {
        host.context.javaClass.simpleName.ifBlank { "?" }
    }.getOrDefault("?")

    /**
     * 主动供给背景场景 (2.1.10, 黑底落点)。
     *
     * 返回宿主父层下「包含导航源」的应用根布局: 默认清扫本
     * 来就以它为主要兄弟回放, 采样集合不变; 真正的变化在场
     * 景底色——宿主只对适配器供给的场景调用
     * backdropSceneBaseColor (接口默认全透明), 默认清扫则走
     * liveSceneBackgroundColor (夜间落不透明黑)。本适配不覆
     * 写 backdropSceneBaseColor: 场景内容覆盖不到条身时,
     * 条身保持透明玻璃, 不再被不透明黑底色填满。找不到合格
     * 兄弟时返回 null, 退回宿主默认清扫 (行为同旧版)。
     */
    override fun resolveBackdropScene(hostView: View): View? {
        val parent = hostView.parent as? ViewGroup ?: return null
        val source = host.navigationSource ?: return null
        cachedScene?.let { scene ->
            if (scene.parent === parent && scene.isAttachedToWindow &&
                QtlViews.isDescendant(source, scene)
            ) return scene
            cachedScene = null
        }
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index) ?: continue
            if (child === hostView) continue
            if (QtlViews.isDescendant(source, child)) {
                cachedScene = child
                return child
            }
        }
        return null
    }

    /**
     * 背景状态变化日志 (2.1.10 定案用)。
     *
     * onHostFrame 由宿主在每个 pre-draw 帧调用, 这里只做 O(1)
     * 的状态键比较; 状态变化时才打一行并做一次有界 WebView
     * 探查 (活动子页疑为合成层页面, 该位是关键证据)。真机
     * 若仍黑: 看黑底出现时段内本行 live/displayList/scene/
     * webView 的取值即可判定背景链断在哪一环。
     */
    override fun onHostFrame(hostView: View) {
        val live = host.liveBackdropActiveForAdapter
        val displayList = host.liveBackdropNodeForAdapter.hasDisplayList()
        val scene = resolveBackdropScene(hostView) != null
        val key = "$live/$displayList/$scene"
        if (key == lastBackdropLogKey) return
        lastBackdropLogKey = key
        val webView = cachedScene?.let { containsWebView(it) }
        XposedBridge.log(
            "[OfflineGlass][QTL] backdrop: live=$live displayList=$displayList " +
                "scene=$scene webView=$webView ctx=${contextLabel()}",
        )
    }

    /** 有界探查子树里是否有可见 WebView (仅状态变化时跑, 上限 800 个视图)。 */
    private fun containsWebView(root: View): Boolean {
        val stack = ArrayDeque<View>()
        stack += root
        var visited = 0
        while (stack.isNotEmpty() && visited++ < 800) {
            val view = stack.removeLast()
            if (view is android.webkit.WebView && view.isShown &&
                view.width > 0 && view.height > 0
            ) return true
            if (view is ViewGroup) {
                for (index in view.childCount - 1 downTo 0) stack += view.getChildAt(index)
            }
        }
        return false
    }

    /**
     * 已有可用快照时, 若当前文字行普查不完整 (某个 tab 单元
     * 既提取不到图标叶子也提取不到文字——商城页的商城单元
     * 即此形态), 推迟本次捕获, 保住上一张完整快照; 行结构
     * 恢复完整后签名机制会自然重捕获。尚无快照时不推迟,
     * 让首捕获先行 (质量由 staged 提交校验兜底)。
     */
    override fun inspectNavigationCaptureLabels(
        source: ViewGroup, hasSnapshot: Boolean,
    ): NavigationCaptureLabels? {
        if (!hasSnapshot) return null
        val tabs = QtlArtworkCapture.censusTabs(source) ?: return null
        val incomplete = tabs.any { !QtlArtworkCapture.hasAnyArt(it) }
        return if (incomplete) {
            NavigationCaptureLabels(emptyList(), CAPTURE_RETRY_DELAY_MS)
        } else null
    }

    /**
     * staged 提交校验 (2.1.9): 逐叶捕获的位图只有图标/文字/
     * 加号按钮本体, 大面积透明 (实测占比约三成以内); 通用
     * 兜底位图带宿主原底栏的整幅底色, 几乎全不透明。不透明
     * 占比超阈值即拒收 (返回 false 后宿主不提交签名、保留
     * 在屏的上一张快照, 后续探针会重试)。发布由宿主的
     * staged 交换块完成, 这里不调 commit (JD 式 commit 会让
     * scratch 与在屏位图同体, 反而破坏暂存隔离)。
     */
    override fun acceptAndCommitNavigationSnapshot(
        source: ViewGroup?, target: Bitmap, selectedIndex: Int, slotCount: Int,
        commit: (Bitmap) -> Unit,
    ): Boolean {
        val fraction = opaqueFraction(target)
        if (fraction > MAX_OPAQUE_FRACTION) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastRejectLogAt > REJECT_LOG_INTERVAL_MS) {
                lastRejectLogAt = now
                XposedBridge.log(
                    "[OfflineGlass][QTL] rejected generic snapshot: " +
                        "opaque fraction=$fraction",
                )
            }
            // 提交分类 (2.1.10): 从未提交成功过又拒收 = 在屏无
            // 快照 (empty-fallback); 已有旧图时拒收只是保旧图。
            logCommit(
                if (everCommittedSnapshot) "rejected-opaque" else "empty-fallback",
                fraction, selectedIndex,
            )
            return false
        }
        val kind = when (lastCaptureWasLeaf) {
            true -> "committed-leaf"
            false -> "committed-generic"
            null -> "committed-unknown"
        }
        everCommittedSnapshot = true
        logCommit(kind, fraction, selectedIndex)
        return true
    }

    /**
     * 快照提交分类日志 (2.1.10 定案用): 分类变化立即打,
     * 同类 30 秒节流。每行带不透明占比、当前页上下文与选中
     * 槽位; 真机复现黑底时, 对照同一时段的 backdrop 行即可
     * 分清黑来自 artwork 链还是条身背景链。
     */
    private fun logCommit(kind: String, fraction: Float, selectedIndex: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        if (kind == lastCommitLogKind && now - lastCommitLogAt < COMMIT_LOG_INTERVAL_MS) return
        lastCommitLogKind = kind
        lastCommitLogAt = now
        XposedBridge.log(
            "[OfflineGlass][QTL] snapshot commit: result=$kind " +
                "opaque=${(fraction * 1000).toInt() / 1000f} sel=$selectedIndex " +
                "everCommitted=$everCommittedSnapshot ctx=${contextLabel()}",
        )
    }

    /** 位图不透明像素占比 (步进 4 采样, alpha ≥ 128 计不透明)。 */
    private fun opaqueFraction(bitmap: Bitmap): Float {
        if (bitmap.width <= 0 || bitmap.height <= 0) return 1f
        val step = 4
        var total = 0
        var opaque = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                total++
                if (Color.alpha(bitmap.getPixel(x, y)) >= 128) opaque++
                x += step
            }
            y += step
        }
        return if (total == 0) 1f else opaque.toFloat() / total
    }

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

    /** 绑定缺失/失效时的兜底枚举 (捕获前或源重建时), 与捕获
     * 同一普查口径 (2.1.9: 无文字的单元也算 tab, 否则商城页
     * 会因凑不齐四个而落回宿主的下标打分判定)。 */
    private fun fallbackResolve(src: ViewGroup): Int? {
        val tabs = QtlArtworkCapture.censusTabs(src) ?: return null
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

    fun isDescendant(view: View, ancestor: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }

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
    /** 图标绘制尺寸上限 (dp): 真图标约 24dp, 异常大叶子钳到此值。 */
    private const val MAX_ICON_DP = 32f

    private class TabArt(
        val iconView: ImageView?,
        val iconDrawable: Drawable?,
        val stateView: View?,
        val label: TextView?,
    ) {
        val isEmpty: Boolean
            get() = label == null && iconView == null && iconDrawable == null
    }

    /**
     * 文字行的 tab 单元普查 (2.1.9)。
     *
     * 旧口径按「非 GONE 且带文字」过滤行子视图: 商城页的商城
     * 单元在软件绘制下没有文字 (只剩椭圆背景视图) 时会被整
     * 个排除, tab 数凑不齐四、捕获整次失败, 宿主随即落到
     * 通用兜底整树绘制 (黑底 + 蓝椭圆上屏)。现按行子视图的
     * 构成普查: 非 GONE 孩子正好四个即全是 tab; 正好五个
     * 时中间一个应是发布占位 (加号按钮本体/包含加号按钮,
     * 或无文字——四个真 tab 都有标签), 剔除后须剩四个;
     * 其余构成判普查失败。捕获、选中兜底、点按兜底与捕获
     * 推迟普查共用同一口径, 保证槽位映射在各页一致。
     */
    fun censusTabs(source: ViewGroup): List<View>? {
        val row = QtlViews.findByEntryName(source, QtlViews.TAB_ROW_ID) as? ViewGroup
            ?: return null
        val children = (0 until row.childCount).map(row::getChildAt)
            .filter { it.visibility != View.GONE }
        if (children.size == SLOT_COUNT - 1) return children
        if (children.size == SLOT_COUNT) {
            val center = children[CENTER_INDEX]
            val button = QtlViews.findCenterButton(source)
            // 发布位在行内固定居中且从无文字 (四个真 tab 都有
            // 标签): 是加号按钮本体、包含加号按钮、或无文字,
            // 任一成立即按占位剔除。
            val isPlaceholder = center === button ||
                (button != null && QtlViews.isDescendant(button, center)) ||
                !hasText(center)
            if (isPlaceholder) {
                return children.filterIndexed { index, _ -> index != CENTER_INDEX }
            }
        }
        return null
    }

    /** 单元能否提取到任何素材 (图标叶子或文字), 供捕获推迟普查。 */
    fun hasAnyArt(unit: View): Boolean = !extractTabArt(unit).isEmpty

    /**
     * 叶子是否「填满单元」: 选中态的椭圆背景/指示视图会铺满
     * 整个 tab 单元, 真图标只是单元中央的小图 (约 24dp)。
     * 宽高同时超过单元六成的 ImageView 按背景处理。单元
     * 本身是 ImageView 时不判 (它就是这个 tab 的全部)。
     */
    private fun fillsUnit(view: View, unit: View): Boolean {
        if (view === unit) return false
        return unit.width > 0 && unit.height > 0 &&
            view.width > unit.width * 0.6f && view.height > unit.height * 0.6f
    }

    private var lastCensusLogAt = 0L

    /** 普查异常日志节流 (捕获失败会随探针重试, 避免刷屏)。 */
    private fun logCensusThrottled(message: String) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastCensusLogAt < 5_000L) return
        lastCensusLogAt = now
        XposedBridge.log("[OfflineGlass][QTL] capture census: $message")
    }

    fun capture(source: ViewGroup, target: Bitmap): Boolean {
        val tabs = censusTabs(source) ?: run {
            logCensusThrottled("census failed")
            return false
        }
        val button = QtlViews.findCenterButton(source) ?: return false
        if (button.width <= 0 || button.height <= 0) return false
        val arts = tabs.map(::extractTabArt)
        // 单个 tab 素材全空只让该槽在本张快照里留空, 不再整次
        // 失败落回通用兜底 (2.1.9 商城页定案); 四个全空才判
        // 失败 (行未就绪/认错行)。
        if (arts.all { it.isEmpty }) {
            logCensusThrottled("all tab arts empty")
            return false
        }
        if (arts.any { it.isEmpty }) {
            logCensusThrottled(
                "empty tab art at " + arts.indexOfFirst { it.isEmpty },
            )
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
        // 深度优先找第一个带 drawable 的 ImageView 作图标叶子;
        // 填满单元的背景型 ImageView (选中态椭圆指示) 跳过,
        // 继续找真图标 (2.1.9)。
        if (iconView == null && iconDrawable == null) {
            val stack = ArrayDeque<View>()
            stack += unit
            while (stack.isNotEmpty()) {
                val view = stack.removeLast()
                if (view is ImageView && view.drawable != null && view.width > 0 &&
                    !fillsUnit(view, unit)
                ) {
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
        // 图标尺寸先钳制 (2.1.9): 真图标约 24dp; 若叶子异常
        // 巨大 (背景型视图漏网), 按原始尺寸布局会把文字顶出
        // 位图、画出占满槽位的大图 (商城页蓝椭圆的形态)。
        // 等比钳到 32dp 内, 布局与绘制都用钳后尺寸。
        var iconWidth = when {
            art.iconView != null -> art.iconView.width.toFloat()
            art.iconDrawable != null -> (art.iconDrawable.intrinsicWidth.takeIf { it > 0 }
                ?: art.iconDrawable.bounds.width()).toFloat()
            else -> 0f
        }
        var iconHeight = when {
            art.iconView != null -> art.iconView.height.toFloat()
            art.iconDrawable != null -> (art.iconDrawable.intrinsicHeight.takeIf { it > 0 }
                ?: art.iconDrawable.bounds.height()).toFloat()
            else -> 0f
        }
        if (iconWidth > 0f && iconHeight > 0f) {
            val maxIcon = MAX_ICON_DP * density
            val scale = min(1f, maxIcon / maxOf(iconWidth, iconHeight))
            iconWidth *= scale
            iconHeight *= scale
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
                    canvas, art.iconView, centerX, top + iconHeight / 2f,
                    maxOf(iconWidth, iconHeight),
                )
            }
            art.iconDrawable != null -> {
                val drawable = art.iconDrawable
                val savedBounds = android.graphics.Rect(drawable.bounds)
                val savedState = drawable.state
                if (art.stateView != null) drawable.state = art.stateView.drawableState
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
