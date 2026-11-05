# 퍼스트픽 — 2학년 시나리오 (EP09 ~ EP13, 카드판)

> **2026-11-05 새로 씀.** 1학년(`scenario_y1_ep01-08.md`)과 같은 카드 형식이다 (`card-plan.md`).
> - 장면 하나 = 카드 한 장. 대사는 1~4줄, 선택지에는 반드시 `| 힌트` 가 붙고, 고르면 반드시 무언가 바뀐다 (능력치·관계·진로).
> - 하린·소율·서지안(방송부)·준수의 2학년 곁가지 이야기는 이 문서가 아니라 폰 문자(`config/content.json` messages, `m_y2_*`)와, 문자에서 예약되는 고백 카드(`y2_side_*`)에 있다.
> - 경기 대본(gamePlan)은 없다 — 2학년 경기는 모두 평소 커리어 경기다. 장면이 언제 뜨는지(어느 주·어느 경기 뒤)는 `scripts/build_scenario.py` 의 META 에 있다.
> - 대사는 이 문서에서만 고친다 → `python3 scripts/build_scenario.py` 가 `content.json` 이벤트로 만든다.
> - 인물·학교·구단·대회 이름은 `docs/worldview.md` 기준. 새 신입생(후배)은 이름을 정하지 않는다 — 게임이 만들 때 뽑는 이름(`{junior}`)을 쓴다.
> - 이 문서에는 컷신을 넣지 않는다. 넣을 만한 장면은 각 장면 아래 **컷신 후보**로만 적어 둔다.

표기 규칙은 1학년 문서 0장과 같다.

---

## 1. 화자 목록 (1학년에 더해)

| 화자ID | 이름 | 소속·설정 |
|---|---|---|
| `junior` | {junior} | 새 1학년 내야수. 내 스윙을 따라 한다 (이름은 게임이 뽑는다) |

2학년에서 소율은 3학년(졸업반), 윤재혁은 3학년 주장, 정민재는 3학년 2루수다.

---

## 2. 변수 (1학년에 더해)

| ID | 타입 | 값 | 생성 | 사용 |
|---|---|---|---|---|
| `scout_revisit` | bool | | EP10 | 고3 (예정) |
| `ep11_result` | enum | `win` / `lose` | EP11 | EP11 |
| `national_first_win_done` | bool | | EP11 | 고3 (팀 목표 달성) |
| `sangbi_called` | bool | | EP12 | 고3 청소년 대표 (예정) |
| `taeo_knows_name` | bool | | EP12 | 고3·프로 (예정) |
| `career_lean` | enum | `draft` / `college` | EP13 | 고3 진로 선택 (예정) |

---

# EP09 — 선배

### SCENE ep09_01 | 3월 첫 주 | 운동장

```
NARR: 3월. 신입생 넷이 들어왔다. 올해 스톱워치는 준수가 든다.
junsu: 야. 우리 이제 선배다.
junior: 선배님 작년 영상 다 봤습니다. 소율 선배님 채널에서요.

CHOICE ep09_junior:
  - "같이 쳐 보자. 손 먼저 나가지 말고." | 후배 친분 ▲ · 컨택 경험치 (가르치며 배운다) -> goto A
  - "내 연습부터 할게. 나중에 봐." | 파워 경험치 (혼자 롱티) -> goto B

A:
  junior: 네! 내일 6시 반에 나오겠습니다.
B:
  NARR: 케이지 밖에서 후배가 내 스윙을 그대로 따라 한다.
```

※ 컷신 후보: `y2_juniors` — 작년 내가 서 있던 자리에 신입생 실루엣이 줄지어 서고, 카메라가 내 뒷모습으로 돌아오는 장면.

---

# EP10 — 다시 온 사람

1학년 EP08 의 `scout_interest` 에 따라 갈린다.

### SCENE ep10_01 | 4월 | 주말리그 경기 전 덕아웃

```
IF scout_interest == checked:
  soyul: 1루 관중석, 나성만 팀장. 오늘은 옆에 다른 구단 점퍼도 하나 있어.
  CHOICE ep10_checked:
    - "(경기 전에 인사드리러 간다)" | 스카우트 관심 ▲ · 평판 ▲ -> goto A
    - "(신경 끄고 몸만 푼다)" | 이번 경기 컨택 ▲ -> goto B
  A:
    na_scout: 인사는 됐고. 초구부터 쳐 봐.
  B:
    NARR: 관중석은 보지 않는다. 배트 끝만 본다.
ELIF scout_interest == watching:
  na_scout: (펜스 너머에서) 봄에 한 번 더 본다고 했지.
  CHOICE ep10_watching:
    - "네. 이번엔 끝까지 보세요." | 스카우트 관심 ▲ · 이번 경기 파워 ▲ -> goto A
    - "(고개만 숙인다)" | 이번 경기 선구안 ▲ -> goto B
  A:
    na_scout: 말은 좋네.
  B:
    NARR: 나 팀장이 수첩을 편다.
ELSE:
  soyul: 1루 관중석에 태성 썬더스. 근데 상대 4번 보러 온 거래.
  CHOICE ep10_none:
    - "그럼 상대 4번보다 잘 치면 되죠." | 이번 경기 파워 ▲ · 컨택 ▼ -> goto A
    - "(평소대로 한다)" | 컨디션 ▲ -> goto B
  A:
    soyul: 오, 그 말 녹음했다.
  B:
    NARR: 6시 반 루틴 그대로. 오늘도 같은 순서로 몸을 푼다.
END

SYSTEM: scout_revisit = true
```

※ 컷신 후보: `y2_scout_gun` — 1루 관중석에서 스피드건이 올라가는 순간을 낮은 각도로 (1학년 `ep08_name` 의 관중석 판을 다시 쓸 수 있다).

---

# EP11 — 전국대회 1승

### SCENE ep11_01 | 6월 말 | 전반기 최종전 이후 | 부실

```
soyul: 백호기 본선, 2년 연속이야.
jaehyuk: 전국대회 1승. 작년에 도윤 선배가 남기고 간 거.

CHOICE ep11_meeting:
  - "제가 앞에서 출루하겠습니다." | 감독 신뢰 ▲ · 컨디션 ▼ (부담) -> goto A
  - "(말 대신 배팅 케이지로 간다)" | 컨택·파워 경험치 -> goto B

A:
  coach_oh: 타순은 내가 정한다. ...마음은 받아 두마.
B:
  NARR: 그날 밤 케이지 불은 열한 시에 꺼졌다.
```

### SCENE ep11_02 | 7월 | 백호기 첫 경기 뒤 | 덕아웃

```
IF ep11_result == win:
  soyul: 이겼어. 전국대회 1승. 창단 9년 만에 처음이야.
  NARR: 3학년 셋이 덕아웃 난간에 매달려 운다.
  SYSTEM: national_first_win_done = true
ELSE:
  soyul: 또 1회전. 근데 작년엔 다섯 점 차였고, 오늘은 9회까지 붙었어.
  jaehyuk: 내년엔 너희가 해라. 우리 몫까지.
END
```

`ep11_result` 는 그 경기의 실제 승패다 (`build_scenario.py` PREPEND).

※ 컷신 후보: `y2_first_win` — 이긴 경우 — 마지막 아웃 뒤 덕아웃 난간을 넘어 뛰어나오는 실루엣들 (1학년 `ep05_seongho` 의 난간 판과 짝).

---

# EP12 — 이름

### SCENE ep12_01 | 10월 말 | 후반기 최종전 이후 | 부실

```
coach_oh: 청소년 대표 상비군 소집. 경기도에서 둘. 하나가 너다.
soyul: 상비군 명단에 청운고 강태오도 있어. 유격수 둘이네.
SYSTEM: sangbi_called = true
```

### SCENE ep12_02 | 11월 | 상비군 훈련장

```
NARR: 상비군 훈련 첫날. 유격수 자리에 둘이 선다.

IF taeo_first_talk == true:
  taeo: 수돗가. 3유간 물어본 거.
ELSE:
  taeo: 서림고. 작년 대수비.
END
taeo: ...이름이 뭐냐.

CHOICE ep12_name:
  - "{player}." | 라이벌 의식 ▲ · 태오가 내 이름을 기억한다 -> taeo_knows_name = true ; goto A
  - "내년 결승에서 알려 줄게." | 라이벌 의식 ▲▲ · 컨디션 ▼ -> taeo_knows_name = false ; goto B

A:
  taeo: {player}. 기억해 둔다.
B:
  taeo: ...그래. 그럼 결승까지 와.
```

※ 1학년 EP03 에서 태오는 이름을 묻지 않았다. 여기가 처음이다.

※ 컷신 후보: `y2_sangbi` — 상비군 훈련장 유격수 자리에 나란히 선 두 실루엣, 태오가 고개만 돌리는 장면 (얼굴 없이 자세로).

---

# EP13 — 겨울, 갈림길 예고

### SCENE ep13_01 | 2학년 겨울방학 첫날 | 부실

```
coach_oh: 내년 9월이 드래프트다. 서호대에서도 전화가 왔다.

IF scout_interest == checked:
  coach_oh: 태성 썬더스는 내년 봄부터 매주 온다고 했다.
END

coach_oh: 지금 정하라는 거 아니다. 겨울 동안 어느 쪽을 보고 몸을 만들지만 말해.

CHOICE ep13_path:
  - "프로요. 졸업하고 바로." | 진로 예고: 드래프트 · 스카우트 관심 ▲ -> career_lean = draft ; goto A
  - "대학도 생각해 볼게요." | 진로 예고: 대학 · 감독 신뢰 ▲ -> career_lean = college ; goto B

A:
  coach_oh: 그럼 겨울엔 몸부터 다시 만든다. 프로는 144경기다.
B:
  coach_oh: 서호대 감독이랑 통화해 두마. 대학 4년 뒤에 다시 드래프트다.

SYSTEM: 2학년 종료 예고 — 고교 마지막 해. 9월 드래프트 전에 진로를 정한다
```

진로는 여기서 정해지지 않는다. `career_lean` 은 고3 진로 카드(예정)의 기본 선택지와 대사를 바꾸는 데만 쓴다.

※ 컷신 후보: `y2_winter_talk` — 겨울 새벽, 부실 창빛 아래에서 감독과 마주 선 두 실루엣 (1학년 `ep07_sound` 의 밤 부실 판).
