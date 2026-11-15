# 타자 타격 시트 v1 프롬프트

도구: 내장 GPT image_gen, transparent_background=true.

```text
Use case: style-transfer
Asset type: 2D baseball batter 12-frame swing animation sprite sheet.
Input image 1: character identity and art style reference ONLY. Input image 2: exact pose sequence, handedness, camera viewpoint and bat path reference ONLY.
Redraw all TWELVE frames of input image 2 as original high-resolution inked illustrations in the style of image 1. Match the boy's black tousled hair, navy headwear, ivory baseball uniform, navy undersleeves, navy belt and tall socks, ivory sneakers, warm skin, bold dark navy hand-inked contours, subtle warm paperlike texture and gentle cel shading. Use a plain navy batting helmet matching the source action silhouette. Plain wooden bat held with BOTH hands; anatomically consistent grip, same bat length in every cell. Keep image 1's body proportions and same character throughout, no realistic adult anatomy, no tiny baby chibi.
Reformat the one-row 12-frame reference into a precise FOUR COLUMNS by THREE ROWS sheet, 12 equal square cells, reading LEFT TO RIGHT then TOP TO BOTTOM. Requested canvas 2048x1536, nominal cell 512x512. Full body and entire bat visible in every cell with generous margins. Consistent scale, fixed camera, same local ground baseline and foot anchor through the swing. Transparent alpha background. No scenery, shadows, labels, text, numbers, logos, gridlines, motion trails or separate baseballs.
Critical: follow the supplied action camera, which looks at the batter from behind and slightly to the side, not a front portrait, and do not turn the character to face the viewer. Eyes when visible track the incoming pitch in source direction; focused neutral closed mouth, no smile. In follow-through the back and rear helmet are visible exactly as source.
Map the original strip cells 1-12 to grid cells 1-12:
1: bent knees, loaded batting stance, hands near rear shoulder, bat diagonally upward behind head.
2: small weight shift/load, keep nearly same stance.
3: stride and begin swing, hands starting forward, bat still back and high.
4: strong torso rotation through contact, back turns toward camera, bat sweeps forward and downward across waist level.
5: follow-through rises, arms extend and bat points diagonally upper left, body pivoting onto rear toe.
6: continued high follow-through with extended arms.
7: hands wrap around shoulder, bat begins folding around upper back.
8: bat lies nearly horizontally behind shoulders, back faces camera.
9: slight settling of same wrapped finish.
10: balanced wrapped finish, rear foot on toe.
11: small recovery in shoulders and bat.
12: final held follow-through, bat behind upper shoulders.
Preserve subtle repeated recovery poses as in source rather than inventing new actions. Prioritize faithfully reproducing each corresponding source pose, same handedness, stance, camera and bat orientation. No pixel art, no 3D, no photorealism.
```
