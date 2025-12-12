import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, forkJoin, of } from 'rxjs';
import { switchMap, map, catchError } from 'rxjs/operators';
import { DlqTopic, DlqMessage } from './dlq.model';

@Injectable({
  providedIn: 'root'
})
export class DlqService {
  
  private apiUrl = '/api/dlq';
  
  private noCacheHeaders = new HttpHeaders({
    'Cache-Control': 'no-cache, no-store, must-revalidate',
    'Pragma': 'no-cache'
  });

  constructor(private http: HttpClient) { }

  getDlqTopics(): Observable<DlqTopic[]> {
    return this.http.get<string[]>(`${this.apiUrl}/topics`, { headers: this.noCacheHeaders }).pipe(
      switchMap(topicNames => {
        if (topicNames.length === 0) {
          return of([]);
        }
        const topicObservables = topicNames.map(name =>
          this.http.get<number>(`${this.apiUrl}/${name}/count`, { headers: this.noCacheHeaders }).pipe(
            map(count => ({ name, messageCount: count } as DlqTopic)),
            catchError(() => of({ name, messageCount: -1 } as DlqTopic)) 
          )
        );
        return forkJoin(topicObservables);
      })
    );
  }

  getMessages(topicName: string, count: number = 10): Observable<DlqMessage[]> {
    return this.http.get<DlqMessage[]>(`${this.apiUrl}/${topicName}/peek?n=${count}`, { headers: this.noCacheHeaders });
  }

  retryAllForTopic(topicName: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/${topicName}/replay`, {});
  }

  purgeTopic(topicName: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${topicName}`);
  }
}