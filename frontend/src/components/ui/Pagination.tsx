import { Button } from './Button';

interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  hasPrevious: boolean;
  hasNext: boolean;
  onChange: (page: number) => void;
}

/**
 * Previous / Next, with the position stated in words.
 *
 * <p>Numbered pages are nicer when there are five of them and unusable when
 * there are two hundred, and they need ellipsis logic that is easy to get
 * subtly wrong. Previous/Next plus "Page 2 of 7" answers the same questions —
 * where am I, can I go on — at a fraction of the complexity.
 *
 * <p>`aria-live="polite"` on the position so a screen reader hears the page
 * change; without it the only feedback is the list silently replacing itself.
 */
export function Pagination({
  page,
  totalPages,
  totalElements,
  hasPrevious,
  hasNext,
  onChange,
}: PaginationProps) {
  if (totalPages <= 1) {
    return null;
  }

  return (
    <nav className="pagination" aria-label="Pagination">
      <Button variant="secondary" disabled={!hasPrevious} onClick={() => onChange(page - 1)}>
        Previous
      </Button>

      <p className="pagination-status" aria-live="polite">
        Page {page + 1} of {totalPages}
        <span className="pagination-total"> · {totalElements} results</span>
      </p>

      <Button variant="secondary" disabled={!hasNext} onClick={() => onChange(page + 1)}>
        Next
      </Button>
    </nav>
  );
}
