package dev.juicey.juicebox

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * A transparent, no-UI activity used only to write the clipboard: background
 * services are not allowed to touch it on Android 10+, but a resumed activity is.
 */
class CopyLinkActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = intent?.getStringExtra(EXTRA_LINK).orEmpty()
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID)
        if (link.isNotBlank()) {
            val container = (application as JuiceboxApplication).container
            val copied = container.runner.copyLink(link)
            if (copied) {
                Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show()
                if (taskId != null) {
                    container.runner.markCopied(taskId)
                }
            } else {
                Toast.makeText(this, "Could not copy the link", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_LINK = "link"
        const val EXTRA_TASK_ID = "task_id"
    }
}
