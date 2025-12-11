import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { AuthService, AuthResponse, LoginCredentials } from '../auth.service';
import { TokenStorageService } from '../token-storage.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  readonly form = this.fb.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]]
  });

  isSubmitting = false;
  errorMessage = '';

  constructor(
    private readonly fb: FormBuilder,
    private readonly auth: AuthService,
    private readonly tokenStorage: TokenStorageService,
    private readonly router: Router
  ) { }

  submit(): void {
    if (this.form.invalid || this.isSubmitting) return;

    const credentials: LoginCredentials = {
      username: this.form.value.username ?? '',
      password: this.form.value.password ?? ''
    };

    this.isSubmitting = true;
    this.errorMessage = '';

    this.auth.login(credentials).subscribe({
      next: (data: AuthResponse) => {
        const token =
          (typeof data.accessToken === 'string' && data.accessToken) ||
          (typeof (data as any).token === 'string' && (data as any).token) ||
          (typeof (data as any).access_token === 'string' && (data as any).access_token) ||
          '';

        if (token) this.tokenStorage.saveToken(token);
        this.tokenStorage.saveUser(data as any);

        
        window.location.href = '/';
      },
      error: (err: any) => {
        this.errorMessage = err?.error?.message ?? 'Login failed.';
      },
      complete: () => (this.isSubmitting = false)
    });
  }
}
