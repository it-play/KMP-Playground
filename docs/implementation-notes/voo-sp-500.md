# VOO / S&P 500 구현

## 실행 범위

VOO는 Vanguard S&P 500 ETF이며 카탈로그에서 패시브 현물 복제, 상품 비용과 분기
분배를 S&P 500 기준 포트폴리오와 분리한다. 기준 포트폴리오는 미국 대형주 적격성,
위원회 선정 proxy, 유동시가총액 가중, 정기·수시 기업행동 및 구성 변경을 버전이 고정된
방법론으로 실행한다.

실제 S&P 위원회 판단, 라이선스 구성종목과 point-in-time 유동주식수는 공개 자료만으로
미래까지 재현할 수 없다. 게임은 별도 미국 합성 유니버스와 결정론적 위원회 proxy를
사용하며, 이를 미래 실제 편입 예측으로 취급하지 않는다.

## 기업행동과 분배

합병·상장종료·분할·spin-off는 발표일, 효력일, 대기 계획과 적용 원장으로 분리한다.
spin-off와 정기 재가중이 같은 효력일이어도 전일 기준 basket을 잃지 않고 정해진 순서로
둘 다 반영한다. 저장 검증은 대기 계획과 적용 원장을 같은 시드의 canonical 재생 결과에
결박한다.

VOO 분배는 다른 Vanguard ETF와 달라질 수 있으므로 전용 일정으로 계산한다. 공표된
2026년 ex/record/pay 날짜는 확정값으로 고정하고 이후는 별도 Vanguard 달력 projection을
사용한다. 분배락일 권리, 지급 전 미수금, 지급일 원천징수와 순현금의 처리는
[ETF 분배 lifecycle](./etf-distribution-lifecycle.md)을 따른다.

## 공식 자료

- [Vanguard VOO 상품 페이지](https://investor.vanguard.com/investment-products/etfs/profile/voo)
- [S&P U.S. Indices 방법론](https://www.spglobal.com/spdji/en/documents/methodologies/methodology-sp-us-indices.pdf)
- [S&P Equity Indices Policies & Practices](https://www.spglobal.com/spdji/en/documents/methodologies/methodology-sp-equity-indices-policies-practices.pdf)

