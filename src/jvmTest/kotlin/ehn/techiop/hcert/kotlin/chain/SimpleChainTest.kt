package ehn.techiop.hcert.kotlin.chain

import ehn.techiop.hcert.kotlin.chain.impl.PrefilledCertificateRepository
import ehn.techiop.hcert.kotlin.chain.impl.RandomEcKeyCryptoService
import ehn.techiop.hcert.kotlin.chain.impl.RandomRsaKeyCryptoService
import ehn.techiop.hcert.kotlin.data.GreenCertificate
import ehn.techiop.hcert.kotlin.trust.ContentType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class SimpleChainTest {

    @ParameterizedTest
    @MethodSource("inputProvider")
    fun successRsa(input: TestInput) {
        verify(input.data, RandomRsaKeyCryptoService(2048, listOf(input.contentType)), input.outcome)
        verify(input.data, RandomRsaKeyCryptoService(3072, listOf(input.contentType)), input.outcome)
    }

    @ParameterizedTest
    @MethodSource("inputProvider")
    fun successEc(input: TestInput) {
        verify(input.data, RandomEcKeyCryptoService(256, listOf(input.contentType)), input.outcome)
        verify(input.data, RandomEcKeyCryptoService(384, listOf(input.contentType)), input.outcome)
    }

    private fun verify(jsonInput: String, cryptoService: CryptoService, outcome: VerificationDecision) {
        val input = Json.decodeFromString<GreenCertificate>(jsonInput)

        val encodingChain = DefaultChain.buildCreationChain(cryptoService)
        val certificateRepository = PrefilledCertificateRepository(cryptoService.getCertificate())
        val decodingChain = DefaultChain.buildVerificationChain(certificateRepository)

        val output = encodingChain.encode(input)

        val vaccinationData = decodingChain.decode(output.step5Prefixed)
        val verificationResult =vaccinationData.verificationResult
        assertThat(vaccinationData.greenCertificate, equalTo(input))
        assertThat(verificationResult.cborDecoded, equalTo(true))
        assertThat(DecisionService().decide(verificationResult), equalTo(outcome))
    }

    companion object {

        @JvmStatic
        @Suppress("unused")
        fun inputProvider() = listOf(
            TestInput(SampleData.testRat, ContentType.TEST, VerificationDecision.GOOD),
            TestInput(SampleData.testRat, ContentType.VACCINATION, VerificationDecision.FAIL),
            TestInput(SampleData.testNaa, ContentType.TEST, VerificationDecision.GOOD),
            TestInput(SampleData.testNaa, ContentType.RECOVERY, VerificationDecision.FAIL),
            TestInput(SampleData.vaccination, ContentType.VACCINATION, VerificationDecision.GOOD),
            TestInput(SampleData.vaccination, ContentType.TEST, VerificationDecision.FAIL),
            TestInput(SampleData.recovery, ContentType.RECOVERY, VerificationDecision.GOOD),
            TestInput(SampleData.recovery, ContentType.VACCINATION, VerificationDecision.FAIL),
        )

    }

    data class TestInput(val data: String, val contentType: ContentType, val outcome: VerificationDecision)

}
