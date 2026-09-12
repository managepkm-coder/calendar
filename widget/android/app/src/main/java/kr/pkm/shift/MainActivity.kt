package kr.pkm.shift

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate

/** 월 단위 근무 달력. 위젯과 같은 Schedule 을 쓰므로 두 화면이 항상 일치합니다. */
class MainActivity : Activity() {

    private var month: LocalDate = LocalDate.now().withDayOfMonth(1)
    private lateinit var grid: GridLayout

    private val dowNames = arrayOf("일", "월", "화", "수", "목", "금", "토")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        grid = findViewById(R.id.grid)

        findViewById<View>(R.id.prev).setOnClickListener { move(-1) }
        findViewById<View>(R.id.next).setOnClickListener { move(1) }
        findViewById<View>(R.id.btnToday).setOnClickListener {
            month = LocalDate.now().withDayOfMonth(1); render()
        }
        findViewById<View>(R.id.btnSettings).setOnClickListener { openSettings() }

        buildDowHeader()
        render()
        openFromWidget(intent)
        if (!Schedule.isConfigured(this)) openSettings()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFromWidget(intent)
    }

    /** 위젯에서 날짜를 눌러 들어온 경우, 그 달로 옮기고 수정 창을 띄운다. */
    private fun openFromWidget(intent: Intent?) {
        val raw = intent?.getStringExtra(EXTRA_DATE) ?: return
        intent.removeExtra(EXTRA_DATE)          // 화면 회전 시 다시 뜨지 않도록
        val date = runCatching { LocalDate.parse(raw) }.getOrNull() ?: return
        month = date.withDayOfMonth(1)
        render()
        openDay(date)
    }

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun move(delta: Int) {
        month = month.plusMonths(delta.toLong()); render()
    }

    private fun buildDowHeader() {
        val row = findViewById<LinearLayout>(R.id.dowRow)
        dowNames.forEachIndexed { i, name ->
            val t = TextView(this)
            t.text = name
            t.gravity = Gravity.CENTER
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            t.setTypeface(null, Typeface.BOLD)
            t.setTextColor(
                when (i) {
                    0 -> SUNDAY
                    6 -> SATURDAY
                    else -> getColor(R.color.text_primary)
                }
            )
            t.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(t)
        }
    }

    private fun render() {
        findViewById<TextView>(R.id.ym).text = "%d. %02d".format(month.year, month.monthValue)

        val today = LocalDate.now()
        // 어느 빌드가 깔려 있는지 화면에서 바로 확인할 수 있도록 버전을 함께 보여준다
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "?"
        val shift = Schedule.at(this, today)
        findViewById<TextView>(R.id.sub).text =
            if (shift == null) "⚙ 를 눌러 오늘의 근무를 먼저 정하세요   ·   v$version"
            else "오늘 %02d.%02d · %s   ·   v%s".format(
                today.monthValue, today.dayOfMonth, shift.full, version)

        // 그 주의 일요일부터 시작해 필요한 주 수만큼만 그린다
        val startDow = month.dayOfWeek.value % 7          // 월=1..일=7 → 일=0
        val start = month.minusDays(startDow.toLong())
        val cells = ((startDow + month.lengthOfMonth() + 6) / 7) * 7

        grid.removeAllViews()
        for (i in 0 until cells) {
            val date = start.plusDays(i.toLong())
            val cell = buildCell(date, date.monthValue == month.monthValue)
            cell.layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(i / 7), GridLayout.spec(i % 7, 1f)
            ).apply { width = 0 }
            grid.addView(cell)
        }
    }

    private fun buildCell(date: LocalDate, inMonth: Boolean): View {
        val holiday = Holidays.nameOf(date)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(0, dp(7), 0, dp(9))
        if (date == LocalDate.now()) col.setBackgroundResource(R.drawable.bg_today)
        col.alpha = if (inMonth) 1f else 0.42f

        val label = TextView(this)
        label.text = (if (date.dayOfMonth == 1) "%02d.%02d".format(date.monthValue, date.dayOfMonth)
                      else "%02d".format(date.dayOfMonth)) + (holiday?.let { " $it" } ?: "")
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        label.setTypeface(null, Typeface.BOLD)
        label.setTextColor(
            when {
                holiday != null || date.dayOfWeek.value == 7 -> SUNDAY
                date.dayOfWeek.value == 6 -> SATURDAY
                else -> getColor(R.color.text_primary)
            }
        )
        col.addView(label)

        val shift = Schedule.at(this, date)
        val badge = TextView(this)
        if (shift != null) {
            badge.text = shift.label
            badge.setTextColor(Palette.fg(shift))
            badge.setBackgroundResource(Palette.bg(shift))
        }
        badge.gravity = Gravity.CENTER
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        badge.setTypeface(null, Typeface.BOLD)
        badge.layoutParams = LinearLayout.LayoutParams(dp(33), dp(33))
            .apply { topMargin = dp(4) }
        col.addView(badge)

        col.setOnClickListener { openDay(date) }
        return col
    }

    /** 날짜를 누르면 주기 맞추기가 기본. 하루만 바꾸는 것은 한 단계 아래에 둔다. */
    private fun openDay(date: LocalDate) {
        val cycle = Shift.CYCLE
        // 손댄 적 없는 날에는 "지정 해제"가 아무 일도 하지 않으므로 아예 숨긴다
        val pinned = Schedule.overrideOf(this, date) != null
        val items = (cycle.map { "${it.full}으로 맞추기" } +
            listOf("이 날만 바꾸기 (연차·교대)") +
            if (pinned) listOf("이 날 지정 해제 (주기대로)") else emptyList()).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("%d. %02d. %02d  ·  전체 주기 맞추기".format(date.year, date.monthValue, date.dayOfMonth))
            .setItems(items) { _, which ->
                when (which) {
                    in cycle.indices -> {
                        val before = Schedule.at(this, date)
                        Schedule.setShiftOn(this, date, cycle[which])
                        applyChange(
                            if (before == cycle[which])
                                "${label(date)}은 이미 ${cycle[which].full}입니다"
                            else "${label(date)}을 ${cycle[which].full}으로 맞췄습니다 · 전체 이동"
                        )
                    }
                    cycle.size -> openDayOnly(date)
                    else -> {
                        Schedule.setOverride(this, date, null)
                        applyChange("${label(date)} 지정을 해제했습니다")
                    }
                }
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    /** 이 하루만 바꾼다 — 교대나 연차처럼 주기에서 벗어나는 날 */
    private fun openDayOnly(date: LocalDate) {
        val choices = Shift.entries
        AlertDialog.Builder(this)
            .setTitle("%02d. %02d  ·  이 날만 변경".format(date.monthValue, date.dayOfMonth))
            .setItems(choices.map { it.full }.toTypedArray()) { _, which ->
                Schedule.setOverride(this, date, choices[which])
                applyChange("${label(date)}만 ${choices[which].full}(으)로 바꿨습니다")
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun label(date: LocalDate) = "%d월 %d일".format(date.monthValue, date.dayOfMonth)

    /** 바뀐 내용을 알려준다 — 고른 값이 원래 값과 같으면 화면이 그대로라 눌린 줄 모른다. */
    private fun applyChange(message: String) {
        ShiftWidget.refreshAll(this)
        ShiftWidget.scheduleMidnight(this)
        Alarms.rescheduleAll(this)      // 근무가 바뀌면 알람 날짜도 달라진다
        render()
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun openSettings() {
        AlertDialog.Builder(this)
            .setTitle("설정")
            .setItems(arrayOf("오늘의 근무 맞추기", "출근 알람", "직접 지정한 날짜 모두 지우기")) { _, which ->
                when (which) {
                    0 -> openTodayShift()
                    1 -> openAlarms()
                    else -> {
                        Schedule.clearOverrides(this)
                        applyChange("직접 지정한 날짜를 모두 지웠습니다")
                    }
                }
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun openTodayShift() {
        AlertDialog.Builder(this)
            .setTitle("오늘의 근무  ·  전체 주기 맞추기")
            .setItems(Shift.CYCLE.map { it.full }.toTypedArray()) { _, which ->
                Schedule.setTodayShift(this, Shift.CYCLE[which])
                applyChange("오늘을 ${Shift.CYCLE[which].full}으로 맞췄습니다 · 전체 이동")
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    /** 근무 종류별 출근 알람. 해당 근무인 날에만 울린다. */
    private fun openAlarms() {
        val items = Alarms.TARGETS
            .map { "${it.full}   ${Alarms.label(Alarms.timeOf(this, it))}" }
            .toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("출근 알람  ·  근무별 시각")
            .setItems(items) { _, which -> pickAlarmTime(Alarms.TARGETS[which]) }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun pickAlarmTime(shift: Shift) {
        val now = Alarms.timeOf(this, shift) ?: (6 * 60)
        val dialog = TimePickerDialog(
            this,
            { _, h, m ->
                Alarms.setTime(this, shift, h * 60 + m)
                askNotificationPermission()
                Toast.makeText(
                    this, "${shift.full} 출근 알람 ${Alarms.label(h * 60 + m)}", Toast.LENGTH_SHORT
                ).show()
                openAlarms()
            },
            now / 60, now % 60, true
        )
        dialog.setTitle("${shift.full} 출근 시각")
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "알람 끄기") { _, _ ->
            Alarms.setTime(this, shift, null)
            Toast.makeText(this, "${shift.full} 알람을 껐습니다", Toast.LENGTH_SHORT).show()
            openAlarms()
        }
        dialog.show()
    }

    /** 안드로이드 13 이상에서는 알림 권한을 따로 받아야 소리가 난다. */
    private fun askNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    // 좌우 스와이프로 달 이동
    private var downX = 0f
    private var downY = 0f

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y }
            MotionEvent.ACTION_UP -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (Math.abs(dx) > dp(60) && Math.abs(dx) > Math.abs(dy) * 2) {
                    move(if (dx < 0) 1 else -1)
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    companion object {
        const val EXTRA_DATE = "kr.pkm.shift.DATE"
        private const val SUNDAY = 0xFFE0483C.toInt()
        private const val SATURDAY = 0xFF2F6FD0.toInt()
    }
}
