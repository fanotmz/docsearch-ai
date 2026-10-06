import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'library' },
  {
    path: 'library',
    loadComponent: () => import('./pages/library/library-page.component').then((m) => m.LibraryPageComponent),
  },
  {
    path: 'search',
    loadComponent: () => import('./pages/search/search-page.component').then((m) => m.SearchPageComponent),
  },
  {
    path: 'qa',
    loadComponent: () => import('./pages/qa/qa-page.component').then((m) => m.QaPageComponent),
  },
  { path: '**', redirectTo: 'library' },
];
