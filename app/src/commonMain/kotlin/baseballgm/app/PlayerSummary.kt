package baseballgm.app

import baseballgm.model.Attribute
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.PitcherRole
import baseballgm.model.Player
import baseballgm.util.fixed

/**
 * 선수 프로필 "요약" 탭 (2026-10-03 화면 개편 — FM 프로필을 캐주얼하게).
 *
 * 비서가 이 선수를 쉬운 말로 한두 문장에 담고, 강점·약점을 하나씩 칩으로 꺼낸다. 핵심 기록 셋을 함께 준다.
 * **능력치는 스카우트 범위(`session.scout`)의 중심만 쓴다** — 타 팀 선수의 진짜 값으로 강점을 고르면
 * "강점: 파워"만 보고도 숨김 값이 샌다 (불변 원칙 4). 우리 선수는 범위 폭이 0 이라 정확한 값이다.
 *
 * 강점·약점 기준(능력치 60 이상 / 50 미만)과 피로 경고(70)는 화면 표시 기준이라 코드 상수다.
 */
data class PlayerSummary(
    val sentence: String,
    val strength: Attribute?,
    val weakness: Attribute?,
    /** (이름, 값) 셋. 1군 기록이 없으면 2군 추정 성적이고 [fromFutures] 가 true */
    val keyNumbers: List<Pair<String, String>>,
    val fromFutures: Boolean,
) {
    companion object {
        fun of(session: GameSession, player: Player): PlayerSummary {
            val scouted = session.scout(player)
            val own = session.isOwn(player)
            val ratings = scouted.ratings.mapValues { it.value.center }
            val best = ratings.maxByOrNull { it.value }
            val worst = ratings.minByOrNull { it.value }
            val strength = best?.takeIf { it.value >= STRENGTH_MIN }?.key
            val weakness = worst?.takeIf { it.value < WEAKNESS_MAX && it.key != strength }?.key

            val age = scouted.age
            val stage = when {
                age <= YOUNG -> "어린 "
                age >= OLD -> "노장 "
                age >= VETERAN -> "베테랑 "
                else -> ""
            }
            val first = buildString {
                strength?.let { append(phraseOf(it)).append(" ") }
                append("${age}세 ${stage}${roleOf(player)}예요.")
            }
            val second = when {
                !own -> "다른 팀 선수라 능력치는 스카우트가 본 범위(${scouted.precision.label})로 봐 주세요."
                player.condition.injury != null -> player.condition.injury!!.let { "지금은 ${it.part} 부상으로 ${it.weeksRemaining}주 쉬어야 해요." }
                player.condition.fatigue >= FATIGUE_ALERT -> "피로가 많이 쌓였어요. 하루 쉬게 해도 좋겠어요."
                player.condition.form >= FORM_UP -> "요즘 감이 좋아요."
                player.condition.form < FORM_DOWN -> "요즘 폼이 떨어졌어요."
                else -> null
            }
            val third = if (own && player.contract.seasonsToFreeAgency == 0 && player.contract.yearsRemaining <= 1 && !player.isForeign) {
                "이번 시즌이 끝나면 FA예요."
            } else {
                null
            }
            val (numbers, futures) = keyNumbers(session, player)
            return PlayerSummary(listOfNotNull(first, second, third).joinToString(" "), strength, weakness, numbers, futures)
        }

        private fun keyNumbers(session: GameSession, player: Player): Pair<List<Pair<String, String>>, Boolean> = when (player) {
            is Batter -> {
                val first = session.batting(player.id)
                val futures = first.plateAppearances == 0
                val line = if (futures) session.futuresBatting(player.id) else first
                listOf(
                    "타율" to (if (line.plateAppearances > 0) line.battingAverage.fixed(3) else "—"),
                    "홈런" to "${line.homeRuns}",
                    "OPS" to (if (line.plateAppearances > 0) line.ops.fixed(3) else "—"),
                ) to futures
            }
            is Pitcher -> {
                val first = session.pitching(player.id)
                val futures = first.outs == 0
                val line = if (futures) session.futuresPitching(player.id) else first
                listOf(
                    "평균자책" to (if (line.outs > 0) line.era.fixed(2) else "—"),
                    if (player.role.isReliever) "세이브·홀드" to "${line.saves}·${line.holds}" else "승패" to "${line.wins}-${line.losses}",
                    "탈삼진" to "${line.strikeouts}",
                ) to futures
            }
        }

        /** 능력치 → 쉬운 말 (강점을 문장 앞에 붙인다) */
        fun phraseOf(attribute: Attribute): String = when (attribute) {
            Attribute.CONTACT -> "정교하게 맞히는"
            Attribute.POWER -> "한 방이 있는"
            Attribute.EYE -> "공을 잘 고르는"
            Attribute.SPEED -> "발이 빠른"
            Attribute.DEFENSE -> "수비가 좋은"
            Attribute.STUFF -> "공에 힘이 있는"
            Attribute.CONTROL -> "제구가 좋은"
            Attribute.GROUNDBALL -> "땅볼을 잘 유도하는"
            Attribute.STAMINA -> "오래 던지는"
        }

        private fun roleOf(player: Player): String = when (player) {
            is Batter -> POSITION_NAMES[player.primaryPosition.label] ?: player.primaryPosition.label
            is Pitcher -> when (player.role) {
                PitcherRole.STARTER -> "선발 투수"
                PitcherRole.CLOSER -> "마무리 투수"
                PitcherRole.RELIEVER -> "불펜 투수"
            }
        }

        private val POSITION_NAMES = mapOf(
            "C" to "포수", "1B" to "1루수", "2B" to "2루수", "3B" to "3루수", "SS" to "유격수",
            "LF" to "좌익수", "CF" to "중견수", "RF" to "우익수", "DH" to "지명타자",
        )

        // 화면 표시 기준 (게임 밸런스 수치가 아니다)
        private const val STRENGTH_MIN = 60.0
        private const val WEAKNESS_MAX = 50.0
        private const val YOUNG = 23
        private const val VETERAN = 31
        private const val OLD = 36
        private const val FATIGUE_ALERT = 70
        private const val FORM_UP = 60
        private const val FORM_DOWN = 45
    }
}
