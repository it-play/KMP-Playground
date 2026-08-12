# Market Ledger 2040 모드 사양

모드는 사용자 앱 데이터의 `mods` 폴더에 설치한다. 실행 위치나 설치 디렉터리를 기준으로 삼지
않으므로 업데이트와 실행 방법이 달라져도 같은 모드 목록을 사용한다.

| 운영체제 | 모드 폴더 |
|---|---|
| Windows | `%APPDATA%/MarketLedger2040/mods` |
| 그 외 | `~/.market-ledger-2040/mods` |

로비의 **모드** 메뉴에서 폴더를 열고 다시 검색할 수 있다. 활성화 여부와 사용자 설정은 모드
원본과 분리해 저장된다. 로비에서 바꾼 활성 모드 집합은 다음 새 게임부터 적용되며, 모드 ID,
버전, 설정값, 선언형 콘텐츠 fingerprint는 저장 게임에 함께 기록된다. 저장 게임이 요구하는
모드가 없거나 버전·콘텐츠가 다르면 불러오기를 거부한다.

## 폴더 구조

모드 ID가 `exmp`이고 커버와 종목팩을 모두 제공한다면 구조는 다음과 같다.

```text
mods/
└── exmp/
    ├── manifest.xml
    ├── cover.png
    └── instruments.json  # 선언형 종목팩을 제공할 때만 필요
```

- 폴더 이름과 manifest의 `id`는 같아야 한다.
- ID는 소문자 영문·숫자로 시작하고 이후 소문자 영문·숫자·`.`·`_`·`-`만 사용할 수 있다.
- 커버는 `png`, `jpg`, `jpeg`, `webp`를 지원한다. manifest에서 파일을 지정하지 않으면
  `cover.*`를 찾으며 후보가 여러 개면 모드를 불러오지 않는다.
- manifest, 커버, 선언한 종목팩은 일반 파일이어야 하며 심볼릭 링크는 허용하지 않는다.

## manifest.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<mod schemaVersion="2" apiVersion="1" id="exmp">
    <name>예제 시장 모드</name>
    <description>시장 환경과 플레이 규칙을 조절하는 예제입니다.</description>
    <author>Example Studio</author>
    <version>1.0.0</version>
    <lastModified>2026-08-11</lastModified>
    <cover>cover.png</cover>

    <content>
        <instruments file="instruments.json"/>
    </content>

    <permissions>
        <permission>game.read</permission>
        <permission>game.playerCommands</permission>
        <permission>game.marketControl</permission>
    </permissions>

    <settings>
        <setting key="showHints" type="boolean">
            <name>시장 힌트</name>
            <description>중요한 시장 변화를 더 자세히 표시합니다.</description>
            <default>true</default>
        </setting>

        <setting key="shockScale" type="decimal">
            <name>충격 배율</name>
            <description>외부 사건이 시장에 미치는 강도입니다.</description>
            <default>1.0</default>
            <min>0.5</min>
            <max>2.0</max>
        </setting>

        <setting key="pace" type="enum">
            <name>시장 속도</name>
            <description>모드가 사용할 기본 진행 성향입니다.</description>
            <default>normal</default>
            <option value="calm">완만</option>
            <option value="normal">보통</option>
            <option value="volatile">격변</option>
        </setting>
    </settings>
</mod>
```

설정 타입은 `boolean`, `integer`, `decimal`, `string`, `enum`이다. 숫자 타입은 선택적으로
`min`과 `max`를 받을 수 있고, `enum`은 하나 이상의 `option`이 필요하다. 기본값은 선언된
타입·범위·선택지에 맞아야 한다. `integer`는 정확한 범위 비교를 위해 IEEE-754 안전 정수 범위
(-9,007,199,254,740,991~9,007,199,254,740,991) 안에서 사용한다.

## 선언형 종목 콘텐츠

`content`는 선택 요소다. 종목팩을 제공하려면 그 안에 `instruments`를 정확히 한 번 선언한다.
`file`은 모드 루트에 직접 놓인 `.json` 일반 파일의 단일 이름이어야 한다. 절대 경로, `/` 또는
`\`가 포함된 경로, `.`·`..` 경로, 제어 문자, 운영체제 예약 이름, 심볼릭 링크는 거부한다.

종목팩은 앱의 기본 카탈로그인
[`base-catalog.json`](../composeApp/src/commonMain/composeResources/files/instruments/base-catalog.json)과
동일한 JSON 스키마를 사용한다. 루트에는 정확히 다음 세 필드가 필요하다.

| 필드 | 설명 |
|---|---|
| `schemaVersion` | 현재 값 `2` |
| `benchmarks` | 이 팩이 정의하는 버전된 벤치마크 배열. 주식 전용 팩은 빈 배열 가능 |
| `instruments` | 등록할 종목 객체 배열. 모드 하나당 1~512개 |

각 종목 객체의 정확한 필드 구성과 중첩 객체 예시는 위 기본 카탈로그를 기준으로 한다.

- `order`는 파일 안의 결정적 등록 순서다.
- `symbol`, `name`, `englishName`, `market`, `sector`, `instrumentType`은 상장 식별정보와 분류다.
  종목 ID는 `market:symbol`로 결정된다.
- `initialPrice`, `volatility`, `dividendYield`, `marketCap`, `sharesOutstanding`, `beta`,
  `quantityStep`, `lotSize`는 시뮬레이션과 거래 단위에 쓰는 수치다.
- `description`은 상품·기업 설명이다.
- `etfProfile`, `behaviorProfile`, `identityProfile`, `industrySegments`는 legacy 가격 입력, 가격·분배 행동,
  발행사·근거·연결 종목, 산업 노출 메타데이터다. 해당되지 않는 nullable 프로필은 `null`로 쓴다.
- `fundProductProfile`은 모든 종목의 필수 필드다. 주식·REIT·ADR은 `null`, ETF·ETN·CEF는
  법적 구조·기준 노출·수익률 변환과 존재하는 `BenchmarkRef`를 담은 객체여야 한다.
- enum 값은 기본 카탈로그와 앱 모델에 적힌 대문자 식별자를 그대로 사용한다. 알 수 없는 필드,
  중복 키, 타입이 다른 값, 유효 범위를 벗어난 값이 하나라도 있으면 종목팩 전체를 거부한다.
- 기본 종목과 모든 활성 모드 종목을 합친 최종 카탈로그는 최대 2,600종이다.
- 최종 카탈로그는 최대 2,600개 벤치마크를 가지며, 팩 하나는 최대 1,024개를 정의할 수 있다.

선언형 콘텐츠는 **추가 전용(add-only)** 이다. 기본 종목이나 먼저 적용된 활성 모드 종목을
덮어쓰거나 삭제할 수 없다. 팩 내부 중복을 포함해 종목 ID 또는 상장시장·심볼 충돌이 하나라도
있으면 일부 종목만 건너뛰지 않고 해당 카탈로그 구성을 전체 거부하며 새 게임을 시작하지 않는다.
기본 팩이나 앞선 모드의 같은 `(benchmarkId, version)`도 다시 정의할 수 없다.

### 벤치마크 정의와 펀드 상품 프로필

스키마 v2는 세 축을 서로 바꿔 쓰지 않는다.

- `FundLegalStructure`: `OPEN_END_ETF`, `EXCHANGE_TRADED_NOTE`, `CLOSED_END_FUND`
- `FundReferenceExposure`: `EQUITY`, `FIXED_INCOME`, `CASH`, `COMMODITY`, `CRYPTO`,
  `MULTI_ASSET`, `REAL_ESTATE`, `ALTERNATIVE`
- `FundReturnTransform`: enum 선언 순서로 정렬한 중복 없는 집합. `PLAIN`, 일일 레버리지·인버스,
  포트폴리오 레버리지, 커버드콜·옵션인컴·옵션스프레드·버퍼, 환헤지, 선물 롤, 재간접, 위험관리,
  CEF 할인·할증과 ETN 발행사 신용을 조합할 수 있다.

상품은 `benchmarkRef`, 복제 방식, 수익률 variant, 위 세 축, nullable 추적오차와 nullable
`dailyResetTerms`, `etnProductTerms`, `etnIssuerCreditModelParameters`, `closedEndFundTerms`,
`closedEndFundMarketModelParameters`, `optionStrategyTerms`, `cashCollateralizedPutSpreadTerms`를
모두 정확히 선언한다. 해당 구조가 아닌
필드도 생략하지 않고 `null`로 쓴다. 추적오차 `null`은 0이 아니라 미검증이다.
`UNVERIFIED` 복제 방식과 `PROVISIONAL_PROXY` 벤치마크는 모르는 값을 공식값처럼 만들지 않기 위한
정상 상태다. 상품명에서 지수명을 추정했거나 범용 거래소 페이지밖에 없다면 이 수준을 사용한다.

`benchmarks`의 각 정의는 다음 필드를 정확히 가진다.

```json
{
  "benchmarkId": "example-provider.example-index",
  "version": 1,
  "displayName": "Example Index",
  "administrator": "Example Index Administrator",
  "officialSourceUrls": ["https://example.com/index-methodology.pdf"],
  "baseCurrency": "USD",
  "engineKind": "EQUITY_REFERENCE",
  "supportLevel": "PROVISIONAL_PROXY",
  "componentBenchmarkRefs": [],
  "equityMethodology": null,
  "equityReferenceProfile": {
    "region": "UNITED_STATES",
    "countryCodes": ["US"],
    "eligibleUniverse": "BROAD_MARKET",
    "sectorPolicy": "ALL_SECTORS",
    "includedSectors": [],
    "themeId": null,
    "stylePolicies": ["CORE"],
    "weightingModel": "FLOAT_ADJUSTED_MARKET_CAP",
    "targetConstituentCount": null,
    "individualWeightCap": null,
    "sectorWeightCap": null,
    "selectionCalendar": "ANNUAL",
    "selectionMonths": [12],
    "reweightCalendar": "QUARTERLY",
    "reweightMonths": [3, 6, 9, 12],
    "supportLevel": "PROVISIONAL_PROXY",
    "provenance": "MODEL_ASSUMPTION",
    "confidence": "LOW",
    "officialSourceUrls": [],
    "assumptionId": "example-provider.example-index-policy-v1"
  },
  "fixedIncomeProfile": null,
  "commoditySpotTerms": null,
  "futuresReferenceTerms": null,
  "fundOfFundsMethodologyProfile": null,
  "compositeReferenceProfile": null,
  "alternativeRiskPremiaProfile": null
}
```

`officialSourceUrls`와 `componentBenchmarkRefs`는 정렬해야 한다. 모드 상품은 같은 팩, 기본 팩 또는
함께 활성화된 다른 팩의 벤치마크를 참조할 수 있다. 최종 스냅샷에서 참조가 없거나 같은
`(benchmarkId, version)`이 둘 이상 정의되거나 구성요소 그래프가 순환하면 새 게임 시작을 거부한다.
합성 벤치마크는 `componentBenchmarkRefs`에 의존성을 선언하며 엔진은 component-first 안정 순서로
평가한다. 프로필 안의 실제 benchmark source 집합과 이 필드는 정확히 같아야 한다.

`supportLevel = VERIFIED_RULES`는 공식 문서뿐 아니라 현재 엔진이 규칙을 재현한다는 뜻이다.
`VERIFIED_REFERENCE`는 상품과 기준지수 연결만 확인한 상태이며 상세 구성 엔진을 의미하지 않는다.
현재 `EQUITY_METHODOLOGY`는 SCHD의 Dow Jones U.S. Dividend 100 v1 규칙만 지원한다.
`EQUITY_REFERENCE`, `FIXED_INCOME_CURVE`, `COMMODITY_SPOT`, `FUTURES_CURVE`,
`FUND_OF_FUNDS_METHODOLOGY`, `COMPOSITE_REFERENCE`, `ALTERNATIVE_RISK_PREMIA`는 각각 대응하는
typed 프로필 하나만 요구한다. `COARSE_FACTOR_PROXY`는 모든 상세 프로필/terms가 `null`이어야 한다.
기본 팩은 501개 벤치마크 전부 typed 경로를 가져 `COARSE_FACTOR_PROXY = 0`이지만, 488개는
`PROVISIONAL_PROXY`이므로 미래 실제 holdings나 공식 규칙으로 해석하면 안 된다.

#### 주식 기준 프로필

`equityReferenceProfile`은 region, canonical ISO alpha-2 `countryCodes`, 유니버스, 섹터 정책과
섹터 집합, nullable `themeId`, 정렬된 style 집합, 가중 방식, nullable 목표 수·상한, 선정 달력과
재가중 달력, support/provenance/confidence/출처/assumption ID를 정확히 가진다. 구성 변경과 비중
변경은 서로 다른 일정이므로 `selectionCalendar/selectionMonths`와
`reweightCalendar/reweightMonths`를 생략하거나 하나로 합칠 수 없다. `SECTOR_INDUSTRY`는
`INCLUDED_ONLY`와 비어 있지 않은 sector 집합, `THEMATIC`은 경제적 의미가 있는 안정적인
`themeId`를 요구한다.

confidence별 대표 포지션 상한은 LOW 64, MEDIUM 128, HIGH 256이고 `SINGLE_SECURITY`는 1이다.
기본 팩과 모든 모드를 합친 equity representative 상한 합이 65,536을 넘으면 카탈로그를 거부한다.

#### 고정수익·현금 기준 프로필

`referenceExposure`가 `FIXED_INCOME` 또는 `CASH`인 상품이 참조하는 벤치마크는
`fixedIncomeProfile`이 필수다. 반대 노출의 상품이 같은 벤치마크를 공유하거나 프로필을 붙이는 것도
거부한다. 같은 벤치마크를 여러 상품이 공유하면 모든 상품의 reference exposure가 같아야 한다.

프로필은 `geography`, enum 순서로 정렬한 `currencies`, `assetType`, 유한한
`effectiveDurationYears`, `tenorBand`, `creditQuality`, `rateReset`, `realRateLinked`,
`supportLevel`, `durationProvenance`, URL 순서로 정렬한 `officialSourceUrls`를 정확히 가진다.
지원 자산 유형은 명목국채·물가연동·agency MBS·securitized credit·지방채·preferred/hybrid·
투자등급·하이일드·변동금리·CLO·머니마켓·다중 섹터 신용이다.
공식 수치를 직접 확인하지 않은 듀레이션은 기존 `behaviorProfile` 값과 같아 보이더라도
`CALIBRATED_ASSUMPTION`으로 선언해야 한다. 현재 기본 팩의 고정수익·현금 상품 117개는 모두 typed
기준을 참조하고, component-only 기준까지 합친 `FIXED_INCOME_CURVE` 정의는 124개다. 상세
필드와 수량은 [`FUND_METHODOLOGIES.md`](FUND_METHODOLOGIES.md)를 참고한다.

`fixedIncomeProfile.currencies`는 기초채권 통화이며 `etfProfile.fxProfile`은 상품 환노출·헤지
오버레이다. 두 집합을 같게 만들기 위해 글로벌 기준 포트폴리오를 상장통화 하나로 축약하면 안 된다.
공식 기준통화를 확인하지 못했다면 임의 통화를 늘리지 말고 provisional 단일 프록시로 남긴다.

#### 원자재 현물·선물 기준 프로필

`COMMODITY_SPOT`은 benchmark ref·자산 유형·기준통화, 현물/담보 배분, 보관·보험 비용,
convenience yield, 담보수익 참여율과 provenance를 담은 `commoditySpotTerms`를 요구한다.
`FUTURES_CURVE`는 포트폴리오 유형·배분 방식·담보비율, 정렬된 sleeve 목록과 roll 달력·적격월·
roll window·가격수익률 convention을 담은 `futuresReferenceTerms`를 요구한다. 현물 terms와 선물 terms를
같은 벤치마크에 동시에 둘 수 없고, 내부 `benchmarkRef`와 `baseCurrency`는 외부 정의와 같아야 한다.

현재 기본 팩은 spot 4개와 futures 7개다. 이 중 상품이 직접 참조하는 금현물 2개·선물 6개 외에,
GLDI/SLVO의 gold/silver spot과 USOI의 WTI futures 옵션 기초 참조도 typed 상태다. ETN 상품 자체
벤치마크는 옵션·발행자 신용 wrapper이므로 futures roll을 다시 넣지 않는다. 상품이
`FUTURES_CURVE`를 참조하면 roll·담보수익은 reference 엔진이 계산하므로 상품
`returnTransforms`에 `FUTURES_ROLL`을 중복 선언하면 안 된다. 공식 방법론을 확인하지 못한 terms는
URL을 비워 두고 `MODEL_ASSUMPTION`과 안정적인 `assumptionId`를 써야 한다.

#### 재간접·합성·대체위험 프리미엄 기준

`fundOfFundsMethodologyProfile`은 후보 universe, 선정/가중 모델, 목표·후보 수, 적격 카테고리와
category reference, screen·개별/카테고리/ranked cap, 선정/재가중 달력과 provenance를 요구한다.
FOF category reference는 선행 실행 가능한 주식·고정수익·원자재·선물 기준만 가리킬 수 있으며
FOF/COMPOSITE/ALTERNATIVE/COARSE 중첩은 현재 지원하지 않는다.

`compositeReferenceProfile`은 최대 64개의 정렬된 sleeve와 allocation model, gross/net constraint,
nullable financing·목표 변동성·lookback·duration·drift, 별도 selection/reweight schedule,
support/provenance/confidence/출처/assumption ID를 정확히 가진다. sleeve의 16개 필드는 ID,
tagged source, `LONG/SHORT`, 역할, nullable target·band·risk budget과 각 origin, short borrow,
benchmark 기준통화 hedge와 선택적 MBS IO terms다. `BENCHMARK` source는 허용된 선행 typed engine,
`INSTRUMENT` source는 존재하는 사업회사만 가리킨다.

`alternativeRiskPremiaProfile`은 비어 있지 않은 전략 family 집합과 driver, 신호 모델, long/short/net
constraint, 목표 변동성·lookback·일정, financing·borrow·구현비와 각 origin을 요구한다. 기본 팩은
17개 COMPOSITE 상품 benchmark와 IALT용 component-only ALTERNATIVE 3개를 사용한다.
COMPOSITE→ALTERNATIVE는 허용하지만 같은 kind 중첩과 역방향은 거부한다.
현재 일정 resolver가 명시적으로 지원하는 거래소는 KRX와 NYSE뿐이므로 이 두 engine의
`baseCurrency`는 `KRW` 또는 `USD`여야 한다. 다른 통화를 NYSE 일정으로 암묵 해석하지 않는다.

cross-currency sleeve는 통화가 다를 때 명시적 hedge ratio를 가진다. 현재 내부 헤지 비용 모델이
없으므로 실행 가능한 값은 명시적 무헤지를 뜻하는 `0`뿐이며, 양의 비율은 거부한다. composite 엔진이
sleeve→benchmark 환율을 계산하므로 상품 `etfProfile.fxProfile`에는 benchmark→listing 단일 leg만
남긴다. 기준통화와 상장통화가 같으면 outer hedge ratio와 비용은 0이어야 한다.

팩 합성 후 FOF 후보 합은 65,536, FOF 목표 포지션+composite sleeve+alternative driver 합은
65,536을 넘을 수 없다. 이 검사는 개별 팩이 아니라 활성 모드를 모두 더한 최종 스냅샷에 적용된다.

#### 일일 reset 상품

`DAILY_LEVERAGED` 또는 `DAILY_INVERSE` transform이 있으면 `dailyResetTerms`가 필수이고, 다른
상품은 반드시 `null`이다. `targetLeverage`는 `etfProfile.leverage`와 같아야 한다. 참조는 tagged
객체다.

- `BENCHMARK`: 존재하는 `benchmarkRef`만 두고 `instrumentId`는 `null`이다.
- `INSTRUMENT`: `benchmarkRef`는 `null`이고, 자기 자신이 아닌 존재하는 사업회사 ID를 둔다.

`INSTRUMENT` 참조에는 `directReferenceTerminationRule`이 반드시 필요하고 `BENCHMARK` 참조에서는
반드시 `null`이다. 현재 실행 정책은 `LIQUIDATE_AT_NEXT_VENUE_CLOSE` 하나다. 기초기업의 확정된
청산·상장 종료를 가격 계산 전에 감지하면 해당 종가를 `effectiveNotBefore`로 고정하고, 상품은
최종 NAV를 확정한 뒤 기존 상장 생명주기 경로로 현금 청산된다. 옵션형 상품은 그 종가에 기존
package만 공정가치로 정산하고 새 cycle을 열 수 없다. 이 정책이 공식 상품문서에 명시되지 않았다면
`provenance = MODEL_ASSUMPTION`, 비어 있지 않은 안정 `assumptionId`를 사용한다. 구조 설명 URL을
보존할 수는 있지만 그것만으로 정책 provenance를 `VERIFIED_PRODUCT_DISCLOSURE`로 올리면 안 된다.
직접 참조 상품과 기초기업은 같은 `market`이어야 하고 reset/roll calendar도 그 시장과 일치해야 한다.
서로 다른 거래소의 마지막 종가가 캠페인 종료 뒤로 넘어가는 모호한 청산 계약은 현재 거부한다.

공식 상품 조건과 게임 carry 파라미터의 출처는 분리한다. 예를 들어 목표배율이 발행사 페이지로
확인되어도 `annualFinancingSpread = 0.005`, `collateralYieldParticipation = 1.0`이 게임 보정값이면
`modelParameters.origin = CALIBRATED_ASSUMPTION`, `sourceUrl = null`로 써야 한다. 공식 URL을 찾지
못한 상품을 `VERIFIED_PRODUCT_TERMS`로 선언하거나 가짜 URL을 넣으면 안 된다.

일일 reset과 옵션 전략은 실행 순서가 다른 오버레이이므로 `dailyResetTerms`와
`optionStrategyTerms`를 동시에 선언할 수 없다.

#### 옵션·ETN·CEF 구조

`optionStrategyTerms`는 covered call, option income, buffered put spread 중 정확히 한 kind와
그 kind에 맞는 세부 객체를 요구한다. reference, tenor·roll 달력, 프리미엄 모델, provenance와
가정 ID/공식 URL을 빠짐없이 선언한다. 현재 기본 팩은 ETF 23개와 ETN 3개, 총 26개다.
공식 strike·tenor를 직접 확인하지 않은 값은 `MODEL_ASSUMPTION`으로 남긴다.

CSHI·USDX처럼 현금성 담보와 broad-index put spread를 결합하는 상품은
`cashCollateralizedPutSpreadTerms`를 사용한다. 상품 ID, 별도 money-market `cashBenchmarkRef`,
별도 옵션 `optionReference`, tenor·roll·최대손실·두 strike와 premium model을 정확히 선언한다.
보호형 `BUFFERED_PUT_SPREAD`나 단일 `referenceExposure`로 두 수익원을 뭉개면 안 된다.

ETN은 상품 계약과 발행자 신용 calibration을 분리한다. 같은 issuer ID를 쓰는 ETN은 동일한
`etnIssuerCreditModelParameters`를 가져야 하며, 공식 supplement의 법적 terms를 게임용 신용
spread·hazard 값의 출처로 오인하면 안 된다. CEF도 법적 자본구조와 할인율·초기 debt/preferred
비율·financing spread·discount shock calibration을 분리한다. 초기 레버리지는 terms의 허용 여부와
최소 asset coverage를 만족해야 한다.

필드별 범위, SCHD와 QLD 예제, 현재 501개 벤치마크/508개 상품 프로필의 지원 수준과 제한은
[`FUND_METHODOLOGIES.md`](FUND_METHODOLOGIES.md)를 참고한다. 엄격 파서는 알 수 없는 필드·enum,
중복 키·배열 값, 누락 필드, 정렬 위반, 비정규 날짜·URL, NaN·무한대·범위 이탈 숫자를 하나라도
발견하면 팩 전체를 거부한다.

`VERIFIED_RULES` 방법론은 파싱 뒤 campaign seed로 2040년까지 후보 수와 상한 배분 가능성을
사전검증한다. 현재 SCHD 달력은 3월 연례 재구성과 3·6·9·12월 재가중만 지원한다. 초기화가
불가능하면 부분 상태를 남기지 않고 새 게임 생성을 거부한다.

로비에서 모드를 활성화하는 행위가 그 모드의 선언형 콘텐츠를 다음 새 게임에 적용하는 승인이다.
이는 실행 코드 권한과 별개이므로 `game.contentRegister`를 요청할 필요가 없고, 그 권한을
요청했다고 선언형 콘텐츠가 자동 적용되는 것도 아니다. `game.contentRegister`는 향후 신뢰된
실행 모드가 런타임 API를 통해 콘텐츠를 등록할 때를 위한 예약 권한으로 계속 유지한다.

새 게임을 시작할 때 활성 모드 목록과 검증된 종목팩으로 불변 카탈로그를 만든다. 이후 로비의
활성 상태나 원본 JSON을 바꿔도 진행 중인 게임의 종목 구성은 바뀌지 않는다. 원본 JSON 바이트의
SHA-256을 소문자 64자리 `contentFingerprint`로 모드 설정과 저장 게임에 고정하므로, 공백만
바뀌어도 다른 콘텐츠로 판정한다. 벤치마크, 상품 프로필, 일일 reset·옵션·ETN·CEF 조건,
원자재·FOF·합성·대체위험 프리미엄 reference terms와 calibration 출처도 같은 JSON 바이트에
포함되므로 별도 해시 파일 없이
자동으로 fingerprint에 고정된다. 저장 게임을 불러올 때 설치된 모드의 ID·버전과 함께 이
fingerprint가 일치해야 하며, 종목팩을 수정할 때는 모드 버전도 함께 올리는 것을 권장한다.

## API 권한

| manifest 값 | 범위 |
|---|---|
| `game.read` | 게임 시각, 단계, 종목·시세, 지수, 현금·보유, 주문·체결, 뉴스, 거시 환경, 장 상태, 포트폴리오 조회 |
| `game.playerCommands` | 화면·종목·턴 선택, 진행, 주문·취소, 환전, 읽음·관심종목, 일시정지·재개 |
| `game.marketControl` | 외부 시장 환경 목표 변경 |
| `game.debugConsole` | 앱에 컴파일된 기본 제공 개발자 콘솔 호스트용 권한. 제3자 모드에는 실행 기능을 부여하지 않음 |
| `game.contentRegister` | 향후 신뢰된 실행 모드의 런타임 콘텐츠 등록용 예약 권한. manifest의 선언형 `content`와 무관 |
| `storage.modState` | 향후 모드별 저장 상태용 예약 권한 |

공개 API 버전은 `MOD_API_VERSION`으로 확인한다. 신뢰된 호스트는
`SimulatorGameModApi`를 만들고 manifest에서 허용된 권한만 전달한다. 모드는
`query.snapshot()`으로 분리된 불변 스냅샷을 읽고, suspend 함수인 `commands.execute(...)`로
명령을 보낸다. 명령은 게임 런타임 스레드에 직렬화되며 권한, 진행 중 동시 변경, 입력값과 엔진
불변식을 검사한 뒤 성공·거부·실패를 구조화해 반환한다. 내부 `SimulatorUiState` 전체를 받는
API는 가변 객체 별칭으로 런타임 소유권과 직렬화 경계를 우회할 수 있어 공개하지 않는다.
`events`는 게임 시작, 상태 변경, 턴
완료, 최종 정산 진입, 게임 종료를 최대 64개 전이의 제한된 버퍼로 전달하며, 느린 구독자는
오래된 전이부터 놓칠 수 있다.

manifest 권한은 모드의 요청 목록일 뿐 자동 승인 목록이 아니다. 실행 호스트를 추가할 때는
사용자가 승인한 권한 집합을 모드 ID·버전에 묶어 별도로 저장하고, 요청 권한과 승인 권한의
교집합만 `SimulatorGameModApi`에 전달해야 한다.

## 실행 코드에 대한 신뢰 경계

현재 모드 검색기는 manifest, 커버, 설정을 적재하며 임의의 JAR이나 스크립트를 자동 실행하지
않는다. 같은 JVM 프로세스에 올린 제3자 코드는 파일·네트워크·프로세스·리플렉션 접근을 API
권한으로 차단할 수 없기 때문이다. API 권한은 게임 기능에 대한 논리 경계이지 JVM 샌드박스가
아니다.

기본 제공 모드나 별도로 신뢰한 실행 호스트는 위 API를 바로 사용할 수 있다. 제3자 실행 모드를
지원할 때는 이 API 뒤에 별도 프로세스와 제한된 IPC, 서명·신뢰 확인, 시간·메모리·명령 수 제한을
추가해야 한다. 로컬 HTTP 서버나 무제한 ClassLoader 실행은 모드 형식의 일부로 간주하지 않는다.

## 기본 제공 개발자 콘솔

앱은 첫 실행 시 `market-ledger.debug` 모드를 모드 폴더에 안전하게 적재한다. 기본값은 비활성이며,
로비의 **모드** 메뉴에서 활성화한 뒤 새 게임을 시작해야 해당 캠페인에 고정된다. 게임 중 물리
키보드의 백틱(`` ` ``)을 누르면 콘솔이 열리고, `help`를 입력하면 현재 버전의 명령과 정확한
인자 형식을 확인할 수 있다.

대표 명령은 다음과 같다.

- `turn jump <turn|max> [--reset]`, `turn cancel`: 정상 시간 진행 경로로 턴 이동 및 취소
- `price set <instrument> <amount> <native|krw|usd>`, `price change <instrument> <percent>`
- `cash add|set <krw|usd> <amount>`, `fx set <usdKrw>`, `fx change <percent>`
- `ending settle|finish`: 남은 일정을 계산해 정산 진입 또는 종료
- `value get|set|add <path> [number]`: `cash.*`, `fx.usdkrw`, `price.*`, `force.*` 허용 목록만 조작
- `rule set <fractional|auto_exchange|ironman> <on|off>`, `force set <name> <0..1>`
- `event list [filter]`, `event describe <templateId>`, `event trigger <templateId> [target]`
- `stocks`, `stock`, `status`, `orders cancel-all`, `pause`, `resume`, `save-check`, `clear`

과거 턴 이동은 현재 진행을 보존한 채 시간을 되감지 않는다. 데이터 손실을 명시하는 `--reset`을
붙였을 때만 같은 시드와 옵션으로 게임을 다시 만들고 목표 턴까지 재생한다. 즉시 정산도 게임
시각만 바꾸지 않고 세금·기업행동·이벤트를 포함한 시간 진행을 수행하므로 계산 중에는 `turn
cancel`로 중단할 수 있다.

가격·현금·환율·규칙 변경은 앱 내부의 타입이 지정된 디버그 명령으로만 실행되며, 매 변경 뒤
저장 불변식 검사를 통과하지 못하면 직전 스냅샷으로 되돌린다. `value`는 reflection이나 임의
속성 경로를 받지 않는다. 이벤트도 원시 payload를 받지 않고 기본 템플릿 ID와 유효 대상만 받아
발생 확률을 우회하며, 중복 활성 이벤트·쿨다운·one-shot 규칙은 유지한다.

이 모드는 로컬 개발·QA용 치트 도구다. 활성화한 캠페인의 공정성은 보장하지 않으며, manifest의
`game.debugConsole` 요청만으로 제3자 코드가 콘솔 호스트나 내부 런타임 접근 권한을 얻지는 않는다.

## 적재 제한

- manifest 최대 256 KiB, 엄격한 UTF-8
- 종목팩 최대 4 MiB, 모드당 1~512종, 엄격한 UTF-8과 JSON 스키마 검증
- 팩당 benchmark 최대 1,024개, 최종 카탈로그 최대 2,600개
- 최종 equity representative 상한 합 65,536, FOF 후보 합 65,536,
  FOF 목표+composite sleeve+alternative driver 합 65,536
- DTD, 외부 엔티티, 외부 스키마, 알 수 없거나 중복된 요소 거부
- 커버 최대 8 MiB, 최대 4096×4096
- 경로 정규화 후 모드 루트 밖으로 나가는 파일 거부
- manifest, 커버, 종목팩과 모드 폴더의 심볼릭 링크 거부
- 손상된 모드는 항목별 오류로 격리하고 정상 모드 목록은 계속 표시
