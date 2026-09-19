package baseballgm.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import baseballgm.app.screen.DirectiveScreen
import baseballgm.app.screen.HomeScreen
import baseballgm.app.screen.PlayerDetailScreen
import baseballgm.app.screen.RecordsScreen
import baseballgm.app.screen.RosterScreen
import baseballgm.app.screen.TeamSelectScreen
import baseballgm.app.screen.WatchScreen
import baseballgm.app.ui.AppTheme
import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.StrengthCalculator
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.season.RookieSupplier

/** 하단 탭. M6~M9 기능(시장·스카우트·구단·커리어)이 붙으면 여기에 늘어난다. */
private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Filled.Home),
    ROSTER("로스터", Icons.Filled.Groups),
    RECORDS("기록실", Icons.Filled.ListAlt),
    DIRECTIVE("지시", Icons.Filled.Rule),
    WATCH("중계", Icons.Filled.SportsBaseball),
}

/**
 * 앱 뿌리.
 *
 * 구단을 고르면 [GameSession] 이 만들어지고, 그 뒤로는 모든 화면이 세션 하나만 본다.
 * 화면 코드는 엔진 상태를 직접 고치지 않는다 (불변 원칙 1).
 */
@Composable
fun App(
    balance: BalanceConfig,
    league: League,
    recommendedTeam: TeamId?,
    rookieSupplier: (League) -> RookieSupplier,
) {
    var session by remember { mutableStateOf<GameSession?>(null) }
    val strength = remember(balance) { StrengthCalculator(balance) }

    AppTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val current = session
            if (current == null) {
                TeamSelectScreen(league, strength, recommendedTeam) { teamId ->
                    session = GameSession(balance, league, teamId, rookieSupplier)
                }
            } else {
                MainScreen(current)
            }
        }
    }
}

@Composable
private fun MainScreen(session: GameSession) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var detailPlayer by remember { mutableStateOf<PlayerId?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry && detailPlayer == null,
                        onClick = {
                            tab = entry
                            detailPlayer = null
                        },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val detail = detailPlayer
            when {
                detail != null -> PlayerDetailScreen(session, detail) { detailPlayer = null }
                tab == Tab.HOME -> HomeScreen(session)
                tab == Tab.ROSTER -> RosterScreen(session) { detailPlayer = it }
                tab == Tab.RECORDS -> RecordsScreen(session) { detailPlayer = it }
                tab == Tab.DIRECTIVE -> DirectiveScreen(session)
                else -> WatchScreen(session)
            }
        }
    }
}
