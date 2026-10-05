package baseballgm.app

import baseballgm.app.generated.resources.Res
import baseballgm.io.BalanceConfig
import baseballgm.io.LeagueLoader
import baseballgm.league.League
import baseballgm.league.StrengthCalculator
import baseballgm.model.TeamId
import baseballgm.tools.ForeignFactory
import baseballgm.tools.ProspectFactory
import baseballgm.tools.RookieFactory
import baseballgm.tools.TeamTemplate
import baseballgm.tools.TeamTemplates
import baseballgm.tools.nextPlayerIdNumber
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * [GameHost] 만들기. 플랫폼마다 다른 것은 "데이터를 어디서 읽나"와 "세이브를 어디에 두나"뿐이라
 * 나머지(생성기 연결)는 여기 한 곳에 둔다.
 */
object HostFactory {

    /**
     * @param leagueText 고정 리그 JSON. 1MB 가 넘어 새 게임을 고른 뒤에야 읽는다
     * @param exitApp 앱을 스스로 끝낼 수 없는 플랫폼이면 null
     */
    fun create(
        balanceText: String,
        teamsText: String,
        leagueText: suspend () -> String,
        saveStore: SaveStore,
        exitApp: (() -> Unit)?,
    ): GameHost {
        val balance = BalanceConfig.parse(balanceText)
        val templates = TeamTemplates.parse(teamsText)
        return GameHost(
            balance = balance,
            teams = templates.teams.map(::toChoice),
            saveStore = saveStore,
            loadStartingLeague = { LeagueLoader.parse(leagueText()) },
            rookieSupplier = { current -> RookieFactory(balance, StrengthCalculator(balance), current) },
            prospectSupplier = { current: League ->
                ProspectFactory(
                    balance = balance,
                    strength = StrengthCalculator(balance),
                    seed = current.seed + current.season,
                    startingIdNumber = nextPlayerIdNumber(current.players, current.draftPool.prospects),
                )
            },
            foreignSupplier = { current: League ->
                ForeignFactory(
                    balance = balance,
                    strength = StrengthCalculator(balance),
                    seed = current.seed - current.season,
                    startingIdNumber = nextPlayerIdNumber(current.players, current.draftPool.prospects) +
                        current.draftPool.prospects.size,
                )
            },
            exitApp = exitApp,
        )
    }

    /** 앱에 넣어 둔 데이터로 만든다 (웹·iOS). 원본은 빌드 때 복사된다 — app/build.gradle.kts */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun fromBundledResources(saveStore: SaveStore, exitApp: (() -> Unit)?): GameHost = create(
        balanceText = Res.readBytes(BALANCE_FILE).decodeToString(),
        teamsText = Res.readBytes(TEAMS_FILE).decodeToString(),
        leagueText = { Res.readBytes(LEAGUE_FILE).decodeToString() },
        saveStore = saveStore,
        exitApp = exitApp,
    )

    private fun toChoice(template: TeamTemplate) = TeamChoice(
        id = TeamId(template.id),
        name = template.name,
        nickname = template.nickname,
        keyword = template.keyword,
        tier = template.tier,
        recommendedForTutorial = template.recommendedForTutorial,
        hardest = template.difficulty == "hardest",
        lineup = template.targets.lineup,
        rotation = template.targets.rotation,
        bullpen = template.targets.bullpen,
        overall = template.targets.overall,
        payroll = template.payroll,
        operatingFunds = template.operatingFunds,
        draftPick = template.draftPick,
        ownerGoal = template.ownerGoal,
        color = template.color,
        colorDark = template.colorDark,
    )

    private const val BALANCE_FILE = "files/balance.json"
    private const val TEAMS_FILE = "files/teams.json"
    private const val LEAGUE_FILE = "files/league_2026.json"
}
