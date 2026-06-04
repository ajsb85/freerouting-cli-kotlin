package app.freerouting.core.scoring

import app.freerouting.settings.RouterScoringSettings

/**
 * An immutable, per-component breakdown of a single board-score calculation.
 *
 * <p>Use [app.freerouting.core.scoring.ScoringWeightComparison] to produce instances, or call
 * [of] directly.
 */
class BoardScoreBreakdown private constructor(
    @JvmField val weights: RouterScoringSettings,
    @JvmField val maxConnections: Int,
    @JvmField val incompleteConnections: Int,
    @JvmField val clearanceViolations: Int,
    @JvmField val bendCount: Int,
    @JvmField val totalTraceLengthMm: Float,
    @JvmField val viaCount: Int
) {
    @JvmField val maximumScore: Float = maxConnections * weights.unroutedNetPenalty!!
    @JvmField val unroutedConnectionsPenalty: Float = incompleteConnections * weights.unroutedNetPenalty!!
    @JvmField val clearanceViolationsPenalty: Float = clearanceViolations * weights.clearanceViolationPenalty!!
    @JvmField val bendsPenalty: Float = bendCount * weights.bendPenalty!!
    @JvmField val traceLengthCost: Float = (totalTraceLengthMm * weights.defaultPreferredDirectionTraceCost!!).toFloat()
    @JvmField val viasCost: Float = (viaCount * weights.viaCosts!!).toFloat()

    @JvmField val totalPenalties: Float = unroutedConnectionsPenalty + clearanceViolationsPenalty + bendsPenalty
    @JvmField val totalCosts: Float = traceLengthCost + viasCost

    @JvmField val rawScore: Float = maximumScore - totalPenalties - totalCosts
    @JvmField val normalizedScore: Float = if (maximumScore > 0f) {
        Math.max(0f, rawScore / maximumScore) * 1000f
    } else {
        0f
    }

    /**
     * Returns a concise human-readable summary of this breakdown, useful for logging
     * and test output.
     */
    fun toSummaryString(): String {
        return String.format(
            "score=%.1f/1000 (raw=%.0f/%.0f) | "
                + "unrouted=%d×%.0f=%.0f | violations=%d×%.0f=%.0f | bends=%d×%.1f=%.0f | "
                + "length=%.1fmm×%.2f=%.0f | vias=%d×%.0f=%.0f",
            normalizedScore, rawScore, maximumScore,
            incompleteConnections, weights.unroutedNetPenalty!!, unroutedConnectionsPenalty,
            clearanceViolations, weights.clearanceViolationPenalty!!, clearanceViolationsPenalty,
            bendCount, weights.bendPenalty!!, bendsPenalty,
            totalTraceLengthMm, weights.defaultPreferredDirectionTraceCost!!, traceLengthCost,
            viaCount, weights.viaCosts!!.toDouble(), viasCost
        )
    }

    companion object {
        /**
         * Computes a [BoardScoreBreakdown] for [stats] using [weights].
         *
         * @param stats   board statistics (must not be null; lengths assumed to be in millimetres)
         * @param weights scoring weight configuration (must not be null, all scoring fields must be set)
         * @return a fully populated breakdown
         * @throws NullPointerException     if either argument is null
         * @throws IllegalArgumentException if a required weight field is null
         */
        @JvmStatic
        fun of(stats: BoardStatistics, weights: RouterScoringSettings): BoardScoreBreakdown {
            validateWeights(weights)

            return BoardScoreBreakdown(
                weights,
                stats.connections.maximumCount ?: 0,
                stats.connections.incompleteCount ?: 0,
                stats.clearanceViolations.totalCount ?: 0,
                stats.bends.totalCount ?: 0,
                stats.traces.totalLength ?: 0f,
                stats.vias.totalCount ?: 0
            )
        }

        private fun validateWeights(w: RouterScoringSettings) {
            requireNotNull(w.unroutedNetPenalty) { "weights.unroutedNetPenalty must not be null" }
            requireNotNull(w.clearanceViolationPenalty) { "weights.clearanceViolationPenalty must not be null" }
            requireNotNull(w.bendPenalty) { "weights.bendPenalty must not be null" }
            requireNotNull(w.defaultPreferredDirectionTraceCost) { "weights.defaultPreferredDirectionTraceCost must not be null" }
            requireNotNull(w.viaCosts) { "weights.viaCosts must not be null" }
        }
    }
}
