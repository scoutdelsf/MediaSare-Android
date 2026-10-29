package org.mediasare.android

import java.net.URI

/** Shared captions often contain a title followed by the real URL. */
object SharedUrl {
    private val links = Regex("https?://[^\\s<>\"\\u3000]+", RegexOption.IGNORE_CASE)
    fun extract(text: String): String? = links.findAll(text).map { it.value.trimEnd('.', ',', '。', '，', ')', '）', ']', '】') }
        .firstOrNull { candidate ->
            runCatching {
                val uri = URI(candidate)
                uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
            }.getOrDefault(false)
        }
}
