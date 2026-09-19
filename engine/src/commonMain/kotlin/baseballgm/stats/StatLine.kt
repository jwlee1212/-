package baseballgm.stats

import baseballgm.model.Hand
import kotlinx.serialization.Serializable

/**
 * 타자 누적 기록 (docs/04 1단계).
 *
 * **비율 지표는 저장하지 않는다.** 카운트만 쌓아 두고 타율·출루율 같은 값은 볼 때 계산한다.
 * 그래야 기록을 합치거나 쪼갤 때(좌우 분리, 월별) 숫자가 어긋나지 않는다.
 */
@Serializable
data class BattingLine(
    val plateAppearances: Int = 0,
    val atBats: Int = 0,
    val hits: Int = 0,
    val doubles: Int = 0,
    val triples: Int = 0,
    val homeRuns: Int = 0,
    val runs: Int = 0,
    val rbi: Int = 0,
    val walks: Int = 0,
    /** 볼넷 중 고의사구 (walks 에 포함된 값이다) */
    val intentionalWalks: Int = 0,
    val hitByPitch: Int = 0,
    val strikeouts: Int = 0,
    val sacFlies: Int = 0,
    val sacBunts: Int = 0,
    val catcherInterference: Int = 0,
    val reachedOnError: Int = 0,
    val fieldersChoice: Int = 0,
    /** 낫아웃 출루 */
    val strikeoutReached: Int = 0,
    val stolenBases: Int = 0,
    val caughtStealing: Int = 0,
    val groundOuts: Int = 0,
    val flyOuts: Int = 0,
    val lineOuts: Int = 0,
    val doublePlays: Int = 0,
) {
    val singles: Int get() = hits - doubles - triples - homeRuns

    /** 출루 횟수. 주자 보존 등식에 쓰는 값이다 (docs/05) */
    val timesReachedBase: Int
        get() = hits + walks + hitByPitch + catcherInterference + reachedOnError + fieldersChoice + strikeoutReached

    val totalBases: Int get() = singles + doubles * 2 + triples * 3 + homeRuns * 4

    val battingAverage: Double get() = ratio(hits, atBats)

    val onBasePercentage: Double
        get() = ratio(hits + walks + hitByPitch, atBats + walks + hitByPitch + sacFlies)

    val sluggingPercentage: Double get() = ratio(totalBases, atBats)

    val ops: Double get() = onBasePercentage + sluggingPercentage

    operator fun plus(other: BattingLine): BattingLine = BattingLine(
        plateAppearances = plateAppearances + other.plateAppearances,
        atBats = atBats + other.atBats,
        hits = hits + other.hits,
        doubles = doubles + other.doubles,
        triples = triples + other.triples,
        homeRuns = homeRuns + other.homeRuns,
        runs = runs + other.runs,
        rbi = rbi + other.rbi,
        walks = walks + other.walks,
        intentionalWalks = intentionalWalks + other.intentionalWalks,
        hitByPitch = hitByPitch + other.hitByPitch,
        strikeouts = strikeouts + other.strikeouts,
        sacFlies = sacFlies + other.sacFlies,
        sacBunts = sacBunts + other.sacBunts,
        catcherInterference = catcherInterference + other.catcherInterference,
        reachedOnError = reachedOnError + other.reachedOnError,
        fieldersChoice = fieldersChoice + other.fieldersChoice,
        strikeoutReached = strikeoutReached + other.strikeoutReached,
        stolenBases = stolenBases + other.stolenBases,
        caughtStealing = caughtStealing + other.caughtStealing,
        groundOuts = groundOuts + other.groundOuts,
        flyOuts = flyOuts + other.flyOuts,
        lineOuts = lineOuts + other.lineOuts,
        doublePlays = doublePlays + other.doublePlays,
    )

    companion object {
        val EMPTY: BattingLine = BattingLine()
    }
}

/** 투수 누적 기록 (docs/04 1단계). 이닝은 아웃 카운트로 저장한다 (⅓ 이닝 반올림 오류 방지). */
@Serializable
data class PitchingLine(
    val outs: Int = 0,
    val battersFaced: Int = 0,
    val hits: Int = 0,
    val homeRuns: Int = 0,
    val walks: Int = 0,
    val hitByPitch: Int = 0,
    val strikeouts: Int = 0,
    val runs: Int = 0,
    val earnedRuns: Int = 0,
    val pitches: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val saves: Int = 0,
    val holds: Int = 0,
    val gamesStarted: Int = 0,
    val games: Int = 0,
) {
    val inningsPitched: Double get() = outs / 3.0

    val era: Double get() = if (outs == 0) 0.0 else earnedRuns * 27.0 / outs

    val whip: Double get() = if (outs == 0) 0.0 else (hits + walks) * 3.0 / outs

    val opponentAverage: Double get() = ratio(hits, battersFaced - walks - hitByPitch)

    /** "6⅓" 처럼 보여줄 때 쓰는 표기. */
    fun inningsText(): String {
        val full = outs / 3
        return when (outs % 3) {
            1 -> "$full⅓"
            2 -> "$full⅔"
            else -> "$full"
        }
    }

    operator fun plus(other: PitchingLine): PitchingLine = PitchingLine(
        outs = outs + other.outs,
        battersFaced = battersFaced + other.battersFaced,
        hits = hits + other.hits,
        homeRuns = homeRuns + other.homeRuns,
        walks = walks + other.walks,
        hitByPitch = hitByPitch + other.hitByPitch,
        strikeouts = strikeouts + other.strikeouts,
        runs = runs + other.runs,
        earnedRuns = earnedRuns + other.earnedRuns,
        pitches = pitches + other.pitches,
        wins = wins + other.wins,
        losses = losses + other.losses,
        saves = saves + other.saves,
        holds = holds + other.holds,
        gamesStarted = gamesStarted + other.gamesStarted,
        games = games + other.games,
    )

    companion object {
        val EMPTY: PitchingLine = PitchingLine()
    }
}

/**
 * 좌우 분리 기록 (docs/02).
 *
 * 타자는 상대 투수의 손, 투수는 상대 타자의 손으로 나눠 쌓는다. 플래툰 민감도(숨김 수치)가
 * 기록으로 드러나게 하려는 구조다. 양타 선수는 실제로 친 손 기준으로 들어간다.
 */
@Serializable
data class SplitStats<T>(val vsLeft: T, val vsRight: T) {
    fun get(hand: Hand): T = if (hand == Hand.LEFT) vsLeft else vsRight
}

@Serializable
data class PlayerBatting(
    val vsLeft: BattingLine = BattingLine.EMPTY,
    val vsRight: BattingLine = BattingLine.EMPTY,
    /** 상대 투수의 손과 무관한 기록(득점, 도루). 좌우 칸에 억지로 넣으면 분리 기록이 틀어진다 */
    val unsplit: BattingLine = BattingLine.EMPTY,
) {
    val total: BattingLine get() = vsLeft + vsRight + unsplit

    operator fun plus(other: PlayerBatting): PlayerBatting =
        PlayerBatting(vsLeft + other.vsLeft, vsRight + other.vsRight, unsplit + other.unsplit)
}

@Serializable
data class PlayerPitching(
    val vsLeft: PitchingLine = PitchingLine.EMPTY,
    val vsRight: PitchingLine = PitchingLine.EMPTY,
    /** 상대 타자의 손으로 나눌 수 없는 기록(실점·자책점, 승·패·세이브·홀드, 도루 저지 아웃) */
    val unsplit: PitchingLine = PitchingLine.EMPTY,
) {
    val total: PitchingLine get() = vsLeft + vsRight + unsplit

    operator fun plus(other: PlayerPitching): PlayerPitching = PlayerPitching(
        vsLeft + other.vsLeft,
        vsRight + other.vsRight,
        unsplit + other.unsplit,
    )
}

internal fun ratio(numerator: Int, denominator: Int): Double =
    if (denominator <= 0) 0.0 else numerator.toDouble() / denominator
