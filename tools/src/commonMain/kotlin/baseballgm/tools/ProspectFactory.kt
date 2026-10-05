package baseballgm.tools

import baseballgm.io.BalanceConfig
import baseballgm.league.StrengthCalculator
import baseballgm.market.DraftProspect
import baseballgm.market.ProspectSupplier
import baseballgm.model.Origin
import baseballgm.model.Pitcher
import baseballgm.model.PitcherRole
import baseballgm.model.Position
import baseballgm.model.RosterLevel
import baseballgm.util.chance
import baseballgm.util.nextGaussian
import baseballgm.util.nextGaussianInt
import baseballgm.util.nextInRange
import kotlin.random.Random

/**
 * 드래프트 풀 생성기 (docs/10).
 *
 * 고졸(18~19)과 대졸(22~23)을 섞어 만든다. **고졸은 현재치가 낮고 잠재력 편차가 크며(도박),
 * 대졸은 현재치가 높고 편차가 작다(안전).** 이 차이는 잠재력 표준편차와 나이로 저절로 생긴다 —
 * [PlayerGenerator] 가 잠재력에서 나이만큼 거꾸로 현재 능력치를 깎아 만들기 때문이다.
 *
 * 소속이 없는 선수라서 `teamId = null` 이다. 지명되기 전에는 리그 선수 명단에 들어가지 않는다.
 */
class ProspectFactory(
    balance: BalanceConfig,
    strength: StrengthCalculator,
    seed: Long,
    /** 리그 선수와 id 가 겹치지 않도록 시작 번호를 뒤에서 잡는다 */
    private val startingIdNumber: Int,
) : ProspectSupplier {

    private val params = GenerationParams(balance)
    private val strengthCalculator = strength
    private val names = NameGenerator(Random(seed xor NAME_SEED_MIX))
    private val section = balance.section("draft")
    private val collegeShare = section.double("collegeShare")
    private val highSchoolAge = section.intRange("highSchoolAge")
    private val collegeAge = section.intRange("collegeAge")
    private val highSchoolPotential = Pair2.of(section, "potential.highSchool")
    private val collegePotential = Pair2.of(section, "potential.college")
    private val pitcherShare = section.double("pitcherShare")
    private val starterShare = section.double("starterShare")
    private val generationalChance = section.double("generational.chance")
    private val generationalPotential = section.doubleRange("generational.potential")

    private var nextId = startingIdNumber

    override fun create(season: Int, count: Int, random: Random): List<DraftProspect> {
        val generator = PlayerGenerator(
            params = params,
            strength = strengthCalculator,
            names = names,
            season = season,
            startingIdNumber = nextId,
        )
        nextId += count

        // 역대급 재능: 해마다 일정 확률로 한 명 (3~4년에 한 명꼴). 고졸로 만든다 — 어릴 때부터 이름난 괴물 신인
        val generationalIndex = if (count > 0 && random.chance(generationalChance)) random.nextInt(count) else -1

        return (0 until count).map { index ->
            val generational = index == generationalIndex
            val college = !generational && random.chance(collegeShare)
            val isPitcher = random.chance(pitcherShare)
            val potential = if (college) collegePotential else highSchoolPotential
            val potentialValue = if (generational) {
                generationalPotential.start + random.nextDouble() * (generationalPotential.endInclusive - generationalPotential.start)
            } else {
                random.nextGaussian(potential.first, potential.second)
            }
            val player = generator.generate(
                PlayerSpec(
                    role = GenerationRole.FUTURES,
                    rosterLevel = RosterLevel.FUTURES,
                    position = if (isPitcher) null else Position.fielding.random(random),
                    pitcherRole = if (isPitcher) {
                        if (random.chance(starterShare)) PitcherRole.STARTER else PitcherRole.RELIEVER
                    } else {
                        null
                    },
                    potentialOverride = potentialValue,
                    ageRange = if (college) collegeAge else highSchoolAge,
                    originOverride = if (college) Origin.COLLEGE else Origin.HIGH_SCHOOL,
                    // 데뷔 시즌은 입단하는 해(다음 시즌)에 다시 정한다
                    debutSeasonOverride = season + 1,
                ),
                teamId = null,
                flavor = TeamFlavor(),
                random = random,
            )
            val highSchool = player.origin != Origin.COLLEGE
            DraftProspect(
                player = player,
                school = schoolName(highSchool, random),
                heightCm = height(player is Pitcher, random),
                weightKg = weight(player is Pitcher, random),
                injuryHistory = injuryHistory(random),
            )
        }
    }

    private fun schoolName(highSchool: Boolean, random: Random): String =
        NamePools.schoolPrefixes.random(random) + if (highSchool) "고" else "대"

    private fun height(isPitcher: Boolean, random: Random): Int =
        random.nextGaussianInt(if (isPitcher) PITCHER_HEIGHT else BATTER_HEIGHT, HEIGHT_SD, HEIGHT_RANGE)

    private fun weight(isPitcher: Boolean, random: Random): Int =
        random.nextGaussianInt(if (isPitcher) PITCHER_WEIGHT else BATTER_WEIGHT, WEIGHT_SD, WEIGHT_RANGE)

    /** 아마추어 때의 부상 이력. 리포트에만 보이고 내구도(숨김)와 직접 연결하지는 않는다. */
    private fun injuryHistory(random: Random): List<String> =
        if (random.chance(INJURY_HISTORY_SHARE)) listOf(INJURIES.random(random)) else emptyList()

    private companion object {
        const val NAME_SEED_MIX = 0x0D2AF7L
        const val PITCHER_HEIGHT = 185.0
        const val BATTER_HEIGHT = 181.0
        const val HEIGHT_SD = 4.5
        val HEIGHT_RANGE = 168..200
        const val PITCHER_WEIGHT = 88.0
        const val BATTER_WEIGHT = 85.0
        const val WEIGHT_SD = 7.0
        val WEIGHT_RANGE = 66..115
        const val INJURY_HISTORY_SHARE = 0.22
        val INJURIES = listOf(
            "고교 3학년 팔꿈치 통증 (수술 없음)",
            "대학 2학년 어깨 염증",
            "햄스트링 재발 이력",
            "손목 골절 후 복귀",
            "허리 디스크 초기 소견",
        )
    }
}

/** 리그에 이미 있는 선수 id 뒤에서 이어 붙일 번호. */
fun nextPlayerIdNumber(existing: List<baseballgm.model.Player>, pool: List<DraftProspect> = emptyList()): Int {
    val ids = existing.map { it.id } + pool.map { it.id }
    return ids.mapNotNull { it.value.removePrefix("P").toIntOrNull() }.maxOrNull()?.plus(1) ?: 1
}
