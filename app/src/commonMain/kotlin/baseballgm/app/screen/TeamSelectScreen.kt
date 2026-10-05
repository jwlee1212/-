package baseballgm.app.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import baseballgm.app.TeamChoice
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.MeterBar
import baseballgm.app.ui.Pill
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.SecretaryCard
import baseballgm.app.ui.TeamColors
import baseballgm.app.ui.TeamEmblem
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * 새 게임 ① 구단 선택 = 난이도 선택 (docs/01).
 *
 * 카드를 누르면 바로 시작하지 않고 확인 창을 띄운다 — 부임 구단은 되돌릴 수 없는 선택이기 때문이다.
 */
@Composable
fun TeamSelectScreen(teams: List<TeamChoice>, onConfirm: (TeamChoice) -> Unit) {
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    val pending = teams.firstOrNull { it.id.value == pendingId }

    val spacing = AppTheme.tokens.spacing
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(spacing.s),
            contentPadding = PaddingValues(spacing.l),
        ) {
            item {
                Text("어느 구단을 맡으실래요?", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(spacing.m))
                SecretaryCard(
                    "구단 고르는 게 곧 난이도 고르는 거예요. 처음이시면 튜토리얼 추천 구단이 무난하고, " +
                        "전력·예산·지명 순번을 같이 보시면 판단이 쉬워요.",
                )
                Spacer(Modifier.height(spacing.xs))
            }
            items(teams, key = { it.id.value }) { team ->
                TeamCard(team) { pendingId = team.id.value }
            }
        }
    }

    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pendingId = null },
            title = { Text("${pending.name}, 이 팀으로 할까요?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppTheme.tokens.spacing.s)) {
                    Text("한번 부임하면 되돌릴 수 없어요. 해임되거나 이직 제안을 받기 전까지는 이 구단을 맡으셔야 해요.")
                    Text(
                        "구단주 목표: ${pending.ownerGoal}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (pending.hardest) {
                        Text(
                            "가장 어려운 구단이에요. 각오는 하셔야 해요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.bad,
                        )
                    }
                }
            },
            confirmButton = {
                // 구단 색으로 칠한 큰 면은 진행 버튼 하나뿐 — 확인 창 버튼은 글자 버튼
                TextButton(
                    onClick = {
                        pendingId = null
                        onConfirm(pending)
                    },
                ) { Text("부임할게요", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pendingId = null }) { Text("다시 고르기") } },
        )
    }
}

/**
 * 구단 카드 한 장. 2026-10-01 절제 작업으로 칩은 특별한 경우(튜토리얼 추천·최고 난이도) 하나만,
 * 부문 막대 셋은 숫자 한 줄로 줄였다. 팀 전력은 능력치가 아니라서 등급 색을 칠하지 않는다.
 */
@Composable
private fun TeamCard(team: TeamChoice, onClick: () -> Unit) {
    val tokens = AppTheme.tokens
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(tokens.radii.card),
        colors = CardDefaults.cardColors(containerColor = tokens.base.card),
    ) {
        Column(Modifier.padding(tokens.spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamEmblem(team.id, team.nickname.take(1))
                Spacer(Modifier.width(tokens.spacing.m))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(team.name, style = MaterialTheme.typography.titleMedium)
                        val badge = when {
                            team.recommendedForTutorial -> "튜토리얼 추천" to AppColors.good
                            team.hardest -> "최고 난이도" to AppColors.bad
                            else -> null
                        }
                        badge?.let { (label, color) ->
                            Spacer(Modifier.width(tokens.spacing.s))
                            Pill(label, color)
                        }
                    }
                    Text("“${team.keyword}”", style = MaterialTheme.typography.bodySmall, color = tokens.base.textSecondary)
                }
                Text("전력 ${team.overall}", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(tokens.spacing.s))
            Text(
                "${tierLabel(team.tier)} · 타선 ${team.lineup} · 선발 ${team.rotation} · 불펜 ${team.bullpen}",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.base.textMuted,
            )
            Text(
                "연봉 ${team.payroll}억 · 운용 자금 ${team.operatingFunds}억 · 드래프트 ${team.draftPick}순위",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.base.textMuted,
            )
        }
    }
}

private fun tierLabel(tier: String): String = when (tier) {
    "strong" -> "강팀"
    "weak" -> "약팀"
    else -> "중위권"
}
