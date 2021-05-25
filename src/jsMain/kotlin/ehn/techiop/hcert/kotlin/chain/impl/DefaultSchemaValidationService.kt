package ehn.techiop.hcert.kotlin.chain.impl

import AJV2020
import Cbor.DecodeOptions
import MainResourceHolder
import addFormats
import ehn.techiop.hcert.kotlin.chain.*
import ehn.techiop.hcert.kotlin.data.loadAsString

actual class DefaultSchemaValidationService : SchemaValidationService {
    val ajv = AJV2020()
    val schema: dynamic

    init {
        addFormats(ajv)

        // Warning: AJV does not support the valueset-uri keyword used in the schema.
        // We configure AJV to ignore the keyword, but that still means we are not checking
        // field values against the allowed options from the linked value sets.
        ajv.addKeyword("valueset-uri")

        val schemaString = MainResourceHolder.loadAsString("json/DGC.combined-schema.json")
        schema = JSON.parse<dynamic>(schemaString!!)

        if (!ajv.validateSchema(schema)) {
            throw Throwable("JSON schema invalid: ${JSON.stringify(ajv.errors)}")
        }
    }


    override fun validate(cbor: ByteArray, verificationResult: VerificationResult) {
        jsTry {
            val json = Cbor.Decoder.decodeFirstSync(input = cbor.toBuffer(), options = object : DecodeOptions {})

            if (!ajv.validate(schema, json)) {
                throw Throwable("Data does not follow schema: ${JSON.stringify(ajv.errors)}")
                /*.let {
                     if (!it) {
                         console.log("Data does not follow schema:")
                         console.log(JSON.stringify(ajv.errors))
                     }
                 }*/
            }
            verificationResult.schemaValidated = true
        }.catch { throw it }

    }
}