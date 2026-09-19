package baseballgm.tools

import baseballgm.io.ConfigException
import baseballgm.io.JsonSection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.math.abs

/**
 * `data/teams.json` — 10개 구단의 생성 목표치.
 *
 * 리그 생성기(M1)가 이 값을 목표로 선수를 만든다. 여기 있는 숫자는 "결과"가 아니라 "목표"라서,
 * 생성 후 실제 전력은 이 값 ±2 안에 들어오면 된다(docs/15 M1 완료 기준).
 */
@Serializable
data class TeamTemplates(
    val season: Int,
    /** 소프트캡 금액(억원). */
    val salaryCap: Int,
    val compositeWeights: CompositeWeights,
    val teams: List<TeamTemplate>,
) {
    fun byId(id: String): TeamTemplate =
        teams.firstOrNull { it.id == id } ?: throw ConfigException("그런 구단이 없다: $id")

    /** 종합 전력 = 타선·선발·불펜의 가중 평균. teams.json 의 overall 값이 맞는지 검산할 때 쓴다. */
    fun compositeOf(target: TeamTargets): Double =
        target.lineup * compositeWeights.lineup +
            target.rotation * compositeWeights.rotation +
            target.bullpen * compositeWeights.bullpen

    /**
     * 데이터 자체의 앞뒤가 맞는지 검사한다. 문제 목록을 돌려주고, 비어 있으면 정상이다.
     * (M1 리그 생성기가 잘못된 목표치를 그대로 믿고 선수를 만드는 일을 막기 위한 안전장치)
     */
    fun validate(): List<String> {
        val problems = mutableListOf<String>()

        if (teams.size != TEAM_COUNT) problems += "구단 수가 ${teams.size}개다 (기대: $TEAM_COUNT)"

        val duplicatedIds = teams.groupBy { it.id }.filterValues { it.size > 1 }.keys
        if (duplicatedIds.isNotEmpty()) problems += "구단 ID가 중복됐다: $duplicatedIds"

        val picks = teams.map { it.draftPick }.sorted()
        if (picks != (1..teams.size).toList()) problems += "드래프트 지명 순번이 1~${teams.size} 순열이 아니다: $picks"

        val tutorialTeams = teams.filter { it.recommendedForTutorial }
        if (tutorialTeams.size != 1) {
            problems += "튜토리얼 추천 구단은 정확히 1개여야 한다 (현재 ${tutorialTeams.map { it.id }})"
        }

        val weightSum = compositeWeights.lineup + compositeWeights.rotation + compositeWeights.bullpen
        if (abs(weightSum - 1.0) > 1e-6) problems += "전력 가중치 합이 1이 아니다: $weightSum"

        for (team in teams) {
            if (team.tier !in TIERS) problems += "${team.id}: 알 수 없는 tier ${team.tier}"
            if (team.marketSize !in MARKET_SIZES) problems += "${team.id}: 알 수 없는 marketSize ${team.marketSize}"
            if (team.targets.prospectGrade !in PROSPECT_GRADES) {
                problems += "${team.id}: 알 수 없는 prospectGrade ${team.targets.prospectGrade}"
            }
            for ((name, rating) in team.targets.namedRatings()) {
                if (rating !in RATING_RANGE) problems += "${team.id}: $name 능력치가 범위를 벗어났다 ($rating)"
            }
            val composite = compositeOf(team.targets)
            if (abs(composite - team.targets.overall) > COMPOSITE_TOLERANCE) {
                problems += "${team.id}: overall ${team.targets.overall} 이 부문별 가중 평균 ${
                    (composite * 10).toInt() / 10.0
                } 과 다르다"
            }
            if (team.payroll <= 0) problems += "${team.id}: 연봉 총액이 0 이하다"
            if (team.operatingFunds < 0) problems += "${team.id}: 운영 자금이 음수다"
            if (team.parkFactor !in PARK_FACTOR_RANGE) problems += "${team.id}: 구장 팩터가 이상하다 (${team.parkFactor})"
        }
        return problems
    }

    companion object {
        const val TEAM_COUNT: Int = 10

        /** overall 은 반올림된 값이라 가중 평균과 1점 미만 차이는 정상으로 본다. */
        private const val COMPOSITE_TOLERANCE = 1.0

        private val TIERS = setOf("strong", "mid", "weak")
        private val MARKET_SIZES = setOf("large", "medium", "small")
        private val PROSPECT_GRADES = setOf("A", "B", "C", "D")
        private val RATING_RANGE = 1..100
        private val PARK_FACTOR_RANGE = 0.8..1.2

        private val json = Json {
            ignoreUnknownKeys = true // "_note" 같은 설명 키를 무시한다
            allowComments = true
            allowTrailingComma = true
        }

        fun parse(text: String): TeamTemplates = try {
            json.decodeFromString(serializer(), text)
        } catch (e: Exception) {
            throw ConfigException("teams.json 을 파싱하지 못했다: ${e.message}")
        }
    }
}

@Serializable
data class CompositeWeights(val lineup: Double, val rotation: Double, val bullpen: Double)

@Serializable
data class TeamTargets(
    val lineup: Int,
    val rotation: Int,
    val bullpen: Int,
    val overall: Int,
    /** 팜(2군·유망주) 등급 A~D. */
    val prospectGrade: String,
) {
    fun namedRatings(): List<Pair<String, Int>> =
        listOf("lineup" to lineup, "rotation" to rotation, "bullpen" to bullpen, "overall" to overall)
}

@Serializable
data class ForeignScoutingBonus(val accuracy: String, val ratingBonus: Int)

@Serializable
data class TeamTemplate(
    val id: String,
    val name: String,
    val city: String,
    /** 모기업명. 아직 정하지 않은 구단은 null (CLAUDE.md §8 확인 필요 항목). */
    val parentCompany: String? = null,
    val nickname: String,
    /** strong / mid / weak */
    val tier: String,
    /** 구단 한 줄 콘셉트. 생성기와 UI 소개문에 쓴다. */
    val keyword: String,
    val recommendedForTutorial: Boolean = false,
    /** 난이도 표기(`hardest` 등). 없으면 보통. */
    val difficulty: String? = null,
    val targets: TeamTargets,
    /** 연봉 총액(억원). */
    val payroll: Int,
    /** 운영 자금(억원). */
    val operatingFunds: Int,
    @SerialName("draftPick2026")
    val draftPick: Int,
    val parkFactor: Double,
    val fanVolatility: Double,
    /** large / medium / small */
    val marketSize: String,
    val ownerGoal: String,
    /** FA 시장 적극성 배율. 기본 1.0. */
    val faAggression: Double = 1.0,
    val foreignScoutingBonus: ForeignScoutingBonus? = null,
    /**
     * 구단마다 다른 생성 힌트. 키 구성이 팀마다 달라서 고정 데이터 클래스로 만들지 않고
     * JSON 그대로 두고 [generationHints] 로 읽는다.
     */
    val generation: JsonObject = JsonObject(emptyMap()),
) {
    /** `generation` 안의 힌트를 경로로 읽는 뷰. 예: `generationHints.doubleOrNull("earlyGrowthBias")` */
    val generationHints: JsonSection
        get() = JsonSection(generation, "$id.generation")
}
