import type { ReactNode } from 'react';

interface CardProps {
  children: ReactNode;
  className?: string;
  /**
   * Renders as an `<article>` instead of a `<section>`. Use for items that make
   * sense on their own -- a job, a company -- which is what a list of them is.
   */
  as?: 'section' | 'article' | 'div';
}

/** A surface with a border and padding. Nothing more. */
export function Card({ children, className, as: Element = 'section' }: CardProps) {
  return (
    <Element className={['card', className].filter(Boolean).join(' ')}>
      {children}
    </Element>
  );
}
