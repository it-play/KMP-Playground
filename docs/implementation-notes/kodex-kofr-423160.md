# KODEX KOFR금리액티브(합성) 구현 기준

## 2026-08-01 시작 anchor

게임 시작 시각은 2026-08-01 09:00 KST다. 토요일이므로 KOFR 공개 상태와
423160 가격은 직전 영업일에 이용할 수 있었던 값으로 고정한다.

- KSD가 2026-07-31 10:50에 공표한 2026-07-30 KOFR는 2.781%, KOFR 지수는
  1194.28656이다.
- 2026-07-31 18:00에 포착된 fixing은 다음 영업일 공표 전 대기 상태다. KSD가
  2026-08-03 공표한 이 fixing은 2.786%다. 시작 시점의 공개 지수와 대기 fixing을
  하나의 값으로 합치지 않는다.
- 423160은 삼성자산운용 기준가 자료의 2026-07-31 종가 110,665원과 KRX KIND의
  2026-07-30 효력 발행좌수 32,714,000좌를 사용한다. 따라서 카탈로그의 시장가치
  anchor는 3,620,294,810,000원이다.

공식 근거는 [KSD KOFR](https://www.kofr.kr/intro/RFRinfo.jsp?sMenuId=001001&sLangCd=01),
[KSD 산출 규정](https://www.kofr.kr/resource/file/20260722140901204.pdf),
[BOK 2026-07-16 기준금리 결정](https://www.bok.or.kr/portal/bbs/P0000559/view.do?menuNo=200788&nttId=11062942),
[삼성자산운용 상품 자료](https://m.samsungfund.com/etf/product/view.do?id=2ETFG6),
[삼성자산운용 기준가 자료](https://m.samsungfund.com/excel_standar.do?fId=2ETFG6&gijunYMD=20260814),
[KRX KIND 발행좌수 공시](https://kind.krx.co.kr/external/2026/07/29/000803/20260729001849/68656.htm)다.

## 금리·지수 실행과 저장 검증

KOFR 계산 금리는 퍼센트 소수점 다섯째 자리, 공표 금리는 셋째 자리, 지수는 다섯째
자리에서 half-up 반올림한다. 당일 지수는 새로 공표된 fixing이 아니라 직전 공표
fixing으로 직전 지수일부터 ACT/365 복리한 뒤, 새 fixing은 다음 구간부터 사용한다.

한국은행 정책금리는 시작 시점에 공개된 2.75%에서 출발한다. 2026-08-01 이전 회의를
다시 적용하지 않고 캠페인 시작 뒤 회의만 순서대로 재생한다. 저장 검증은 카탈로그
anchor, 캠페인 시드, 같은 정책금리 회의 경로로 시작 이후 fixing·공표·지수를 전부
재생해 저장 상태와 정확히 비교한다.

## 상품 오버레이와 분배

423160 가격의 기준 수익은 KOFR 총수익 지수에서 한 번만 온다. 상품 계층은 그 위에
연 0.0594% 총보수와 상품 고유 추적오차를 적용하고, 별도 버전 가정
`kodex-kofr-active-fully-funded-overlay-2026-08-v1`로 다음 항목을 적용한다.

- 액티브 alpha 평균·변동성
- fully-funded swap funding spread
- 거래상대방 hazard, recovery, 무담보 노출 비율로 계산한 기대손실

이 수치들은 운용사 전망이나 공시값이 아니라 재현 가능한 deterministic model
assumption이다. gross alpha 평균 11.8bp에서 총보수 5.94bp, swap funding 0.8bp,
거래상대방 기대손실 약 0.06bp를 각각 한 번 차감해 장기 net benchmark excess 평균을
약 5bp로 calibration한다. 이는 과거 1년 표본을 본뜬 모델 기준일 뿐 미래 성과 보장이
아니다. KOFR 이자수익은 가격에 다시 더하지 않고, 분배 가능 재원의 귀속만
별도 원장에 기록하므로 총수익과 분배를 이중계상하지 않는다.

월말 분배의 미공표 미래 지급일은 record date 뒤 2개 KRX 영업일로 투영한다. 시작
전에 이미 공표된 2026년 1~7월 금액·기준일·지급일은
[삼성자산운용 분배 API](https://m.samsungfund.com/api/v1/kodex/divid-info.do?id=2ETFG6)로
고정한다. 가장 최근 7월 건은 [삼성자산운용 공지](https://m.samsungfund.com/etf/lounge/notice-view.do?no=78273)에
따라 2026-07-30 배당락, 2026-07-31 기준일, 2026-08-04 지급, 좌당 242원이다.
미공표 미래 금액은 mutable NAV나 적립금에서 가져오지 않고, 카탈로그에 버전으로
고정한 성장률과 날짜별 변동 가정으로 결정론적으로 투영한다. 이 금액은 운용사
공시나 수익률 전망이 아니며, 자세한 권리 확정·미수금·지급·저장 검증 계약은
[ETF 분배 lifecycle](./etf-distribution-lifecycle.md)에 기록한다.
