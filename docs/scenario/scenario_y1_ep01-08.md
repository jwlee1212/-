# 퍼스트픽 — 1학년 시나리오 (EP01 ~ EP08, 카드판)

> **2026-11-04 카드판으로 다시 씀.** 지인 테스트에서 스토리를 전부 넘겨서, 읽는 컷신을 **고르는 카드**로 바꿨다 (`card-plan.md`).
> - 장면 하나 = 카드 한 장. 대사는 1~4줄, 선택지는 고르면 반드시 무언가 바뀐다 (능력치·관계·진로). 선택지 뒤 `| 힌트` 는 카드에서 버튼 아래 보이는 결과 안내다.
> - 하린·소율·준수의 곁가지 이야기는 이 문서가 아니라 폰 문자(`config/content.json` messages, `m_y1_*`)에 있다. 원하는 사람만 답장한다.
> - 경기 대본(EP03 대수비, EP04 첫 타석, EP05 백호기, EP06 병살, EP08 서호대전)과 미니게임은 직접 하는 플레이라 그대로 둔다.
> - 옛 컷신 대본(대사 약 1,000줄)은 git 태그 `pre-card-events` 에 있다.
> - **2026-11-09: EP01 은 스토리판 시험** (한 줄씩 넘기는 대화, 옛 분량의 1/3 — `story-plan.md`). 나머지는 아직 카드.
> - 대사는 이 문서에서만 고친다 → `python3 scripts/build_scenario.py` 가 `content.json` 이벤트로 만든다.
> - 인물·학교·구단·대회 이름은 `docs/worldview.md` 기준.

---

## 0. 표기 규칙

| 표기 | 의미 |
|---|---|
| `SCENE epXX_YY` | 장면(카드) 시작. 시간·장소 포함 |
| `NARR:` | 지문 |
| `화자ID: 대사` | 대사 |
| `{player}` | 주인공 이름 |
| `CHOICE id` | 선택지. `"말" \| 힌트 -> 효과`. `[조건]`이 붙은 항목은 조건 충족 시에만 표시 |
| `IF / ELIF / ELSE / END` | 분기 |
| `GAMEPLAY` | 직접 조작 구간 (미니게임·경기 장면). 여기서 카드가 끊긴다 |
| `CUTSCENE id` | 도트 컷신 (content.json cutscenes, 그림은 scripts/make_cutscene_art.py). 카드가 그 자리에서 틀고 이어 간다. 에피소드마다 하나: ep01_join · ep02_bench · ep03_sink · ep04_first · ep05_seongho · ep06_dp(병살 성공 때) · ep07_sound(폼을 바꿨을 때) · ep08_name(체크 대상일 때). 사이드 스토리 컷신(side_harin_museum · side_soyul_folder)은 데이트 문자 답장 → 다음 주 카드(content.json events y1_side_*)가 연다 |
| `UI` | 휴대폰·화이트보드·전광판 등 화면 표시 |
| `ITEM_SHOW` | 쪽지 화면 |
| `SYSTEM` | 알림, 변수 변경, 해금 |

선택지의 엔진 효과(경험치·컨디션·관계)는 `scripts/build_scenario.py` 의 `CHOICE_FX` 에 있다 (문서에는 힌트로 보인다).

---

## 1. 화자 목록

| 화자ID | 이름 | 소속·설정 |
|---|---|---|
| `player` | {player} | 서림고 1학년 유격수 |
| `harin` | 하린 | 소꿉친구, 옆집. 미대 입시생 (EP01 등굣길, 그 뒤는 폰 문자) |
| `soyul` | 소율 | 야구부 매니저 2학년 |
| `junsu` | 준수 | 박준수, 1학년 포수 |
| `coach_oh` | 오 감독 | 서림고 감독 |
| `taeo` | 강태오 | 청운고 1학년 유격수 (라이벌) |
| `minjae` | 정민재 | 2학년 유격수 → EP06부터 2루수 |
| `doyun` | 한도윤 | 3학년 주장. EP05로 은퇴 |
| `seongho` | 김성호 | 3학년 외야수. EP05로 은퇴 |
| `jaehyuk` | 윤재혁 | 2학년 투수, EP06부터 주장 |
| `na_scout` | 나 팀장 | 나성만, 태성 썬더스 스카우트 팀장 |

하린(소꿉친구)은 EP01 등굣길(스토리판 시험)에만 메인으로 나온다. 그 뒤로는 폰 문자로만 이야기한다.

---

## 2. 변수

| ID | 타입 | 값 | 생성 | 사용 |
|---|---|---|---|---|
| `test_*_grade` | enum | `high` / `mid` / `low` | EP01 | 반응 → EP02·EP03, 스쿼트 → 향후 |
| `warmup_effect` | enum | `calm` / `eager` | EP02 | EP04 첫 타석 보정 |
| `taeo_first_talk` | bool | | EP03 | 고2 (예정) |
| `taeo_ball_fielded` | bool | | EP03 | — |
| `first_ab_result` | enum | `hit` / `out` / `strikeout` | EP04 | — |
| `seongho_tee` | bool | | EP05 | EP05 |
| `team_goal` | string | `"national_first_win"` | EP05 | 고2~고3 |
| `minjae_trust` | int | | EP06 | EP06 병살 송구 보너스 |
| `ep06_game_result` | enum | `win` / `lose` | EP06 | EP06 |
| `dongin_first_win` | bool | | EP06 | 향후 |
| `form_answer` | enum | `change` / `keep` | EP07 | EP07 (새 폼 훈련 · 능력치 상승 폭) |
| `form_adaptation` | int | 0~100 | EP07 | EP07 겨울 배팅 미니게임 판정 (낮을수록 좁다, balance.json drills boostByVar). 폼을 지키면 100 |
| `stats.at_bat_video_count` · `stats.fielding_video_count` | int | | 경기마다 자동 | 소율이 찍은 영상 수 (폰 문자·향후) |
| `training_direction` | enum | `contact` / `power` / `defense_run` | EP07 | EP08, 이후 |
| `scout_interest` | enum | `checked` / `watching` / `none` | EP08 | 고2~ |
| `scout_visited_teams` | list | | EP08 | 고2~ |
| `school_year` | int | | EP08 종료 시 2 | |

휴대폰 해금: `phone_contacts`(EP01) · `phone_records_team`(EP02) · `phone_profile_taeo`(EP03) · `phone_records_personal`(EP04) · `phone_news`(EP05) · `phone_records_season`(EP06) · `scout_tab_unlocked`(EP08).

---

# EP01 — 입부

> **2026-11-09 스토리판 시험 (유저 결정: "스토리가 더 재밌는데 옛날엔 너무 많고 루즈했다").** EP01 만 카드 대신 한 줄씩 넘기는 대화로 되돌렸다.
> 압축 규칙: 에피소드당 장면 2~4개 · 장면당 대사 약 10줄 · 장면은 선택이나 플레이로 끝낸다 · 이동·하루 정리 장면은 뺀다 · 밤 폰 장면은 폰 문자로.
> 옛 EP01(장면 7개·대사 약 120줄) → 장면 4개·대사 약 45줄. 대사는 옛 대본(`pre-card-events`)에서 골라 줄였다.

### SCENE ep01_01 | 아침 | 학교 가는 길

```
NARR: 3월 초, 아파트 단지 앞. 하린이 이어폰을 빼며 기다리고 있다.
harin: 늦었어. 3분.
player: 엘리베이터가 13층에서 안 내려왔어.
harin: 맨날 엘리베이터 탓이야. ...너 진짜 야구부 들어가?

CHOICE ep01_harin_club:
  - "응. 오늘 입부서 낼 거야." | 하린 호감 ++ -> goto A
  - "일단 가보고." | 하린 호감 + -> goto B

A:
  harin: 그래. 그럼 이제 같이 집 갈 일은 없겠네.
B:
  harin: 너 그 말 중학교 때도 했어. 결국 3년 했잖아.

harin: 난 이번 주부터 학원 저녁반이야. 열 시에 끝나.
```

### SCENE ep01_02 | 방과 후 | 야구부실

```
NARR: 운동장 끝, 컨테이너를 고친 부실. 덩치 큰 1학년이 먼저 서 있다.
junsu: 어, 너도 입부? 다행이다, 나 혼자인 줄. 박준수, 포수야.
player: {player}. 유격수.
NARR: 부실 문이 벌컥 열린다.
soyul: 1학년 맞지? 매니저 소율, 2학년. 여기 이름이랑 포지션 써.
soyul: 박준수... 아, 동산중 포수! 도루 저지율 좋던데. 중학교 대회 영상은 웬만하면 다 받아놨거든.
NARR: 주인공 입부서를 받은 소율의 손이 멈춘다.
soyul: {player}... 중3 지역 리그 11경기, 타율 2할 6푼. 근데 영상이 하나도 없어. 이런 경우 처음 봐.

CHOICE ep01_soyul_record:
  - "그럼 이제부터 찍으시면 되죠." | 소율 호감 ++ -> goto A
  - "...그 기록, 어디서 찾으신 거예요?" | 소율 호감 + -> goto B

A:
  soyul: 오, 그렇지. 오늘부터 너 1호 촬영 대상이야.
B:
  soyul: 지역 리그 게시판. 3년 치 엑셀로 정리해놨어. 이제부턴 내가 직접 찍을 거고.

CUTSCENE ep01_join
coach_oh: 소율아, 기록.
soyul: 스톱워치 두 개, 반응속도 측정기까지 준비됐어요.
coach_oh: 측정기는 어디서 났냐.
soyul: 제 거요. 용돈 두 달 치.
```

### SCENE ep01_05 | 운동장 | 체력 테스트

```
SYSTEM: 체력 테스트 — 3종목. 결과에 따라 초기 능력치가 정해집니다.

GAMEPLAY fitness_test id=speed
  result -> test_speed_grade

IF test_speed_grade == high:
  soyul: 오, 빠르다! 2학년 평균보다 빨라.
ELIF test_speed_grade == mid:
  soyul: 괜찮은데? 내야수로는 충분해.
ELSE:
  junsu: 나보다는 빠르네.
  soyul: 준수야, 넌 꼴찌였어.
END

coach_oh: 다음. 스쿼트. 하체가 버텨야 공도 치고 공도 잡는다.

GAMEPLAY fitness_test id=squat
  result -> test_squat_grade

IF test_squat_grade == high:
  soyul: 감독님, 이거 3학년 기록보다 많은데요.
  coach_oh: 알아.
ELIF test_squat_grade == mid:
  junsu: (누워서) 너... 다리 뭐냐...
ELSE:
  soyul: 첫날이니까. 괜찮아.
END

soyul: 마지막. 반응속도. 이거 제일 보고 싶었어.

GAMEPLAY fitness_test id=reaction
  result -> test_reaction_grade

IF test_reaction_grade == high:
  NARR: 감독이 처음으로 팔짱을 푼다.
  coach_oh: ...다시 한 번 해봐.
ELIF test_reaction_grade == mid:
  coach_oh: 됐다.
ELSE:
  coach_oh: 반응은 연습하면 는다.
END

SYSTEM: 초기 능력치가 결정되었습니다.
SYSTEM: test_overall_grade = 3종목 등급의 평균 (high=3, mid=2, low=1 → 평균 2.5 이상 high, 1.5 이상 mid, 미만 low)
```

### SCENE ep01_06 | 테스트 후 | 벤치

```
NARR: 해가 지는 운동장. 감독이 소율의 기록지를 넘겨본다.
IF test_overall_grade == high:
  coach_oh: {player}. 중학교 때 왜 아무도 몰랐냐.
  player: ...대회를 많이 못 나가서요.
  coach_oh: 그래. 내일 6시 반까지 나와.
ELIF test_overall_grade == mid:
  coach_oh: 둘 다 내일 6시 반까지 나와. 늦으면 뛴다.
ELSE:
  coach_oh: 이제 시작이다. 내일 6시 반.
END

soyul: 너희 파일명 만들어놨다. {player}_1학년_3월. 졸업할 때까지 쌓을 거야.
player: ...3년 치요?
soyul: 나중에 프로 가면 내가 원본 갖고 있는 거잖아. 좀 멋있지 않아?

SYSTEM: 해금 — phone_contacts (연락처: 하린, 소율, 준수)
```

---

# EP02 — 벤치

### SCENE ep02_01 | 새벽 | 운동장

```
NARR: 6시 28분. 감독이 이미 펑고 배트를 들고 있다.
coach_oh: 유격수 자리 가 있어.

GAMEPLAY fielding_drill id=fungo
  result -> 수비 경험치

IF test_reaction_grade == high:
  coach_oh: 한 바구니 더.
ELSE:
  coach_oh: 첫 발이 늦다. 공 보고 움직이지 말고, 배트에 맞는 소리 듣고 움직여.
END
```

### SCENE ep02_02 | 아침 | 부실

```
UI whiteboard:
  토요일 주말리그 첫 경기 — vs 동인고
  1학년 {player} · 박준수 — 벤치
junsu: 역시.
```

### SCENE ep02_03 | 토요일 | 주말리그 — 서림고 vs 동인고

```
NARR: 8회 초, 1 대 9. 감독이 벤치를 돌아본다.
coach_oh: {player}. 몸 풀어.

CHOICE ep02_warmup:
  - "(가볍게 스윙하며 상대 투수를 지켜본다)" | 고교 첫 타석에서 선구안 ▲ -> warmup_effect = calm
  - "(최대한 세게 스윙해서 몸을 데운다)" | 고교 첫 타석에서 파워 ▲ · 컨택 ▼ -> warmup_effect = eager

NARR: 차례는 오지 않았다. 1 대 10, 8회 콜드게임.
CUTSCENE ep02_bench
SYSTEM: 해금 — phone_records_team (기록실: 팀 경기 기록)
```

### SCENE ep02_04 | 귀가 버스

```
soyul: 이거 봐. 청운고 1학년 유격수 강태오. 중3 전국대회 MVP.
soyul: 근데 너 운 좋다. 다음 주 연습경기 상대가 청운고야.
```

---

# EP03 — 청운고

### SCENE ep03_02 | 오전 | 청운고 야구장

```
NARR: 청운고 원정 연습경기. 인조잔디, 전광판, 3층짜리 야구부 숙소.
soyul: 저기, 6번. 강태오.
```

### SCENE ep03_04 | 7회 말 | 대수비

```
NARR: 7회 말 2사 주자 없음. 타석에 강태오.
coach_oh: {player}. 유격수 들어가.

GAMEPLAY fielding id=ep03_def
  result -> taeo_ball_fielded

IF taeo_ball_fielded == true:
  NARR: 잡아서 1루. 아웃, 경기 끝. 태오는 유격수 쪽을 돌아보지 않는다.
ELSE:
  NARR: 공이 글러브를 맞고 튄다. 태오는 1루에서 헬멧만 고쳐 쓴다.
END
```

### SCENE ep03_05 | 경기 후 | 야구장 뒤 수돗가

```
CUTSCENE ep03_sink
NARR: 수돗가. 강태오가 얼굴에 물을 끼얹고 있다.

CHOICE ep03_taeo_sink:
  - "아까 3회 그 3유간 수비요. 치기 전에 먼저 움직이던데." | 수비 경험치 (태오의 한마디) -> taeo_first_talk = true ; goto A
  - "(그냥 고개만 숙이고 물을 튼다)" | 컨디션 ▲ -> goto B

A:
  taeo: 그냥 갈 데가 보여서.
B:
  NARR: 태오도 짧게 고개를 까딱하고 간다.

SYSTEM: 해금 — phone_profile_taeo (인물 정보 '강태오')
```

※ 태오는 이 화에서 주인공 이름을 묻지 않는다 (고2에서 처음 묻는다).

---

# EP04 — 첫 타석

### SCENE ep04_02 | 토요일 | 주말리그 — 서림고 vs 한빛고

```
NARR: 주말리그, 한빛고 원정. 오늘도 벤치에서 시작한다.
```

### SCENE ep04_03 | 7회 초 | 대타

```
NARR: 7회 초 2사 1루. 대기 타석의 정민재를 감독이 부른다.
coach_oh: 민재야, 잠깐. {player}. 대타.
CUTSCENE ep04_first
soyul: (지나가며) 초구 직구 80퍼센트. 2스트라이크면 바깥쪽 슬라이더.
```

### SCENE ep04_04 | 타석

```
GAMEPLAY batting id=ep04_ab
  result -> first_ab_result

IF first_ab_result == hit:
  NARR: 좌중간 안타. 고교 첫 안타다.
ELIF first_ab_result == out:
  NARR: 2루수 땅볼. 전력으로 뛰었지만 한 걸음 차이로 아웃.
ELSE:
  NARR: 바깥쪽으로 흘러나가는 슬라이더. 헛스윙 삼진.
END
```

### SCENE ep04_05 | 7회 말 | 유격수

```
coach_oh: 그대로 유격수 들어가.

GAMEPLAY fielding id=ep04_def
  result -> ep04_def_result

IF ep04_def_result == success:
  NARR: 3유간 땅볼, 아웃. 덕아웃에서 박수 소리.
ELSE:
  NARR: 주자 1루. 감독은 표정 없이 다음 수비 사인을 낸다.
END

NARR: 경기는 1 대 6으로 끝났다.
SYSTEM: 해금 — phone_records_personal (기록실: 개인 타격 기록)
```

---

# EP05 — 마지막 날이 아닌 날

### SCENE ep05_01 | 6월 말 | 주말리그 전반기 최종전 이후

```
soyul: 권역 4위! 백호기 본선이야!
coach_oh: 3년 만이다.
```

### SCENE ep05_02 | 7월 | 대회 전주 | 배팅 케이지

```
NARR: 백호기 전주. 배팅 케이지.

GAMEPLAY batting_practice id=ep05_cage
  result -> 타격 경험치

seongho: 1학년, 공 좀 올려줄래?

CHOICE ep05_seongho:
  - "네, 올려드릴게요." | 성호 선배 친분 ▲ · 평판 ▲ -> seongho_tee = true ; goto A
  - "저 먼저 들어가 볼게요." | 컨택 경험치 (혼자 더 친다) -> goto B

A:
  seongho: 고마워. 3년 동안 이거 혼자 했거든.
B:
  NARR: 혼자 남아 스윙을 더 돌린다.
```

### SCENE ep05_03 | 대회 당일 | 서울 원정 버스

```
soyul: 백호기 1회전 상대 해성고. 작년 8강. 청운고는 반대편이야.
```

### SCENE ep05_04 | 7회 초 | 대타

```
NARR: 7회 초 1사 2루, 1 대 5. 감독이 부른다.
minjae: ...쳐.
soyul: (지나갈 때) 초구 몸쪽 직구 70퍼센트.

GAMEPLAY batting id=ep05_ab
  result -> ep05_ab_result

IF ep05_ab_result == hit:
  NARR: 몸쪽 직구를 당겨 3유간! 2루 주자 홈인.
ELIF ep05_ab_result == out:
  NARR: 2루 쪽 땅볼. 주자는 3루로.
ELSE:
  NARR: 포크볼. 배트가 공 위로 지나간다.
END
```

### SCENE ep05_05 | 7회 말 | 유격수

```
GAMEPLAY fielding id=ep05_def
  result -> ep05_def_result

IF ep05_def_result == success:
  NARR: 아웃. 관중석 서림고 학부모 쪽에서 작은 박수.
ELSE:
  NARR: 주자 출루. 다음 타자 범타로 실점 없이 끝.
END
```

### SCENE ep05_06 | 9회 초 | 마지막 타석

```
CUTSCENE ep05_seongho

IF seongho_tee == true:
  seongho: 공 올려줘서 고마웠다.
END

NARR: 3년 만의 첫 타석. 한 걸음 차이로 아웃. 백호기 1회전 탈락.
doyun: 내년엔 네가 좀 쳐라. 우리 학교 전국대회 1승 아직 없다.

UI phone_news_notification:
  백호기 1회전 — 청운고 콜드게임 승리, 1학년 강태오 2안타 3타점

SYSTEM: team_goal = "national_first_win" — 팀 목표 "전국대회 1승" 등록
SYSTEM: 해금 — phone_news (뉴스 탭: 고교야구 대회 소식)
```

---

# EP06 — 새 팀

### SCENE ep06_01 | 8월 초 | 운동장

```
NARR: 3학년이 빠지고 열 명이 남았다.
coach_oh: 주장은 재혁이. 민재, 2루로 간다. 유격수는 {player}.
minjae: ...알겠습니다.
```

### SCENE ep06_02 | 오후 | 내야 — 병살 연습

```
coach_oh: 둘이 병살 연습부터 해. 오늘부터 매일.

GAMEPLAY fielding_drill id=dp_practice
  result -> ep06_dp_practice_success

IF ep06_dp_practice_success >= 4:
  coach_oh: 됐다. 내일도.
ELIF ep06_dp_practice_success >= 2:
  coach_oh: 토스가 높다. 2루수 가슴 높이로.
ELSE:
  coach_oh: 오늘은 해 질 때까지 한다.
END

minjae: 너 토스할 때 손목 써. 팔 전체로 밀어. 2루수가 받기 편하게.

CHOICE ep06_minjae:
  - "알겠습니다. 내일 다시 해볼게요." | 민재 신뢰 ▲ — 실전 병살 송구 보너스 -> minjae_trust += 1 ; goto A
  - "(고개만 숙인다)" | 컨디션 ▲ (혼자 정리한다) · 민재 신뢰 그대로 -> goto B

A:
  minjae: 그래.
B:
  NARR: 정민재가 그대로 걸어간다.
```

### SCENE ep06_04 | 9월 | 주말리그 후반기 개막 — 서림고 vs 동인고

```
UI scoreboard_lineup:
  9번 유격수 {player}
NARR: 후반기 개막, 동인고전. 첫 선발이다.

GAMEPLAY full_game id=ep06_game
  result -> ep06_game_result
```

#### 7회 말 — 병살 상황

```
NARR: 7회 말 2 대 2, 무사 1루.
jaehyuk: (마운드에서) 땅볼 유도한다. 병살!

GAMEPLAY fielding id=ep06_dp
  result -> ep06_dp_result

IF ep06_dp_result == success:
  CUTSCENE ep06_dp
  NARR: 6-4-3 병살! 정민재가 글러브로 허벅지를 한 번 친다.
ELSE:
  NARR: 토스가 살짝 높았다. 타자 주자 세이프.
END
```

#### 9회 — 결과

```
IF ep06_game_result == win:
  soyul: 동인고 상대 첫 승. 창단 이후 처음이야.
  SYSTEM: dongin_first_win = true
ELSE:
  soyul: 지난번엔 콜드였는데. 오늘은 9회까지 갔어.
END
```

### SCENE ep06_07 | 10월 말 | 후반기 최종전 이후 | 부실

```
UI whiteboard_stats:
  후반기 개인 기록

IF ep06_season_avg_grade == high:
  soyul: 9번 타자 타율로는 권역 1등이야. 몰랐지?
ELIF ep06_season_avg_grade == mid:
  soyul: 처음 선발인 1학년치고는 괜찮아.
ELSE:
  soyul: 숫자는 별로인데, 마지막 3경기는 좋아졌어.
END

coach_oh: 겨울에 할 얘기가 있다. 방학하면 첫날 6시 반에 나와.
SYSTEM: 해금 — phone_records_season (기록실: 시즌 기록 탭)
```

---

# EP07 — 겨울

### SCENE ep07_01 | 겨울방학 첫날 새벽 6시 반 | 부실

```
NARR: 노트북 화면에 내 스윙과 KKG 스타즈 유격수 백승현의 스윙이 나란히 돈다.
coach_oh: 넌 손으로 친다. 맞히긴 잘 맞히는데 공이 안 뻗어.
coach_oh: 겨울 동안 폼을 바꾸자. 처음 한 달은 지금보다 못 칠 거다.

CHOICE ep07_form:
  - "하겠습니다." | 겨울 훈련 동안 타격 판정이 좁아진다 — 끝나면 능력치 크게 상승 -> form_answer = change ; form_adaptation = 0 ; goto A
  - "지금 폼으로 가겠습니다." | 고생은 없지만 능력치 상승 폭이 작다 -> form_answer = keep ; form_adaptation = 100 ; goto B

A:
  coach_oh: 대답이 빠르네. 내일부터.
B:
  coach_oh: ...그래. 네 몸이다. 대신 겨울에 다른 걸 더 한다.
```

### SCENE ep07_02 | 12월 마지막 주 | 운동장 — 새 폼 1주차

```
NARR: 새 폼 1주차. 헛스윙, 빗맞은 타구, 또 헛스윙.

GAMEPLAY batting_practice id=ep07_new_form_w1
  (form_answer == change 일 때만 — build_scenario.py WHEN)

coach_oh: 손 먼저 나갔다. 다시.
SYSTEM: form_adaptation += 20
```

### SCENE ep07_05 | 1월 중순 | 운동장 — 훈련 방향

```
coach_oh: 남은 겨울은 하나에 집중한다. 네가 골라.

CHOICE ep07_training:
  - "컨택 — 공을 끝까지 보고 정확히 맞히는 쪽" | 캠프 컨택 집중 · 서호대전 선구안 ▲ -> training_direction = contact ; goto A
  - "파워 — 타구를 멀리 보내는 쪽" | 캠프 파워 집중 · 서호대전 파워 ▲ -> training_direction = power ; goto B
  - "수비·주루 — 유격수로서 첫 발과 송구, 주루 스타트" | 캠프 수비 집중 · 서호대전 수비·주력 ▲ -> training_direction = defense_run ; goto C

A:
  coach_oh: 맞히는 감각을 다시 만든다. 하루 500구.
B:
  coach_oh: 하체 웨이트 두 배. 배팅은 롱티 위주로.
C:
  coach_oh: 아침엔 펑고, 오후엔 베이스러닝.

GAMEPLAY training_minigame id=ep07_focus
```

### SCENE ep07_06 | 1월 말 저녁 | 배팅 케이지

```
NARR: 1월 마지막 날. 배팅 케이지에 혼자.

GAMEPLAY batting_practice id=ep07_final

IF form_answer == change:
  CUTSCENE ep07_sound
  NARR: 7구째. 소리가 다르다. 짧고 단단하다.
  soyul: (부실 안에서) 감독님, 방금 그 소리 맞죠? 맞죠?
  SYSTEM: form_adaptation = 100 — 타격폼 개조 완료
  SYSTEM: 능력치 대폭 상승 (training_direction에 따라 추가 보너스)
ELSE:
  NARR: 익숙한 폼 그대로. 겨울 동안 정타가 조금 늘었다.
  SYSTEM: 능력치 상승 (폼 유지)
END
```

### SCENE ep07_07 | 2월 초 | 부실

```
coach_oh: 2월 셋째 주. 서호대랑 연습경기 잡았다. 대학팀이다.
coach_oh: 구경 오는 사람도 있을 거다.
```

---

# EP08 — 구경 오는 사람

활약도 (스카우트 첫 평가): 타석1 hit +2 / walk +1 · 수비 double_play +2 / out +1 / error −1 · 타석2 extra_base +3 / rbi_hit +2 · 타석3 hit +3 / out +1.
6 이상 `checked`(체크 대상, 태성 썬더스 방문) · 3~5 `watching`(관찰 중) · 2 이하 `none`. 임계값은 `balance.json` career.story.constants.

### SCENE ep08_01 | 2월 셋째 주 토요일 아침 | 서호대 야구장

```
NARR: 서호대 야구장. 1루 관중석에 스피드건과 수첩을 든 두 사람.
soyul: (작게) 태성 썬더스 나성만 팀장. 근데 우리 보러 온 건 아닐 거야. 오늘 선발 류건우 보러 왔지.
```

### SCENE ep08_02 | 경기 전 | 서림고 덕아웃

```
coach_oh: {player}. 손 먼저 나가지 마라.

ITEM_SHOW memo_ryu_gunwoo:
  류건우 — 직구 평균 146, 최고 150
  결정구 슬라이더 (우타자 바깥쪽)
  1회엔 직구 위주. 2바퀴째부터 슬라이더 비율 ↑
  세트포지션 때 투구 템포 빨라짐
```

### SCENE ep08_03 | 경기 | 서림고 vs 서호대 (연습경기)

```
SYSTEM: 이번 경기 활약도가 스카우트 관심도 첫 평가에 반영됩니다
NARR: 3회 초, 첫 타석. 류건우는 직구 위주.

GAMEPLAY batting id=ab1
  result -> ep08_ab1_result

IF ep08_ab1_result == hit:
  NARR: 직구를 끝까지 보고 받아친다. 중견수 앞.
ELIF ep08_ab1_result == walk:
  NARR: 높은 공 네 개를 골라 볼넷.
ELSE:
  NARR: 직구에 밀렸다.
END
NARR: 4회 말 1사 1루. 3유간으로 강한 타구!

GAMEPLAY fielding id=def1
  result -> ep08_def_result

IF ep08_def_result == double_play:
  NARR: 6-4-3 병살. 서호대 덕아웃에서 "오" 소리가 난다.
ELIF ep08_def_result == out:
  NARR: 1루 송구. 아웃 하나.
ELSE:
  NARR: 공이 글러브를 맞고 튄다.
END
NARR: 6회 초 2사 2루. 세트포지션이면 템포가 빨라진다.

GAMEPLAY batting id=ab2
  result -> ep08_ab2_result

IF ep08_ab2_result == rbi_hit:
  NARR: 바깥쪽 슬라이더를 밀어 우익선상. 2루 주자 홈인.
ELIF ep08_ab2_result == extra_base:
  NARR: 슬라이더를 걷어 올려 좌중간 펜스 직격. 2루타.
ELSE:
  NARR: 슬라이더에 배트가 따라 나갔다.
END
NARR: 8회 초, 마지막 타석. 류건우가 전력으로 던진다. 초구 150.

GAMEPLAY batting id=ab3
  result -> ep08_ab3_result

IF ep08_ab3_result == hit:
  NARR: 왼발, 골반, 그다음 손. 짧고 단단한 소리. 중견수 앞.
ELIF ep08_ab3_result == out:
  NARR: 유격수 정면. 그래도 소리가 다르다.
ELSE:
  NARR: 150 직구 세 개. 세 번째에 배트가 나가지만 늦는다.
END

SYSTEM: ep08_performance 계산 (3장 공식)
```

### SCENE ep08_04 | 경기 후 | 1루 관중석 아래

```
IF ep08_performance >= 6:
  CUTSCENE ep08_name
  na_scout: 9번 유격수, 1학년이라고? ...이름.
  coach_oh: {player}.
  na_scout: 봄에 주말리그 일정 보내줘.
  SYSTEM: scout_interest = checked
  SYSTEM: scout_visited_teams = ["taesung_thunders"]
ELIF ep08_performance >= 3:
  na_scout: 9번 유격수, 마지막 타석 스윙 괜찮던데. 봄에 한 번 더 보지.
  SYSTEM: scout_interest = watching
  SYSTEM: scout_visited_teams = []
ELSE:
  na_scout: 오늘 고생했어. 류건우는 생각보다 더 좋네.
  SYSTEM: scout_interest = none
  SYSTEM: scout_visited_teams = []
END

SYSTEM: 첫 평가 결과 표시 — checked: "체크 대상" / watching: "관찰 중" / none: "관심 없음"
SYSTEM: scout_tab_unlocked = true  (휴대폰 '스카우트' 탭 추가)
```

### SCENE ep08_07 | 밤 | 주인공 방 (휴대폰)

```
UI phone_scout_tab:
  스카우트 탭

SYSTEM: school_year = 2
SYSTEM: 1학년 종료 — 다음은 2학년
```
