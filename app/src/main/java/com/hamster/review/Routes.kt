package com.hamster.review

import kotlinx.serialization.Serializable

sealed interface Route

@Serializable
object Main : Route

@Serializable
object Review : Route