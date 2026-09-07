package com.hamster.review

import kotlinx.serialization.Serializable

sealed interface Route

@Serializable
object Main : Route

@Serializable
data class Review(
    val subjectId: Long
) : Route

@Serializable
data class AddQuestion(
    val subjectId: Long,
    val typeName: String
) : Route