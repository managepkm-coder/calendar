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
    private var current: AlertDialog? = null

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

        // 위젯에서 누른 날짜가 있으면 그 달을 펴서 보여준다
        val tapped = dateOf(intent)
        if (tapped != null) month = tapped.withDayOfMonth(1)
        render()

        // 화면이 다시 만들어질 때(회전 등)는 창을 다시 띄우지 않는다
        if (savedInstanceState == null) when {
            // 누른 날짜 창을 바로 띄운다. 근무를 정하기 전이어도 이 창에서 맞출 수 있다.
            tapped != null -> openDay(tapped)
            // 그 외에는 근무를 아직 정하지 않았을 때만 설정을 띄우고, 달력만 보여준다.
            !Schedule.isConfigured(this) -> openSettings()
        }
    }

    /** 앱이 이미 떠 있는 채로 위젯 날짜를 누르면 여기로 온다 (launchMode=singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tapped = dateOf(intent) ?: return
        month = tapped.withDayOfMonth(1)
        render()
        openDay(tapped)
    }

    override fun onDestroy() {
        current?.dismiss()
        current = null
        super.onDestroy()
    }

    /** 위젯 날짜 칸이 넘겨준 날짜. 그 밖의 경로로 열렸으면 null. */
    private fun dateOf(intent: Intent?): LocalDate? {
        if (intent?.action != ShiftWidget.ACTION_DAY) return null
        val raw = intent.data?.lastPathSegment ?: return null
        return runCatching { LocalDate.parse(raw) }.getOrNull()
    }

    /** 창은 한 번에 하나만 — 위젯에서 다른 날짜를 누르면 앞서 뜬 창을 갈아탄다. */
    private fun AlertDialog.Builder.present() {
        current?.dismiss()
        current = show()
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
            .present()
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
            .present()
    }

    private fun label(date: LocalDate) = "%d월 %d일".format(date.monthValue, date.dayOfMonth)

    /** 바뀐 내용을 알려준다 — 고른 값이 원래 값과 같으면 화면이 그대로라 눌린 줄 모른다. */
    private fun applyChange(message: String) {
        ShiftWidget.refreshAll(this)
        ShiftWidget.scheduleMidnight(this)
        Alarms.rescheduleAll(this)        // 근무가 바뀌면 알람 날짜도 달라진다
        CalendarSync.onScheduleChanged(this)  // 캘린더에 내보낸 일정도 다시 맞춘다
        render()
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun openSettings() {
        AlertDialog.Builder(this)
            .setTitle("설정")
            .setItems(
                arrayOf(
                    "오늘의 근무 맞추기",
                    "출근 알람",
                    if (CalendarSync.isEnabled(this)) "캘린더 자동 연동  ·  켜짐"
                    else "캘린더에 내보내기",
                    "직접 지정한 날짜 모두 지우기",
                )
            ) { _, which ->
                when (which) {
                    0 -> openTodayShift()
                    1 -> openAlarms()
                    2 -> exportToCalendar()
                    else -> {
                        Schedule.clearOverrides(this)
                        applyChange("직접 지정한 날짜를 모두 지웠습니다")
                    }
                }
            }
            .setNegativeButton("닫기", null)
            .present()
    }

    private fun openTodayShift() {
        AlertDialog.Builder(this)
            .setTitle("오늘의 근무  ·  전체 주기 맞추기")
            .setItems(Shift.CYCLE.map { it.full }.toTypedArray()) { _, which ->
                Schedule.setTodayShift(this, Shift.CYCLE[which])
                applyChange("오늘을 ${Shift.CYCLE[which].full}으로 맞췄습니다 · 전체 이동")
            }
            .setNegativeButton("닫기", null)
            .present()
    }

    /** 근무 종류별 출근 알람. 해당 근무인 날에만 울린다. */
    private fun openAlarms() {
        val items = Alarms.TARGETS
            .map { "${it.full}   ${Alarms.label(this, Alarms.timeOf(this, it))}" }
            .toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("출근 알람  ·  근무별 시각")
            .setItems(items) { _, which -> pickAlarmTime(Alarms.TARGETS[which]) }
            .setNegativeButton("닫기", null)
            .present()
    }

    private fun pickAlarmTime(shift: Shift) {
        val now = Alarms.timeOf(this, shift) ?: (6 * 60)
        val dialog = TimePickerDialog(
            this,
            { _, h, m ->
                Alarms.setTime(this, shift, h * 60 + m)
                askNotificationPermission()
                Toast.makeText(
                    this, "${shift.full} 출근 알람 ${Alarms.label(this, h * 60 + m)}", Toast.LENGTH_SHORT
                ).show()
                openAlarms()
            },
            now / 60, now % 60,
            // 폰의 시간 표시 설정을 따른다. true 로 고정하면 오전/오후를 고를 수 없다.
            android.text.format.DateFormat.is24HourFormat(this)
        )
        dialog.setTitle("${shift.full} 출근 시각")
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "알람 끄기") { _, _ ->
            Alarms.setTime(this, shift, null)
            Toast.makeText(this, "${shift.full} 알람을 껐습니다", Toast.LENGTH_SHORT).show()
            openAlarms()
        }
        dialog.show()
    }

    /** 근무를 폰 캘린더에 내보낸다. Google 계정을 고르면 클라우드로 동기화된다. */
    private fun exportToCalendar() {
        if (CalendarSync.isEnabled(this)) { manageSync(); return }
        if (!Schedule.isConfigured(this)) {
            Toast.makeText(this, "먼저 오늘의 근무를 정해주세요", Toast.LENGTH_SHORT).show()
            return
        }
        val need = arrayOf(
            android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR
        )
        if (need.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(need, 2)
            return
        }
        val targets = CalendarExport.targets(this)
        if (targets.isEmpty()) {
            Toast.makeText(this, "쓸 수 있는 캘린더가 없습니다", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("어느 캘린더에 넣을까요")
            .setItems(targets.map { "${it.name}\n${it.account}" }.toTypedArray()) { _, i ->
                pickExportRange(targets[i])
            }
            .setNegativeButton("닫기", null)
            .present()
    }

    private fun pickExportRange(target: CalendarExport.Target) {
        val months = longArrayOf(3, 6, 12)
        AlertDialog.Builder(this)
            .setTitle("${target.name}  ·  항상 유지할 기간")
            .setItems(months.map { "앞으로 ${it}개월" }.toTypedArray()) { _, i ->
                CalendarSync.enable(this, target.id, months[i])
                Toast.makeText(this, "내보내는 중…", Toast.LENGTH_SHORT).show()
                CalendarSync.syncNow(this) { n ->
                    Toast.makeText(this, "일정 ${n}개를 넣었습니다 · 자동 연동 켜짐", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("닫기", null)
            .present()
    }

    /** 연동이 켜져 있을 때의 관리 화면 */
    private fun manageSync() {
        val (id, months) = CalendarSync.target(this) ?: return
        val name = CalendarExport.targets(this).firstOrNull { it.id == id }?.name ?: "캘린더"
        AlertDialog.Builder(this)
            .setTitle("자동 연동  ·  ${name} · ${months}개월")
            .setItems(
                arrayOf(
                    "지금 다시 내보내기  (${CalendarSync.lastSyncLabel(this)})",
                    "캘린더 · 기간 바꾸기",
                    "자동 연동 끄고 넣은 일정 지우기",
                )
            ) { _, which ->
                when (which) {
                    0 -> {
                        Toast.makeText(this, "내보내는 중…", Toast.LENGTH_SHORT).show()
                        CalendarSync.syncNow(this) { n ->
                            Toast.makeText(this, "일정 ${n}개를 다시 넣었습니다", Toast.LENGTH_LONG).show()
                        }
                    }
                    1 -> { CalendarSync.disable(this); exportToCalendar() }
                    else -> {
                        val n = CalendarExport.clear(this, id)
                        CalendarSync.disable(this)
                        Toast.makeText(this, "일정 ${n}개를 지우고 연동을 껐습니다", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("닫기", null)
            .present()
    }

    /** 안드로이드 13 이상에서는 알림 권한을 따로 받아야 소리가 난다. */

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            exportToCalendar()
        }
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

    private companion object {
        private const val SUNDAY = 0xFFE0483C.toInt()
        private const val SATURDAY = 0xFF2F6FD0.toInt()
    }
}
