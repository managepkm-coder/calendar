package kr.pkm.shift

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

/** 출근 알람이 울릴 시각에 호출된다. 알린 뒤 다음 번을 다시 건다. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val name = intent.getStringExtra(Alarms.EXTRA_SHIFT)
        val shift = runCatching { Shift.valueOf(name!!) }.getOrNull()

        // 알람을 건 뒤 근무가 바뀌었을 수 있으니 오늘 근무를 다시 확인한다
        if (shift != null && Schedule.at(ctx, LocalDate.now()) == shift) {
            Alarms.notify(ctx, shift)
        }
        Alarms.rescheduleAll(ctx)
    }
}
