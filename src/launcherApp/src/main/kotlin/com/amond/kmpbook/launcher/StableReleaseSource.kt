package com.amond.kmpbook.launcher

import java.net.URI

internal class StableReleaseSource(
    private val httpClient: BoundedHttpsClient,
    private val signatureVerifier: FeedSignatureVerifier,
    private val parser: StableFeedParser,
    private val logger: LauncherLogger,
) {
    fun load(): VerifiedFeedDocument {
        val remoteFailure = try {
            return verify(
                httpClient.getBytes(REMOTE_FEED, StableFeedParser.MAX_FEED_BYTES),
                httpClient.getBytes(REMOTE_SIGNATURE, MAX_SIGNATURE_BYTES),
            )
        } catch (error: LauncherException) {
            logger.info("remote-feed-unavailable:${error.diagnosticCode}")
            error
        }
        return try {
            verify(readResource(BUNDLED_FEED, StableFeedParser.MAX_FEED_BYTES), readResource(BUNDLED_SIGNATURE, MAX_SIGNATURE_BYTES))
        } catch (_: LauncherException) {
            throw remoteFailure
        }
    }

    fun verifyStored(content: ByteArray, signature: ByteArray): VerifiedFeedDocument {
        signatureVerifier.verify(content, signature)
        return VerifiedFeedDocument(parser.parse(content), content, signature)
    }

    private fun verify(content: ByteArray, signatureText: ByteArray): VerifiedFeedDocument {
        val signature = signatureVerifier.decodeDetachedSignature(signatureText)
        return verifyStored(content, signature)
    }

    private fun readResource(path: String, maximumBytes: Int): ByteArray {
        val input = StableReleaseSource::class.java.getResourceAsStream(path)
            ?: throw LauncherException("bundled-release-missing", "내장된 오프라인 배포 feed가 없습니다.")
        return input.use { stream ->
            stream.readNBytes(maximumBytes + 1).also { bytes ->
                if (bytes.isEmpty() || bytes.size > maximumBytes) {
                    throw LauncherException("bundled-release-size", "내장된 오프라인 배포 파일 크기가 올바르지 않습니다.")
                }
            }
        }
    }

    private companion object {
        val REMOTE_FEED: URI = URI(
            "https://github.com/it-play/KMP-Playground/releases/download/" +
                "market-ledger-game-stable/market-ledger-stable-feed.json",
        )
        val REMOTE_SIGNATURE: URI = URI(
            "https://github.com/it-play/KMP-Playground/releases/download/" +
                "market-ledger-game-stable/market-ledger-stable-feed.json.sig",
        )
        const val BUNDLED_FEED = "/bundled-release/market-ledger-stable-feed.json"
        const val BUNDLED_SIGNATURE = "/bundled-release/market-ledger-stable-feed.json.sig"
        const val MAX_SIGNATURE_BYTES = 256
    }
}
