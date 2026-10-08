interface MatchScoreProps {
  score: number;
  /** When false, nothing could be compared and no score should be shown. */
  scored?: boolean;
  size?: 'sm' | 'lg';
}

/**
 * A match score.
 *
 * <p>Always renders the number and the word "match". A coloured ring with no
 * text is unusable for anyone who cannot distinguish the colours, and
 * meaningless to a screen reader — colour here is reinforcement, never the
 * message.
 *
 * <p>The three bands are presentation, not a judgement the backend made. They
 * exist so a strong match is noticeable while scanning a list; the score itself
 * is the fact.
 */
export function MatchScore({ score, scored = true, size = 'sm' }: MatchScoreProps) {
  if (!scored) {
    return <span className="match-score match-score-unscored">Not scored</span>;
  }

  const band = score >= 75 ? 'strong' : score >= 50 ? 'fair' : 'weak';

  return (
    <span className={`match-score match-score-${band} match-score-${size}`}>
      <strong>{score}%</strong> match
    </span>
  );
}
