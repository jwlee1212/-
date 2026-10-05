package baseballgm.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import baseballgm.tools.ProjectFiles
import java.io.File

/**
 * 데스크톱(맥·윈도) 진입점.
 *
 * 데스크톱은 개발용이라 데이터를 **프로젝트 파일에서 바로** 읽는다 — json 을 고치고 다시 켜면 바로 반영된다.
 * 웹·iOS 는 빌드 때 앱에 넣은 사본을 읽는다 (HostFactory.fromBundledResources).
 *
 * 세이브는 `~/.baseballgm/save.json` 에 둔다. 새 게임부터 다시 보고 싶으면 이 파일을 지우면 된다.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = AppInfo.FULL_TITLE,
        state = rememberWindowState(size = DpSize(430.dp, 900.dp)),
    ) {
        App(loadHost = {
            HostFactory.create(
                balanceText = ProjectFiles.read(ProjectFiles.BALANCE_PATH),
                teamsText = ProjectFiles.read(ProjectFiles.TEAMS_PATH),
                leagueText = { ProjectFiles.read(ProjectFiles.leaguePath(ProjectFiles.loadTeamTemplates().season)) },
                saveStore = FileSaveStore(File(System.getProperty("user.home"), ".baseballgm/save.json")),
                exitApp = ::exitApplication,
            )
        })
    }
}

/**
 * 파일 하나짜리 세이브 저장소.
 *
 * 임시 파일에 먼저 쓰고 바꿔 끼운다 — 저장 도중 앱이 꺼져도 이전 세이브가 반쯤 덮이지 않게 하려는 것이다.
 */
class FileSaveStore(private val file: File) : SaveStore {
    override fun read(): String? = file.takeIf { it.isFile }?.readText()

    override fun write(text: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(text)
        java.nio.file.Files.move(
            temp.toPath(),
            file.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
        )
    }
}
