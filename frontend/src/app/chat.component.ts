import { Component, ElementRef, OnInit, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, DocumentInfo, Source, errorMessage } from './api.service';
import { AuthService } from './auth.service';

interface Part { text: string; cite: number; }
interface Message {
  role: 'user' | 'assistant';
  parts: Part[];
  sources: Source[];
  error?: boolean;
}

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './chat.component.html',
})
export class ChatComponent implements OnInit {
  private api = inject(ApiService);
  auth = inject(AuthService);

  @ViewChild('scroller') scroller?: ElementRef<HTMLElement>;

  docs = signal<DocumentInfo[]>([]);
  messages = signal<Message[]>([]);
  uploading = signal(false);
  asking = signal(false);
  dragging = signal(false);
  uploadError = signal('');
  openSource = signal<string | null>(null); // "<messageIndex>-<n>"
  question = '';

  readonly suggestions = ['Summarize the key points', 'What are the deadlines or dates mentioned?', 'What are the main risks?'];

  ngOnInit() { this.refresh(); }

  refresh() { this.api.documents().subscribe({ next: d => this.docs.set(d), error: e => this.uploadError.set(errorMessage(e)) }); }

  get readyCount() { return this.docs().filter(d => d.status === 'READY').length; }

  onFiles(files: FileList | null) {
    if (!files?.length) return;
    this.uploadError.set('');
    const queue = Array.from(files);
    const next = () => {
      const file = queue.shift();
      if (!file) { this.uploading.set(false); return; }
      this.uploading.set(true);
      this.api.upload(file).subscribe({
        next: d => {
          this.docs.update(list => [d, ...list]);
          if (d.status === 'FAILED') this.uploadError.set(`${d.filename}: ${d.error ?? 'could not be processed'}`);
          next();
        },
        error: e => { this.uploadError.set(`${file.name}: ${errorMessage(e)}`); next(); },
      });
    };
    next();
  }

  onDrop(ev: DragEvent) { ev.preventDefault(); this.dragging.set(false); this.onFiles(ev.dataTransfer?.files ?? null); }

  remove(d: DocumentInfo) {
    if (!confirm(`Delete "${d.filename}"? Its passages will no longer be searchable.`)) return;
    this.api.remove(d.id).subscribe({
      next: () => this.docs.update(list => list.filter(x => x.id !== d.id)),
      error: e => this.uploadError.set(errorMessage(e)),
    });
  }

  send(text?: string) {
    const q = (text ?? this.question).trim();
    if (!q || this.asking()) return;
    this.question = '';
    this.messages.update(m => [...m, { role: 'user', parts: [{ text: q, cite: 0 }], sources: [] }]);
    this.asking.set(true);
    this.scrollDown();
    this.api.ask(q).subscribe({
      next: r => this.push({ role: 'assistant', parts: this.split(r.answer), sources: r.sources }),
      error: e => this.push({ role: 'assistant', parts: [{ text: errorMessage(e), cite: 0 }], sources: [], error: true }),
    });
  }

  onEnter(ev: Event) {
    const k = ev as KeyboardEvent;
    if (!k.shiftKey) { k.preventDefault(); this.send(); }
  }

  toggleSource(msgIndex: number, n: number) {
    const key = `${msgIndex}-${n}`;
    this.openSource.update(cur => (cur === key ? null : key));
  }

  sourceFor(m: Message, n: number) { return m.sources.find(s => s.n === n); }

  private push(m: Message) { this.messages.update(list => [...list, m]); this.asking.set(false); this.scrollDown(); }

  /** Splits "text [1] more [2]" into plain and citation parts so citations render as clickable chips. */
  private split(text: string): Part[] {
    return text.split(/(\[\d+\])/g).filter(Boolean).map(s => {
      const m = /^\[(\d+)\]$/.exec(s);
      return m ? { text: s, cite: +m[1] } : { text: s, cite: 0 };
    });
  }

  private scrollDown() {
    setTimeout(() => { const el = this.scroller?.nativeElement; if (el) el.scrollTop = el.scrollHeight; });
  }
}
