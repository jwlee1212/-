package baseballgm.app.screen

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import baseballgm.app.GameSession
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.SecretaryCard
import baseballgm.app.ui.SectionCard
import baseballgm.util.ieyo

/**
 * 포스트시즌 다시 보기 (2026-10-01, 진단 3번).
 *
 * 시리즈마다 결과 한 줄 + 경기별 점수. 경기를 누르면 문자 중계로 간다 — 포스트시즌을 치를 때 남긴
 * 이벤트 스트림을 문장으로 바꿀 뿐 경기를 다시 돌리지 않는다 (불변 원칙 5).
 * 경기 기록은 세이브에 넣지 않아서, 앱을 다시 켜면 결과(대진)만 남는다.
 */
@Composable
fun PostseasonScreen(session: GameSession, onWatch: (index: Int) -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val tokens = AppTheme.tokens
    val result = session.postseason()
    val games = session.postseasonGames

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.m),
    ) {
        val progress = session.postseasonProgress
        if (result == null && progress == null) {
            SecretaryCard("아직 포스트시즌을 치르지 않았어요.")
            return@Column
        }
        if (result != null) {
            val champion = session.league.team(result.champion)
            SecretaryCard(
                if (result.champion == session.userTeamId) {
                    "${result.season} 우승은 우리 ${champion.name.ieyo()}. 몇 번을 다시 봐도 좋은 경기들이에요."
                } else {
                    "${result.season} 우승은 ${champion.name}. 경기를 누르면 문자 중계로 다시 볼 수 있어요."
                } + if (games.isEmpty()) " (경기 기록은 저장되지 않아서, 앱을 다시 켜면 결과만 남아요.)" else "",
                title = "${result.season} 포스트시즌",
            )
        } else {
            SecretaryCard("포스트시즌이 진행 중이에요. 치른 경기는 눌러서 문자 중계로 볼 수 있어요.", title = "${progress!!.season} 포스트시즌")
        }
        // 끝난 시리즈 + (진행 중이면) 지금 시리즈
        val rows: List<Pair<baseballgm.season.PostseasonRound, String>> =
            (result?.series ?: progress!!.completed).map { series ->
                series.round to series.line { id -> session.league.team(id).name }
            } + listOfNotNull(
                progress?.current?.takeIf { result == null }?.let { current ->
                    val name = { id: baseballgm.model.TeamId -> session.league.team(id).name }
                    current.round to "${current.round.label}: ${name(current.higherSeed)} ${current.higherWins} - ${current.lowerWins} ${name(current.lowerSeed)} (진행 중)"
                },
            )
        val userRounds = (result?.series ?: progress!!.completed).filter { it.higherSeed == session.userTeamId || it.lowerSeed == session.userTeamId }
            .map { it.round }.toSet() + listOfNotNull(progress?.current?.takeIf { it.involves(session.userTeamId) }?.round)
        rows.forEach { (round, line) ->
            val ours = round in userRounds
            SectionCard(round.label) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (ours) FontWeight.Bold else FontWeight.Normal,
                )
                games.withIndex().filter { it.value.round == round }.forEach { (index, game) ->
                    val box = game.box
                    val home = session.league.team(box.home.teamId).nickname
                    val away = session.league.team(box.away.teamId).nickname
                    val watchable = game.events.isNotEmpty()
                    Row(
                        Modifier.fillMaxWidth()
                            .then(if (watchable) Modifier.clickable { onWatch(index) } else Modifier)
                            .padding(vertical = tokens.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${game.gameNumber}차전", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted, modifier = Modifier.width(48.dp))
                        Text(
                            "$away ${box.awayScore} : ${box.homeScore} $home" +
                                (if (box.innings > 9) " (${box.innings}회)" else "") + if (box.walkOff) " 끝내기" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (watchable) {
                            Text("중계", style = MaterialTheme.typography.bodySmall, color = tokens.base.brand)
                            androidx.compose.material3.Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = tokens.base.brand,
                            )
                        }
                    }
                }
            }
        }
    }
}
