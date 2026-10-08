import { Link } from 'react-router-dom';
import { PageHeader } from '../../../components/layout/PageHeader';
import { Button, Card, Input } from '../../../components/ui';

/**
 * Registration form.
 *
 * <p>Markup only, like {@link LoginPage}.
 *
 * <p>When it is wired: POST `/auth/register`, which returns the created user
 * and deliberately <b>no token</b> — the client calls `/auth/login` next. A 409
 * means the address is already registered and should be shown on the email
 * field rather than as a page-level error.
 *
 * <p>The password hint states the real limits: the backend requires 10-72
 * characters, and 72 is bcrypt's ceiling rather than an arbitrary choice.
 */
export function RegisterPage() {
  return (
    <div className="narrow-page">
      <PageHeader title="Create an account" description="Start matching jobs to your profile." />

      <Card>
        <form className="stack" onSubmit={(event) => event.preventDefault()}>
          <div className="field-row">
            <Input label="First name" name="firstName" autoComplete="given-name" required />
            <Input label="Last name" name="lastName" autoComplete="family-name" required />
          </div>

          <Input label="Email address" type="email" name="email" autoComplete="email" required />

          <Input
            label="Password"
            type="password"
            name="password"
            autoComplete="new-password"
            hint="Between 10 and 72 characters. Longer is better than complicated."
            required
          />

          <p className="form-note">Registration is connected in the next step.</p>

          <Button type="submit" disabled>
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
