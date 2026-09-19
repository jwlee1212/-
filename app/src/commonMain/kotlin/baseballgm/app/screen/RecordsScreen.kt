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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import baseballgm.app.GameSession
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.SectionCard
import baseballgm.model.PlayerId

/** 기록실 (docs/15 M10 표): 순위표, 개인 기록 순위. */
@Composable
fun RecordsScreen(session: GameSession, onPlayer: (PlayerId) -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    var tab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("순위표") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("타자") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("투수") })
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (tab) {
                0 -> StandingsTable(session)
                1 -> BattingLeaders(session, onPlayer)
                else -> PitchingLeaders(session, onPlayer)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StandingsTable(session: GameSession) {
    SectionCard("정규시즌 순위") {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            HeaderCell("", 0.08f)
            HeaderCell("구단", 0.34f)
            HeaderCell("승", 0.1f)
            HeaderCell("패", 0.1f)
            HeaderCell("무", 0.1f)
            HeaderCell("승률", 0.16f)
            HeaderCell("차", 0.12f)
        }
        session.teamsRanked().forEachIndexed { index, record ->
            val isUser = record.teamId == session.userTeamId
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Cell("${index + 1}", 0.08f, if (index < 5) AppColors.good else AppColors.muted)
                Cell(
                    session.league.team(record.teamId).name,
                    0.34f,
                    if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    bold = isUser,
                )
                Cell("${record.wins}", 0.1f)
                Cell("${record.losses}", 0.1f)
                Cell("${record.ties}", 0.1f)
                Cell("%.3f".format(record.winPct), 0.16f)
                Cell("%.1f".format(session.gamesBehind(record.teamId)), 0.12f)
            }
        }
    }
    SectionCard("득실") {
        session.teamsRanked().forEach { record ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Cell(session.league.team(record.teamId).name, 0.4f)
                Cell("득 ${record.runsScored}", 0.25f)
                Cell("실 ${record.runsAllowed}", 0.2f)
                Cell(
                    "${if (record.runDifferential >= 0) "+" else ""}${record.runDifferential}",
                    0.15f,
                    if (record.runDifferential >= 0) AppColors.good else AppColors.bad,
                )
            }
        }
    }
}

@Composable
private fun BattingLeaders(session: GameSession, onPlayer: (PlayerId) -> Unit) {
    SectionCard("타율") {
        session.battingLeaders().forEachIndexed { index, (player, line) ->
            LeaderRow(
                index,
                player.registeredName,
                session.league.team(player.teamId!!).name,
                "%.3f".format(line.battingAverage),
                "${line.homeRuns}홈런 ${line.rbi}타점 OPS ${"%.3f".format(line.ops)}",
            ) { onPlayer(player.id) }
        }
    }
    SectionCard("홈런") {
        session.homeRunLeaders().forEachIndexed { index, (player, line) ->
            LeaderRow(
                index,
                player.registeredName,
                session.league.team(player.teamId!!).name,
                "${line.homeRuns}",
                "타율 ${"%.3f".format(line.battingAverage)} ${line.rbi}타점",
            ) { onPlayer(player.id) }
        }
    }
}

@Composable
private fun PitchingLeaders(session: GameSession, onPlayer: (PlayerId) -> Unit) {
    SectionCard("평균자책") {
        session.eraLeaders().forEachIndexed { index, (player, line) ->
            LeaderRow(
                index,
                player.registeredName,
                session.league.team(player.teamId!!).name,
                "%.2f".format(line.era),
                "${line.wins}승 ${line.losses}패 ${line.strikeouts}K ${line.inningsText()}이닝",
            ) { onPlayer(player.id) }
        }
    }
    SectionCard("다승") {
        session.winLeaders().forEachIndexed { index, (player, line) ->
            LeaderRow(
                index,
                player.registeredName,
                session.league.team(player.teamId!!).name,
                "${line.wins}승",
                "ERA ${"%.2f".format(line.era)} ${line.inningsText()}이닝",
            ) { onPlayer(player.id) }
        }
    }
}

@Composable
private fun LeaderRow(
    index: Int,
    name: String,
    team: String,
    value: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 3.dp)) {
        Text(
            "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.muted,
            modifier = Modifier.fillMaxWidth(0.07f),
        )
        Column(Modifier.fillMaxWidth(0.55f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("$team · $detail", style = MaterialTheme.typography.labelSmall, color = AppColors.muted)
        }
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun HeaderCell(text: String, width: Float) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = AppColors.muted,
        modifier = Modifier.fillMaxWidth(width),
    )
}

@Composable
private fun Cell(
    text: String,
    width: Float,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false,
) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.fillMaxWidth(width),
    )
}
