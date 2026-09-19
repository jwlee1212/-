package baseballgm.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HangulTest {

    @Test
    fun `받침 여부를 판정한다`() {
        assertTrue(Hangul.hasFinalConsonant("김민준") == true)
        assertTrue(Hangul.hasFinalConsonant("이서우") == false)
        assertNull(Hangul.hasFinalConsonant("Santana"))
    }

    @Test
    fun `조사를 골라 붙인다`() {
        assertEquals("이", Hangul.subject("김민준"))
        assertEquals("가", Hangul.subject("이서우"))
        assertEquals("을", Hangul.objectParticle("박성찬"))
        assertEquals("를", Hangul.objectParticle("최유재"))
        assertEquals("은", Hangul.topic("정한결"))
        assertEquals("는", Hangul.topic("한도하"))
    }

    @Test
    fun `ㄹ 받침은 으로 대신 로를 쓴다`() {
        assertEquals("로", Hangul.particle("이현일", "으로", "로"))
        assertEquals("으로", Hangul.particle("김정현", "으로", "로"))
        assertEquals("로", Hangul.particle("박서우", "으로", "로"))
    }
}
