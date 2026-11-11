package dev.juicey.juicebox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import dev.juicey.juicebox.core.friendlyMessage
import dev.juicey.juicebox.core.model.ShareLinkMode
import dev.juicey.juicebox.upload.UploadQueue
import dev.juicey.juicebox.upload.UploadTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Share-sheet entry point. It reads the shared payload into app storage while
 * the URI grant is alive, queues the upload, and closes. No confirmation, no
 * foreground service, no progress UI - the upload itself runs in WorkManager.
 */
class ShareActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val payload = parse(intent)
        if (payload.isEmpty) {
            finish()
            return
        }

        val container = (application as JuiceboxApplication).container

        scope.launch {
            try {
                val config = container.core.config.value
                    ?: runCatching { container.core.connect() }.getOrNull()
                val settings = container.core.settings.value
                val mode = when {
                    payload.uris.isNotEmpty() -> ShareLinkMode.TEXT
                    payload.text.isNullOrBlank() -> ShareLinkMode.TEXT
                    payload.looksLikeUrl && config?.cobalt == true ->
                        if (settings.shareLinkMode == ShareLinkMode.TEXT) {
                            ShareLinkMode.TEXT
                        } else {
                            ShareLinkMode.FETCH
                        }
                    else -> ShareLinkMode.TEXT
                }

                val ttl = container.core.ttlForUpload()
                val tasks = withContext(Dispatchers.IO) {
                    val created = mutableListOf<UploadTask>()
                    when {
                        payload.uris.isNotEmpty() -> {
                            for (uri in payload.uris) {
                                created += container.taskFactory.fromUri(uri, ttl)
                            }
                        }
                        payload.text != null && mode == ShareLinkMode.FETCH -> {
                            created += container.taskFactory.fromFetch(payload.text, ttl)
                        }
                        payload.text != null -> {
                            created += container.taskFactory.fromText(payload.text, ttl)
                        }
                    }
                    created
                }

                if (tasks.isEmpty()) {
                    toast("Nothing to share")
                } else {
                    UploadQueue.enqueueAll(this@ShareActivity, tasks.map { it.localId })
                    val message = when {
                        payload.uris.isNotEmpty() && tasks.size > 1 ->
                            "Queued ${tasks.size} files - running in the background"
                        payload.uris.isNotEmpty() ->
                            "Queued ${tasks.first().filename} - running in the background"
                        mode == ShareLinkMode.FETCH ->
                            "On it! Uploading this media."
                        else ->
                            "Saved - uploading to Juicebox in the background"
                    }
                    toast(message)
                }
            } catch (error: Throwable) {
                toast(error.friendlyMessage())
            } finally {
                finish()
                overridePendingTransition(0, 0)
            }
        }
    }

    private fun parse(intent: Intent?): SharePayload {
        if (intent == null) return SharePayload()
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val uris = when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(
                intent,
                Intent.EXTRA_STREAM,
                Uri::class.java,
            ).orEmpty()
            Intent.ACTION_SEND -> listOfNotNull(
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java),
            )
            else -> emptyList()
        }
        return SharePayload(uris = uris, text = text)
    }

    private fun toast(message: String) {
        runCatching { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

data class SharePayload(
    val uris: List<Uri> = emptyList(),
    val text: String? = null,
) {
    val isEmpty: Boolean get() = uris.isEmpty() && text.isNullOrBlank()
    val looksLikeUrl: Boolean
        get() = text?.trim()?.let { it.startsWith("http://") || it.startsWith("https://") } == true
}
