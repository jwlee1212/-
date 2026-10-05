package baseballgm.scouting

import baseballgm.io.BalanceConfig
import kotlin.math.roundToInt

/**
 * 스카우트 투자 단계 (docs/10).
 *
 * 단계(1~5)는 스토브리그에 정하고 한 시즌 동안 유지된다. 단계가 올라가면 두 가지가 좋아진다.
 * 1) **아마추어 기본 정확도** — 드래프트 풀 전체를 보는 눈이 좋아진다
 * 2) **집중 관찰 슬롯 수** — 따로 붙어 볼 수 있는 인원이 늘어난다
 *
 * 슬롯은 드래프트 대상만이 아니라 타 팀 선수·외국인에게도 쓸 수 있어서(docs/10),
 * "누구에게 슬롯을 쓸 것인가"가 고민거리가 된다.
 */
class ScoutingBudget(balance: BalanceConfig) {

    private val section = balance.section("scouting")
    private val focus = section.section("focus")

    val defaultLevel: Int = section.int("defaultLevel")
    val levels: IntRange = 1..5

    private val narrowingPerWeek = focus.double("narrowingPerWeek")
    private val halfWidthFloor = focus.int("halfWidthFloor")
    private val gradeSpreadWeeks = focus.intList("gradeSpreadWeeks")
    private val growthTypeReliableWeeks = focus.int("growthTypeReliableWeeks")

    private val knownProspect = section.section("knownProspect")
    private val knownPerClass = knownProspect.intRange("perClass")
    private val knownRankNoise = knownProspect.double("rankNoise")
    private val knownHeadStartWeeks = knownProspect.int("headStartWeeks")

    /**
     * 이름난 고교 유망주인가. 드래프트 풀이 만들어질 때 [markKnownProspects] 가 붙인 **공개 평판**을 읽는다.
     * 프로에 입단하면(소속이 생기면) 더 이상 아마추어 평판으로 보지 않는다.
     *
     * 이런 선수는 고교 시절부터 대회·언론에 많이 노출돼서 **어느 구단이든** 기본 자료를 꽤 갖고 있다.
     * 그래서 정확도는 보는 구단이 아니라 선수에게 달린다 — AI 구단도 똑같이 잘 본다.
     */
    fun isKnownProspect(player: baseballgm.model.Player): Boolean =
        player.teamId == null && player.origin == baseballgm.model.Origin.HIGH_SCHOOL && player.knownProspect

    /**
     * 한 해 드래프트 풀에서 이름난 유망주를 정한다 (유저 요청 2026-10-04: 해마다 3~4명).
     *
     * 고졸 선수를 "잠재력 + 선수별 고정 흔들림"으로 줄 세워 위에서 [knownPerClass] 명. 기준선 하나로 자르면
     * 해마다 0명인 해가 많았고(고졸 잠재력 평균 52·표준편차 12.5 라 80 근처가 드물다), "주목 = 잠재력 몇 이상"이
     * 그대로 읽혔다. 순위로 정하니 인원이 일정하고, 흔들림 때문에 주목 선수가 꼭 풀 최상위는 아니다.
     * 인원(3 또는 4)과 흔들림은 시즌·선수 고정 씨앗으로 정해 다시 불러도 같다 (불변 원칙 2).
     * 이미 정해진 풀이면(누군가 표시돼 있으면) 그대로 둔다.
     */
    fun markKnownProspects(pool: baseballgm.market.DraftPool): baseballgm.market.DraftPool {
        if (pool.prospects.any { it.player.knownProspect } || pool.prospects.isEmpty()) return pool
        val seasonRandom = kotlin.random.Random(pool.season.toLong() * KNOWN_SEED_MULTIPLIER + KNOWN_SEED_SALT)
        val count = knownPerClass.first + seasonRandom.nextInt(knownPerClass.last - knownPerClass.first + 1)
        val chosen = pool.prospects
            .filter { it.isHighSchool }
            .sortedByDescending { prospect ->
                val noise = kotlin.random.Random(prospect.player.hidden.scoutingNoiseSeed.toLong() * KNOWN_SEED_MULTIPLIER + KNOWN_SEED_SALT)
                    .nextDouble() * 2 - 1
                prospect.player.hidden.potential.values.average() + noise * knownRankNoise
            }
            .take(count)
            .map { it.id }
            .toSet()
        return pool.copy(
            prospects = pool.prospects.map { if (it.id in chosen) it.copy(player = it.player.withKnownProspect(true)) else it },
        )
    }

    /** 관찰을 시작하기 전부터 쌓여 있는 것으로 치는 주차. 이름난 유망주가 아니면 0 */
    fun headStartWeeks(player: baseballgm.model.Player): Int =
        if (isKnownProspect(player)) knownHeadStartWeeks else 0

    /** 리포트 소식이 몇 주에 한 번 올라오는가. */
    val newsEveryWeeks: Int = focus.int("newsEveryWeeks")

    /** AI 구단 평가에 섞는 오차의 세기 (docs/02 구현 규칙 3). */
    val aiEvaluationNoise: Double = section.double("aiEvaluationNoise")

    fun focusSlots(level: Int): Int = section.int("focusSlotsByLevel.${clamp(level)}")

    fun annualCost(level: Int): Double = section.double("annualCostByLevel.${clamp(level)}")

    /** 집중 관찰이 없는 드래프트 풀 선수를 보는 정확도. */
    fun amateurPrecision(level: Int): ScoutingPrecision {
        val halfWidth = section.int("amateurHalfWidthByLevel.${clamp(level)}")
        return ScoutingPrecision(
            halfWidth = halfWidth,
            gradeSpread = ScoutingAccuracy.MINIMAL.gradeSpread,
            growthTypeReliable = false,
            label = labelOf(halfWidth),
        )
    }

    /**
     * 집중 관찰 주차만큼 정확도를 좁힌다.
     *
     * 오차가 **줄어드는** 것이지 새로 뽑히는 게 아니다 ([ScoutingView] 가 같은 고정 씨앗을 쓰므로
     * 범위가 좁아지면서 중심이 진짜 값 쪽으로 다가온다). 그래서 "관찰을 끊었다 이었다" 해도
     * 다른 답이 나오지 않는다.
     */
    fun refine(base: ScoutingPrecision, focusWeeks: Int): ScoutingPrecision {
        // 우리 팀(숨김 성질까지 정확)은 더 볼 게 없다. 준주전급 공개 기록 선수는 능력치는 정확해도 잠재력·숨김 성질은 관찰로 좁혀진다
        if (focusWeeks <= 0 || (base.halfWidth == 0 && base.traitHalfWidth == 0)) return base
        fun narrow(width: Int): Int =
            if (width == 0) 0 else (width - narrowingPerWeek * focusWeeks).roundToInt().coerceAtLeast(halfWidthFloor)
        val narrowed = narrow(base.halfWidth)
        val gradeSpread = when {
            focusWeeks >= gradeSpreadWeeks[1] -> 0
            focusWeeks >= gradeSpreadWeeks[0] -> minOf(base.gradeSpread, 1)
            else -> base.gradeSpread
        }
        return ScoutingPrecision(
            halfWidth = narrowed,
            gradeSpread = gradeSpread,
            growthTypeReliable = base.growthTypeReliable || focusWeeks >= growthTypeReliableWeeks,
            label = if (narrowed == 0) base.label else labelOf(narrowed),
            traitHalfWidth = narrow(base.traitHalfWidth),
        )
    }

    private fun labelOf(halfWidth: Int): String = when {
        halfWidth == 0 -> "정확"
        halfWidth <= 4 -> "높음"
        halfWidth <= 7 -> "중간"
        halfWidth <= 12 -> "낮음"
        else -> "매우 낮음"
    }

    private companion object {
        /** 고정 씨앗을 이 판정 전용으로 섞는 값. 다른 오차(능력치 범위 등)와 겹치지 않게 한다 */
        const val KNOWN_SEED_MULTIPLIER = 31L
        const val KNOWN_SEED_SALT = 7_771L
    }

    private fun clamp(level: Int): Int = level.coerceIn(levels.first, levels.last)
}
