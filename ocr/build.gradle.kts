import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// On-device OCR: PaddleOCR's PP-OCRv6 models on ONNX Runtime, and the
// sequence of reads that turns a photo of a result screen into a parse.
// Plain Kotlin like :core, so the exact code the phone runs is tested here
// on photos of real screens.
kotlin {
    jvmToolchain(17)
}

/**
 * PP-OCRv6 "small" text detection and recognition (Apache-2.0, converted to
 * ONNX by RapidOCR). The recogniser reads Japanese and Latin in one model and
 * carries its character list in the model's metadata. Downloaded once and
 * checked against these hashes; models/ is gitignored.
 */
val ocrModels = mapOf(
    "PP-OCRv6_det_small.onnx" to "090f04abcd9d9a7498bc4ebf677e4cb9bdce1fe4197ddb7e529f1ef44e1ff94f",
    "PP-OCRv6_rec_small.onnx" to "6f327246b50388f3c176ae304bd95767ea6dc0c9ae92153ef8cbe210b3c14884",
)
val ocrModelBaseUrl = "https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.10.0/onnx/PP-OCRv6"
val modelsDir = layout.projectDirectory.dir("models")

val downloadOcrModels by tasks.registering {
    description = "Downloads the PP-OCRv6 models into ocr/models and verifies their SHA-256."
    val dir = modelsDir.asFile
    outputs.files(ocrModels.keys.map { File(dir, it) })
    doLast {
        dir.mkdirs()
        for ((name, sha256) in ocrModels) {
            val file = File(dir, name)
            fun hash() = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            if (file.exists() && hash() == sha256) continue
            val kind = if ("_det_" in name) "det" else "rec"
            logger.lifecycle("Downloading $name")
            val partial = File(dir, "$name.part")
            URI("$ocrModelBaseUrl/$kind/$name").toURL().openStream().use { input ->
                partial.outputStream().use { input.copyTo(it) }
            }
            partial.renameTo(file)
            check(hash() == sha256) { "$name: checksum mismatch; delete ocr/models and try again" }
        }
    }
}

tasks.test {
    dependsOn(downloadOcrModels)
    systemProperty("ocr.models", modelsDir.asFile.absolutePath)
    // The photos of real result screens that RealPhotoTest reads.
    systemProperty("samples.dir", rootProject.file("samples").absolutePath)
    maxHeapSize = "3g"
    // For OcrBench: e.g. OCR_JVMARGS=-Xint to time the reader without the JIT.
    System.getenv("OCR_JVMARGS")?.let { jvmArgs(it.split(" ")) }
}

dependencies {
    api(project(":core"))
    // The app brings onnxruntime-android, which has the same Java API.
    compileOnly(libs.onnxruntime)

    testImplementation(libs.onnxruntime)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
