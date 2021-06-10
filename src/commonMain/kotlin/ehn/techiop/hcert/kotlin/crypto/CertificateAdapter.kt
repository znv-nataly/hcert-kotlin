package ehn.techiop.hcert.kotlin.crypto

import ehn.techiop.hcert.kotlin.trust.ContentType
import ehn.techiop.hcert.kotlin.trust.TrustedCertificateV2
import kotlinx.datetime.Instant

expect class CertificateAdapter(pemEncoded: String) {

    constructor(_encoded: ByteArray)

    val validContentTypes: List<ContentType>
    val validFrom: Instant
    val validUntil: Instant
    val publicKey: PubKey<*>
    fun toTrustedCertificate(): TrustedCertificateV2
    val kid: ByteArray
    val encoded: ByteArray
}

interface PrivKey<T> {
    fun toCoseRepresentation(): Any
    fun toPlatformPrivateKey(): T
}

interface PubKey<T> {
    fun toCoseRepresentation(): Any
    fun toPlatformPublicKey(): T
}
