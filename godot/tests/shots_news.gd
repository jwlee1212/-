extends Node
## 폰 뉴스 확인용 스크린샷 → godot/build/shots/news-*.png
##   ~/Applications/Godot.app/Contents/MacOS/Godot --always-on-top --path godot res://tests/shots_news.tscn
## 1학년을 빨리 감아(FastForward) 기사가 쌓인 커리어로 허브 → 폰 뉴스 목록 → 게시글 → 전문 매체 기사 → 일간 매체 기사

var _app: Control


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(844, 390)
	await get_tree().process_frame
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	_app = load("res://scenes/batting.tscn").instantiate()
	_app.save_path = "user://shots_news.save"
	SaveGame.delete(_app.save_path)
	add_child(_app)
	await _frames(10)
	var career := CareerState.new(_app.career_config, "이도현", _app.batting_config)
	FastForward.run(career, 2, "phase:2")
	# 이야기는 남겨 두지 않는다 (허브에서 대화 화면이 먼저 뜨지 않게)
	for timing in ["start", "after"]:
		while not career.events(timing).is_empty():
			var run := DialogueRun.new(career, career.events(timing)[0])
			run.auto(0)
			run.commit()
	_app.start_test_career(career)
	await _frames(10)
	var home = _app._current
	home._notify_messages()
	await _wait(0.8)
	await _shot("news-hub-toast")
	home.open_phone()
	var pv: PhoneView = home.phone_view()
	pv.show_tab(PhoneView.TAB_NEWS)
	await _frames(10)
	await _shot("news-list")
	for source: String in ["board", "report", "daily", "local"]:
		for i in range(career.feed.articles.size() - 1, -1, -1):
			var a: Dictionary = career.feed.articles[i]
			if a.source == source:
				pv.open_article(int(a.id))
				await _frames(10)
				await _shot("news-article-" + source)
				break
	print("기사 %d건" % career.feed.articles.size())
	_app.end_test()
	get_tree().quit()


func _wait(sec: float) -> void:
	await get_tree().create_timer(sec).timeout


func _frames(n: int) -> void:
	for i in n:
		await get_tree().process_frame


func _shot(name: String) -> void:
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % name)
