import { Link } from 'react-router-dom';
import { PageHeader } from '../../components/layout/PageHeader';

export function NotFoundPage() {
  return (
    <>
      <PageHeader
        title="Page not found"
        description="That address does not match anything in JobLens."
      />
      <Link className="btn btn-secondary" to="/">
        Back to home
      </Link>
    </>
  );
}
