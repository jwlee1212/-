extends SceneTree
## 효과음을 WAV 로 꺼내 맥에서 바로 들어 본다 → godot/build/sfx/<이름>.wav
##   $G --headless --path godot --script res://tests/export_sfx.gd
##   open godot/build/sfx/CRACK_SOLID.wav


func _init() -> void:
	var dir := ProjectSettings.globalize_path("res://build/sfx")
	DirAccess.make_dir_recursive_absolute(dir)
	var sounds := SoundSynth.all()
	for sfx: SoundSynth.Sfx in sounds:
		var name: String = SoundSynth.Sfx.keys()[sfx]
		var path := dir.path_join(name + ".wav")
		if SoundPlayer.file_stream(sfx) != null:
			continue  # 파일로 된 소리(관중 함성)는 godot/audio/sfx/ 에 있다
		SoundSynth.to_stream(sounds[sfx]).save_to_wav(path)
		print(path)
	quit()
