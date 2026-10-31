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
## 추월 금지: 앞 주자가 서 있거나 가는 베이스 너머는 누를 수 없다 (판단 기록의 limit).
## 주루 내내: 베이스 사이를 달리는 동안에도 판단("dir", 앞/뒤)이 자주 열려서, 누른 베이스를 바꾸면 바로 그쪽으로 간다
## (지나온 베이스를 누르면 돌아선다). 버튼은 내 주자가 멈추거나 아웃·득점할 때까지 떠 있다 (active).
## 런다운: 내 주자가 두 베이스 사이에 갇히면 그 두 베이스만 누를 수 있다 — 누른 쪽으로 도망친다.
## 런다운 전에 누른 목표(예: 홈)는 런다운 판단에 쓰지 않는다 (갇힌 뒤 새로 눌러야 한다).
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
## 목표를 누른 플레이 시각
var target_t := -INF
## 화면이 마지막으로 알려 준 플레이 시각 (advance)
var now := 0.0

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


## 방향 바꾸기("dir") 말고 진짜 갈림길(한 베이스 더·태그업·런다운)이 있는 플레이인가 — 루상 주루 장면을 띄울지 정할 때
func has_choices() -> bool:
	return play.decisions.any(func(d: Dictionary) -> bool: return str(d.kind) != "dir")


## 내 주자가 아직 뛰고 있나 (베이스 버튼을 띄워 둔다): 멈추거나(세이프) 아웃·득점한 시각 전까지
func active() -> bool:
	if play.decisions.is_empty():
		return false
	var id: int = play.decisions[0].id
	for r in play.runners:
		if r.id != id:
			continue
		if r.state == RunnerAI.State.SAFE or not r.is_on_field():
			return r.history.is_empty() or now <= float(r.history[r.history.size() - 1][0])
		return true
	return false


## 아직 확정되지 않은 다음 판단. 없으면 {} (베이스 버튼을 숨긴다).
## 돌려주는 값: PlaySimulator 의 판단 기록 {t, id, kind, from, to, ai, go}
func pending() -> Dictionary:
	if _locked.size() >= play.decisions.size():
		return {}
	var d: Dictionary = play.decisions[_locked.size()]
	# 런다운 판단은 런다운이 벌어진 뒤에야 보인다 (미리 보이면 갇힐 걸 알려 주는 셈).
	# 벌어진 뒤에는 다음 송구 전이라도 계속 보인다 (버튼이 깜빡이지 않게, 미리 눌러 둘 수 있게)
	if now < float(d.get("since", -INF)):
		return {}
	return d


## 지금 판단이 런다운인가
func in_rundown() -> bool:
	return str(pending().get("kind", "")) == "rundown"


## 지금 누를 수 있는 가장 가까운 베이스 (그 앞 베이스는 이미 지났다). 판단이 없으면 -1
func min_base() -> int:
	var d := pending()
	return -1 if d.is_empty() else int(d.from)


## 누를 수 있는 가장 먼 베이스: 앞 주자 바로 뒤까지 (추월 금지), 런다운이면 앞 베이스. 판단이 없으면 -1
func max_base() -> int:
	var d := pending()
	if d.is_empty():
		return -1
	if str(d.kind) == "rundown":
		return int(d.to)
	return int(d.get("limit", 4))


## 지금 판단에 쓸 목표인가 (런다운은 갇힌 뒤 누른 것만)
func target_counts() -> bool:
	return _counts(pending())


## 지금대로면 어디로 가나: 누른 베이스, 안 눌렀으면 보조는 코치 사인·수동은 가장 가까운 베이스
func going() -> int:
	if target_counts():
		return clampi(target, min_base(), max_base())
	var coach := coach_base()
	return min_base() if coach < 0 else coach


## 코치 사인이 가리키는 베이스 (보조일 때만, 없으면 -1): 다음 판단에서 AI 라면 갈 곳
func coach_base() -> int:
	var d := pending()
	if d.is_empty() or manual:
		return -1
	if now < float(d.get("show", -INF)) and str(d.kind) == "rundown":
		return -1  # 런다운: 공이 아직 안 움직였다 — 코치도 아직 모른다
	# 달리는 중의 방향 판단은 "지금 방향 그대로"일 뿐이라, 코치 사인은 다음 진짜 갈림길(한 베이스 더)을 보여 준다
	if str(d.kind) == "dir":
		for i in range(_locked.size(), play.decisions.size()):
			var e: Dictionary = play.decisions[i]
			if str(e.kind) == "extra" or str(e.kind) == "tag":
				return int(e.to) if e.ai else int(e.from)
			if str(e.kind) == "rundown":
				break
	return int(d.to) if d.ai else int(d.from)


## 목표 베이스를 누른다 (판단 시각 전까지는 바꿀 수 있다). 이미 지난 베이스는 무시한다
func set_target(base: int) -> void:
	if min_base() >= 0 and base >= min_base() and base <= max_base():
		target = clampi(base, 1, 4)
		target_t = now


## 시간이 흘렀다: 판단 시각이 지난 선택을 확정한다. 플레이가 달라졌으면 true
func advance(t: float) -> bool:
	now = maxf(now, t)
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
	if _counts(d):
		return target >= int(d.to)
	if str(d.kind) == "dir":
		return bool(d.ai)  # 안 눌렀으면 방향 그대로 (수동도)
	return false if manual else bool(d.ai)


## 누른 목표를 이 판단에 쓰나 (런다운 판단은 런다운이 시작된 뒤 누른 것만)
func _counts(d: Dictionary) -> bool:
	return target >= 0 and (str(d.get("kind", "")) != "rundown" or target_t >= float(d.get("since", -INF)))


## 수동: 아직 확정 안 된 다음 판단을 지금 고른 대로(안 눌렀으면 "멈춘다") 미리 돌려 둔다 (AI 가 뛰는 그림을 보여 줬다 바꾸지 않게)
func _align() -> void:
	# 다음 진짜 갈림길(달리는 중의 방향 판단 말고)까지: 그 앞 방향 판단은 지금 고른 대로, 갈림길은 수동 기본값으로
	var ch: Array = _locked.duplicate()
	for i in range(_locked.size(), play.decisions.size()):
		var d: Dictionary = play.decisions[i]
		var want := _choice(d)
		ch.append(want)
		if want != bool(d.go):
			play = _resim.call(ch)
			return
		if str(d.kind) != "dir":
			return
