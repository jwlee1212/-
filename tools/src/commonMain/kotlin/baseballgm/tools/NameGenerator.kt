package baseballgm.tools

import baseballgm.text.Hangul
import baseballgm.util.chance
import baseballgm.util.nextGaussian
import baseballgm.util.nextGaussianInt
import baseballgm.util.nextInRange
import baseballgm.util.weightedPick
import kotlin.random.Random

/**
 * 이름 생성기 (docs/03).
 *
 * - 성씨는 실제 인구 비율 가중 추출
 * - 이름 두 음절은 출생 연도 세대에 맞는 음절 풀에서 뽑는다
 * - 리그 안에서 동명이인이 없고, 유명 선수 이름(금지 목록)은 나오지 않는다
 * - 만들어진 이름은 문자 중계 조사 처리를 위해 한글 음절이어야 한다
 */
class NameGenerator(private val random: Random) {

    private val used = mutableSetOf<String>()
    private val usedForeign = mutableSetOf<String>()
    private var batch: NameBatch? = null

    /**
     * 팀을 목표 전력에 맞출 때까지 여러 번 다시 만들기 때문에, **버려진 시도가 이름을 소모하면 안 된다.**
     * (특히 외국인 이름 풀은 작다) 한 번의 시도를 [startBatch] ~ [endBatch] 로 감싸고,
     * 버릴 시도는 [release] 로 이름을 돌려준다.
     */
    fun startBatch() {
        batch = NameBatch()
    }

    fun endBatch(): NameBatch {
        val finished = batch ?: NameBatch()
        batch = null
        return finished
    }

    /** 쓰지 않기로 한 이름들을 풀에 돌려준다. */
    fun release(batch: NameBatch) {
        used.removeAll(batch.korean.toSet())
        usedForeign.removeAll(batch.foreign.toSet())
    }

    private fun remember(name: String) {
        used += name
        batch?.korean?.add(name)
    }

    private fun rememberForeign(registeredName: String) {
        usedForeign += registeredName
        batch?.foreign?.add(registeredName)
    }

    /** 한국 선수 이름. [birthYear] 로 세대에 맞는 음절 풀을 고른다. */
    fun korean(birthYear: Int): String {
        repeat(MAX_ATTEMPTS) {
            val name = buildKorean(birthYear)
            if (name !in used && name !in NamePools.bannedNames && Hangul.hasFinalConsonant(name) != null) {
                remember(name)
                return name
            }
        }
        // 음절 조합이 바닥나는 일은 사실상 없지만, 무한 루프 대신 숫자를 붙여 유일성을 보장한다
        var suffix = 2
        while (true) {
            val fallback = buildKorean(birthYear) + suffix
            if (fallback !in used) {
                remember(fallback)
                return fallback
            }
            suffix++
        }
    }

    /** 스태프(감독·코치·메디컬·단장) 이름. 선수보다 윗세대 음절을 쓴다. */
    fun staff(): String {
        repeat(MAX_ATTEMPTS) {
            val name = random.weightedPick(NamePools.surnames) +
                NamePools.staffFirstSyllables.random(random) +
                NamePools.secondSyllables.random(random)
            if (name !in used && name !in NamePools.bannedNames) {
                remember(name)
                return name
            }
        }
        error("스태프 이름을 만들지 못했다")
    }

    /** 외국인 선수. 영문 이름과 한글 등록명을 함께 돌려준다. */
    fun foreign(): ForeignName {
        val candidates = NamePools.foreignNames.filter { it.second !in usedForeign }
        require(candidates.isNotEmpty()) { "외국인 이름 풀이 모자란다" }
        val picked = candidates[random.nextInt(candidates.size)]
        rememberForeign(picked.second)
        return ForeignName(picked.first, picked.second)
    }

    private fun buildKorean(birthYear: Int): String {
        val surname = random.weightedPick(NamePools.surnames)
        val firstPool = if (birthYear >= YOUNGER_FROM) NamePools.youngerFirstSyllables else NamePools.olderFirstSyllables
        return surname + firstPool.random(random) + NamePools.secondSyllables.random(random)
    }

    companion object {
        private const val MAX_ATTEMPTS = 200
        private const val YOUNGER_FROM = 1998
    }
}

data class ForeignName(val latinName: String, val registeredName: String)

/** 한 번의 생성 시도에서 쓴 이름들. 시도를 버리면 [NameGenerator.release] 로 돌려준다. */
class NameBatch {
    internal val korean: MutableList<String> = mutableListOf()
    internal val foreign: MutableList<String> = mutableListOf()
}
