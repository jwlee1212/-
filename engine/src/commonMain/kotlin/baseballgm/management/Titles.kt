package baseballgm.management

import baseballgm.io.BalanceConfig

/** 단장 칭호 하나. [description] 은 어떻게 얻었는지 한 줄 */
data class GmTitle(val name: String, val description: String)

/**
 * 단장 칭호 (docs/13).
 *
 * 커리어 기록에서 저절로 붙는 별명이다. 여러 개를 얻을 수 있고, 가장 무거운 것 하나가 **대표 칭호**로
 * 타이틀 화면·커리어 연대기·비서 인사에 쓰인다. 조건 수치는 balance.json `narrative.titles`.
 */
class GmTitles(balance: BalanceConfig) {
    private val section = balance.section("narrative").section("titles")
    private val postseasonRegular = section.int("postseasonRegular")
    private val dynasty = section.int("dynasty")
    private val journeyman = section.int("journeyman")
    private val oneClub = section.int("oneClub")
    private val veteran = section.int("veteran")
    private val rebuildRankJump = section.int("rebuildRankJump")
    private val reputationStar = section.int("reputationStar")

    /** 얻은 칭호 전부. 앞에 있을수록 무겁다 */
    fun earned(career: CareerRecord?): List<GmTitle> {
        if (career == null || career.totalSeasons == 0) return listOf(ROOKIE)
        val seasons = career.seasons.filter { !it.commentary }
        return buildList {
            if (career.championships >= dynasty) add(GmTitle("왕조의 설계자", "우승 ${career.championships}회"))
            if (career.championships >= 1) add(GmTitle("우승 청부사", "첫 우승을 만든 단장"))
            if (career.reputation >= reputationStar) add(GmTitle("리그가 아는 이름", "평판 ${career.reputation}"))
            if (rebuilt(seasons)) add(GmTitle("리빌딩 장인", "한 해 만에 순위를 ${rebuildRankJump}계단 이상 끌어올림"))
            if (career.postseasonAppearances >= postseasonRegular) {
                add(GmTitle("가을 단골", "포스트시즌 ${career.postseasonAppearances}회 진출"))
            }
            if (seasons.size >= oneClub && seasons.map { it.teamId }.distinct().size == 1) {
                add(GmTitle("원클럽 단장", "한 구단에서만 ${seasons.size}시즌"))
            }
            if (career.teamsServed.size >= journeyman) add(GmTitle("저니맨", "${career.teamsServed.size}개 구단을 거침"))
            if (career.seasons.any { it.commentary }) add(GmTitle("돌아온 단장", "해설위원을 거쳐 복귀"))
            if (career.totalSeasons >= veteran) add(GmTitle("베테랑 단장", "${career.totalSeasons}시즌 재임"))
            if (isEmpty()) add(GmTitle("현장의 단장", "${career.totalSeasons}시즌째 자리를 지키는 중"))
        }
    }

    fun headline(career: CareerRecord?): GmTitle = earned(career).first()

    /** 같은 구단에서 이어진 두 시즌 사이에 순위가 크게 오른 적이 있나 */
    private fun rebuilt(seasons: List<CareerSeason>): Boolean =
        seasons.sortedBy { it.season }.zipWithNext().any { (before, after) ->
            before.teamId == after.teamId && after.season == before.season + 1 &&
                before.rank - after.rank >= rebuildRankJump
        }

    private companion object {
        val ROOKIE = GmTitle("신임 단장", "첫 시즌을 앞두고 있다")
    }
}
