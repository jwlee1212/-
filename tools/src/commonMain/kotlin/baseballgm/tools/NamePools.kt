package baseballgm.tools

/**
 * 이름 생성용 데이터 (docs/03).
 *
 * 밸런스 수치가 아니라 사전 데이터라서 `balance.json` 이 아니라 코드에 둔다. 손으로 늘리기 쉽게
 * 목록만 모아 두었다.
 */
object NamePools {

    /** 성씨와 대략적인 인구 비율. 합이 1이 아니어도 가중치로 쓰인다. */
    val surnames: Map<String, Double> = mapOf(
        "김" to 21.5, "이" to 14.7, "박" to 8.4, "최" to 4.7, "정" to 4.3,
        "강" to 2.4, "조" to 2.1, "윤" to 2.1, "장" to 2.0, "임" to 1.7,
        "한" to 1.5, "오" to 1.4, "서" to 1.4, "신" to 1.3, "권" to 1.2,
        "황" to 1.2, "안" to 1.2, "송" to 1.2, "전" to 1.1, "홍" to 1.0,
        "유" to 1.0, "고" to 0.9, "문" to 0.9, "양" to 0.8, "손" to 0.8,
        "배" to 0.7, "백" to 0.7, "허" to 0.7, "노" to 0.7, "심" to 0.6,
        "하" to 0.5, "곽" to 0.5, "성" to 0.5, "차" to 0.5, "주" to 0.5,
        "우" to 0.4, "구" to 0.4, "민" to 0.3, "류" to 0.3, "나" to 0.3,
        "진" to 0.3, "지" to 0.3, "엄" to 0.2, "채" to 0.2, "원" to 0.2,
        "천" to 0.2, "방" to 0.2, "공" to 0.1, "현" to 0.1, "함" to 0.1,
        "변" to 0.1, "염" to 0.1, "여" to 0.1, "추" to 0.1, "도" to 0.1,
        "소" to 0.1, "석" to 0.1, "선우" to 0.03, "남궁" to 0.03, "제갈" to 0.01,
    )

    /** 1986~1997년생에게 흔한 이름 앞 음절. */
    val olderFirstSyllables: List<String> = listOf(
        "성", "동", "재", "상", "승", "태", "준", "진", "종", "현",
        "창", "영", "기", "병", "정", "용", "철", "광", "규", "대",
        "민", "형", "경", "석", "우", "호", "원", "세", "건", "근",
    )

    /** 1998~2008년생에게 흔한 이름 앞 음절. */
    val youngerFirstSyllables: List<String> = listOf(
        "준", "민", "지", "서", "예", "도", "하", "시", "주", "윤",
        "건", "현", "수", "재", "승", "우", "태", "찬", "규", "연",
        "은", "정", "성", "진", "영", "다", "한", "선", "강", "율",
    )

    /** 이름 뒷 음절. */
    val secondSyllables: List<String> = listOf(
        "우", "호", "준", "석", "민", "수", "진", "성", "규", "한",
        "영", "재", "환", "혁", "원", "빈", "철", "식", "근", "열",
        "찬", "훈", "현", "기", "범", "결", "휘", "겸", "형", "택",
        "윤", "건", "율", "람", "연", "후", "온", "담", "결", "산",
    )

    /**
     * 금지 이름 (docs/03). 실존 유명 선수 이름이 우연히 나오지 않게 거른다.
     * 완전하지 않으므로 발견할 때마다 추가한다.
     */
    val bannedNames: Set<String> = setOf(
        "이승엽", "박찬호", "류현진", "김광현", "양현종", "오승환", "강정호", "이대호", "최정", "김하성",
        "이정후", "추신수", "박병호", "손아섭", "나성범", "구자욱", "김현수", "정우람", "안우진", "원태인",
        "고우석", "강백호", "이종범", "선동열", "최동원", "장종훈", "양준혁", "심정수", "이병규", "정근우",
        "이용규", "김태균", "홍성흔", "박용택", "김재환", "최형우", "황재균", "서건창", "김재호", "오재원",
        "김선빈", "박해민", "전준우", "민병헌", "채태인", "이범호", "김주찬", "송승준", "장원준", "윤석민",
        "임창용", "조용준", "정민철", "구대성", "박경완", "진갑용", "홍창기", "문동주", "김도영", "노시환",
    )

    /** 외국인 선수: 영문 이름과 한글 등록명 쌍. */
    val foreignNames: List<Pair<String, String>> = listOf(
        "Miguel Santana" to "산타나", "Carlos Reyes" to "레이예스", "David Coleman" to "콜먼",
        "Ryan Whitaker" to "휘태커", "Luis Vargas" to "바르가스", "Anthony Brooks" to "브룩스",
        "Jose Medina" to "메디나", "Tyler Bennett" to "베넷", "Rafael Duran" to "두란",
        "Kevin Mercer" to "머서", "Victor Salas" to "살라스", "Brandon Ellis" to "엘리스",
        "Jorge Peralta" to "페랄타", "Austin Reed" to "리드", "Hector Rivas" to "리바스",
        "Cody Harrison" to "해리슨", "Pedro Nunez" to "누네스", "Jake Lawson" to "로슨",
        "Marcos Tejada" to "테하다", "Eric Donovan" to "도노반", "Wilson Cabrera" to "카브레라",
        "Shane Murphy" to "머피", "Julio Espinoza" to "에스피노사", "Nathan Cole" to "콜",
        "Felix Ortega" to "오르테가", "Derek Sanders" to "샌더스", "Ramon Castillo" to "카스티요",
        "Blake Turner" to "터너", "Omar Beltran" to "벨트란", "Chris Vaughn" to "본",
        "Elias Moreno" to "모레노", "Jason Pierce" to "피어스", "Andres Guzman" to "구스만",
        "Trevor Hayes" to "헤이스", "Manuel Rosario" to "로사리오", "Logan Pratt" to "프랫",
        "Diego Ramos" to "라모스", "Sean Walters" to "월터스", "Alexis Herrera" to "에레라",
        "Mitchell Doyle" to "도일", "Ivan Solano" to "솔라노", "Grant Foster" to "포스터",
        "Yunior Paredes" to "파레데스", "Curtis Blake" to "블레이크", "Emilio Vega" to "베가",
        "Patrick Sullivan" to "설리번", "Nelson Marte" to "마르테", "Dustin Klein" to "클라인",
        "Gabriel Nieves" to "니에베스", "Travis Mayfield" to "메이필드", "Cesar Aguilar" to "아길라르",
        "Jordan Kemp" to "켐프", "Alberto Cruz" to "크루스", "Wesley Barton" to "바턴",
        "Dario Leon" to "레온", "Corey Shelton" to "셸턴", "Luis Zapata" to "사파타",
        "Bryan Nolan" to "놀란", "Efrain Campos" to "캄포스", "Zachary Hobbs" to "홉스",
        "Angel Mejia" to "메히아", "Preston Gates" to "게이츠", "Roberto Silva" to "실바",
        "Casey Lindberg" to "린드버그", "Jhon Quintero" to "킨테로", "Adam Fletcher" to "플레처",
        "Yordan Batista" to "바티스타", "Micah Sorensen" to "소렌슨", "Eduardo Lara" to "라라",
        "Garrett Boone" to "분", "Wilfredo Polanco" to "폴란코", "Colton Reeves" to "리브스",
        "Alfredo Serrano" to "세라노", "Ethan Marsh" to "마시", "Randy Alvarado" to "알바라도",
        "Damian Kirby" to "커비", "Junior Fermin" to "페르민", "Levi Strand" to "스트랜드",
        "Oscar Pineda" to "피네다", "Brett Calloway" to "캘러웨이", "Hugo Arteaga" to "아르테아가",
        "Spencer Yates" to "예이츠", "Marcelo Bravo" to "브라보", "Kyle Ferrell" to "페럴",
        "Jefferson Lugo" to "루고", "Drew Hollister" to "홀리스터", "Enrique Valdez" to "발데스",
    )

    /** 감독·코치·스태프에 쓸 조금 더 윗세대 이름 앞 음절. */
    /**
     * 아마추어 선수의 학교 이름 조각 (docs/10 드래프트 풀).
     * 실존 학교와 겹치지 않도록 지어낸 낱말만 쓴다.
     */
    val schoolPrefixes: List<String> = listOf(
        "청림", "남도", "한빛", "백산", "은하", "새벽", "태양", "금강", "덕원", "연무",
        "성진", "가온", "예성", "우현", "동백", "청운", "한솔", "미래", "정암", "호림",
        "강림", "수림", "명성", "세림", "도원", "신천", "백록", "청해", "만경", "월산",
    )

    val staffFirstSyllables: List<String> = listOf(
        "종", "철", "진", "영", "성", "기", "병", "만", "광", "용",
        "재", "동", "상", "태", "경", "인", "정", "형", "호", "근",
    )
}
