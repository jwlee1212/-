# SD 타자 시트 1차 시안

내장 이미지 생성 도구로 제작. 자세와 시점은 main의 ingame_hitter_source.png, 비율과 색은 직전 SD 타자 콘셉트를 참조했다. 원본과 비교하면 준비 자세의 앞뒤 발 겹침이 줄어들고 마무리 자세의 뒷발 피벗이 약하다. 게임에 연결하기 전 이 두 자세와 컨택 위치를 확인해야 한다.

```text
Create a fresh transparent 4 columns x 3 rows animation sprite sheet. Reference 1 is ONLY for game camera and pose silhouette: fixed camera behind catcher, right handed batter in side profile looking screen right, face obscured, back turns toward us after impact. DO NOT trace reference anatomy or preserve its frame order. Reference 2 LEFT blue character is design reference ONLY: use its big helmet and ultra short legs, exaggerate to genuine 2.3-head chibi. Navy clean thick outlines, flat cel shading, same white and royal blue uniform. Camera of ref 1 takes absolute priority over ref 2. Same character scale and ground anchor in every equally sized cell. Strict frame order:
row 1: neutral bat diagonally up-left / nearly same breathing pose / coiled with bat up-left / bat still up-left, front foot lifted slightly (absolutely no swing yet).
row 2: front foot lands, bat remains up-left / hips begin turning, bat still back / impact, back partly visible, barrel points screen right waist-high / extension, both arms reach screen right at chest level.
row 3: bat high screen left and back visible / bat wrapping over shoulder / finish with bat across back of shoulders / profile facing right holding bat horizontally forward with spaced hands for bunt.
Very large helmets and very short bodies in every cell, full body and bat, transparent gutters, no text, no grid lines, no effects, no shadows, no ground.
```
