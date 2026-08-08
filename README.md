# KMPBook

Kotlin Multiplatform 앱 프로젝트

---

## 앱 히스토리

| <img src="assets/body-fit-icon.svg" alt="Body Fit MVP" width="108" height="108"> |  <img src="assets/meal-icon.svg" alt="급식" width="108" height="108">   | <img src="assets/market-ledger-icon.png" alt="Market Ledger 2040" width="108" height="108"> |
|:--------------------------------------------------------------------------------:|:---------------------------------------------------------------------:|:-------------------------------------------------------------------------------------------:|
|                                 **Body Fit MVP**                                 | [**급식**](https://github.com/it-play/KMP-Playground/releases/tag/meal) |                                   **Market Ledger 2040**                                    |
|                                   AI 신체 치수 측정                                    |                              NEIS 급식 조회                               |                                       한·미 주식시장 시뮬레이터                                        |

## Market Ledger 2040

한·미 시장의 종목, ETF·ETN·CEF, 뉴스와 거래소 절차를 턴제로 시뮬레이션하는 Compose Desktop 앱입니다.

```bash
./gradlew :composeApp:run
./gradlew :composeApp:check --no-daemon
```

뉴스가 시장·경제 요인·산업·회사로 전달되는 공식과 확장 규칙은
[2층 가중 인과 시장 엔진](docs/CAUSAL_MARKET_ENGINE.md)에 정리되어 있습니다.
