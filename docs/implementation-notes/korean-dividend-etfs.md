# 한국 배당 ETF 두 종목 구현

## KODEX 금융고배당TOP10 (0089D0)

0089D0은 코스피 200 금융 고배당 TOP 10 지수를 추종한다. 실행 정책은 KOSPI 200 금융
후보, 상장기간, 연속배당·배당성향, 유동성, ROE와 PBR 심사를 거쳐 배당수익률 상위 13개,
평균시가총액 상위 10개를 선택한다. 직전 4분기 현금배당총액으로 가중하고 단일종목 25%
cap을 반복 재배분한다.

6월·12월 정기변경과 수시 교체는 모두 T/T+1/T+2에 신규 30/70/100%, 편출
70/30/0%로 이동한다. 합병·상장종료에 따른 예비종목 교체도 같은 단계 원장으로 실행하며,
spin-off 임시 line은 별도 기업행동으로 처리한다.

## TIGER 코리아배당다우존스 (0052D0)

0052D0은 Dow Jones Korea Dividend 30 Price Return 지수를 추종한다. S&P Korea BMI
합성 후보에서 10년 연속배당, 총시가총액과 3개월 MDVT를 심사하고 indicated yield 상위
절반을 남긴다. FCF/부채, ROE, indicated yield, 5년 배당성장률 순위를 같은 비중으로
합성해 기존종목 top-40 buffer를 적용하고 30개를 선정한다. 유동시가총액 가중의 목표
단일종목 cap은 4%다.

6월·12월에는 구성과 비중을 바꾸고 3월·9월에는 비중만 조정한다. 예정 배당 누락·무기한
중단의 월별 review, 상장종료·파산·합병·spin-off를 별도 유지관리 경로로 처리한다. 정기
사이 삭제는 원칙적으로 즉시 대체하지 않으며, 일시적인 종목수 감소와 cap 초과를 명시적
interim 상태로 허용한다.

## 상품 계층과 합성 경계

두 ETF 모두 월 15일(비영업일이면 직전 KRX 영업일) 지급기준일을 사용하지만 실제
지급일은 별도다. 공표 금액은 확정 일정으로, 미공표 금액은 카탈로그 버전의 결정론적
projection으로 처리한다. 총보수·기타비용과 별도 거래비용은 한 번씩만 가격에 반영한다.

실제 KOSPI 200/S&P Korea BMI 구성, point-in-time 재무·배당과 미래 운용사 판단은
라이선스 데이터이므로 한국 전용 합성 유니버스를 사용한다. 미국 방법론의 후보나 휴장일을
섞지 않으며 KRX/KSD 계산형 달력과 원화 기준을 사용한다.

## 공식 자료

- [삼성자산운용 0089D0](https://www.samsungfund.com/etf/product/view.do?id=2ETFS1)
- [KRX 코스피 200 금융 고배당 TOP 10](https://index.krx.co.kr/contents/MKD/03/0304/03040101/MKD03040101.jsp?upmidCd=0104&idxCd=1359&idxId=K2D04P)
- [미래에셋 TIGER 0052D0](https://www.tigeretf.com/ko/product/search/detail/index.do?ksdFund=KR70052D0006)
- [S&P DJI Dividend Indices 방법론](https://www.spglobal.com/spdji/en/documents/methodologies/methodology-dj-dividend-indices.pdf)

