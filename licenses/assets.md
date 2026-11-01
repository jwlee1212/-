# 에셋 출처와 상업 이용 가능 여부

CLAUDE.md 원칙 6: 외부·AI 생성 그림·소리를 넣을 때는 여기 기록한다. **기록 없는 에셋은 넣지 않는다.**

| 에셋 | 위치 | 출처·도구 | 라이선스 | 상업 이용 |
|---|---|---|---|---|
| Pretendard 글꼴 (Regular, Bold) | `godot/fonts/` | Kil Hyung-jin, github.com/orioncactus/pretendard | SIL OFL 1.1 (`licenses/Pretendard-OFL.txt`, `godot/fonts/Pretendard-OFL.txt`) | 가능 (글꼴 단독 판매 금지) |
| 효과음 전부 (투구·스윙·타격음·미트·함성) | `godot/scripts/audio/sound_synth.gd` | 외부 음원·AI 도구 없이 코드로 합성 (사인파·잡음·감쇠 곡선) | 이 프로젝트 자체 코드 | 가능 |
| 투수·야구장 그림 | `godot/scripts/batting/batting_view.gd` | 코드로 그린 단순 도형 (임시, G8 에서 타자와 같은 그림체로 교체) | 이 프로젝트 자체 코드 | 가능 |

| **(임시)** 인물 일러스트 9장 (주인공 그림은 2026-11-01 뺐다): 한소율(매니저 — 2026-11-01 새 그림 + 표정 3장 `manager_happy` `manager_pout` `manager_surprised`, 회색 배경과 ✦ 표시를 지웠다) · 감독 · 동기 · 코치 · 정다온(소꿉친구) · 선배 · 엄마 · 후배 · 서지안(방송부) | `godot/art/portraits/` 의 `manager` `coach` `buddy` `batting_coach` `neighbor` `senior` `mom` `junior` `reporter` `.png` | 유저가 **Google Gemini** 로 생성 (2026-10-11), 흰 배경만 지움 (선배는 가로로 긴 그림이라 인물만 남게 잘랐다). 그림 오른쪽 아래에 Gemini 표시(✦)가 남아 있다 | Gemini 이용 약관을 따른다 | **확인 필요 — 출시 전에 유저가 쓴 요금제의 약관(생성물의 상업 이용, 표시·워터마크 조건)을 확인해 여기 적는다** |

| **(임시)** 배경 2장: 학교 운동장 · 더그아웃 | `godot/art/backgrounds/field.png` `dugout.png` | 유저가 **Google Gemini** 로 생성 (2026-10-11), 그대로 넣음 | Gemini 이용 약관을 따른다 | **확인 필요** (위와 같음) |

| **(임시)** 인물 일러스트 1장: 라이벌 | `godot/art/portraits/rival.png` | 유저가 준 그림 (2026-10-11). 오른쪽 아래에 Gemini 표시(✦)가 있어 **Google Gemini** 생성으로 적는다 — 다른 도구면 고친다. 흰 배경만 지움 | Gemini 이용 약관을 따른다 | **확인 필요** (위와 같음) |

| **(임시)** 배경 3장: 관중석 · 집 앞 골목 · 우리 집 | `godot/art/backgrounds/stands.png` `street.png` `home.png` | 유저가 준 그림 (2026-10-12), 그대로 넣음. 골목·집 그림 오른쪽 아래에 Gemini 표시(✦)가 있어 셋 다 **Google Gemini** 생성으로 적는다 — 다른 도구면 고친다 | Gemini 이용 약관을 따른다 | **확인 필요** (위와 같음) |

| **(임시)** 배경 1장: 교실 | `godot/art/backgrounds/classroom.png` | 유저가 준 그림 (2026-10-12), 그대로 넣음. 오른쪽 아래에 Gemini 표시(✦)가 있어 **Google Gemini** 생성으로 적는다 — 다른 도구면 고친다 | Gemini 이용 약관을 따른다 | **확인 필요** (위와 같음) |

~~타자·투수 캐릭터 포즈 그림 (Google Gemini, `godot/art/characters/`)~~ — 2026-10-31 도트 확정으로 **지웠다** (게임에 들어 있지 않음. 커밋 `809d631` 까지의 기록에만 있다).

AI 생성 에셋: 위 인물 일러스트 11장, 배경 6장 (Google Gemini).

> 그림체는 원칙 6에 맞다 (대화 화면 일러스트는 애니메이션풍, 2026-10-11 결정). 남은 것은 상업 이용 약관 확인 하나다 — 확인 전까지는 임시로 본다.

## 인게임 도트 (2026-10-30 시험, 2026-10-31 확정)
| 파일 | 출처 | 상업 이용 |
|---|---|---|
| `godot/art/pixel/*.png` (경기장·타자·투수·포수·미트·수비 화면 선수) | 이 저장소의 `scripts/make_pixel_art.py` 가 코드로 찍어 만든 그림 (AI 생성 아님, 외부 에셋 없음) | 가능 (자체 제작) |
| `godot/art/pixel/training.png` (훈련 장면 5개: 티 배팅 · 헬스장 스쿼트 · 피칭머신 선구안 · 트랙 달리기 · 침대에서 잠, 2026-11-01) | 이 저장소의 `scripts/make_training_art.py` 가 코드로 찍어 만든 그림 (AI 생성 아님, 외부 에셋 없음) | 가능 (자체 제작) |
| `godot/art/pixel/minigame.png`, `minigame_bg.png` (입부 체력 테스트: 트랙·웨이트장·신호등 배경, 달리는 나·동기, 스쿼트, 반응 스윙, 신호등, 감독, 출발·결승선, 2026-11-01) | 이 저장소의 `scripts/make_minigame_art.py` 가 코드로 찍어 만든 그림 (AI 생성 아님, 외부 에셋 없음) | 가능 (자체 제작) |

## 배경음악 (2026-10-31)
| 파일 | 출처 | 상업 이용 |
|---|---|---|
| `godot/audio/bgm/lobby.wav` `home.wav` `game.wav` | 이 저장소의 `scripts/make_bgm.py` 가 사인파·펄스파·톱니파·잡음을 코드로 합성한 곡 (관중 목소리도 톱니파 + 모음 공명 필터로 합성, 녹음 없음) (작곡·합성 모두 자체, AI 생성 아님, 샘플·외부 음원 없음). 기존 곡의 멜로디를 옮기지 않았다 | 가능 (자체 제작) |
| 효과음 (타격음·미트·바람·함성) | `godot/scripts/audio/sound_synth.gd` 가 실행 중에 코드로 합성 (파일 없음, 녹음·AI 생성 아님) | 가능 (자체 제작) |
| 관중 함성 `godot/audio/sfx/cheer_small.wav` `cheer_big.wav` | `scripts/make_bgm.py` 가 목소리 수십 개(톱니파 + 모음 공명 필터)·휘파람·박수를 코드로 합성 (녹음·AI 생성 아님) | 가능 (자체 제작) |
| 심판 콜 `godot/audio/sfx/call_strike.wav` `call_strikeout.wav` | `scripts/make_bgm.py` 가 포먼트 합성(성대 펄스 + 공명 4개를 시간에 따라 옮김)으로 만든 목소리 (녹음·TTS·AI 생성 아님) | 가능 (자체 제작) |
