package org.rdtoolkit.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.rdtoolkit.MainActivity
import org.rdtoolkit.R
import org.rdtoolkit.model.session.SessionRepository
import org.rdtoolkit.support.interop.RdtIntentBuilder.Companion.ACTION_TEST_CAPTURE
import org.rdtoolkit.support.interop.RdtIntentBuilder.Companion.INTENT_EXTRA_RDT_SESSION_ID
import org.rdtoolkit.support.model.session.FLAG_CALLING_PACKAGE
import org.rdtoolkit.support.model.session.STATUS
import org.rdtoolkit.support.model.session.TestSession
import org.rdtoolkit.util.InjectorUtils
import org.rdtoolkit.util.getFormattedTimeForSpan


const val CHANNEL_ID_COUNTDOWN ="Test"
const val CHANNEL_ID_FIRE ="Triggered"

const val SERVICE_TIMER = 1
const val NOTIFICATION_TAG_TEST_ID = "test_id"

const val COUNTDOWN_INTERVAL_MS = 500L
const val EXPIRED_NOTIFICATION_TIMEOUT_MS = 1000 * 30L

class TestTimerService : LifecycleService() {

    private lateinit var sessionRepository : SessionRepository;
    val pendingTimers : MutableMap<String, CountDownTimer> = HashMap()

    override fun onCreate() {
        super.onCreate()
        sessionRepository = InjectorUtils.provideSessionRepository(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job

        createNotificationChannels();

        var testId : String = intent?.getStringExtra(NOTIFICATION_TAG_TEST_ID)!!;

        var builder = getNotificationBuilder();

        var baselineNotification = builder.build()

        NotificationManagerCompat.from(this)
                .notify(testId, SERVICE_TIMER, baselineNotification)

        startForeground(SERVICE_TIMER, baselineNotification)

        lifecycleScope.launch(Dispatchers.IO) {
            var session = sessionRepository.getTestSession(testId)

            launch(Dispatchers.Main) {
                startResolvingTestTimer(session)
            }

        }
        // If we get killed, after returning from here, restart
        return START_REDELIVER_INTENT
    }

    private fun checkForServiceClosure() {
        synchronized(pendingTimers) {
            if(this.pendingTimers.isEmpty()) {
                this.stopForeground(true)
            }
        }
    }

    private fun startResolvingTestTimer(session: TestSession) {
        val sessionId = session.sessionId
        synchronized(pendingTimers) {
            var builder = getNotificationBuilder()
            setReturnIntent(builder, session)
            val timer = object : CountDownTimer(session.timeResolved.time - System.currentTimeMillis(), 500) {
                override fun onTick(millisUntilFinished: Long) {
                    builder.setContentTitle(getString(R.string.service_message_resolving_title).format(session.configuration.flavorText))
                    builder.setContentText(getString(R.string.service_message_resolving_text).format(getFormattedTimeForSpan(millisUntilFinished)))
                    NotificationManagerCompat.from(this@TestTimerService)
                            .notify(sessionId, SERVICE_TIMER, builder.build())

                    val timer = this
                    lifecycleScope.launch(Dispatchers.IO) {
                        if (sessionRepository.getTestSession(sessionId).state != STATUS.RUNNING) {
                            synchronized(pendingTimers) {
                                timer.cancel()
                                NotificationManagerCompat.from(this@TestTimerService).cancel(sessionId,
                                        SERVICE_TIMER);
                                pendingTimers.remove(sessionId)
                                checkForServiceClosure()
                            }
                        }
                    }
                }

                override fun onFinish() {
                    synchronized(pendingTimers) {
                        var manager = NotificationManagerCompat.from(this@TestTimerService)
                        manager.cancel(sessionId, SERVICE_TIMER);
                        pendingTimers.remove(sessionId)
                        beginTestReady(session)
                        checkForServiceClosure()
                    }
                }
            }

            pendingTimers.set(session.sessionId, timer);
            timer.start();
        }
    }

    private fun setReturnIntent(builder: NotificationCompat.Builder, session: TestSession) {
        var launchIntent = Intent()
        launchIntent.action = ACTION_TEST_CAPTURE
        launchIntent.putExtra(INTENT_EXTRA_RDT_SESSION_ID, session.sessionId)

        if (FLAG_CALLING_PACKAGE in session.configuration.flags) {
            getPackageManager().getLaunchIntentForPackage(session.configuration.flags[FLAG_CALLING_PACKAGE]!!)?.let {
                launchIntent = it
            }
        }
        val notificationIntent = PendingIntent.getActivity(this,
                session.sessionId.hashCode(), launchIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        builder.setContentIntent(notificationIntent)
    }

    private fun beginTestReady(session: TestSession) {
        var manager = NotificationManagerCompat.from(this)

        var builder = getFinishedNotificationBuilder().setContentTitle(getString(R.string.service_message_ready_text).format(session.configuration.flavorText))
        setReturnIntent(builder, session)
        manager.notify(session.sessionId, SERVICE_TIMER,
                builder.setContentText(getText(R.string.service_message_ready_text)).build())

        if (session.timeExpired == null) {
            //No expirey date set for this test
            return;
        }

        val sessionId = session.sessionId

        session.timeExpired?.let {
            synchronized(pendingTimers) {
                var timer = object : CountDownTimer(it.time - System.currentTimeMillis(), COUNTDOWN_INTERVAL_MS) {
                    override fun onTick(millisUntilFinished: Long) {
                        builder.setContentText(getString(R.string.service_message_valid_text).format(getFormattedTimeForSpan(millisUntilFinished)))
                        NotificationManagerCompat.from(this@TestTimerService)
                                .notify(sessionId, SERVICE_TIMER, builder.build())

                        val timer = this
                        lifecycleScope.launch(Dispatchers.IO) {
                            if (sessionRepository.getTestSession(sessionId).state != STATUS.RUNNING) {
                                synchronized(pendingTimers) {
                                    timer.cancel()
                                    NotificationManagerCompat.from(this@TestTimerService).cancel(sessionId,
                                            SERVICE_TIMER);
                                    pendingTimers.remove(sessionId)
                                    checkForServiceClosure()
                                }
                            }
                        }
                    }


                    override fun onFinish() {
                        synchronized(pendingTimers) {
                            builder.setContentTitle(getString(R.string.service_message_expired_title).format(session.configuration.flavorText))
                            builder.setContentText(getString(R.string.service_message_expired_text))
                            NotificationManagerCompat.from(this@TestTimerService)
                                    .notify(session.sessionId, SERVICE_TIMER, builder.setTimeoutAfter(EXPIRED_NOTIFICATION_TIMEOUT_MS).build())
                            pendingTimers.remove(session.sessionId)
                            checkForServiceClosure()
                        }

                    }
                }
                pendingTimers.set(session.sessionId, timer);
                timer.start();
            }
        }
    }

    private fun createNotificationChannels() {
        val defaultSoundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val countdown = NotificationChannel(CHANNEL_ID_COUNTDOWN,
                    getString(R.string.service_channel_running_name),
                    NotificationManager.IMPORTANCE_LOW);
            countdown.description = getString(R.string.service_channel_running_description)

            val fireChannel = NotificationChannel(CHANNEL_ID_FIRE,
                    getString(R.string.service_channel_triggered_name),
                    NotificationManager.IMPORTANCE_HIGH)

            fireChannel.enableLights(true)

            fireChannel.enableVibration(true)
            fireChannel.setSound(defaultSoundUri,
                    AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
            )

            countdown.description = getString(R.string.service_channel_triggered_description)

            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(countdown)
            notificationManager.createNotificationChannel(fireChannel)
        }

    }

    private fun getNotificationBuilder(): NotificationCompat.Builder {
        // The PendingIntent to launch our activity if the user selects
        // this notification
        val title = getString(R.string.service_pending_intent_title_running)
        val contentIntent = PendingIntent.getActivity(this,
                0, Intent(this, MainActivity::class.java), 0)
        return NotificationCompat.Builder(this, CHANNEL_ID_COUNTDOWN)
                .setOngoing(true)
                .setContentTitle(title)
                .setSmallIcon(R.drawable.ic_baseline_timer_24)
                .setContentIntent(contentIntent)
    }

    private fun getFinishedNotificationBuilder(): NotificationCompat.Builder {
        val defaultSoundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // The PendingIntent to launch our activity if the user selects
        // this notification
        val title = getString(R.string.service_message_ready_text)
        val contentIntent = PendingIntent.getActivity(this,
                0, Intent(this, MainActivity::class.java), 0)
        return NotificationCompat.Builder(this, CHANNEL_ID_FIRE)
                .setContentTitle(title)
                .setSmallIcon(R.drawable.ic_baseline_timer_24)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_ALARM)
                //Only relevant for SDK 21 - 26
                .setSound(defaultSoundUri)
                .setPriority(NotificationManager.IMPORTANCE_HIGH)
                .setDefaults(Notification.DEFAULT_ALL)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        // We don't provide binding, so return null
        return null
    }

    override fun onDestroy() {
        synchronized(pendingTimers) {
            for (runningTimer in pendingTimers.entries) {
                runningTimer.value.cancel()
                NotificationManagerCompat.from(this@TestTimerService).cancel(runningTimer.key,
                        SERVICE_TIMER);
            }
        }
        super.onDestroy()
    }
}