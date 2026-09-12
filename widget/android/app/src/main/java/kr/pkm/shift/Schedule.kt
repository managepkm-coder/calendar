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
    ANNUAL("연", "연차");

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
    private const val KEY_MONTH = "widgetMonth"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun base(date: LocalDate): Int {
        val n = Shift.CYCLE.size
        return ((ChronoUnit.DAYS.between(ANCHOR, date) % n + n) % n).toInt()
    }

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

    fun clearOverrides(ctx: Context) {
        val e = prefs(ctx).edit()
        prefs(ctx).all.keys.filter { it.startsWith(OVERRIDE) }.forEach { e.remove(it) }
        e.apply()
    }

    /** 하루 지정이 있으면 그것을, 없으면 주기 계산 결과를 돌려줍니다. */
    fun at(ctx: Context, date: LocalDate): Shift =
        overrideOf(ctx, date) ?: Shift.CYCLE[(base(date) + offset(ctx)) % Shift.CYCLE.size]
}
