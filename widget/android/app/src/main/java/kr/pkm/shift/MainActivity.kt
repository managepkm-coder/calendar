package kr.pkm.shift

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate

/** 월 단위 근무 달력. 위젯과 같은 Schedule 을 쓰므로 두 화면이 항상 일치합니다. */
class MainActivity : Activity() {

    private var month: LocalDate = LocalDate.now().withDayOfMonth(1)
    private lateinit var grid: LinearLayout
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
            // 아직 근무를 정하지 않았으면 패턴부터 정하는 안내를 띄운다.
            !Schedule.isConfigured(this) -> startSetup()
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

    /** 기기 크기에 따른 배율. 360dp 폭을 1.0 으로 본다.
     *  smallestScreenWidthDp 는 눕혀도 바뀌지 않으므로 가로로 돌렸다고 글자가 커지지 않는다. */
    private val scale: Float by lazy {
        (resources.configuration.smallestScreenWidthDp / 360f).coerceIn(0.85f, 1.4f)
    }

    /** 배율을 먹인 글자 크기 */
    private fun sp(v: Float) = v * scale

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
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(13f))
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
        val weeks = (startDow + month.lengthOfMonth() + 6) / 7

        grid.removeAllViews()
        for (w in 0 until weeks) {
            if (w > 0) grid.addView(line())
            val cells = (0 until 7).map { d ->
                val date = start.plusDays((w * 7 + d).toLong())
                buildCell(date, date.monthValue == month.monthValue)
            }
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            // 줄 높이를 직접 재서 박는다. 칸이 MATCH_PARENT 라 스스로 높이를 알리지 못하므로,
            // 이 값을 주지 않으면 줄은 늘 "화면 나누기"로만 잡히고 넘치는 띠가 조용히 잘린다.
            // 높이를 박아 두면 다 합쳐 화면보다 짧을 땐 무게로 남은 자리를 나눠 갖고,
            // 길 땐 스크롤로 넘어간다.
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, rowHeight(cells), 1f
            )
            cells.forEach { cell ->
                cell.layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                row.addView(cell)
            }
            grid.addView(row)
        }
    }

    /** 주와 주 사이를 가르는 연회색 선. 요일 사이는 긋지 않는다 — 칩만으로 충분히 갈린다. */
    private fun line(): View {
        val v = View(this)
        v.setBackgroundColor(getColor(R.color.divider))
        v.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, maxOf(1, dp(1))
        )
        return v
    }

    /** 한 칸의 너비. 줄 높이를 재려면 글이 몇 줄로 접히는지 알아야 하므로 먼저 셈한다. */
    private fun cellWidthPx(): Int {
        // 화면 여백 + 카드 여백
        val outer = dp(10) * 2 + dp(12) * 2
        return ((resources.displayMetrics.widthPixels - outer) / 7).coerceAtLeast(dp(20))
    }

    /** 그 줄에서 가장 높은 칸만큼. 날짜와 근무 띠는 늘 들어가도록 아래도 받쳐 둔다. */
    private fun rowHeight(cells: List<View>): Int {
        val wSpec = View.MeasureSpec.makeMeasureSpec(cellWidthPx(), View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        var need = dp((52 * scale).toInt())
        cells.forEach {
            it.measure(wSpec, hSpec)
            need = maxOf(need, it.measuredHeight)
        }
        return need
    }

    private fun buildCell(date: LocalDate, inMonth: Boolean): View {
        val holiday = Holidays.nameOf(date)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(3), dp(3), dp(3), dp(6))
        if (date == LocalDate.now()) col.setBackgroundResource(R.drawable.bg_today)
        col.alpha = if (inMonth) 1f else 0.42f

        // 날짜는 굵기 없이 가운데. 아래로 공휴일 · 근무 · 메모 칩이 쌓인다
        val label = TextView(this)
        label.text = if (date.dayOfMonth == 1) "%02d.%02d".format(date.monthValue, date.dayOfMonth)
                     else "%02d".format(date.dayOfMonth)
        label.gravity = Gravity.CENTER
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(13f))
        label.setTextColor(
            when {
                holiday != null || date.dayOfWeek.value == 7 -> SUNDAY
                date.dayOfWeek.value == 6 -> SATURDAY
                else -> getColor(R.color.text_primary)
            }
        )
        col.addView(label)

        if (holiday != null) {
            col.addView(
                bar(holiday, getColor(R.color.holiday_bg), getColor(R.color.holiday_fg), sp(9f), 1)
            )
        }
        Schedule.at(this, date)?.let { shift ->
            col.addView(
                bar(shift.full, getColor(Palette.barColor(shift)), Palette.fg(shift), sp(10f), 1)
            )
        }

        // 메모는 조금 작게. 하나뿐이면 두 줄까지 펴 보이고, 여럿이면 한 줄씩 줄여 담는다.
        val memos = Schedule.memosOf(this, date)
        val mbg = getColor(R.color.memo_bg)
        val mfg = getColor(R.color.memo_fg)
        if (memos.size == 1) {
            col.addView(bar(memos[0], mbg, mfg, sp(9f), 2))
        } else {
            memos.take(MEMO_BARS).forEach { col.addView(bar(it, mbg, mfg, sp(9f), 1)) }
            if (memos.size > MEMO_BARS) {
                col.addView(bar("+${memos.size - MEMO_BARS}", mbg, mfg, sp(9f), 1))
            }
        }

        col.setOnClickListener { openDay(date) }
        return col
    }

    /** 날짜 아래에 칸 너비만큼 깔리는 띠 — 근무 하나, 메모 하나. */
    private fun bar(text: String, bg: Int, fg: Int, size: Float, lines: Int): TextView {
        val t = TextView(this)
        t.text = text
        t.gravity = Gravity.CENTER
        t.maxLines = lines
        t.ellipsize = TextUtils.TruncateAt.END
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        t.setTextColor(fg)
        t.setPadding(dp(2), dp(3), dp(2), dp(3))
        t.background = GradientDrawable().apply {
            cornerRadius = dp(4).toFloat()
            setColor(bg)
        }
        t.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(3) }
        return t
    }

    /** 날짜를 누르면 나오는 메뉴.
     *  자주 쓰는 연차·지원근무는 한 번에 고를 수 있도록 첫 화면에 둔다. */
    private fun openDay(date: LocalDate) {
        // 항목과 동작을 짝지어 만들면 번호를 세다 어긋날 일이 없다
        val actions = mutableListOf<Pair<String, () -> Unit>>()

        val cycle = Schedule.cycle(this)
        cycle.forEachIndexed { i, sh ->
            // 같은 근무가 차례에 두 번 이상 나오면 이름만으로는 어느 자리인지 알 수 없다
            val name = if (cycle.count { it == sh } == 1) "${sh.fullRo} 맞추기"
                       else "${i + 1}일째(${sh.full})로 맞추기"
            actions += name to {
                val was = if (Schedule.isConfigured(this)) Schedule.offset(this) else null
                Schedule.setPatternDayOn(this, date, i)
                applyChange(
                    if (was == Schedule.offset(this)) "${label(date)}은 이미 ${sh.full}입니다"
                    else "${label(date)}을 ${sh.fullRo} 맞췄습니다 · 전체 이동"
                )
            }
        }

        for (sh in listOf(Shift.ANNUAL, Shift.SUPPORT)) {
            actions += "이 날만 ${sh.full}" to {
                Schedule.setOverride(this, date, sh)
                applyChange("${label(date)}만 ${sh.fullRo} 바꿨습니다")
            }
        }

        Schedule.memosOf(this, date).forEachIndexed { i, memo ->
            actions += "메모 · $memo" to { openMemo(date, i) }
        }
        actions += "메모 추가" to { openMemo(date, -1) }

        actions += "이 날만 바꾸기 (교대)" to { openDayOnly(date) }

        if (Schedule.overrideOf(this, date) != null) {
            actions += "이 날 지정 해제 (주기대로)" to {
                Schedule.setOverride(this, date, null)
                applyChange("${label(date)} 지정을 해제했습니다")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("%d. %02d. %02d".format(date.year, date.monthValue, date.dayOfMonth))
            .setItems(actions.map { it.first }.toTypedArray()) { _, i -> actions[i].second() }
            .setNegativeButton("닫기", null)
            .present()
    }

    /** 주기 근무를 하루만 바꾼다 — 교대로 다른 조 근무를 서는 날 */
    private fun openDayOnly(date: LocalDate) {
        // 교대로 대신 서는 근무이므로 지금 차례에 든 것들 중에서 고른다
        val choices = Schedule.cycle(this).distinct()
        AlertDialog.Builder(this)
            .setTitle("%02d. %02d  ·  이 날만 변경".format(date.monthValue, date.dayOfMonth))
            .setItems(choices.map { it.full }.toTypedArray()) { _, which ->
                Schedule.setOverride(this, date, choices[which])
                applyChange("${label(date)}만 ${choices[which].fullRo} 바꿨습니다")
            }
            .setNegativeButton("닫기", null)
            .present()
    }

    /** 그 날의 메모 하나를 쓰거나 고친다. index 가 없는 자리(-1)면 새로 덧붙인다.
     *  근무와 달리 주기·알람·캘린더와는 무관하다. */
    private fun openMemo(date: LocalDate, index: Int) {
        val saved = Schedule.memosOf(this, date).getOrNull(index)
        val input = EditText(this)
        input.setText(saved ?: "")
        input.setSelection(input.text.length)
        input.hint = "예: 치과 예약"
        // 줄바꿈이 메모 사이를 가르므로 한 줄로 받는다
        input.setSingleLine(true)
        // 창 좌우에 여백을 주지 않으면 글자가 모서리에 붙는다
        val box = FrameLayout(this)
        box.setPadding(dp(22), dp(8), dp(22), 0)
        box.addView(input)

        val b = AlertDialog.Builder(this)
            .setTitle(
                "%02d. %02d  ·  %s".format(
                    date.monthValue, date.dayOfMonth, if (saved == null) "메모 추가" else "메모"
                )
            )
            .setView(box)
            .setPositiveButton("저장") { _, _ ->
                if (saved == null) Schedule.addMemo(this, date, input.text.toString())
                else Schedule.editMemo(this, date, index, input.text.toString())
                afterMemo(date, "메모를 저장했습니다")
            }
            .setNegativeButton("닫기", null)
        // 아직 없는 메모는 지울 것도 없다
        if (saved != null) b.setNeutralButton("지우기") { _, _ ->
            Schedule.editMemo(this, date, index, null)
            afterMemo(date, "메모를 지웠습니다")
        }
        b.present()
    }

    /** 메모는 근무를 바꾸지 않으므로 알람·캘린더까지 건드리지 않고 화면만 다시 그린다. */
    private fun afterMemo(date: LocalDate, message: String) {
        render()
        Toast.makeText(this, "${label(date)} $message", Toast.LENGTH_SHORT).show()
    }

    private fun label(date: LocalDate) = "%d월 %d일".format(date.monthValue, date.dayOfMonth)

    private fun applyChange(message: String) {
        ShiftWidget.refreshAll(this)
        ShiftWidget.scheduleMidnight(this)
        Alarms.rescheduleAll(this)        // 근무가 바뀌면 알람 날짜도 달라진다
        CalendarSync.onScheduleChanged(this)  // 캘린더에 내보낸 일정도 다시 맞춘다
        render()
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun openSettings() {
        val actions = mutableListOf<Pair<String, () -> Unit>>()

        actions += "오늘 위치 맞추기" to { pickToday() }
        actions += "근무 패턴  ·  ${patternLabel()}" to { startSetup() }
        actions += "출근 알람" to { openAlarms() }
        actions += (
            if (CalendarSync.isEnabled(this)) "캘린더 자동 연동  ·  켜짐"
            else "캘린더에 내보내기"
            ) to { exportToCalendar() }
        actions += "직접 지정한 날짜 모두 지우기" to {
            Schedule.clearOverrides(this)
            applyChange("직접 지정한 날짜를 모두 지웠습니다")
        }

        AlertDialog.Builder(this)
            .setTitle("설정")
            .setItems(actions.map { it.first }.toTypedArray()) { _, i -> actions[i].second() }
            .setNegativeButton("닫기", null)
            .present()
    }

    /** "주 · 야 · 비 · 휴 (4일)" 처럼 지금 차례를 한 줄로 */
    private fun patternLabel(): String {
        val cycle = Schedule.cycle(this)
        return "${cycle.joinToString(" · ") { it.brief }}  (${cycle.size}일)"
    }

    /** 처음 켰을 때, 그리고 ⚙ 에서 다시 부를 때 — 되풀이되는 차례부터 정한다. */
    private fun startSetup() {
        AlertDialog.Builder(this)
            .setTitle("근무 패턴  ·  되풀이되는 차례를 정합니다")
            .setItems(
                arrayOf(
                    "주 · 야 · 비 · 휴   (4일 주기)",
                    "직접 만들기",
                )
            ) { _, which ->
                if (which == 0) {
                    Schedule.setCycle(this, Shift.DEFAULT_CYCLE)
                    pickToday()
                } else {
                    buildPattern(mutableListOf())
                }
            }
            .setNegativeButton("닫기", null)
            .present()
    }

    /** 하루씩 골라 차례를 만든다. 이틀 이상 쌓이면 거기서 끝낼 수 있다. */
    private fun buildPattern(days: MutableList<Shift>) {
        val choices = Shift.PATTERN_CHOICES
        val items = choices.map { it.full } +
            if (days.size >= 2) listOf("여기까지 — ${days.size}일 주기로 저장") else emptyList()
        AlertDialog.Builder(this)
            .setTitle(
                "%d일째 근무\n%s".format(
                    days.size + 1,
                    if (days.isEmpty()) "아직 고른 것 없음" else days.joinToString(" · ") { it.brief }
                )
            )
            .setItems(items.toTypedArray()) { _, i ->
                if (i >= choices.size) {
                    savePattern(days)
                    return@setItems
                }
                days += choices[i]
                // 더 담을 자리가 없으면 거기서 끝낸다
                if (days.size >= Schedule.MAX_PATTERN) savePattern(days) else buildPattern(days)
            }
            .setNegativeButton("닫기", null)
            .present()
    }

    private fun savePattern(days: List<Shift>) {
        Schedule.setCycle(this, days)
        // 차례가 바뀌면 오늘이 어느 자리인지 다시 정해야 근무가 나온다
        pickToday()
    }

    /** 오늘이 차례의 몇 번째 날인지 고른다. 이것 하나면 나머지 날짜가 전부 맞춰진다. */
    private fun pickToday() {
        val cycle = Schedule.cycle(this)
        AlertDialog.Builder(this)
            .setTitle("오늘은 차례의 몇 번째 날인가요\n${patternLabel()}")
            .setItems(
                cycle.mapIndexed { i, sh -> "${i + 1}일째  ·  ${sh.full}" }.toTypedArray()
            ) { _, i ->
                Schedule.setPatternDayOn(this, LocalDate.now(), i)
                applyChange("오늘을 ${i + 1}일째(${cycle[i].full})로 맞췄습니다 · 전체 이동")
            }
            .setNegativeButton("닫기", null)
            .present()
    }

    /** 근무 종류별 출근 알람. 해당 근무인 날에만 울린다. */
    private fun openAlarms() {
        val targets = Alarms.targets(this)
        val items = targets
            .map { "${it.full}   ${Alarms.label(this, Alarms.timeOf(this, it))}" }
            .toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("출근 알람  ·  근무별 시각")
            .setItems(items) { _, which -> pickAlarmTime(targets[which]) }
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
        chooseTarget()
    }

    /** 캘린더 → 기간 순서로 고른다. 끝까지 고르기 전에는 지금 설정을 건드리지 않으므로
     *  중간에 닫아도 쓰던 연동이 그대로 남는다. */
    private fun chooseTarget() {
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
                switchTo(target, months[i])
            }
            .setNegativeButton("닫기", null)
            .present()
    }

    /** 여기서 비로소 설정이 바뀐다 — 캘린더와 기간을 모두 고른 뒤에만 부른다. */
    private fun switchTo(target: CalendarExport.Target, months: Long) {
        // 다른 캘린더로 옮기는 길이면 전에 쓰던 곳의 일정을 먼저 거둔다.
        // 내보내기는 넣을 캘린더만 비우므로, 이걸 빼면 옛 캘린더에 그대로 남는다.
        val previous = CalendarSync.target(this)?.first
        if (previous != null && previous != target.id) CalendarExport.clear(this, previous)

        CalendarSync.enable(this, target.id, months)
        Toast.makeText(this, "내보내는 중…", Toast.LENGTH_SHORT).show()
        CalendarSync.syncNow(this) { n ->
            Toast.makeText(this, "일정 ${n}개를 넣었습니다 · 자동 연동 켜짐", Toast.LENGTH_LONG).show()
        }
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
                    1 -> chooseTarget()
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
            chooseTarget()      // 권한을 물은 곳이 고르기 화면이므로 그리로 되돌아간다
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
        /** 메모가 여럿일 때 칸에 늘어놓는 최대 개수. 그 뒤는 +N 으로 뭉친다. */
        private const val MEMO_BARS = 3

        private const val SUNDAY = 0xFFE0483C.toInt()
        private const val SATURDAY = 0xFF2F6FD0.toInt()
    }
}
