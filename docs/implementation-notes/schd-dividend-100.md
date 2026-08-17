# SCHD / Dow Jones U.S. Dividend 100 구현

## 상품과 기준지수

SCHD는 Schwab U.S. Dividend Equity ETF이며 Dow Jones U.S. Dividend 100 Index를 추종한다.
카탈로그는 상품의 패시브 현물 운용, 총보수, 분기 분배와 기준지수의 total-return 성격을
서로 구분한다. 기준 포트폴리오의 가격수익과 구성종목 배당수익은 엔진에서 한 번만 NAV에
반영하고, ETF 분배 시에는 누적 재원을 NAV에서 차감해 투자자의 현금 권리로 옮긴다.

## 방법론 경계

내장 정책은 미국 광범위 주식 유니버스에서 REIT를 제외하고 배당 지속성, 시가총액과
유동성을 심사한 뒤 현금흐름 대비 부채, ROE, 배당수익률, 5년 배당성장률을 같은 비중으로
순위화한다. 기존 종목 buffer, 100종목 목표, 유동시가총액 가중과 단일종목 cap, 연간
재구성·분기 재가중 및 기업행동을 별도 원장으로 실행한다.

실제 구성종목, point-in-time 재무자료와 위원회 판단은 라이선스 데이터이므로 게임은
버전이 고정된 합성 미국 유니버스와 결정론적 신호를 사용한다. 공개 규칙은 실행하지만
미래 실제 편입 종목을 예측한다는 의미는 아니다.

## 분배 일정

SCHD는 범용 분기 15일 규칙을 사용하지 않는다. 2026년 공표된 ex/record/pay 날짜는
카탈로그의 확정 일정으로 고정하고, 이후 연도는 Schwab 분기 일정의 결정론적 달력
projection을 사용한다. 분배락일 보유 수량을 영구 origin에 고정하고 지급일까지 미수금으로
보존하므로, 그 사이 매도해도 지급 권리는 유지된다. 지급일에는 통화 최소단위로 반올림한
총액에서 원천징수를 계산해 순현금을 입금한다.

## 공식 자료

- [Schwab SCHD 상품 페이지](https://www.schwabassetmanagement.com/products/schd)
- [Schwab ETF 분배 일정](https://www.schwabassetmanagement.com/resource/schwab-equity-etfs-distribution-schedule-2026)
- [S&P DJI 지수 방법론](https://www.spglobal.com/spdji/en/documents/methodologies/methodology-dj-dividend-indices.pdf)

