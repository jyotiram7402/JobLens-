import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { PageHeader } from '../../../components/layout/PageHeader';
import { Button, Card, Input } from '../../../components/ui';
import { ApiError } from '../../../services/api/ApiError';
import { useAuth } from '../AuthContext';

interface RedirectState {
  from?: string;
}

/**
 * Sign-in.
 *
 * <p>Returns the user to wherever the guard interrupted them, so following a
 * link to a protected page and signing in lands on that page rather than
 * dumping them on the dashboard.
 *
 * <p>A failed sign-in shows one message for both causes. The backend returns
 * the same 401 whether the email is unknown or the password is wrong — on
 * purpose, so the endpoint cannot be used to discover which addresses have
 * accounts — and the UI must not be more specific than the API it is reporting.
 */
export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const redirectTo = (location.state as RedirectState | null)?.from ?? '/dashboard';

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      await login({ email, password });
      // replace, so Back does not return to the login form after signing in.
      navigate(redirectTo, { replace: true });
    } catch (cause) {
      setError(
        cause instanceof ApiError ? cause.message : 'Could not sign in. Please try again.',
      );
      setSubmitting(false);
    }
  }

  return (
    <div className="narrow-page">
      <PageHeader title="Sign in" description="Welcome back to JobLens." />

      <Card>
        <form className="stack" onSubmit={handleSubmit}>
          <Input
            label="Email address"
            type="email"
            name="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
          <Input
            label="Password"
            type="password"
            name="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />

          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}

          <Button type="submit" loading={submitting}>
            Sign in
          </Button>
        </form>
      </Card>

      <p className="form-footer">
        No account yet? <Link to="/register">Create one</Link>.
      </p>
    </div>
  );
}
