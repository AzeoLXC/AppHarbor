package dev.appharbor

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    private enum class Page { LIBRARY, TOOLS, PROFILES, SETTINGS }
    private enum class Filter { ALL, USER, SYSTEM, DISABLED }

    private val repository by lazy { AppRepository(this) }
    private val worker = Executors.newSingleThreadExecutor()
    private val preferences by lazy { getSharedPreferences("appharbor", MODE_PRIVATE) }
    private val selectedPackages = linkedSetOf<String>()
    private var allApps: List<AppRecord> = emptyList()
    private var page = Page.LIBRARY
    private var filter = Filter.ALL
    private var query = ""
    private var includeSystem = true
    private var includeDisabled = true
    private var sortByRecent = false
    private var pendingExportFile: File? = null
    private var hasLoaded = false
    private lateinit var contentHost: FrameLayout
    private lateinit var navigationHost: LinearLayout
    private lateinit var summaryApps: android.widget.TextView
    private lateinit var summarySystem: android.widget.TextView
    private lateinit var summaryUser: android.widget.TextView

    private val installFeedbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(InstallStatusReceiver.EXTRA_SUCCESS, false)
            val message = intent.getStringExtra(InstallStatusReceiver.EXTRA_MESSAGE)
                ?: if (success) "Install completed." else "Install did not complete."
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            if (success) refreshInventory()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = HarborPalette.background
        window.navigationBarColor = HarborPalette.background
        window.decorView.systemUiVisibility = 0
        includeSystem = preferences.getBoolean("include_system", true)
        includeDisabled = preferences.getBoolean("include_disabled", true)
        sortByRecent = preferences.getBoolean("sort_recent", false)
        buildShell()
        renderPage()
        refreshInventory()
        val filter = IntentFilter(InstallStatusReceiver.ACTION_FEEDBACK)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(installFeedbackReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(installFeedbackReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasLoaded) refreshInventory()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(installFeedbackReceiver) }
        worker.shutdown()
        super.onDestroy()
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(HarborPalette.background)
            setPadding(dp(17f), dp(12f), dp(17f), dp(10f))
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                dp(17f),
                dp(12f) + insets.systemWindowInsetTop,
                dp(17f),
                dp(10f) + insets.systemWindowInsetBottom
            )
            insets
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4f), 0, dp(13f))
        }
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleBlock.addView(label(this, "AppHarbor", 23f, HarborPalette.text, bold = true))
        titleBlock.addView(label(this, "LOCAL APP CONTROL", 10f, HarborPalette.muted, bold = true).apply {
            letterSpacing = 0.12f
            setPadding(0, dp(4f), 0, 0)
        })
        header.addView(titleBlock)
        header.addView(actionText(this, "＋  Import APK", active = true) { chooseApk() })
        root.addView(header)
        contentHost = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(contentHost)
        navigationHost = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(9f), 0, 0)
        }
        root.addView(navigationHost)
        setContentView(root)
    }

    private fun renderPage() {
        contentHost.removeAllViews()
        val view = when (page) {
            Page.LIBRARY -> buildLibraryPage()
            Page.TOOLS -> buildToolsPage()
            Page.PROFILES -> buildProfilesPage()
            Page.SETTINGS -> buildSettingsPage()
        }
        contentHost.addView(view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        renderNavigation()
    }

    private fun renderNavigation() {
        navigationHost.removeAllViews()
        if (page == Page.LIBRARY && selectedPackages.isNotEmpty()) {
            navigationHost.addView(actionText(this, selectedPackages.size.toString() + " selected", active = true) { showBatchActions() }, weightParams())
            navigationHost.addView(actionText(this, "Actions") { showBatchActions() }, weightParams())
            navigationHost.addView(actionText(this, "Clear") {
                selectedPackages.clear()
                renderPage()
            }, weightParams())
            return
        }
        val items = listOf(
            Page.LIBRARY to "Apps",
            Page.TOOLS to "Tools",
            Page.PROFILES to "Lists",
            Page.SETTINGS to "Settings"
        )
        items.forEach { (target, title) ->
            navigationHost.addView(
                actionText(this, title, active = target == page) {
                    page = target
                    renderPage()
                },
                weightParams()
            )
        }
    }

    private fun weightParams() = LinearLayout.LayoutParams(
        0,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        1f
    ).apply { marginStart = dp(3f); marginEnd = dp(3f) }

    private fun buildLibraryPage(): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (selectedPackages.isNotEmpty()) {
            val selection = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(HarborPalette.surfaceRaised, dp(16f).toFloat())
                setPadding(dp(11f), dp(9f), dp(11f), dp(9f))
            }
            selection.addView(
                label(this, selectedPackages.size.toString() + " selected", 13f, HarborPalette.accent, true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            selection.addView(actionText(this, "Operations", active = true) { showBatchActions() })
            column.addView(selection, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10f) })
        }

        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(HarborPalette.surface, dp(20f).toFloat(), HarborPalette.border)
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }
        val appStat = statBlock("APPS", allApps.size.toString(), HarborPalette.accent)
        val systemStat = statBlock("SYSTEM", allApps.count { it.system }.toString(), HarborPalette.accentAlt)
        val userStat = statBlock("USER", allApps.count { !it.system }.toString(), HarborPalette.warning)
        summary.addView(appStat, weightParams())
        summary.addView(systemStat, weightParams())
        summary.addView(userStat, weightParams())
        summaryApps = appStat.findViewWithTag("value")
        summarySystem = systemStat.findViewWithTag("value")
        summaryUser = userStat.findViewWithTag("value")
        column.addView(summary)

        val search = EditText(this).apply {
            setSingleLine(true)
            hint = "Find by app or package name"
            setHintTextColor(HarborPalette.muted)
            setTextColor(HarborPalette.text)
            textSize = 14f
            setPadding(dp(15f), 0, dp(15f), 0)
            background = rounded(HarborPalette.surfaceRaised, dp(15f).toFloat(), HarborPalette.border)
            setText(query)
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString().orEmpty()
                updateAppList()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        column.addView(search, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(48f)
        ).apply { topMargin = dp(11f) })

        val filterStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(9f), 0, dp(9f))
        }
        listOf(
            Filter.ALL to "All",
            Filter.USER to "User",
            Filter.SYSTEM to "System",
            Filter.DISABLED to "Disabled"
        ).forEach { (type, title) ->
            filterStrip.addView(actionText(this, title, active = type == filter) {
                filter = type
                renderPage()
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(7f) })
        }
        filterStrip.addView(actionText(this, "Sort") { showSortOptions() })
        column.addView(filterStrip)

        val recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, gridColumns())
            clipToPadding = false
            setPadding(0, dp(3f), 0, dp(7f))
            itemAnimator = null
        }
        recycler.adapter = AppAdapter(
            onClick = { app ->
                if (selectedPackages.isNotEmpty()) toggleSelection(app)
                else startActivity(Intent(this, DetailsActivity::class.java).putExtra(DetailsActivity.EXTRA_PACKAGE, app.packageName))
            },
            onLongClick = { app -> toggleSelection(app) }
        )
        column.addView(recycler, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        (recycler.adapter as AppAdapter).submit(filteredApps(), selectedPackages)
        return column
    }

    private fun statBlock(title: String, value: String, color: Int): LinearLayout {
        val block = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        block.addView(label(this, title, 9f, HarborPalette.muted, bold = true))
        block.addView(label(this, value, 21f, color, bold = true).apply {
            tag = "value"
            setPadding(0, dp(3f), 0, 0)
        })
        return block
    }

    private fun gridColumns(): Int {
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        return when {
            widthDp >= 900f -> 3
            widthDp >= 620f -> 2
            else -> 1
        }
    }

    private fun filteredApps(): List<AppRecord> {
        val needle = query.trim().lowercase()
        val stream = allApps.asSequence()
            .filter { includeSystem || !it.system }
            .filter { includeDisabled || filter == Filter.DISABLED || it.enabled }
            .filter {
                when (filter) {
                    Filter.ALL -> true
                    Filter.USER -> !it.system
                    Filter.SYSTEM -> it.system
                    Filter.DISABLED -> !it.enabled
                }
            }
            .filter { needle.isEmpty() || it.label.lowercase().contains(needle) || it.packageName.lowercase().contains(needle) }
        return (if (sortByRecent) stream.sortedByDescending { it.lastUpdateTime }
        else stream.sortedBy { it.label.lowercase() }).toList()
    }

    private fun updateAppList() {
        if (!::contentHost.isInitialized || page != Page.LIBRARY) return
        val recycler = findRecycler(contentHost)
        val adapter = recycler?.adapter as? AppAdapter ?: return
        adapter.submit(filteredApps(), selectedPackages)
        if (::summaryApps.isInitialized) {
            summaryApps.text = allApps.size.toString()
            summarySystem.text = allApps.count { it.system }.toString()
            summaryUser.text = allApps.count { !it.system }.toString()
        }
    }

    private fun findRecycler(view: View): RecyclerView? {
        if (view is RecyclerView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findRecycler(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun toggleSelection(app: AppRecord) {
        if (!selectedPackages.add(app.packageName)) selectedPackages.remove(app.packageName)
        renderPage()
    }

    private fun refreshInventory() {
        worker.execute {
            val loaded = repository.installedApps()
            runOnUiThread {
                allApps = loaded
                hasLoaded = true
                updateAppList()
                if (page == Page.LIBRARY) renderPage()
            }
        }
    }

    private fun buildToolsPage(): View {
        val scroll = ScrollView(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(3f), 0, dp(18f))
        }
        column.addView(label(this, "Tools", 25f, HarborPalette.text, true))
        column.addView(label(this, "Inspect packages and move APK files.", 13f, HarborPalette.muted).apply {
            setPadding(0, dp(5f), 0, dp(17f))
        })
        toolCard(column, "Inspect or install APK", "Choose an APK file to review its package details.") { chooseApk() }
        toolCard(column, "Export installed apps", "Create a ZIP containing the selected APK files.") {
            exportApks(allApps.filter { it.packageName in selectedPackages })
        }
        toolCard(column, "Refresh app inventory", "Read the current package list from Android.") { refreshInventory() }
        toolCard(column, "Usage access", "Open Android settings to grant usage access.") {
            runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                .onFailure { toast("Usage access settings are unavailable on this device.") }
        }
        toolCard(column, "App inventory", "Total " + allApps.size + " packages · " +
            allApps.count { it.system } + " system · " + allApps.count { !it.system } + " user") {
            page = Page.LIBRARY
            renderPage()
        }
        scroll.addView(column)
        return scroll
    }

    private fun toolCard(parent: LinearLayout, title: String, subtitle: String, action: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(HarborPalette.surface, dp(18f).toFloat(), HarborPalette.border)
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        card.addView(label(this, title, 15f, HarborPalette.text, true))
        card.addView(label(this, subtitle, 12f, HarborPalette.muted).apply { setPadding(0, dp(6f), 0, 0) })
        parent.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(9f) })
    }

    private fun buildProfilesPage(): View {
        val scroll = ScrollView(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(3f), 0, dp(18f))
        }
        column.addView(label(this, "Lists", 25f, HarborPalette.text, true))
        column.addView(label(this, "Save package groups for quick selection.", 13f, HarborPalette.muted).apply {
            setPadding(0, dp(5f), 0, dp(16f))
        })
        column.addView(actionText(this, "Save current selection", active = true) {
            saveSelectionAsList()
        }.apply { gravity = Gravity.CENTER })
        column.addView(label(this, "Saved lists", 15f, HarborPalette.accent, true).apply {
            setPadding(0, dp(19f), 0, dp(10f))
        })
        val saved = readLists()
        if (saved.length() == 0) {
            column.addView(label(this, "No lists yet. Select apps, then save them here.", 13f, HarborPalette.muted)
                .apply { setPadding(dp(3f), dp(5f), dp(3f), dp(8f)) })
        }
        for (i in 0 until saved.length()) {
            val item = saved.optJSONObject(i) ?: continue
            val name = item.optString("name", "List")
            val packages = item.optJSONArray("packages") ?: JSONArray()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(HarborPalette.surface, dp(17f).toFloat(), HarborPalette.border)
                setPadding(dp(15f), dp(13f), dp(15f), dp(12f))
            }
            row.addView(label(this, name, 15f, HarborPalette.text, true))
            row.addView(label(this, packages.length().toString() + " packages", 12f, HarborPalette.muted).apply {
                setPadding(0, dp(4f), 0, dp(10f))
            })
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(actionText(this@MainActivity, "Select", active = true) {
                selectedPackages.clear()
                for (j in 0 until packages.length()) {
                    packages.optString(j).takeIf { it.isNotBlank() }?.let(selectedPackages::add)
                }
                page = Page.LIBRARY
                renderPage()
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            buttons.addView(actionText(this@MainActivity, "Delete", danger = true) {
                val current = readLists()
                val updated = JSONArray()
                for (j in 0 until current.length()) {
                    val candidate = current.optJSONObject(j) ?: continue
                    if (j != i) updated.put(candidate)
                }
                preferences.edit().putString("saved_lists", updated.toString()).apply()
                renderPage()
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(buttons)
            column.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8f) })
        }
        scroll.addView(column)
        return scroll
    }

    private fun buildSettingsPage(): View {
        val scroll = ScrollView(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(3f), 0, dp(18f))
        }
        column.addView(label(this, "Settings", 25f, HarborPalette.text, true))
        column.addView(label(this, "Choose what appears in the app inventory.", 13f, HarborPalette.muted).apply {
            setPadding(0, dp(5f), 0, dp(15f))
        })
        settingRow(column, "Include system apps", "Show built-in and updated system packages.", includeSystem) {
            includeSystem = it
            preferences.edit().putBoolean("include_system", it).apply()
            renderPage()
        }
        settingRow(column, "Include disabled apps", "Keep disabled packages visible in the inventory.", includeDisabled) {
            includeDisabled = it
            preferences.edit().putBoolean("include_disabled", it).apply()
            renderPage()
        }
        settingRow(column, "Sort by recent update", "Place recently updated apps first.", sortByRecent) {
            sortByRecent = it
            preferences.edit().putBoolean("sort_recent", it).apply()
            renderPage()
        }
        column.addView(label(this, "AppHarbor 1.0.0", 12f, HarborPalette.muted).apply {
            setPadding(dp(3f), dp(18f), 0, 0)
        })
        scroll.addView(column)
        return scroll
    }

    private fun settingRow(parent: LinearLayout, title: String, subtitle: String, checked: Boolean, changed: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(HarborPalette.surface, dp(16f).toFloat(), HarborPalette.border)
            setPadding(dp(14f), dp(12f), dp(11f), dp(12f))
        }
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        text.addView(label(this, title, 14f, HarborPalette.text, true))
        text.addView(label(this, subtitle, 11f, HarborPalette.muted).apply { setPadding(0, dp(4f), 0, 0) })
        val toggle = android.widget.Switch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> changed(value) }
        }
        row.addView(text)
        row.addView(toggle)
        parent.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8f) })
    }

    private fun showSortOptions() {
        AlertDialog.Builder(this)
            .setTitle("Sort app inventory")
            .setSingleChoiceItems(arrayOf("Name", "Recently updated"), if (sortByRecent) 1 else 0) { dialog, which ->
                sortByRecent = which == 1
                preferences.edit().putBoolean("sort_recent", sortByRecent).apply()
                dialog.dismiss()
                updateAppList()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun readLists(): JSONArray = runCatching {
        JSONArray(preferences.getString("saved_lists", "[]"))
    }.getOrDefault(JSONArray())

    private fun saveSelectionAsList() {
        if (selectedPackages.isEmpty()) {
            toast("Select apps in Apps first, then save the group.")
            return
        }
        val input = EditText(this).apply {
            hint = "List name"
            setTextColor(HarborPalette.text)
            setHintTextColor(HarborPalette.muted)
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
        }
        AlertDialog.Builder(this)
            .setTitle("Save app list")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim().ifBlank { "Saved list" }
                val current = readLists()
                val packages = JSONArray()
                selectedPackages.forEach(packages::put)
                current.put(JSONObject().put("name", name).put("packages", packages))
                preferences.edit().putString("saved_lists", current.toString()).apply()
                toast("List saved.")
                renderPage()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBatchActions() {
        val chosen = allApps.filter { it.packageName in selectedPackages }
        if (chosen.isEmpty()) {
            toast("Select one or more apps first.")
            return
        }
        val actions = arrayOf(
            "Export APK files",
            "Disable packages (root)",
            "Enable packages (root)",
            "Force stop (root)",
            "Clear app data (root)",
            "Uninstall for this user (root)"
        )
        AlertDialog.Builder(this)
            .setTitle(chosen.size.toString() + " selected")
            .setItems(actions) { _, index ->
                when (index) {
                    0 -> exportApks(chosen)
                    1 -> confirmRootAction(chosen, "Disable packages", "pm disable-user --user 0", refresh = true)
                    2 -> confirmRootAction(chosen, "Enable packages", "pm enable", refresh = true)
                    3 -> confirmRootAction(chosen, "Force stop apps", "am force-stop --user 0", refresh = false)
                    4 -> confirmRootAction(chosen, "Clear app data", "pm clear", refresh = false)
                    5 -> confirmRootAction(chosen, "Uninstall apps for user 0", "pm uninstall --user 0", refresh = true)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmRootAction(apps: List<AppRecord>, title: String, command: String, refresh: Boolean) {
        val packages = RootController.safePackages(apps.map { it.packageName })
        if (packages.isEmpty()) {
            toast("No valid package names were selected.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("This operation needs root access. Android will ask the root manager to authorize it. Continue for " +
                packages.size + " package(s)?")
            .setPositiveButton("Continue") { _, _ ->
                worker.execute {
                    val script = buildString {
                        append("result=0; ")
                        packages.forEach { packageName ->
                            append(command).append(' ').append(packageName)
                                .append("; code=\$?; echo '").append(packageName)
                                .append(": exit '\"\$code\"; [ \"\$code\" -eq 0 ] || result=1; ")
                        }
                        append("exit \$result")
                    }
                    val result = RootController.run(script)
                    runOnUiThread {
                        AlertDialog.Builder(this)
                            .setTitle(if (result.success) "Operation finished" else "Operation failed")
                            .setMessage(result.output.take(3500))
                            .setPositiveButton("OK", null)
                            .show()
                        if (refresh) refreshInventory()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseApk() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.android.package-archive"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivityForResult(intent, REQUEST_OPEN_APK) }
            .onFailure { toast("No file picker is available.") }
    }

    @Deprecated("Uses the platform file picker result API for compatibility with API 23.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        when (requestCode) {
            REQUEST_OPEN_APK -> copySelectedApk(data.data!!)
            REQUEST_SAVE_ARCHIVE -> copyArchiveToDestination(data.data!!)
        }
    }

    private fun copySelectedApk(uri: Uri) {
        worker.execute {
            val target = File(cacheDir, "inspect-" + UUID.randomUUID() + ".apk")
            val copied = runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                } ?: error("The file could not be opened.")
                target
            }.getOrNull()
            runOnUiThread {
                if (copied == null) {
                    toast("Could not read the selected APK.")
                    return@runOnUiThread
                }
                val info = repository.inspectArchive(copied)
                if (info == null) {
                    copied.delete()
                    toast("This file is not a readable Android APK.")
                    return@runOnUiThread
                }
                startActivity(Intent(this, DetailsActivity::class.java)
                    .putExtra(DetailsActivity.EXTRA_ARCHIVE_PATH, copied.absolutePath))
            }
        }
    }

    private fun exportApks(apps: List<AppRecord>) {
        if (apps.isEmpty()) {
            toast("Select apps first. Long press an app card to select it.")
            return
        }
        worker.execute {
            val archive = File(cacheDir, "appharbor-" + System.currentTimeMillis() + ".zip")
            val result = runCatching {
                ZipOutputStream(FileOutputStream(archive)).use { zip ->
                    apps.forEach { app ->
                        val apkPaths = listOfNotNull(app.sourceApk) + app.splitApks
                        apkPaths.distinct().forEachIndexed { index, path ->
                            val source = File(path)
                            if (source.isFile && source.canRead()) {
                                val suffix = if (index == 0) ".apk" else "-split$index.apk"
                                zip.putNextEntry(ZipEntry(app.packageName + suffix))
                                FileInputStream(source).use { it.copyTo(zip) }
                                zip.closeEntry()
                            }
                        }
                    }
                }
                archive
            }.getOrNull()
            runOnUiThread {
                if (result == null || result.length() == 0L) {
                    result?.delete()
                    toast("No readable APK files were found.")
                } else {
                    pendingExportFile = result
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/zip"
                        putExtra(Intent.EXTRA_TITLE, "appharbor-export.zip")
                    }
                    runCatching { startActivityForResult(intent, REQUEST_SAVE_ARCHIVE) }
                        .onFailure { toast("No save location is available.") }
                }
            }
        }
    }

    private fun copyArchiveToDestination(uri: Uri) {
        val archive = pendingExportFile
        pendingExportFile = null
        if (archive == null || !archive.isFile) {
            toast("The archive is no longer available.")
            return
        }
        worker.execute {
            val success = runCatching {
                contentResolver.openOutputStream(uri, "w")?.use { output ->
                    FileInputStream(archive).use { it.copyTo(output) }
                } ?: error("The destination could not be opened.")
            }.isSuccess
            archive.delete()
            runOnUiThread { toast(if (success) "APK archive saved." else "Could not save the APK archive.") }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val REQUEST_OPEN_APK = 301
        private const val REQUEST_SAVE_ARCHIVE = 302
    }
}

