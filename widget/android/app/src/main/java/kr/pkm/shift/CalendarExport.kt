package kr.pkm.shift

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneOffset

/** 근무를 폰의 캘린더에 하루 종일 일정으로 넣습니다.
 *  Google 계정 캘린더에 넣으면 Google 이 알아서 클라우드로 동기화합니다. */
object CalendarExport {

    /** 사용자가 고를 수 있는 캘린더 */
    data class Target(val id: Long, val name: String, val account: String)

    /** 이 앱이 넣은 일정만 나중에 지울 수 있도록 설명란에 표식을 남긴다 */
    private const val TAG = "[근무표]"

    fun targets(ctx: Context): List<Target> {
        val cols = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        val out = mutableListOf<Target>()
        ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, cols, null, null, null)
            ?.use { c ->
                while (c.moveToNext()) {
                    // 쓰기가 가능한 캘린더만 보여준다
                    if (c.getInt(3) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                    out += Target(c.getLong(0), c.getString(1) ?: "이름 없음", c.getString(2) ?: "")
                }
            }
        return out
    }

    /** 이 앱이 예전에 넣은 일정을 지운다 — 근무표가 바뀌어도 중복이 쌓이지 않도록 */
    fun clear(ctx: Context, calendarId: Long): Int =
        ctx.contentResolver.delete(
            CalendarContract.Events.CONTENT_URI,
            "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.DESCRIPTION}=?",
            arrayOf(calendarId.toString(), TAG)
        )

    /**
     * 오늘부터 months 개월치 근무를 넣는다. 기존에 넣었던 것은 먼저 지운다.
     * @return 넣은 일정 수
     */
    fun export(ctx: Context, calendarId: Long, months: Long): Int {
        clear(ctx, calendarId)
        val from = LocalDate.now()
        val until = from.plusMonths(months)
        val rows = mutableListOf<ContentValues>()

        var date = from
        while (date.isBefore(until)) {
            val shift = Schedule.at(ctx, date)
            if (shift != null) {
                // 하루 종일 일정은 UTC 자정을 기준으로 기록해야 한다.
                // 로컬 자정을 쓰면 한국(UTC+9)에서 하루 앞으로 밀린다.
                val start = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                rows += ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.TITLE, shift.full)
                    put(CalendarContract.Events.DESCRIPTION, TAG)
                    put(CalendarContract.Events.DTSTART, start)
                    put(CalendarContract.Events.DTEND, start + 86_400_000L)
                    put(CalendarContract.Events.ALL_DAY, 1)
                    // 하루 종일 일정은 UTC 로 기록하는 것이 규약이다
                    put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                    put(CalendarContract.Events.HAS_ALARM, 0)
                }
            }
            date = date.plusDays(1)
        }
        if (rows.isEmpty()) return 0
        return ctx.contentResolver.bulkInsert(
            CalendarContract.Events.CONTENT_URI, rows.toTypedArray()
        )
    }

    fun eventUri(id: Long) = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
}
