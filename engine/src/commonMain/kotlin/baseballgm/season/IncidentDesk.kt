package baseballgm.season

import baseballgm.events.FanMood
import baseballgm.events.FanReaction
import baseballgm.events.Incident
import baseballgm.events.IncidentEffect
import baseballgm.events.IncidentKind
import baseballgm.events.IncidentOption
import baseballgm.io.BalanceConfig
import baseballgm.league.StrengthCalculator
import baseballgm.management.MemorySlot
import baseballgm.management.PromiseKind
import baseballgm.model.Batter
import baseballgm.model.InjurySeverity
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import baseballgm.stats.BoxScore
import baseballgm.tactics.WeeklyPolicy
import baseballgm.util.chance
import baseballgm.util.eulReul
import baseballgm.util.eunNeun
import baseballgm.util.fixed
import baseballgm.util.waGwa
import baseballgm.util.iGa
import kotlin.random.Random
import kotlin.math.roundToInt

/**
 * 돌발 이벤트 판정 (docs/07·16 주중 개입).
 *
 * 한 주에 두 번 들여다본다 — **주 시작**(경기 전: 트레이드 제안, 피로, 2군 폭격, 출장 요구, 라이벌 주간)과
 * **매일 경기 뒤**(주전 부상, 연패, 감독 거취, 기자 질문, SNS 논란). 걸리면 주간 진행이 그 자리에서 멈추고
 * 비서가 카드로 묻는다.
 *
 * 왜 이렇게 나눴나: 부상·연패는 "경기 결과의 결과"라 그날 경기 뒤에 알아야 하고, 피로·콜업 같은 준비성 결정은
 * 한 주를 시작하기 전에 해야 의미가 있다.
 *
 * **판정 난수는 주차·요일 시드로 따로 만든다.** 시즌 난수를 쓰면 "카드가 떴는지"가 다음 경기 결과를 바꾼다
 * (불변 원칙 2). 그래서 같은 결정을 하면 항상 같은 시즌이 나온다.
 *
 * 유저 구단에만 생긴다. AI 구단은 원래대로 자동 운영된다.
 */
class IncidentDesk(private val balance: BalanceConfig, private val strength: StrengthCalculator) {

    private val section = balance.section("incidents")
    private val potentialScale = baseballgm.scouting.PotentialScale.from(balance)
    private val maxPerWeek = section.int("maxPerWeek")
    private val coreBatters = section.int("coreBatters")
    private val corePitchers = section.int("corePitchers")
    private val injury = section.section("injury")
    private val injuryReturn = section.section("injuryReturn")
    private val streak = section.section("losingStreak")
    private val fatigue = section.section("fatigue")
    private val prospect = section.section("hotProspect")
    private val playingTime = section.section("playingTime")
    private val rivalWeek = section.section("rivalWeek")
    private val media = section.section("media")
    private val controversy = section.section("controversy")
    private val heat = section.section("managerHeat")
    private val roster = RosterActions(balance)
    private val firstTeamSize = balance.int("roster.firstTeamRegistered")
    private val opportunity = section.section("opportunity")
    private val tradeOffer = section.section("tradeOffer")
    private val trades = TradeService(balance, strength)
    private val valuation = baseballgm.market.Valuation(balance, baseballgm.development.AgingCurves(balance))
    private val faAskingPremium = balance.double("faMarket.askingPremium")
    private val minimumSalary = balance.double("minimumSalary.value")
    private val daysInWeek = Incident.DAY_NAMES.size
    private val morale = baseballgm.management.MoraleService(balance, strength)
    private val moraleCfg = balance.section("morale")
    private val extensionService = ContractExtensionService(balance, strength)
    private val war = baseballgm.stats.War(balance, baseballgm.stats.Sabermetrics(balance))

    fun randomFor(state: SeasonState, week: Int, day: Int?): Random =
        Random(state.league.seed * 104_729 + state.season * 7_907L + week * 211L + ((day ?: -1) + 2) * 17L)

    // ---------- 주 시작 ----------

    fun atWeekStart(state: SeasonState, team: TeamId, progress: WeekProgress): List<Incident> {
        val random = randomFor(state, progress.week, null)
        val found = mutableListOf<Incident>()
        // 부상 복귀는 한도와 상관없이 묻는다 — 엔트리 정리에서 빼 두었으니 묻지 않으면 2군에 남는다
        progress.heldReturns.mapNotNullTo(found) { returnIncident(state, team, progress.week, it) }
        tradeOffer(state, team, progress.week)?.let { found += it }

        val optional = listOfNotNull(
            fatigueAlert(state, team, progress.week, random),
            hotProspect(state, team, progress.week, random),
            playingTimeComplaint(state, team, progress.week, random),
            rivalWeek(state, team, progress.week, random),
        )
        // 선수 메시지 판정은 따로 만든 난수로 한다 — 예전 이벤트의 난수 순서를 건드리지 않는다 (2026-10-04)
        val playerRandom = Random(randomFor(state, progress.week, null).nextLong() xor PLAYER_SALT)
        val request = tradeRequest(state, team, progress.week, playerRandom)
        found += (optional + listOfNotNull(request)).take((maxPerWeek - progress.incidentCount).coerceAtLeast(0))
        // 기회형 (재미 개선 3번): 위기형 선택 이벤트도, 들어온 트레이드 제안도 없는 주에만 한 건.
        // 판정은 위기형을 다 본 **뒤에** 같은 난수로 하므로, 기회형이 생겨도 위기형 판정은 예전과 같다
        if (found.isEmpty()) opportunityIncident(state, team, progress.week, random)?.let { found += it }
        // 선수 메시지 (연봉 불만·멘토링): 그래도 빈 주에만 한 건 (docs/13 — 이벤트가 잔소리가 되지 않게)
        if (found.isEmpty()) playerMessage(state, team, progress.week, playerRandom)?.let { found += it }
        progress.incidentCount += found.size
        return found
    }

    // ---------- 경기 뒤 ----------

    fun afterDay(
        state: SeasonState,
        team: TeamId,
        progress: WeekProgress,
        day: Int,
        dayGames: List<BoxScore>,
    ): List<Incident> {
        val random = randomFor(state, progress.week, day)
        val found = mutableListOf<Incident>()

        // 부상과 연패는 한도와 상관없이 묻는다 — 경기 결과가 만든 일이라 피할 수 없다
        progress.injuries.filter { it.day == day && it.teamId == team }
            .mapNotNull { injuryIncident(state, team, progress.week, it) }
            .firstOrNull()?.let { found += it }
        streakIncident(state, team, progress.week, day)?.let { found += it }

        if (progress.incidentCount + found.size < maxPerWeek) {
            val mine = dayGames.firstOrNull { it.home.teamId == team || it.away.teamId == team }
            val optional = listOfNotNull(
                mine?.let { mediaQuestion(state, team, progress.week, day, it, random) },
                controversy(state, team, progress.week, day, random),
            )
            found += optional.take(maxPerWeek - progress.incidentCount - found.size)
        }
        progress.incidentCount += found.size
        return found
    }

    // ---------- 주전 ----------

    /** 주전 = 1군(부상자 포함) 야수 능력 상위 N + 투수 상위 N */
    fun coreOf(state: SeasonState, team: TeamId): Set<PlayerId> {
        val firstTeam = state.firstTeamOf(team)
        val batters = firstTeam.filter { it !is Pitcher }.sortedByDescending { strength.overallOf(it) }.take(coreBatters)
        val pitchers = firstTeam.filterIsInstance<Pitcher>().sortedByDescending { strength.overallOf(it) }.take(corePitchers)
        return (batters + pitchers).map { it.id }.toSet()
    }

    /**
     * 재활을 마치고 이번 주 올라올 수 있는 주전 (2026-10-05, 부상 복귀 이벤트).
     * [WeekLoop.beginWeek] 가 **엔트리 정리 전에** 불러 자동 복귀에서 빼 두고, [atWeekStart] 가 카드로 묻는다.
     *
     * 주전 = 지금 1군의 건강한 같은 쪽(야수/투수) 상위 N 에 들어갈 능력. 2군에 있으니 [coreOf] 로는 못 센다.
     * 한 주 미룬 선수는 그다음 주에 다시 묻지 않는다 — 그대로 자동 복귀한다.
     */
    fun returningCore(state: SeasonState, team: TeamId, week: Int): List<Player> {
        val healthy = state.firstTeamOf(team).filter { !it.condition.isInjured }
        return state.futuresOf(team).filter { player ->
            val ready = state.roster.rehabReadyAt(player.id) ?: return@filter false
            if (week < ready || roster.eligibilityProblem(state, player.id) != null) return@filter false
            if ("$RETURN_DELAYED:${state.season}:${player.id.value}:$week" in state.incidentKeys) return@filter false
            val size = if (player is Pitcher) corePitchers else coreBatters
            val cut = healthy.filter { (it is Pitcher) == (player is Pitcher) }
                .map { strength.overallOf(it) }.sortedDescending().getOrNull(size - 1)
            cut == null || strength.overallOf(player) >= cut
        }
    }

    // ---------- 판정 하나하나 ----------

    private fun returnIncident(state: SeasonState, team: TeamId, week: Int, playerId: PlayerId): Incident? {
        val player = runCatching { state.player(playerId) }.getOrNull() ?: return null
        // 그사이 트레이드로 떠났거나 엔트리가 바뀌었으면 묻지 않는다
        if (player.teamId != team || player.rosterLevel != RosterLevel.FUTURES || roster.eligibilityProblem(state, playerId) != null) return null
        val name = player.registeredName
        val role = roleOf(player)
        val firstTeam = state.firstTeamOf(team)
        val full = firstTeam.size >= firstTeamSize
        // 자리를 내줄 후보: 같은 포지션 최약체, 그리고 (다르면) 같은 투수/야수 쪽 최약체
        val sameSide = firstTeam.filter { (it is Pitcher) == (player is Pitcher) }
        val slot = RosterSlot.of(player)
        val victims = if (!full) {
            listOf<Player?>(null)
        } else {
            listOfNotNull(
                sameSide.filter { RosterSlot.of(it) == slot }.minByOrNull { strength.overallOf(it) },
                sameSide.minByOrNull { strength.overallOf(it) },
            ).distinctBy { it.id }
        }
        if (victims.isEmpty()) return null
        val relapse = player.condition.relapseRiskWeeks
        val cut = injuryReturn.int("tuneRelapseCutWeeks")
        val options = buildList {
            victims.forEachIndexed { index, victim ->
                val returnMemory = memory(player.id, MemorySlot.ROSTER, "calledUp", "부상에서 돌아오자마자 1군 자리를 받았다")
                add(
                    if (victim == null) {
                        IncidentOption(
                            id = "back",
                            label = "바로 1군 복귀",
                            detail = "1군에 빈자리가 있어서 아무도 안 내려가요. 오늘부터 엔트리에 있어요.",
                            recommended = true,
                            effects = listOf(IncidentEffect.Promote(player.id), returnMemory),
                            reactions = listOf(FanReaction(FanMood.POSITIVE, "$name 돌아왔다!! 이제 좀 숨통 트이겠네")),
                            followUp = "$name 선수 등록했어요. 본인도 많이 기다렸대요.",
                        )
                    } else {
                        val victimName = victim.registeredName
                        IncidentOption(
                            id = if (index == 0) "back" else "backAlt",
                            label = "바로 복귀 — ${victimName} 말소",
                            detail = "${victimName.iGa()} 2군으로 내려가요(${roleOf(victim)}, ${victim.ageIn(state.season)}세). " +
                                "${victimName.eunNeun()} ${roster.reRegisterWeekIfDemotedNow(state)}주차부터 다시 올릴 수 있어요.",
                            recommended = index == 0,
                            effects = listOf(IncidentEffect.Promote(player.id, swapOut = victim.id), returnMemory),
                            reactions = listOf(
                                FanReaction(FanMood.POSITIVE, "$name 복귀 ㄷㄷ 라인업 무게감 달라진다"),
                                FanReaction(FanMood.NEUTRAL, "${victimName}도 나름 잘 버텼는데 아쉽네"),
                            ),
                            followUp = "$name 선수 등록하고 $victimName 선수한테는 제가 따로 얘기했어요. 금방 다시 기회 올 거라고요.",
                        )
                    },
                )
            }
            add(
                IncidentOption(
                    id = "tune",
                    label = "2군에서 1주 더 조율",
                    detail = "이번 주는 2군에서 경기 감각을 찾아요. 재발 위험 기간이 ${cut}주 줄고, 다음 주 시작에 자동으로 올라와요.",
                    effects = listOf(IncidentEffect.DelayReturn(player.id, cut)),
                    reactions = listOf(
                        FanReaction(FanMood.NEUTRAL, "$name 복귀는 다음 주래. 확실하게 만들어서 와라"),
                        FanReaction(FanMood.NEGATIVE, "다 나았다며 왜 안 올림? 지금 한 경기가 급한데"),
                    ),
                    followUp = "2군 코치진에 전했어요. 다음 주엔 몸 확실히 만들어서 올라올 거예요.",
                ),
            )
        }
        val risk = if (relapse > 0) " 다만 앞으로 ${relapse}주는 재발 위험이 조금 남아 있어요." else ""
        return Incident(
            id = idOf(state, week, null, IncidentKind.INJURY_RETURN, player.id),
            season = state.season, week = week, day = null, teamId = team,
            kind = IncidentKind.INJURY_RETURN,
            headline = "$role $name 부상 복귀 준비 완료",
            message = "좋은 소식이에요. $name 선수가 재활까지 다 마쳤어요. 2군에서 몸 상태 확인했고 이번 주부터 바로 올릴 수 있어요.$risk" +
                if (full) " 1군이 꽉 차 있어서 한 명은 내려가야 해요." else "",
            options = options,
            playerId = player.id,
        )
    }


    private fun injuryIncident(state: SeasonState, team: TeamId, week: Int, event: InjuryEvent): Incident? {
        val player = state.player(event.playerId)
        if (player.teamId != team || player.rosterLevel != RosterLevel.FIRST_TEAM) return null
        if (event.playerId !in coreOf(state, team)) return null
        val hurt = player.condition.injury ?: return null
        val name = player.registeredName
        val role = roleOf(player)
        val day = Incident.DAY_NAMES[event.day]
        // 같은 포지션을 먼저, 그 안에서 능력 순 (포지션 범위를 지키는 후보만 나온다)
        val slot = RosterSlot.of(player)
        val replacements = roster.replacementsFor(state, team, player)
            .sortedWith(compareBy<Player> { if (RosterSlot.of(it) == slot) 0 else 1 }.thenByDescending { strength.overallOf(it) })
        val best = replacements.firstOrNull()

        if (hurt.severity == InjurySeverity.MINOR) {
            val penalty = injury.int("playThroughFormPenalty")
            val relapse = injury.int("playThroughRelapseWeeks")
            val options = buildList {
                add(
                    IncidentOption(
                        id = "rest",
                        label = "엔트리에 두고 쉬게 한다",
                        detail = "1군 자리는 그대로예요. 나을 때까지 벤치에 있고, 주 시작에 상태 보고 정리해요.",
                        recommended = hurt.weeksRemaining <= 1,
                        followUp = "알겠어요. 트레이너실에서 매일 상태 보고 올릴게요.",
                    ),
                )
                if (best != null) {
                    add(
                        IncidentOption(
                            id = "list",
                            label = "부상자 명단 → ${best.registeredName} 콜업",
                            detail = "$name 말소, 2군 ${best.registeredName}(${roleOf(best)}) 바로 등록. " +
                                "${name.eunNeun()} ${hurt.weeksRemaining}주 치료 + 재활 뒤에 돌아와요.",
                            recommended = hurt.weeksRemaining > 1,
                            effects = listOf(IncidentEffect.InjuredList(player.id), IncidentEffect.Promote(best.id)),
                            followUp = "${best.registeredName} 선수 오늘 밤 차 타고 올라와요. 내일부터 엔트리에 있어요.",
                        ),
                    )
                }
                add(
                    IncidentOption(
                        id = "play",
                        label = "본인 뜻대로 참고 뛰게 한다",
                        detail = "다음 경기부터 바로 나와요. 대신 폼이 $penalty 떨어지고, ${relapse}주 동안 다시 다칠 위험이 커져요.",
                        effects = listOf(
                            IncidentEffect.PlayThrough(player.id),
                            memory(player.id, MemorySlot.INJURY, "playThrough", "뛰겠다는 뜻을 존중받았다"),
                        ),
                        quote = "$name 선수가 뛰겠다고 했다. 의지를 믿는다",
                        reactions = listOf(
                            FanReaction(FanMood.POSITIVE, "$name 투혼 보소… 이게 프로지"),
                            FanReaction(FanMood.NEGATIVE, "아픈 애를 왜 내보내 시즌 길다고"),
                        ),
                        followUp = "본인이 원해서 테이핑하고 나가요. 저도 매 경기 체크할게요.",
                    ),
                )
            }
            return Incident(
                id = idOf(state, week, event.day, IncidentKind.PLAY_THROUGH, player.id),
                season = state.season, week = week, day = event.day, teamId = team,
                kind = IncidentKind.PLAY_THROUGH,
                headline = "$role $name ${hurt.part} 통증",
                message = "단장님, ${day}요일 경기에서 $name 선수가 ${hurt.part}를 만지면서 내려왔어요. " +
                    "검진 결과는 경미한 편이고 ${hurt.weeksRemaining}주 정도 보래요. 그런데 본인은 계속 뛰겠다고 하네요.",
                options = options,
                playerId = player.id,
            )
        }

        val young = replacements.filter { it.ageIn(state.season) <= YOUNG_AGE && it.id != best?.id }
            .maxByOrNull { strength.overallOf(it) }
        val severity = when (hurt.severity) {
            InjurySeverity.MODERATE -> "${hurt.weeksRemaining}주 진단"
            InjurySeverity.MAJOR -> "${hurt.weeksRemaining}주 진단, 꽤 길어요"
            else -> "시즌 아웃 진단이에요"
        }
        val options = buildList {
            if (best != null) {
                add(
                    IncidentOption(
                        id = "best",
                        label = "즉시 전력: ${best.registeredName} 콜업",
                        detail = "2군에서 지금 제일 나은 ${roleOf(best)} ${best.registeredName}" +
                            "(${best.ageIn(state.season)}세)을 올려요. ${name.eunNeun()} 부상자 명단으로.",
                        recommended = true,
                        effects = listOf(
                            IncidentEffect.InjuredList(player.id),
                            IncidentEffect.Promote(best.id),
                            memory(best.id, MemorySlot.ROSTER, "calledUp", "부상 공백에 1군 부름을 받았다"),
                        ),
                        reactions = listOf(FanReaction(FanMood.NEUTRAL, "${best.registeredName} 올라왔네. 일단 메우는 게 급하지")),
                        followUp = "${best.registeredName} 선수한테 연락했어요. 내일 경기부터 뛸 수 있어요.",
                    ),
                )
            }
            if (young != null) {
                add(
                    IncidentOption(
                        id = "young",
                        label = "기회를 준다: ${young.registeredName} 콜업",
                        detail = "${young.ageIn(state.season)}세 ${roleOf(young)} ${young.registeredName}에게 1군 기회를 줘요. " +
                            "당장은 조금 약해도 1군 경험은 성장에 도움이 돼요.",
                        effects = listOf(
                            IncidentEffect.InjuredList(player.id),
                            IncidentEffect.Promote(young.id),
                            memory(young.id, MemorySlot.ROSTER, "calledUp", "어린 나이에 1군 기회를 받았다"),
                        ),
                        reactions = listOf(
                            FanReaction(FanMood.POSITIVE, "드디어 ${young.registeredName} 보는구나 ㅠㅠ 단장 일한다"),
                            FanReaction(FanMood.NEGATIVE, "순위 싸움 중에 육성은 좀…"),
                        ),
                        followUp = "${young.registeredName} 선수 목소리가 떨리더라고요. 잘할 거예요.",
                    ),
                )
            }
            add(
                IncidentOption(
                    id = "hold",
                    label = "1군 안에서 버틴다",
                    detail = "엔트리는 손대지 않아요. 남은 경기는 있는 선수로 돌리고 주 시작에 자동으로 정리돼요.",
                    recommended = best == null,
                    followUp = "감독님께 그렇게 전할게요. 벤치 자원으로 버텨 볼게요.",
                ),
            )
        }
        return Incident(
            id = idOf(state, week, event.day, IncidentKind.INJURY_REPLACEMENT, player.id),
            season = state.season, week = week, day = event.day, teamId = team,
            kind = IncidentKind.INJURY_REPLACEMENT,
            headline = "$role $name ${hurt.part} 부상 이탈",
            message = "안 좋은 소식이에요. ${day}요일 경기에서 $name 선수가 ${hurt.part}를 다쳤고 $severity. " +
                "빈자리를 어떻게 메울지 정해 주세요.",
            options = options,
            playerId = player.id,
        )
    }

    private fun streakIncident(state: SeasonState, team: TeamId, week: Int, day: Int): Incident? {
        val current = state.standings.record(team).streak
        if (current >= 0) return null
        val losses = -current
        val trigger = streak.int("trigger")
        val heatAt = heat.int("losingStreak")
        val manager = state.league.managerOf(team)

        val heatDue = losses >= heatAt && (losses - heatAt) % streak.int("every") == 0
        if (heatDue && manager != null && state.incidentKeys.add("heat:${state.season}:$week:$losses")) {
            val name = manager.name
            return Incident(
                id = idOf(state, week, day, IncidentKind.MANAGER_HEAT, null),
                season = state.season, week = week, day = day, teamId = team,
                kind = IncidentKind.MANAGER_HEAT,
                headline = "${losses}연패 — 감독 거취 질문",
                message = "${losses}연패예요. 기자들이 ${name} 감독님 거취를 묻고 있어요. " +
                    "구단 입장을 오늘 안에 내야 할 것 같아요. 어떤 말이든 선수단도 같이 들어요.",
                options = listOf(
                    IncidentOption(
                        id = "back",
                        label = "감독 신임을 공개 표명",
                        detail = "선수단 폼 +${heat.int("backForm")}. 대신 구단주는 '책임지는 사람이 없다'며 신뢰 ${heat.int("backOwner")}.",
                        effects = listOf(
                            IncidentEffect.TeamForm(heat.int("backForm")),
                            IncidentEffect.OwnerTrust(heat.int("backOwner")),
                        ),
                        quote = "${name} 감독을 전적으로 신뢰한다. 흔들릴 이유가 없다",
                        reactions = listOf(
                            FanReaction(FanMood.NEGATIVE, "${losses}연패인데 신임?? 아무도 책임 안 지네"),
                            FanReaction(FanMood.NEUTRAL, "감독 바꾼다고 달라질 것도 없긴 함"),
                        ),
                        followUp = "보도자료 나갔어요. 감독님이 고맙다고 전해 달래요.",
                    ),
                    IncidentOption(
                        id = "warn",
                        label = "분발을 공개 촉구",
                        detail = "선수단 폼 +${heat.int("warnForm")}, 팬심 +${heat.int("warnFan")}. 현장과는 살짝 껄끄러워질 수 있어요.",
                        recommended = true,
                        effects = listOf(IncidentEffect.TeamForm(heat.int("warnForm")), IncidentEffect.Fan(heat.int("warnFan"))),
                        quote = "지금 성적은 받아들일 수 없다. 현장 모두가 결과로 답해야 한다",
                        reactions = listOf(
                            FanReaction(FanMood.POSITIVE, "단장이 할 말은 했네. 이제 선수들이 보여줘라"),
                            FanReaction(FanMood.NEUTRAL, "말은 쉽지… 보강이나 해줘"),
                        ),
                        followUp = "인터뷰 나갔어요. 선수단 분위기는 제가 따로 살필게요.",
                    ),
                    IncidentOption(
                        id = "silent",
                        label = "말을 아낀다",
                        detail = "아무 말도 하지 않아요. 팬심 ${heat.int("silentFan")} (무대응이 불안을 키워요).",
                        effects = listOf(IncidentEffect.Fan(heat.int("silentFan"))),
                        reactions = listOf(FanReaction(FanMood.NEGATIVE, "프런트는 뭐 하냐 입장문 하나 없네")),
                        followUp = "알겠어요. 질문은 제가 정중히 돌려 둘게요.",
                    ),
                ),
            )
        }

        if (losses < trigger) return null
        if (losses != trigger && (losses - trigger) % streak.int("every") != 0) return null
        if (!state.incidentKeys.add("streak:${state.season}:$week:$losses")) return null
        val meeting = streak.int("meetingForm")
        val practice = streak.int("practiceForm")
        val tired = streak.int("practiceFatigue")
        return Incident(
            id = idOf(state, week, day, IncidentKind.LOSING_STREAK, null),
            season = state.season, week = week, day = day, teamId = team,
            kind = IncidentKind.LOSING_STREAK,
            headline = "${losses}연패",
            message = "${Incident.DAY_NAMES[day]}요일까지 ${losses}연패예요. 라커룸이 조용하대요. " +
                "이럴 때 단장님이 한 번 움직이면 분위기가 바뀌기도 해요.",
            options = listOf(
                IncidentOption(
                    id = "meeting",
                    label = "선수단 미팅을 소집한다",
                    detail = "단장님이 직접 라커룸에 가요. 1군 전원 폼 +$meeting.",
                    recommended = true,
                    effects = listOf(IncidentEffect.TeamForm(meeting)),
                    reactions = listOf(FanReaction(FanMood.NEUTRAL, "단장이 라커룸 갔다던데 이번엔 좀 달라지려나")),
                    followUp = "미팅 끝났어요. 주장이 '다시 해보자'고 했대요.",
                ),
                IncidentOption(
                    id = "practice",
                    label = "특타·특별 훈련을 지시한다",
                    detail = "1군 전원 폼 +$practice, 대신 피로 +$tired. 남은 경기에 몸이 무거울 수 있어요.",
                    effects = listOf(IncidentEffect.TeamForm(practice), IncidentEffect.TeamFatigue(tired)),
                    reactions = listOf(
                        FanReaction(FanMood.POSITIVE, "경기 끝나고 특타 했다는데 그래 이 정도 독기는 있어야지"),
                        FanReaction(FanMood.NEGATIVE, "연패 중에 특훈은 오히려 독일 듯"),
                    ),
                    followUp = "코치진이 오늘 밤 특타 잡았어요. 다들 이 악물었대요.",
                ),
                IncidentOption(
                    id = "wait",
                    label = "흐름이 바뀌길 기다린다",
                    detail = "아무것도 하지 않아요. 연패는 언젠가 끊겨요.",
                    reactions = listOf(FanReaction(FanMood.NEGATIVE, "${losses}연패인데 프런트는 조용하네")),
                    followUp = "네, 길게 보죠. 제가 분위기는 계속 체크할게요.",
                ),
            ),
        )
    }

    private fun mediaQuestion(
        state: SeasonState,
        team: TeamId,
        week: Int,
        day: Int,
        game: BoxScore,
        random: Random,
    ): Incident? {
        val ours = if (game.home.teamId == team) game.homeScore else game.awayScore
        val theirs = if (game.home.teamId == team) game.awayScore else game.homeScore
        val opponentId = if (game.home.teamId == team) game.away.teamId else game.home.teamId
        val opponent = state.league.team(opponentId).name
        val record = state.standings.record(team)
        val rival = state.league.team(team).rival == opponentId
        val topic = when {
            game.walkOff && game.winner == opponentId -> "${opponent}전 끝내기 패배"
            theirs - ours >= media.int("blowoutMargin") -> "${opponent}에 $ours-$theirs 대패"
            rival && game.winner == opponentId -> "라이벌 ${opponent}에 진 경기"
            record.streak == media.int("winStreak") -> "${record.streak}연승 상승세"
            else -> return null
        }
        if (!cooledDown(state, "media", week, media.int("cooldownWeeks"))) return null
        if (!random.chance(media.double("chance"))) return null
        state.incidentKeys += "media@$week"
        val good = record.streak > 0 && game.winner == team
        val rivalLoss = rival && game.winner == opponentId
        val humble = media.int("humbleFan")
        val (humbleQuote, confidentQuote) = when {
            good -> "들뜰 때가 아니다. 아직 갈 길이 멀다" to "지금 리그에서 우리가 제일 강하다"
            rivalLoss -> "$opponent 팬들께 축하를, 우리 팬들께는 사과를 드린다" to "다음 $opponent 전에서 반드시 갚아 주겠다"
            else -> "팬들께 죄송하다. 반드시 바로잡겠다" to "선수들을 믿는다. 곧 제자리로 돌아온다"
        }
        return Incident(
            id = idOf(state, week, day, IncidentKind.MEDIA, null),
            season = state.season, week = week, day = day, teamId = team,
            kind = IncidentKind.MEDIA,
            headline = "$topic 뒤 기자 질문",
            message = "${baseballgm.events.NewsDesk.OUTLETS[random.nextInt(baseballgm.events.NewsDesk.OUTLETS.size)]} 기자가 " +
                "\"$topic\" 얘기로 단장님 코멘트를 원해요. 짧게 한마디면 돼요. 어떻게 답할까요?",
            options = listOf(
                IncidentOption(
                    id = "humble",
                    label = "\"${humbleQuote.substringBefore(".")}\"",
                    detail = "겸손하게 받아요. 팬심 +$humble.",
                    recommended = true,
                    effects = listOf(IncidentEffect.Fan(humble)),
                    quote = humbleQuote,
                    reactions = listOf(
                        FanReaction(FanMood.NEUTRAL, "단장 인터뷰 봤는데 말은 진정성 있더라"),
                        FanReaction(FanMood.POSITIVE, "그래도 단장이 고개 숙일 줄은 아네"),
                        FanReaction(FanMood.NEUTRAL, "사과는 됐고 다음 경기 이기면 됨"),
                        FanReaction(FanMood.NEGATIVE, "인터뷰는 매번 똑같음. 바뀌는 게 없잖아"),
                    ),
                    followUp = "기사 나갔어요. 반응 괜찮아요.",
                ),
                IncidentOption(
                    id = "confident",
                    label = "\"${confidentQuote.substringBefore(".")}\"",
                    detail = "자신감을 보여요. 1군 폼 +${media.int("confidentForm")}, 팬심 +${media.int("confidentFan")}. " +
                        "구단주는 큰소리를 싫어해요(신뢰 ${media.int("confidentOwner")}).",
                    effects = listOf(
                        IncidentEffect.TeamForm(media.int("confidentForm")),
                        IncidentEffect.Fan(media.int("confidentFan")),
                        IncidentEffect.OwnerTrust(media.int("confidentOwner")),
                    ),
                    quote = confidentQuote,
                    reactions = listOf(
                        FanReaction(FanMood.POSITIVE, "단장 자신감 좋다 ㅋㅋ 그 말 책임져라"),
                        FanReaction(FanMood.NEGATIVE, "말만 번지르르… 결과로 보여줘"),
                        FanReaction(FanMood.POSITIVE, "이 인터뷰 캡처해둠. 시즌 끝나고 다시 보자"),
                        FanReaction(FanMood.NEGATIVE, "단장 발언 기사 댓글 난리 났던데 ㅋㅋ"),
                    ),
                    followUp = "헤드라인 꽤 세게 뽑혔어요. 선수들은 좋아하던데요.",
                ),
                IncidentOption(
                    id = "deflect",
                    label = "\"현장 일은 현장에 맡긴다\"",
                    detail = "말을 아껴요. 팬심 ${media.int("deflectFan")}, 구단주 신뢰 +${media.int("deflectOwner")}.",
                    effects = listOf(
                        IncidentEffect.Fan(media.int("deflectFan")),
                        IncidentEffect.OwnerTrust(media.int("deflectOwner")),
                    ),
                    quote = "현장 일은 현장에 맡긴다. 프런트는 지원할 뿐",
                    reactions = listOf(
                        FanReaction(FanMood.NEGATIVE, "단장 코멘트 성의 없다 진짜"),
                        FanReaction(FanMood.NEUTRAL, "프런트가 말 아끼는 건 차라리 낫다"),
                        FanReaction(FanMood.NEGATIVE, "책임은 현장이 지고 프런트는 뒤에 숨고?"),
                    ),
                    followUp = "네, 짧게 전했어요. 구단주님 쪽은 조용히 넘어가는 걸 좋아하세요.",
                ),
            ),
        )
    }

    private fun controversy(state: SeasonState, team: TeamId, week: Int, day: Int, random: Random): Incident? {
        if (!random.chance(controversy.double("chancePerWeek") / daysInWeek)) return null
        val candidates = state.firstTeamOf(team).filter { !it.condition.isInjured }
        if (candidates.isEmpty()) return null
        val player = candidates[random.nextInt(candidates.size)]
        if (!state.incidentKeys.add("controversy:${state.season}:${player.id.value}")) return null
        val name = player.registeredName
        val story = CONTROVERSIES[random.nextInt(CONTROVERSIES.size)]
        val canSuspend = roster.demoteProblem(state, team, player.id) == null
        val options = buildList {
            if (canSuspend) {
                add(
                    IncidentOption(
                        id = "suspend",
                        label = "구단 자체 징계 — 1군 말소",
                        detail = "단호하게 대응해요. 팬심 +${controversy.int("suspendFan")}. ${name.eunNeun()} " +
                            "${roster.reRegisterWeekIfDemotedNow(state)}주차까지 1군에 못 올라와요.",
                        effects = listOf(
                            IncidentEffect.Demote(player.id),
                            IncidentEffect.Fan(controversy.int("suspendFan")),
                            memory(player.id, MemorySlot.CONTROVERSY, "suspended", "논란 뒤 구단 징계를 받았다"),
                        ),
                        quote = "$name 선수에게 구단 차원의 징계를 내렸다. 재발 방지에 힘쓰겠다",
                        reactions = listOf(
                            FanReaction(FanMood.POSITIVE, "구단 대처 빠르네. 이래야지"),
                            FanReaction(FanMood.NEGATIVE, "이걸로 말소까지? 전력 손실 어쩔"),
                        ),
                        followUp = "징계 발표했어요. $name 선수는 2군에서 반성문부터 쓴대요.",
                    ),
                )
            }
            add(
                IncidentOption(
                    id = "apology",
                    label = "공개 사과와 벌금",
                    detail = "선수가 사과문을 올려요. 팬심 +${controversy.int("apologyFan")}, $name 폼 ${controversy.int("apologyForm")}.",
                    recommended = true,
                    effects = listOf(
                        IncidentEffect.Fan(controversy.int("apologyFan")),
                        IncidentEffect.PlayerForm(player.id, controversy.int("apologyForm")),
                        memory(player.id, MemorySlot.CONTROVERSY, "apologized", "논란 뒤 공개 사과를 했다"),
                    ),
                    quote = "$name 선수가 깊이 반성하고 있다. 내부 규정에 따라 벌금을 부과했다",
                    reactions = listOf(FanReaction(FanMood.NEUTRAL, "사과문 올라왔네. 야구로 갚아라")),
                    followUp = "사과문 올라갔어요. 본인도 많이 반성하고 있어요.",
                ),
            )
            add(
                IncidentOption(
                    id = "defend",
                    label = "선수를 감싼다",
                    detail = "\"오해가 있었다\"고 입장을 내요. 팬심 ${controversy.int("defendFan")}, $name 폼 +${controversy.int("defendForm")}.",
                    effects = listOf(
                        IncidentEffect.Fan(controversy.int("defendFan")),
                        IncidentEffect.PlayerForm(player.id, controversy.int("defendForm")),
                        memory(player.id, MemorySlot.CONTROVERSY, "defended", "논란 때 구단이 감싸 줬다"),
                    ),
                    quote = "와전된 부분이 있다. 선수를 믿는다",
                    reactions = listOf(
                        FanReaction(FanMood.NEGATIVE, "구단이 쉴드 치는 거 보고 정 떨어짐"),
                        FanReaction(FanMood.POSITIVE, "솔직히 이게 이렇게까지 난리 날 일인가"),
                    ),
                    followUp = "입장문 냈어요. 선수가 고맙다고 하더라고요. 여론은 좀 지켜봐야 해요.",
                ),
            )
        }
        return Incident(
            id = idOf(state, week, day, IncidentKind.CONTROVERSY, player.id),
            season = state.season, week = week, day = day, teamId = team,
            kind = IncidentKind.CONTROVERSY,
            headline = "$name $story",
            message = "단장님, 지금 커뮤니티가 시끄러워요. $name 선수 $story 이 퍼지고 있어요. " +
                "기사화되기 전에 구단 입장을 정해야 해요.",
            options = options,
            playerId = player.id,
        )
    }

    private fun tradeOffer(state: SeasonState, team: TeamId, week: Int): Incident? {
        val offer = state.pendingTradeOffer ?: return null
        if (state.pendingTradeOfferWeek != week || offer.partner != team) return null
        val partner = state.league.team(offer.proposer).name
        val nameOf = { id: PlayerId -> runCatching { state.player(id).registeredName }.getOrDefault("?") }
        val give = offer.fromPartner.describe(nameOf)
        val get = offer.fromProposer.describe(nameOf)
        return Incident(
            id = idOf(state, week, null, IncidentKind.TRADE_OFFER, null),
            season = state.season, week = week, day = null, teamId = team,
            kind = IncidentKind.TRADE_OFFER,
            headline = "$partner 트레이드 제안",
            message = "$partner 단장님한테서 전화가 왔어요. ${offerMotive(state, offer)}" +
                "우리 쪽 ${give.eulReul()} 원하고, ${get.eulReul()} 주겠대요. ${offerFairness(state, offer, team)}" +
                "이번 주 안에 답을 달래요. 다음 주엔 제안이 사라져요.",
            options = listOf(
                IncidentOption(
                    id = "accept",
                    label = "수락한다",
                    detail = "바로 성사돼요. 데려오는 선수는 2군으로 먼저 합류해요.",
                    effects = listOf(IncidentEffect.AcceptTrade),
                    quote = "필요한 부분을 채운 거래다. 양쪽 모두 만족한다",
                    reactions = listOf(
                        FanReaction(FanMood.POSITIVE, "트레이드 떴다!! 이건 이득 같은데"),
                        FanReaction(FanMood.NEGATIVE, "$give 보낸다고? 단장 뭐 하냐"),
                    ),
                    followUp = "계약서 오갔어요. 트레이드 공식 발표 나갑니다.",
                ),
                IncidentOption(
                    id = "reject",
                    label = "정중히 거절한다",
                    detail = "없던 일로 해요.",
                    effects = listOf(IncidentEffect.RejectTrade),
                    followUp = "거절 의사 전했어요. 그쪽도 이해한대요.",
                ),
                IncidentOption(
                    id = "think",
                    label = "시장 탭에서 더 따져 본다",
                    detail = "답을 미뤄요. 이번 주 안에 시장 탭에서 수락·거절할 수 있어요. 안 하면 다음 주에 철회돼요.",
                    recommended = true,
                    followUp = "네, 이번 주 안에만 답 주시면 돼요. 시장 탭에 올려 둘게요.",
                ),
            ),
        )
    }

    /** 상대가 왜 이 제안을 거는지 (비서 설명). 이유가 없는 옛 세이브 제안이면 빈 문자열 */
    private fun offerMotive(state: SeasonState, offer: baseballgm.market.TradeProposal): String {
        val reason = offer.reason ?: return ""
        val player = runCatching { state.player(reason.playerId) }.getOrNull() ?: return ""
        val name = player.registeredName
        return when (reason.motive) {
            baseballgm.market.TradeMotive.NEED ->
                "그쪽 ${roleOf(player)} 자리가 약해서 급한가 봐요." + (if (reason.contending) " 올해 승부를 걸겠대요. " else " ")
            baseballgm.market.TradeMotive.SELL ->
                "그쪽이 리빌딩에 들어가서 ${roleOf(player)} $name 선수를 정리하려나 봐요. 대신 유망주나 지명권을 원해요. "
        }
    }

    /** 우리 스카우트 시선으로 본 손익 한마디. */
    private fun offerFairness(state: SeasonState, offer: baseballgm.market.TradeProposal, team: TeamId): String {
        val ratio = trades.ai.fairnessFor(state.currentLeague(), state.standings, offer, team)
        return when {
            ratio >= tradeOffer.double("goodDealRatio") -> "우리 계산으론 꽤 남는 거래예요. "
            ratio >= tradeOffer.double("fairDealRatio") -> "우리 계산으론 거의 공정해요. "
            else -> "우리 계산으론 조금 손해예요. 그쪽이 그만큼 급하진 않다는 거죠. "
        }
    }

    private fun fatigueAlert(state: SeasonState, team: TeamId, week: Int, random: Random): Incident? {
        val core = coreOf(state, team)
        val tired = state.firstTeamOf(team)
            .filter { it.id in core && !it.condition.isInjured && it.condition.fatigue >= fatigue.int("threshold") }
            .filter { player -> cooledDown(state, "fatigue:${player.id.value}", week, fatigue.int("cooldownWeeks")) }
            .maxByOrNull { it.condition.fatigue } ?: return null
        if (!random.chance(fatigue.double("chance"))) return null
        state.incidentKeys += "fatigue:${tired.id.value}@$week"
        val name = tired.registeredName
        return Incident(
            id = idOf(state, week, null, IncidentKind.FATIGUE, tired.id),
            season = state.season, week = week, day = null, teamId = team,
            kind = IncidentKind.FATIGUE,
            headline = "${roleOf(tired)} $name 피로 누적",
            message = "트레이닝 파트 보고예요. ${roleOf(tired)} $name 선수 피로도가 ${tired.condition.fatigue}까지 올라왔어요. " +
                "이대로 계속 쓰면 다칠 확률이 꽤 올라가요.",
            options = listOf(
                IncidentOption(
                    id = "rest",
                    label = "이번 주는 쉬게 한다",
                    detail = "이번 주 경기에서 빼요. 엔트리는 그대로고, 다음 주엔 가벼운 몸으로 돌아와요.",
                    recommended = true,
                    effects = listOf(IncidentEffect.Rest(tired.id)),
                    followUp = "감독님께 전했어요. $name 선수는 이번 주 치료와 회복에 집중해요.",
                ),
                IncidentOption(
                    id = "push",
                    label = "그래도 쓴다",
                    detail = "평소처럼 써요. 감독이 그날그날 상태를 보고 뺄 수는 있어요.",
                    reactions = listOf(FanReaction(FanMood.NEGATIVE, "$name 요즘 너무 굴리는 거 아님? 저러다 다친다")),
                    followUp = "알겠어요. 트레이너가 매일 체크할게요.",
                ),
            ),
            playerId = tired.id,
        )
    }

    private fun hotProspect(state: SeasonState, team: TeamId, week: Int, random: Random): Incident? {
        val hot = state.futuresOf(team).filter { player ->
            if (state.incidentKeys.contains("prospect:${state.season}:${player.id.value}")) return@filter false
            if (roster.eligibilityProblem(state, player.id) != null) return@filter false
            when (player) {
                is Batter -> state.stats.futuresBattingOf(player.id).let {
                    it.plateAppearances >= prospect.int("batterPa") && it.ops >= prospect.double("batterOps")
                }
                is Pitcher -> state.stats.futuresPitchingOf(player.id).let {
                    it.outs >= prospect.int("pitcherOuts") && it.era <= prospect.double("pitcherEra")
                }
            }
        }.maxByOrNull { strength.overallOf(it) } ?: return null
        if (!random.chance(prospect.double("chance"))) return null
        state.incidentKeys += "prospect:${state.season}:${hot.id.value}"
        return callUpIncident(
            state, team, week, hot, IncidentKind.HOT_PROSPECT,
            headline = "2군 ${hot.registeredName} 폭격 중",
            message = "2군 감독님 전화예요. ${hot.registeredName} 선수가 ${futuresLine(state, hot)} 이래요. " +
                "\"이제 1군에서 볼 때가 됐다\"고 하시네요.",
            declineLabel = "더 지켜본다",
            declineEffects = listOf(memory(hot.id, MemorySlot.ROSTER, "keptDown", "2군에서 잘했는데 콜업이 없었다")),
            declineDetail = "2군에서 계속 뛰게 해요. 본인은 좀 실망할 수 있어요.",
            declineReactions = listOf(FanReaction(FanMood.NEGATIVE, "${hot.registeredName} 2군에서 저러는데 왜 안 올림??")),
            recommendCallUp = true,
            callUpMemory = memory(hot.id, MemorySlot.ROSTER, "calledUp", "2군 활약 끝에 1군에 올라왔다"),
        )
    }

    /**
     * 출장 요구 (2026-10-04 선수 메시지로 개편): 2군의 고액 베테랑이 단장에게 직접 말한다.
     * 올려 주거나, N주 안에 올리겠다고 **약속**하거나(지키면 +, 못 지키면 크게 −), 선을 긋는다.
     */
    private fun playingTimeComplaint(state: SeasonState, team: TeamId, week: Int, random: Random): Incident? {
        val veteran = state.futuresOf(team).filter {
            it.contract.salary >= playingTime.double("minSalary") &&
                it.ageIn(state.season) >= playingTime.int("minAge") &&
                !state.incidentKeys.contains("playing:${state.season}:${it.id.value}") &&
                roster.eligibilityProblem(state, it.id) == null
        }.maxByOrNull { it.contract.salary } ?: return null
        if (!random.chance(playingTime.double("chance"))) return null
        state.incidentKeys += "playing:${state.season}:${veteran.id.value}"
        val refuse = playingTime.int("refuseFormPenalty")
        val name = veteran.registeredName
        val deadline = promiseDeadline(state, week)
        return callUpIncident(
            state, team, week, veteran, IncidentKind.PLAYING_TIME,
            headline = "$name 면담 요청",
            message = "단장님, ${veteran.ageIn(state.season)}살에 2군에만 있으려니 솔직히 속이 상합니다. " +
                "아직 1군에서 할 수 있다고 생각해요. 기회를 주시든지, 아니면 길을 열어 주셨으면 합니다.",
            declineLabel = "${deadline}주차까지 올리겠다고 약속한다",
            declineEffects = listOf(
                IncidentEffect.Promise(veteran.id, PromiseKind.PLAYING_TIME, deadline),
                memory(veteran.id, MemorySlot.PROMISE, "promiseMade", "1군 기회를 약속받았다"),
            ),
            declineDetail = "지금 엔트리는 그대로예요. ${deadline}주차가 끝날 때까지 1군에 올리면 약속을 지킨 거고, " +
                "못 올리면 크게 실망해요.",
            declineReactions = listOf(FanReaction(FanMood.NEUTRAL, "$name 2군 생활 길어지네…")),
            // 약속은 단장이 직접 지켜야 하는 일이라 비서 추천에서 뺀다 (맡기면 올린다)
            recommendCallUp = true,
            callUpMemory = memory(veteran.id, MemorySlot.ROSTER, "calledUp", "요청대로 1군에 올라왔다"),
            extra = IncidentOption(
                id = "refuse",
                label = "단호하게 선을 긋는다",
                detail = "\"실력으로 올라와라.\" $name 폼 -$refuse, 팬심 ${playingTime.int("refuseFan")}, 만족도가 크게 떨어져요.",
                effects = listOf(
                    IncidentEffect.PlayerForm(veteran.id, -refuse),
                    IncidentEffect.Fan(playingTime.int("refuseFan")),
                    memory(veteran.id, MemorySlot.ROSTER, "refusedPlayingTime", "출장 요구를 단칼에 거절당했다"),
                ),
                quote = "1군 자리는 이름값이 아니라 실력으로 얻는 것이다",
                reactions = listOf(
                    FanReaction(FanMood.NEGATIVE, "$name 대우가 이게 맞냐… 프랜차이즈를"),
                    FanReaction(FanMood.POSITIVE, "단장 원칙 있네. 실력대로 가자"),
                ),
                followUp = "분위기가 좀 싸했어요. 에이전트가 트레이드 얘기도 꺼내더라고요.",
            ),
        )
    }

    /** 2군 선수를 올릴까 묻는 공통 모양. 1군이 꽉 찼으면 같은 쪽(투수/야수) 최하위를 내린다 */
    private fun callUpIncident(
        state: SeasonState,
        team: TeamId,
        week: Int,
        player: Player,
        kind: IncidentKind,
        headline: String,
        message: String,
        declineLabel: String,
        declineEffects: List<IncidentEffect>,
        declineDetail: String,
        declineReactions: List<FanReaction>,
        recommendCallUp: Boolean,
        extra: IncidentOption? = null,
        callUpMemory: IncidentEffect.Memory? = null,
    ): Incident? {
        // 꽉 찼거나, 자리는 있어도 그 포지션이 최대라면 누군가를 내려야 한다
        val full = state.firstTeamOf(team).size >= firstTeamSize || roster.promoteProblem(state, team, player.id) != null
        val weakest = state.firstTeamOf(team)
            .filter { roster.demoteProblem(state, team, it.id, replacement = player.id) == null }
            // 같은 포지션의 가장 약한 선수가 먼저, 없으면 포지션 범위가 허락하는 다른 선수
            .sortedWith(compareBy<Player> { if (RosterSlot.of(it) == RosterSlot.of(player)) 0 else 1 }.thenBy { strength.overallOf(it) })
            .firstOrNull()
        if (full && weakest == null) return null
        val swap = if (full) weakest else null
        val name = player.registeredName
        val callUp = IncidentOption(
            id = "callup",
            label = "1군에 올린다",
            detail = if (swap != null) {
                "$name 등록, 대신 ${swap.registeredName}(${roleOf(swap)}) 말소. ${swap.registeredName.eunNeun()} " +
                    "${roster.reRegisterWeekIfDemotedNow(state)}주차부터 다시 올릴 수 있어요."
            } else {
                "$name 등록. 1군에 빈자리가 있어서 아무도 안 내려가요."
            },
            recommended = recommendCallUp,
            effects = listOfNotNull(IncidentEffect.Promote(player.id, swap?.id), callUpMemory),
            reactions = listOf(FanReaction(FanMood.POSITIVE, "$name 콜업 떴다!! 드디어")),
            followUp = "$name 선수 이번 주부터 1군이에요. 본인 많이 설레하더라고요.",
        )
        val decline = IncidentOption(
            id = "decline",
            label = declineLabel,
            detail = declineDetail,
            recommended = !recommendCallUp,
            effects = declineEffects,
            reactions = declineReactions,
            followUp = "네, 그렇게 전할게요.",
        )
        return Incident(
            id = idOf(state, week, null, kind, player.id),
            season = state.season, week = week, day = null, teamId = team,
            kind = kind,
            headline = headline,
            message = message,
            options = listOfNotNull(callUp, decline, extra),
            playerId = player.id,
        )
    }

    private fun rivalWeek(state: SeasonState, team: TeamId, week: Int, random: Random): Incident? {
        val rivalId = state.league.team(team).rival ?: return null
        val games = state.league.schedule.gamesInWeek(week).count {
            setOf(it.home, it.away) == setOf(team, rivalId)
        }
        if (games < rivalWeek.int("minGames")) return null
        if (state.policyOf(team) == WeeklyPolicy.ALL_OUT) return null
        if (!cooledDown(state, "rival", week, rivalWeek.int("cooldownWeeks"))) return null
        if (!random.chance(rivalWeek.double("chance"))) return null
        state.incidentKeys += "rival@$week"
        val rival = state.league.team(rivalId).name
        val (wins, losses) = state.headToHeadOf(team, rivalId)
        return Incident(
            id = idOf(state, week, null, IncidentKind.RIVAL_WEEK, null),
            season = state.season, week = week, day = null, teamId = team,
            kind = IncidentKind.RIVAL_WEEK,
            headline = "이번 주 $rival ${games}연전",
            message = "이번 주엔 ${rival.waGwa()} ${games}경기가 있어요. 올해 상대 전적은 ${wins}승 ${losses}패. " +
                "예매 사이트가 벌써 터졌대요. 감독님이 운영 방침을 물어보세요.",
            options = listOf(
                IncidentOption(
                    id = "allout",
                    label = "총력전으로 간다",
                    detail = "이번 주만 '총력' 방침: 필승조를 넓게 쓰고 투구수 제한을 풀어요. 팬심 +${rivalWeek.int("allOutFan")}, " +
                        "대신 피로와 부상 위험이 올라가요. 다음 주엔 원래 방침으로 돌아와요.",
                    effects = listOf(
                        IncidentEffect.PolicyThisWeek(WeeklyPolicy.ALL_OUT),
                        IncidentEffect.Fan(rivalWeek.int("allOutFan")),
                    ),
                    quote = "$rival 전은 그냥 한 경기가 아니다. 모든 걸 쏟겠다",
                    reactions = listOf(FanReaction(FanMood.POSITIVE, "$rival 전 총력전 선언 ㄷㄷ 이번 주 직관 간다")),
                    followUp = "감독님 웃으시더라고요. 불펜 다 대기시킨대요.",
                ),
                IncidentOption(
                    id = "normal",
                    label = "평소대로 한다",
                    detail = "시즌은 길어요. 방침은 그대로 둬요.",
                    recommended = true,
                    followUp = "네, 평소대로 갑니다.",
                ),
            ),
        )
    }

    // ---------- 도움 ----------

    // ---------- 기회형 (2026-10-03, 재미 개선 3번) ----------

    /**
     * 기회형 하나. 시즌 상한·확률을 넘기면 종류를 시드로 섞어 순서대로 시도해, 조건이 맞는 첫 종류를 고른다.
     * 같은 종류는 [opportunity] `kindCooldownWeeks` 간격을 둔다.
     */
    private fun opportunityIncident(state: SeasonState, team: TeamId, week: Int, random: Random): Incident? {
        val usedThisSeason = state.incidentKeys.count { it.startsWith("opp:${state.season}:") }
        if (usedThisSeason >= opportunity.int("maxPerSeason")) return null
        if (!random.chance(opportunity.double("chance"))) return null
        val kinds = listOf(
            IncidentKind.TRADE_INQUIRY,
            IncidentKind.RELEASED_VETERAN,
            IncidentKind.PROSPECT_TRIAL,
            IncidentKind.EXTENSION,
        ).shuffled(random)
        for (kind in kinds) {
            if (!cooledDown(state, "opp-kind:${state.season}:${kind.name}", week, opportunity.int("kindCooldownWeeks"))) continue
            val incident = when (kind) {
                IncidentKind.TRADE_INQUIRY -> tradeInquiry(state, team, week, random)
                IncidentKind.RELEASED_VETERAN -> releasedVeteran(state, team, week)
                IncidentKind.PROSPECT_TRIAL -> prospectTrial(state, team, week)
                else -> extensionTalk(state, team, week)
            } ?: continue
            state.incidentKeys += "opp:${state.season}:${incident.id}"
            state.incidentKeys += "opp-kind:${state.season}:${kind.name}@$week"
            return incident
        }
        return null
    }

    /**
     * 트레이드 문의: 우승을 노리는 팀이 우리 1군 베테랑을 원한다. 내놓는 건 그 팀 2군 유망주 중
     * **트레이드 AI 가 실제로 받아들일 묶음 가운데 가장 값진 것** — 말도 안 되는 제안은 오지 않는다.
     * 상대 유망주는 스카우트 범위로만 소개한다 (불변 원칙 4).
     */
    private fun tradeInquiry(state: SeasonState, team: TeamId, week: Int, random: Random): Incident? {
        val cfg = opportunity.section("tradeInquiry")
        if (!trades.isOpen(state) || state.pendingTradeOffer != null) return null
        val asked = { player: Player -> state.incidentKeys.contains("inquiry:${state.season}:${player.id.value}") }
        // 이적 요청으로 시장에 내놓은 선수가 먼저다 (나이·능력 조건 없이, 2군이어도) — docs/13
        val listed = state.playersOf(team).filter {
            state.morale[it.id]?.transferListed == true && !it.isForeign && !it.condition.isInjured && !asked(it)
        }
        val ours = listed.shuffled(random) + state.firstTeamOf(team).filter {
            !it.isForeign && !it.condition.isInjured && it.ageIn(state.season) >= cfg.int("minAge") &&
                strength.overallOf(it) >= cfg.int("minOverall") && !asked(it) && it !in listed
        }.shuffled(random).take(cfg.int("playerCandidates"))
        if (ours.isEmpty()) return null
        val league = state.currentLeague()
        val partners = state.league.teams.map { it.id }.filter { it != team && trades.modeOf(state, it) == baseballgm.market.TeamMode.CONTEND }
            .shuffled(random)
        for (player in ours) {
            for (partner in partners) {
                val prospects = state.futuresOf(partner)
                    .filter { it.ageIn(state.season) <= cfg.int("prospectMaxAge") && !it.condition.isInjured }
                    .sortedByDescending { it.hidden.potential.values.average() }
                    .take(cfg.int("candidates"))
                val proposal = prospects.map { prospect ->
                    baseballgm.market.TradeProposal(
                        proposer = partner,
                        partner = team,
                        fromProposer = baseballgm.market.TradePackage(listOf(prospect.id)),
                        fromPartner = baseballgm.market.TradePackage(listOf(player.id)),
                    )
                }.firstOrNull { proposal ->
                    trades.rules.problems(league, proposal, state.week, state.draftResult != null, state.tradeHistory).isEmpty() &&
                        trades.ai.judge(league, state.standings, proposal, partner, state.difficulty).accepted
                } ?: continue
                state.incidentKeys += "inquiry:${state.season}:${player.id.value}"
                val prospect = state.player(proposal.fromProposer.playerIds.single())
                val view = baseballgm.scouting.ScoutingView.of(prospect, baseballgm.scouting.ScoutingAccuracy.LOW, state.season, potentialScale)
                val partnerName = state.league.team(partner).name
                val listed = state.morale[player.id]?.transferListed == true
                // 이적을 원하는 선수면 보내는 쪽을 추천한다
                val rebuild = trades.modeOf(state, team) == baseballgm.market.TeamMode.REBUILD || listed
                return Incident(
                    id = idOf(state, week, null, IncidentKind.TRADE_INQUIRY, player.id),
                    season = state.season, week = week, day = null, teamId = team,
                    kind = IncidentKind.TRADE_INQUIRY,
                    headline = "$partnerName, ${player.registeredName} 문의",
                    message = "$partnerName 쪽에서 연락이 왔어요. " +
                        (if (listed) "이적을 요청한 ${roleOf(player)} ${player.registeredName} 선수에게 관심이 있대요. " else "올해 승부를 걸겠다고 ${roleOf(player)} ${player.registeredName} 선수를 원한대요. ") +
                        "대신 2군 ${view.positionLabel} ${prospect.registeredName}(${view.age}세)${prospect.registeredName.eulReul().removePrefix(prospect.registeredName)} 주겠다고 하네요. " +
                        "우리 스카우트 평가로는 지금 종합 ${view.overall}, 잠재력 ${view.potentialLabel}예요.",
                    options = listOf(
                        IncidentOption(
                            id = "accept",
                            label = "${prospect.registeredName} 받고 보낸다",
                            detail = "바로 성사돼요. ${player.registeredName.eunNeun()} 1군에서 빠지고, ${prospect.registeredName.eunNeun()} 2군으로 와요. " +
                                "올해 전력은 조금 내려가고 미래를 얻어요.",
                            recommended = rebuild,
                            effects = listOf(IncidentEffect.ExecuteTrade(proposal)),
                            quote = "미래를 위한 선택이다. ${player.registeredName} 선수에게 고맙다",
                            reactions = listOf(
                                FanReaction(FanMood.NEGATIVE, "${player.registeredName} 보낸다고?? 올해는 접는 거냐"),
                                FanReaction(FanMood.POSITIVE, "${prospect.registeredName} 잠재력 좋다던데 키워보자"),
                            ),
                            followUp = "계약서 오갔어요. ${prospect.registeredName} 선수는 2군 숙소로 바로 들어가요.",
                        ),
                        IncidentOption(
                            id = "decline",
                            label = "정중히 거절한다",
                            detail = "${player.registeredName.eunNeun()} 그대로 우리 1군에 남아요. 지켜 준 걸 알면 본인도 좋아할 거예요.",
                            recommended = !rebuild,
                            effects = listOfNotNull(
                                // 이적을 원한 선수에게는 기쁜 일이 아니다
                                memory(player.id, MemorySlot.TRADE, "keptFromTrade", "트레이드 문의에도 구단이 지켜 줬다")
                                    .takeUnless { listed },
                            ),
                            followUp = "거절 의사 전했어요. 그쪽도 아쉬워하더라고요.",
                        ),
                    ),
                    playerId = player.id,
                )
            }
        }
        return null
    }

    /**
     * 방출 매물: 다른 팀 2군에서 정리하려는 베테랑. 우리 같은 자리 1군 최하위보다 확실히 나을 때만 온다.
     * 계약(연봉)은 그대로 넘어온다. 상대 팀 선수라 스카우트 범위로만 소개한다.
     */
    private fun releasedVeteran(state: SeasonState, team: TeamId, week: Int): Incident? {
        val cfg = opportunity.section("releasedVeteran")
        val ourFirst = state.firstTeamOf(team)
        val candidate = state.league.teams.map { it.id }.filter { it != team }.flatMap { state.futuresOf(it) }
            .filter {
                !it.isForeign && it.military.isAvailable && !it.condition.isInjured &&
                    it.ageIn(state.season) >= cfg.int("minAge") && it.contract.salary <= cfg.double("maxSalary") &&
                    !state.incidentKeys.contains("released:${state.season}:${it.id.value}")
            }
            .mapNotNull { player ->
                val weakest = ourFirst.filter { RosterSlot.of(it) == RosterSlot.of(player) }.minByOrNull { strength.overallOf(it) }
                    ?: return@mapNotNull null
                if (strength.overallOf(player) < strength.overallOf(weakest) + cfg.int("overallMargin")) return@mapNotNull null
                player to weakest
            }
            .maxByOrNull { (player, _) -> strength.overallOf(player) } ?: return null
        val (player, weakest) = candidate
        state.incidentKeys += "released:${state.season}:${player.id.value}"
        val from = state.league.team(player.teamId!!).name
        val view = baseballgm.scouting.ScoutingView.of(player, baseballgm.scouting.ScoutingAccuracy.LOW, state.season, potentialScale)
        val salary = player.contract.salary
        val affordable = (state.funds[team] ?: 0.0) >= salary
        return Incident(
            id = idOf(state, week, null, IncidentKind.RELEASED_VETERAN, player.id),
            season = state.season, week = week, day = null, teamId = team,
            kind = IncidentKind.RELEASED_VETERAN,
            headline = "$from, ${view.positionLabel} ${player.registeredName} 정리 예정",
            message = "${from.iGa()} 2군 베테랑 ${player.registeredName}(${view.age}세, ${view.positionLabel})를 정리한대요. " +
                "연봉 ${salary.fixed(1)}억 계약을 그대로 넘겨받으면 우리한테 보내 주겠대요. " +
                "스카우트 평가로는 종합 ${view.overall} — 우리 ${roleOf(weakest)} ${weakest.registeredName}보다 나아 보여요.",
            options = listOf(
                IncidentOption(
                    id = "sign",
                    label = "데려와서 1군에 쓴다",
                    detail = "연봉 ${salary.fixed(1)}억을 우리가 내요. 바로 1군에 등록하고 ${weakest.registeredName.eunNeun()} 2군으로 내려가요.",
                    recommended = affordable,
                    effects = listOf(IncidentEffect.AcquirePlayer(player.id), IncidentEffect.Promote(player.id, swapOut = weakest.id)),
                    reactions = listOf(FanReaction(FanMood.POSITIVE, "${player.registeredName} 데려옴? 경험 있는 선수라 쏠쏠할 듯")),
                    followUp = "${player.registeredName} 선수랑 통화했어요. 기회 줘서 고맙대요. 내일부터 1군이에요.",
                ),
                IncidentOption(
                    id = "pass",
                    label = "넘긴다",
                    detail = "돈은 안 써요. ${player.registeredName.eunNeun()} ${from}에 남아요.",
                    recommended = !affordable,
                    followUp = "알겠어요. 다른 기회 오면 또 말씀드릴게요.",
                ),
            ),
            playerId = player.id,
        )
    }

    /**
     * 유망주 1군 시험: 우리 2군의 잠재력 높은 어린 선수를 1군에서 써 본다.
     * 써 보면 능력치가 조금 오르고(1군 경험), 대신 같은 자리 1군 최하위가 2군으로 내려간다.
     */
    private fun prospectTrial(state: SeasonState, team: TeamId, week: Int): Incident? {
        val cfg = opportunity.section("prospectTrial")
        val prospect = state.futuresOf(team).filter {
            it.ageIn(state.season) <= cfg.int("maxAge") && !it.condition.isInjured &&
                it.hidden.potential.values.average() >= cfg.int("minPotential") &&
                roster.eligibilityProblem(state, it.id) == null &&
                !state.incidentKeys.contains("trial:${state.season}:${it.id.value}")
        }.maxByOrNull { it.hidden.potential.values.average() } ?: return null
        val weakest = state.firstTeamOf(team).filter { RosterSlot.of(it) == RosterSlot.of(prospect) }
            .minByOrNull { strength.overallOf(it) } ?: return null
        state.incidentKeys += "trial:${state.season}:${prospect.id.value}"
        // 우리 선수라 잠재력은 등급으로 정확히 보인다 (docs/02)
        val view = baseballgm.scouting.ScoutingView.of(prospect, baseballgm.scouting.ScoutingAccuracy.OWN_TEAM, state.season, potentialScale)
        val contend = trades.modeOf(state, team) == baseballgm.market.TeamMode.CONTEND
        val boost = cfg.int("ratingBoost")
        return Incident(
            id = idOf(state, week, null, IncidentKind.PROSPECT_TRIAL, prospect.id),
            season = state.season, week = week, day = null, teamId = team,
            kind = IncidentKind.PROSPECT_TRIAL,
            headline = "유망주 ${prospect.registeredName} 1군 시험",
            message = "육성 파트에서 제안이 왔어요. ${view.age}세 ${view.positionLabel} ${prospect.registeredName} 선수(종합 ${view.overall}, 잠재력 ${view.potentialLabel})를 " +
                "1군에서 한번 써 보자고요. 1군 공을 직접 보면 크는 속도가 다르대요.",
            options = listOf(
                IncidentOption(
                    id = "trial",
                    label = "1군에 올려 써 본다",
                    detail = "바로 1군 등록, ${weakest.registeredName.eunNeun()} 2군으로 내려가요. 1군 경험으로 능력치 두 개가 ${boost}씩 올라요. 올해 전력은 조금 내려갈 수 있어요.",
                    recommended = !contend,
                    effects = listOf(
                        IncidentEffect.Promote(prospect.id, swapOut = weakest.id),
                        IncidentEffect.RatingBoost(prospect.id),
                        memory(prospect.id, MemorySlot.ROSTER, "calledUp", "유망주로 1군 시험 기회를 받았다"),
                    ),
                    reactions = listOf(FanReaction(FanMood.POSITIVE, "${prospect.registeredName} 드디어 1군!! 기대된다")),
                    followUp = "${prospect.registeredName} 선수 엄청 들떠 있어요. 1군 코치들이 붙어서 봐 준대요.",
                ),
                IncidentOption(
                    id = "wait",
                    label = "2군에서 더 키운다",
                    detail = "지금 1군은 그대로예요.",
                    recommended = contend,
                    followUp = "네, 2군에서 계속 경기 뛰게 할게요.",
                ),
            ),
            playerId = prospect.id,
        )
    }

    /**
     * 연장 계약 협상: 이번 시즌이 끝나면 FA 가 되는 우리 주전. 지금 묶으면 시장가보다 조금 비싸지만 FA 경쟁이 없다.
     * 요구 연봉 = max(지금 연봉 × raiseFloor, FA 시장가 × premium). 연장하면 지금부터 그 연봉이다.
     */
    private fun extensionTalk(state: SeasonState, team: TeamId, week: Int): Incident? {
        val cfg = opportunity.section("extension")
        val player = state.firstTeamOf(team).filter {
            !it.isForeign && it.contract.seasonsToFreeAgency == 0 && it.contract.yearsRemaining <= 1 &&
                it.ageIn(state.season) <= cfg.int("maxAge") && strength.overallOf(it) >= cfg.int("minOverall") &&
                !state.incidentKeys.contains("extension:${state.season}:${it.id.value}") &&
                // 만족도가 바닥인 선수는 먼저 연장을 꺼내지 않는다 (docs/13)
                !morale.refusesTalks(state, it)
        }.maxByOrNull { strength.overallOf(it) } ?: return null
        state.incidentKeys += "extension:${state.season}:${player.id.value}"
        val age = player.ageIn(state.season)
        val years = cfg.numericMap("yearsByAge").entries.sortedBy { it.key }.firstOrNull { age <= it.key }?.value?.roundToInt() ?: 1
        val market = maxOf(minimumSalary, valuation.expectedWar(player, strength.overallOf(player)) * valuation.salaryPerWar * faAskingPremium)
        // 만족도·충성심이 높으면 덜 부른다 (지금 연봉 아래로는 안 내려간다, docs/13)
        val asking = (maxOf(player.contract.salary * cfg.double("raiseFloor"), market * cfg.double("premium") * morale.contractMultiplier(state, player)) * 10)
            .roundToInt() / 10.0
        val name = player.registeredName
        val recommendExtend = age <= cfg.int("recommendMaxAge")
        return Incident(
            id = idOf(state, week, null, IncidentKind.EXTENSION, player.id),
            season = state.season, week = week, day = null, teamId = team,
            kind = IncidentKind.EXTENSION,
            headline = "$name 연장 계약 협상",
            message = "$name 선수 에이전트가 찾아왔어요. 이번 시즌 끝나면 FA라서, 지금 연장하면 ${years}년 연봉 ${asking.fixed(1)}억에 사인하겠대요. " +
                "지금 연봉은 ${player.contract.salary.fixed(1)}억이고, 시장에 나가면 ${market.fixed(1)}억 언저리에서 다른 팀들과 경쟁하게 될 거예요.",
            options = listOf(
                IncidentOption(
                    id = "extend",
                    label = "${years}년 ${asking.fixed(1)}억에 연장한다",
                    detail = "FA 시장에 안 나가요. 대신 지금부터 연봉이 ${asking.fixed(1)}억으로 올라요.",
                    recommended = recommendExtend,
                    effects = listOf(
                        IncidentEffect.ExtendContract(player.id, asking, years),
                        memory(player.id, MemorySlot.CONTRACT, "extended", "연장 계약으로 미래를 약속받았다"),
                    ),
                    quote = "${name.eunNeun()} 우리 팀의 중심이다. 오래 함께하고 싶다",
                    reactions = listOf(FanReaction(FanMood.POSITIVE, "$name 연장 ㄹㅇ 잘했다 프랜차이즈 가자")),
                    followUp = "도장 찍었어요. $name 선수도 마음 편하게 뛸 수 있겠대요.",
                ),
                IncidentOption(
                    id = "wait",
                    label = "시즌 끝까지 보고 정한다",
                    detail = "연봉은 그대로예요. 시즌이 끝나면 FA 시장에서 다른 팀들과 경쟁해야 해요. 본인은 조금 서운해할 거예요.",
                    recommended = !recommendExtend,
                    effects = listOf(memory(player.id, MemorySlot.CONTRACT, "extensionDeferred", "연장 계약 제안이 미뤄졌다")),
                    followUp = "알겠어요. 시즌 끝나고 다시 얘기하자고 전해 둘게요.",
                ),
            ),
            playerId = player.id,
        )
    }

    // ---------- 선수가 보내는 메시지 (2026-10-04, docs/13 선수 성향과 만족도) ----------

    private fun memory(playerId: PlayerId, slot: MemorySlot, key: String, label: String) =
        IncidentEffect.Memory(playerId, slot, key, label)

    /** 출장 약속 기한: 지금부터 N주 뒤, 정규시즌 마지막 주를 넘지 않게 */
    private fun promiseDeadline(state: SeasonState, week: Int): Int =
        minOf(week + moraleCfg.int("promise.playingTimeWeeks"), state.calendar.regularSeasonWeeks)

    /**
     * 이적 요청: 만족도가 불만 문턱 아래로 몇 주째인 야심 있는 선수가 직접 찾아온다.
     * 달래거나(약속), 트레이드 시장에 내놓거나(트레이드 문의가 이 선수로 먼저 온다), 거절한다.
     */
    private fun tradeRequest(state: SeasonState, team: TeamId, week: Int, random: Random): Incident? {
        val cfg = moraleCfg.section("tradeRequest")
        val player = state.playersOf(team).filter { player ->
            val mood = state.morale[player.id] ?: return@filter false
            !mood.transferListed && mood.promise == null && mood.lowWeeks >= cfg.int("weeks") &&
                !player.condition.isInjured && player.military.isAvailable &&
                morale.personalityOf(player).ambition >= cfg.int("minAmbition") &&
                cooledDown(state, "request:${state.season}:${player.id.value}", week, cfg.int("cooldownWeeks"))
        }.minByOrNull { state.morale.getValue(it.id).value } ?: return null
        if (!random.chance(cfg.double("chance"))) return null
        state.incidentKeys += "request:${state.season}:${player.id.value}@$week"
        val name = player.registeredName
        val worst = morale.factors(state, team, player, state.morale.getValue(player.id).memories).minByOrNull { it.value }
            ?.takeIf { it.value < 0 }
        val futures = player.rosterLevel == RosterLevel.FUTURES
        val deadline = promiseDeadline(state, week)
        val canContract = extensionService.ineligibleReason(state, team, player, ignoreMorale = true) == null
        val options = buildList {
            if (futures) {
                add(
                    IncidentOption(
                        id = "promise",
                        label = "${deadline}주차까지 1군 기회를 약속한다",
                        detail = "엔트리는 그대로예요. ${deadline}주차가 끝날 때까지 1군에 올리면 약속을 지킨 거고, 못 올리면 크게 실망해요.",
                        effects = listOf(
                            IncidentEffect.Promise(player.id, PromiseKind.PLAYING_TIME, deadline),
                            memory(player.id, MemorySlot.PROMISE, "promiseMade", "1군 기회를 약속받았다"),
                        ),
                        followUp = "$name 선수가 일단 기다려 보겠대요. 약속 기한은 제가 챙겨 둘게요.",
                    ),
                )
            } else if (canContract) {
                add(
                    IncidentOption(
                        id = "contract",
                        label = "다년계약으로 붙잡겠다고 약속한다",
                        detail = "정규시즌이 끝나기 전에 다년계약(선수 상세 → 계약)에 도장을 찍으면 약속을 지킨 거예요. 못 찍으면 크게 실망해요.",
                        effects = listOf(
                            IncidentEffect.Promise(player.id, PromiseKind.CONTRACT, state.calendar.regularSeasonWeeks),
                            memory(player.id, MemorySlot.PROMISE, "promiseMade", "다년계약을 약속받았다"),
                        ),
                        followUp = "$name 선수가 단장님 말을 믿어 보겠대요. 협상은 계약 탭에서 하시면 돼요.",
                    ),
                )
            }
            add(
                IncidentOption(
                    id = "list",
                    label = "트레이드 시장에 내놓겠다",
                    detail = "이적 희망 선수로 알려요. 다른 구단 트레이드 문의가 $name 선수로 먼저 와요. 받아들일지는 그때 정해요.",
                    // 약속은 단장이 직접 지켜야 하는 일이라 비서가 대신 하지 않는다 — 맡기면 시장에 내놓는다
                    recommended = true,
                    effects = listOf(
                        IncidentEffect.TransferList(player.id),
                        memory(player.id, MemorySlot.TRADE, "tradeListed", "이적 요청을 들어줬다"),
                    ),
                    reactions = listOf(FanReaction(FanMood.NEGATIVE, "$name 이적 요청했다는 기사 떴던데 진짜임?")),
                    followUp = "알겠어요. 에이전트한테 다른 구단 문의를 받아 보라고 전할게요.",
                ),
            )
            add(
                IncidentOption(
                    id = "refuse",
                    label = "\"넌 우리 팀에 필요하다\" — 거절한다",
                    detail = "보내지 않아요. 대신 $name 선수의 만족도가 크게 떨어져요.",
                    effects = listOf(memory(player.id, MemorySlot.TRADE, "tradeRefused", "이적 요청을 거절당했다")),
                    followUp = "말은 전했어요. 표정이 많이 굳더라고요.",
                ),
            )
        }
        return Incident(
            id = idOf(state, week, null, IncidentKind.TRADE_REQUEST, player.id),
            season = state.season, week = week, day = null, teamId = team,
            kind = IncidentKind.TRADE_REQUEST,
            headline = "$name 이적 요청",
            message = "단장님, 고민 많이 하다가 말씀드립니다. " +
                (worst?.let { "${it.label}는 게 계속 마음에 걸려요. " } ?: "요즘 여기서 야구가 즐겁지 않아요. ") +
                "저를 필요로 하는 팀으로 보내 주셨으면 합니다.",
            options = options,
            playerId = player.id,
        )
    }

    /**
     * 선수 메시지 슬롯: 위기형·기회형 이벤트가 없는 주에 한 건 (연봉 불만 또는 멘토링 자청).
     * 시즌 상한을 넘기면 오지 않는다.
     */
    private fun playerMessage(state: SeasonState, team: TeamId, week: Int, random: Random): Incident? {
        val cfg = moraleCfg.section("playerMessages")
        if (state.incidentKeys.count { it.startsWith("pmsg:${state.season}:") } >= cfg.int("maxPerSeason")) return null
        if (!random.chance(cfg.double("chance"))) return null
        val makers = listOf<() -> Incident?>(
            { salaryComplaint(state, team, week, random) },
            { mentorOffer(state, team, week) },
        ).shuffled(random)
        for (make in makers) {
            val incident = make() ?: continue
            state.incidentKeys += "pmsg:${state.season}:${incident.id}"
            return incident
        }
        return null
    }

    /** 연봉 불만: 올해 잘하는데 시장가의 절반도 못 받는 선수. 프로의식이 낮을수록 잘 말한다 */
    private fun salaryComplaint(state: SeasonState, team: TeamId, week: Int, random: Random): Incident? {
        val cfg = moraleCfg.section("salaryComplaint")
        if (week < cfg.int("minWeek")) return null
        val constants = baseballgm.stats.LeagueConstants.from(state.stats, balance)
        val (player, seasonWar) = state.firstTeamOf(team).asSequence()
            .filter {
                !it.isForeign && it.contract.serviceSeasons >= cfg.int("minService") &&
                    state.morale[it.id]?.promise == null &&
                    cooledDown(state, "salary:${state.season}:${it.id.value}", week, cfg.int("cooldownWeeks")) &&
                    extensionService.ineligibleReason(state, team, it, ignoreMorale = true) == null
            }
            .map { it to war.of(it, state.stats, constants).war }
            .filter { (player, seasonWar) ->
                val market = valuation.expectedWar(player, strength.overallOf(player)) * valuation.salaryPerWar
                seasonWar >= cfg.double("minWar") && player.contract.salary < market * cfg.double("ratio")
            }
            .maxByOrNull { it.second } ?: return null
        val professionalism = morale.personalityOf(player).professionalism
        if (!random.chance((1.5 - professionalism / 100.0).coerceIn(0.5, 1.5) / 1.5)) return null
        state.incidentKeys += "salary:${state.season}:${player.id.value}@$week"
        val name = player.registeredName
        val market = maxOf(minimumSalary, valuation.expectedWar(player, strength.overallOf(player)) * valuation.salaryPerWar)
        return Incident(
            id = idOf(state, week, null, IncidentKind.SALARY_COMPLAINT, player.id),
            season = state.season, week = week, day = null, teamId = team,
            kind = IncidentKind.SALARY_COMPLAINT,
            headline = "$name 연봉 얘기",
            message = "단장님, 올해 제 기록 보셨잖아요 (WAR ${seasonWar.fixed(1)}). 연봉 ${player.contract.salary.fixed(1)}억인데, " +
                "밖에서는 ${market.fixed(1)}억은 받는 선수라고들 해요. 제 가치를 좀 인정해 주셨으면 합니다.",
            options = listOf(
                IncidentOption(
                    id = "promise",
                    label = "다년계약을 약속한다",
                    detail = "정규시즌이 끝나기 전에 다년계약(선수 상세 → 계약)에 도장을 찍으면 약속을 지킨 거예요. 못 찍으면 크게 실망해요.",
                    effects = listOf(
                        IncidentEffect.Promise(player.id, PromiseKind.CONTRACT, state.calendar.regularSeasonWeeks),
                        memory(player.id, MemorySlot.PROMISE, "promiseMade", "다년계약을 약속받았다"),
                    ),
                    followUp = "$name 선수 표정이 밝아졌어요. 협상은 계약 탭에서 하시면 돼요.",
                ),
                IncidentOption(
                    id = "later",
                    label = "시즌 뒤 연봉 협상에서 반영하겠다",
                    detail = "올해 성적은 시즌 뒤 연봉 재계약에 원래 반영돼요. 본인도 조금은 누그러져요.",
                    recommended = true,
                    effects = listOf(memory(player.id, MemorySlot.CONTRACT, "salaryNoted", "연봉 얘기를 들어줬다")),
                    followUp = "그렇게 전했어요. 일단 수긍하는 눈치예요.",
                ),
                IncidentOption(
                    id = "no",
                    label = "지금은 때가 아니다",
                    detail = "선을 그어요. $name 선수의 만족도가 떨어져요.",
                    effects = listOf(memory(player.id, MemorySlot.CONTRACT, "salaryRefused", "연봉 얘기를 꺼냈다가 거절당했다")),
                    followUp = "말은 전했어요. 많이 서운해하네요.",
                ),
            ),
            playerId = player.id,
        )
    }

    /** 멘토링 자청: 만족한 고충성심 베테랑이 같은 쪽(투수/야수) 유망주를 맡겠다고 한다 */
    private fun mentorOffer(state: SeasonState, team: TeamId, week: Int): Incident? {
        val cfg = moraleCfg.section("mentor")
        val veteran = state.playersOf(team).filter {
            it.ageIn(state.season) >= cfg.int("minAge") && !it.condition.isInjured && it.military.isAvailable &&
                (state.morale[it.id]?.value ?: 0) >= cfg.int("minMorale") &&
                morale.personalityOf(it).loyalty >= cfg.int("minLoyalty") &&
                cooledDown(state, "mentor:${state.season}:${it.id.value}", week, cfg.int("cooldownWeeks"))
        }.maxByOrNull { state.morale[it.id]?.value ?: 0 } ?: return null
        val prospect = state.playersOf(team).filter {
            it.id != veteran.id && (it is Pitcher) == (veteran is Pitcher) && it.ageIn(state.season) <= cfg.int("prospectMaxAge") &&
                !it.condition.isInjured && it.military.isAvailable
        }.maxByOrNull { it.hidden.potential.values.average() - it.ratingsMap().values.average() } ?: return null
        state.incidentKeys += "mentor:${state.season}:${veteran.id.value}@$week"
        val name = veteran.registeredName
        val young = prospect.registeredName
        return Incident(
            id = idOf(state, week, null, IncidentKind.MENTOR, veteran.id),
            season = state.season, week = week, day = null, teamId = team,
            kind = IncidentKind.MENTOR,
            headline = "$name, ${young} 멘토링 자청",
            message = "단장님, ${young} 보니까 제 어릴 때 생각이 나더라고요. 괜찮으시면 제가 옆에 붙어서 좀 가르쳐 보고 싶습니다. " +
                "팀에 받은 게 많아서 이렇게라도 돌려드리고 싶어요.",
            options = listOf(
                IncidentOption(
                    id = "accept",
                    label = "부탁한다",
                    detail = "$young 능력치 ${cfg.int("boostAttributes")}개가 ${cfg.int("ratingBoost")}씩 올라요(잠재력 안에서). $name 선수도 뿌듯해해요.",
                    recommended = true,
                    effects = listOf(
                        IncidentEffect.RatingBoost(prospect.id, cfg.int("ratingBoost"), cfg.int("boostAttributes")),
                        memory(veteran.id, MemorySlot.MENTOR, "mentorAccepted", "후배를 맡겨 줬다"),
                        memory(prospect.id, MemorySlot.MENTOR, "mentored", "${name}에게 많이 배웠다"),
                    ),
                    reactions = listOf(FanReaction(FanMood.POSITIVE, "$name 이 $young 데리고 다닌다던데 이런 게 팀이지")),
                    followUp = "$name 선수가 오늘부터 바로 붙어서 본대요. $young 선수도 신났어요.",
                ),
                IncidentOption(
                    id = "decline",
                    label = "마음만 받겠다",
                    detail = "코칭스태프에게 맡겨요. $name 선수는 조금 머쓱해해요.",
                    effects = listOf(memory(veteran.id, MemorySlot.MENTOR, "mentorDeclined", "멘토링 제안이 거절됐다")),
                    followUp = "고맙다는 말은 전했어요.",
                ),
            ),
            playerId = veteran.id,
        )
    }

    private fun futuresLine(state: SeasonState, player: Player): String = when (player) {
        is Batter -> state.stats.futuresBattingOf(player.id).let {
            "${it.plateAppearances}타석 OPS ${it.ops.fixed(3)}"
        }
        is Pitcher -> state.stats.futuresPitchingOf(player.id).let {
            "${it.outs / 3}이닝 평균자책 ${it.era.fixed(2)}"
        }
    }

    private fun roleOf(player: Player): String = when (player) {
        is Batter -> POSITION_NAMES[player.primaryPosition.label] ?: player.primaryPosition.label
        is Pitcher -> when (player.role) {
            baseballgm.model.PitcherRole.STARTER -> "선발"
            baseballgm.model.PitcherRole.CLOSER -> "마무리"
            baseballgm.model.PitcherRole.RELIEVER -> "불펜"
        }
    }

    /**
     * 같은 종류를 너무 자주 묻지 않는다. 열쇠에 "@주차"를 붙여 마지막으로 물은 주를 기억한다.
     * 매주 같은 카드가 뜨면 그건 돌발이 아니라 잔소리다.
     */
    private fun cooledDown(state: SeasonState, key: String, week: Int, weeks: Int): Boolean {
        val last = state.incidentKeys.filter { it.startsWith("$key@") }
            .mapNotNull { it.substringAfter("@").toIntOrNull() }
            .maxOrNull() ?: return true
        return week - last >= weeks
    }

    private fun idOf(state: SeasonState, week: Int, day: Int?, kind: IncidentKind, playerId: PlayerId?): String =
        "${state.season}-$week-${day ?: "s"}-${kind.name}-${playerId?.value ?: ""}"

    internal companion object {
        const val YOUNG_AGE = 24

        /** 선수 메시지 난수를 예전 이벤트 난수와 떼어 놓는 소금 */
        const val PLAYER_SALT = 0x0B_5E55_EDL

        /** 부상 복귀를 미룬 선수: 이 열쇠의 주차에는 묻지 않고 자동으로 올린다 */
        const val RETURN_DELAYED = "returnDelayed"

        val POSITION_NAMES = mapOf(
            "C" to "포수", "1B" to "1루수", "2B" to "2루수", "3B" to "3루수", "SS" to "유격수",
            "LF" to "좌익수", "CF" to "중견수", "RF" to "우익수", "DH" to "지명타자",
        )

        val CONTROVERSIES = listOf(
            "SNS 발언 논란",
            "팬 사인 요청 거절 영상",
            "심판 판정 항의 영상",
            "경기 중 더그아웃 물병 투척 장면",
            "늦은 밤 번화가 목격담",
        )
    }
}
