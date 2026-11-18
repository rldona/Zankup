import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.openapi.generator)
}

android {
    namespace = "com.zankup.android.network"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

/**
 * The API's models and Retrofit interfaces, generated on every build from the server's OpenAPI document
 * (`make generate-api` exports it from the backend). Nothing generated is committed, so it cannot go stale.
 */
val openApiSpec = rootDir.resolve("../../packages/core/src/api/openapi.json")

/**
 * The document as the generator reads it. Pydantic writes `"const": true` for a boolean that is always
 * true (`Challenge.mfa_required`), which openapi-generator turns into a one-value enum that does not
 * compile; the const is dropped and the field stays a plain boolean.
 */
abstract class PrepareApiSpec : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val spec: RegularFileProperty

    @get:OutputFile
    abstract val prepared: RegularFileProperty

    @TaskAction
    fun prepare() {
        @Suppress("UNCHECKED_CAST")
        fun clean(node: Any?): Any? = when (node) {
            is Map<*, *> ->
                (node as Map<String, Any?>)
                    .filterNot { (key, _) -> key == "const" && node["type"] == "boolean" }
                    .mapValues { (_, value) -> clean(value) }
            is List<*> -> node.map(::clean)
            else -> node
        }
        val document = groovy.json.JsonSlurper().parse(spec.get().asFile)
        prepared.get().asFile.writeText(groovy.json.JsonOutput.toJson(clean(document)))
    }
}

/** The generated Kotlin, handed to the Kotlin compiler as a generated source directory. */
abstract class ApiSources : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generated: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:javax.inject.Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun copy() {
        files.sync {
            from(generated.dir("src/main/kotlin"))
            into(outputDirectory)
        }
    }
}

val prepareApiSpec by tasks.registering(PrepareApiSpec::class) {
    spec.set(openApiSpec)
    prepared.set(layout.buildDirectory.file("openapi/openapi.json"))
}

val generatedApi = layout.buildDirectory.dir("openapi/generator")

val generateApi by tasks.registering(GenerateTask::class) {
    dependsOn(prepareApiSpec)
    generatorName.set("kotlin")
    library.set("jvm-retrofit2")
    inputSpec.set(prepareApiSpec.flatMap { it.prepared }.map { it.asFile.absolutePath })
    outputDir.set(generatedApi.map { it.asFile.absolutePath })
    packageName.set("com.zankup.android.api")
    apiPackage.set("com.zankup.android.api.endpoints")
    modelPackage.set("com.zankup.android.api.model")
    cleanupOutput.set(true)
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    configOptions.set(
        mapOf(
            "serializationLibrary" to "kotlinx_serialization",
            "useCoroutines" to "true",
            "dateLibrary" to "string",
            "enumPropertyNaming" to "UPPERCASE",
            "enumUnknownDefaultCase" to "true",
            "omitGradleWrapper" to "true",
            "omitGradlePluginVersions" to "true",
            "sourceFolder" to "src/main/kotlin"
        )
    )
    // Free-form JSON (passkey credentials, event payloads, a setting's value) as kotlinx's JsonElement.
    typeMappings.set(mapOf("AnyType" to "JsonElement"))
    importMappings.set(mapOf("JsonElement" to "kotlinx.serialization.json.JsonElement"))
    inputs.file(prepareApiSpec.flatMap { it.prepared })
    outputs.dir(generatedApi)
}

val apiSources by tasks.registering(ApiSources::class) {
    dependsOn(generateApi)
    generated.set(generatedApi)
    outputDirectory.set(layout.buildDirectory.dir("generated/api"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.kotlin?.addGeneratedSourceDirectory(apiSources, ApiSources::outputDirectory)
    }
}

dependencies {
    api(libs.okhttp)
    api(libs.retrofit)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.retrofit.scalars)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
