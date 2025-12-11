import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ChaosRule } from './chaos.model';

@Injectable({
  providedIn: 'root'
})
export class ChaosService {

  
  
  private readonly rulesApiUrl = '/api/chaos/rules';
  private readonly canaryApiUrl = '/api/canary/config';

  constructor(private http: HttpClient) { }

  
  getRules(): Observable<Record<string, ChaosRule>> {
    return this.http.get<Record<string, ChaosRule>>(this.rulesApiUrl);
  }

  
  updateRule(topic: string, rule: ChaosRule): Observable<ChaosRule> {
    return this.http.put<ChaosRule>(`${this.rulesApiUrl}/${topic}`, rule);
  }

  
  deleteRule(topic: string): Observable<void> {
    return this.http.delete<void>(`${this.rulesApiUrl}/${topic}`);
  }

  
  clearAllRules(): Observable<void> {
    return this.http.delete<void>(this.rulesApiUrl);
  }

  
  getCanary(): Observable<{ enabled: boolean; percentage: number }> {
    return this.http.get<{ enabled: boolean; percentage: number }>(this.canaryApiUrl);
  }

  
  setCanary(enabled: boolean, percentage: number): Observable<{ enabled: boolean; percentage: number }> {
    const config = { enabled, percentage };
    return this.http.post<{ enabled: boolean; percentage: number }>(this.canaryApiUrl, config);
  }
}