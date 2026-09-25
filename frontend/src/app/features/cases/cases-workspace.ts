import { DatePipe } from '@angular/common';
import { Component, inject, Input, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CaseApiService, CaseDetail, CasePage, CaseStatus, ProposalReview, ReviewDecision, ResolutionProposal, SupportCase } from './case-api.service';

@Component({
  selector: 'app-cases-workspace',
  imports: [FormsModule, DatePipe],
  templateUrl: './cases-workspace.html',
  styleUrl: './cases-workspace.css'
})
export class CasesWorkspace implements OnInit {
  protected readonly Math = Math;
  @Input({ required: true }) authorization!: string;
  @Input({ required: true }) organizationName!: string;

  private readonly api = inject(CaseApiService);
  protected readonly results = signal<CasePage | null>(null);
  protected readonly selected = signal<CaseDetail | null>(null);
  protected readonly title = signal('');
  protected readonly description = signal('');
  protected readonly status = signal<CaseStatus | ''>('');
  protected readonly note = signal('');
  protected readonly proposals = signal<ResolutionProposal[]>([]);
  protected readonly activeProposal = signal<ResolutionProposal | null>(null);
  protected readonly reviews = signal<ProposalReview[]>([]);
  protected readonly editedAnswer = signal('');
  protected readonly reviewNote = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  ngOnInit(): void { this.load(0); }

  protected label(status: CaseStatus): string {
    return {
      OPEN: 'Aberto', IN_PROGRESS: 'Em andamento', NEEDS_INFORMATION: 'Aguardando informações',
      RESOLVED: 'Resolvido', CLOSED: 'Encerrado'
    }[status];
  }

  protected load(page: number): void {
    this.error.set('');
    this.api.list(this.authorization, page).subscribe({
      next: (result) => this.results.set(result),
      error: () => this.error.set('Não foi possível carregar os casos.')
    });
  }

  protected open(item: SupportCase): void {
    this.error.set('');
    this.api.get(this.authorization, item.id).subscribe({
      next: (detail) => { this.selected.set(detail); this.status.set(''); this.note.set(''); this.proposals.set([]); this.reviews.set([]); this.activeProposal.set(null); this.loadProposals(item.id); this.loadReviews(item.id); },
      error: () => this.error.set('Não foi possível abrir este caso.')
    });
  }

  private loadProposals(caseId: string): void {
    this.api.proposals(this.authorization, caseId).subscribe({
      next: (items) => {
        if (this.selected()?.supportCase.id !== caseId) return;
        this.proposals.set(items);
        this.activeProposal.set(items[0] ?? null);
      },
      error: () => this.error.set('Não foi possível carregar as propostas.')
    });
  }

  private loadReviews(caseId: string): void {
    this.api.reviews(this.authorization, caseId).subscribe({
      next: (items) => { if (this.selected()?.supportCase.id === caseId) this.reviews.set(items); },
      error: () => this.error.set('Não foi possível carregar as decisões.')
    });
  }

  protected selectProposal(id: string): void {
    const selected = this.proposals().find(proposal => proposal.id === id) ?? null;
    this.activeProposal.set(selected);
    this.editedAnswer.set(selected?.answer ?? '');
    this.reviewNote.set('');
  }

  protected reviewFor(proposalId: string): ProposalReview | undefined {
    return this.reviews().find(review => review.proposalId === proposalId);
  }

  protected decide(decision: ReviewDecision): void {
    const proposal = this.activeProposal();
    if (!proposal || !this.selected() || this.busy()) return;
    const caseId = this.selected()!.supportCase.id;
    this.error.set(''); this.busy.set(true);
    this.api.review(this.authorization, caseId, proposal.id, decision,
      decision === 'EDITED' ? this.editedAnswer().trim() : null, this.reviewNote().trim() || null).subscribe({
      next: (review) => {
        this.busy.set(false);
        if (this.selected()?.supportCase.id === caseId) this.reviews.update(items => [review, ...items]);
      },
      error: () => {
        this.busy.set(false);
        this.error.set('Não foi possível registrar a decisão. Confira as fontes e atualize o caso.');
        this.loadReviews(caseId);
      }
    });
  }

  protected generateProposal(): void {
    const caseId = this.selected()?.supportCase.id;
    if (!caseId) return;
    this.busy.set(true); this.error.set('');
    this.api.generateProposal(this.authorization, caseId).subscribe({
      next: (proposal) => {
        this.busy.set(false);
        if (this.selected()?.supportCase.id !== caseId) return;
        this.proposals.update(items => [proposal, ...items]);
        this.activeProposal.set(proposal);
        this.editedAnswer.set(proposal.answer); this.reviewNote.set('');
      },
      error: () => { this.busy.set(false); this.error.set('Não foi possível gerar a proposta. Atualize o caso e tente novamente.'); }
    });
  }

  protected create(): void {
    this.error.set('');
    this.busy.set(true);
    this.api.create(this.authorization, this.title().trim(), this.description().trim()).subscribe({
      next: (item) => {
        this.title.set(''); this.description.set(''); this.busy.set(false);
        this.load(0); this.open(item);
      },
      error: () => { this.busy.set(false); this.error.set('Não foi possível criar o caso. Confira os campos.'); }
    });
  }

  protected changeStatus(): void {
    const detail = this.selected();
    const status = this.status();
    if (!detail || !status) return;
    this.error.set('');
    this.busy.set(true);
    this.api.changeStatus(this.authorization, detail.supportCase.id, status, this.note().trim()).subscribe({
      next: (updated) => {
        this.selected.set(updated); this.status.set(''); this.note.set(''); this.busy.set(false);
        this.load(this.results()?.page ?? 0);
      },
      error: () => { this.busy.set(false); this.error.set('Não foi possível mudar o estado. Atualize o caso e tente novamente.'); }
    });
  }
}
