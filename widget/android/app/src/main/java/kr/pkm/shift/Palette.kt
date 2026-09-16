package kr.pkm.shift

/** 근무별 배지 색. 위젯과 달력 화면이 같은 값을 씁니다. */
object Palette {
    fun bg(s: Shift) = when (s) {
        Shift.DAY -> R.drawable.badge_ju
        Shift.NIGHT -> R.drawable.badge_ya
        Shift.OFF -> R.drawable.badge_bi
        Shift.REST -> R.drawable.badge_hyu
        Shift.ANNUAL -> R.drawable.badge_yeon
        Shift.SUPPORT -> R.drawable.badge_ji
    }

    /** 띠로 깔 때 쓰는 바탕색. 배지 drawable 과 같은 값을 가리킨다. */
    fun barColor(s: Shift) = when (s) {
        Shift.DAY -> R.color.shift_ju
        Shift.NIGHT -> R.color.shift_ya
        Shift.OFF -> R.color.shift_bi
        Shift.REST -> R.color.shift_hyu
        Shift.ANNUAL -> R.color.shift_yeon
        Shift.SUPPORT -> R.color.shift_ji
    }

    /** 캘린더에 내보낼 때 목표로 삼는 색.
     *  배지 바탕은 연한 것이 섞여 있어 그대로 맞추면 엉뚱한 색이 잡히므로,
     *  근무마다 또렷한 색을 따로 둔다. 계정이 주는 색 중 여기서 가장 가까운 것을 쓴다. */
    fun exportRgb(s: Shift) = when (s) {
        Shift.DAY -> 0xF6BF26      // 노랑
        Shift.NIGHT -> 0x616161    // 진회색
        Shift.OFF -> 0xD50000      // 빨강
        Shift.REST -> 0x7986CB     // 연보라
        Shift.ANNUAL -> 0x3F51B5   // 남색
        Shift.SUPPORT -> 0x0B8043  // 초록
    }

    fun fg(s: Shift) = when (s) {
        Shift.DAY -> 0xFF3D3300.toInt()
        Shift.NIGHT -> 0xFFFFFFFF.toInt()
        Shift.OFF -> 0xFFC9372B.toInt()
        Shift.REST -> 0xFF6B6B73.toInt()
        Shift.ANNUAL -> 0xFF2F6FD0.toInt()
        Shift.SUPPORT -> 0xFF1E7A45.toInt()
    }
}
