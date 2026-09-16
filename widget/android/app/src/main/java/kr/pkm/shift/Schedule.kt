package kr.pkm.shift

import android.content.Context
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 근무 한 가지. 되풀이되는 차례(패턴)는 사용자가 정하며 Schedule.cycle 이 들고 있습니다.
 *  ANNUAL 은 하루 지정으로만 쓰므로 패턴에는 넣지 않습니다. */
enum class Shift(val label: String, val full: String) {
    DAY("주", "주간"),
    NIGHT("야", "야간"),
    OFF("비", "비번"),
    REST("휴", "휴무"),
    ANNUAL("연", "연차"),
    SUPPORT("지", "지원근무");

    /** 띠에 쓰는 이름. 한 달을 일곱 칸으로 나눠 쓰므로 두 글자로 맞춘다 —
     *  네 글자인 지원근무만 줄어들고 나머지는 그대로다. */
    val brief: String get() = if (full.length <= 2) full else full.take(2)

    /** 조사를 붙인 이름 — "주간으로", "휴무로".
     *  받침이 없는 말에 '으로'를 붙이면 "휴무으로"가 되어 읽기 어색합니다. */
    val fullRo: String
        get() {
            val last = full.last()
            // 한글 음절은 (글자 - '가') % 28 이 0 이면 받침이 없다. 8 은 ㄹ 받침.
            val jong = if (last in '가'..'힣') (last - '가') % 28 else 0
            return full + if (jong == 0 || jong == 8) "로" else "으로"
        }

    companion object {
        /** 사용자가 패턴을 정하기 전까지 쓰는 차례. 실제로 쓰는 차례는 Schedule.cycle(ctx). */
        val DEFAULT_CYCLE = listOf(DAY, NIGHT, OFF, REST)

        /** 패턴에 넣을 수 있는 근무. 연차는 하루짜리라 되풀이되는 차례에 들어가지 않는다. */
        val PATTERN_CHOICES = listOf(DAY, NIGHT, OFF, REST, SUPPORT)
    }
}

object Schedule {
    /** 세는 시작점. 어느 날이든 상관없습니다 — 사용자가 고른 offset 이 나머지를 맞춥니다. */
    private val ANCHOR: LocalDate = LocalDate.of(2026, 8, 30)

    /** 패턴 길이의 한계. 이보다 길면 만들다 지치고 화면에도 담기지 않습니다. */
    const val MAX_PATTERN = 31

    private const val PREFS = "shift"
    private const val KEY_OFFSET = "offset"
    private const val KEY_PATTERN = "pattern"
    private const val OVERRIDE = "ov:"
    private const val MEMO = "memo:"
    private const val KEY_MONTH = "widgetMonth"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 되풀이되는 근무 차례. 정한 적이 없으면 기본값(주·야·비·휴)을 씁니다. */
    fun cycle(ctx: Context): List<Shift> {
        val raw = prefs(ctx).getString(KEY_PATTERN, null) ?: return Shift.DEFAULT_CYCLE
        val list = raw.split(",").mapNotNull { runCatching { Shift.valueOf(it) }.getOrNull() }
        return list.ifEmpty { Shift.DEFAULT_CYCLE }
    }

    /** 차례를 바꾸면 길이가 달라져 기존 offset 이 가리키던 자리가 뜻을 잃는다.
     *  그래서 지워 두고, 사용자가 오늘 위치를 다시 고르게 한다. */
    fun setCycle(ctx: Context, list: List<Shift>) {
        if (list.isEmpty()) return
        prefs(ctx).edit()
            .putString(KEY_PATTERN, list.take(MAX_PATTERN).joinToString(",") { it.name })
            .remove(KEY_OFFSET)
            .apply()
    }

    private fun base(ctx: Context, date: LocalDate): Int {
        val n = cycle(ctx).size
        return ((ChronoUnit.DAYS.between(ANCHOR, date) % n + n) % n).toInt()
    }

    /** 근무 주기를 아직 정하지 않았으면 false.
     *  기준일을 코드에 박아두면 다른 조 사람에게 틀린 표가 그럴듯하게 보이므로,
     *  사용자가 한 번 고르기 전까지는 어떤 근무도 만들어내지 않는다. */
    fun isConfigured(ctx: Context): Boolean = prefs(ctx).contains(KEY_OFFSET)

    fun offset(ctx: Context): Int = prefs(ctx).getInt(KEY_OFFSET, 0)

    /** "이 날이 차례의 i 번째"만 알려주면 나머지 날짜가 전부 맞춰집니다.
     *  같은 근무가 차례에 두 번 나올 수 있으므로 근무 이름이 아니라 자리로 받습니다.
     *  그 날에 걸어둔 하루 지정은 결과를 가리므로 함께 지웁니다. */
    fun setPatternDayOn(ctx: Context, date: LocalDate, i: Int) {
        val n = cycle(ctx).size
        if (i !in 0 until n) return
        prefs(ctx).edit()
            .putInt(KEY_OFFSET, ((i - base(ctx, date)) % n + n) % n)
            .remove(OVERRIDE + date)
            .apply()
    }

    /** 위젯이 보여주는 달 (이번 달 기준 +- 개월). 0 이면 이번 달. */
    fun monthOffset(ctx: Context): Int = prefs(ctx).getInt(KEY_MONTH, 0)

    fun setMonthOffset(ctx: Context, v: Int) {
        prefs(ctx).edit().putInt(KEY_MONTH, v.coerceIn(-600, 600)).apply()
    }

    /** 하루만 다르게 지정한 값 (교대·연차). 없으면 null. */
    fun overrideOf(ctx: Context, date: LocalDate): Shift? =
        prefs(ctx).getString(OVERRIDE + date, null)?.let { runCatching { Shift.valueOf(it) }.getOrNull() }

    fun setOverride(ctx: Context, date: LocalDate, shift: Shift?) {
        val e = prefs(ctx).edit()
        if (shift == null) e.remove(OVERRIDE + date) else e.putString(OVERRIDE + date, shift.name)
        e.apply()
    }

    /** 그 날 적어둔 메모들. 줄바꿈으로 이어 한 칸에 담으므로 메모 안에는 줄바꿈이 없다.
     *  하루 지정(ov:)과 다른 열쇠를 쓰므로 "직접 지정한 날짜 지우기"에 함께 지워지지 않는다. */
    fun memosOf(ctx: Context, date: LocalDate): List<String> =
        prefs(ctx).getString(MEMO + date, null)
            ?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun setMemos(ctx: Context, date: LocalDate, list: List<String>) {
        val e = prefs(ctx).edit()
        if (list.isEmpty()) e.remove(MEMO + date)
        else e.putString(MEMO + date, list.joinToString("\n"))
        e.apply()
    }

    /** 줄바꿈이 메모 사이를 가르므로 적힌 줄바꿈은 빈칸으로 바꿔 담는다. */
    private fun oneLine(text: String) = text.trim().replace(Regex("\\s*\n\\s*"), " ")

    fun addMemo(ctx: Context, date: LocalDate, text: String) {
        if (text.isBlank()) return
        setMemos(ctx, date, memosOf(ctx, date) + oneLine(text))
    }

    /** i 번째 메모를 고치거나(text), 지운다(null). */
    fun editMemo(ctx: Context, date: LocalDate, i: Int, text: String?) {
        val list = memosOf(ctx, date).toMutableList()
        if (i !in list.indices) return
        if (text.isNullOrBlank()) list.removeAt(i) else list[i] = oneLine(text)
        setMemos(ctx, date, list)
    }

    fun clearOverrides(ctx: Context) {
        val e = prefs(ctx).edit()
        prefs(ctx).all.keys.filter { it.startsWith(OVERRIDE) }.forEach { e.remove(it) }
        e.apply()
    }

    /** 하루 지정이 있으면 그것을, 없으면 주기 계산 결과를 돌려줍니다.
     *  아직 주기를 정하지 않았다면 null — 화면에는 빈 칸으로 나옵니다. */
    fun at(ctx: Context, date: LocalDate): Shift? {
        overrideOf(ctx, date)?.let { return it }
        if (!isConfigured(ctx)) return null
        val c = cycle(ctx)
        return c[(base(ctx, date) + offset(ctx)) % c.size]
    }
}
