# 인물 일러스트 셀 채색

사용자가 고른 [하린 1번 시안](harin-style-01.png)의 선과 명암을 기준으로 삼았다. 기존 인물의 복장과 소품은 이전 게임 에셋을 참고했다. 하린은 1번 시안의 인물 디자인을 그대로 사용했다.

Codex의 GPT 이미지 생성으로 인물 14명과 소율의 표정 3종을 개별 생성했다. 투명 배경 원본은 [originals](originals/)에 보관하고, 게임용 PNG는 알파를 유지해 높이 1024px 이내로 줄였다.

## 기본 인물 프롬프트

공통 문장 뒤에 각 인물의 문장을 붙였다. 첫 참고 그림은 하린 1번 시안이고, 두 번째는 교체 전 같은 파일 이름의 인물 그림이다.

```text
Create one separate game dialogue portrait, 3:4 tall canvas, half-body to hips with complete head and uncropped elbows and accessories, natural Korean face and proportion. The FIRST image is the approved style reference: match its clean anime cel shading, fine dark linework, crisp broad shadow shapes, warm neutral skin, restrained highlights and subdued colors. The SECOND image is the existing character design for this portrait; preserve recognizable face, hair, costume and pose unless specifically instructed. Transparent background, clean cutout, no background motifs or glow, top of head roughly 8% down, bottom edge crops the torso cleanly. No text, signatures or branding.
```

| 파일 | 인물별 추가 문장 |
|---|---|
| `neighbor.png` | Harin, 16-year-old calm art student with long black hair and beige coat, pale blue shirt, dark diagonal drawing tube. Reproduce the exact face, hair, subtle smile, pose and outfit from the FIRST image. Do not use the second image's old design. |
| `manager.png` | Soyul, energetic 17-year-old baseball team data manager, messy black high ponytail, navy school cardigan with rolled sleeves, blue scorebook held to her chest, playful toothy grin. |
| `batting_coach.png` | Korean male high school batting coach in his early 30s, navy cap and tracksuit, cheerful and talkative, white baseball in one hand and stopwatch on neck. |
| `buddy.png` | Park Junsu, 16-year-old lively catcher, short cropped hair, sturdy build, navy catcher chest protector over white school jersey, bright grin, fist raised chest high. |
| `coach.png` | Coach Oh, reserved Korean man in his late 40s with a weathered face, navy cap and dark navy coaching jacket, arms folded, wry half-smile. |
| `jaehyuk.png` | Yun Jaehyeok, focused 17-year-old pitcher and future captain, short dark hair with side fringe, thin rectangular glasses, white jersey over navy compression shirt, worn brown glove at chest. |
| `junior.png` | Korean 16-year-old rookie infielder, youthful round face, messy dark hair under a navy cap, white jersey with navy trim, dark fielding glove, enthusiastic smile. |
| `minjae.png` | Jeong Minjae, Korean 17-year-old shortstop, angular face and messy black fringe, white jersey over navy undershirt, one hand holding the strap of a navy equipment bag, guarded quiet expression. |
| `mom.png` | Korean mother and neighborhood restaurant owner in her early 40s, soft round glasses, brown-black hair in a low loose bun, beige knit and muted checked apron, welcoming gentle smile. |
| `reporter.png` | Seo Jian, sharp 17-year-old Korean high school broadcasting club student, long chestnut hair, brown eyes, navy school blazer with no logo and burgundy armband, compact black camera held at chest, confident friendly expression. |
| `rival.png` | Kang Taeo, 16-year-old Korean rival shortstop, narrow dark eyes, straight black fringe under a deep navy cap, deep navy team jersey, black batting gloves, wood bat angled over one shoulder, calm unsmiling face. |
| `scout.png` | Na Seongman, discerning Korean male baseball scouting team leader in his late 40s, short brushed-back salt-and-pepper hair, navy warmup jacket with muted gold piping, pen and clipboard, confident slight smile. |
| `senior.png` | Han Doyun, 18-year-old Korean captain, short dark buzz cut and thick eyebrows, white jersey over navy undershirt, arms folded, steady serious expression. |
| `seongho.png` | Kim Seongho, 18-year-old Korean reserve outfielder, slightly shaggy dark fringe, navy warmup jacket over white jersey, one hand at the back of his neck, faint bashful smile. |

## 소율 표정 프롬프트

첫 참고 그림은 새 소율 기본 원본이다. 아래 공통 문장 뒤에 각 표정의 문장을 붙였다.

```text
Create one dialogue sprite expression edit of the FIRST image, preserving her exact identity and style. Lock canvas dimensions, position, head angle, silhouette, hair, pose, fingers, clothes, scorebook, colors, and lighting; keep true transparent background. Change ONLY her eyebrows, eyes and mouth to this expression:
```

| 파일 | 표정별 추가 문장 |
|---|---|
| `manager_happy.png` | a very happy laughing grin with a bigger open smile, eyes joyfully squeezed into crescents, playful energy. |
| `manager_pout.png` | a lightly sulky playful pout, lips pursed to one side, brows gently lowered, eyes slightly narrowed. |
| `manager_surprised.png` | a spontaneous surprised reaction, small open O-shaped mouth, visibly widened eyes, raised eyebrows. |
