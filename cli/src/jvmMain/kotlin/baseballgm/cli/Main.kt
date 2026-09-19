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
 *   calibrate [N]  N시즌 평균을 캘리브레이션 목표와 비교
 *   soak [N]       N시즌 검증 (M4 완료 기준: 1,000시즌 실패 0)
 *   longterm [N]   N시즌 연속 진행 + 장기 밸런스 확인 (M5 완료 기준: 30시즌)
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
        "calibrate" -> calibrate(args.getOrNull(1)?.toIntOrNull() ?: 5)
        "soak" -> soak(args.getOrNull(1)?.toIntOrNull() ?: 50)
        "longterm" -> longTerm(args.getOrNull(1)?.toIntOrNull() ?: 30)
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
    strengthOrder.take(8).forEach { player ->
        val own = ScoutingView.of(player, ScoutingAccuracy.OWN_TEAM, league.season)
        val rival = ScoutingView.of(player, ScoutingAccuracy.MEDIUM, league.season)
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
private fun longTerm(seasons: Int) {
    val (balance, league) = loadLeague()
    println("$seasons 시즌 연속 진행...")
    val summaries = baseballgm.tools.MultiSeasonRunner(balance, league).run(seasons, seed = 5150L) { summary ->
        println("  " + summary.line())
    }
    println()
    println(baseballgm.tools.longTermReport(summaries, balance))
}
