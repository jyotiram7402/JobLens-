import { useParams } from 'react-router-dom';
import { PageHeader } from '../../../components/layout/PageHeader';
import { Card, EmptyState } from '../../../components/ui';

/**
 * Placeholder for one company.
 *
 * <p>Will show the record from `GET /companies/{id}` and its openings.
 *
 * <p>Note for whoever builds it: there is <b>no</b>
 * `/companies/{id}/jobs` endpoint. Its jobs come from
 * `GET /jobs?companyId={id}`, which is the same search endpoint with one more
 * filter — so the list gets pagination, sorting and every other filter for
 * free.
 */
export function CompanyDetailPage() {
  const { companyId } = useParams<{ companyId: string }>();

  return (
    <>
      <PageHeader title="Company" description="What they do, and what they are hiring for." />
      <Card>
        <EmptyState
          title="Company details are not wired up yet"
          description={`Route parameter received: ${companyId ?? 'none'}`}
        />
      </Card>
    </>
  );
}
