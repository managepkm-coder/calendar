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
            ACTION_PREV -> { Schedule.setMonthOffset(ctx, Schedule.monthOffset(ctx) - 1); refreshAll(ctx) }
            ACTION_NEXT -> { Schedule.setMonthOffset(ctx, Schedule.monthOffset(ctx) + 1); refreshAll(ctx) }
            ACTION_TODAY -> { Schedule.setMonthOffset(ctx, 0); refreshAll(ctx) }

            ACTION_REFRESH,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED -> {
                Schedule.setMonthOffset(ctx, 0)
                refreshAll(ctx)
                scheduleMidnight(ctx)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "kr.pkm.shift.REFRESH"
        const val ACTION_PREV = "kr.pkm.shift.PREV"
        const val ACTION_NEXT = "kr.pkm.shift.NEXT"
        const val ACTION_TODAY = "kr.pkm.shift.TODAY"

        private const val SUNDAY = 0xFFE0483C.toInt()
        private const val SATURDAY = 0xFF2F6FD0.toInt()

        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ShiftWidget::class.java))
            ids.forEach { mgr.updateAppWidget(it, buildViews(ctx)) }
        }

        private fun buildViews(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget)
            val today = LocalDate.now()
            val month = today.withDayOfMonth(1)
                .plusMonths(Schedule.monthOffset(ctx).toLong())

            v.setTextViewText(R.id.ym, "%d. %02d".format(month.year, month.monthValue))
            v.setTextColor(R.id.ym, ctx.getColor(R.color.text_primary))
            for (id in intArrayOf(R.id.btnToday, R.id.btnPrev, R.id.btnNext, R.id.btnSettings)) {
                v.setTextColor(id, ctx.getColor(R.color.text_muted))
            }
            v.setOnClickPendingIntent(R.id.btnPrev, broadcast(ctx, ACTION_PREV, 10))
            v.setOnClickPendingIntent(R.id.btnNext, broadcast(ctx, ACTION_NEXT, 11))
            v.setOnClickPendingIntent(R.id.btnToday, broadcast(ctx, ACTION_TODAY, 12))
            v.setOnClickPendingIntent(R.id.btnSettings, openApp(ctx))

            // 그 주 일요일부터 시작한다
            val startDow = month.dayOfWeek.value % 7          // 월=1..일=7 → 일=0
            val start = month.minusDays(startDow.toLong())
            val usedWeeks = (startDow + month.lengthOfMonth() + 6) / 7

            // 쓰지 않는 주 행은 숨겨서 남은 주들이 높이를 나눠 갖게 한다
            WidgetIds.WEEK.forEachIndexed { w, id ->
                v.setViewVisibility(id, if (w < usedWeeks) View.VISIBLE else View.GONE)
            }

            for (i in 0 until 42) {
                val date = start.plusDays(i.toLong())
                val inMonth = i < usedWeeks * 7 && date.monthValue == month.monthValue
                val holiday = if (inMonth) Holidays.nameOf(date) else null

                v.setTextViewText(WidgetIds.DATE[i], date.dayOfMonth.toString())
                v.setTextColor(WidgetIds.DATE[i], when {
                    !inMonth -> ctx.getColor(R.color.text_muted)
                    holiday != null || date.dayOfWeek.value == 7 -> SUNDAY
                    date.dayOfWeek.value == 6 -> SATURDAY
                    else -> ctx.getColor(R.color.text_primary)
                })

                v.setTextViewText(WidgetIds.HOLIDAY[i], holiday ?: "")
                v.setViewVisibility(
                    WidgetIds.HOLIDAY[i], if (holiday != null) View.VISIBLE else View.GONE
                )

                if (inMonth) {
                    val s = Schedule.at(ctx, date)
                    v.setTextViewText(WidgetIds.BADGE[i], s.label)
                    v.setInt(WidgetIds.BADGE[i], "setBackgroundResource", Palette.bg(s))
                    v.setTextColor(WidgetIds.BADGE[i], Palette.fg(s))
                    v.setViewVisibility(WidgetIds.BADGE[i], View.VISIBLE)
                } else {
                    // 자리는 지키되 보이지 않게 해 칸 높이가 흔들리지 않도록 한다
                    v.setViewVisibility(WidgetIds.BADGE[i], View.INVISIBLE)
                }

                v.setInt(
                    WidgetIds.CELL[i], "setBackgroundResource",
                    if (inMonth && date == today) R.drawable.bg_today else 0
                )
                v.setOnClickPendingIntent(WidgetIds.CELL[i], openApp(ctx))
            }
            return v
        }

        /** 위젯 버튼이 자기 자신에게 보내는 브로드캐스트 */
        private fun broadcast(ctx: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, code, Intent(ctx, ShiftWidget::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        private fun openApp(ctx: Context): PendingIntent =
            PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

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
