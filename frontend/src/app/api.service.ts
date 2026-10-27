import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface DocumentInfo {
  id: number; filename: string; status: 'PROCESSING' | 'READY' | 'FAILED';
  chunkCount: number; createdAt: string; error: string | null;
}
export interface Source { n: number; documentId: number; filename: string; page: number | null; snippet: string; score: number; }
export interface QueryResponse { answer: string; sources: Source[]; }

/** Pulls a readable message out of the backend's {"message": "..."} error body. */
export function errorMessage(err: any, fallback = 'Something went wrong. Please try again.'): string {
  if (err?.status === 0) return 'Cannot reach the server. Check your connection and try again.';
  return err?.error?.message ?? fallback;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);

  documents() { return this.http.get<DocumentInfo[]>('/api/documents'); }
  upload(file: File) {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<DocumentInfo>('/api/documents', form);
  }
  remove(id: number) { return this.http.delete<void>(`/api/documents/${id}`); }
  ask(question: string) { return this.http.post<QueryResponse>('/api/query', { question }); }
}
