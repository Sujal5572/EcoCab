package com.mobility.domain.driver.guidance;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

// domain/driver/guidance/GuidanceEngine.java
@Component
@Slf4j
public class GuidanceEngine {

    /**
     * How many segments ahead the driver can "see" in the algorithm.
     * Beyond this, demand data is too uncertain to act on.
     */
    private static final int LOOKAHEAD_SEGMENTS = 3;

    /**
     * Demand-per-driver ratio thresholds — externalize to config in prod.
     */
    private static final double SURGE_THRESHOLD       = 6.0;
    private static final double HIGH_THRESHOLD        = 3.0;
    private static final double LOW_THRESHOLD         = 1.0;

    /**
     * Distance decay factor: demand one segment ahead is worth 100%,
     * two segments ahead is worth 70%, three is worth 50%.
     * Prevents drivers blindly racing to distant surge zones.
     */
    private static final double[] DISTANCE_DECAY = {1.0, 0.7, 0.5};

    /**
     * Maximum drivers allowed per segment before it's considered overcrowded.
     * In real prod this comes from corridor_segments.max_driver_capacity.
     */
    private static final int DEFAULT_MAX_DRIVERS_PER_SEGMENT = 5;

    // ---------------------------------------------------------------
    // MAIN ENTRY POINT
    // ---------------------------------------------------------------

    /**
     * Called by DriverService on every location ping.
     * Runs in < 5ms (pure in-memory computation on already-fetched Redis data).
     *
     * @param context  all data needed — driver state + full corridor heatmap
     * @return         GuidanceResult — what the driver should do and why
     */
    public GuidanceResult compute(GuidanceContext context) {
        int currentSeg     = context.getCurrentSegmentIndex();
        int totalSegments  = context.getTotalSegments();
        List<SegmentSnapshot> segments = context.getSegments();

        // ── Guard: corridor end approaching ─────────────────────────
        if (currentSeg >= totalSegments - 2) {
            return GuidanceResult.builder()
                    .action(GuidanceAction.RETURN)
                    .recommendedSegment(0)
                    .reason("Approaching corridor end — loop back to start")
                    .urgency(Urgency.LOW)
                    .build();
        }

        SegmentSnapshot current = segments.get(currentSeg);

        // ── Guard: current segment overcrowded ───────────────────────
        int maxCap = context.getMaxDriverCapacity() > 0
                ? context.getMaxDriverCapacity()
                : DEFAULT_MAX_DRIVERS_PER_SEGMENT;

        if (current.getDriverCount() > maxCap) {
            int bestSeg = findBestUndercrowdedSegment(segments, currentSeg, totalSegments);
            return GuidanceResult.builder()
                    .action(GuidanceAction.REDISTRIBUTE)
                    .recommendedSegment(bestSeg)
                    .reason(String.format(
                            "Segment %d overcrowded (%d drivers). Move to segment %d.",
                            currentSeg, current.getDriverCount(), bestSeg))
                    .urgency(Urgency.HIGH)
                    .build();
        }

        // ── Score segments in lookahead window ──────────────────────
        ScoredSegment bestAhead = scoreSegmentsAhead(segments, currentSeg, totalSegments);

        // ── Check for SURGE ahead ────────────────────────────────────
        if (bestAhead != null && bestAhead.getRawRatio() >= SURGE_THRESHOLD) {
            return GuidanceResult.builder()
                    .action(GuidanceAction.SURGE_FORWARD)
                    .recommendedSegment(bestAhead.getIndex())
                    .reason(String.format(
                            "SURGE detected at segment %d (ratio %.1f). Accelerate now.",
                            bestAhead.getIndex(), bestAhead.getRawRatio()))
                    .urgency(Urgency.CRITICAL)
                    .demandAhead(totalDemandAhead(segments, currentSeg, totalSegments))
                    .build();
        }

        // ── Check if moving forward is worthwhile ────────────────────
        double currentScore = weightedScore(current);
        if (bestAhead != null && bestAhead.getWeightedScore() > currentScore * 1.2) {
            // 20% improvement threshold — avoids jitter from tiny ratio differences
            return GuidanceResult.builder()
                    .action(GuidanceAction.SPEED_UP)
                    .recommendedSegment(bestAhead.getIndex())
                    .reason(String.format(
                            "Higher demand ahead at segment %d (ratio %.1f). Move forward.",
                            bestAhead.getIndex(), bestAhead.getRawRatio()))
                    .urgency(Urgency.MEDIUM)
                    .demandAhead(totalDemandAhead(segments, currentSeg, totalSegments))
                    .build();
        }

        // ── Check if there's better demand behind (missed pickup) ────
        ScoredSegment bestBehind = scoreSegmentsBehind(segments, currentSeg);
        if (bestBehind != null && bestBehind.getWeightedScore() > currentScore * 1.5) {
            // Higher threshold behind — we only slow down if significantly better
            return GuidanceResult.builder()
                    .action(GuidanceAction.SLOW_DOWN)
                    .recommendedSegment(bestBehind.getIndex())
                    .reason(String.format(
                            "High unserved demand behind at segment %d. Slow down.",
                            bestBehind.getIndex()))
                    .urgency(Urgency.LOW)
                    .build();
        }

        // ── Default: conditions are fine, keep going ─────────────────
        return GuidanceResult.builder()
                .action(GuidanceAction.CONTINUE)
                .recommendedSegment(currentSeg + 1)
                .reason(String.format(
                        "Demand balanced (current ratio %.1f). Maintain pace.",
                        current.getRatio()))
                .urgency(Urgency.NONE)
                .demandAhead(totalDemandAhead(segments, currentSeg, totalSegments))
                .build();
    }

    // ---------------------------------------------------------------
    // PRIVATE HELPERS
    // ---------------------------------------------------------------

    /**
     * Scan LOOKAHEAD_SEGMENTS ahead, apply distance decay to each score,
     * return the best-scoring segment.
     */
    private ScoredSegment scoreSegmentsAhead(List<SegmentSnapshot> segments,
                                             int currentSeg,
                                             int totalSegments) {
        ScoredSegment best = null;

        for (int i = 1; i <= LOOKAHEAD_SEGMENTS; i++) {
            int targetSeg = currentSeg + i;
            if (targetSeg >= totalSegments) break;

            SegmentSnapshot seg = segments.get(targetSeg);
            double decay = DISTANCE_DECAY[i - 1];
            double score = weightedScore(seg) * decay;

            if (best == null || score > best.getWeightedScore()) {
                best = ScoredSegment.builder()
                        .index(targetSeg)
                        .rawRatio(seg.getRatio())
                        .weightedScore(score)
                        .build();
            }
        }
        return best;
    }

    /**
     * Look 1 segment behind — if the driver passed a high-demand zone
     * without picking anyone up, slow down so passengers can catch them.
     */
    private ScoredSegment scoreSegmentsBehind(List<SegmentSnapshot> segments,
                                              int currentSeg) {
        if (currentSeg <= 0) return null;
        SegmentSnapshot behind = segments.get(currentSeg - 1);
        return ScoredSegment.builder()
                .index(currentSeg - 1)
                .rawRatio(behind.getRatio())
                .weightedScore(weightedScore(behind) * 0.8) // slight decay
                .build();
    }

    /**
     * Find the nearest segment with driver count below capacity.
     * Used for overcrowding redistribution.
     */
    private int findBestUndercrowdedSegment(List<SegmentSnapshot> segments,
                                            int currentSeg,
                                            int totalSegments) {
        // Prefer higher demand, but must not be overcrowded itself
        int bestSeg = currentSeg + 1;
        double bestScore = -1;

        for (int i = 0; i < totalSegments; i++) {
            if (i == currentSeg) continue;
            SegmentSnapshot s = segments.get(i);
            if (s.getDriverCount() < DEFAULT_MAX_DRIVERS_PER_SEGMENT) {
                double score = weightedScore(s) - (Math.abs(i - currentSeg) * 0.1);
                if (score > bestScore) {
                    bestScore = score;
                    bestSeg = i;
                }
            }
        }
        return bestSeg;
    }

    /**
     * Weighted score for a segment.
     * Combines demand-per-driver ratio with absolute demand count.
     *
     * Pure ratio ignores segments with demand=1, drivers=0 (ratio=1 but trivial).
     * Blending in raw demand count prevents chasing ghost signals.
     */
    private double weightedScore(SegmentSnapshot seg) {
        double ratio  = seg.getRatio();
        int    demand = seg.getDemandCount();

        // Blend: 70% ratio signal + 30% absolute demand signal
        // Scale demand to 0-10 range (cap at 20 passengers)
        double demandScore = Math.min(demand, 20) / 2.0;
        return (ratio * 0.7) + (demandScore * 0.3);
    }

    private int totalDemandAhead(List<SegmentSnapshot> segments,
                                 int currentSeg,
                                 int totalSegments) {
        int total = 0;
        for (int i = currentSeg + 1; i < Math.min(currentSeg + LOOKAHEAD_SEGMENTS + 1,
                totalSegments); i++) {
            total += segments.get(i).getDemandCount();
        }
        return total;
    }
}