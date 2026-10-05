package baseballgm.app

import baseballgm.io.LeagueLoader
import baseballgm.model.TeamId
import baseballgm.tools.ProjectFiles
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 디자인 규칙(docs/16)과 비서 브리핑 문장을 검사한다.
 */
class DesignRulesTest {

    // ---------- 토큰 규칙 ----------

    @Test
    fun `화면 코드에는 색상값을 직접 쓰지 않는다`() {
        val screenDir = File(ProjectFiles.root, "app/src/commonMain/kotlin/baseballgm/app/screen")
        val colorLiteral = Regex("""Color\(0x|Color\.(White|Black|Red|Green|Blue|Gray|Yellow)\b|#[0-9A-Fa-f]{6}\b""")
        val offenders = screenDir.walk().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (colorLiteral.containsMatchIn(line)) "${file.name}:${index + 1}  ${line.trim()}" else null
            }
        }.toList()
        assertEquals(emptyList(), offenders, "색은 app/ui/Tokens.kt 의 토큰으로만 고른다")
    }

    // ---------- 절제 규칙 (docs/16, 2026-10-01) ----------

    /** 화면·공용 컴포넌트 코드. 주석 줄은 뺀다 (설명 안의 "▲" 같은 글자는 괜찮다) */
    private fun uiCodeLines(): List<Pair<String, String>> {
        val dirs = listOf("screen", "ui").map { File(ProjectFiles.root, "app/src/commonMain/kotlin/baseballgm/app/$it") }
        return dirs.flatMap { dir ->
            dir.walk().filter { it.extension == "kt" }.flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val code = line.trim()
                    if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) null
                    else "${file.name}:${index + 1}" to line.substringBefore(" // ")
                }
            }
        }
    }

    private fun offenders(pattern: Regex) =
        uiCodeLines().filter { (_, line) -> pattern.containsMatchIn(line) }.map { (where, line) -> "$where  ${line.trim()}" }

    @Test
    fun `간격은 토큰만 쓴다`() {
        // padding·Spacer·spacedBy 안에 숫자 dp 가 오면 안 된다 (4·8·12·16·24·32 도 토큰 이름으로)
        val literal = Regex("""(\.padding\(|spacedBy\(|PaddingValues\(|Spacer\(Modifier\.(width|height)\()[^)]*\b\d+(\.\d+)?\.dp""")
        assertEquals(emptyList(), offenders(literal), "간격은 AppTheme.tokens.spacing 으로")
    }

    @Test
    fun `굵기는 Regular 와 Bold 두 가지만`() {
        assertEquals(emptyList(), offenders(Regex("""FontWeight\.(Thin|ExtraLight|Light|Medium|SemiBold|ExtraBold|Black)""")))
    }

    @Test
    fun `글자 크기는 테마의 세 단계만 - 화면에서 sp 를 직접 쓰지 않는다`() {
        // Theme.kt 는 세 단계 크기 토큰을 정의하는 곳이라 뺀다
        val found = offenders(Regex("""\b\d+(\.\d+)?\.sp\b|fontSize\s*=""")).filterNot { it.startsWith("Theme.kt:") }
        assertEquals(emptyList(), found)
    }

    @Test
    fun `그라데이션 등 금지 효과를 쓰지 않는다`() {
        assertEquals(emptyList(), offenders(Regex("""Brush\.|Gradient|\.blur\(|\.shadow\(""")))
    }

    @Test
    fun `이모지와 글자 아이콘을 쓰지 않는다 - 아이콘은 한 세트`() {
        // 이모지(보조 평면·기호 블록)와 화살표·삼각형 글자. 아이콘이 필요하면 Material 아이콘 세트에서
        val glyph = Regex("""[\x{1F000}-\x{1FFFF}\x{2600}-\x{27BF}›‹»«▲▼▶◀①②③④⑤]""")
        val inStrings = uiCodeLines().filter { (_, line) ->
            Regex("\"[^\"]*\"").findAll(line).any { glyph.containsMatchIn(it.value) }
        }.map { (where, line) -> "$where  ${line.trim()}" }
        assertEquals(emptyList(), inStrings)
    }

    @Test
    fun `모든 구단에 라이트 다크 구단 색이 있다`() {
        val templates = ProjectFiles.loadTeamTemplates()
        templates.teams.forEach { team ->
            assertTrue(team.color.matches(Regex("#[0-9A-Fa-f]{6}")), "${team.id} color")
            assertTrue(team.colorDark.matches(Regex("#[0-9A-Fa-f]{6}")), "${team.id} colorDark")
        }
        assertEquals(templates.teams.size, templates.teams.map { it.color.lowercase() }.toSet().size, "구단 색이 겹친다")
    }

    // ---------- 비서 브리핑 ----------

    private val balance = ProjectFiles.loadBalanceConfig()
    private val league = LeagueLoader.parse(
        ProjectFiles.read(ProjectFiles.leaguePath(ProjectFiles.loadTeamTemplates().season)),
    )

    private fun session() = GameSession(balance, league, TeamId("SWR"), seed = 4242L)

    @Test
    fun `개막 주에는 개막 인사를 하고 결정할 것이 없다`() {
        val session = session()
        assertTrue(Briefing.headline(session).startsWith("개막 주"))
        assertEquals(emptyList(), Briefing.decisions(session))
    }

    @Test
    fun `한 주 뒤에는 지난주 성적과 순위를 말하고 주간 요약과 숫자가 맞다`() {
        val session = session()
        session.advanceWeek(delegate = true)
        val report = session.lastReport!!
        val week = Briefing.weekRecord(session, report)

        assertEquals(report.games.count { it.home.teamId == session.userTeamId || it.away.teamId == session.userTeamId }, week.games)
        assertTrue(Briefing.headline(session).contains(week.text()))
        assertTrue(Briefing.headline(session).contains("${session.rank()}위"))
        assertTrue(Briefing.weeklySummary(session, report).startsWith("${report.week}주차는 ${week.text()}"))
    }

    @Test
    fun `드래프트 주차에는 지명이 결정할 것으로 잡힌다`() {
        val session = session()
        session.advanceUntil(balance.int("season.regularSeasonWeeks"), delegate = true)
        assertTrue(session.isDraftWeek && !session.draftDone)
        assertTrue(Briefing.decisions(session).any { "드래프트" in it.text })
    }
}
