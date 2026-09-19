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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.MeterBar
import baseballgm.app.ui.Pill
import baseballgm.league.League
import baseballgm.league.StrengthCalculator
import baseballgm.model.TeamId

/**
 * 구단 선택 = 난이도 선택 (docs/01).
 *
 * 전력·예산·지명권을 함께 보여줘서, 유저가 "어떤 어려움을 고를지" 판단할 수 있게 한다.
 */
@Composable
fun TeamSelectScreen(
    league: League,
    strength: StrengthCalculator,
    recommended: TeamId?,
    onSelect: (TeamId) -> Unit,
) {
    val teams = league.teams.sortedByDescending { strength.of(league.playersOf(it.id)).overall }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("야구 단장", style = MaterialTheme.typography.titleLarge)
        Text(
            "${league.season} 시즌 · 맡을 구단을 고르세요",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(teams) { team ->
                val power = strength.of(league.playersOf(team.id))
                val payroll = league.payrollOf(team.id)
                Card(
                    Modifier.fillMaxWidth().clickable { onSelect(team.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(team.name, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.fillMaxWidth(0.02f))
                            if (team.id == recommended) {
                                Spacer(Modifier.height(0.dp))
                                Pill("추천", AppColors.good, Modifier.padding(start = 6.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                "종합 ${"%.0f".format(power.overall)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = AppColors.forRating(power.overall),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            team.keyword,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PowerBar("타선", power.lineup, Modifier.weight(1f))
                            PowerBar("선발", power.rotation, Modifier.weight(1f))
                            PowerBar("불펜", power.bullpen, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "연봉 ${"%.0f".format(payroll)}/${"%.0f".format(league.salaryCap)}억",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (payroll > league.salaryCap) AppColors.bad else AppColors.muted,
                            )
                            Text(
                                "운용 자금 ${"%.0f".format(team.operatingFunds)}억",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.muted,
                            )
                            Text(
                                "드래프트 ${team.draftPick}순위",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.muted,
                            )
                        }
                        Text(
                            "구단주 목표: ${team.ownerGoal}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerBar(label: String, value: Double, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.muted)
            Text(
                "%.0f".format(value),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.forRating(value),
            )
        }
        Spacer(Modifier.height(3.dp))
        MeterBar(value.toInt(), AppColors.forRating(value), Modifier.fillMaxWidth())
    }
}
