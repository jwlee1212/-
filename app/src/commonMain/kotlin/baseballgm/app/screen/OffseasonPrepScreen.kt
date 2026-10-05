package baseballgm.app.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.PlayerLine
import baseballgm.app.ui.Pill
import baseballgm.app.ui.SecretaryCard
import baseballgm.app.ui.SectionCard
import baseballgm.model.PlayerId
import baseballgm.model.StaffContract
import baseballgm.model.StaffId
import baseballgm.util.fixed

/**
 * 스토브리그 준비 (2026-10-01, 진단 2번 "스토브리그를 결정의 시간으로").
 *
 * 시즌이 끝나면 비서가 겨울 결정거리를 한 화면에 모아 브리핑한다. 비서 추천으로 미리 채워져 있어서
 * 아무것도 안 건드리고 시작해도 되고, 하나하나 바꿔도 된다.
 *
 * 위에서부터: 한 줄 요약(예상 인원) → 외국인 재계약 → 연봉 재계약 → FA 자격 취득(안내) → 방출 명단 → 스태프 → 시작 버튼.
 * 시작은 되돌릴 수 없어서 확인 창을 띄운다 (docs/16 "되돌릴 수 없는 행동의 확인 창").
 */
@Composable
fun OffseasonPrepScreen(session: GameSession, onPlayer: (PlayerId) -> Unit, onDone: () -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val tokens = AppTheme.tokens

    if (!session.offseasonPrep) {
        Column(Modifier.fillMaxSize().padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m)) {
            SecretaryCard("지금은 스토브리그를 준비할 때가 아니에요. 정규시즌과 포스트시즌이 끝나면 여기서 겨울 결정을 같이 정해요.")
        }
        return
    }

    val preview = session.offseasonPreview()
    val plan = session.offseasonPlan()
    val projected = session.projectedRoster()
    var confirm by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.m),
    ) {
        SecretaryCard(
            "${session.league.season} 시즌 고생 많으셨어요. 겨울에 정할 게 몇 가지 있어요. " +
                "제가 추천대로 채워 뒀으니 마음에 안 드는 것만 바꾸시면 돼요.",
            title = "스토브리그 준비",
        ) {
            val color = when {
                projected > preview.maxRoster -> AppColors.bad
                projected > preview.targetRoster -> AppColors.warn
                else -> AppColors.good
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("다음 시즌 예상 선수단", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${projected}명", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            }
            Text(
                "목표 ${preview.targetRoster}명 · 최대 ${preview.maxRoster}명 (넘으면 가장 아래부터 자동 방출). 은퇴는 스토브리그에서 정해져서 빠져 있어요.",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.base.textMuted,
            )
        }

        ForeignSection(session, preview, plan, onPlayer)
        SalarySection(session, preview, plan, onPlayer)
        FreeAgentSection(session, preview, onPlayer)
        ReleaseSection(session, preview, plan, projected, onPlayer)
        StaffSection(session)

        Button(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = !session.busy) {
            Text("이대로 스토브리그 시작", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(tokens.spacing.s))
    }

    if (confirm) {
        val letGo = preview.foreign.count { plan.foreign[it.playerId] == null }
        val lowballs = plan.salaries.count { it.value.lowball }
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("스토브리그를 시작할까요?") },
            text = {
                Text(
                    "방출 ${plan.releases.size}명 · 외국인 결별 ${letGo}명 · 연봉 깎아서 제시 ${lowballs}명. " +
                        "시작하면 성장·은퇴·드래프트 입단·결산이 한꺼번에 처리되고 FA 시장이 열려요. 되돌릴 수 없어요.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    session.confirmOffseasonPlan()
                    onDone()
                }) { Text("시작할게요") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("조금 더 볼게요") } },
        )
    }
}

// ---------- 외국인 ----------

@Composable
private fun ForeignSection(
    session: GameSession,
    preview: baseballgm.season.OffseasonPreview,
    plan: baseballgm.season.OffseasonPlan,
    onPlayer: (PlayerId) -> Unit,
) {
    if (preview.foreign.isEmpty()) return
    val tokens = AppTheme.tokens
    SectionCard("외국인 재계약") {
        Text(
            "외국인은 1년 계약이라 매년 다시 잡아야 해요. 결별하면 스토브리그에서 새 외국인을 찾아요.",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.base.textMuted,
        )
        preview.foreign.forEach { renewal ->
            val player = session.player(renewal.playerId)
            val resign = plan.foreign[renewal.playerId] != null
            PlayerHeader(session, player.id, onPlayer) {
                if (renewal.recommendResign) Pill("비서 추천: 재계약", tokens.base.brand) else Pill("비서 추천: 결별", AppColors.muted)
            }
            Text(
                "WAR ${renewal.war.fixed(1)} · ${session.seasonSummary(player)}",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.base.textSecondary,
            )
            if (renewal.outflow) {
                Text(
                    "일본 구단이 ${renewal.asking.fixed(1)}억을 제안했어요. 잡으려면 그만큼 줘야 해요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.warn,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s), modifier = Modifier.padding(bottom = tokens.spacing.s)) {
                FilterChip(
                    selected = resign,
                    onClick = { session.setForeignResign(renewal.playerId, true) },
                    label = { Text("재계약 ${renewal.asking.fixed(1)}억 (올해 ${renewal.currentSalary.fixed(1)}억)") },
                )
                FilterChip(selected = !resign, onClick = { session.setForeignResign(renewal.playerId, false) }, label = { Text("결별") })
            }
        }
    }
}

// ---------- 연봉 ----------

@Composable
private fun SalarySection(
    session: GameSession,
    preview: baseballgm.season.OffseasonPreview,
    plan: baseballgm.season.OffseasonPlan,
    onPlayer: (PlayerId) -> Unit,
) {
    if (preview.salaries.isEmpty()) return
    val tokens = AppTheme.tokens
    var expanded by rememberSaveable { mutableStateOf(false) }
    // 액수가 큰 계약부터 — 결정의 무게가 큰 순서
    val cases = preview.salaries.sortedByDescending { it.demand }
    val shown = if (expanded) cases else cases.take(SALARY_PREVIEW)
    val saved = cases.filter { plan.salaries[it.playerId]?.lowball == true }.sumOf { it.demand - it.lowball }
    SectionCard(
        "연봉 재계약 ${cases.size}명",
        trailing = { if (saved > 0) Pill("아끼는 돈 ${saved.fixed(1)}억", AppColors.good) },
    ) {
        Text(
            "계약이 끝난 선수들이에요. 요구액은 올해 활약(WAR)으로 정해졌어요. 깎아서 제시하면 연봉은 아끼지만, 서운해서 개막 때 폼이 떨어져요.",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.base.textMuted,
        )
        shown.forEach { case ->
            val lowball = plan.salaries[case.playerId]?.lowball == true
            PlayerHeader(session, case.playerId, onPlayer) {
                Text(
                    "WAR ${case.war.fixed(1)} · 올해 ${case.currentSalary.fixed(2)}억",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.base.textSecondary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s), modifier = Modifier.padding(bottom = tokens.spacing.s)) {
                FilterChip(
                    selected = !lowball,
                    onClick = { session.setLowball(case.playerId, false) },
                    label = { Text("요구 수용 ${case.demand.fixed(2)}억") },
                )
                FilterChip(
                    selected = lowball,
                    onClick = { session.setLowball(case.playerId, true) },
                    label = { Text("깎아서 ${case.lowball.fixed(2)}억") },
                )
            }
        }
        if (cases.size > SALARY_PREVIEW) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "접기" else "나머지 ${cases.size - SALARY_PREVIEW}명 보기")
            }
        }
    }
}

// ---------- FA ----------

@Composable
private fun FreeAgentSection(session: GameSession, preview: baseballgm.season.OffseasonPreview, onPlayer: (PlayerId) -> Unit) {
    if (preview.freeAgents.isEmpty()) return
    SectionCard("FA 자격 취득 ${preview.freeAgents.size}명") {
        Text(
            "이 선수들은 계약이 끝나 FA 시장에 나가요. 붙잡으려면 FA 시장에서 다른 구단과 경쟁해서 조건을 내야 해요.",
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.tokens.base.textMuted,
        )
        preview.freeAgents.forEach { id -> PlayerHeader(session, id, onPlayer) {} }
    }
}

// ---------- 방출 ----------

@Composable
private fun ReleaseSection(
    session: GameSession,
    preview: baseballgm.season.OffseasonPreview,
    plan: baseballgm.season.OffseasonPlan,
    projected: Int,
    onPlayer: (PlayerId) -> Unit,
) {
    val tokens = AppTheme.tokens
    var all by rememberSaveable { mutableStateOf(false) }
    // 추천·체크된 선수 + 남길 가치가 낮은 순으로 몇 명 더. 전체 보기를 누르면 모두
    val ordered = preview.releaseCandidates
    val shown = if (all) ordered else ordered.filterIndexed { index, it -> it.playerId in plan.releases || index < RELEASE_PREVIEW }
    SectionCard(
        "방출 명단 ${plan.releases.size}명",
        trailing = { Pill("예상 ${projected}명", if (projected > preview.maxRoster) AppColors.bad else AppColors.muted) },
    ) {
        Text(
            "남길 가치(지금 능력 + 잠재력 등급 + 나이)가 낮은 순이에요. 체크한 선수를 내보내요. " +
                "방출한 선수 중 쓸 만한 선수는 시장에 나가서 다른 구단이 데려갈 수 있어요.",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.base.textMuted,
        )
        shown.forEach { candidate ->
            val player = session.player(candidate.playerId)
            val scouted = session.scout(player)
            val checked = candidate.playerId in plan.releases
            Row(
                Modifier.fillMaxWidth().clickable { session.toggleRelease(candidate.playerId) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = { session.toggleRelease(candidate.playerId) })
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(scouted.positionLabel, style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted, modifier = Modifier.width(26.dp))
                        Text(
                            player.registeredName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.base.brand,
                            modifier = Modifier.clickable { onPlayer(player.id) },
                        )
                        Spacer(Modifier.width(tokens.spacing.s))
                        if (candidate.recommended) Pill("추천", AppColors.warn)
                    }
                    Text(
                        "${scouted.age}세 · 종합 ${scouted.overall} · 잠재 ${scouted.potentialLabel} · ${player.contract.salary.fixed(1)}억",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.base.textSecondary,
                    )
                }
            }
        }
        if (ordered.size > shown.size || all) {
            TextButton(onClick = { all = !all }) { Text(if (all) "접기" else "전체 ${ordered.size}명 보기") }
        }
    }
}

// ---------- 스태프 ----------

@Composable
private fun StaffSection(session: GameSession) {
    val tokens = AppTheme.tokens
    var firing by remember { mutableStateOf<Triple<StaffId, String, StaffContract>?>(null) }
    val rows = buildList {
        session.manager()?.let { add(Triple(it.id, "감독 ${it.name}", it.contract)) }
        session.coaches().forEach { add(Triple(it.id, "코치 ${it.name} (${it.grade}등급)", it.contract)) }
        session.medicalStaff().forEach { add(Triple(it.id, "메디컬 ${it.name} (${it.grade}등급)", it.contract)) }
    }
    SectionCard("스태프") {
        Text(
            "경질하면 남은 계약의 일부를 위약금으로 내요. 빈자리는 스토브리그가 끝난 뒤 구단 운영 화면에서 새로 데려와요.",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.base.textMuted,
        )
        rows.forEach { (id, label, contract) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "연봉 ${contract.salary.fixed(1)}억 · 계약 ${contract.yearsRemaining}년 남음",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.base.textMuted,
                    )
                }
                TextButton(onClick = { firing = Triple(id, label, contract) }) { Text("경질") }
            }
        }
    }
    firing?.let { (id, label, contract) ->
        val cost = session.firingCost(contract)
        AlertDialog(
            onDismissRequest = { firing = null },
            title = { Text("$label 경질") },
            text = { Text("위약금 ${cost.fixed(1)}억을 운용 자금에서 내요. 되돌릴 수 없어요.") },
            confirmButton = { TextButton(onClick = { session.fireStaff(id); firing = null }) { Text("경질할게요") } },
            dismissButton = { TextButton(onClick = { firing = null }) { Text("취소") } },
        )
    }
}

// ---------- 공통 ----------

/** 선수 이름(누르면 상세) + 포지션·나이 + 오른쪽 꼬리표 */
/** 선수 한 줄 머리: 다른 화면과 같은 정체 표시(누르면 상세) + 오른쪽 조작부 (2026-10-02 전 화면 통일) */
@Composable
private fun PlayerHeader(session: GameSession, id: PlayerId, onPlayer: (PlayerId) -> Unit, trailing: @Composable () -> Unit) {
    val player = session.player(id)
    val scouted = session.scout(player)
    PlayerLine(
        session.tagOf(player, caption = "${scouted.age}세 · 종합 ${scouted.overall}"),
        onClick = null,
        modifier = Modifier.padding(top = AppTheme.tokens.spacing.s).clickable { onPlayer(id) },
        trailing = trailing,
    )
}

private const val SALARY_PREVIEW = 8
private const val RELEASE_PREVIEW = 12
