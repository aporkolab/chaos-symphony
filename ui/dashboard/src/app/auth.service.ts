import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface LoginCredentials {
  username: string;
  password: string;
}

export interface AuthResponse {
  accessToken?: string;
  token?: string;
  access_token?: string;
  roles?: string[];
  [k: string]: unknown;
}

const AUTH_API = '/api/auth/';
const httpOptions = { headers: new HttpHeaders({ 'Content-Type': 'application/json' }) };

@Injectable({ providedIn: 'root' })
export class AuthService {
  constructor(private readonly http: HttpClient) { }

  login(credentials: LoginCredentials): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(AUTH_API + 'signin', credentials, httpOptions);
  }

  register(user: { username: string; password: string }): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(AUTH_API + 'signup', user, httpOptions);
  }
}
