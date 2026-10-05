package baseballgm.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.graphics.toAwtImage
import baseballgm.app.batting.BattingScreen
import baseballgm.app.batting.BattingSession
import baseballgm.app.batting.Phase
import baseballgm.app.batting.Presentation
import baseballgm.app.ui.ScoutTheme
import baseballgm.batting.BattingConfig
import baseballgm.tools.ProjectFiles
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 타석 화면 연기 테스트: 화면이 뜨고, 시간이 지나면 공이 날아오고, 탭하면 스윙 판정이 난다.
 * 확인용 스크린샷을 app/build/screens/ 에 남긴다.
 */
@OptIn(ExperimentalTestApi::class)
class BattingScreenTest {
    private val balance = ProjectFiles.loadBalanceConfig()

    @Test
    fun `공이 날아오고 탭하면 판정이 난다`() = runComposeUiTest {
        val session = BattingSession(BattingConfig.from(balance), Presentation.from(balance))
        // 화면이 프레임마다 다시 그려져 "한가해지지" 않으므로, 시계를 손으로 돌린다
        mainClock.autoAdvance = false
        // 폰 세로 화면 크기로 그린다 (테스트 창 높이가 768 이라 그 안에 맞춘다)
        setContent { Box(Modifier.requiredSize(390.dp, 760.dp)) { ScoutTheme { BattingScreen(session) } } }

        // 판정은 실제 시간(Monotonic)으로 재므로 진짜로 기다리면서 프레임을 넘긴다
        fun runFor(ms: Long) {
            val end = System.nanoTime() + ms * 1_000_000
            while (System.nanoTime() < end) {
                Thread.sleep(16)
                mainClock.advanceTimeByFrame()
            }
        }
        val deadline = System.nanoTime() + 5_000_000_000
        while (session.phase !is Phase.Flight && System.nanoTime() < deadline) runFor(16)
        val flight = session.phase as Phase.Flight
        // 공이 절반 넘게 왔을 때 찍는다
        runFor((flight.pitch.flightMs * 0.6).toLong())
        save("flight")

        session.tap()
        assertTrue(session.lastTimingMs != null, "탭하면 스윙 타이밍이 기록된다")
        runFor(200)
        save("after-swing")
        runFor(2_000)
        save("later")

        // 컨택 100 으로 공이 도착하는 순간에 맞춰 탭 → 대개 정타·약한 타구가 나온다 (확인용 사진)
        session.updateSkills(session.skills.copy(contact = 100, power = 100))
        val next = System.nanoTime() + 10_000_000_000
        while ((session.phase as? Phase.Flight)?.swing != null || session.phase !is Phase.Flight) {
            if (session.phase is Phase.Over) session.tap()
            runFor(16)
            if (System.nanoTime() > next) break
        }
        val pitch = (session.phase as Phase.Flight).pitch
        while ((session.phase as Phase.Flight).release.elapsedNow().inWholeMilliseconds < pitch.flightMs.toLong()) Thread.sleep(1)
        session.tap()
        runFor(40)
        save("impact") // 히트스톱 중: 섬광·파편
        runFor(220)
        save("launch") // 공이 튕겨 나가는 중
        runFor(600)
        save("hit")
    }

    private fun androidx.compose.ui.test.ComposeUiTest.save(name: String) {
        val dir = File(ProjectFiles.root, "app/build/screens").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }
}
