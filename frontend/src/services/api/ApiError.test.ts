import { describe, expect, it } from 'vitest';
import { ApiError } from './ApiError';

/** An example utility test. */
describe('ApiError', () => {
  it('reads the backend error shape', async () => {
    const response = new Response(
      JSON.stringify({
        status: 400,
        error: 'VALIDATION_ERROR',
        message: 'Request validation failed',
        path: '/api/v1/companies',
        traceId: 'abc123',
        details: { name: 'must not be blank' },
      }),
      { status: 400, headers: { 'Content-Type': 'application/json' } },
    );

    const error = await ApiError.fromResponse(response);

    expect(error.status).toBe(400);
    expect(error.code).toBe('VALIDATION_ERROR');
    expect(error.traceId).toBe('abc123');
    expect(error.details.name).toBe('must not be blank');
    expect(error.hasFieldErrors).toBe(true);
  });

  it('falls back when the body is not the expected shape', async () => {
    // A proxy or gateway returning HTML, which is exactly when a naive
    // response.json() would throw and lose the error entirely.
    const response = new Response('<html>502 Bad Gateway</html>', { status: 502 });

    const error = await ApiError.fromResponse(response);

    expect(error.status).toBe(502);
    expect(error.code).toBe('UNEXPECTED_ERROR');
    expect(error.message).not.toContain('html');
  });

  it('classifies the statuses callers branch on', () => {
    expect(new ApiError(401, 'UNAUTHENTICATED', 'x').isUnauthenticated).toBe(true);
    expect(new ApiError(404, 'JOB_NOT_FOUND', 'x').isNotFound).toBe(true);
    expect(ApiError.network().status).toBe(0);
  });
});
