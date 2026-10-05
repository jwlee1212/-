package baseballgm.app

import baseballgm.season.WeekReport
import baseballgm.util.ieyo

/** 한 주 동안 우리 팀 성적 */
data class WeekRecord(val wins: Int, val losses: Int, val ties: Int) {
    val games: Int get() = wins + losses + ties

    fun text(): String = "${wins}승 ${losses}패" + if (ties > 0) " ${ties}무" else ""
}

/** 브리핑의 결정 항목이 데려가는 곳 */
enum class BriefingTarget { INCIDENT, CAREER, MARKET, SCOUT, ROSTER, RECORDS, OFFSEASON }

/** 브리핑 "결정하실 것" 한 줄 */
data class BriefingItem(val text: String, val target: BriefingTarget, val urgent: Boolean = false)

/**
 * 비서 브리핑 문장 (docs/16 §1·4).
 *
 * 말투 규칙: 해요체, 호칭 "단장님", 나쁜 소식도 침착하게 · 좋은 소식도 과장 없이.
 * 문장만 만들고 그리지는 않는다 — 화면 없이 테스트할 수 있게 여기 둔다.
 */
object Briefing {

    fun weekRecord(session: GameSession, report: WeekReport): WeekRecord {
        val mine = report.games.filter { it.home.teamId == session.userTeamId || it.away.teamId == session.userTeamId }
        val ties = mine.count { it.tie }
        val wins = mine.count { it.winner == session.userTeamId }
        return WeekRecord(wins, mine.size - wins - ties, ties)
    }

    /** 홈 맨 위 한 줄 요약 */
    fun headline(session: GameSession): String {
        val rank = session.rank()
        val report = session.lastReport
        return when {
            session.unemployed -> "${session.gmName} 단장님, 새 자리를 알아봐야 할 때예요. 들어온 제안부터 볼까요?"
            session.pendingIncident != null -> session.pendingIncident!!.let {
                "${session.gmName} 단장님, 급히 정하실 게 생겼어요. ${it.whenLabel.ieyo()}."
            }
            session.weekPaused -> "정리됐어요. 남은 경기 이어서 진행하시면 돼요."
            session.inFreeAgency -> "FA 시장 ${session.faRound()}/${session.faRounds()}라운드예요. " + faNegotiationLine(session)
            session.seasonOver && session.postseasonDone -> "${session.league.season} 시즌이 끝났어요. 최종 ${rank}위였어요."
            session.seasonOver -> "정규시즌이 끝났어요. 최종 ${rank}위예요."
            report == null -> "개막 주예요. 준비는 다 돼 있어요."
            else -> {
                val week = weekRecord(session, report)
                "지난주는 ${week.text()}, 지금 ${rank}위예요." + streakComment(session.record().streak)
            }
        }
    }

    /** 우리 FA 협상을 한 줄로: 밀린 게 있으면 그것부터 */
    fun faNegotiationLine(session: GameSession): String {
        val mine = session.faMyNegotiations().map { it.second.standing }
        val outbid = mine.count { it == baseballgm.market.FaStanding.OUTBID || it == baseballgm.market.FaStanding.BELOW_FLOOR }
        val leading = mine.size - outbid
        return when {
            mine.isEmpty() -> "남은 FA는 ${session.faAgents().size}명이고, 아직 조건을 낸 선수는 없어요."
            outbid > 0 -> "협상 ${mine.size}건 중 ${outbid}건은 밀려 있거나 최저 연봉에 못 미쳐요. 조건을 다시 볼까요?"
            else -> "협상 ${leading}건 모두 우리가 1순위예요."
        }
    }

    private fun streakComment(streak: Int): String = when {
        streak >= STREAK_MENTION -> " ${streak}연승 중이고요."
        streak <= -STREAK_MENTION -> " ${-streak}연패지만, 아직 만회할 시간은 충분해요."
        else -> ""
    }

    /**
     * 유저가 직접 해야 할 결정. 비어 있으면 "진행만 누르면 된다".
     * 각 항목은 그 결정을 내리는 화면([BriefingTarget])으로 바로 데려간다 — 브리핑이 곧 결정의 입구다.
     */
    fun decisions(session: GameSession): List<BriefingItem> = buildList {
        session.pendingIncident?.let { incident ->
            val more = session.pendingIncidentCount - 1
            add(BriefingItem("${incident.kind.label} — ${incident.headline}" + if (more > 0) " 외 ${more}건" else "", BriefingTarget.INCIDENT, urgent = true))
        }
        if (session.unemployed) add(BriefingItem("이직 제안 고르기", BriefingTarget.CAREER, urgent = true))
        // 스토브리그 준비 (2026-10-01): 겨울 결정거리를 한 줄로
        if (session.offseasonPrep) {
            val preview = session.offseasonPreview()
            val parts = buildList {
                if (preview.foreign.isNotEmpty()) add("외국인 재계약 ${preview.foreign.size}명")
                if (preview.salaries.isNotEmpty()) add("연봉 재계약 ${preview.salaries.size}명")
                add("방출 명단")
            }
            add(BriefingItem("스토브리그 준비 — ${parts.joinToString(" · ")}", BriefingTarget.OFFSEASON, urgent = true))
        }
        if (session.inFreeAgency) add(BriefingItem("FA 조건 내기", BriefingTarget.MARKET))
        if (!session.seasonOver && session.isDraftWeek && !session.draftDone) {
            add(BriefingItem("신인 드래프트 지명", BriefingTarget.SCOUT, urgent = true))
        }
        val offer = session.pendingTradeOffer()
        if (offer != null && session.pendingIncident?.kind != baseballgm.events.IncidentKind.TRADE_OFFER) {
            val deadline = session.tradeOfferDeadlineWeek()?.let { " (${it}주차까지)" }.orEmpty()
            add(BriefingItem("${session.league.team(offer.proposer).name} 트레이드 제안 답하기$deadline", BriefingTarget.MARKET))
        }
        if (!session.seasonOver && !session.inFreeAgency && !session.unemployed) {
            val firstTeam = session.roster(baseballgm.model.RosterLevel.FIRST_TEAM)
            val injured = firstTeam.count { it.condition.isInjured }
            if (firstTeam.size < session.firstTeamLimit) {
                add(BriefingItem("1군 엔트리 ${firstTeam.size}/${session.firstTeamLimit} — 빈자리가 있어요", BriefingTarget.ROSTER))
            }
            if (injured > 0) add(BriefingItem("1군에 부상자 ${injured}명이 등록돼 있어요", BriefingTarget.ROSTER))
            // 선수에게 한 약속 (docs/13 만족도): 기한을 잊으면 크게 실망하니 비서가 챙긴다. 마지막 주면 급함
            session.state.playersOf(session.userTeamId).forEach { player ->
                val promise = session.state.morale[player.id]?.promise ?: return@forEach
                val left = promise.deadlineWeek - session.week
                val what = when (promise.kind) {
                    baseballgm.management.PromiseKind.PLAYING_TIME -> "${promise.deadlineWeek}주차까지 1군 등록"
                    baseballgm.management.PromiseKind.CONTRACT -> "정규시즌 끝까지 다년계약 (선수 상세 → 계약)"
                }
                add(BriefingItem("${player.registeredName}에게 한 약속 — $what", BriefingTarget.ROSTER, urgent = left <= 0))
            }
            // 포지션 제한은 없다(2026-10-01). 다만 뛸 수 있는 포수가 아예 없으면 비서가 알려는 준다 — 막지는 않는다
            if (session.slotCounts().none { it.label == "포수" && it.healthy > 0 }) {
                add(BriefingItem("1군에 뛸 수 있는 포수가 없어요 — 다른 선수가 마스크를 써요", BriefingTarget.ROSTER))
            }
        }
    }

    fun quietWeek(): String = "이번 주는 조용해요. 바로 진행하셔도 됩니다."

    /**
     * 결정은 아니지만 비서가 짚어 주는 것: 이번 주 라이벌전, 과거 결정의 메아리 (docs/13).
     */
    fun notes(session: GameSession): List<String> = buildList {
        val rivalGames = session.rivalGamesThisWeek()
        session.rival()?.takeIf { rivalGames > 0 && !session.seasonOver }?.let { rival ->
            add("이번 주엔 라이벌 ${rival.name}와 ${rivalGames}경기가 있어요. 팬들이 제일 신경 쓰는 경기예요.")
        }
        addAll(session.echoes())
        // 결정 성적표 한 줄 (재미 개선 2번): 최근 결정 중 판정이 난 것
        session.decisionReviewer.headline(session)?.let { add(it) }
    }

    fun welcome(session: GameSession): String =
        "반가워요, ${session.gmName} 단장님. 앞으로 매주 브리핑해 드릴 ${baseballgm.app.ui.Secretary.NAME}예요. " +
            "${session.userTeam.name}의 ${session.league.season} 시즌이 곧 시작돼요. " +
            "구단주님 바람은 '${session.userTeam.ownerGoal}'이고요. " +
            "제가 정리해 드리면 단장님은 결정만 하시고, 아래 진행 버튼을 누르시면 한 주가 흘러가요."

    /** 주간 브리핑 맨 위 한 줄 */
    fun weeklySummary(session: GameSession, report: WeekReport): String {
        val week = weekRecord(session, report)
        val tone = when {
            week.games == 0 -> "경기가 없는 주였어요."
            week.wins > week.losses -> "좋은 한 주였어요."
            week.wins < week.losses -> "조금 아쉬운 한 주였어요."
            else -> "반타작한 한 주였어요."
        }
        return "${report.week}주차는 ${week.text()}. $tone 지금 ${session.rank()}위예요."
    }

    private const val STREAK_MENTION = 3
}
