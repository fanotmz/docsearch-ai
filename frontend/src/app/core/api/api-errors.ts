import { HttpErrorResponse } from '@angular/common/http';

export function apiErrorCode(error: unknown): string | null {
  if (!(error instanceof HttpErrorResponse) || !error.error || typeof error.error !== 'object') {
    return null;
  }
  const code = (error.error as { code?: unknown }).code;
  return typeof code === 'string' ? code : null;
}

export function isNetworkError(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 0;
}
