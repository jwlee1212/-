package baseballgm.util

import kotlin.test.Test
import kotlin.test.assertEquals

/** 기사·비서 문장의 조사 (받침에 따라 이/가, 을/를 …) */
class JosaTest {
    @Test
    fun `받침에 따라 조사를 고른다`() {
        assertEquals("한강 팬텀스가", "한강 팬텀스".iGa())
        assertEquals("대성 나이츠를", "대성 나이츠".eulReul())
        assertEquals("조우호는", "조우호".eunNeun())
        assertEquals("강현준은", "강현준".eunNeun())
        assertEquals("수원 로얄스와", "수원 로얄스".waGwa())
        assertEquals("광주 스톰과", "광주 스톰".waGwa())
        assertEquals("주 시작이에요", "주 시작".ieyo())
        assertEquals("금요일 경기 뒤예요", "금요일 경기 뒤".ieyo())
    }

    @Test
    fun `ㄹ 받침 뒤에는 로`() {
        assertEquals("서울로", "서울".euro())
        assertEquals("부산으로", "부산".euro())
        assertEquals("대구로", "대구".euro())
    }

    @Test
    fun `숫자로 끝나면 읽는 소리로 고르고 모르면 둘 다 쓴다`() {
        assertEquals("10이", "10".iGa())
        assertEquals("2가", "2".iGa())
        assertEquals("ABC이(가)", "ABC".iGa())
    }
}
