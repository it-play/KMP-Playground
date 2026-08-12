package com.amond.kmpbook.domain.data

import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.BenchmarkEngineKind
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.BenchmarkSupportLevel
import com.amond.kmpbook.domain.model.fund.AlternativeRiskPremiaDriver
import com.amond.kmpbook.domain.model.fund.AlternativeRiskPremiaProfile
import com.amond.kmpbook.domain.model.fund.AlternativeRiskPremiaSignalDirectionPolicy
import com.amond.kmpbook.domain.model.fund.AlternativeRiskPremiaSignalModel
import com.amond.kmpbook.domain.model.fund.AlternativeRiskPremiaStrategyFamily
import com.amond.kmpbook.domain.model.fund.CompositeAllocationModel
import com.amond.kmpbook.domain.model.fund.CompositeConfidence
import com.amond.kmpbook.domain.model.fund.CompositeDurationConstraint
import com.amond.kmpbook.domain.model.fund.CompositeExposureConstraint
import com.amond.kmpbook.domain.model.fund.CompositeParameterOrigin
import com.amond.kmpbook.domain.model.fund.CompositeRebalanceCalendar
import com.amond.kmpbook.domain.model.fund.CompositeRebalanceSchedule
import com.amond.kmpbook.domain.model.fund.CompositeReferenceProfile
import com.amond.kmpbook.domain.model.fund.CompositeReferenceSleeve
import com.amond.kmpbook.domain.model.fund.CompositeReferenceSource
import com.amond.kmpbook.domain.model.fund.CompositeReferenceSourceKind
import com.amond.kmpbook.domain.model.fund.CompositeRuleProvenance
import com.amond.kmpbook.domain.model.fund.CompositeSleeveDirection
import com.amond.kmpbook.domain.model.fund.CompositeSleeveRole
import com.amond.kmpbook.domain.model.fund.EquityEligibleUniverse
import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile
import com.amond.kmpbook.domain.model.fund.EquityRebalanceCalendar
import com.amond.kmpbook.domain.model.fund.EquityReferenceConfidence
import com.amond.kmpbook.domain.model.fund.EquityReferenceProfile
import com.amond.kmpbook.domain.model.fund.EquityReferenceProvenance
import com.amond.kmpbook.domain.model.fund.EquityReferenceRegion
import com.amond.kmpbook.domain.model.fund.EquityReferenceWeightingModel
import com.amond.kmpbook.domain.model.fund.EquitySectorPolicy
import com.amond.kmpbook.domain.model.fund.EquityStylePolicy
import com.amond.kmpbook.domain.model.fund.FixedIncomeAssetType
import com.amond.kmpbook.domain.model.fund.FixedIncomeCreditBucket
import com.amond.kmpbook.domain.model.fund.FixedIncomeDurationProvenance
import com.amond.kmpbook.domain.model.fund.FixedIncomeGeography
import com.amond.kmpbook.domain.model.fund.FixedIncomeProfileSupportLevel
import com.amond.kmpbook.domain.model.fund.FixedIncomeRateReset
import com.amond.kmpbook.domain.model.fund.FixedIncomeReferenceProfile
import com.amond.kmpbook.domain.model.fund.FixedIncomeTenorBand
import com.amond.kmpbook.domain.model.fund.FundOfFundsCategory
import com.amond.kmpbook.domain.model.fund.FundOfFundsCategoryReference
import com.amond.kmpbook.domain.model.fund.FundOfFundsConfidence
import com.amond.kmpbook.domain.model.fund.FundOfFundsMethodologyProfile
import com.amond.kmpbook.domain.model.fund.FundOfFundsRankedWeightCapTier
import com.amond.kmpbook.domain.model.fund.FundOfFundsRebalanceCalendar
import com.amond.kmpbook.domain.model.fund.FundOfFundsRuleProvenance
import com.amond.kmpbook.domain.model.fund.FundOfFundsSelectionModel
import com.amond.kmpbook.domain.model.fund.FundOfFundsUniverse
import com.amond.kmpbook.domain.model.fund.FundOfFundsWeightingModel
import com.amond.kmpbook.domain.model.fund.FundLegalStructure
import com.amond.kmpbook.domain.model.fund.FundProductProfile
import com.amond.kmpbook.domain.model.fund.FundReferenceUniverse
import com.amond.kmpbook.domain.model.fund.FundReferenceExposure
import com.amond.kmpbook.domain.model.fund.FundReplicationMode
import com.amond.kmpbook.domain.model.fund.FundReturnTransform
import com.amond.kmpbook.domain.model.fund.FundReturnVariant
import com.amond.kmpbook.domain.model.fund.FundSelectionModel
import com.amond.kmpbook.domain.model.fund.FundWeightingModel
import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector
import com.amond.kmpbook.domain.model.fund.MbsInterestOnlyModelParameterOrigin
import com.amond.kmpbook.domain.model.fund.MbsInterestOnlyModelParameters
import com.amond.kmpbook.domain.model.fund.MbsInterestOnlyPrepaymentModel
import com.amond.kmpbook.domain.model.fund.MbsInterestOnlySleeveTerms
import com.amond.kmpbook.domain.model.fund.MbsInterestOnlyTermsProvenance
import com.amond.kmpbook.domain.model.fundproduct.DailyResetCalendar
import com.amond.kmpbook.domain.model.fundproduct.DailyResetModelParameterOrigin
import com.amond.kmpbook.domain.model.fundproduct.DailyResetModelParameters
import com.amond.kmpbook.domain.model.fundproduct.DailyResetReference
import com.amond.kmpbook.domain.model.fundproduct.DailyResetReferenceKind
import com.amond.kmpbook.domain.model.fundproduct.DailyResetTerms
import com.amond.kmpbook.domain.model.fundproduct.DailyResetTermsProvenance
import com.amond.kmpbook.domain.model.fundproduct.DirectReferenceTerminationPolicy
import com.amond.kmpbook.domain.model.fundproduct.DirectReferenceTerminationRule
import com.amond.kmpbook.domain.model.fundproduct.DirectReferenceTerminationRuleProvenance
import com.amond.kmpbook.domain.model.fundproduct.BufferedPutSpreadStrategyTerms
import com.amond.kmpbook.domain.model.fundproduct.CashCollateralizedPutSpreadTerms
import com.amond.kmpbook.domain.model.fundproduct.CoveredCallStrategyTerms
import com.amond.kmpbook.domain.model.fundproduct.OptionIncomeStrategyTerms
import com.amond.kmpbook.domain.model.fundproduct.OptionPremiumModelParameterOrigin
import com.amond.kmpbook.domain.model.fundproduct.OptionPremiumModelParameters
import com.amond.kmpbook.domain.model.fundproduct.OptionRollCalendar
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyKind
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyTerms
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyTermsProvenance
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundDistributionPolicy
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundMarketModelParameters
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundTerms
import com.amond.kmpbook.domain.model.fundstructure.EtnAccelerationTerms
import com.amond.kmpbook.domain.model.fundstructure.EtnCallTerms
import com.amond.kmpbook.domain.model.fundstructure.EtnCouponKind
import com.amond.kmpbook.domain.model.fundstructure.EtnCouponRule
import com.amond.kmpbook.domain.model.fundstructure.EtnIssuerCreditModelParameters
import com.amond.kmpbook.domain.model.fundstructure.EtnProductTerms
import com.amond.kmpbook.domain.model.fundstructure.EtnSettlementValuationMethod
import com.amond.kmpbook.domain.model.fundstructure.EtnSettlementValuationRule
import com.amond.kmpbook.domain.model.fundstructure.FundStructureModelParameterOrigin
import com.amond.kmpbook.domain.model.fundstructure.FundStructureTermsProvenance
import com.amond.kmpbook.domain.model.instrument.CurrencyExposureLeg
import com.amond.kmpbook.domain.model.instrument.DistributionFrequency
import com.amond.kmpbook.domain.model.instrument.EtfAssetClass
import com.amond.kmpbook.domain.model.instrument.EtfExposureRegion
import com.amond.kmpbook.domain.model.instrument.EtfFxProfile
import com.amond.kmpbook.domain.model.instrument.EtfProfile
import com.amond.kmpbook.domain.model.instrument.EtfTaxCategory
import com.amond.kmpbook.domain.model.instrument.InstrumentBehaviorProfile
import com.amond.kmpbook.domain.model.instrument.InstrumentIdentityProfile
import com.amond.kmpbook.domain.model.instrument.InstrumentStrategy
import com.amond.kmpbook.domain.model.instrument.InstrumentType
import com.amond.kmpbook.domain.model.instrument.PrincipalRisk
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.IndustrySegment
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import com.amond.kmpbook.domain.model.market.Sector
import com.amond.kmpbook.domain.model.reference.CommodityAssetClass
import com.amond.kmpbook.domain.model.reference.CommodityReferenceTermsProvenance
import com.amond.kmpbook.domain.model.reference.CommoditySpotReferenceTerms
import com.amond.kmpbook.domain.model.reference.FuturesAllocationMode
import com.amond.kmpbook.domain.model.reference.FuturesPortfolioStyle
import com.amond.kmpbook.domain.model.reference.FuturesPriceReturnConvention
import com.amond.kmpbook.domain.model.reference.FuturesReferenceTerms
import com.amond.kmpbook.domain.model.reference.FuturesRollCalendar
import com.amond.kmpbook.domain.model.reference.FuturesSleeveTerms
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.math.BigDecimal
import java.net.URI
import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.datetime.LocalDate

/** Desktop/JVM 경계에서 종목팩 JSON을 도메인 객체로 명시적으로 변환한다. */
object DesktopInstrumentPackParser {
    const val MAX_PACK_BYTES: Int = 4 * 1024 * 1024

    fun parse(
        bytes: ByteArray,
        sourceId: String,
        maxInstruments: Int,
    ): InstrumentPack {
        require(bytes.isNotEmpty()) { "종목팩 JSON은 비어 있을 수 없습니다." }
        require(bytes.size <= MAX_PACK_BYTES) { "종목팩 JSON은 4 MiB 이하여야 합니다." }
        require(maxInstruments in 1..InstrumentPack.MAX_INSTRUMENTS) {
            "종목팩 최대 종목 수는 1~${InstrumentPack.MAX_INSTRUMENTS} 사이여야 합니다."
        }

        val json = decodeUtf8(bytes)
        val fingerprint = bytes.sha256Lowercase()
        return try {
            JsonReader(StringReader(json)).use { reader ->
                reader.strictness = Strictness.STRICT
                val document = StrictInstrumentPackReader(reader, maxInstruments).readDocument()
                InstrumentPack(
                    sourceId = sourceId,
                    fingerprint = fingerprint,
                    benchmarks = document.benchmarks,
                    definitions = document.definitions,
                )
            }
        } catch (error: Exception) {
            if (error is IllegalArgumentException && error.message?.startsWith(ERROR_PREFIX) == true) {
                throw error
            }
            val detail = error.message
                ?.take(MAX_ERROR_DETAIL_LENGTH)
                ?.takeIf(String::isNotBlank)
                ?: "JSON 형식 또는 종목 데이터가 올바르지 않습니다."
            throw IllegalArgumentException("$ERROR_PREFIX$detail", error)
        }
    }

    private fun decodeUtf8(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (error: CharacterCodingException) {
        throw IllegalArgumentException("${ERROR_PREFIX}올바른 UTF-8 문서가 아닙니다.", error)
    }

    private fun ByteArray.sha256Lowercase(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(this)
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val unsigned = byte.toInt() and 0xff
                append(HEX_DIGITS[unsigned ushr 4])
                append(HEX_DIGITS[unsigned and 0x0f])
            }
        }
    }

    private const val ERROR_PREFIX: String = "종목팩을 해석할 수 없습니다: "
    private const val MAX_ERROR_DETAIL_LENGTH: Int = 240
    private const val HEX_DIGITS: String = "0123456789abcdef"

    /**
     * 이 파서 전용 상태와 작은 wire 레코드는 중복 키·노드 수·깊이를 읽는 순간 검증해야 하므로
     * JSON 경계 구현과 함께 둔다. 도메인 모델로 직접 노출되지 않는다.
     */
    private class StrictInstrumentPackReader(
        private val reader: JsonReader,
        private val maxInstruments: Int,
    ) {
        private var depth: Int = 0
        private var nodeCount: Int = 0

        fun readDocument(): ParsedDocument {
            var schemaVersion: Int? = null
            var benchmarks: List<BenchmarkDefinition>? = null
            var definitions: List<StockDefinition>? = null
            readObject("루트", ROOT_FIELDS) { field ->
                when (field) {
                    "schemaVersion" -> schemaVersion = readExactInt("schemaVersion")
                    "benchmarks" -> benchmarks = readBenchmarks()
                    "instruments" -> definitions = readInstruments()
                }
            }
            if (schemaVersion != SCHEMA_VERSION) {
                fail("지원하지 않는 schemaVersion입니다: $schemaVersion")
            }
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                fail("루트 JSON 뒤에 추가 값이 있습니다.")
            }
            return ParsedDocument(
                benchmarks = benchmarks ?: fail("benchmarks 배열이 없습니다."),
                definitions = definitions ?: fail("instruments 배열이 없습니다."),
            )
        }

        private fun readBenchmarks(): List<BenchmarkDefinition> {
            beginArray("benchmarks")
            val benchmarks = ArrayList<BenchmarkDefinition>()
            val refs = HashSet<BenchmarkRef>()
            while (reader.hasNext()) {
                if (benchmarks.size >= InstrumentPack.MAX_BENCHMARKS) {
                    fail("benchmarks 배열은 최대 ${InstrumentPack.MAX_BENCHMARKS}개 항목만 허용합니다.")
                }
                val definition = readBenchmarkDefinition()
                if (!refs.add(definition.ref)) {
                    fail("benchmarks 배열에서 같은 (benchmarkId, version)을 재정의할 수 없습니다.")
                }
                benchmarks += definition
            }
            endArray()
            return benchmarks
        }

        private fun readBenchmarkDefinition(): BenchmarkDefinition {
            var benchmarkId: String? = null
            var version: Int? = null
            var displayName: String? = null
            var administrator: String? = null
            var officialSourceUrls: Set<String>? = null
            var baseCurrency: ReferenceCurrency? = null
            var engineKind: BenchmarkEngineKind? = null
            var supportLevel: BenchmarkSupportLevel? = null
            var componentBenchmarkRefs: Set<BenchmarkRef>? = null
            var equityMethodology: EquityMethodologyProfile? = null
            var equityReferenceProfile: EquityReferenceProfile? = null
            var fixedIncomeProfile: FixedIncomeReferenceProfile? = null
            var commoditySpotTerms: CommoditySpotReferenceTerms? = null
            var futuresReferenceTerms: FuturesReferenceTerms? = null
            var fundOfFundsMethodologyProfile: FundOfFundsMethodologyProfile? = null
            var compositeReferenceProfile: CompositeReferenceProfile? = null
            var alternativeRiskPremiaProfile: AlternativeRiskPremiaProfile? = null

            readObject("벤치마크", BENCHMARK_FIELDS) { field ->
                when (field) {
                    "benchmarkId" -> benchmarkId =
                        readString("benchmarkId", BenchmarkRef.MAX_ID_LENGTH, allowBlank = false)
                    "version" -> version = readExactIntInRange("version", 1, BenchmarkRef.MAX_VERSION)
                    "displayName" -> displayName = readString(
                        "displayName",
                        BenchmarkDefinition.MAX_DISPLAY_NAME_LENGTH,
                        allowBlank = false,
                    )
                    "administrator" -> administrator = readString(
                        "administrator",
                        BenchmarkDefinition.MAX_ADMINISTRATOR_LENGTH,
                        allowBlank = false,
                    )
                    "officialSourceUrls" -> officialSourceUrls = readSortedUniqueHttpsUrlSet(
                        "officialSourceUrls",
                        BenchmarkDefinition.MAX_OFFICIAL_SOURCE_URLS,
                    )
                    "baseCurrency" -> baseCurrency = readEnum<ReferenceCurrency>("baseCurrency")
                    "engineKind" -> engineKind = readEnum<BenchmarkEngineKind>("engineKind")
                    "supportLevel" -> supportLevel = readEnum<BenchmarkSupportLevel>("supportLevel")
                    "componentBenchmarkRefs" -> componentBenchmarkRefs = readBenchmarkRefs()
                    "equityMethodology" -> equityMethodology =
                        readNullableObject("equityMethodology", ::readEquityMethodology)
                    "equityReferenceProfile" -> equityReferenceProfile =
                        readNullableObject("equityReferenceProfile", ::readEquityReferenceProfile)
                    "fixedIncomeProfile" -> fixedIncomeProfile =
                        readNullableObject("fixedIncomeProfile", ::readFixedIncomeReferenceProfile)
                    "commoditySpotTerms" -> commoditySpotTerms =
                        readNullableObject("commoditySpotTerms", ::readCommoditySpotReferenceTerms)
                    "futuresReferenceTerms" -> futuresReferenceTerms =
                        readNullableObject("futuresReferenceTerms", ::readFuturesReferenceTerms)
                    "fundOfFundsMethodologyProfile" -> fundOfFundsMethodologyProfile =
                        readNullableObject("fundOfFundsMethodologyProfile", ::readFundOfFundsMethodologyProfile)
                    "compositeReferenceProfile" -> compositeReferenceProfile =
                        readNullableObject("compositeReferenceProfile", ::readCompositeReferenceProfile)
                    "alternativeRiskPremiaProfile" -> alternativeRiskPremiaProfile =
                        readNullableObject("alternativeRiskPremiaProfile", ::readAlternativeRiskPremiaProfile)
                }
            }

            return BenchmarkDefinition(
                ref = BenchmarkRef(
                    benchmarkId = benchmarkId ?: missing("benchmarkId"),
                    version = version ?: missing("version"),
                ),
                displayName = displayName ?: missing("displayName"),
                administrator = administrator ?: missing("administrator"),
                officialSourceUrls = officialSourceUrls ?: missing("officialSourceUrls"),
                baseCurrency = baseCurrency ?: missing("baseCurrency"),
                engineKind = engineKind ?: missing("engineKind"),
                supportLevel = supportLevel ?: missing("supportLevel"),
                componentBenchmarkRefs = componentBenchmarkRefs ?: missing("componentBenchmarkRefs"),
                equityMethodology = equityMethodology,
                equityReferenceProfile = equityReferenceProfile,
                fixedIncomeProfile = fixedIncomeProfile,
                commoditySpotTerms = commoditySpotTerms,
                futuresReferenceTerms = futuresReferenceTerms,
                fundOfFundsMethodologyProfile = fundOfFundsMethodologyProfile,
                compositeReferenceProfile = compositeReferenceProfile,
                alternativeRiskPremiaProfile = alternativeRiskPremiaProfile,
            )
        }

        private fun readBenchmarkRefs(): Set<BenchmarkRef> {
            beginArray("componentBenchmarkRefs")
            val refs = linkedSetOf<BenchmarkRef>()
            var previous: BenchmarkRef? = null
            while (reader.hasNext()) {
                if (refs.size >= BenchmarkDefinition.MAX_COMPONENT_BENCHMARKS) {
                    fail("componentBenchmarkRefs 배열 항목이 너무 많습니다.")
                }
                val ref = readBenchmarkRef("componentBenchmarkRefs")
                if (!refs.add(ref)) fail("componentBenchmarkRefs 배열에 중복 값이 있습니다.")
                if (previous != null && previous >= ref) {
                    fail("componentBenchmarkRefs 배열은 benchmarkId와 version 순으로 정렬해야 합니다.")
                }
                previous = ref
            }
            endArray()
            return refs.toSet()
        }

        private fun readBenchmarkRef(label: String): BenchmarkRef {
            var benchmarkId: String? = null
            var version: Int? = null
            readObject(label, BENCHMARK_REF_FIELDS) { field ->
                when (field) {
                    "benchmarkId" -> benchmarkId =
                        readString("benchmarkId", BenchmarkRef.MAX_ID_LENGTH, allowBlank = false)
                    "version" -> version = readExactIntInRange("version", 1, BenchmarkRef.MAX_VERSION)
                }
            }
            return BenchmarkRef(
                benchmarkId = benchmarkId ?: missing("benchmarkId"),
                version = version ?: missing("version"),
            )
        }

        private fun readEquityMethodology(): EquityMethodologyProfile {
            var effectiveFrom: LocalDate? = null
            var referenceUniverse: FundReferenceUniverse? = null
            var selectionModel: FundSelectionModel? = null
            var weightingModel: FundWeightingModel? = null
            var targetConstituentCount: Int? = null
            var minDividendPaymentYears: Int? = null
            var minFloatMarketCap: Double? = null
            var minAverageDailyValueTraded: Double? = null
            var eligibleYieldFraction: Double? = null
            var incumbentRankBuffer: Int? = null
            var individualWeightCap: Double? = null
            var sectorWeightCap: Double? = null
            var annualReconstitutionMonth: Int? = null
            var rebalanceMonths: Set<Int>? = null
            var dailyWeightThreshold: Double? = null
            var dailyAggregateWeightLimit: Double? = null

            readObject("equityMethodology", EQUITY_METHODOLOGY_FIELDS) { field ->
                when (field) {
                    "effectiveFrom" -> effectiveFrom = readLocalDate("effectiveFrom")
                    "referenceUniverse" -> referenceUniverse = readEnum<FundReferenceUniverse>("referenceUniverse")
                    "selectionModel" -> selectionModel = readEnum<FundSelectionModel>("selectionModel")
                    "weightingModel" -> weightingModel = readEnum<FundWeightingModel>("weightingModel")
                    "targetConstituentCount" -> targetConstituentCount = readExactIntInRange(
                        "targetConstituentCount",
                        1,
                        EquityMethodologyProfile.MAX_CONSTITUENTS,
                    )
                    "minDividendPaymentYears" -> minDividendPaymentYears = readExactIntInRange(
                        "minDividendPaymentYears",
                        0,
                        MAX_DIVIDEND_PAYMENT_YEARS,
                    )
                    "minFloatMarketCap" -> minFloatMarketCap = readFiniteDoubleInRange(
                        "minFloatMarketCap",
                        0.0,
                        MAX_METHODOLOGY_MARKET_CAP,
                    )
                    "minAverageDailyValueTraded" -> minAverageDailyValueTraded = readFiniteDoubleInRange(
                        "minAverageDailyValueTraded",
                        0.0,
                        MAX_AVERAGE_DAILY_VALUE_TRADED,
                    )
                    "eligibleYieldFraction" -> eligibleYieldFraction =
                        readFiniteDoubleInRange("eligibleYieldFraction", MIN_POSITIVE_FRACTION, 1.0)
                    "incumbentRankBuffer" -> incumbentRankBuffer = readExactIntInRange(
                        "incumbentRankBuffer",
                        0,
                        EquityMethodologyProfile.MAX_RANK_BUFFER,
                    )
                    "individualWeightCap" -> individualWeightCap =
                        readFiniteDoubleInRange("individualWeightCap", MIN_POSITIVE_FRACTION, 1.0)
                    "sectorWeightCap" -> sectorWeightCap =
                        readFiniteDoubleInRange("sectorWeightCap", 0.0, 1.0)
                    "annualReconstitutionMonth" -> annualReconstitutionMonth =
                        readExactIntInRange("annualReconstitutionMonth", 1, MONTHS_PER_YEAR)
                    "rebalanceMonths" -> rebalanceMonths = readSortedUniqueIntSet(
                        label = "rebalanceMonths",
                        maxItems = MONTHS_PER_YEAR,
                        minValue = 1,
                        maxValue = MONTHS_PER_YEAR,
                    )
                    "dailyWeightThreshold" -> dailyWeightThreshold =
                        readFiniteDoubleInRange("dailyWeightThreshold", 0.0, 1.0)
                    "dailyAggregateWeightLimit" -> dailyAggregateWeightLimit =
                        readFiniteDoubleInRange("dailyAggregateWeightLimit", 0.0, 1.0)
                }
            }
            return EquityMethodologyProfile(
                effectiveFrom = effectiveFrom ?: missing("effectiveFrom"),
                referenceUniverse = referenceUniverse ?: missing("referenceUniverse"),
                selectionModel = selectionModel ?: missing("selectionModel"),
                weightingModel = weightingModel ?: missing("weightingModel"),
                targetConstituentCount = targetConstituentCount ?: missing("targetConstituentCount"),
                minDividendPaymentYears = minDividendPaymentYears ?: missing("minDividendPaymentYears"),
                minFloatMarketCap = minFloatMarketCap ?: missing("minFloatMarketCap"),
                minAverageDailyValueTraded = minAverageDailyValueTraded
                    ?: missing("minAverageDailyValueTraded"),
                eligibleYieldFraction = eligibleYieldFraction ?: missing("eligibleYieldFraction"),
                incumbentRankBuffer = incumbentRankBuffer ?: missing("incumbentRankBuffer"),
                individualWeightCap = individualWeightCap ?: missing("individualWeightCap"),
                sectorWeightCap = sectorWeightCap ?: missing("sectorWeightCap"),
                annualReconstitutionMonth = annualReconstitutionMonth
                    ?: missing("annualReconstitutionMonth"),
                rebalanceMonths = rebalanceMonths ?: missing("rebalanceMonths"),
                dailyWeightThreshold = dailyWeightThreshold ?: missing("dailyWeightThreshold"),
                dailyAggregateWeightLimit = dailyAggregateWeightLimit
                    ?: missing("dailyAggregateWeightLimit"),
            )
        }

        private fun readEquityReferenceProfile(): EquityReferenceProfile {
            var region: EquityReferenceRegion? = null
            var countryCodes: Set<String>? = null
            var eligibleUniverse: EquityEligibleUniverse? = null
            var sectorPolicy: EquitySectorPolicy? = null
            var includedSectors: Set<MethodologyEquitySector>? = null
            var themeId: String? = null
            var themeIdSeen = false
            var stylePolicies: Set<EquityStylePolicy>? = null
            var weightingModel: EquityReferenceWeightingModel? = null
            var targetConstituentCount: Int? = null
            var targetConstituentCountSeen = false
            var individualWeightCap: Double? = null
            var individualWeightCapSeen = false
            var sectorWeightCap: Double? = null
            var sectorWeightCapSeen = false
            var selectionCalendar: EquityRebalanceCalendar? = null
            var selectionMonths: Set<Int>? = null
            var reweightCalendar: EquityRebalanceCalendar? = null
            var reweightMonths: Set<Int>? = null
            var supportLevel: BenchmarkSupportLevel? = null
            var provenance: EquityReferenceProvenance? = null
            var confidence: EquityReferenceConfidence? = null
            var officialSourceUrls: Set<String>? = null
            var assumptionId: String? = null
            var assumptionIdSeen = false

            readObject("equityReferenceProfile", EQUITY_REFERENCE_PROFILE_FIELDS) { field ->
                when (field) {
                    "region" -> region = readEnum<EquityReferenceRegion>("region")
                    "countryCodes" -> countryCodes = readSortedUniqueStringSet(
                        label = "countryCodes",
                        maxItems = EquityReferenceProfile.MAX_COUNTRY_CODES,
                        maxStringLength = EquityReferenceProfile.MAX_COUNTRY_CODE_LENGTH,
                    )
                    "eligibleUniverse" -> eligibleUniverse =
                        readEnum<EquityEligibleUniverse>("eligibleUniverse")
                    "sectorPolicy" -> sectorPolicy = readEnum<EquitySectorPolicy>("sectorPolicy")
                    "includedSectors" -> includedSectors =
                        readSortedUniqueEnumSet<MethodologyEquitySector>(
                            label = "includedSectors",
                            maxItems = MethodologyEquitySector.entries.size,
                            allowEmpty = true,
                        )
                    "themeId" -> {
                        themeIdSeen = true
                        themeId = readNullableString(
                            "themeId",
                            EquityReferenceProfile.MAX_THEME_ID_LENGTH,
                            allowBlank = false,
                        )
                    }
                    "stylePolicies" -> stylePolicies = readSortedUniqueEnumSet<EquityStylePolicy>(
                        label = "stylePolicies",
                        maxItems = EquityReferenceProfile.MAX_STYLE_POLICIES,
                    )
                    "weightingModel" -> weightingModel =
                        readEnum<EquityReferenceWeightingModel>("weightingModel")
                    "targetConstituentCount" -> {
                        targetConstituentCountSeen = true
                        targetConstituentCount = readNullableExactIntInRange(
                            "targetConstituentCount",
                            1,
                            EquityReferenceProfile.MAX_CONSTITUENTS,
                        )
                    }
                    "individualWeightCap" -> {
                        individualWeightCapSeen = true
                        individualWeightCap = readNullableFiniteDoubleInRange(
                            "individualWeightCap",
                            MIN_POSITIVE_FRACTION,
                            1.0,
                        )
                    }
                    "sectorWeightCap" -> {
                        sectorWeightCapSeen = true
                        sectorWeightCap = readNullableFiniteDoubleInRange(
                            "sectorWeightCap",
                            MIN_POSITIVE_FRACTION,
                            1.0,
                        )
                    }
                    "selectionCalendar" -> selectionCalendar =
                        readEnum<EquityRebalanceCalendar>("selectionCalendar")
                    "selectionMonths" -> selectionMonths = readSortedUniqueIntSet(
                        label = "selectionMonths",
                        maxItems = MONTHS_PER_YEAR,
                        minValue = 1,
                        maxValue = MONTHS_PER_YEAR,
                        allowEmpty = true,
                    )
                    "reweightCalendar" -> reweightCalendar =
                        readEnum<EquityRebalanceCalendar>("reweightCalendar")
                    "reweightMonths" -> reweightMonths = readSortedUniqueIntSet(
                        label = "reweightMonths",
                        maxItems = MONTHS_PER_YEAR,
                        minValue = 1,
                        maxValue = MONTHS_PER_YEAR,
                        allowEmpty = true,
                    )
                    "supportLevel" -> supportLevel = readEnum<BenchmarkSupportLevel>("supportLevel")
                    "provenance" -> provenance =
                        readEnum<EquityReferenceProvenance>("provenance")
                    "confidence" -> confidence = readEnum<EquityReferenceConfidence>("confidence")
                    "officialSourceUrls" -> officialSourceUrls = readSortedUniqueHttpsUrlSet(
                        "officialSourceUrls",
                        EquityReferenceProfile.MAX_OFFICIAL_SOURCE_URLS,
                    )
                    "assumptionId" -> {
                        assumptionIdSeen = true
                        assumptionId = readNullableString(
                            "assumptionId",
                            EquityReferenceProfile.MAX_ASSUMPTION_ID_LENGTH,
                            allowBlank = false,
                        )
                    }
                }
            }
            if (!targetConstituentCountSeen) missing("targetConstituentCount")
            if (!individualWeightCapSeen) missing("individualWeightCap")
            if (!sectorWeightCapSeen) missing("sectorWeightCap")
            if (!themeIdSeen) missing("themeId")
            if (!assumptionIdSeen) missing("assumptionId")
            return EquityReferenceProfile(
                region = region ?: missing("region"),
                countryCodes = countryCodes ?: missing("countryCodes"),
                eligibleUniverse = eligibleUniverse ?: missing("eligibleUniverse"),
                sectorPolicy = sectorPolicy ?: missing("sectorPolicy"),
                includedSectors = includedSectors ?: missing("includedSectors"),
                themeId = themeId,
                stylePolicies = stylePolicies ?: missing("stylePolicies"),
                weightingModel = weightingModel ?: missing("weightingModel"),
                targetConstituentCount = targetConstituentCount,
                individualWeightCap = individualWeightCap,
                sectorWeightCap = sectorWeightCap,
                selectionCalendar = selectionCalendar ?: missing("selectionCalendar"),
                selectionMonths = selectionMonths ?: missing("selectionMonths"),
                reweightCalendar = reweightCalendar ?: missing("reweightCalendar"),
                reweightMonths = reweightMonths ?: missing("reweightMonths"),
                supportLevel = supportLevel ?: missing("supportLevel"),
                provenance = provenance ?: missing("provenance"),
                confidence = confidence ?: missing("confidence"),
                officialSourceUrls = officialSourceUrls ?: missing("officialSourceUrls"),
                assumptionId = assumptionId,
            )
        }

        private fun readFixedIncomeReferenceProfile(): FixedIncomeReferenceProfile {
            var geography: FixedIncomeGeography? = null
            var currencies: Set<ReferenceCurrency>? = null
            var assetType: FixedIncomeAssetType? = null
            var effectiveDurationYears: Double? = null
            var tenorBand: FixedIncomeTenorBand? = null
            var creditQuality: FixedIncomeCreditBucket? = null
            var rateReset: FixedIncomeRateReset? = null
            var realRateLinked: Boolean? = null
            var supportLevel: FixedIncomeProfileSupportLevel? = null
            var durationProvenance: FixedIncomeDurationProvenance? = null
            var officialSourceUrls: Set<String>? = null

            readObject("fixedIncomeProfile", FIXED_INCOME_PROFILE_FIELDS) { field ->
                when (field) {
                    "geography" -> geography = readEnum<FixedIncomeGeography>("geography")
                    "currencies" -> currencies = readSortedUniqueEnumSet<ReferenceCurrency>(
                        label = "currencies",
                        maxItems = FixedIncomeReferenceProfile.MAX_CURRENCIES,
                    )
                    "assetType" -> assetType = readEnum<FixedIncomeAssetType>("assetType")
                    "effectiveDurationYears" -> effectiveDurationYears = readFiniteDoubleInRange(
                        "effectiveDurationYears",
                        0.0,
                        FixedIncomeReferenceProfile.MAX_DURATION_YEARS,
                    )
                    "tenorBand" -> tenorBand = readEnum<FixedIncomeTenorBand>("tenorBand")
                    "creditQuality" -> creditQuality = readEnum<FixedIncomeCreditBucket>("creditQuality")
                    "rateReset" -> rateReset = readEnum<FixedIncomeRateReset>("rateReset")
                    "realRateLinked" -> realRateLinked = readBoolean("realRateLinked")
                    "supportLevel" -> supportLevel =
                        readEnum<FixedIncomeProfileSupportLevel>("supportLevel")
                    "durationProvenance" -> durationProvenance =
                        readEnum<FixedIncomeDurationProvenance>("durationProvenance")
                    "officialSourceUrls" -> officialSourceUrls = readSortedUniqueHttpsUrlSet(
                        "officialSourceUrls",
                        FixedIncomeReferenceProfile.MAX_OFFICIAL_SOURCE_URLS,
                    )
                }
            }
            return FixedIncomeReferenceProfile(
                geography = geography ?: missing("geography"),
                currencies = currencies ?: missing("currencies"),
                assetType = assetType ?: missing("assetType"),
                effectiveDurationYears = effectiveDurationYears ?: missing("effectiveDurationYears"),
                tenorBand = tenorBand ?: missing("tenorBand"),
                creditQuality = creditQuality ?: missing("creditQuality"),
                rateReset = rateReset ?: missing("rateReset"),
                realRateLinked = realRateLinked ?: missing("realRateLinked"),
                supportLevel = supportLevel ?: missing("supportLevel"),
                durationProvenance = durationProvenance ?: missing("durationProvenance"),
                officialSourceUrls = officialSourceUrls ?: missing("officialSourceUrls"),
            )
        }

        private fun readFundOfFundsMethodologyProfile(): FundOfFundsMethodologyProfile {
            var universe: FundOfFundsUniverse? = null
            var selectionModel: FundOfFundsSelectionModel? = null
            var weightingModel: FundOfFundsWeightingModel? = null
            var targetFundCount: Int? = null
            var candidateUniverseSize: Int? = null
            var eligibleCategories: Set<FundOfFundsCategory>? = null
            var categoryReferences: List<FundOfFundsCategoryReference>? = null
            var minimumDistributionYield: Double? = null
            var maximumAbsoluteDiscount: Double? = null
            var minimumLiquidityScore: Double? = null
            var individualWeightCap: Double? = null
            var categoryWeightCap: Double? = null
            var rankedWeightCapTiers: List<FundOfFundsRankedWeightCapTier>? = null
            var selectionCalendar: FundOfFundsRebalanceCalendar? = null
            var selectionMonths: Set<Int>? = null
            var reweightCalendar: FundOfFundsRebalanceCalendar? = null
            var reweightMonths: Set<Int>? = null
            var supportLevel: BenchmarkSupportLevel? = null
            var provenance: FundOfFundsRuleProvenance? = null
            var confidence: FundOfFundsConfidence? = null
            var officialSourceUrls: Set<String>? = null
            var assumptionId: String? = null

            readObject("fundOfFundsMethodologyProfile", FUND_OF_FUNDS_METHODOLOGY_PROFILE_FIELDS) { field ->
                when (field) {
                    "universe" -> universe = readEnum<FundOfFundsUniverse>("universe")
                    "selectionModel" -> selectionModel =
                        readEnum<FundOfFundsSelectionModel>("selectionModel")
                    "weightingModel" -> weightingModel =
                        readEnum<FundOfFundsWeightingModel>("weightingModel")
                    "targetFundCount" -> targetFundCount = readExactIntInRange(
                        "targetFundCount",
                        1,
                        FundOfFundsMethodologyProfile.MAX_TARGET_FUNDS,
                    )
                    "candidateUniverseSize" -> candidateUniverseSize = readExactIntInRange(
                        "candidateUniverseSize",
                        1,
                        FundOfFundsMethodologyProfile.MAX_CANDIDATE_FUNDS,
                    )
                    "eligibleCategories" -> eligibleCategories =
                        readSortedUniqueEnumSet<FundOfFundsCategory>(
                            label = "eligibleCategories",
                            maxItems = FundOfFundsCategory.entries.size,
                        )
                    "categoryReferences" -> categoryReferences = readFundOfFundsCategoryReferences()
                    "minimumDistributionYield" -> minimumDistributionYield = readFiniteDoubleInRange(
                        "minimumDistributionYield",
                        0.0,
                        1.0,
                    )
                    "maximumAbsoluteDiscount" -> maximumAbsoluteDiscount = readFiniteDoubleInRange(
                        "maximumAbsoluteDiscount",
                        0.0,
                        MAX_FUND_OF_FUNDS_DISCOUNT,
                    )
                    "minimumLiquidityScore" -> minimumLiquidityScore = readFiniteDoubleInRange(
                        "minimumLiquidityScore",
                        0.0,
                        1.0,
                    )
                    "individualWeightCap" -> individualWeightCap = readFiniteDoubleInRange(
                        "individualWeightCap",
                        MIN_POSITIVE_FRACTION,
                        1.0,
                    )
                    "categoryWeightCap" -> categoryWeightCap = readFiniteDoubleInRange(
                        "categoryWeightCap",
                        MIN_POSITIVE_FRACTION,
                        1.0,
                    )
                    "rankedWeightCapTiers" -> rankedWeightCapTiers =
                        readFundOfFundsRankedWeightCapTiers()
                    "selectionCalendar" -> selectionCalendar =
                        readEnum<FundOfFundsRebalanceCalendar>("selectionCalendar")
                    "selectionMonths" -> selectionMonths = readSortedUniqueIntSet(
                        label = "selectionMonths",
                        maxItems = MONTHS_PER_YEAR,
                        minValue = 1,
                        maxValue = MONTHS_PER_YEAR,
                    )
                    "reweightCalendar" -> reweightCalendar =
                        readEnum<FundOfFundsRebalanceCalendar>("reweightCalendar")
                    "reweightMonths" -> reweightMonths = readSortedUniqueIntSet(
                        label = "reweightMonths",
                        maxItems = MONTHS_PER_YEAR,
                        minValue = 1,
                        maxValue = MONTHS_PER_YEAR,
                    )
                    "supportLevel" -> supportLevel = readEnum<BenchmarkSupportLevel>("supportLevel")
                    "provenance" -> provenance = readEnum<FundOfFundsRuleProvenance>("provenance")
                    "confidence" -> confidence = readEnum<FundOfFundsConfidence>("confidence")
                    "officialSourceUrls" -> officialSourceUrls = readSortedUniqueHttpsUrlSet(
                        "officialSourceUrls",
                        FundOfFundsMethodologyProfile.MAX_OFFICIAL_SOURCE_URLS,
                    )
                    "assumptionId" -> assumptionId = readNullableString(
                        "assumptionId",
                        MAX_FUND_OF_FUNDS_ASSUMPTION_ID_LENGTH,
                        allowBlank = false,
                    )
                }
            }
            return FundOfFundsMethodologyProfile(
                universe = universe ?: missing("universe"),
                selectionModel = selectionModel ?: missing("selectionModel"),
                weightingModel = weightingModel ?: missing("weightingModel"),
                targetFundCount = targetFundCount ?: missing("targetFundCount"),
                candidateUniverseSize = candidateUniverseSize ?: missing("candidateUniverseSize"),
                eligibleCategories = eligibleCategories ?: missing("eligibleCategories"),
                categoryReferences = categoryReferences ?: missing("categoryReferences"),
                minimumDistributionYield = minimumDistributionYield ?: missing("minimumDistributionYield"),
                maximumAbsoluteDiscount = maximumAbsoluteDiscount ?: missing("maximumAbsoluteDiscount"),
                minimumLiquidityScore = minimumLiquidityScore ?: missing("minimumLiquidityScore"),
                individualWeightCap = individualWeightCap ?: missing("individualWeightCap"),
                categoryWeightCap = categoryWeightCap ?: missing("categoryWeightCap"),
                rankedWeightCapTiers = rankedWeightCapTiers ?: missing("rankedWeightCapTiers"),
                selectionCalendar = selectionCalendar ?: missing("selectionCalendar"),
                selectionMonths = selectionMonths ?: missing("selectionMonths"),
                reweightCalendar = reweightCalendar ?: missing("reweightCalendar"),
                reweightMonths = reweightMonths ?: missing("reweightMonths"),
                supportLevel = supportLevel ?: missing("supportLevel"),
                provenance = provenance ?: missing("provenance"),
                confidence = confidence ?: missing("confidence"),
                officialSourceUrls = officialSourceUrls ?: missing("officialSourceUrls"),
                assumptionId = assumptionId,
            )
        }

        private fun readFundOfFundsCategoryReferences(): List<FundOfFundsCategoryReference> {
            beginArray("categoryReferences")
            val references = mutableListOf<FundOfFundsCategoryReference>()
            var previousCategoryOrdinal = -1
            while (reader.hasNext()) {
                if (references.size >= FundOfFundsCategory.entries.size) {
                    fail("categoryReferences 배열 항목이 너무 많습니다.")
                }
                var category: FundOfFundsCategory? = null
                var benchmarkRef: BenchmarkRef? = null
                readObject("categoryReference", FUND_OF_FUNDS_CATEGORY_REFERENCE_FIELDS) { field ->
                    when (field) {
                        "category" -> category = readEnum<FundOfFundsCategory>("category")
                        "benchmarkRef" -> benchmarkRef = readBenchmarkRef("benchmarkRef")
                    }
                }
                val resolvedCategory = category ?: missing("category")
                if (resolvedCategory.ordinal <= previousCategoryOrdinal) {
                    fail("categoryReferences 배열은 category enum 선언 순서로 정렬해야 합니다.")
                }
                previousCategoryOrdinal = resolvedCategory.ordinal
                references += FundOfFundsCategoryReference(
                    category = resolvedCategory,
                    benchmarkRef = benchmarkRef ?: missing("benchmarkRef"),
                )
            }
            endArray()
            if (references.isEmpty()) fail("categoryReferences 배열은 비어 있을 수 없습니다.")
            return references.toList()
        }

        private fun readFundOfFundsRankedWeightCapTiers(): List<FundOfFundsRankedWeightCapTier> {
            beginArray("rankedWeightCapTiers")
            val tiers = mutableListOf<FundOfFundsRankedWeightCapTier>()
            var previousLastRank = 0
            while (reader.hasNext()) {
                if (tiers.size >= FundOfFundsMethodologyProfile.MAX_TARGET_FUNDS) {
                    fail("rankedWeightCapTiers 배열 항목이 너무 많습니다.")
                }
                var lastRankInclusive: Int? = null
                var maximumWeight: Double? = null
                readObject("rankedWeightCapTier", FUND_OF_FUNDS_RANKED_WEIGHT_CAP_TIER_FIELDS) { field ->
                    when (field) {
                        "lastRankInclusive" -> lastRankInclusive = readExactIntInRange(
                            "lastRankInclusive",
                            1,
                            FundOfFundsMethodologyProfile.MAX_TARGET_FUNDS,
                        )
                        "maximumWeight" -> maximumWeight = readFiniteDoubleInRange(
                            "maximumWeight",
                            MIN_POSITIVE_FRACTION,
                            1.0,
                        )
                    }
                }
                val resolvedLastRank = lastRankInclusive ?: missing("lastRankInclusive")
                if (resolvedLastRank <= previousLastRank) {
                    fail("rankedWeightCapTiers 배열은 lastRankInclusive 오름차순이어야 합니다.")
                }
                previousLastRank = resolvedLastRank
                tiers += FundOfFundsRankedWeightCapTier(
                    lastRankInclusive = resolvedLastRank,
                    maximumWeight = maximumWeight ?: missing("maximumWeight"),
                )
            }
            endArray()
            return tiers.toList()
        }

        private fun readCompositeReferenceProfile(): CompositeReferenceProfile {
            var sleeves: List<CompositeReferenceSleeve>? = null
            var allocationModel: CompositeAllocationModel? = null
            var grossExposureConstraint: CompositeExposureConstraint? = null
            var netExposureConstraint: CompositeExposureConstraint? = null
            var annualFinancingSpread: Double? = null
            var annualFinancingSpreadOrigin: CompositeParameterOrigin? = null
            var targetVolatilityAnnual: Double? = null
            var targetVolatilityOrigin: CompositeParameterOrigin? = null
            var riskLookbackTradingDays: Int? = null
            var riskLookbackOrigin: CompositeParameterOrigin? = null
            var durationConstraint: CompositeDurationConstraint? = null
            var driftThreshold: Double? = null
            var driftThresholdOrigin: CompositeParameterOrigin? = null
            var selectionSchedule: CompositeRebalanceSchedule? = null
            var reweightSchedule: CompositeRebalanceSchedule? = null
            var supportLevel: BenchmarkSupportLevel? = null
            var provenance: CompositeRuleProvenance? = null
            var confidence: CompositeConfidence? = null
            var officialSourceUrls: Set<String>? = null
            var assumptionId: String? = null

            readObject("compositeReferenceProfile", COMPOSITE_REFERENCE_PROFILE_FIELDS) { field ->
                when (field) {
                    "sleeves" -> sleeves = readCompositeReferenceSleeves()
                    "allocationModel" -> allocationModel = readEnum<CompositeAllocationModel>(field)
                    "grossExposureConstraint" -> grossExposureConstraint = readCompositeExposureConstraint(field)
                    "netExposureConstraint" -> netExposureConstraint = readCompositeExposureConstraint(field)
                    "annualFinancingSpread" -> annualFinancingSpread =
                        readNullableFiniteDoubleInRange(field, 0.0, CompositeReferenceProfile.MAX_ANNUAL_RATE)
                    "annualFinancingSpreadOrigin" -> annualFinancingSpreadOrigin =
                        readNullableEnum<CompositeParameterOrigin>(field)
                    "targetVolatilityAnnual" -> targetVolatilityAnnual =
                        readNullableFiniteDoubleInRange(
                            field,
                            MIN_POSITIVE_FRACTION,
                            CompositeReferenceProfile.MAX_TARGET_VOLATILITY,
                        )
                    "targetVolatilityOrigin" -> targetVolatilityOrigin =
                        readNullableEnum<CompositeParameterOrigin>(field)
                    "riskLookbackTradingDays" -> riskLookbackTradingDays = readNullableExactIntInRange(
                        field,
                        CompositeReferenceProfile.MIN_LOOKBACK_DAYS,
                        CompositeReferenceProfile.MAX_LOOKBACK_DAYS,
                    )
                    "riskLookbackOrigin" -> riskLookbackOrigin =
                        readNullableEnum<CompositeParameterOrigin>(field)
                    "durationConstraint" -> durationConstraint =
                        readNullableObject(field) { readCompositeDurationConstraint(field) }
                    "driftThreshold" -> driftThreshold =
                        readNullableFiniteDoubleInRange(field, MIN_POSITIVE_FRACTION, 1.0)
                    "driftThresholdOrigin" -> driftThresholdOrigin =
                        readNullableEnum<CompositeParameterOrigin>(field)
                    "selectionSchedule" -> selectionSchedule = readCompositeRebalanceSchedule(field)
                    "reweightSchedule" -> reweightSchedule = readCompositeRebalanceSchedule(field)
                    "supportLevel" -> supportLevel = readEnum<BenchmarkSupportLevel>(field)
                    "provenance" -> provenance = readEnum<CompositeRuleProvenance>(field)
                    "confidence" -> confidence = readEnum<CompositeConfidence>(field)
                    "officialSourceUrls" -> officialSourceUrls = readSortedUniqueHttpsUrlSet(
                        field,
                        CompositeReferenceProfile.MAX_OFFICIAL_SOURCE_URLS,
                    )
                    "assumptionId" -> assumptionId = readNullableString(
                        field,
                        MAX_COMPOSITE_ASSUMPTION_ID_LENGTH,
                        allowBlank = false,
                    )
                }
            }
            return CompositeReferenceProfile(
                sleeves = sleeves ?: missing("sleeves"),
                allocationModel = allocationModel ?: missing("allocationModel"),
                grossExposureConstraint = grossExposureConstraint ?: missing("grossExposureConstraint"),
                netExposureConstraint = netExposureConstraint ?: missing("netExposureConstraint"),
                annualFinancingSpread = annualFinancingSpread,
                annualFinancingSpreadOrigin = annualFinancingSpreadOrigin,
                targetVolatilityAnnual = targetVolatilityAnnual,
                targetVolatilityOrigin = targetVolatilityOrigin,
                riskLookbackTradingDays = riskLookbackTradingDays,
                riskLookbackOrigin = riskLookbackOrigin,
                durationConstraint = durationConstraint,
                driftThreshold = driftThreshold,
                driftThresholdOrigin = driftThresholdOrigin,
                selectionSchedule = selectionSchedule ?: missing("selectionSchedule"),
                reweightSchedule = reweightSchedule ?: missing("reweightSchedule"),
                supportLevel = supportLevel ?: missing("supportLevel"),
                provenance = provenance ?: missing("provenance"),
                confidence = confidence ?: missing("confidence"),
                officialSourceUrls = officialSourceUrls ?: missing("officialSourceUrls"),
                assumptionId = assumptionId,
            )
        }

        private fun readCompositeReferenceSleeves(): List<CompositeReferenceSleeve> {
            beginArray("sleeves")
            val sleeves = mutableListOf<CompositeReferenceSleeve>()
            var previousSleeveId: String? = null
            while (reader.hasNext()) {
                if (sleeves.size >= CompositeReferenceProfile.MAX_SLEEVES) {
                    fail("sleeves 배열 항목이 너무 많습니다.")
                }
                val sleeve = readCompositeReferenceSleeve()
                if (previousSleeveId != null && sleeve.sleeveId <= previousSleeveId) {
                    fail("sleeves 배열은 sleeveId 오름차순이어야 합니다.")
                }
                previousSleeveId = sleeve.sleeveId
                sleeves += sleeve
            }
            endArray()
            if (sleeves.isEmpty()) fail("sleeves 배열은 비어 있을 수 없습니다.")
            return sleeves.toList()
        }

        private fun readCompositeReferenceSleeve(): CompositeReferenceSleeve {
            var sleeveId: String? = null
            var source: CompositeReferenceSource? = null
            var direction: CompositeSleeveDirection? = null
            var role: CompositeSleeveRole? = null
            var targetWeight: Double? = null
            var minimumWeight: Double? = null
            var maximumWeight: Double? = null
            var targetWeightOrigin: CompositeParameterOrigin? = null
            var weightBandOrigin: CompositeParameterOrigin? = null
            var riskBudget: Double? = null
            var riskBudgetOrigin: CompositeParameterOrigin? = null
            var annualBorrowSpread: Double? = null
            var annualBorrowSpreadOrigin: CompositeParameterOrigin? = null
            var hedgeRatio: Double? = null
            var hedgeRatioOrigin: CompositeParameterOrigin? = null
            var mbsInterestOnlyTerms: MbsInterestOnlySleeveTerms? = null
            readObject("composite sleeve", COMPOSITE_REFERENCE_SLEEVE_FIELDS) { field ->
                when (field) {
                    "sleeveId" -> sleeveId = readString(
                        field,
                        CompositeReferenceSleeve.MAX_SLEEVE_ID_LENGTH,
                        allowBlank = false,
                    )
                    "source" -> source = readCompositeReferenceSource()
                    "direction" -> direction = readEnum<CompositeSleeveDirection>(field)
                    "role" -> role = readEnum<CompositeSleeveRole>(field)
                    "targetWeight" -> targetWeight =
                        readNullableFiniteDoubleInRange(field, 0.0, CompositeReferenceSleeve.MAX_WEIGHT)
                    "minimumWeight" -> minimumWeight =
                        readNullableFiniteDoubleInRange(field, 0.0, CompositeReferenceSleeve.MAX_WEIGHT)
                    "maximumWeight" -> maximumWeight =
                        readNullableFiniteDoubleInRange(field, 0.0, CompositeReferenceSleeve.MAX_WEIGHT)
                    "targetWeightOrigin" -> targetWeightOrigin =
                        readNullableEnum<CompositeParameterOrigin>(field)
                    "weightBandOrigin" -> weightBandOrigin =
                        readNullableEnum<CompositeParameterOrigin>(field)
                    "riskBudget" -> riskBudget =
                        readNullableFiniteDoubleInRange(field, MIN_POSITIVE_FRACTION, 1.0)
                    "riskBudgetOrigin" -> riskBudgetOrigin =
                        readNullableEnum<CompositeParameterOrigin>(field)
                    "annualBorrowSpread" -> annualBorrowSpread = readNullableFiniteDoubleInRange(
                        field,
                        0.0,
                        CompositeReferenceSleeve.MAX_ANNUAL_RATE,
                    )
                    "annualBorrowSpreadOrigin" -> annualBorrowSpreadOrigin =
                        readNullableEnum<CompositeParameterOrigin>(field)
                    "hedgeRatioToCompositeBaseCurrency" -> hedgeRatio =
                        readNullableFiniteDoubleInRange(field, 0.0, 1.0)
                    "hedgeRatioOrigin" -> hedgeRatioOrigin =
                        readNullableEnum<CompositeParameterOrigin>(field)
                    "mbsInterestOnlyTerms" -> mbsInterestOnlyTerms =
                        readNullableObject(field, ::readMbsInterestOnlySleeveTerms)
                }
            }
            return CompositeReferenceSleeve(
                sleeveId = sleeveId ?: missing("sleeveId"),
                source = source ?: missing("source"),
                direction = direction ?: missing("direction"),
                role = role ?: missing("role"),
                targetWeight = targetWeight,
                minimumWeight = minimumWeight,
                maximumWeight = maximumWeight,
                targetWeightOrigin = targetWeightOrigin,
                weightBandOrigin = weightBandOrigin,
                riskBudget = riskBudget,
                riskBudgetOrigin = riskBudgetOrigin,
                annualBorrowSpread = annualBorrowSpread,
                annualBorrowSpreadOrigin = annualBorrowSpreadOrigin,
                hedgeRatioToCompositeBaseCurrency = hedgeRatio,
                hedgeRatioOrigin = hedgeRatioOrigin,
                mbsInterestOnlyTerms = mbsInterestOnlyTerms,
            )
        }

        private fun readCompositeReferenceSource(): CompositeReferenceSource {
            var kind: CompositeReferenceSourceKind? = null
            var benchmarkRef: BenchmarkRef? = null
            var instrumentId: String? = null
            readObject("composite source", COMPOSITE_REFERENCE_SOURCE_FIELDS) { field ->
                when (field) {
                    "kind" -> kind = readEnum<CompositeReferenceSourceKind>(field)
                    "benchmarkRef" -> benchmarkRef = readNullableObject(field) { readBenchmarkRef(field) }
                    "instrumentId" -> instrumentId = readNullableString(
                        field,
                        CompositeReferenceSource.MAX_INSTRUMENT_ID_LENGTH,
                        allowBlank = false,
                    )
                }
            }
            return CompositeReferenceSource(
                kind = kind ?: missing("kind"),
                benchmarkRef = benchmarkRef,
                instrumentId = instrumentId,
            )
        }

        private fun readCompositeExposureConstraint(label: String): CompositeExposureConstraint {
            var target: Double? = null
            var minimum: Double? = null
            var maximum: Double? = null
            var origin: CompositeParameterOrigin? = null
            readObject(label, COMPOSITE_EXPOSURE_CONSTRAINT_FIELDS) { field ->
                when (field) {
                    "target" -> target = readNullableFiniteDoubleInRange(
                        field,
                        -CompositeExposureConstraint.MAX_ABSOLUTE_EXPOSURE,
                        CompositeExposureConstraint.MAX_ABSOLUTE_EXPOSURE,
                    )
                    "minimum" -> minimum = readFiniteDoubleInRange(
                        field,
                        -CompositeExposureConstraint.MAX_ABSOLUTE_EXPOSURE,
                        CompositeExposureConstraint.MAX_ABSOLUTE_EXPOSURE,
                    )
                    "maximum" -> maximum = readFiniteDoubleInRange(
                        field,
                        -CompositeExposureConstraint.MAX_ABSOLUTE_EXPOSURE,
                        CompositeExposureConstraint.MAX_ABSOLUTE_EXPOSURE,
                    )
                    "origin" -> origin = readEnum<CompositeParameterOrigin>(field)
                }
            }
            return CompositeExposureConstraint(
                target = target,
                minimum = minimum ?: missing("minimum"),
                maximum = maximum ?: missing("maximum"),
                origin = origin ?: missing("origin"),
            )
        }

        private fun readCompositeDurationConstraint(label: String): CompositeDurationConstraint {
            var targetYears: Double? = null
            var minimumYears: Double? = null
            var maximumYears: Double? = null
            var origin: CompositeParameterOrigin? = null
            readObject(label, COMPOSITE_DURATION_CONSTRAINT_FIELDS) { field ->
                when (field) {
                    "targetYears" -> targetYears = readNullableFiniteDoubleInRange(
                        field,
                        -CompositeDurationConstraint.MAX_ABSOLUTE_DURATION_YEARS,
                        CompositeDurationConstraint.MAX_ABSOLUTE_DURATION_YEARS,
                    )
                    "minimumYears" -> minimumYears = readFiniteDoubleInRange(
                        field,
                        -CompositeDurationConstraint.MAX_ABSOLUTE_DURATION_YEARS,
                        CompositeDurationConstraint.MAX_ABSOLUTE_DURATION_YEARS,
                    )
                    "maximumYears" -> maximumYears = readFiniteDoubleInRange(
                        field,
                        -CompositeDurationConstraint.MAX_ABSOLUTE_DURATION_YEARS,
                        CompositeDurationConstraint.MAX_ABSOLUTE_DURATION_YEARS,
                    )
                    "origin" -> origin = readEnum<CompositeParameterOrigin>(field)
                }
            }
            return CompositeDurationConstraint(
                targetYears = targetYears,
                minimumYears = minimumYears ?: missing("minimumYears"),
                maximumYears = maximumYears ?: missing("maximumYears"),
                origin = origin ?: missing("origin"),
            )
        }

        private fun readCompositeRebalanceSchedule(label: String): CompositeRebalanceSchedule {
            var calendar: CompositeRebalanceCalendar? = null
            var months: Set<Int>? = null
            var origin: CompositeParameterOrigin? = null
            readObject(label, COMPOSITE_REBALANCE_SCHEDULE_FIELDS) { field ->
                when (field) {
                    "calendar" -> calendar = readEnum<CompositeRebalanceCalendar>(field)
                    "months" -> months = readSortedUniqueIntSet(
                        field,
                        MONTHS_PER_YEAR,
                        1,
                        MONTHS_PER_YEAR,
                        allowEmpty = true,
                    )
                    "origin" -> origin = readEnum<CompositeParameterOrigin>(field)
                }
            }
            return CompositeRebalanceSchedule(
                calendar = calendar ?: missing("calendar"),
                months = months ?: missing("months"),
                origin = origin ?: missing("origin"),
            )
        }

        private fun readMbsInterestOnlySleeveTerms(): MbsInterestOnlySleeveTerms {
            var prepaymentModel: MbsInterestOnlyPrepaymentModel? = null
            var termsProvenance: MbsInterestOnlyTermsProvenance? = null
            var officialSourceUrls: Set<String>? = null
            var modelParameters: MbsInterestOnlyModelParameters? = null
            readObject("mbsInterestOnlyTerms", MBS_INTEREST_ONLY_TERMS_FIELDS) { field ->
                when (field) {
                    "prepaymentModel" -> prepaymentModel = readEnum<MbsInterestOnlyPrepaymentModel>(field)
                    "termsProvenance" -> termsProvenance = readEnum<MbsInterestOnlyTermsProvenance>(field)
                    "officialSourceUrls" -> officialSourceUrls = readSortedUniqueHttpsUrlSet(
                        field,
                        MbsInterestOnlySleeveTerms.MAX_OFFICIAL_SOURCE_URLS,
                    )
                    "modelParameters" -> modelParameters = readMbsInterestOnlyModelParameters()
                }
            }
            return MbsInterestOnlySleeveTerms(
                prepaymentModel = prepaymentModel ?: missing("prepaymentModel"),
                termsProvenance = termsProvenance ?: missing("termsProvenance"),
                officialSourceUrls = officialSourceUrls ?: missing("officialSourceUrls"),
                modelParameters = modelParameters ?: missing("modelParameters"),
            )
        }

        private fun readMbsInterestOnlyModelParameters(): MbsInterestOnlyModelParameters {
            var effectiveDurationYears: Double? = null
            var baseCpr: Double? = null
            var cprSensitivity: Double? = null
            var cprVolatility: Double? = null
            var couponStripYield: Double? = null
            var origin: MbsInterestOnlyModelParameterOrigin? = null
            var sourceUrl: String? = null
            var calibrationId: String? = null
            readObject("MBS IO model parameters", MBS_INTEREST_ONLY_MODEL_PARAMETER_FIELDS) { field ->
                when (field) {
                    "effectiveDurationYears" -> effectiveDurationYears = readFiniteDoubleInRange(
                        field,
                        -CompositeDurationConstraint.MAX_ABSOLUTE_DURATION_YEARS,
                        -MIN_POSITIVE_FRACTION,
                    )
                    "baseConditionalPrepaymentRateAnnual" -> baseCpr =
                        readFiniteDoubleInRange(field, 0.0, 1.0)
                    "cprIncreasePerOnePercentMortgageRateDecline" -> cprSensitivity =
                        readFiniteDoubleInRange(field, 0.0, 1.0)
                    "annualConditionalPrepaymentRateVolatility" -> cprVolatility =
                        readFiniteDoubleInRange(field, 0.0, 1.0)
                    "couponStripYieldAnnual" -> couponStripYield =
                        readFiniteDoubleInRange(field, 0.0, 1.0)
                    "origin" -> origin = readEnum<MbsInterestOnlyModelParameterOrigin>(field)
                    "sourceUrl" -> sourceUrl = readNullableHttpsUrl(field)
                    "calibrationId" -> calibrationId = readNullableString(
                        field,
                        MAX_COMPOSITE_ASSUMPTION_ID_LENGTH,
                        allowBlank = false,
                    )
                }
            }
            return MbsInterestOnlyModelParameters(
                effectiveDurationYears = effectiveDurationYears ?: missing("effectiveDurationYears"),
                baseConditionalPrepaymentRateAnnual = baseCpr ?: missing("baseConditionalPrepaymentRateAnnual"),
                cprIncreasePerOnePercentMortgageRateDecline =
                    cprSensitivity ?: missing("cprIncreasePerOnePercentMortgageRateDecline"),
                annualConditionalPrepaymentRateVolatility =
                    cprVolatility ?: missing("annualConditionalPrepaymentRateVolatility"),
                couponStripYieldAnnual = couponStripYield ?: missing("couponStripYieldAnnual"),
                origin = origin ?: missing("origin"),
                sourceUrl = sourceUrl,
                calibrationId = calibrationId,
            )
        }

        private fun readAlternativeRiskPremiaProfile(): AlternativeRiskPremiaProfile {
            var strategyFamilies: Set<AlternativeRiskPremiaStrategyFamily>? = null
            var drivers: List<AlternativeRiskPremiaDriver>? = null
            var signalModel: AlternativeRiskPremiaSignalModel? = null
            var longGross: CompositeExposureConstraint? = null
            var shortGross: CompositeExposureConstraint? = null
            var net: CompositeExposureConstraint? = null
            var targetVolatilityAnnual: Double? = null
            var targetVolatilityOrigin: CompositeParameterOrigin? = null
            var lookback: Int? = null
            var lookbackOrigin: CompositeParameterOrigin? = null
            var rebalanceSchedule: CompositeRebalanceSchedule? = null
            var financingSpread: Double? = null
            var financingOrigin: CompositeParameterOrigin? = null
            var borrowSpread: Double? = null
            var borrowOrigin: CompositeParameterOrigin? = null
            var implementationCost: Double? = null
            var implementationOrigin: CompositeParameterOrigin? = null
            var supportLevel: BenchmarkSupportLevel? = null
            var provenance: CompositeRuleProvenance? = null
            var confidence: CompositeConfidence? = null
            var officialSourceUrls: Set<String>? = null
            var assumptionId: String? = null
            readObject("alternativeRiskPremiaProfile", ALTERNATIVE_RISK_PREMIA_PROFILE_FIELDS) { field ->
                when (field) {
                    "strategyFamilies" -> strategyFamilies =
                        readSortedUniqueEnumSet<AlternativeRiskPremiaStrategyFamily>(
                            field,
                            AlternativeRiskPremiaStrategyFamily.entries.size,
                        )
                    "drivers" -> drivers = readAlternativeRiskPremiaDrivers()
                    "signalModel" -> signalModel = readEnum<AlternativeRiskPremiaSignalModel>(field)
                    "longGrossExposureConstraint" -> longGross = readCompositeExposureConstraint(field)
                    "shortGrossExposureConstraint" -> shortGross = readCompositeExposureConstraint(field)
                    "netExposureConstraint" -> net = readCompositeExposureConstraint(field)
                    "targetVolatilityAnnual" -> targetVolatilityAnnual = readNullableFiniteDoubleInRange(
                        field,
                        MIN_POSITIVE_FRACTION,
                        CompositeReferenceProfile.MAX_TARGET_VOLATILITY,
                    )
                    "targetVolatilityOrigin" -> targetVolatilityOrigin =
                        readNullableEnum<CompositeParameterOrigin>(field)
                    "signalLookbackTradingDays" -> lookback = readExactIntInRange(
                        field,
                        CompositeReferenceProfile.MIN_LOOKBACK_DAYS,
                        CompositeReferenceProfile.MAX_LOOKBACK_DAYS,
                    )
                    "signalLookbackOrigin" -> lookbackOrigin = readEnum<CompositeParameterOrigin>(field)
                    "rebalanceSchedule" -> rebalanceSchedule = readCompositeRebalanceSchedule(field)
                    "annualFinancingSpread" -> financingSpread = readFiniteDoubleInRange(field, 0.0, 1.0)
                    "annualFinancingSpreadOrigin" -> financingOrigin = readEnum<CompositeParameterOrigin>(field)
                    "annualShortBorrowSpread" -> borrowSpread = readFiniteDoubleInRange(field, 0.0, 1.0)
                    "annualShortBorrowSpreadOrigin" -> borrowOrigin = readEnum<CompositeParameterOrigin>(field)
                    "annualImplementationCostRate" -> implementationCost =
                        readFiniteDoubleInRange(field, 0.0, 1.0)
                    "annualImplementationCostOrigin" -> implementationOrigin =
                        readEnum<CompositeParameterOrigin>(field)
                    "supportLevel" -> supportLevel = readEnum<BenchmarkSupportLevel>(field)
                    "provenance" -> provenance = readEnum<CompositeRuleProvenance>(field)
                    "confidence" -> confidence = readEnum<CompositeConfidence>(field)
                    "officialSourceUrls" -> officialSourceUrls = readSortedUniqueHttpsUrlSet(
                        field,
                        AlternativeRiskPremiaProfile.MAX_OFFICIAL_SOURCE_URLS,
                    )
                    "assumptionId" -> assumptionId = readNullableString(
                        field,
                        MAX_COMPOSITE_ASSUMPTION_ID_LENGTH,
                        allowBlank = false,
                    )
                }
            }
            return AlternativeRiskPremiaProfile(
                strategyFamilies = strategyFamilies ?: missing("strategyFamilies"),
                drivers = drivers ?: missing("drivers"),
                signalModel = signalModel ?: missing("signalModel"),
                longGrossExposureConstraint = longGross ?: missing("longGrossExposureConstraint"),
                shortGrossExposureConstraint = shortGross ?: missing("shortGrossExposureConstraint"),
                netExposureConstraint = net ?: missing("netExposureConstraint"),
                targetVolatilityAnnual = targetVolatilityAnnual,
                targetVolatilityOrigin = targetVolatilityOrigin,
                signalLookbackTradingDays = lookback ?: missing("signalLookbackTradingDays"),
                signalLookbackOrigin = lookbackOrigin ?: missing("signalLookbackOrigin"),
                rebalanceSchedule = rebalanceSchedule ?: missing("rebalanceSchedule"),
                annualFinancingSpread = financingSpread ?: missing("annualFinancingSpread"),
                annualFinancingSpreadOrigin = financingOrigin ?: missing("annualFinancingSpreadOrigin"),
                annualShortBorrowSpread = borrowSpread ?: missing("annualShortBorrowSpread"),
                annualShortBorrowSpreadOrigin = borrowOrigin ?: missing("annualShortBorrowSpreadOrigin"),
                annualImplementationCostRate = implementationCost ?: missing("annualImplementationCostRate"),
                annualImplementationCostOrigin = implementationOrigin ?: missing("annualImplementationCostOrigin"),
                supportLevel = supportLevel ?: missing("supportLevel"),
                provenance = provenance ?: missing("provenance"),
                confidence = confidence ?: missing("confidence"),
                officialSourceUrls = officialSourceUrls ?: missing("officialSourceUrls"),
                assumptionId = assumptionId,
            )
        }

        private fun readAlternativeRiskPremiaDrivers(): List<AlternativeRiskPremiaDriver> {
            beginArray("drivers")
            val drivers = mutableListOf<AlternativeRiskPremiaDriver>()
            var previousDriverId: String? = null
            while (reader.hasNext()) {
                if (drivers.size >= AlternativeRiskPremiaProfile.MAX_DRIVERS) {
                    fail("drivers 배열 항목이 너무 많습니다.")
                }
                val driver = readAlternativeRiskPremiaDriver()
                if (previousDriverId != null && driver.driverId <= previousDriverId) {
                    fail("drivers 배열은 driverId 오름차순이어야 합니다.")
                }
                previousDriverId = driver.driverId
                drivers += driver
            }
            endArray()
            if (drivers.isEmpty()) fail("drivers 배열은 비어 있을 수 없습니다.")
            return drivers.toList()
        }

        private fun readAlternativeRiskPremiaDriver(): AlternativeRiskPremiaDriver {
            var driverId: String? = null
            var source: CompositeReferenceSource? = null
            var strategyFamily: AlternativeRiskPremiaStrategyFamily? = null
            var directionPolicy: AlternativeRiskPremiaSignalDirectionPolicy? = null
            var targetRiskBudget: Double? = null
            var riskBudgetOrigin: CompositeParameterOrigin? = null
            var hedgeRatio: Double? = null
            var hedgeRatioOrigin: CompositeParameterOrigin? = null
            readObject("alternative risk-premia driver", ALTERNATIVE_RISK_PREMIA_DRIVER_FIELDS) { field ->
                when (field) {
                    "driverId" -> driverId = readString(
                        field,
                        AlternativeRiskPremiaDriver.MAX_DRIVER_ID_LENGTH,
                        allowBlank = false,
                    )
                    "source" -> source = readCompositeReferenceSource()
                    "strategyFamily" -> strategyFamily = readEnum<AlternativeRiskPremiaStrategyFamily>(field)
                    "signalDirectionPolicy" -> directionPolicy =
                        readEnum<AlternativeRiskPremiaSignalDirectionPolicy>(field)
                    "targetRiskBudget" -> targetRiskBudget =
                        readNullableFiniteDoubleInRange(field, MIN_POSITIVE_FRACTION, 1.0)
                    "riskBudgetOrigin" -> riskBudgetOrigin =
                        readNullableEnum<CompositeParameterOrigin>(field)
                    "hedgeRatioToProfileBaseCurrency" -> hedgeRatio =
                        readNullableFiniteDoubleInRange(field, 0.0, 1.0)
                    "hedgeRatioOrigin" -> hedgeRatioOrigin =
                        readNullableEnum<CompositeParameterOrigin>(field)
                }
            }
            return AlternativeRiskPremiaDriver(
                driverId = driverId ?: missing("driverId"),
                source = source ?: missing("source"),
                strategyFamily = strategyFamily ?: missing("strategyFamily"),
                signalDirectionPolicy = directionPolicy ?: missing("signalDirectionPolicy"),
                targetRiskBudget = targetRiskBudget,
                riskBudgetOrigin = riskBudgetOrigin,
                hedgeRatioToProfileBaseCurrency = hedgeRatio,
                hedgeRatioOrigin = hedgeRatioOrigin,
            )
        }

        private fun readCommoditySpotReferenceTerms(): CommoditySpotReferenceTerms {
            var benchmarkRef: BenchmarkRef? = null
            var assetClass: CommodityAssetClass? = null
            var baseCurrency: ReferenceCurrency? = null
            var spotAllocation: Double? = null
            var collateralAllocation: Double? = null
            var annualStorageCostRate: Double? = null
            var annualCustodyAndInsuranceCostRate: Double? = null
            var annualConvenienceYieldRate: Double? = null
            var collateralYieldParticipation: Double? = null
            var provenance: CommodityReferenceTermsProvenance? = null
            var officialSourceUrls: Set<String>? = null
            var assumptionId: String? = null
            readObject("commoditySpotTerms", COMMODITY_SPOT_TERMS_FIELDS) { field ->
                when (field) {
                    "benchmarkRef" -> benchmarkRef = readBenchmarkRef("benchmarkRef")
                    "assetClass" -> assetClass = readEnum<CommodityAssetClass>("assetClass")
                    "baseCurrency" -> baseCurrency = readEnum<ReferenceCurrency>("baseCurrency")
                    "spotAllocation" -> spotAllocation =
                        readFiniteDoubleInRange("spotAllocation", MIN_POSITIVE_FRACTION, 1.0)
                    "collateralAllocation" -> collateralAllocation =
                        readFiniteDoubleInRange("collateralAllocation", 0.0, 1.0)
                    "annualStorageCostRate" -> annualStorageCostRate =
                        readFiniteDoubleInRange("annualStorageCostRate", 0.0, 0.50)
                    "annualCustodyAndInsuranceCostRate" -> annualCustodyAndInsuranceCostRate =
                        readFiniteDoubleInRange("annualCustodyAndInsuranceCostRate", 0.0, 0.50)
                    "annualConvenienceYieldRate" -> annualConvenienceYieldRate =
                        readFiniteDoubleInRange("annualConvenienceYieldRate", -0.50, 1.0)
                    "collateralYieldParticipation" -> collateralYieldParticipation =
                        readFiniteDoubleInRange("collateralYieldParticipation", 0.0, 1.0)
                    "provenance" -> provenance =
                        readEnum<CommodityReferenceTermsProvenance>("provenance")
                    "officialSourceUrls" -> officialSourceUrls = readSortedUniqueHttpsUrlSet(
                        "officialSourceUrls",
                        MAX_COMMODITY_OFFICIAL_SOURCE_URLS,
                    )
                    "assumptionId" -> assumptionId = readNullableString(
                        "assumptionId",
                        MAX_COMMODITY_ASSUMPTION_ID_LENGTH,
                        allowBlank = false,
                    )
                }
            }
            return CommoditySpotReferenceTerms(
                benchmarkRef = benchmarkRef ?: missing("benchmarkRef"),
                assetClass = assetClass ?: missing("assetClass"),
                baseCurrency = baseCurrency ?: missing("baseCurrency"),
                spotAllocation = spotAllocation ?: missing("spotAllocation"),
                collateralAllocation = collateralAllocation ?: missing("collateralAllocation"),
                annualStorageCostRate = annualStorageCostRate ?: missing("annualStorageCostRate"),
                annualCustodyAndInsuranceCostRate = annualCustodyAndInsuranceCostRate
                    ?: missing("annualCustodyAndInsuranceCostRate"),
                annualConvenienceYieldRate = annualConvenienceYieldRate
                    ?: missing("annualConvenienceYieldRate"),
                collateralYieldParticipation = collateralYieldParticipation
                    ?: missing("collateralYieldParticipation"),
                provenance = provenance ?: missing("provenance"),
                officialSourceUrls = officialSourceUrls ?: missing("officialSourceUrls"),
                assumptionId = assumptionId,
            )
        }

        private fun readFuturesReferenceTerms(): FuturesReferenceTerms {
            var benchmarkRef: BenchmarkRef? = null
            var baseCurrency: ReferenceCurrency? = null
            var portfolioStyle: FuturesPortfolioStyle? = null
            var allocationMode: FuturesAllocationMode? = null
            var collateralRatio: Double? = null
            var collateralYieldParticipation: Double? = null
            var sleeves: List<FuturesSleeveTerms>? = null
            var provenance: CommodityReferenceTermsProvenance? = null
            var officialSourceUrls: Set<String>? = null
            var assumptionId: String? = null
            readObject("futuresReferenceTerms", FUTURES_REFERENCE_TERMS_FIELDS) { field ->
                when (field) {
                    "benchmarkRef" -> benchmarkRef = readBenchmarkRef("benchmarkRef")
                    "baseCurrency" -> baseCurrency = readEnum<ReferenceCurrency>("baseCurrency")
                    "portfolioStyle" -> portfolioStyle =
                        readEnum<FuturesPortfolioStyle>("portfolioStyle")
                    "allocationMode" -> allocationMode =
                        readEnum<FuturesAllocationMode>("allocationMode")
                    "collateralRatio" -> collateralRatio =
                        readFiniteDoubleInRange("collateralRatio", 0.0, 2.0)
                    "collateralYieldParticipation" -> collateralYieldParticipation =
                        readFiniteDoubleInRange("collateralYieldParticipation", 0.0, 1.0)
                    "sleeves" -> sleeves = readFuturesSleeves()
                    "provenance" -> provenance =
                        readEnum<CommodityReferenceTermsProvenance>("provenance")
                    "officialSourceUrls" -> officialSourceUrls = readSortedUniqueHttpsUrlSet(
                        "officialSourceUrls",
                        MAX_COMMODITY_OFFICIAL_SOURCE_URLS,
                    )
                    "assumptionId" -> assumptionId = readNullableString(
                        "assumptionId",
                        MAX_COMMODITY_ASSUMPTION_ID_LENGTH,
                        allowBlank = false,
                    )
                }
            }
            return FuturesReferenceTerms(
                benchmarkRef = benchmarkRef ?: missing("benchmarkRef"),
                baseCurrency = baseCurrency ?: missing("baseCurrency"),
                portfolioStyle = portfolioStyle ?: missing("portfolioStyle"),
                allocationMode = allocationMode ?: missing("allocationMode"),
                collateralRatio = collateralRatio ?: missing("collateralRatio"),
                collateralYieldParticipation = collateralYieldParticipation
                    ?: missing("collateralYieldParticipation"),
                sleeves = sleeves ?: missing("sleeves"),
                provenance = provenance ?: missing("provenance"),
                officialSourceUrls = officialSourceUrls ?: missing("officialSourceUrls"),
                assumptionId = assumptionId,
            )
        }

        private fun readFuturesSleeves(): List<FuturesSleeveTerms> {
            beginArray("sleeves")
            val result = ArrayList<FuturesSleeveTerms>()
            var previousSleeveId: String? = null
            while (reader.hasNext()) {
                if (result.size >= MAX_FUTURES_SLEEVES) fail("sleeves 배열 항목이 너무 많습니다.")
                val sleeve = readFuturesSleeveTerms()
                if (previousSleeveId != null && previousSleeveId >= sleeve.sleeveId) {
                    fail("sleeves 배열은 sleeveId 오름차순이며 중복이 없어야 합니다.")
                }
                previousSleeveId = sleeve.sleeveId
                result += sleeve
            }
            endArray()
            if (result.isEmpty()) fail("sleeves 배열은 비어 있을 수 없습니다.")
            return result
        }

        private fun readFuturesSleeveTerms(): FuturesSleeveTerms {
            var sleeveId: String? = null
            var curveId: String? = null
            var assetClass: CommodityAssetClass? = null
            var targetWeight: Double? = null
            var notionalExposureRatio: Double? = null
            var rollCalendar: FuturesRollCalendar? = null
            var eligibleDeliveryMonths: Set<Int>? = null
            var rollStartTradingDaysBeforeExpiry: Int? = null
            var rollWindowTradingDays: Int? = null
            var priceReturnConvention: FuturesPriceReturnConvention? = null
            var fixedPriceReturnNotional: Double? = null
            readObject("futures sleeve", FUTURES_SLEEVE_TERMS_FIELDS) { field ->
                when (field) {
                    "sleeveId" -> sleeveId = readString(
                        "sleeveId",
                        MAX_FUTURES_IDENTIFIER_LENGTH,
                        allowBlank = false,
                    )
                    "curveId" -> curveId = readString(
                        "curveId",
                        MAX_FUTURES_IDENTIFIER_LENGTH,
                        allowBlank = false,
                    )
                    "assetClass" -> assetClass = readEnum<CommodityAssetClass>("assetClass")
                    "targetWeight" -> targetWeight =
                        readFiniteDoubleInRange("targetWeight", MIN_POSITIVE_FRACTION, 1.0)
                    "notionalExposureRatio" -> notionalExposureRatio =
                        readFiniteDoubleInRange("notionalExposureRatio", 0.0, 5.0)
                    "rollCalendar" -> rollCalendar =
                        readEnum<FuturesRollCalendar>("rollCalendar")
                    "eligibleDeliveryMonths" -> eligibleDeliveryMonths = readSortedUniqueIntSet(
                        "eligibleDeliveryMonths",
                        12,
                        1,
                        12,
                    )
                    "rollStartTradingDaysBeforeExpiry" -> rollStartTradingDaysBeforeExpiry =
                        readExactIntInRange("rollStartTradingDaysBeforeExpiry", 1, 90)
                    "rollWindowTradingDays" -> rollWindowTradingDays =
                        readExactIntInRange("rollWindowTradingDays", 1, 90)
                    "priceReturnConvention" -> priceReturnConvention =
                        readEnum<FuturesPriceReturnConvention>("priceReturnConvention")
                    "fixedPriceReturnNotional" -> fixedPriceReturnNotional =
                        readNullableFiniteDoubleInRange("fixedPriceReturnNotional", MIN_POSITIVE_FRACTION, 1.0e12)
                }
            }
            return FuturesSleeveTerms(
                sleeveId = sleeveId ?: missing("sleeveId"),
                curveId = curveId ?: missing("curveId"),
                assetClass = assetClass ?: missing("assetClass"),
                targetWeight = targetWeight ?: missing("targetWeight"),
                notionalExposureRatio = notionalExposureRatio ?: missing("notionalExposureRatio"),
                rollCalendar = rollCalendar ?: missing("rollCalendar"),
                eligibleDeliveryMonths = eligibleDeliveryMonths ?: missing("eligibleDeliveryMonths"),
                rollStartTradingDaysBeforeExpiry = rollStartTradingDaysBeforeExpiry
                    ?: missing("rollStartTradingDaysBeforeExpiry"),
                rollWindowTradingDays = rollWindowTradingDays ?: missing("rollWindowTradingDays"),
                priceReturnConvention = priceReturnConvention ?: missing("priceReturnConvention"),
                fixedPriceReturnNotional = fixedPriceReturnNotional,
            )
        }

        private fun readInstruments(): List<StockDefinition> {
            beginArray("instruments")
            val definitions = ArrayList<StockDefinition>()
            val ids = HashSet<String>()
            val marketSymbols = HashSet<Pair<Market, String>>()
            while (reader.hasNext()) {
                if (definitions.size >= maxInstruments) {
                    fail("instruments 배열은 최대 ${maxInstruments}개 항목만 허용합니다.")
                }
                val parsed = readInstrument()
                if (parsed.order != definitions.size) {
                    fail("종목 order는 0부터 빠짐없이 증가해야 합니다: ${parsed.order}")
                }
                if (!ids.add(parsed.definition.id)) {
                    fail("instruments 배열에 중복된 종목 ID가 있습니다.")
                }
                val marketSymbol = parsed.definition.market to parsed.definition.symbol.trim().uppercase()
                if (!marketSymbols.add(marketSymbol)) {
                    fail("instruments 배열의 같은 시장에 중복된 종목 코드가 있습니다.")
                }
                definitions += parsed.definition
            }
            endArray()
            if (definitions.isEmpty()) fail("instruments 배열은 비어 있을 수 없습니다.")
            return definitions
        }

        private fun readInstrument(): ParsedInstrument {
            var order: Int? = null
            var symbol: String? = null
            var name: String? = null
            var englishName: String? = null
            var market: Market? = null
            var sector: Sector? = null
            var instrumentType: InstrumentType? = null
            var initialPrice: Double? = null
            var volatility: Double? = null
            var dividendYield: Double? = null
            var marketCap: Double? = null
            var sharesOutstanding: Long? = null
            var description: String? = null
            var beta: Double? = null
            var quantityStep: Double? = null
            var lotSize: Double? = null
            var etfProfile: EtfProfile? = null
            var fundProductProfile: FundProductProfile? = null
            var behaviorProfile: InstrumentBehaviorProfile? = null
            var identityProfile: InstrumentIdentityProfile? = null
            var industrySegments: Set<IndustrySegment>? = null

            readObject(
                label = "종목",
                allowedFields = INSTRUMENT_FIELDS,
                requiredFields = REQUIRED_INSTRUMENT_FIELDS,
            ) { field ->
                when (field) {
                    "order" -> order = readExactInt("order")
                    "symbol" -> symbol = readString("symbol", MAX_SYMBOL_LENGTH, allowBlank = false)
                    "name" -> name = readString("name", MAX_NAME_LENGTH, allowBlank = false)
                    "englishName" -> englishName = readString("englishName", MAX_ENGLISH_NAME_LENGTH, allowBlank = false)
                    "market" -> market = readEnum<Market>("market")
                    "sector" -> sector = readEnum<Sector>("sector")
                    "instrumentType" -> instrumentType = readEnum<InstrumentType>("instrumentType")
                    "initialPrice" -> initialPrice = readFiniteDouble("initialPrice")
                    "volatility" -> volatility = readFiniteDouble("volatility")
                    "dividendYield" -> dividendYield = readFiniteDouble("dividendYield")
                    "marketCap" -> marketCap = readFiniteDouble("marketCap")
                    "sharesOutstanding" -> {
                        sharesOutstanding = readExactLong("sharesOutstanding").also { value ->
                            if (value !in 1L..MAX_BASE_SHARES_OUTSTANDING) {
                                fail(
                                    "sharesOutstanding 숫자는 1 이상 " +
                                        "$MAX_BASE_SHARES_OUTSTANDING 이하여야 합니다.",
                                )
                            }
                        }
                    }
                    "description" -> description = readString("description", MAX_DESCRIPTION_LENGTH, allowBlank = false)
                    "beta" -> beta = readFiniteDouble("beta")
                    "quantityStep" -> quantityStep = readFiniteDouble("quantityStep")
                    "lotSize" -> lotSize = readFiniteDouble("lotSize")
                    "etfProfile" -> etfProfile = readNullableObject("etfProfile", ::readEtfProfile)
                    "fundProductProfile" -> fundProductProfile =
                        readNullableObject("fundProductProfile", ::readFundProductProfile)
                    "behaviorProfile" -> behaviorProfile =
                        readNullableObject("behaviorProfile", ::readBehaviorProfile)
                    "identityProfile" -> identityProfile =
                        readNullableObject("identityProfile", ::readIdentityProfile)
                    "industrySegments" -> industrySegments = readUniqueEnumSet<IndustrySegment>(
                        label = "industrySegments",
                        maxItems = IndustrySegment.entries.size,
                    )
                }
            }

            val definition = StockDefinition(
                symbol = symbol ?: missing("symbol"),
                name = name ?: missing("name"),
                englishName = englishName ?: missing("englishName"),
                market = market ?: missing("market"),
                sector = sector ?: missing("sector"),
                initialPrice = initialPrice ?: missing("initialPrice"),
                volatility = volatility ?: missing("volatility"),
                dividendYield = dividendYield ?: missing("dividendYield"),
                marketCap = marketCap ?: missing("marketCap"),
                sharesOutstanding = sharesOutstanding ?: missing("sharesOutstanding"),
                description = description ?: missing("description"),
                beta = beta ?: missing("beta"),
                quantityStep = quantityStep ?: missing("quantityStep"),
                lotSize = lotSize ?: missing("lotSize"),
                etfProfile = etfProfile,
                fundProductProfile = fundProductProfile,
                instrumentTypeOverride = instrumentType ?: missing("instrumentType"),
                behaviorProfile = behaviorProfile,
                identityProfile = identityProfile,
                industrySegments = industrySegments ?: missing("industrySegments"),
            )
            return ParsedInstrument(order ?: missing("order"), definition)
        }

        private fun readFundProductProfile(): FundProductProfile {
            var benchmarkRef: BenchmarkRef? = null
            var replicationMode: FundReplicationMode? = null
            var returnVariant: FundReturnVariant? = null
            var legalStructure: FundLegalStructure? = null
            var referenceExposure: FundReferenceExposure? = null
            var returnTransforms: Set<FundReturnTransform>? = null
            var trackingErrorAnnualVolatility: Double? = null
            var dailyResetTerms: DailyResetTerms? = null
            var etnProductTerms: EtnProductTerms? = null
            var etnIssuerCreditModelParameters: EtnIssuerCreditModelParameters? = null
            var closedEndFundTerms: ClosedEndFundTerms? = null
            var closedEndFundMarketModelParameters: ClosedEndFundMarketModelParameters? = null
            var optionStrategyTerms: OptionStrategyTerms? = null
            var cashCollateralizedPutSpreadTerms: CashCollateralizedPutSpreadTerms? = null
            var trackingErrorSeen = false

            readObject("fundProductProfile", FUND_PRODUCT_PROFILE_FIELDS) { field ->
                when (field) {
                    "benchmarkRef" -> benchmarkRef = readBenchmarkRef("benchmarkRef")
                    "replicationMode" -> replicationMode = readEnum<FundReplicationMode>("replicationMode")
                    "returnVariant" -> returnVariant = readEnum<FundReturnVariant>("returnVariant")
                    "legalStructure" -> legalStructure = readEnum<FundLegalStructure>("legalStructure")
                    "referenceExposure" -> referenceExposure =
                        readEnum<FundReferenceExposure>("referenceExposure")
                    "returnTransforms" -> returnTransforms = readSortedUniqueEnumSet<FundReturnTransform>(
                        label = "returnTransforms",
                        maxItems = FundReturnTransform.entries.size,
                    )
                    "trackingErrorAnnualVolatility" -> {
                        trackingErrorSeen = true
                        trackingErrorAnnualVolatility = readNullableFiniteDoubleInRange(
                            "trackingErrorAnnualVolatility",
                            0.0,
                            FundProductProfile.MAX_TRACKING_ERROR,
                        )
                    }
                    "dailyResetTerms" -> dailyResetTerms =
                        readNullableObject("dailyResetTerms", ::readDailyResetTerms)
                    "etnProductTerms" -> etnProductTerms =
                        readNullableObject("etnProductTerms", ::readEtnProductTerms)
                    "etnIssuerCreditModelParameters" -> etnIssuerCreditModelParameters =
                        readNullableObject(
                            "etnIssuerCreditModelParameters",
                            ::readEtnIssuerCreditModelParameters,
                        )
                    "closedEndFundTerms" -> closedEndFundTerms =
                        readNullableObject("closedEndFundTerms", ::readClosedEndFundTerms)
                    "closedEndFundMarketModelParameters" -> closedEndFundMarketModelParameters =
                        readNullableObject(
                            "closedEndFundMarketModelParameters",
                            ::readClosedEndFundMarketModelParameters,
                        )
                    "optionStrategyTerms" -> optionStrategyTerms =
                        readNullableObject("optionStrategyTerms", ::readOptionStrategyTerms)
                    "cashCollateralizedPutSpreadTerms" -> cashCollateralizedPutSpreadTerms =
                        readNullableObject(
                            "cashCollateralizedPutSpreadTerms",
                            ::readCashCollateralizedPutSpreadTerms,
                        )
                }
            }
            if (!trackingErrorSeen) missing("trackingErrorAnnualVolatility")
            return FundProductProfile(
                benchmarkRef = benchmarkRef ?: missing("benchmarkRef"),
                replicationMode = replicationMode ?: missing("replicationMode"),
                returnVariant = returnVariant ?: missing("returnVariant"),
                legalStructure = legalStructure ?: missing("legalStructure"),
                referenceExposure = referenceExposure ?: missing("referenceExposure"),
                returnTransforms = returnTransforms ?: missing("returnTransforms"),
                trackingErrorAnnualVolatility = trackingErrorAnnualVolatility,
                dailyResetTerms = dailyResetTerms,
                etnProductTerms = etnProductTerms,
                etnIssuerCreditModelParameters = etnIssuerCreditModelParameters,
                closedEndFundTerms = closedEndFundTerms,
                closedEndFundMarketModelParameters = closedEndFundMarketModelParameters,
                optionStrategyTerms = optionStrategyTerms,
                cashCollateralizedPutSpreadTerms = cashCollateralizedPutSpreadTerms,
            )
        }

        private fun readCashCollateralizedPutSpreadTerms(): CashCollateralizedPutSpreadTerms {
            var productId: String? = null
            var cashBenchmarkRef: BenchmarkRef? = null
            var optionReference: DailyResetReference? = null
            var directReferenceTerminationRule: DirectReferenceTerminationRule? = null
            var tenorTradingDays: Int? = null
            var rollCalendar: OptionRollCalendar? = null
            var rollLeadTradingDays: Int? = null
            var maximumSettlementLossRatio: Double? = null
            var shortPutStrikeMoneyness: Double? = null
            var longPutStrikeMoneyness: Double? = null
            var provenance: OptionStrategyTermsProvenance? = null
            var officialSourceUrls: Set<String>? = null
            var assumptionId: String? = null
            var premiumModel: OptionPremiumModelParameters? = null

            readObject(
                "cashCollateralizedPutSpreadTerms",
                CASH_COLLATERALIZED_PUT_SPREAD_TERMS_FIELDS,
            ) { field ->
                when (field) {
                    "productId" -> productId =
                        readString("productId", MAX_FUND_PRODUCT_ID_LENGTH, allowBlank = false)
                    "cashBenchmarkRef" -> cashBenchmarkRef = readBenchmarkRef("cashBenchmarkRef")
                    "optionReference" -> optionReference = readDailyResetReference()
                    "directReferenceTerminationRule" -> directReferenceTerminationRule =
                        readNullableObject(
                            "directReferenceTerminationRule",
                            ::readDirectReferenceTerminationRule,
                        )
                    "tenorTradingDays" -> tenorTradingDays =
                        readExactIntInRange("tenorTradingDays", 1, MAX_OPTION_TENOR_TRADING_DAYS)
                    "rollCalendar" -> rollCalendar = readEnum<OptionRollCalendar>("rollCalendar")
                    "rollLeadTradingDays" -> rollLeadTradingDays = readExactIntInRange(
                        "rollLeadTradingDays",
                        0,
                        MAX_OPTION_TENOR_TRADING_DAYS - 1,
                    )
                    "maximumSettlementLossRatio" -> maximumSettlementLossRatio =
                        readFiniteDoubleInRange("maximumSettlementLossRatio", MIN_POSITIVE_FRACTION, 1.0)
                    "shortPutStrikeMoneyness" -> shortPutStrikeMoneyness =
                        readFiniteDoubleInRange("shortPutStrikeMoneyness", 0.051, 1.5)
                    "longPutStrikeMoneyness" -> longPutStrikeMoneyness =
                        readFiniteDoubleInRange("longPutStrikeMoneyness", 0.05, 1.5)
                    "provenance" -> provenance =
                        readEnum<OptionStrategyTermsProvenance>("provenance")
                    "officialSourceUrls" -> officialSourceUrls = readSortedUniqueHttpsUrlSet(
                        "officialSourceUrls",
                        MAX_OPTION_OFFICIAL_SOURCE_URLS,
                    )
                    "assumptionId" -> assumptionId = readNullableString(
                        "assumptionId",
                        MAX_OPTION_ASSUMPTION_ID_LENGTH,
                        allowBlank = false,
                    )
                    "premiumModel" -> premiumModel = readOptionPremiumModelParameters()
                }
            }
            return CashCollateralizedPutSpreadTerms(
                productId = productId ?: missing("productId"),
                cashBenchmarkRef = cashBenchmarkRef ?: missing("cashBenchmarkRef"),
                optionReference = optionReference ?: missing("optionReference"),
                directReferenceTerminationRule = directReferenceTerminationRule,
                tenorTradingDays = tenorTradingDays ?: missing("tenorTradingDays"),
                rollCalendar = rollCalendar ?: missing("rollCalendar"),
                rollLeadTradingDays = rollLeadTradingDays ?: missing("rollLeadTradingDays"),
                maximumSettlementLossRatio = maximumSettlementLossRatio
                    ?: missing("maximumSettlementLossRatio"),
                shortPutStrikeMoneyness = shortPutStrikeMoneyness ?: missing("shortPutStrikeMoneyness"),
                longPutStrikeMoneyness = longPutStrikeMoneyness ?: missing("longPutStrikeMoneyness"),
                provenance = provenance ?: missing("provenance"),
                officialSourceUrls = officialSourceUrls ?: missing("officialSourceUrls"),
                assumptionId = assumptionId,
                premiumModel = premiumModel ?: missing("premiumModel"),
            )
        }

        private fun readOptionStrategyTerms(): OptionStrategyTerms {
            var productId: String? = null
            var reference: DailyResetReference? = null
            var directReferenceTerminationRule: DirectReferenceTerminationRule? = null
            var kind: OptionStrategyKind? = null
            var tenorTradingDays: Int? = null
            var rollCalendar: OptionRollCalendar? = null
            var rollLeadTradingDays: Int? = null
            var provenance: OptionStrategyTermsProvenance? = null
            var officialSourceUrls: Set<String>? = null
            var assumptionId: String? = null
            var premiumModel: OptionPremiumModelParameters? = null
            var coveredCall: CoveredCallStrategyTerms? = null
            var optionIncome: OptionIncomeStrategyTerms? = null
            var bufferedPutSpread: BufferedPutSpreadStrategyTerms? = null
            readObject("optionStrategyTerms", OPTION_STRATEGY_TERMS_FIELDS) { field ->
                when (field) {
                    "productId" -> productId =
                        readString("productId", MAX_FUND_PRODUCT_ID_LENGTH, allowBlank = false)
                    "reference" -> reference = readDailyResetReference()
                    "directReferenceTerminationRule" -> directReferenceTerminationRule =
                        readNullableObject(
                            "directReferenceTerminationRule",
                            ::readDirectReferenceTerminationRule,
                        )
                    "kind" -> kind = readEnum<OptionStrategyKind>("kind")
                    "tenorTradingDays" -> tenorTradingDays =
                        readExactIntInRange("tenorTradingDays", 1, MAX_OPTION_TENOR_TRADING_DAYS)
                    "rollCalendar" -> rollCalendar = readEnum<OptionRollCalendar>("rollCalendar")
                    "rollLeadTradingDays" -> rollLeadTradingDays = readExactIntInRange(
                        "rollLeadTradingDays",
                        0,
                        MAX_OPTION_TENOR_TRADING_DAYS - 1,
                    )
                    "provenance" -> provenance =
                        readEnum<OptionStrategyTermsProvenance>("provenance")
                    "officialSourceUrls" -> officialSourceUrls = readSortedUniqueHttpsUrlSet(
                        "officialSourceUrls",
                        MAX_OPTION_OFFICIAL_SOURCE_URLS,
                    )
                    "assumptionId" -> assumptionId = readNullableString(
                        "assumptionId",
                        MAX_OPTION_ASSUMPTION_ID_LENGTH,
                        allowBlank = false,
                    )
                    "premiumModel" -> premiumModel = readOptionPremiumModelParameters()
                    "coveredCall" -> coveredCall =
                        readNullableObject("coveredCall", ::readCoveredCallStrategyTerms)
                    "optionIncome" -> optionIncome =
                        readNullableObject("optionIncome", ::readOptionIncomeStrategyTerms)
                    "bufferedPutSpread" -> bufferedPutSpread =
                        readNullableObject("bufferedPutSpread", ::readBufferedPutSpreadStrategyTerms)
                }
            }
            return OptionStrategyTerms(
                productId = productId ?: missing("productId"),
                reference = reference ?: missing("reference"),
                directReferenceTerminationRule = directReferenceTerminationRule,
                kind = kind ?: missing("kind"),
                tenorTradingDays = tenorTradingDays ?: missing("tenorTradingDays"),
                rollCalendar = rollCalendar ?: missing("rollCalendar"),
                rollLeadTradingDays = rollLeadTradingDays ?: missing("rollLeadTradingDays"),
                provenance = provenance ?: missing("provenance"),
                officialSourceUrls = officialSourceUrls ?: missing("officialSourceUrls"),
                assumptionId = assumptionId,
                premiumModel = premiumModel ?: missing("premiumModel"),
                coveredCall = coveredCall,
                optionIncome = optionIncome,
                bufferedPutSpread = bufferedPutSpread,
            )
        }

        private fun readOptionPremiumModelParameters(): OptionPremiumModelParameters {
            var impliedVolatilityMultiplier: Double? = null
            var soldPremiumCaptureRatio: Double? = null
            var purchasedPremiumCostRatio: Double? = null
            var implementationCostRatePerRoll: Double? = null
            var origin: OptionPremiumModelParameterOrigin? = null
            var sourceUrl: String? = null
            var calibrationId: String? = null
            readObject("premiumModel", OPTION_PREMIUM_MODEL_PARAMETER_FIELDS) { field ->
                when (field) {
                    "impliedVolatilityMultiplier" -> impliedVolatilityMultiplier =
                        readFiniteDoubleInRange("impliedVolatilityMultiplier", 0.25, 4.0)
                    "soldPremiumCaptureRatio" -> soldPremiumCaptureRatio =
                        readFiniteDoubleInRange("soldPremiumCaptureRatio", 0.0, 1.5)
                    "purchasedPremiumCostRatio" -> purchasedPremiumCostRatio =
                        readFiniteDoubleInRange("purchasedPremiumCostRatio", 0.5, 2.0)
                    "implementationCostRatePerRoll" -> implementationCostRatePerRoll =
                        readFiniteDoubleInRange("implementationCostRatePerRoll", 0.0, 0.10)
                    "origin" -> origin = readEnum<OptionPremiumModelParameterOrigin>("origin")
                    "sourceUrl" -> sourceUrl = readNullableHttpsUrl("sourceUrl")
                    "calibrationId" -> calibrationId = readNullableString(
                        "calibrationId",
                        MAX_OPTION_ASSUMPTION_ID_LENGTH,
                        allowBlank = false,
                    )
                }
            }
            return OptionPremiumModelParameters(
                impliedVolatilityMultiplier = impliedVolatilityMultiplier
                    ?: missing("impliedVolatilityMultiplier"),
                soldPremiumCaptureRatio = soldPremiumCaptureRatio
                    ?: missing("soldPremiumCaptureRatio"),
                purchasedPremiumCostRatio = purchasedPremiumCostRatio
                    ?: missing("purchasedPremiumCostRatio"),
                implementationCostRatePerRoll = implementationCostRatePerRoll
                    ?: missing("implementationCostRatePerRoll"),
                origin = origin ?: missing("origin"),
                sourceUrl = sourceUrl,
                calibrationId = calibrationId,
            )
        }

        private fun readCoveredCallStrategyTerms(): CoveredCallStrategyTerms {
            var overwriteRatio: Double? = null
            var callStrikeMoneyness: Double? = null
            readObject("coveredCall", COVERED_CALL_STRATEGY_TERMS_FIELDS) { field ->
                when (field) {
                    "overwriteRatio" -> overwriteRatio =
                        readFiniteDoubleInRange("overwriteRatio", MIN_POSITIVE_FRACTION, 1.0)
                    "callStrikeMoneyness" -> callStrikeMoneyness =
                        readFiniteDoubleInRange("callStrikeMoneyness", 0.50, 2.0)
                }
            }
            return CoveredCallStrategyTerms(
                overwriteRatio = overwriteRatio ?: missing("overwriteRatio"),
                callStrikeMoneyness = callStrikeMoneyness ?: missing("callStrikeMoneyness"),
            )
        }

        private fun readOptionIncomeStrategyTerms(): OptionIncomeStrategyTerms {
            var coreEquityAllocation: Double? = null
            var optionIncomeAllocation: Double? = null
            var upsideParticipation: Double? = null
            var downsideParticipation: Double? = null
            var callStrikeMoneyness: Double? = null
            readObject("optionIncome", OPTION_INCOME_STRATEGY_TERMS_FIELDS) { field ->
                when (field) {
                    "coreEquityAllocation" -> coreEquityAllocation =
                        readFiniteDoubleInRange("coreEquityAllocation", 0.0, 1.0)
                    "optionIncomeAllocation" -> optionIncomeAllocation =
                        readFiniteDoubleInRange("optionIncomeAllocation", MIN_POSITIVE_FRACTION, 1.0)
                    "upsideParticipation" -> upsideParticipation =
                        readFiniteDoubleInRange("upsideParticipation", 0.0, 1.0)
                    "downsideParticipation" -> downsideParticipation =
                        readFiniteDoubleInRange("downsideParticipation", 0.0, 1.0)
                    "callStrikeMoneyness" -> callStrikeMoneyness =
                        readFiniteDoubleInRange("callStrikeMoneyness", 1.0, 3.0)
                }
            }
            return OptionIncomeStrategyTerms(
                coreEquityAllocation = coreEquityAllocation ?: missing("coreEquityAllocation"),
                optionIncomeAllocation = optionIncomeAllocation ?: missing("optionIncomeAllocation"),
                upsideParticipation = upsideParticipation ?: missing("upsideParticipation"),
                downsideParticipation = downsideParticipation ?: missing("downsideParticipation"),
                callStrikeMoneyness = callStrikeMoneyness ?: missing("callStrikeMoneyness"),
            )
        }

        private fun readBufferedPutSpreadStrategyTerms(): BufferedPutSpreadStrategyTerms {
            var outcomeNotionalRatio: Double? = null
            var longPutStrikeMoneyness: Double? = null
            var downsideBufferFraction: Double? = null
            var downsideParticipationBeyondBuffer: Double? = null
            var upsideCapFraction: Double? = null
            readObject("bufferedPutSpread", BUFFERED_PUT_SPREAD_STRATEGY_TERMS_FIELDS) { field ->
                when (field) {
                    "outcomeNotionalRatio" -> outcomeNotionalRatio =
                        readFiniteDoubleInRange("outcomeNotionalRatio", MIN_POSITIVE_FRACTION, 1.0)
                    "longPutStrikeMoneyness" -> longPutStrikeMoneyness =
                        readFiniteDoubleInRange("longPutStrikeMoneyness", 0.50, 1.50)
                    "downsideBufferFraction" -> downsideBufferFraction =
                        readFiniteDoubleInRange("downsideBufferFraction", 0.001, 0.95)
                    "downsideParticipationBeyondBuffer" -> downsideParticipationBeyondBuffer =
                        readFiniteDoubleInRange("downsideParticipationBeyondBuffer", 0.0, 1.0)
                    "upsideCapFraction" -> upsideCapFraction =
                        readFiniteDoubleInRange("upsideCapFraction", 0.001, 2.0)
                }
            }
            return BufferedPutSpreadStrategyTerms(
                outcomeNotionalRatio = outcomeNotionalRatio ?: missing("outcomeNotionalRatio"),
                longPutStrikeMoneyness = longPutStrikeMoneyness
                    ?: missing("longPutStrikeMoneyness"),
                downsideBufferFraction = downsideBufferFraction ?: missing("downsideBufferFraction"),
                downsideParticipationBeyondBuffer = downsideParticipationBeyondBuffer
                    ?: missing("downsideParticipationBeyondBuffer"),
                upsideCapFraction = upsideCapFraction ?: missing("upsideCapFraction"),
            )
        }

        private fun readEtnProductTerms(): EtnProductTerms {
            var productId: String? = null
            var referenceId: String? = null
            var issuerId: String? = null
            var settlementCurrency: ReferenceCurrency? = null
            var statedPrincipalPerNote: Double? = null
            var annualInvestorFeeRate: Double? = null
            var investorFeeDayCountBasis: Int? = null
            var issueDate: LocalDate? = null
            var maturityDate: LocalDate? = null
            var maturityValuationRule: EtnSettlementValuationRule? = null
            var maturitySettlementMultiplier: Double? = null
            var maturityIncludesAccruedCoupon: Boolean? = null
            var couponRule: EtnCouponRule? = null
            var callTerms: EtnCallTerms? = null
            var accelerationTerms: EtnAccelerationTerms? = null
            var termsProvenance: FundStructureTermsProvenance? = null
            var officialSourceUrl: String? = null

            readObject("etnProductTerms", ETN_PRODUCT_TERMS_FIELDS) { field ->
                when (field) {
                    "productId" -> productId = readFundStructureId("productId")
                    "referenceId" -> referenceId = readFundStructureId("referenceId")
                    "issuerId" -> issuerId = readFundStructureId("issuerId")
                    "settlementCurrency" -> settlementCurrency =
                        readEnum<ReferenceCurrency>("settlementCurrency")
                    "statedPrincipalPerNote" -> statedPrincipalPerNote = readFiniteDoubleInRange(
                        "statedPrincipalPerNote",
                        MIN_POSITIVE_FUND_STRUCTURE_VALUE,
                        MAX_FUND_STRUCTURE_VALUE,
                    )
                    "annualInvestorFeeRate" -> annualInvestorFeeRate =
                        readFiniteDoubleInRange("annualInvestorFeeRate", 0.0, MAX_FUND_STRUCTURE_RATE)
                    "investorFeeDayCountBasis" -> investorFeeDayCountBasis =
                        readExactIntInRange("investorFeeDayCountBasis", 1, 366)
                    "issueDate" -> issueDate = readLocalDate("issueDate")
                    "maturityDate" -> maturityDate = readLocalDate("maturityDate")
                    "maturityValuationRule" -> maturityValuationRule =
                        readEtnSettlementValuationRule("maturityValuationRule")
                    "maturitySettlementMultiplier" -> maturitySettlementMultiplier =
                        readFiniteDoubleInRange(
                            "maturitySettlementMultiplier",
                            MIN_POSITIVE_FUND_STRUCTURE_VALUE,
                            MAX_FUND_STRUCTURE_RATE,
                        )
                    "maturityIncludesAccruedCoupon" -> maturityIncludesAccruedCoupon =
                        readBoolean("maturityIncludesAccruedCoupon")
                    "couponRule" -> couponRule = readEtnCouponRule()
                    "callTerms" -> callTerms = readEtnCallTerms()
                    "accelerationTerms" -> accelerationTerms = readEtnAccelerationTerms()
                    "termsProvenance" -> termsProvenance =
                        readEnum<FundStructureTermsProvenance>("termsProvenance")
                    "officialSourceUrl" -> officialSourceUrl = readNullableHttpsUrl("officialSourceUrl")
                }
            }
            return EtnProductTerms(
                productId = productId ?: missing("productId"),
                referenceId = referenceId ?: missing("referenceId"),
                issuerId = issuerId ?: missing("issuerId"),
                settlementCurrency = settlementCurrency ?: missing("settlementCurrency"),
                statedPrincipalPerNote = statedPrincipalPerNote ?: missing("statedPrincipalPerNote"),
                annualInvestorFeeRate = annualInvestorFeeRate ?: missing("annualInvestorFeeRate"),
                investorFeeDayCountBasis = investorFeeDayCountBasis
                    ?: missing("investorFeeDayCountBasis"),
                issueDate = issueDate ?: missing("issueDate"),
                maturityDate = maturityDate ?: missing("maturityDate"),
                maturityValuationRule = maturityValuationRule ?: missing("maturityValuationRule"),
                maturitySettlementMultiplier = maturitySettlementMultiplier
                    ?: missing("maturitySettlementMultiplier"),
                maturityIncludesAccruedCoupon = maturityIncludesAccruedCoupon
                    ?: missing("maturityIncludesAccruedCoupon"),
                couponRule = couponRule ?: missing("couponRule"),
                callTerms = callTerms ?: missing("callTerms"),
                accelerationTerms = accelerationTerms ?: missing("accelerationTerms"),
                termsProvenance = termsProvenance ?: missing("termsProvenance"),
                officialSourceUrl = officialSourceUrl,
            )
        }

        private fun readEtnIssuerCreditModelParameters(): EtnIssuerCreditModelParameters {
            var issuerId: String? = null
            var initialCreditSpread: Double? = null
            var initialHazardRate: Double? = null
            var recoveryRate: Double? = null
            var annualSpreadMeanReversionRate: Double? = null
            var spreadShockAnnualVolatility: Double? = null
            var origin: FundStructureModelParameterOrigin? = null
            var sourceUrl: String? = null
            readObject(
                "etnIssuerCreditModelParameters",
                ETN_ISSUER_CREDIT_MODEL_PARAMETER_FIELDS,
            ) { field ->
                when (field) {
                    "issuerId" -> issuerId = readFundStructureId("issuerId")
                    "initialCreditSpread" -> initialCreditSpread = readFiniteDoubleInRange(
                        "initialCreditSpread",
                        0.0,
                        EtnIssuerCreditModelParameters.MAX_CREDIT_SPREAD,
                    )
                    "initialHazardRate" -> initialHazardRate = readFiniteDoubleInRange(
                        "initialHazardRate",
                        0.0,
                        EtnIssuerCreditModelParameters.MAX_HAZARD_RATE,
                    )
                    "recoveryRate" -> recoveryRate =
                        readFiniteDoubleInRange("recoveryRate", 0.0, 1.0)
                    "annualSpreadMeanReversionRate" -> annualSpreadMeanReversionRate =
                        readFiniteDoubleInRange(
                            "annualSpreadMeanReversionRate",
                            0.0,
                            EtnIssuerCreditModelParameters.MAX_MEAN_REVERSION_RATE,
                        )
                    "spreadShockAnnualVolatility" -> spreadShockAnnualVolatility =
                        readFiniteDoubleInRange(
                            "spreadShockAnnualVolatility",
                            0.0,
                            EtnIssuerCreditModelParameters.MAX_SPREAD_VOLATILITY,
                        )
                    "origin" -> origin = readEnum<FundStructureModelParameterOrigin>("origin")
                    "sourceUrl" -> sourceUrl = readNullableHttpsUrl("sourceUrl")
                }
            }
            return EtnIssuerCreditModelParameters(
                issuerId = issuerId ?: missing("issuerId"),
                initialCreditSpread = initialCreditSpread ?: missing("initialCreditSpread"),
                initialHazardRate = initialHazardRate ?: missing("initialHazardRate"),
                recoveryRate = recoveryRate ?: missing("recoveryRate"),
                annualSpreadMeanReversionRate = annualSpreadMeanReversionRate
                    ?: missing("annualSpreadMeanReversionRate"),
                spreadShockAnnualVolatility = spreadShockAnnualVolatility
                    ?: missing("spreadShockAnnualVolatility"),
                origin = origin ?: missing("origin"),
                sourceUrl = sourceUrl,
            )
        }

        private fun readEtnSettlementValuationRule(label: String): EtnSettlementValuationRule {
            var method: EtnSettlementValuationMethod? = null
            var observationCount: Int? = null
            readObject(label, ETN_SETTLEMENT_VALUATION_RULE_FIELDS) { field ->
                when (field) {
                    "method" -> method = readEnum<EtnSettlementValuationMethod>("method")
                    "observationCount" -> observationCount =
                        readExactIntInRange("observationCount", 1, MAX_ETN_SETTLEMENT_OBSERVATIONS)
                }
            }
            return EtnSettlementValuationRule(
                method = method ?: missing("method"),
                observationCount = observationCount ?: missing("observationCount"),
            )
        }

        private fun readEtnCouponRule(): EtnCouponRule {
            var kind: EtnCouponKind? = null
            var paymentFrequencyMonths: Int? = null
            var annualFixedRate: Double? = null
            var participationRate: Double? = null
            var accrualReducesIndicativeValue: Boolean? = null
            var accruedCouponPaidAtTermination: Boolean? = null
            readObject("couponRule", ETN_COUPON_RULE_FIELDS) { field ->
                when (field) {
                    "kind" -> kind = readEnum<EtnCouponKind>("kind")
                    "paymentFrequencyMonths" -> paymentFrequencyMonths =
                        readExactIntInRange("paymentFrequencyMonths", 0, 120)
                    "annualFixedRate" -> annualFixedRate =
                        readFiniteDoubleInRange("annualFixedRate", 0.0, MAX_FUND_STRUCTURE_RATE)
                    "participationRate" -> participationRate =
                        readFiniteDoubleInRange("participationRate", 0.0, MAX_FUND_STRUCTURE_RATE)
                    "accrualReducesIndicativeValue" -> accrualReducesIndicativeValue =
                        readBoolean("accrualReducesIndicativeValue")
                    "accruedCouponPaidAtTermination" -> accruedCouponPaidAtTermination =
                        readBoolean("accruedCouponPaidAtTermination")
                }
            }
            return EtnCouponRule(
                kind = kind ?: missing("kind"),
                paymentFrequencyMonths = paymentFrequencyMonths ?: missing("paymentFrequencyMonths"),
                annualFixedRate = annualFixedRate ?: missing("annualFixedRate"),
                participationRate = participationRate ?: missing("participationRate"),
                accrualReducesIndicativeValue = accrualReducesIndicativeValue
                    ?: missing("accrualReducesIndicativeValue"),
                accruedCouponPaidAtTermination = accruedCouponPaidAtTermination
                    ?: missing("accruedCouponPaidAtTermination"),
            )
        }

        private fun readEtnCallTerms(): EtnCallTerms {
            var issuerCallable: Boolean? = null
            var issuerCallMayBePartial: Boolean? = null
            var holderRedeemable: Boolean? = null
            var minimumHolderRedemptionNotes: Long? = null
            var holderRedemptionNoteIncrement: Long? = null
            var minimumNoticeBusinessDays: Int? = null
            var issuerCallValuationRule: EtnSettlementValuationRule? = null
            var holderRedemptionValuationRule: EtnSettlementValuationRule? = null
            var issuerCallSettlementMultiplier: Double? = null
            var holderRedemptionSettlementMultiplier: Double? = null
            var holderRedemptionChargeRate: Double? = null
            var includesAccruedCoupon: Boolean? = null
            readObject("callTerms", ETN_CALL_TERMS_FIELDS) { field ->
                when (field) {
                    "issuerCallable" -> issuerCallable = readBoolean("issuerCallable")
                    "issuerCallMayBePartial" -> issuerCallMayBePartial =
                        readBoolean("issuerCallMayBePartial")
                    "holderRedeemable" -> holderRedeemable = readBoolean("holderRedeemable")
                    "minimumHolderRedemptionNotes" -> minimumHolderRedemptionNotes =
                        readNullableExactLongInRange(
                            "minimumHolderRedemptionNotes",
                            1L,
                            MAX_EXACT_FUND_STRUCTURE_QUANTITY,
                        )
                    "holderRedemptionNoteIncrement" -> holderRedemptionNoteIncrement =
                        readNullableExactLongInRange(
                            "holderRedemptionNoteIncrement",
                            1L,
                            MAX_EXACT_FUND_STRUCTURE_QUANTITY,
                        )
                    "minimumNoticeBusinessDays" -> minimumNoticeBusinessDays =
                        readExactIntInRange("minimumNoticeBusinessDays", 0, 365)
                    "issuerCallValuationRule" -> issuerCallValuationRule = readNullableObject(
                        "issuerCallValuationRule",
                    ) { readEtnSettlementValuationRule("issuerCallValuationRule") }
                    "holderRedemptionValuationRule" -> holderRedemptionValuationRule =
                        readNullableObject("holderRedemptionValuationRule") {
                            readEtnSettlementValuationRule("holderRedemptionValuationRule")
                        }
                    "issuerCallSettlementMultiplier" -> issuerCallSettlementMultiplier =
                        readFiniteDoubleInRange(
                            "issuerCallSettlementMultiplier",
                            0.0,
                            MAX_FUND_STRUCTURE_RATE,
                        )
                    "holderRedemptionSettlementMultiplier" -> holderRedemptionSettlementMultiplier =
                        readFiniteDoubleInRange(
                            "holderRedemptionSettlementMultiplier",
                            0.0,
                            MAX_FUND_STRUCTURE_RATE,
                        )
                    "holderRedemptionChargeRate" -> holderRedemptionChargeRate =
                        readFiniteDoubleInRange("holderRedemptionChargeRate", 0.0, 1.0)
                    "includesAccruedCoupon" -> includesAccruedCoupon =
                        readBoolean("includesAccruedCoupon")
                }
            }
            return EtnCallTerms(
                issuerCallable = issuerCallable ?: missing("issuerCallable"),
                issuerCallMayBePartial = issuerCallMayBePartial ?: missing("issuerCallMayBePartial"),
                holderRedeemable = holderRedeemable ?: missing("holderRedeemable"),
                minimumHolderRedemptionNotes = minimumHolderRedemptionNotes,
                holderRedemptionNoteIncrement = holderRedemptionNoteIncrement,
                minimumNoticeBusinessDays = minimumNoticeBusinessDays
                    ?: missing("minimumNoticeBusinessDays"),
                issuerCallValuationRule = issuerCallValuationRule,
                holderRedemptionValuationRule = holderRedemptionValuationRule,
                issuerCallSettlementMultiplier = issuerCallSettlementMultiplier
                    ?: missing("issuerCallSettlementMultiplier"),
                holderRedemptionSettlementMultiplier = holderRedemptionSettlementMultiplier
                    ?: missing("holderRedemptionSettlementMultiplier"),
                holderRedemptionChargeRate = holderRedemptionChargeRate
                    ?: missing("holderRedemptionChargeRate"),
                includesAccruedCoupon = includesAccruedCoupon ?: missing("includesAccruedCoupon"),
            )
        }

        private fun readEtnAccelerationTerms(): EtnAccelerationTerms {
            var issuerMayAccelerate: Boolean? = null
            var partialAccelerationAllowed: Boolean? = null
            var minimumPartialAccelerationNotes: Long? = null
            var partialAccelerationNoteIncrement: Long? = null
            var creditDefaultCausesAcceleration: Boolean? = null
            var fullAccelerationValuationRule: EtnSettlementValuationRule? = null
            var partialAccelerationValuationRule: EtnSettlementValuationRule? = null
            var accelerationSettlementMultiplier: Double? = null
            var nonCreditAccelerationIncludesAccruedCoupon: Boolean? = null
            var creditDefaultIncludesAccruedCouponBeforeRecovery: Boolean? = null
            readObject("accelerationTerms", ETN_ACCELERATION_TERMS_FIELDS) { field ->
                when (field) {
                    "issuerMayAccelerate" -> issuerMayAccelerate = readBoolean("issuerMayAccelerate")
                    "partialAccelerationAllowed" -> partialAccelerationAllowed =
                        readBoolean("partialAccelerationAllowed")
                    "minimumPartialAccelerationNotes" -> minimumPartialAccelerationNotes =
                        readNullableExactLongInRange(
                            "minimumPartialAccelerationNotes",
                            1L,
                            MAX_EXACT_FUND_STRUCTURE_QUANTITY,
                        )
                    "partialAccelerationNoteIncrement" -> partialAccelerationNoteIncrement =
                        readNullableExactLongInRange(
                            "partialAccelerationNoteIncrement",
                            1L,
                            MAX_EXACT_FUND_STRUCTURE_QUANTITY,
                        )
                    "creditDefaultCausesAcceleration" -> creditDefaultCausesAcceleration =
                        readBoolean("creditDefaultCausesAcceleration")
                    "fullAccelerationValuationRule" -> fullAccelerationValuationRule =
                        readNullableObject("fullAccelerationValuationRule") {
                            readEtnSettlementValuationRule("fullAccelerationValuationRule")
                        }
                    "partialAccelerationValuationRule" -> partialAccelerationValuationRule =
                        readNullableObject("partialAccelerationValuationRule") {
                            readEtnSettlementValuationRule("partialAccelerationValuationRule")
                        }
                    "accelerationSettlementMultiplier" -> accelerationSettlementMultiplier =
                        readFiniteDoubleInRange(
                            "accelerationSettlementMultiplier",
                            0.0,
                            MAX_FUND_STRUCTURE_RATE,
                        )
                    "nonCreditAccelerationIncludesAccruedCoupon" ->
                        nonCreditAccelerationIncludesAccruedCoupon =
                            readBoolean("nonCreditAccelerationIncludesAccruedCoupon")
                    "creditDefaultIncludesAccruedCouponBeforeRecovery" ->
                        creditDefaultIncludesAccruedCouponBeforeRecovery =
                            readBoolean("creditDefaultIncludesAccruedCouponBeforeRecovery")
                }
            }
            return EtnAccelerationTerms(
                issuerMayAccelerate = issuerMayAccelerate ?: missing("issuerMayAccelerate"),
                partialAccelerationAllowed = partialAccelerationAllowed
                    ?: missing("partialAccelerationAllowed"),
                minimumPartialAccelerationNotes = minimumPartialAccelerationNotes,
                partialAccelerationNoteIncrement = partialAccelerationNoteIncrement,
                creditDefaultCausesAcceleration = creditDefaultCausesAcceleration
                    ?: missing("creditDefaultCausesAcceleration"),
                fullAccelerationValuationRule = fullAccelerationValuationRule,
                partialAccelerationValuationRule = partialAccelerationValuationRule,
                accelerationSettlementMultiplier = accelerationSettlementMultiplier
                    ?: missing("accelerationSettlementMultiplier"),
                nonCreditAccelerationIncludesAccruedCoupon = nonCreditAccelerationIncludesAccruedCoupon
                    ?: missing("nonCreditAccelerationIncludesAccruedCoupon"),
                creditDefaultIncludesAccruedCouponBeforeRecovery =
                    creditDefaultIncludesAccruedCouponBeforeRecovery
                        ?: missing("creditDefaultIncludesAccruedCouponBeforeRecovery"),
            )
        }

        private fun readClosedEndFundTerms(): ClosedEndFundTerms {
            var fundId: String? = null
            var settlementCurrency: ReferenceCurrency? = null
            var distributionPolicy: ClosedEndFundDistributionPolicy? = null
            var allowsTenderOffers: Boolean? = null
            var allowsShareRepurchases: Boolean? = null
            var allowsRightsOfferings: Boolean? = null
            var allowsAtTheMarketOfferings: Boolean? = null
            var allowsDebtLeverage: Boolean? = null
            var allowsPreferredLeverage: Boolean? = null
            var minimumDebtAssetCoverageRatio: Double? = null
            var minimumPreferredAssetCoverageRatio: Double? = null
            var termsProvenance: FundStructureTermsProvenance? = null
            var officialSourceUrl: String? = null
            readObject("closedEndFundTerms", CLOSED_END_FUND_TERMS_FIELDS) { field ->
                when (field) {
                    "fundId" -> fundId = readFundStructureId("fundId")
                    "settlementCurrency" -> settlementCurrency =
                        readEnum<ReferenceCurrency>("settlementCurrency")
                    "distributionPolicy" -> distributionPolicy =
                        readEnum<ClosedEndFundDistributionPolicy>("distributionPolicy")
                    "allowsTenderOffers" -> allowsTenderOffers = readBoolean("allowsTenderOffers")
                    "allowsShareRepurchases" -> allowsShareRepurchases =
                        readBoolean("allowsShareRepurchases")
                    "allowsRightsOfferings" -> allowsRightsOfferings =
                        readBoolean("allowsRightsOfferings")
                    "allowsAtTheMarketOfferings" -> allowsAtTheMarketOfferings =
                        readBoolean("allowsAtTheMarketOfferings")
                    "allowsDebtLeverage" -> allowsDebtLeverage = readBoolean("allowsDebtLeverage")
                    "allowsPreferredLeverage" -> allowsPreferredLeverage =
                        readBoolean("allowsPreferredLeverage")
                    "minimumDebtAssetCoverageRatio" -> minimumDebtAssetCoverageRatio =
                        readNullableFiniteDoubleInRange(
                            "minimumDebtAssetCoverageRatio",
                            1.0,
                            MAX_CEF_ASSET_COVERAGE_RATIO,
                        )
                    "minimumPreferredAssetCoverageRatio" -> minimumPreferredAssetCoverageRatio =
                        readNullableFiniteDoubleInRange(
                            "minimumPreferredAssetCoverageRatio",
                            1.0,
                            MAX_CEF_ASSET_COVERAGE_RATIO,
                        )
                    "termsProvenance" -> termsProvenance =
                        readEnum<FundStructureTermsProvenance>("termsProvenance")
                    "officialSourceUrl" -> officialSourceUrl = readNullableHttpsUrl("officialSourceUrl")
                }
            }
            return ClosedEndFundTerms(
                fundId = fundId ?: missing("fundId"),
                settlementCurrency = settlementCurrency ?: missing("settlementCurrency"),
                distributionPolicy = distributionPolicy ?: missing("distributionPolicy"),
                allowsTenderOffers = allowsTenderOffers ?: missing("allowsTenderOffers"),
                allowsShareRepurchases = allowsShareRepurchases ?: missing("allowsShareRepurchases"),
                allowsRightsOfferings = allowsRightsOfferings ?: missing("allowsRightsOfferings"),
                allowsAtTheMarketOfferings = allowsAtTheMarketOfferings
                    ?: missing("allowsAtTheMarketOfferings"),
                allowsDebtLeverage = allowsDebtLeverage ?: missing("allowsDebtLeverage"),
                allowsPreferredLeverage = allowsPreferredLeverage
                    ?: missing("allowsPreferredLeverage"),
                minimumDebtAssetCoverageRatio = minimumDebtAssetCoverageRatio,
                minimumPreferredAssetCoverageRatio = minimumPreferredAssetCoverageRatio,
                termsProvenance = termsProvenance ?: missing("termsProvenance"),
                officialSourceUrl = officialSourceUrl,
            )
        }

        private fun readClosedEndFundMarketModelParameters(): ClosedEndFundMarketModelParameters {
            var fundId: String? = null
            var targetMarketDiscountRate: Double? = null
            var annualDiscountMeanReversionRate: Double? = null
            var initialDebtToGrossAssets: Double? = null
            var initialPreferredToGrossAssets: Double? = null
            var annualBorrowingSpread: Double? = null
            var annualPreferredDistributionSpread: Double? = null
            var discountShockAnnualVolatility: Double? = null
            var origin: FundStructureModelParameterOrigin? = null
            var sourceUrl: String? = null
            readObject(
                "closedEndFundMarketModelParameters",
                CLOSED_END_FUND_MARKET_MODEL_PARAMETER_FIELDS,
            ) { field ->
                when (field) {
                    "fundId" -> fundId = readFundStructureId("fundId")
                    "targetMarketDiscountRate" -> targetMarketDiscountRate =
                        readFiniteDoubleInRange(
                            "targetMarketDiscountRate",
                            -0.99,
                            MAX_FUND_STRUCTURE_RATE,
                        )
                    "annualDiscountMeanReversionRate" -> annualDiscountMeanReversionRate =
                        readFiniteDoubleInRange(
                            "annualDiscountMeanReversionRate",
                            0.0,
                            MAX_FUND_STRUCTURE_RATE,
                        )
                    "initialDebtToGrossAssets" -> initialDebtToGrossAssets =
                        readFiniteDoubleInRange(
                            "initialDebtToGrossAssets",
                            0.0,
                            ClosedEndFundMarketModelParameters.MAX_LEVERAGE_RATIO,
                        )
                    "initialPreferredToGrossAssets" -> initialPreferredToGrossAssets =
                        readFiniteDoubleInRange(
                            "initialPreferredToGrossAssets",
                            0.0,
                            ClosedEndFundMarketModelParameters.MAX_LEVERAGE_RATIO,
                        )
                    "annualBorrowingSpread" -> annualBorrowingSpread = readFiniteDoubleInRange(
                        "annualBorrowingSpread",
                        0.0,
                        ClosedEndFundMarketModelParameters.MAX_FINANCING_SPREAD,
                    )
                    "annualPreferredDistributionSpread" -> annualPreferredDistributionSpread =
                        readFiniteDoubleInRange(
                            "annualPreferredDistributionSpread",
                            0.0,
                            ClosedEndFundMarketModelParameters.MAX_FINANCING_SPREAD,
                        )
                    "discountShockAnnualVolatility" -> discountShockAnnualVolatility =
                        readFiniteDoubleInRange(
                            "discountShockAnnualVolatility",
                            0.0,
                            ClosedEndFundMarketModelParameters.MAX_DISCOUNT_VOLATILITY,
                        )
                    "origin" -> origin = readEnum<FundStructureModelParameterOrigin>("origin")
                    "sourceUrl" -> sourceUrl = readNullableHttpsUrl("sourceUrl")
                }
            }
            return ClosedEndFundMarketModelParameters(
                fundId = fundId ?: missing("fundId"),
                targetMarketDiscountRate = targetMarketDiscountRate
                    ?: missing("targetMarketDiscountRate"),
                annualDiscountMeanReversionRate = annualDiscountMeanReversionRate
                    ?: missing("annualDiscountMeanReversionRate"),
                initialDebtToGrossAssets = initialDebtToGrossAssets
                    ?: missing("initialDebtToGrossAssets"),
                initialPreferredToGrossAssets = initialPreferredToGrossAssets
                    ?: missing("initialPreferredToGrossAssets"),
                annualBorrowingSpread = annualBorrowingSpread ?: missing("annualBorrowingSpread"),
                annualPreferredDistributionSpread = annualPreferredDistributionSpread
                    ?: missing("annualPreferredDistributionSpread"),
                discountShockAnnualVolatility = discountShockAnnualVolatility
                    ?: missing("discountShockAnnualVolatility"),
                origin = origin ?: missing("origin"),
                sourceUrl = sourceUrl,
            )
        }

        private fun readDailyResetTerms(): DailyResetTerms {
            var productId: String? = null
            var reference: DailyResetReference? = null
            var directReferenceTerminationRule: DirectReferenceTerminationRule? = null
            var targetLeverage: Double? = null
            var resetCalendar: DailyResetCalendar? = null
            var provenance: DailyResetTermsProvenance? = null
            var officialSourceUrl: String? = null
            var modelParameters: DailyResetModelParameters? = null
            readObject("dailyResetTerms", DAILY_RESET_TERMS_FIELDS) { field ->
                when (field) {
                    "productId" -> productId =
                        readString("productId", MAX_FUND_PRODUCT_ID_LENGTH, allowBlank = false)
                    "reference" -> reference = readDailyResetReference()
                    "directReferenceTerminationRule" -> directReferenceTerminationRule =
                        readNullableObject(
                            "directReferenceTerminationRule",
                            ::readDirectReferenceTerminationRule,
                        )
                    "targetLeverage" -> targetLeverage =
                        readFiniteDoubleInRange("targetLeverage", -MAX_DAILY_RESET_LEVERAGE, MAX_DAILY_RESET_LEVERAGE)
                    "resetCalendar" -> resetCalendar = readEnum<DailyResetCalendar>("resetCalendar")
                    "provenance" -> provenance = readEnum<DailyResetTermsProvenance>("provenance")
                    "officialSourceUrl" -> officialSourceUrl = readNullableHttpsUrl("officialSourceUrl")
                    "modelParameters" -> modelParameters = readDailyResetModelParameters()
                }
            }
            return DailyResetTerms(
                productId = productId ?: missing("productId"),
                reference = reference ?: missing("reference"),
                directReferenceTerminationRule = directReferenceTerminationRule,
                targetLeverage = targetLeverage ?: missing("targetLeverage"),
                resetCalendar = resetCalendar ?: missing("resetCalendar"),
                provenance = provenance ?: missing("provenance"),
                officialSourceUrl = officialSourceUrl,
                modelParameters = modelParameters ?: missing("modelParameters"),
            )
        }

        private fun readDirectReferenceTerminationRule(): DirectReferenceTerminationRule {
            var policy: DirectReferenceTerminationPolicy? = null
            var provenance: DirectReferenceTerminationRuleProvenance? = null
            var officialSourceUrls: Set<String>? = null
            var assumptionId: String? = null
            readObject(
                "directReferenceTerminationRule",
                DIRECT_REFERENCE_TERMINATION_RULE_FIELDS,
            ) { field ->
                when (field) {
                    "policy" -> policy = readEnum<DirectReferenceTerminationPolicy>("policy")
                    "provenance" -> provenance =
                        readEnum<DirectReferenceTerminationRuleProvenance>("provenance")
                    "officialSourceUrls" -> officialSourceUrls = readSortedUniqueHttpsUrlSet(
                        "officialSourceUrls",
                        DirectReferenceTerminationRule.MAX_OFFICIAL_SOURCE_URLS,
                    )
                    "assumptionId" -> assumptionId = readNullableString(
                        "assumptionId",
                        MAX_OPTION_ASSUMPTION_ID_LENGTH,
                        allowBlank = false,
                    )
                }
            }
            return DirectReferenceTerminationRule(
                policy = policy ?: missing("policy"),
                provenance = provenance ?: missing("provenance"),
                officialSourceUrls = officialSourceUrls ?: missing("officialSourceUrls"),
                assumptionId = assumptionId,
            )
        }

        private fun readDailyResetReference(): DailyResetReference {
            var kind: DailyResetReferenceKind? = null
            var benchmarkRef: BenchmarkRef? = null
            var instrumentId: String? = null
            readObject("reference", DAILY_RESET_REFERENCE_FIELDS) { field ->
                when (field) {
                    "kind" -> kind = readEnum<DailyResetReferenceKind>("kind")
                    "benchmarkRef" -> benchmarkRef =
                        readNullableObject("benchmarkRef") { readBenchmarkRef("benchmarkRef") }
                    "instrumentId" -> instrumentId = readNullableString(
                        "instrumentId",
                        MAX_FUND_PRODUCT_ID_LENGTH,
                        allowBlank = false,
                    )
                }
            }
            return DailyResetReference(
                kind = kind ?: missing("kind"),
                benchmarkRef = benchmarkRef,
                instrumentId = instrumentId,
            )
        }

        private fun readDailyResetModelParameters(): DailyResetModelParameters {
            var annualFinancingSpread: Double? = null
            var collateralYieldParticipation: Double? = null
            var origin: DailyResetModelParameterOrigin? = null
            var sourceUrl: String? = null
            readObject("modelParameters", DAILY_RESET_MODEL_PARAMETER_FIELDS) { field ->
                when (field) {
                    "annualFinancingSpread" -> annualFinancingSpread =
                        readFiniteDoubleInRange("annualFinancingSpread", 0.0, MAX_DAILY_RESET_RATE)
                    "collateralYieldParticipation" -> collateralYieldParticipation =
                        readFiniteDoubleInRange("collateralYieldParticipation", 0.0, 1.5)
                    "origin" -> origin = readEnum<DailyResetModelParameterOrigin>("origin")
                    "sourceUrl" -> sourceUrl = readNullableHttpsUrl("sourceUrl")
                }
            }
            return DailyResetModelParameters(
                annualFinancingSpread = annualFinancingSpread ?: missing("annualFinancingSpread"),
                collateralYieldParticipation = collateralYieldParticipation
                    ?: missing("collateralYieldParticipation"),
                origin = origin ?: missing("origin"),
                sourceUrl = sourceUrl,
            )
        }

        private fun readEtfProfile(): EtfProfile {
            var benchmark: String? = null
            var assetClass: EtfAssetClass? = null
            var taxCategory: EtfTaxCategory? = null
            var annualExpenseRatio: Double? = null
            var fxProfile: EtfFxProfile? = null
            var leverage: Double? = null
            var taxablePriceGainRatio: Double? = null
            var exposureRegion: EtfExposureRegion? = null
            readObject("etfProfile", ETF_PROFILE_FIELDS) { field ->
                when (field) {
                    "benchmark" -> benchmark = readString("benchmark", MAX_BENCHMARK_LENGTH, allowBlank = false)
                    "assetClass" -> assetClass = readEnum<EtfAssetClass>("assetClass")
                    "taxCategory" -> taxCategory = readEnum<EtfTaxCategory>("taxCategory")
                    "annualExpenseRatio" -> annualExpenseRatio = readFiniteDouble("annualExpenseRatio")
                    "fxProfile" -> fxProfile = readEtfFxProfile()
                    "leverage" -> leverage = readFiniteDouble("leverage")
                    "taxablePriceGainRatio" -> taxablePriceGainRatio = readFiniteDouble("taxablePriceGainRatio")
                    "exposureRegion" -> exposureRegion = readEnum<EtfExposureRegion>("exposureRegion")
                }
            }
            return EtfProfile(
                benchmark = benchmark ?: missing("benchmark"),
                assetClass = assetClass ?: missing("assetClass"),
                taxCategory = taxCategory ?: missing("taxCategory"),
                annualExpenseRatio = annualExpenseRatio ?: missing("annualExpenseRatio"),
                fxProfile = fxProfile ?: missing("fxProfile"),
                leverage = leverage ?: missing("leverage"),
                taxablePriceGainRatio = taxablePriceGainRatio ?: missing("taxablePriceGainRatio"),
                exposureRegion = exposureRegion ?: missing("exposureRegion"),
            )
        }

        private fun readEtfFxProfile(): EtfFxProfile {
            var legs: List<CurrencyExposureLeg>? = null
            var annualHedgeCostRate: Double? = null
            readObject("fxProfile", ETF_FX_PROFILE_FIELDS) { field ->
                when (field) {
                    "legs" -> legs = readCurrencyExposureLegs()
                    "annualHedgeCostRate" -> annualHedgeCostRate = readFiniteDouble("annualHedgeCostRate")
                }
            }
            return EtfFxProfile(
                legs = legs ?: missing("legs"),
                annualHedgeCostRate = annualHedgeCostRate ?: missing("annualHedgeCostRate"),
            )
        }

        private fun readCurrencyExposureLegs(): List<CurrencyExposureLeg> {
            beginArray("legs")
            val legs = ArrayList<CurrencyExposureLeg>()
            val currencies = HashSet<ReferenceCurrency>()
            while (reader.hasNext()) {
                if (legs.size >= ReferenceCurrency.entries.size) {
                    fail("legs 배열 항목이 너무 많습니다.")
                }
                val leg = readCurrencyExposureLeg()
                if (!currencies.add(leg.currency)) {
                    fail("legs 배열에 중복된 currency가 있습니다.")
                }
                legs += leg
            }
            endArray()
            return legs
        }

        private fun readCurrencyExposureLeg(): CurrencyExposureLeg {
            var currency: ReferenceCurrency? = null
            var grossNotional: Double? = null
            var hedgeRatioToListingCurrency: Double? = null
            readObject("통화 노출", CURRENCY_EXPOSURE_LEG_FIELDS) { field ->
                when (field) {
                    "currency" -> currency = readEnum<ReferenceCurrency>("currency")
                    "grossNotional" -> grossNotional = readFiniteDouble("grossNotional")
                    "hedgeRatioToListingCurrency" -> hedgeRatioToListingCurrency =
                        readFiniteDouble("hedgeRatioToListingCurrency")
                }
            }
            return CurrencyExposureLeg(
                currency = currency ?: missing("currency"),
                grossNotional = grossNotional ?: missing("grossNotional"),
                hedgeRatioToListingCurrency = hedgeRatioToListingCurrency
                    ?: missing("hedgeRatioToListingCurrency"),
            )
        }

        private fun readBehaviorProfile(): InstrumentBehaviorProfile {
            var strategy: InstrumentStrategy? = null
            var distributionFrequency: DistributionFrequency? = null
            var upsideParticipation: Double? = null
            var downsideParticipation: Double? = null
            var durationYears: Double? = null
            var creditSpreadSensitivity: Double? = null
            var cashRateAccrual: Double? = null
            var annualStructuralDrag: Double? = null
            var distributionCoverageRatio: Double? = null
            var priceDislocationVolatility: Double? = null
            var referenceCurrency: ReferenceCurrency? = null
            var referenceCurrencySensitivity: Double? = null
            var commodityFactorSensitivity: Double? = null
            var cryptoFactorSensitivity: Double? = null
            var principalRisk: PrincipalRisk? = null
            readObject("behaviorProfile", BEHAVIOR_PROFILE_FIELDS) { field ->
                when (field) {
                    "strategy" -> strategy = readEnum<InstrumentStrategy>("strategy")
                    "distributionFrequency" -> distributionFrequency =
                        readEnum<DistributionFrequency>("distributionFrequency")
                    "upsideParticipation" -> upsideParticipation = readFiniteDouble("upsideParticipation")
                    "downsideParticipation" -> downsideParticipation = readFiniteDouble("downsideParticipation")
                    "durationYears" -> durationYears = readFiniteDouble("durationYears")
                    "creditSpreadSensitivity" -> creditSpreadSensitivity =
                        readFiniteDouble("creditSpreadSensitivity")
                    "cashRateAccrual" -> cashRateAccrual = readFiniteDouble("cashRateAccrual")
                    "annualStructuralDrag" -> annualStructuralDrag = readFiniteDouble("annualStructuralDrag")
                    "distributionCoverageRatio" -> distributionCoverageRatio =
                        readFiniteDouble("distributionCoverageRatio")
                    "priceDislocationVolatility" -> priceDislocationVolatility =
                        readFiniteDouble("priceDislocationVolatility")
                    "referenceCurrency" -> referenceCurrency = readNullableEnum<ReferenceCurrency>("referenceCurrency")
                    "referenceCurrencySensitivity" -> referenceCurrencySensitivity =
                        readFiniteDouble("referenceCurrencySensitivity")
                    "commodityFactorSensitivity" -> commodityFactorSensitivity =
                        readFiniteDouble("commodityFactorSensitivity")
                    "cryptoFactorSensitivity" -> cryptoFactorSensitivity =
                        readFiniteDouble("cryptoFactorSensitivity")
                    "principalRisk" -> principalRisk = readEnum<PrincipalRisk>("principalRisk")
                }
            }
            return InstrumentBehaviorProfile(
                strategy = strategy ?: missing("strategy"),
                distributionFrequency = distributionFrequency ?: missing("distributionFrequency"),
                upsideParticipation = upsideParticipation ?: missing("upsideParticipation"),
                downsideParticipation = downsideParticipation ?: missing("downsideParticipation"),
                durationYears = durationYears ?: missing("durationYears"),
                creditSpreadSensitivity = creditSpreadSensitivity ?: missing("creditSpreadSensitivity"),
                cashRateAccrual = cashRateAccrual ?: missing("cashRateAccrual"),
                annualStructuralDrag = annualStructuralDrag ?: missing("annualStructuralDrag"),
                distributionCoverageRatio = distributionCoverageRatio ?: missing("distributionCoverageRatio"),
                priceDislocationVolatility = priceDislocationVolatility ?: missing("priceDislocationVolatility"),
                referenceCurrency = referenceCurrency,
                referenceCurrencySensitivity = referenceCurrencySensitivity
                    ?: missing("referenceCurrencySensitivity"),
                commodityFactorSensitivity = commodityFactorSensitivity
                    ?: missing("commodityFactorSensitivity"),
                cryptoFactorSensitivity = cryptoFactorSensitivity ?: missing("cryptoFactorSensitivity"),
                principalRisk = principalRisk ?: missing("principalRisk"),
            )
        }

        private fun readIdentityProfile(): InstrumentIdentityProfile {
            var aliases: Set<String>? = null
            var issuerOrManager: String? = null
            var strategySummary: String? = null
            var officialSourceUrl: String? = null
            var supportingSourceUrls: Set<String>? = null
            var eventRiskTags: Set<String>? = null
            var maturityDate: String? = null
            var callable: Boolean? = null
            var adrUnderlyingShareRatio: Double? = null
            var underlyingInstrumentIds: Set<String>? = null
            var exposedSectors: Set<Sector>? = null
            readObject("identityProfile", IDENTITY_PROFILE_FIELDS) { field ->
                when (field) {
                    "aliases" -> aliases = readUniqueStringSet(
                        label = "aliases",
                        maxItems = MAX_ALIASES,
                        maxStringLength = MAX_ALIAS_LENGTH,
                    )
                    "issuerOrManager" -> issuerOrManager =
                        readString("issuerOrManager", MAX_ISSUER_LENGTH, allowBlank = false)
                    "strategySummary" -> strategySummary =
                        readString("strategySummary", MAX_DESCRIPTION_LENGTH, allowBlank = false)
                    "officialSourceUrl" -> officialSourceUrl =
                        readString("officialSourceUrl", MAX_URL_LENGTH, allowBlank = false)
                    "supportingSourceUrls" -> supportingSourceUrls = readUniqueStringSet(
                        label = "supportingSourceUrls",
                        maxItems = MAX_SUPPORTING_SOURCE_URLS,
                        maxStringLength = MAX_URL_LENGTH,
                    )
                    "eventRiskTags" -> eventRiskTags = readUniqueStringSet(
                        label = "eventRiskTags",
                        maxItems = MAX_EVENT_RISK_TAGS,
                        maxStringLength = MAX_EVENT_RISK_TAG_LENGTH,
                    )
                    "maturityDate" -> maturityDate =
                        readNullableString("maturityDate", MAX_MATURITY_DATE_LENGTH, allowBlank = false)
                    "callable" -> callable = readBoolean("callable")
                    "adrUnderlyingShareRatio" -> adrUnderlyingShareRatio =
                        readNullableFiniteDouble("adrUnderlyingShareRatio")
                    "underlyingInstrumentIds" -> underlyingInstrumentIds = readUniqueStringSet(
                        label = "underlyingInstrumentIds",
                        maxItems = MAX_UNDERLYING_INSTRUMENTS,
                        maxStringLength = MAX_INSTRUMENT_ID_LENGTH,
                    )
                    "exposedSectors" -> exposedSectors = readUniqueEnumSet<Sector>(
                        label = "exposedSectors",
                        maxItems = Sector.entries.size,
                    )
                }
            }
            return InstrumentIdentityProfile(
                aliases = aliases ?: missing("aliases"),
                issuerOrManager = issuerOrManager ?: missing("issuerOrManager"),
                strategySummary = strategySummary ?: missing("strategySummary"),
                officialSourceUrl = officialSourceUrl ?: missing("officialSourceUrl"),
                supportingSourceUrls = supportingSourceUrls ?: missing("supportingSourceUrls"),
                eventRiskTags = eventRiskTags ?: missing("eventRiskTags"),
                maturityDate = maturityDate,
                callable = callable ?: missing("callable"),
                adrUnderlyingShareRatio = adrUnderlyingShareRatio,
                underlyingInstrumentIds = underlyingInstrumentIds ?: missing("underlyingInstrumentIds"),
                exposedSectors = exposedSectors ?: missing("exposedSectors"),
            )
        }

        private fun readObject(
            label: String,
            allowedFields: Set<String>,
            requiredFields: Set<String> = allowedFields,
            readField: (String) -> Unit,
        ) {
            require(requiredFields.all(allowedFields::contains)) {
                "$label 객체의 필수 필드는 허용 필드의 부분집합이어야 합니다."
            }
            beginObject(label)
            val seen = HashSet<String>(allowedFields.size)
            while (reader.hasNext()) {
                if (reader.peek() != JsonToken.NAME) fail("$label 객체의 필드 형식이 올바르지 않습니다.")
                val field = reader.nextName()
                countNode()
                if (field.length > MAX_FIELD_NAME_LENGTH) fail("$label 객체의 필드명이 너무 깁니다.")
                if (!seen.add(field)) fail("$label 객체에 중복된 '$field' 필드가 있습니다.")
                if (field !in allowedFields) fail("$label 객체에 알 수 없는 '$field' 필드가 있습니다.")
                readField(field)
            }
            endObject()
            val missing = requiredFields - seen
            if (missing.isNotEmpty()) {
                fail("$label 객체에 필수 필드가 누락되었습니다: ${missing.sorted().joinToString()}")
            }
        }

        private fun beginObject(label: String) {
            expect(JsonToken.BEGIN_OBJECT, "$label 객체")
            enterContainer()
            reader.beginObject()
        }

        private fun endObject() {
            reader.endObject()
            depth -= 1
        }

        private fun beginArray(label: String) {
            expect(JsonToken.BEGIN_ARRAY, "$label 배열")
            enterContainer()
            reader.beginArray()
        }

        private fun endArray() {
            reader.endArray()
            depth -= 1
        }

        private fun enterContainer() {
            countNode()
            depth += 1
            if (depth > MAX_JSON_DEPTH) fail("JSON 중첩 깊이가 $MAX_JSON_DEPTH 단계를 초과했습니다.")
        }

        private fun countNode() {
            nodeCount += 1
            if (nodeCount > MAX_JSON_NODES) fail("JSON 노드 수가 ${MAX_JSON_NODES}개를 초과했습니다.")
        }

        private fun expect(token: JsonToken, label: String) {
            if (reader.peek() != token) fail("$label 형식이 올바르지 않습니다.")
        }

        private fun readString(label: String, maxLength: Int, allowBlank: Boolean): String {
            expect(JsonToken.STRING, label)
            countNode()
            val value = reader.nextString()
            if (value.length > maxLength) fail("$label 문자열은 $maxLength 이하여야 합니다.")
            if (!allowBlank && value.isBlank()) fail("$label 문자열은 비어 있을 수 없습니다.")
            if (value.any(Char::isISOControl)) fail("$label 문자열에 제어 문자를 사용할 수 없습니다.")
            return value
        }

        private fun readNullableString(label: String, maxLength: Int, allowBlank: Boolean): String? {
            if (reader.peek() == JsonToken.NULL) {
                readNull()
                return null
            }
            return readString(label, maxLength, allowBlank)
        }

        private fun readBoolean(label: String): Boolean {
            expect(JsonToken.BOOLEAN, label)
            countNode()
            return reader.nextBoolean()
        }

        private fun readFiniteDouble(label: String): Double {
            val literal = readNumberLiteral(label)
            val value = literal.toDoubleOrNull() ?: fail("$label 숫자가 올바르지 않습니다.")
            if (!value.isFinite()) fail("$label 숫자는 유한한 Double이어야 합니다.")
            return value
        }

        private fun readFiniteDoubleInRange(
            label: String,
            minValue: Double,
            maxValue: Double,
        ): Double {
            val value = readFiniteDouble(label)
            if (value !in minValue..maxValue) {
                fail("$label 숫자는 $minValue 이상 $maxValue 이하여야 합니다.")
            }
            return value
        }

        private fun readNullableFiniteDouble(label: String): Double? {
            if (reader.peek() == JsonToken.NULL) {
                readNull()
                return null
            }
            return readFiniteDouble(label)
        }

        private fun readNullableFiniteDoubleInRange(
            label: String,
            minValue: Double,
            maxValue: Double,
        ): Double? {
            if (reader.peek() == JsonToken.NULL) {
                readNull()
                return null
            }
            return readFiniteDoubleInRange(label, minValue, maxValue)
        }

        private fun readExactLong(label: String): Long {
            val literal = readNumberLiteral(label)
            return try {
                BigDecimal(literal).longValueExact()
            } catch (_: ArithmeticException) {
                fail("$label 숫자는 Long으로 정확히 표현되어야 합니다.")
            } catch (_: NumberFormatException) {
                fail("$label 숫자가 올바르지 않습니다.")
            }
        }

        private fun readNullableExactLongInRange(
            label: String,
            minValue: Long,
            maxValue: Long,
        ): Long? {
            if (reader.peek() == JsonToken.NULL) {
                readNull()
                return null
            }
            val value = readExactLong(label)
            if (value !in minValue..maxValue) {
                fail("$label 숫자는 $minValue 이상 $maxValue 이하여야 합니다.")
            }
            return value
        }

        private fun readNullableExactIntInRange(
            label: String,
            minValue: Int,
            maxValue: Int,
        ): Int? {
            if (reader.peek() == JsonToken.NULL) {
                readNull()
                return null
            }
            return readExactIntInRange(label, minValue, maxValue)
        }

        private fun readExactInt(label: String): Int {
            val literal = readNumberLiteral(label)
            return try {
                BigDecimal(literal).intValueExact()
            } catch (_: ArithmeticException) {
                fail("$label 숫자는 Int로 정확히 표현되어야 합니다.")
            } catch (_: NumberFormatException) {
                fail("$label 숫자가 올바르지 않습니다.")
            }
        }

        private fun readExactIntInRange(
            label: String,
            minValue: Int,
            maxValue: Int,
        ): Int {
            val value = readExactInt(label)
            if (value !in minValue..maxValue) {
                fail("$label 숫자는 $minValue 이상 $maxValue 이하여야 합니다.")
            }
            return value
        }

        private fun readLocalDate(label: String): LocalDate {
            val literal = readString(label, MAX_LOCAL_DATE_LENGTH, allowBlank = false)
            val date = try {
                LocalDate.parse(literal)
            } catch (_: IllegalArgumentException) {
                fail("$label 날짜는 YYYY-MM-DD 형식의 유효한 날짜여야 합니다.")
            }
            if (date.toString() != literal) {
                fail("$label 날짜는 YYYY-MM-DD 형식이어야 합니다.")
            }
            return date
        }

        private fun readHttpsUrl(label: String): String {
            val literal = readString(label, MAX_URL_LENGTH, allowBlank = false)
            val uri = try {
                URI(literal)
            } catch (_: URISyntaxException) {
                fail("$label URL 형식이 올바르지 않습니다.")
            }
            if (
                uri.scheme != "https" ||
                uri.host.isNullOrBlank() ||
                uri.userInfo != null ||
                !uri.isAbsolute
            ) {
                fail("$label URL은 사용자 정보가 없는 절대 HTTPS URL이어야 합니다.")
            }
            return literal
        }

        private fun readFundStructureId(label: String): String =
            readString(label, MAX_FUND_STRUCTURE_ID_LENGTH, allowBlank = false)

        private fun readNullableHttpsUrl(label: String): String? {
            if (reader.peek() == JsonToken.NULL) {
                readNull()
                return null
            }
            return readHttpsUrl(label)
        }

        private fun readNumberLiteral(label: String): String {
            expect(JsonToken.NUMBER, label)
            countNode()
            val literal = reader.nextString()
            if (literal.length > MAX_NUMBER_LITERAL_LENGTH) fail("$label 숫자 표현이 너무 깁니다.")
            return literal
        }

        private inline fun <reified T : Enum<T>> readEnum(label: String): T {
            val value = readString(label, MAX_ENUM_LENGTH, allowBlank = false)
            return enumValues<T>().firstOrNull { it.name == value }
                ?: fail("$label enum 값이 올바르지 않습니다: $value")
        }

        private inline fun <reified T : Enum<T>> readNullableEnum(label: String): T? {
            if (reader.peek() == JsonToken.NULL) {
                readNull()
                return null
            }
            return readEnum<T>(label)
        }

        private inline fun <reified T : Enum<T>> readUniqueEnumSet(
            label: String,
            maxItems: Int,
        ): Set<T> {
            beginArray(label)
            val values = linkedSetOf<T>()
            while (reader.hasNext()) {
                if (values.size >= maxItems) fail("$label 배열 항목이 너무 많습니다.")
                val value = readEnum<T>(label)
                if (!values.add(value)) fail("$label 배열에 중복 값이 있습니다.")
            }
            endArray()
            return values.toSet()
        }

        private inline fun <reified T : Enum<T>> readSortedUniqueEnumSet(
            label: String,
            maxItems: Int,
            allowEmpty: Boolean = false,
        ): Set<T> {
            beginArray(label)
            val values = linkedSetOf<T>()
            var previousOrdinal = -1
            while (reader.hasNext()) {
                if (values.size >= maxItems) fail("$label 배열 항목이 너무 많습니다.")
                val value = readEnum<T>(label)
                if (!values.add(value)) fail("$label 배열에 중복 값이 있습니다.")
                if (value.ordinal <= previousOrdinal) fail("$label 배열은 enum 선언 순서로 정렬해야 합니다.")
                previousOrdinal = value.ordinal
            }
            endArray()
            if (!allowEmpty && values.isEmpty()) fail("$label 배열은 비어 있을 수 없습니다.")
            return values.toSet()
        }

        private fun readUniqueIntSet(
            label: String,
            maxItems: Int,
            minValue: Int,
            maxValue: Int,
        ): Set<Int> {
            beginArray(label)
            val values = linkedSetOf<Int>()
            while (reader.hasNext()) {
                if (values.size >= maxItems) fail("$label 배열 항목이 너무 많습니다.")
                val value = readExactIntInRange(label, minValue, maxValue)
                if (!values.add(value)) fail("$label 배열에 중복 값이 있습니다.")
            }
            endArray()
            if (values.isEmpty()) fail("$label 배열은 비어 있을 수 없습니다.")
            return values.toSet()
        }

        private fun readSortedUniqueIntSet(
            label: String,
            maxItems: Int,
            minValue: Int,
            maxValue: Int,
            allowEmpty: Boolean = false,
        ): Set<Int> {
            beginArray(label)
            val values = linkedSetOf<Int>()
            var previous: Int? = null
            while (reader.hasNext()) {
                if (values.size >= maxItems) fail("$label 배열 항목이 너무 많습니다.")
                val value = readExactIntInRange(label, minValue, maxValue)
                if (!values.add(value)) fail("$label 배열에 중복 값이 있습니다.")
                if (previous != null && value <= previous) fail("$label 배열은 오름차순으로 정렬해야 합니다.")
                previous = value
            }
            endArray()
            if (!allowEmpty && values.isEmpty()) fail("$label 배열은 비어 있을 수 없습니다.")
            return values.toSet()
        }

        private fun readSortedUniqueStringSet(
            label: String,
            maxItems: Int,
            maxStringLength: Int,
        ): Set<String> {
            beginArray(label)
            val values = linkedSetOf<String>()
            var previous: String? = null
            while (reader.hasNext()) {
                if (values.size >= maxItems) fail("$label 배열 항목이 너무 많습니다.")
                val value = readString(label, maxStringLength, allowBlank = false)
                if (!values.add(value)) fail("$label 배열에 중복 값이 있습니다.")
                if (previous != null && value <= previous) fail("$label 배열은 문자열 오름차순으로 정렬해야 합니다.")
                previous = value
            }
            endArray()
            return values.toSet()
        }

        private fun readSortedUniqueHttpsUrlSet(label: String, maxItems: Int): Set<String> {
            beginArray(label)
            val values = linkedSetOf<String>()
            var previous: String? = null
            while (reader.hasNext()) {
                if (values.size >= maxItems) fail("$label 배열 항목이 너무 많습니다.")
                val value = readHttpsUrl(label)
                if (!values.add(value)) fail("$label 배열에 중복 값이 있습니다.")
                if (previous != null && value <= previous) fail("$label 배열은 URL 오름차순으로 정렬해야 합니다.")
                previous = value
            }
            endArray()
            return values.toSet()
        }

        private fun readUniqueStringSet(
            label: String,
            maxItems: Int,
            maxStringLength: Int,
        ): Set<String> {
            beginArray(label)
            val values = linkedSetOf<String>()
            while (reader.hasNext()) {
                if (values.size >= maxItems) fail("$label 배열 항목이 너무 많습니다.")
                val value = readString(label, maxStringLength, allowBlank = false)
                if (!values.add(value)) fail("$label 배열에 중복 값이 있습니다.")
            }
            endArray()
            return values.toSet()
        }

        private fun <T> readNullableObject(label: String, readValue: () -> T): T? {
            if (reader.peek() == JsonToken.NULL) {
                readNull()
                return null
            }
            if (reader.peek() != JsonToken.BEGIN_OBJECT) fail("$label 객체 또는 null이 필요합니다.")
            return readValue()
        }

        private fun readNull() {
            expect(JsonToken.NULL, "null")
            countNode()
            reader.nextNull()
        }

        private fun missing(field: String): Nothing = fail("필수 필드 '$field'가 없습니다.")

        private fun fail(message: String): Nothing = throw IllegalArgumentException("$ERROR_PREFIX$message")

        private data class ParsedInstrument(
            val order: Int,
            val definition: StockDefinition,
        )

        data class ParsedDocument(
            val benchmarks: List<BenchmarkDefinition>,
            val definitions: List<StockDefinition>,
        )

        private companion object {
            const val SCHEMA_VERSION: Int = 2
            const val MAX_JSON_DEPTH: Int = 12
            const val MAX_JSON_NODES: Int = 750_000
            const val MAX_FIELD_NAME_LENGTH: Int = 64
            const val MAX_NUMBER_LITERAL_LENGTH: Int = 128
            const val MAX_ENUM_LENGTH: Int = 64
            const val MAX_LOCAL_DATE_LENGTH: Int = 10
            const val MAX_SYMBOL_LENGTH: Int = 32
            const val MAX_NAME_LENGTH: Int = 120
            const val MAX_ENGLISH_NAME_LENGTH: Int = 160
            const val MAX_DESCRIPTION_LENGTH: Int = 60
            const val MAX_BENCHMARK_LENGTH: Int = 240
            const val MAX_ALIAS_LENGTH: Int = 160
            const val MAX_ISSUER_LENGTH: Int = 160
            const val MAX_URL_LENGTH: Int = 2_048
            const val MAX_EVENT_RISK_TAG_LENGTH: Int = 120
            const val MAX_MATURITY_DATE_LENGTH: Int = 10
            const val MAX_INSTRUMENT_ID_LENGTH: Int = 128
            const val MAX_ALIASES: Int = 128
            const val MAX_SUPPORTING_SOURCE_URLS: Int = 32
            const val MAX_EVENT_RISK_TAGS: Int = 128
            const val MAX_UNDERLYING_INSTRUMENTS: Int = 128
            const val MAX_BASE_SHARES_OUTSTANDING: Long = 10_000_000_000_000L
            const val MAX_DIVIDEND_PAYMENT_YEARS: Int = 200
            const val MAX_METHODOLOGY_MARKET_CAP: Double = 1e20
            const val MAX_AVERAGE_DAILY_VALUE_TRADED: Double = 1e15
            const val MIN_POSITIVE_FRACTION: Double = 1e-12
            const val MONTHS_PER_YEAR: Int = 12
            const val MAX_FUND_PRODUCT_ID_LENGTH: Int = 200
            const val MAX_DAILY_RESET_LEVERAGE: Double = 5.0
            const val MAX_DAILY_RESET_RATE: Double = 1.0
            const val MAX_FUND_STRUCTURE_ID_LENGTH: Int = 256
            const val MAX_FUND_STRUCTURE_VALUE: Double = 1.0e18
            const val MIN_POSITIVE_FUND_STRUCTURE_VALUE: Double = 1.0e-9
            const val MAX_FUND_STRUCTURE_RATE: Double = 100.0
            const val MAX_EXACT_FUND_STRUCTURE_QUANTITY: Long = 9_000_000_000_000_000L
            const val MAX_ETN_SETTLEMENT_OBSERVATIONS: Int = 31
            const val MAX_CEF_ASSET_COVERAGE_RATIO: Double = 1_000.0
            const val MAX_OPTION_TENOR_TRADING_DAYS: Int = 504
            const val MAX_OPTION_OFFICIAL_SOURCE_URLS: Int = 16
            const val MAX_OPTION_ASSUMPTION_ID_LENGTH: Int = 160
            const val MAX_COMMODITY_OFFICIAL_SOURCE_URLS: Int = 16
            const val MAX_COMMODITY_ASSUMPTION_ID_LENGTH: Int = 160
            const val MAX_FUTURES_SLEEVES: Int = 128
            const val MAX_FUTURES_IDENTIFIER_LENGTH: Int = 200
            const val MAX_FUND_OF_FUNDS_DISCOUNT: Double = 0.95
            const val MAX_FUND_OF_FUNDS_ASSUMPTION_ID_LENGTH: Int = 160
            const val MAX_COMPOSITE_ASSUMPTION_ID_LENGTH: Int = 160

            val ROOT_FIELDS: Set<String> = setOf("schemaVersion", "benchmarks", "instruments")
            val BENCHMARK_FIELDS: Set<String> = setOf(
                "benchmarkId",
                "version",
                "displayName",
                "administrator",
                "officialSourceUrls",
                "baseCurrency",
                "engineKind",
                "supportLevel",
                "componentBenchmarkRefs",
                "equityMethodology",
                "equityReferenceProfile",
                "fixedIncomeProfile",
                "commoditySpotTerms",
                "futuresReferenceTerms",
                "fundOfFundsMethodologyProfile",
                "compositeReferenceProfile",
                "alternativeRiskPremiaProfile",
            )
            val BENCHMARK_REF_FIELDS: Set<String> = setOf("benchmarkId", "version")
            val EQUITY_METHODOLOGY_FIELDS: Set<String> = setOf(
                "effectiveFrom",
                "referenceUniverse",
                "selectionModel",
                "weightingModel",
                "targetConstituentCount",
                "minDividendPaymentYears",
                "minFloatMarketCap",
                "minAverageDailyValueTraded",
                "eligibleYieldFraction",
                "incumbentRankBuffer",
                "individualWeightCap",
                "sectorWeightCap",
                "annualReconstitutionMonth",
                "rebalanceMonths",
                "dailyWeightThreshold",
                "dailyAggregateWeightLimit",
            )
            val EQUITY_REFERENCE_PROFILE_FIELDS: Set<String> = setOf(
                "region",
                "countryCodes",
                "eligibleUniverse",
                "sectorPolicy",
                "includedSectors",
                "themeId",
                "stylePolicies",
                "weightingModel",
                "targetConstituentCount",
                "individualWeightCap",
                "sectorWeightCap",
                "selectionCalendar",
                "selectionMonths",
                "reweightCalendar",
                "reweightMonths",
                "supportLevel",
                "provenance",
                "confidence",
                "officialSourceUrls",
                "assumptionId",
            )
            val FIXED_INCOME_PROFILE_FIELDS: Set<String> = setOf(
                "geography",
                "currencies",
                "assetType",
                "effectiveDurationYears",
                "tenorBand",
                "creditQuality",
                "rateReset",
                "realRateLinked",
                "supportLevel",
                "durationProvenance",
                "officialSourceUrls",
            )
            val FUND_OF_FUNDS_METHODOLOGY_PROFILE_FIELDS: Set<String> = setOf(
                "universe",
                "selectionModel",
                "weightingModel",
                "targetFundCount",
                "candidateUniverseSize",
                "eligibleCategories",
                "categoryReferences",
                "minimumDistributionYield",
                "maximumAbsoluteDiscount",
                "minimumLiquidityScore",
                "individualWeightCap",
                "categoryWeightCap",
                "rankedWeightCapTiers",
                "selectionCalendar",
                "selectionMonths",
                "reweightCalendar",
                "reweightMonths",
                "supportLevel",
                "provenance",
                "confidence",
                "officialSourceUrls",
                "assumptionId",
            )
            val FUND_OF_FUNDS_CATEGORY_REFERENCE_FIELDS: Set<String> = setOf(
                "category",
                "benchmarkRef",
            )
            val FUND_OF_FUNDS_RANKED_WEIGHT_CAP_TIER_FIELDS: Set<String> = setOf(
                "lastRankInclusive",
                "maximumWeight",
            )
            val COMPOSITE_REFERENCE_PROFILE_FIELDS: Set<String> = setOf(
                "sleeves",
                "allocationModel",
                "grossExposureConstraint",
                "netExposureConstraint",
                "annualFinancingSpread",
                "annualFinancingSpreadOrigin",
                "targetVolatilityAnnual",
                "targetVolatilityOrigin",
                "riskLookbackTradingDays",
                "riskLookbackOrigin",
                "durationConstraint",
                "driftThreshold",
                "driftThresholdOrigin",
                "selectionSchedule",
                "reweightSchedule",
                "supportLevel",
                "provenance",
                "confidence",
                "officialSourceUrls",
                "assumptionId",
            )
            val COMPOSITE_REFERENCE_SLEEVE_FIELDS: Set<String> = setOf(
                "sleeveId",
                "source",
                "direction",
                "role",
                "targetWeight",
                "minimumWeight",
                "maximumWeight",
                "targetWeightOrigin",
                "weightBandOrigin",
                "riskBudget",
                "riskBudgetOrigin",
                "annualBorrowSpread",
                "annualBorrowSpreadOrigin",
                "hedgeRatioToCompositeBaseCurrency",
                "hedgeRatioOrigin",
                "mbsInterestOnlyTerms",
            )
            val COMPOSITE_REFERENCE_SOURCE_FIELDS: Set<String> = setOf(
                "kind",
                "benchmarkRef",
                "instrumentId",
            )
            val COMPOSITE_EXPOSURE_CONSTRAINT_FIELDS: Set<String> = setOf(
                "target",
                "minimum",
                "maximum",
                "origin",
            )
            val COMPOSITE_DURATION_CONSTRAINT_FIELDS: Set<String> = setOf(
                "targetYears",
                "minimumYears",
                "maximumYears",
                "origin",
            )
            val COMPOSITE_REBALANCE_SCHEDULE_FIELDS: Set<String> = setOf(
                "calendar",
                "months",
                "origin",
            )
            val MBS_INTEREST_ONLY_TERMS_FIELDS: Set<String> = setOf(
                "prepaymentModel",
                "termsProvenance",
                "officialSourceUrls",
                "modelParameters",
            )
            val MBS_INTEREST_ONLY_MODEL_PARAMETER_FIELDS: Set<String> = setOf(
                "effectiveDurationYears",
                "baseConditionalPrepaymentRateAnnual",
                "cprIncreasePerOnePercentMortgageRateDecline",
                "annualConditionalPrepaymentRateVolatility",
                "couponStripYieldAnnual",
                "origin",
                "sourceUrl",
                "calibrationId",
            )
            val ALTERNATIVE_RISK_PREMIA_PROFILE_FIELDS: Set<String> = setOf(
                "strategyFamilies",
                "drivers",
                "signalModel",
                "longGrossExposureConstraint",
                "shortGrossExposureConstraint",
                "netExposureConstraint",
                "targetVolatilityAnnual",
                "targetVolatilityOrigin",
                "signalLookbackTradingDays",
                "signalLookbackOrigin",
                "rebalanceSchedule",
                "annualFinancingSpread",
                "annualFinancingSpreadOrigin",
                "annualShortBorrowSpread",
                "annualShortBorrowSpreadOrigin",
                "annualImplementationCostRate",
                "annualImplementationCostOrigin",
                "supportLevel",
                "provenance",
                "confidence",
                "officialSourceUrls",
                "assumptionId",
            )
            val ALTERNATIVE_RISK_PREMIA_DRIVER_FIELDS: Set<String> = setOf(
                "driverId",
                "source",
                "strategyFamily",
                "signalDirectionPolicy",
                "targetRiskBudget",
                "riskBudgetOrigin",
                "hedgeRatioToProfileBaseCurrency",
                "hedgeRatioOrigin",
            )
            val COMMODITY_SPOT_TERMS_FIELDS: Set<String> = setOf(
                "benchmarkRef",
                "assetClass",
                "baseCurrency",
                "spotAllocation",
                "collateralAllocation",
                "annualStorageCostRate",
                "annualCustodyAndInsuranceCostRate",
                "annualConvenienceYieldRate",
                "collateralYieldParticipation",
                "provenance",
                "officialSourceUrls",
                "assumptionId",
            )
            val FUTURES_REFERENCE_TERMS_FIELDS: Set<String> = setOf(
                "benchmarkRef",
                "baseCurrency",
                "portfolioStyle",
                "allocationMode",
                "collateralRatio",
                "collateralYieldParticipation",
                "sleeves",
                "provenance",
                "officialSourceUrls",
                "assumptionId",
            )
            val FUTURES_SLEEVE_TERMS_FIELDS: Set<String> = setOf(
                "sleeveId",
                "curveId",
                "assetClass",
                "targetWeight",
                "notionalExposureRatio",
                "rollCalendar",
                "eligibleDeliveryMonths",
                "rollStartTradingDaysBeforeExpiry",
                "rollWindowTradingDays",
                "priceReturnConvention",
                "fixedPriceReturnNotional",
            )
            val INSTRUMENT_FIELDS: Set<String> = setOf(
                "order",
                "symbol",
                "name",
                "englishName",
                "market",
                "sector",
                "instrumentType",
                "initialPrice",
                "volatility",
                "dividendYield",
                "marketCap",
                "sharesOutstanding",
                "description",
                "beta",
                "quantityStep",
                "lotSize",
                "etfProfile",
                "fundProductProfile",
                "behaviorProfile",
                "identityProfile",
                "industrySegments",
            )
            val REQUIRED_INSTRUMENT_FIELDS: Set<String> = INSTRUMENT_FIELDS
            val FUND_PRODUCT_PROFILE_FIELDS: Set<String> = setOf(
                "benchmarkRef",
                "replicationMode",
                "returnVariant",
                "legalStructure",
                "referenceExposure",
                "returnTransforms",
                "trackingErrorAnnualVolatility",
                "dailyResetTerms",
                "etnProductTerms",
                "etnIssuerCreditModelParameters",
                "closedEndFundTerms",
                "closedEndFundMarketModelParameters",
                "optionStrategyTerms",
                "cashCollateralizedPutSpreadTerms",
            )
            val CASH_COLLATERALIZED_PUT_SPREAD_TERMS_FIELDS: Set<String> = setOf(
                "productId",
                "cashBenchmarkRef",
                "optionReference",
                "directReferenceTerminationRule",
                "tenorTradingDays",
                "rollCalendar",
                "rollLeadTradingDays",
                "maximumSettlementLossRatio",
                "shortPutStrikeMoneyness",
                "longPutStrikeMoneyness",
                "provenance",
                "officialSourceUrls",
                "assumptionId",
                "premiumModel",
            )
            val OPTION_STRATEGY_TERMS_FIELDS: Set<String> = setOf(
                "productId",
                "reference",
                "directReferenceTerminationRule",
                "kind",
                "tenorTradingDays",
                "rollCalendar",
                "rollLeadTradingDays",
                "provenance",
                "officialSourceUrls",
                "assumptionId",
                "premiumModel",
                "coveredCall",
                "optionIncome",
                "bufferedPutSpread",
            )
            val OPTION_PREMIUM_MODEL_PARAMETER_FIELDS: Set<String> = setOf(
                "impliedVolatilityMultiplier",
                "soldPremiumCaptureRatio",
                "purchasedPremiumCostRatio",
                "implementationCostRatePerRoll",
                "origin",
                "sourceUrl",
                "calibrationId",
            )
            val COVERED_CALL_STRATEGY_TERMS_FIELDS: Set<String> = setOf(
                "overwriteRatio",
                "callStrikeMoneyness",
            )
            val OPTION_INCOME_STRATEGY_TERMS_FIELDS: Set<String> = setOf(
                "coreEquityAllocation",
                "optionIncomeAllocation",
                "upsideParticipation",
                "downsideParticipation",
                "callStrikeMoneyness",
            )
            val BUFFERED_PUT_SPREAD_STRATEGY_TERMS_FIELDS: Set<String> = setOf(
                "outcomeNotionalRatio",
                "longPutStrikeMoneyness",
                "downsideBufferFraction",
                "downsideParticipationBeyondBuffer",
                "upsideCapFraction",
            )
            val ETN_PRODUCT_TERMS_FIELDS: Set<String> = setOf(
                "productId",
                "referenceId",
                "issuerId",
                "settlementCurrency",
                "statedPrincipalPerNote",
                "annualInvestorFeeRate",
                "investorFeeDayCountBasis",
                "issueDate",
                "maturityDate",
                "maturityValuationRule",
                "maturitySettlementMultiplier",
                "maturityIncludesAccruedCoupon",
                "couponRule",
                "callTerms",
                "accelerationTerms",
                "termsProvenance",
                "officialSourceUrl",
            )
            val ETN_ISSUER_CREDIT_MODEL_PARAMETER_FIELDS: Set<String> = setOf(
                "issuerId",
                "initialCreditSpread",
                "initialHazardRate",
                "recoveryRate",
                "annualSpreadMeanReversionRate",
                "spreadShockAnnualVolatility",
                "origin",
                "sourceUrl",
            )
            val ETN_SETTLEMENT_VALUATION_RULE_FIELDS: Set<String> = setOf(
                "method",
                "observationCount",
            )
            val ETN_COUPON_RULE_FIELDS: Set<String> = setOf(
                "kind",
                "paymentFrequencyMonths",
                "annualFixedRate",
                "participationRate",
                "accrualReducesIndicativeValue",
                "accruedCouponPaidAtTermination",
            )
            val ETN_CALL_TERMS_FIELDS: Set<String> = setOf(
                "issuerCallable",
                "issuerCallMayBePartial",
                "holderRedeemable",
                "minimumHolderRedemptionNotes",
                "holderRedemptionNoteIncrement",
                "minimumNoticeBusinessDays",
                "issuerCallValuationRule",
                "holderRedemptionValuationRule",
                "issuerCallSettlementMultiplier",
                "holderRedemptionSettlementMultiplier",
                "holderRedemptionChargeRate",
                "includesAccruedCoupon",
            )
            val ETN_ACCELERATION_TERMS_FIELDS: Set<String> = setOf(
                "issuerMayAccelerate",
                "partialAccelerationAllowed",
                "minimumPartialAccelerationNotes",
                "partialAccelerationNoteIncrement",
                "creditDefaultCausesAcceleration",
                "fullAccelerationValuationRule",
                "partialAccelerationValuationRule",
                "accelerationSettlementMultiplier",
                "nonCreditAccelerationIncludesAccruedCoupon",
                "creditDefaultIncludesAccruedCouponBeforeRecovery",
            )
            val CLOSED_END_FUND_TERMS_FIELDS: Set<String> = setOf(
                "fundId",
                "settlementCurrency",
                "distributionPolicy",
                "allowsTenderOffers",
                "allowsShareRepurchases",
                "allowsRightsOfferings",
                "allowsAtTheMarketOfferings",
                "allowsDebtLeverage",
                "allowsPreferredLeverage",
                "minimumDebtAssetCoverageRatio",
                "minimumPreferredAssetCoverageRatio",
                "termsProvenance",
                "officialSourceUrl",
            )
            val CLOSED_END_FUND_MARKET_MODEL_PARAMETER_FIELDS: Set<String> = setOf(
                "fundId",
                "targetMarketDiscountRate",
                "annualDiscountMeanReversionRate",
                "initialDebtToGrossAssets",
                "initialPreferredToGrossAssets",
                "annualBorrowingSpread",
                "annualPreferredDistributionSpread",
                "discountShockAnnualVolatility",
                "origin",
                "sourceUrl",
            )
            val DAILY_RESET_TERMS_FIELDS: Set<String> = setOf(
                "productId",
                "reference",
                "directReferenceTerminationRule",
                "targetLeverage",
                "resetCalendar",
                "provenance",
                "officialSourceUrl",
                "modelParameters",
            )
            val DIRECT_REFERENCE_TERMINATION_RULE_FIELDS: Set<String> = setOf(
                "policy",
                "provenance",
                "officialSourceUrls",
                "assumptionId",
            )
            val DAILY_RESET_REFERENCE_FIELDS: Set<String> = setOf(
                "kind",
                "benchmarkRef",
                "instrumentId",
            )
            val DAILY_RESET_MODEL_PARAMETER_FIELDS: Set<String> = setOf(
                "annualFinancingSpread",
                "collateralYieldParticipation",
                "origin",
                "sourceUrl",
            )
            val ETF_PROFILE_FIELDS: Set<String> = setOf(
                "benchmark",
                "assetClass",
                "taxCategory",
                "annualExpenseRatio",
                "fxProfile",
                "leverage",
                "taxablePriceGainRatio",
                "exposureRegion",
            )
            val ETF_FX_PROFILE_FIELDS: Set<String> = setOf("legs", "annualHedgeCostRate")
            val CURRENCY_EXPOSURE_LEG_FIELDS: Set<String> = setOf(
                "currency",
                "grossNotional",
                "hedgeRatioToListingCurrency",
            )
            val BEHAVIOR_PROFILE_FIELDS: Set<String> = setOf(
                "strategy",
                "distributionFrequency",
                "upsideParticipation",
                "downsideParticipation",
                "durationYears",
                "creditSpreadSensitivity",
                "cashRateAccrual",
                "annualStructuralDrag",
                "distributionCoverageRatio",
                "priceDislocationVolatility",
                "referenceCurrency",
                "referenceCurrencySensitivity",
                "commodityFactorSensitivity",
                "cryptoFactorSensitivity",
                "principalRisk",
            )
            val IDENTITY_PROFILE_FIELDS: Set<String> = setOf(
                "aliases",
                "issuerOrManager",
                "strategySummary",
                "officialSourceUrl",
                "supportingSourceUrls",
                "eventRiskTags",
                "maturityDate",
                "callable",
                "adrUnderlyingShareRatio",
                "underlyingInstrumentIds",
                "exposedSectors",
            )
        }
    }
}
