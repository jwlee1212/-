package baseballgm.text

/**
 * 한글 조사 처리 (docs/07).
 *
 * 문자 중계 문장을 만들 때 "김민우가 / 박선호는" 처럼 이름 끝 글자의 받침에 따라 조사가 달라진다.
 * 받침 판정은 `(유니코드 − 0xAC00) % 28 != 0`.
 */
object Hangul {

    private const val FIRST_SYLLABLE = 0xAC00
    private const val LAST_SYLLABLE = 0xD7A3

    fun isHangulSyllable(char: Char): Boolean = char.code in FIRST_SYLLABLE..LAST_SYLLABLE

    /** 마지막 글자에 받침이 있는가. 한글이 아니면 null. */
    fun hasFinalConsonant(word: String): Boolean? {
        val last = word.lastOrNull() ?: return null
        if (!isHangulSyllable(last)) return null
        return (last.code - FIRST_SYLLABLE) % 28 != 0
    }

    /** 받침이 ㄹ 인가. "으로/로" 처리에 쓴다. */
    fun endsWithRieul(word: String): Boolean {
        val last = word.lastOrNull() ?: return false
        if (!isHangulSyllable(last)) return false
        return (last.code - FIRST_SYLLABLE) % 28 == 8
    }

    /**
     * 이름 뒤에 붙일 조사를 고른다.
     * @param withFinal 받침이 있을 때 쓰는 형태 ("이", "을", "은", "과")
     * @param withoutFinal 받침이 없을 때 쓰는 형태 ("가", "를", "는", "와")
     */
    fun particle(word: String, withFinal: String, withoutFinal: String): String {
        val hasFinal = hasFinalConsonant(word) ?: false
        if (withFinal == "으로" && endsWithRieul(word)) return "로"
        return if (hasFinal) withFinal else withoutFinal
    }

    fun subject(word: String): String = particle(word, "이", "가")
    fun topic(word: String): String = particle(word, "은", "는")
    fun objectParticle(word: String): String = particle(word, "을", "를")
    fun withParticle(word: String): String = particle(word, "과", "와")
}
