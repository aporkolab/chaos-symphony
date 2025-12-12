import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpErrorResponse } from '@angular/common/http';
import { Observable, forkJoin, of, throwError } from 'rxjs';
import { switchMap, map, catchError, tap } from 'rxjs/operators';
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
    console.log('[DLQ Service] Fetching topics from:', `${this.apiUrl}/topics`);
    
    return this.http.get<string[]>(`${this.apiUrl}/topics`, { headers: this.noCacheHeaders }).pipe(
      tap(response => {
        console.log('[DLQ Service] Raw topics response:', response);
        console.log('[DLQ Service] Response type:', typeof response);
        console.log('[DLQ Service] Is array:', Array.isArray(response));
      }),
      switchMap(topicNames => {
        
        if (!Array.isArray(topicNames)) {
          console.error('[DLQ Service] Response is not an array:', topicNames);
          return of([]);
        }
        
        if (topicNames.length === 0) {
          console.log('[DLQ Service] Empty topics array');
          return of([]);
        }
        
        console.log('[DLQ Service] Processing', topicNames.length, 'topics:', topicNames);
        
        const topicObservables = topicNames.map(name => {
          const countUrl = `${this.apiUrl}/${encodeURIComponent(name)}/count`;
          console.log('[DLQ Service] Fetching count from:', countUrl);
          
          return this.http.get<number>(countUrl, { headers: this.noCacheHeaders }).pipe(
            tap(count => console.log(`[DLQ Service] Topic "${name}" count:`, count)),
            map(count => ({ name, messageCount: typeof count === 'number' ? count : 0 } as DlqTopic)),
            catchError(err => {
              console.error(`[DLQ Service] Failed to get count for "${name}":`, err);
              return of({ name, messageCount: -1 } as DlqTopic);
            })
          );
        });
        
        return forkJoin(topicObservables).pipe(
          tap(results => console.log('[DLQ Service] All topic counts fetched:', results))
        );
      }),
      catchError((err: HttpErrorResponse) => {
        console.error('[DLQ Service] Failed to get topics:', err);
        console.error('[DLQ Service] Error status:', err.status);
        console.error('[DLQ Service] Error message:', err.message);
        return throwError(() => err);
      })
    );
  }

  getMessages(topicName: string, count: number = 10): Observable<DlqMessage[]> {
    const url = `${this.apiUrl}/${encodeURIComponent(topicName)}/peek?n=${count}`;
    console.log('[DLQ Service] Fetching messages from:', url);
    
    return this.http.get<DlqMessage[]>(url, { headers: this.noCacheHeaders }).pipe(
      tap(msgs => console.log(`[DLQ Service] Messages for "${topicName}":`, msgs)),
      catchError(err => {
        console.error(`[DLQ Service] Failed to get messages for "${topicName}":`, err);
        return of([]);
      })
    );
  }

  retryAllForTopic(topicName: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/${encodeURIComponent(topicName)}/replay`, {});
  }

  purgeTopic(topicName: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${encodeURIComponent(topicName)}`);
  }
}
