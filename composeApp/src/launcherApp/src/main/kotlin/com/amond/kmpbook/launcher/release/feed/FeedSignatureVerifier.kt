package com.amond.kmpbook.launcher.release.feed

import com.amond.kmpbook.launcher.foundation.LauncherException
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

internal class FeedSignatureVerifier private constructor(private val publicKey: PublicKey) {
    fun verify(content: ByteArray, signature: ByteArray) {
        if (signature.size != ED25519_SIGNATURE_SIZE) {
            throw LauncherException("feed-signature-size", "feed 서명 길이가 올바르지 않습니다.")
        }
        val verifier = try {
            Signature.getInstance("Ed25519").apply {
                initVerify(publicKey)
                update(SIGNATURE_DOMAIN)
                update(content)
            }
        } catch (error: Exception) {
            throw LauncherException("feed-signature-init", "feed 서명 검증기를 초기화하지 못했습니다.", error)
        }
        if (!verifier.verify(signature)) {
            throw LauncherException("feed-signature-invalid", "안정 채널 feed 서명이 유효하지 않습니다.")
        }
    }

    fun decodeDetachedSignature(bytes: ByteArray): ByteArray {
        if (bytes.isEmpty() || bytes.size > MAX_SIGNATURE_TEXT_BYTES ||
            bytes.any { (it.toInt() and 0xff) !in 0x21..0x7e }
        ) {
            throw LauncherException("feed-signature-format", "feed 서명 파일 형식이 올바르지 않습니다.")
        }
        return try {
            Base64.getDecoder().decode(bytes)
        } catch (error: IllegalArgumentException) {
            throw LauncherException("feed-signature-base64", "feed 서명 파일이 올바른 Base64가 아닙니다.", error)
        }
    }

    companion object {
        val SIGNATURE_DOMAIN: ByteArray = "MarketLedger2040.StableFeed.v1\u0000"
            .toByteArray(StandardCharsets.UTF_8)
        private const val KEY_RESOURCE = "/market-ledger/release/stable-feed-public-key.b64"
        private const val MAX_KEY_TEXT_BYTES = 4 * 1024
        private const val MAX_SIGNATURE_TEXT_BYTES = 256
        private const val ED25519_SIGNATURE_SIZE = 64

        fun fromEmbeddedKey(): FeedSignatureVerifier {
            val encoded = FeedSignatureVerifier::class.java.getResourceAsStream(KEY_RESOURCE)?.use { stream ->
                stream.readNBytes(MAX_KEY_TEXT_BYTES + 1)
            } ?: throw LauncherException("release-key-missing", "런처에 안정 채널 공개 키가 없습니다.")
            if (encoded.isEmpty() || encoded.size > MAX_KEY_TEXT_BYTES ||
                encoded.any { (it.toInt() and 0xff) !in 0x21..0x7e }
            ) {
                throw LauncherException("release-key-format", "런처의 안정 채널 공개 키 형식이 올바르지 않습니다.")
            }
            val keyBytes = try {
                Base64.getDecoder().decode(encoded)
            } catch (error: IllegalArgumentException) {
                throw LauncherException("release-key-base64", "런처의 안정 채널 공개 키가 올바른 Base64가 아닙니다.", error)
            }
            val key = try {
                KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(keyBytes))
            } catch (error: Exception) {
                throw LauncherException("release-key-invalid", "런처의 안정 채널 공개 키가 유효하지 않습니다.", error)
            }
            return FeedSignatureVerifier(key)
        }
    }
}
