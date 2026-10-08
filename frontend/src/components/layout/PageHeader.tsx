import type { ReactNode } from 'react';

interface PageHeaderProps {
  title: string;
  description?: string;
  actions?: ReactNode;
}

/**
 * The title block at the top of a page.
 *
 * <p>Renders the page's `<h1>`. Every page needs exactly one, and putting it
 * here means no page accidentally ships with two or none -- which is the most
 * common heading-structure mistake and a real navigation problem for screen
 * reader users.
 */
export function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <div className="page-header">
      <div>
        <h1 className="page-title">{title}</h1>
        {description && <p className="page-description">{description}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </div>
  );
}
