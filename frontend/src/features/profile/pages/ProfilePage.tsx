import { PageHeader } from '../../../components/layout/PageHeader';
import { Card, EmptyState } from '../../../components/ui';

/**
 * Placeholder for profile editing, backed by GET and PUT /users/me/profile. PUT is a full replacement, so the form must submit every field, not only the changed ones.
 */
export function ProfilePage() {
  return (
    <>
      <PageHeader title="Your profile" description="Skills, experience and preferences — what jobs are matched against." />
      <Card>
        <EmptyState title="Profile editing is not wired up yet" />
      </Card>
    </>
  );
}
