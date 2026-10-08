import { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
}

/**
 * Catches a rendering error so one broken component does not blank the page.
 *
 * <p>Without this, an exception thrown during render unmounts the whole React
 * tree and leaves a white screen with the reason only in the console -- the
 * same failure mode the missing-API-URL case was fixed for.
 *
 * <p>A class component because React has no hook equivalent;
 * `componentDidCatch` is the only way to do this.
 *
 * <p>Deliberately not a monitoring integration. It logs to the console and
 * shows a recovery path. Sending errors somewhere is a real decision with a
 * cost and a privacy question attached, and belongs to the
 * production-readiness step.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // eslint-disable-next-line no-console
    console.error('Unhandled rendering error', error, info.componentStack);
  }

  render(): ReactNode {
    if (!this.state.hasError) {
      return this.props.children;
    }

    return (
      <div className="state-block state-block-error" role="alert">
        <p className="state-title">Something went wrong</p>
        <p className="state-message">
          This part of JobLens failed to load. Reloading the page usually fixes it.
        </p>
        {/*
          A full reload rather than a state reset: whatever broke the render is
          probably still in memory, so clearing the flag would just break again.
        */}
        <button type="button" className="btn btn-secondary"
                onClick={() => window.location.reload()}>
          Reload the page
        </button>
      </div>
    );
  }
}
