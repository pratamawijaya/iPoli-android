package io.ipoli.android.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Build.VERSION_CODES.N
import android.support.v4.app.NotificationCompat
import android.view.ContextThemeWrapper
import android.widget.Toast
import com.evernote.android.job.Job
import com.evernote.android.job.JobManager
import com.evernote.android.job.JobRequest
import com.evernote.android.job.util.support.PersistableBundleCompat
import io.ipoli.android.R
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.di.ControllerModule
import io.ipoli.android.common.di.JobModule
import io.ipoli.android.iPoliApp
import io.ipoli.android.quest.Quest
import io.ipoli.android.reminder.view.ReminderNotificationOverlay
import io.ipoli.android.reminder.view.ReminderNotificationViewModel
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.threeten.bp.LocalDate
import org.threeten.bp.temporal.ChronoUnit
import space.traversal.kapsule.Injects
import space.traversal.kapsule.Kapsule
import java.util.*


/**
 * Created by Venelin Valkov <venelin@ipoli.io>
 * on 10/26/17.
 */

class ReminderNotificationJob : Job(), Injects<ControllerModule> {

    override fun onRunJob(params: Job.Params): Job.Result {

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = "iPoli"
        val channelName = "iPoli"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(id, channelName, importance)
            channel.description = "Reminder notification"
            channel.enableLights(true)
            channel.enableVibration(true)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            notificationManager.createNotificationChannel(channel)
        }

        val kap = Kapsule<JobModule>()
        val findQuestsToRemindUseCase by kap.required { findQuestToRemindUseCase }
        val snoozeQuestUseCase by kap.required { snoozeQuestUseCase }
        val completeQuestUseCase by kap.required { completeQuestUseCase }
        kap.inject(iPoliApp.jobModule(context))

        val c = ContextThemeWrapper(context, R.style.Theme_iPoli)

        launch(UI) {

            val quests = findQuestsToRemindUseCase.execute(params.extras.getLong("start", -1))

            quests.forEach {

                val reminder = it.reminder!!
                val message = reminder.message.let { if (it.isEmpty()) "Ready for a quest?" else it }

                val startTimeMessage = startTimeMessage(it)

                val questName = it.name
                val notificationId = showNotification(questName, message, notificationManager)

                ReminderNotificationOverlay(ReminderNotificationViewModel(it.id, questName, message, startTimeMessage),
                    object : ReminderNotificationOverlay.OnClickListener {
                        override fun onDismiss() {
                            notificationManager.cancel(notificationId)
                        }

                        override fun onSnooze() {
                            notificationManager.cancel(notificationId)
                            snoozeQuestUseCase.execute(it.id)
                            Toast.makeText(c, "Quest snoozed", Toast.LENGTH_SHORT).show()
                        }

                        override fun onDone() {
                            notificationManager.cancel(notificationId)
                            completeQuestUseCase.execute(it.id)
                            Toast.makeText(c, "Quest completed", Toast.LENGTH_SHORT).show()
                        }
                    }).show(c)
            }
        }

        return Job.Result.SUCCESS
    }

    private fun showNotification(questName: String, message: String, notificationManager: NotificationManager): Int {
        val notification = createNotification(questName, message)
        val notificationId = Random().nextInt()
        notificationManager.notify(notificationId, notification)
        return notificationId
    }

    private fun createNotification(title: String, message: String) =
        NotificationCompat.Builder(context, "iPoli")
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(message)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()

    private fun startTimeMessage(quest: Quest): String {
        val daysDiff = ChronoUnit.DAYS.between(quest.scheduledDate, LocalDate.now())
        return if (daysDiff > 0) {
            "Starts in $daysDiff day(s)"
        } else {
            val minutesDiff = quest.startTime!!.toMinuteOfDay() - Time.now().toMinuteOfDay()

            if (minutesDiff > Time.MINUTES_IN_AN_HOUR) {
                "Starts at ${quest.startTime.toString(false)}"
            } else if(minutesDiff > 0){
                "Starts in $minutesDiff min"
            } else {
                "Starts now"
            }
        }
    }

    companion object {
        val TAG = "job_reminder_notification_tag"
    }
}

class ReminderScheduler {
    fun schedule(time: Long) {
        JobManager.instance().cancelAllForTag(ReminderNotificationJob.TAG)

        val bundle = PersistableBundleCompat()
        bundle.putLong("start", time)
        JobRequest.Builder(ReminderNotificationJob.TAG)
            .setExtras(bundle)
            .setExact(time - System.currentTimeMillis())
            .build()
            .schedule()
    }

}