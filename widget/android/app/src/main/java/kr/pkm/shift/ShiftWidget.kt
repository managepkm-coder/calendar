package kr.pkm.shift

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId

class ShiftWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, buildViews(ctx)) }
        scheduleMidnight(ctx)
    }

    override fun onEnabled(ctx: Context) {
        scheduleMidnight(ctx)
    }

    override fun onDisabled(ctx: Context) {
        alarmManager(ctx).cancel(midnightIntent(ctx))
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            ACTION_REFRESH,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED -> {
                refreshAll(ctx)
                scheduleMidnight(ctx)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "kr.pkm.shift.REFRESH"

        private const val SUNDAY = 0xFFE0483C.toInt()
        private const val SATURDAY = 0xFF2F6FD0.toInt()

        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ShiftWidget::class.java))
            ids.forEach { mgr.updateAppWidget(it, buildViews(ctx)) }
        }

        /** 달력 한 칸. 이번 달이 아닌 날은 날짜만 흐리게 두고 근무 배지는 숨긴다. */
        private fun cell(ctx: Context, date: LocalDate, inMonth: Boolean): RemoteViews {
            val c = RemoteViews(ctx.packageName, R.layout.widget_cell)
            val holiday = Holidays.nameOf(date)

            c.setTextViewText(R.id.cellDate, date.dayOfMonth.toString())
            c.setTextColor(R.id.cellDate, when {
                !inMonth -> ctx.getColor(R.color.text_muted)
                holiday != null || date.dayOfWeek.value == 7 -> SUNDAY
                date.dayOfWeek.value == 6 -> SATURDAY
                else -> ctx.getColor(R.color.text_primary)
            })

            if (inMonth) {
                val s = Schedule.at(ctx, date)
                c.setTextViewText(R.id.cellBadge, s.label)
                c.setInt(R.id.cellBadge, "setBackgroundResource", Palette.bg(s))
                c.setTextColor(R.id.cellBadge, Palette.fg(s))
                if (date == LocalDate.now()) {
                    c.setInt(R.id.cellRoot, "setBackgroundResource", R.drawable.bg_today)
                }
            } else {
                // 자리는 차지하되 보이지 않게 해서 주마다 높이가 흔들리지 않도록 한다
                c.setViewVisibility(R.id.cellBadge, View.INVISIBLE)
            }
            return c
        }

        private fun buildViews(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget)
            val today = LocalDate.now()
            val month = today.withDayOfMonth(1)

            v.setTextViewText(R.id.ym, "%d. %02d".format(month.year, month.monthValue))
            v.setTextColor(R.id.ym, ctx.getColor(R.color.text_primary))
            v.setTextViewText(R.id.todayInfo, "오늘 · ${Schedule.at(ctx, today).full}")
            v.setTextColor(R.id.todayInfo, ctx.getColor(R.color.text_muted))

            // 그 주 일요일부터 시작해 필요한 주 수만큼만 그린다
            val startDow = month.dayOfWeek.value % 7          // 월=1..일=7 → 일=0
            val start = month.minusDays(startDow.toLong())
            val weeks = (startDow + month.lengthOfMonth() + 6) / 7

            v.removeAllViews(R.id.weeks)
            for (w in 0 until weeks) {
                val row = RemoteViews(ctx.packageName, R.layout.widget_week)
                for (d in 0 until 7) {
                    val date = start.plusDays((w * 7L + d))
                    row.addView(R.id.weekRow, cell(ctx, date, date.monthValue == month.monthValue))
                }
                v.addView(R.id.weeks, row)
            }

            // 위젯을 누르면 앱이 열립니다
            v.setOnClickPendingIntent(
                R.id.root,
                PendingIntent.getActivity(
                    ctx, 0, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            return v
        }

        private fun alarmManager(ctx: Context) =
            ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        private fun midnightIntent(ctx: Context): PendingIntent {
            val i = Intent(ctx, ShiftWidget::class.java).setAction(ACTION_REFRESH)
            return PendingIntent.getBroadcast(
                ctx, 1, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        /** 매일 자정 직후에 위젯을 다시 그린다 (부정확 알람이라 별도 권한이 필요 없음). */
        fun scheduleMidnight(ctx: Context) {
            val next = LocalDate.now().plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .plusMinutes(1)
                .toInstant().toEpochMilli()
            alarmManager(ctx).setRepeating(
                AlarmManager.RTC, next, AlarmManager.INTERVAL_DAY, midnightIntent(ctx)
            )
        }
    }
}
