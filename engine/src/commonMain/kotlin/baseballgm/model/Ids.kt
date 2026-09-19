package baseballgm.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** 선수 식별자. 리그 안에서 유일하다. 예: `P0137` */
@Serializable
@JvmInline
value class PlayerId(val value: String) {
    override fun toString(): String = value
}

/** 구단 식별자. `data/teams.json` 의 id 와 같다. 예: `DSK` */
@Serializable
@JvmInline
value class TeamId(val value: String) {
    override fun toString(): String = value
}

/** 감독·코치·메디컬·단장 식별자. 예: `M03`, `C118`, `G07` */
@Serializable
@JvmInline
value class StaffId(val value: String) {
    override fun toString(): String = value
}
