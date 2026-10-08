import { Link } from 'react-router-dom';
import { Avatar } from '../../../components/ui/Avatar';
import { MatchScore } from '../../matching/components/MatchScore';
import { formatExperienceRange, formatRelativeDate } from '../../../utils/format';
import { EMPLOYMENT_TYPE_LABELS, WORK_MODE_LABELS } from '../types';
import type { JobSummary } from '../types';

interface JobCardProps {
  job: JobSummary;
  /** Shown when this card appears in a matched context, such as the dashboard. */
  matchScore?: number;
  /** False when the backend could not score the job at all. */
  matchScored?: boolean;
  /** Skills, when the caller has them. Search results do not. */
  skills?: string[];
}

/**
 * One job, in a list.
 *
 * <p>Used by search, the dashboard and the company page, which is exactly why
 * it is shared: three places showing the same thing three different ways is how
 * a product starts feeling inconsistent.
 *
 * <p>An `<article>` with the title as the only link. Making the whole card a
 * link would swallow any future control inside it and produce an unreadable
 * announcement for screen readers — the accessible name would be the entire
 * card's text.
 *
 * <p>Fields that are null are omitted rather than rendered as "Not specified".
 * The backend distinguishes "unstated" from "zero" deliberately, and inventing
 * a value here would undo that.
 */
export function JobCard({ job, matchScore, matchScored = true, skills }: JobCardProps) {
  const experience = formatExperienceRange(job.experienceMin, job.experienceMax);

  return (
    <article className="job-card">
      <div className="job-card-head">
        <Avatar name={job.company.name} />

        <div className="job-card-headings">
          <h3 className="job-card-title">
            <Link to={`/jobs/${job.id}`}>{job.title}</Link>
          </h3>
          <p className="job-card-company">
            <Link to={`/companies/${job.company.id}`}>{job.company.name}</Link>
          </p>
        </div>

        {matchScore !== undefined && (
          <MatchScore score={matchScore} scored={matchScored} />
        )}
      </div>

      {/* A definition list, because these are labelled facts rather than prose.
          The labels are visually hidden: sighted users read them from position
          and the separators, screen reader users need them spoken. */}
      <dl className="job-card-facts">
        {job.location && (
          <>
            <dt className="sr-only">Location</dt>
            <dd>{job.location}</dd>
          </>
        )}
        <dt className="sr-only">Working arrangement</dt>
        <dd>{WORK_MODE_LABELS[job.workMode]}</dd>
        <dt className="sr-only">Employment type</dt>
        <dd>{EMPLOYMENT_TYPE_LABELS[job.employmentType]}</dd>
        {experience && (
          <>
            <dt className="sr-only">Experience</dt>
            <dd>{experience}</dd>
          </>
        )}
      </dl>

      {skills && skills.length > 0 && (
        <ul className="tag-list" aria-label="Skills this job asks for">
          {skills.map((skill) => (
            <li key={skill} className="tag">
              {skill}
            </li>
          ))}
        </ul>
      )}

      <p className="job-card-posted">
        <span className="sr-only">Posted </span>
        {formatRelativeDate(job.postedAt)}
      </p>
    </article>
  );
}
