package baseballgm.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import baseballgm.io.LeagueLoader
import baseballgm.league.StrengthCalculator
import baseballgm.model.TeamId
import baseballgm.tools.ProjectFiles
import baseballgm.tools.RookieFactory

/**
 * 데스크톱(맥·윈도) 진입점.
 *
 * 파일 읽기는 여기서만 한다 — 엔진과 화면 코드는 파일 시스템을 모른다 (불변 원칙 1).
 * Android/iOS 를 붙일 때는 각 플랫폼의 진입점에서 같은 데이터를 읽어 [App] 에 넘기면 된다.
 */
fun main() = application {
    val balance = ProjectFiles.loadBalanceConfig()
    val templates = ProjectFiles.loadTeamTemplates()
    val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
    val recommended = templates.teams.firstOrNull { it.recommendedForTutorial }?.let { TeamId(it.id) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "야구 단장 ${league.season}",
        state = rememberWindowState(size = DpSize(430.dp, 900.dp)),
    ) {
        App(balance, league, recommended) { current ->
            RookieFactory(balance, StrengthCalculator(balance), current)
        }
    }
}
