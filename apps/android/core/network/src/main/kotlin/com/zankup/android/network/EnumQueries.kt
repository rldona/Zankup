package com.zankup.android.network

import java.lang.reflect.Type
import kotlinx.serialization.SerialName
import retrofit2.Converter
import retrofit2.Retrofit

/**
 * Enums in a query or a path are written as the API names them (`kind=power`), not as their Kotlin
 * constant (`POWER`), which is what Retrofit would send: the generated enums keep their API names only
 * in each constant's `@SerialName` (kept in release builds by `consumer-rules.pro`).
 */
internal object EnumQueries : Converter.Factory() {
    override fun stringConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<*, String>? {
        val enum = (type as? Class<*>)?.takeIf { it.isEnum } ?: return null
        val names =
            enum.declaredFields
                .filter { it.isEnumConstant }
                .associate { field ->
                    field.get(null) to
                        (field.getAnnotation(SerialName::class.java)?.value ?: field.name)
                }
        return Converter<Any, String> { value -> names[value] ?: value.toString() }
    }
}
