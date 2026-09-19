package baseballgm.stats

/** 박스스코어 검증 실패. 개발·테스트 빌드에서는 바로 던져서 버그를 그 자리에서 잡는다. */
class BoxScoreValidationException(message: String) : IllegalStateException(message)

/**
 * 박스스코어 검증기 (docs/05).
 *
 * 경기마다 등식이 맞는지 본다. 시뮬레이터가 주자를 잃어버리거나 아웃을 두 번 세거나
 * 자책점을 잘못 매기면 **여기서 바로 걸린다.** 시뮬레이션 버그는 몇백 경기 뒤 이상한 기록으로
 * 드러나는 경우가 많아서, 매 경기 등식을 확인하는 쪽이 훨씬 싸다.
 *
 * - 개발·테스트: [validateOrThrow] 로 즉시 예외
 * - 릴리스: [validate] 결과를 로그로만 남긴다
 */
object BoxScoreValidator {

    fun validateOrThrow(box: BoxScore) {
        val problems = validate(box)
        if (problems.isNotEmpty()) {
            throw BoxScoreValidationException("박스스코어 검증 실패:\n" + problems.joinToString("\n") { "  - $it" })
        }
    }

    fun validate(box: BoxScore): List<String> {
        val problems = mutableListOf<String>()
        problems += validateTeam(box.home, box.away, box)
        problems += validateTeam(box.away, box.home, box)
        problems += validateDecisions(box)
        return problems
    }

    private fun validateTeam(team: TeamBoxScore, opponent: TeamBoxScore, box: BoxScore): List<String> {
        val problems = mutableListOf<String>()
        val prefix = "${team.teamId}"

        // ① 타석 구성: 타석 = 타수 + 볼넷 + 사구 + 희생번트 + 희생플라이 + 타격방해
        team.batting.forEach { (playerId, batting) ->
            val line = batting.total
            val parts = line.atBats + line.walks + line.hitByPitch + line.sacBunts +
                line.sacFlies + line.catcherInterference
            if (line.plateAppearances != parts) {
                problems += "$prefix $playerId: 타석 ${line.plateAppearances} != 타수+볼넷+사구+희생+방해 $parts"
            }
        }

        // ② 주자 보존: 출루 = 득점 + 잔루 + 베이스에서 아웃된 주자
        val reached = team.timesReachedBase
        val accounted = team.runs + team.leftOnBase + team.runnersOutOnBase
        if (reached != accounted) {
            problems += "$prefix: 출루 $reached != 득점 ${team.runs} + 잔루 ${team.leftOnBase} + " +
                "주자 아웃 ${team.runnersOutOnBase} (= $accounted)"
        }

        // ③ 아웃 수: 투수들이 잡은 아웃 합 = 수비한 하프 이닝의 아웃 합
        val pitcherOuts = team.pitchingTotal.outs
        val halfInningOuts = team.halfInningOuts.sum()
        if (pitcherOuts != halfInningOuts) {
            problems += "$prefix: 투수 아웃 합 $pitcherOuts != 하프 이닝 아웃 합 $halfInningOuts"
        }
        team.halfInningOuts.forEachIndexed { index, outs ->
            if (outs > OUTS_PER_INNING) problems += "$prefix: ${index + 1}번째 수비 이닝 아웃 $outs 개"
            // 마지막 이닝은 끝내기로 중간에 끝날 수 있다
            if (outs < OUTS_PER_INNING && index != team.halfInningOuts.lastIndex) {
                problems += "$prefix: ${index + 1}번째 수비 이닝이 ${outs}아웃으로 끝났다"
            }
        }

        // ④ 득점 일치: 팀 득점 = 선수 득점 합 = 이닝별 합, 그리고 상대 투수 실점 합과도 같다
        val playerRuns = team.battingTotal.runs
        if (playerRuns != team.runs) {
            problems += "$prefix: 팀 득점 ${team.runs} != 선수 득점 합 $playerRuns"
        }
        val opponentRunsAllowed = opponent.pitchingTotal.runs
        if (opponentRunsAllowed != team.runs) {
            problems += "$prefix: 팀 득점 ${team.runs} != 상대 투수 실점 합 $opponentRunsAllowed"
        }

        // ⑤ 자책점 ≤ 실점
        team.pitching.forEach { (playerId, pitching) ->
            val line = pitching.total
            if (line.earnedRuns > line.runs) {
                problems += "$prefix $playerId: 자책 ${line.earnedRuns} > 실점 ${line.runs}"
            }
        }

        // 타점은 득점보다 많을 수 없다 (실책 득점은 타점이 없다)
        if (team.battingTotal.rbi > team.runs) {
            problems += "$prefix: 타점 ${team.battingTotal.rbi} > 득점 ${team.runs}"
        }

        if (box.innings <= 0) problems += "$prefix: 이닝 수가 0이다"
        return problems
    }

    private fun validateDecisions(box: BoxScore): List<String> {
        val problems = mutableListOf<String>()
        val pitching = box.home.pitching.values + box.away.pitching.values
        val wins = pitching.sumOf { it.total.wins }
        val losses = pitching.sumOf { it.total.losses }
        val saves = pitching.sumOf { it.total.saves }

        if (box.tie) {
            if (wins != 0 || losses != 0 || saves != 0) {
                problems += "무승부인데 승 $wins 패 $losses 세이브 $saves 가 기록됐다"
            }
        } else {
            if (wins != 1) problems += "승리 투수가 ${wins}명이다"
            if (losses != 1) problems += "패전 투수가 ${losses}명이다"
            if (saves > 1) problems += "세이브가 ${saves}개다"
            if (box.savePitcher != null && box.savePitcher == box.winningPitcher) {
                problems += "승리 투수와 세이브 투수가 같다"
            }
        }
        if (box.homeScore == box.awayScore && !box.tie) problems += "동점인데 무승부가 아니다"
        if (box.homeScore != box.awayScore && box.tie) problems += "점수가 다른데 무승부로 기록됐다"
        return problems
    }

    private const val OUTS_PER_INNING = 3
}
