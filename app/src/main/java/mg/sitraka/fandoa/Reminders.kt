package mg.sitraka.fandoa

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject

object Reminders {
    private const val PREFS = "reminders"

    fun syncAll(ctx: Context, list: List<Payment>) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = JSONObject(sp.getString("items", "{}") ?: "{}")
        val now = System.currentTimeMillis()
        val fresh = JSONObject()
        for (p in list) {
            val at = p.reminderAt
            if (at != null && at > now && (p.alarm || p.notif) && p.remaining > 0.0) {
                fresh.put(
                    p.id, JSONObject()
                        .put("at", at).put("alarm", p.alarm).put("title", p.name)
                        .put("body", "${p.reason.ifBlank { p.category }} · reste ${p.remaining.ar()}")
                )
            }
        }
        val it = old.keys()
        while (it.hasNext()) {
            val k = it.next()
            if (!fresh.has(k)) cancel(ctx, k)
        }
        val ks = fresh.keys()
        while (ks.hasNext()) {
            val k = ks.next()
            schedule(ctx, k, fresh.getJSONObject(k))
        }
        sp.edit().putString("items", fresh.toString()).apply()
    }

    fun rescheduleFromStore(ctx: Context) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val items = JSONObject(sp.getString("items", "{}") ?: "{}")
        val now = System.currentTimeMillis()
        val ks = items.keys()
        while (ks.hasNext()) {
            val k = ks.next()
            val o = items.getJSONObject(k)
            if (o.getLong("at") > now) schedule(ctx, k, o)
        }
    }

    private fun pending(ctx: Context, id: String, o: JSONObject?): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
        i.putExtra("id", id)
        if (o != null) {
            i.putExtra("title", o.optString("title"))
            i.putExtra("body", o.optString("body"))
            i.putExtra("alarm", o.optBoolean("alarm"))
        }
        return PendingIntent.getBroadcast(
            ctx, id.hashCode(), i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    @SuppressLint("MissingPermission")
    private fun schedule(ctx: Context, id: String, o: JSONObject) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx, id, o)
        val at = o.getLong("at")
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancel(ctx: Context, id: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx, id, null))
    }
}

class AlarmReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        val title = i.getStringExtra("title") ?: ""
        val body = i.getStringExtra("body") ?: ""
        val alarm = i.getBooleanExtra("alarm", false)
        val open = PendingIntent.getActivity(
            c, 0,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(c, if (alarm) FandoaApp.CH_ALARM else FandoaApp.CH_REMINDER)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Paiement à effectuer : $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(if (alarm) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (alarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .build()
        val nm = NotificationManagerCompat.from(c)
        if (nm.areNotificationsEnabled()) nm.notify(id.hashCode(), n)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED) Reminders.rescheduleFromStore(c)
    }
}
