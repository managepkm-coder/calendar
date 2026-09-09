package kr.pkm.shift

/** 근무별 배지 색. 위젯과 달력 화면이 같은 값을 씁니다. */
object Palette {
    fun bg(s: Shift) = when (s) {
        Shift.DAY -> R.drawable.badge_ju
        Shift.NIGHT -> R.drawable.badge_ya
        Shift.OFF -> R.drawable.badge_bi
        Shift.REST -> R.drawable.badge_hyu
        Shift.ANNUAL -> R.drawable.badge_yeon
    }

    fun fg(s: Shift) = when (s) {
        Shift.DAY -> 0xFF3D3300.toInt()
        Shift.NIGHT -> 0xFFFFFFFF.toInt()
        Shift.OFF -> 0xFFC9372B.toInt()
        Shift.REST -> 0xFF6B6B73.toInt()
        Shift.ANNUAL -> 0xFF2F6FD0.toInt()
    }
}
