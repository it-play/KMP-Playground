package com.amond.kmpbook.domain.model.instrument

enum class PrincipalRisk(val displayName: String, val explanation: String) {
    ORDINARY_MARKET("시장 가격", "기초자산 가격과 시장 위험에 따라 원금 손실이 발생합니다."),
    DAILY_RESET_DECAY("일일 재조정 감가", "횡보와 높은 변동성이 지수의 누적수익률과 장기 성과를 크게 벌어지게 합니다."),
    OPTION_INCOME_EROSION("옵션인컴·원금잠식", "분배금의 일부가 원금환급으로 재분류되거나 상승참여 제한으로 NAV가 약화될 수 있습니다."),
    RATE_AND_CREDIT("금리·신용", "듀레이션, 신용스프레드와 부도율 변화가 가격을 좌우합니다."),
    FUTURES_ROLL("선물 롤오버", "콘탱고·백워데이션과 재투자 비용으로 현물과 장기 성과가 달라질 수 있습니다."),
    PREMIUM_DISCOUNT("괴리율·레버리지", "폐쇄형 펀드는 NAV와 시장가가 달라지고 차입비용이 손익을 확대합니다."),
    ISSUER_CREDIT("발행사 신용", "ETN은 기초지수 외에 발행사 신용, 조기상환과 만기 위험을 부담합니다."),
}
