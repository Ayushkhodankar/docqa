import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { errorMessage } from './api.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  template: `
    <main class="auth">
      <section class="auth-card">
        <h1>DocQA</h1>
        <p class="lede">Upload your documents, then ask questions. Every answer points back to the passage it came from.</p>

        <form (ngSubmit)="submit()" #f="ngForm">
          <label>Email
            <input name="email" type="email" required autocomplete="email" [(ngModel)]="email">
          </label>
          <label>Password
            <input name="password" type="password" required minlength="8"
                   [autocomplete]="mode() === 'login' ? 'current-password' : 'new-password'" [(ngModel)]="password">
            @if (mode() === 'register') { <small>At least 8 characters</small> }
          </label>
          @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
          <button class="primary" type="submit" [disabled]="busy() || f.invalid">
            {{ busy() ? 'Please wait…' : mode() === 'login' ? 'Sign in' : 'Create account' }}
          </button>
        </form>

        <button class="link" type="button" (click)="toggle()">
          {{ mode() === 'login' ? 'New here? Create an account' : 'Already registered? Sign in' }}
        </button>
      </section>
    </main>
  `,
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  mode = signal<'login' | 'register'>('login');
  busy = signal(false);
  error = signal('');
  email = '';
  password = '';

  toggle() { this.mode.update(m => (m === 'login' ? 'register' : 'login')); this.error.set(''); }

  submit() {
    this.busy.set(true);
    this.error.set('');
    const call = this.mode() === 'login'
      ? this.auth.login(this.email, this.password)
      : this.auth.register(this.email, this.password);
    call.subscribe({
      next: () => this.router.navigate(['/']),
      error: e => { this.error.set(errorMessage(e)); this.busy.set(false); },
    });
  }
}
