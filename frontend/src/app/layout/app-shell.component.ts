import { Component, EventEmitter, Input, Output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { ConnectionState } from '../core/api/api.models';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.css',
})
export class AppShellComponent {
  @Input({ required: true }) connection: ConnectionState = 'loading';
  @Output() readonly retry = new EventEmitter<void>();
}
