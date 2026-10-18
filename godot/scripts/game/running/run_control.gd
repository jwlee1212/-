class_name RunControl
extends RefCounted
## 내 주자 조작: 플레이 중 내 주자를 어느 베이스까지 보낼지 유저가 정한다 (베이스 버튼, BasePad).
##
## 플레이(PlaySimulator)는 한 번에 끝까지 계산되므로, 먼저 AI 판단대로 돌린 플레이를 보여 주다가
## 내 주자가 판단하는 순간("한 베이스 더", 태그업)마다 유저가 누른 목표 베이스로 확정한다:
## 목표가 그 판단의 다음 베이스 이상이면 뛰고, 아니면 멈춘다.
## 처음 돌린 것과 다르게 확정되면 그 판단까지의 선택을 넣어 플레이를 다시 계산한다 — 판단 전까지는 똑같이
## 흘러가므로 화면은 끊기지 않고 그 뒤만 달라진다. 노드가 아닌 로직이라 화면 없이 테스트한다 (원칙 1).
##
## 주루 설정 (더쇼의 Baserunning Decisions 를 따랐다):
##   auto   자동 — 주루는 전부 AI (RunControl 을 만들지 않는다, 도루 기회도 없다)
##   assist 보조 — 베이스를 누르지 않으면 코치 사인(AI 판단)대로 뛴다
##   manual 수동 — 베이스를 누르지 않으면 꼭 가야 하는 베이스에서 멈춘다 (코치 사인 없음)

const AUTO := "auto"
const ASSIST := "assist"
const MANUAL := "manual"
## 설정 화면 순서와 이름·설명
const MODES := [AUTO, ASSIST, MANUAL]
const MODE_LABELS := {AUTO: "자동", ASSIST: "보조", MANUAL: "수동"}
const MODE_NOTES := {
	AUTO: "주루는 전부 알아서 뛴다. 타격에만 집중한다. 도루는 하지 않는다",
	ASSIST: "베이스 버튼으로 내 주자를 보낸다. 누르지 않으면 코치 사인대로 뛴다. 도루 기회가 온다",
	MANUAL: "베이스 버튼으로 내 주자를 보낸다. 누르지 않으면 한 베이스만 간다. 코치 사인이 없다. 도루 기회가 온다",
}

## 지금 보여 줄 플레이 (선택이 확정될 때마다 바뀔 수 있다)
var play: PlaySimulator.PlayResult
## 수동인가 (누르지 않으면 멈춘다). 아니면 보조 (누르지 않으면 코치 사인)
var manual := false
## 유저가 누른 목표 베이스 (1~3, 4 = 홈, -1 = 아직 안 눌렀다)
var target := -1

var _resim: Callable
## 확정된 선택 (순서대로)
var _locked: Array = []


## resim: func(choices: Array) -> PlaySimulator.PlayResult — 같은 타구·같은 난수 상태에서 그 선택으로 다시 돌린다
func _init(first: PlaySimulator.PlayResult, resim: Callable, p_manual: bool = false) -> void:
	play = first
	_resim = resim
	manual = p_manual
	if manual:
		# 수동은 누르지 않으면 멈춘다 — 처음 보여 줄 플레이도 그렇게 맞춘다 (AI 가 뛰는 그림을 보여 줬다 바꾸지 않게)
		_align()


## 유저가 고를 판단이 하나라도 있는 플레이인가
func has_decisions() -> bool:
	return not play.decisions.is_empty()


## 아직 확정되지 않은 다음 판단. 없으면 {} (베이스 버튼을 숨긴다).
## 돌려주는 값: PlaySimulator 의 판단 기록 {t, id, kind, from, to, ai, go}
func pending() -> Dictionary:
	if _locked.size() >= play.decisions.size():
		return {}
	return play.decisions[_locked.size()]


## 지금 누를 수 있는 가장 가까운 베이스 (그 앞 베이스는 이미 지났다). 판단이 없으면 -1
func min_base() -> int:
	var d := pending()
	return -1 if d.is_empty() else int(d.from)


## 코치 사인이 가리키는 베이스 (보조일 때만, 없으면 -1): 다음 판단에서 AI 라면 갈 곳
func coach_base() -> int:
	var d := pending()
	if d.is_empty() or manual:
		return -1
	return int(d.to) if d.ai else int(d.from)


## 목표 베이스를 누른다 (판단 시각 전까지는 바꿀 수 있다). 이미 지난 베이스는 무시한다
func set_target(base: int) -> void:
	if min_base() >= 0 and base >= min_base():
		target = clampi(base, 1, 4)


## 시간이 흘렀다: 판단 시각이 지난 선택을 확정한다. 플레이가 달라졌으면 true
func advance(t: float) -> bool:
	var changed := false
	while _locked.size() < play.decisions.size() and t >= float(play.decisions[_locked.size()].t):
		var d: Dictionary = play.decisions[_locked.size()]
		var go := _choice(d)
		_locked.append(go)
		if go != bool(d.go):
			play = _resim.call(_locked.duplicate())
			changed = true
			if manual:
				_align()
	return changed


## 남은 판단을 모두 확정한다 (화면을 건너뛸 때)
func finish() -> void:
	advance(INF)


## 이 판단에서 뛰는가: 목표를 눌렀으면 거기까지, 안 눌렀으면 보조는 코치 사인·수동은 멈춘다
func _choice(d: Dictionary) -> bool:
	if target >= 0:
		return target >= int(d.to)
	return false if manual else bool(d.ai)


## 수동: 아직 확정 안 된 다음 판단이 "뛴다"로 돌려져 있으면 "멈춘다"로 다시 돌려 둔다 (목표를 누르기 전의 기본 그림)
func _align() -> void:
	var d := pending()
	if not d.is_empty() and bool(d.go):
		play = _resim.call(_locked.duplicate() + [false])
