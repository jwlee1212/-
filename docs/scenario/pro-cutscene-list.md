# 컷신 목록 — 2학년 끝 · 3학년 · 프로 (자리만 있고 그림은 아직, 2026-11-05)

> 1학년 컷신(EP01~08, 사이드 스토리)은 `cutscene-plan.md` · `cutscene-gemini-prompts.md` 에 있다. 여기는 그 뒤.
> **1장 = 이야기 카드 속 컷신 후보** (대본에 `CUTSCENE` 줄을 아직 쓰지 않았다 — 그림이 들어오면 대본 해당 장면 첫 줄에 `CUTSCENE <id>` 를 넣고 `build_scenario.py`).
> **2장 = 프로 편 상황 컷신** (프로 데뷔 뒤로는 줄거리 대신 "상황"이 컷신을 연다). 정의는 `content.json pro.milestones` (`scripts/pro_content.py`).
> 상황 컷신은 **이미 게임에 연결돼 있다**: 그 순간이 오면 프로 허브가 `content.json cutscenes` 에서 같은 id 를 찾고, 있으면 컷신을 틀고 없으면 **"컷신 자리" 카드**(제목 + 장면 한 줄 + id)를 띄운다. 그림을 만들어 `cutscenes` 에 id 를 더하기만 하면 카드가 컷신으로 바뀐다.
> 그림 규칙은 CLAUDE.md 원칙 6 그대로: 인물은 실루엣, 주인공 얼굴은 안 보이게(뒷모습·모자 그늘), 컷마다 1~3장.

## 1. 이야기 카드 속 컷신 후보

| id | 장면 (문서) | 한 줄 |
|---|---|---|
| `y2_hundred` | EP14 백 번째 (`scenario_y3_ep14-22.md`) | 노트북 불빛만 켜진 부실, 소율 실루엣이 엔터를 누르고 화면에 "100" |
| `y3_restaurant` | EP16 은하식당 | 반쯤 내린 셔터 아래 새어 나오는 불빛, 서류를 정리하는 엄마 실루엣 |
| `y3_taeo_final` | EP18 결승 전날 → 결승 | 3루 쪽 유격수 자리의 태오와 타석의 나, 두 실루엣이 한 화면에 (1학년 수돗가 구도와 짝) |
| `y3_last_summer` | EP19 마지막 여름 | 노을 아래 빈 유격수 자리, 두 실루엣 사이로 글러브가 건네지는 손 |
| `y3_draft` | EP21 드래프트 | 은하식당 TV 불빛에 비친 네 실루엣, 이름이 불리는 순간 엄마가 행주를 떨어뜨린다 (미지명이면 TV 소리만 남는 정적 판) |
| `y3_graduation` | EP22 졸업 | 1학년 EP01 오프닝과 같은 구도 — 빈 운동장 가운데 교복 실루엣 하나, 카메라가 천천히 멀어진다 |
| `pro_signing` | EP23 입단 (`scenario_pro_ep23-26.md`) | 입단식 플래시 속 유니폼 실루엣 / 육성선수면 새벽 퓨처스 구장에 혼자 선 뒷모습 |
| `pro_futures_bus` | EP25 퓨처스 | 새벽 고속도로 위 원정 버스 한 대, 창에 비친 실루엣 |
| `pro_debut` | EP26 데뷔 | 2장 `debut` 와 같은 컷신 (이야기 카드와 상황 컷신이 한 장면을 나눠 쓴다) |
| `side_jian_booth` | 사이드: 서지안 고백 (`y2_side_jian_answer`) | 텅 빈 중계석, 카메라 렌즈 뚜껑을 닫는 손 |
| `side_chae_store` | 사이드: 윤채아 진심 (`pro_side_chae_truth`) | 새벽 편의점 유리창 너머, 컵라면 김 사이로 앉은 두 실루엣 |
| `side_chae_river` | 사이드: 윤채아 1년 뒤 (`pro_side_chae_return`) | 밤 한강 둔치, 편의점 봉투 두 개를 든 실루엣이 다가온다 |

## 2. 프로 편 상황 컷신 (기념 순간)

조건은 1군 기록만 센다 (퓨처스 기록은 안 센다). 커리어에 한 번. `fame` = 인기도 상승.

| id (컷신 = `pro_<id>`) | 언제 | 장면 한 줄 |
|---|---|---|
| `debut` | 1군 첫 경기 | 조명 아래 처음 걸어 나가는 타석, 헬멧 속에서 울리는 함성 |
| `callup` | 퓨처스 → 1군 콜업 | 퓨처스 라커에서 짐을 싸는 밤, 휴대폰에 "내일 1군 합류" |
| `first_hit` | 통산 1안타 | 1루를 밟고 돌아본 덕아웃, 선배가 공을 챙겨 흔든다 |
| `first_rbi` | 통산 1타점 | 홈을 밟은 주자가 헬멧을 두드리고 지나간다 |
| `first_hr` | 통산 1홈런 | 조명탑 사이로 사라지는 타구, 소리 없는 베이스 일주 |
| `first_sb` | 통산 1도루 | 흙먼지 속 슬라이딩, 2루심의 두 팔이 벌어진다 |
| `four_hit` | 한 경기 4안타 | 네 번째 안타 뒤 고개를 젓는 1루 코치 |
| `multi_hr` | 한 경기 2홈런 | 덕아웃 난간에 매달린 선배들 |
| `hr10` · `hr20` · `hr30` | 시즌 10 · 20 · 30홈런 | 전광판의 숫자 / 모자를 벗는 상대 투수 / 일어서는 외야 관중석 |
| `hits150` | 시즌 150안타 | 덕아웃으로 굴러오는 150번째 공 |
| `hits100` | 통산 100안타 | 기념구를 받아 드는 구단 직원 |
| `hr100` | 통산 100홈런 | 관중석에서 행주 대신 응원봉을 든 엄마 |
| `post_debut` | 첫 포스트시즌 경기 | 포스트시즌 엠블럼이 박힌 더그아웃 |
| `ks_champion` | 한국시리즈 우승 | 마운드로 달려가는 실루엣들, 꽃가루 |
| `roy` | 신인왕 | 시상식 무대 조명, 객석 어딘가의 강태오 |
| `gg` | 골든글러브 유격수 | 황금 글러브를 받아 든 손 (고1 때 끈을 묶던 그 손) |
| `mvp` | 정규시즌 MVP | 트로피 너머 은하식당 가족 테이블 |
| `ks_mvp` | 한국시리즈 MVP | 샴페인 속 내 이름 |
| `hr_king` · `batting_title` | 홈런왕 · 타격왕 | 순위표 맨 위의 내 이름 / 고개를 끄덕이는 기록원 |

## 3. Gemini 장면 프롬프트 (1학년 `cutscene-gemini-prompts.md` 의 공통 문구·인물 기준 그림 첨부 규칙을 그대로 붙여 쓴다)

각 줄 앞에 공통 문구(16:9 도트 장면, 실루엣 위주, 주인공 얼굴 안 보이게, 실제 구단·로고 금지)를 붙인다. 유니폼에는 글자·로고를 넣지 않는다.

- `pro_debut`: night baseball stadium under bright floodlights, a young shortstop in a plain navy uniform seen from behind walking from the dugout to home plate, crowd as soft color dots, warm light rim on the helmet, no logos.
- `pro_first_hit`: low angle at first base, the runner silhouette standing on the bag looking back toward the dugout where a teammate silhouette raises a baseball, evening sky.
- `pro_first_hr`: wide shot from behind home plate, the ball a tiny bright dot between two light towers, the batter silhouette mid-follow-through, the crowd blurred.
- `pro_callup`: dim minor-league locker room at night, a duffel bag on a bench, a phone screen glowing with one line of text (unreadable), the player seen from behind.
- `pro_ks_champion`: infield at night, silhouettes running toward the pitcher's mound, confetti falling in the floodlights, no faces.
- `pro_roy`: award ceremony stage with a single spotlight, a figure from behind holding a trophy, audience as dark rows.
- `side_chae_store`: late-night convenience store window from outside, two silhouettes at the window counter, steam rising from cup noodles, neon reflections on the glass.
- `y3_draft`: small Korean family restaurant at night, four silhouettes in front of a TV, the TV light on their backs, a dish towel falling.
- `y3_graduation`: empty high-school dirt field in winter afternoon, one figure in a school uniform in the middle seen from behind, long shadow.
