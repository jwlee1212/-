package baseballgm.scouting

import baseballgm.model.Attribute
import baseballgm.model.GrowthType
import baseballgm.model.Pitcher
import baseballgm.model.Player
import kotlin.math.roundToInt

/** 선수의 겉으로 보이는 정보. 능력치가 아니라서 정확도와 상관없이 그대로 보인다 (docs/10). */
data class Physique(val heightCm: Int, val weightKg: Int) {
    override fun toString(): String = "${heightCm}cm ${weightKg}kg"
}

/**
 * 스카우트 리포트 (docs/10).
 *
 * 구성: 현재 능력치 범위 · 잠재력 등급 범위 · 성장 타입 추정 · 코멘트 · 부상 이력 · 체격.
 * 능력치는 전부 [ScoutedPlayer] 를 거쳐 들어오므로 리포트에도 진짜 값은 없다.
 */
data class ScoutReport(
    val scouted: ScoutedPlayer,
    val focusWeeks: Int,
    val school: String?,
    val physique: Physique?,
    val topSpeedKmh: Int?,
    val injuryHistory: List<String>,
    val potentialRange: RatingRange,
    val comments: List<String>,
    /** 이름난 고교 유망주라 관찰 전부터 자료가 꽤 있는 선수 (ScoutingBudget.isKnownProspect) */
    val knownProspect: Boolean = false,
) {
    val accuracyLabel: String get() = scouted.precision.label

    val injuryText: String get() = if (injuryHistory.isEmpty()) "특이사항 없음" else injuryHistory.joinToString(", ")
}

/**
 * 리포트의 코멘트를 만든다.
 *
 * 코멘트는 **정확도가 낮으면 틀릴 수 있다.** 성장 타입 추정(`growthTypeGuess`)이 이미 틀릴 수 있게
 * 되어 있고, 여기서는 그 추정을 그대로 문장으로 옮긴다. 정확한 값을 새로 꺼내 쓰지 않는 이유다.
 * 문장 선택도 선수별 고정 씨앗으로 정해서, 같은 선수를 몇 번 열어도 같은 코멘트가 나온다.
 */
object ScoutReportWriter {

    private const val KNOWN_PROSPECT_COMMENT = "고교 시절부터 이름난 유망주라 대회 영상·기록이 많이 쌓여 있다"

    fun write(
        player: Player,
        precision: ScoutingPrecision,
        season: Int,
        focusWeeks: Int = 0,
        school: String? = null,
        physique: Physique? = null,
        injuryHistory: List<String> = emptyList(),
        knownProspect: Boolean = false,
        scale: PotentialScale,
    ): ScoutReport {
        val scouted = ScoutingView.of(player, precision, season, scale)
        return ScoutReport(
            scouted = scouted,
            focusWeeks = focusWeeks,
            school = school,
            physique = physique,
            topSpeedKmh = (player as? Pitcher)?.topSpeedKmh,
            injuryHistory = injuryHistory,
            potentialRange = ScoutingView.potentialRange(player, precision),
            comments = (if (knownProspect) listOf(KNOWN_PROSPECT_COMMENT) else emptyList()) +
                comments(scouted, precision, player),
            knownProspect = knownProspect,
        )
    }

    private fun comments(
        scouted: ScoutedPlayer,
        precision: ScoutingPrecision,
        player: Player,
    ): List<String> {
        val seed = player.hidden.scoutingNoiseSeed
        val comments = mutableListOf<String>()

        // ① 가장 눈에 띄는 능력치 — 범위 중심 기준이라 이것도 틀릴 수 있다
        val best = scouted.ratings.maxByOrNull { it.value.center }
        if (best != null) comments += strengthComment(best.key, best.value.center, seed)

        // ② 성장 타입 추정
        comments += growthComment(scouted.growthTypeGuess, precision.growthTypeReliable, seed)

        // ③ 내구도
        comments += scouted.durabilityComment

        // ④ 정확도가 낮으면 "더 봐야 안다"는 단서를 붙인다
        if (precision.halfWidth >= UNCERTAIN_HALF_WIDTH) comments += UNCERTAIN_COMMENTS[pick(seed, 41, UNCERTAIN_COMMENTS.size)]
        return comments
    }

    private fun strengthComment(attribute: Attribute, value: Double, seed: Int): String {
        val templates = STRENGTH_COMMENTS[attribute] ?: listOf("${attribute.label}이(가) 눈에 띈다")
        val level = when {
            value >= HIGH_RATING -> "지금 당장 1군에서도 통한다"
            value >= MID_RATING -> "또래 중에서는 앞선다"
            else -> "아직 다듬을 부분이 많다"
        }
        return templates[pick(seed, attribute.ordinal + 11, templates.size)] + " — " + level
    }

    private fun growthComment(guess: GrowthType, reliable: Boolean, seed: Int): String {
        val base = when (guess) {
            GrowthType.EARLY -> "일찍 완성되는 유형으로 보인다"
            GrowthType.NORMAL -> "무난한 성장 곡선을 그릴 것으로 본다"
            GrowthType.LATE -> "늦게 터지는 유형으로 보인다"
        }
        return if (reliable) base else "$base (확신은 없다)"
    }

    /** 씨앗으로 문장을 고른다. 같은 선수면 항상 같은 문장이 나온다. */
    private fun pick(seed: Int, salt: Int, size: Int): Int =
        (((ScoutingView.noise(seed, salt) + 1.0) / 2.0) * size).roundToInt().coerceIn(0, size - 1)

    private const val HIGH_RATING = 70.0
    private const val MID_RATING = 55.0
    private const val UNCERTAIN_HALF_WIDTH = 10

    private val UNCERTAIN_COMMENTS = listOf(
        "본 횟수가 적어 평가가 흔들린다",
        "컨디션 좋은 날만 봤을 수 있다",
        "상대 수준이 낮아 판단이 어렵다",
    )

    private val STRENGTH_COMMENTS: Map<Attribute, List<String>> = mapOf(
        Attribute.CONTACT to listOf("배트 컨트롤이 좋다", "공을 맞히는 재주가 있다", "삼진이 적다"),
        Attribute.POWER to listOf("타구 속도가 빠르다", "한 방이 있다", "중심 이동이 좋아 비거리가 나온다"),
        Attribute.EYE to listOf("선구안이 또래보다 낫다", "볼을 골라낼 줄 안다", "타석에서 서두르지 않는다"),
        Attribute.SPEED to listOf("발이 빠르다", "주루 센스가 좋다", "1루까지 전력으로 뛴다"),
        Attribute.DEFENSE to listOf("수비가 안정적이다", "송구가 정확하다", "타구 판단이 빠르다"),
        Attribute.STUFF to listOf("공에 힘이 있다", "변화구 각이 크다", "헛스윙을 잘 유도한다"),
        Attribute.CONTROL to listOf("스트라이크를 던질 줄 안다", "제구가 안정적이다", "볼넷이 적다"),
        Attribute.GROUNDBALL to listOf("땅볼 유도가 좋다", "공이 낮게 깔린다", "장타를 잘 맞지 않는다"),
        Attribute.STAMINA to listOf("이닝을 먹을 체력이 있다", "후반에도 구속이 유지된다", "투구 수를 늘려도 버틴다"),
    )
}

/**
 * 주간 스카우트 소식 (docs/10 "리포트는 매주 갱신, 알림함에 소식").
 *
 * 리포트 수치를 바꾸는 게 아니라 **읽을거리**다. 실제 정확도는 관찰 주차가 올린다.
 */
object ScoutNews {

    fun forProspect(report: ScoutReport, week: Int): String {
        val name = report.scouted.name
        val speed = report.topSpeedKmh
        val templates = buildList {
            if (speed != null) {
                add("$name 최고 구속 ${speed + (week % 3) - 1}km/h")
                add("$name 완투 — 삼진 ${8 + week % 5}개")
            } else {
                add("$name 주간 ${3 + week % 3}안타 ${week % 2}홈런")
                add("$name 전국 대회 결승 진출 — 타율 .${370 + week % 30}")
            }
            add("$name ${report.physique?.let { "체중 ${it.weightKg}kg 로 증량" } ?: "몸이 좋아졌다는 평"}")
            add("$name 스카우트 평: ${report.comments.firstOrNull() ?: "관찰 중"}")
        }
        return templates[(report.scouted.id.value.hashCode() + week).mod(templates.size)]
    }

    /** 자동 집중 관찰 소식. "누구는 다 봤고, 누구를 새로 붙였다" */
    fun forAutoFocus(result: baseballgm.season.AutoFocusResult): String {
        fun names(list: List<baseballgm.market.DraftProspect>) =
            list.take(NAMES_SHOWN).joinToString(", ") { it.player.registeredName } +
                if (list.size > NAMES_SHOWN) " 외 ${list.size - NAMES_SHOWN}명" else ""
        return buildList {
            if (result.finished.isNotEmpty()) add("관찰 완료 ${names(result.finished)}")
            if (result.started.isNotEmpty()) add("자동 관찰 시작 ${names(result.started)}")
        }.joinToString(" · ")
    }

    private const val NAMES_SHOWN = 3
}
