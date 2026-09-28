package com.joblens.api.matching;

import com.joblens.api.common.response.PageResponse;
import com.joblens.api.common.text.TextNormalizer;
import com.joblens.api.job.JobRepository;
import com.joblens.api.job.domain.Job;
import com.joblens.api.job.dto.JobSummary;
import com.joblens.api.job.exception.JobNotFoundException;
import com.joblens.api.matching.dto.MatchResponse;
import com.joblens.api.matching.dto.RecommendedJob;
import com.joblens.api.matching.exception.ProfileNotReadyException;
import com.joblens.api.skill.domain.Skill;
import com.joblens.api.user.UserProfileRepository;
import com.joblens.api.user.domain.UserProfile;
import com.joblens.api.user.exception.UserNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads the data a match needs, hands it to the engine, and assembles the
 * response.
 *
 * <p>The split matters: {@link JobMatchingEngine} holds the rules and touches no
 * database, this class touches the database and holds no rules. Changing how
 * experience is scored means editing one pure method with fast tests around it;
 * changing how candidates are fetched means editing here.
 */
@Service
public class JobMatchingService {

    /**
     * How many recent openings a recommendation request scores.
     *
     * <p>Recommendations work over a bounded candidate set, not the whole table:
     * take the most recent active jobs, score them, sort by score. That keeps
     * one request to one page-sized query plus one skills query, whatever the
     * table grows to.
     *
     * <p>The limitation is real and worth stating. A strong match posted a year
     * ago, beyond the window, will not be found. Fixing that properly means
     * narrowing candidates by something better than recency -- jobs sharing at
     * least one of the user's skills, which {@code ix_job_skills_skill_id}
     * already supports -- or precomputing scores. Both are worth doing when
     * there are enough jobs for the window to bite; neither is worth doing now.
     */
    private static final int CANDIDATE_LIMIT = 200;

    private final JobRepository jobRepository;
    private final UserProfileRepository userProfileRepository;
    private final JobMatchingEngine engine;
    private final MatchExplanationWriter explanationWriter;

    public JobMatchingService(JobRepository jobRepository,
                              UserProfileRepository userProfileRepository,
                              JobMatchingEngine engine,
                              MatchExplanationWriter explanationWriter) {
        this.jobRepository = jobRepository;
        this.userProfileRepository = userProfileRepository;
        this.engine = engine;
        this.explanationWriter = explanationWriter;
    }

    /**
     * Scores one job against the signed-in user.
     *
     * @param userId always from the security context, never from the request
     * @throws UserNotFoundException     if the profile is missing entirely
     * @throws ProfileNotReadyException  if the profile has nothing to match on
     * @throws JobNotFoundException      if the job does not exist
     */
    @Transactional(readOnly = true)
    public MatchResponse match(UUID userId, UUID jobId) {
        MatchProfile profile = loadProfile(userId);

        Job job = jobRepository.findWithCompanyAndSkillsById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        MatchOutcome outcome = engine.score(profile, toMatchJob(job, skillsOf(job)));
        return MatchResponse.of(outcome, engine.weights(), explanationWriter.write(outcome));
    }

    /**
     * The best of the recent active openings for the signed-in user.
     *
     * <p>Sorted by score descending, then by posting date descending. The
     * secondary sort matters: without it, jobs tied on score come back in
     * whatever order the database produced, and a client paging through them can
     * see the same job twice.
     *
     * <p>Paging is applied after scoring, over the candidate set, so
     * {@code totalElements} is the number of candidates rather than the number
     * of jobs in existence.
     */
    @Transactional(readOnly = true)
    public PageResponse<RecommendedJob> recommend(UUID userId, Pageable pageable) {
        MatchProfile profile = loadProfile(userId);

        List<Job> candidates = loadCandidates();
        Map<UUID, Map<String, String>> skillsByJob = loadSkillsFor(candidates);

        List<RecommendedJob> ranked = candidates.stream()
                .map(job -> {
                    MatchOutcome outcome =
                            engine.score(profile, toMatchJob(job, skillsByJob.getOrDefault(job.getId(), Map.of())));
                    return new ScoredJob(job, outcome);
                })
                .sorted(Comparator
                        .comparingInt((ScoredJob scored) -> scored.outcome().sortableScore()).reversed()
                        .thenComparing((ScoredJob scored) -> scored.job().getPostedAt(),
                                Comparator.reverseOrder())
                        .thenComparing(scored -> scored.job().getId()))
                .map(this::toRecommendation)
                .toList();

        return paginate(ranked, pageable);
    }

    // --- loading ----------------------------------------------------------

    /**
     * Builds the engine's view of a user.
     *
     * <p>A profile with nothing in it is rejected rather than scored. Every
     * criterion would be inapplicable, every job would come back unscored, and a
     * page of "we cannot tell" is worse than being told what to fill in.
     */
    private MatchProfile loadProfile(UUID userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Map<String, String> skills = profile.getSkillEntities().stream()
                .collect(Collectors.toMap(Skill::getNormalizedName, Skill::getName,
                        (first, second) -> first, LinkedHashMap::new));

        Set<String> roles = normalizeAll(profile.getPreferredRoles());
        Set<String> locations = normalizeAll(profile.getPreferredLocations());

        boolean empty = skills.isEmpty()
                && profile.getYearsOfExperience() == null
                && roles.isEmpty()
                && locations.isEmpty();
        if (empty) {
            throw new ProfileNotReadyException();
        }

        return new MatchProfile(skills, profile.getYearsOfExperience(), roles, locations,
                profile.getRemotePreference());
    }

    /**
     * The most recent active openings, with their companies already fetched.
     *
     * <p>Reuses the job module's own search machinery rather than a second query
     * path, so "active" means exactly what it means everywhere else, and the
     * entity graph that prevents the company N+1 applies here too.
     */
    private List<Job> loadCandidates() {
        // Newest first, with id as a tiebreaker so the window is stable between
        // requests rather than shifting under a client that is paging.
        Pageable window = PageRequest.of(0, CANDIDATE_LIMIT,
                Sort.by(Sort.Order.desc("postedAt"), Sort.Order.asc("id")));

        return jobRepository.findByActiveTrue(window).getContent();
    }

    /**
     * Every candidate's skills, in one query, grouped by job.
     *
     * <p>The alternative is touching {@code job.getSkills()} inside the scoring
     * loop, which is a lazy load per job and the N+1 this exists to avoid.
     */
    private Map<UUID, Map<String, String>> loadSkillsFor(List<Job> jobs) {
        if (jobs.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = jobs.stream().map(Job::getId).toList();

        Map<UUID, Map<String, String>> byJob = new HashMap<>();
        for (JobRepository.JobSkillRow row : jobRepository.findSkillsForJobs(ids)) {
            byJob.computeIfAbsent(row.getJobId(), key -> new LinkedHashMap<>())
                    .put(row.getNormalizedName(), row.getName());
        }
        return byJob;
    }

    private Map<String, String> skillsOf(Job job) {
        return job.getSkills().stream()
                .collect(Collectors.toMap(Skill::getNormalizedName, Skill::getName,
                        (first, second) -> first, LinkedHashMap::new));
    }

    private MatchJob toMatchJob(Job job, Map<String, String> skills) {
        return new MatchJob(
                job.getId(),
                job.getNormalizedTitle(),
                skills,
                job.getExperienceMin(),
                job.getExperienceMax(),
                TextNormalizer.normalize(job.getLocation()),
                job.getWorkMode());
    }

    private RecommendedJob toRecommendation(ScoredJob scored) {
        List<String> explanation = explanationWriter.write(scored.outcome());
        // The first two sentences: the score, and the single most useful reason.
        // A full breakdown per row would make a 20-job response enormous for
        // detail the user has not asked to see yet.
        List<String> summary = explanation.size() <= 2
                ? explanation
                : List.copyOf(explanation.subList(0, 2));

        return new RecommendedJob(JobSummary.from(scored.job()), scored.outcome().scored(),
                scored.outcome().score(), summary);
    }

    /**
     * Pages an already-ranked list.
     *
     * <p>In memory, because the ranking is: scores are computed per user and
     * cannot be expressed as SQL ordering without precomputing and storing them.
     * The candidate limit is what keeps that honest -- the list being sliced is
     * at most {@code CANDIDATE_LIMIT} entries, not the whole table.
     */
    private PageResponse<RecommendedJob> paginate(List<RecommendedJob> ranked, Pageable pageable) {
        int from = (int) Math.min(pageable.getOffset(), ranked.size());
        int to = Math.min(from + pageable.getPageSize(), ranked.size());
        List<RecommendedJob> pageContent = new ArrayList<>(ranked.subList(from, to));

        Page<RecommendedJob> page = new org.springframework.data.domain.PageImpl<>(
                pageContent, pageable, ranked.size());
        return PageResponse.from(page, Function.identity());
    }

    /** A job and what the engine made of it, while sorting. */
    private record ScoredJob(Job job, MatchOutcome outcome) {
    }

    private static Set<String> normalizeAll(List<String> values) {
        return values.stream()
                .map(TextNormalizer::normalize)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
