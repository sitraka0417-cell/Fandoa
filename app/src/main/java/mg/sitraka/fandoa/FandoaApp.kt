package mg.sitraka.fandoa

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager

class FandoaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDER, "Rappels de paiement", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val alarm = NotificationChannel(CH_ALARM, "Alarmes de paiement", NotificationManager.IMPORTANCE_HIGH)
        alarm.setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        alarm.enableVibration(true)
        nm.createNotificationChannel(alarm)
    }

    companion object {
        const val CH_REMINDER = "rappel"
        const val CH_ALARM = "alarme"
    }
}
