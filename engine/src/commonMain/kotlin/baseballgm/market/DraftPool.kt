package baseballgm.market

import baseballgm.model.Origin
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.scouting.Physique
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * 드래프트 지명 대상 한 명 (docs/10).
 *
 * [Player] 를 그대로 품고 있지만 **소속이 없다**(`teamId == null`). 지명되기 전까지는 리그
 * 선수 명단(`League.players`)에 들어가지 않으므로, 기록·성장·은퇴 처리에서 자동으로 빠진다.
 *
 * 학교·체격·부상 이력은 능력치가 아니라 **겉으로 보이는 정보**라서 정확도와 무관하게 그대로 보인다.
 */
@Serializable
data class DraftProspect(
    val player: Player,
    val school: String,
    val heightCm: Int,
    val weightKg: Int,
    val injuryHistory: List<String> = emptyList(),
) {
    val id: PlayerId get() = player.id

    val isHighSchool: Boolean get() = player.origin == Origin.HIGH_SCHOOL

    val isPitcher: Boolean get() = player is Pitcher

    val schoolTypeLabel: String get() = if (isHighSchool) "고졸" else "대졸"

    fun physique(): Physique = Physique(heightCm, weightKg)
}

/**
 * 한 해의 드래프트 풀 (docs/10).
 *
 * **개막 시점에 공개**되어 시즌 내내 관찰할 수 있다. 9월 드래프트에서 지명되지 않은 선수는
 * 육성선수 계약 대상이 된다.
 */
@Serializable
data class DraftPool(
    val season: Int,
    val prospects: List<DraftProspect>,
) {
    fun byId(id: PlayerId): DraftProspect? = prospects.firstOrNull { it.id == id }

    val highSchoolCount: Int get() = prospects.count { it.isHighSchool }

    val collegeCount: Int get() = prospects.size - highSchoolCount

    companion object {
        val EMPTY: DraftPool = DraftPool(0, emptyList())
    }
}

/**
 * 드래프트 풀 공급기.
 *
 * 엔진은 선수를 만들 수 없다 — 이름 생성기와 아키타입이 도구(`tools`)에 있기 때문이다 (불변 원칙 1).
 * [baseballgm.season.RookieSupplier] 와 같은 이유로 밖에서 받아 쓴다.
 */
fun interface ProspectSupplier {
    fun create(season: Int, count: Int, random: Random): List<DraftProspect>
}
