# 에셋 출처와 상업 이용 가능 여부

CLAUDE.md 원칙 6: 외부·AI 생성 그림·소리를 넣을 때는 여기 기록한다. **기록 없는 에셋은 넣지 않는다.**

| 에셋 | 위치 | 출처·도구 | 라이선스 | 상업 이용 |
|---|---|---|---|---|
| Pretendard 글꼴 (Regular, Bold) | `godot/fonts/` | Kil Hyung-jin, github.com/orioncactus/pretendard | SIL OFL 1.1 (`licenses/Pretendard-OFL.txt`, `godot/fonts/Pretendard-OFL.txt`) | 가능 (글꼴 단독 판매 금지) |
| 효과음 전부 (투구·스윙·타격음·미트·함성) | `godot/scripts/audio/sound_synth.gd` | 외부 음원·AI 도구 없이 코드로 합성 (사인파·잡음·감쇠 곡선) | 이 프로젝트 자체 코드 | 가능 |
| 투수·야구장 그림 | `godot/scripts/batting/batting_view.gd` | 코드로 그린 단순 도형 (임시, G8 에서 타자와 같은 그림체로 교체) | 이 프로젝트 자체 코드 | 가능 |

| **(임시)** 인물 일러스트 10장: 주인공 · 한소율(매니저) · 감독 · 동기 · 코치 · 정다온(소꿉친구) · 선배 · 엄마 · 후배 · 서지안(방송부) | `godot/art/portraits/` 의 `player` `manager` `coach` `buddy` `batting_coach` `neighbor` `senior` `mom` `junior` `reporter` `.png` | 유저가 **Google Gemini** 로 생성 (2026-10-11), 흰 배경만 지움 (선배는 가로로 긴 그림이라 인물만 남게 잘랐다). 그림 오른쪽 아래에 Gemini 표시(✦)가 남아 있다 | Gemini 이용 약관을 따른다 | **확인 필요 — 출시 전에 유저가 쓴 요금제의 약관(생성물의 상업 이용, 표시·워터마크 조건)을 확인해 여기 적는다** |

| **(임시)** 배경 2장: 학교 운동장 · 더그아웃 | `godot/art/backgrounds/field.png` `dugout.png` | 유저가 **Google Gemini** 로 생성 (2026-10-11), 그대로 넣음 | Gemini 이용 약관을 따른다 | **확인 필요** (위와 같음) |

| **(임시)** 인물 일러스트 1장: 라이벌 | `godot/art/portraits/rival.png` | 유저가 준 그림 (2026-10-11). 오른쪽 아래에 Gemini 표시(✦)가 있어 **Google Gemini** 생성으로 적는다 — 다른 도구면 고친다. 흰 배경만 지움 | Gemini 이용 약관을 따른다 | **확인 필요** (위와 같음) |

| **(임시)** 배경 3장: 관중석 · 집 앞 골목 · 우리 집 | `godot/art/backgrounds/stands.png` `street.png` `home.png` | 유저가 준 그림 (2026-10-12), 그대로 넣음. 골목·집 그림 오른쪽 아래에 Gemini 표시(✦)가 있어 셋 다 **Google Gemini** 생성으로 적는다 — 다른 도구면 고친다 | Gemini 이용 약관을 따른다 | **확인 필요** (위와 같음) |

| **(임시)** 배경 1장: 교실 | `godot/art/backgrounds/classroom.png` | 유저가 준 그림 (2026-10-12), 그대로 넣음. 오른쪽 아래에 Gemini 표시(✦)가 있어 **Google Gemini** 생성으로 적는다 — 다른 도구면 고친다 | Gemini 이용 약관을 따른다 | **확인 필요** (위와 같음) |

| 타자 캐릭터 포즈 그림 4장 (준비·힘 모으기·맞는 순간·팔로스루) + 초록 배경 원본 | `godot/art/characters/batter/*.png`, `source/` | 유저가 **Google Gemini** 로 생성 (2026-10-12, 요금제는 유저 말로 'Gemini Plus'). 초록 배경만 뺐다 (`scripts/cut_character_poses.py`). 원본 오른쪽 아래의 Gemini 표시(✦)는 잘라 낸 그림에는 없다 (원본에는 남아 있다) | Gemini 이용 약관을 따른다 | **확인 필요** (위와 같음) |

| 투수 캐릭터 포즈 그림 5장 (세트·다리 들기·몸 돌려 모으기·공 놓기·팔로스루) + 초록 배경 원본 | `godot/art/characters/pitcher/*.png`, `source/` | 유저가 **Google Gemini** 로 생성 (2026-10-13). 초록 배경만 뺐다 (`scripts/cut_character_poses.py`). Gemini 표시(✦)는 잘라 낸 그림에는 없다 (원본에는 남아 있다) | Gemini 이용 약관을 따른다 | **확인 필요** (위와 같음) |

AI 생성 에셋: 위 인물 일러스트 11장, 배경 6장, 타자·투수 캐릭터 포즈 그림 (Google Gemini).

> 그림체는 원칙 6에 맞다 (대화 화면 일러스트는 애니메이션풍, 2026-10-11 결정). 남은 것은 상업 이용 약관 확인 하나다 — 확인 전까지는 임시로 본다.
