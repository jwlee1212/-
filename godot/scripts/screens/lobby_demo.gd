class_name LobbyDemo
extends Control
## 로비 배경: 타석 화면(BattingView)을 그대로 깔고 컴퓨터 타자가 혼자 친다 (게임 시작 전 데모 화면).
## 소리는 내지 않고, 판정 문구(callout)도 지운다 — 메뉴를 고르는 데 방해되지 않게. 기록에는 아무것도 남지 않는다.
## 스트라이크가 오면 공이 도착하는 순간 + 치우침 ± 흔들림에 친다. 흔들림은 타석·공 번호로 정해지는 가짜 난수라 매번 같은 장면이 나온다.

var _session: BattingSession
var _view: BattingView
var _pres: Presentation
## 이번 공을 칠 시각 (공을 놓은 순간부터 ms). 안 칠 공이면 INF
var _tap_at_ms := INF
var _pitch_no := 0


func _init(config: BattingConfig, presentation: Presentation, jersey_number: int) -> void:
	_pres = presentation
	_session = BattingSession.new(config, presentation)
	_session.update_skills(presentation.lobby_skills)
	_view = BattingView.new()
	_view.session = _session
	_view.jersey_number = jersey_number
	_view.show_zone = false


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(UiKit.fill_parent(_view))
	# 타석 화면은 탭을 받아 스윙하지만 로비에서는 메뉴가 탭을 받는다
	_view.mouse_filter = Control.MOUSE_FILTER_IGNORE


func _process(_delta: float) -> void:
	_session.tick()
	_session.callout = null
	var e := _session.since(_session.phase_since_ms)
	match _session.phase:
		BattingSession.Phase.WAITING:
			# 고를 사람이 없으니 스윙 고르는 시간은 건너뛴다
			if e >= _pres.pitch_interval_ms:
				_session.ready_now()
				_pitch_no += 1
				_tap_at_ms = INF
				if _session.pitch != null and _session.pitch.is_strike():
					_tap_at_ms = _session.pitch.flight_ms + _pres.lobby_tap_bias_ms + (_jitter() * 2.0 - 1.0) * _pres.lobby_tap_spread_ms
		BattingSession.Phase.FLIGHT:
			if not _session.swung and e >= _tap_at_ms:
				_session.tap()
		BattingSession.Phase.OVER:
			if e >= _pres.lobby_over_hold_ms:
				# 타석마다 상대 투수를 바꿔 구종이 달라 보이게
				_session.cycle_pitcher()
				_session.tap()


## 0~1, 타석 번호와 공 번호로 정해진다
func _jitter() -> float:
	return fposmod(sin(_session.seed_value * 12.9898 + _pitch_no * 78.233) * 43758.5453, 1.0)
