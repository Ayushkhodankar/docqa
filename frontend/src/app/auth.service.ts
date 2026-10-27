import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs';

interface AuthResponse { token: string; email: string; }
const KEY = 'docqa.auth';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private state = signal<AuthResponse | null>(this.load());

  readonly email = computed(() => this.state()?.email ?? null);

  get token(): string | null { return this.state()?.token ?? null; }
  isLoggedIn(): boolean { return !!this.state(); }

  login(email: string, password: string) {
    return this.http.post<AuthResponse>('/api/auth/login', { email, password }).pipe(tap(r => this.save(r)));
  }
  register(email: string, password: string) {
    return this.http.post<AuthResponse>('/api/auth/register', { email, password }).pipe(tap(r => this.save(r)));
  }
  logout(): void {
    localStorage.removeItem(KEY);
    this.state.set(null);
    this.router.navigate(['/login']);
  }

  private save(r: AuthResponse): void {
    localStorage.setItem(KEY, JSON.stringify(r));
    this.state.set(r);
  }
  private load(): AuthResponse | null {
    try { return JSON.parse(localStorage.getItem(KEY) ?? 'null'); } catch { return null; }
  }
}
