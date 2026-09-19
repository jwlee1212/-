package baseballgm.app.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import baseballgm.season.WatchedGame
import baseballgm.text.CommentaryRenderer

/**
 * 문자 중계 (docs/07).
 *
 * 경기를 다시 돌리지 않는다. 주간 진행 때 만들어 둔 **이벤트 스트림을 문장으로 바꿔** 보여줄 뿐이다
 * (불변 원칙 5). 그래서 관전해도 결과가 달라지지 않는다.
 */
@Composable
fun WatchScreen(session: GameSession) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val games = session.lastReport?.watched.orEmpty()
    var selected by remember(games) { mutableStateOf(0) }
    val renderer = remember(session) { CommentaryRenderer { session.player(it).registeredName } }

    if (games.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("관전할 경기가 없습니다", style = MaterialTheme.typography.titleMedium)
            Text(
                "홈 화면에서 한 주를 진행하면 우리 팀 경기를 문자 중계로 볼 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        return
    }

    val game = games[selected.coerceIn(0, games.lastIndex)]
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text("${session.calendarLabel(session.week - 1)} 경기", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                games.forEachIndexed { index, watched ->
                    FilterChip(
                        selected = index == selected,
                        onClick = { selected = index },
                        label = { Text("${index + 1}차전") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            ScoreCard(session, game)
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
        ) {
            items(renderer.render(game.events)) { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = lineColor(line),
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ScoreCard(session: GameSession, game: WatchedGame) {
    val box = game.box
    val userIsHome = box.home.teamId == session.userTeamId
    val won = box.winner == session.userTeamId
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(session.league.team(box.away.teamId).name, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    "${box.awayScore} : ${box.homeScore}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        box.tie -> AppColors.muted
                        won -> AppColors.good
                        else -> AppColors.bad
                    },
                )
                Spacer(Modifier.weight(1f))
                Text(session.league.team(box.home.teamId).name, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "${box.innings}회" + (if (box.walkOff) " 끝내기" else "") + (if (box.tie) " 무승부" else "") +
                    " · ${if (userIsHome) "홈" else "원정"}",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.muted,
            )
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    "안타 ${box.away.hits}-${box.home.hits}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.muted,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "실책 ${box.away.errors}-${box.home.errors}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.muted,
                )
                box.winningPitcher?.let {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "승 ${session.player(it).registeredName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.muted,
                    )
                }
            }
        }
    }
}

private fun lineColor(line: String) = when {
    line.contains("홈런") -> AppColors.good
    line.contains("경기 종료") || line.contains("경기 시작") -> AppColors.warn
    line.contains("회 초 시작") || line.contains("회 말 시작") -> AppColors.muted
    else -> androidx.compose.ui.graphics.Color.Unspecified
}
