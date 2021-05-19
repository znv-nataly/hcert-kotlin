plugins {
    kotlin("multiplatform") version "1.5.0"
    kotlin("plugin.serialization") version "1.5.0"
    id("idea")
}

group = "ehn.techiop.hcert"
version = "0.3.0-SNAPSHOT"

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

repositories {
    mavenLocal()
    jcenter()
    mavenCentral()
}

kotlin {
    targets.all {
        compilations.all {
            kotlinOptions {
                freeCompilerArgs = listOf(
                    "-Xopt-in=kotlin.RequiresOptIn",
                    "-Xopt-in=kotlin.ExperimentalUnsignedTypes",
                    "-Xopt-in=kotlin.time.ExperimentalTime",
                    "-Xopt-in=kotlinx.serialization.ExperimentalSerializationApi"
                )
            }
        }
    }
    sourceSets.all {
        languageSettings.apply {
            useExperimentalAnnotation("kotlin.time.ExperimentalTime")
            useExperimentalAnnotation("kotlin.ExperimentalUnsignedTypes")
            useExperimentalAnnotation("kotlinx.serialization.ExperimentalSerializationApi")
        }
    }
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
                freeCompilerArgs = listOf(
                    "-Xjsr305=strict"
                )
            }
        }
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(LEGACY) {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                    webpackConfig.cssSupport.enabled = false
                }
            }
        }
        useCommonJs()

    }



    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.1.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.1.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.2.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.5.0")
                implementation("com.augustcellars.cose:cose-java:1.1.0")
                implementation("com.google.zxing:core:3.4.1")
                implementation("com.google.zxing:javase:3.4.1")
                implementation("org.bouncycastle:bcpkix-jdk15to18:1.68")
                implementation("javax.validation:validation-api:2.0.1.Final")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter-api:5.7.1")
                implementation("org.junit.jupiter:junit-jupiter-params:5.7.1")
                implementation("org.hamcrest:hamcrest:2.2")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.1")
            }
        }
        val jsMain by getting {
            sourceSets { kotlin.srcDir("src/jsMain/generated") }
            dependencies {
                implementation(npm("pako", "2.0.3"))
                implementation(npm("@types/pako", "1.0.1", generateExternals = true))
                implementation(npm("pkijs", "2.1.95"))
                implementation(npm("cose-js", File("${projectDir.absolutePath}/cose-js"), generateExternals = false))
                implementation(npm("crypto-browserify", "3.12.0"))
                implementation(npm("stream-browserify", "3.0.0"))
                implementation(npm("util", "0.12.3"))
                implementation(npm("buffer", "6.0.3"))
                implementation(npm("process", "0.11.10"))
                implementation(npm("cbor", "7.0.5"))
                implementation(npm("node-inspect-extracted", "1.0.7"))
                implementation(npm("fast-sha256", "1.3.0", generateExternals = true))
                implementation(npm("url", "0.11.0"))
            }
        }


        val jsTest by getting {
            sourceSets { kotlin.srcDir("src/jsTest/generated") }
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}

/*
* KJS: No way to get test resources in a multiplatform project.
* https://youtrack.jetbrains.com/issue/KT-36824
*
* These tasks work around this glaring issue by wrapping test resources into code and providing accessors
* requires base64 decoding afterwards and can surely be optimized, but at least it provides access to test resources
*
* Bonus Issue: this also affects main resources, bc test cases can obviously call code which already depends on resources
* Therefore, we also use the trick in jsMain
* */

tasks.named("clean") { dependsOn(tasks.named("jsCleanResources")) }
tasks.named("compileKotlinJs") { dependsOn(tasks.named("jsWrapMainResources")) }
tasks.named("compileTestKotlinJs") { dependsOn(tasks.named("jsWrapTestResources")) }
tasks.register("jsWrapTestResources") { doFirst { wrapJsResources(test = true) } }
tasks.register("jsWrapMainResources") { doFirst { wrapJsResources() } }

tasks.register("jsCleanResources") {
    File("${projectDir.absolutePath}/src/jsTest/generated").deleteRecursively()
    File("${projectDir.absolutePath}/src/jsMain/generated").deleteRecursively()
}


fun wrapJsResources(test: Boolean = false) {
    val prefix = if (test) "Test" else "Main"
    println("Wrapping $prefix resources into ${prefix}ResourceHolder.kt")

    val dir = File("${projectDir.absolutePath}/src/js${prefix}/generated")
    if (!dir.exists()) {
        if (!dir.mkdirs()) {
            throw Throwable("Could not create generated sources folder")
        }
    }
    val f = File("${projectDir.absolutePath}/src/js${prefix}/generated/${prefix}ResourceHolder.kt")
    f.delete()
    f.createNewFile()
    if (!f.canWrite()) {
        throw Throwable("Could not write generated source file $f")
    }
    f.writer().use { w ->
        if (!test) {
            w.write("interface R { fun get(key: String): String?;fun allResourceNames():List<String>}")
        }
        w.write(
            "object ${prefix}ResourceHolder:R{private val m = mutableMapOf<String,String>();init{\n"
        )

        val basePath = "${projectDir.absolutePath}/src/common${prefix}/resources"
        val baseFile = File(basePath)
        baseFile.walkBottomUp().filter { !it.isDirectory }.forEach {
            val encodeBase64 =
                de.undercouch.gradle.tasks.download.org.apache.commons.codec.binary.Base64.encodeBase64(it.readBytes())
            val key = it.absolutePath.substring(baseFile.absolutePath.length + 1)
            w.write(
                "m[\"${key.replace("\$", "\\\$")}\"]=\"" + String(encodeBase64) + "\"\n"
            )
        }
        w.write("}override fun get(key:String)=m[key];")
        w.write("override fun allResourceNames()=m.keys.toList()}")
    }
}