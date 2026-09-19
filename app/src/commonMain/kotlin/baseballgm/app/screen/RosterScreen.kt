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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import baseballgm.app.GameSession
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.MeterBar
import baseballgm.app.ui.Pill
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel

/**
 * 로스터 화면 (docs/15 M10 표).
 * 1·2군을 나눠 보고, 폼과 피로를 한눈에 확인한다.
 */
@Composable
fun RosterScreen(session: GameSession, onPlayer: (PlayerId) -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    var tab by remember { mutableStateOf(0) }
    val level = if (tab == 0) RosterLevel.FIRST_TEAM else RosterLevel.FUTURES
    val players = session.roster(level)

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("1군 ${session.roster(RosterLevel.FIRST_TEAM).size}") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("2군 ${session.roster(RosterLevel.FUTURES).size}") })
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            items(players) { player ->
                RosterRow(session, player, level, onPlayer)
            }
        }
    }
}

@Composable
private fun RosterRow(
    session: GameSession,
    player: Player,
    level: RosterLevel,
    onPlayer: (PlayerId) -> Unit,
) {
    val overall = session.overall(player)
    val injury = player.condition.injury

    Card(
        Modifier.fillMaxWidth().clickable { onPlayer(player.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    session.positionLabel(player),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.muted,
                    modifier = Modifier.width(30.dp),
                )
                Text(player.registeredName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${player.ageIn(session.league.season)}세",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.muted,
                )
                Spacer(Modifier.weight(1f))
                if (injury != null) {
                    Pill("${injury.part} ${injury.weeksRemaining}주", AppColors.bad)
                    Spacer(Modifier.width(6.dp))
                } else if (!player.military.isAvailable) {
                    Pill("군 복무", AppColors.muted)
                    Spacer(Modifier.width(6.dp))
                } else {
                    Pill(session.formLabel(player), formColor(player.condition.form))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    "%.0f".format(overall),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.forRating(overall),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("피로", style = MaterialTheme.typography.labelSmall, color = AppColors.muted)
                Spacer(Modifier.width(6.dp))
                MeterBar(
                    player.condition.fatigue,
                    when {
                        player.condition.fatigue >= 70 -> AppColors.bad
                        player.condition.fatigue >= 45 -> AppColors.warn
                        else -> AppColors.good
                    },
                    Modifier.width(60.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    statLine(session, player, level),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formColor(form: Int) = when {
    form >= 60 -> AppColors.good
    form >= 45 -> AppColors.muted
    else -> AppColors.bad
}

/** 1군은 시즌 기록, 2군은 추정 성적을 보여준다 (docs/07). */
private fun statLine(session: GameSession, player: Player, level: RosterLevel): String = when (player) {
    is Batter -> {
        val line = if (level == RosterLevel.FIRST_TEAM) session.batting(player.id) else session.futuresBatting(player.id)
        if (line.plateAppearances == 0) {
            "기록 없음"
        } else {
            "${line.plateAppearances}타석 타율 ${"%.3f".format(line.battingAverage)} ${line.homeRuns}홈런 ${line.rbi}타점"
        }
    }

    is Pitcher -> {
        val line = if (level == RosterLevel.FIRST_TEAM) session.pitching(player.id) else session.futuresPitching(player.id)
        if (line.outs == 0) {
            "기록 없음"
        } else {
            "${line.inningsText()}이닝 ERA ${"%.2f".format(line.era)} ${line.wins}승 ${line.losses}패 ${line.strikeouts}K"
        }
    }
}
