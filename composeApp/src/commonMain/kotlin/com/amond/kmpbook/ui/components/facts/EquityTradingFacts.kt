package com.amond.kmpbook.ui.components.facts

/**
 * Completed historical events are favored over prices, rankings, and other facts that quickly expire.
 * Each catalog is independently sourced and interleaved to keep consecutive loading messages varied.
 */
internal val EQUITY_TRADING_FACTS: List<String> = interleaveEquityFacts(
    marketOriginsFacts(),
    marketDramaFacts(),
    koreanCompanyFacts(),
    japaneseCompanyFacts(),
    semiconductorCompanyFacts(),
    globalTechnologyFacts(),
    consumerBrandFacts(),
    industrialCompanyFacts(),
    unusualStockFacts(),
)

private fun interleaveEquityFacts(vararg catalogs: List<String>): List<String> =
    buildList(capacity = catalogs.sumOf(List<String>::size)) {
        repeat(catalogs.maxOf(List<String>::size)) { index ->
            catalogs.forEach { catalog ->
                catalog.getOrNull(index)?.let(::add)
            }
        }
    }

/*
 * Primary sources:
 * https://www.euronext.com/en/about/media/bell-ceremony-archive/415th-anniversary
 * https://www.amsterdam.nl/stadsarchief/stukken/geld/onbekend-voc-aandeel/
 * https://www.amsterdam.nl/stadsarchief/stukken/geld/voc-aandeel-wees/
 * https://live.euronext.com/en/news/exchange-drumming-historical-traditions
 * https://www.beursgeschiedenis.nl/en/the-story/
 * https://www.beursgeschiedenis.nl/en/moment/first-rules-for-share-trading/
 * https://www.beursgeschiedenis.nl/en/moment/exchange-drumming/
 * https://www.londonstockexchange.com/discover/lseg/our-history
 * https://www.jpx.co.jp/english/corporate/about-jpx/history/01-02.html
 * https://www.jpx.co.jp/english/corporate/about-jpx/history/02-01.html
 * https://beta.nyse.com/history-of-nyse
 * https://www.nyse.com/equities/tom-farley-testimony
 * https://www.nyse.com/american-stock-exchange
 * https://blogs.loc.gov/inside_adams/2022/11/ticker-tapes-parades/
 * https://www.spglobal.com/spdji/en/education/article/130-years-of-stewardship-the-evolution-of-the-dow/
 * https://www.spglobal.com/spdji/en/research-insights/index-literacy/the-sp-500-and-the-dow/
 * https://ir.nasdaq.com/news-releases/news-release-details/nasdaq-celebrates-50-years-innovation
 * https://www.cboe.com/50th_anniversary
 * https://www.ssga.com/us/en/individual/etfs/state-street-spdr-s-p-500-etf-trust-spy
 */
private fun marketOriginsFacts(): List<String> = listOf(
    "1602년 네덜란드 동인도회사 주식은 암스테르담 공개시장의 출발점이 됐습니다.",
    "1602년 VOC 암스테르담 청약부에는 장인과 가사노동자까지 1,143명이 이름을 올렸습니다.",
    "암스테르담은 1610년부터 보유하지 않은 주식을 파는 거래를 금지하려 했습니다.",
    "1623년 서인도회사는 동인도회사에 이어 암스테르담의 두 번째 상장사가 됐습니다.",
    "1688년 출간된 ‘혼돈 속의 혼돈’은 현존하는 가장 오래된 주식거래 책입니다.",
    "전설에 따르면 1622년 거래소 폭파를 막은 소년은 안에서 북 치기를 소원했습니다.",
    "런던 증시의 뿌리는 1698년 조너선 커피하우스에서 발행한 가격표입니다.",
    "초기 런던 중개인들은 거래소 건물 대신 커피하우스에서 주식을 사고팔았습니다.",
    "1773년 런던 중개인들은 새 모임 장소를 ‘증권거래소’라고 부르기 시작했습니다.",
    "1802년 런던 거래소에서는 회원이 책을 청하면 위층 직원이 아래로 던져줬습니다.",
    "1730년 공인된 오사카 도지마 쌀시장은 제도화된 선물시장의 선구자였습니다.",
    "1792년 뉴욕 중개인 24명은 버튼우드 협정으로 거래 규칙을 약속했습니다.",
    "버튼우드 협정은 경쟁 중개인보다 서로를 우선하고 고정 수수료를 받게 했습니다.",
    "뉴욕은행은 버튼우드 협정 무렵 거래된 초기 뉴욕 종목 중 하나였습니다.",
    "1817년 뉴욕 중개인들은 하루 두 번 지정된 의자에 앉아 거래했습니다.",
    "NYSE 회원권을 뜻한 ‘좌석’은 초기 중개인에게 배정된 실제 의자에서 나왔습니다.",
    "1867년 주식 시세기는 월스트리트 가격을 미국 각지로 빠르게 전했습니다.",
    "NYSE 거래장에는 1878년 전화가 설치돼 주문 전달 방식이 달라졌습니다.",
    "NYSE의 개장 신호는 1870년대에는 종이 아니라 중국식 징이었습니다.",
    "1903년 새 NYSE 건물이 열리면서 전기식 황동 종이 징을 대신했습니다.",
    "현재의 NYSE 본관은 1903년 더 넓은 거래장을 갖추고 문을 열었습니다.",
    "1903년 NYSE에는 얼음 450톤과 맞먹는 냉각 설비가 설치됐습니다.",
    "미국증권거래소의 전신 중개인들은 건물 밖 연석에서 종목을 거래했습니다.",
    "주식 시세기 종이테이프는 뉴욕의 유명한 환영 퍼레이드 재료가 됐습니다.",
    "도쿄증권거래소의 전신은 1878년 5월 세워져 6월 1일 거래를 시작했습니다.",
    "1880년대 도쿄 거래장 직원들은 서양식 정장 대신 기모노를 입었습니다.",
    "1890년대 도쿄에서 가장 활발히 거래된 종목은 거래소 자신의 주식이었습니다.",
    "1884년 찰스 다우는 철도회사 중심의 첫 주가평균을 발표했습니다.",
    "1896년 최초 다우산업지수는 철도주를 뺀 산업회사 12곳으로 출발했습니다.",
    "첫 다우산업지수 값 40.94는 종목 가격을 연필과 종이로 계산한 결과였습니다.",
    "다우산업지수는 1916년 20종목, 1928년 30종목 체제로 커졌습니다.",
    "공동창업자 에드워드 존스는 초기 다우지수 계산에 직접 관여하지 않았습니다.",
    "S&P 지수의 전신은 1923년 233개 종목을 매주 계산하며 시작했습니다.",
    "S&P는 1926년 90개 종목의 지수를 매일 계산하기 시작했습니다.",
    "S&P 500 지수는 컴퓨터 계산을 활용해 1957년 3월 출범했습니다.",
    "나스닥은 1971년 2월 8일 전자 호가 시스템으로 첫날을 열었습니다.",
    "NASDAQ은 ‘미국증권업협회 자동호가’의 영어 머리글자에서 나온 이름입니다.",
    "나스닥은 처음부터 물리적인 거래장 없이 컴퓨터 화면으로 호가를 전했습니다.",
    "초기 나스닥은 자동 체결 거래소가 아니라 전자식 호가 전달망에 가까웠습니다.",
    "CBOE는 1973년 첫날 16개 종목의 콜옵션 911계약을 처리했습니다.",
    "SPY는 1993년 1월 미국에서 처음 상장된 ETF로 기록됐습니다.",
    "제2차 세계대전 중 여성들은 처음으로 NYSE 거래장 직원으로 일했습니다.",
    "뮤리엘 시버트는 1967년 NYSE 최초의 여성 정회원이 됐습니다.",
    "조지프 시얼스 3세는 1970년 NYSE 최초의 흑인 회원이 됐습니다.",
    "게일 팬키는 1985년 NYSE 최초의 흑인 여성 회원이 됐습니다.",
    "NYSE 상장사 시가총액 합계는 1980년 처음 1조 달러를 넘었습니다.",
)

/*
 * Primary sources:
 * https://www.bankofengland.co.uk/about/history
 * https://www.federalreservehistory.org/essays/stock-market-crash-of-1929
 * https://www.federalreservehistory.org/essays/stock-market-crash-of-1987
 * https://www.sec.gov/news/speech/spch509.htm
 * https://www.finra.org/media-center/speeches-testimony/remarks-baruch-colleges-financial-markets-conference
 * https://www.sec.gov/news/studies/2010/marketevents-report.pdf
 * https://www.sec.gov/newsroom/press-releases/2013-222
 * https://www.sec.gov/files/litigation/admin/2013/34-70694.pdf
 * https://www.sec.gov/files/staff-report-equity-options-market-struction-conditions-early-2021.pdf
 * https://www.sec.gov/files/litigation/suspensions/2020/34-88477.pdf
 * https://beta.nyse.com/history-of-nyse
 */
private fun marketDramaFacts(): List<String> = listOf(
    "1720년 남해회사 거품 붕괴는 영국 금융사에 ‘남해버블’이라는 이름을 남겼습니다.",
    "제1차 세계대전이 시작된 1914년 NYSE는 넉 달 넘게 문을 닫았습니다.",
    "1929년 10월 28일 검은 월요일에 다우지수는 하루 약 13% 급락했습니다.",
    "1929년 검은 월요일 다음 날 다우지수는 다시 약 12% 떨어졌습니다.",
    "다우지수는 1929년 고점 381에서 1932년 41까지 약 89% 무너졌습니다.",
    "1920년대 미국에서는 주식값의 10%만 내고 나머지를 빌리기도 했습니다.",
    "1929년 폭락 이전의 다우 고점은 1954년 11월에야 다시 회복됐습니다.",
    "미국 SEC는 대공황 뒤인 1934년 투자자 보호 기관으로 태어났습니다.",
    "1968년 NYSE는 밀린 서류를 처리하려고 수요일마다 거래를 쉬기도 했습니다.",
    "미국 증권사의 고정 주식수수료 제도는 1975년 5월 끝났습니다.",
    "1987년 10월 19일 다우지수는 하루 만에 22.6% 떨어졌습니다.",
    "1987년 폭락을 계기로 급락 때 거래를 멈추는 서킷브레이커가 도입됐습니다.",
    "다우지수는 1987년 폭락 전 수준을 2년이 채 안 돼 회복했습니다.",
    "2001년 미국 주식 호가는 분수 단위에서 센트 단위로 바뀌었습니다.",
    "9·11 테러 뒤 NYSE는 네 거래일을 쉬고 2001년 9월 17일 재개장했습니다.",
    "2010년 플래시 크래시 때 주요 지수는 몇 분 만에 5~6% 급락했습니다.",
    "플래시 크래시에서는 300개 넘는 종목이 직전 가격과 60% 넘게 벌어졌습니다.",
    "플래시 크래시 일부 체결가는 1센트 이하로 떨어지거나 10만 달러까지 치솟았습니다.",
    "나이트캐피털은 2012년 새 거래 코드를 일부 서버에만 배포했습니다.",
    "나이트 오류는 고객 주문 212건을 처리하다 154개 종목에서 400만 건 넘게 체결했습니다.",
    "나이트캐피털은 45분의 소프트웨어 사고로 4억 6천만 달러 넘게 잃었습니다.",
    "나이트 시스템은 개장 전 오류 메일 97통을 보냈지만 직원들은 조치하지 않았습니다.",
    "2020년 투자자들은 ZM 대신 이름이 비슷한 ZOOM 주식을 사들이기도 했습니다.",
    "SEC는 혼동이 커지자 무관한 회사 Zoom Technologies 거래를 정지했습니다.",
    "게임스톱은 한 주가 거듭 대여되며 공매도 잔고가 유통주식 수를 넘었습니다.",
    "게임스톱 종가는 2021년 1월 11일부터 27일까지 약 1,600% 올랐습니다.",
    "게임스톱 주가는 2021년 1월 28일 장중 483달러까지 치솟았습니다.",
    "SEC는 공매도 환매만으로 게임스톱의 지속적 상승을 설명하기 어렵다고 봤습니다.",
    "2020년 NYSE는 처음으로 거래장을 완전히 닫고 전자거래만 이어갔습니다.",
)

/*
 * Primary sources:
 * https://semiconductor.samsung.com/about-us/history/
 * https://news.skhynix.com/sk-hynix-leading-revolutions-for-nearly-40-years/
 * https://www.lg.com/global/about-lg/company-history/
 * https://www.hyundai.com/worldwide/en/footer/corporate/history/1967-2000
 * https://newsroom.posco.com/en/one-billion-posco-grows-with-korea/
 * https://www.navercorp.com/en/company/history
 * https://www.kakaocorp.com/page/detail/10810
 * https://www.kakaocorp.com/page/detail/7603
 * https://global.krx.co.kr/contents/GLB/01/0102/0102040000/GLB0102040000.jsp
 * https://global.krx.co.kr/contents/GLB/02/0201/0201010200/GLB0201010200.jsp
 */
private fun koreanCompanyFacts(): List<String> = listOf(
    "삼성전자는 1969년 1월 13일 설립됐습니다.",
    "삼성전자의 첫 흑백TV 수출지는 1971년 파나마였습니다.",
    "삼성전자는 1975년 6월 한국증권거래소에 상장했습니다.",
    "삼성은 1970년대 한국반도체를 인수하며 반도체 사업의 씨앗을 심었습니다.",
    "삼성은 1983년 64Kb D램 개발을 마쳤습니다.",
    "삼성전자는 1984년 현재의 회사 이름으로 사명을 바꿨습니다.",
    "삼성은 1986년 1Mb D램 개발로 메모리 기술 격차를 좁혔습니다.",
    "삼성은 1992년 세계 D램 시장 선두에 올랐습니다.",
    "삼성은 2013년 업계 최초로 3D V낸드를 양산했습니다.",
    "SK하이닉스는 1983년 현대전자라는 이름으로 출발했습니다.",
    "현대전자는 1984년 국내 최초 16Kb SRAM 시험생산에 성공했습니다.",
    "현대전자는 1989년 현대컴보이로 닌텐도를 한국에 들여왔습니다.",
    "현대전자는 1999년 LG반도체의 대주주 지분을 인수했습니다.",
    "현대전자는 2001년 회사 이름을 하이닉스반도체로 바꿨습니다.",
    "SK그룹 편입 뒤 2012년 SK하이닉스로 다시 태어났습니다.",
    "LG전자는 1958년 금성사라는 이름으로 출발했습니다.",
    "금성사는 1959년 한국 최초의 라디오를 생산했습니다.",
    "금성사는 1965년 한국 최초의 냉장고를 생산했습니다.",
    "금성사는 1966년 한국 최초의 TV를 생산했습니다.",
    "금성사는 1982년 미국 앨라배마에 첫 해외 생산기지를 세웠습니다.",
    "LG전자는 1995년 금성사라는 이름을 역사 속으로 보냈습니다.",
    "LG 로고의 곡선은 ‘신라의 미소’에서 영감을 받았습니다.",
    "‘Life’s Good’ 문구는 1990년대 LG 호주 법인에서 태어났습니다.",
    "현대자동차 주식은 1974년 한국증권거래소에 상장됐습니다.",
    "현대 포니의 차체 디자인은 이탈리아의 조르제토 주지아로가 맡았습니다.",
    "포니의 첫 수출 목적지는 1976년 에콰도르였습니다.",
    "현대차는 1986년 엑셀을 미국 시장에 처음 수출했습니다.",
    "현대차는 1991년 독자 개발 엔진 알파를 완성했습니다.",
    "현대차는 1998년 기아자동차를 인수했습니다.",
    "포항제철 1고로의 첫 쇳물은 1973년 6월 9일 흘렀습니다.",
    "한국의 철의 날은 포항제철의 첫 쇳물 날짜를 기념합니다.",
    "포항제철은 1973년 8월 1일 첫 철강 제품을 출하했습니다.",
    "포스코는 1994년 한국 기업 최초로 NYSE에 상장했습니다.",
    "포스코의 누적 조강 생산량은 2019년 10억 톤을 넘었습니다.",
    "네이버는 1999년 법인 설립과 검색 포털 출시를 함께했습니다.",
    "네이버는 2000년 한게임과 합병했습니다.",
    "네이버는 2002년 지식iN을 열고 같은 해 코스닥에 상장했습니다.",
    "네이버는 2004년 코스닥 시가총액 1위에 올랐습니다.",
    "네이버는 2008년 코스닥에서 코스피로 옮겨갔습니다.",
    "NHN은 2013년 회사를 나누며 네이버라는 이름을 되찾았습니다.",
    "라인은 2016년 뉴욕과 도쿄 증시에 동시에 상장했습니다.",
    "네이버웹툰은 2024년 나스닥에 상장했습니다.",
    "카카오의 전신 아이위랩은 2006년 설립됐습니다.",
    "카카오톡 iOS판은 2010년 3월 18일 출시됐습니다.",
    "카카오 이모티콘은 2011년 단 여섯 개로 시작했습니다.",
    "다음과 카카오는 2014년 합쳐져 다음카카오가 됐습니다.",
    "1956년 문을 연 한국 증시에는 상장사가 단 12곳뿐이었습니다.",
    "초기 한국 증권거래소에서는 주식보다 국채 거래가 더 활발했습니다.",
    "한국증권거래소는 1979년 명동에서 여의도로 옮겼습니다.",
    "외국인의 한국 주식 직접투자는 1992년 허용됐습니다.",
    "코스닥은 1996년 7월 성장기업을 위한 시장으로 출범했습니다.",
    "한국 증권시장은 1997년 거래 시스템을 완전 전산화했습니다.",
    "2005년 증권·코스닥·선물 시장 운영기관이 하나로 합쳐졌습니다.",
)

/*
 * Primary sources:
 * https://www.nintendo.co.jp/corporate/en/history/index.html
 * https://www.sony.com/SonyInfo/CorporateInfo/History/company/
 * https://www.sony.com/en/SonyInfo/IR/faq/history.html
 * https://global.toyota/en/company/trajectory-of-toyota/history/
 * https://global.toyota/en/mobility/toyota-brand/emblem/
 * https://global.honda/en/about/history-digest/
 * https://www.fastretailing.com/eng/ir/library/pdf/ar2022_en_08.pdf
 * https://www.7andi.com/en/company/history.html
 * https://holdings.panasonic/global/corporate/about/history/chronicle/1918.html
 * https://www.mazda.com/en/about/history/highlights/
 * https://www.subaru.co.jp/en/outline/
 * https://www.subaru.co.jp/en/ir/library/pdf/ar/ar_2017e.pdf
 */
private fun japaneseCompanyFacts(): List<String> = listOf(
    "닌텐도의 역사는 1889년 야마우치 후사지로가 교토에서 화투를 만들며 시작됐습니다.",
    "닌텐도는 1902년 일본에서 처음으로 서양식 트럼프를 생산했습니다.",
    "1947년 세운 마루후쿠는 오늘날 닌텐도 법인의 전신입니다.",
    "닌텐도는 1962년 오사카 2부와 교토 거래소에 동시에 상장했습니다.",
    "화투 회사 닌텐도는 1963년 사명에서 ‘카드’를 떼었습니다.",
    "닌텐도는 1973년 오락시설용 레이저 사격 시스템도 만들었습니다.",
    "닌텐도는 1980년 게임앤워치 출시와 미국법인 설립을 함께 이뤘습니다.",
    "닌텐도는 1983년 도쿄 1부에 상장하고 패미컴을 출시했습니다.",
    "화투로 출발한 닌텐도는 1989년 휴대용 게임보이를 내놓았습니다.",
    "소니의 전신은 1946년 자본금 19만 엔, 약 20명으로 출발했습니다.",
    "소니는 1950년 일본에서 처음으로 자기테이프 녹음기를 내놓았습니다.",
    "도쿄통신공업은 1955년 첫 트랜지스터 라디오 TR-55를 출시했습니다.",
    "도쿄통신공업은 1958년 회사 이름을 소니로 바꿨습니다.",
    "소니 주식은 1955년 공개됐고 1958년 도쿄거래소에 올랐습니다.",
    "소니는 1961년 일본 기업 최초로 미국예탁증서 ADR을 발행했습니다.",
    "소니는 1970년 일본 기업 최초로 NYSE에 상장했습니다.",
    "소니는 1975년 가정용 비디오 규격 베타맥스를 내놓았습니다.",
    "소니는 1979년 휴대용 카세트 플레이어 워크맨을 출시했습니다.",
    "소니는 1982년 세계 첫 상용 CD 플레이어 CDP-101을 내놓았습니다.",
    "소니는 1988년 CBS레코드, 1989년 컬럼비아픽처스를 인수했습니다.",
    "소니의 첫 플레이스테이션은 1994년 일본에서 출시됐습니다.",
    "도요타 그룹의 뿌리는 자동차가 아니라 자동직기였습니다.",
    "1924년 도요다 사키치는 멈춤 장치를 갖춘 G형 자동직기를 완성했습니다.",
    "도요다는 1929년 자동직기 특허권을 영국 회사에 팔아 자동차 자금을 마련했습니다.",
    "도요타는 1933년 도요다자동직기의 자동차 부문으로 출발했습니다.",
    "1936년 공개 공모가 브랜드 철자를 도요다에서 도요타로 바꿨습니다.",
    "일본어 ‘トヨタ’는 길한 수로 여긴 여덟 획으로 적힙니다.",
    "자동차 부문은 1937년 별도 회사 도요타자동차가 됐습니다.",
    "도요타자동차와 판매회사는 1982년 합쳐져 현재 법인이 됐습니다.",
    "도요타의 세 타원 로고는 1989년 셀시오에 처음 붙었습니다.",
    "도요타는 1997년 양산형 하이브리드 승용차 프리우스를 내놓았습니다.",
    "혼다의 전신 연구소는 1946년 불탄 터의 작은 목조건물에서 시작됐습니다.",
    "혼다의 첫 동력제품은 남은 군용 엔진을 자전거에 붙여 만들었습니다.",
    "혼다 시제품에는 일본식 온수주머니가 연료탱크로 쓰이기도 했습니다.",
    "혼다는 1948년 자본금 100만 엔으로 하마마쓰에서 법인이 됐습니다.",
    "혼다는 1949년 차체까지 직접 만든 첫 오토바이 드림 D를 출시했습니다.",
    "혼다는 1959년 첫 해외법인을 로스앤젤레스에 세웠습니다.",
    "오토바이 회사 혼다는 1963년 경트럭 T360으로 자동차에 진출했습니다.",
    "혼다는 1986년 미국에서 일본차 최초 고급 브랜드 아큐라를 출범시켰습니다.",
    "패스트리테일링의 뿌리는 1949년 야나이 가문의 남성복점입니다.",
    "첫 유니클로는 1984년 히로시마의 후쿠로마치점이었습니다.",
    "오고리상사는 1991년 회사 이름을 패스트리테일링으로 바꿨습니다.",
    "패스트리테일링은 1994년 히로시마거래소에 먼저 상장했습니다.",
    "유니클로의 첫 해외 매장은 2001년 런던에 문을 열었습니다.",
    "패스트리테일링은 2004년 Theory 개발사에 투자했습니다.",
    "세븐앤아이의 뿌리는 1920년 아사쿠사의 옷가게 요카도입니다.",
    "요크세븐은 1973년 설립됐고 이듬해 일본 첫 세븐일레븐을 열었습니다.",
    "일본 세븐일레븐은 1991년 미국 사우스랜드 지분 69.98%를 샀습니다.",
    "세븐앤아이홀딩스는 2005년 순수지주회사로 출범했습니다.",
    "2005년 일본 세븐일레븐은 미국 7-Eleven을 완전자회사화했습니다.",
    "세븐앤아이는 2007년 전자화폐 nanaco를 선보였습니다.",
    "2007년 세븐프리미엄이 그룹 공동 자체상표로 출발했습니다.",
    "파나소닉은 1918년 오사카의 세 사람짜리 작업장에서 출발했습니다.",
    "파나소닉의 첫 플러그에는 폐전구에서 떼낸 금속 나사가 쓰였습니다.",
    "파나소닉은 1927년 ‘내셔널’ 브랜드를 만들었습니다.",
    "창업 17년 뒤인 1935년 마쓰시타전기산업 법인이 세워졌습니다.",
    "마쓰시타전기산업은 2008년 파나소닉으로 사명을 바꿨습니다.",
    "파나소닉은 2022년 지주회사 전환 뒤 사명에 홀딩스를 붙였습니다.",
    "마쓰다는 1920년 히로시마의 동양코르크공업으로 출발했습니다.",
    "공장 70%를 태운 화재 뒤 마쓰다는 코르크에서 기계로 방향을 틀었습니다.",
    "동양코르크공업은 1927년 사명에서 코르크를 떼었습니다.",
    "마쓰다는 1931년 삼륜트럭 Mazda-Go로 자동차 사업을 시작했습니다.",
    "초기 Mazda-Go 표식에는 판매사 미쓰비시의 세 다이아가 들어갔습니다.",
    "마쓰다는 원폭 투하 넉 달 뒤 삼륜트럭 생산을 재개했습니다.",
    "동양공업은 1979년 포드와 자본 제휴를 맺었습니다.",
    "동양공업은 1984년 회사 이름도 마쓰다로 바꿨습니다.",
    "스바루의 뿌리는 1917년 세운 비행기연구소입니다.",
    "스바루의 전신은 1946년 첫 래빗 스쿠터를 생산했습니다.",
    "후지중공업은 1953년 항공기 생산과 자동차 개발로 출범했습니다.",
    "후지중공업 주식은 1960년 도쿄거래소에 상장됐습니다.",
    "후지중공업은 2017년 회사 이름을 스바루로 바꿨습니다.",
)

/*
 * Primary sources:
 * https://www.tsmc.com/english/aboutTSMC/company_profile
 * https://investor.tsmc.com/static/annualReports/1998/html/intro.htm
 * https://investor.tsmc.com/english/faq
 * https://www.tsmc.com/static/abouttsmcaz/index.htm
 * https://www.honhai.com/en-us/about/group-profile/key-milestones
 * https://www.mediatek.com/investor-relations/investor-relations-faq
 * https://corp.mediatek.com/about/awards-and-recognition
 * https://www.acer.com/corporate/en/overview/milestones
 * https://www.asus.com/content/about_asus_history/
 * https://www.asus.com/microsite/mb/asusmb1st/index.aspx
 * https://www.asml.com/en/company/about-asml/history
 * https://timeline.intel.com/
 * https://www.intel.com/content/www/us/en/history/virtual-vault/articles/end-user-marketing-intel-inside.html
 * https://www.nvidia.com/en-us/about-nvidia/corporate-timeline/
 * https://www.amd.com/en/blogs/2024/amd-55th-anniversary-special-relive-history-with-.html
 * https://www.qualcomm.com/company
 */
private fun semiconductorCompanyFacts(): List<String> = listOf(
    "TSMC는 1987년 설립과 함께 전업 반도체 파운드리 모델을 만들었습니다.",
    "TSMC의 초기 정관은 자체 브랜드 칩의 설계와 생산을 금지했습니다.",
    "TSMC는 1994년 대만증권거래소에 상장했습니다.",
    "TSMC는 1997년 뉴욕에서 ADR 거래를 시작했습니다.",
    "TSMC는 2020년 피닉스를 첫 미국 첨단공정 생산지로 골랐습니다.",
    "폭스콘의 모회사 홍하이는 1974년 플라스틱 회사로 출발했습니다.",
    "홍하이는 1982년 정밀공업 회사로 사명을 바꿨습니다.",
    "홍하이는 1985년 미국에 FOXCONN 자회사를 세웠습니다.",
    "홍하이는 1988년 중국 투자를 시작했습니다.",
    "홍하이는 1991년 대만거래소에 숫자 티커 2317로 상장했습니다.",
    "홍하이 계열 FIH Mobile은 2005년 홍콩거래소에 상장했습니다.",
    "미디어텍은 1997년 신주과학산업단지에서 법인으로 출발했습니다.",
    "미디어텍은 1998년 48배속 CD-ROM 칩셋을 내놓았습니다.",
    "미디어텍은 2001년 대만거래소에 숫자 티커 2454로 상장했습니다.",
    "에이서는 1976년 자본금 100만 대만달러의 멀티테크로 출발했습니다.",
    "멀티테크는 1978년 교육센터에서 대만 엔지니어 3천 명을 길렀습니다.",
    "에이서의 첫 자체 브랜드 제품은 1981년 MicroProfessor-I였습니다.",
    "에이서는 1982년 대만 최초의 8비트 가정용 PC를 내놓았습니다.",
    "에이서는 1984년 주변기기 회사를 세웠고 훗날 BenQ가 됐습니다.",
    "멀티테크는 1987년 글로벌화를 위해 에이서라는 이름을 만들었습니다.",
    "에이서는 1990년 다중사용자 컴퓨터 업체 Altos를 인수했습니다.",
    "ASUS 창업 아이디어는 1989년 타이베이 카페 대화에서 태어났습니다.",
    "ASUS는 1989년 엔지니어 네 명이 작은 아파트에서 시작했습니다.",
    "ASUS는 창업 8개월 만에 IBM 제품용 메인보드를 만들었습니다.",
    "ASUS는 1997년 첫 노트북 P6300을 출시했습니다.",
    "ASML은 1984년 필립스와 ASMI의 합작회사로 태어났습니다.",
    "ASML의 첫 사무실은 필립스 건물 옆 물 새는 조립식 창고였습니다.",
    "ASML은 창업 때 PAS 2000 웨이퍼 스테퍼 한 제품으로 승부했습니다.",
    "ASML의 1991년 PAS 5500은 회사를 흑자로 돌린 핵심 장비가 됐습니다.",
    "ASML은 1995년 암스테르담과 나스닥에 동시에 상장했습니다.",
    "ASML은 2001년 두 웨이퍼 스테이지를 쓰는 TWINSCAN을 내놓았습니다.",
    "ASML은 2010년 첫 EUV 노광장비 시제품을 고객사에 보냈습니다.",
    "ASML은 2023년 첫 High-NA EUV 시스템을 출하했습니다.",
    "인텔은 1968년 로버트 노이스와 고든 무어가 설립했습니다.",
    "인텔은 1970년 초기 상용 D램 가운데 하나인 1103을 내놓았습니다.",
    "인텔 4004는 1971년 한 칩에 CPU를 담은 상용 마이크로프로세서가 됐습니다.",
    "인텔은 1978년 16비트 마이크로프로세서 8086을 출시했습니다.",
    "인텔은 1985년 D램 사업에서 물러나고 386 프로세서를 내놓았습니다.",
    "인텔은 1991년 PC 속 칩을 알리는 ‘Intel Inside’를 시작했습니다.",
    "인텔은 1993년 숫자 대신 이름을 붙인 펜티엄 프로세서를 출시했습니다.",
    "엔비디아는 1993년 젠슨 황을 포함한 세 명이 설립했습니다.",
    "엔비디아는 1997년 그래픽칩 RIVA 128로 첫 큰 성공을 거뒀습니다.",
    "엔비디아는 1999년 지포스 256을 내놓으며 GPU라는 이름을 내세웠습니다.",
    "엔비디아 주식은 1999년 나스닥에 상장됐습니다.",
    "엔비디아는 2006년 병렬연산 플랫폼 CUDA를 공개했습니다.",
    "엔비디아는 2018년 실시간 광선추적을 내세운 RTX를 출시했습니다.",
    "AMD는 1969년 페어차일드를 나온 제리 샌더스와 일곱 명이 세웠습니다.",
    "AMD의 첫 자체 제품 Am9300은 1970년 출시됐습니다.",
    "퀄컴은 1985년 어윈 제이컵스의 집에 모인 일곱 명이 시작했습니다.",
    "퀄컴이라는 이름은 ‘Quality Communications’를 줄여 만들었습니다.",
    "퀄컴은 1989년 CDMA 이동통신 기술을 처음 공개 시연했습니다.",
)

/*
 * Primary sources:
 * https://news.microsoft.com/facts-about-microsoft/
 * https://learn.microsoft.com/en-us/shows/history/history-of-microsoft-1975
 * https://www.apple.com/ca/newsroom/2026/03/apple-to-celebrate-50-years-of-thinking-different/
 * https://investor.apple.com/investor-relations/faq/default.aspx
 * https://www.apple.com/newsroom/2001/10/23Apple-Presents-iPod/
 * https://www.apple.com/newsroom/2007/01/09Apple-Reinvents-the-Phone-with-iPhone/
 * https://ir.aboutamazon.com/faqs/default.aspx
 * https://www.aboutamazon.com/news/workplace/first-amazon-office-jeff-bezos-garage
 * https://www.aboutamazon.com/news/aws/aws-cloud-computing-it-services
 * https://press.aboutamazon.com/2007/11/introducing-amazon-kindle
 * https://about.google/company-info/our-story/
 * https://abc.xyz/investor/founders-letters/ipo-letter/
 * https://about.netflix.com/en/news/netflix-dvd-the-final-season
 * https://www.netflix.com/tudum/articles/netflix-trivia-25th-anniversary
 * https://www.ibm.com/history/ctr-and-ibm
 * https://www.ibm.com/history/personal-computer
 * https://www.ibm.com/history/floppy-disk
 * https://www.ibm.com/history/ramac
 * https://www.ibm.com/history/upc
 * https://www.hp.com/hpinfo/abouthp/histnfacts/timeline/hist_30s.html
 * https://www.ebayinc.com/company/our-history/
 * https://press.airbnb.com/wp-content/uploads/sites/4/2018/08/The-Airbnb-Story-Timeline-EN-GLOBAL.pdf
 * https://slack.com/intl/en-gb/resources/why-use-slack/what-is-slack-and-how-does-it-work
 * https://www.shopify.com/blog/how-to-save-entrepreneurship
 * https://blog.youtube/news-and-events/youtube-to-z-happybirthdayyoutube/
 */
private fun globalTechnologyFacts(): List<String> = listOf(
    "마이크로소프트는 1975년 빌 게이츠와 폴 앨런이 설립했습니다.",
    "마이크로소프트의 첫 제품은 Altair용 BASIC 인터프리터였습니다.",
    "마이크로소프트는 Altair 제조사 가까운 앨버커키에서 출발했습니다.",
    "마이크로소프트는 1979년 뉴멕시코에서 워싱턴주로 본사를 옮겼습니다.",
    "1981년 IBM PC에는 마이크로소프트의 MS-DOS가 탑재됐습니다.",
    "마이크로소프트는 1986년 3월 13일 나스닥에 상장했습니다.",
    "마이크로소프트는 상장한 1986년에 레드먼드 캠퍼스로 옮겼습니다.",
    "마이크로소프트는 1989년 처음으로 Office 제품군을 발표했습니다.",
    "1990년 Windows 3.0 출시는 PC 운영환경의 대중화를 앞당겼습니다.",
    "Windows 95는 1995년 한밤중 출시 행사와 함께 등장했습니다.",
    "소프트웨어 회사 마이크로소프트는 2001년 Xbox를 출시했습니다.",
    "애플은 1976년 4월 1일 설립돼 2026년에 50주년을 맞았습니다.",
    "애플은 1977년 Apple II, 1984년 Macintosh를 출시했습니다.",
    "애플은 1980년 12월 12일 주당 22달러로 기업공개를 했습니다.",
    "애플은 2001년 주머니에 1천 곡을 담는다는 iPod을 발표했습니다.",
    "첫 iPod의 무게는 6.5온스였고 맥과 FireWire로 연결됐습니다.",
    "애플은 2007년 iPhone을 전화·iPod·인터넷 기기의 결합으로 소개했습니다.",
    "아마존은 1994년 법인으로 설립되고 1995년 7월 온라인 서점을 열었습니다.",
    "아마존의 첫 고객은 존 웨인라이트라는 소프트웨어 엔지니어였습니다.",
    "아마존 첫 주문 상품은 인공지능 연구서 ‘Fluid Concepts’였습니다.",
    "초기 아마존은 주문마다 종을 울렸지만 주문이 늘자 곧 그만뒀습니다.",
    "아마존은 1997년 5월 주당 18달러로 나스닥에 상장했습니다.",
    "아마존은 2005년 무료 빠른 배송을 묶은 Prime을 시작했습니다.",
    "온라인 서점 아마존은 2006년 클라우드 사업 AWS를 출범시켰습니다.",
    "아마존은 2007년 전자책 단말 Kindle을 선보였습니다.",
    "구글 창업자 래리 페이지와 세르게이 브린은 1995년 스탠퍼드에서 만났습니다.",
    "구글의 첫 검색엔진 이름은 웹의 연결을 추적한다는 뜻의 Backrub이었습니다.",
    "Google이라는 이름은 거대한 수를 뜻하는 googol의 철자를 비튼 결과였습니다.",
    "앤디 벡톨샤임은 법인이 생기기 전 Google Inc. 앞으로 수표를 써줬습니다.",
    "구글은 1998년 9월 4일 캘리포니아에서 법인으로 설립됐습니다.",
    "구글의 첫 사무실은 수전 워치츠키가 빌려준 차고였습니다.",
    "초기 구글 서버 보관함에는 값싼 레고 블록이 사용됐습니다.",
    "구글의 첫 두들은 1998년 창업자들의 버닝맨 여행을 알리는 그림이었습니다.",
    "구글은 2004년 기업공개 가격과 배정을 경매 방식으로 결정했습니다.",
    "구글은 2015년 여러 사업을 묶는 지주회사 Alphabet을 만들었습니다.",
    "넷플릭스는 1997년 리드 헤이스팅스와 마크 랜돌프가 세웠습니다.",
    "넷플릭스는 1998년 온라인 DVD 대여점을 열었습니다.",
    "넷플릭스가 처음 배송한 DVD는 영화 ‘비틀쥬스’였습니다.",
    "넷플릭스는 1999년 월정액 DVD 대여 서비스를 시작했습니다.",
    "넷플릭스 주식은 2002년 나스닥에 상장됐습니다.",
    "DVD 회사 넷플릭스는 2007년 스트리밍 서비스를 시작했습니다.",
    "넷플릭스는 25년간 운영한 미국 DVD 우편 서비스를 2023년 끝냈습니다.",
    "IBM의 전신 CTR은 1911년 서로 다른 세 회사의 합병으로 태어났습니다.",
    "CTR에는 천공카드 기계뿐 아니라 출퇴근 기록기와 저울 사업도 있었습니다.",
    "CTR은 해외 사업을 반영해 1924년 이름을 IBM으로 바꿨습니다.",
    "IBM은 1956년 디스크 드라이브를 쓴 최초의 컴퓨터 RAMAC을 내놓았습니다.",
    "IBM의 플로피디스크 개발은 1967년 ‘미노’라는 비밀 프로젝트로 시작됐습니다.",
    "IBM은 1971년 처음으로 8인치 플로피디스크 드라이브를 판매했습니다.",
    "초기 8인치 플로피 한 장은 천공카드 약 3천 장을 담았습니다.",
    "IBM은 1972년 여덟 줄로 쪼갠 현재 형태의 로고를 도입했습니다.",
    "IBM 기술진이 만든 UPC 바코드는 1973년 소매업계 표준이 됐습니다.",
    "IBM PC 5150은 1981년 8월 1,565달러부터 판매됐습니다.",
    "IBM PC의 메인보드는 40일 만에 설계되고 시제품은 넉 달 만에 나왔습니다.",
    "IBM PC는 인텔 8088과 마이크로소프트 운영체제를 외부에서 가져왔습니다.",
    "IBM은 PC 회로와 소스 정보를 공개해 호환기기 생태계를 키웠습니다.",
    "HP는 1939년 팰로앨토의 차고에서 빌 휴렛과 데이브 패커드가 시작했습니다.",
    "HP의 첫 제품은 컴퓨터가 아니라 200A 오디오 발진기였습니다.",
    "디즈니는 HP 200B 오디오 발진기의 첫 대량 고객이었습니다.",
    "HP 장비는 영화 ‘판타지아’ 특별 상영관 12곳의 음향 시험에 쓰였습니다.",
    "eBay는 1995년 Labor Day 주말에 AuctionWeb이라는 이름으로 시작했습니다.",
    "eBay 첫 판매품은 14.83달러에 팔린 고장 난 레이저 포인터였습니다.",
    "eBay가 아내의 Pez 수집 때문에 생겼다는 일화는 꾸며낸 홍보 이야기였습니다.",
    "AuctionWeb은 1997년 회사 이름을 eBay로 바꿨습니다.",
    "에어비앤비는 집세를 마련하려고 거실에 에어매트리스를 놓으며 시작됐습니다.",
    "에어비앤비 창업자들의 첫 손님은 디자인 행사에 온 낯선 여행자 세 명이었습니다.",
    "에어비앤비는 자금난 때 Obama O’s 시리얼로 약 3만 달러를 벌었습니다.",
    "게임 ‘Glitch’가 실패하자 내부 채팅 도구가 Slack으로 독립했습니다.",
    "Slack이라는 이름은 검색 가능한 모든 대화와 지식의 머리글자입니다.",
    "Shopify 창업자는 2004년 Snowdevil이라는 스노보드 쇼핑몰을 열었습니다.",
    "쓸 만한 쇼핑몰 소프트웨어가 없어 직접 만든 도구가 Shopify가 됐습니다.",
    "Shopify 창업자는 캐나다 취업비자를 못 얻은 일이 창업 계기가 됐습니다.",
    "유튜브 최초 영상은 2005년 코끼리 앞 공동창업자를 담은 19초 영상이었습니다.",
)

/*
 * Primary sources:
 * https://www.3m.com/3M/en_US/about-3m/history/
 * https://us.pg.com/pg-history/
 * https://us.pg.com/blogs/beyond-the-gates/
 * https://www.coca-colacompany.com/about-us/history/the-birth-of-a-refreshing-idea
 * https://www.coca-colacompany.com/about-us/history/the-asa-candler-era
 * https://investors.coca-colacompany.com/stock-info
 * https://www.pepsico.com/en/investors/shareholders-services
 * https://www.pepsico.com/en/newsroom/press-releases/2023/pepsi-celebrates-its-historic-125th-anniversary-with-125-day-long-campaign-spotlighting-iconic-moments-of-the-past-present-and-future
 * https://about.nike.com/en/magazine/bill-bowerman-nike-s-original-innovator
 * https://about.nike.com/en/magazine/nike-swoosh-logo-history
 * https://about.nike.com/en/magazine/nike-moon-shoe-waffle-iron-true-history
 * https://about.starbucks.com/history/our-original-store/
 * https://stories.starbucks.com/uploads/2019/01/AboutUs-Timeline-1.26.17-1.pdf
 * https://d23.com/disney-history/
 * https://d23.com/a-to-z/stock-disney/
 * https://corporate.mcdonalds.com/corpmcd/our-company/who-we-are/our-history.html
 * https://www.colgatepalmolive.com/en-us/who-we-are/history
 * https://www.mars.com/about/history
 * https://www.costco.com/f/-/company-information
 * https://corporate.target.com/news-features/article/2022/05/60th-anniversary
 */
private fun consumerBrandFacts(): List<String> = listOf(
    "3M은 사포용 강옥을 캐려 했지만 회장암이 나와 초기 판매가 부진했습니다.",
    "3M 연구원 리처드 드루는 1925년 자동차 도색용 마스킹테이프를 만들었습니다.",
    "3M은 1930년 투명한 셀로판테이프를 선보였습니다.",
    "스펜서 실버의 약한 접착제는 아트 프라이의 책갈피 실험을 거쳐 포스트잇이 됐습니다.",
    "3M 포스트잇은 저점착 접착제 발견 12년 뒤인 1980년 출시됐습니다.",
    "P&G는 1837년 촛불 장인과 비누 장인이 동업하며 설립됐습니다.",
    "P&G 창업자 윌리엄 프록터와 제임스 갬블은 자매와 결혼한 동서지간이었습니다.",
    "P&G는 1879년 물에 뜨는 흰 비누 Ivory를 개발했습니다.",
    "P&G는 1890년 Ivorydale 공장에 첫 사내 연구소를 세웠습니다.",
    "P&G는 1924년 소비자를 직접 조사하는 시장조사 부서를 만들었습니다.",
    "P&G는 1931년 제품별 책임자를 두는 브랜드 관리 체계를 시작했습니다.",
    "P&G가 후원한 라디오 연속극은 ‘소프 오페라’라는 장르명이 퍼지는 데 한몫했습니다.",
    "P&G 연구원은 중단 명령 뒤에도 7년간 몰래 연구해 1946년 Tide를 내놓았습니다.",
    "P&G는 1955년 불소 치약 Crest를 시험 시장에 내놓았습니다.",
    "P&G 연구원이 만든 일회용 기저귀 Pampers는 1961년 출시됐습니다.",
    "코카콜라는 1886년 5월 8일 애틀랜타 약국에서 한 잔 5센트에 팔렸습니다.",
    "코카콜라 첫해 판매량은 하루 평균 약 아홉 잔에 불과했습니다.",
    "Coca-Cola라는 이름과 필기체 로고는 회계담당자 프랭크 로빈슨이 만들었습니다.",
    "아사 캔들러는 코카콜라 제조법과 권리를 사들여 전국 사업으로 키웠습니다.",
    "코카콜라는 공모로 고른 곡선형 유리병을 1916년 시장에 내놓았습니다.",
    "코카콜라 주식은 1919년 주당 40달러로 처음 공개됐습니다.",
    "펩시의 원래 이름은 약사 케일럽 브래드햄이 만든 ‘Brad’s Drink’였습니다.",
    "케일럽 브래드햄은 1898년 음료 이름을 Pepsi-Cola로 바꿨습니다.",
    "Pepsi-Cola와 Frito-Lay는 1965년 합쳐져 PepsiCo가 됐습니다.",
    "PepsiCo는 2001년 오트밀 브랜드를 보유한 Quaker Oats를 인수했습니다.",
    "나이키의 전신 Blue Ribbon Sports는 1964년 출발했습니다.",
    "초기 나이키는 자체 신발 대신 일본 운동화를 미국에 수입해 팔았습니다.",
    "빌 바우어만은 집의 와플 기계에 녹인 우레탄을 부어 밑창을 시험했습니다.",
    "Blue Ribbon Sports는 1971년 자체 브랜드 이름으로 Nike를 골랐습니다.",
    "나이키의 스우시는 학생 캐럴린 데이비슨이 1971년 디자인했습니다.",
    "와플 밑창을 단 나이키 운동화 시제품은 1972년 육상대회에서 시험됐습니다.",
    "스타벅스는 1971년 시애틀 파이크 플레이스에서 문을 열었습니다.",
    "첫 스타벅스는 음료 카페가 아니라 원두와 차, 향신료를 파는 가게였습니다.",
    "첫 스타벅스 매장에는 직원이 한 명뿐이었습니다.",
    "하워드 슐츠는 1982년 스타벅스의 소매·마케팅 책임자로 합류했습니다.",
    "슐츠는 1983년 밀라노의 에스프레소 바에서 커피하우스 아이디어를 얻었습니다.",
    "스타벅스는 1984년 시애틀 매장에서 카페라테를 처음 시험 판매했습니다.",
    "슐츠는 1985년 별도 커피회사 Il Giornale를 세웠습니다.",
    "Il Giornale는 1987년 스타벅스 자산을 사며 그 이름을 이어받았습니다.",
    "스타벅스 주식은 1992년 나스닥에 상장됐습니다.",
    "스타벅스는 1995년 얼음 음료 Frappuccino를 전국에 출시했습니다.",
    "디즈니는 1923년 Disney Brothers Cartoon Studio로 출발했습니다.",
    "디즈니는 오스월드 캐릭터 권리를 잃은 뒤 미키마우스를 만들었습니다.",
    "미키마우스는 1928년 유성 애니메이션 ‘증기선 윌리’로 이름을 알렸습니다.",
    "디즈니는 1937년 첫 장편 애니메이션 ‘백설공주’를 공개했습니다.",
    "디즈니는 1940년 보통주를 팔기 위한 첫 투자설명서를 냈습니다.",
    "디즈니랜드는 1955년 캘리포니아 애너하임에서 문을 열었습니다.",
    "디즈니 주식은 1957년 뉴욕증권거래소에 상장됐습니다.",
    "월트디즈니월드는 1971년 플로리다에서 문을 열었습니다.",
    "디즈니는 2006년 픽사, 2009년 마블을 인수했습니다.",
    "디즈니는 2012년 루카스필름을 인수해 스타워즈를 품었습니다.",
    "디즈니는 2019년 21세기폭스 자산 인수를 마쳤습니다.",
    "맥도날드 형제는 1948년 식당을 석 달 닫고 셀프서비스로 개조했습니다.",
    "새 맥도날드의 메뉴는 아홉 개뿐이었고 햄버거 가격은 15센트였습니다.",
    "레이 크록은 1954년 믹서기를 팔러 왔다가 프랜차이즈 가능성을 봤습니다.",
    "레이 크록은 1955년 일리노이에 자신의 첫 맥도날드 매장을 열었습니다.",
    "레이 크록은 1961년 맥도날드 형제에게 회사 권리를 사들였습니다.",
    "Colgate는 1806년 치약이 아니라 전분·비누·양초 사업으로 시작했습니다.",
    "Colgate 치약은 1873년 튜브가 아니라 단지에 담겨 나왔습니다.",
    "Colgate는 1896년 치약을 짜 쓰는 튜브에 담기 시작했습니다.",
    "Colgate와 Palmolive-Peet는 1928년 합병했습니다.",
    "윌리엄 리글리는 1891년 32달러를 들고 시카고에서 사업을 시작했습니다.",
    "리글리는 베이킹파우더 판촉물로 준 껌이 더 인기 있다는 사실을 발견했습니다.",
    "리글리는 1893년 Juicy Fruit와 Spearmint 껌을 출시했습니다.",
    "첫 Price Club은 1976년 개조한 샌디에이고 비행기 격납고에서 열렸습니다.",
    "첫 Costco 창고형 매장은 1983년 시애틀에서 문을 열었습니다.",
    "Costco는 창업 6년이 되기 전 연 매출 30억 달러를 달성했습니다.",
    "Target이라는 이름은 200개가 넘는 후보 중에서 선택됐습니다.",
    "Target의 빨간 과녁 이름과 로고는 1962년 함께 구상됐습니다.",
    "첫 Target 네 매장은 1962년 미네소타에서 문을 열었습니다.",
)

/*
 * Primary sources:
 * https://corporate.exxonmobil.com/who-we-are/our-global-organization/our-history
 * https://www.americanexpress.com/en-us/company/our-history/
 * https://www.yamaha.com/en/about/history/brand/
 * https://www.adidas-group.com/en/about/history
 * https://about.puma.com/en/puma/history
 * https://www.lego.com/en-us/aboutus/lego-group/the-lego-group-history
 */
private fun industrialCompanyFacts(): List<String> = listOf(
    "ExxonMobil의 뿌리 Standard Oil은 1870년 설립됐습니다.",
    "미국 대법원은 1911년 Standard Oil을 34개 회사로 나누게 했습니다.",
    "훗날 Exxon과 Mobil이 될 회사들도 Standard Oil 해체로 갈라졌습니다.",
    "초기 석유회사의 주력상품은 자동차 연료가 아니라 등유였습니다.",
    "Jersey Standard는 1972년 회사 이름을 Exxon으로 바꿨습니다.",
    "Exxon과 Mobil은 갈라진 지 88년 만인 1999년 다시 합병했습니다.",
    "American Express는 1850년 세 운송회사가 합쳐지며 태어났습니다.",
    "초기 American Express의 주력은 금융이 아니라 소포·화물 운송이었습니다.",
    "American Express는 1882년 우편환 사업을 시작했습니다.",
    "American Express 여행자수표는 1891년 처음 나왔습니다.",
    "American Express는 1895년 파리에 첫 유럽 사무소를 열었습니다.",
    "화물회사 American Express는 1915년 여행서비스 사업을 시작했습니다.",
    "American Express는 1958년 첫 결제카드를 발행했습니다.",
    "첫 American Express 카드는 플라스틱이 아니라 종이로 만들어졌습니다.",
    "야마하 도라쿠스는 1887년 학교 오르간을 수리한 뒤 직접 시제품을 만들었습니다.",
    "야마하의 로고는 세 개의 소리굽쇠를 겹친 모양입니다.",
    "야마하 악기사업 법인은 1897년 Nippon Gakki로 설립됐습니다.",
    "야마하의 오토바이 사업은 1955년 Yamaha Motor로 분리됐습니다.",
    "아디다스의 전신은 다슬러 형제가 어머니의 세탁실에서 신발을 만들며 시작됐습니다.",
    "다슬러 형제는 1924년 ‘다슬러 형제 신발공장’을 정식 등록했습니다.",
    "다슬러 형제가 결별한 뒤 아디는 1949년 adidas를 등록했습니다.",
    "adidas라는 이름은 창업자 아디 다슬러의 이름을 합쳐 만들었습니다.",
    "adidas는 회사 등록과 같은 1949년에 세 줄무늬 신발도 등록했습니다.",
    "루돌프 다슬러는 직원 14명과 별도 신발공장을 시작했습니다.",
    "루돌프의 새 회사는 처음에 자신의 이름을 줄여 RUDA라고 불렸습니다.",
    "RUDA는 1948년 더 역동적인 이름 PUMA로 바뀌었습니다.",
    "PUMA는 1952년 나사식 스터드를 단 축구화 SUPER ATOM을 출시했습니다.",
    "LEGO는 1932년 목수 올레 키르크 크리스티안센의 목제 장난감으로 시작됐습니다.",
    "LEGO라는 이름은 덴마크어 ‘leg godt’, 즉 ‘잘 놀다’에서 나왔습니다.",
    "LEGO는 1949년 자동결합 블록이라는 초기 플라스틱 블록을 내놓았습니다.",
    "현재 형태의 LEGO 스터드와 튜브 결합 구조는 1958년 특허를 받았습니다.",
    "1960년 목제 장난감 창고 화재 뒤 LEGO는 플라스틱 블록에 집중했습니다.",
)

/*
 * Primary sources:
 * https://history.southwest.com/faqs/
 * https://www.southwestairlinesinvestorrelations.com/news-events/press-releases/detail/1737/southwest-airlines-gives-away-vegas-valentine
 * https://www.ferrari.com/en-EN/corporate/stock-info
 * https://www.sec.gov/Archives/edgar/data/1605484/000160548415000079/a99210-22x15.htm
 * https://www.sec.gov/Archives/edgar/data/69891/000143774924021311/fizz20240430_10k.htm
 * https://ir.daveandbusters.com/static-files/c276b821-f397-4d3f-9090-782dc88234a8
 * https://investor.harley-davidson.com/stock-info/default.aspx
 * https://abc.xyz/investor/founders-letters/2011/default.aspx
 * https://www.sec.gov/Archives/edgar/data/1652044/000165204426000018/goog-20251231.htm
 * https://www.sec.gov/Archives/edgar/data/37996/000155278126000164/e26003_f-def14a.htm
 * https://www.berkshirehathaway.com/1995ar/1995ar.html
 * https://www.nyse.com/direct-listings
 * https://investor.apple.com/investor-relations/faq/default.aspx
 * https://investors.coca-colacompany.com/shareowners/faqs
 * https://www.sec.gov/Archives/edgar/data/317540/000031754024000016/cokeconsolidatedannualrepo.pdf
 */
private fun unusualStockFacts(): List<String> = listOf(
    "사우스웨스트항공은 댈러스 러브필드와 하트 로고를 떠올려 LUV를 골랐습니다.",
    "1970년대 사우스웨스트는 칵테일을 ‘러브 포션’, 발권기를 ‘러브 머신’이라 불렀습니다.",
    "페라리의 NYSE 티커 RACE는 영어로 ‘경주’를 뜻합니다.",
    "페라리는 공모가를 주당 52달러로 정해 2015년 10월 21일 NYSE에 상장했습니다.",
    "National Beverage의 나스닥 티커는 FIZZ입니다.",
    "FIZZ라는 티커의 회사는 LaCroix와 Shasta 음료를 보유합니다.",
    "Dave & Buster’s는 2014년 기업공개 때 PLAY를 티커로 골랐습니다.",
    "Harley-Davidson의 티커 HOG는 1983년 만든 오너클럽 약칭과 같습니다.",
    "Alphabet의 상장 A주 GOOGL에는 주당 한 표가 붙습니다.",
    "Alphabet의 상장 C주 GOOG에는 원칙적으로 의결권이 없습니다.",
    "Alphabet의 비상장 B주에는 주당 열 표가 붙습니다.",
    "Ford의 일반 상장주는 주당 한 표의 의결권을 가집니다.",
    "포드 가문의 B주는 전체 의결권 40%를 유지하도록 설계됐습니다.",
    "Ford B주는 포드 가문 밖의 일반 투자자에게 공개 거래되지 않습니다.",
    "Berkshire Hathaway는 1996년 처음으로 B주를 만들었습니다.",
    "도입 당시 버크셔 B주의 경제적 권리는 A주의 30분의 1이었습니다.",
    "도입 당시 버크셔 B주의 의결권은 A주의 200분의 1이었습니다.",
    "버크셔 B주는 고수수료 ‘복제 신탁’ 판매를 막으려는 목적도 있었습니다.",
    "Spotify는 2018년 전통적 IPO 없이 NYSE에 처음 직상장했습니다.",
    "Slack은 2019년 Spotify에 이어 NYSE에 직상장했습니다.",
    "애플 IPO 당시 한 주는 다섯 차례 분할을 거치며 224주가 됐습니다.",
    "Coca-Cola Company의 NYSE 티커는 KO입니다.",
    "COKE는 Coca-Cola Consolidated라는 독립 병입사의 나스닥 티커입니다.",
)
