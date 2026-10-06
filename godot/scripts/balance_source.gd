class_name BalanceSource
extends Node
## balance.json 을 비동기로 받아 오고 저장한다 (웹은 개발 서버와 HTTP 로, 맥은 파일로).
##   var balance: Dictionary = await source.fetch()
##   var error: String = await source.save(patch)

var last_origin := ""  ## 마지막으로 읽은 곳 ("개발 서버" / "원본 파일" / "앱 내장 복사본")


## name: "balance.json" | "content.json"
func fetch(name: String = "balance.json") -> Dictionary:
	if OS.has_feature("web"):
		var res: Array = await _request(HTTPClient.METHOD_GET, "", name)
		if res[0] == "":
			var data: Variant = JSON.parse_string(res[1])
			if data is Dictionary:
				last_origin = "개발 서버"
				return data
		last_origin = "앱 내장 복사본"
		return BalanceLoader.parse(FileAccess.get_file_as_string(BalanceLoader.bundled_path(name)))
	last_origin = "원본 파일" if FileAccess.file_exists(BalanceLoader.source_path(name)) else "앱 내장 복사본"
	return BalanceLoader.load_config(name)


## 고친 수치를 원본 config/balance.json 에 저장한다. 성공하면 빈 문자열
func save(patch: Dictionary) -> String:
	if OS.has_feature("web"):
		var res: Array = await _request(HTTPClient.METHOD_POST, JSON.stringify({"patch": patch}), "balance.json")
		return res[0]
	return BalanceLoader.save_patch_to_file(patch)


## [오류 문자열("" = 성공), 응답 본문]
func _request(method: HTTPClient.Method, body: String, name: String) -> Array:
	var http := HTTPRequest.new()
	add_child(http)
	# 캐시를 피하려고 주소 끝에 시각을 붙인다 (난수가 아니라 시계라 원칙 2와 무관)
	var url := str(JavaScriptBridge.eval("new URL('%s', window.location.href).href" % name)) + "?t=%d" % Time.get_ticks_msec()
	var err := http.request(url, ["Content-Type: application/json"], method, body)
	if err != OK:
		http.queue_free()
		return ["요청 실패 (%d)" % err, ""]
	var r: Array = await http.request_completed
	http.queue_free()
	var code: int = r[1]
	var text: String = (r[3] as PackedByteArray).get_string_from_utf8()
	if r[0] != HTTPRequest.RESULT_SUCCESS or code != 200:
		return ["서버 응답 %d %s" % [code, text.substr(0, 120)], text]
	return ["", text]
