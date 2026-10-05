package baseballgm.cli

import baseballgm.io.BalanceConfig
import baseballgm.io.LeagueLoader
import baseballgm.league.League
import baseballgm.league.ScheduleRules
import baseballgm.league.StrengthCalculator
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.RosterLevel
import baseballgm.scouting.ScoutingAccuracy
import baseballgm.scouting.ScoutingView
import baseballgm.tools.LeagueGenerator
import baseballgm.tools.ProjectFiles
import baseballgm.tools.TeamTemplates
import baseballgm.tools.report

/**
 * 콘솔 프로토타입.
 * 화면 코드에서만 println 을 쓴다 (불변 원칙 1).
 *
 * 사용법:
 *   (없음)        설정·구단 데이터 확인
 *   generate [시드]  리그를 생성해 data/league_2026.json 에 저장
 *   league        저장된 리그 요약 보기
 *   stats         생성된 리그의 분포 확인 (캘리브레이션용)
 *   simulate [N]  N경기 시뮬레이션 + 박스스코어 검증 + 리그 평균 출력
 *   game [시드]    경기 한 판을 박스스코어로 출력
 *   directives [팀] 감독 성향으로 만든 사전 지시 규칙표 보기
 *   season [주]    정규시즌 N주 진행 (주간 결산 + 순위표)
 *   watch [시드]   경기 한 판 문자 중계
 *   scout [팀]     드래프트 풀과 스카우트 리포트 (집중 관찰 정확도 비교)
 *   draft [시드]   드래프트 주차까지 진행하고 지명 결과 보기
 *   pickvalue [N]  N시즌 시뮬레이션으로 지명권 가치표 뽑기
 *   war [시드]     한 시즌을 돌리고 리그 상수·세이버 지표·WAR 확인
 *   warcurve [N]   N시즌으로 "종합 능력치 → 기대 WAR" 표 뽑기 (가치 평가용)
 *   market [시드]   한 시즌 + 스토브리그를 돌리고 트레이드·FA·소프트캡 결과 보기
 *   club [시드]     포스트시즌·재정·팬심·구단주·스태프 결산 보기
 *   career [N] [팀] N시즌 커리어 진행 (평판·업적·해임·이직)
 *   foreign [시드]  외국인 시장·적응력·교체와 스토브리그 외국인 계약 보기
 *   national [시드] 국제대회(대표 선발·결과·병역 특례)와 군 복무 보기
 *   calibrate [N]  N시즌 평균을 캘리브레이션 목표와 비교
 *   soak [N]       N시즌 검증 (M4 완료 기준: 1,000시즌 실패 0)
 *   longterm [N] [시드]   N시즌 연속 진행 + 장기 밸런스 확인 (M5 완료 기준: 30시즌). 시드를 바꿔 여러 번 돌리면 우연 폭을 볼 수 있다
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        null, "info" -> printInfo()
        "generate" -> generateLeague(args.getOrNull(1)?.toLongOrNull() ?: LeagueGenerator.DEFAULT_SEED)
        "league" -> printLeague()
        "stats" -> printStats()
        "simulate" -> simulateGames(args.getOrNull(1)?.toIntOrNull() ?: 1000)
        "game" -> printSingleGame(args.getOrNull(1)?.toLongOrNull() ?: 1L)
        "directives" -> printDirectives(args.getOrNull(1) ?: "DSK")
        "season" -> playSeason(args.getOrNull(1)?.toIntOrNull() ?: 24)
        "watch" -> watchGame(args.getOrNull(1)?.toLongOrNull() ?: 1L)
        "scout" -> printScouting(args.getOrNull(1) ?: "SWR")
        "draft" -> runDraft(args.getOrNull(1)?.toLongOrNull() ?: 20260401L)
        "club" -> printClub(args.getOrNull(1)?.toLongOrNull() ?: 20260401L)
        "career" -> printCareer(args.getOrNull(1)?.toIntOrNull() ?: 8, args.getOrNull(2) ?: "SWR")
        "foreign" -> printForeign(args.getOrNull(1)?.toLongOrNull() ?: 20260401L)
        "national" -> printNationalTeam(args.getOrNull(1)?.toLongOrNull() ?: 20260401L)
        "market" -> printMarket(args.getOrNull(1)?.toLongOrNull() ?: 20260401L)
        "warcurve" -> printWarCurve(args.getOrNull(1)?.toIntOrNull() ?: 5)
        "war" -> printSabermetrics(args.getOrNull(1)?.toLongOrNull() ?: 20260401L)
        "pickvalue" -> printPickValue(args.getOrNull(1)?.toIntOrNull() ?: 12, args.getOrNull(2)?.toIntOrNull() ?: 1)
        "calibrate" -> calibrate(args.getOrNull(1)?.toIntOrNull() ?: 5)
        "soak" -> soak(args.getOrNull(1)?.toIntOrNull() ?: 50)
        "longterm" -> longTerm(args.getOrNull(1)?.toIntOrNull() ?: 30, args.getOrNull(2)?.toLongOrNull() ?: LONG_TERM_SEED)
        else -> println("알 수 없는 명령: ${args.first()} (info / generate [시드] / league)")
    }
}

private fun printInfo() {
    val balance = ProjectFiles.loadBalanceConfig()
    val templates = ProjectFiles.loadTeamTemplates()

    println("=== 야구 단장 시뮬레이션 ===")
    println("저장소 루트: ${ProjectFiles.root.absolutePath}")
    println("시즌 ${templates.season} · 구단 ${templates.teams.size}개 · 소프트캡 ${templates.salaryCap}억")
    println()
    printTeams(templates)
    println()
    printConfigStatus(balance)
    println()
    val problems = templates.validate()
    if (problems.isEmpty()) {
        println("데이터 검증: 이상 없음")
    } else {
        println("데이터 검증 실패:")
        problems.forEach { println("  - $it") }
    }
}

private fun generateLeague(seed: Long) {
    val balance = ProjectFiles.loadBalanceConfig()
    val templates = ProjectFiles.loadTeamTemplates()
    val tolerance = StrengthCalculator(balance).targetTolerance

    println("리그 생성 시작 (시드 $seed)")
    val started = System.currentTimeMillis()
    val generated = LeagueGenerator(balance, templates, seed).generate()
    val elapsed = System.currentTimeMillis() - started

    println(generated.report(tolerance))
    val scheduleProblems = generated.league.schedule.validate(
        generated.league.teams.map { it.id },
        ScheduleRules.from(balance),
    )
    println(if (scheduleProblems.isEmpty()) "일정표 검증: 이상 없음" else "일정표 문제: $scheduleProblems")

    val path = ProjectFiles.leaguePath(templates.season)
    ProjectFiles.write(path, LeagueLoader.encode(generated.league))
    println("선수 ${generated.league.players.size}명 · ${elapsed}ms · 저장: $path")
}

private fun printLeague() {
    val balance = ProjectFiles.loadBalanceConfig()
    val templates = ProjectFiles.loadTeamTemplates()
    val path = ProjectFiles.leaguePath(templates.season)
    val league = LeagueLoader.parse(ProjectFiles.read(path))
    val strength = StrengthCalculator(balance)

    println("=== ${league.season} 시즌 리그 (시드 ${league.seed}) ===")
    println("선수 ${league.players.size}명 · 감독 ${league.managers.size}명(무직 ${league.unemployedManagers().size}) · 경기 ${league.schedule.games.size}판")
    println()
    league.teams.forEach { team ->
        val players = league.playersOf(team.id)
        val value = strength.of(players).rounded()
        println(
            "${team.id} ${team.name.padEnd(14)} 종합 ${"%.1f".format(value.overall)} " +
                "(타선 ${"%.1f".format(value.lineup)} 선발 ${"%.1f".format(value.rotation)} 불펜 ${"%.1f".format(value.bullpen)}) " +
                "1군 ${players.count { it.rosterLevel == RosterLevel.FIRST_TEAM }} 2군 ${players.count { it.rosterLevel == RosterLevel.FUTURES }} " +
                "연봉 ${"%.1f".format(league.payrollOf(team.id))}억",
        )
    }
    println()
    printSampleRoster(league)
}

/** 첫 팀의 주요 선수를 스카우트 화면으로 보여준다 (정보 은닉 확인용). */
private fun printSampleRoster(league: League) {
    val team = league.teams.first()
    val strengthOrder = league.firstTeamOf(team.id)
    println("[${team.name}] 1군 주요 선수 — 우리 팀 시선(정확) / 타 팀 시선(범위)")
    val scale = baseballgm.scouting.PotentialScale.from(ProjectFiles.loadBalanceConfig())
    strengthOrder.take(8).forEach { player ->
        val own = ScoutingView.of(player, ScoutingAccuracy.OWN_TEAM, league.season, scale)
        val rival = ScoutingView.of(player, ScoutingAccuracy.MEDIUM, league.season, scale)
        val detail = when (player) {
            is Batter -> "컨택 ${own.ratings.values.first()} / 타 팀: 컨택 ${rival.ratings.values.first()}"
            is Pitcher -> "구위 ${own.ratings.values.first()} / 타 팀: 구위 ${rival.ratings.values.first()} (최고 ${player.topSpeedKmh}km/h)"
        }
        println(
            "  ${own.positionLabel.padEnd(3)} ${player.registeredName.padEnd(12)} ${own.age}세 " +
                "잠재 ${own.potentialLabel}/${rival.potentialLabel}  $detail  ${"%.2f".format(player.contract.salary)}억",
        )
    }
}

private fun printTeams(templates: TeamTemplates) {
    println("ID  구단명            연고  전력  타선 선발 불펜  연봉  지명  콘셉트")
    for (team in templates.teams.sortedByDescending { it.targets.overall }) {
        val t = team.targets
        println(
            buildString {
                append(team.id.padEnd(4))
                append(team.name.padEnd(16))
                append(team.city.padEnd(6))
                append(t.overall.toString().padStart(4))
                append(t.lineup.toString().padStart(6))
                append(t.rotation.toString().padStart(5))
                append(t.bullpen.toString().padStart(5))
                append("${team.payroll}억".padStart(7))
                append("${team.draftPick}순".padStart(6))
                append("  ${team.keyword}")
            },
        )
    }
}

private fun printConfigStatus(balance: BalanceConfig) {
    val needVerify = balance.sectionsNeedingVerification()
    val draft = balance.draftSections()
    println("확인 필요 규정(TODO verify) ${needVerify.size}개: ${needVerify.joinToString(", ")}")
    println("캘리브레이션 대상 초안 수치 ${draft.size}개: ${draft.joinToString(", ")}")
}


/** 생성 결과 분포. 능력치·나이·계약이 기획 의도대로 나왔는지 눈으로 확인한다. */
private fun printStats() {
    val balance = ProjectFiles.loadBalanceConfig()
    val templates = ProjectFiles.loadTeamTemplates()
    val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
    val strength = StrengthCalculator(balance)

    val firstTeam = league.players.filter { it.rosterLevel == RosterLevel.FIRST_TEAM }
    val starters = league.teams.flatMap { team ->
        val roster = league.firstTeamOf(team.id)
        roster.filterIsInstance<Batter>().sortedByDescending { strength.overallOf(it) }.take(9) +
            roster.filterIsInstance<Pitcher>().filter { it.role.name == "STARTER" }
                .sortedByDescending { strength.overallOf(it) }.take(5)
    }
    println("=== 분포 (${league.players.size}명) ===")
    println("1군 ${firstTeam.size}명 / 2군 ${league.players.size - firstTeam.size}명")
    println("주전(팀별 야수 9 + 선발 5 = ${starters.size}명) 평균 능력치 ${"%.1f".format(starters.map { strength.overallOf(it) }.average())}")
    println("1군 전체 평균 ${"%.1f".format(firstTeam.map { strength.overallOf(it) }.average())} · 리그 전체 평균 ${"%.1f".format(league.players.map { strength.overallOf(it) }.average())}")

    val allRatings = league.players.flatMap { it.ratingsMap().values }
    println("능력치 90+ ${allRatings.count { it >= 90 }}개 · 85+ ${allRatings.count { it >= 85 }}개 · 전체 ${allRatings.size}개")
    println("종합 85+ 선수 ${league.players.count { strength.overallOf(it) >= 85 }}명 · 80+ ${league.players.count { strength.overallOf(it) >= 80 }}명")

    val ages = league.players.map { it.ageIn(league.season) }
    println("나이 최소 ${ages.min()} 최대 ${ages.max()} 평균 ${"%.1f".format(ages.average())} · 주전 평균 ${"%.1f".format(starters.map { it.ageIn(league.season) }.average())}")

    val military = league.players.groupingBy { it.military::class.simpleName }.eachCount()
    println("군 복무: $military")
    val origins = league.players.groupingBy { it.origin.name }.eachCount()
    println("출신: $origins")
    val hands = league.players.groupingBy { it.bats.name }.eachCount()
    println("타격 손: $hands · 좌완 투수 ${league.players.filterIsInstance<Pitcher>().count { it.throwsWith.name == "LEFT" }}명")

    val salaries = league.players.map { it.contract.salary }.sorted()
    println("연봉 최저 ${"%.2f".format(salaries.first())}억 중앙 ${"%.2f".format(salaries[salaries.size / 2])}억 최고 ${"%.2f".format(salaries.last())}억 · 총 ${"%.0f".format(salaries.sum())}억")
    println("FA까지 0시즌(올 시즌 후 FA) ${league.players.count { it.contract.seasonsToFreeAgency == 0 && !it.isForeign }}명 · 외국인 ${league.players.count { it.isForeign }}명")
    val speeds = league.players.filterIsInstance<Pitcher>().map { it.topSpeedKmh }
    println("최고 구속 평균 ${"%.1f".format(speeds.average())}km/h 최고 ${speeds.max()}km/h")
}


private fun loadLeague(): Pair<baseballgm.io.BalanceConfig, baseballgm.league.League> {
    val balance = ProjectFiles.loadBalanceConfig()
    val templates = ProjectFiles.loadTeamTemplates()
    return balance to LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
}

/** 대량 경기 검증 + 캘리브레이션 지표 (docs/04 목표와 비교). */
private fun simulateGames(count: Int) {
    val (balance, league) = loadLeague()
    val runner = baseballgm.tools.GameRunner(balance, league)
    val aggregate = baseballgm.tools.LeagueTotals()
    var failures = 0
    val failureMessages = mutableListOf<String>()

    println("경기 $count 판 시뮬레이션...")
    val started = System.currentTimeMillis()
    runner.playSchedule(count, seed = 77L) { played ->
        val problems = baseballgm.stats.BoxScoreValidator.validate(played.box)
        if (problems.isNotEmpty()) {
            failures++
            if (failureMessages.size < 5) failureMessages += problems.joinToString("; ")
        }
        aggregate.add(played)
    }
    val elapsed = System.currentTimeMillis() - started

    println("검증 실패 $failures 건 / $count 경기 (${elapsed}ms)")
    failureMessages.forEach { println("  실패 예: $it") }
    println()
    println(aggregate.report(balance))
}

/** 경기 한 판을 박스스코어로 본다. */
private fun printSingleGame(seed: Long) {
    val (balance, league) = loadLeague()
    val runner = baseballgm.tools.GameRunner(balance, league)
    val game = league.schedule.games.first()
    val played = runner.play(game.home, game.away, rotationIndex = 0, seed = seed)
    val box = played.box
    println("${league.team(box.away.teamId).name} @ ${league.team(box.home.teamId).name}  (시드 $seed)")
    println(box.line())
    println("이닝 ${box.innings}" + (if (box.walkOff) " 끝내기" else "") + (if (box.tie) " 무승부" else ""))
    println()
    listOf(box.away, box.home).forEach { team ->
        println("[${league.team(team.teamId).name}] ${team.runs}득점 ${team.hits}안타 ${team.errors}실책 잔루 ${team.leftOnBase}")
        team.batting.entries.sortedByDescending { it.value.total.plateAppearances }.take(9).forEach { (id, line) ->
            val t = line.total
            println("  ${league.player(id).registeredName.padEnd(12)} ${t.atBats}타수 ${t.hits}안타 ${t.homeRuns}홈런 ${t.rbi}타점 ${t.runs}득점 ${t.strikeouts}삼진 ${t.walks}볼넷")
        }
        team.pitching.entries.sortedByDescending { it.value.total.outs }.forEach { (id, line) ->
            val t = line.total
            println("  P ${league.player(id).registeredName.padEnd(10)} ${t.inningsText()}이닝 ${t.hits}피안타 ${t.runs}실점(자책 ${t.earnedRuns}) ${t.strikeouts}탈삼진 ${t.walks}볼넷 ${t.pitches}구")
        }
        val decisions = listOfNotNull(
            box.winningPitcher?.takeIf { team.pitching.containsKey(it) }?.let { "승 ${league.player(it).registeredName}" },
            box.losingPitcher?.takeIf { team.pitching.containsKey(it) }?.let { "패 ${league.player(it).registeredName}" },
            box.savePitcher?.takeIf { team.pitching.containsKey(it) }?.let { "세이브 ${league.player(it).registeredName}" },
        )
        if (decisions.isNotEmpty()) println("  " + decisions.joinToString(" / "))
        println()
    }
}


/** 감독 성향 → 사전 지시 규칙표 (docs/06). 유저가 읽고 이해할 수 있게 풀어서 보여준다. */
private fun printDirectives(teamIdValue: String) {
    val (balance, league) = loadLeague()
    val teamId = baseballgm.model.TeamId(teamIdValue)
    val team = league.team(teamId)
    val manager = league.managerOf(teamId)
    val runner = baseballgm.tools.GameRunner(balance, league)
    val sheet = runner.sheetFor(teamId)

    println("=== ${team.name} 사전 지시 규칙표 ===")
    if (manager != null) {
        val t = manager.tendencies
        println("감독 ${manager.name} (${manager.ageIn(league.season)}세, ${manager.playingBackground}, 전문 ${manager.specialty})")
        println(
            "  성향: 선발인내 ${t.starterPatience} / 불펜혹사 ${t.bullpenAggression} / 번트 ${t.buntPreference} / " +
                "도루 ${t.stealAggression} / 플래툰 ${t.platoonUsage} / 유망주 ${t.prospectUsage} / 베테랑신뢰 ${t.veteranTrust}",
        )
    }
    println()
    listOf("좌완 선발 상대" to sheet.lineupVsLeft, "우완 선발 상대" to sheet.lineupVsRight).forEach { (label, plan) ->
        println("[$label 라인업]")
        plan.slots.forEachIndexed { index, slot ->
            val player = league.player(slot.playerId) as baseballgm.model.Batter
            println("  ${index + 1}번 ${slot.position.label.padEnd(3)} ${player.registeredName.padEnd(12)} ${player.bats}타 컨택 ${player.ratings.contact} 파워 ${player.ratings.power} 주루 ${player.ratings.speed}")
        }
    }
    println()
    println("[선발 로테이션] 최소 휴식 ${sheet.rotation.minimumRestDays}일")
    sheet.rotation.starters.forEachIndexed { index, id ->
        val p = league.player(id) as baseballgm.model.Pitcher
        println("  ${index + 1}선발 ${p.registeredName.padEnd(12)} 구위 ${p.ratings.stuff} 제구 ${p.ratings.control} 체력 ${p.ratings.stamina}")
    }
    println()
    println("[선발 교체 조건] 투구수 ${sheet.starterHook.pitchLimit}구 / 실점 ${sheet.starterHook.runsAllowedLimit}점 / 피로 ${sheet.starterHook.fatigueLimit} / 3번째 타순에서 교체 ${if (sheet.starterHook.pullOnThirdTimeThroughOrder) "예" else "아니오"}")
    println("[불펜] 연투 최대 ${sheet.bullpen.maxConsecutiveDays}일 / 최근 7일 ${sheet.bullpen.maxPitchesLast7Days}구")
    sheet.bullpen.roles.forEach { (role, ids) ->
        if (ids.isNotEmpty()) {
            println("  ${role.label.padEnd(12)} ${ids.joinToString { league.player(it).registeredName }}")
        }
    }
    println()
    val sub = sheet.substitution
    println("[교체] 대타 ${sub.pinchHitFromInning}회부터(${sub.pinchHitRatingGap}점 이상 나을 때) / 대주자 ${sub.pinchRunFromInning}회부터 / 대수비 ${sub.defensiveSubFromInning}회 ${sub.defensiveSubMinLead}점 리드부터")
    println("[작전] 번트 ${sheet.tactics.bunt} / 도루 ${sheet.tactics.steal} / 고의사구 ${sheet.tactics.intentionalWalk} (1~5)")
    println("[휴식] 피로도 ${sheet.rest.fatigueThreshold} 이상이면 제외, 포수는 ${sheet.rest.catcherRestEveryDays}일에 한 번")
}


/** 정규시즌 진행 (docs/07 주간 루프). */
private fun playSeason(weeks: Int) {
    val (balance, league) = loadLeague()
    val runner = baseballgm.tools.SeasonRunner(balance, league)
    val state = runner.newSeason()
    val loop = baseballgm.season.WeekLoop(balance, league)
    val random = kotlin.random.Random(20260401L)
    val userTeam = league.teams.first { it.id.value == "SWR" }  // 튜토리얼 추천 구단

    println("=== ${league.season} 정규시즌 ===")
    println("기준 구단: ${userTeam.name}")
    var failures = 0
    val started = System.currentTimeMillis()
    repeat(minOf(weeks, state.calendar.regularSeasonWeeks)) {
        if (state.isRegularSeasonOver) return@repeat
        val report = loop.playWeek(state, random, highlightTeam = userTeam.id)
        failures += report.validationProblems.size
        printWeek(state, report, userTeam.id, league)
    }
    val elapsed = System.currentTimeMillis() - started
    println()
    printStandings(state, league)
    println()
    printLeaders(state, league)
    println()
    println("검증 실패 ${failures}건 · ${elapsed}ms")
}

private fun printWeek(
    state: baseballgm.season.SeasonState,
    report: baseballgm.season.WeekReport,
    teamId: baseballgm.model.TeamId,
    league: baseballgm.league.League,
) {
    val record = state.standings.record(teamId)
    val weekGames = report.games.filter { it.home.teamId == teamId || it.away.teamId == teamId }
    val wins = weekGames.count { it.winner == teamId }
    val losses = weekGames.count { !it.tie && it.winner != teamId }
    val ties = weekGames.count { it.tie }
    println(
        "[${state.calendar.label(report.week)}] 주간 ${wins}승 ${losses}패${if (ties > 0) " ${ties}무" else ""} · " +
            "시즌 ${record.wins}승 ${record.losses}패${if (record.ties > 0) " ${record.ties}무" else ""} " +
            "(${"%.3f".format(record.winPct)}) ${state.standings.rankOf(teamId)}위 ${record.streakText()}",
    )
    report.messages.filter { it.teamId == null || it.teamId == teamId }.take(4).forEach {
        println("   · [${it.category.label}] ${it.text}")
    }
    report.highlights.take(2).forEach { println("   > $it") }
    if (report.validationProblems.isNotEmpty()) {
        println("   !! 검증 실패: ${report.validationProblems.first()}")
    }
}

private fun printStandings(state: baseballgm.season.SeasonState, league: baseballgm.league.League) {
    println("순위  구단            승   패   무   승률    게임차  득점  실점")
    state.standings.ranked().forEachIndexed { index, record ->
        val team = league.team(record.teamId)
        println(
            "${(index + 1).toString().padStart(2)}    ${team.name.padEnd(14)}" +
                "${record.wins.toString().padStart(3)} ${record.losses.toString().padStart(3)} ${record.ties.toString().padStart(3)}  " +
                "${"%.3f".format(record.winPct)}  ${"%.1f".format(state.standings.gamesBehind(record.teamId)).padStart(5)}  " +
                "${record.runsScored.toString().padStart(4)}  ${record.runsAllowed.toString().padStart(4)}",
        )
    }
}

private fun printLeaders(state: baseballgm.season.SeasonState, league: baseballgm.league.League) {
    val games = state.standings.ranked().first().games
    val minimumPa = (games * 3.1).toInt()
    val minimumOuts = games * 3
    println("[타자 순위] 규정 타석 $minimumPa")
    state.stats.battingLeaders(minimumPa) { it.battingAverage }.take(5).forEach { (id, line) ->
        val player = league.player(id)
        println("  ${player.registeredName.padEnd(12)} 타율 ${"%.3f".format(line.battingAverage)} ${line.homeRuns}홈런 ${line.rbi}타점 OPS ${"%.3f".format(line.ops)}")
    }
    println("[홈런]")
    state.stats.battingLeaders(0) { it.homeRuns.toDouble() }.take(3).forEach { (id, line) ->
        println("  ${league.player(id).registeredName.padEnd(12)} ${line.homeRuns}홈런")
    }
    println("[투수 순위] 규정 이닝 ${minimumOuts / 3}")
    state.stats.pitchingLeaders(minimumOuts) { -it.era }.take(5).forEach { (id, line) ->
        println("  ${league.player(id).registeredName.padEnd(12)} 평균자책 ${"%.2f".format(line.era)} ${line.wins}승 ${line.losses}패 ${line.strikeouts}탈삼진 ${line.inningsText()}이닝")
    }
}

/** 여러 시즌 평균을 목표와 비교 (M4 캘리브레이션). */
private fun calibrate(seasons: Int) {
    val (balance, league) = loadLeague()
    println("$seasons 시즌 캘리브레이션 시작...")
    val report = baseballgm.tools.Calibrator(balance, league).run(seasons, seed = 4242L)
    println(report.text())
}

/** 대량 시즌 검증 (M4 완료 기준). */
private fun soak(seasons: Int) {
    val (balance, league) = loadLeague()
    println("$seasons 시즌 검증 시작...")
    val (failures, elapsed) = baseballgm.tools.Calibrator(balance, league).soak(seasons, seed = 90210L) { done, fails ->
        if (done % 25 == 0) println("  $done 시즌 완료 (누적 실패 $fails)")
    }
    println("검증 실패 ${failures}건 / $seasons 시즌 (${elapsed}ms, 시즌당 ${"%.0f".format(elapsed.toDouble() / seasons)}ms)")
}


/** 문자 중계 관전 (docs/07). 이벤트 스트림을 문장으로 바꾸기만 한다. */
private fun watchGame(seed: Long) {
    val (balance, league) = loadLeague()
    val runner = baseballgm.tools.GameRunner(balance, league)
    val game = league.schedule.games.first()
    val played = runner.play(game.home, game.away, rotationIndex = 0, seed = seed)
    val renderer = baseballgm.text.CommentaryRenderer { id -> league.player(id).registeredName }

    println("${league.team(game.away).name} @ ${league.team(game.home).name}")
    renderer.render(played.events).forEach { println("  $it") }
    println()
    println("[하이라이트]")
    renderer.highlights(played.events).forEach { println("  · $it") }
}


/** 장기 밸런스 테스트 (M5 완료 기준). 시즌 → 스토브리그 → 다음 시즌을 N번 반복한다. */
private fun longTerm(seasons: Int, seed: Long) {
    val (balance, league) = loadLeague()
    println("$seasons 시즌 연속 진행 (시드 $seed)...")
    val summaries = baseballgm.tools.MultiSeasonRunner(balance, league).run(seasons, seed = seed) { summary ->
        println("  " + summary.line())
    }
    println()
    println(baseballgm.tools.longTermReport(summaries, balance))
}


// ---------- M6. 스카우트·드래프트 ----------

/**
 * 드래프트 풀을 스카우트 시선으로 본다.
 *
 * 같은 선수를 **집중 관찰한 경우와 아닌 경우**로 나란히 찍어서, 관찰이 실제로 범위를 좁히는지
 * 눈으로 확인할 수 있게 했다.
 */
private fun printScouting(teamKey: String) {
    val (balance, league) = loadLeague()
    val team = league.teams.firstOrNull { it.id.value == teamKey } ?: league.teams.first()
    val service = baseballgm.scouting.ScoutingService(balance)
    val pool = league.draftPool

    println("=== ${league.season} 드래프트 풀 ===")
    println("대상 ${pool.prospects.size}명 (고졸 ${pool.highSchoolCount} · 대졸 ${pool.collegeCount}) · 기준 구단 ${team.name}")
    println()

    val department = baseballgm.scouting.ScoutingDepartment(team.id, balance.int("scouting.defaultLevel"))
    val slots = service.focusSlots(department)
    println("투자 단계 ${department.level} — 집중 관찰 슬롯 ${slots}개 · 연간 ${service.budget.annualCost(department.level)}억")
    println()

    val runner = baseballgm.season.DraftRunner(balance, baseballgm.league.StrengthCalculator(balance))
    val state = baseballgm.tools.SeasonRunner(balance, league).newSeason()
    val board = runner.board(state, team.id, limit = 10)

    println("[지명 후보 10명 — 우리 구단 평가 순]")
    board.forEach { (prospect, value) ->
        val report = runner.report(state, team.id, prospect)
        val scouted = report.scouted
        println(
            "  ${scouted.positionLabel.padEnd(3)} ${scouted.name.padEnd(10)} ${prospect.schoolTypeLabel} " +
                "${prospect.school.padEnd(6)} ${scouted.age}세 ${report.physique} " +
                "종합 ${scouted.overall} 잠재 ${scouted.potentialLabel} (평가 ${"%.1f".format(value)})",
        )
    }
    println()

    val sample = board.first().first
    println("[리포트] ${sample.player.registeredName} — 관찰 주차에 따른 변화")
    listOf(0, 4, 10, 18).forEach { weeks ->
        val precision = service.budget.refine(service.budget.amateurPrecision(department.level), weeks)
        val report = baseballgm.scouting.ScoutReportWriter.write(
            player = sample.player,
            precision = precision,
            season = league.season,
            focusWeeks = weeks,
            school = sample.school,
            physique = sample.physique(),
            injuryHistory = sample.injuryHistory,
            scale = service.potentialScale,
        )
        println(
            "  ${weeks.toString().padStart(2)}주 관찰 — 정확도 ${report.accuracyLabel.padEnd(5)} " +
                "종합 ${report.scouted.overall.toString().padEnd(7)} 잠재 ${report.scouted.potentialLabel.padEnd(4)} " +
                "성장 ${growthLabel(report.scouted.growthTypeGuess)}${if (precision.growthTypeReliable) "" else "(추정)"}",
        )
    }
    println()
    println("  코멘트: " + baseballgm.scouting.ScoutReportWriter.write(
        player = sample.player,
        precision = service.budget.refine(service.budget.amateurPrecision(department.level), 18),
        season = league.season,
        focusWeeks = 18,
        school = sample.school,
        physique = sample.physique(),
        injuryHistory = sample.injuryHistory,
        scale = service.potentialScale,
    ).comments.joinToString(" / "))
}

private fun growthLabel(type: baseballgm.model.GrowthType): String = when (type) {
    baseballgm.model.GrowthType.EARLY -> "조기완성"
    baseballgm.model.GrowthType.NORMAL -> "일반"
    baseballgm.model.GrowthType.LATE -> "대기만성"
}

/** 드래프트 주차까지 시즌을 돌리고 지명 결과를 출력한다. */
private fun runDraft(seed: Long) {
    val (balance, league) = loadLeague()
    val state = baseballgm.tools.SeasonRunner(balance, league).newSeason()
    val loop = baseballgm.season.WeekLoop(balance, league)
    val random = kotlin.random.Random(seed)
    val draftWeek = balance.int("season.draftWeek")

    println("=== ${league.season} 신인 드래프트 (${draftWeek}주차) ===")
    val started = System.currentTimeMillis()
    while (state.week <= draftWeek) loop.playWeek(state, random, validate = false)
    val result = state.draftResult ?: error("드래프트가 열리지 않았다")
    val elapsed = System.currentTimeMillis() - started

    println("지명 ${result.selections.size}명 · 미지명 ${result.undrafted.size}명 · ${elapsed}ms")
    println()
    println("[1라운드]")
    result.selections.filter { it.round == 1 }.forEach { selection ->
        val traded = if (selection.teamId != selection.originalTeam) " ← ${selection.originalTeam.value} 지명권" else ""
        println("  ${selection.overallPick.toString().padStart(2)}순위 ${league.team(selection.teamId).name.padEnd(12)} " +
            "${selection.playerName.padEnd(10)} ${selection.positionLabel.padEnd(3)} ${selection.schoolTypeLabel} " +
            "계약금 ${selection.signingBonus}억$traded")
    }
    println()
    val sample = league.teams.first { it.id.value == "SWR" }
    println("[${sample.name} 지명 전체]")
    result.selections.filter { it.teamId == sample.id }.forEach {
        println("  ${it.round}R ${it.overallPick}순위 ${it.playerName} ${it.positionLabel} ${it.schoolTypeLabel} ${it.signingBonus}억")
    }
    println()
    println("고졸 ${result.selections.count { it.schoolTypeLabel == "고졸" }} · 대졸 ${result.selections.count { it.schoolTypeLabel == "대졸" }}")
    println("투수 ${result.selections.count { it.positionLabel == "SP" || it.positionLabel == "RP" }} · " +
        "야수 ${result.selections.count { it.positionLabel != "SP" && it.positionLabel != "RP" }}")
}

/** 지명권 가치표를 뽑는다 (docs/10). 결과를 config/balance.json 의 draftPickValue 에 넣는다. */
private fun printPickValue(seasons: Int, repeats: Int) {
    val (balance, league) = loadLeague()
    val tool = baseballgm.tools.DraftPickValueTool(balance, league)

    println("=== 지명권 가치표 생성 (${seasons}시즌 × ${repeats}회, 추적 ${baseballgm.tools.DraftPickValueTool.DEFAULT_HORIZON}시즌) ===")
    val started = System.currentTimeMillis()
    val merged = mutableMapOf<Int, MutableList<Pair<Int, Double>>>()
    repeat(repeats) { round ->
        tool.run(seasons, seed = 7000L + round * 131L).forEach { outcome ->
            merged.getOrPut(outcome.overallPick) { mutableListOf() } += outcome.samples to outcome.averageValue
        }
        println("  ${round + 1}회 완료 (${(System.currentTimeMillis() - started) / 1000}초)")
    }
    val outcomes = merged.entries.sortedBy { it.key }.map { (pick, list) ->
        val samples = list.sumOf { it.first }
        baseballgm.tools.PickOutcome(pick, samples, list.sumOf { it.first * it.second } / samples)
    }
    val anchors = tool.anchors(outcomes)

    println()
    println("순번 구간   표본   대체수준 초과 종합   1순위 대비")
    anchors.forEach { anchor ->
        println("  ${anchor.overallPick.toString().padStart(3)}~   ${anchor.samples.toString().padStart(4)}   " +
            "${"%.2f".format(anchor.value).padStart(8)}        ${"%.2f".format(anchor.normalized)}")
    }
    println()
    println("config/balance.json 의 draftPickValue 에 넣을 값:")
    println("  " + tool.asConfigLine(anchors))
}


// ---------- M7. 세이버 지표 ----------

/** 한 시즌을 돌리고 리그 상수와 WAR 을 출력한다 (docs/04 2·3단계). */
private fun printSabermetrics(seed: Long) {
    val (balance, league) = loadLeague()
    println("한 시즌 진행 중...")
    val result = baseballgm.tools.SeasonRunner(balance, league).playSeason(seed, validate = false)
    println()
    println(baseballgm.tools.SabermetricsReport(balance, league).of(result.state).text())
}

/** 능력치 → 기대 WAR 표를 뽑는다 (M7 가치 평가). */
private fun printWarCurve(seasons: Int) {
    val (balance, league) = loadLeague()
    println("${seasons}시즌 진행 중...")
    val tool = baseballgm.tools.WarCurveTool(balance, league)
    val curves = tool.run(seasons, seed = 909L)

    listOf("타자" to curves.batter, "선발" to curves.starter, "불펜" to curves.reliever).forEach { (label, buckets) ->
        println()
        println("[$label] 종합 능력치 구간별 평균 WAR")
        buckets.forEach { bucket ->
            println("  ${bucket.overall.toString().padStart(3)}~  표본 ${bucket.samples.toString().padStart(5)}  WAR ${"%6.2f".format(bucket.averageWar)}")
        }
        println("  config: " + tool.asConfigLine(buckets))
    }
}


// ---------- M7. 시장 ----------

/** 한 시즌과 스토브리그를 돌리고 시장(트레이드·FA·소프트캡)에서 일어난 일을 출력한다. */
private fun printMarket(seed: Long) {
    val (balance, league) = loadLeague()
    val strength = StrengthCalculator(balance)
    println("한 시즌 진행 중...")
    val result = baseballgm.tools.SeasonRunner(balance, league).playSeason(seed, validate = false)
    val state = result.state

    println()
    println("=== 시즌 중 트레이드 ===")
    val trades = state.inbox.all().filter { it.category == baseballgm.season.InboxCategory.TRADE }
        .map { it.text }.distinct()
    if (trades.isEmpty()) println("  (없음)") else trades.forEach { println("  $it") }

    println()
    println("=== 구단 모드 ===")
    val tradeService = baseballgm.season.TradeService(balance, strength)
    league.teams.forEach { team ->
        val record = state.standings.record(team.id)
        println(
            "  ${team.name.padEnd(12)} ${record.wins}승 ${record.losses}패 · " +
                "${tradeService.modeOf(state, team.id).label} · 연봉 ${"%.1f".format(league.payrollOf(team.id))}억",
        )
    }

    println()
    println("스토브리그 진행 중...")
    val (next, report) = baseballgm.season.Offseason(balance, strength).run(
        state = state,
        random = kotlin.random.Random(seed),
        rookieSupplier = baseballgm.tools.RookieFactory(balance, strength, league),
        prospectSupplier = baseballgm.tools.ProspectFactory(
            balance = balance,
            strength = strength,
            seed = seed,
            startingIdNumber = baseballgm.tools.nextPlayerIdNumber(league.players, league.draftPool.prospects),
        ),
    )

    println()
    println("=== 소프트캡 (상한 ${balance.double("softCap.cap")}억) ===")
    if (report.capPenalties.isEmpty()) {
        println("  초과 구단 없음")
    } else {
        report.capPenalties.forEach { println("  " + it.message(league.team(it.teamId).name)) }
    }

    println()
    println("=== FA 시장 ===")
    println("  시장에 나온 선수 ${report.freeAgents.size}명 · 계약 ${report.faSignings.size}건")
    report.faSignings.sortedByDescending { it.offer.salary }.take(15).forEach { signing ->
        val player = next.players.firstOrNull { it.id == signing.playerId }
            ?: league.players.first { it.id == signing.playerId }
        val moved = if (signing.offer.teamId == signing.previousTeam) "잔류" else "이적"
        val compensation = signing.compensation?.let { comp ->
            val name = comp.playerId?.let { id -> next.players.firstOrNull { it.id == id }?.registeredName }
            " · 보상 ${"%.1f".format(comp.cash)}억" + (name?.let { "+$it" } ?: "")
        } ?: ""
        println(
            "  ${player.registeredName.padEnd(10)} ${next.team(signing.offer.teamId).name.padEnd(12)} " +
                "${"%5.1f".format(signing.offer.salary)}억 × ${signing.offer.years}년 ($moved)$compensation",
        )
    }

    println()
    println("=== 스토브리그 요약 ===")
    println("  " + report.summary())
    println("  선수 ${league.players.size}명 → ${next.players.size}명")
    val payrolls = next.teams.map { next.payrollOf(it.id) }
    println("  연봉 총액 ${"%.1f".format(payrolls.min())} ~ ${"%.1f".format(payrolls.max())}억")
    println("  운용 자금 " + next.teams.joinToString(" ") { "${it.id.value} ${"%.0f".format(it.operatingFunds)}" })
}


// ---------- M8. 외국인·군 복무·국제대회 ----------

/** 외국인 시장과 적응력을 확인한다 (docs/12). */
private fun printForeign(seed: Long) {
    val (balance, league) = loadLeague()
    val strength = StrengthCalculator(balance)
    val service = baseballgm.season.ForeignService(balance, strength)

    println("=== ${league.season} 외국인 시장 (${league.foreignPool.candidates.size}명) ===")
    println("보유 ${balance.int("foreignPlayers.maxPerTeam")}명 · 한쪽 최대 ${balance.int("foreignPlayers.maxSameType")}명 · " +
        "신규 상한 ${balance.double("foreignPlayers.newContractMaxTotal")}억 · " +
        "시즌 중 교체 ${balance.int("foreignPlayers.inSeasonReplacements")}회(${balance.int("foreignPlayers.replacementDeadlineWeek")}주차 마감)")
    println()
    println("   선수          출신        요구   종합(스카우트)  KBO 환산 기록")
    league.foreignPool.candidates.sortedByDescending { strength.overallOf(it.player) }.take(12).forEach { candidate ->
        val scouted = baseballgm.scouting.ScoutingView.of(
            candidate.player,
            baseballgm.scouting.ScoutingAccuracy.MINIMAL,
            league.season,
            baseballgm.scouting.PotentialScale.from(balance),
        )
        println(
            "  ${candidate.player.registeredName.padEnd(10)} ${candidate.originLabel.padEnd(10)} " +
                "${"%5.2f".format(candidate.askingSalary)}억 ${scouted.overall.toString().padEnd(8)} " +
                service.market.convertedLine(candidate).text(),
        )
    }

    println()
    println("=== 적응력 (docs/12) — 같은 능력치라도 적응 감점이 다르다 ===")
    println("보유 외국인의 주차별 감점:")
    league.teams.take(3).forEach { team ->
        league.foreignersOf(team.id).forEach { player ->
            val weeks = listOf(1, 4, 8, 16, 24)
            val penalties = weeks.map { week ->
                "%d주 -%.1f".format(week, service.adaptation.penaltyFor(player, league.season, week))
            }
            println("  ${team.name.padEnd(12)} ${player.registeredName.padEnd(10)} ${penalties.joinToString(" · ")}")
        }
    }

    println()
    println("한 시즌 진행 중...")
    val result = baseballgm.tools.SeasonRunner(balance, league).playSeason(seed, validate = false)
    val constants = baseballgm.stats.LeagueConstants.from(result.state.stats, balance)
    val war = baseballgm.stats.War(balance, baseballgm.stats.Sabermetrics(balance))

    println()
    println("=== 외국인 성적 (적응 감점이 기록에 드러난다) ===")
    println("   선수          팀           적응 감점  WAR   기록")
    result.state.allPlayers().filter { it.isForeign }.sortedByDescending {
        war.of(it, result.state.stats, constants).war
    }.take(12).forEach { player ->
        val teamName = player.teamId?.let { league.team(it).name } ?: "무소속"
        val line = if (player is baseballgm.model.Pitcher) {
            val stats = result.state.stats.pitchingOf(player.id).total
            "${stats.inningsText()}이닝 ${"%.2f".format(stats.era)}"
        } else {
            val stats = result.state.stats.battingOf(player.id).total
            "${stats.plateAppearances}타석 ${"%.3f".format(stats.battingAverage)} ${stats.homeRuns}홈런"
        }
        println(
            "  ${player.registeredName.padEnd(10)} ${teamName.padEnd(12)} " +
                "${"%6.1f".format(player.condition.adaptationPenalty)}  " +
                "${"%5.1f".format(war.of(player, result.state.stats, constants).war)}  $line",
        )
    }

    println()
    println("스토브리그 진행 중...")
    val (next, report) = offseasonOf(balance, league, result.state, seed)
    println("외국인 계약 ${report.foreignSignings.size}건 · 이탈 ${report.foreignDepartures.size}건")
    report.foreignDepartures.forEach { departure ->
        val name = league.player(departure.playerId).registeredName
        println("  ${league.team(departure.teamId).name} $name — ${departure.reason}")
    }
    report.foreignSignings.filterNot { it.reSigned }.forEach { signing ->
        val name = next.player(signing.playerId).registeredName
        println("  ${next.team(signing.teamId).name} $name 영입 (${signing.originLabel}, ${signing.salary}억)")
    }
    println()
    println("다음 시즌 팀별 외국인: " + next.teams.joinToString(" ") { "${it.id.value} ${next.foreignersOf(it.id).size}" })
}

/** 국제대회와 군 복무를 확인한다 (docs/12). */
private fun printNationalTeam(seed: Long) {
    val (balance, league) = loadLeague()
    val strength = StrengthCalculator(balance)
    val tournaments = baseballgm.events.InternationalTournament(balance, strength)
    val rules = tournaments.rulesFor(league.season)

    println("=== ${league.season} 국제대회 ===")
    if (rules == null) {
        println("올해는 대회가 없다")
    } else {
        println("${rules.label} · ${rules.startWeek}주차부터 ${rules.weeksMissed}주 · " +
            "연령 제한 ${if (rules.ageLimit == 0) "없음" else "${rules.ageLimit}세"} · " +
            "와일드카드 ${rules.wildcards}명 · 팀당 최대 ${rules.maxPerTeam}명 · 특례 ${rules.exemption}")
    }

    println()
    println("한 시즌 진행 중...")
    val result = baseballgm.tools.SeasonRunner(balance, league).playSeason(seed, validate = false)
    val state = result.state

    state.tournamentResult?.let { tournament ->
        println()
        println("=== 대표팀 ===")
        println("전력 ${"%.1f".format(tournaments.squadStrength(league, tournament.squad))} · ${tournament.medal.label}")
        tournament.scores.forEach { println("  $it") }
        println()
        println("   선수          팀           나이  종합  구분")
        tournament.squad.sortedByDescending { strength.overallOf(league.player(it.playerId)) }.take(14).forEach { member ->
            val player = league.player(member.playerId)
            println(
                "  ${player.registeredName.padEnd(10)} ${league.team(member.teamId).name.padEnd(12)} " +
                    "${player.ageIn(league.season)}세  ${"%4.1f".format(strength.overallOf(player))}  " +
                    (if (member.wildcard) "와일드카드" else "") +
                    (if (member.playerId in tournament.exempted) " 병역특례" else ""),
            )
        }
        println()
        println("병역 특례 ${tournament.exempted.size}명")
    }

    println()
    println("스토브리그 진행 중...")
    val (_, report) = offseasonOf(balance, league, state, seed)
    println()
    println("=== 군 복무 ===")
    println("입대 ${report.enlistments.size}명 (상무 ${report.enlistments.count { it.kind == baseballgm.model.ServiceKind.SANGMU }} · " +
        "현역 ${report.enlistments.count { it.kind == baseballgm.model.ServiceKind.ACTIVE_DUTY }}) · 제대 ${report.discharged.size}명")
    report.enlistments.take(12).forEach { println("  " + it.message(league.player(it.playerId).registeredName)) }
    println()
    println("입대 기한 임박 ${report.enlistmentWarnings.size}명")
    report.enlistmentWarnings.take(5).forEach { println("  " + it.message(league.player(it.playerId).registeredName)) }
    println()
    println("리그 환경 발표: ${report.environmentAnnouncement}")
}

/** 콘솔 확인용 스토브리그 한 번. */
private fun offseasonOf(
    balance: baseballgm.io.BalanceConfig,
    league: League,
    state: baseballgm.season.SeasonState,
    seed: Long,
): Pair<League, baseballgm.season.OffseasonReport> {
    val strength = StrengthCalculator(balance)
    val startingId = baseballgm.tools.nextPlayerIdNumber(league.players, league.draftPool.prospects)
    return baseballgm.season.Offseason(balance, strength).run(
        state = state,
        random = kotlin.random.Random(seed),
        rookieSupplier = baseballgm.tools.RookieFactory(balance, strength, league),
        prospectSupplier = baseballgm.tools.ProspectFactory(balance, strength, seed, startingId),
        foreignSupplier = baseballgm.tools.ForeignFactory(balance, strength, seed, startingId + 500),
    )
}


// ---------- M9. 경영·커리어 ----------

/** 한 시즌 + 포스트시즌 + 경영 결산을 출력한다 (docs/13, 14). */
private fun printClub(seed: Long) {
    val (balance, league) = loadLeague()
    val strength = StrengthCalculator(balance)
    println("한 시즌 진행 중...")
    val result = baseballgm.tools.SeasonRunner(balance, league).playSeason(seed, validate = false)
    val state = result.state

    println()
    println("=== ${league.season} 포스트시즌 ===")
    val postseason = result.postseason
    if (postseason == null) {
        println("  (치르지 않음)")
    } else {
        println("진출: " + postseason.participants.mapIndexed { index, id -> "${index + 1}위 ${league.team(id).name}" }
            .joinToString(" · "))
        postseason.series.forEach { println("  " + it.line { id -> league.team(id).name }) }
        println("  ** ${postseason.summary { id -> league.team(id).name }} **")
    }

    println()
    println("스토브리그 진행 중...")
    val (next, report) = offseasonOf(balance, league, state, seed)
    val review = report.review!!

    println()
    println("=== 재정 결산 (docs/13) ===")
    println("구단          관중   중계  스폰서  PS   모기업 |  연봉   계약금 스태프 스카웃 제재 | 수지    자금")
    review.teams.sortedBy { league.team(it.teamId).name }.forEach { team ->
        val r = team.finance.revenue
        val e = team.finance.expenses
        val net = r.total - e.total
        println(
            "${league.team(team.teamId).name.padEnd(12)} " +
                "${"%5.1f".format(r.attendance)} ${"%5.1f".format(r.broadcast)} ${"%5.1f".format(r.sponsorship)} " +
                "${"%4.1f".format(r.postseason)} ${"%6.1f".format(r.parentSupport)} | " +
                "${"%5.1f".format(e.payroll)} ${"%5.1f".format(e.signingBonus)} ${"%5.1f".format(e.staff)} " +
                "${"%5.1f".format(e.scouting)} ${"%4.1f".format(e.penalties)} | " +
                "${"%6.1f".format(net)} ${"%6.1f".format(team.finance.fundsAfter)}",
        )
    }

    println()
    println("=== 팬심·구단주 (docs/13) ===")
    println("구단          팬심        관중률  모기업   목표                 결과      신뢰도")
    review.teams.sortedByDescending { it.trust.trustAfter }.forEach { team ->
        println(
            "${league.team(team.teamId).name.padEnd(12)} " +
                "${team.fanBefore}→${team.fanAfter}".padEnd(10) +
                " ${"%.0f%%".format(team.finance.attendanceRate * 100).padEnd(6)} " +
                "${team.parent.cycle.label.padEnd(6)} ${team.trust.goal.description.padEnd(20)} " +
                "${team.trust.outcome.label.padEnd(9)} ${team.trust.trustBefore}→${team.trust.trustAfter}" +
                if (team.trust.fired) " (해임)" else "",
        )
    }

    println()
    println("=== 스태프 시장 ===")
    if (report.staffHires.isEmpty()) {
        println("  (고용 없음)")
    } else {
        report.staffHires.forEach { println("  ${next.team(it.teamId).name} ${it.roleLabel} ${it.name} (${it.salary}억)") }
    }
    println()
    println("리그 환경: ${report.environmentAnnouncement}")
}

/** 여러 시즌을 커리어 모드로 진행한다 (docs/13, 14). */
private fun printCareer(seasons: Int, teamKey: String) {
    val (balance, league) = loadLeague()
    val strength = StrengthCalculator(balance)
    val userTeam = (league.teams.firstOrNull { it.id.value == teamKey } ?: league.teams.first()).id
    val career = baseballgm.management.CareerRecord(
        gmName = "유저 단장",
        reputation = balance.int("career.startReputation"),
    )
    var current = league.copy(
        management = league.management.copy(career = career, userTeam = userTeam),
    )

    println("=== 커리어 시작: ${current.team(userTeam).name} (목표 '${current.team(userTeam).ownerGoal}') ===")
    repeat(seasons) { index ->
        val seed = 3000L + index
        val result = baseballgm.tools.SeasonRunner(balance, current).playSeason(seed, validate = false)
        val (next, report) = offseasonOf(balance, current, result.state, seed)
        val review = report.review!!
        val mine = current.management.userTeam ?: userTeam
        val row = review.management.career?.seasons?.lastOrNull()

        println()
        println("[${current.season}] " + (row?.line() ?: "기록 없음"))
        result.postseason?.let { println("  " + it.summary { id -> current.team(id).name }) }
        review.of(mine)?.let { team ->
            println("  " + team.finance.line() + " · 팬심 ${team.fanBefore}→${team.fanAfter}")
            println("  " + team.trust.message(current.team(mine).name))
        }
        if (review.newAchievements.isNotEmpty()) {
            println("  업적 달성: " + review.newAchievements.joinToString(", ") { it.label })
        }
        if (review.userFired) println("  ** 해임 **")
        if (review.management.userTeam == null) {
            if (review.offers.isEmpty()) {
                println("  제안이 없다 — 한 해 해설위원으로 지낸다")
            } else {
                val offer = review.offers.first()
                println("  이직 제안 " + review.offers.joinToString(", ") { "${it.teamName}(${it.expectation})" } +
                    " → ${offer.teamName} 수락")
                current = next.copy(management = next.management.copy(userTeam = offer.teamId))
                return@repeat
            }
        }
        current = next
    }

    val finalCareer = current.management.career
    if (finalCareer != null) {
        println()
        println("=== 커리어 기록 ===")
        finalCareer.seasons.forEach { println("  " + it.line()) }
        println()
        println(finalCareer.summary())
        println("업적 ${finalCareer.unlockedAchievements.size}개: " +
            finalCareer.unlockedAchievements.mapNotNull { baseballgm.management.Achievements.byId(it)?.label }
                .joinToString(", "))
        println("명예의 전당 평가: " + baseballgm.management.Achievements.hallOfFameGrade(finalCareer))
    }
}

/** 장기 밸런스 기본 시드. 예전 기록과 비교할 수 있게 그대로 둔다 */
private const val LONG_TERM_SEED = 5150L
