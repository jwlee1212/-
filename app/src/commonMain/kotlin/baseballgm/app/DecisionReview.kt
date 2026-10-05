package baseballgm.app

import baseballgm.events.BaselineRole
import baseballgm.events.DecisionBaseline
import baseballgm.events.DecisionFocus
import baseballgm.events.IncidentKind
import baseballgm.events.IncidentRecord
import baseballgm.events.PlayerBaseline
import baseballgm.io.BalanceConfig
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.RosterLevel
import baseballgm.util.fixed

/** 결정 판정. 색은 화면이 의미 색 토큰에서 고른다 (좋음/나쁨/중립) */
enum class Verdict(val label: String) {
    WORKED("통했어요"),
    MIXED("반반이에요"),
    MISSED("아쉬워요"),
    TOO_EARLY("지켜보는 중"),
}

/**
 * 결정 하나의 성적표 (2026-10-02 재미 개선 2번, 2026-10-03 "결정과 관련된 지표만" 개정).
 *
 * @param title "연패 · 선수단 미팅" (비서 처리면 끝에 표시)
 * @param main 판정의 근거가 된 숫자 한 줄 ("받은 김OO 이후 42타석 타율 .310 · OPS .842")
 * @param extras 같은 결정이 건드린 다른 것 ("보낸 박OO …", "구단주 신뢰 60 → 55"). 최대 2줄
 * @param gamesSince 결정한 뒤 우리 팀이 치른 경기 수
 */
data class DecisionReview(
    val record: IncidentRecord,
    val title: String,
    val verdict: Verdict,
    val main: String,
    val extras: List<String>,
    val gamesSince: Int,
)

/**
 * 결정 성적표 만들기. **게임 결과를 바꾸지 않는다** — 결정할 때 엔진이 찍어 둔 기준값([DecisionBaseline])과
 * 지금 값을 빼서 "그 뒤로 무엇이 바뀌었나"를 보여 줄 뿐이다. 판정 기준은 `balance.json` 의 `decisionReview`.
 *
 * **고른 선택이 건드린 것만 본다** (유저 요청 2026-10-03, docs/16 §4-1 표). 엔진이 기준값에 남긴 [DecisionFocus] 를 읽어
 * - 트레이드 수락 → 받은 선수 기록(판정) + 보낸 선수 기록 / 거절 → 지킨 선수(판정) + 놓친 선수
 * - 선수(콜업·영입·유망주·휴식·부상 투혼) → 그 선수 기록 (휴식은 피로, 부상 투혼은 재발, 유망주는 종합 변화)
 * - 연장 계약 → 그 선수 기록 + 계약 / 팀 폼 → 연패를 끊었나(연패 중이었으면) 또는 선수단 폼 / 팀 피로 → 평균 피로
 * - 이번 주 방침 → 라이벌 상대 전적 / 팬심·구단주 신뢰 → 그 수치
 * - 아무것도 안 함 → 이벤트 대상 선수의 그 뒤 기록 (2군에 남겼으면 2군 기록)
 * 팀 전체 승패를 일괄로 붙이지 않는다. 표본이 작으면(최소 경기·타석·아웃) "지켜보는 중".
 */
class DecisionReviewer(balance: BalanceConfig) {

    private val minGames = balance.int("decisionReview.minGames")
    private val minPa = balance.int("decisionReview.minPlateAppearances")
    private val minOuts = balance.int("decisionReview.minOuts")
    private val goodOps = balance.double("decisionReview.goodOps")
    private val badOps = balance.double("decisionReview.badOps")
    private val goodEra = balance.double("decisionReview.goodEra")
    private val badEra = balance.double("decisionReview.badEra")
    private val fatigueRecovered = balance.int("decisionReview.fatigueRecovered")
    private val fanDelta = balance.int("decisionReview.fanDelta")
    private val formDelta = balance.int("decisionReview.formDelta")
    val reviewWeeks: Int = balance.int("decisionReview.reviewWeeks")

    /** 기준값이 없는 옛 기록이면 null */
    fun review(session: GameSession, record: IncidentRecord): DecisionReview? {
        val base = record.baseline ?: return null
        val now = session.record()
        val games = (now.wins + now.losses + now.ties) - (base.wins + base.losses + base.ties)
        // 선택이 건드린 것이 없는 기준값(2026-10-02 판 기록)은 선수가 있으면 선수, 없으면 팬심으로 본다
        val focus = base.focus.ifEmpty { if (base.players.isNotEmpty()) listOf(DecisionFocus.PLAYER) else listOf(DecisionFocus.FAN) }
        val chosen = PRIORITY.firstOrNull { it in focus } ?: DecisionFocus.NOTHING
        // 라이벌 주간에 "평소대로"를 골라도 그 결정이 걸린 지표는 라이벌 상대 전적이다
        val primary = if (chosen == DecisionFocus.NOTHING && record.kind == IncidentKind.RIVAL_WEEK) DecisionFocus.POLICY else chosen

        val (verdict, main) = metric(session, record, base, primary, games)
        // 같은 선택이 건드린 다른 것 (트레이드의 반대편 선수, 팬심·신뢰 등)
        val extras = buildList {
            addAll(counterpart(session, record, base, primary))
            PRIORITY.filter { it in focus && it != primary }.forEach { other -> secondary(session, base, other)?.let { add(it) } }
        }.take(MAX_EXTRAS)
        return DecisionReview(
            record = record,
            title = "${record.kind.label} · ${record.choice}" + if (record.delegated) " (비서 처리)" else "",
            verdict = verdict,
            main = main,
            extras = extras,
            gamesSince = games,
        )
    }

    /** 최근 [reviewWeeks] 주의 결정 성적표 (최근 것부터) */
    fun recent(session: GameSession): List<DecisionReview> {
        val from = session.week - reviewWeeks
        // incidentLog() 는 최근 것부터다
        return session.incidentLog()
            .filter { it.season == session.league.season && it.week >= from }
            .mapNotNull { review(session, it) }
    }

    /**
     * 비서가 브리핑에서 꺼낼 한 줄: 최근 결정 중 판정이 난(통했거나 아쉬운) 가장 최근 것.
     * 나쁜 소식도 침착하게, 좋은 소식도 과장 없이 (docs/16 비서 말투).
     */
    fun headline(session: GameSession): String? {
        val pick = recent(session).firstOrNull { it.verdict == Verdict.WORKED || it.verdict == Verdict.MISSED } ?: return null
        val choice = "'${pick.record.choice}'"
        return when (pick.verdict) {
            Verdict.WORKED -> "$choice 결정은 통했어요. ${pick.main}."
            else -> "$choice 이후로는 ${pick.main}. 다음엔 다르게 가 봐도 좋겠어요."
        }
    }

    // ---------- 주 지표 ----------

    private fun metric(
        session: GameSession,
        record: IncidentRecord,
        base: DecisionBaseline,
        focus: DecisionFocus,
        games: Int,
    ): Pair<Verdict, String> = when (focus) {
        DecisionFocus.TRADE_ACCEPTED -> base.role(BaselineRole.ACQUIRED)?.let { playerMetric(session, it, "받은 ") }
            ?: (Verdict.TOO_EARLY to "거래가 성사되지 않았어요")

        DecisionFocus.TRADE_DECLINED -> base.role(BaselineRole.KEPT)?.let { kept ->
            val player = session.playerOrNull(kept.playerId)
            if (player != null && player.teamId != session.userTeamId) {
                Verdict.MIXED to "지킨 ${player.registeredName}: 결국 ${session.teamName(player)}로 갔어요"
            } else {
                playerMetric(session, kept, "지킨 ")
            }
        } ?: (Verdict.TOO_EARLY to "지켜볼 선수가 없어요")

        DecisionFocus.CONTRACT -> base.subject()?.let { playerMetric(session, it, "") } ?: (Verdict.TOO_EARLY to "지켜볼 선수가 없어요")

        DecisionFocus.PLAYER -> base.subject()?.let { subject ->
            when (record.kind) {
                IncidentKind.FATIGUE -> fatigueMetric(session, subject, games)
                IncidentKind.PLAY_THROUGH -> playerMetric(session, subject, "", relapse = true)
                else -> playerMetric(session, subject, "")
            }
        } ?: (Verdict.TOO_EARLY to "지켜볼 선수가 없어요")

        DecisionFocus.TEAM_FORM -> teamFormMetric(session, record, base, games)

        DecisionFocus.TEAM_FATIGUE -> {
            val now = session.firstTeamAverage { it.condition.fatigue }
            when {
                games < minGames -> Verdict.TOO_EARLY
                now <= base.teamFatigue -> Verdict.WORKED
                now - base.teamFatigue >= fatigueRecovered -> Verdict.MISSED
                else -> Verdict.MIXED
            } to "1군 평균 피로 ${base.teamFatigue} → $now"
        }

        DecisionFocus.POLICY -> {
            val rival = base.rival
            if (rival == null) {
                Verdict.TOO_EARLY to "라이벌 정보가 없어요"
            } else {
                val (w, l) = session.state.headToHeadOf(session.userTeamId, rival)
                val wins = w - base.rivalWins
                val losses = l - base.rivalLosses
                when {
                    wins + losses == 0 -> Verdict.TOO_EARLY
                    wins > losses -> Verdict.WORKED
                    wins < losses -> Verdict.MISSED
                    else -> Verdict.MIXED
                } to "라이벌 ${session.league.team(rival).nickname} 상대 이후 ${wins}승 ${losses}패"
            }
        }

        DecisionFocus.FAN -> deltaMetric("팬심", base.fanSupport, session.fanSupport())
        DecisionFocus.OWNER_TRUST -> deltaMetric("구단주 신뢰", base.ownerTrust, session.ownerTrust())

        DecisionFocus.NOTHING -> base.subject()?.let { subject ->
            if (record.kind == IncidentKind.FATIGUE) fatigueMetric(session, subject, games) else playerMetric(session, subject, "")
        } ?: (Verdict.MIXED to "따로 바뀐 건 없어요")
    }

    /** 반대편: 트레이드 수락이면 보낸 선수, 거절이면 놓친 선수, 연장 계약이면 계약 줄 (판정에는 안 쓴다) */
    private fun counterpart(session: GameSession, record: IncidentRecord, base: DecisionBaseline, primary: DecisionFocus): List<String> = when (primary) {
        DecisionFocus.TRADE_ACCEPTED -> base.players.filter { it.role == BaselineRole.DEPARTED }.map { playerMetric(session, it, "보낸 ").second }
        DecisionFocus.TRADE_DECLINED -> base.players.filter { it.role == BaselineRole.MISSED }.map { playerMetric(session, it, "놓친 ").second }
        DecisionFocus.CONTRACT -> base.subject()?.let { subject ->
            session.playerOrNull(subject.playerId)?.let { player ->
                listOf("계약 ${player.contract.yearsRemaining}년 남음 · 연봉 ${subject.salary.fixed(1)}억 → ${player.contract.salary.fixed(1)}억")
            }
        }.orEmpty()
        DecisionFocus.PLAYER -> base.subject()?.let { subject ->
            // 유망주 시험은 능력치가 오르는 결정이라 종합 변화를 함께 (우리 선수라 정확한 값이 보인다)
            val player = session.playerOrNull(subject.playerId)
            if (record.kind == IncidentKind.PROSPECT_TRIAL && player != null && subject.overall >= 0 && session.isOwn(player)) {
                listOf("${player.registeredName} 종합 ${subject.overall} → ${session.scout(player).overall}")
            } else {
                null
            }
        }.orEmpty()
        DecisionFocus.NOTHING -> if (record.kind == IncidentKind.EXTENSION) {
            // 연장을 미뤘다 → 계약이 어떻게 됐나
            base.subject()?.let { subject ->
                val player = session.playerOrNull(subject.playerId)
                listOf(
                    when {
                        player == null -> "리그를 떠났어요"
                        player.teamId != session.userTeamId -> "FA로 ${session.teamName(player)} 이적"
                        player.contract.yearsRemaining > subject.yearsRemaining -> "재계약 · 연봉 ${player.contract.salary.fixed(1)}억"
                        else -> "시즌 끝에 FA 예정"
                    },
                )
            }.orEmpty()
        } else {
            emptyList()
        }
        else -> emptyList()
    }

    /** 같은 선택이 건드린 다른 것 한 줄 */
    private fun secondary(session: GameSession, base: DecisionBaseline, focus: DecisionFocus): String? = when (focus) {
        DecisionFocus.FAN -> session.fanSupport().takeIf { it != base.fanSupport }?.let { "팬심 ${base.fanSupport} → $it" }
        DecisionFocus.OWNER_TRUST -> session.ownerTrust().takeIf { it != base.ownerTrust }?.let { "구단주 신뢰 ${base.ownerTrust} → $it" }
        DecisionFocus.TEAM_FATIGUE -> "1군 평균 피로 ${base.teamFatigue} → ${session.firstTeamAverage { it.condition.fatigue }}"
        DecisionFocus.TEAM_FORM -> "선수단 폼 ${base.teamForm} → ${session.firstTeamAverage { it.condition.form }}"
        else -> null
    }

    // ---------- 지표 계산 ----------

    /**
     * 선수 한 명의 그 뒤 기록. 1군 기록이 있거나 1군에 있으면 1군(타자 OPS / 투수 평균자책으로 판정), 2군에 있으면 2군 기록.
     * 다른 팀 선수(보낸·놓친 선수)는 이름 뒤에 지금 소속을 붙인다. 기록은 공개 정보라 그대로 보인다.
     * @param prefix "받은 " / "보낸 " 처럼 역할 머리말
     */
    private fun playerMetric(session: GameSession, base: PlayerBaseline, prefix: String, relapse: Boolean = false): Pair<Verdict, String> {
        val player = session.playerOrNull(base.playerId) ?: return Verdict.TOO_EARLY to "${prefix}선수는 리그를 떠났어요"
        val name = "$prefix${player.registeredName}"
        val where = if (player.teamId != null && player.teamId != session.userTeamId) "(${session.teamName(player)})" else ""
        val reinjured = relapse && player.condition.isInjured
        val injuryNote = if (reinjured) " · 부상 재발" else ""
        if (player is Pitcher) {
            val first = session.pitching(player.id) - base.pitching
            val futures = session.futuresPitching(player.id) - base.futuresPitching
            val (line, level) = if (first.outs > 0 || player.rosterLevel == RosterLevel.FIRST_TEAM) first to "" else futures to "2군 "
            if (line.outs == 0) return (if (reinjured) Verdict.MISSED else Verdict.TOO_EARLY) to "$name$where 이후 ${level}등판 없음$injuryNote"
            val text = "$name$where 이후 $level${inningsOf(line.outs)}이닝 평균자책 ${line.era.fixed(2)} · ${line.strikeouts}탈삼진$injuryNote"
            return when {
                reinjured -> Verdict.MISSED
                line.outs < minOuts -> Verdict.TOO_EARLY
                line.era <= goodEra -> Verdict.WORKED
                line.era >= badEra -> Verdict.MISSED
                else -> Verdict.MIXED
            } to text
        }
        val first = session.batting(player.id) - base.batting
        val futures = session.futuresBatting(player.id) - base.futuresBatting
        val (line, level) = if (first.plateAppearances > 0 || player.rosterLevel == RosterLevel.FIRST_TEAM) first to "" else futures to "2군 "
        if (line.plateAppearances == 0) return (if (reinjured) Verdict.MISSED else Verdict.TOO_EARLY) to "$name$where 이후 ${level}출전 없음$injuryNote"
        val text = buildString {
            append("$name$where 이후 $level${line.plateAppearances}타석 타율 ${line.battingAverage.fixed(3)} · OPS ${line.ops.fixed(3)}")
            if (line.homeRuns > 0) append(" · ${line.homeRuns}홈런")
            append(injuryNote)
        }
        return when {
            reinjured -> Verdict.MISSED
            line.plateAppearances < minPa -> Verdict.TOO_EARLY
            line.ops >= goodOps -> Verdict.WORKED
            line.ops < badOps -> Verdict.MISSED
            else -> Verdict.MIXED
        } to text
    }

    /** 쉬게 했으면 피로가 빠졌나, 그대로 썼으면 다쳤나 */
    private fun fatigueMetric(session: GameSession, base: PlayerBaseline, games: Int): Pair<Verdict, String> {
        val player = session.playerOrNull(base.playerId) ?: return Verdict.TOO_EARLY to "선수가 리그를 떠났어요"
        val now = player.condition.fatigue
        val hurt = player.condition.isInjured && !base.injured
        val text = "${player.registeredName} 피로 ${base.fatigue} → $now" + if (hurt) " · 부상" else ""
        return when {
            hurt -> Verdict.MISSED
            games < minGames -> Verdict.TOO_EARLY
            base.fatigue - now >= fatigueRecovered -> Verdict.WORKED
            now >= base.fatigue -> Verdict.MISSED
            else -> Verdict.MIXED
        } to text
    }

    /**
     * 팀 폼 결정(선수단 미팅·특타·신임 표명·분발 촉구·자신감 있는 인터뷰).
     * 연패 중에 내린 결정이면 "연패를 끊었나"가 그 결정이 노린 것이라 그것을 본다. 아니면 선수단 폼 변화.
     */
    private fun teamFormMetric(session: GameSession, record: IncidentRecord, base: DecisionBaseline, games: Int): Pair<Verdict, String> {
        val now = session.record()
        val wins = now.wins - base.wins
        val losses = now.losses - base.losses
        if (base.streak < 0 || record.kind == IncidentKind.LOSING_STREAK || record.kind == IncidentKind.MANAGER_HEAT) {
            if (games == 0) return Verdict.TOO_EARLY to "아직 경기가 없어요"
            val text = if (wins > 0) "연패 끊음 · 이후 ${wins}승 ${losses}패" else "연패 이어짐 · 이후 ${losses}패"
            return when {
                wins == 0 -> if (games >= minGames) Verdict.MISSED else Verdict.TOO_EARLY
                games < minGames -> Verdict.TOO_EARLY
                wins >= losses -> Verdict.WORKED
                else -> Verdict.MIXED
            } to text
        }
        val formNow = session.firstTeamAverage { it.condition.form }
        return when {
            games < minGames -> Verdict.TOO_EARLY
            formNow - base.teamForm >= formDelta -> Verdict.WORKED
            base.teamForm - formNow >= formDelta -> Verdict.MISSED
            else -> Verdict.MIXED
        } to "선수단 폼 ${base.teamForm} → $formNow"
    }

    private fun deltaMetric(label: String, before: Int, after: Int): Pair<Verdict, String> = when {
        after - before >= fanDelta -> Verdict.WORKED
        before - after >= fanDelta -> Verdict.MISSED
        else -> Verdict.MIXED
    } to "$label $before → $after"

    private fun inningsOf(outs: Int): String = "${outs / 3}" + when (outs % 3) {
        1 -> " 1/3"
        2 -> " 2/3"
        else -> ""
    }

    private fun DecisionBaseline.role(role: BaselineRole): PlayerBaseline? = players.firstOrNull { it.role == role }

    private fun DecisionBaseline.subject(): PlayerBaseline? = role(BaselineRole.SUBJECT)

    private fun GameSession.playerOrNull(id: baseballgm.model.PlayerId): Player? = runCatching { player(id) }.getOrNull()

    private fun GameSession.teamName(player: Player): String = player.teamId?.let { league.team(it).nickname } ?: "무소속"

    private fun GameSession.firstTeamAverage(value: (Player) -> Int): Int {
        val players = roster(RosterLevel.FIRST_TEAM)
        return if (players.isEmpty()) 0 else kotlin.math.round(players.map(value).average()).toInt()
    }

    private companion object {
        const val MAX_EXTRAS = 2

        /** 주 지표를 고르는 순서: 선수 거래 > 계약 > 선수 > 팀 폼 > 팀 피로 > 방침 > 팬심 > 신뢰 > 아무것도 안 함 */
        val PRIORITY = listOf(
            DecisionFocus.TRADE_ACCEPTED,
            DecisionFocus.TRADE_DECLINED,
            DecisionFocus.CONTRACT,
            DecisionFocus.PLAYER,
            DecisionFocus.TEAM_FORM,
            DecisionFocus.TEAM_FATIGUE,
            DecisionFocus.POLICY,
            DecisionFocus.FAN,
            DecisionFocus.OWNER_TRUST,
            DecisionFocus.NOTHING,
        )
    }
}
