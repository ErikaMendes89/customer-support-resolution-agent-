import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { map, switchMap } from 'rxjs';
import { CasesWorkspace } from './features/cases/cases-workspace';

@Component({
  selector: 'app-root',
  imports: [FormsModule, CasesWorkspace],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private readonly http = inject(HttpClient);
  protected readonly username = signal('');
  protected readonly password = signal('');
  protected readonly authenticatedAs = signal<string | null>(null);
  protected readonly organizationName = signal('');
  protected readonly authorization = signal<string | null>(null);
  protected readonly error = signal('');
  protected readonly loading = signal(false);

  protected login(): void {
    this.error.set('');
    this.loading.set(true);
    // Credentials remain in memory. Use HTTPS outside local development.
    const credentials = btoa(String.fromCharCode(...new TextEncoder().encode(
      `${this.username()}:${this.password()}`
    )));
    const authHeader = `Basic ${credentials}`;
    const headers = { Authorization: authHeader };
    this.http.get<{ username: string; organizationName: string }>('/api/v1/me', { headers }).pipe(
      switchMap(user => this.http.get<void>('/api/v1/me/csrf', { headers }).pipe(map(() => user)))
    ).subscribe({
      next: ({ username, organizationName }) => {
        this.authenticatedAs.set(username);
        this.organizationName.set(organizationName);
        this.authorization.set(authHeader);
        this.password.set('');
        this.loading.set(false);
      },
      error: () => {
        this.password.set('');
        this.error.set('Não foi possível entrar. Confira as credenciais e se a API está ativa.');
        this.loading.set(false);
      }
    });
  }

  protected logout(): void {
    this.authenticatedAs.set(null);
    this.authorization.set(null);
    this.organizationName.set('');
    this.username.set('');
    this.password.set('');
  }
}
