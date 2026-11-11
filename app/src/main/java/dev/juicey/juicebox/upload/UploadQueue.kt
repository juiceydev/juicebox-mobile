package dev.juicey.juicebox.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.juicey.juicebox.AppContainer
import java.util.concurrent.TimeUnit

/** Enqueues persisted tasks as WorkManager jobs, one unique job per task. */
object UploadQueue {

    private const val PREFIX = "juicebox-upload-"
    const val TAG = "juicebox-upload"

    fun enqueue(context: Context, taskId: String, replace: Boolean = false) {
        val request: OneTimeWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf(UploadWorker.KEY_TASK_ID to taskId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            PREFIX + taskId,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun enqueueAll(context: Context, taskIds: List<String>) {
        taskIds.forEach { enqueue(context, it) }
    }

    fun retry(context: Context, taskId: String) {
        enqueue(context, taskId, replace = true)
    }

    fun cancel(context: Context, taskId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(PREFIX + taskId)
    }

    /** Requeues anything that was mid-flight when the process died. */
    suspend fun resumePending(container: AppContainer) {
        val pending = container.tasks.snapshot()
            .filter { it.status == TaskStatus.QUEUED || it.status == TaskStatus.RUNNING }
        for (task in pending) {
            if (task.status == TaskStatus.RUNNING) {
                container.tasks.update(task.localId) {
                    it.copy(status = TaskStatus.QUEUED, error = null)
                }
            }
            enqueue(container.appContext, task.localId)
        }
    }
}
