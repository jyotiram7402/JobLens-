import { PageHeader } from '../../../components/layout/PageHeader';
import { Card, EmptyState } from '../../../components/ui';

/**
 * Placeholder for the job search UI. The backend already supports ten composable filters, pagination and sorting on GET /jobs; this page will expose them.
 */
export function JobsPage() {
  return (
    <>
      <PageHeader title="Jobs" description="Search openings by keyword, location, work mode and experience." />
      <Card>
        <EmptyState title="Job search is not wired up yet" />
      </Card>
    </>
  );
}
