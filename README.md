# Market Ledger 2040

대한민국 거주 개인의 **일반 증권계좌**를 기준으로 만든 KOSPI·KOSDAQ·미국주식 턴제 투자 시뮬레이터입니다. 게임은 2026-08-07 09:00 KST에 시작하며, 모든 진행 명령을 1시간 단위로 처리한 뒤 2040-12-31 23:00 KST에 최종 정산합니다.

> 실제 종목명을 사용하지만 가격, 기업 지표, 뉴스와 미래 사건은 투자 정보가 아닌 시뮬레이션용 게임 데이터입니다. 세금은 법률 기준일 2026-08-07의 규칙을 2040년까지 동결한 교육용 추정치입니다.

## 기술 스택

- Kotlin 2.3.20
- Kotlin Multiplatform, JVM Desktop 타깃
- Compose Multiplatform 1.10.3 / Material 3
- AndroidX Lifecycle ViewModel
- kotlinx.coroutines 1.10.2
- kotlinx-datetime 0.7.1
- Gradle 9.5

차트는 외부 차트 라이브러리 없이 Compose `Canvas`로 구현합니다. 시장·세금·주문 엔진은 UI와 분리된 순수 Kotlin 코드이며 시드 기반으로 재현할 수 있습니다.

## 구현 범위

- 1시간, 4시간, 12시간, 1일, 1주일 진행 — 내부 처리는 항상 1시간씩 순차 실행
- KRX 정규장, 미국 현지 정규장과 DST, 주말·휴장 주입 구조
- KRX 호가 단위와 ±30% 가격제한폭, 미국 시장 서킷브레이커 근사
- 시장·산업·기업·거시·지정학·기업행동을 포함한 60개 이상의 규칙 기반 이벤트
- 시장가·지정가, DAY·GTC·IOC·FOK, 미체결·취소·체결 원장
- KRW/USD 예수금, 자동·수동 환전, 평균단가, 실현·미실현손익
- 캔들·거래량·이동평균, 자산곡선, 낙폭, 수익률 분포, 자산·섹터 배분 차트
- 국내 거래세·대주주 양도세, 국외주식 연간 손익통산, 배당 원천징수, 고배당 분리과세
- FIFO 세무 원가·취득/매도 환율 원장, 다음 해 납부일 자동 현금 반영
- 스키마 버전 JSON 수동 저장·복원, 원자적 파일 교체와 손상/크기 검증
- 2040년 최종 정산 엔딩

세금 규칙과 구현 가정은 [docs/TAX_RULES_2026.md](docs/TAX_RULES_2026.md)에 정리되어 있습니다.

## Windows 실행과 검증

```powershell
.\gradlew.bat :composeApp:run
.\gradlew.bat :composeApp:desktopTest
.\gradlew.bat :composeApp:build
.\gradlew.bat :composeApp:packageMsi
```

MSI 설치 파일은 Windows에서 `composeApp\build\compose\binaries\main\msi`에 생성됩니다. 설치 프로그램은 사용자 단위 설치, 설치 위치 선택, 바탕 화면 바로가기와 시작 메뉴 그룹을 지원합니다. GitHub Actions의 `Windows MSI` 워크플로를 수동 실행해도 동일한 설치 파일을 받을 수 있습니다.

명령을 직접 입력하지 않으려면 저장소 루트의 `build-windows-installer.bat`를 실행하면 테스트 후 MSI를 생성합니다. MSI 패키징은 Windows 환경에서 실행합니다.

게임 저장 파일의 기본 위치는 `%APPDATA%\MarketLedger2040\savegame.json`입니다.

## 새 종목 추가

기본 종목은 `StockCatalog.definitions`의 데이터 행으로 관리합니다. `StockDefinition` 한 건만 추가하면 검색, 시장 분류, 가격·이벤트·주문 엔진에서 자동으로 사용할 수 있습니다.

```kotlin
StockDefinition(
    symbol = "TICKER",
    name = "표시 이름",
    englishName = "English Name",
    market = Market.NASDAQ,
    sector = Sector.INFORMATION_TECHNOLOGY,
    initialPrice = 100.0,
    volatility = 0.30,
    dividendYield = 0.01,
    marketCap = 10_000_000_000.0,
    sharesOutstanding = 100_000_000L,
    description = "게임 내 기업 설명",
)
```

사용자 종목팩은 `StockCatalog.withAdditional(...)`, 미국 소수점 거래 유니버스는 `StockCatalog.withUsFractionalTrading(...)`으로 불변 목록을 만들 수 있습니다.

## 구조

```text
domain/model       불변 종목·시세·주문·포트폴리오·이벤트 모델
domain/data        데이터 중심 종목 카탈로그
domain/time        게임 시계와 시장 세션
domain/simulation  가격·호가·이벤트 엔진
domain/tax         시행일 기반 세금·수수료 정책팩과 계산기
presentation       게임 세션 통합 상태와 ViewModel
persistence        버전형 저장 포맷과 데스크톱 원자적 파일 저장소
ui                 화면, 거래창, 차트, 테마
```
