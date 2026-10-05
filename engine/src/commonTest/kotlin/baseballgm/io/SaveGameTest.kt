package baseballgm.io

import baseballgm.market.rosterFor
import baseballgm.market.testLeague
import baseballgm.model.TeamId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SaveGameTest {

    private val league = testLeague(players = rosterFor("AAA") + rosterFor("BBB"))

    @Test
    fun `저장한 리그를 그대로 읽어 온다`() {
        val save = SaveGame(userTeam = TeamId("AAA"), league = league)

        val restored = SaveGameCodec.parseOrNull(SaveGameCodec.encode(save))

        assertEquals(save, restored)
    }

    @Test
    fun `깨진 세이브는 예외 대신 null`() {
        assertNull(SaveGameCodec.parseOrNull("{ not json"))
        assertNull(SaveGameCodec.parseOrNull(""))
    }

    @Test
    fun `형식 1 세이브(개막 스냅샷)도 읽는다`() {
        val v1 = SaveGame(formatVersion = 1, userTeam = TeamId("AAA"), league = league)
        val restored = SaveGameCodec.parseOrNull(SaveGameCodec.encode(v1))
        assertEquals(1, restored?.formatVersion)
        assertNull(restored?.season)
        assertNull(restored?.seed)
    }

    @Test
    fun `다른 형식 버전은 읽지 않는다`() {
        val old = SaveGame(formatVersion = SaveGame.CURRENT_VERSION + 1, userTeam = TeamId("AAA"), league = league)

        assertNull(SaveGameCodec.parseOrNull(SaveGameCodec.encode(old)))
    }
}
