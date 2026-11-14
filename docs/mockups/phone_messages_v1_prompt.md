# 폰 메시지 목록 UI 시안 v1

- 제작일: 2026-11-14
- 도구: 내장 GPT 이미지 생성
- 사용자 요청: 기존 일러스트를 메시지 프로필 사진으로 활용
- 프로필 기준: docs/refs/portraits/originals/manager.png, neighbor.png, buddy.png, mom.png
- UI·골목 배경 기준: docs/mockups/career_street_home_v1.png
- 메시지 미리보기: config/content.json의 기존 메시지 문장. 함께 노출되는 인물과 시점은 검토용 예시.

Use case: ui-mockup
Create ONE landscape in-game PHONE MESSAGES LIST screen mockup for Korean baseball career game FIRST PICK, aspect ratio 2.16:1, equivalent to 844×390 logical pixels.
REFERENCE ROLES, IMPORTANT:
Image 1 = existing SOYUL dialogue illustration (black messy high ponytail, toothy grin, navy cardigan, blue scorebook). Image 2 = HARIN (long straight black hair, beige coat, drawing tube). Image 3 = PARK JUNSU (short cropped hair, cheerful boy in navy catcher equipment). Image 4 = MOM (round glasses, low bun, beige clothes and checked apron). These are EXISTING ART ASSETS: reuse their exact recognizable faces, original anime linework, colors and hair as cropped profile thumbnails. Do not redesign, redraw them in SD style, swap identities or substitute generic avatars. We specifically want to see existing portraits used as message profile pictures.
Image 5 = approved neighborhood HOME UI reference, supplies only game's cream/navy/burnt-orange visual system and underlying neighborhood setting.
COMPOSITION: show actual landscape game viewport, with neighborhood background greatly muted by a translucent navy-gray modal scrim, no duplicated readable background UI. LEFT ~45% displays the existing Soyul illustration from Image 1 large as a selected-contact portrait, bust-to-hips, same grin, same ponytail and scorebook. Reuse portrait as is, no new pose. Small understated ivory nameplate beneath "소율". The existing anime portrait deliberately retains its original style. No speech bubble outside phone.
RIGHT a prominent upright in-game phone panel, about 35% canvas width and 88% height, positioned with generous margins and whole body inside image. Slim matte navy frame with modest rounded corners, warm ivory screen. No real device branding, no realistic hardware rendering, no oversized metallic bevels, no hands holding phone. This is an actual in-game phone overlay, NOT a photograph of a mobile phone.
PHONE UI: small top status line "1학년 · 봄" and simple battery indicator. Header larger "메시지", top right simple close X. Under header subtle caption "안 읽은 메시지 2".
Four roomy readable chat list rows vertically, each has circular avatar 44 logical pixels or proportionally suitable size, NAME in navy bold, one smaller single-line clipped preview below name, right small time. Thin separators. Exact identity mapping:
ROW1 avatar CROPPED FACE from Image1 Soyul, name "소율", preview "네 폴더 만들었어. 졸업할 때까지 다 찍을 거야" clipped naturally if needed, time "방금", small burnt-orange unread badge "1". Pale dusty-blue selected row.
ROW2 avatar CROPPED FACE from Image2 Harin, name "하린", preview "야구부 들어갔어?", time "조금 전", small orange unread badge "1".
ROW3 avatar CROPPED FACE from Image3 Junsu, name "박준수", preview "혼났어?", time "어제", no unread badge.
ROW4 avatar CROPPED FACE from Image4 Mom, name "엄마", preview "주말엔 좀 쉬어. 야구는 안 도망간다." clipped to one line, time "어제", no unread badge.
Do not generate extra people or contact rows. Heads tightly but comfortably framed in circular crops on pale neutral backgrounds; all four identities clearly distinct and recognizable at thumbnail scale.
BOTTOM inside phone fixed three-tab navigation with clean navy outline icons and exact labels "메시지", "뉴스", "연락처". "메시지" selected navy with small burnt-orange underline. No game-level bottom tabs. Keep phone text legible and aligned, no tiny clutter. Prioritize portraits and chat row hierarchy.
No new story dialogue, no keyboard or reply input on list, no chat bubble conversation in this screen, no invented NPCs, no currency, ads, premium gems, quest badge, social-media brands, logos or watermark. Outside phone no other controls except the selected-contact nameplate. Warm restrained cream paper and navy UI matching reference5, modest flat surfaces, no glossy 3D. All four message previews come from existing game copy; render exact text or sensible ellipsis, not invented prose.
