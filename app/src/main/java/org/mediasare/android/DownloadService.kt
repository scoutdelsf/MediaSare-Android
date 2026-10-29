package org.mediasare.android

import android.app.*
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.CancellationException

class DownloadService : Service() {
    companion object {
        const val CANCEL = "org.mediasare.android.CANCEL"
        private const val CHANNEL = "downloads"
        private const val NOTICE = 1
        @Volatile var running = false
            private set
    }
    private val executor = Executors.newSingleThreadExecutor()
    private val lock = Any()
    @Volatile private var cancelled = false
    @Volatile private var publishing = false
    private var processId = ""
    private var lastNotice = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            synchronized(lock) {
                if (running && !publishing) cancelled = true
            }
            if (cancelled) {
                report("正在取消下載…")
                killProcess()
            } else if (!running) stopSelf()
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY
        val link = intent?.getStringExtra("url")?.let(SharedUrl::extract)
        if (link == null) { stopSelf(); return START_NOT_STICKY }
        running = true
        cancelled = false
        processId = UUID.randomUUID().toString()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "影片下載", NotificationManager.IMPORTANCE_LOW)
        )
        DownloadState(true, "正在準備下載核心…").save(this)
        val notification = notification("正在準備下載核心…", -1, true)
        startForeground(NOTICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        val format = intent?.getIntExtra("format", 0) ?: 0
        executor.execute { download(link, format) }
        return START_NOT_STICKY
    }

    private fun download(link: String, format: Int) {
        // Native downloader writes in the sandbox; publish only the completed file.
        val base = File(cacheDir, "downloads").apply { mkdirs() }
        // Only one task runs. Remove interrupted tasks left by a killed process.
        base.listFiles()?.forEach { it.deleteRecursively() }
        val task = File(base, processId).apply { mkdirs() }
        try {
            checkCancelled()
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            checkCancelled()
            // Refresh extractors at most daily; an update outage must not block
            // downloads that the bundled extractor can still handle.
            val prefs = getSharedPreferences("engine", MODE_PRIVATE)
            if (System.currentTimeMillis() - prefs.getLong("updated", 0) > 86_400_000L) {
                report("正在檢查下載核心更新…")
                runCatching {
                    YoutubeDL.getInstance().updateYoutubeDL(this, YoutubeDL.UpdateChannel.STABLE)
                    prefs.edit().putLong("updated", System.currentTimeMillis()).apply()
                }
            }
            checkCancelled()
            report("正在解析影片網址…")
            val request = YoutubeDLRequest(link).apply {
                addOption("--no-playlist")
                addOption("--playlist-items", "1")
                addOption("--no-mtime")
                addOption("--newline")
                addOption("--socket-timeout", "30")
                addOption("--retries", "3")
                addOption("-o", File(task, "%(title).100B [%(id).40B].%(ext)s").absolutePath)
                if (format == 2) {
                    addOption("-f", "bestaudio/best")
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                } else {
                    val height = if (format == 1) 720 else 1080
                    addOption("-f", "bv*[height<=$height][ext=mp4]+ba[ext=m4a]/b[height<=$height][ext=mp4]/bv*[height<=$height]+ba/b[height<=$height]")
                    addOption("--merge-output-format", "mp4")
                    addOption("--recode-video", "mp4")
                }
            }
            YoutubeDL.getInstance().execute(request, processId = processId) { percent, _, _ ->
                // Cancellation may arrive just before the native process registers.
                if (cancelled) { killProcess(); throw CancellationException() }
                val value = percent.toInt().coerceIn(0, 100)
                if (value >= 100) report("下載資料完成，正在合併／轉檔…")
                else report("正在下載 $value%", value)
            }
            checkCancelled()
            val ext = if (format == 2) "mp3" else "mp4"
            val file = task.listFiles()?.filter { it.isFile && it.extension == ext && it.length() > 0 }
                ?.singleOrNull() ?: error("下載程序沒有產生完整的 $ext 檔案")
            synchronized(lock) { checkCancelled(); publishing = true }
            report("正在儲存到下載／MediaSare…")
            val mime = if (format == 2) "audio/mpeg" else "video/mp4"
            val uri = publish(file, mime)
            val message = "下載完成：${file.name}\n位置：下載 / MediaSare"
            DownloadState(message = message, progress = 100, uri = uri.toString(), mime = mime).save(this)
            stopForeground(STOP_FOREGROUND_DETACH)
            getSystemService(NotificationManager::class.java).notify(NOTICE, notification("下載完成：${file.name}", 100, false))
        } catch (e: Exception) {
            val message = if (cancelled || e is CancellationException) "已取消下載。"
                else "下載失敗。請確認網址、網路及影片是否公開。\n${e.localizedMessage.orEmpty().takeLast(900)}"
            DownloadState(message = message).save(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
        } finally {
            task.deleteRecursively()
            running = false
            stopSelf()
        }
    }

    private fun publish(file: File, mime: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MediaSare")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("無法建立下載檔案，請確認手機剩餘空間")
        try {
            val stream = contentResolver.openOutputStream(uri) ?: error("無法開啟儲存檔案")
            stream.use { output -> file.inputStream().use { input -> input.copyTo(output) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            check(contentResolver.update(uri, values, null, null) == 1) { "無法完成儲存" }
            return uri
        } catch (e: Exception) { contentResolver.delete(uri, null, null); throw e }
    }
    private fun checkCancelled() { if (cancelled) throw CancellationException() }
    private fun killProcess() {
        // Do not block the main thread while the native process tree is stopping.
        Thread { runCatching { YoutubeDL.getInstance().destroyProcessById(processId) } }.start()
    }
    private fun report(message: String, progress: Int = -1) {
        DownloadState(true, message, progress).save(this)
        val now = System.currentTimeMillis()
        if (now - lastNotice >= 700) {
            lastNotice = now
            getSystemService(NotificationManager::class.java).notify(NOTICE, notification(message, progress, true))
        }
    }
    private fun notification(message: String, progress: Int, active: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_download).setContentTitle("MediaSare")
            .setContentText(message).setContentIntent(open).setOnlyAlertOnce(true)
            .setOngoing(active).setAutoCancel(!active).apply {
                if (active) {
                    setProgress(100, progress.coerceAtLeast(0), progress < 0)
                    if (!publishing) {
                        val cancel = PendingIntent.getService(this@DownloadService, 1,
                            Intent(this@DownloadService, DownloadService::class.java).setAction(CANCEL), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        addAction(Notification.Action.Builder(null, "取消", cancel).build())
                    }
                }
            }.build()
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        cancelled = true
        killProcess()
        DownloadState(message = "背景下載已達系統時間限制，請重新下載。").save(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        if (running) { cancelled = true; killProcess() }
        running = false
        executor.shutdown()
        super.onDestroy()
    }
}
