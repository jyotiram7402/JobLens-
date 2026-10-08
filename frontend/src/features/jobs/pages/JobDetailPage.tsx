import { useParams } from 'react-router-dom';
import { PageHeader } from '../../../components/layout/PageHeader';
import { Card, EmptyState } from '../../../components/ui';

/**
 * Placeholder for one job.
 *
 * <p>Will show the full record from `GET /jobs/{id}` and, for a signed-in user,
 * the match breakdown from `GET /jobs/{id}/match` — including which skills
 * matched and which are missing.
 *
 * <p>Reads the id from the route to prove the parameter is wired, which is the
 * one thing worth verifying at this stage.
 */
export function JobDetailPage() {
  const { jobId } = useParams<{ jobId: string }>();

  return (
    <>
      <PageHeader title="Job" description="Details and how well it matches your profile." />
      <Card>
        <EmptyState
          title="Job details are not wired up yet"
          description={`Route parameter received: ${jobId ?? 'none'}`}
        />
      </Card>
    </>
  );
}
