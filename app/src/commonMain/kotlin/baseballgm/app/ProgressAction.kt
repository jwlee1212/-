package baseballgm.app

/**
 * 하단 진행 버튼이 지금 할 일.
 *
 * 진행 버튼은 하나지만 시즌 단계마다 뜻이 다르다. 유저가 따로 결정해야 하는 일(드래프트 지명,
 * 이직 선택)이 걸려 있으면 진행 대신 그 화면으로 보낸다 — 그 외에는 누르기만 하면 된다 (docs/07).
 */
sealed interface ProgressAction {
    val label: String

    data class PlayWeek(override val label: String) : ProgressAction

    /** 드래프트 주차. 지명은 유저가 한다 (docs/07·10) → 스카우트 탭으로 보낸다 */
    data object GoToDraft : ProgressAction {
        override val label: String = "드래프트 지명하러 가기"
    }

    /** 포스트시즌 한 걸음: 시작(대진) → 우리 시리즈는 한 경기씩, 남의 시리즈는 시리즈째 (2026-10-03) */
    data class PostseasonStep(override val label: String) : ProgressAction

    /** 우리 팀이 없는(탈락했거나 못 나간) 포스트시즌을 끝까지 */
    data object PostseasonFinish : ProgressAction {
        override val label: String = "포스트시즌 끝까지 진행"
    }

    /** 시즌이 끝났다 → 스토브리그 준비 화면으로 (외국인 재계약·연봉·방출 명단·스태프를 정하고 시작한다) */
    data class PrepareOffseason(override val label: String) : ProgressAction

    data class FreeAgencyRound(override val label: String) : ProgressAction

    /** 돌발 이벤트에 답해야 진행할 수 있다 (docs/07·16 주중 개입) */
    data object ResolveIncident : ProgressAction {
        override val label: String = "결정하고 진행하기"
    }

    /** 주중에 멈췄다가 남은 경기를 이어 간다 */
    data class ContinueWeek(override val label: String) : ProgressAction

    /** 해임 뒤 무직. 커리어 화면에서 이직 제안을 고른다 (docs/13) */
    data object ChooseJob : ProgressAction {
        override val label: String = "이직 제안 확인하기"
    }

    companion object {
        private fun postseasonAction(session: GameSession): ProgressAction {
            val progress = session.postseasonProgress
                ?: return if (session.rank() <= session.postseasonSpots) PostseasonStep("포스트시즌 시작") else PostseasonFinish
            val current = progress.current ?: return PostseasonFinish
            return when {
                current.involves(session.userTeamId) -> PostseasonStep("${current.round.label} ${current.gamesPlayed + 1}차전 진행")
                progress.isAlive(session.userTeamId) -> PostseasonStep("${current.round.label} 진행")
                else -> PostseasonFinish
            }
        }

        fun of(session: GameSession): ProgressAction = when {
            session.unemployed -> ChooseJob
            session.pendingIncident != null -> ResolveIncident
            session.weekPaused -> ContinueWeek(
                baseballgm.events.Incident.DAY_NAMES.getOrNull(session.pausedNextDay ?: 0)
                    ?.let { "${session.week}주차 이어서 · ${it}요일 경기부터" }
                    ?: "${session.week}주차 마무리 · 주간 결산 받기",
            )
            session.inFreeAgency ->
                FreeAgencyRound("FA ${session.faRound()}/${session.faRounds()} 라운드 진행")
            !session.seasonOver && session.isDraftWeek && !session.draftDone -> GoToDraft
            !session.seasonOver -> PlayWeek("${session.week}주차 진행")
            !session.postseasonDone -> postseasonAction(session)
            else -> PrepareOffseason("스토브리그 준비 (${session.league.season + 1} 시즌)")
        }
    }
}
