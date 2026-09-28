package com.joblens.api.matching.dto;

import com.joblens.api.job.dto.JobSummary;

import java.util.List;

/**
 * One recommendation: the job, its score, and a short reason.
 *
 * <p>Carries a {@link JobSummary} rather than a job id, so a client can render
 * the list without a request per row.
 *
 * <p>The explanation is trimmed to the first couple of sentences. A list of
 * twenty jobs does not need twenty full breakdowns, and the client can fetch
 * one from {@code /jobs/{id}/match} when the user opens a row.
 */
public record RecommendedJob(
        JobSummary job,
        boolean scored,
        int score,
        List<String> explanation
) {
}
