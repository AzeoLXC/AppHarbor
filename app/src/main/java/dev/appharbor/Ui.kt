package dev.appharbor

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.Gravity
import android.view.View
import android.util.TypedValue
import android.widget.Button
import android.widget.TextView

object HarborPalette {
    val isDark: Boolean
        get() = (Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    val background get() = if (isDark) Color.rgb(20, 22, 25) else Color.rgb(244, 245, 247)
    val surface get() = if (isDark) Color.rgb(30, 33, 38) else Color.WHITE
    val surfaceRaised get() = if (isDark) Color.rgb(41, 45, 52) else Color.rgb(234, 237, 241)
    val border get() = if (isDark) Color.rgb(59, 64, 73) else Color.rgb(213, 217, 224)
    val accent get() = if (isDark) Color.rgb(174, 187, 208) else Color.rgb(70, 86, 111)
    val accentAlt get() = if (isDark) Color.rgb(146, 154, 166) else Color.rgb(104, 115, 134)
    val onAccent get() = if (isDark) Color.rgb(26, 29, 34) else Color.WHITE
    val text get() = if (isDark) Color.rgb(236, 238, 241) else Color.rgb(32, 36, 42)
    val muted get() = if (isDark) Color.rgb(165, 170, 179) else Color.rgb(100, 107, 117)
    val warning get() = if (isDark) Color.rgb(216, 164, 85) else Color.rgb(154, 104, 28)
    val danger get() = if (isDark) Color.rgb(225, 134, 134) else Color.rgb(179, 38, 30)
}

fun Window.applyAppSystemBars() {
    val decor = decorView
    statusBarColor = HarborPalette.background
    navigationBarColor = HarborPalette.background

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        setDecorFitsSystemWindows(false)
        val lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        decor.post {
            decor.windowInsetsController?.setSystemBarsAppearance(
                if (HarborPalette.isDark) 0 else lightBars,
                lightBars
            )
        }
    } else {
        @Suppress("DEPRECATION")
        var flags = View.SYSTEM_UI_FLAG_VISIBLE
        if (!HarborPalette.isDark) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
        @Suppress("DEPRECATION")
        decor.systemUiVisibility = flags
    }
}

fun View.applySystemBarInsets(left: Int, top: Int, right: Int, bottom: Int) {
    setPadding(left, top, right, bottom)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

    setOnApplyWindowInsetsListener { view, insets ->
        val safeInsets = insets.getInsets(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        )
        view.setPadding(
            left + safeInsets.left,
            top + safeInsets.top,
            right + safeInsets.right,
            bottom + safeInsets.bottom
        )
        insets
    }
}

fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

fun rounded(color: Int, radius: Float, stroke: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (stroke != null) setStroke(2, stroke)
    }

fun label(
    context: Context,
    text: CharSequence,
    size: Float = 14f,
    color: Int = HarborPalette.text,
    bold: Boolean = false,
    gravity: Int = Gravity.START
): TextView = TextView(context).apply {
    this.text = text
    textSize = size
    setTextColor(color)
    this.gravity = gravity or Gravity.CENTER_VERTICAL
    if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    includeFontPadding = false
}

fun actionButton(
    context: Context,
    text: String,
    active: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
): Button = Button(context).apply {
    this.text = text
    textSize = 14f
    isAllCaps = false
    setTypeface(typeface, android.graphics.Typeface.BOLD)
    gravity = Gravity.CENTER
    setTextColor(when {
        danger -> HarborPalette.danger
        active -> HarborPalette.onAccent
        else -> HarborPalette.text
    })
    backgroundTintList = ColorStateList.valueOf(
        if (active) HarborPalette.accent else HarborPalette.surfaceRaised
    )
    minWidth = 0
    minimumWidth = 0
    minHeight = context.dp(48f)
    setPadding(context.dp(12f), context.dp(6f), context.dp(12f), context.dp(6f))
    setOnClickListener { onClick() }
}

fun View.useNativeSelectableBackground() {
    val value = TypedValue()
    if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)) {
        setBackgroundResource(value.resourceId)
    }
}

fun View.hideScrollbars() {
    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
}

fun View.horizontalMargins(context: Context, start: Int = 0, top: Int = 0, end: Int = 0, bottom: Int = 0) {
    val p = context.dp(start.toFloat())
    val t = context.dp(top.toFloat())
    val e = context.dp(end.toFloat())
    val b = context.dp(bottom.toFloat())
    (layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.setMargins(p, t, e, b)
}
