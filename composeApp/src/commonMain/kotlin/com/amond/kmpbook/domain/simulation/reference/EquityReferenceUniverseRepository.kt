package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.EquityReferenceRegion
import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector
import com.amond.kmpbook.domain.model.reference.EquityReferenceAssetIdentity
import com.amond.kmpbook.domain.simulation.price.DeterministicRandom
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Campaign-seeded repository of non-listed representative equity anchors across explicit countries.
 * It is synthetic reference data, not a claim about actual future constituents or listings.
 */
class EquityReferenceUniverseRepository private constructor(private val seed: Long) {
    private val candidates: List<EquityReferenceCandidate> = buildCandidates()
    private val candidateById: Map<String, EquityReferenceCandidate> =
        candidates.associateBy(EquityReferenceCandidate::assetId)
    private val identityById: Map<String, EquityReferenceAssetIdentity> = candidates.associate { candidate ->
        candidate.assetId to EquityReferenceAssetIdentity(
            assetId = candidate.assetId,
            region = candidate.region,
            countryCode = candidate.countryCode,
            sector = candidate.sector,
        )
    }
    private val annualSnapshots = mutableMapOf<Int, List<EquityReferenceCandidateSnapshot>>()
    private val annualSnapshotsById = mutableMapOf<Int, Map<String, EquityReferenceCandidateSnapshot>>()
    private val scopedAnnualSnapshots = mutableMapOf<String, List<EquityReferenceCandidateSnapshot>>()

    val universeModelVersion: String = UNIVERSE_MODEL_VERSION
    val universeFingerprint: String = stableHex(
        "$UNIVERSE_MODEL_VERSION|$seed|" + COUNTRY_CANDIDATE_COUNTS.entries.joinToString { "${it.key}:${it.value}" },
    )

    fun identity(assetId: String): EquityReferenceAssetIdentity? = identityById[assetId]

    fun containsCanonicalAssetId(assetId: String): Boolean = assetId in candidateById

    internal fun snapshot(assetId: String, year: Int): EquityReferenceCandidateSnapshot? =
        snapshotsByIdForYear(year)[assetId]

    internal fun snapshots(
        region: EquityReferenceRegion,
        countryCodes: Set<String>,
        year: Int,
    ): List<EquityReferenceCandidateSnapshot> {
        require(year in FIRST_SUPPORTED_YEAR..LAST_SUPPORTED_YEAR)
        val resolvedCountries = if (countryCodes.isEmpty()) countryCodesForRegion(region) else countryCodes
        require(resolvedCountries.isNotEmpty())
        require(resolvedCountries.all(COUNTRY_CODE_PATTERN::matches))
        require(resolvedCountries.all { it in countryCodesForRegion(region) }) {
            "Country scope must remain inside its declared equity region."
        }
        val scopeKey = "$year|$region|${resolvedCountries.sorted().joinToString(",")}"
        return scopedAnnualSnapshots.getOrPut(scopeKey) {
            snapshotsForYear(year)
                .filter { it.countryCode in resolvedCountries }
                .sortedWith(
                    compareByDescending<EquityReferenceCandidateSnapshot> { it.floatMarketCap }
                        .thenBy(EquityReferenceCandidateSnapshot::assetId),
                )
        }
    }

    fun countryCodesForRegion(region: EquityReferenceRegion): Set<String> = when (region) {
        EquityReferenceRegion.KOREA -> setOf("KR")
        EquityReferenceRegion.UNITED_STATES -> setOf("US")
        EquityReferenceRegion.DEVELOPED_EX_US -> DEVELOPED_EX_US_COUNTRIES
        EquityReferenceRegion.EMERGING_MARKETS -> EMERGING_COUNTRIES
        EquityReferenceRegion.GLOBAL -> ALL_COUNTRIES
    }

    private fun snapshotsForYear(year: Int): List<EquityReferenceCandidateSnapshot> =
        annualSnapshots.getOrPut(year) { candidates.map { snapshot(it, year) } }

    private fun snapshotsByIdForYear(year: Int): Map<String, EquityReferenceCandidateSnapshot> {
        require(year in FIRST_SUPPORTED_YEAR..LAST_SUPPORTED_YEAR)
        return annualSnapshotsById.getOrPut(year) {
            snapshotsForYear(year).associateBy(EquityReferenceCandidateSnapshot::assetId)
        }
    }

    private fun buildCandidates(): List<EquityReferenceCandidate> = COUNTRY_CANDIDATE_COUNTS
        .flatMap { (countryCode, count) ->
            val region = regionForCountry(countryCode)
            (1..count).map { ordinal ->
                val assetId = "sim-eq:${countryCode.lowercase()}:" + ordinal.toString().padStart(4, '0')
                val random = DeterministicRandom.keyed(seed, "equity-reference-candidate:$assetId")
                val value = tanh(random.nextGaussian() * .70)
                val growth = tanh(random.nextGaussian() * .70)
                val quality = tanh(random.nextGaussian() * .65)
                val momentum = tanh(random.nextGaussian() * .70)
                val esg = tanh(random.nextGaussian() * .65)
                val logCap = ln(MIN_BASE_MARKET_CAP) +
                    random.nextDouble() * (ln(MAX_BASE_MARKET_CAP) - ln(MIN_BASE_MARKET_CAP))
                val marketCap = exp(logCap)
                val volatility = (.16 + random.nextDouble() * .54 - quality * .035)
                    .coerceIn(.10, 1.20)
                EquityReferenceCandidate(
                    assetId = assetId,
                    region = region,
                    countryCode = countryCode,
                    sector = MethodologyEquitySector.entries[random.nextInt(MethodologyEquitySector.entries.size)],
                    baseMarketCap = marketCap,
                    baseFloatFactor = random.nextDouble(.35, 1.0),
                    basePrice = exp(random.nextDouble(ln(4.0), ln(600.0))),
                    baseRevenueToMarketCap = exp(random.nextDouble(ln(.12), ln(3.0))),
                    baseDividendYield = (
                        .012 + value * .010 - growth * .006 + random.nextGaussian() * .009
                        ).coerceIn(0.0, .18),
                    baseValue = value,
                    baseGrowth = growth,
                    baseQuality = quality,
                    baseMomentum = momentum,
                    baseBeta = (1.0 + random.nextGaussian() * .25 + growth * .10)
                        .coerceIn(.25, 2.25),
                    baseAnnualVolatility = volatility,
                    baseEsg = esg,
                    baseLiquidity = marketCap * random.nextDouble(.00004, .0030),
                )
            }
        }.sortedBy(EquityReferenceCandidate::assetId)

    private fun snapshot(
        candidate: EquityReferenceCandidate,
        year: Int,
    ): EquityReferenceCandidateSnapshot {
        val years = (year - BASE_YEAR).coerceAtLeast(0)
        val random = DeterministicRandom.keyed(
            seed,
            "equity-reference-annual:${candidate.assetId}:$year",
        )
        val cumulativeShock = if (years == 0) 0.0 else random.nextGaussian() * sqrt(years.toDouble())
        val capGrowth = .035 + candidate.baseGrowth * .025 + candidate.baseQuality * .010
        val marketCap = candidate.baseMarketCap * exp(
            capGrowth * years + candidate.baseAnnualVolatility * .35 * cumulativeShock,
        )
        val featureJitter = .12
        val value = (candidate.baseValue + random.nextGaussian() * featureJitter).coerceIn(-1.0, 1.0)
        val growth = (candidate.baseGrowth + random.nextGaussian() * featureJitter).coerceIn(-1.0, 1.0)
        val quality = (candidate.baseQuality + random.nextGaussian() * featureJitter).coerceIn(-1.0, 1.0)
        val momentum = (candidate.baseMomentum * .35 + random.nextGaussian() * .45).coerceIn(-1.0, 1.0)
        val esg = (candidate.baseEsg + random.nextGaussian() * .06).coerceIn(-1.0, 1.0)
        val dividendYield = (
            candidate.baseDividendYield + value * .004 - growth * .003 + random.nextGaussian() * .004
            ).coerceIn(0.0, .25)
        val annualVolatility = (
            candidate.baseAnnualVolatility * exp(random.nextGaussian() * .08) - quality * .015
            ).coerceIn(.08, 1.50)
        return EquityReferenceCandidateSnapshot(
            assetId = candidate.assetId,
            region = candidate.region,
            countryCode = candidate.countryCode,
            sector = candidate.sector,
            marketCap = marketCap.coerceIn(MIN_SNAPSHOT_VALUE, MAX_SNAPSHOT_VALUE),
            floatMarketCap = (marketCap * candidate.baseFloatFactor).coerceIn(
                MIN_SNAPSHOT_VALUE,
                MAX_SNAPSHOT_VALUE,
            ),
            price = (candidate.basePrice * sqrt(marketCap / candidate.baseMarketCap))
                .coerceIn(.01, 1e9),
            revenue = (marketCap * candidate.baseRevenueToMarketCap).coerceIn(
                MIN_SNAPSHOT_VALUE,
                MAX_SNAPSHOT_VALUE,
            ),
            dividendYield = dividendYield,
            value = value,
            growth = growth,
            quality = quality,
            momentum = momentum,
            beta = (candidate.baseBeta + random.nextGaussian() * .04).coerceIn(.10, 2.75),
            annualVolatility = annualVolatility,
            esg = esg,
            liquidity = (candidate.baseLiquidity * sqrt(marketCap / candidate.baseMarketCap))
                .coerceIn(1.0, MAX_SNAPSHOT_VALUE),
        )
    }

    private fun regionForCountry(countryCode: String): EquityReferenceRegion = when (countryCode) {
        "KR" -> EquityReferenceRegion.KOREA
        "US" -> EquityReferenceRegion.UNITED_STATES
        in DEVELOPED_EX_US_COUNTRIES -> EquityReferenceRegion.DEVELOPED_EX_US
        in EMERGING_COUNTRIES -> EquityReferenceRegion.EMERGING_MARKETS
        else -> error("Unclassified equity-reference country $countryCode.")
    }

    private fun stableHex(value: String): String =
        DeterministicRandom.stableHash64(value).toULong().toString(16).padStart(16, '0')

    companion object {
        fun forCampaignSeed(campaignSeed: Long): EquityReferenceUniverseRepository =
            EquityReferenceUniverseRepository(
                DeterministicRandom.mixSeed(campaignSeed, REPOSITORY_STREAM_ID),
            )

        const val UNIVERSE_MODEL_VERSION: String = "equity-reference-universe-v1"
        private const val REPOSITORY_STREAM_ID: Long = 0x4551554954595246L
        private const val BASE_YEAR: Int = 2026
        private const val FIRST_SUPPORTED_YEAR: Int = 2026
        private const val LAST_SUPPORTED_YEAR: Int = 2040
        private const val MIN_BASE_MARKET_CAP: Double = 50_000_000.0
        private const val MAX_BASE_MARKET_CAP: Double = 2_500_000_000_000.0
        private const val MIN_SNAPSHOT_VALUE: Double = 1.0
        private const val MAX_SNAPSHOT_VALUE: Double = 1e18
        private val COUNTRY_CODE_PATTERN = Regex("[A-Z]{2}")

        val DEVELOPED_EX_US_COUNTRIES: Set<String> = setOf(
            "AT", "AU", "BE", "CA", "CH", "DE", "DK", "ES", "FI", "FR", "GB", "HK",
            "IE", "IL", "IT", "JP", "NL", "NO", "NZ", "PT", "SE", "SG",
        ).sorted().toSet()
        val EMERGING_COUNTRIES: Set<String> = setOf(
            "AE", "BR", "CL", "CN", "CO", "CZ", "EG", "GR", "HU", "ID", "IN", "KW",
            "MX", "MY", "PE", "PH", "PL", "QA", "SA", "TH", "TR", "TW", "VN", "ZA",
        ).sorted().toSet()
        val ALL_COUNTRIES: Set<String> = (setOf("KR", "US") +
            DEVELOPED_EX_US_COUNTRIES + EMERGING_COUNTRIES).sorted().toSet()

        private val COUNTRY_CANDIDATE_COUNTS: Map<String, Int> = linkedMapOf(
            "US" to 2_500,
            "KR" to 900,
            "JP" to 550, "GB" to 300, "CA" to 250, "FR" to 200, "DE" to 200,
            "CH" to 150, "AU" to 180, "NL" to 100, "SE" to 80, "DK" to 60,
            "ES" to 80, "IT" to 80, "SG" to 80, "HK" to 100, "NO" to 50,
            "FI" to 40, "BE" to 40, "AT" to 30, "IE" to 40, "NZ" to 30,
            "IL" to 30, "PT" to 20,
            "CN" to 600, "TW" to 300, "IN" to 450, "BR" to 250, "ZA" to 150,
            "MX" to 160, "ID" to 140, "TH" to 100, "MY" to 80, "PH" to 60,
            "VN" to 80, "PL" to 70, "SA" to 100, "AE" to 60, "QA" to 40,
            "KW" to 40, "TR" to 80, "CL" to 50, "CO" to 40, "PE" to 30,
            "GR" to 20, "CZ" to 30, "HU" to 30, "EG" to 40,
        ).toSortedMap()
    }
}
