package com.hamster.review

import kotlinx.serialization.Serializable

sealed interface Route

@Serializable
object Main : Route

@Serializable
data class Review(
    val subjectId: Long
) : Route