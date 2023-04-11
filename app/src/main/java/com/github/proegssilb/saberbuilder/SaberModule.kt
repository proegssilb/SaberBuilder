package com.github.proegssilb.saberbuilder

import java.util.*

enum class TypeCode {
    String,
    Int,
    Float,
}

open class SaberModuleAttribute(
    val name: String,
    val bleAddress: UUID,
)

data class SaberModule(
    val name: String,
    val address: UUID,
    val attributesLoaded: Boolean = false,
    val attributes: List<SaberModuleAttribute>? = null,
    val instance: Int = 0,
)