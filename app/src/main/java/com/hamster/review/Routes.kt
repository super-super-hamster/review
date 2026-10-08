package com.hamster.review

import kotlinx.serialization.Serializable

sealed interface Route

@Serializable
object Main : Route

@Serializable
data class Review(
    val subjectId: Long,
    val mode: String = "daily"
) : Route

@Serializable
data class AddQuestion(
    val subjectId: Long,
    val typeName: String,
    val questionId: Long = 0L
) : Route

@Serializable
data class ManageQuestions(
    val subjectId: Long
) : Route