package kr.pkm.shift

import android.content.Context
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 주 → 야 → 비 → 휴 4일 주기. ANNUAL 은 주기에 들어가지 않고 하루 지정으로만 씁니다. */
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
        /** 반복되는 근무 주기. 순서를 바꾸려면 여기만 고치면 됩니다. */
        val CYCLE = listOf(DAY, NIGHT, OFF, REST)
    }
}

object Schedule {
    /** 이 날짜가 CYCLE[0](주간)입니다. */
    private val ANCHOR: LocalDate = LocalDate.of(2026, 8, 30)

    private const val PREFS = "shift"
    private const val KEY_OFFSET = "offset"
    private const val OVERRIDE = "ov:"
    private const val MEMO = "memo:"
    private const val KEY_MONTH = "widgetMonth"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun base(date: LocalDate): Int {
        val n = Shift.CYCLE.size
        return ((ChronoUnit.DAYS.between(ANCHOR, date) % n + n) % n).toInt()
    }

    /** 근무 주기를 아직 정하지 않았으면 false.
     *  기준일을 코드에 박아두면 다른 조 사람에게 틀린 표가 그럴듯하게 보이므로,
     *  사용자가 한 번 고르기 전까지는 어떤 근무도 만들어내지 않는다. */
    fun isConfigured(ctx: Context): Boolean = prefs(ctx).contains(KEY_OFFSET)

    fun offset(ctx: Context): Int = prefs(ctx).getInt(KEY_OFFSET, 0)

    /** "이 날은 OO 근무"만 알려주면 나머지 날짜가 전부 맞춰집니다.
     *  그 날에 걸어둔 하루 지정은 결과를 가리므로 함께 지웁니다. */
    fun setShiftOn(ctx: Context, date: LocalDate, shift: Shift) {
        val n = Shift.CYCLE.size
        val i = Shift.CYCLE.indexOf(shift)
        if (i < 0) return
        prefs(ctx).edit()
            .putInt(KEY_OFFSET, ((i - base(date)) % n + n) % n)
            .remove(OVERRIDE + date)
            .apply()
    }

    fun setTodayShift(ctx: Context, shift: Shift) = setShiftOn(ctx, LocalDate.now(), shift)

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
        return Shift.CYCLE[(base(date) + offset(ctx)) % Shift.CYCLE.size]
    }
}
