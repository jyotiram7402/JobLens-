import { PageHeader } from '../../../components/layout/PageHeader';
import { Card } from '../../../components/ui';

/**
 * Placeholder for the scan feature.
 *
 * <p>Nothing here touches the camera. Step 11 adds image capture and upload,
 * and step 12 adds the OCR service behind it.
 *
 * <p>This page is the clearest reason the layout is mobile-first: a scan is
 * someone standing outside a building with a phone, so the small screen is the
 * primary one here rather than a narrower version of the desktop.
 */
export function ScanPage() {
  return (
    <>
      <PageHeader
        title="Scan a company"
        description="Point your camera at a company sign to find out who they are and what they are hiring for."
      />

      <Card>
        <h2 className="card-title">Not available yet</h2>
        <p>
          Camera capture and image recognition arrive in a later step. The flow will be:
        </p>
        <ol className="step-list">
          <li>Take a photo of a company sign or storefront.</li>
          <li>JobLens reads the visible text and proposes matching companies.</li>
          <li>You confirm which one it is.</li>
          <li>Their public openings appear, scored against your profile.</li>
        </ol>
      </Card>
    </>
  );
}
