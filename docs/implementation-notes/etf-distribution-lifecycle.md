# ETF 분배 lifecycle

## 회계 경계

ETF 분배는 하나의 `dividend date`로 합치지 않고 세 날짜를 별도 상태로 유지한다.

1. `exDate`: 상품 시장의 현지 날짜가 바뀌는 경계에서 좌당 NAV와 시세를 분배액만큼 낮추고, 그 직전 보유 수량으로 현금 권리를 확정한다.
2. `recordDate`: 운용사 공시의 기준일을 보존하는 법적 메타데이터다. 이 날 보유량을 다시 읽지 않는다.
3. `payDate`: 확정 권리를 gross 미수금에서 제거하고, net 현금과 원천징수 세금 원장으로 바꾼다. ex-date 뒤 매도해도 권리는 유지된다.

ex-date부터 pay-date까지의 gross 권리는 주문 가능 현금이 아니라 통화별 비현금 미수금이다. 따라서 총자산·포트폴리오 스냅샷·일별 수익률에는 포함하지만 주문 가능 현금에는 포함하지 않는다. 지급 때 `gross receivable → net cash + withholding tax`로 전환되어 총자산은 세금만큼만 줄어든다.

## 일정과 금액의 정본

`DistributionPolicy.announcedDistributions`가 날짜와 금액의 최우선 정본이다. `skip=true`는 해당 분배를 만들지 않으며, 공표된 좌당 금액은 그대로 적용한다. 2026년 SCHD·VOO·VTV와 한국 ETF의 공표 일정은 카탈로그에 명시적으로 고정했다. 이후 미공표 일정은 각 상품 달력과 시장 영업일을 사용한다. 한국 월 15일·월말 상품은 T-1 분배락, T 기준일, 상품별 영업일 지급 지연을 적용한다.

미공표 좌당 금액은 mutable NAV나 적립금에서 읽지 않는다. 다음 pure projection을 runtime과 validator가 같은 함수로 계산한다.

```text
anchor = initialPrice × catalogDividendYield ÷ periodsPerYear
growth = (1 + projectedAnnualNominalGrowthRate)^(exYear - 2026)
variation = 1 + boundedHash(projectionAssumption, stockId, exDate)
grossPerUnit = currencyRound(anchor × growth × variation)
```

주식 ETF는 연 2% 명목 성장과 ±5% manager-decision 변동, KOFR 금리형 ETF는 성장 0%와 ±15% 금리·운용 판단 변동을 사용한다. KRW는 원 단위, USD는 소수 넷째 자리로 반올림한다. 이 값은 공식 공시나 수익률 전망이 아니라 저장 재생과 변조 검증을 위한 버전된 게임 가정이다. 표시 분배수익률이 0이면 projection도 정확히 0이며 NAV 조정과 권리를 만들지 않는다.

`FundFinancialState.accruedDistributionPerUnit`는 분배재원의 income/ROC 성격을 추적하는 memo reserve다. 시작 시 직전 canonical ex-date 이후 완료된 실제 정규장 시간만 적립하고, 휴장일과 조기폐장을 반영한다. 분배 때 차감하되 이 mutable 값은 지급액의 법적 정본으로 사용하지 않는다.

## 권리 계보와 저장 검증

권리가 생기면 `DistributionEntitlementOrigin`을 영구 보존한다. origin은 ex-date 현지 자정, 전역 회계 순번, 금액 basis, 분배 전 적립 memo와 NAV 전후를 기록한다. 미지급 권리는 `originId`를 참조하고, 지급 뒤에도 origin은 배당 원장의 근거로 남는다.

Validator는 origin에 복제된 보유량을 신뢰하지 않는다. origin 시각 전의 `Trade` 매수·매도와 `CorporateActionRecord.quantityMultiplier`를 전역 회계 순서대로 재생해 ex-date 권리 수량을 독립 산출한다. 공표 금액은 카탈로그와 exact 비교하고, 미공표 금액은 위 pure projection을 다시 계산한다. 따라서 pending 권리만 바꾸는 변조와 origin까지 함께 바꾸는 금액 변조를 모두 거부한다. `(stockId, exDate)`는 pending·paid 전체에서 한 번만 존재할 수 있다.

현재 게임 저장 schema 49는 pending entitlement, 영구 origin, ex-date ROC 원가조정,
gross 미수금, 지급 원장과 현금 조정 계보를 strict exact-field 방식으로 읽는다. 날짜는
`exDate ≤ recordDate ≤ payDate`, 금액·수량·세율은 bounded finite 값이어야 하며, 현재
스냅샷의 미수금 합계도 pending 원장과 일치해야 한다.

`distributionCoverageRatio`는 상품의 경제적 분배재원 신호이지 모든 시장에서 곧바로 세무상
ROC 비율이 아니다. 현 세무 모형은 미국 상장 `FOREIGN_LISTED` open-end ETF와 CEF에만
coverage가 1보다 작은 synthetic ROC를 허용한다. 한국 상장 `KOREAN_DOMESTIC_EQUITY`·
`KOREAN_OTHER` ETF와 미국 ETN은 behavior coverage를 세무 ROC로 사용하지 않는다. 한국 ETF는 국내
과세표준·원천징수 규칙을 적용하되 세무 coverage는 항상 1이고, FIFO 원가 감소와 초과 ROC
양도이익을 만들지 않는다.

미국 ROC 대상 상품은 ex-date에 권리가 생긴 당시 수량과 FIFO lot에만 ROC를 즉시 적용한다.
origin은 권리 수량, coverage, ex-date 관측 원가환산 FX, ROC, 초과 ROC를 영구 보존한다.
지급일에는 현금·원천징수용 FX만 관측하고 FIFO 원가를 다시 낮추지 않는다. 따라서 ex-date 뒤
매도는 이미 낮아진 원가를 사용하며, ex-date 뒤 신규 매수 lot은 이전 권리의 ROC 영향을 받지
않는다. 초과 ROC는 `FOREIGN_STANDARD` 미국 해외주식 양도이익으로 분류하고 지급연도에 귀속한다.

## 기본 정책 경계

명시적 `distributionPolicy`가 없는 기존 ETF는 `DistributionPolicy.DEFAULT`를 사용한다. 기존 `distributionCalendar`와 `distributionFrequency`는 유지하고, 지급 지연 0일·명목 성장 0%·변동 0%인 결정론적 금액을 적용한다. 이는 레거시 상품의 날짜 동작을 보존하기 위한 fallback이지 운용사 공시를 뜻하지 않는다. 명시된 announcement가 생기면 언제나 default projection보다 우선한다.

## 공식 자료

- [Schwab SCHD 상품·분배 정보](https://www.schwabassetmanagement.com/products/schd)
- [Vanguard VOO 상품 정보](https://investor.vanguard.com/investment-products/etfs/profile/voo)
- [Vanguard VTV 상품 정보](https://investor.vanguard.com/investment-products/etfs/profile/vtv)
- [삼성 KODEX KOFR금리액티브 상품 정보](https://m.samsungfund.com/etf/product/view.do?id=2ETFG6)
- [삼성 KODEX KOFR 분배 API](https://m.samsungfund.com/api/v1/kodex/divid-info.do?id=2ETFG6)
- [삼성 KODEX 금융고배당TOP10 상품 정보](https://www.samsungfund.com/etf/product/view.do?id=2ETFS1)
- [미래에셋 TIGER 코리아배당다우존스 상품 정보](https://investments.miraeasset.com/tigeretf/ko/product/search/detail/index.do?ksdFund=KR70052D0006)
- [S&P DJI 배당지수 방법론](https://www.spglobal.com/spdji/en/documents/methodologies/methodology-dj-dividend-indices.pdf)
