extends Node
## 컷신 확인용 스크린샷 → godot/build/shots/cutscene-<id>-<컷>-<순간>.png
##   ~/Applications/Godot.app/Contents/MacOS/Godot --always-on-top --path godot res://tests/shots_cutscene.tscn
## 컷신을 시간으로 멈춰 세워(CutsceneView.show_at) 컷마다 시작·가운데·끝 가까이를 찍는다. 히트스톱·번쩍임 순간도 따로.
## 마지막에 이야기 카드(EP05 #6) 안에서 실제로 틀어 카드로 돌아오는지 본다

var _app: Control


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(844, 390)
	await get_tree().process_frame
	get_tree().create_timer(120.0).timeout.connect(func() -> void: get_tree().quit(1))
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	_app = load("res://scenes/batting.tscn").instantiate()
	_app.save_path = "user://shots_cutscene.save"
	_app.show_tips = false
	SaveGame.delete(_app.save_path)
	add_child(_app)
	await _frames(10)
	_app.start_career("김도윤")
	await _frames(10)
	var career: CareerState = _app.career
	for id: String in career.cfg.cutscenes:
		var cs: Dictionary = career.cfg.cutscenes[id]
		var cv := CutsceneView.new(career, id)
		_app.add_child(UiKit.fill_parent(cv))
		cv.set_process(false)  # 시간은 여기서 정한다 (트리에 붙인 뒤에 꺼야 한다 — 붙을 때 다시 켜진다)
		await _frames(3)
		var shots: Array = cs["shots"]
		for i in shots.size():
			var shot: Dictionary = shots[i]
			var start := CutscenePlan.shot_start(cs, i)
			var moments := {"a": 0.15, "b": float(shot["dur"]) * 0.5, "c": float(shot["dur"]) - 0.12}
			for key in ["hold", "flash"]:
				if shot.has(key):
					moments["hit"] = float(shot[key][0]) + 0.05
			for m: String in moments:
				cv.show_at(start + float(moments[m]))
				await _frames(2)
				await _shot("cutscene-%s-%d-%s" % [id, i + 1, m])
		cv.queue_free()
		await _frames(3)
	# 카드 안에서: EP05 #6 → 컷신 → (끝나면) 카드
	for e: Dictionary in career.cfg.events:
		if e.id == "ep05_06":
			var dv := DialogueView.new(career, e)
			_app.add_child(UiKit.fill_parent(dv))
			await _frames(5)
			var cv2 := dv.cutscene_view()
			print("카드 안 컷신: ", cv2 != null)
			if cv2 != null:
				cv2.finish()
				await _frames(5)
			await _shot("cutscene-card-after")
			dv.queue_free()
	SaveGame.delete(_app.save_path)
	get_tree().quit()


func _frames(n: int) -> void:
	for i in n:
		await get_tree().process_frame


func _shot(name: String) -> void:
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % name)
