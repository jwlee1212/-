package baseballgm.app

import baseballgm.io.LeagueLoader
import baseballgm.market.TradeVerdict
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import baseballgm.tools.ProjectFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 선수 프로필 요약 · 트레이드 교환대 반응 (2026-10-03 화면 개편) */
class ProfileTradeTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(ProjectFiles.loadTeamTemplates().season)))
    private fun session() = GameSession(balance, league, TeamId("SWR"), seed = 77L).also { repeat(3) { _ -> it.advanceWeek(delegate = true) } }

    @Test
    fun `요약의 강점·약점은 스카우트 범위 중심으로만 고른다 (불변 원칙 4)`() {
        val session = session()
        val other = session.league.teams.first { it.id != session.userTeamId }.id
        (session.roster(RosterLevel.FIRST_TEAM) + session.roster(RosterLevel.FIRST_TEAM, other)).forEach { player ->
            val summary = PlayerSummary.of(session, player)
            val centers = session.scout(player).ratings.mapValues { it.value.center }
            summary.strength?.let { assertEquals(centers.maxOf { it.value }, centers.getValue(it), "${player.registeredName} 강점은 범위 중심이 가장 높은 능력치") }
            summary.weakness?.let { assertEquals(centers.minOf { it.value }, centers.getValue(it)) }
            assertEquals(3, summary.keyNumbers.size)
            assertTrue(summary.sentence.contains("${session.scout(player).age}세"))
            if (!session.isOwn(player)) assertTrue(summary.sentence.contains("범위"), "타 팀 선수는 범위로 본다는 말이 있다")
        }
    }

    @Test
    fun `교환대 게이지 - 수락이면 꽉 차고, 규칙 위반이면 비고, 모자라면 마지막 칸은 비어 있다`() {
        val cells = balance.int("tradeGauge.cells")
        val yes = TradeReaction.of(TradeVerdict(true, "이득이 된다", 10.0, 5.0, 8.0), balance)
        assertEquals(ReactionLevel.YES, yes.level)
        assertEquals(cells, yes.filled)

        val blocked = TradeReaction.of(TradeVerdict(false, "규칙", 0.0, 0.0, 0.0, listOf("외국인 3명 초과")), balance)
        assertEquals(ReactionLevel.BLOCKED, blocked.level)
        assertEquals(0, blocked.filled)

        val close = TradeReaction.of(TradeVerdict(false, "조금 모자란다", 9.0, 5.0, 10.0), balance)
        assertEquals(ReactionLevel.CLOSE, close.level)
        assertTrue(close.filled in 1 until cells)

        val far = TradeReaction.of(TradeVerdict(false, "가치 차이가 크다", 1.0, 5.0, 10.0), balance)
        assertEquals(ReactionLevel.FAR, far.level)
        assertTrue(far.filled < close.filled)
    }

    @Test
    fun `교환대 반응은 실제 미리 보기 판정과 어긋나지 않는다`() {
        val session = session()
        val partner = session.league.teams.first { it.id != session.userTeamId }.id
        val give = session.roster(RosterLevel.FIRST_TEAM).filter { !it.isForeign }.take(2).map { it.id }
        val get = session.roster(RosterLevel.FIRST_TEAM, partner).filter { !it.isForeign }.take(1).map { it.id }
        val verdict = session.previewTrade(session.buildProposal(partner, give, get))
        val reaction = TradeReaction.of(verdict, balance)
        assertEquals(verdict.accepted, reaction.level == ReactionLevel.YES)
    }

    @Test
    fun `지명권을 얹으면 상대가 받는 값이 오르고, 성사되면 지명권 주인이 바뀐다`() {
        val session = session()
        val partner = session.league.teams.first { it.id != session.userTeamId }.id
        val give = session.roster(RosterLevel.FIRST_TEAM).filter { !it.isForeign }.take(1).map { it.id }
        val get = session.roster(RosterLevel.FIRST_TEAM, partner).filter { !it.isForeign }.take(1).map { it.id }
        val myPick = session.picksOf(session.userTeamId).first { it.round == 2 }

        val without = session.previewTrade(session.buildProposal(partner, give, get))
        val with = session.previewTrade(session.buildProposal(partner, give, get, givingPicks = listOf(myPick)))
        assertTrue(with.incomingValue > without.incomingValue, "지명권을 얹었는데 상대가 받는 값이 그대로다")

        // 지명권만 주고 상대 하위 선수 하나를 받는 거래 — 받아들여지면 지명권이 상대에게 간다
        val cheap = session.roster(RosterLevel.FUTURES, partner).filter { !it.isForeign }.minByOrNull { session.scout(it).overall.center }!!.id
        val proposal = session.buildProposal(partner, emptyList(), listOf(cheap), givingPicks = listOf(myPick))
        val verdict = session.proposeTrade(proposal)
        assertTrue(verdict.accepted, "2라운드 지명권 ↔ 상대 2군 최하위는 받아들여져야 한다: ${verdict.reason} ${verdict.problems}")
        assertTrue(session.picksOf(partner).any { it.season == myPick.season && it.round == myPick.round && it.originalTeam == myPick.originalTeam })
        assertTrue(session.picksOf(session.userTeamId).none { it.season == myPick.season && it.round == myPick.round && it.originalTeam == myPick.originalTeam })
        assertEquals(session.userTeamId, session.player(cheap).teamId)
    }

    @Test
    fun `상대 지명권을 요구할 수 있고 규칙에 걸리면 게이지가 이유를 보여 준다`() {
        val session = session()
        val partner = session.league.teams.first { it.id != session.userTeamId }.id
        val theirPick = session.picksOf(partner).first()
        val give = session.roster(RosterLevel.FIRST_TEAM).filter { !it.isForeign }.take(1).map { it.id }
        val verdict = session.previewTrade(session.buildProposal(partner, give, emptyList(), receivingPicks = listOf(theirPick)))
        val reaction = TradeReaction.of(verdict, balance)
        // 판정이 뭐든 게이지는 판정과 어긋나지 않는다 — 규칙 위반이면 이유가 힌트에 그대로
        if (verdict.problems.isNotEmpty()) {
            assertEquals(ReactionLevel.BLOCKED, reaction.level)
            assertEquals(verdict.problems.first(), reaction.hint)
        } else {
            assertEquals(verdict.accepted, reaction.level == ReactionLevel.YES)
        }
    }

    @Test
    fun `나이 필터는 스카우트 시선의 나이로 거르고 예전에 저장된 필터도 읽힌다`() {
        val session = session()
        val filter = baseballgm.app.ui.PlayerFilter(age = baseballgm.app.ui.AgeFilter.YOUNG)
        session.roster(RosterLevel.FIRST_TEAM).forEach { player ->
            val scouted = session.scout(player)
            assertEquals(scouted.age <= 25, filter.matches(scouted))
        }
        // 나이 칸이 생기기 전(다섯 칸)에 저장된 필터
        val restored = with(baseballgm.app.ui.PlayerFilter.Saver) {
            restore(listOf(1, 0, 2, 0, 0))
        }
        assertEquals(baseballgm.app.ui.AgeFilter.ALL, restored?.age)
        assertEquals(baseballgm.app.ui.PositionFilter.SP, restored?.position)
    }

    @Test
    fun `지명권 가치표 - 1순위가 가장 비싸고 순번이 밀릴수록 싸진다`() {
        val table = baseballgm.market.DraftPickValue(balance)
        val values = (1..110).map { table.ofOverallPick(it) }
        assertEquals(1.0, values.first())
        assertTrue(values.zipWithNext().all { (a, b) -> a >= b }, "뒤 순번이 앞 순번보다 비싸면 안 된다")
        // 1라운드(1~10순위) 평균이 2라운드(11~20)보다 확실히 크다 — 상위 지명권 프리미엄 (2026-10-03 재조정)
        assertTrue(values.subList(0, 10).average() > values.subList(10, 20).average() * 2)
    }
}
