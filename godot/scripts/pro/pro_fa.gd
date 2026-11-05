class_name ProFA
extends RefCounted
## FA (docs/worldview.md 5-4·6장, balance.json career.pro.fa). 1군에 faWeeks 주(× 한 주 경기 날) 이상 있던 시즌만 등록 시즌으로 센다 (복무 시즌 제외).
## 고졸 seasonsHigh · 대졸 seasonsCollege 시즌이 차고 계약이 남아 있지 않으면 시즌 결산에서 FA — 구단들이 제안을 낸다.
## 첫 FA 가 메인 스토리 엔딩 (잔류 / 이적, worldview 6장). 그 뒤는 커리어 모드 — 계약(years 년)이 끝나면 다시 FA.

static func required(cfg: CareerConfig, pro: ProCareer) -> int:
	return int(cfg.pro["fa"]["seasonsCollege"] if pro.college else cfg.pro["fa"]["seasonsHigh"])


## 시즌 결산에서 (ProCareer.close_season): 등록 시즌 세기 · 계약 해 줄이기 → FA 가 됐나
static func close_season(cfg: CareerConfig, pro: ProCareer) -> bool:
	if pro.first_days >= int(cfg.pro["fa"]["faWeeks"]) * int(cfg.pro["gamesPerWeek"]) and not ProService.serving(pro):
		pro.fa_seasons += 1
	if pro.contract_years_left > 0:
		pro.contract_years_left -= 1
	pro.fa_pending = pro.fa_seasons >= required(cfg, pro) and pro.contract_years_left == 0
	return pro.fa_pending


## 최근 3시즌 평균 기여도 (FA 금액 기준)
static func recent_value(pro: ProCareer) -> float:
	var vals: Array = pro.value_history.slice(maxi(0, pro.value_history.size() - 3))
	if vals.is_empty():
		return 0.0
	var sum := 0.0
	for v in vals:
		sum += float(v)
	return sum / vals.size()


## 제안 [{"team", "name", "annual", "bonus", "years", "home"}] — 원 소속 구단이 먼저, 나머지는 금액 순. 난수 없음
static func offers(cfg: CareerConfig, pro: ProCareer) -> Array:
	var fc: Dictionary = cfg.pro["fa"]
	var v := recent_value(pro)
	var base := maxf(maxf(float(pro.salary), float(fc["minAnnual"])), float(fc["baseAnnual"]) + v * float(fc["perValue"]))
	var rows := []
	for t: ProTeam in pro.league.teams:
		var f := float(fc["teamFactor"].get(t.tier, 1.0))
		if t.id == fc["bigSpender"]:
			f = float(fc["bigSpenderFactor"])
		var home := t.id == pro.team_id
		if home and pro.trust >= int(fc["loyaltyTrust"]):
			f *= float(fc["homeBonus"])
		var annual := roundi(base * f / 10000000.0) * 10000000
		rows.append({"team": t.id, "name": t.name, "annual": annual, "bonus": roundi(annual * float(fc["bonusShare"])),
			"years": int(fc["years"]), "home": home})
	var home_row: Dictionary = rows.filter(func(r: Dictionary) -> bool: return r["home"])[0]
	var others := rows.filter(func(r: Dictionary) -> bool: return not r["home"])
	others.sort_custom(func(a: Dictionary, b: Dictionary) -> bool:
		if a["annual"] != b["annual"]:
			return a["annual"] > b["annual"]
		return a["team"] < b["team"])
	var out := [home_row]
	if v >= float(fc["minValueForBids"]):
		out.append_array(others.slice(0, int(fc["maxBidders"])))
	return out


## 계약 (시즌 결산 화면이 고른 제안). 이적이면 리그 명단을 옮긴다. 첫 FA 면 메인 스토리 엔딩
static func sign(career: CareerState, pro: ProCareer, offer: Dictionary) -> void:
	var moved: bool = offer["team"] != pro.team_id
	if moved:
		var old := pro.team()
		for list: Array in [old.lineup, old.bench]:
			list.erase(pro.me)
		var nt: ProTeam = pro.league.team(offer["team"])
		nt.bench.push_front(pro.me)
		pro.me.team_id = nt.id
		pro.team_id = nt.id
		pro.trust = int(career.cfg.pro["trust"]["afterTransfer"])
		pro.level = ProCareer.LEVEL_FIRST
	pro.salary = int(offer["annual"])
	pro.contract_years_left = int(offer["years"])
	pro.fa_pending = false
	pro.fa_count += 1
	career.player.money += int(offer["bonus"])
	pro.salary_history.append({"year": pro.league.year + 1, "salary": pro.salary})
	pro.season_note = "FA 계약: %s %d년 · 연평균 %s · 계약금 %s" % [offer["name"], int(offer["years"]), ProCareer.money_text(pro.salary),
		ProCareer.money_text(int(offer["bonus"]))]
	career.player.vars["fa_choice"] = "move" if moved else "stay"
	career.player.flags["fa_signed"] = true
	if pro.fa_count == 1:
		career.player.flags["main_ending"] = true
		pro.ending = "move" if moved else "stay"
	Milestones.fire_event(career, pro, "fa_move" if moved else "fa_stay")
	pro.feed.post_custom(career, pro, "fa_move" if moved else "fa_stay", {"team": offer["name"]})
