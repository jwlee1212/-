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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextAlign
import baseballgm.app.ui.SectionDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import baseballgm.app.GameSession
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.PlayerLine
import baseballgm.app.ui.MeterBar
import baseballgm.app.ui.SectionCard
import baseballgm.app.ui.StatRow
import baseballgm.model.ManagerTendencies
import baseballgm.tactics.BullpenRole
import baseballgm.tactics.WeeklyPolicy

/**
 * 사전 지시 화면 (docs/06, docs/15 M10 표).
 *
 * 단장 전용 모드이므로 유저는 **방침만 전달**한다. 감독은 지시를 그대로 따르지 않고
 * 자기 성향에서 반영률만큼만 움직인다 — 매주 조금씩 끌려온다.
 *
 * 2026-10-01 절제 작업: 섹션이 아홉이라 세그먼트 탭 둘로 나눴다.
 * - **방침**: 주간 방침(주인공) → 단장 방침(슬라이더마다 감독의 지금 성향을 같이) → 감독
 * - **규칙표**: 감독이 실제로 쓰는 규칙(읽기 전용) — 교체·작전 / 투수진 / 라인업
 */
@Composable
fun DirectiveScreen(session: GameSession) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    var tab by rememberSaveable { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("방침") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("규칙표") })
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = AppTheme.tokens.spacing.l, vertical = AppTheme.tokens.spacing.m),
            verticalArrangement = Arrangement.spacedBy(AppTheme.tokens.spacing.m),
        ) {
            if (tab == 0) PolicyTab(session) else SheetTab(session)
            Spacer(Modifier.height(AppTheme.tokens.spacing.s))
        }
    }
}

@Composable
private fun PolicyTab(session: GameSession) {
    val tokens = AppTheme.tokens
    val manager = session.manager()
    val tendencies = session.tendencies()
    var direction by remember { mutableStateOf(session.direction() ?: tendencies) }

    SectionCard("주간 방침") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            WeeklyPolicy.entries.forEachIndexed { index, policy ->
                SegmentedButton(
                    selected = session.policy() == policy,
                    onClick = { session.setPolicy(policy) },
                    shape = SegmentedButtonDefaults.itemShape(index, WeeklyPolicy.entries.size),
                ) { Text(policy.label) }
            }
        }
        Text(
            when (session.policy()) {
                WeeklyPolicy.NORMAL -> "규칙표 그대로 운영합니다."
                WeeklyPolicy.ALL_OUT -> "투구수·연투 제한을 풀고 필승조를 넓게 씁니다. 피로와 부상 위험이 올라갑니다."
                WeeklyPolicy.PROTECT -> "제한을 조이고 주전을 더 쉬게 합니다."
            },
            style = MaterialTheme.typography.bodySmall,
            color = tokens.base.textMuted,
            modifier = Modifier.padding(top = tokens.spacing.s),
        )
    }

    SectionCard(
        "단장 방침",
        trailing = { if (session.direction() != null) TextButton(onClick = { session.setDirection(null) }) { Text("해제") } },
    ) {
        Text(
            if (session.direction() != null) {
                "방침 전달 중이에요. 감독 성향이 매주 조금씩 이쪽으로 움직여요."
            } else {
                "감독에게 요청할 방향이에요. 한 번에 바뀌지 않고 매주 조금씩 움직여요."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (session.direction() != null) AppColors.good else tokens.base.textMuted,
        )
        Spacer(Modifier.height(tokens.spacing.s))
        DirectionSlider("선발 인내심", direction.starterPatience, tendencies.starterPatience) {
            direction = direction.copy(starterPatience = it)
            session.setDirection(direction)
        }
        DirectionSlider("불펜 혹사", direction.bullpenAggression, tendencies.bullpenAggression) {
            direction = direction.copy(bullpenAggression = it)
            session.setDirection(direction)
        }
        DirectionSlider("번트 선호", direction.buntPreference, tendencies.buntPreference) {
            direction = direction.copy(buntPreference = it)
            session.setDirection(direction)
        }
        DirectionSlider("도루 적극성", direction.stealAggression, tendencies.stealAggression) {
            direction = direction.copy(stealAggression = it)
            session.setDirection(direction)
        }
        DirectionSlider("유망주 기용", direction.prospectUsage, tendencies.prospectUsage) {
            direction = direction.copy(prospectUsage = it)
            session.setDirection(direction)
        }
    }

    SectionCard("감독") {
        if (manager == null) {
            Text("감독 공석 (감독 겸직)", style = MaterialTheme.typography.bodyMedium)
        } else {
            StatRow("이름", "${manager.name} (${manager.ageIn(session.league.season)}세)")
            StatRow("경력", manager.playingBackground)
            StatRow(
                "전문 분야",
                when (manager.specialty) {
                    baseballgm.model.ManagerSpecialty.PITCHING -> "투수 육성"
                    baseballgm.model.ManagerSpecialty.BATTING -> "타격"
                    baseballgm.model.ManagerSpecialty.FIELDING -> "수비·주루"
                },
            )
            StatRow("평판", "${manager.reputation}")
        }
        // 방침으로 못 바꾸는 성향만 막대로 (나머지 다섯은 단장 방침 슬라이더에 같이 보인다)
        SectionDivider()
        TendencyBar("플래툰 활용", tendencies.platoonUsage)
        TendencyBar("베테랑 신뢰", tendencies.veteranTrust)
    }
}

/** 감독이 실제로 쓰는 규칙표 (읽기 전용). 여섯 카드를 셋으로 묶었다 */
@Composable
private fun SheetTab(session: GameSession) {
    val sheet = session.sheet()
    SectionCard("교체·작전") {
        StatRow("선발 투구수 한계", "${sheet.starterHook.pitchLimit}구")
        StatRow("선발 실점 한계", "${sheet.starterHook.runsAllowedLimit}점")
        StatRow("선발 피로 한계", "${sheet.starterHook.fatigueLimit}")
        StatRow("3번째 타순에서 교체", if (sheet.starterHook.pullOnThirdTimeThroughOrder) "예" else "아니오")
        SectionDivider()
        StatRow("희생번트", sliderText(sheet.tactics.bunt))
        StatRow("도루", sliderText(sheet.tactics.steal))
        StatRow("고의사구", sliderText(sheet.tactics.intentionalWalk))
    }

    SectionCard("투수진") {
        // 선수가 나오는 줄은 다른 화면과 같은 정체 표시 (2026-10-02 전 화면 통일)
        sheet.rotation.starters.forEachIndexed { index, id ->
            PlayerLine(session.tagOf(session.player(id), caption = "${index + 1}선발"), onClick = null)
        }
        StatRow("선발 최소 휴식", "${sheet.rotation.minimumRestDays}일")
        SectionDivider()
        StatRow("불펜 연투 제한", "${sheet.bullpen.maxConsecutiveDays}일")
        StatRow("불펜 최근 7일 투구수", "${sheet.bullpen.maxPitchesLast7Days}구")
        BullpenRole.entries.forEach { role ->
            val names = sheet.bullpen.candidates(role).map { session.player(it).registeredName }
            if (names.isNotEmpty()) StatRow(role.label, names.joinToString(", "))
        }
    }

    SectionCard("라인업") {
        Text("좌완 선발 상대", style = MaterialTheme.typography.bodySmall, color = AppColors.muted)
        sheet.lineupVsLeft.slots.forEachIndexed { index, slot ->
            PlayerLine(session.tagOf(session.player(slot.playerId), caption = "${index + 1}번 · 수비 ${slot.position.label}"), onClick = null)
        }
        SectionDivider()
        Text("우완 선발 상대", style = MaterialTheme.typography.bodySmall, color = AppColors.muted)
        sheet.lineupVsRight.slots.forEachIndexed { index, slot ->
            PlayerLine(session.tagOf(session.player(slot.playerId), caption = "${index + 1}번 · 수비 ${slot.position.label}"), onClick = null)
        }
    }
}

@Composable
private fun TendencyBar(label: String, value: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = AppTheme.tokens.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp),
        )
        MeterBar(value, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        Spacer(Modifier.width(AppTheme.tokens.spacing.s))
        Text("$value", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, modifier = Modifier.width(26.dp))
    }
}

/** 단장 방침 한 줄: 라벨 · 요청값, 아래 회색으로 감독의 지금 성향 */
@Composable
private fun DirectionSlider(label: String, value: Int, current: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text("감독 지금 $current · 요청 $value", style = MaterialTheme.typography.bodySmall, color = AppColors.muted)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(1, 100)) },
            valueRange = 1f..100f,
        )
    }
}

private fun sliderText(value: Int): String = "$value / 5 " + when (value) {
    1 -> "(거의 안 함)"
    2 -> "(소극적)"
    3 -> "(보통)"
    4 -> "(적극적)"
    else -> "(매우 적극적)"
}
