import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { timeout } from 'rxjs';

interface SystemInfo { name: string; phase: string; documentSearchAvailable: boolean; }

@Component({ selector: 'app-root', templateUrl: './app.html', styleUrl: './app.css' })
export class App {
  private readonly http = inject(HttpClient);
  readonly connection = signal<'loading' | 'online' | 'offline'>('loading');
  constructor() { this.refresh(); }
  refresh(): void {
    this.connection.set('loading');
    this.http.get<SystemInfo>('/api/v1/system').pipe(timeout(5000)).subscribe({
      next: () => this.connection.set('online'),
      error: () => this.connection.set('offline'),
    });
  }
}
