package org.rdtoolkit.processing

import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.rdtoolkit.component.Sandbox
import org.rdtoolkit.support.interop.INTENT_EXTRA_RDT_CONFIG_CLOUDWORKS_DNS
import org.rdtoolkit.support.interop.RdtIntentBuilder
import org.rdtoolkit.util.InjectorUtils
import java.io.File

class SessionSubmissionWorker(val appContext: Context, workerParams: WorkerParameters):
        Worker(appContext, workerParams){

    override fun doWork(): Result {
        val sessionId = inputData.getString(RdtIntentBuilder.INTENT_EXTRA_RDT_SESSION_ID)!!
        val cloudworksEndpoint = inputData.getString(INTENT_EXTRA_RDT_CONFIG_CLOUDWORKS_DNS)!!

        Log.i(LOG_TAG, "Submitting session data for $sessionId")

        val session = InjectorUtils.provideSessionRepository(appContext).getTestSession(sessionId)
        //TODO: How to cancel this whole submission if the session is gone somehow


        try {
            CloudworksApi(cloudworksEndpoint, sessionId).submitSessionJson(session)
        } catch(e : Exception) {
            return Result.retry()
        }

        return Result.success()
    }

    companion object {
        const val TAG_SESSION = "worker_submission"
        const val LOG_TAG = "SessionSubmissionWorker"
    }
}

class TraceSubmissionWorker(val appContext: Context, workerParams: WorkerParameters):
        Worker(appContext, workerParams){

    override fun doWork(): Result {
        val sessionId = inputData.getString(RdtIntentBuilder.INTENT_EXTRA_RDT_SESSION_ID)!!
        val cloudworksEndpoint = inputData.getString(INTENT_EXTRA_RDT_CONFIG_CLOUDWORKS_DNS)!!

        Log.i(LOG_TAG, "Submitting trace data for $sessionId")

        val traces = InjectorUtils.provideSessionRepository(appContext).loadTraceEvents(sessionId)

        if (traces.isNotEmpty()) {
            try {
                CloudworksApi(cloudworksEndpoint, sessionId).submitTraceJson(traces)
            } catch (e: Exception) {
                return Result.retry()
            }
        }

        return Result.success()
    }

    companion object {
        const val TAG_TRACES = "worker_traces"
        const val LOG_TAG = "SessionSubmissionWorker"
    }
}

class ImageSubmissionWorker(appContext: Context, workerParams: WorkerParameters):
        Worker(appContext, workerParams){
    override fun doWork(): Result {
        val sessionId = inputData.getString(RdtIntentBuilder.INTENT_EXTRA_RDT_SESSION_ID)!!
        val cloudworksEndpoint = inputData.getString(INTENT_EXTRA_RDT_CONFIG_CLOUDWORKS_DNS)!!
        val key = inputData.getString(DATA_MEDIA_KEY)!!
        val file = File(inputData.getString(DATA_FILE_PATH))

        Log.i(LOG_TAG, "Submitting image $key for $sessionId")

        try {
            CloudworksApi(cloudworksEndpoint, sessionId).submitSessionMedia(key, file)
        } catch(e : Exception) {
            e.printStackTrace()
            return Result.retry()
        }

        return Result.success()
    }

    companion object {
        const val DATA_MEDIA_KEY = "media_key"
        const val DATA_FILE_PATH = "media_file"
        const val TAG_MEDIA = "worker_media_submission"
        const val LOG_TAG = "ImageSubmissionWorker"
    }
}


class SessionPurgeWorker(appContext: Context, workerParams: WorkerParameters):
        Worker(appContext, workerParams){
    override fun doWork(): Result {
        val sessionId = inputData.getString(RdtIntentBuilder.INTENT_EXTRA_RDT_SESSION_ID)!!
        Log.i(LOG_TAG, "Starting session data purge for $sessionId")

        val repo = InjectorUtils.provideSessionRepository(applicationContext)
        repo.clearSession(sessionId)
        repo.clearTraceEvents(sessionId)

        val root = Sandbox(applicationContext, sessionId).getFileRoot()
        Log.i(LOG_TAG, "Clearing ${root.list().size} files")
        root.deleteRecursively()

        return Result.success()
    }

    companion object {
        const val TAG_PURGE = "worker_purge"
        const val LOG_TAG = "SessionPurgeWorker"
    }
}

fun ListenableWorker.cloudworks() : CloudworksApi {
    val sessionId = this.inputData.getString(RdtIntentBuilder.INTENT_EXTRA_RDT_SESSION_ID)!!
    val cloudworksEndpoint = this.inputData.getString(INTENT_EXTRA_RDT_CONFIG_CLOUDWORKS_DNS)!!

    return CloudworksApi(sessionId, cloudworksEndpoint, this::isStopped)

}