# SCHD / Dow Jones U.S. Dividend 100 구현 사례 (참고용)

이 문서는 프로젝트 규범이나 새 종목의 구현 절차, 그대로 복사하는 템플릿이 아니다. 2026년 8월에 확인한 공개 자료를 바탕으로 완성한 SCHD v2 구현 사례를 다른 종목 작업에서 **참고**할 수 있게 정리한 기록이며, 다른 종목에서는 해당 상품과 지수의 최신 공식 문서를 별도로 다시 검증한다.

## 확인한 공식 자료와 버전 경계

SCHD가 추종하는 Dow Jones U.S. Dividend 100 Index의 공개 규칙은 다음 자료를 서로 대조했다.

- [Dow Jones Dividend Indices Methodology (2026년 8월 확인)](https://www.spglobal.com/spdji/en/documents/methodologies/methodology-dj-dividend-indices.pdf)
- [S&P Dow Jones Indices Equity Indices Policies & Practices](https://www.spglobal.com/spdji/en/documents/methodologies/methodology-sp-equity-indices-policies-practices.pdf)
- [2026-04-17 구성종목 간 합병 처리 변경 공지](https://www.spglobal.com/spdji/en/documents/indexnews/announcements/20260417-1482712/1482712_dj-dividend-indices-coac-update-20260417.pdf)
- [Dow Jones U.S. Dividend 100 Index 페이지](https://www.spglobal.com/spdji/en/indices/dividends-factors/dow-jones-us-dividend-100-index/)

실행 방법론 식별자는 `builtin:base/schd-dividend-100@v2`, 벤치마크 식별자는 `spdj-dow-jones-us-dividend-100@v2`다. 실행 동작이 바뀌면 방법론과 이를 감싸는 벤치마크 버전이 함께 구분되도록 구성했다. 이 상태가 포함되는 게임 저장 형식은 schema 40이다.

- 방법론 식별자: [EquityMethodologyRef.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/model/methodology/EquityMethodologyRef.kt)
- 내장 방법론 등록: [BuiltInEquityMethodologies.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/methodology/BuiltInEquityMethodologies.kt)
- 벤치마크·SCHD 상품 선언: [base-catalog.json](../../composeApp/src/commonMain/composeResources/files/instruments/base-catalog.json)
- 저장 형식 버전: [GameSaveStorage.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/persistence/storage/GameSaveStorage.kt)

## 제품, 벤치마크, 기준 포트폴리오의 분리

SCHD에서는 세 층을 서로 다른 책임으로 두었다.

1. **상품 층**은 상장 ETF인 SCHD의 비용, 분배 주기, 복제 방식, 추적오차를 가진다. 현재 카탈로그 값은 개방형 ETF, 물리적 완전복제, total return, 연 0.06% 비용, 연율 추적오차 변동성 0.2%, 분기 분배다.
2. **벤치마크 층**은 공식 출처, 방법론 버전, 기준통화, 실행 provider를 연결한다. 여러 상품이 같은 벤치마크를 참조할 수 있다.
3. **기준 포트폴리오 층**은 구성종목, 현재 비중, 목표 비중, 일정, 대기 계획, revision과 원장을 보유한다. ETF의 법적 보유 원장과는 별개다.

이 분리로 지수의 gross reference return과 구성 변경은 공유하면서, 상품별 비용·환헤지·추적오차·분배는 상품 경로에서 적용했다. 관련 경계는 [BenchmarkDefinition.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/model/fund/BenchmarkDefinition.kt), [FundProductProfile.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/model/fund/FundProductProfile.kt), [ReferencePortfolioState.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/model/fund/ReferencePortfolioState.kt)에 드러난다. 실제 수익률 오버레이와 현금 분배 연결은 [FundProductOverlayEngine.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/simulation/fundproduct/FundProductOverlayEngine.kt), [PriceEngine.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/simulation/price/PriceEngine.kt), [SimulatorRuntime.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/presentation/simulator/SimulatorRuntime.kt)에 나뉘어 있다.

## 합성 미국 광범위 유니버스와 시점 일치 신호

실제 미래 기업 목록을 고정 데이터로 넣지 않고, 캠페인 seed로 재현되는 내부 미국 광범위 주식 유니버스를 만들었다. 이 유니버스에서는 `companyId` 하나가 주상장 `assetId` 하나에 정확히 대응해 같은 회사 기초자료가 복수 주식 클래스에 중복 반영되지 않는다. 거래 가능한 상품 카탈로그와도 분리돼 있다.

후보 스냅샷은 관찰 시점에 알려진 원천값을 보관하고, 선정 신호는 원천값에서 다시 계산한다.

| 공개 규칙에 대응한 신호 | SCHD v2에서 사용한 원천과 계산 |
|---|---|
| 배당 지급 이력 | 정규 배당 지급 연수 10년 이상 |
| 유동주식수 조정 시가총액 | `floatMarketCap` 5억 달러 이상 |
| 유동성 | 관찰일 직전 3개월 일별 거래대금 평균 200만 달러 이상 |
| 업종 제외 | GICS classification code `6010`, `402040`을 정확히 제외 |
| IAD 수익률 | 특별배당을 제외한 정규 고정 연환산 DPS / 관찰일 주가 |
| 잉여현금흐름/총부채 | `(영업현금흐름 - 자본적 지출) / 총부채`; 총부채 0인 후보는 별도 boolean으로 앞 그룹에 배치 |
| 자기자본이익률 | basic EPS / BVPS; BVPS가 음수인 후보는 뒤 그룹에 배치 |
| 5년 배당 성장 | 최근 정규 DPS / 최근 5개년 정규 DPS 평균 - 1; 특별배당 제외 |
| 월간 배당 유지 검토 | 예정 지급 생략과 무기한 배당 중단을 별도 boolean으로 관찰 |

원천 모델은 [SimulatedReferenceEquity.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/simulation/fund/SimulatedReferenceEquity.kt), 시점별 계산은 [SimulatedReferenceEquitySnapshot.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/simulation/fund/SimulatedReferenceEquitySnapshot.kt), provider에 전달되는 신호 이름은 [StandardEquityMethodologySignalIds.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/methodology/StandardEquityMethodologySignalIds.kt)에 있다. 유니버스 생성, point-in-time 스냅샷과 신호 변환은 [ReferencePortfolioEngine.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/simulation/fund/ReferencePortfolioEngine.kt)가 담당한다.

## 선정, 비중, 일정 처리 사례

SCHD v2 provider인 [SchdDividend100Policy.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/methodology/builtin/SchdDividend100Policy.kt)는 다음 동작을 한곳에 모았다.

- 10년 배당, 5억 달러 FMC, 3개월 ADVT 200만 달러, GICS 제외 조건을 통과한 후보를 정규 IAD 수익률 내림차순으로 정렬하고 상위 절반을 남긴다.
- 남은 후보에 FCF/부채, ROE, IAD 수익률, 5년 배당 성장의 네 서열을 부여한다. 부채 0 후보는 FCF/부채 서열의 앞 그룹, 음수 BVPS 후보는 ROE 서열의 뒤 그룹에 둔다.
- 네 서열의 합이 작은 순서로 종합 순위를 만들며, 동률은 IAD 수익률과 `assetId`로 결정한다.
- 기존 구성종목은 종합 순위 200위 안에서 먼저 유지하고, 빈자리를 신규 후보로 채워 정기 재구성 결과를 100종목으로 만든다.
- 유동주식수 조정 시가총액 비중에서 종목당 4%, 섹터당 25% 상한을 반복 적용한다.

일정 계산은 [SchdDividend100Schedule.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/methodology/builtin/SchdDividend100Schedule.kt)과 [SchdDividend100Calendar.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/methodology/builtin/SchdDividend100Calendar.kt)에 분리했다. 3월에는 연간 재구성, 3·6·9·12월에는 비중 조정이 발생한다. 연간 선정 기준일은 2월 셋째 금요일, 연간 비중 기준일은 효력일 12거래일 전이다. 나머지 분기의 비중 기준일은 해당 월 첫 금요일 이틀 전을 기본으로 하며 휴장일이면 앞 거래일로 이동한다. 정기 변경은 셋째 금요일 다음 월요일을 기본 효력일로 삼아 휴장일을 건너뛴다.

일일 상한 검토에서는 현재 비중이 4.7%를 넘는 종목들의 합이 22%를 넘으면 관찰일로부터 2거래일 뒤 비중 조정 계획을 만든다. 분기 리밸런싱 월의 둘째 금요일 전 수요일부터 셋째 금요일 다음 월요일까지는 관찰일 기준으로 이 검토를 동결한다.

월간 배당 유지 검토는 Approach C의 정보 창을 구현했다. 일반 월은 21일, 2월은 18일을 cutoff로 삼아 그날 이후 첫 미국 거래일에 관찰하고, 예정 배당 생략 또는 무기한 중단이 확인된 구성종목을 다음 달 첫 거래일에 대체 편입 없이 제거한다. 연기·유예나 단순 감액은 이 제거 조건에 넣지 않았다.

## 기업행동 처리 사례

기업행동 사실과 방법론 결정을 분리했다. [ReferencePortfolioCorporateAction.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/model/fund/ReferencePortfolioCorporateAction.kt)는 발표일·효력일·대상·상대 종목·대가 형태·가치 이전 비율만 보관하고, SCHD provider가 현재 구성과 적격 유니버스를 받아 처리 결과를 결정한다.

- **합병**: 인수자가 구성종목이 아니면 대상만 제거한다. 구성종목 간 합병에서 2026-04-30 이후 규칙은 인수자의 적격성을 다시 확인한다. 적격 인수자는 유지하며 주식·혼합 대가의 가치 이전분을 인수자 포지션에 더하고, 현금 대가는 주식수를 늘리는 효과가 없어 대상만 제거한다. 인수자가 부적격이면 대상과 인수자를 함께 제거한다.
- **분사**: ex-date에 모기업 가치를 모기업과 자회사로 나눠 자회사를 임시 편입한다. 이때 최대 구성 수는 101이다. 자회사는 적어도 정규 거래일 한 번을 거친 다음 거래일에 제거한다.
- **소멸 제거**: 합성 유니버스에서 거래 종료가 예정된 현재 구성종목 한 종목을 대체 편입 없이 제거한다.

기업행동 종류가 [ReferencePortfolioCorporateActionKind.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/model/fund/ReferencePortfolioCorporateActionKind.kt)에 합병·분사·소멸 제거만 있는 이유도 코드 주석으로 남겼다. 가격과 주식 수를 따로 보유하지 않고 float market value를 보유하는 기준 층에서는 분할, 보통 주식 수 변화, IWF 변화가 가치 중립이라 별도 상태 변경이 없기 때문이다.

## 대기 계획, 동일 날짜 처리, 원장

[ReferencePortfolioPlan.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/model/fund/ReferencePortfolioPlan.kt)은 기준일 종가에 확정되고 효력일 개장까지 유지되는 계획이다. SCHD에서는 다음 정보를 저장해 미래 데이터나 현재 상태로 과거 결정을 다시 쓰지 않게 했다.

- 연간 선정 종가의 기존 구성종목 스냅샷과 당시 사용 가능한 데이터의 경계
- 비중 기준일의 정렬된 `assetId -> float market value` 입력
- provider가 계산한 목표 비중과 기준일 이후 움직임이 누적된 현재 비중
- 계획을 만든 선정일, 비중 기준일, 효력일, 편입·편출 ID와 기업행동 사실

기준일 이후 대기 중인 계획의 포지션도 시간별 수익률로 drift한다. 앞선 제거·합병·분사와 아직 효력이 오지 않은 정기 계획이 교차하면 계획을 효력일·종류 우선순위·ID 순으로 재배치하고 각 선행 결과를 다음 계획의 baseline으로 사용한다. 기존 계획에 있던 종목은 관찰된 post-reference drift를 보존한다. 늦게 들어온 대체 종목은 아래 구현 범위에서 설명한 activation drift를 사용한다.

같은 효력일에는 합병·분사 후속 제거·소멸 제거, 월간 배당 유지 제거, 정기 재구성, 정기 비중 조정, 일일 상한 비중 조정, 분사 임시 편입 순으로 처리하고 같은 종류는 계획 ID로 순서를 고정했다. 각 적용은 revision을 하나 올리고 [ReferencePortfolioRecord.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/model/fund/ReferencePortfolioRecord.kt)에 편입·편출, 회전율, 결과 종목 수, 전후 구성 hash를 남긴다. 같은 날짜에 여러 record가 생겨도 앞 record의 after hash가 다음 record의 before hash로 이어진다.

## 저장 복원 검증

[SimulatorUiStateValidator.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/persistence/validation/SimulatorUiStateValidator.kt)는 저장 JSON의 모양만 확인하지 않고 현재 카탈로그와 캠페인 seed에서 canonical 상태를 다시 계산한다. SCHD 관련 검증에는 다음 내용이 포함된다.

- 벤치마크·방법론 버전, provider 일정, 구성종목 identity와 상한
- 대기 계획의 시간 창, 동일 날짜 우선순위, 순차 baseline과 편입·편출 차집합
- 선정 종가 incumbent 스냅샷, 사용 가능 데이터 경계, 선정 종목과 순위
- 정렬된 불변 weight-reference 입력과 provider가 재계산한 목표 비중
- seed에서 재생성한 기업행동 사실, provider 결정과 기업행동 목표 비중
- 발표 종가 구성과 선행 실행을 재생한 기업행동 대기·적용 단계의 완전한 집합
- record ID, 연속 revision, 전후 hash 사슬, 역방향 membership 복원

이 검증은 저장 파일에서 일부 비중이나 기업행동을 서로 일관되게 보이도록 함께 변조한 경우도 canonical 입력과 비교해 거부하는 방향으로 구성했다. schema 40은 이 상태와 계획·원장 필드를 하나의 저장 버전 경계로 묶는다.

## 공식 규칙과 합성 구현의 경계

다음 항목은 공개 방법론을 공식 규칙으로 옮긴 부분이 아니라, 2026~2040 캠페인을 결정적으로 실행하기 위해 SCHD v2가 선택한 범위와 가정이다.

- **늦은 대체 종목의 drift**: 공개 자료에는 대기 중 재구성 계획에서 뒤늦게 활성화된 대체 종목의 reserve-security share 배정 방식이 없다. rebase 시 기존 종목은 관찰 drift를 유지하고 새 종목의 activation drift factor는 `1.0`으로 둔다.
- **특별배당**: 선정 IAD와 5년 배당 성장에서 특별배당을 제외한다. 특별배당의 개별 ex-date·현금·divisor 사건은 만들지 않았고, 기준 포트폴리오의 정규 연간 income yield에도 특별배당을 넣지 않았다.
- **합병 후 신규 법인**: 합성 합병은 이미 유니버스에 존재하는 인수자만 사용한다. 새로 설립된 존속 법인에 과거 배당 이력을 이전할지에 관한 지수위원회의 재량은 현재 사건 형태로 합성하지 않았다.
- **분할과 주식 수 조정**: 기준 층이 가격×주식 수 대신 float market value를 직접 보유하므로, 가치 중립인 분할·통상 주식 수·IWF 변화는 no-op으로 취급한다.
- **소멸 가격**: terminal event는 거래 가능한 가격에서 제거되는 경우를 합성한다. 가격이 0이거나 극소인 종목에 대한 위원회 판단 시나리오는 생성하지 않았다.
- **정규 배당 현금흐름**: 구성종목 정규 IAD를 현재 비중으로 합산한 연간 yield를 시간별 carry와 상품의 분기 분배 재원에 연결한다. 실제 개별 종목의 이산적인 ex-date·지급일을 재현한 현금흐름이 아니라 연환산 accrual 근사다.
- **관리자 재량**: 공개 문서가 S&P Dow Jones Indices의 재량을 허용하는 부분은 deterministic 합성 사건으로 명시한 경우만 실행한다. 공개 자료만으로 특정할 수 없는 위원회 판단을 추측해 생성하지 않았다.

이 경계는 다른 종목에 그대로 적용되는 공통 전제가 아니다. 예를 들어 다른 지수는 reserve list, 신규 법인 처리, 가격 0 제거, 특별배당 divisor 조정, 복수 주식 클래스가 공개 규칙의 핵심일 수 있다.

## 검증 예시

개발 중 임시 검증 harness에서 seed 1~10 각각을 2026-08-07부터 2040-12-31까지 시간 단위로 진행했다. 검증 당시 원장은 seed별 65~74개, 전체 700개의 구성 변경 record를 남겼다. 종류별로는 연간 재구성 140, 정기 비중 조정 440, 일일 상한 조정 2, Approach C 제거 73, 합병 23, 분사 편입·제거 각 8, 소멸 제거 6개였다. 현재 구성종목을 대상으로 생성된 canonical 기업행동이 모두 적용됐고, 저장 validator와 동일 날짜 record hash 사슬 검사도 통과했다. 별도로 기업행동 계획 하나만 삭제한 7개 저장 변조 사례도 모두 거부됐다. 이 수치와 사건 분포는 seed·캠페인 범위·후속 구현에 따라 달라질 수 있는 검증 예시다. 임시 harness 코드는 최종 구현에 포함하지 않았다.

## 코드 위치 요약

- provider 계약과 compiler: [EquityMethodologyPolicy.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/methodology/EquityMethodologyPolicy.kt), [BenchmarkMethodologyCompiler.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/simulation/fund/BenchmarkMethodologyCompiler.kt)
- SCHD provider와 달력: [SchdDividend100Policy.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/methodology/builtin/SchdDividend100Policy.kt), [SchdDividend100Schedule.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/methodology/builtin/SchdDividend100Schedule.kt), [SchdDividend100Calendar.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/methodology/builtin/SchdDividend100Calendar.kt)
- 합성 데이터와 실행 host: [SimulatedReferenceEquity.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/simulation/fund/SimulatedReferenceEquity.kt), [SimulatedReferenceEquitySnapshot.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/simulation/fund/SimulatedReferenceEquitySnapshot.kt), [ReferencePortfolioEngine.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/simulation/fund/ReferencePortfolioEngine.kt)
- 상태·계획·원장: [ReferencePortfolioState.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/model/fund/ReferencePortfolioState.kt), [ReferencePortfolioPlan.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/model/fund/ReferencePortfolioPlan.kt), [ReferencePortfolioRecord.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/domain/model/fund/ReferencePortfolioRecord.kt)
- strict 저장 검증: [SimulatorUiStateValidator.kt](../../composeApp/src/commonMain/kotlin/com/amond/kmpbook/persistence/validation/SimulatorUiStateValidator.kt)
