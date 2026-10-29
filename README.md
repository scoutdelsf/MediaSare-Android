# MediaSare Android 0.1.0

第一版原生 Android 專案，Android 10 以上。尚未完成 APK 編譯與實機驗證。

## 操作

1. 在 YouTube、Facebook、Instagram、Threads 或瀏覽器，找到影片的「分享／分享至其他應用程式」。
2. 選擇 **MediaSare**。若沒看到，展開「更多」。
3. 確認帶入的影片網址，選擇 MP4 最高 1080p、MP4 最高 720p 或 MP3。
4. 按「開始下載」。可切回原本的 App，從通知或主畫面查看進度及取消。
5. 完成後按「開啟剛下載的檔案」，或到手機 **下載／MediaSare** 查看。

也可直接開啟 App，按「貼上剪貼簿」。分享只會帶入網址，不會未經確認就開始下載。

## 建置

需要 Android Studio、JDK 17、Android SDK Platform 35、Build Tools 35.0.0、Gradle 8.11.1，以及可連線到 Google Maven、Maven Central、Gradle 的環境。

1. 使用 Android Studio 開啟本資料夾。
2. 專案設定選擇 Gradle 8.11.1 的本機安裝目錄與 JDK 17。此環境無法下載官方 wrapper，因此未附 gradle-wrapper.jar，不以空白或來源不明的檔案代替。
3. 同步專案、安裝缺少的 SDK，執行 `gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`。
4. APK 位置：`app/build/outputs/apk/debug/app-debug.apk`。
5. 將 APK 傳到手機並允許安裝此來源的應用程式，再安裝測試。

Windows 也可執行 `build-android.ps1 -GradleHome C:\工具\gradle-8.11.1 -JavaHome C:\工具\jdk-17 -AndroidSdk C:\工具\android-sdk`。

## 實作範圍

- ACTION_SEND / text/plain，支援冷啟動與已開啟 App 接收分享。
- 從分享標題與說明擷取第一個有效 HTTPS 網址。
- 手機內執行 yt-dlp、FFmpeg；不依賴 Windows 電腦或自架伺服器。
- dataSync 前景服務、進度通知、取消、合併與轉檔狀態。
- 使用 MediaStore 發布完整檔案，不要求廣泛儲存權限。
- 暫存放在 App 快取，失敗或取消時刪除；程序中斷的舊暫存於下次下載清除。
- 每日最多一次嘗試更新 yt-dlp；更新失敗仍嘗試使用現有核心。
- 目前不包含登入／Cookies、佇列、播放清單、下載歷史、背景自動重試、斷點恢復、縮圖與字幕。
- MP4 限制解析度，但來源編碼仍可能不受個別手機播放器支援；尚未加入 H.264 強制轉碼。
- 公開影片依平台解析器及來源限制而異，不保證列出平台全部可用。
- 直播未列入驗收。私人、需登入、年齡／地區限制影片可能失敗。
- 合併、轉檔與發布會額外使用儲存空間，請預留影片大小約兩倍以上空間。

## 驗收清單（待實機執行）

- Android 10、13、15 各安裝、啟動、分享帶入網址。
- 分別測試 YouTube、Facebook、Instagram、Threads 的公開短影片；記錄實際網址及結果。
- 含標題的分享文字、重複分享、旋轉畫面、複製貼上。
- 720p／1080p／MP3 檔案內容、聲音、播放、資料夾位置。
- 下載時切換 App／關螢幕、拒絕通知權限、關閉後重開。
- 初始化、解析、下載、合併各階段取消，不留下公開的半成品。
- 網路中斷、空間不足、無效／不公開影片：顯示失敗並可重新操作。
- 重複下載同一影片不覆寫舊檔；強制終止後重新開啟會顯示中斷。

## 依賴與授權

使用 [youtubedl-android](https://github.com/yausername/youtubedl-android) 0.18.1 的 library 與 ffmpeg（上游 README 的 Maven 座標）。上游 Kotlin API 相容性需經首次 Gradle 編譯確認。內含／間接使用 yt-dlp、Python、FFmpeg 與其他元件；正式發布前須依實際 AAR 內容整理各元件授權、通知與適用的原始碼提供義務。此專案尚未製作商店發布包或宣稱完成授權審核。
