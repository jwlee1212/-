package baseballgm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 투구·타격 손. S 는 양타. */
@Serializable
enum class Hand {
    @SerialName("L") LEFT,
    @SerialName("R") RIGHT,
    @SerialName("S") SWITCH,
}

/** 야수 포지션. 투수는 [PitcherRole] 로 따로 다룬다. */
@Serializable
enum class Position(val label: String, val defenseDifficulty: Double) {
    @SerialName("C") CATCHER("C", 1.35),
    @SerialName("1B") FIRST_BASE("1B", 0.75),
    @SerialName("2B") SECOND_BASE("2B", 1.15),
    @SerialName("3B") THIRD_BASE("3B", 1.05),
    @SerialName("SS") SHORTSTOP("SS", 1.30),
    @SerialName("LF") LEFT_FIELD("LF", 0.90),
    @SerialName("CF") CENTER_FIELD("CF", 1.20),
    @SerialName("RF") RIGHT_FIELD("RF", 0.95),
    @SerialName("DH") DESIGNATED_HITTER("DH", 0.60),
    ;

    companion object {
        /** 지명타자를 뺀 수비 포지션. */
        val fielding: List<Position> = entries.filter { it != DESIGNATED_HITTER }

        fun fromLabel(label: String): Position =
            entries.firstOrNull { it.label == label } ?: error("알 수 없는 포지션: $label")
    }
}

/** 투수의 기본 보직. 세부 불펜 역할(셋업·추격조 등)은 M3 규칙표에서 정한다. */
@Serializable
enum class PitcherRole {
    @SerialName("SP") STARTER,
    @SerialName("RP") RELIEVER,
    @SerialName("CL") CLOSER,
    ;

    val isReliever: Boolean get() = this != STARTER
}

/** 1군 / 2군. */
@Serializable
enum class RosterLevel {
    @SerialName("first") FIRST_TEAM,
    @SerialName("futures") FUTURES,
}

/** 출신. FA 자격 연수 계산(docs/11)과 성장 곡선에 쓴다. */
@Serializable
enum class Origin {
    @SerialName("highSchool") HIGH_SCHOOL,
    @SerialName("college") COLLEGE,
    @SerialName("foreign") FOREIGN,
}

/** 성장 타입(숨김). 나이 곡선을 정한다 (docs/09). */
@Serializable
enum class GrowthType {
    @SerialName("early") EARLY,
    @SerialName("normal") NORMAL,
    @SerialName("late") LATE,
    ;

    /** `config/balance.json` 의 `growth.peakAges` 키. */
    val configKey: String
        get() = when (this) {
            EARLY -> "early"
            NORMAL -> "normal"
            LATE -> "late"
        }
}

/**
 * 능력치 한 종류. 성장·노화·스카우트가 능력치 종류에 상관없이 같은 코드로 돌 수 있게 열거형으로 둔다.
 * [configKey] 는 `balance.json` 의 능력치별 배율 키와 같다.
 */
@Serializable
enum class Attribute(val configKey: String, val label: String, val forBatter: Boolean) {
    @SerialName("contact") CONTACT("contact", "컨택", true),
    @SerialName("power") POWER("power", "파워", true),
    @SerialName("eye") EYE("eye", "선구안", true),
    @SerialName("speed") SPEED("speed", "주루", true),
    @SerialName("defense") DEFENSE("defense", "수비", true),
    @SerialName("stuff") STUFF("stuff", "구위", false),
    @SerialName("control") CONTROL("control", "제구", false),
    @SerialName("groundball") GROUNDBALL("groundball", "땅볼 유도", false),
    @SerialName("stamina") STAMINA("stamina", "체력", false),
    ;

    companion object {
        val batterAttributes: List<Attribute> = entries.filter { it.forBatter }
        val pitcherAttributes: List<Attribute> = entries.filter { !it.forBatter }
    }
}

/** 타자 유형 (docs/03). 생성 시 능력치 편향을 준다. */
@Serializable
enum class BatterArchetype(val configKey: String, val label: String) {
    @SerialName("contactHitter") CONTACT_HITTER("contactHitter", "교타자"),
    @SerialName("slugger") SLUGGER("slugger", "거포"),
    @SerialName("onBase") ON_BASE("onBase", "출루형"),
    @SerialName("speedster") SPEEDSTER("speedster", "호타준족"),
    @SerialName("defensive") DEFENSIVE("defensive", "수비형"),
}

/** 투수 유형 (docs/03). */
@Serializable
enum class PitcherArchetype(val configKey: String, val label: String) {
    @SerialName("flamethrower") FLAMETHROWER("flamethrower", "파이어볼러"),
    @SerialName("controlArtist") CONTROL_ARTIST("controlArtist", "제구형"),
    @SerialName("groundballer") GROUNDBALLER("groundballer", "땅볼형"),
    @SerialName("inningsEater") INNINGS_EATER("inningsEater", "이닝이터"),
}

/** 코치 보직. */
@Serializable
enum class CoachRole {
    @SerialName("batting") BATTING,
    @SerialName("pitching") PITCHING,
    @SerialName("fielding") FIELDING,
    @SerialName("futuresManager") FUTURES_MANAGER,
}

/** 코치 성향 = 집중 육성 능력치 (docs/09). 2군 감독은 성향이 없다(null). */
@Serializable
enum class CoachFocus(val attribute: Attribute) {
    @SerialName("power") POWER(Attribute.POWER),
    @SerialName("contact") CONTACT(Attribute.CONTACT),
    @SerialName("eye") EYE(Attribute.EYE),
    @SerialName("stuff") STUFF(Attribute.STUFF),
    @SerialName("control") CONTROL(Attribute.CONTROL),
    @SerialName("groundball") GROUNDBALL(Attribute.GROUNDBALL),
    @SerialName("defense") DEFENSE(Attribute.DEFENSE),
    @SerialName("speed") SPEED(Attribute.SPEED),
}

/** 감독 전문 분야. 해당 그룹 성장 +10% (docs/06, 09). */
@Serializable
enum class ManagerSpecialty {
    @SerialName("pitching") PITCHING,
    @SerialName("batting") BATTING,
    @SerialName("fielding") FIELDING,
}

/** 메디컬 스태프 보직 (docs/13). */
@Serializable
enum class MedicalRole {
    @SerialName("teamDoctor") TEAM_DOCTOR,
    @SerialName("rehabTrainer") REHAB_TRAINER,
    @SerialName("conditioningCoach") CONDITIONING_COACH,
}

/** 군 복무 방식 (docs/12). */
@Serializable
enum class ServiceKind {
    /** 상무: 퓨처스리그 출전, 성장 유지. */
    @SerialName("sangmu") SANGMU,

    /** 현역·사회복무: 성장 정지 + 감각 하락. */
    @SerialName("activeDuty") ACTIVE_DUTY,
}
