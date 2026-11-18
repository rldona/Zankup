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
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // android.util.Log in unit tests: a no-op instead of an exception.
    testOptions.unitTests.isReturnDefaultValues = true
}

/**
 * The API's models and Retrofit interfaces, generated on every build from the server's OpenAPI document
 * (`make generate-api` exports it from the backend). Nothing generated is committed, so it cannot go stale.
 */
val openApiSpec = rootDir.resolve("../../packages/core/src/api/openapi.json")

/**
 * The document as the generator reads it, with the changes the generator needs:
 * - It is written as OpenAPI 3.0 (see below), which has no `const`, `propertyNames` or tuples: a string
 *   `const` becomes a one-value enum, a boolean one a plain boolean (as an enum it would not compile),
 *   and a tuple a list of anything.
 * - `anyOf: [X, null]` becomes X with `nullable: true`, in a document written as OpenAPI 3.0, the only
 *   form the generator reads as nullable everywhere (array items included).
 * - Sign-in answers `Me` to the web and `AppTokens` to the app (`client: "android"`), as an `anyOf` the
 *   generator merges into one class with both sets of required fields: the app only ever gets
 *   `AppTokens`, so that is what it reads.
 */
abstract class PrepareApiSpec : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val spec: RegularFileProperty

    @get:OutputFile
    abstract val prepared: RegularFileProperty

    /** `Schema.property` of every list whose items may be null, for [ApiSources] to mark them so. */
    @get:OutputFile
    abstract val nullableItems: RegularFileProperty

    @TaskAction
    fun prepare() {
        @Suppress("UNCHECKED_CAST")
        fun clean(node: Any?): Any? = when (node) {
            is Map<*, *> -> {
                val options = node["anyOf"] as? List<*>
                val refs = options?.mapNotNull { (it as? Map<*, *>)?.get("\$ref") as? String }
                val tokens = refs?.firstOrNull { it.endsWith("/AppTokens") }
                val types = options?.map { (it as? Map<*, *>)?.get("type") }
                when {
                    tokens != null && refs.any { it.endsWith("/Me") } -> mapOf("\$ref" to tokens)
                    // `anyOf: [X, null]` is X that may be null, which the generator only reads as `nullable`
                    // (without it, a series with a GPS gap would be a list of non-null numbers).
                    options != null && options.size == 2 && types!!.count { it == "null" } == 1 -> {
                        val other = options.first { (it as? Map<*, *>)?.get("type") != "null" } as Map<String, Any?>
                        val rest = node.filterKeys { it != "anyOf" }.mapKeys { it.key as String }
                        // Beside a `$ref`, 3.0 ignores every other key: wrapped in `allOf` it keeps `nullable`.
                        val base = if ("\$ref" in other) mapOf("allOf" to listOf(other)) else other
                        clean(base + rest + ("nullable" to true))
                    }
                    else -> {
                        val map = node as Map<String, Any?>
                        val const = map["const"]
                        map
                            .filterKeys { it != "const" && it != "propertyNames" && it != "prefixItems" }
                            .mapValues { (_, value) -> clean(value) }
                            // 3.0 has no `const`: a string one is a one-value enum, a boolean one just a boolean.
                            .plus(if (const is String) mapOf("enum" to listOf(const)) else emptyMap())
                            // Nor tuples (`prefixItems`, a route's [lat, lon] pairs): a list of anything.
                            .plus(
                                if ("prefixItems" in map &&
                                    "items" !in map
                                ) {
                                    mapOf("items" to emptyMap<String, Any?>())
                                } else {
                                    emptyMap()
                                }
                            )
                    }
                }
            }
            is List<*> -> node.map(::clean)
            else -> node
        }
        val document = groovy.json.JsonSlurper().parse(spec.get().asFile) as Map<String, Any?>
        val schemas = ((document["components"] as Map<String, Any?>)["schemas"] as Map<String, Map<String, Any?>>)
        fun nullItems(property: Map<*, *>): Boolean {
            val list =
                (property["anyOf"] as? List<*>)?.firstOrNull {
                    (it as? Map<*, *>)?.get("type") == "array"
                } as? Map<*, *>
                    ?: property
            val items = list["items"] as? Map<*, *> ?: return false
            return (items["anyOf"] as? List<*>)?.any { (it as? Map<*, *>)?.get("type") == "null" } == true
        }
        nullableItems.get().asFile.writeText(
            schemas
                .flatMap { (name, schema) ->
                    (schema["properties"] as? Map<String, Map<*, *>>).orEmpty().filterValues(::nullItems).keys.map {
                        "$name.$it"
                    }
                }.joinToString("\n")
        )
        // Written as OpenAPI 3.0, where the generator honours `nullable` (3.1 dropped it for type lists).
        prepared.get().asFile.writeText(groovy.json.JsonOutput.toJson(clean(document + ("openapi" to "3.0.3"))))
    }
}

/** The generated Kotlin, handed to the Kotlin compiler as a generated source directory. */
abstract class ApiSources : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generated: DirectoryProperty

    @get:InputFile
    abstract val nullableItems: RegularFileProperty

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
        // Number defaults come out as `kotlin.Double("0")`, the BigDecimal template: written as Double literals.
        val literal = Regex("""kotlin\.Double\("([-0-9.eE]+)"\)""")
        outputDirectory.get().asFile.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            if (literal.containsMatchIn(text)) {
                file.writeText(literal.replace(text) { it.groupValues[1].toDouble().toString() })
            }
        }
        // The generator drops `null` from list items: a series with a GPS gap must read as List<Double?>.
        val models = outputDirectory.get().asFile.walkTopDown().filter {
            it.parentFile.name == "model"
        }.associateBy { it.nameWithoutExtension }
        nullableItems.get().asFile.readLines().filter { it.isNotBlank() }.forEach { entry ->
            val (schema, property) = entry.split('.', limit = 2)
            val file = models[schema] ?: models[schema.replace("_", "")] ?: return@forEach
            val lines = file.readLines().toMutableList()
            val at = lines.indexOfFirst { it.contains("@SerialName(value = \"$property\")") }
            if (at >= 0 && at + 1 < lines.size) {
                lines[at + 1] = lines[at + 1].replace(Regex("""List<([\w.]+)>"""), "List<$1?>")
                file.writeText(lines.joinToString("\n") + "\n")
            }
        }
    }
}

val prepareApiSpec by tasks.registering(PrepareApiSpec::class) {
    spec.set(openApiSpec)
    prepared.set(layout.buildDirectory.file("openapi/openapi.json"))
    nullableItems.set(layout.buildDirectory.file("openapi/nullable-items.txt"))
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
    // Numbers as Double: the default BigDecimal needs a contextual serializer and is slow for 1 Hz series.
    typeMappings.set(mapOf("AnyType" to "JsonElement", "number" to "kotlin.Double", "decimal" to "kotlin.Double"))
    importMappings.set(mapOf("JsonElement" to "kotlinx.serialization.json.JsonElement"))
    inputs.file(prepareApiSpec.flatMap { it.prepared })
    outputs.dir(generatedApi)
}

val apiSources by tasks.registering(ApiSources::class) {
    dependsOn(generateApi)
    generated.set(generatedApi)
    nullableItems.set(prepareApiSpec.flatMap { it.nullableItems })
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
