import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-root',
  imports: [FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private readonly http = inject(HttpClient);
  protected readonly username = signal('');
  protected readonly password = signal('');
  protected readonly authenticatedAs = signal<string | null>(null);
  protected readonly error = signal('');
  protected readonly loading = signal(false);

  protected login(): void {
    this.error.set('');
    this.loading.set(true);
    // Credentials remain in memory. Use HTTPS outside local development.
    const credentials = btoa(String.fromCharCode(...new TextEncoder().encode(
      `${this.username()}:${this.password()}`
    )));
    this.http.get<{ username: string }>('/api/v1/me', {
      headers: { Authorization: `Basic ${credentials}` }
    }).subscribe({
      next: ({ username }) => {
        this.authenticatedAs.set(username);
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
    this.username.set('');
    this.password.set('');
  }
}
