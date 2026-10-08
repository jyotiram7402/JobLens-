import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { PageHeader } from '../../../components/layout/PageHeader';
import { Button, Card, Input } from '../../../components/ui';
import { ApiError } from '../../../services/api/ApiError';
import { useAuth } from '../AuthContext';

/**
 * Registration.
 *
 * <p>Signs the user straight in afterwards. The backend deliberately issues no
 * token on registration — one code path for issuing tokens, and room for email
 * verification later — so the context makes a second call. That is a backend
 * detail this form should not have to know, and the user should not have to
 * type their password twice in a row.
 *
 * <p>Field-level errors from a 400 are attached to the fields they belong to;
 * the backend returns a `details` map keyed by field name for exactly this.
 */
export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', password: '' });
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function update(field: keyof typeof form, value: string) {
    setForm((previous) => ({ ...previous, [field]: value }));
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    setSubmitting(true);

    try {
      await register(form);
      navigate('/dashboard', { replace: true });
    } catch (cause) {
      if (cause instanceof ApiError && cause.hasFieldErrors) {
        setFieldErrors(cause.details);
      } else if (cause instanceof ApiError) {
        // 409 EMAIL_ALREADY_REGISTERED belongs on the email field, where the
        // user can act on it, rather than floating above the form.
        if (cause.code === 'EMAIL_ALREADY_REGISTERED') {
          setFieldErrors({ email: cause.message });
        } else {
          setError(cause.message);
        }
      } else {
        setError('Could not create your account. Please try again.');
      }
      setSubmitting(false);
    }
  }

  return (
    <div className="narrow-page">
      <PageHeader title="Create an account" description="Start matching jobs to your profile." />

      <Card>
        <form className="stack" onSubmit={handleSubmit}>
          <div className="field-row">
            <Input
              label="First name"
              name="firstName"
              autoComplete="given-name"
              value={form.firstName}
              onChange={(event) => update('firstName', event.target.value)}
              error={fieldErrors.firstName}
              required
            />
            <Input
              label="Last name"
              name="lastName"
              autoComplete="family-name"
              value={form.lastName}
              onChange={(event) => update('lastName', event.target.value)}
              error={fieldErrors.lastName}
              required
            />
          </div>

          <Input
            label="Email address"
            type="email"
            name="email"
            autoComplete="email"
            value={form.email}
            onChange={(event) => update('email', event.target.value)}
            error={fieldErrors.email}
            required
          />

          <Input
            label="Password"
            type="password"
            name="password"
            autoComplete="new-password"
            hint="Between 10 and 72 characters. Longer is better than complicated."
            value={form.password}
            onChange={(event) => update('password', event.target.value)}
            error={fieldErrors.password}
            required
          />

          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}

          <Button type="submit" loading={submitting}>
            Create account
          </Button>
        </form>
      </Card>

      <p className="form-footer">
        Already have an account? <Link to="/login">Sign in</Link>.
      </p>
    </div>
  );
}
