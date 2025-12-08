import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SloMetrics } from './slo.model';

@Injectable({
  providedIn: 'root'
})
export class SloService {
  // Use a relative path that will be proxied by the Angular dev server
  private apiUrl = '/api/metrics/slo';

  constructor(private http: HttpClient) { }

  getSloMetrics(): Observable<SloMetrics> {
    return this.http.get<SloMetrics>(this.apiUrl);
  }
}
