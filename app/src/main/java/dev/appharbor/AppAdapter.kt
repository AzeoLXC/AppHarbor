package dev.appharbor

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val onClick: (AppRecord) -> Unit,
    private val onLongClick: (AppRecord) -> Unit
) : RecyclerView.Adapter<AppAdapter.Holder>() {
    private var items: List<AppRecord> = emptyList()
    private var selectedPackages: Set<String> = emptySet()

    init { setHasStableIds(true) }

    fun submit(nextItems: List<AppRecord>, selected: Set<String>) {
        items = nextItems
        selectedPackages = selected
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long = items[position].packageName.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(14f), context.dp(13f), context.dp(14f), context.dp(13f))
            background = rounded(HarborPalette.surface, context.dp(19f).toFloat(), HarborPalette.border)
            isClickable = true
            isFocusable = true
        }
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(context.dp(46f), context.dp(46f))
        }
        val textBlock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(11f), 0, context.dp(6f), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = label(context, "", 15f, HarborPalette.text, bold = true)
        val packageName = label(context, "", 11f, HarborPalette.muted)
        textBlock.addView(title)
        textBlock.addView(packageName, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = context.dp(4f) })
        val mark = label(context, "", 11f, HarborPalette.accent, bold = true, gravity = Gravity.CENTER).apply {
            minimumWidth = context.dp(28f)
            setPadding(context.dp(7f), context.dp(6f), context.dp(7f), context.dp(6f))
            background = rounded(HarborPalette.surfaceRaised, context.dp(14f).toFloat())
        }
        top.addView(icon)
        top.addView(textBlock)
        top.addView(mark)
        val meta = label(context, "", 11f, HarborPalette.muted).apply {
            setPadding(0, context.dp(11f), 0, 0)
        }
        root.addView(top)
        root.addView(meta)
        return Holder(root, icon, title, packageName, mark, meta)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val isSelected = item.packageName in selectedPackages
        holder.icon.setImageDrawable(item.icon)
        holder.title.text = item.label
        holder.packageName.text = item.packageName
        holder.mark.text = if (isSelected) "✓" else if (item.system) "SYS" else "APP"
        holder.mark.setTextColor(if (isSelected) HarborPalette.background else if (item.system) HarborPalette.accentAlt else HarborPalette.accent)
        holder.mark.background = rounded(
            if (isSelected) HarborPalette.accent else HarborPalette.surfaceRaised,
            holder.itemView.context.dp(14f).toFloat()
        )
        val state = if (item.enabled) "Enabled" else "Disabled"
        val kind = if (item.system) "System" else "User"
        holder.meta.text = "$kind  ·  $state  ·  v${item.versionName}"
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    class Holder(
        itemView: View,
        val icon: ImageView,
        val title: android.widget.TextView,
        val packageName: android.widget.TextView,
        val mark: android.widget.TextView,
        val meta: android.widget.TextView
    ) : RecyclerView.ViewHolder(itemView)
}
