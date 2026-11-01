# 퍼스트픽 — 1학년 시나리오 (EP01 ~ EP08)

> **Claude Code 작업 지침**
> - 이 문서는 고1 시나리오 1~8화의 통합 원본이다. 프로젝트의 기존 대사·이벤트 데이터 형식에 맞춰 변환해서 구현할 것.
> - 인물·학교·구단·대회 이름은 `docs/worldview.md` 기준. 이 문서에 없는 이름을 새로 만들지 말 것.
> - 대사 문구는 임의로 수정하지 말 것. 데이터 형식 때문에 바꿔야 하면 변경 내역을 보고할 것.
> - 플래그·변수는 "2. 전역 플래그 / 변수" 표의 ID를 그대로 사용할 것. 회차 간에 이름이 어긋나면 안 된다.
> - 1화 체력 테스트(주력·스쿼트·반응속도)가 이미 구현되어 있으면 그 로직을 유지하고, 결과만 `high / mid / low` 3등급으로 매핑해 이 문서의 분기에 연결할 것.
> - 이미 구현된 시스템과 이 문서의 설명이 다르면 구현을 바꾸기 전에 보고할 것.

---

## 0. 표기 규칙

| 표기 | 의미 |
|---|---|
| `SCENE epXX_YY` | 장면 시작. 시간·장소 포함 |
| `NARR:` | 연출/지문 (화면 묘사, 캐릭터 동작) |
| `화자ID: 대사` | 대사. 괄호 안은 말투·상황 지시 |
| `{player}` | 유저가 입력한 주인공 이름 |
| `CHOICE id` | 선택지. `->` 뒤가 선택 시 효과. `[조건]`이 붙은 항목은 조건 충족 시에만 표시 |
| `IF / ELIF / ELSE / END` | 분기 |
| `GAMEPLAY` | 직접 조작 구간. 파라미터와 결과 변수 정의 |
| `AUTO` | 자동 진행 하이라이트 (관전) |
| `UI` | 휴대폰·화이트보드·노트북·TV 등 화면 UI |
| `ITEM_SHOW` | 아이템(메모 등) 화면 표시 |
| `SYSTEM` | 시스템 메시지, 플래그/변수 변경, 해금 |

**이름 조사 처리**: `{player}이가`, `{player}이`, `{player}아` 등은 이름 받침 유무에 따라 처리할 것 (받침 없음 → `가` / 생략 / `야`).

---

## 1. 화자 목록

| 화자ID | 이름 | 소속·설정 | 첫 등장 |
|---|---|---|---|
| `player` | {player} | 서림고 1학년 유격수 | EP01 |
| `harin` | 하린 | 소꿉친구, 같은 학년 7반, 미대 입시생 | EP01 |
| `soyul` | 소율 | 야구부 매니저 2학년 (EP08 #6부터 3학년). 주인공에게 반말, 주인공은 존댓말 | EP01 |
| `junsu` | 준수 | 박준수, 1학년 2반 포수, 동산중 출신 | EP01 |
| `coach_oh` | 오 감독 | 서림고 감독 | EP01 |
| `senior_a` | 2학년 선배 | 이름 없는 2학년 부원 (공용) | EP01 |
| `taeo` | 강태오 | 청운고 1학년 유격수 | EP03 (영상은 EP02) |
| `cheongun_coach` | 청운고 감독 | | EP03 |
| `cheongun_player` | 청운고 선수 | 이름 없는 청운고 부원 | EP03 |
| `umpire` | 심판 | | EP02 |
| `first_base_coach` | 1루 코치 | 서림고 3학년 (이름 없음) | EP04 |
| `minjae` | 정민재 | 2학년 유격수 → EP06부터 2루수 | EP04 |
| `doyun` | 한도윤 | 3학년 주장, 3번 타자. EP05로 은퇴 | EP05 (이름 언급 EP05) |
| `seongho` | 김성호 | 3학년 외야수. EP05로 은퇴 | EP05 |
| `jaehyuk` | 윤재혁 | 2학년 투수, EP06부터 주장 | EP06 |
| `mom` | 엄마 | 주인공 어머니, 은하식당 운영 | EP06 |
| `na_scout` | 나 팀장 | 나성만, 태성 썬더스 스카우트 팀장 | EP08 |

**대사 없는 등장 인물**

| 인물 | 등장 | 용도 |
|---|---|---|
| 백승현 | EP07 | KKG 스타즈 유격수. 폼 개조 비교 영상 |
| 류건우 | EP08 | 서호대 4학년 투수. 경기 내 상대 투수 데이터 |

---

## 2. 전역 플래그 / 변수

### 2-1. 호감도·스탯

| ID | 타입 | 설명 |
|---|---|---|
| `harin_affinity` | int | 하린 호감도 (EP01부터 누적) |
| `soyul_affinity` | int | 소율 호감도 (EP01부터 누적) |
| `minjae_trust` | int | 정민재 신뢰도 (EP06부터 누적) |
| `stats.at_bat_video_count` | int | 소율이 찍은 주인공 타석 영상 수. 주인공 실전 타석마다 +1 (EP04부터) |
| `stats.fielding_video_count` | int | 주인공 실전 수비 영상 수. 실전 수비 조작 구간마다 +1 (EP03부터) |

### 2-2. 회차 간 플래그

| ID | 타입 | 값 | 생성 | 사용 |
|---|---|---|---|---|
| `test_speed_grade` | enum | `high` / `mid` / `low` | EP01 | 향후 주루 이벤트 |
| `test_squat_grade` | enum | `high` / `mid` / `low` | EP01 | EP07 |
| `test_reaction_grade` | enum | `high` / `mid` / `low` | EP01 | EP02, EP03 |
| `test_overall_grade` | enum | `high` / `mid` / `low` | EP01 | EP01 |
| `warmup_effect` | enum | `calm` / `eager` | EP02 | EP04 |
| `taeo_interest` | bool | | EP02 | EP03 |
| `taeo_first_talk` | bool | | EP03 | 고2 (예정) |
| `taeo_ball_fielded` | bool | | EP03 | EP04 |
| `first_ab_result` | enum | `hit` / `out` / `strikeout` | EP04 | EP05 |
| `seongho_tee` | bool | | EP05 | EP05 |
| `doyun_talk` | bool | | EP05 | 향후 |
| `team_goal` | string | `"national_first_win"` | EP05 | 고2~고3 |
| `ep06_game_result` | enum | `win` / `lose` | EP06 | EP06 |
| `dongin_first_win` | bool | | EP06 | 향후 |
| `form_answer` | enum | `immediate` / `question` | EP07 | 향후 |
| `form_adaptation` | int | 0~100 | EP07 | EP07 (임시 수치) |
| `training_direction` | enum | `contact` / `power` / `defense_run` | EP07 | EP08, 이후 |
| `soyul_restaurant_invite` | bool | | EP07 | EP08 |
| `scout_interest` | enum | `checked` / `watching` / `none` | EP08 | 고2~ |
| `scout_visited_teams` | list | | EP08 | 고2~ |
| `school_year` | int | | EP08 종료 시 2 | |

※ 회차 내부에서만 쓰는 결과 변수(`ep04_ab_result` 등)는 각 회차에 정의.

### 2-3. 휴대폰 기능 해금

| ID | 기능 | 해금 |
|---|---|---|
| `phone_contacts` | 연락처 (하린, 소율, 준수) | EP01 |
| `phone_records_team` | 기록실 — 팀 경기 기록 | EP02 |
| `phone_profile_taeo` | 인물 정보 '강태오' (기본 프로필) | EP03 |
| `phone_records_personal` | 기록실 — 개인 타격 기록 | EP04 |
| `phone_news` | 뉴스 탭 | EP05 |
| `phone_records_season` | 기록실 — 시즌 기록 탭 | EP06 |
| `scout_tab_unlocked` | 스카우트 탭 | EP08 |

---

# EP01 — 입부

| 항목 | 값 |
|---|---|
| 시기 | 고1 3월 초 |
| 역할 | 도입. 주요 인물 소개, 체력 테스트로 초기 능력치 결정 |
| 직접 조작 | 체력 테스트 3종 (주력·스쿼트·반응속도) |

---

### SCENE ep01_01 | 아침 | 학교 가는 길

```
NARR: 3월 초, 아파트 단지 앞. 주인공이 나오자 하린이 이어폰을 빼며 기다리고 있다.

harin: 늦었어. 3분.
player: 엘리베이터가 13층에서 안 내려왔어.
harin: 맨날 엘리베이터 탓이야.

NARR: 나란히 걷기 시작한다.

harin: 반 배정 봤어? 너 3반이던데.
player: 너는?
harin: 7반. 끝과 끝이네.
player: 점심때 매점은 내가 더 가깝겠다.
harin: 그게 제일 먼저 드는 생각이야?

NARR: 하린이 가방끈을 고쳐 멘다. 화구통이 같이 흔들린다.

player: 그거 오늘도 들고 가?
harin: 학원 바로 가야 돼. 이번 주부터 저녁반이야.
player: 몇 시에 끝나는데.
harin: 열 시. ...너는? 진짜 야구부 들어가?

CHOICE ep01_harin_club:
  - "응. 오늘 입부서 낼 거야." -> harin_affinity += 1 ; goto A
  - "일단 가보고." -> goto B
  - "중학교 때도 했는데 그냥 하는 거지." -> goto B

A:
  harin: 그래. 그럼 이제 같이 집 갈 일은 없겠네.
B:
  harin: 너 그 말 중학교 때도 했어. 결국 3년 했잖아.

harin: 근데 여기 야구부 있었어? 한 번도 못 본 것 같은데.
player: 있어. ...있을 거야.
```

---

### SCENE ep01_02 | 방과 후 | 야구부실 앞

```
NARR: 운동장 끝, 컨테이너를 개조한 부실. 문 옆에 손글씨로 '서림고 야구부'. 1학년 한 명이 먼저 서 있다. 덩치가 크다.

junsu: 어, 너도 입부?
player: 응.
junsu: 다행이다. 나 혼자인 줄 알았어. 박준수, 2반. 포수야.
player: {player}. 3반.
junsu: 포지션은?
player: 유격수.
junsu: 오, 내야. 근데 너 어디 중학교? 나 중학교 대회 웬만한 데 다 나갔는데 처음 보는 것 같아서.
player: ...우리 학교가 대회를 많이 못 나갔어.
junsu: 아. 그렇구나.

NARR: 부실 문이 벌컥 열린다.
```

---

### SCENE ep01_03 | 야구부실 | 입부서

```
soyul: 왔다! 1학년 맞지? 들어와, 들어와.

NARR: 좁은 부실. 책상 위에 노트북 두 대, 외장하드, 기록지 파일이 쌓여 있다. 벽에는 프로 구단 시간표가 붙어 있다.

soyul: 매니저 소율. 2학년. 여기 이름이랑 반, 포지션 쓰고.
junsu: 매니저님 계셨구나.
soyul: 선배라고 해. 매니저님은 좀 이상하잖아.
junsu: 네, 소율 선배.

NARR: 소율이 준수 입부서를 보더니 바로 노트북을 두드린다.

soyul: 박준수... 아, 너 동산중 포수! 작년 소년체전 나왔지? 도루 저지율 꽤 좋던데.
junsu: 그걸 어떻게 아세요?
soyul: 영상 다 있어. 중학교 대회 중계는 웬만하면 다 받아놨거든.
junsu: ...다요?

NARR: 소율이 주인공 입부서를 받는다. 타이핑. 멈춤. 다시 타이핑.

soyul: {player}... 유격수... 어, 잠깐만.
player: 없죠.
soyul: 아니, 있긴 있어. 중3 지역 리그 11경기. 타율 2할 6푼. 근데 영상이 하나도 없어.
player: 저희 학교 경기는 중계를 안 했어요.
soyul: 이런 경우 처음 봐. 나 이런 거 못 참는데.

CHOICE ep01_soyul_record:
  - "그럼 이제부터 찍으시면 되죠." -> soyul_affinity += 2 ; goto A
  - "별로 볼 것도 없었어요." -> soyul_affinity += 1 ; goto B
  - "...그 기록, 어디서 찾으신 거예요?" -> soyul_affinity += 1 ; goto C

A:
  soyul: 오, 그렇지. 내가 그 말 하려고 했는데. 오늘부터 너 1호 촬영 대상이야.
B:
  soyul: 그건 내가 판단할게. 이제부터 다 찍을 거야.
C:
  soyul: 지역 리그 기록실 게시판. 3년 치 엑셀로 정리해놨어. 이제부터 네 건 내가 직접 찍을 거고.

soyul: 자, 둘 다 나와. 감독님 기다리셔.
```

---

### SCENE ep01_04 | 운동장

```
NARR: 흙이 고르지 않은 운동장. 외야 펜스 대신 그물. 2·3학년 열두 명이 스트레칭 중이다. 감독은 벤치 옆에 팔짱을 끼고 서 있다.

soyul: 감독님, 1학년 두 명이요!
coach_oh: 둘?
soyul: 네, 둘이요.
coach_oh: ...그래. 그럼 열넷이네.

NARR: 감독이 1학년 둘을 한 번씩 훑어본다.

coach_oh: 오 감독이다. 길게 말 안 한다. 오늘은 체력 테스트만 한다. 너희가 지금 어느 정도인지 봐야 연습을 짤 수 있으니까.
junsu: 네!
player: 네.
coach_oh: 소율아, 기록.
soyul: 준비됐어요. 스톱워치 두 개, 반응속도 측정기까지.
coach_oh: 측정기는 어디서 났냐.
soyul: 제 거요.

NARR: 2학년 선배 몇 명이 웃는다.
```

---

### SCENE ep01_05 | 체력 테스트

```
SYSTEM: 체력 테스트 — 총 3종목. 결과에 따라 주인공의 초기 능력치가 정해집니다.
```

#### ① 주력

```
GAMEPLAY fitness_test id=speed
  ※ 기존 구현된 주력 테스트 사용
  result -> test_speed_grade  (high | mid | low)

soyul: 준비— 출발!

IF test_speed_grade == high:
  soyul: 오, 빠르다! 2학년 평균보다 빨라.
ELIF test_speed_grade == mid:
  soyul: 괜찮은데? 내야수로는 충분해.
ELSE:
  junsu: 나보다는 빠르네.
  soyul: 준수야, 넌 꼴찌였어.
END
```

#### ② 스쿼트

```
coach_oh: 다음. 스쿼트. 하체가 버텨야 공도 치고 공도 잡는다.

GAMEPLAY fitness_test id=squat
  ※ 기존 구현된 스쿼트 테스트 사용
  result -> test_squat_grade  (high | mid | low)

NARR: 준수가 먼저 주저앉는다. 주인공 혼자 남아 계속한다.

IF test_squat_grade == high:
  soyul: 감독님, 이거 3학년 기록보다 많은데요.
  coach_oh: 알아.
ELIF test_squat_grade == mid:
  junsu: (누워서) 너... 다리 뭐냐...
ELSE:
  soyul: 첫날이니까. 괜찮아.
END
```

※ `test_squat_grade == high`의 "3학년 기록보다 많은데요" 대사는 EP07 감독 대사와 연결되므로 수정 금지.

#### ③ 반응속도

```
soyul: 이거 제일 보고 싶었던 거. 반응속도 측정기.
player: 이것도 선배 거예요?
soyul: 응. 용돈 두 달 치.

GAMEPLAY fitness_test id=reaction
  ※ 기존 구현된 반응속도 테스트 사용
  result -> test_reaction_grade  (high | mid | low)

IF test_reaction_grade == high:
  NARR: 감독이 처음으로 팔짱을 푼다.
  coach_oh: ...다시 한 번 해봐.
ELIF test_reaction_grade == mid:
  coach_oh: 됐다.
ELSE:
  coach_oh: 반응은 연습하면 는다.
END
```

```
SYSTEM: 초기 능력치가 결정되었습니다.
UI stat_screen: 주력 / 스쿼트(하체) / 반응속도 기반 초기 능력치 표시
SYSTEM: test_overall_grade = 3종목 등급의 평균 (high=3, mid=2, low=1 → 평균 2.5 이상 high, 1.5 이상 mid, 미만 low)
```

---

### SCENE ep01_06 | 테스트 후 | 벤치

```
NARR: 해가 지는 운동장. 1학년 둘이 벤치에 앉아 물을 마신다. 감독이 소율의 기록지를 넘겨본다.

IF test_overall_grade == high:
  coach_oh: {player}.
  player: 네.
  coach_oh: 중학교 때 왜 아무도 몰랐냐.
  player: ...대회를 많이 못 나가서요.
  coach_oh: 그래. 내일 6시 반까지 나와.
ELIF test_overall_grade == mid:
  coach_oh: 둘 다 내일 6시 반까지 나와. 늦으면 뛴다.
ELSE:
  coach_oh: 이제 시작이다. 내일 6시 반.
END

NARR: 감독이 자리를 뜬다. 소율이 노트북을 끌어안고 다가온다.

soyul: 오늘 기록 다 정리했어. 너희 거 파일명 만들어놨다.
junsu: 파일명이요?
soyul: 박준수_1학년_3월. {player}_1학년_3월. 졸업할 때까지 쌓을 거야.
player: ...3년 치요?
soyul: 당연하지. 나중에 프로 가면 내가 원본 갖고 있는 거잖아. 그거 좀 멋있지 않아?

NARR: 준수가 웃는다. 주인공은 대답하지 않고 운동장을 본다.
```

---

### SCENE ep01_07 | 밤 | 주인공 방 (휴대폰)

```
NARR: 주인공 방. 침대에 누운 채 휴대폰을 본다. 알림.

UI phone_message thread=harin:
  harin: 학원 끝남
  harin: 야구부 들어갔어?

CHOICE ep01_harin_text:
  - "응. 체력 테스트 했는데 다리 안 움직여" -> harin_affinity += 1 ; goto A
  - "들어감" -> goto B
  - "감독님이 내일 6시 반까지 나오래" -> goto C

A:
  harin: ㅋㅋ 내일 엘리베이터 핑계 대지 마
B:
  harin: 그래 잘했네
C:
  harin: 그럼 내일부터 아침에 못 보겠네

NARR: 휴대폰을 내려놓는다. 알람을 6시로 맞춘다.

SYSTEM: 1화 완료
SYSTEM: 획득 — 초기 능력치 / 소율 호감도 / 하린 호감도
SYSTEM: 해금 — phone_contacts (하린, 소율, 준수)
```

---

# EP02 — 벤치

| 항목 | 값 |
|---|---|
| 시기 | 고1 3월 ~ 4월 |
| 역할 | 무명 1학년의 현실. 강태오 첫 노출(영상) |
| 직접 조작 | 펑고 수비 연습 (10구) |

---

### SCENE ep02_01 | 새벽 | 운동장

```
NARR: 6시 28분. 운동장에 안개가 깔려 있다. 주인공이 도착하자 감독은 이미 홈플레이트 옆에서 펑고 배트를 들고 있다.

coach_oh: 2분 남았네.
player: 안녕하세요.
coach_oh: 유격수 자리 가 있어.

NARR: 준수가 뛰어 들어온다. 6시 31분.

coach_oh: 박준수. 운동장 다섯 바퀴.
junsu: 1분인데요...
coach_oh: 그래서 다섯 바퀴다. 늦었으면 열 바퀴고.

GAMEPLAY fielding_drill id=fungo
  position: shortstop
  balls: 10
  input: 타구 방향 표시 → 이동 → 포구 타이밍 → 송구 방향
  modifiers: test_reaction_grade
  result -> 수비 경험치

IF test_reaction_grade == high:
  NARR: 펑고가 끝나도 감독이 공 바구니를 하나 더 가져온다.
  coach_oh: 한 바구니 더.
  player: 네.
  soyul: (벤치에서, 혼잣말) 저거 테스트 기록 보시고 그러는 거다...
ELSE:
  coach_oh: 첫 발이 늦다. 공 보고 움직이지 말고, 배트에 맞는 소리 듣고 움직여.
  player: 소리요?
  coach_oh: 내일 또 친다.
END
```

---

### SCENE ep02_02 | 아침 | 부실

```
NARR: 훈련 후. 부원들이 옷을 갈아입는 사이, 소율이 화이트보드에 일정을 적는다.

soyul: 자, 공지! 이번 주 토요일 주말리그 첫 경기. 상대는 동인고.
senior_a: 동인고면 작년에 콜드게임으로 진 데 아니야?
soyul: 7회 콜드. 14 대 2.
senior_a: 그걸 왜 정확하게 기억해.
soyul: 내가 기록했으니까.

NARR: 소율이 화이트보드 구석에 라인업을 붙인다. 주인공과 준수의 이름은 맨 아래 '벤치' 칸에 있다.

junsu: 역시.
player: 1학년이니까.
junsu: 아니 근데 우리 열네 명이잖아. 벤치가 다섯 명밖에 없는데.
player: 그 다섯 명 안에 들었네.
junsu: ...그렇게 생각하면 좀 낫다.
```

---

### SCENE ep02_03 | 토요일 | 주말리그 — 서림고 vs 동인고

```
SYSTEM: 이번 경기는 관전 모드입니다. 경기는 자동으로 진행됩니다.

NARR: 공설운동장 야구장. 관중석은 거의 비어 있다. 학부모 몇 명과 동인고 응원단 열댓 명.

AUTO:
  - 1회 초, 서림고 삼자범퇴
  - 2회 말, 동인고 3점 홈런
  - 4회 말, 서림고 실책 두 개로 추가 3실점

junsu: 6 대 0...
player: 아직 5회야.
junsu: 너 지금 위로하는 거야?

NARR: 5회 초. 서림고 3번 타자(3학년 주장)가 2루타. 벤치가 처음으로 일어선다. 후속 타자 땅볼로 1점.

soyul: (기록지에 적으며) 1점. 시즌 첫 득점.
player: 그것도 기록해요?
soyul: 다 기록해. 나중에 보면 이런 게 제일 재밌어.

NARR: 8회 초. 9 대 1. 감독이 벤치 쪽을 돌아본다.

coach_oh: {player}. 몸 풀어.

NARR: 주인공이 장갑을 끼고 배트를 든다. 덕아웃 앞에서 스윙. 손에 땀이 찬다.

CHOICE ep02_warmup:
  - "(가볍게 스윙하며 상대 투수를 지켜본다)" -> warmup_effect = calm
  - "(최대한 세게 스윙해서 몸을 데운다)" -> warmup_effect = eager

NARR: 앞 타자가 병살타. 8회 초 종료.
NARR: 8회 말, 동인고가 1점 추가. 10 대 1. 콜드게임.

umpire: 게임 셋!

NARR: 주인공은 배트를 든 채로 덕아웃 앞에 서 있다.

junsu: ...몸만 풀었네.
player: 응.
coach_oh: (지나가면서) 다음에.

SYSTEM: 경기 결과 — 서림고 1 – 10 동인고 (8회 콜드)
```

---

### SCENE ep02_04 | 귀가 버스

```
NARR: 학교 소형 버스. 부원들 대부분 잠들어 있다. 주인공 뒷자리의 소율이 몸을 내밀어 휴대폰을 들이민다.

soyul: 야, 이거 봤어?
player: 뭔데요.
soyul: 오늘 같은 시간에 청운고도 주말리그 했거든. 하이라이트 방금 올라왔어.

NARR: 휴대폰 화면. 청운고 유격수가 3유간 깊은 타구를 역동작으로 잡아 1루에 원바운드 없이 꽂는다. 이어서 다음 타석, 밀어서 우중간 담장을 넘기는 홈런.

player: ......
soyul: 강태오. 1학년.
player: 1학년이요?
soyul: 너랑 동갑. 중3 때 전국대회 타율 4할 5푼, 대회 MVP. 청운고는 들어가자마자 주전 유격수 줬고.

CHOICE ep02_taeo:
  - "...방금 그 수비, 다시 볼 수 있어요?" -> soyul_affinity += 1 ; taeo_interest = true ; goto A
  - "같은 1학년인데 저렇게 다르네요." -> taeo_interest = true ; goto B
  - "청운고는 원래 강팀이잖아요." -> goto C

A:
  soyul: 오, 너 그거 봤구나. 다들 홈런만 보는데.
  NARR: 소율이 영상을 뒤로 감는다. 수비 장면이 다시 나온다.
  soyul: 봐봐. 타구 맞기도 전에 한 발 먼저 가 있어. 이게 진짜 무서운 거야.
B:
  soyul: 다르지. 근데 쟤도 중1 땐 키 150 정도였대. 인터뷰에서 봤어.
  player: 그런 것까지 아세요?
  soyul: 그런 게 제일 재밌다니까.
C:
  soyul: 강팀이라도 1학년한테 바로 주전 주는 건 흔하지 않아. 그만큼 잘한다는 거지.

NARR: 주인공이 휴대폰 화면을 한참 본다. 화면 속 태오가 홈런을 치고 아무 표정 없이 베이스를 돈다.

soyul: 근데 너 운 좋은 거 알아?
player: 뭐가요?
soyul: 다음 주 연습경기 상대가 청운고야.
player: ......네?
soyul: 감독님이 감독님 친구분한테 부탁해서 겨우 잡았대. 저기서 우리랑 해줄 이유가 없거든.
```

---

### SCENE ep02_05 | 밤 | 아파트 놀이터

```
NARR: 밤 10시 15분. 놀이터 가로등 아래에서 주인공이 혼자 배트를 휘두른다. 휴대폰은 벤치 위에 세워져 있고, 화면에는 태오 영상이 일시정지되어 있다.
NARR: 스윙. 영상 재생. 일시정지. 다시 스윙.

harin: 뭐 해.

NARR: 화구통을 멘 하린이 놀이터 입구에 서 있다.

player: 학원 끝났어?
harin: 응. 근데 너 이 시간에 여기서 뭐 해. 경기 진 거 아니었어?
player: 어떻게 알아?
harin: 준수라는 애가 우리 반 애랑 같은 학원이래. 걔가 단톡에 올렸대. 10 대 1.
player: ...개인정보가 없네.

NARR: 하린이 벤치에 놓인 휴대폰을 본다.

harin: 이건 누구야?
player: 청운고 유격수. 나랑 동갑.
harin: 잘해?
player: 엄청.
harin: 그래서 따라 하는 거야?
player: 그냥... 어떻게 치는 건지 궁금해서.

CHOICE ep02_harin_playground:
  - "다음 주에 저 사람이랑 연습경기 해." -> harin_affinity += 1 ; goto A
  - "넌 그림 그릴 때 잘 그리는 사람 거 따라 그려?" -> harin_affinity += 1 ; goto B
  - "들어가. 늦었어." -> goto C

A:
  harin: 그럼 너도 나가?
  player: ...모르지.
  harin: 나가면 말해줘. 결과는 준수한테 듣기 싫으니까.
B:
  harin: 응. 처음엔 거의 베껴. 손 모양까지.
  player: 그래도 돼?
  harin: 계속 베끼면 안 되고. 근데 처음엔 다 그래.
C:
  harin: 너도. 내일 6시 반이라며.

NARR: 하린이 아파트 쪽으로 걸어간다. 주인공은 휴대폰을 집어 들고, 영상을 한 번 더 재생한다.

UI phone_message thread=soyul:
  soyul: [파일] 강태오_중3_전국대회_전타석.mp4
  soyul: 다음 주 전까지 봐둬
  soyul: 1시간 40분짜리야 ㅎ

SYSTEM: 2화 완료
SYSTEM: 획득 — 펑고 수비 경험치 / 소율 호감도 / 하린 호감도
SYSTEM: 플래그 — taeo_interest, warmup_effect
SYSTEM: 해금 — phone_records_team (기록실: 팀 경기 기록)
```

---

# EP03 — 청운고

| 항목 | 값 |
|---|---|
| 시기 | 고1 4월 |
| 역할 | 강태오와 첫 대면. 태오는 주인공을 신경 쓰지 않음 |
| 직접 조작 | 관찰 미니게임 (조건부), 대수비 1이닝 |

---

### SCENE ep03_01 | 오전 | 청운고 야구장

```
NARR: 버스에서 내린 서림고 부원들이 일제히 멈춰 선다. 인조잔디 내야, 전광판, 실내 연습장, 3층짜리 야구부 숙소.

junsu: ...여기 고등학교 맞지?
senior_a: 숙소 봐. 우리 부실이 저기 화장실만 하겠다.
soyul: 화장실보다 작아. 아까 들어가봤어.
junsu: 벌써요?

NARR: 오 감독이 3루 쪽에서 청운고 감독과 악수를 한다. 짧게 웃는다. 서림고 부원들이 감독이 웃는 걸 처음 본다.

junsu: (작게) 감독님 웃으셨어.
player: 봤어.

coach_oh: (돌아와서) 7이닝까지만 한다. 3루 덕아웃 써. 이 학교 물건 함부로 만지지 말고.
```

---

### SCENE ep03_02 | 경기 전 | 청운고 수비 훈련

```
NARR: 청운고 선수들이 펑고를 받는다. 동작이 하나같이 군더더기 없다. 유격수 자리에 등번호 6번.

soyul: 저기, 6번. 강태오.

NARR: 소율은 이미 삼각대를 펴고 있다. 카메라가 두 대다.

player: 카메라를 두 대나 가져오셨어요?
soyul: 하나는 전체, 하나는 강태오만. 이런 기회 흔하지 않거든.
junsu: 선배 그거 상대 학교 허락은 받았어요?
soyul: ...감독님이 받아주셨겠지?

IF taeo_interest == true:

  GAMEPLAY observation id=taeo_first_step
    target: taeo
    rounds: 5
    input: 태오가 타구 쪽으로 첫 발을 떼는 순간 탭
    result -> 정확도에 따라 수비 위치 선정 경험치

  NARR: 펑고 배트에 공이 맞기 직전, 태오의 오른발이 이미 움직인다.

  player: ...방금.
  soyul: 봤어?
  player: 치기 전에 움직였어요.
  soyul: 그치! 영상으로만 봐서 확신이 없었는데. 너도 보이지?
  player: 감독님 배트 각도를 보는 건가.
  soyul: 그런가? 와, 그건 생각 못 했다. 그거 나중에 프레임 단위로 돌려볼래.

ELSE:

  NARR: 태오가 깊은 타구를 걷어 1루에 송구한다. 공이 빨랫줄처럼 날아간다.

  junsu: 저 송구 뭐야.
  player: ...영상보다 빨라.
  soyul: 그치. 직접 보면 다르다니까.

END
```

---

### SCENE ep03_03 | 경기 | 서림고 vs 청운고 (연습경기, 7이닝)

```
SYSTEM: 관전 모드. 주요 장면만 재생됩니다.

AUTO:
  - 1회 말, 태오 첫 타석. 초구를 밀어 우중간 2루타. 선제 2타점.
  - 3회 초, 서림고 2사 1·2루. 3유간 깊은 타구 — 태오가 역동작으로 잡아 2루 포스아웃.
  - 4회 말, 태오 좌월 솔로 홈런. 6 대 0.

NARR: 홈런을 친 태오가 베이스를 돈다. 표정 변화가 없다. 홈을 밟고 덕아웃에 들어가며 장갑을 벗는 것도 무덤덤하다.

junsu: 저거 봐. 홈런 쳤는데 하나도 안 좋아해.
senior_a: 연습경기니까 그렇겠지.
soyul: 아니야. 공식 경기에서도 저래. 중학교 때부터 그랬어.

NARR: 5회 초. 서림고 3학년 주장이 적시타. 6 대 1. 서림고 덕아웃이 들썩인다.

soyul: (기록하며) 강팀 상대 득점! 이건 표시해둬야지.

NARR: 6회 말. 청운고가 1·2학년 위주로 교체. 그래도 2점 추가. 8 대 1.
```

---

### SCENE ep03_04 | 7회 말 | 대수비

```
coach_oh: {player}. 유격수 들어가.
player: ...네!
junsu: (작게) 들어간다, 들어간다.

NARR: 주인공이 유격수 자리로 뛰어간다. 인조잔디라 바운드가 다르다. 장갑을 몇 번 쥐었다 편다.
NARR: 2사 주자 없음. 청운고 타석에 다시 6번이 들어선다. 교체되지 않았다.

cheongun_coach: (덕아웃에서) 태오야, 마지막 타석 한 번만 더 쳐.

NARR: 주인공 시점. 타석의 태오가 보인다. 거리가 생각보다 가깝다.
NARR: 2구째. 태오의 타구가 유격수 정면으로 빠르게 튄다.

GAMEPLAY fielding id=ep03_def
  position: shortstop
  situation: two_outs_bases_empty
  ball: hard_grounder_ss_direct (일반 타구보다 속도 빠름)
  surface: artificial_turf (바운드 낮음)
  modifiers: test_reaction_grade (높을수록 포구 판정 구간 확대)
  result -> taeo_ball_fielded  (true: 포구+송구 성공 / false: 실책)
  SYSTEM: stats.fielding_video_count += 1

IF taeo_ball_fielded == true:
  NARR: 주인공이 공을 잡아 1루로 던진다. 아웃. 경기 종료.
  NARR: 태오는 1루를 밟고 그대로 덕아웃 쪽으로 걸어간다. 유격수 쪽은 돌아보지 않는다.
  junsu: (덕아웃에서 일어나며) 잡았다!
  soyul: 강태오 타구 잡았어. 영상 남았어, 영상.
ELSE:
  NARR: 공이 글러브를 맞고 옆으로 튄다. 주자 1루.
  NARR: 태오는 1루에서 헬멧을 고쳐 쓴다. 유격수 쪽은 보지 않는다.
  NARR: 다음 타자 플라이아웃. 경기 종료.
  junsu: 괜찮아, 저거 엄청 빨랐어.
  soyul: 인조잔디 처음이잖아. 바운드가 달라서 그래.
END

SYSTEM: 경기 결과 — 서림고 1 – 8 청운고 (연습경기)
```

---

### SCENE ep03_05 | 경기 후 | 야구장 뒤 수돗가

```
NARR: 정리 시간. 주인공이 장비를 들고 수돗가에 간다. 먼저 와 있는 사람. 등번호 6번. 태오가 고개를 숙이고 얼굴에 물을 끼얹고 있다.
NARR: 주인공이 걸음을 멈춘다. 태오가 고개를 들고, 수건으로 얼굴을 닦는다. 주인공을 한 번 보지만 별 반응이 없다.

CHOICE ep03_taeo_sink:
  - [조건: taeo_interest == true] "아까 3회 그 3유간 수비요." -> taeo_first_talk = true ; goto A
  - "...수고하셨습니다." -> taeo_first_talk = true ; goto B
  - "(그냥 고개만 숙이고 물을 튼다)" -> goto C

A:
  player: 아까 3회 그 3유간 수비요. 치기 전에 먼저 움직이던데.
  taeo: 아.
  NARR: 태오가 수건을 어깨에 건다. 잠깐 생각하는 표정.
  taeo: 그냥 갈 데가 보여서.
  player: ...보인다고요?
  taeo: 응.
  NARR: 그게 설명의 전부다.
B:
  player: ...수고하셨습니다.
  taeo: 어. 수고.
  NARR: 태오는 같은 1학년인 줄도 모르는 눈치다.
C:
  NARR: 주인공이 고개를 숙이고 수도꼭지를 튼다. 태오도 짧게 고개를 까딱한다.

cheongun_player: 태오야! 웨이트 간다!
taeo: 어, 간다.

NARR: 태오가 그대로 걸어간다. 몇 걸음 가다가 멈춰서 돌아본다.

IF taeo_ball_fielded == true:
  taeo: 아, 마지막에 유격수. 너였구나.
  player: 네.
  taeo: 서림고도 1학년 쓰네.
  NARR: 그걸로 끝. 태오는 다시 걸어간다. 이름은 묻지 않는다.
ELSE:
  NARR: 태오는 주인공 뒤쪽, 수돗가에 두고 간 수건을 집으러 왔을 뿐이다. 수건을 챙겨 그대로 간다.
END

NARR: 주인공이 수도꼭지를 잠근다. 손에 아직 공 감각이 남아 있다.

junsu: (멀리서) {player}! 버스 간대!
```

※ 태오는 이 화에서 주인공 이름을 묻지 않는다. 고2에서 처음 이름을 묻는 장면과 대비되므로 유지할 것.

---

### SCENE ep03_06 | 귀가 버스

```
NARR: 소율이 노트북에 카메라를 연결하고 있다. 눈이 반짝인다.

soyul: 오늘 거 진짜 대박이야. 강태오 수비 전부, 타석 전부 다 찍혔어. 이거 정리하면 중계보다 나아.
junsu: 선배, 그거 결국 허락은 받으셨어요?
soyul: ...감독님, 저 촬영 허락 받아주셨죠?
coach_oh: (앞자리에서, 눈 감은 채) 안 받았다.
soyul: ......
coach_oh: 근데 저쪽 감독도 알면서 냅뒀다.
soyul: 휴.

NARR: 준수가 주인공 옆에서 휴대폰을 본다.

IF taeo_first_talk == true:
  junsu: 너 강태오랑 얘기했지? 수돗가에서. 내가 봤어.
  player: 얘기라고 하기엔 좀.
  junsu: 뭐래?
  IF (ep03_taeo_sink == A):
    player: ...갈 데가 보인대.
    junsu: 그게 뭔 소리야.
    player: 나도 몰라.
  ELSE:
    player: 수고하래.
    junsu: 그게 다야?
    player: 응.
  END
END

NARR: 주인공이 창밖을 본다.
```

---

### SCENE ep03_07 | 밤 | 휴대폰

```
UI phone_message thread=harin:
  harin: 오늘 연습경기 그거 했어?
  harin: 나갔어?

CHOICE ep03_harin_text:
  - "마지막에 수비 한 이닝. 공 하나 왔어" -> harin_affinity += 1 ; goto A
  - "잠깐 나감" -> goto B
  - [조건: taeo_first_talk == true] "그 유격수랑 얘기했어" -> harin_affinity += 1 ; goto C

A:
  harin: 잡았어?
  IF taeo_ball_fielded == true:
    player: 잡았어
    harin: 오 잘했네
  ELSE:
    player: 놓쳤어
    harin: 그래도 공 왔잖아. 지난주엔 안 왔다며
  END
B:
  harin: 그럼 다음엔 좀 더 오래 나가겠네
C:
  harin: 놀이터에서 따라 하던 사람?
  player: ㅇㅇ
  harin: 뭐래
  IF (ep03_taeo_sink == A):
    player: 그냥 보인대
    harin: 뭐가?
    player: 나도 그걸 모르겠어
  ELSE:
    player: 수고하래
    harin: ㅋㅋ 그게 다야?
  END

SYSTEM: 3화 완료
SYSTEM: 획득 — 수비 경험치 / (관찰 성공 시) 수비 위치 선정 경험치 / 소율 호감도 / 하린 호감도
SYSTEM: 플래그 — taeo_first_talk, taeo_ball_fielded
SYSTEM: 해금 — phone_profile_taeo (인물 정보 '강태오', 기본 프로필만)
```

※ 원본 대비 수정: #6·#7에서 수돗가 선택지(A/B)에 따라 대사가 갈리도록 보완함. C를 고른 경우 태오와 대화가 없으므로 해당 대사 비노출.

---

# EP04 — 첫 타석

| 항목 | 값 |
|---|---|
| 시기 | 고1 4월 ~ 5월 |
| 역할 | 고교 첫 실전 타석. 정민재 첫 등장 |
| 직접 조작 | 대타 타석 1회, 유격수 수비 1이닝 |

---

### SCENE ep04_01 | 월요일 방과 후 | 부실

```
NARR: 소율이 노트북을 주인공 쪽으로 돌린다. 화면에는 3화 연습경기 영상. 7회 말, 유격수 자리의 주인공.

soyul: 자, 복습 시간.
player: 이것도 보는 거예요?
soyul: 당연하지. 너 수비 영상 처음 생긴 거잖아.

IF taeo_ball_fielded == true:
  NARR: 소율이 영상을 0.25배속으로 돌린다. 주인공이 공을 잡아 1루로 던지는 장면.
  soyul: 봐봐. 잡기 전에 왼발이 먼저 나갔어. 이거 강태오 따라 한 거지?
  player: ...티 나요?
  soyul: 나한텐. 근데 잘 따라 했어. 송구 끝까지 자세 안 무너졌고.
ELSE:
  NARR: 소율이 영상을 0.25배속으로 돌린다. 공이 글러브 끝을 맞고 튀는 장면.
  soyul: 바운드 보이지? 인조잔디라 생각보다 낮게 깔렸어. 넌 흙 바운드 기준으로 글러브 댄 거고.
  player: 알면서도 손이 먼저 갔어요.
  soyul: 그럼 됐어. 이유를 알면 고칠 수 있거든.
END

soyul: 근데 문제가 하나 있어.
player: 뭔데요.
soyul: 네 폴더 열어봐.

UI laptop_folder:
  {player}_1학년
    ├ 수비 (1)
    └ 타석 (0)

soyul: 타석 영상이 0개야. 나 이런 거 진짜 못 참는다니까.
player: 그건 제가 정하는 게 아니라서요.
soyul: 알아. 그냥 말해본 거야.
```

---

### SCENE ep04_02 | 화요일 점심 | 매점

```
NARR: 매점 앞 긴 줄. 주인공과 준수가 줄 끝에 선다. 앞쪽에 하린이 친구와 서 있다가 돌아본다.

harin: 3반이 왜 여기까지 와.
player: 1층이 우리 반이 더 가까워.
harin: 그래서 줄은 내 뒤네.
junsu: (작게) ...이분이 그 단톡 학원 친구 친구?
player: 그 얘기는 하지 마.

NARR: 하린이 계산을 마치고 나오면서 주인공 손에 든 것을 본다.

harin: 또 그거야? 소시지빵.
player: 이게 제일 배불러.
harin: 중학교 때부터 3년 동안 그것만 먹었잖아.

CHOICE ep04_harin_store:
  - "넌 뭐 샀는데?" -> harin_affinity += 1 ; goto A
  - "토요일에 경기 있어." -> harin_affinity += 1 ; goto B
  - "질리면 바꿀게." -> goto C

A:
  harin: 우유. 학원 가기 전에 먹을 거.
  player: 그게 점심이야?
  harin: 점심은 급식 먹었어. 너야말로 급식 먹고 또 온 거잖아.
B:
  harin: 이번엔 나가?
  player: 모르지.
  harin: 나가면 말해달라고 했잖아.
C:
  harin: 3년째 안 질렸으면 평생 안 질려.
```

※ `ep04_harin_store` 선택 결과는 SCENE ep04_07에서 사용.

---

### SCENE ep04_03 | 토요일 | 주말리그 — 서림고 vs 한빛고

```
SYSTEM: 관전 모드. 주요 장면만 재생됩니다.

NARR: 한빛고 홈구장. 날이 흐리다.

AUTO:
  - 2회 말, 한빛고 2타점 적시타. 0 대 2.
  - 4회 초, 서림고 9번 타자(2학년 유격수 정민재) 삼진. 이번 경기 두 번째.
  - 5회 말, 서림고 투수 볼넷 연속 세 개 후 만루 홈런. 0 대 6.
  - 6회 초, 서림고 주장 솔로 홈런. 1 대 6.

NARR: 7회 초. 서림고 공격. 2사 주자 1루. 다음 타자 9번 정민재가 대기 타석에서 일어난다.

coach_oh: 민재야. 잠깐.

NARR: 정민재가 멈춘다. 감독이 벤치를 돌아본다.

coach_oh: {player}. 대타.

NARR: 벤치가 조용해진다. 준수가 주인공 등을 툭 친다. 정민재는 헬멧을 벗고 아무 말 없이 들어온다.

player: ...네.

IF taeo_ball_fielded == true:
  coach_oh: 청운고 6번 타구 잡는 거 봤다. 그 정도 눈이면 공도 보일 거다.
ELSE:
  coach_oh: 청운고에서 공 하나 놓친 거, 여기서 갚아.
END

coach_oh: 초구부터 쳐도 된다.

NARR: 소율이 덕아웃 끝에서 카메라를 들고 있다. 렌즈가 주인공을 향해 있다.

soyul: (주인공이 지나갈 때, 빠르게) 저 투수 오늘 초구 직구 비율 80퍼센트. 2스트라이크 되면 슬라이더 바깥쪽.
player: 그걸 언제 세셨어요.
soyul: 1회부터. 가.
```

---

### SCENE ep04_04 | 타석

```
NARR: 타석에 선다. 뒤쪽 시점. 마운드가 생각보다 가깝다. 포수 미트가 바깥쪽에 자리 잡는다.

GAMEPLAY batting id=ep04_ab
  pitcher: hanbit_starter
  pitch_profile: first_pitch_fastball_80 / two_strike_outside_slider
  runners: first
  outs: 2
  modifiers: warmup_effect
    - calm: 첫 2구 동안 투구 궤적 표시가 조금 더 길게 유지
    - eager: 정타 시 타구 속도 보너스, 대신 헛스윙 판정 구간이 넓어짐
  result -> first_ab_result  (hit | out | strikeout)
  SYSTEM: stats.at_bat_video_count += 1

NARR: 투수가 와인드업한다.

IF first_ab_result == hit:
  NARR: 배트 중심에 맞는 소리. 타구가 유격수 머리를 넘어 좌중간에 떨어진다. 1루 주자는 3루까지. 주인공은 1루를 밟고 선다.
  NARR: 1루 코치(3학년 선배)가 주인공 헬멧을 두드린다.
  first_base_coach: 됐어, 됐어.
  NARR: 덕아웃에서 준수가 일어나 소리를 지른다. 소율은 카메라를 든 채로 다른 손을 흔든다.
  NARR: 다음 타자 2루 땅볼. 득점 없이 이닝 종료.
ELIF first_ab_result == out:
  NARR: 배트에 맞긴 했다. 타구가 2루수 정면으로 굴러간다. 1루까지 전력으로 뛴다. 한 걸음 차이로 아웃.
  NARR: 주인공이 1루를 지나쳐 멈춘다. 숨이 차다.
  first_base_coach: 잘 뛰었다. 들어가.
ELSE:
  NARR: 2스트라이크. 바깥쪽으로 흘러나가는 공. 배트가 따라간다. 헛스윙.
  NARR: 주인공이 잠깐 타석에 서 있다가 배트를 들고 돌아선다.
  NARR: 덕아웃 끝, 소율이 카메라를 내리지 않고 있다.
END

SYSTEM: 첫 타석 기록이 저장되었습니다.
```

---

### SCENE ep04_05 | 7회 말 | 유격수

```
NARR: 감독이 짧게 말한다.

coach_oh: 그대로 유격수 들어가.

NARR: 주인공이 글러브를 들고 나간다. 덕아웃 뒤쪽에 앉아 있는 정민재와 잠깐 눈이 마주친다. 정민재가 먼저 시선을 돌린다.
NARR: 1사 후. 3유간 땅볼.

GAMEPLAY fielding id=ep04_def
  position: shortstop
  situation: one_out
  ball: grounder_ss_3b_hole
  balls: 1~2
  result -> ep04_def_result  (success | error)
  SYSTEM: stats.fielding_video_count += 1

IF ep04_def_result == success:
  NARR: 아웃. 덕아웃에서 박수 소리.
ELSE:
  NARR: 주자 1루. 감독은 표정 변화 없이 다음 수비 사인을 낸다.
END

NARR: 이닝 종료. 경기는 그대로 끝난다.

SYSTEM: 경기 결과 — 서림고 1 – 6 한빛고
```

---

### SCENE ep04_06 | 경기 후 | 덕아웃

```
NARR: 장비를 정리하는 부원들. 감독이 주인공 옆을 지나가다 멈춘다.

IF first_ab_result == hit:
  coach_oh: 초구 쳤냐.
  player: 두 번째 공이요.
  coach_oh: 그래. 다음 주 연습 때 배팅 케이지 한 칸 더 써.
  NARR: 감독이 그냥 지나간다. 준수가 입 모양으로 '한 칸 더'라고 말하며 눈을 크게 뜬다.
ELIF first_ab_result == out:
  coach_oh: 맞히긴 맞혔네.
  player: 네.
  coach_oh: 1루까지 뛴 거 좋았다. 그거 계속해.
ELSE:
  coach_oh: 마지막 공, 뭐였는지 알았냐.
  player: ...슬라이더요.
  coach_oh: 알면서 쫓아갔으면 다음엔 안 쫓아가면 되는 거다.
END

NARR: 소율이 노트북을 펴고 쪼그려 앉아 있다. 주인공이 다가가자 화면을 돌려 보여준다.

UI laptop_folder:
  {player}_1학년
    └ 타석 (1)
       └ {player}_타석_001_vs한빛고.mp4

soyul: 1호.
player: ...그게 그렇게 중요해요?
soyul: 응. 나중에 100호 넘어가면 이거 다시 볼 거거든.

CHOICE ep04_soyul_001:
  - "100호까지 찍어주실 거예요?" -> soyul_affinity += 2 ; goto A
  - "볼 거 없을 텐데요." -> soyul_affinity += 1 ; goto B
  - "투수 공 세주신 거 덕분이에요." -> soyul_affinity += 1 ; goto C

A:
  soyul: 내가 졸업하기 전에 100호 채우면 찍어주지. 그러니까 빨리 나가.
B:
  soyul: 그건 내가 판단한다니까. 입부할 때도 말했잖아.
C:
  soyul: 그건 그냥 센 거고. 친 건 너지.

NARR: 버스 쪽으로 걸어가는 길. 정민재가 혼자 앞서 걷고 있다. 준수가 주인공 옆으로 와서 작게 말한다.

junsu: 민재 선배 좀 그렇겠다.
player: ......
junsu: 아니, 네 잘못은 아니고.
```

---

### SCENE ep04_07 | 밤 | 휴대폰

```
IF ep04_harin_store == B:
  UI phone_message thread=harin:
    harin: 오늘 나갔어?
  goto RESULT
ELSE:
  CHOICE ep04_text_first:
    - "(하린에게 먼저 보낸다) 오늘 경기 나갔어" -> harin_affinity += 1 ; goto RESULT
    - "(안 보낸다)" -> goto END_SCENE
END

RESULT:
  IF first_ab_result == hit:
    UI phone_message thread=harin:
      player: 대타로 한 번 쳤는데 안타 침
      harin: 오
      harin: 첫 안타?
      player: 고등학교 와서는
      harin: 그럼 첫 안타 맞네
  ELIF first_ab_result == out:
    UI phone_message thread=harin:
      player: 대타로 한 번. 땅볼
      harin: 그래도 쳤네
      player: 아웃이야
      harin: 지난주엔 몸만 풀었다며
  ELSE:
    UI phone_message thread=harin:
      player: 대타 한 번. 삼진
      harin: 아
      harin: 그럼 다음 거 기다리면 되겠네
      player: 언제 올지 모르는데
      harin: 그래도 한 번은 왔잖아
  END

END_SCENE:
  SYSTEM: 4화 완료
  SYSTEM: 획득 — 타격 경험치 / 수비 경험치 / 소율 호감도 / 하린 호감도
  SYSTEM: 플래그 — first_ab_result
  SYSTEM: 해금 — phone_records_personal (기록실: 개인 타격 기록)
```

---

# EP05 — 마지막 날이 아닌 날

| 항목 | 값 |
|---|---|
| 시기 | 고1 6월 말 ~ 7월 |
| 역할 | 백호기 1회전 탈락, 3학년 은퇴. 팀 목표 "전국대회 1승" 등록 |
| 직접 조작 | 배팅 연습 10구, 대타 타석 1회, 유격수 수비 |

---

### SCENE ep05_01 | 6월 말 | 주말리그 전반기 최종전 이후

```
NARR: 서림고 덕아웃. 경기는 이미 끝났다. 부원들이 휴대폰 하나에 머리를 모으고 있다. 소율이 화면을 새로고침한다.

soyul: 잠깐, 잠깐만... 지금 다른 구장 결과 올라온다.
junsu: 우리 이긴 거 아니에요? 왜 다른 데를 봐요?
senior_a: 우리가 이겨도 동인고가 이기면 끝이야.
junsu: ...아.

NARR: 화면 갱신. 동인고 패배.

soyul: 권역 4위! 백호기 본선이야!

NARR: 덕아웃이 터진다. 3학년 주장 한도윤이 의자에 털썩 앉아 모자를 얼굴에 덮는다. 감독은 벤치 끝에서 기록지를 접는다.

coach_oh: 3년 만이다.
junsu: (작게) 3년 만이래.
player: 들었어.

NARR: 한도윤이 모자를 내리고 웃는다.

doyun: 이걸로 나 졸업 전에 전국대회 한 번은 나가보네.
```

---

### SCENE ep05_02 | 7월 | 대회 전주 | 배팅 케이지

```
IF first_ab_result == hit:
  NARR: 배팅 케이지 두 칸 중 한 칸에 주인공 이름이 적혀 있다. 감독이 약속대로 한 칸을 더 준 것.
  junsu: 진짜 한 칸 주셨네.
ELSE:
  NARR: 배팅 케이지 앞, 부원들이 순서를 기다린다. 주인공 차례는 맨 끝이다.
END

GAMEPLAY batting_practice id=ep05_cage
  balls: 10
  pitch_mix: fastball + breaking
  result -> 정타 수에 따라 타격 경험치

NARR: 연습이 끝나고 장비를 정리하는데, 3학년 김성호가 혼자 남아 티배팅을 하고 있다. 외야수. 주인공이 입부한 뒤로 경기에 나온 걸 한 번도 본 적이 없다.

junsu: (작게) 성호 선배는 맨날 마지막까지 남으시네.
player: 그러게.

NARR: 김성호가 주인공 쪽을 보고 손을 든다.

seongho: 1학년, 공 좀 올려줄래?

CHOICE ep05_seongho:
  - "네, 올려드릴게요." -> seongho_tee = true ; goto A
  - "저 먼저 들어가 볼게요." -> goto B

A:
  NARR: 주인공이 공을 하나씩 티 위에 올린다. 김성호가 친다. 소리가 좋다.
  seongho: 고마워. 3년 동안 이거 혼자 했거든.
  player: ...왜 혼자 하셨어요?
  seongho: 다들 집에 갔으니까. 너도 이제 가.
B:
  seongho: 어, 그래. 수고했어.
```

---

### SCENE ep05_03 | 대회 당일 | 서울 원정 버스

```
NARR: 새벽 5시. 학교 버스 안. 부원들 대부분 잠들어 있다. 소율은 안 자고 노트북으로 대진표를 보고 있다.

soyul: 상대 해성고. 작년 이 대회 8강.
player: 세요?
soyul: 세지. 근데 1회전에서 강팀 안 만나는 건 우리 같은 학교한텐 거의 없어.

NARR: 소율이 화면을 넘긴다. 다른 쪽 대진. '청운고'.

soyul: 청운고는 반대편. 우리가 4강까지 가야 만나.
player: ...멀다.
soyul: 응. 엄청.
```

---

### SCENE ep05_04 | 백호기 1회전 — 서림고 vs 해성고

```
SYSTEM: 관전 모드. 주요 장면만 재생됩니다.

NARR: 서울 야구장. 서림고 부원들이 그라운드에 처음 들어서며 관중석을 올려다본다. 비어 있지만, 크다.

AUTO:
  - 1회 말, 해성고 4번 타자 투런 홈런. 0 대 2.
  - 3회 초, 한도윤 적시 2루타. 1 대 2. 서림고 덕아웃이 일어선다.
  - 5회 말, 해성고 연속 안타로 2실점. 1 대 4.
  - 6회 말, 서림고 실책으로 1실점. 1 대 5.

NARR: 7회 초. 서림고 공격. 1사 2루. 9번 정민재 차례.

coach_oh: {player}.

NARR: 주인공이 일어선다. 정민재가 대기 타석에서 돌아본다. 이번엔 헬멧을 벗으며 한마디 한다.

minjae: ...쳐.
player: 네.

IF first_ab_result == hit:
  coach_oh: 한 번 더 해봐.
ELIF first_ab_result == out:
  coach_oh: 이번엔 끝까지 뛰는 걸로 끝나지 마라.
ELSE:
  coach_oh: 바깥쪽 슬라이더. 기억하지.
END

soyul: (지나갈 때) 이 투수는 몸쪽 직구로 카운트 잡아. 초구 몸쪽 70퍼센트.

GAMEPLAY batting id=ep05_ab
  pitcher: haeseong_starter
  pitch_profile: first_pitch_inside_fastball_70 / finisher_forkball
  runners: second
  outs: 1
  result -> ep05_ab_result  (hit | out | strikeout)
  SYSTEM: stats.at_bat_video_count += 1

IF ep05_ab_result == hit:
  NARR: 몸쪽 직구를 당겨 3유간을 꿰뚫는다. 2루 주자 홈인. 2 대 5.
  NARR: 1루에서 주인공이 덕아웃을 본다. 한도윤이 두 팔을 들고 있다.
  NARR: 후속 타자 병살. 이닝 종료.
ELIF ep05_ab_result == out:
  NARR: 2루 쪽 땅볼. 주자는 3루로 진루. 주인공은 1루에서 아웃.
  NARR: 후속 타자 삼진. 이닝 종료.
ELSE:
  NARR: 포크볼. 배트가 공 위로 지나간다.
  NARR: 덕아웃으로 돌아오는 길, 한도윤이 주인공 엉덩이를 배트로 툭 친다.
  doyun: 괜찮아. 저거 나도 못 쳐.
END
```

---

### SCENE ep05_05 | 7회 말 ~ 8회 | 유격수

```
NARR: 주인공이 그대로 유격수로 들어간다.

GAMEPLAY fielding id=ep05_def
  position: shortstop
  balls: 1~2
  result -> ep05_def_result  (success | error)
  SYSTEM: stats.fielding_video_count += 1

IF ep05_def_result == success:
  NARR: 아웃. 관중석 서림고 학부모 쪽에서 작은 박수.
ELSE:
  NARR: 주자 출루. 다음 타자 범타로 실점 없이 종료.
END

NARR: 8회는 양 팀 무득점.
```

---

### SCENE ep05_06 | 9회 초 | 마지막 타석

```
NARR: 9회 초. 서림고 마지막 공격. 2사 주자 없음. 다음 타자는 주인공.
NARR: 주인공이 헬멧을 쓰고 대기 타석으로 걸어간다. 감독이 부른다.

coach_oh: {player}. 잠깐.

NARR: 주인공이 멈춘다. 감독이 벤치 끝을 본다.

coach_oh: 성호야. 대타.

NARR: 덕아웃이 조용해진다. 김성호가 고개를 든다. 잠깐 아무도 움직이지 않는다.

seongho: ...저요?
coach_oh: 그래. 너.

NARR: 주인공이 헬멧을 벗는다. 김성호가 배트를 들고 일어선다. 지나가면서 주인공 어깨에 손을 잠깐 올린다.

IF seongho_tee == true:
  seongho: 공 올려줘서 고마웠다.
ELSE:
  seongho: 미안하다, 1학년.
END

NARR: 김성호가 타석에 선다. 3학년 전원이 덕아웃 난간에 붙어 선다.

doyun: 성호야!
NARR: (3학년 전원) 김성호!

NARR: 풀카운트까지 간다. 파울, 파울, 파울. 덕아웃이 숨을 죽인다.
NARR: 7구째. 배트에 맞는다. 유격수 쪽 땅볼. 김성호가 1루를 향해 전력으로 달린다. 헬멧이 벗겨진다.
NARR: 송구. 1루수 미트에 공이 꽂히는 소리. 김성호의 발이 베이스를 밟는다. 한 걸음 차이.

umpire: 아웃!

NARR: 김성호가 1루를 지나쳐 몇 걸음 더 뛰다가 멈춘다. 숨을 고른다. 돌아서서 덕아웃을 향해 고개를 숙인다.

IF ep05_ab_result == hit:
  SYSTEM: 경기 결과 — 서림고 2 – 5 해성고 (백호기 1회전 탈락)
ELSE:
  SYSTEM: 경기 결과 — 서림고 1 – 5 해성고 (백호기 1회전 탈락)
END
```

※ 김성호 타석은 결과 고정 연출 (조작 없음).

---

### SCENE ep05_07 | 경기 후 | 야구장 복도

```
NARR: 3학년들이 복도 벤치에 앉아 있다. 누구는 울고, 누구는 웃는다. 김성호는 벗겨진 헬멧을 손에 들고 있다.
NARR: 1·2학년들은 조금 떨어진 곳에서 장비를 챙긴다. 준수가 코를 훌쩍인다.

player: 너 울어?
junsu: 아니. 먼지.
player: 실내야.
junsu: 실내도 먼지 있어.

NARR: 한도윤이 일어나 1학년 쪽으로 온다.

doyun: 1학년.
player: 네.
doyun: 오늘 감독님이 성호 넣으신 거, 서운했냐?

CHOICE ep05_doyun:
  - "아니요. 성호 선배 타석 봤으니까요." -> doyun_talk = true ; goto A
  - "...조금요." -> doyun_talk = true ; goto B
  - "잘 모르겠어요." -> doyun_talk = true ; goto C

A:
  doyun: 그래. 3년 동안 오늘이 처음이었어, 성호한테.
B:
  doyun: 그래도 돼. 근데 넌 타석 많이 남았잖아. 성호는 오늘이 처음이자 마지막이었어.
C:
  doyun: 그래. 나중에 3학년 되면 알 거야.

doyun: 내년엔 네가 좀 쳐라. 우리 학교 전국대회 1승 아직 없다.

NARR: 한도윤이 돌아서서 3학년들 쪽으로 간다.
```

---

### SCENE ep05_08 | 귀가 버스

```
NARR: 저녁. 서울을 빠져나가는 버스. 다들 조용하다. 소율이 노트북을 펴고 있다. 평소처럼 타이핑하지 않고, 폴더 목록만 보고 있다.

UI laptop_folder:
  한도윤_3학년 / 김성호_3학년 / ...

player: 정리 안 하세요?
soyul: 해야지.

NARR: 소율이 '김성호_3학년' 폴더를 연다. '타석 (1)'. 오늘 날짜의 파일 하나.

soyul: 성호 선배 거, 이거 하나야. 3년 동안.
player: ......
soyul: 3학년 폴더는 이제 안 늘어나.

NARR: 소율이 노트북을 덮는다. 잠깐 창밖을 보다가, 평소 목소리로 돌아온다.

soyul: 그러니까 너는 빨리 늘려. 나 2년밖에 안 남았어.

CHOICE ep05_soyul_bus:
  - "...네." -> soyul_affinity += 1 ; goto A
  - "선배 졸업하면 영상은 누가 찍어요?" -> soyul_affinity += 2 ; goto B

A:
  soyul: 대답 좋다.
B:
  soyul: 그러게. 후배 매니저 하나 들어와야 하는데. 너 아는 애 없어?
  player: 영상 1시간 40분짜리 보내는 거 견딜 수 있는 애요?
  soyul: ...그건 좀 힘들려나.

NARR: 소율의 휴대폰이 진동한다. 대회 속보 알림.

UI phone_news_notification:
  백호기 1회전 — 청운고 콜드게임 승리, 1학년 강태오 2안타 3타점

soyul: 쟤는 오늘도 쳤네.
player: 4강까지 가야 만난다고 하셨죠.
soyul: 응.
player: ...멀다.
```

---

### SCENE ep05_09 | 밤 | 아파트 앞 편의점

```
NARR: 밤 10시 반. 주인공이 편의점에서 나오다가 학원 가방을 멘 하린과 마주친다.

harin: 서울 갔다 온 거 아니었어?
player: 방금 왔어.
harin: 졌어?
player: 1회전.

NARR: 하린이 편의점에 들어갔다가 나온다. 아이스크림 두 개. 하나를 내민다.

harin: 방학 특강 첫날이라 나도 오늘 죽는 줄 알았어.
player: 몇 시부터인데.
harin: 아침 9시부터 밤 10시. 점심이랑 저녁 빼고 계속 그림.
player: ...우리보다 오래 하네.

CHOICE ep05_harin_store:
  - "오늘 3학년 선배들 마지막 경기였어." -> harin_affinity += 1 ; goto A
  - "방학 때 쉬는 날 있어?" -> harin_affinity += 1 ; goto B
  - "잘 먹을게." -> goto C

A:
  harin: 그럼 너는? 나갔어?
  IF ep05_ab_result == hit:
    player: 한 번. 안타 쳤어.
    harin: 졌는데 쳤구나.
    player: 응. 그게 좀 이상해.
  ELSE:
    player: 한 번. 못 쳤어.
    harin: 그래도 나갔네.
  END
  player: 마지막 타석은 3학년 선배가 쳤어. 3년 동안 처음 나온 선배.
  harin: ...그 사람은 쳤어?
  player: 아웃. 근데 엄청 뛰었어.
B:
  harin: 8월에 3일. 너는?
  player: 이제 3학년 빠지니까 더 없을걸.
  harin: 그럼 서로 못 보겠네.
C:
  harin: 응. 녹기 전에 먹어.

SYSTEM: 5화 완료
SYSTEM: 획득 — 타격 경험치 / 수비 경험치 / 소율 호감도 / 하린 호감도
SYSTEM: 플래그 — seongho_tee, doyun_talk
SYSTEM: 해금 — phone_news (뉴스 탭: 고교야구 대회 소식)
SYSTEM: team_goal = "national_first_win" — 팀 목표 "전국대회 1승" 등록
```

---

# EP06 — 새 팀

| 항목 | 값 |
|---|---|
| 시기 | 고1 8월 ~ 10월 |
| 역할 | 3학년 은퇴 후 새 팀. 주전 유격수 확보, 정민재 2루 전환. 어머니·은하식당 첫 등장 |
| 직접 조작 | 병살 연습 5구, 주말리그 후반기 개막전 (주인공 타석·수비 전체), 후반기 주요 경기 |

---

### SCENE ep06_01 | 8월 초 | 운동장

```
NARR: 한낮. 운동장에 아지랑이가 올라온다. 3학년이 빠진 자리. 부원들이 홈플레이트 앞에 줄을 서 있다. 줄이 짧다.

junsu: (작게) 열 명.
player: 세지 마.
junsu: 안 세도 보여.

NARR: 오 감독이 기록지를 들고 앞에 선다.

coach_oh: 오늘부터 이 열 명이 팀이다. 주장은 재혁이.

NARR: 2학년 투수 윤재혁이 고개를 끄덕인다. 놀라는 사람은 없다.

coach_oh: 포지션 바뀌는 사람 있다. 민재.
minjae: 네.
coach_oh: 2루로 간다.

NARR: 운동장이 조용해진다. 매미 소리만 들린다.

coach_oh: 유격수는 {player}.

NARR: 아무도 주인공을 보지 않는다. 정민재도 정면만 본다.

minjae: ...알겠습니다.
coach_oh: 둘이 병살 연습부터 해. 오늘부터 매일.
```

---

### SCENE ep06_02 | 오후 | 내야 — 병살 연습

```
NARR: 감독이 펑고를 친다. 주인공은 유격수, 정민재는 2루. 둘 사이에 말이 없다.

GAMEPLAY fielding_drill id=dp_practice
  balls: 5
  patterns: 6-4-3 (유격수 포구 → 2루수에게 토스) / 4-6-3 (2루수 토스 받아 베이스 밟고 1루 송구)
  judge: 토스 타이밍·방향 정확도
  result -> ep06_dp_practice_success  (int 0~5)

IF ep06_dp_practice_success >= 4:
  NARR: 마지막 공. 주인공의 토스가 정민재 글러브에 정확히 들어간다. 정민재가 1루로 던진다.
  coach_oh: 됐다. 내일도.
ELIF ep06_dp_practice_success >= 2:
  coach_oh: 토스가 높다. 2루수 가슴 높이로.
ELSE:
  coach_oh: 오늘은 해 질 때까지 한다.
  NARR: 해가 기운다. 부원들은 다 들어가고 둘만 남아 있다.
END

NARR: 연습 후. 수돗가. 주인공과 정민재가 나란히 물을 마신다. 정민재가 먼저 입을 연다.

minjae: 너 토스할 때 손목 써.
player: ...네?
minjae: 손목 쓰면 공이 휘어. 팔 전체로 밀어. 2루수가 받기 편하게.

NARR: 정민재가 수건을 집어 들고 일어난다.

CHOICE ep06_minjae:
  - "알겠습니다. 내일 다시 해볼게요." -> minjae_trust += 1 ; goto A
  - "선배, 유격수 자리 일은..." -> minjae_trust += 1 ; goto B
  - "(고개만 숙인다)" -> goto C

A:
  minjae: 그래.
B:
  minjae: 그 얘기는 하지 마.
  NARR: 잠깐 멈춘다.
  minjae: 감독님이 정한 거야. 너 때문에 된 거 아니고. ...근데 실책하면 그건 너 때문이다.
C:
  NARR: 정민재가 그대로 걸어간다.
```

---

### SCENE ep06_03 | 8월 중순 | 은하식당

```
SYSTEM: 휴식일 — 훈련 없음

NARR: 아파트 앞 상가. '은하식당' 간판. 점심시간이 막 지난 오후 2시. 주인공이 앞치마를 두르고 테이블을 닦는다.

mom: 3번 테이블 그릇 좀 갖다줘.
player: 응.
mom: 너 오늘 쉬는 날이라며. 친구들이랑 안 놀아?
player: 준수는 집에서 잔대.
mom: 그 덩치 큰 애? 걔 지난번에 와서 제육 두 그릇 먹고 갔잖아.
player: 세 그릇이야. 하나는 엄마가 서비스로 준 거.

NARR: 가게 문이 열린다. 하린이다. 화구통 없이 맨손.

mom: 하린이 왔네! 오늘 학원 안 가?
harin: 안녕하세요. 오늘부터 3일 쉬어요.
mom: 앉아, 앉아. 김치찌개?
harin: 네.

NARR: 하린이 구석 자리에 앉는다. 주인공이 물을 가져다준다.

harin: 너도 쉬는 날이야?
player: 오늘만.
harin: 근데 왜 여기 있어?
player: 집에 있으면 엄마가 여기로 부르니까. 그럴 바엔 먼저 와.

NARR: 김치찌개가 나온다. 엄마가 계란말이를 하나 더 올려준다.

mom: 이건 서비스.
harin: 감사합니다.
mom: (주방으로 가며) {player}, 너도 먹어. 앉아.

NARR: 주인공이 하린 맞은편에 앉는다.

harin: 야구부 3학년 빠졌다며. 이제 너 경기 나가?
player: 유격수 주전 됐어.
harin: 오. 갑자기?
player: 원래 하던 선배가 2루로 갔어.
harin: ...그 선배는 괜찮대?

CHOICE ep06_harin_restaurant:
  - "괜찮은 척하는 것 같아." -> harin_affinity += 1 ; goto A
  - "토스할 때 손목 쓰지 말래." -> harin_affinity += 1 ; goto B
  - "모르겠어." -> goto C

A:
  harin: 그럼 괜찮은 척하게 둬. 그게 그 사람한테 편한 걸 수도 있어.
B:
  harin: ...그게 무슨 말이야?
  player: 공 던지는 방법 알려준 거.
  harin: 자리 뺏긴 사람이 그걸 알려줬어?
  player: 응.
  harin: 좋은 선배네.
C:
  harin: 그렇겠지. 물어보기도 애매하고.

NARR: 가게 TV에서 야구 뉴스가 흘러나온다. 하린이 고개를 돌린다.

UI tv:
  KKG배 결승 — 청운고, 해성고 꺾고 우승. 1학년 강태오 결승 홈런

harin: 저 사람. 놀이터에서 영상 보던.
player: 응.
harin: 우승했네.
player: 1학년인데.
mom: (주방에서) 누구? 너랑 동갑이야?
player: 응. 청운고.
mom: 잘생겼네.
player: ......
harin: (작게 웃으며) 찌개 식는다.
```

---

### SCENE ep06_04 | 9월 | 주말리그 후반기 개막 — 서림고 vs 동인고

```
NARR: 공설운동장. 2화에서 콜드게임으로 졌던 상대. 전광판에 라인업이 뜬다.

UI scoreboard_lineup:
  9번 유격수 {player}

junsu: (전광판을 가리키며) 저거. 저거 봐.
player: 봤어.
junsu: 사진 찍어도 돼?
player: 찍지 마.

NARR: 소율이 덕아웃 끝에서 카메라를 세팅한다. 이번엔 삼각대가 주인공 쪽 각도로 고정되어 있다.

soyul: 오늘은 처음부터 끝까지 찍는다. 너 선발 첫 경기니까.

SYSTEM: 이번 경기는 직접 조작합니다. 주인공 타석 + 유격수 수비 전체 조작 / 나머지는 자동 진행

GAMEPLAY full_game id=ep06_game
  opponent: dongin
  player_control: batting (all at-bats), fielding (shortstop)
  scripted_moments:
    - 3회 초: 첫 타석
    - 4회 말: 수비
    - 6회 초: 두 번째 타석
    - 7회 말: 병살 상황 (아래 스크립트)
  result -> ep06_game_result  (win | lose)
  SYSTEM: 타석마다 stats.at_bat_video_count += 1, 수비 조작마다 stats.fielding_video_count += 1
```

#### 7회 말 — 병살 상황 (스크립트 구간)

```
NARR: 서림고 2 대 동인고 2 동점. 무사 1루. 동인고 타자가 타석에 선다.

jaehyuk: (마운드에서, 내야를 향해) 땅볼 유도한다. 병살!

NARR: 주인공과 정민재가 눈을 맞춘다. 정민재가 손가락으로 자기 가슴을 가리킨다. 가슴 높이.

GAMEPLAY fielding id=ep06_dp
  pattern: 6-4-3
  partner: minjae
  modifiers:
    - ep06_dp_practice_success >= 4: 토스 판정 구간 확대
    - minjae_trust >= 1: 정민재 송구 정확도 보너스
  result -> ep06_dp_result  (success | fail)

IF ep06_dp_result == success:
  NARR: 유격수 정면 땅볼. 주인공이 잡아 팔 전체로 토스. 정민재가 베이스를 밟고 1루로 던진다. 투 아웃.
  NARR: 정민재가 1루 쪽을 보며 주먹을 쥔다. 주인공 쪽은 안 본다. 대신 글러브로 허벅지를 한 번 친다.
  jaehyuk: 나이스!
ELSE:
  NARR: 토스가 살짝 높다. 정민재가 받아 1루에 던지지만 타자 주자가 먼저 들어간다. 1사 1루.
  NARR: 정민재가 주인공을 보지 않고 자리로 돌아간다.
END
```

※ 7회 말 동점(2-2) 상황은 스크립트로 고정. 이후 진행은 실제 플레이 결과로 승패 결정.

#### 9회 — 결과

```
IF ep06_game_result == win:
  NARR: 9회 말 마지막 아웃. 서림고 덕아웃이 그라운드로 뛰쳐나온다.
  junsu: 이겼어! 동인고 이겼어!
  soyul: (기록지를 보며) 동인고 상대 첫 승. 창단 이후 처음.
  junsu: 처음이요?
  soyul: 처음이야. 내가 기록 다 찾아봤어.
  SYSTEM: dongin_first_win = true
ELSE:
  NARR: 9회 말 끝내기. 동인고 선수들이 홈으로 몰려든다.
  junsu: ...아.
  soyul: (기록지를 보며) 지난번엔 콜드였는데. 오늘은 9회까지 갔어.
  junsu: 그게 위로예요?
  soyul: 기록이야. 위로는 네가 해.
END

SYSTEM: 경기 결과가 저장되었습니다.
```

---

### SCENE ep06_05 | 경기 후 | 덕아웃

```
NARR: 소율이 노트북을 연다. '{player}_1학년' 폴더.

soyul: 타석 영상, 오늘로 {stats.at_bat_video_count}호.
player: 선발 나가니까 확 늘었네요.
soyul: 이제 시작이야. 후반기 끝나면 지금의 세 배는 돼.

NARR: 오 감독이 지나간다.

IF ep06_game_result == win:
  coach_oh: 이긴 경기는 오늘 밤까지만 생각해라.
ELSE:
  coach_oh: 진 경기는 내일 아침까지만 생각해라.
END

IF ep06_dp_result == success:
  NARR: 정민재가 장비 가방을 메고 주인공 옆을 지나가며 짧게 말한다.
  minjae: 토스, 오늘은 괜찮았다.
ELSE:
  NARR: 정민재가 주인공 옆을 지나가다 멈춘다.
  minjae: 손목 썼지.
  player: ...네.
  minjae: 내일 또 하자.
END
```

---

### SCENE ep06_06 | 9월 ~ 10월 | 후반기 진행

```
SYSTEM: 주말리그 후반기 진행
SYSTEM: 주요 경기는 직접 조작 / 나머지 경기는 자동 진행 (주인공 능력치 기반 결과 산출)

GAMEPLAY season_progress id=ep06_second_half
  league: weekend_league_second_half (경기 남부권)
  result -> 후반기 개인 기록 (타율 / 출루율 / 실책 / 병살 처리)
  result -> ep06_season_avg_grade  (high | mid | low)  ※ 후반기 타율 기준, 임계값은 상수로 분리

NARR: (몽타주)
  - 새벽 6시 반, 둘만 남은 병살 연습
  - 비 오는 날 실내 대신 부실 앞 처마 밑에서 하는 티배팅
  - 소율의 노트북 폴더 숫자가 하나씩 올라가는 화면
  - 준수가 처음으로 선발 포수로 나가는 경기, 주인공이 먼저 하이파이브를 내민다
```

---

### SCENE ep06_07 | 10월 말 | 후반기 최종전 이후 | 부실

```
NARR: 후반기가 끝났다. 소율이 화이트보드에 결산을 적는다.

soyul: 후반기 서림고 성적, 권역 5위.
senior_a: 전반기보다 하나 내려갔네.
soyul: 3학년 빠지고 열 명으로 한 거잖아. 이 정도면 잘한 거야.

NARR: 소율이 화이트보드 오른쪽에 개인 기록을 붙인다. 주인공 이름 옆에 후반기 성적.

UI whiteboard_stats:
  후반기 개인 기록 — 타율 / 출루율 / 실책 / 병살 처리 (실제 진행 결과 표시)

IF ep06_season_avg_grade == high:
  soyul: 9번 타자 타율로는 권역 1등이야. 몰랐지?
  player: 그런 것도 따로 세요?
  soyul: 당연하지.
ELIF ep06_season_avg_grade == mid:
  soyul: 후반기 처음 선발인 1학년치고는 괜찮아.
ELSE:
  soyul: 숫자는 별로인데, 마지막 3경기는 좋아졌어. 그게 중요해.
END

NARR: 오 감독이 부실 문을 열고 고개만 들이민다.

coach_oh: {player}. 잠깐 나와.

NARR: 운동장. 해가 빨리 진다. 감독이 홈플레이트 근처에 서 있다.

coach_oh: 후반기 타석, 소율이가 찍은 거 다 봤다.
player: ...네.
coach_oh: 겨울에 할 얘기가 있다. 방학하면 첫날 6시 반에 나와.
player: 무슨 얘기요?
coach_oh: 그때 해.

NARR: 감독이 돌아서서 간다.
```

---

### SCENE ep06_08 | 밤 | 휴대폰

```
UI phone_message thread=junsu:
  junsu: 감독님이 뭐래
  junsu: 혼났어?

CHOICE ep06_junsu_text:
  - "겨울에 할 얘기 있대" -> goto NEXT
  - "모름. 방학 첫날 나오래" -> goto NEXT

NEXT:
  UI phone_message thread=junsu:
    junsu: 헐
    junsu: 그거 좋은 거야 나쁜 거야
    player: 나도 몰라

UI phone_message thread=soyul:
  soyul: 감독님이 너 영상 다 달라고 하셔서 드렸어
  soyul: 후반기 거 전부
  soyul: 뭔가 하실 생각인가 봐
  soyul: 나도 끼워달라고 할 거야 ㅎ

SYSTEM: 6화 완료
SYSTEM: 획득 — 타격 경험치 / 수비 경험치 / 하린 호감도
SYSTEM: 플래그 — minjae_trust (누적), dongin_first_win (승리 시)
SYSTEM: 해금 — phone_records_season (기록실: 시즌 기록 탭)
SYSTEM: 이벤트 예고 — 겨울방학 첫날 감독 면담
```

---

# EP07 — 겨울

| 항목 | 값 |
|---|---|
| 시기 | 고1 겨울방학 (12월 말 ~ 2월 초) |
| 역할 | 전환점. 타격폼 개조, 훈련 방향 선택, 능력치 대폭 상승 |
| 직접 조작 | 새 폼 배팅 연습, 집중 훈련 미니게임, 마지막 배팅 10구 |
| 신규 시스템 | 폼 적응도 (이 화에서만 사용하는 임시 수치) |

---

### SCENE ep07_01 | 겨울방학 첫날 새벽 6시 반 | 부실

```
NARR: 아직 어둡다. 부실 창문에만 불이 켜져 있다. 주인공이 문을 열자 감독과 소율이 노트북 앞에 앉아 있다. 히터가 덜덜거린다.

soyul: 왔다.
player: ...선배는 왜 계세요?
soyul: 끼워달라고 했잖아. 감독님이 6시에 오라셔서 5시 50분에 왔어.
coach_oh: 앉아.

NARR: 노트북 화면. 후반기 주인공 타석 영상이 2분할로 재생된다.

coach_oh: 중학교 때 누가 타격폼 봐준 사람 있냐.
player: ...없어요. 감독님이 투수 출신이셔서 타격은 그냥 알아서 했어요.
coach_oh: 그래 보인다.

NARR: 감독이 영상을 멈춘다. 스윙 직전 프레임.

coach_oh: 네 스윙은 손으로 친다. 공이 오면 손이 먼저 나가서 맞춰. 그래서 맞히긴 잘 맞히는데, 공이 안 뻗어.
player: ......
coach_oh: 소율아.
soyul: 네.

NARR: 소율이 화면을 바꾼다. 왼쪽에 주인공, 오른쪽에 KKG 스타즈 유니폼을 입은 선수. 같은 구종, 같은 코스. 프레임을 맞춰 동시에 재생.

soyul: 백승현. KKG 스타즈 유격수. 이거 만드느라 이틀 걸렸어. 둘 다 몸쪽 직구 당겨 친 거.
player: ...프로랑 비교하는 거예요?
coach_oh: 고등학생 거 보고 배우면 고등학생만큼 된다.

NARR: 0.25배속. 백승현은 공이 투수 손을 떠나기 전에 왼발이 먼저 내려앉고, 골반이 돌고, 그다음에 손이 따라 나온다. 주인공은 손과 몸이 거의 동시에 돈다.

player: ...순서가 달라요.
coach_oh: 그래. 저쪽은 다리 → 허리 → 손. 넌 전부 한 번에.
soyul: 별명이 교과서야. 프로 코치들도 신인들한테 이 사람 영상부터 보여준대.

IF test_squat_grade == high:
  coach_oh: 체력 테스트 때 스쿼트 기록 기억하냐. 3학년보다 많이 했다. 다리 힘은 있는데 그걸 하나도 안 쓰고 있는 거다.
ELSE:
  coach_oh: 다리 힘도 부족하다. 순서를 바꿔도 받쳐줄 하체가 없으면 소용없어. 겨울에 둘 다 해야 한다.
END

coach_oh: 겨울 동안 폼을 바꾼다.
player: 전부요?
coach_oh: 순서만. 근데 순서를 바꾸면 전부 바뀐다. 처음 한 달은 지금보다 못 칠 거다.
player: ......
coach_oh: 봄에 안 돌아오면 그대로 망가지는 거고.

NARR: 히터 소리만 들린다. 소율이 주인공을 본다.

soyul: 근데 돌아오면 완전 다른 타자 되는 거야. 나 그거 찍고 싶어.

CHOICE ep07_form:
  - "하겠습니다." -> form_answer = immediate ; goto A
  - "...한 달이면 언제쯤 돌아와요?" -> form_answer = question ; goto B

A:
  coach_oh: 대답이 빠르네. 그래. 내일부터.
B:
  coach_oh: 모른다. 한 달일 수도 있고 석 달일 수도 있다.
  player: ...그래도 할게요.
  coach_oh: 그래. 내일부터.

SYSTEM: 타격폼 개조 시작
SYSTEM: form_adaptation = 0  ("폼 적응도" 게이지 표시, 0 / 100)
SYSTEM: 폼 적응도가 낮을수록 타격 판정 구간이 좁아집니다
```

※ 백승현은 대사 없음. 화면 속 영상으로만 등장.

---

### SCENE ep07_02 | 12월 마지막 주 | 운동장 — 새 폼 1주차

```
NARR: 눈이 녹아 질척한 운동장. 주인공 혼자 배팅 케이지에 서 있다. 감독은 케이지 뒤에서 팔짱을 끼고 있다.

GAMEPLAY batting_practice id=ep07_new_form_w1
  balls: 10
  modifiers: form_adaptation (낮음 → 판정 구간 축소)

NARR: 헛스윙. 빗맞은 타구. 또 헛스윙.

coach_oh: 손 먼저 나갔다.
player: ...네.
coach_oh: 다시.

NARR: 또 헛스윙. 주인공이 배트를 내리고 숨을 내쉰다.

player: 감독님, 공이 안 보여요.
coach_oh: 보인다. 몸이 아직 모르는 거다. 다시.

NARR: 케이지 밖. 준수가 포수 장비를 입은 채 지켜본다.

junsu: 야, 너 지금 9월보다 못 친다.
player: 알아.
junsu: 아니, 진짜로. 나보다 못 쳐.
player: 너 오늘 몇 개 쳤는데.
junsu: ...두 개.
player: 나 세 개.
junsu: 그럼 비슷하네.

SYSTEM: form_adaptation += 8
```

---

### SCENE ep07_03 | 12월 31일 저녁 | 은하식당

```
NARR: 연말. 가게에 손님이 없다. 엄마가 일찍 가게 정리를 하고, 주인공은 구석 테이블에 엎드려 있다. 손바닥에 물집 테이프가 감겨 있다.

mom: 손 왜 그래.
player: 연습.
mom: 방학인데 학기 중보다 더 하네.
player: 폼 바꾸는 중이야.
mom: 폼?
player: 치는 방법.
mom: 원래 잘 쳤잖아.
player: ...그냥 맞히는 거였대.

NARR: 가게 문이 열린다. 하린이다. 패딩에 목도리를 칭칭 감았다.

mom: 하린이! 오늘도 학원이었어?
harin: 네. 12월 31일에도 해요.
mom: 떡국 먹고 가. 하루 일찍 먹어도 돼.

NARR: 하린이 주인공 맞은편에 앉는다. 주인공 손을 본다.

harin: 그거 뭐야.
player: 물집.
harin: 몇 개.
player: 네 개.
harin: 나 여기.

NARR: 하린이 오른손 중지를 내민다. 연필 쥐는 자리에 굳은살이 단단하게 박혀 있다.

harin: 겨울 특강 석고 데생 하루 여섯 시간. 같은 석고상을 3주째 그리고 있어.
player: 같은 걸?
harin: 응. 아그리파. 이제 눈 감고도 그려.
player: 그럼 이제 다른 거 그리면 되잖아.
harin: 선생님이 아직 아니래. 눈 감고 그리는 거랑 제대로 그리는 거랑 다르대.

NARR: 떡국 두 그릇이 나온다.

CHOICE ep07_harin_newyear:
  - "나도 3주째 같은 스윙만 해." -> harin_affinity += 1 ; goto A
  - "그거 언제 끝나는데?" -> harin_affinity += 1 ; goto B
  - "떡국 식겠다." -> goto C

A:
  harin: 그럼 우리 둘 다 3주째네.
  player: 넌 언제 끝나?
  harin: 몰라. 넌?
  player: 나도 몰라.
  harin: ...떡국이나 먹자.
B:
  harin: 1월 말 평가 보고. 통과하면 다음 석고상.
  player: 통과 못 하면?
  harin: 또 아그리파.
C:
  harin: 너 그거 3년 전에도 똑같이 말했어. 12월 31일에.

NARR: 가게 TV에서 연말 스포츠 결산이 나온다.

UI tv:
  올해의 고교야구 — 강태오 (청운고 1학년) 신인 부문 선정

mom: 저번에 그 잘생긴 애네.
player: ...응.
harin: 너 저거 보면서 연습하지.
player: 어떻게 알아.
harin: 놀이터에서 봤잖아.
```

---

### SCENE ep07_04 | 1월 초 밤 | 부실

```
NARR: 밤 9시. 부실에 불이 켜져 있다. 주인공이 장비를 두러 들어가자 소율이 노트북 앞에 앉아 있다. 화면에 주인공 스윙이 2분할 — 왼쪽 12월, 오른쪽 오늘.

player: 아직 계셨어요?
soyul: 오늘 거 정리 중. 너 오늘 거 봐봐.

NARR: 0.25배속. 오른쪽 스윙에서 왼발이 먼저 내려앉는다. 손은 아직 조금 빠르다.

soyul: 다리는 됐어. 이제 손만 반 박자 늦추면 돼.
player: 반 박자요?
soyul: 프레임으로 세면 네 프레임. 0.13초 정도.
player: ...그걸 다 세셨어요?
soyul: 응. 3주 치 전부.

NARR: 소율이 기지개를 켠다. 책상 위에 편의점 삼각김밥 포장지 두 개.

player: 선배, 저녁 그거 드신 거예요?
soyul: 응. 왜?
player: 집에 안 가세요?
soyul: 이거 끝내고. 이번 겨울 거 다 정리하면 나중에 엄청 재밌을 거라니까. "폼 개조 전/후" 이런 거.

CHOICE ep07_soyul_night:
  - "다음엔 은하식당 오세요. 저희 엄마 가게요." -> soyul_affinity += 2 ; soyul_restaurant_invite = true ; goto A
  - "고맙습니다, 선배." -> soyul_affinity += 1 ; goto B
  - "너무 늦게까지 계시지 마세요." -> soyul_affinity += 1 ; goto C

A:
  soyul: 너네 집 식당이야? 진짜? 뭐가 맛있어?
  player: 제육이요. 준수가 세 그릇 먹었어요.
  soyul: 그럼 나 네 그릇 먹어야지. 기록 깨야 되니까.
B:
  soyul: 고맙긴. 나 이거 재밌어서 하는 거야. ...근데 좀 고맙다는 말 듣고 싶긴 했어.
C:
  soyul: 응, 이것만. 너야말로 손 그거 테이프 다시 감아. 다 떨어졌어.

SYSTEM: form_adaptation += 12
```

---

### SCENE ep07_05 | 1월 중순 | 운동장 — 훈련 방향

```
NARR: 감독이 주인공을 홈플레이트 앞으로 부른다. 화이트보드에 세 줄.

coach_oh: 폼은 이제 반쯤 왔다. 남은 겨울은 하나에 집중한다. 네가 골라.

CHOICE ep07_training:
  - "컨택 — 공을 끝까지 보고 정확히 맞히는 쪽" -> training_direction = contact ; goto A
  - "파워 — 새 폼으로 타구를 멀리 보내는 쪽" -> training_direction = power ; goto B
  - "수비·주루 — 유격수로서 첫 발과 송구, 주루 스타트" -> training_direction = defense_run ; goto C

A:
  coach_oh: 새 폼으로 맞히는 감각을 다시 만든다. 하루 500구.
B:
  coach_oh: 하체 웨이트 두 배. 배팅은 롱티 위주로.
C:
  coach_oh: 아침엔 펑고, 오후엔 베이스러닝. 배팅은 폼 유지만.

GAMEPLAY training_minigame id=ep07_focus
  IF training_direction == contact:  변화구 섞인 배팅 연습 (정타율 측정)
  IF training_direction == power:    스쿼트 리듬 탭 + 롱티 비거리 측정
  IF training_direction == defense_run: 펑고 반응 + 1루 스타트 타이밍
  result -> 선택한 방향 능력치 경험치
SYSTEM: form_adaptation 증가 (최종 SCENE ep07_06에서 100 도달하도록 조정)
```

---

### SCENE ep07_06 | 1월 말 저녁 | 운동장

```
NARR: (몽타주)
  - 새벽 운동장, 입김을 내뱉으며 스윙하는 주인공
  - 준수가 배팅볼을 던져주다 어깨를 돌리며 투덜대는 모습
  - [조건: minjae_trust >= 1] 정민재가 말없이 와서 펑고 바구니를 대신 들어주는 모습
  - 소율의 노트북 폴더: 폼개조_1주차 ~ 폼개조_5주차

NARR: 1월 마지막 날. 해 질 녘. 배팅 케이지에 주인공 혼자.

GAMEPLAY batting_practice id=ep07_final
  balls: 10
  modifiers: 판정 구간 축소 해제 (form_adaptation 100 도달 연출)

NARR: 7구째. 소리가 다르다. 둔탁하지 않고, 짧고 단단하다. 공이 케이지 그물을 뚫을 듯이 날아가 꽂힌다.
NARR: 주인공이 배트를 든 채 멈춘다. 손바닥에 울림이 없다.
NARR: 부실 문이 열린다. 감독이 고개를 내민다. 아무 말 없이 잠깐 서 있다가, 문을 닫고 들어간다.
NARR: 몇 초 뒤, 부실 안에서 소율 목소리.

soyul: (안에서) 감독님, 방금 그 소리 맞죠? 맞죠?
coach_oh: (안에서) 카메라 켜져 있었냐.
soyul: (안에서) 당연하죠!

SYSTEM: form_adaptation = 100 — 타격폼 개조 완료
SYSTEM: 능력치 대폭 상승 (training_direction에 따라 추가 보너스)
UI stat_compare: 개조 전 → 개조 후 능력치 비교
SYSTEM: 폼 적응도 게이지 화면에서 제거
```

---

### SCENE ep07_07 | 2월 초 | 부실

```
NARR: 부원들이 모여 있다. 감독이 화이트보드 앞에 선다.

coach_oh: 2월 셋째 주. 연습경기 하나 잡았다.
jaehyuk: 어디랑요?
coach_oh: 서호대.
junsu: 대학팀이요?
coach_oh: 그래. 대학팀.

NARR: 부원들이 술렁인다.

coach_oh: 구경 오는 사람도 있을 거다.
soyul: 누구요?
coach_oh: 와보면 안다.

NARR: 감독이 나가면서 주인공을 한 번 본다. 그게 전부다.
```

---

### SCENE ep07_08 | 밤 | 휴대폰

```
UI phone_message thread=soyul:
  soyul: [파일] {player}_폼개조_전후비교_최종.mp4
  soyul: 3분 12초짜리야
  soyul: 이번엔 짧지?

IF soyul_restaurant_invite == true:
  UI phone_message thread=soyul:
    soyul: 그리고 연습경기 끝나면 은하식당 간다
    soyul: 제육 네 그릇 각오해
END

UI phone_message thread=harin:
  harin: 평가 통과함
  harin: 이제 아그리파 안 그려도 됨

CHOICE ep07_harin_text:
  - "축하해. 나도 오늘 폼 끝났어" -> harin_affinity += 1 ; goto A
  - "다음은 뭐 그려?" -> harin_affinity += 1 ; goto B

A:
  harin: 같은 날이네
  harin: 그럼 둘 다 다음 거 하면 되겠다
B:
  harin: 줄리앙
  harin: 또 3주 그리겠지

SYSTEM: 7화 완료
SYSTEM: 획득 — 능력치 대폭 상승 / 소율 호감도 / 하린 호감도
SYSTEM: 플래그 — form_answer, training_direction, soyul_restaurant_invite
SYSTEM: 이벤트 예고 — 2월 셋째 주 서호대 연습경기
```

---

# EP08 — 구경 오는 사람

| 항목 | 값 |
|---|---|
| 시기 | 고1 겨울방학 끝 (2월 셋째 주 ~ 2월 말) |
| 역할 | 1학년 마지막 화. 스카우트 관심도 시스템 해금 |
| 직접 조작 | 타격 3회, 수비 1회 |

**이 화의 결과 변수**

| ID | 타입 | 값 |
|---|---|---|
| `ep08_ab1_result` | enum | `hit` / `walk` / `out` / `strikeout` |
| `ep08_def_result` | enum | `double_play` / `out` / `error` |
| `ep08_ab2_result` | enum | `rbi_hit` / `extra_base` / `out` / `strikeout` |
| `ep08_ab3_result` | enum | `hit` / `out` / `strikeout` |
| `ep08_performance` | int | 활약도 점수 |

※ 이 화의 모든 실전 타석은 `stats.at_bat_video_count += 1`, 수비 조작은 `stats.fielding_video_count += 1`.

## 활약도 계산 (스카우트 첫 평가)

```
ep08_performance = 타석1 + 수비 + 타석2 + 타석3

타석1 (ep08_ab1_result):   hit +2 / walk +1 / out 0 / strikeout 0
수비  (ep08_def_result):   double_play +2 / out +1 / error -1
타석2 (ep08_ab2_result):   extra_base +3 / rbi_hit +2 / out 0 / strikeout 0
타석3 (ep08_ab3_result):   hit +3 / out +1 / strikeout 0
```

| 점수 | 등급 | scout_interest | scout_visited_teams |
|---|---|---|---|
| 6 이상 | 상위 | `checked` (체크 대상) | `["taesung_thunders"]` |
| 3 ~ 5 | 중위 | `watching` (관찰 중) | `[]` |
| 2 이하 | 하위 | `none` (관심 없음) | `[]` |

※ 임계값은 밸런스 테스트 후 조정 가능. 상수로 분리할 것.
※ 타석3 범타가 +1인 이유: 150km 직구를 정타로 맞혔다는 연출(소리)이 있기 때문.

---

## 시나리오

---

### SCENE ep08_01 | 2월 셋째 주 토요일 아침 | 서호대 야구장

```
NARR: 대학 야구장. 외야 뒤로 학교 건물이 보인다. 서림고 부원들이 버스에서 내린다. 서호대 선수들이 몸을 풀고 있다. 다들 한 뼘씩 크다.

junsu: ...저 사람들 우리랑 같은 사람 맞아?
player: 4학년이면 스물셋이야.
junsu: 우리 형뻘이네.
player: 너 형 없잖아.
junsu: 그러니까 더 무섭지.

NARR: 소율이 1루 쪽 관중석을 가리킨다. 관중석 중간쯤에 두 사람. 한 명은 스피드건, 한 명은 수첩. 점퍼 등에 구단 로고.

soyul: (작게) 저거 봐. 태성 썬더스.
junsu: 수원 그 프로팀이요?
soyul: 응. 저 사람 나성만 팀장이야. 태성 스카우트 팀장.
player: 그것도 아세요?
soyul: 스카우트 얼굴은 웬만하면 다 알아. 고교 대회 때마다 오니까.

NARR: 소율의 눈이 반짝이다가, 서호대 덕아웃 쪽을 보고 조금 가라앉는다.

soyul: ...근데 우리 보러 온 건 아닐 거야.
player: 그럼요?
soyul: 저기 저 사람. 서호대 4학년 류건우. 올해 대졸 드래프트 상위 후보. 오늘 선발이래.

NARR: 서호대 불펜. 키 큰 투수가 공을 던진다. 미트에 꽂히는 소리가 1루 관중석까지 들린다.

junsu: ...저걸 우리가 쳐요?
coach_oh: (뒤에서) 친다.
```

---

### SCENE ep08_02 | 경기 전 | 서림고 덕아웃

```
NARR: 라인업. 주인공은 여전히 9번 유격수.

coach_oh: 오늘 이기라는 말은 안 한다. 대학팀이다.
jaehyuk: 네.
coach_oh: 대신 겨울에 한 거, 그대로 해. 바뀐 거 있으면 오늘 나온다.

NARR: 감독이 주인공을 따로 본다.

coach_oh: {player}.
player: 네.
coach_oh: 손 먼저 나가지 마라.
player: ...네.

NARR: 소율이 지나가며 주인공 손에 메모 한 장을 쥐여준다.

ITEM_SHOW memo_ryu_gunwoo:
  류건우 — 직구 평균 146, 최고 150
  결정구 슬라이더 (우타자 바깥쪽)
  1회엔 직구 위주. 2바퀴째부터 슬라이더 비율 ↑
  세트포지션 때 투구 템포 빨라짐

player: 이건 언제...
soyul: 대학 리그 영상 어젯밤에 다 봤어. 4시간.
```

---

### SCENE ep08_03 | 경기 | 서림고 vs 서호대 (연습경기)

```
SYSTEM: 이번 경기는 직접 조작합니다. 주인공 타석 + 유격수 수비 / 나머지는 자동 진행
SYSTEM: 이번 경기 활약도가 스카우트 관심도 첫 평가에 반영됩니다
```

**훈련 방향 보정 (경기 전체 적용)**

| training_direction | 보정 |
|---|---|
| `contact` | 슬라이더 궤적 표시 시간 증가 |
| `power` | 정타 시 타구 속도 보너스. 타석2에서 `extra_base` 결과 가능 |
| `defense_run` | 수비 판정 구간 확대 + 도루 성공률 상승 |

**상대 투수 데이터**

| 항목 | 값 |
|---|---|
| 이름 | 류건우 (서호대 4학년, 우투) |
| 직구 | 평균 146 / 최고 150 |
| 결정구 | 슬라이더 (우타자 바깥쪽) |
| 1회차 (타석1) | 직구 위주 |
| 2회차 (타석2) | 슬라이더 비율 상승, 세트포지션 시 템포 빠름 |
| 타석3 한정 | 전력 투구 — 직구 위주, 최고 150 |

```
AUTO:
  - 1회 말, 서호대 연속 안타로 2점. (서림고 0 – 2 서호대)
  - 2회 초, 서림고 삼자범퇴. 류건우 직구에 배트가 하나도 안 따라간다.
```

#### 3회 초 — 타석1

```
NARR: 타석에 선다. 마운드가 멀다. 류건우가 와인드업한다.

GAMEPLAY batting id=ab1
  pitcher: ryu_gunwoo
  pitch_profile: round1_fastball_heavy
  modifiers: training_direction
  result -> ep08_ab1_result  (hit | walk | out | strikeout)

IF ep08_ab1_result == hit:
  NARR: 직구를 끝까지 보고 받아친다. 중견수 앞. 덕아웃이 일어선다.
ELIF ep08_ab1_result == walk:
  NARR: 직구 두 개를 커트하고, 높은 공 네 개를 골라낸다.
ELSE:
  NARR: 직구에 밀린다. 타구가 힘없이 굴러간다.
END

NARR: 관중석. 나성만 팀장이 스피드건을 내리고, 옆 사람에게 뭔가 말한다.
```

#### 4회 말 — 수비

```
NARR: 1사 1루. 서호대 타자가 3유간으로 강한 타구를 친다.

GAMEPLAY fielding id=def1
  position: shortstop
  situation: one_out_runner_on_first
  ball: hard_grounder_ss_3b_hole
  double_play_partner: minjae (2B)
  modifiers: training_direction, minjae_trust
  result -> ep08_def_result  (double_play | out | error)

IF ep08_def_result == double_play:
  NARR: 6-4-3. 서호대 덕아웃에서 "오" 소리가 난다.
ELIF ep08_def_result == out:
  NARR: 1루 송구. 아웃 하나.
ELSE:
  NARR: 공이 글러브를 맞고 튄다.
END

AUTO:
  - 5회 말, 서호대 1점 추가. (서림고 0 – 3 서호대)
```

#### 6회 초 — 타석2

```
NARR: 2사 2루. 서림고 첫 득점 기회.

soyul: (덕아웃에서, 혼잣말) 세트포지션이야. 템포 빨라진다...

GAMEPLAY batting id=ab2
  pitcher: ryu_gunwoo
  pitch_profile: round2_slider_up_set_position_fast_tempo
  runners: second
  outs: 2
  modifiers: training_direction
  result -> ep08_ab2_result  (rbi_hit | extra_base | out | strikeout)
  rule: extra_base는 training_direction == power 이고 정타일 때만 발생

IF ep08_ab2_result == rbi_hit:
  NARR: 바깥쪽 슬라이더를 밀어 우익선상. 2루 주자 홈인. (서림고 1 – 3 서호대)
ELIF ep08_ab2_result == extra_base:
  NARR: 슬라이더를 걷어 올려 좌중간 펜스 직격. 2루타. (서림고 1 – 3 서호대)
ELSE:
  NARR: 슬라이더에 배트가 따라 나간다.
END
```

#### 8회 초 — 타석3

```
NARR: 서호대가 투수를 바꾸려다 멈춘다. 류건우가 마운드에 남는다. 마지막 이닝.
NARR: 서호대 포수가 마운드에 올라간다. 류건우가 서림고 덕아웃 쪽을 한 번 본다. 정확히는 대기 타석의 주인공을.

junsu: (작게) 쟤 너 보는데.
player: ......

NARR: 초구. 150. 미트 소리가 운동장을 울린다.

GAMEPLAY batting id=ab3
  pitcher: ryu_gunwoo
  pitch_profile: full_effort_fastball_150
  start_count: 0-1  (초구 스트라이크 연출 후 시작)
  modifiers: training_direction
  result -> ep08_ab3_result  (hit | out | strikeout)

IF ep08_ab3_result == hit:
  NARR: 2구째, 같은 직구. 왼발이 먼저 내려앉는다. 골반, 그다음 손. 짧고 단단한 소리. 타구가 투수 옆을 스치고 중견수 앞으로 빠진다.
  NARR: 1루에 선 주인공. 마운드의 류건우가 모자를 벗었다 다시 쓴다.
ELIF ep08_ab3_result == out:
  NARR: 배트에 맞는다. 유격수 정면. 하지만 소리가 다르다. 서호대 유격수가 공을 잡고 손을 털며 1루에 던진다.
ELSE:
  NARR: 150 직구 세 개. 세 번째에 배트가 나가지만 늦는다.
END
```

#### 경기 종료

```
IF ep08_ab2_result in [rbi_hit, extra_base]:
  SYSTEM: 경기 결과 — 서림고 1 – 4 서호대 (연습경기)
ELSE:
  SYSTEM: 경기 결과 — 서림고 0 – 4 서호대 (연습경기)
END

SYSTEM: ep08_performance 계산 (3장 공식)
```

---

### SCENE ep08_04 | 경기 후 | 1루 관중석 아래

```
NARR: 정리 시간. 주인공이 장비를 챙기는데, 관중석 계단으로 나성만 팀장이 내려온다. 오 감독에게 간다. 둘이 악수한다. 오래 알던 사이처럼.

na_scout: 오 감독, 오랜만이네.
coach_oh: 와줘서 고맙다.
na_scout: 류건우 보러 온 거지 뭐. 근데...

NARR: 나 팀장이 주인공 쪽을 본다.

IF ep08_performance >= 6:
  na_scout: 9번 유격수. 몇 학년이야?
  coach_oh: 1학년. 이제 2학년 올라가고.
  na_scout: ...1학년?
  NARR: 나 팀장이 수첩을 꺼낸다.
  na_scout: 이름.
  coach_oh: {player}.
  NARR: 나 팀장이 적는다. 펜을 수첩에 끼우고 주인공을 한 번 더 본다.
  na_scout: 너 일부러 남아 있으라고 한 거였구나.
  coach_oh: 보라고 한 적 없다. 네가 본 거지.
  na_scout: 봄에 주말리그 일정 보내줘.
  SYSTEM: scout_interest = checked
  SYSTEM: scout_visited_teams = ["taesung_thunders"]

ELIF ep08_performance >= 3:
  na_scout: 9번 유격수, 마지막 타석 스윙 괜찮던데. 몇 학년?
  coach_oh: 1학년.
  na_scout: 아직 어리네. 봄에 한 번 더 보지.
  NARR: 나 팀장이 수첩에 짧게 뭔가 적는다. 이름까지는 묻지 않는다.
  SYSTEM: scout_interest = watching
  SYSTEM: scout_visited_teams = []

ELSE:
  na_scout: 오늘 고생했어. 류건우는 생각보다 더 좋네.
  NARR: 나 팀장이 떠난다.
  coach_oh: (혼잣말) ...봄에 또 온다.
  SYSTEM: scout_interest = none
  SYSTEM: scout_visited_teams = []
END

NARR: 소율이 덕아웃 기둥 뒤에서 이 장면을 전부 보고 있었다. 카메라는 내린 채로.

SYSTEM: 스카우트 관심도 시스템이 해금되었습니다.
SYSTEM: 첫 평가 결과 표시 — checked: "체크 대상" / watching: "관찰 중" / none: "관심 없음"
SYSTEM: scout_tab_unlocked = true  (휴대폰 '스카우트' 탭 추가)
```

---

### SCENE ep08_05 | 저녁 | 은하식당

```
IF soyul_restaurant_invite == true:

  NARR: 저녁 7시. 은하식당 구석 테이블. 주인공, 준수, 소율. 엄마가 제육볶음을 내온다.

  mom: 네가 소율이구나. {player}이가 영상 찍어주는 선배라고 하던데.
  soyul: 안녕하세요! 네, 제가 찍어요. 전부요.
  mom: 전부?
  soyul: 입학 때부터 전부요. 나중에 보여드릴게요.
  mom: 그래, 먹어. 많이 먹어.

  NARR: 준수가 첫 그릇을 비운다. 소율도 첫 그릇을 비운다. 둘이 서로를 본다.

  junsu: 선배, 진짜 네 그릇 드실 거예요?
  soyul: 기록은 깨라고 있는 거야.

  NARR: 30분 뒤. 소율 앞에 빈 그릇 두 개 반. 숟가락을 내려놓는다.

  soyul: ...기록은 내년에 깰게.
  junsu: 저 세 그릇 기록 아직 유효한 거죠?
  mom: (주방에서) 준수는 오늘도 세 그릇이야.

  NARR: 소율이 휴대폰을 꺼내 주인공에게 내민다. 오늘 경기 영상. 8회 세 번째 타석.

  soyul: 이거 봐봐. 3월 영상이랑 비교하면 완전 다른 사람이야.
  player: 그렇게 달라요?
  soyul: 응. 내가 폴더를 월별로 나눠놔서 바로 보여.

  NARR: 가게 문이 열린다. 하린이다. 테이블을 보고 잠깐 멈춘다.

  harin: ...안녕하세요.
  soyul: 어! 혹시 소꿉친구? {player}이 얘기 많이 했어!
  player: 안 했어요.
  soyul: 안 했어. 근데 준수가 했어.
  junsu: 제가요?
  mom: 하린이도 앉아. 찌개 줄게.

  CHOICE ep08_harin_seat:
    - "(하린 자리를 옆에 만들어준다)" -> harin_affinity += 1
    - "학원 끝났어?" -> harin_affinity += 1
    - "(그냥 둔다)" -> (변화 없음)

  NARR: 하린이 테이블 끝에 앉는다. 소율이 휴대폰을 하린에게도 보여준다.

  soyul: 이거 오늘 경기! 상대가 대학생이었어.
  harin: ...잘 쳤네.
  soyul: 그치? 겨울에 폼 바꿨거든.
  harin: 알아요. 손에 물집 네 개 있었어요.
  soyul: 어, 그것도 알아?

ELSE:

  NARR: 저녁. 은하식당. 주인공 혼자 카운터 옆에 앉아 있다. 엄마가 찌개를 내준다.

  mom: 오늘 대학생들이랑 했다며. 어땠어?
  player: 졌어.
  mom: 쳤어?

  IF ep08_ab3_result == hit:
    player: 응. 마지막에.
  ELSE:
    player: 아니. 근데 소리는 좋았대.
  END

  mom: 그래. 먹어.

END
```

※ 수정 사항: 이전 버전의 "2·3화 영상이랑 비교" 대사는 메타 발언이라 "3월 영상"으로 교체함.
※ `{player}이가` / `{player}이` 조사는 이름 받침 유무에 따라 처리 필요 (이가/가, 이/—).

---

### SCENE ep08_06 | 2월 말 | 야구부 부실

```
NARR: 졸업식이 끝난 다음 날. 부실 벽 달력이 3월로 넘어가 있다. 소율이 화이트보드에 새로 적는다.

UI whiteboard:
  신입생 입부 설명회 — 3월 둘째 주
  대통령배 — 출전권 없음
  주말리그 전반기 개막 — 3월 넷째 주

soyul: 자, 이제 나 3학년이야.
junsu: 축하드려요.
soyul: 축하할 일 아니야. 나 이제 1년 남았어.

NARR: 소율이 노트북을 연다. 폴더 이름을 바꾼다.

UI laptop_folder:
  {player}_1학년 → {player}_1학년_완료
  새 폴더 생성: {player}_2학년

soyul: 1학년 폴더 닫는다.
player: 몇 개였어요?
soyul: 타석 영상 {stats.at_bat_video_count}개, 수비 {stats.fielding_video_count}개, 폼 개조 5주 치. 그리고 백승현 비교 영상 하나.
player: ...많네요.
soyul: 2학년 땐 이거 세 배 할 거야. 나 졸업 전에 100호 채운다고 했잖아.
```

---

### SCENE ep08_07 | 밤 | 주인공 방 (휴대폰)

```
NARR: 주인공 방. 휴대폰에 새로 생긴 '스카우트' 탭을 연다.

UI phone_scout_tab:
  {player} — 서림고 2학년 (예정) / 유격수
  관심도: {scout_interest 표시명}
  방문 구단: {scout_visited_teams 표시 — 비어 있으면 "없음"}
  예상 지명: —

UI phone_news_notification:
  고교야구 3월 파워랭킹 예고 — 1위 후보 강태오 (청운고)

UI phone_message thread=junsu:
  junsu: 야 내일 신입생 몇 명 올 것 같아
  junsu: 나 후배 생기는 거 처음이야
  player: 나도
  junsu: 우리 선배 되는 거임
  junsu: 믿기지 않음

SYSTEM: 8화 완료 — 1학년 종료
SYSTEM: 1학년 결산 화면 표시
  - 시즌 기록
  - 능력치 변화 (입부 → 현재)
  - 스카우트 관심도
  - 소율 / 하린 호감도
  - 획득 플래그 목록
SYSTEM: school_year = 2
SYSTEM: 다음 — 2학년: 이름 알리기
```

---


---

# 부록 A. 회차별 신규 등장 요소

| 회차 | 인물 | 장소 | 시스템 |
|---|---|---|---|
| EP01 | 하린, 소율, 준수, 오 감독 | 등굣길, 부실, 서림고 운동장 | 체력 테스트, 호감도, 휴대폰 연락처 |
| EP02 | (강태오 영상) | 공설운동장, 학교 버스, 아파트 놀이터 | 펑고 수비 연습, 관전 모드, 기록실(팀) |
| EP03 | 강태오, 청운고 감독 | 청운고 야구장, 수돗가 | 관찰 미니게임, 인물 정보 |
| EP04 | 정민재 | 매점, 한빛고 구장 | 실전 타격, 소율 영상 카운트, 기록실(개인) |
| EP05 | 한도윤, 김성호 | 서울 야구장(백호기), 편의점 | 뉴스 탭, 팀 목표 |
| EP06 | 윤재혁, 엄마 | 은하식당 | 병살 연계, 풀게임 조작, 시즌 진행, 기록실(시즌) |
| EP07 | (백승현 영상) | — | 폼 적응도(임시), 훈련 방향 |
| EP08 | 나성만, (류건우) | 서호대 야구장 | 스카우트 관심도, 스카우트 탭, 1학년 결산 |

---

# 부록 B. 구현 체크리스트

- [ ] 화자 목록(1장)의 인물 데이터 등록, 초상화 필요 인물 목록 정리
- [ ] 전역 플래그·변수(2장) 정의. 회차 간 ID 일치 확인
- [ ] 1화 체력 테스트 결과 → `high / mid / low` 매핑 함수
- [ ] `test_overall_grade` 계산 (3종목 평균)
- [ ] 선택지 조건 표시 (`[조건: ...]`) 지원
- [ ] 소율 영상 카운트 (`stats.at_bat_video_count`, `stats.fielding_video_count`) 자동 증가
- [ ] EP06 풀게임 조작 + 7회 말 스크립트 구간 삽입
- [ ] EP06 후반기 시즌 진행 + 타율 등급 임계값 상수화
- [ ] EP07 폼 적응도 게이지 (EP07 종료 시 제거)
- [ ] EP07 훈련 방향 미니게임 3종
- [ ] EP08 활약도 계산 + 임계값 상수화
- [ ] 휴대폰 기능 해금 순서 (2-3장)
- [ ] 이름 조사 처리 (`{player}이가` 등)
- [ ] `docs/worldview.md`에 아래 인물·장소가 없으면 추가 여부 보고: 정민재, 윤재혁, 한도윤, 김성호, 나성만, 류건우, 백승현, 서호대

---

# 부록 C. 원본 대비 수정 내역

| 회차 | 수정 |
|---|---|
| EP01 | 체력 테스트를 주력·스쿼트·반응속도 3종으로 교체. 소율 장비 대사를 "반응속도 측정기"로 변경 |
| EP03 | 귀가 버스·문자에서 수돗가 선택지(A/B/C)에 따라 대사가 갈리도록 보완 |
| EP04 | 소율 대사 "1화 때도" → "입부할 때도" |
| EP05 | "여름 전국대회" → 백호기 |
| EP07 | 비교 영상 강태오 → 백승현. 연습경기 상대 경기대 → 서호대 |
| EP08 | 소율 대사 "2·3화 영상" → "3월 영상" |
