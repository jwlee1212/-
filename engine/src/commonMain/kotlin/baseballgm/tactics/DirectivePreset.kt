package baseballgm.tactics

import baseballgm.model.ManagerTendencies

/**
 * 규칙표 프리셋 (docs/06).
 *
 * 프리셋은 규칙표를 직접 만들지 않고 **감독 성향 값**을 만든다. 그래야 유저가 고른 프리셋과
 * AI 감독이 같은 경로(성향 → 규칙표)를 타고, 프리셋으로 채운 뒤 항목별로 손보는 것도 자연스럽다.
 */
enum class DirectivePreset(val label: String) {
    /** 정석형: 전부 평균 */
    STANDARD("정석형") {
        override fun tendencies(): ManagerTendencies = ManagerTendencies(
            starterPatience = 50, bullpenAggression = 50, buntPreference = 45, stealAggression = 50,
            platoonUsage = 50, prospectUsage = 50, veteranTrust = 50,
        )
    },

    /** 공격형: 많이 뛰고 번트는 잘 안 댄다 */
    AGGRESSIVE("공격형") {
        override fun tendencies(): ManagerTendencies = ManagerTendencies(
            starterPatience = 45, bullpenAggression = 65, buntPreference = 20, stealAggression = 80,
            platoonUsage = 55, prospectUsage = 60, veteranTrust = 45,
        )
    },

    /** 투수 보호형: 선발을 일찍 내리고 불펜을 아낀다 */
    PITCHER_PROTECTION("투수 보호형") {
        override fun tendencies(): ManagerTendencies = ManagerTendencies(
            starterPatience = 25, bullpenAggression = 25, buntPreference = 45, stealAggression = 40,
            platoonUsage = 60, prospectUsage = 50, veteranTrust = 50,
        )
    },

    /** 스몰볼: 번트와 도루로 한 점을 짜낸다 */
    SMALL_BALL("스몰볼") {
        override fun tendencies(): ManagerTendencies = ManagerTendencies(
            starterPatience = 50, bullpenAggression = 55, buntPreference = 85, stealAggression = 75,
            platoonUsage = 70, prospectUsage = 45, veteranTrust = 55,
        )
    },
    ;

    abstract fun tendencies(): ManagerTendencies
}
