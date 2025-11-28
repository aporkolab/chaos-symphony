import { Injectable } from '@angular/core';

const TOKEN_KEY = 'auth-token';
const USER_KEY = 'auth-user';

@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  saveToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
  }
  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }
  saveUser(user: unknown): void {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  }
  getUser(): any {
    const raw = localStorage.getItem(USER_KEY);
    try { return raw ? JSON.parse(raw) : null; } catch { return null; }
  }
  signOut(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }
}
