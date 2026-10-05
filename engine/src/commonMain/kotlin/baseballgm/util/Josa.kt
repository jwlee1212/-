package baseballgm.util

/**
 * 한국어 조사 (이/가, 을/를, 은/는, 와/과, 으로/로).
 *
 * 기사·팬 글은 이름이 바뀌어 들어가는 틀이라 "삼성이(가)" 처럼 쓰면 바로 기계가 쓴 티가 난다.
 * 마지막 글자가 한글이면 받침으로 고르고, 숫자·영문으로 끝나면 읽는 소리로 대충 고른다.
 */
private fun hasFinalConsonant(word: String): Boolean? {
    val last = word.trimEnd().lastOrNull() ?: return null
    if (last in '가'..'힣') return (last - '가') % 28 != 0
    if (last.isDigit()) return last in "013678"
    return null
}

/** "OO이" / "OO가" */
fun String.iGa(): String = this + when (hasFinalConsonant(this)) { true -> "이"; false -> "가"; null -> "이(가)" }

/** "OO을" / "OO를" */
fun String.eulReul(): String = this + when (hasFinalConsonant(this)) { true -> "을"; false -> "를"; null -> "을(를)" }

/** "OO은" / "OO는" */
fun String.eunNeun(): String = this + when (hasFinalConsonant(this)) { true -> "은"; false -> "는"; null -> "은(는)" }

/** "OO과" / "OO와" */
fun String.waGwa(): String = this + when (hasFinalConsonant(this)) { true -> "과"; false -> "와"; null -> "와(과)" }

/** "OO으로" / "OO로" (ㄹ 받침은 "로") */
fun String.euro(): String {
    val last = trimEnd().lastOrNull()
    if (last != null && last in '가'..'힣' && (last - '가') % 28 == 8) return this + "로"
    return this + when (hasFinalConsonant(this)) { true -> "으로"; false -> "로"; null -> "(으)로" }
}

/** "OO이에요" / "OO예요" */
fun String.ieyo(): String = this + when (hasFinalConsonant(this)) { true -> "이에요"; false -> "예요"; null -> "이에요" }
