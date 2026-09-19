package baseballgm.app.screen

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
import androidx.compose.material3.FilterChip
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
 */
@Composable
fun DirectiveScreen(session: GameSession) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val manager = session.manager()
    val tendencies = session.tendencies()
    val sheet = session.sheet()
    var direction by remember { mutableStateOf(session.direction() ?: tendencies) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
            Spacer(Modifier.height(6.dp))
            TendencyBar("선발 인내심", tendencies.starterPatience)
            TendencyBar("불펜 혹사", tendencies.bullpenAggression)
            TendencyBar("번트 선호", tendencies.buntPreference)
            TendencyBar("도루 적극성", tendencies.stealAggression)
            TendencyBar("플래툰 활용", tendencies.platoonUsage)
            TendencyBar("유망주 기용", tendencies.prospectUsage)
            TendencyBar("베테랑 신뢰", tendencies.veteranTrust)
        }

        SectionCard("주간 방침") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeeklyPolicy.entries.forEach { policy ->
                    FilterChip(
                        selected = session.policy() == policy,
                        onClick = { session.setPolicy(policy) },
                        label = { Text(policy.label) },
                    )
                }
            }
            Text(
                when (session.policy()) {
                    WeeklyPolicy.NORMAL -> "규칙표 그대로 운영합니다."
                    WeeklyPolicy.ALL_OUT -> "투구수·연투 제한을 풀고 필승조를 넓게 씁니다. 피로와 부상 위험이 올라갑니다."
                    WeeklyPolicy.PROTECT -> "제한을 조이고 주전을 더 쉬게 합니다."
                },
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        SectionCard(
            "단장 방침",
            trailing = {
                TextButton(onClick = { session.setDirection(null) }) { Text("해제") }
            },
        ) {
            Text(
                "감독에게 요청할 방향입니다. 한 번에 바뀌지 않고 매주 조금씩 성향이 움직입니다.",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.muted,
            )
            Spacer(Modifier.height(6.dp))
            DirectionSlider("선발 인내심", direction.starterPatience) {
                direction = direction.copy(starterPatience = it)
                session.setDirection(direction)
            }
            DirectionSlider("불펜 혹사", direction.bullpenAggression) {
                direction = direction.copy(bullpenAggression = it)
                session.setDirection(direction)
            }
            DirectionSlider("번트 선호", direction.buntPreference) {
                direction = direction.copy(buntPreference = it)
                session.setDirection(direction)
            }
            DirectionSlider("도루 적극성", direction.stealAggression) {
                direction = direction.copy(stealAggression = it)
                session.setDirection(direction)
            }
            DirectionSlider("유망주 기용", direction.prospectUsage) {
                direction = direction.copy(prospectUsage = it)
                session.setDirection(direction)
            }
            if (session.direction() != null) {
                Text(
                    "방침 전달 중",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.good,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        SectionCard("현재 규칙표 — 선발 교체 조건") {
            StatRow("투구수 한계", "${sheet.starterHook.pitchLimit}구")
            StatRow("실점 한계", "${sheet.starterHook.runsAllowedLimit}점")
            StatRow("피로 한계", "${sheet.starterHook.fatigueLimit}")
            StatRow("3번째 타순에서 교체", if (sheet.starterHook.pullOnThirdTimeThroughOrder) "예" else "아니오")
        }

        SectionCard("불펜 역할") {
            StatRow("연투 제한", "${sheet.bullpen.maxConsecutiveDays}일")
            StatRow("최근 7일 투구수", "${sheet.bullpen.maxPitchesLast7Days}구")
            Spacer(Modifier.height(4.dp))
            BullpenRole.entries.forEach { role ->
                val names = sheet.bullpen.candidates(role).map { session.player(it).registeredName }
                if (names.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            role.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.muted,
                            modifier = Modifier.width(96.dp),
                        )
                        Text(names.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        SectionCard("작전 성향") {
            StatRow("희생번트", sliderText(sheet.tactics.bunt))
            StatRow("도루", sliderText(sheet.tactics.steal))
            StatRow("고의사구", sliderText(sheet.tactics.intentionalWalk))
        }

        SectionCard("선발 로테이션") {
            sheet.rotation.starters.forEachIndexed { index, id ->
                StatRow("${index + 1}선발", session.player(id).registeredName)
            }
            StatRow("최소 휴식", "${sheet.rotation.minimumRestDays}일")
        }

        SectionCard("라인업 (좌완 선발 상대)") {
            sheet.lineupVsLeft.slots.forEachIndexed { index, slot ->
                StatRow("${index + 1}번 ${slot.position.label}", session.player(slot.playerId).registeredName)
            }
        }

        SectionCard("라인업 (우완 선발 상대)") {
            sheet.lineupVsRight.slots.forEachIndexed { index, slot ->
                StatRow("${index + 1}번 ${slot.position.label}", session.player(slot.playerId).registeredName)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TendencyBar(label: String, value: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp),
        )
        MeterBar(value, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text("$value", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(26.dp))
    }
}

@Composable
private fun DirectionSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            Text("$value", style = MaterialTheme.typography.labelSmall, color = AppColors.muted)
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
