import { CommonModule, DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { apiErrorCode, isNetworkError } from '../../core/api/api-errors';
import { DocumentStatus, DocumentSummary } from '../../core/api/api.models';
import { DocsearchApiService } from '../../core/api/docsearch-api.service';

@Component({
  standalone: true,
  imports: [CommonModule, DatePipe, FormsModule],
  templateUrl: './library-page.component.html',
  styleUrl: './library-page.component.css',
})
export class LibraryPageComponent implements OnInit {
  private readonly api = inject(DocsearchApiService);
  readonly documents = signal<DocumentSummary[]>([]);
  readonly loading = signal(true);
  readonly uploading = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);
  selectedFile: File | null = null;

  ngOnInit(): void { this.loadDocuments(); }

  loadDocuments(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listDocuments().pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (documents) => this.documents.set(documents),
      error: (error: unknown) => this.error.set(isNetworkError(error)
        ? 'La bibliothèque est momentanément inaccessible.'
        : 'Impossible de charger la bibliothèque.'),
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
    this.error.set(null);
    this.success.set(null);
  }

  upload(): void {
    if (!this.selectedFile || this.uploading()) return;
    if (this.selectedFile.size > 20 * 1024 * 1024) {
      this.error.set('Le fichier dépasse la limite de 20 Mo.');
      return;
    }
    this.uploading.set(true);
    this.error.set(null);
    this.success.set(null);
    this.api.uploadDocument(this.selectedFile).pipe(finalize(() => this.uploading.set(false))).subscribe({
      next: (response) => {
        this.success.set(`${response.filename} a été importé (${response.pageCount} pages, ${response.status}).`);
        this.selectedFile = null;
        this.loadDocuments();
      },
      error: (error: unknown) => this.error.set(this.uploadErrorMessage(error)),
    });
  }

  statusLabel(status: DocumentStatus): string {
    return status === 'READY' ? 'Prêt' : status === 'PROCESSING' ? 'Traitement' : 'Échec';
  }

  private uploadErrorMessage(error: unknown): string {
    if (isNetworkError(error)) return 'Le backend est indisponible. Vérifiez la connexion puis réessayez.';
    switch (apiErrorCode(error)) {
      case 'EMPTY_FILE': return 'Le fichier sélectionné est vide.';
      case 'FILE_TOO_LARGE': return 'Le fichier dépasse la limite de 20 Mo.';
      case 'UNSUPPORTED_MEDIA_TYPE': return 'Seuls les fichiers PDF sont acceptés.';
      case 'NOT_PDF': return 'Le fichier ne contient pas un PDF valide.';
      case 'NO_EXTRACTABLE_TEXT': return 'Ce PDF ne contient pas de texte extractible. L’OCR n’est pas disponible.';
      case 'MALFORMED_PDF': return 'Le PDF est illisible ou incomplet.';
      default: return 'L’import du PDF a échoué. Réessayez.';
    }
  }
}
