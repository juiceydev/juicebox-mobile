package dev.juicey.juicebox.upload

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.juicey.juicebox.JuiceboxApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the "Retry" action on a failed-upload notification. */
class UploadActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RETRY) return
        val localId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val app = context.applicationContext as JuiceboxApplication
        UploadNotifications.cancel(context, localId)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.tasks.update(localId) {
                    it.copy(status = TaskStatus.QUEUED, error = null)
                }
                UploadQueue.retry(context, localId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_RETRY = "dev.juicey.juicebox.action.RETRY_UPLOAD"
        const val EXTRA_TASK_ID = "task_id"
    }
}
