class_name Presentation
extends RefCounted
## 화면 연출·소리 수치 (balance.json battingPrototype.presentation). 판정과는 무관하다.

var windup_ms: float
var pitch_interval_ms: float
var result_hold_ms: float
var hit_flight_ms: float
## 배트를 휘두르는 시간
var swing_ms: float
## 맞는 순간 화면을 멈추는 시간 (히트스톱)
var hit_stop_ms: float
## 날아오는 공 뒤 잔상: 지난 trail_ms 동안의 자리를 trail_dots 개 점으로 (0 이면 끔)
var trail_ms: float
## 화면에서만 변화(break·arc)를 과장하는 배율 (x 좌우, y 위아래)
var movement_scale := Vector2.ONE
var trail_dots: int
var flash_ms: float
var burst_ms: float
var burst_home_run_ms: float
var shake_ms: float
var shake_solid_px: float
var shake_weak_px: float
var shake_home_run_px: float
var confetti_ms: float
var sfx_volume: float
## 배경음악 크기 (곡 id -> 0~1, MusicPlayer.TRACKS) · 곡 바꿀 때 크로스페이드 시간
var bgm_volumes := {}
var bgm_fade_ms: float
## 심판 콜 크기 (0~1, 효과음 크기에 곱한다): 그냥 보낸 스트라이크 · 루킹 삼진 · 헛스윙 삼진
var umpire_strike_volume: float
var umpire_strikeout_looking_volume: float
var umpire_strikeout_swinging_volume: float
## 타이밍이 완벽할수록 타격음이 이만큼 높아진다 (재생 속도 비율)
var crack_pitch_spread: float
## 정타 뒤 투구 화면에서 공이 튀어 나가는 시간 (그다음 중계 화면)
var launch_ms: float
## 중계 화면 재생 배속 (1 = 실제 시간)
var broadcast_speed: float
## 플레이가 끝나고 결과를 띄우기까지
var after_play_hold_ms: float
## 투구 전, 투수가 세트 자세로 기다리며 스윙 종류를 고르는 시간
var select_ms: float
## 타석이 시작될 때 왼쪽 아래 타자 자막이 떠 있는 시간 (첫 공을 던지면 그 전이라도 들어간다), 미끄러져 나오고 들어가는 시간
var batter_card_ms: float
var batter_card_slide_ms: float
## 로비 메뉴 판이 미끄러져 들어오는 시간, 메뉴 줄이 하나씩 나타나는 간격
## 타자가 앞발을 내디뎌 땅을 딛는 때 (공이 날아오는 시간 중 비율). 그 전에 스트라이드, 뒤에는 발사 자세
var stride_land_at: float
## 공이 끝난 뒤 멈춰 있던 타자가(pitchIntervalMs 동안) 준비 자세로 돌아가는 시간
var batter_recover_ms: float
## 홈런 뒤 이만큼(ms) 지나면 탭으로 베이스 도는 장면을 건너뛸 수 있다
var skip_after_ms: float
var lobby_menu_slide_ms: float
var lobby_menu_stagger_ms: float


static func from_balance(balance: Dictionary) -> Presentation:
	var s: Dictionary = balance[BattingConfig.SECTION]["presentation"]
	var p := Presentation.new()
	p.windup_ms = BattingConfig.num(s, "windupMs")
	p.pitch_interval_ms = BattingConfig.num(s, "pitchIntervalMs")
	p.result_hold_ms = BattingConfig.num(s, "resultHoldMs")
	p.hit_flight_ms = BattingConfig.num(s, "hitFlightMs")
	p.swing_ms = BattingConfig.num(s, "swingMs")
	p.hit_stop_ms = BattingConfig.num(s, "hitStopMs")
	p.trail_ms = BattingConfig.num(s, "trailMs")
	p.movement_scale = Vector2(BattingConfig.num(s, "movementScaleX"), BattingConfig.num(s, "movementScaleY"))
	p.trail_dots = int(BattingConfig.num(s, "trailDots"))
	p.flash_ms = BattingConfig.num(s, "flashMs")
	p.burst_ms = BattingConfig.num(s, "burstMs")
	p.burst_home_run_ms = BattingConfig.num(s, "burstHomeRunMs")
	p.shake_ms = BattingConfig.num(s, "shakeMs")
	p.shake_solid_px = BattingConfig.num(s, "shakeSolidPx")
	p.shake_weak_px = BattingConfig.num(s, "shakeWeakPx")
	p.shake_home_run_px = BattingConfig.num(s, "shakeHomeRunPx")
	p.confetti_ms = BattingConfig.num(s, "confettiMs")
	p.sfx_volume = BattingConfig.num(s, "sfxVolume")
	for id in MusicPlayer.TRACKS:
		p.bgm_volumes[id] = BattingConfig.num(s, "bgm." + id)
	p.bgm_fade_ms = BattingConfig.num(s, "bgm.fadeMs")
	p.umpire_strike_volume = BattingConfig.num(s, "umpireCall.strike")
	p.umpire_strikeout_looking_volume = BattingConfig.num(s, "umpireCall.strikeoutLooking")
	p.umpire_strikeout_swinging_volume = BattingConfig.num(s, "umpireCall.strikeoutSwinging")
	p.crack_pitch_spread = BattingConfig.num(s, "crackPitchSpread")
	p.launch_ms = BattingConfig.num(s, "launchMs")
	p.broadcast_speed = BattingConfig.num(s, "broadcastSpeed")
	p.after_play_hold_ms = BattingConfig.num(s, "afterPlayHoldMs")
	p.select_ms = BattingConfig.num(s, "selectMs")
	p.batter_card_ms = BattingConfig.num(s, "batterCardMs")
	p.batter_card_slide_ms = BattingConfig.num(s, "batterCardSlideMs")
	p.lobby_menu_slide_ms = BattingConfig.num(s, "lobby.menuSlideMs")
	p.lobby_menu_stagger_ms = BattingConfig.num(s, "lobby.menuStaggerMs")
	p.stride_land_at = BattingConfig.num(s, "strideLandAt")
	p.batter_recover_ms = BattingConfig.num(s, "batterRecoverMs")
	p.skip_after_ms = BattingConfig.num(s, "skipAfterMs")
	return p
