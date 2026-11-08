package dev.appharbor

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.text.TextUtils
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
            setPadding(context.dp(12f), context.dp(9f), context.dp(8f), context.dp(9f))
            useNativeSelectableBackground()
            isClickable = true
            isFocusable = true
        }
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(context.dp(44f), context.dp(44f))
        }
        val textBlock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(12f), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = label(context, "", 16f, HarborPalette.text, bold = true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val packageName = label(context, "", 12f, HarborPalette.muted).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textBlock.addView(title)
        textBlock.addView(packageName, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = context.dp(4f) })
        top.addView(icon)
        top.addView(textBlock)
        val meta = label(context, "", 12f, HarborPalette.muted).apply {
            setPadding(context.dp(56f), context.dp(3f), 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        root.addView(top)
        root.addView(meta)
        return Holder(root, icon, title, packageName, meta)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val isSelected = item.packageName in selectedPackages
        holder.icon.setImageDrawable(item.icon)
        holder.title.text = item.label
        holder.packageName.text = item.packageName
        val state = if (item.enabled) "Enabled" else "Disabled"
        val kind = if (item.system) "System" else "User"
        holder.meta.text = "$kind  ·  $state  ·  v${item.versionName}"
        holder.itemView.background = if (isSelected) {
            rounded(HarborPalette.surfaceRaised, holder.itemView.context.dp(8f).toFloat())
        } else {
            val value = android.util.TypedValue()
            holder.itemView.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
            holder.itemView.context.getDrawable(value.resourceId)
        }
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
        val meta: android.widget.TextView
    ) : RecyclerView.ViewHolder(itemView)
}
