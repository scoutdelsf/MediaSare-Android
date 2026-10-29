package org.mediasare.android

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.widget.*

class MainActivity : Activity() {
    private lateinit var url: EditText
    private lateinit var download: Button
    private lateinit var cancel: Button
    private lateinit var open: Button
    private lateinit var format: Spinner
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar

    private val handler = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(244, 247, 246))
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or
                        WindowInsets.Type.ime()
                )
                view.setPadding(
                    dp(24) + bars.left,
                    dp(24) + bars.top,
                    dp(24) + bars.right,
                    dp(24) + bars.bottom
                )
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    dp(24),
                    dp(24) + insets.systemWindowInsetTop,
                    dp(24),
                    dp(24) + insets.systemWindowInsetBottom
                )
            }
            insets
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
        setContentView(scroll)

        root.addView(label("MediaSare", 32).apply {
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(label("把想留住的影片，存進手機。", 17))
        root.addView(label("影片網址", 16), spaced())

        url = EditText(this).apply {
            id = android.R.id.edit
            hint = "貼上影片連結，或從其他 App 分享過來"
            textSize = 16f
            minLines = 2
            maxLines = 5
            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(12).toFloat()
            }
        }
        root.addView(url, spaced(8))

        root.addView(button("貼上剪貼簿") {
            val clipboard =
                getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            val text = if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(this).toString()
            } else {
                ""
            }
            url.setText(SharedUrl.extract(text) ?: text)
        }, spaced(6))

        format = Spinner(this).apply {
            id = android.R.id.custom
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf(
                    "MP4 影片 · 最高 1080p",
                    "MP4 影片 · 最高 720p",
                    "MP3 音訊"
                )
            )
        }
        root.addView(format, spaced())

        download = button("開始下載") {
            startDownload()
        }
        root.addView(download, spaced())

        progress = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
        }
        root.addView(progress, spaced())

        status = label("", 16).apply {
            setTextIsSelectable(true)
        }
        root.addView(status, spaced(8))

        cancel = button("取消下載") {
            startService(
                Intent(this, DownloadService::class.java)
                    .setAction(DownloadService.CANCEL)
            )
        }
        root.addView(cancel, spaced(8))

        open = button("開啟剛下載的檔案") {
            val state = DownloadState.read(this)
            launch(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse(state.uri), state.mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
        root.addView(open, spaced(8))

        root.addView(button("查看下載資料夾") {
            launch(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
        }, spaced(8))

        root.addView(
            label(
                "儲存位置：下載 / MediaSare\n" +
                    "首次下載需要解壓縮下載核心，請稍候。\n" +
                    "目前一次下載一支影片；私人或需登入的影片可能無法下載。",
                14
            ),
            spaced()
        )

        if (savedInstanceState == null) {
            receive(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
    }

    private fun receive(intent: Intent) {
        if (
            intent.action != Intent.ACTION_SEND ||
            intent.type != "text/plain"
        ) {
            return
        }

        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?.toString()
            ?: intent.clipData?.let {
                if (it.itemCount > 0) {
                    it.getItemAt(0).coerceToText(this).toString()
                } else {
                    null
                }
            }
            ?: ""

        val link = SharedUrl.extract(text)
        url.setText(link ?: text)

        if (text.isNotBlank() && link == null) {
            url.error = "分享內容中沒有有效的 HTTPS 影片網址"
        }
    }

    private fun startDownload() {
        val link = SharedUrl.extract(url.text.toString())

        if (link == null) {
            url.error = "請貼上完整的 HTTPS 影片網址"
            return
        }

        if (DownloadService.running) {
            return
        }

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                42
            )
        }

        try {
            startForegroundService(
                Intent(this, DownloadService::class.java)
                    .putExtra("url", link)
                    .putExtra("format", format.selectedItemPosition)
            )
            download.isEnabled = false
        } catch (e: Exception) {
            status.text = "無法啟動下載：${e.localizedMessage}"
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun render() {
        val state = DownloadState.read(this)

        status.text = state.message
        progress.visibility =
            if (state.active) View.VISIBLE else View.GONE
        progress.isIndeterminate = state.progress < 0
        progress.progress = state.progress.coerceAtLeast(0)

        download.isEnabled = !state.active
        format.isEnabled = !state.active

        cancel.visibility =
            if (state.active) View.VISIBLE else View.GONE

        open.visibility =
            if (state.uri.isNotEmpty() && !state.active) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "手機沒有可開啟此內容的 App，請從檔案管理員查看下載資料夾。",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun label(text: String, size: Int) =
        TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            setTextColor(Color.rgb(25, 48, 44))
        }

    private fun button(text: String, action: () -> Unit) =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            minHeight = dp(52)
            setOnClickListener { action() }
        }

    private fun spaced(top: Int = 20) =
        LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(top)
        }

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density).toInt()
}
