import { useState } from 'react';
import type { FormEvent } from 'react';
import { PageHeader } from '../../../components/layout/PageHeader';
import { Button, Card, Input, Select } from '../../../components/ui';
import { ApiError } from '../../../services/api/ApiError';
import { useAuth } from '../../auth/AuthContext';
import { profileApi } from '../api';
import type { RemotePreference, UserProfile } from '../types';

const REMOTE_OPTIONS = [
  { value: 'ANY', label: 'No preference' },
  { value: 'REMOTE', label: 'Remote' },
  { value: 'HYBRID', label: 'Hybrid' },
  { value: 'ONSITE', label: 'On site' },
];

const EMPTY_PROFILE: UserProfile = {
  headline: null,
  summary: null,
  yearsOfExperience: null,
  currentRole: null,
  remotePreference: 'ANY',
  preferredRoles: [],
  preferredLocations: [],
  skills: [],
};

/**
 * Comma-separated text for the list fields.
 *
 * <p>A tag input with chips and remove buttons would look nicer and is a lot of
 * keyboard handling to get right. A comma-separated field is immediately
 * obvious, works with autofill and with a screen reader, and is honest about
 * what it does. Worth revisiting once the rest of the product is further along.
 */
function toList(value: string): string[] {
  return value
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean);
}

interface FormState {
  headline: string;
  summary: string;
  yearsOfExperience: string;
  currentRole: string;
  remotePreference: RemotePreference;
  skills: string;
  preferredRoles: string;
  preferredLocations: string;
}

function toFormState(profile: UserProfile): FormState {
  return {
    headline: profile.headline ?? '',
    summary: profile.summary ?? '',
    yearsOfExperience:
      profile.yearsOfExperience === null ? '' : String(profile.yearsOfExperience),
    currentRole: profile.currentRole ?? '',
    remotePreference: profile.remotePreference,
    skills: profile.skills.join(', '),
    preferredRoles: profile.preferredRoles.join(', '),
    preferredLocations: profile.preferredLocations.join(', '),
  };
}

/**
 * The career profile — what matching scores against.
 *
 * <p>Seeded from the user already loaded by `AuthContext`, so opening the page
 * costs no request. Saving refreshes that context, which keeps the dashboard's
 * completion figure and the header in step without either of them knowing this
 * page exists.
 *
 * <p>The hints explain what each field does to a match score. A profile field
 * whose effect is invisible is a field nobody fills in, and every one of these
 * changes the number the user sees on a job.
 */
export function ProfilePage() {
  const { user, refresh } = useAuth();

  const [form, setForm] = useState<FormState>(() =>
    toFormState(user?.profile ?? EMPTY_PROFILE),
  );
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  function update(field: keyof FormState, value: string) {
    setForm((previous) => ({ ...previous, [field]: value }));
    setSaved(false);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    setSaving(true);

    try {
      // Every field, every time: PUT is a full replacement, so sending only
      // what changed would silently clear the rest.
      await profileApi.update({
        headline: form.headline || null,
        summary: form.summary || null,
        yearsOfExperience: form.yearsOfExperience ? Number(form.yearsOfExperience) : null,
        currentRole: form.currentRole || null,
        remotePreference: form.remotePreference,
        skills: toList(form.skills),
        preferredRoles: toList(form.preferredRoles),
        preferredLocations: toList(form.preferredLocations),
      });

      await refresh();
      setSaved(true);
    } catch (cause) {
      if (cause instanceof ApiError && cause.hasFieldErrors) {
        setFieldErrors(cause.details);
      } else {
        setError(cause instanceof ApiError ? cause.message : 'Could not save your profile.');
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="narrow-page narrow-page-wide">
      <PageHeader
        title="Your profile"
        description="This is what JobLens scores jobs against. Every field below changes your match."
      />

      <Card>
        <form className="stack" onSubmit={handleSubmit}>
          <Input
            label="Headline"
            name="headline"
            value={form.headline}
            onChange={(event) => update('headline', event.target.value)}
            error={fieldErrors.headline}
            hint="How you would describe yourself in one line."
          />

          <div className="field">
            <label className="field-label" htmlFor="profile-summary">
              Summary
            </label>
            <textarea
              id="profile-summary"
              className="field-input"
              rows={4}
              value={form.summary}
              onChange={(event) => update('summary', event.target.value)}
            />
          </div>

          <div className="field-row">
            <Input
              label="Years of experience"
              name="yearsOfExperience"
              type="number"
              min={0}
              max={60}
              value={form.yearsOfExperience}
              onChange={(event) => update('yearsOfExperience', event.target.value)}
              error={fieldErrors.yearsOfExperience}
              hint="Matched against each job's stated range."
            />
            <Input
              label="Current role"
              name="currentRole"
              value={form.currentRole}
              onChange={(event) => update('currentRole', event.target.value)}
              error={fieldErrors.currentRole}
            />
          </div>

          <Input
            label="Skills"
            name="skills"
            value={form.skills}
            onChange={(event) => update('skills', event.target.value)}
            error={fieldErrors.skills}
            hint="Comma separated, e.g. Java, Spring Boot, PostgreSQL. Worth half of every match score."
          />

          <Input
            label="Preferred roles"
            name="preferredRoles"
            value={form.preferredRoles}
            onChange={(event) => update('preferredRoles', event.target.value)}
            error={fieldErrors.preferredRoles}
            hint="Comma separated job titles you are looking for."
          />

          <Input
            label="Preferred locations"
            name="preferredLocations"
            value={form.preferredLocations}
            onChange={(event) => update('preferredLocations', event.target.value)}
            error={fieldErrors.preferredLocations}
            hint="Comma separated. Add Remote if you are open to it."
          />

          <Select
            label="Working arrangement"
            options={REMOTE_OPTIONS}
            value={form.remotePreference}
            onChange={(event) =>
              update('remotePreference', event.target.value as RemotePreference)
            }
          />

          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}

          {/* role="status" rather than "alert": a successful save is worth
              announcing but should not interrupt what the user is doing. */}
          {saved && (
            <p className="form-success" role="status">
              Profile saved. Your match scores will use it from now on.
            </p>
          )}

          <Button type="submit" loading={saving}>
            Save profile
          </Button>
        </form>
      </Card>
    </div>
  );
}
