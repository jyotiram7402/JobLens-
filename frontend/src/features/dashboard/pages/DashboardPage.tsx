import { PageHeader } from '../../../components/layout/PageHeader';
import { Card, EmptyState } from '../../../components/ui';

/**
 * Placeholder. Step 9 builds this: a profile summary, recently added companies, and recommended jobs from GET /jobs/recommended.
 */
export function DashboardPage() {
  return (
    <>
      <PageHeader title="Dashboard" description="Your profile at a glance, with jobs matched to it." />
      <Card>
        <EmptyState title="Nothing to show yet" />
      </Card>
    </>
  );
}
