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

    /** 이 캘린더가 속한 계정. 일정 색 목록이 계정 단위로 있어서 필요하다. */
    private fun accountOf(ctx: Context, calendarId: Long): Pair<String, String>? {
        val cols = arrayOf(
            CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE
        )
        ctx.contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId),
            cols, null, null, null
        )?.use { c ->
            if (c.moveToFirst()) return (c.getString(0) ?: "") to (c.getString(1) ?: "")
        }
        return null
    }

    /** 그 계정이 일정에 쓸 수 있는 색. key → 색.
     *  색을 제공하지 않는 계정도 있으므로 비어 있을 수 있다. */
    private fun eventColors(ctx: Context, account: String, type: String): Map<String, Int> {
        val cols = arrayOf(CalendarContract.Colors.COLOR_KEY, CalendarContract.Colors.COLOR)
        val where = "${CalendarContract.Colors.ACCOUNT_NAME}=? AND " +
            "${CalendarContract.Colors.ACCOUNT_TYPE}=? AND ${CalendarContract.Colors.COLOR_TYPE}=?"
        val args = arrayOf(account, type, CalendarContract.Colors.TYPE_EVENT.toString())
        val out = mutableMapOf<String, Int>()
        runCatching {
            ctx.contentResolver.query(CalendarContract.Colors.CONTENT_URI, cols, where, args, null)
                ?.use { c ->
                    while (c.moveToNext()) {
                        val key = c.getString(0) ?: continue
                        out[key] = c.getInt(1)
                    }
                }
        }
        return out
    }

    /** 두 색이 얼마나 먼지 — RGB 거리. 투명도는 보지 않는다. */
    private fun gap(a: Int, b: Int): Int {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db
    }

    /** 근무마다 쓸 색의 key. 계정이 색을 주지 않으면 빈 map 이고, 그러면 색 없이 넣는다.
     *  key 는 반드시 그 계정 목록에서 가져와야 한다 — 없는 값을 넣으면 일정 쓰기가 거부된다. */
    private fun colorKeys(ctx: Context, calendarId: Long): Map<Shift, String> {
        val (account, type) = accountOf(ctx, calendarId) ?: return emptyMap()
        val colors = eventColors(ctx, account, type)
        if (colors.isEmpty()) return emptyMap()
        return Shift.entries.associateWith { shift ->
            colors.minByOrNull { gap(it.value, Palette.exportRgb(shift)) }!!.key
        }
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
        // 근무별 색. 계정이 색을 주지 않으면 비어 있고, 그때는 캘린더 기본색으로 나간다
        val colorKey = colorKeys(ctx, calendarId)

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
                    colorKey[shift]?.let { put(CalendarContract.Events.EVENT_COLOR_KEY, it) }
                }
            }
            date = date.plusDays(1)
        }
        if (rows.isEmpty()) return 0
        return runCatching {
            ctx.contentResolver.bulkInsert(CalendarContract.Events.CONTENT_URI, rows.toTypedArray())
        }.getOrElse {
            // 색을 받아주지 않는 계정이라면 색 때문에 근무표 전체를 못 넣는 편이 더 나쁘다
            rows.forEach { it.remove(CalendarContract.Events.EVENT_COLOR_KEY) }
            ctx.contentResolver.bulkInsert(CalendarContract.Events.CONTENT_URI, rows.toTypedArray())
        }
    }

    fun eventUri(id: Long) = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
}
