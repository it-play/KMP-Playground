# Canonical tax accounting replay

게임 저장 schema 49는 저장된 실현손익을 연간 세금 계산의 입력 권위로 신뢰하지 않는다.
체결·거래비용·기업행동·ETF ex-date origin·일반 분배 ROC를 전역 회계 순번대로 다시 적용해
현재 보유 수량·평균원가·누적 실현손익, FIFO lot, 모든 매도 `RealizedGainRecord`, 분배별 초과
ROC를 하나의 순수 계산기로 재구성한다.
Runtime 복원과 저장 Validator는 같은 계산기를 사용하고 결과가 저장값과 정확히 일치해야 한다.

국내/외 주식과 ETF의 양도 세무 분류도 공통 resolver가 종목 구조, 매도 전 수량, 직전 연말
스냅샷, 기업행동-adjusted 발행주식수에서 다시 결정한다. 따라서 실현손익의 종목·시장·세무
분류를 바꾸고 연간 원장까지 함께 바꾼 저장도 canonical replay에서 거부된다.

전체 과거 거시 시계열은 저장하지 않으므로 체결 결제일 세무 FX, ETF 지급일 배당 FX,
ex-date ROC 원가환산 FX는 명시적인 관측 사실 경계다. 이 값들은 자체 해시로 정본인 척하지
않고, 각각 거래/FIFO/분배/연간 세금의 모든 소비 경로에서 동일 값으로 strict하게 결박한다.
다만 미결제 세무 FX 대상 ID는 관측 사실이 아니다. 미국 거래소 체결과 시장 현지일, canonical
T+ 결제 달력에서 매번 다시 계산하며 계약상 지급일 현금정산은 이 집합에 포함하지 않는다.

분배 ROC 자격과 세무 coverage는 시장·법적 구조를 읽는 공통 정책으로 결정한다. 한국 상장
ETF의 경제적 coverage 신호는 세무 ROC로 전환하지 않으며, 미국 상장 open-end ETF와 CEF만
origin 또는 구조별 분배 분류에 따라 FIFO 원가를 낮출 수 있다.

일별 포트폴리오 관측에는 `accountingSequenceExclusiveUpperBound`를 함께 저장한다. 관측 시각보다
이른 사건과, 같은 시각이면서 이 상한보다 작은 전역 회계 순번만 그 점에 포함한다. 이 경계로
현금·보유 수량/평균원가·FIFO·분배 미수금·실현손익·수수료/세금 누계를 각 일별 점마다 다시
계산한다. 과거 종가와 USD/KRW 평가는 보존 한도가 있는 가격·거시 이력으로 완전 재생할 수 없어
유한 범위와 거래소 tick에 묶인 관측 valuation fact로 남는다. 이 fact를 거시경로의 canonical
재생값이라고 간주하지 않는다.
