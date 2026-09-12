package kr.pkm.shift

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.text.format.DateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 근무 종류별 출근 알람. 해당 근무인 날에만 울립니다. */
object Alarms {
    private const val PREFS = "shift"
    private const val KEY = "alarm:"          // alarm:DAY = 분 단위 시각, 없으면 꺼짐
    private const val CHANNEL = "shift_alarm"
    const val EXTRA_SHIFT = "kr.pkm.shift.ALARM_SHIFT"

    /** 알람을 걸 수 있는 근무 — 주기에 도는 네 가지만 */
    val TARGETS = Shift.CYCLE

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 자정 기준 분. 꺼져 있으면 null. */
    fun timeOf(ctx: Context, shift: Shift): Int? =
        prefs(ctx).getInt(KEY + shift.name, -1).takeIf { it >= 0 }

    fun setTime(ctx: Context, shift: Shift, minutes: Int?) {
        prefs(ctx).edit().putInt(KEY + shift.name, minutes ?: -1).apply()
        rescheduleAll(ctx)
    }

    /** 폰이 12시간제면 오전/오후로, 24시간제면 0~23 시로 보여준다. */
    fun label(ctx: Context, minutes: Int?): String {
        if (minutes == null) return "꺼짐"
        val h = minutes / 60
        val m = minutes % 60
        if (DateFormat.is24HourFormat(ctx)) return "%02d:%02d".format(h, m)
        val half = if (h % 12 == 0) 12 else h % 12
        return "%s %d:%02d".format(if (h < 12) "오전" else "오후", half, m)
    }

    private fun alarmManager(ctx: Context) =
        ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun intentFor(ctx: Context, shift: Shift): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 200 + shift.ordinal,
            Intent(ctx, AlarmReceiver::class.java).putExtra(EXTRA_SHIFT, shift.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** 오늘 이후로 이 근무가 처음 오는 날의 알람 시각. 근무가 설정 전이면 null. */
    private fun nextTime(ctx: Context, shift: Shift, minutes: Int): Long? {
        if (!Schedule.isConfigured(ctx)) return null
        val now = LocalDateTime.now()
        for (d in 0..400L) {
            val date = LocalDate.now().plusDays(d)
            if (Schedule.at(ctx, date) != shift) continue
            val at = date.atStartOfDay().plusMinutes(minutes.toLong())
            if (at.isAfter(now)) {
                return at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        return null
    }

    /** 근무표나 알람 설정이 바뀔 때마다 다시 계산해 건다. */
    fun rescheduleAll(ctx: Context) {
        val am = alarmManager(ctx)
        for (shift in TARGETS) {
            val pi = intentFor(ctx, shift)
            am.cancel(pi)
            val minutes = timeOf(ctx, shift) ?: continue
            val at = nextTime(ctx, shift, minutes) ?: continue
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (_: SecurityException) {
                // 정확한 알람 권한이 없으면 근사 알람으로라도 건다
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "출근 알람", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "근무 종류별 출근 시각 알림"
        ch.enableVibration(true)
        // 알람 볼륨으로 울리도록 한다 (벨소리 볼륨과 별개)
        ch.setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        nm.createNotificationChannel(ch)
    }

    fun notify(ctx: Context, shift: Shift) {
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 300 + shift.ordinal, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("${shift.full} 출근")
            .setContentText("오늘은 ${shift.full}입니다")
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(open)
            .apply { if (Build.VERSION.SDK_INT >= 31) setFullScreenIntent(open, true) }
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(200 + shift.ordinal, n)
    }
}
