class_name SoundPlayer
extends Node
## 효과음 재생기. 소리마다 플레이어를 몇 개씩 두어 같은 소리가 겹쳐 울릴 수 있게 한다.
## 웹에서 브라우저가 "사용자가 만진 뒤에만 소리"를 요구하는 건 Godot 가 알아서 처리한다.

const POOL := 3

var _players := {}  # Sfx -> Array[AudioStreamPlayer]
var _next := {}


func _ready() -> void:
	var sounds := SoundSynth.all()
	for sfx: SoundSynth.Sfx in SoundSynth.Sfx.values():
		var stream := file_stream(sfx)
		if stream == null:
			if not sounds.has(sfx):
				continue  # 파일로만 있는 소리인데 파일이 없다 → 조용히 넘어간다
			stream = SoundSynth.to_stream(sounds[sfx])
		var pool: Array[AudioStreamPlayer] = []
		for i in POOL:
			var p := AudioStreamPlayer.new()
			p.stream = stream
			add_child(p)
			pool.append(p)
		_players[sfx] = pool
		_next[sfx] = 0


## 미리 만들어 둔 소리 파일 (audio/sfx/<소리 이름 소문자>.wav — 관중 함성처럼 실행 중 합성하기엔 무거운 것,
## scripts/make_bgm.py 가 만든다). 없으면 null 이고 SoundSynth 합성음을 쓴다
static func file_stream(sfx: SoundSynth.Sfx) -> AudioStream:
	var path := "res://audio/sfx/%s.wav" % String(SoundSynth.Sfx.keys()[sfx]).to_lower()
	return load(path) if ResourceLoader.exists(path) else null


## volume 0~1, pitch_scale 은 재생 속도 (1 = 원음)
func play(sfx: SoundSynth.Sfx, volume: float = 1.0, pitch_scale: float = 1.0) -> void:
	if not _players.has(sfx):
		return
	var pool: Array[AudioStreamPlayer] = _players[sfx]
	var index: int = _next[sfx]
	_next[sfx] = (index + 1) % pool.size()
	var p := pool[index]
	p.volume_db = linear_to_db(maxf(volume, 0.0001))
	p.pitch_scale = pitch_scale
	p.play()
