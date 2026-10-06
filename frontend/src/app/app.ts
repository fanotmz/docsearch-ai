import { Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { timeout } from 'rxjs';
import { AppShellComponent } from './layout/app-shell.component';
import { DocsearchApiService } from './core/api/docsearch-api.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.html',
  styleUrl: './app.css',
  imports: [RouterOutlet, AppShellComponent],
})
export class App {
  private readonly api = inject(DocsearchApiService);
  readonly connection = signal<'loading' | 'online' | 'offline'>('loading');
  constructor() { this.refresh(); }
  refresh(): void {
    this.connection.set('loading');
    this.api.getSystem().pipe(timeout(5000)).subscribe({
      next: () => this.connection.set('online'),
      error: () => this.connection.set('offline'),
    });
  }
}
