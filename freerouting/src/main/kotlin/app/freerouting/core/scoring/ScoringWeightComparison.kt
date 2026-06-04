package app.freerouting.core.scoring

import app.freerouting.settings.RouterScoringSettings

class ScoringWeightComparison private constructor() {
    companion object {
        @JvmStatic
        fun compare(
            stats: BoardStatistics,
            weightsA: RouterScoringSettings,
            weightsB: RouterScoringSettings
        ): Result {
            val breakdownA = BoardScoreBreakdown.of(stats, weightsA)
            val breakdownB = BoardScoreBreakdown.of(stats, weightsB)
            return Result(breakdownA, breakdownB)
        }
    }

    class Result internal constructor(
        @JvmField val scoreA: BoardScoreBreakdown,
        @JvmField val scoreB: BoardScoreBreakdown
    ) {
        @JvmField val rawScoreDelta: Float = scoreB.rawScore - scoreA.rawScore
        @JvmField val normalizedScoreDelta: Float = scoreB.normalizedScore - scoreA.normalizedScore
        @JvmField val unroutedPenaltyDelta: Float = scoreB.unroutedConnectionsPenalty - scoreA.unroutedConnectionsPenalty
        @JvmField val clearancePenaltyDelta: Float = scoreB.clearanceViolationsPenalty - scoreA.clearanceViolationsPenalty
        @JvmField val bendsPenaltyDelta: Float = scoreB.bendsPenalty - scoreA.bendsPenalty
        @JvmField val traceLengthCostDelta: Float = scoreB.traceLengthCost - scoreA.traceLengthCost
        @JvmField val viasCostDelta: Float = scoreB.viasCost - scoreA.viasCost

        fun isCandidateBetter(): Boolean {
            return normalizedScoreDelta > 0
        }

        fun toReportString(): String {
            val a = scoreA
            val b = scoreB

            val header = String.format(
                "%n╔══ Scoring Weight Comparison ══════════════════════════════════════╗"
                    + "%n║ Board:  %d connections, %d unrouted, %d violations, %d bends"
                    + "%n║         total trace %.1f mm, %d vias",
                a.maxConnections, a.incompleteConnections,
                a.clearanceViolations, a.bendCount,
                a.totalTraceLengthMm, a.viaCount
            )

            val tableHeader = String.format(
                "%n╠══ Component breakdown ═════════════════════════════════════════════╣"
                    + "%n║ %-34s %12s  %12s  %10s",
                "", "A (baseline)", "B (candidate)", "Delta"
            )

            val rows = String.format(
                "%n║ %-34s %12.1f  %12.1f  %+10.1f"
                    + "%n║ %-34s %12.1f  %12.1f  %+10.1f   (w: %.0f → %.0f)"
                    + "%n║ %-34s %12.1f  %12.1f  %+10.1f   (w: %.0f → %.0f)"
                    + "%n║ %-34s %12.1f  %12.1f  %+10.1f   (w: %.1f → %.1f)"
                    + "%n║ %-34s %12.1f  %12.1f  %+10.1f   (w: %.2f → %.2f)"
                    + "%n║ %-34s %12.1f  %12.1f  %+10.1f   (w: %.0f → %.0f)",
                "Maximum score",
                a.maximumScore, b.maximumScore, b.maximumScore - a.maximumScore,
                "Unrouted penalty",
                a.unroutedConnectionsPenalty, b.unroutedConnectionsPenalty, unroutedPenaltyDelta,
                a.weights.unroutedNetPenalty ?: 0f, b.weights.unroutedNetPenalty ?: 0f,
                "Clearance penalty",
                a.clearanceViolationsPenalty, b.clearanceViolationsPenalty, clearancePenaltyDelta,
                a.weights.clearanceViolationPenalty ?: 0f, b.weights.clearanceViolationPenalty ?: 0f,
                "Bend penalty",
                a.bendsPenalty, b.bendsPenalty, bendsPenaltyDelta,
                a.weights.bendPenalty ?: 0f, b.weights.bendPenalty ?: 0f,
                "Trace length cost",
                a.traceLengthCost, b.traceLengthCost, traceLengthCostDelta,
                a.weights.defaultPreferredDirectionTraceCost ?: 0.0, b.weights.defaultPreferredDirectionTraceCost ?: 0.0,
                "Via cost",
                a.viasCost, b.viasCost, viasCostDelta,
                (a.weights.viaCosts ?: 0).toDouble(), (b.weights.viaCosts ?: 0).toDouble()
            )

            val totalsAndScores = String.format(
                "%n╠════════════════════════════════════════════════════════════════════╣"
                    + "%n║ %-34s %12.1f  %12.1f  %+10.1f"
                    + "%n║ %-34s %12.1f  %12.1f  %+10.1f",
                "Raw score", a.rawScore, b.rawScore, rawScoreDelta,
                "Normalised score (0–1000)", a.normalizedScore, b.normalizedScore, normalizedScoreDelta
            )

            val footer = "%n╚═══════════════════════════════════════════════════════════════════╝"

            val verdict = if (Math.abs(normalizedScoreDelta) < 0.01f) {
                "Verdict: A and B produce identical scores for this board."
            } else if (isCandidateBetter()) {
                String.format("Verdict: B (candidate) is better by %.1f normalised points.", normalizedScoreDelta)
            } else {
                String.format("Verdict: A (baseline) is better by %.1f normalised points.", -normalizedScoreDelta)
            }

            return header + tableHeader + rows + totalsAndScores + String.format(footer) + "\n" + verdict
        }
    }
}
