package com.mobility.domain.demand;

// Projection interface — Spring Data maps native query columns to this
public interface ExpiredSignalSummary {
    String getCorridorCode();
    int    getSegmentIndex();
    int    getExpiredCount();
}
