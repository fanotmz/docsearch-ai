import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { apiErrorCode, isNetworkError } from '../../core/api/api-errors';
import { GroundedAnswerResponse } from '../../core/api/api.models';
import { DocsearchApiService } from '../../core/api/docsearch-api.service';

@Component({
  standalone: true,
  imports: [FormsModule],
  templateUrl: './qa-page.component.html',
  styleUrl: './qa-page.component.css',
})
export class QaPageComponent {
  private readonly api = inject(DocsearchApiService);
  readonly response = signal<GroundedAnswerResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  question = '';

  submit(): void {
    const trimmed = this.question.trim();
    this.error.set(null);
    if (!trimmed) { this.error.set('Saisissez une question.'); return; }
    if (trimmed.length > 2000) { this.error.set('La question ne peut pas dépasser 2 000 caractères.'); return; }
    this.loading.set(true);
    this.response.set(null);
    this.api.answer(trimmed).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (response) => this.response.set(response),
      error: (error: unknown) => this.error.set(this.errorMessage(error)),
    });
  }

  private errorMessage(error: unknown): string {
    if (isNetworkError(error)) return 'Le backend est indisponible. Vérifiez la connexion puis réessayez.';
    switch (apiErrorCode(error)) {
      case 'INVALID_MODEL_OUTPUT': return 'Le modèle local a renvoyé une réponse sourcée invalide. Réessayez.';
      case 'GENERATION_FAILED': return 'Le modèle local n’a pas pu générer de réponse.';
      case 'INVALID_QUESTION': return 'La question est invalide.';
      case 'QUESTION_TOO_LONG': return 'La question ne peut pas dépasser 2 000 caractères.';
      default: return 'La réponse n’a pas pu être générée. Réessayez.';
    }
  }
}
