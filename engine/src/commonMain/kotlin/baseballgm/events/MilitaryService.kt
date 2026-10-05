package baseballgm.events

import baseballgm.io.BalanceConfig
import baseballgm.model.MilitaryStatus
import baseballgm.model.Origin
import baseballgm.model.Player
import baseballgm.model.ServiceKind
import baseballgm.util.chance
import baseballgm.util.interpolateAnchors
import kotlin.random.Random

/** 입대 판정 한 건. */
data class Enlistment(
    val playerId: baseballgm.model.PlayerId,
    val kind: ServiceKind,
    val status: MilitaryStatus.Serving,
    /** 상무에 지원했는가 (불합격이면 현역으로 간다) */
    val appliedToSangmu: Boolean,
) {
    fun message(name: String): String = when (kind) {
        ServiceKind.SANGMU -> "$name 상무 입대 (합격)"
        ServiceKind.ACTIVE_DUTY ->
            if (appliedToSangmu) "$name 현역 입대 (상무 불합격)" else "$name 현역 입대"
    }
}

/** 입대 기한이 다가온다는 알림 (docs/12). */
data class EnlistmentWarning(val playerId: baseballgm.model.PlayerId, val age: Int, val deadlineAge: Int) {
    fun message(name: String): String =
        "$name ${age}세 — 입대 기한(${deadlineAge}세)이 ${deadlineAge - age}년 남았다"
}

/**
 * 군 복무 (docs/12).
 *
 * 세 가지를 정한다.
 * 1. **언제 가는가** — 기한(만 28세)에 닿으면 무조건, 그 전에도 자리가 없는 선수는 일찍 간다
 * 2. **어디로 가는가** — 상무에 지원해 합격하면 퓨처스리그에서 뛰고 성장을 유지한다.
 *    불합격이면 현역으로 가고 성장이 멈추며 감각이 떨어진다
 * 3. **언제 돌아오는가** — 복무 기간이 끝나는 주차에 즉시 복귀한다 (시즌 중 제대)
 *
 * 상무 합격은 능력치가 높을수록 쉽다. 정원이 있어서 리그 전체로 보면 한 해에
 * `slotsPerSeason` 명까지만 붙는다 — 그래서 어중간한 선수는 현역을 각오해야 하고,
 * 이것이 "유망주를 일찍 보낼까"라는 결정을 만든다.
 */
class MilitaryService(balance: BalanceConfig) {

    private val section = balance.section("military")
    val deadlineAge: Int = section.int("enlistDeadlineAge")
    private val serviceMonths = section.int("serviceMonths")
    private val sangmuChance = section.numericMap("sangmu.chanceByOverall")
    private val sangmuSlots = section.int("sangmu.slotsPerSeason")
    private val warningAges = section.intList("warningAges")
    private val earlyEnlistOverall = section.double("earlyEnlistOverall")
    private val earlyEnlistChance = section.double("earlyEnlistChance")

    private val regularSeasonWeeks = balance.int("season.regularSeasonWeeks")

    /** 복무 기간을 시즌·주차로 환산한다. 18개월이면 두 시즌 뒤 시즌 중반에 돌아온다. */
    fun returnPoint(enlistSeason: Int): Pair<Int, Int> {
        val seasons = serviceMonths / MONTHS_PER_YEAR
        val remainderMonths = serviceMonths % MONTHS_PER_YEAR
        val week = (remainderMonths.toDouble() / MONTHS_PER_YEAR * regularSeasonWeeks).toInt().coerceIn(1, regularSeasonWeeks)
        return (enlistSeason + seasons + 1) to week
    }

    /** 이번 스토브리그에 입대해야 하는가. */
    fun shouldEnlist(player: Player, nextSeason: Int, overall: Double, random: Random): Boolean {
        if (player.origin == Origin.FOREIGN) return false
        if (player.military !is MilitaryStatus.Unfulfilled) return false
        val age = player.ageIn(nextSeason)
        if (age >= deadlineAge) return true
        // 자리가 없는 어린 선수는 일찍 다녀오는 편이 낫다
        return age >= deadlineAge - EARLY_WINDOW && overall < earlyEnlistOverall && random.chance(earlyEnlistChance)
    }

    /**
     * 상무 지원과 합격 판정.
     *
     * @param sangmuTaken 올해 이미 상무에 붙은 인원 (정원 관리)
     */
    fun enlist(player: Player, nextSeason: Int, overall: Double, sangmuTaken: Int, random: Random): Enlistment {
        val applies = true
        val slotOpen = sangmuTaken < sangmuSlots
        val passed = slotOpen && random.chance(interpolateAnchors(sangmuChance, overall))
        val kind = if (passed) ServiceKind.SANGMU else ServiceKind.ACTIVE_DUTY
        val (returnSeason, returnWeek) = returnPoint(nextSeason)
        return Enlistment(
            playerId = player.id,
            kind = kind,
            status = MilitaryStatus.Serving(kind, returnSeason, returnWeek),
            appliedToSangmu = applies,
        )
    }

    /** 기한이 다가온 선수 알림 (docs/12 "기한 임박 알림"). */
    fun warningFor(player: Player, season: Int): EnlistmentWarning? {
        if (player.military !is MilitaryStatus.Unfulfilled) return null
        val age = player.ageIn(season)
        return if (age in warningAges) EnlistmentWarning(player.id, age, deadlineAge) else null
    }

    /** 이번 주에 제대하는가 (docs/12 "시즌 중 제대 시 즉시 복귀"). */
    fun dischargesNow(player: Player, season: Int, week: Int): Boolean {
        val serving = player.military as? MilitaryStatus.Serving ?: return false
        return serving.returnSeason < season || (serving.returnSeason == season && serving.returnWeek <= week)
    }

    private companion object {
        const val MONTHS_PER_YEAR = 12
        /** 기한 몇 년 전부터 "일찍 입대"를 고려하는가 */
        const val EARLY_WINDOW = 4
    }
}
