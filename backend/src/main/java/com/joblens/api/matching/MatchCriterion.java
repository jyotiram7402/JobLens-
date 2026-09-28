package com.joblens.api.matching;

/**
 * The five things a match is judged on.
 *
 * <p>An enum rather than strings so that adding a criterion is a compile error
 * everywhere it needs handling -- the weights, the engine, the explanation --
 * instead of a silently missing case.
 */
public enum MatchCriterion {

    SKILLS("skills"),
    EXPERIENCE("experience"),
    LOCATION("location"),
    ROLE("role"),
    WORK_MODE("workMode");

    private final String jsonName;

    MatchCriterion(String jsonName) {
        this.jsonName = jsonName;
    }

    /** The key this criterion appears under in a response body. */
    public String jsonName() {
        return jsonName;
    }
}
