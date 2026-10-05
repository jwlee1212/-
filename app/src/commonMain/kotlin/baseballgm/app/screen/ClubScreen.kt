package baseballgm.app.screen

import baseballgm.app.ui.AppTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import baseballgm.app.GameSession
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.MeterBar
import baseballgm.app.ui.Pill
import baseballgm.app.ui.SectionCard
import baseballgm.app.ui.SectionDivider
import baseballgm.app.ui.StatRow
import kotlin.math.roundToInt

/**
 * 구단 화면 (docs/13, docs/15 M10 표).
 *
 * 재정·구단주·팬심·스태프를 한 화면에 모았다. 이 네 가지는 서로 묶여 있다 —
 * 성적이 팬심을 올리고, 팬심이 관중 수입을 올리고, 수입이 적자를 줄이고, 적자가 구단주
 * 신뢰도를 지킨다. 화면에서도 그 연결이 보이게 배치했다.
 */
@Composable
fun ClubScreen(session: GameSession) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    var tab by rememberSaveable { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("재정") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("연봉 계획") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("구단주") })
            Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("스태프") })
        }
        when (tab) {
            0 -> FinanceTab(session)
            1 -> PayrollPlanTab(session)
            2 -> OwnerTab(session)
            else -> StaffTab(session)
        }
    }
}

@Composable
private fun FinanceTab(session: GameSession) {
    val projected = session.projectedFinance()
    val last = session.finance()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = AppTheme.tokens.spacing.l),
        verticalArrangement = Arrangement.spacedBy(AppTheme.tokens.spacing.m),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = AppTheme.tokens.spacing.s),
    ) {
        item {
            SectionCard("${session.league.season} 시즌 예상") {
                // 핵심 숫자(손익)를 맨 위에 — 한 줄 요약 → 핵심 숫자 → 상세 표 (docs/16 §3)
                val deficit = projected.deficit
                Text(
                    "${if (deficit > 0) "적자" else "흑자"} ${money(kotlin.math.abs(deficit))}",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (projected.withinAllowance) AppColors.good else AppColors.bad,
                )
                Text(
                    "구단주 허용 적자 ${money(session.allowedDeficit())} · 자체 수입으로 모자란 만큼 모기업이 메워요",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.tokens.base.textMuted,
                )
                SectionDivider()
                StatRow("관중", money(projected.revenue.attendance))
                StatRow("중계권", money(projected.revenue.broadcast))
                StatRow("스폰서·굿즈", money(projected.revenue.sponsorship))
                StatRow("포스트시즌", money(projected.revenue.postseason))
                StatRow("모기업 지원", money(projected.revenue.parentSupport))
                StatRow("수입 합계", money(projected.revenue.total), AppColors.good)
                Spacer(Modifier.height(AppTheme.tokens.spacing.s))
                StatRow("선수단 연봉", money(projected.expenses.payroll))
                StatRow("스태프 연봉", money(projected.expenses.staff))
                StatRow("스카우트", money(projected.expenses.scouting))
                StatRow("지출 합계", money(projected.expenses.total), AppColors.bad)
                Spacer(Modifier.height(AppTheme.tokens.spacing.s))
                StatRow("관중률", "${(projected.attendanceRate * 100).roundToInt()}%")
                StatRow("운용 자금", money(projected.fundsBefore))
            }
        }
        last?.let { report ->
            item {
                SectionCard("${report.season} 시즌 결산") {
                    Text(report.line(), style = MaterialTheme.typography.bodyMedium)
                    StatRow("결산 후 운용 자금", money(report.fundsAfter))
                }
            }
        }
    }
}

/**
 * 연봉 계획 (2026-10-04). 앞으로 몇 년의 샐러리캡 여유·운용 자금 → 해마다 FA 로 풀리는 선수 → 선수별 장부.
 * FA·트레이드 전에 "언제 돈이 비는지"를 보고 계획을 세우는 화면이다.
 */
@Composable
private fun PayrollPlanTab(session: GameSession) {
    val plan = session.financialPlan()
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = AppTheme.tokens.spacing.l),
        verticalArrangement = Arrangement.spacedBy(AppTheme.tokens.spacing.m),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = AppTheme.tokens.spacing.s),
    ) {
        item {
            SectionCard("앞으로 ${plan.years.size}년") {
                StatRow("샐러리캡", money(session.salaryCap))
                StatRow("지금 운용 자금", money(session.currentFunds()))
                Text(
                    "연봉은 캡 안에서, 계약금·트레이드 현금은 운용 자금에서 나가요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.tokens.base.textMuted,
                )
                SectionDivider()
                FinancePlanSummary(plan)
            }
        }
        item {
            SectionCard("FA 로 풀리는 선수") { FreeAgentTimeline(plan) }
        }
        item {
            SectionCard("선수별 연봉") { ContractLedger(plan) }
        }
    }
}

@Composable
private fun OwnerTab(session: GameSession) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = AppTheme.tokens.spacing.l),
        verticalArrangement = Arrangement.spacedBy(AppTheme.tokens.spacing.m),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = AppTheme.tokens.spacing.s),
    ) {
        item {
            SectionCard("구단주", trailing = { ScoreLabel(session.ownerTrust(), session.ownerTrustLabel()) }) {
                // 신뢰도는 능력치가 아니라서 등급 색이 아니라 의미 색 (색 그룹 분리, docs/16 §6)
                MeterBar(session.ownerTrust(), moodColor(session.ownerTrust()))
                Spacer(Modifier.height(AppTheme.tokens.spacing.s))
                StatRow("올 시즌 목표", session.seasonGoal().description)
                StatRow("기대 승수", "${session.expectedWins().roundToInt()}승")
                session.ownerWarning()?.let {
                    Spacer(Modifier.height(AppTheme.tokens.spacing.s))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.bad)
                }
            }
        }
        item {
            SectionCard("팬심", trailing = { ScoreLabel(session.fanSupport(), session.fanLabel()) }) {
                MeterBar(session.fanSupport(), moodColor(session.fanSupport()))
                Spacer(Modifier.height(AppTheme.tokens.spacing.s))
                Text(
                    "승리와 포스트시즌이 팬심을 올려요. 프랜차이즈 스타를 내보내면 한 번에 크게 빠져요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            val company = session.userTeam.parentCompany
            SectionCard("모기업") {
                StatRow("이름", company?.name ?: "없음")
                StatRow("연간 지원금", money(company?.annualSupport ?: 0.0))
                StatRow("재정 상태", "${company?.financialHealth ?: 0}")
            }
        }
    }
}

@Composable
private fun StaffTab(session: GameSession) {
    var message by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = AppTheme.tokens.spacing.l),
        verticalArrangement = Arrangement.spacedBy(AppTheme.tokens.spacing.m),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = AppTheme.tokens.spacing.s),
    ) {
        item {
            SectionCard("우리 스태프") {
                StatRow("연봉 합계", money(session.staffSalary()))
                Spacer(Modifier.height(AppTheme.tokens.spacing.xs))
                session.manager()?.let { StatRow("감독", "${it.name} (평판 ${it.reputation})") }
                session.coaches().forEach { coach ->
                    StatRow(
                        coachLabel(coach.role),
                        "${coach.name} ${coach.grade}등급${coach.focus?.let { " · ${it.attribute.label}" } ?: ""}",
                    )
                }
                session.medicalStaff().forEach { staff ->
                    StatRow(medicalLabel(staff.role), "${staff.name} ${staff.grade}등급")
                }
            }
        }
        item {
            SectionCard("스태프 시장") {
                Text(
                    "등급 효과는 10~20%로 작아요. 대신 연봉이 운용 자금에서 나가요. " +
                        "좋은 스태프는 다른 구단이 먼저 데려가요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                message?.let {
                    Spacer(Modifier.height(AppTheme.tokens.spacing.xs))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.warn)
                }
            }
        }
        items(session.staffOffers(), key = { it.id.value }) { offer ->
            Card(
                Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(AppTheme.tokens.radii.card),
                colors = CardDefaults.cardColors(containerColor = AppTheme.tokens.base.card),
            ) {
                Row(Modifier.padding(horizontal = AppTheme.tokens.spacing.l, vertical = AppTheme.tokens.spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(offer.name, fontWeight = FontWeight.Bold)
                        Text(
                            "${offer.roleLabel} · ${offer.grade}등급 · ${money(offer.askingSalary)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 줄마다 색 채운 버튼을 두지 않는다 — 색 면은 진행 버튼 하나뿐 (절제 규칙)
                    OutlinedButton(onClick = {
                        message = if (session.hireStaff(offer)) {
                            "${offer.name} 영입"
                        } else {
                            "운용 자금이 모자라거나 이미 다른 팀으로 갔어요"
                        }
                    }) { Text("영입") }
                }
            }
        }
    }
}

private fun coachLabel(role: baseballgm.model.CoachRole): String = when (role) {
    baseballgm.model.CoachRole.BATTING -> "타격코치"
    baseballgm.model.CoachRole.PITCHING -> "투수코치"
    baseballgm.model.CoachRole.FIELDING -> "수비주루코치"
    baseballgm.model.CoachRole.FUTURES_MANAGER -> "2군 감독"
}

private fun medicalLabel(role: baseballgm.model.MedicalRole): String = when (role) {
    baseballgm.model.MedicalRole.TEAM_DOCTOR -> "팀 닥터"
    baseballgm.model.MedicalRole.REHAB_TRAINER -> "재활 트레이너"
    baseballgm.model.MedicalRole.CONDITIONING_COACH -> "컨디셔닝 코치"
}

private fun money(value: Double): String = "${(value * 10).roundToInt() / 10.0}억"

/** 카드 머리 오른쪽의 "60 · 신뢰" */
@Composable
private fun ScoreLabel(value: Int, label: String) {
    Text("$value · $label", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = moodColor(value))
}

@Composable
private fun moodColor(value: Int) = when {
    value >= 65 -> AppColors.good
    value >= 40 -> AppColors.warn
    else -> AppColors.bad
}
