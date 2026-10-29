package org.mediasare.android

import android.content.Context

data class DownloadState(
    val active: Boolean = false,
    val message: String = "分享影片網址到 MediaSare，或在下方貼上網址。",
    val progress: Int = -1,
    val uri: String = "",
    val mime: String = "video/mp4"
) {
    fun save(context: Context) {
        context.getSharedPreferences("download", Context.MODE_PRIVATE).edit()
            .putBoolean("active", active).putString("message", message)
            .putInt("progress", progress).putString("uri", uri).putString("mime", mime).apply()
    }
    companion object {
        fun read(context: Context): DownloadState {
            val p = context.getSharedPreferences("download", Context.MODE_PRIVATE)
            val storedActive = p.getBoolean("active", false)
            // A killed service cannot resume this prototype's partial task.
            val interrupted = storedActive && !DownloadService.running
            return DownloadState(
                storedActive && !interrupted,
                if (interrupted) "上次下載已中斷，請重新下載。" else p.getString("message", DownloadState().message)!!,
                if (interrupted) -1 else p.getInt("progress", -1),
                p.getString("uri", "")!!, p.getString("mime", "video/mp4")!!
            )
        }
    }
}
