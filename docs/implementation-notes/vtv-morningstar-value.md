# VTV / Morningstar US Large Cap Value 구현

## 상품과 지수

VTV는 2026-07-29부터 Vanguard Morningstar Value ETF라는 상품명을 사용하며,
방법론 명칭이 변경된 Morningstar US Large Cap Value Index를 추종한다. ticker와 상품
계약의 연속성은 유지한다. 카탈로그는 총보수, 현물 패시브 운용, total-return benchmark와
분기 분배를 서로 다른 계층으로 기록한다.

## 분기 경로 상태

Morningstar 방법론의 50% size/style packet은 현재 VTV 편입종목만 저장해서는 다음 분기에
재현할 수 없다. 따라서 전체 합성 미국 후보의 large/mid/small/micro 크기 배분과 각 크기별
value/growth 배분, 재진입 상태를 영구 방법론 경로로 보존한다. 분기 ranking은 다음을
결정론적으로 실행한다.

- 적격·투자가능성 및 125거래일 유동성 심사
- 전체 적격 회사 시가총액을 사용한 size breakpoint와 50% packet 이동
- 공개된 11개 value/growth factor의 winsorization, z-score와 cap-weighted style rank
- 유동주식수의 EFF 반올림과 size/style multiplier 가중
- RIC 25/50 test, ranking buffer와 공개 최소제곱 제약 최적화

분기별 size/style packet 이동은 ranking 사이의 상태 변화이고, 지수 보유량을 5개 종가에
걸쳐 옮기는 5일 transition과 별개다. 저장 상태는 두 경로를 구분하며, transition 중
기업행동이 발생하면 남은 단계 전체를 새 final basket으로 다시 만든다.

라이선스 실제 구성, 애널리스트 전망치, Morningstar의 가격일 선택과 위원회 판단은 공개
자료만으로 재현할 수 없어 버전 고정 합성 신호와 명시적 proxy를 사용한다. 공개 규칙을
실행한다는 의미이지 미래 실제 포트폴리오를 예측한다는 의미는 아니다.

## 분배와 공식 자료

VTV는 VOO와 4분기 날짜까지 항상 같지 않으므로 전용 Vanguard 일정이 있다. 공표된 날짜를
우선하고 미래는 독립 projection을 사용한다.

- [Vanguard VTV 상품 페이지](https://investor.vanguard.com/investment-products/etfs/profile/vtv)
- [Morningstar US Large Cap Value Index](https://indexes.morningstar.com/indexes/details/morningstar-us-large-cap-value-FS00009VTJ)
- [Morningstar US Market Indexes 방법론](https://indexes.morningstar.com/api/docs/6a64c1efd7ce7b357809b99e)
- [Vanguard 지수명 변경 안내](https://corporate.vanguard.com/content/corporatesite/us/en/corp/who-we-are/pressroom/press-release-vanguard-to-update-names-of-us-equity-index-funds-tracking-morningstar-indexes-042926.html)

