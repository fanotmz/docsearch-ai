import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { apiErrorCode, isNetworkError } from '../../core/api/api-errors';
import { SemanticSearchResult } from '../../core/api/api.models';
import { DocsearchApiService } from '../../core/api/docsearch-api.service';

@Component({
  standalone: true,
  imports: [FormsModule, DecimalPipe],
  templateUrl: './search-page.component.html',
  styleUrl: './search-page.component.css',
})
export class SearchPageComponent {
  private readonly api = inject(DocsearchApiService);
  readonly results = signal<SemanticSearchResult[] | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  query = '';
  topK = 5;

  submit(): void {
    const trimmed = this.query.trim();
    this.error.set(null);
    if (!trimmed) { this.error.set('Saisissez une recherche.'); return; }
    if (trimmed.length > 2000) { this.error.set('La recherche ne peut pas dépasser 2 000 caractères.'); return; }
    this.loading.set(true);
    this.results.set(null);
    this.api.search(trimmed, this.topK).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (response) => this.results.set(response.results),
      error: (error: unknown) => this.error.set(isNetworkError(error)
        ? 'Le backend est indisponible. Vérifiez la connexion puis réessayez.'
        : apiErrorCode(error) === 'INVALID_TOP_K' ? 'Le nombre de résultats demandé est invalide.' : 'La recherche a échoué. Réessayez.'),
    });
  }
}
