import { Button, Select } from '../../../components/ui';
import { EMPLOYMENT_TYPE_LABELS, WORK_MODE_LABELS } from '../types';
import type { EmploymentType, WorkMode } from '../types';

export interface FilterValues {
  location: string;
  employmentType: string;
  workMode: string;
  experience: string;
  sort: string;
}

interface JobFiltersProps {
  values: FilterValues;
  onChange: (field: keyof FilterValues, value: string) => void;
  onClear: () => void;
  /** True when any filter is set, so Clear is only offered when it does something. */
  active: boolean;
}

const EMPLOYMENT_OPTIONS = (Object.keys(EMPLOYMENT_TYPE_LABELS) as EmploymentType[]).map(
  (value) => ({ value, label: EMPLOYMENT_TYPE_LABELS[value] }),
);

const WORK_MODE_OPTIONS = (Object.keys(WORK_MODE_LABELS) as WorkMode[]).map((value) => ({
  value,
  label: WORK_MODE_LABELS[value],
}));

/**
 * Experience as a few named bands rather than a slider.
 *
 * <p>Two numeric inputs or a dual-handle slider would both be fiddlier to use
 * and harder to make keyboard-accessible, for a question most people answer
 * approximately. Each band maps to the `experienceMin` the backend already
 * understands — and because matching is a range *overlap*, "3+ years" correctly
 * returns jobs asking for 1-5 as well as jobs asking for 5+.
 */
export const EXPERIENCE_OPTIONS = [
  { value: '0', label: 'No experience needed' },
  { value: '1', label: '1+ years' },
  { value: '3', label: '3+ years' },
  { value: '5', label: '5+ years' },
  { value: '8', label: '8+ years' },
];

/**
 * Sort options, mapped to the three fields the backend allows.
 *
 * <p>The values are fixed here rather than built from user input: the backend
 * rejects anything outside its allowlist with a 400, and there is no reason to
 * let a URL produce one.
 */
const SORT_OPTIONS = [
  { value: 'postedAt,desc', label: 'Newest first' },
  { value: 'postedAt,asc', label: 'Oldest first' },
  { value: 'title,asc', label: 'Title (A-Z)' },
];

/**
 * The filter controls.
 *
 * <p>Changing any of them applies immediately — no Apply button. These are
 * selects and a short text field, so a change is a deliberate act rather than a
 * stream of keystrokes; only the keyword box (which lives in the page) needs
 * debouncing.
 */
export function JobFilters({ values, onChange, onClear, active }: JobFiltersProps) {
  return (
    <section className="filters" aria-label="Filter jobs">
      <div className="filters-grid">
        <div className="field">
          <label className="field-label" htmlFor="filter-location">
            Location
          </label>
          <input
            id="filter-location"
            className="field-input"
            type="text"
            placeholder="Any location"
            value={values.location}
            onChange={(event) => onChange('location', event.target.value)}
          />
        </div>

        <Select
          label="Employment type"
          placeholder="Any type"
          options={EMPLOYMENT_OPTIONS}
          value={values.employmentType}
          onChange={(event) => onChange('employmentType', event.target.value)}
        />

        <Select
          label="Working arrangement"
          placeholder="Any arrangement"
          options={WORK_MODE_OPTIONS}
          value={values.workMode}
          onChange={(event) => onChange('workMode', event.target.value)}
        />

        <Select
          label="Experience"
          placeholder="Any experience"
          options={EXPERIENCE_OPTIONS}
          value={values.experience}
          onChange={(event) => onChange('experience', event.target.value)}
        />

        <Select
          label="Sort by"
          options={SORT_OPTIONS}
          value={values.sort || 'postedAt,desc'}
          onChange={(event) => onChange('sort', event.target.value)}
        />
      </div>

      {active && (
        <Button variant="ghost" onClick={onClear}>
          Clear filters
        </Button>
      )}
    </section>
  );
}
