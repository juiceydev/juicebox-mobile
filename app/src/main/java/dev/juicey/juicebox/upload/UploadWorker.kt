package dev.juicey.juicebox.upload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.juicey.juicebox.JuiceboxApplication

/**
 * Runs one queued upload as ordinary background work. WorkManager owns the
 * wakelock, so the transfer survives the share sheet closing, the screen going
 * off, or the activity being destroyed - and it shows no progress notification.
 */
class UploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val localId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val container = (applicationContext as JuiceboxApplication).container
        val task = container.tasks.snapshot().firstOrNull { it.localId == localId }
            ?: return Result.success()

        if (task.status == TaskStatus.DONE) return Result.success()

        // A previous run may have died mid-transfer; start it over.
        if (task.status == TaskStatus.RUNNING) {
            container.tasks.update(localId) {
                it.copy(status = TaskStatus.QUEUED, error = null)
            }
        }

        val finished = container.runner.execute(task)
        return if (finished.status == TaskStatus.DONE) Result.success() else Result.failure()
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
    }
}
