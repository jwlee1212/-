# 일러스트·배경 자리

대화 화면(`scripts/screens/dialogue_view.gd`)은 아래 경로에 PNG 가 있으면 자동으로 쓰고, 없으면 자리(테두리 상자 + 이름 + 경로)만 보여 준다.

- 원칙 6: 대화 화면 일러스트는 애니메이션풍 반신, 인게임 캐릭터(`characters/`)는 SD 카툰풍. 실사·사실적 AI 얼굴 금지. AI 로 만든 그림은 `licenses/assets.md` 에 도구와 상업 이용 여부를 적는다
- 일러스트: 세로로 긴 그림(대략 3:4, 높이 1000px 안팎, 배경 투명). 화면 왼쪽에 서고 아래쪽은 대사창에 가려진다
- 배경: 가로 그림(844×390 비율 이상, 1688×780 권장). 화면을 덮도록 잘린다

## 인물 일러스트 `portraits/<id>.png`

| 파일 | 인물 |
|---|---|
| `player.png` | 주인공 (내 대사 때 오른쪽에 나온다) — **임시 그림이 들어 있다** |
| `coach.png` | 감독 · (이름은 커리어마다 뽑음) — **임시 그림이 들어 있다** |
| `batting_coach.png` | 코치 (타격 코치) · (이름은 커리어마다 뽑음) — **임시 그림이 들어 있다** |
| `senior.png` | 선배 (3학년 주장) · (이름은 커리어마다 뽑음) — **임시 그림이 들어 있다** |
| `junior.png` | 후배 · (이름은 커리어마다 뽑음) — **임시 그림이 들어 있다** |
| `mom.png` | 엄마 · 엄마 — **임시 그림이 들어 있다** |
| `buddy.png` | 동기 · (이름은 커리어마다 뽑음) — **임시 그림이 들어 있다** |
| `rival.png` | 라이벌 · (이름은 커리어마다 뽑음) — **임시 그림이 들어 있다** |
| `scout.png` | 스카우트 · (이름은 커리어마다 뽑음) |
| `manager.png` | 매니저 · 한소율 — **임시 그림이 들어 있다** |
| `neighbor.png` | 소꿉친구 · 정다온 — **임시 그림이 들어 있다** |
| `reporter.png` | 방송부 · 서지안 — **임시 그림이 들어 있다** |
| `agent.png` | 에이전트라는 남자 (조연) |
| `butcher.png` | 동네 정육점 사장님 (조연) |
| `college_coach.png` | 대학 감독 (조연) |
| `local_reporter.png` | 지역 신문 기자 (조연) |

## 배경 `backgrounds/<id>.png`

| 파일 | 장소 |
|---|---|
| `field.png` | 학교 운동장 — **임시 그림이 들어 있다** |
| `dugout.png` | 더그아웃 — **임시 그림이 들어 있다** |
| `home.png` | 우리 집 — **임시 그림이 들어 있다** |
| `stadium.png` | 야구장 |
| `stands.png` | 관중석 — **임시 그림이 들어 있다** |
| `bus.png` | 원정 버스 |
| `street.png` | 집 앞 골목 — **임시 그림이 들어 있다** |
| `cafe.png` | 카페 |
| `studio.png` | 방송부실 |
| `classroom.png` | 교실 — **임시 그림이 들어 있다** |
| `phone.png` | 휴대폰 |
| `dorm.png` | 합숙소 |
| `gym.png` | 웨이트장 |

## 인게임 캐릭터 `characters/batter/` (타자 뒷모습)

`scripts/characters/batter_rig.gd` 가 이 파츠들을 뼈대에 붙여 움직인다. 관절 자리(그림 픽셀)는 그 파일의 `PARTS` 에 있다 — 그림을 바꾸면 거기 숫자도 맞춘다.

| 파일 | 파츠 |
|---|---|
| `head.png` | 헬멧 쓴 머리 (목 포함) |
| `torso.png` | 상의 (짧은 소매 포함) |
| `pelvis.png` | 벨트 + 하의 윗부분 |
| `upper_arm.png` `forearm.png` | 위팔(소매) · 아래팔 — 두 팔이 같은 그림을 쓴다 |
| `glove_top.png` `glove_bottom.png` | 배트를 쥔 장갑 낀 주먹 |
| `leg_back.png` `leg_front.png` | 굽힌 다리 (허벅지~스파이크 한 덩어리) |
| `bat.png` | 나무 배트 |
| `source/` | 자르기 전 원본: `parts_sheet.png` `arms_sheet.png` `stance_reference.png`(자세 참고). `scripts/cut_batter_parts.py` 로 다시 자른다 |

새로 뽑을 때: 초록(#00FF00) 단색 배경, 조각끼리 닿지 않게, 관절 끝은 둥글고 넉넉하게, 유니폼은 밝은 회색 단색.
