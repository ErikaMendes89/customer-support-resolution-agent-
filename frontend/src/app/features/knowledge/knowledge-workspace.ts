import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, Input, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { DocumentDetail, DocumentPage, KnowledgeApiService, KnowledgeDocument, SearchHit } from './knowledge-api.service';

@Component({
  selector: 'app-knowledge-workspace',
  imports: [FormsModule, DatePipe, DecimalPipe],
  templateUrl: './knowledge-workspace.html',
  styleUrl: './knowledge-workspace.css'
})
export class KnowledgeWorkspace implements OnInit {
  @Input({ required: true }) authorization!: string;
  @Input({ required: true }) organizationName!: string;

  private readonly api = inject(KnowledgeApiService);
  protected readonly Math = Math;
  protected readonly documents = signal<DocumentPage | null>(null);
  protected readonly selected = signal<DocumentDetail | null>(null);
  protected readonly results = signal<SearchHit[] | null>(null);
  protected readonly title = signal('');
  protected readonly content = signal('');
  protected readonly query = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  ngOnInit(): void { this.load(0); }

  protected load(page: number): void {
    this.error.set('');
    this.api.list(this.authorization, page).subscribe({
      next: docs => this.documents.set(docs),
      error: () => this.error.set('Não foi possível carregar os documentos.')
    });
  }

  protected open(id: string): void {
    this.error.set('');
    this.api.get(this.authorization, id).subscribe({
      next: detail => this.selected.set(detail),
      error: () => this.error.set('Não foi possível abrir o documento.')
    });
  }

  protected async readFile(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (!/\.(txt|md)$/i.test(file.name) || file.size > 60_000) {
      this.error.set('Escolha um arquivo .txt ou .md de até 60 KB.');
      input.value = '';
      return;
    }
    try {
      const text = await file.text();
      if (text.length > 12_000) {
        this.error.set('O texto pode ter no máximo 12 mil caracteres.');
        return;
      }
      this.title.set(file.name.replace(/\.(txt|md)$/i, ''));
      this.content.set(text);
      this.error.set('');
    } catch {
      this.error.set('Não foi possível ler o arquivo.');
    } finally {
      input.value = '';
    }
  }

  protected ingest(): void {
    this.busy.set(true);
    this.error.set('');
    this.api.ingest(this.authorization, this.title().trim(), this.content().trim()).subscribe({
      next: doc => {
        this.busy.set(false); this.title.set(''); this.content.set('');
        this.load(0); this.open(doc.id);
      },
      error: (error: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set(error.status === 503
          ? 'O modelo de embeddings não respondeu. Confira o Ollama e tente novamente.'
          : 'Não foi possível indexar o documento. Confira título e conteúdo.');
      }
    });
  }

  protected search(): void {
    this.busy.set(true);
    this.error.set('');
    this.results.set(null);
    this.api.search(this.authorization, this.query().trim(), 5).subscribe({
      next: hits => { this.results.set(hits); this.busy.set(false); },
      error: (error: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set(error.status === 503 ? 'O modelo de embeddings não respondeu.' : 'Busca indisponível.');
      }
    });
  }

  protected delete(doc: KnowledgeDocument): void {
    if (!window.confirm(`Excluir “${doc.title}” e todos os seus trechos?`)) return;
    this.busy.set(true);
    this.api.delete(this.authorization, doc.id).subscribe({
      next: () => {
        if (this.selected()?.document.id === doc.id) this.selected.set(null);
        this.results.set(null); this.busy.set(false); this.load(0);
      },
      error: () => { this.busy.set(false); this.error.set('Não foi possível excluir o documento.'); }
    });
  }
}
