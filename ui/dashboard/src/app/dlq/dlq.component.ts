import { Component, OnInit, OnDestroy } from '@angular/core';
import { DlqService } from './dlq.service';
import { DlqTopic, DlqMessage } from './dlq.model';
import { finalize, interval, Subscription, switchMap } from 'rxjs';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'app-dlq',
    templateUrl: './dlq.component.html',
    styleUrls: ['./dlq.component.css'],
    standalone: true,
    imports: [CommonModule],
})
export class DlqComponent implements OnInit, OnDestroy {

  topics: DlqTopic[] = [];
  selectedTopic: DlqTopic | null = null;
  messages: DlqMessage[] = [];
  isLoadingTopics = false;
  isLoadingMessages = false;
  error: string | null = null;
  debugInfo: string | null = null;
  private pollSubscription?: Subscription;

  constructor(private dlqService: DlqService) { }

  ngOnInit(): void {
    this.loadTopics();
    
    
    this.pollSubscription = interval(5000).pipe(
      switchMap(() => this.dlqService.getDlqTopics())
    ).subscribe({
      next: (data) => {
        this.error = null;
        this.topics = data;
        console.log('[DLQ Component] Poll received topics:', data);
        
        if (this.selectedTopic) {
          const updated = data.find(t => t.name === this.selectedTopic!.name);
          if (updated) {
            this.selectedTopic = updated;
            this.refreshMessages();
          }
        }
      },
      error: (err) => {
        console.error('[DLQ Component] Poll error:', err);
        this.error = this.formatError(err);
      }
    });
  }

  ngOnDestroy(): void {
    this.pollSubscription?.unsubscribe();
  }

  loadTopics(): void {
    this.isLoadingTopics = true;
    this.error = null;
    this.debugInfo = 'Fetching /api/dlq/topics...';
    
    this.dlqService.getDlqTopics()
      .pipe(finalize(() => this.isLoadingTopics = false))
      .subscribe({
        next: (data) => {
          console.log('[DLQ Component] Topics loaded:', data);
          this.topics = data;
          this.debugInfo = `Loaded ${data.length} topics`;
          this.error = null;
        },
        error: (err) => {
          console.error('[DLQ Component] Load error:', err);
          this.error = this.formatError(err);
          this.debugInfo = `Error: ${JSON.stringify(err)}`;
        }
      });
  }
  
  private formatError(err: any): string {
    if (err.status === 0) {
      return 'Nem sikerült kapcsolódni a dlq-admin szolgáltatáshoz. Ellenőrizd, hogy fut-e a 8089-es porton.';
    }
    if (err.status === 404) {
      return 'A /api/dlq/topics végpont nem található (404).';
    }
    if (err.error?.message) {
      return err.error.message;
    }
    if (err.message) {
      return err.message;
    }
    return `Hiba: ${err.status || 'ismeretlen'} - ${err.statusText || ''}`;
  }
  
  private refreshMessages(): void {
    if (this.selectedTopic) {
      this.dlqService.getMessages(this.selectedTopic.name, 20)
        .subscribe(msgs => this.messages = msgs);
    }
  }

  selectTopic(topic: DlqTopic): void {
    console.log('[DLQ Component] Selected topic:', topic);
    this.selectedTopic = topic;
    this.messages = [];
    if (topic) {
      this.isLoadingMessages = true;
      this.dlqService.getMessages(topic.name, 20) 
        .pipe(finalize(() => this.isLoadingMessages = false))
        .subscribe(msgs => {
          console.log('[DLQ Component] Messages loaded:', msgs);
          this.messages = msgs;
        });
    }
  }

  onReplay(topic: DlqTopic): void {
    if (confirm(`Are you sure you want to replay all messages from "${topic.name}"?`)) {
      this.isLoadingTopics = true; 
      this.dlqService.retryAllForTopic(topic.name)
        .pipe(finalize(() => this.loadTopics())) 
        .subscribe({
          next: response => {
            console.log('[DLQ Component] Replay response:', response);
            this.selectedTopic = null; 
            this.messages = [];
          },
          error: err => {
            console.error('[DLQ Component] Replay error:', err);
            this.error = this.formatError(err);
          }
        });
    }
  }

  onPurge(topic: DlqTopic): void {
    if (confirm(`DANGER: Are you sure you want to permanently delete all messages from "${topic.name}"?`)) {
      this.isLoadingTopics = true;
      this.dlqService.purgeTopic(topic.name)
        .pipe(finalize(() => this.loadTopics()))
        .subscribe({
          next: response => {
            console.log('[DLQ Component] Purge response:', response);
            this.selectedTopic = null;
            this.messages = [];
          },
          error: err => {
            console.error('[DLQ Component] Purge error:', err);
            this.error = this.formatError(err);
          }
        });
    }
  }
}
