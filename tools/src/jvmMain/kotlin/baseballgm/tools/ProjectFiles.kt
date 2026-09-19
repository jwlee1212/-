package baseballgm.tools

import baseballgm.io.BalanceConfig
import java.io.File

/**
 * 저장소 안의 설정/데이터 파일을 읽는 JVM 전용 헬퍼.
 *
 * 엔진(commonMain)은 파일 시스템을 모르고 문자열만 받는다(불변 원칙 1). 파일을 찾아 읽는 책임은
 * 여기(도구·콘솔 쪽)에 둔다.
 *
 * 저장소 루트는 `-Dbaseballgm.root=...` 로 지정할 수 있고, 없으면 현재 디렉터리에서 위로 올라가며
 * `config/balance.json` 과 `data/teams.json` 이 함께 있는 폴더를 찾는다.
 */
object ProjectFiles {

    const val BALANCE_PATH: String = "config/balance.json"
    const val TEAMS_PATH: String = "data/teams.json"

    val root: File by lazy { locateRoot() }

    fun read(relativePath: String): String {
        val file = File(root, relativePath)
        if (!file.isFile) error("파일을 찾지 못했다: ${file.absolutePath}")
        return file.readText(Charsets.UTF_8)
    }

    fun loadBalanceConfig(): BalanceConfig = BalanceConfig.parse(read(BALANCE_PATH))

    fun loadTeamTemplates(): TeamTemplates = TeamTemplates.parse(read(TEAMS_PATH))

    fun write(relativePath: String, text: String) {
        val file = File(root, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(text, Charsets.UTF_8)
    }

    /** 생성된 고정 리그 데이터 경로. */
    fun leaguePath(season: Int): String = "data/league_" + season + ".json"

    private fun locateRoot(): File {
        System.getProperty("baseballgm.root")?.let { return File(it) }
        var current: File? = File(System.getProperty("user.dir")).absoluteFile
        while (current != null) {
            if (File(current, BALANCE_PATH).isFile && File(current, TEAMS_PATH).isFile) return current
            current = current.parentFile
        }
        error("저장소 루트를 찾지 못했다. -Dbaseballgm.root 로 경로를 지정해라. (현재 위치: ${System.getProperty("user.dir")})")
    }
}
