# 타자 머리 방향 고정·2컷 분할 v2 (2026-11-15)

요청: 첨부의 1·2·3번 머리만 이후 동작의 뒤쪽 머리 시점으로 맞추고 12동작을 2컷씩 크게 생성.
원본 첨부: /Users/ijaeu/Downloads/ChatGPT 이미지 2026년 11월 15일 오후 05_41_47.png

| 동작 번호 | 파일 | 실제 크기(px) | 명목 칸 크기(px) |
|---|---|---|---|
| 1-2 | docs/refs/sprites/batter_ink_swing_pair01_v2.png | 1774x887 | 887x887 |
| 3-4 | docs/refs/sprites/batter_ink_swing_pair02_v2.png | 1774x887 | 887x887 |
| 5-6 | docs/refs/sprites/batter_ink_swing_pair03_v2.png | 1774x887 | 887x887 |
| 7-8 | docs/refs/sprites/batter_ink_swing_pair04_v2.png | 1774x887 | 887x887 |
| 9-10 | docs/refs/sprites/batter_ink_swing_pair05_v2.png | 1774x887 | 887x887 |
| 11-12 | docs/refs/sprites/batter_ink_swing_pair06_v2.png | 1774x887 | 887x887 |

- 각 장은 2열×1행, 왼쪽→오른쪽. pair01부터 pair06 순으로 총 12동작.
- 각 파일과 같은 이름의 _prompt.md에 실제 프롬프트와 참조 기록.
- 내장 GPT image_gen 사용. 투명 배경 요청, PNG 알파 확인. 요청 3072×1536과 실제 반환 크기가 달라 표에 실측 기록. 확대·리사이즈 없음.
- 1~3은 헬멧 뒷면과 머리 뒤쪽이 보이도록 수정. 4~12는 첨부의 머리·몸 동작을 기준으로 다시 그림.
- 시각 확인: 전신·배트, 준비→컨택→팔로스루→마무리 순서, 처음 3칸의 옆얼굴 제거 확인.
- 한계: 생성형 재작화라 원본 대비 선·비율이 소폭 달라졌다. 발 위치와 장 사이 크기가 픽셀 단위로 엄격하게 같지는 않다. Claude가 게임 적용 전 지지발·회전축·공통 바닥선을 확인·정렬해야 한다. 정확한 발 고정 완료로 취급하지 않는다.
- 코드·게임 폴더 변경, 칸 정리·변환, 테스트·커밋·푸시 없음. 중단된 측면/완전 후면 시안은 채택하지 않음.
