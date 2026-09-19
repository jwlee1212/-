package baseballgm.sim

import baseballgm.io.BalanceConfig
import baseballgm.league.LeagueEnvironment
import baseballgm.model.Attribute
import baseballgm.model.Batter
import baseballgm.model.Hand
import baseballgm.model.Pitcher
import baseballgm.model.PlayerId
import baseballgm.util.chance
import baseballgm.util.nextGaussian
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** 수비 쪽 정보. 실책을 누구 기록으로 남길지까지 들고 있다. */
data class DefenseContext(
    val infieldDefense: Double,
    val outfieldDefense: Double,
    val catcherDefense: Double,
    val infielders: List<PlayerId>,
    val outfielders: List<PlayerId>,
)

/** 타석 하나의 결과. 베이스 상태 변화는 [BaseRunning] 이 처리한다. */
data class PlateAppearanceResult(
    val outcome: PaOutcome,
    val battedBall: BattedBallType?,
    val pitches: Int,
    val errorBy: PlayerId? = null,
)

/** 유효 능력치 (폼·피로·좌우 상성·투구수를 반영한 값). */
private data class EffectiveRatings(val values: Map<Attribute, Double>) {
    operator fun get(attribute: Attribute): Double = values.getValue(attribute)
}

/**
 * 타석 시뮬레이션 (docs/04).
 *
 * 단계별로 결과를 좁혀 간다.
 * ① 삼진/볼넷/사구/인플레이 → ② 홈런 → ③ 타구 종류 → ④ 안타 여부 → ⑤ 안타 종류 → ⑥ 상황 결과
 *
 * 각 단계의 확률은 **Log5** 로 타자·투수 비율을 리그 평균 기준으로 합친다. 능력치는 전부
 * `RatingTables` 를 거쳐 비율로 바뀌므로 이 파일에는 밸런스 숫자가 없다 (불변 원칙 3).
 */
class PlateAppearanceSim(
    private val balance: BalanceConfig,
    private val tables: RatingTables,
    private val environment: LeagueEnvironment = LeagueEnvironment.NEUTRAL,
) {
    private val leagueK = balance.double("leagueAverages.kRate")
    private val leagueBb = balance.double("leagueAverages.bbRate")
    private val leagueHr = balance.double("leagueAverages.hrPerBattedBall")
    private val leagueBabip = balance.double("leagueAverages.babip")
    private val leagueGb = balance.double("leagueAverages.gbShare")
    private val leagueFly = balance.double("leagueAverages.flyShare")

    private val hitChanceGround = balance.double("battedBall.hitChance.ground")
    private val hitChanceFly = balance.double("battedBall.hitChance.fly")
    private val hitChanceLine = balance.double("battedBall.hitChance.line")
    private val lineShareBase = balance.double("battedBall.lineShare.base")
    private val lineShareMin = balance.double("battedBall.lineShare.min")
    private val lineShareMax = balance.double("battedBall.lineShare.max")
    private val fieldersChoiceChance = balance.double("battedBall.fieldersChoiceChance")
    private val sacFlyChance = balance.double("battedBall.sacFlyChance")
    private val extraBaseFromPower = balance.double("battedBall.extraBaseFromPower")

    private val droppedThirdStrike = balance.double("rareEvents.droppedThirdStrike")
    private val catcherInterference = balance.double("rareEvents.catcherInterference")

    private val platoonBatterSwing = balance.double("platoon.batterRatingSwing")
    private val platoonPitcherSwing = balance.double("platoon.pitcherRatingSwing")
    private val platoonPivot = balance.double("platoon.sensitivityPivot")
    private val formMaxEffect = balance.double("form.maxRatingEffect")
    private val fatiguePenaltyAt100 = balance.double("conditionEffects.fatigueRatingPenaltyAt100")

    private val pitchesStrikeout = balance.double("gameFlow.pitchesPerOutcome.strikeout")
    private val pitchesWalk = balance.double("gameFlow.pitchesPerOutcome.walk")
    private val pitchesInPlay = balance.double("gameFlow.pitchesPerOutcome.inPlay")
    private val pitchesHbp = balance.double("gameFlow.pitchesPerOutcome.hitByPitch")
    private val pitchSpread = balance.double("gameFlow.pitchCountSpread")
    private val pitchesPerEyePoint = balance.double("gameFlow.pitchesPerEyePoint")
    private val fatigueStartShare = balance.double("gameFlow.pitchCountFatigueStart")
    private val fatiguePerPitch = balance.double("gameFlow.pitchCountFatiguePerPitch")

    /** 투수가 지금까지 던진 공으로 능력치가 얼마나 떨어지는지 (docs/04 투수 피로 보정). */
    fun inGameFatiguePenalty(pitcher: Pitcher, pitchesThrown: Int): Double {
        val limit = tables.value("pitcherStaminaToPitchLimit", effectiveStamina(pitcher))
        val start = limit * fatigueStartShare
        return max(0.0, (pitchesThrown - start)) * fatiguePerPitch
    }

    fun pitchLimitOf(pitcher: Pitcher): Int =
        tables.value("pitcherStaminaToPitchLimit", effectiveStamina(pitcher)).roundToInt()

    /**
     * 타석 하나를 굴린다.
     *
     * @param basesCode 베이스 상태 비트 (1루=1, 2루=2, 3루=4)
     * @param parkFactor 구장 계수. 홈런·장타에 곱한다
     */
    fun simulate(
        batter: Batter,
        pitcher: Pitcher,
        defense: DefenseContext,
        basesCode: Int,
        outs: Int,
        parkFactor: Double,
        pitcherPitchCount: Int,
        random: Random,
    ): PlateAppearanceResult {
        val batterHand = effectiveBatterHand(batter, pitcher)
        val samehand = batterHand == pitcher.throwsWith
        val bat = effectiveBatter(batter, samehand)
        val pit = effectivePitcher(pitcher, samehand, pitcherPitchCount)

        // 타격방해: 아주 드물지만 출루 방법 중 하나다
        if (random.chance(catcherInterference)) {
            return PlateAppearanceResult(PaOutcome.CATCHER_INTERFERENCE, null, pitchCount(pitchesInPlay, bat, random))
        }

        // ① 승부 결과
        val batterK = (tables.value("batterContactToK", bat[Attribute.CONTACT]) +
            tables.value("batterEyeToK", bat[Attribute.EYE])) / 2.0
        val kRate = (Log5.rate(batterK, tables.value("pitcherStuffToK", pit[Attribute.STUFF]), leagueK) +
            environment.strikeoutDelta).coerceIn(0.01, 0.65)
        val bbRate = (Log5.rate(
            tables.value("batterEyeToBB", bat[Attribute.EYE]),
            tables.value("pitcherControlToBB", pit[Attribute.CONTROL]),
            leagueBb,
        ) + environment.walkDelta).coerceIn(0.005, 0.40)
        val hbpRate = tables.value("pitcherControlToHbp", pit[Attribute.CONTROL])

        val roll = random.nextDouble()
        if (roll < kRate) {
            // 낫아웃: 1루가 비었거나 2아웃일 때만 살아 나갈 수 있다
            val firstOpen = basesCode and 1 == 0
            val outcome = if ((firstOpen || outs == 2) && random.chance(droppedThirdStrike)) {
                PaOutcome.STRIKEOUT_REACHED
            } else {
                PaOutcome.STRIKEOUT
            }
            return PlateAppearanceResult(outcome, null, pitchCount(pitchesStrikeout, bat, random))
        }
        if (roll < kRate + bbRate) {
            return PlateAppearanceResult(PaOutcome.WALK, null, pitchCount(pitchesWalk, bat, random))
        }
        if (roll < kRate + bbRate + hbpRate) {
            return PlateAppearanceResult(PaOutcome.HIT_BY_PITCH, null, pitchCount(pitchesHbp, bat, random))
        }

        val pitches = pitchCount(pitchesInPlay, bat, random)

        // ② 홈런
        val hrRate = Log5.rate(
            tables.value("batterPowerToHR", bat[Attribute.POWER]),
            tables.value("pitcherStuffToHR", pit[Attribute.STUFF]),
            leagueHr,
        ) * parkFactor * environment.homeRunMultiplier
        if (random.chance(hrRate)) {
            return PlateAppearanceResult(PaOutcome.HOME_RUN, BattedBallType.FLY, pitches)
        }

        // ③ 타구 종류
        val battedBall = pickBattedBall(bat, pit, random)

        // 실책: 인플레이 타구의 약 1.5% (docs/05)
        val fielder = pickFielder(battedBall, defense, random)
        val fielderDefense = if (battedBall == BattedBallType.GROUND) defense.infieldDefense else defense.outfieldDefense
        if (random.chance(tables.value("defenseToErrorRate", fielderDefense))) {
            return PlateAppearanceResult(PaOutcome.REACHED_ON_ERROR, battedBall, pitches, errorBy = fielder)
        }

        // ④ 안타 여부
        val matchupBabip = (
            Log5.rate(
                tables.value("batterContactToBabip", bat[Attribute.CONTACT]),
                tables.value("pitcherStuffToBabip", pit[Attribute.STUFF]),
                leagueBabip,
            ) +
                tables.value("batterSpeedToBabip", bat[Attribute.SPEED]) +
                tables.value("defenseToBabip", fielderDefense)
            ).coerceIn(0.15, 0.55)
        val hitChance = (baseHitChance(battedBall) * (matchupBabip / leagueBabip)).coerceIn(0.01, 0.95)

        if (random.chance(hitChance)) {
            // ⑤ 안타 종류
            return PlateAppearanceResult(hitType(battedBall, bat, parkFactor, random), battedBall, pitches)
        }

        // ⑥ 상황 결과 — 베이스·아웃 상태에 맞는 결과만 남긴다
        return PlateAppearanceResult(outType(battedBall, bat, defense, basesCode, outs, random), battedBall, pitches)
    }

    // ---------- 단계별 부속 ----------

    private fun pickBattedBall(
        bat: EffectiveRatings,
        pit: EffectiveRatings,
        random: Random,
    ): BattedBallType {
        val pitcherGb = tables.value("pitcherGroundballToGbShare", pit[Attribute.GROUNDBALL])
        val batterFly = tables.value("batterPowerToFlyShare", bat[Attribute.POWER])
        var ground = (pitcherGb + (leagueFly - batterFly)).coerceIn(0.15, 0.75)
        var fly = (batterFly + (leagueGb - pitcherGb) * 0.5).coerceIn(0.15, 0.65)
        val line = (lineShareBase * (1.0 + (bat[Attribute.CONTACT] - 50.0) / 250.0))
            .coerceIn(lineShareMin, lineShareMax)
        val rest = 1.0 - line
        val sum = ground + fly
        ground = ground / sum * rest
        fly = fly / sum * rest

        val roll = random.nextDouble()
        return when {
            roll < ground -> BattedBallType.GROUND
            roll < ground + fly -> BattedBallType.FLY
            else -> BattedBallType.LINE
        }
    }

    private fun baseHitChance(type: BattedBallType): Double = when (type) {
        BattedBallType.GROUND -> hitChanceGround
        BattedBallType.FLY -> hitChanceFly
        BattedBallType.LINE -> hitChanceLine
    }

    private fun hitType(
        type: BattedBallType,
        bat: EffectiveRatings,
        parkFactor: Double,
        random: Random,
    ): PaOutcome {
        val key = when (type) {
            BattedBallType.GROUND -> "ground"
            BattedBallType.FLY -> "fly"
            BattedBallType.LINE -> "line"
        }
        val single = balance.double("battedBall.hitTypeShares.$key.single")
        var double = balance.double("battedBall.hitTypeShares.$key.double")
        var triple = balance.double("battedBall.hitTypeShares.$key.triple")
        // 파워는 2루타를, 주루는 3루타를 늘린다
        val powerBoost = 1.0 + (bat[Attribute.POWER] - 50.0) / 50.0 * extraBaseFromPower
        val speedBoost = 1.0 + tables.value("batterSpeedToExtraBase", bat[Attribute.SPEED])
        double *= powerBoost * parkFactor
        triple *= speedBoost
        val total = single + double + triple
        val roll = random.nextDouble() * total
        return when {
            roll < single -> PaOutcome.SINGLE
            roll < single + double -> PaOutcome.DOUBLE
            else -> PaOutcome.TRIPLE
        }
    }

    private fun outType(
        type: BattedBallType,
        bat: EffectiveRatings,
        defense: DefenseContext,
        basesCode: Int,
        outs: Int,
        random: Random,
    ): PaOutcome {
        val runnerOnFirst = basesCode and 1 != 0
        val runnerOnThird = basesCode and 4 != 0
        return when (type) {
            BattedBallType.GROUND -> when {
                runnerOnFirst && outs < 2 -> {
                    val dpChance = tables.value("batterSpeedToDoublePlay", bat[Attribute.SPEED]) *
                        (1.0 + (defense.infieldDefense - 50.0) / 200.0)
                    when {
                        random.chance(dpChance) -> PaOutcome.DOUBLE_PLAY
                        random.chance(fieldersChoiceChance) -> PaOutcome.FIELDERS_CHOICE
                        else -> PaOutcome.GROUND_OUT
                    }
                }
                basesCode != 0 && outs < 2 && random.chance(fieldersChoiceChance) -> PaOutcome.FIELDERS_CHOICE
                else -> PaOutcome.GROUND_OUT
            }
            BattedBallType.FLY ->
                if (runnerOnThird && outs < 2 && random.chance(sacFlyChance)) PaOutcome.SAC_FLY else PaOutcome.FLY_OUT
            BattedBallType.LINE -> PaOutcome.LINE_OUT
        }
    }

    private fun pickFielder(type: BattedBallType, defense: DefenseContext, random: Random): PlayerId? {
        val pool = if (type == BattedBallType.GROUND) defense.infielders else defense.outfielders
        if (pool.isEmpty()) return null
        return pool[random.nextInt(pool.size)]
    }

    private fun pitchCount(base: Double, bat: EffectiveRatings, random: Random): Int {
        val eyeBonus = (bat[Attribute.EYE] - 50.0) * pitchesPerEyePoint
        return random.nextGaussian(base + eyeBonus, pitchSpread).roundToInt().coerceIn(1, 15)
    }

    // ---------- 유효 능력치 ----------

    /** 양타는 항상 반대 손으로 친다 (docs/02). 기록도 이 손 기준으로 좌우 분리한다. */
    fun effectiveBatterHand(batter: Batter, pitcher: Pitcher): Hand = when (batter.bats) {
        Hand.SWITCH -> if (pitcher.throwsWith == Hand.LEFT) Hand.RIGHT else Hand.LEFT
        else -> batter.bats
    }

    private fun effectiveBatter(batter: Batter, sameHand: Boolean): EffectiveRatings {
        val platoon = platoonShift(batter.hidden.platoonSplit, platoonBatterSwing, sameHand)
        val condition = conditionShift(batter.condition.form, batter.condition.fatigue)
        return EffectiveRatings(
            batter.ratingsMap().mapValues { (attribute, value) ->
                val shift = when (attribute) {
                    Attribute.CONTACT, Attribute.POWER, Attribute.EYE -> platoon
                    else -> 0.0
                }
                (value + shift + condition).coerceIn(1.0, 100.0)
            },
        )
    }

    private fun effectivePitcher(pitcher: Pitcher, sameHand: Boolean, pitchesThrown: Int): EffectiveRatings {
        // 투수 입장에서는 같은 손 매치업이 유리하다
        val platoon = platoonShift(pitcher.hidden.platoonSplit, platoonPitcherSwing, !sameHand)
        val condition = conditionShift(pitcher.condition.form, pitcher.condition.fatigue)
        val inGame = inGameFatiguePenalty(pitcher, pitchesThrown)
        return EffectiveRatings(
            pitcher.ratingsMap().mapValues { (attribute, value) ->
                val shift = when (attribute) {
                    Attribute.STUFF, Attribute.CONTROL -> platoon - inGame
                    else -> 0.0
                }
                (value + shift + condition).coerceIn(1.0, 100.0)
            },
        )
    }

    /** 같은 손 매치업이면 불리(−), 반대면 유리(+). 폭은 선수별 플래툰 민감도에 비례한다. */
    private fun platoonShift(sensitivity: Int, swing: Double, disadvantage: Boolean): Double {
        val scale = sensitivity / platoonPivot
        return if (disadvantage) -swing * scale else swing * scale
    }

    private fun conditionShift(form: Int, fatigue: Int): Double {
        val formShift = (form - 50) / 50.0 * formMaxEffect
        val fatiguePenalty = fatigue / 100.0 * fatiguePenaltyAt100
        return formShift - fatiguePenalty
    }

    private fun effectiveStamina(pitcher: Pitcher): Double =
        (pitcher.ratings.stamina + conditionShift(pitcher.condition.form, pitcher.condition.fatigue))
            .coerceIn(1.0, 100.0)
}
