package baseballgm.model

/**
 * 등번호 배정 (2026-10-02).
 *
 * 규칙은 셋뿐이다.
 * 1. **한 구단 안에서 겹치지 않는다** (1·2군 합쳐서 — 현실 구단도 같다).
 * 2. 이미 번호가 있고 새 팀에서 비어 있으면 **그대로 단다** (이적해도 번호 유지). 겹치면 먼저 있던 선수가 지킨다.
 * 3. 새로 받아야 하면 선수 id 로 정한 "선호 번호"부터 위로 빈 번호를 찾는다. 1~99 가 다 차면 100번대(육성선수 번호처럼).
 *
 * 난수를 쓰지 않는다 — id 만으로 정해지므로 같은 리그면 언제 불러도 같은 번호다 (불변 원칙 2와 충돌 없음).
 * 시뮬레이션에는 영향이 없는 표시용 정보라 `balance.json` 에 두지 않는다.
 */
object UniformNumbers {

    const val MIN: Int = 1
    const val MAX: Int = 99

    /**
     * 리그 전체(또는 여러 팀)의 번호를 정리한다. 소속 없는 선수(FA 등)는 그대로 둔다.
     * @param newcomers 막 팀을 옮긴 선수(트레이드·FA). 번호가 겹치면 **원래 있던 선수가 지키고** 이쪽이 바꾼다
     */
    fun assign(players: List<Player>, newcomers: Set<PlayerId> = emptySet()): List<Player> {
        val assigned = mutableMapOf<PlayerId, Player>()
        players.filter { it.teamId != null }.groupBy { it.teamId }.values.forEach { team ->
            val taken = mutableSetOf<Int>()
            // 순서가 결과를 정하므로 고정한다: 원래 있던 선수 → 새로 온 선수, 그 안에서는 id 순.
            // 번호가 있는 선수가 먼저 자기 번호를 지킨다
            val ordered = team.sortedWith(compareBy<Player> { it.id in newcomers }.thenBy { it.id.value })
            val (numbered, unnumbered) = ordered.partition { it.uniformNumber > 0 }
            val needNumber = unnumbered.toMutableList()
            numbered.forEach { player ->
                if (taken.add(player.uniformNumber)) assigned[player.id] = player else needNumber += player
            }
            needNumber.forEach { player ->
                val number = pick(player.id, taken)
                taken += number
                assigned[player.id] = player.withUniformNumber(number)
            }
        }
        return players.map { assigned[it.id] ?: it }
    }

    /** 한 선수가 새 팀에 들어간다. 번호가 비어 있으면 유지, 겹치거나 없으면 새로 받는다 */
    fun forNewcomer(player: Player, teammates: List<Player>): Player {
        val taken = teammates.filter { it.id != player.id }.map { it.uniformNumber }.toSet()
        if (player.uniformNumber > 0 && player.uniformNumber !in taken) return player
        return player.withUniformNumber(pick(player.id, taken))
    }

    private fun pick(id: PlayerId, taken: Set<Int>): Int {
        val preferred = preferred(id)
        val range = MAX - MIN + 1
        for (step in 0 until range) {
            val number = MIN + (preferred - MIN + step) % range
            if (number !in taken) return number
        }
        // 1~99 가 다 찼다 → 100번대
        return generateSequence(MAX + 1) { it + 1 }.first { it !in taken }
    }

    /** id 로 정하는 선호 번호. 플랫폼마다 같은 값이 나오게 해시를 직접 계산한다 */
    private fun preferred(id: PlayerId): Int {
        var hash = 0
        id.value.forEach { hash = hash * 31 + it.code }
        // id 가 연번이라 그냥 나누면 번호도 연번이 된다 → 한 번 섞는다
        hash *= -0x61c88647
        hash = hash xor (hash ushr 15)
        return MIN + hash.mod(MAX - MIN + 1)
    }
}
