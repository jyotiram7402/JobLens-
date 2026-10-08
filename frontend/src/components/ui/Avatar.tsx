import { useState } from 'react';
import { companyInitials } from '../../utils/format';

interface AvatarProps {
  name: string;
  logoUrl?: string | null;
  size?: 'sm' | 'md' | 'lg';
}

/**
 * A company's logo, or its initials when there is none.
 *
 * <p>Initials are generated locally rather than fetched from a logo service:
 * that would be an external dependency, a cost, and a leak of which companies a
 * user is looking at — all to avoid drawing two letters.
 *
 * <p>A broken image URL falls back to the initials rather than leaving the
 * browser's broken-image icon. `logoUrl` comes from whoever created the company
 * record, so it will sometimes be wrong.
 */
export function Avatar({ name, logoUrl, size = 'md' }: AvatarProps) {
  const [failed, setFailed] = useState(false);

  if (logoUrl && !failed) {
    return (
      <img
        className={`avatar avatar-${size}`}
        src={logoUrl}
        // The company name is the meaningful alt text; "logo" would describe
        // the decoration rather than the information.
        alt={name}
        loading="lazy"
        onError={() => setFailed(true)}
      />
    );
  }

  return (
    // aria-hidden because the company name is always rendered next to this;
    // announcing the initials too would just repeat it.
    <span className={`avatar avatar-${size} avatar-initials`} aria-hidden="true">
      {companyInitials(name)}
    </span>
  );
}
