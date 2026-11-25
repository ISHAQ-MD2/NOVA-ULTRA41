package com.nova.app

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.*

/**
 * Shared Sunset / Pastel Peach Design System components:
 * - Palette & Glass Card helpers
 * - SunsetOrbView (3D dotted glowing particle sphere with "TAP TO START")
 * - SunsetNavBar (floating frosted bottom bar with coral arch curve cradling the central Mic)
 */
object SunsetColors {
    val PEACH_TOP = Color.parseColor("#FFF5EE")
    val PEACH_MID = Color.parseColor("#FFEADF")
    val PEACH_BOTTOM = Color.parseColor("#FFE2D4")
    val CORAL = Color.parseColor("#FF643D")
    val PINK = Color.parseColor("#FF4D6D")
    val ORANGE = Color.parseColor("#FF7A33")
    val GLASS_BG = Color.parseColor("#F7FFFCFA")
    val GLASS_SOFT = Color.parseColor("#E6FFFFFF")
    val GLASS_STROKE = Color.parseColor("#E8D6CC")
    val TEXT_PRIMARY = Color.parseColor("#181A1F")
    val TEXT_SECONDARY = Color.parseColor("#6B6664")
    val TEXT_MUTED = Color.parseColor("#938C88")
    val GREEN_BG = Color.parseColor("#E3F5EA")
    val GREEN_FG = Color.parseColor("#168A45")
    val CORAL_BG = Color.parseColor("#FFF0E8")
    val BLUE_BG = Color.parseColor("#E8F1FF")
    val BLUE_FG = Color.parseColor("#2563EB")
    val DIVIDER = Color.parseColor("#EDE0D8")
    val SLATE_DOT = Color.parseColor("#3A4454")
    val RADIO_OFF = Color.parseColor("#6B7280")

    fun orbColors(ctx: Context): Triple<Int, Int, Int> {
        return when (ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).getString("orb_color", "sunset")) {
            "ocean" -> Triple(Color.parseColor("#0EA5E9"), Color.parseColor("#38BDF8"), Color.parseColor("#1D4ED8"))
            "violet" -> Triple(Color.parseColor("#8B5CF6"), Color.parseColor("#EC4899"), Color.parseColor("#6D28D9"))
            "emerald" -> Triple(Color.parseColor("#10B981"), Color.parseColor("#14B8A6"), Color.parseColor("#047857"))
            else -> Triple(CORAL, PINK, Color.parseColor("#FF9E75"))
        }
    }
}

object SunsetUiHelper {
    fun dp(ctx: Context, x: Int): Int = (x * ctx.resources.displayMetrics.density).toInt()

    fun pageBg(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(SunsetColors.PEACH_TOP, SunsetColors.PEACH_MID, SunsetColors.PEACH_BOTTOM)
    )

    fun glassCard(ctx: Context, radiusDp: Int = 22, bg: Int = SunsetColors.GLASS_BG, stroke: Int = SunsetColors.GLASS_STROKE): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(ctx, radiusDp).toFloat()
            setColor(bg)
            setStroke(dp(ctx, 1), stroke)
        }

    fun gradientPill(ctx: Context, radiusDp: Int = 28): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(SunsetColors.PINK, SunsetColors.CORAL)
        ).apply {
            cornerRadius = dp(ctx, radiusDp).toFloat()
        }

    fun outlinePill(ctx: Context, radiusDp: Int = 28, stroke: Int = SunsetColors.CORAL): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(ctx, radiusDp).toFloat()
            setColor(SunsetColors.GLASS_SOFT)
            setStroke(dp(ctx, 1), stroke)
        }

    fun badge(ctx: Context, text: String, bgColor: Int, fgColor: Int): TextView = TextView(ctx).apply {
        this.text = text
        textSize = 10.5f
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.06f
        setTextColor(fgColor)
        setPadding(dp(ctx, 10), dp(ctx, 4), dp(ctx, 10), dp(ctx, 4))
        background = GradientDrawable().apply {
            cornerRadius = dp(ctx, 99).toFloat()
            setColor(bgColor)
        }
    }

    fun circleIconBtn(ctx: Context, iconName: String, contentDesc: String, onClick: () -> Unit): FrameLayout {
        val d = dp(ctx, 48)
        return FrameLayout(ctx).apply {
            minimumWidth = d
            minimumHeight = d
            contentDescription = contentDesc
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#AAFFFFFF"))
                setStroke(dp(ctx, 1), SunsetColors.GLASS_STROKE)
            }
            setPadding(dp(ctx, 11), dp(ctx, 11), dp(ctx, 11), dp(ctx, 11))
            addView(IconView(ctx).apply {
                icon = iconName
                tint = SunsetColors.TEXT_PRIMARY
            }, FrameLayout.LayoutParams(dp(ctx, 22), dp(ctx, 22), Gravity.CENTER))
            setOnClickListener { onClick() }
        }
    }
}

/** Custom 3D rotating dotted particle sphere with audio reactive pulse and central CTA pill. */
class SunsetOrbView(ctx: Context) : View(ctx) {
    var active = false
        set(value) { field = value; invalidate() }
    var speaking = false
        set(value) { field = value; invalidate() }
    var ctaLabel = "TAP TO START"
        set(value) { field = value; invalidate() }

    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillRect = RectF()
    private val points = ArrayList<FloatArray>(210)
    private var visible = true

    init {
        contentDescription = "NOVA Live Voice Sphere"
        isClickable = true
        isFocusable = true
        // Fibonacci lattice on unit sphere
        val n = 200
        val golden = Math.PI * (3.0 - sqrt(5.0))
        for (i in 0 until n) {
            val y = 1.0 - (i.toDouble() / (n - 1)) * 2.0
            val r = sqrt(maxOf(0.0, 1.0 - y * y))
            val theta = golden * i
            val x = cos(theta) * r
            val z = sin(theta) * r
            points.add(floatArrayOf(x.toFloat(), y.toFloat(), z.toFloat(), (i % 7) / 6f))
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        visible = visibility == VISIBLE
        if (visible) invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f
        val cy = h / 2f
        val baseR = minOf(w, h) * 0.36f
        val level = maxOf(Vol.level, Vol.micLevel).coerceIn(0f, 1f)
        val t = (System.currentTimeMillis() % 600_000L) / 1000f
        val breath = 1f + 0.035f * sin(t * 2.4f) + (if (active || speaking) level * 0.14f else 0f)
        val radius = baseR * breath

        val (c1, c2, c3) = SunsetColors.orbColors(context)

        // Outer ambient sunset halo
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx, cy, radius * 1.48f,
            intArrayOf(
                c1.withAlpha(if (active) 115 else 75),
                c2.withAlpha(if (active) 60 else 36),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, radius * 1.48f, p)

        // Warm core sphere body
        p.shader = RadialGradient(
            cx - radius * 0.25f, cy - radius * 0.28f, radius * 1.15f,
            intArrayOf(Color.WHITE.withAlpha(240), c3.withAlpha(210), c1.withAlpha(230), c2.withAlpha(240)),
            floatArrayOf(0f, 0.35f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, radius * 0.92f, p)
        p.shader = null

        // Rotating 3D particle cloud
        val rotY = t * (if (speaking) 1.35f else if (active) 0.9f else 0.45f)
        val rotX = sin(t * 0.5f) * 0.35f
        val cosY = cos(rotY); val sinY = sin(rotY)
        val cosX = cos(rotX); val sinX = sin(rotX)

        for (pt in points) {
            val x0 = pt[0]; val y0 = pt[1]; val z0 = pt[2]
            // Rotate around Y
            val x1 = x0 * cosY - z0 * sinY
            val z1 = x0 * sinY + z0 * cosY
            // Rotate around X
            val y2 = y0 * cosX - z1 * sinX
            val z2 = y0 * sinX + z1 * cosX

            val depth = (z2 + 1f) * 0.5f // 0 back .. 1 front
            val projScale = 1f + depth * 0.14f
            val px = cx + x1 * radius * projScale
            val py = cy + y2 * radius * projScale
            val dotR = (1.6f + depth * 2.4f + (if (speaking) level * 1.4f else 0f)) * d
            p.color = when {
                z2 > 0.3f -> Color.WHITE.withAlpha((130 + 120 * depth).toInt())
                pt[3] > 0.5f -> c1.withAlpha((90 + 135 * depth).toInt())
                else -> c2.withAlpha((80 + 130 * depth).toInt())
            }
            c.drawCircle(px, py, dotR, p)
        }

        // Crisp status/CTA pill in the lower-center of orb
        val label = if (speaking) "SPEAKING..." else if (active) "LISTENING..." else ctaLabel
        tp.textSize = 11.5f * d
        tp.typeface = Typeface.DEFAULT_BOLD
        tp.letterSpacing = 0.08f
        val tw = tp.measureText(label)
        val pw = tw + 28 * d
        val ph = 28 * d
        pillRect.set(cx - pw / 2f, cy + radius * 0.58f, cx + pw / 2f, cy + radius * 0.58f + ph)
        pillPaint.color = Color.parseColor("#EBF7FFFC")
        pillPaint.setShadowLayer(8 * d, 0f, 3 * d, Color.argb(35, 0, 0, 0))
        c.drawRoundRect(pillRect, ph / 2f, ph / 2f, pillPaint)
        pillPaint.clearShadowLayer()
        pillPaint.style = Paint.Style.STROKE
        pillPaint.strokeWidth = 1f * d
        pillPaint.color = c1.withAlpha(110)
        c.drawRoundRect(pillRect, ph / 2f, ph / 2f, pillPaint)
        pillPaint.style = Paint.Style.FILL

        tp.textAlign = Paint.Align.CENTER
        tp.color = if (active) c2 else SunsetColors.TEXT_PRIMARY
        val fm = tp.fontMetrics
        c.drawText(label, cx, pillRect.centerY() - (fm.ascent + fm.descent) / 2f, tp)

        if (visible) postInvalidateDelayed(36)
    }
}

/**
 * Floating frosted bottom navigation pill with upward coral arch curve cradling the central Mic circle.
 * Tabs: 0 = Home, 1 = Chat, 2 = Settings, 3 = Updates (plus center Mic action).
 */
class SunsetNavBar(ctx: Context) : FrameLayout(ctx) {
    var selectedTab = 0
        set(value) { field = value; refreshSelection() }
    var micActive = false
        set(value) { field = value; refreshMic() }
    var hasUpdatesDot = true
        set(value) { field = value; updatesDot.visibility = if (value) View.VISIBLE else View.GONE }

    var onTabSelected: ((Int) -> Unit)? = null
    var onMicClick: (() -> Unit)? = null

    private val d = ctx.resources.displayMetrics.density
    private val barBg = BarBackgroundView(ctx)
    private val micCircle: FrameLayout
    private val micIconView: IconView
    private val tabViews = ArrayList<LinearLayout>()
    private val tabIcons = ArrayList<IconView>()
    private val tabLabels = ArrayList<TextView>()
    private val updatesDot: View

    init {
        setPadding(dp(12), dp(6), dp(12), dp(8))
        clipChildren = false
        clipToPadding = false

        // Rounded glass backdrop with central coral wave arch
        addView(barBg, LayoutParams(-1, dp(72), Gravity.BOTTOM))

        // 5-slot row inside bar
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        val tabs = listOf(
            "home" to "Home",
            "chat" to "Chat"
        )
        val tabsRight = listOf(
            "settings" to "Settings",
            "bell" to "Updates"
        )

        var dotRef: View? = null
        fun buildTab(idx: Int, iconName: String, title: String, withDot: Boolean = false): LinearLayout {
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumWidth = dp(48)
                minimumHeight = dp(56)
                contentDescription = title
                setPadding(dp(6), dp(6), dp(6), dp(6))
                setOnClickListener { selectedTab = idx; onTabSelected?.invoke(idx) }
            }
            val iconHolder = FrameLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(22))
            }
            val iv = IconView(ctx).apply { icon = iconName; tint = SunsetColors.TEXT_SECONDARY }
            iconHolder.addView(iv, LayoutParams(dp(20), dp(20), Gravity.CENTER))
            if (withDot) {
                val dot = View(ctx).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#EF4444"))
                    }
                }
                iconHolder.addView(dot, LayoutParams(dp(7), dp(7), Gravity.TOP or Gravity.END))
                dotRef = dot
            }
            val tv = TextView(ctx).apply {
                text = title
                textSize = 11f
                setTextColor(SunsetColors.TEXT_SECONDARY)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            col.addView(iconHolder)
            col.addView(tv, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
            tabViews.add(col)
            tabIcons.add(iv)
            tabLabels.add(tv)
            return col
        }

        row.addView(buildTab(0, tabs[0].first, tabs[0].second), LinearLayout.LayoutParams(0, dp(60), 1f))
        row.addView(buildTab(1, tabs[1].first, tabs[1].second), LinearLayout.LayoutParams(0, dp(60), 1f))
        // Spacer for central Mic
        row.addView(View(ctx), LinearLayout.LayoutParams(dp(68), dp(56)))
        row.addView(buildTab(2, tabsRight[0].first, tabsRight[0].second), LinearLayout.LayoutParams(0, dp(60), 1f))
        row.addView(buildTab(3, tabsRight[1].first, tabsRight[1].second, withDot = true), LinearLayout.LayoutParams(0, dp(60), 1f))
        updatesDot = dotRef ?: View(ctx)

        addView(row, LayoutParams(-1, dp(72), Gravity.BOTTOM))

        // Elevated center circular Mic button cradled by the coral arch
        micIconView = IconView(ctx).apply { icon = "mic"; tint = Color.parseColor("#4B5563") }
        micCircle = FrameLayout(ctx).apply {
            contentDescription = "Start or stop NOVA live mic"
            elevation = dp(6).toFloat()
            addView(micIconView, LayoutParams(dp(24), dp(24), Gravity.CENTER))
            setOnClickListener { onMicClick?.invoke() }
        }
        addView(micCircle, LayoutParams(dp(58), dp(58), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(16)
        })

        refreshSelection()
        refreshMic()
    }

    private fun dp(x: Int) = (x * d).toInt()

    private fun refreshSelection() {
        for (i in tabViews.indices) {
            val active = (i == selectedTab)
            tabViews[i].background = if (active) {
                GradientDrawable().apply {
                    cornerRadius = dp(24).toFloat()
                    setColor(Color.parseColor("#14000000"))
                    setStroke(dp(1), Color.parseColor("#55A8A29E"))
                }
            } else null
            tabIcons[i].tint = if (active) SunsetColors.TEXT_PRIMARY else SunsetColors.TEXT_SECONDARY
            tabLabels[i].setTextColor(if (active) SunsetColors.TEXT_PRIMARY else SunsetColors.TEXT_SECONDARY)
            tabLabels[i].typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun refreshMic() {
        if (micActive) {
            micCircle.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(SunsetColors.CORAL, SunsetColors.PINK)
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2), Color.WHITE)
            }
            micIconView.tint = Color.WHITE
        } else {
            micCircle.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#F5E2DCE8"))
                setStroke(dp(1), Color.parseColor("#6B7280"))
            }
            micIconView.tint = Color.parseColor("#4B5563")
        }
    }

    private class BarBackgroundView(ctx: Context) : View(ctx) {
        private val d = ctx.resources.displayMetrics.density
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * d
            color = Color.parseColor("#CCFF7A50")
        }
        private val path = Path()
        private val curvePath = Path()
        private val rect = RectF()

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val topY = 8 * d
            val r = 30 * d
            rect.set(2 * d, topY, w - 2 * d, h - 2 * d)
            path.reset()
            path.addRoundRect(rect, r, r, Path.Direction.CW)

            // Subtle frosted white-peach fill
            fillPaint.style = Paint.Style.FILL
            fillPaint.color = Color.parseColor("#F2FFF5EE")
            fillPaint.setShadowLayer(10 * d, 0f, 3 * d, Color.argb(30, 0, 0, 0))
            c.drawPath(path, fillPaint)
            fillPaint.clearShadowLayer()

            // Crisp soft outer border
            strokePaint.color = Color.parseColor("#D6C7BE")
            strokePaint.strokeWidth = 1f * d
            c.drawPath(path, strokePaint)

            // Coral arch crest over the central mic cutout
            val cx = w / 2f
            val archSpan = 46 * d
            curvePath.reset()
            curvePath.moveTo(cx - archSpan, topY)
            curvePath.cubicTo(
                cx - archSpan * 0.45f, topY,
                cx - archSpan * 0.38f, 1.5f * d,
                cx, 1.5f * d
            )
            curvePath.cubicTo(
                cx + archSpan * 0.38f, 1.5f * d,
                cx + archSpan * 0.45f, topY,
                cx + archSpan, topY
            )
            strokePaint.color = Color.parseColor("#FF7A50")
            strokePaint.strokeWidth = 2f * d
            c.drawPath(curvePath, strokePaint)
        }
    }
}
