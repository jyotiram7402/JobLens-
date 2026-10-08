import { Link } from 'react-router-dom';
import { PageHeader } from '../../../components/layout/PageHeader';
import { Button, Card, Input } from '../../../components/ui';

/**
 * Sign-in form.
 *
 * <p>Markup and accessibility only — it does not call the API yet. The form is
 * built now so the next step adds a submit handler rather than a whole page,
 * and so the labelling and keyboard behaviour are right from the start instead
 * of being retrofitted.
 *
 * <p>When it is wired: POST `/auth/login`, store `accessToken`, redirect. A 401
 * never says whether the email or the password was wrong, so the form must show
 * one message for both — the backend is deliberately vague and the UI must not
 * be more specific than it.
 */
export function LoginPage() {
  return (
    <div className="narrow-page">
      <PageHeader title="Sign in" description="Welcome back to JobLens." />

      <Card>
        <form
          className="stack"
          onSubmit={(event) => {
            // Prevents a page reload while the handler is still a placeholder.
            event.preventDefault();
          }}
        >
          <Input
            label="Email address"
            type="email"
            name="email"
            autoComplete="email"
            required
          />
          <Input
            label="Password"
            type="password"
            name="password"
            autoComplete="current-password"
            required
          />

          <p className="form-note">Sign-in is connected in the next step.</p>

          <Button type="submit" disabled>
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
