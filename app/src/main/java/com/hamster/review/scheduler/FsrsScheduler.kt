package com.hamster.review.scheduler

import com.hamster.review.data.db.SchedulerStateEntity
import com.hamster.review.data.model.CardState
import com.hamster.review.data.model.DEFAULT_RETENTION
import com.hamster.review.data.model.FsrsRating
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object FsrsScheduler {

    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0
    private const val DAY_MS = 24.0 * 60.0 * 60.0 * 1000.0

    private val w = doubleArrayOf(
        0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046,
        1.54575, 0.1192, 1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315,
        2.9898, 0.51655, 0.6621
    )

    fun ratingToGrade(rating: FsrsRating): Int = when (rating) {
        FsrsRating.AGAIN -> 1
        FsrsRating.HARD -> 2
        FsrsRating.GOOD -> 3
        FsrsRating.EASY -> 4
    }

    fun retrievability(state: SchedulerStateEntity, now: Long): Double {
        val lastReviewAt = state.lastReviewAt ?: return 1.0
        val elapsedDays = (now - lastReviewAt).toDouble() / DAY_MS
        if (state.stability <= 0.0 || elapsedDays <= 0.0) return 1.0
        val r = (1.0 + FACTOR * elapsedDays / state.stability).pow(DECAY)
        return r.coerceIn(0.0, 1.0)
    }

    fun intervalForRetention(stability: Double, retention: Double = DEFAULT_RETENTION): Double {
        val safeRetention = retention.coerceIn(0.01, 0.99)
        val interval = (stability / FACTOR) * (safeRetention.pow(1.0 / DECAY) - 1.0)
        return max(interval, 1.0)
    }

    fun review(
        state: SchedulerStateEntity?,
        rating: FsrsRating,
        now: Long
    ): SchedulerStateEntity {
        val grade = ratingToGrade(rating)

        if (state == null) {
            return createInitialState(rating, grade, now)
        }

        val currentR = retrievability(state, now)
        val newDifficulty = nextDifficulty(state.difficulty, grade)
        val newStability = if (grade == 1) {
            nextShortTermStability(state.stability, currentR, grade)
        } else {
            nextStability(state.difficulty, state.stability, currentR, grade)
        }

        val interval = intervalForRetention(newStability)
        val dueDate = now + (interval * DAY_MS).toLong()

        return SchedulerStateEntity(
            questionId = state.questionId,
            state = if (grade == 1) CardState.LEARNING else CardState.REVIEW,
            dueDate = dueDate,
            stability = newStability,
            difficulty = newDifficulty,
            retrievability = 1.0,
            reps = state.reps + 1,
            lapses = if (grade == 1) state.lapses + 1 else state.lapses,
            lastReviewAt = now
        )
    }

    private fun createInitialState(rating: FsrsRating, grade: Int, now: Long): SchedulerStateEntity {
        val difficulty = nextDifficulty(0.0, grade).coerceIn(1.0, 10.0)
        val stability = max(w[0] + w[1] * (grade - 1), 0.1)
        val interval = intervalForRetention(stability)
        val dueDate = now + (interval * DAY_MS).toLong()

        return SchedulerStateEntity(
            questionId = 0L,
            state = if (rating == FsrsRating.AGAIN) CardState.LEARNING else CardState.REVIEW,
            dueDate = dueDate,
            stability = stability,
            difficulty = difficulty,
            retrievability = 1.0,
            reps = 1,
            lapses = if (rating == FsrsRating.AGAIN) 1 else 0,
            lastReviewAt = now
        )
    }

    private fun nextDifficulty(d: Double, grade: Int): Double {
        val init = w[4] - exp(w[5] * (grade - 1)) + 1.0
        val next = d - w[6] * (grade - 3)
        val reverted = w[7] * init + (1.0 - w[7]) * next
        return reverted.coerceIn(1.0, 10.0)
    }

    private fun nextStability(d: Double, s: Double, r: Double, grade: Int): Double {
        val hardPenalty = if (grade == 2) w[15] else 1.0
        val easyBonus = if (grade == 4) w[16] else 1.0
        val nextS = s * (
            1.0 +
                exp(w[8]) *
                (11.0 - d) *
                s.pow(-w[9]) *
                (exp((1.0 - r) * w[10]) - 1.0) *
                hardPenalty *
                easyBonus
            )
        return nextS.coerceIn(0.1, 36500.0)
    }

    private fun nextShortTermStability(s: Double, r: Double, grade: Int): Double {
        val nextS = s * exp(w[17] * (grade - 3 + w[18]) * (1.0 - r))
        return nextS.coerceIn(0.1, 36500.0)
    }
}
