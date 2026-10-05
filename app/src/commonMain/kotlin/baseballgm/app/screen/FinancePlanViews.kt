package baseballgm.app.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.MeterBar
import baseballgm.app.ui.SectionDivider
import baseballgm.app.ui.StatTable
import baseballgm.app.ui.TableCell
import baseballgm.app.ui.TableColumn
import baseballgm.management.FinancialPlan
import baseballgm.management.OutlookYear
import baseballgm.management.SalarySource
import baseballgm.util.fixed
import kotlin.math.roundToInt

/**
 * 연봉 계획 — 앞으로 몇 년의 샐러리캡 여유와 운용 자금 (2026-10-04 유저 요청).
 *
 * 돈이 두 주머니라는 걸 화면에서 바로 보이게 한다:
 * - **연봉 총액 ↔ 샐러리캡**: FA·트레이드로 늘어나는 연봉은 여기서 여유를 깎는다
 * - **운용 자금**: 계약금·트레이드 현금은 여기서 한 번에 나간다
 *
 * 한 줄 요약 → 해마다 캡 막대(핵심 숫자) → 상세 표 순서 (docs/16 §3).
 */
@Composable
fun ColumnScope.FinancePlanSummary(plan: FinancialPlan, showTable: Boolean = true) {
    val tokens = AppTheme.tokens
    Text(planHeadline(plan), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(tokens.spacing.s))
    plan.years.forEach { year -> CapBarRow(year) }
    if (!showTable) return
    SectionDivider()
    PlanTable(plan)
    Spacer(Modifier.height(tokens.spacing.s))
    AssumptionNote()
}

/** 시즌 하나: "2027  ███████░░  98.2 / 120억 · 여유 21.8억" */
@Composable
private fun CapBarRow(year: OutlookYear) {
    val tokens = AppTheme.tokens
    val ratio = year.payroll / year.cap
    val color = capColor(year)
    Row(Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text("${year.season}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(44.dp))
        MeterBar((ratio * 100).roundToInt(), color, Modifier.weight(1f))
        Spacer(Modifier.width(tokens.spacing.s))
        Text(
            if (year.capRoom >= 0) "여유 ${money(year.capRoom)}" else "초과 ${money(-year.capRoom)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.width(84.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun PlanTable(plan: FinancialPlan) {
    val years = plan.years
    val muted = AppColors.muted
    val good = AppColors.good
    val warn = AppColors.warn
    val bad = AppColors.bad
    data class Line(val label: String, val cell: (OutlookYear) -> TableCell)
    val lines = buildList {
        add(Line("확정 연봉") { TableCell(it.contracted.fixed(1)) })
        add(Line("재계약 예상") { TableCell(it.estimated.fixed(1), muted) })
        if (years.any { it.planned > 0.0 }) add(Line("검토 중") { TableCell(it.planned.fixed(1), warn) })
        add(Line("연봉 총액") { TableCell(it.payroll.fixed(1), bold = true) })
        add(Line("캡 여유") { TableCell(it.capRoom.fixed(1), if (it.capRoom >= 0) good else bad, bold = true) })
        if (years.any { it.capFine > 0.0 }) add(Line("제재금") { TableCell(it.capFine.fixed(1), if (it.capFine > 0) bad else null) })
        add(Line("수입") { TableCell(it.revenue.fixed(1)) })
        add(Line("스태프·스카우트") { TableCell((it.staff + it.scouting).fixed(1)) })
        add(Line("적자") { TableCell(it.deficit.fixed(1), if (it.withinAllowance) null else bad) })
        add(Line("운용 자금(연말)") { TableCell(it.fundsEnd.fixed(1), if (it.fundsEnd <= 0.0) bad else null, bold = true) })
    }
    StatTable(
        firstHeader = "억원",
        firstWidth = 104.dp,
        columns = years.map { TableColumn("${it.season}", 56.dp) },
        rowCount = lines.size,
        firstCell = { row -> Text(lines[row].label, style = MaterialTheme.typography.bodySmall, maxLines = 1) },
        cell = { row, column -> lines[row].cell(years[column]) },
    )
}

@Composable
private fun AssumptionNote() {
    Text(
        "재계약 예상은 FA 자격이 안 되는 선수·외국인을 지금 연봉으로 붙잡는다고 본 금액이에요. " +
            "수입은 올해만 지금 성적으로, 그 뒤는 5할·포스트시즌 없음으로 계산했어요. " +
            "운용 자금이 바닥나면 모자란 만큼 모기업이 메우지만 구단주 신뢰가 떨어져요.",
        style = MaterialTheme.typography.bodySmall,
        color = AppTheme.tokens.base.textMuted,
    )
}

/**
 * "이 계약(트레이드)을 하면" — 해마다 캡 여유와 연말 운용 자금이 어떻게 바뀌는지.
 * FA 조건 창·트레이드 교환대에서 슬라이더를 움직일 때마다 바로 다시 계산한다.
 */
@Composable
fun PlanChangeTable(before: FinancialPlan, after: FinancialPlan) {
    val tokens = AppTheme.tokens
    val good = AppColors.good
    val bad = AppColors.bad
    val muted = AppColors.muted
    fun delta(value: Double): TableCell = when {
        value > 0.05 -> TableCell("+${value.fixed(1)}", good)
        value < -0.05 -> TableCell(value.fixed(1), bad)
        else -> TableCell("—", muted)
    }
    data class Line(val label: String, val cell: (Int) -> TableCell)
    val lines = listOf(
        Line("캡 여유") { i -> after.years[i].capRoom.let { TableCell(it.fixed(1), if (it >= 0) good else bad, bold = true) } },
        Line("  변화") { i -> delta(after.years[i].capRoom - before.years[i].capRoom) },
        Line("운용 자금(연말)") { i -> after.years[i].fundsEnd.let { TableCell(it.fixed(1), if (it <= 0.0) bad else null, bold = true) } },
        Line("  변화") { i -> delta(after.years[i].fundsEnd - before.years[i].fundsEnd) },
    )
    Text(changeHeadline(before, after), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(tokens.spacing.xs))
    StatTable(
        firstHeader = "억원",
        firstWidth = 104.dp,
        columns = after.years.map { TableColumn("${it.season}", 56.dp) },
        rowCount = lines.size,
        firstCell = { row -> Text(lines[row].label, style = MaterialTheme.typography.bodySmall, maxLines = 1) },
        cell = { row, column -> lines[row].cell(column) },
    )
}

/**
 * 선수별 연봉 장부 — 누가 언제까지 얼마를 받고, 언제 FA 로 풀리는지.
 * 확정은 진하게, 재계약 예상은 회색 "~", 검토 중은 경고 색, FA 로 빠지는 해 다음 칸은 "FA".
 */
@Composable
fun ColumnScope.ContractLedger(plan: FinancialPlan, initialRows: Int = 15) {
    val tokens = AppTheme.tokens
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rows = if (expanded) plan.players else plan.players.take(initialRows)
    val muted = AppColors.muted
    val warn = AppColors.warn
    val text = tokens.base.text
    val seasons = plan.seasons

    StatTable(
        firstHeader = "선수",
        firstWidth = 96.dp,
        columns = seasons.map { TableColumn("$it", 56.dp) },
        rowCount = rows.size,
        firstCell = { row ->
            Text(rows[row].name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        cell = { row, column ->
            val player = rows[row]
            val cell = player.cells[column]
            val faAt = player.freeAgentAfter
            when {
                cell != null -> when (cell.source) {
                    SalarySource.CONTRACT -> TableCell(cell.amount.fixed(1), text)
                    SalarySource.ESTIMATE -> TableCell("~" + cell.amount.fixed(1), muted)
                    SalarySource.PLANNED -> TableCell(cell.amount.fixed(1), warn, bold = true)
                }
                faAt != null && seasons[column] == faAt + 1 -> TableCell("FA", warn)
                else -> TableCell("", null)
            }
        },
    )
    if (plan.players.size > initialRows) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "접기" else "${plan.players.size - initialRows}명 더 보기")
        }
    }
    Text(
        "숫자 = 계약 · ~숫자 = 재계약 예상 · FA = 그해부터 FA 시장으로",
        style = MaterialTheme.typography.labelSmall,
        color = muted,
    )
}

/** 해마다 계약이 끝나 FA 로 풀리는 선수와 비는 연봉 */
@Composable
fun ColumnScope.FreeAgentTimeline(plan: FinancialPlan) {
    val tokens = AppTheme.tokens
    var any = false
    plan.seasons.forEach { season ->
        val leaving = plan.freeAgentsAfter(season)
        if (leaving.isEmpty()) return@forEach
        any = true
        val freed = leaving.sumOf { row -> row.cells[plan.seasons.indexOf(season)]?.amount ?: 0.0 }
        Column(Modifier.padding(vertical = tokens.spacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${season} 시즌 끝 · ${leaving.size}명", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("연 ${money(freed)} 비어요", style = MaterialTheme.typography.bodyMedium, color = AppColors.good)
            }
            Text(
                leaving.joinToString(" · ") { "${it.name} ${(it.cells[plan.seasons.indexOf(season)]?.amount ?: 0.0).fixed(1)}" },
                style = MaterialTheme.typography.bodySmall,
                color = tokens.base.textMuted,
            )
        }
    }
    if (!any) Text("앞으로 ${plan.years.size}년 안에 FA 로 풀리는 선수가 없어요.", style = MaterialTheme.typography.bodySmall)
}

// ---------- 문장 (비서 말투) ----------

/** 가장 먼저 눈에 들어와야 할 한 줄: 넘는 해가 있으면 그것, 없으면 가장 빠듯한 해 */
private fun planHeadline(plan: FinancialPlan): String {
    val over = plan.years.firstOrNull { it.capRoom < 0 }
    val broke = plan.years.firstOrNull { it.coveredByOwner > 0.0 }
    return when {
        over != null -> "${over.season} 시즌 연봉이 상한을 ${money(-over.capRoom)} 넘어요. 제재금 ${money(over.capFine)}이 붙어요."
        broke != null -> "${broke.season} 시즌에 운용 자금이 바닥나요. 모기업이 ${money(broke.coveredByOwner)} 메워야 해요."
        else -> {
            val now = plan.years.first()
            val tight = plan.years.minBy { it.capRoom }
            if (tight.season == now.season) {
                "올해 캡 여유 ${money(now.capRoom)}, 운용 자금은 ${money(now.fundsStart)}이에요."
            } else {
                "올해 캡 여유 ${money(now.capRoom)} · 가장 빠듯한 해는 ${tight.season}년(${money(tight.capRoom)})이에요."
            }
        }
    }
}

private fun changeHeadline(before: FinancialPlan, after: FinancialPlan): String {
    val over = after.years.firstOrNull { it.capRoom < 0 }
    val nowBefore = before.years.first()
    val nowAfter = after.years.first()
    val cashNow = nowBefore.fundsStart - nowAfter.fundsStart
    return buildString {
        if (cashNow > 0.05) append("지금 운용 자금에서 ${money(cashNow)} 나가요. ")
        if (cashNow < -0.05) append("지금 운용 자금이 ${money(-cashNow)} 늘어요. ")
        append(
            if (over != null) "${over.season} 시즌엔 상한을 ${money(-over.capRoom)} 넘어요 (제재금 ${money(over.capFine)})."
            else "앞으로 ${after.years.size}년 모두 상한 안이에요.",
        )
    }
}

@Composable
private fun capColor(year: OutlookYear): Color = when {
    year.capRoom < 0 -> AppColors.bad
    year.capRoom < year.cap * TIGHT_SHARE -> AppColors.warn
    else -> AppColors.good
}

/** 상한의 이 비율보다 여유가 적으면 "빠듯" 색 (표시용 기준, 밸런스 수치 아님) */
private const val TIGHT_SHARE = 0.05

private fun money(value: Double): String = "${value.fixed(1)}억"
