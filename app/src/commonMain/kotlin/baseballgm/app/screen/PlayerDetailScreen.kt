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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import baseballgm.app.GameSession
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.Pill
import baseballgm.app.ui.RatingBar
import baseballgm.app.ui.SectionCard
import baseballgm.app.ui.StatRow
import baseballgm.model.Attribute
import baseballgm.model.Batter
import baseballgm.model.Hand
import baseballgm.model.MilitaryStatus
import baseballgm.model.Origin
import baseballgm.model.Pitcher
import baseballgm.model.PlayerId
import baseballgm.scouting.ScoutingAccuracy

/**
 * 선수 상세 (docs/15 M10 표).
 *
 * **능력치는 `ScoutingView` 를 거쳐서만 들어온다.** 우리 팀 선수는 정확한 값이, 타 팀 선수는
 * 범위가 나온다. 잠재력은 우리 팀 선수라도 등급으로만 보인다 (docs/02).
 */
@Composable
fun PlayerDetailScreen(session: GameSession, playerId: PlayerId, onBack: () -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val player = session.player(playerId)
    val scouted = session.scout(player)
    val own = session.isOwn(player)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 목록") }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(player.registeredName, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            Text(
                "${scouted.positionLabel} · ${scouted.age}세",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (!own) Pill("정보 ${scouted.accuracy.label}", AppColors.warn)
        }

        SectionCard("기본") {
            StatRow("소속", player.teamId?.let { session.league.team(it).name } ?: "무소속")
            StatRow("투타", "${handLabel(player.throwsWith)}투 ${handLabel(player.bats)}타")
            StatRow(
                "출신",
                when (player.origin) {
                    Origin.HIGH_SCHOOL -> "고졸 (${player.debutSeason} 데뷔)"
                    Origin.COLLEGE -> "대졸 (${player.debutSeason} 데뷔)"
                    Origin.FOREIGN -> "외국인 (${player.name})"
                },
            )
            StatRow("군 복무", militaryLabel(player.military))
            if (own) {
                StatRow("폼", session.formLabel(player))
                StatRow("피로도", "${player.condition.fatigue}")
            }
            player.condition.injury?.let {
                StatRow("부상", "${it.part} (${it.weeksRemaining}주)", AppColors.bad)
            }
        }

        SectionCard(
            "능력치",
            trailing = {
                Text(
                    if (own) "정확" else "추정 범위",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.muted,
                )
            },
        ) {
            val order = if (player is Pitcher) Attribute.pitcherAttributes else Attribute.batterAttributes
            order.forEach { attribute ->
                val range = scouted.ratings.getValue(attribute)
                RatingBar(attribute.label, range.low, range.high)
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Text("종합", style = MaterialTheme.typography.bodyMedium, color = AppColors.muted)
                Spacer(Modifier.weight(1f))
                Text(
                    scouted.overall.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.forRating(((scouted.overall.low + scouted.overall.high) / 2).toDouble()),
                )
            }
            if (player is Pitcher) StatRow("최고 구속", "${player.topSpeedKmh}km/h")
        }

        SectionCard("스카우트 리포트") {
            StatRow("잠재력", scouted.potentialLabel)
            StatRow(
                "성장 타입",
                when (scouted.growthTypeGuess) {
                    baseballgm.model.GrowthType.EARLY -> "조기 완성형"
                    baseballgm.model.GrowthType.NORMAL -> "일반형"
                    baseballgm.model.GrowthType.LATE -> "대기만성형"
                } + if (scouted.accuracy == ScoutingAccuracy.OWN_TEAM) "" else " (추정)",
            )
            StatRow("내구성", scouted.durabilityComment)
        }

        SectionCard("${session.league.season} 시즌 기록") {
            when (player) {
                is Batter -> {
                    val line = session.batting(player.id)
                    if (line.plateAppearances == 0) {
                        val futures = session.futuresBatting(player.id)
                        if (futures.plateAppearances == 0) {
                            Text("출장 기록이 없습니다.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(
                                "2군 추정 성적",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.muted,
                            )
                            BattingStats(futures)
                        }
                    } else {
                        BattingStats(line)
                    }
                }

                is Pitcher -> {
                    val line = session.pitching(player.id)
                    if (line.outs == 0) {
                        val futures = session.futuresPitching(player.id)
                        if (futures.outs == 0) {
                            Text("출장 기록이 없습니다.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(
                                "2군 추정 성적",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.muted,
                            )
                            PitchingStats(futures)
                        }
                    } else {
                        PitchingStats(line)
                    }
                }
            }
        }

        SectionCard("계약") {
            StatRow("연봉", "${"%.2f".format(player.contract.salary)}억")
            StatRow("잔여 계약", "${player.contract.yearsRemaining}년")
            StatRow(
                "FA까지",
                if (player.contract.seasonsToFreeAgency == 0) "이번 시즌 후 취득" else "${player.contract.seasonsToFreeAgency}시즌",
            )
            StatRow("누적 시즌", "${player.contract.serviceSeasons}시즌")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BattingStats(line: baseballgm.stats.BattingLine) {
    StatRow("타율 / 출루 / 장타", "${"%.3f".format(line.battingAverage)} / ${"%.3f".format(line.onBasePercentage)} / ${"%.3f".format(line.sluggingPercentage)}")
    StatRow("타석 / 타수 / 안타", "${line.plateAppearances} / ${line.atBats} / ${line.hits}")
    StatRow("홈런 / 타점 / 득점", "${line.homeRuns} / ${line.rbi} / ${line.runs}")
    StatRow("볼넷 / 삼진", "${line.walks} / ${line.strikeouts}")
    StatRow("2루타 / 3루타 / 도루", "${line.doubles} / ${line.triples} / ${line.stolenBases}")
    StatRow("OPS", "%.3f".format(line.ops))
}

@Composable
private fun PitchingStats(line: baseballgm.stats.PitchingLine) {
    StatRow("평균자책 / WHIP", "${"%.2f".format(line.era)} / ${"%.2f".format(line.whip)}")
    StatRow("이닝 / 상대 타자", "${line.inningsText()} / ${line.battersFaced}")
    StatRow("승 / 패 / 세이브 / 홀드", "${line.wins} / ${line.losses} / ${line.saves} / ${line.holds}")
    StatRow("탈삼진 / 볼넷", "${line.strikeouts} / ${line.walks}")
    StatRow("피안타 / 피홈런", "${line.hits} / ${line.homeRuns}")
    StatRow("투구수", "${line.pitches}")
}

private fun handLabel(hand: Hand) = when (hand) {
    Hand.LEFT -> "좌"
    Hand.RIGHT -> "우"
    Hand.SWITCH -> "양"
}

private fun militaryLabel(status: MilitaryStatus) = when (status) {
    is MilitaryStatus.Completed -> "군필"
    is MilitaryStatus.Unfulfilled -> "미필 (${status.deadlineAge}세까지)"
    is MilitaryStatus.Serving -> "복무 중 (${status.returnSeason} 복귀)"
    is MilitaryStatus.Exempt -> "면제"
    is MilitaryStatus.NotRequired -> "해당 없음"
}
