class_name MusicPlayer
extends Node
## 배경음악 재생기. 화면이 바뀌면 곡을 바꾸고, 앞 곡은 줄이면서 새 곡을 키운다(크로스페이드).
## 곡 파일은 scripts/make_bgm.py 가 코드로 합성한 루프 WAV (audio/bgm/<곡 id>.wav). 파일이 없으면 조용히 넘어간다.
## 곡마다 크기·바꾸는 시간은 balance.json presentation.bgm 에서 읽는다 (Presentation).

const DIR := "res://audio/bgm/"
## 곡 id: 로비(타이틀·선수 만들기) · 홈(경기 밖 화면) · 경기
const TRACKS: PackedStringArray = ["lobby", "home", "game"]

## 지금 곡 ("" = 없음)
var current := ""

var _volumes := {}  # 곡 id -> 0~1
var _fade_s := 0.8
var _streams := {}  # 곡 id -> AudioStream
var _players: Array[AudioStreamPlayer] = []
var _active := 0
var _tween: Tween


func _ready() -> void:
	for i in 2:
		var p := AudioStreamPlayer.new()
		p.volume_db = linear_to_db(0.0001)
		add_child(p)
		_players.append(p)
	for id in TRACKS:
		var path := DIR + id + ".wav"
		if not ResourceLoader.exists(path):
			continue
		var s: AudioStream = load(path)
		if s is AudioStreamWAV:
			# 루프: 파일의 루프 지점과 상관없이 처음 → 끝을 반복
			var wav := s as AudioStreamWAV
			wav.loop_mode = AudioStreamWAV.LOOP_FORWARD
			wav.loop_begin = 0
			wav.loop_end = int(round(wav.get_length() * wav.mix_rate))
		_streams[id] = s


## 수치 반영 (처음과 손맛 패널 "다시 읽기" 때). 지금 곡 크기도 바로 바꾼다
func configure(presentation: Presentation) -> void:
	_volumes = presentation.bgm_volumes
	_fade_s = presentation.bgm_fade_ms / 1000.0
	if current != "":
		_players[_active].volume_db = _db(current)


## 곡 바꾸기. 같은 곡이면 이어서 튼다
func play(id: String) -> void:
	if id == current:
		return
	current = id
	var old := _players[_active]
	_active = 1 - _active
	var next := _players[_active]
	if _tween != null:
		_tween.kill()
	next.stop()  # 바로 전 전환에서 줄어들던 곡이 아직 남아 있을 수 있다
	var has_next := _streams.has(id)
	if not has_next and not old.playing:
		return
	_tween = create_tween().set_parallel(true)
	if has_next:
		next.stream = _streams[id]
		next.volume_db = linear_to_db(0.0001)
		next.play()
		_tween.tween_property(next, "volume_db", _db(id), _fade_s)
	if old.playing:
		_tween.tween_property(old, "volume_db", linear_to_db(0.0001), _fade_s)
		_tween.chain().tween_callback(old.stop)


func stop() -> void:
	play("")


func _db(id: String) -> float:
	return linear_to_db(maxf(float(_volumes.get(id, 0.5)), 0.0001))
