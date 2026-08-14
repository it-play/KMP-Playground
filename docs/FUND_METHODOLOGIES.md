# 펀드 방법론 엔진

펀드 방법론 프로필은 ETF·ETN·폐쇄형 펀드(CEF)를 하나의 정적 주가 공식으로만 다루지 않고,
파일에 선언된 운용 규칙을 장기 시뮬레이션 상태에 연결하는 경계다. 실제 구성종목을 선정하는 세부
공식 구성종목 선정 규칙을 그대로 재생하는 엔진은 현재 SCHD 한 종류다. 그 밖의 상품도 암묵적인
coarse 가격 경로에 남겨 두지 않고 주식 기준 정책, 금리곡선, 원자재, 선물, 재간접, 합성 sleeve와
대체위험 프리미엄 중 하나의 typed 실행 계약을 가진다. 미래의 실제 편입 종목을 예언하는 것이 아니라,
공개된 방법론과 출처가 구분된 모델 가정을 결정적 게임 유니버스에 적용하는 것이 목표다.

## 현재 지원 범위

기본 카탈로그의 fund-like 종목은 ETF 500개, CEF 5개, ETN 3개로 모두 508개다.
스키마 v3에서는 508개 모두가 법적 구조·기준 노출·수익률 변환을 담은 `fundProductProfile`을
정확히 하나씩 가진다. 루트의 벤치마크 레지스트리는 501개이며 지원 수준은 다음과 같다.

| 벤치마크 지원 수준 | 정의 수 | 현재 동작 |
|---|---:|---|
| `VERIFIED_RULES` | 1 | SCHD의 공식 배당·펀더멘털 선정, 정기 재구성, 비중 상한과 표류를 실행한다. |
| `VERIFIED_REFERENCE` | 12 | 상품과 기준지수·참조의 연결을 1차 출처로 확인했지만 전체 구성 규칙 재현을 뜻하지 않는다. |
| `PROVISIONAL_PROXY` | 488 | 상품 설명과 전략을 typed 정책으로 옮겼지만 공식 방법론으로 단정하지 않는 결정적 게임 프록시다. |

엔진 종류별 정의 수는 다음과 같다. 합계가 501이며 `COARSE_FACTOR_PROXY`는 0개다.

| 실행 종류 | 정의 수 | 역할 |
|---|---:|---|
| `EQUITY_METHODOLOGY` | 1 | SCHD의 검증된 상세 선정·재구성 규칙 |
| `EQUITY_REFERENCE` | 342 | 지역·국가·섹터·스타일·테마·선정/재가중 달력 기반 대표 포트폴리오 |
| `FIXED_INCOME_CURVE` | 124 | 금리곡선·듀레이션·신용·변동금리/실질금리 기준수익률 |
| `COMMODITY_SPOT` / `FUTURES_CURVE` | 4 / 7 | 현물 carry 또는 선물 만기곡선·롤·담보수익 |
| `FUND_OF_FUNDS_METHODOLOGY` | 3 | PCEF·YYY·YMAX 후보 선정과 재가중 |
| `COMPOSITE_REFERENCE` | 17 | 서명된 benchmark/종목 sleeve, band·전술·위험·듀레이션 규칙 |
| `ALTERNATIVE_RISK_PREMIA` | 3 | IALT가 소비하는 시장중립·신용상대가치·글로벌매크로 driver |

`FIXED_INCOME` 상품 99개와 `CASH` 상품 18개는 모두 typed 고정수익 기준을 참조한다. 124개
`FIXED_INCOME_CURVE` 정의에는 이 상품용 공유 기준과 복합·재간접 엔진만 소비하는 component-only
기준도 포함된다. 공식 1차 상품 자료가 있어도 실행용 듀레이션·spread가 공시값이 아니면
`CALIBRATED_ASSUMPTION`으로 남긴다. legacy `behaviorProfile.durationYears`를 공식값으로 승격한 것이 아니다.

별도로 일일 레버리지·인버스 11개는 `dailyResetTerms`에 따라 기준수익률을 매 거래일 목표
배율로 다시 고정한다. 이 중 10개 상품의 목표 배율·기초 참조는 발행사 자료로 확인했고,
`KOSPI:0195S0`은 직접 상품 URL을 확인하기 전까지 `MODEL_ASSUMPTION`이다. 11개 모두의 연 0.5%
자금조달 스프레드와 담보수익 참여율 100%는 공식 공시값이 아니라
`CALIBRATED_ASSUMPTION`으로 명시한다.

옵션 전략은 ETF 23개와 옵션 프리미엄 연계 ETN 3개, 모두 26개가 typed
`optionStrategyTerms`를 가진다. 현재 strike·tenor·프리미엄 모수는 전부 상품별 공식 계약값으로
검증한 것이 아니므로 `MODEL_ASSUMPTION`과 calibration ID로 표시한다. 일일 reset과 옵션 전략은
운용 순서가 다른 별도 오버레이이며 한 상품에 동시에 둘 수 없다. QLD와 TQQQ의 기준수익률은
발행사 문서의 daily price-return 표현에 따라 `PRICE_RETURN`으로 확인했고, 나머지 일일 reset 9개는
배당·쿠폰 포함 여부를 1차 자료로 확정하기 전까지 `UNVERIFIED`다.

CSHI·USDX 2개는 위 26개와 별도의 `cashCollateralizedPutSpreadTerms`를 사용한다. 현금성 기준과
옵션 기초를 따로 참조하고, 현금 carry와 풋스프레드 공정가치·정산·roll을 한 번씩만 반영한다.
`INSTRUMENT`를 직접 기초로 쓰는 daily-reset·옵션·현금담보 상품은
`directReferenceTerminationRule`을 반드시 선언한다. 기초기업이 청산·상장 종료 단계에 들어가면
같은 최종 종가에 기존 옵션 패키지만 공정가치로 정산하고 새 주기를 열지 않은 뒤 상품 청산을
시작한다. 기본 팩의 직접참조 상품 4개는 이 종료 정책 자체를 공식 계약으로 확정하지 못했으므로
`MODEL_ASSUMPTION`과 버전된 assumption ID로 표시한다.
현재 직접참조 상품은 상품·기초기업의 상장시장과 reset/roll calendar가 모두 같아야 한다. 이는
2040년 마지막 종가 뒤 다른 거래소의 청산 종가가 필요해지는 실행 불가능한 모드 계약을 막는다.

ETN 3개는 만기·수수료·상환·쿠폰·조기상환 조건과 발행자 신용 모델을 분리한다. UBS 발행자
스프레드·hazard·회수율·평균회귀·충격 변동성은 세 상품이 같은 issuer ID와 calibration을
공유하지만 공식 계약값이 아닌 `CALIBRATED_ASSUMPTION`이다. CEF 5개는 법적 구조 조건과
할인율·레버리지 시장 모수를 분리한다. BLW·PHK·GOF·TYG는 초기 debt/gross 20%, HIO는 0%로
시작하며 차입 스프레드와 할인율 충격도 명시적 게임 calibration이다.

`COARSE_FACTOR_PROXY = 0`은 508개 상품의 실제 holdings와 계약을 전부 검증했다는 뜻이 아니다.
현재 488개 벤치마크가 `PROVISIONAL_PROXY`이며, 그 typed 정책과 calibration은 게임 실행을 위한
명시적 가정이다. 알지 못하는 미래 실제 편입종목을 고정 목록으로 만들어 정확한 것처럼 보이는 대신,
공식 원문을 확인한 규칙부터 버전된 방법론으로 승격한다.

## 실행 구조

```text
종목팩 루트의 benchmarks와 종목별 fundProductProfile
→ (id, version) 참조를 검증하고 구성요소 우선 DAG 순서로 고정
→ VERIFIED_RULES인 equityMethodology를 비거래 기준자산 후보군에 적용
→ 방법론/지수 기준 구성·표류 비중·예약된 계획을 ReferencePortfolioState에 보관
→ 구성종목별 시장·섹터·펀더멘털 반응을 비중 합성
→ EQUITY_REFERENCE는 지역·국가·섹터·스타일 정책으로 결정적 대표 포트폴리오를 운용
→ FIXED_INCOME_CURVE는 국가별 금리곡선·듀레이션·신용 구간·변동금리/실질금리 축을 합성
→ 원자재·선물·재간접·합성·대체위험 프리미엄 엔진을 component-first 순서로 평가
→ 일일 reset 또는 옵션 전략, 추적오차·복제 방식 같은 상품 오버레이를 적용
→ 상품 보수·FX, ETN 계약·발행자 신용, CEF 자본구조·할인 등 효과를 분리해 시장가격 생성
```

`ReferencePortfolioState`의 포지션은 실제 ETF 회계 원장의 전체 holdings가 아니라 방법론/지수의
**reference portfolio**다. ETF 보수와 추적오차는 기준 포트폴리오 비중과 별도의 상품 효과이며,
실제 펀드가 가질 수 있는 현금, 미결제 거래, 증권대여 담보와 설정·환매 바스켓은 현재 상태에 넣지 않는다.

### 전용 주식 방법론 프레임워크

`EQUITY_METHODOLOGY`는 더 이상 SCHD 전용 enum 조합을 공용 엔진이 해석하는 형태가 아니다.
종목팩의 `methodologyRef = (ownerSourceId, methodologyId, version)`가 불변 등록부의 실행 provider를
가리키고, provider가 자기 파라미터와 일정·선정·가중·특별 편출 규칙을 검증하고 실행한다. SCHD의
`builtin:base/schd-dividend-100@v1`이 이 계약을 사용하는 첫 번째 provider다. 아직 등록된 두 번째
실제 상품 방법론이 있다는 뜻은 아니다.

패키지 경계는 다음처럼 고정한다.

```text
domain/model/methodology      # JSON에도 나타나는 ref와 bounded typed parameters
domain/methodology            # 공개 policy·schedule·input/output·component·registry SPI
domain/methodology/builtin    # 앱이 소유하는 SCHD 등 내장 provider 구현과 등록
modding/api/content           # 별도로 신뢰한 실행 모드용 pre-game 등록 API
domain/simulation/fund        # 등록된 policy를 소비하는 reference portfolio 런타임
```

공개 SPI는 다음 경계를 갖는다.

- `EquityMethodologyPolicy`: 정의 검증, 후보 선정, 목표 비중, 특별 편출을 구현한다.
- `EquityMethodologySchedule`: 방법론이 사용하는 시장, 지역, 기준일·효력일과 거래 세션을 소유한다.
- selection·weighting·removal input과 candidate·selection은 불변 typed 값만 전달한다. 선정 input은
  canonical scheduled action을, 비중 input은 action kind·관찰일·효력일을 함께 받아 같은 provider도
  재구성·정기 재조정·제약 재조정별로 다른 규칙을 구현할 수 있다.
- candidate의 공통 identity·sector와 bounded typed feature map을 분리한다. 현재 host adapter는
  시가총액·유동성·배당·FCF/부채·ROE·배당성장·배당 프로그램 중단 feature ID를 제공하며, 새 universe adapter는
  안정적인 feature ID를 버전 계약으로 추가할 수 있다. provider가 컴파일 시 선언한 feature만 실제
  candidate에 노출하므로 미선언 신호에 묵시적으로 의존할 수 없다.
- `EquityMethodologyComponentCatalog`은 동일/비례 비중, ordinal/composite rank, incumbent buffer,
  종목·그룹 상한 배분처럼 검증 가능한 순수 결정적 부품을 재사용하게 한다.
- provider가 돌려준 ID·순위·비중은 호스트가 다시 검증한다. 등록된 코드라는 이유만으로 캠페인
  상태를 직접 바꿀 수는 없다.
- 호스트는 provider 일정의 요청 kind·날짜·거래일·strict progress와 canonical round-trip을 검증하고,
  두 정기 lane과 특별 검토를 bounded하게 2040년까지 사전 실행한다.

코드 패키지는 책임별로 분리한다.

- `domain.model.methodology`: 버전된 ref와 bounded typed parameter wire model
- `domain.methodology`: 공개 policy·schedule·입출력 SPI, 재사용 컴포넌트와 불변 등록부
- `domain.methodology.builtin`: 앱에 포함된 개별 상품/지수 provider
- `domain.simulation.fund`: 등록 provider를 상태·계획·원장으로 실행하는 host compiler와 엔진
- `modding.api.content`: 별도로 신뢰한 실행 모드가 캠페인 전에 쓰는 capability-scoped 공개 API
- `domain.data`와 desktop parser: 선언형 팩, provider ref와 최종 카탈로그 결속

등록부는 카탈로그를 만들기 전에 한 번 동결되며 같은 ref의 교체·중복 등록을 허용하지 않는다.
엄격 파서, 사전검증기와 실제 런타임은 같은 등록부와 같은 provider를 사용한다. 따라서 JSON이
알려지지 않은 ref를 선언하거나 provider 검증에 실패하면 게임을 시작하기 전에 팩 전체를 거부한다.
`ownerSourceId`는 provider 등록 namespace이며 소비 벤치마크 팩의 source ID일 필요는 없다. 데이터
팩도 이미 설치된 provider를 그 provider가 허용하는 프로필로 참조할 수 있다.

### 2,500개 비거래 기준주식

`US_BROAD_EQUITY` 후보군은 플레이어가 거래하는 기업 종목과 분리된 2,500개의 시뮬레이션
기준주식으로 구성된다. ID는 `REF:US-BROAD:0001`부터 `REF:US-BROAD:2500`까지이고,
표시 심볼 `SIM0001` 등은 게임 내부 식별자다.

- 실제 현재 SCHD 구성종목 목록이 아니다.
- 2027~2040년의 실제 미래 편입·편출을 예측한 목록이 아니다.
- 캠페인 seed와 자산 ID·연도를 키로 시가총액, 거래대금, 배당 연속 이력, 배당률,
  자유현금흐름/부채, ROE와 5년 배당성장률을 결정적으로 생성한다.
- 같은 seed·방법론 버전·시간은 항상 같은 후보군, 순위, 수익률과 재구성 결과를 만든다.

현재 실행 host v1은 `US_BROAD_EQUITY`, USD 기준, 미국 시장 schedule과 두 개의 정기 일정 lane
(`SCHEDULED_RECONSTITUTION`, `SCHEDULED_REWEIGHT`)만 명시적으로 지원한다. 후보 adapter가 제공하는
feature도 위에 열거한 배당·펀더멘털 집합으로 제한된다. 따라서 S&P 500, Nasdaq-100처럼 실적 이력,
상장·본점 국가, free-float, 수시 교체 등 다른 관측치와 이벤트가 필요한 방법론은 이름만 다른
provider로 지금 등록할 수 없다. 필요한 feature와 일정 lane을 host에 먼저 추가한 뒤 같은 등록
구조를 사용해야 한다. 글로벌 지수는 후보 universe·통화·calendar adapter도 함께 확장해야 한다.
실제 상표를 붙이거나 실제 편입 이력으로 오인할 수 있는 표시를 사용하지 않는다.

### 재구성과 가격 전파

- 연례 원장의 선정일은 2월 세 번째 금요일이다. 배당 지급 이력과 IAD 배당수익률을 제외한
  펀더멘털은 전년도 마지막 영업일을 기준으로 하고, 그때 이용 가능했던 데이터만 쓴다. 유동
  시가총액(FMC), 최근 3개월 평균 일거래대금(ADVT), indicated annual dividend(IAD)와
  배당수익률 가격은 선정일인 2월 세 번째 금요일을 기준으로 한다.
- 연례 재구성은 신규 편입과 편출을 허용하고 기존 종목에 순위 버퍼를 적용한다. 비중 가격 기준일은
  3월 효력일 12 미국 거래일 전이며, 결과는 3월 세 번째 금요일 다음 월요일 장 시작에 효력이 생긴다.
- 6·9·12월 분기 재조정은 구성종목을 바꾸지 않고 목표 비중만 다시 계산한다. 기준 데이터와 가격은
  첫 번째 금요일 전 수요일 장 마감 기준이며, 결과는 세 번째 금요일 다음 월요일 장 시작에 효력이 생긴다.
- 기준일에 만든 계획 포지션도 효력일까지 같은 기준자산 수익률로 표류한다. 따라서 기준일의 목표 비중과
  효력일에 실제 적용되는 비중은 시장 움직임 때문에 다를 수 있다.
- 효력일에 시장 전체가 중단되어 기준자산 수익률이 0이어도 구성 변경 자체는 미국 정규장 개장 구간에
  적용하고 원장을 남긴다. 거래 재개를 기다리며 정기 일정을 뒤로 미루지 않는다.
- 종목별 목표 상한은 4%, GICS 호환 섹터별 목표 상한은 25%다.
- 일일 검사에서 4.7%를 초과한 종목의 비중 합이 22%를 초과하면 T+2 미국 거래일에 상한 재조정을
  적용한다. 3·6·9·12월에는 두 번째 금요일 전 수요일 장 마감부터 세 번째 금요일 다음 월요일 장
  마감까지 이 일일 절차를 동결한다.
- 월말 검사는 결정적으로 생성된 배당 지급 중단·취소를 발견하면 다음 달 첫 미국 거래일에 해당 종목을
  대체 없이 편출한다. 일반적인 배당 감액은 지급 이력을 0으로 만들거나 이 월별 편출을 일으키지 않는다.
- 종목별 수익률은 미국 시장, 섹터, 품질·가치·배당 틸트, 변동성 레짐과 종목별 잔차에 반응한다.
- 현재 비중은 수익률에 따라 매 구간 표류하고, 재조정 때 목표 비중으로 돌아간다.
- 재구성 원장에는 방법론 ID·버전, 선정일·실효일, 편입·편출 ID, 전후 구성 해시,
  one-way turnover와 revision을 남긴다.

`targetConstituentCount = 100`은 연례 재구성의 목표이지 매일 지켜지는 고정 holdings 수가 아니다.
월별 지급 중단·취소 편출에는 대체 편입이 없으므로 다음 연례 재구성까지 99개 등으로 줄어들 수 있다.
반대로 Schwab의 2026-07-28 상품 스냅샷에 표시된 실제 SCHD holdings 103개는 실제
펀드의 당시 스냅샷이며, 게임의 100개 목표 reference portfolio와 같은 수치일 필요가 없다.

## SCHD 방법론 v1

SCHD는 Dow Jones U.S. Dividend 100 Index를 추종하는 물리적 지수 ETF로 모델링한다.
게임 프로필 v1의 `effectiveFrom = 2026-03-23`은 캠페인 이전에 완료된 2026년 연례 재구성을
결정적으로 재생하기 위한 부트스트랩 효력일이며, 공식 방법론 전체나 개별 조항의 시행일을 뜻하지 않는다.
캠페인은 이 파일에 고정된 규칙을 2040년 말까지 적용한다. 공식 변경 이력의 `2026-03-23`
사업활동 스크린은 별도 상품인 **S&P U.S. Dividend 100 Index**에 해당하므로 SCHD 선정 규칙에
적용하지 않는다. `2026-04-29`의 구성종목 간 인수합병 처리도 현재 엔진의 미구현 영역이다.

공식 근거:

- [Schwab SCHD 상품 페이지](https://www.schwabassetmanagement.com/products/schd)
- [S&P Dow Jones Indices 지수 개요](https://www.spglobal.com/spdji/en/indices/dividends-factors/dow-jones-us-dividend-100-index/)
- [S&P Dow Jones Indices Dividend Indices Methodology PDF](https://www.spglobal.com/spdji/en/documents/methodologies/methodology-dj-dividend-indices.pdf)
- [S&P Dow Jones Indices Index Mathematics Methodology](https://www.spglobal.com/spdji/en/methodology/article/index-mathematics-methodology/)

기본 카탈로그 값은 실시간 시세가 아니라 위 Schwab 상품 페이지를 고정한 게임 시작 baseline이다.

- 2026-07-28 기준: 가격 USD 33.94, 총 순자산 USD 104,754,277,041.23, 발행좌수 3,092,400,000.
- 2026-06-30 기준: TTM 분배수익률 3.30%, 3년 표준편차 13.32%, 기준지수 대비 3년 베타 1.00.
  연 보수는 0.06%다.

| 규칙 | v1 값 | 엔진 적용 |
|---|---:|---|
| 목표 종목 수 | 100 | 연례 재구성 때 목표로 한다. 무대체 특별 편출 뒤에는 다음 연례까지 더 적을 수 있다. |
| 배당 연속 지급 | 10년 | 전년도 마지막 영업일 기준 합성 이력으로 선별한다. 지급 중단·취소만 이력을 0으로 되돌린다. |
| 최소 유동 시가총액 | USD 5억 | 2월 세 번째 금요일 기준 `500000000.0` 이상을 남긴다. |
| 최소 3개월 평균 일거래대금 | USD 200만 | 2월 세 번째 금요일 기준 합성 ADVT로 대응한다. |
| REIT | 제외 | 게임 기준자산의 `REAL_ESTATE` 섹터를 적격 후보에서 제외한다. |
| 배당수익률 후보 | 상위 50% | 적격 후보를 배당수익률로 정렬해 상위 반을 남긴다. |
| 종합 순위 | 4개 팩터 | 자유현금흐름/부채, ROE, 배당수익률, 5년 배당성장률 순위 합을 쓴다. |
| 기존 종목 버퍼 | 종합순위 200위 | 재구성 때 잔류 자격을 적용한다. |
| 가중 | 유동 시가총액 | 종목별·섹터별 상한을 지키며 배분한다. |
| 종목별 상한 | 4% | 목표 비중에 적용한다. |
| GICS 섹터별 상한 | 25% | GICS 호환 11개 섹터 분류의 목표 합계에 적용한다. |
| 정기 일정 | 3·6·9·12월 | 3월에 편입·편출, 네 달 모두에 재가중한다. |
| 일일 상한 검사 | 4.7% / 22% | 4.7%를 넘는 종목의 합이 22%를 넘으면 2 미국 거래일 후 재조정한다. |
| 추적오차 연 변동성 | 0.2% | 종목·시각 keyed 잔차로 적용한다. |
| SCHD 연 보수 | 0.06% | `etfProfile.annualExpenseRatio = 0.0006`으로 NAV에 누적한다. |

위 일정과 규칙은 공식 문서를 게임 시계와 합성 기준자산에 맞게 결정적으로 구현한 운용 모델이다.
지수 산출기관의 미공개 파일, 실제 기업 데이터, 실제 주식수와 실제 설정·환매 바스켓을 복제하지는 않는다.

## 스키마 v3: 실행 provider와 벤치마크·상품의 분리

종목팩 루트에는 `schemaVersion`, `benchmarks`, `instruments` 세 필드가 정확히 필요하다.
`schemaVersion`은 `3`이다. 주식·REIT·ADR도 `fundProductProfile: null`을 명시하고, ETF·ETN·CEF는
null이 아닌 상품 프로필을 정확히 하나 가진다. 종목 안에 방법론을 중복 저장하지 않는다.

```json
{
  "schemaVersion": 3,
  "benchmarks": [
    {
      "benchmarkId": "spdj-dow-jones-us-dividend-100",
      "version": 1,
      "displayName": "Dow Jones U.S. Dividend 100 Index",
      "administrator": "S&P Dow Jones Indices",
      "officialSourceUrls": [
        "https://www.spglobal.com/spdji/en/documents/methodologies/methodology-dj-dividend-indices.pdf"
      ],
      "baseCurrency": "USD",
      "engineKind": "EQUITY_METHODOLOGY",
      "supportLevel": "VERIFIED_RULES",
      "componentBenchmarkRefs": [],
      "equityMethodology": {
        "methodologyRef": {
          "ownerSourceId": "builtin:base",
          "methodologyId": "schd-dividend-100",
          "version": 1
        },
        "effectiveFrom": "2026-03-23",
        "referenceUniverse": "US_BROAD_EQUITY",
        "parameters": {
          "integers": {
            "annualReconstitutionMonth": 3,
            "dailyCapReweightDelayTradingDays": 2,
            "incumbentRankBuffer": 200,
            "minDividendPaymentYears": 10,
            "targetConstituentCount": 100
          },
          "decimals": {
            "dailyAggregateWeightLimit": 0.22,
            "dailyWeightThreshold": 0.047,
            "eligibleYieldFraction": 0.5,
            "individualWeightCap": 0.04,
            "minAverageDailyValueTraded": 2000000.0,
            "minFloatMarketCap": 500000000.0,
            "sectorWeightCap": 0.25
          },
          "booleans": {},
          "texts": {},
          "integerSets": {
            "rebalanceMonths": [3, 6, 9, 12]
          }
        }
      },
      "equityReferenceProfile": null,
      "fixedIncomeProfile": null,
      "commoditySpotTerms": null,
      "futuresReferenceTerms": null,
      "fundOfFundsMethodologyProfile": null,
      "compositeReferenceProfile": null,
      "alternativeRiskPremiaProfile": null
    }
  ],
  "instruments": []
}
```

### `BenchmarkDefinition`

| 필드 | 계약 |
|---|---|
| `benchmarkId`, `version` | 안정 ID와 양의 버전. 두 팩이 같은 쌍을 다시 정의할 수 없다. |
| `displayName`, `administrator` | 표시명과 산출기관. 미확인 프록시는 산출기관을 `Unverified`로 둔다. |
| `officialSourceUrls` | 정렬된 중복 없는 절대 HTTPS URL. 검증된 두 수준은 하나 이상, provisional은 빈 배열을 허용한다. |
| `baseCurrency` | `ReferenceCurrency` enum. 결제통화가 아니라 지수 기준통화다. |
| `engineKind` | `EQUITY_METHODOLOGY`, `EQUITY_REFERENCE`, `FIXED_INCOME_CURVE`, `COMMODITY_SPOT`, `FUTURES_CURVE`, `FUND_OF_FUNDS_METHODOLOGY`, `COMPOSITE_REFERENCE`, `ALTERNATIVE_RISK_PREMIA`, `COARSE_FACTOR_PROXY` |
| `supportLevel` | `VERIFIED_RULES`, `VERIFIED_REFERENCE`, `PROVISIONAL_PROXY` |
| `componentBenchmarkRefs` | `(benchmarkId, version)` 객체의 정렬된 집합. 재간접·합성·대체위험 프리미엄의 실제 의존성과 정확히 같아야 한다. |
| `equityMethodology` | 등록된 실행 provider ref, 효력일·유니버스와 provider 전용 typed 파라미터 또는 `null`. 현재는 SCHD 정의만 non-null이다. |
| `equityReferenceProfile` | 지역·국가·유니버스·섹터·스타일·선정/재가중 정책 또는 `null`. 현재 342개다. |
| `fixedIncomeProfile` | 실행 가능한 고정수익·현금 기준 프로필 또는 `null`. 현재 124개 벤치마크가 non-null이다. |
| `commoditySpotTerms`, `futuresReferenceTerms` | 현물 carry 또는 선물 curve·roll 기준 조건. 각각 4개와 7개다. |
| `fundOfFundsMethodologyProfile` | 후보 펀드 선정·상한·카테고리·재구성 규칙. 현재 3개다. |
| `compositeReferenceProfile` | signed sleeve·노출·비중/위험/듀레이션·FX·달력 계약. 현재 17개다. |
| `alternativeRiskPremiaProfile` | long/short driver·신호·위험예산·비용 calibration. 현재 component-only 3개다. |

팩 안과 활성 팩 사이의 모든 참조가 존재해야 한다. 같은 `(benchmarkId, version)` 재정의,
없는 구성요소, 자기 참조와 순환을 거부하며, 스냅샷은 구성요소가 합성 벤치마크보다 먼저 오는
결정적 `BenchmarkRef` 순서로 평가한다. 복합→대체위험 프리미엄 참조는 허용하지만 같은 종류의
중첩과 대체위험 프리미엄→복합 역참조는 현재 실행 계약에서 거부한다. `VERIFIED_REFERENCE`는 지수 연결을 확인했다는 뜻이지
그 구성 규칙을 엔진이 재현한다는 뜻이 아니다.

현재 공유 기준지수의 공식 근거는 다음과 같다.

- [S&P U.S. Indices Methodology](https://www.spglobal.com/spdji/en/documents/methodologies/methodology-sp-us-indices.pdf)
- [Nasdaq-100 Methodology](https://indexes.nasdaqomx.com/docs/methodology_NDX.pdf)
- [KRX 지수 방법론 안내](https://global.krx.co.kr/contents/GLB/02/0205/0205020300/GLB0205020300.jsp)

S&P 500·Nasdaq-100의 성장·섹터·동일가중·액티브 파생 상품을 단순 모지수에 연결하지 않는다.
명시적 allowlist의 plain tracker와 기초가 확인된 일일 reset·커버드콜만 공유 참조를 사용하고,
나머지는 상품별 `proxy.<market>.<symbol>` ID를 쓴다. 상품명에서 추정한 문자열은 절대
`VERIFIED_*`로 승격하지 않는다.

### `equityReferenceProfile`

상세 holdings 규칙을 아직 재현하지 않는 342개 주식·리츠 기준도 하나의 범용 수익률 숫자로
뭉개지 않는다. 프로필은 `region`, ISO alpha-2 `countryCodes`, `eligibleUniverse`, `sectorPolicy`,
`includedSectors`, 안정적인 `themeId`, `stylePolicies`, `weightingModel`, nullable 목표 종목 수·상한,
별도의 `selectionCalendar/selectionMonths`와 `reweightCalendar/reweightMonths`, 지원 수준·provenance·
confidence·공식 URL·`assumptionId`를 정확히 가진다.

편입·편출 달력과 비중 재조정 달력을 분리하므로 분기 재가중 상품을 분기마다 새 종목을 고르는
것처럼 처리하지 않는다. broad GICS 섹터 상품은 `SECTOR_INDUSTRY + INCLUDED_ONLY`를 쓰고,
교차 섹터 테마만 `THEMATIC`과 경제적 의미가 있는 공유 `themeId`를 사용한다. 리츠 상품은
`REAL_ESTATE` 섹터만 포함해야 한다. 현재 confidence 분포는 HIGH 56, MEDIUM 15, LOW 271개다.

대표 포지션 상한은 단일종목 1, LOW 64, MEDIUM 128, HIGH 256이다. 기본 팩의 합계 상한은
33,156개이며, 모드를 합친 전체 카탈로그는 65,536개를 넘으면 새 게임 시작 전에 거부한다.
이는 초기화·저장 크기를 제한하기 위한 예산이지 실제 지수 종목 수를 주장하는 값이 아니다.

### `fixedIncomeProfile`

고정수익 프로필은 상품의 법적 구조나 레버리지 조건이 아니라 기준수익률을 만드는 금리·신용 축이다.
따라서 TYO는 미국 7~10년 명목국채 프로필 위에 일일 -3배 reset을 적용하고, 채권 CEF 4개는
고정수익 reference 위에 CEF 레버리지·할인/할증을 별도로 적용한다. TYLD도 다중 섹터 고정수익
reference와 전술적 상품 오버레이를 분리한다.

```json
"fixedIncomeProfile": {
  "geography": "UNITED_STATES",
  "currencies": ["USD"],
  "assetType": "NOMINAL_GOVERNMENT",
  "effectiveDurationYears": 7.5,
  "tenorBand": "INTERMEDIATE",
  "creditQuality": "GOVERNMENT_BACKED",
  "rateReset": "NOT_FLOATING",
  "realRateLinked": false,
  "supportLevel": "VERIFIED_REFERENCE",
  "durationProvenance": "CALIBRATED_ASSUMPTION",
  "officialSourceUrls": [
    "https://www.direxion.com/product/daily-7-10-year-treasury-bull-bear-3x-etfs"
  ]
}
```

현재 124개 기준 정의의 자산 유형 분포는 투자등급 37, 머니마켓 20, 명목국채 22, 지방채 10, 하이일드 9,
preferred/hybrid 7, 변동금리 5, 물가연동 5, 다중 섹터 신용 4, agency MBS 1,
securitized credit 2, CLO 2개다. 상품 기준 노출 집계가 과거 103→114→116→117로 변한 것은
숫자 맞추기가 아니라 TYO·채권 CEF·475630, 오분류 채권, PFFA·VRP를 기초 노출 축으로 다시
분류하고 MBS·muni·preferred를 세분한 결과다. 나머지 7개 차이는 복합·재간접 엔진이 소비하는
component-only 기준과 상품 간 공유에서 생기므로 상품 수와 벤치마크 정의 수를 같은 값으로 보지 않는다.
지원 enum은 다음과 같다.

- `FixedIncomeGeography`: `KOREA`, `UNITED_STATES`, `GLOBAL`, `DEVELOPED_EX_US`,
  `EMERGING_MARKETS`
- `FixedIncomeAssetType`: `NOMINAL_GOVERNMENT`, `INFLATION_LINKED`, `AGENCY_MBS`,
  `SECURITIZED_CREDIT`, `MUNICIPAL`, `PREFERRED_HYBRID`, `INVESTMENT_GRADE`, `HIGH_YIELD`,
  `FLOATING_RATE`, `CLO`, `MONEY_MARKET`, `MULTI_SECTOR_CREDIT`
- `FixedIncomeTenorBand`: `OVERNIGHT`, `ULTRA_SHORT`, `SHORT`, `INTERMEDIATE`, `LONG`,
  `BROAD`, `VARIABLE`
- `FixedIncomeCreditBucket`: `GOVERNMENT_BACKED`, `AAA`, `INVESTMENT_GRADE`, `HIGH_YIELD`,
  `MIXED`, `UNVERIFIED`
- `FixedIncomeRateReset`: `NOT_FLOATING`, `OVERNIGHT`, `MONTHLY`, `QUARTERLY`, `VARIABLE`

`currencies`와 `officialSourceUrls`는 각각 enum 선언 순서와 URL 오름차순으로 정렬한 중복 없는
집합이다. 물가연동 유형만 `realRateLinked = true`이고, 변동금리·CLO에는 `NOT_FLOATING`이 아닌
reset이 필요하다. `durationProvenance = OFFICIAL_DISCLOSURE`는 해당 수치를 직접 뒷받침하는 공식
URL이 있을 때만 사용할 수 있다. 모델 보정값은 출처 URL이 있더라도 계속
`CALIBRATED_ASSUMPTION`으로 남긴다.

`fixedIncomeProfile.currencies`는 상장통화나 ETF 헤지 통화가 아니라 기초채권 통화다. BNDW처럼
여러 통화 채권을 보유하는 기준 포트폴리오와 `etfProfile.fxProfile`의 상품 환노출·헤지 legs는
같을 필요가 없다. 공식 기준이 명백한 글로벌·비미국 상품만 EUR·JPY·CNY 등을 추가하고,
미확인 상품은 단일 통화 프록시와 `PROVISIONAL_PROXY`를 유지한다.

### 원자재·선물과 재간접 방법론

`commoditySpotTerms`는 기준자산·기준통화, 현물/담보 배분, 보관·보험비, convenience yield와
담보수익 참여율을 가진다. `futuresReferenceTerms`는 포트폴리오 유형, 담보비율, curve별 sleeve,
적격 만기월, roll 달력·window와 가격수익률 convention을 가진다. 모델 가정 terms는 공식 URL을
비우고 안정적인 `assumptionId`를 요구한다. 기준 엔진이 roll과 담보수익을 계산하므로 상품
`returnTransforms`에 `FUTURES_ROLL`을 중복 적용하지 않는다.

PCEF·YYY·YMAX의 `fundOfFundsMethodologyProfile`은 후보 universe, 선정·가중 모델, 목표/후보 수,
적격 카테고리와 typed category benchmark, 분배율·할인율·유동성 screen, 개별·카테고리·순위별
상한, 선정/재가중 달력과 provenance를 담는다. 현재 후보 수 합계는 768, 목표 펀드 수 합계는
221이다. YYY의 순위 30까지 3.5%, 60까지 2% 상한처럼 순위별 규칙도 별도 tier로 보존한다.
재간접 component는 선행 실행 가능한 주식·고정수익·원자재·선물 기준만 허용하며, 재간접·합성·
대체위험 프리미엄 또는 coarse 기준을 다시 참조하는 중첩은 현재 거부한다.

### `compositeReferenceProfile`과 대체위험 프리미엄

17개 복합 기준은 44개 sleeve를 사용한다. 각 sleeve source는 `BENCHMARK` 또는 `INSTRUMENT`로
tagged되며 종목 source는 카탈로그의 사업회사만 가리킬 수 있다. 비중은 양의 크기로 저장하고
`LONG/SHORT` 방향을 별도로 둔다. target·min/max band·risk budget, gross/net 노출, 목표 변동성,
lookback·drift, leverage financing·short borrow, 선정/재가중 달력과 각 수치의
`OFFICIAL_DISCLOSURE`/`CALIBRATED_ASSUMPTION` 경계를 보존한다.

직접 `INSTRUMENT` source가 청산·상장 종료로 지수 적격성을 잃으면 그 source의 가격·배당·듀레이션을
더 이상 읽지 않는다. 같은 turn에 `EXTRAORDINARY_SOURCE_TO_CASH` 원장을 남기고 기존 current/target
노출을 기준통화 현금으로 영구 치환한다. ALT가 먼저 치환된 뒤 이를 기초로 쓰는 COMPOSITE의 소득률·
듀레이션도 같은 시각에 다시 계산하므로 저장 직전 한 tick의 유령 노출이 남지 않는다.

| 운용 모델 | 기본 상품 |
|---|---|
| `STATIC_TARGET` | 0162Z0, 0177N0, 237370, 284430, 448330, AOA, AOR, AOM, AOK |
| `TARGET_BAND` | 438080, 438100 |
| `TACTICAL_ALLOCATION` | 461490, RLY |
| `ACTIVE_LONG_SHORT` | FTLS |
| `DURATION_HEDGE` | RISR |
| `EQUAL_RISK_CONTRIBUTION` | RPAR |
| `SYSTEMATIC_ALTERNATIVE` | IALT |

IALT는 상품 벤치마크 자체를 `COMPOSITE_REFERENCE`로 두고, 시장중립·신용상대가치·글로벌 매크로
세 `ALTERNATIVE_RISK_PREMIA` component를 소비한다. 세 profile은 모두 driver별 전략 family,
long/short 신호 방향, 위험예산, gross/net 노출, 목표 변동성, 126거래일 lookback, 연 financing
0.5%·short borrow 0.75%·구현비 0.2%를 명시한다. 이 수치는 공식 계약값이 아니라
`ml2040.*.v1` calibration이다.

RISR의 agency MBS interest-only sleeve는 별도 typed prepayment 모델을 가진다. 유효 듀레이션 -12년,
기본 CPR 8%, 모기지 금리 1%p 하락당 CPR 6%p 증가, CPR 변동성 2%, coupon-strip yield 5.5%는
`CALIBRATED_ASSUMPTION`이다. 공식 상품의 -9~-3년 포트폴리오 band와 모델 calibration을 같은
출처로 오인하지 않는다.

복합 엔진은 각 sleeve 통화를 벤치마크 기준통화로 변환·헤지한다. 따라서 상품의
`etfProfile.fxProfile`은 벤치마크 기준통화에서 상장통화로 가는 outer leg만 표현한다. 두 통화가
같으면 hedge ratio와 비용은 0이어야 하며, 한국 복합 상품의 USD sleeve를 상품 FX에 다시 넣어
환율을 이중 계상할 수 없다.
내부 sleeve/driver에는 아직 헤지 비용 항목이 없으므로 현재 실행 가능한 내부 hedge ratio는
명시적 무헤지 `0`뿐이다. 양의 환헤지는 비용 스키마가 추가되기 전까지 허용하지 않는다.
복합·대체위험 프리미엄 일정은 현재 KRX와 NYSE만 구현하므로 기준통화도 `KRW`와 `USD`로
제한한다. 다른 통화권은 거래소·휴일·시간대가 스키마에 추가되기 전까지 모드에서 선언할 수 없다.

### `fundProductProfile`

```json
"fundProductProfile": {
  "benchmarkRef": {
    "benchmarkId": "spdj-dow-jones-us-dividend-100",
    "version": 1
  },
  "replicationMode": "PHYSICAL_FULL_REPLICATION",
  "returnVariant": "TOTAL_RETURN",
  "legalStructure": "OPEN_END_ETF",
  "referenceExposure": "EQUITY",
  "returnTransforms": ["PLAIN"],
  "trackingErrorAnnualVolatility": 0.002,
  "dailyResetTerms": null,
  "etnProductTerms": null,
  "etnIssuerCreditModelParameters": null,
  "closedEndFundTerms": null,
  "closedEndFundMarketModelParameters": null,
  "optionStrategyTerms": null,
  "cashCollateralizedPutSpreadTerms": null
}
```

| 축·필드 | 지원 값과 의미 |
|---|---|
| `legalStructure` | `OPEN_END_ETF`, `EXCHANGE_TRADED_NOTE`, `CLOSED_END_FUND` |
| `referenceExposure` | `EQUITY`, `FIXED_INCOME`, `CASH`, `COMMODITY`, `CRYPTO`, `MULTI_ASSET`, `REAL_ESTATE`, `ALTERNATIVE` |
| `returnTransforms` | enum 선언 순서의 중복 없는 비어 있지 않은 집합: `PLAIN`, `DAILY_LEVERAGED`, `DAILY_INVERSE`, `PORTFOLIO_LEVERAGE`, `COVERED_CALL`, `OPTION_INCOME`, `OPTION_SPREAD`, `CASH_COLLATERALIZED_PUT_SPREAD`, `BUFFERED`, `CURRENCY_HEDGED`, `FUTURES_ROLL`, `FUND_OF_FUNDS`, `RISK_CONTROL`, `PREMIUM_DISCOUNT`, `ISSUER_CREDIT` |
| `replicationMode` | `PHYSICAL_FULL_REPLICATION`, `PHYSICAL_SAMPLING`, `DERIVATIVE_SYNTHETIC`, `HYBRID`, `ACTIVE_MANAGEMENT`, `SYNTHETIC_NOTE`, `UNVERIFIED` |
| `returnVariant` | `PRICE_RETURN`, `TOTAL_RETURN`, `NET_TOTAL_RETURN`, `UNVERIFIED` |
| `trackingErrorAnnualVolatility` | 유한 0~1 또는 `null`. null은 0이 아니라 공식값 미검증이다. |

`PLAIN`은 다른 변환과 함께 쓰지 않는다. ETN은 `SYNTHETIC_NOTE`와 `ISSUER_CREDIT`, CEF는
`ACTIVE_MANAGEMENT`와 `PREMIUM_DISCOUNT`가 필요하다. 법적 구조와 노출은 기존
`instrumentType`·`etfProfile`과 일치해야 하지만, 새 상품 프로필이 권위이고 legacy behavior는
가격 fallback에만 쓴다. 그래서 커버드콜 ETN처럼 `COMMODITY + COVERED_CALL + ISSUER_CREDIT`도
한 상품에서 손실 없이 표현할 수 있다.

`referenceExposure`는 전략명이 아니라 기초 기준자산 축이다. 따라서 주식 커버드콜·버퍼 상품은
`EQUITY`, T-bill과 S&P put spread를 결합하는 CSHI는 `MULTI_ASSET`, SLVO·USOI·GLDI는
`COMMODITY`다. 옵션, 일일 reset, CEF 레버리지와 ETN 신용은 `returnTransforms`와 typed terms에
표현하며 기초 노출을 `ALTERNATIVE`로 바꾸지 않는다.

### `dailyResetTerms`

`DAILY_LEVERAGED` 또는 `DAILY_INVERSE`가 있으면 `dailyResetTerms`가 반드시 있고, 그 외에는
반드시 `null`이다. `targetLeverage`는 `etfProfile.leverage`와 정확히 같아야 한다.
`dailyResetTerms`와 `optionStrategyTerms`는 동시에 non-null일 수 없다.

```json
"dailyResetTerms": {
  "productId": "NYSE_ARCA:QLD",
  "reference": {
    "kind": "BENCHMARK",
    "benchmarkRef": {"benchmarkId": "nasdaq-nasdaq-100", "version": 1},
    "instrumentId": null
  },
  "directReferenceTerminationRule": null,
  "targetLeverage": 2.0,
  "resetCalendar": "US_EQUITY",
  "provenance": "VERIFIED_PRODUCT_TERMS",
  "officialSourceUrl": "https://www.proshares.com/our-etfs/leveraged-and-inverse/qld",
  "modelParameters": {
    "annualFinancingSpread": 0.005,
    "collateralYieldParticipation": 1.0,
    "origin": "CALIBRATED_ASSUMPTION",
    "sourceUrl": null
  }
}
```

`reference.kind`는 `BENCHMARK` 또는 `INSTRUMENT`다. 전자는 존재하는 `BenchmarkRef`만,
후자는 자기 자신이 아닌 존재하는 사업회사 종목만 가리킨다. 단일종목형 네 상품은 삼성전자
`KOSPI:005930` 또는 SK하이닉스 `KOSPI:000660`을 직접 참조한다. 목표배율·기초·reset 달력의
출처와 게임의 carry 가정은 별도 provenance다. 따라서 공식 목표배율을 확인했더라도 자금조달
스프레드를 공식 공시값처럼 표현하지 않는다.

### 옵션·ETN·CEF typed terms

`optionStrategyTerms`는 `productId`, tagged `reference`, `kind`, tenor·roll 달력,
`provenance`, 공식 URL 집합, `assumptionId`, `premiumModel`과 kind별 세부 객체를 정확히 가진다.
`COVERED_CALL`은 overwrite·call strike, `OPTION_INCOME`은 core/option 배분과 상·하방 참여율,
`BUFFERED_PUT_SPREAD`는 notional·long put strike·buffer·buffer 이후 참여율·상방 cap을 쓴다.
프리미엄 모델은 변동성 배율, 매도 프리미엄 포착률, 매수 프리미엄 비용률, roll 구현비용과
calibration 출처를 분리한다. ETN 3개도 참조 covered-call 지수의 가격·쿠폰 적립을 위해 이 terms를
가지지만, 런타임은 같은 옵션 수익을 상품 오버레이로 두 번 적용하지 않는다.

CSHI와 USDX는 보호형 버퍼 ETF가 아니라 현금성 담보 위에 broad-index put spread를 운용한다.
`cashCollateralizedPutSpreadTerms`는 상품 ID, 별도 money-market `cashBenchmarkRef`, 별도 주식
`optionReference`, tenor·roll, 최대 결제손실, short/long put strike와 프리미엄 모델을 가진다.
두 참조를 하나의 `referenceExposure`나 buffered payoff로 뭉개지 않으며, 확인하지 못한 동적
옵션 수치는 `MODEL_ASSUMPTION`으로 남긴다.

ETN은 `etnProductTerms`와 `etnIssuerCreditModelParameters`를 둘 다 요구한다. 전자는 원금·수수료,
발행·만기일, 평가 관측, 쿠폰, holder redemption, issuer call/acceleration과 공식 supplement
provenance를 담는다. 후자는 `issuerId`, 초기 credit spread·hazard·recovery, spread 평균회귀·충격
변동성과 모델 출처를 담는다. 같은 issuer ID를 쓰는 모든 상품은 정확히 같은 신용 calibration을
공유해야 한다.

CEF는 `closedEndFundTerms`와 `closedEndFundMarketModelParameters`를 둘 다 요구한다. 시장 모수의
정확한 필드는 fund ID, 목표 할인율·평균회귀, 초기 debt/preferred-to-gross-assets,
차입·우선주 분배 spread, 할인율 충격 연 변동성, provenance와 source URL이다. 초기 레버리지는
법적 terms의 허용 여부와 최소 asset-coverage를 만족해야 하며, 레버리지를 허용하지 않는 구조는
해당 비율과 financing spread가 모두 0이어야 한다.

### 엄격 검증과 실행 가능성

`equityMethodology`는 공통 `methodologyRef`, 효력일·유니버스와 provider 전용 `parameters`를
정확히 요구한다. 목표 수, 상한, 일정과 조건은 공통 SCHD형 필드가 아니라 provider 파라미터다.
`parameters`도 `integers`, `decimals`, `booleans`, `texts`, `integerSets` 다섯 객체를 빠짐없이
가지며 임의 중첩 JSON, 클래스명이나 실행 코드는 받지 않는다. 파라미터 이름은 소문자로 시작하는
영숫자 camel-case 식별자이고 각 객체에서 문자열 오름차순이어야 한다. 다섯 타입 사이에서도 키가
중복될 수 없으며 전체 64개로 제한한다. `integerSets`의 각 값은 중복 없는 오름차순 정수 배열이고
최대 64개 값만 허용한다. 실수는 유한해야 하고 문자열은 trim된 비어 있지 않은 256자 이하 값이어야
한다. 파서는 이 wire 형식·크기·유한성을 검사하고, 등록된 provider는 필요한 키의 정확한 집합과
각 값의 의미 범위를 다시 검사한다.

모든 nullable 실행 프로필 필드도 생략할 수 없고 해당하지 않으면 명시적으로 `null`이어야 한다.
알 수 없는 필드나 enum, 중복 키·집합 값, 누락된 필드, 비정규 날짜·HTTP URL, 정렬되지 않은
집합을 하나라도 발견하면 팩 전체를 거부한다.

엄격 파싱 뒤 `VERIFIED_RULES` 방법론은 campaign seed로 2040년까지 후보 선정과 상한 배분을
사전 실행한다. 지원 일정과 맞지 않는 `effectiveFrom`, 부족한 적격 후보, 종목·섹터 상한 안에서
100%를 만들 수 없는 구성이 있으면 부분 상태를 만들지 않고 새 게임 시작을 거부한다. 기본
등록부에는 현재 SCHD v1 provider 하나만 있고, 이 provider는 위 예제의 정확한 integer/decimal
및 integer-set 키, 비어 있는 boolean/text 키 집합과 SCHD 규칙 범위를 요구한다.

팩을 합친 뒤에도 graph와 실행 비용을 다시 검증한다. benchmark ref 존재·중복·순환,
component-first 순서, composite/alternative의 허용된 비중첩 방향, 사업회사 source, cross-currency
hedge 명시, outer FX 단일계상과 함께 equity representative 65,536, FOF 후보 65,536,
FOF 목표+composite sleeve+alternative driver 65,536의 카탈로그 전체 상한을 적용한다.

## ETF·ETN·CEF를 분리해야 하는 이유

| 구조 | 보유·가격 경계 | 필요한 전용 엔진 |
|---|---|---|
| 개방형 ETF | 펀드가 자산을 보유하고 설정·환매로 좌수와 바스켓을 조정한다. | 실물 완전복제, 표본복제, 파생·옵션 오버레이, 설정·환매 흐름 |
| ETN | 통상 발행사의 무담보 채무로 참조지수 수익률을 지급한다. ETF처럼 구성주식을 법적으로 보유한다고 보면 안 된다. | 참조지수 상태, 발행사 신용·스프레드, 비용, 조기상환·만기 |
| CEF | 실제 자산을 보유하지만 일상적인 ETF 설정·환매 기능이 없고, 유통주식 수가 상대적으로 고정된다. | 포트폴리오·레버리지, 실현손익·원금분배, NAV 할인·할증 |

채권형, 원자재 선물형, 가상자산형, 레버리지·인버스, 커버드콜, 단일종목 상품도 각각 만기·롤,
일일 리셋, 옵션 프리미엄, 기초종목, 보관 규칙이 다르다. 이들을 `PHYSICAL_FULL_REPLICATION`
주식 구성 엔진에 억지로 연결하지 않고 전략별 상태·회계 엔진을 추가해야 한다.

## 검증된 현재 제한

- SCHD 외 507개 fund-like 종목은 공식 전체 holdings 방법론을 재현한 것이 아니다. 주식 342개는
  typed 대표 포트폴리오, 재간접 3개와 합성 17개는 명시적 정책·sleeve, 나머지는 자산별 reference
  엔진을 사용하지만 대부분 `PROVISIONAL_PROXY`다. 일일 reset 11개도 목표배율을 기준수익률에
  적용할 뿐 실제 파생계약·담보 원장을 복제하지 않는다. 고정수익·현금 기준 124개가 typed curve를
  참조하지만 실제 채권 CUSIP·현금흐름 원장은 아니다.
- 2,500개 기준자산은 방법론 동작을 위한 결정적 대리 유니버스다. 실제 기업, 현재 holdings 또는
  2040년까지의 미래 편입을 나타내지 않는다.
- SCHD 월별 `EXTRAORDINARY_REMOVAL`은 합성 배당 지급 중단·취소를 처리한다. 복합·대체전략의
  직접 사업회사 source가 청산·상장 종료 단계에 들어가면 같은 시각 `EXTRAORDINARY_SOURCE_TO_CASH`
  원장으로 영구 현금 치환한다. 직접 기초를 쓰는 일일 reset·옵션·현금담보 상품은 정책에 따라
  다음 거래소 종가의 최종 NAV로 상품 청산을 시작하고, 옵션형은 기존 package를 정산한 뒤 새
  cycle을 열지 않는다. 다만 합성 대표 유니버스 안의 개별 회사를 실제 상장기업과 연결한 인수합병,
  파산·대체편입과 실제 주식수 조정은 아직 재현하지 않는다.
- 주식 방법론 host v1은 미국 broad-equity·USD, 두 정기 일정 lane과 편출-only 특별 조치만
  지원한다. 다른 시장, 종목 즉시 대체·편입, 임의 corporate-action 일정과 현재 adapter에 없는
  신호가 필요한 provider는 해당 host 계약을 먼저 버전 확장해야 한다.
- turnover는 계산·저장하지만 종목별 체결과 리밸런싱 거래비용 원장은 아직 없다.
- 동적 구성의 추정 연 배당률은 NAV 분배 재원 적립과 지급액 계산에 사용한다. 지급 빈도는 여전히
  카탈로그의 `distributionFrequency`를 사용하며, 실제 구성종목별 권리락·입금 시점을 합산하지 않는다.
- 실제 펀드 holdings, 현금·미결제, 설정·환매, 증권대여 담보와 구성종목의 배당·이자·옵션 프리미엄을
  연결한 완전한 펀드 회계 원장은 아직 없다.
- 공식 방법론이 개정되면 기존 캠페인을 묵시적으로 바꾸지 말고 벤치마크 `version`과 종목팩 콘텐츠를
  함께 갱신해야 한다. `effectiveFrom`은 공식 문서 발행일이 아니라 그 버전을 부트스트랩할 수 있는
  지원 연례 재구성 효력일로 기록한다.

## 콘텐츠 SHA와 저장 재현성

`benchmarks`, `fundProductProfile`, daily reset·option·ETN·CEF terms와 calibration은 종목팩 JSON
파일 안에 있으므로 별도 해시 파일이 필요하지 않다. 로더는
`instruments.json` 원본 바이트 전체의 SHA-256을 소문자 64자리로 계산한다. 따라서 방법론 값,
공식 출처, 버전을 바꾸면 자동으로 콘텐츠 fingerprint가 바뀐다. 공백·필드 순서만 바꿔도 원본 바이트가
다르므로 다른 fingerprint다.

새 게임은 기본 카탈로그와 활성 모드 팩의 순서있는 source ID·SHA를 고정한다. 저장 게임은 이 참조와
현재 구성, 방법론 버전, 다음 재구성·재조정일, 표류 비중, 재구성 원장을 함께 보관한다. 불러올 때 모드
ID·버전·콘텐츠 fingerprint 또는 카탈로그 참조가 다르면 해당 저장을 거부한다. 방법론을 수정할 때는
모드의 시맨틱 버전과 변경한 벤치마크 `version`도 함께 올리는 것을 권장한다.
provider 실행 코드는 JSON fingerprint에 포함되지 않으므로 코드 동작을 바꾸면
`methodologyRef.version`도 반드시 올려야 한다.

복원 검증은 저장된 기준자산 ID·표시정보·섹터를 캠페인 seed의 2,500개 원본과 대조하고, 다음 일정과
대기 계획을 같은 미국 거래일 달력으로 다시 계산한다. 구성 해시·revision 원장 계보와 향후 종목·섹터
상한으로 100%를 다시 배분할 수 있는지도 확인하므로, 잘못된 상태를 복원한 뒤 첫 거래에서 실패시키지 않는다.

모드는 기본 SCHD provider나 다른 모드의 ref를 덮어쓰지 못한다. 데이터 전용 모드는 JSON만으로
새 정책 함수를 만들 수 없지만 이미 설치된 provider를 참조할 수 있으며, 등록되지 않은
`methodologyRef`를 선언하면 거부된다. 별도로 신뢰한 실행 모드는 캠페인 전용 콘텐츠 API와
`game.contentRegister` 승인으로 자기 source namespace의
provider를 등록하고, 같은 source의 새 벤치마크·종목을 추가할 수 있다. provider 코드를 바꿀 때는
`methodologyRef.version`, 벤치마크 버전과 모드 버전을 함께 올려 기존 캠페인의 의미를 바꾸지 않는다.
