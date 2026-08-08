package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** 현실 종목과 이벤트를 함께 분류하기 위한 넓은 산업군. */
enum class Sector(val displayName: String) {
    SEMICONDUCTOR("반도체"),
    INFORMATION_TECHNOLOGY("정보기술"),
    INTERNET_PLATFORM("인터넷·플랫폼"),
    COMMUNICATION_SERVICES("커뮤니케이션 서비스"),
    CONSUMER_DISCRETIONARY("경기소비재"),
    CONSUMER_STAPLES("필수소비재"),
    FINANCIALS("금융"),
    HEALTHCARE_BIO("헬스케어·바이오"),
    AUTOMOTIVE("자동차"),
    INDUSTRIALS("산업재"),
    AEROSPACE_DEFENSE("우주항공·방산"),
    ENERGY("에너지"),
    MATERIALS_CHEMICALS("소재·화학"),
    BATTERY("이차전지"),
    ROBOTICS("로봇"),
    ENTERTAINMENT("엔터테인먼트"),
    GAMING("게임"),
    RETAIL_ECOMMERCE("유통·전자상거래"),
    TRANSPORTATION_LOGISTICS("운송·물류"),
    UTILITIES("유틸리티"),
    REAL_ESTATE("부동산"),
    CONGLOMERATE("복합기업"),
    OTHER("기타"),
}
