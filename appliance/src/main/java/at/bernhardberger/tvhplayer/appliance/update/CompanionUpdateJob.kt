package at.bernhardberger.tvhplayer.appliance.update

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

class CompanionUpdateJob : JobService() {
    private var cancelled = AtomicBoolean(false)

    override fun onStartJob(params: JobParameters): Boolean {
        val cancellation = AtomicBoolean(false)
        cancelled = cancellation
        CompanionUpdater.check(this, manual = false, cancelled = cancellation) {
            Handler(Looper.getMainLooper()).post {
                if (!cancellation.get()) jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        CompanionUpdater.cancel(cancelled)
        return false // Only another screen-off event schedules another attempt.
    }

    companion object {
        const val JOB_ID = 4001
        fun schedule(context: Context) {
            context.getSystemService(JobScheduler::class.java).schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, CompanionUpdateJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build())
        }

        fun cancel(context: Context) {
            CompanionUpdater.cancelAutomatic()
            context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        }
    }
}
