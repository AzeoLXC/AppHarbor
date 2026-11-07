package dev.appharbor

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.TextView

object HarborPalette {
    val background = Color.rgb(8, 21, 29)
    val surface = Color.rgb(16, 38, 48)
    val surfaceRaised = Color.rgb(24, 51, 62)
    val border = Color.rgb(38, 69, 78)
    val accent = Color.rgb(101, 230, 196)
    val accentAlt = Color.rgb(186, 155, 255)
    val text = Color.rgb(238, 247, 244)
    val muted = Color.rgb(157, 180, 182)
    val warning = Color.rgb(255, 194, 116)
    val danger = Color.rgb(255, 130, 137)
}

fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

fun rounded(color: Int, radius: Float, stroke: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (stroke != null) setStroke(1, stroke)
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

fun actionText(
    context: Context,
    text: String,
    active: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
): TextView = label(
    context,
    text,
    13f,
    when {
        danger -> HarborPalette.danger
        active -> HarborPalette.background
        else -> HarborPalette.text
    },
    bold = true,
    gravity = Gravity.CENTER
).apply {
    setPadding(context.dp(14f), context.dp(10f), context.dp(14f), context.dp(10f))
    background = rounded(
        if (active) HarborPalette.accent else HarborPalette.surfaceRaised,
        context.dp(18f).toFloat(),
        if (active) null else HarborPalette.border
    )
    isClickable = true
    isFocusable = true
    setOnClickListener { onClick() }
}

fun View.horizontalMargins(context: Context, start: Int = 0, top: Int = 0, end: Int = 0, bottom: Int = 0) {
    val p = context.dp(start.toFloat())
    val t = context.dp(top.toFloat())
    val e = context.dp(end.toFloat())
    val b = context.dp(bottom.toFloat())
    (layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.setMargins(p, t, e, b)
}
