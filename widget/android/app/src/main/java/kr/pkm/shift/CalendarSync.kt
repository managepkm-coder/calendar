package kr.pkm.shift

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 캘린더 자동 연동.
 * 내보낸 일정은 그때 만든 기간만큼만 존재하므로, 근무가 바뀔 때와
 * 남은 기간이 줄어들 때 알아서 다시 내보내 항상 앞쪽이 채워지도록 한다.
 */
object CalendarSync {
    private const val PREFS = "shift"
    private const val KEY_CAL = "sync.calendar"
    private const val KEY_MONTHS = "sync.months"
    private const val KEY_LAST = "sync.lastDay"     // epochDay

    /** 이 간격이 지나면 기간을 다시 채운다 */
    private const val REFRESH_DAYS = 28L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context) = prefs(ctx).getLong(KEY_CAL, -1L) >= 0

    /** 연동 대상: 캘린더 id 와 유지할 개월 수 */
    fun target(ctx: Context): Pair<Long, Long>? {
        val id = prefs(ctx).getLong(KEY_CAL, -1L)
        if (id < 0) return null
        return id to prefs(ctx).getLong(KEY_MONTHS, 6L)
    }

    fun enable(ctx: Context, calendarId: Long, months: Long) {
        prefs(ctx).edit()
            .putLong(KEY_CAL, calendarId)
            .putLong(KEY_MONTHS, months)
            .remove(KEY_LAST)
            .apply()
    }

    fun disable(ctx: Context) {
        prefs(ctx).edit().remove(KEY_CAL).remove(KEY_MONTHS).remove(KEY_LAST).apply()
    }

    private fun canWrite(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** 지금 다시 내보낸다. 일정이 수백 개라 항상 background 에서 수행한다. */
    fun syncNow(ctx: Context, onDone: ((Int) -> Unit)? = null) {
        val (id, months) = target(ctx) ?: return
        if (!canWrite(ctx) || !Schedule.isConfigured(ctx)) return
        val app = ctx.applicationContext
        Thread {
            val n = runCatching { CalendarExport.export(app, id, months) }.getOrElse { -1 }
            if (n >= 0) {
                prefs(app).edit().putLong(KEY_LAST, LocalDate.now().toEpochDay()).apply()
            }
            onDone?.let { cb -> Handler(Looper.getMainLooper()).post { cb(n) } }
        }.start()
    }

    /** 근무표가 바뀌었을 때 — 바로 반영한다 */
    fun onScheduleChanged(ctx: Context) {
        if (isEnabled(ctx)) syncNow(ctx)
    }

    /** 자정·부팅 때 호출 — 기간이 줄었으면 다시 채운다 */
    fun syncIfDue(ctx: Context) {
        if (!isEnabled(ctx)) return
        val last = prefs(ctx).getLong(KEY_LAST, -1L)
        if (last >= 0 &&
            ChronoUnit.DAYS.between(LocalDate.ofEpochDay(last), LocalDate.now()) < REFRESH_DAYS
        ) return
        syncNow(ctx)
    }

    fun lastSyncLabel(ctx: Context): String {
        val last = prefs(ctx).getLong(KEY_LAST, -1L)
        if (last < 0) return "아직 내보내지 않음"
        val d = LocalDate.ofEpochDay(last)
        return "마지막 %d.%02d.%02d".format(d.year, d.monthValue, d.dayOfMonth)
    }
}
