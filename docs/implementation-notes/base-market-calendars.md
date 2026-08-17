# 베이스 거래·금융 달력 구현 기준

## 범위와 정본

게임 캠페인은 2026-08-01 09:00 KST에 시작하고 2040-12-31 미국
정규장 종료에 끝난다. 직전·직후 영업일 계산을 위해 베이스 달력은
2025~2041년을 지원한다. `GameCalendar`는 이 베이스 휴장을 항상 적용하고,
호출자가 넘긴 휴장일은 예측할 수 없는 임시 휴장으로 추가한다.

한국 공휴일은 [관공서의 공휴일에 관한 규정](https://www.law.go.kr/LSW/lsInfoP.do?ancYnChk=0&chrClsCd=010202&efYd=20260501&lsiSeq=285779&urlMode=lsInfoP),
정기 선거일은 [공직선거법 제34조](https://law.go.kr/lsLawLinkInfo.do?chrClsCd=010202&lsJoLnkSeq=1014495905),
2025-06-03 조기 대선은 [중앙선관위 확정 자료](https://cb.nec.go.kr/su/bbs/B0000268/view.do?category1=su&category2=&deleteCd=0&menuNo=200008&nttId=256375&pageIndex=4)를
따른다. 설날·부처님 오신 날·추석은 [한국천문연구원 음양력 변환](https://astro.kasi.re.kr/life/pageView/8)과
[월력요항](https://astro.kasi.re.kr/life/post/almanac)을 기준으로 압축 음력 연도 메타데이터에서
매년 변환한다. 대체공휴일은 주말·중복 사유를 연결된 군집으로 계산해
같은 중복에서 여러 대체일이 생기지 않게 한다.

## KRX·KSD 분리

KRX는 한국 금융 공휴일에 매년 말 거래소 폐장일을 추가한다. KSD/KOFR
영업일은 거래소 전용 연말 폐장일을 제외한다. 따라서 KRX가 쉬는 12월 31일에도
KSD fixing·공표가 있을 수 있다. KOFR 영업일 처리는
[KSD 산출업무규정](https://www.kofr.kr/resource/file/20260722140901204.pdf)을 기준으로 한다.

## NYSE 휴장과 조기폐장

NYSE 휴장일은 신정, Martin Luther King Jr. Day, Presidents Day, Good Friday,
Memorial Day, Juneteenth, Independence Day, Labor Day, Thanksgiving, Christmas를
매년 연산한다. [NYSE 거래 시간 표](https://www.nyse.com/trade/hours-calendars)에 따라
정상 거래일인 7월 3일, Thanksgiving 다음 금요일, 12월 24일은 13:00 ET
조기폐장으로 산출한다. 조기폐장 시간은 시세 누적·주문 만료·지수 및 ETF
방법론 종가 판정과 MWCB·LULD 시간 경계에 공통 적용한다. 2025-01-09
Jimmy Carter 국가애도일은 [NYSE 공식 공지](https://www.nyse.com/publicdocs/nyse/markets/american-options/rule-interpretations/2025/National_Day_of_Mourning_20250102.pdf)에
따라 알려진 일회성 휴장으로 고정한다. 아직 공표되지 않은 국가애도일·날씨·시스템
장애 휴장은 연산할 수 없으므로 임시 휴장 입력으로 추가한다.
