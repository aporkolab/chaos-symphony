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
  private pollSubscription?: Subscription;

  constructor(private dlqService: DlqService) { }

  ngOnInit(): void {
    this.loadTopics();
    
    
    this.pollSubscription = interval(5000).pipe(
      switchMap(() => this.dlqService.getDlqTopics())
    ).subscribe({
      next: (data) => {
        this.topics = data;
        
        if (this.selectedTopic) {
          const updated = data.find(t => t.name === this.selectedTopic!.name);
          if (updated) {
            this.selectedTopic = updated;
            this.refreshMessages();
          }
        }
      },
      error: (err) => console.error('Failed to poll DLQ topics', err)
    });
  }

  ngOnDestroy(): void {
    this.pollSubscription?.unsubscribe();
  }

  loadTopics(): void {
    this.isLoadingTopics = true;
    this.dlqService.getDlqTopics()
      .pipe(finalize(() => this.isLoadingTopics = false))
      .subscribe(data => {
        this.topics = data;
      });
  }
  
  private refreshMessages(): void {
    if (this.selectedTopic) {
      this.dlqService.getMessages(this.selectedTopic.name, 20)
        .subscribe(msgs => this.messages = msgs);
    }
  }

  selectTopic(topic: DlqTopic): void {
    this.selectedTopic = topic;
    this.messages = [];
    if (topic) {
      this.isLoadingMessages = true;
      this.dlqService.getMessages(topic.name, 20) 
        .pipe(finalize(() => this.isLoadingMessages = false))
        .subscribe(msgs => this.messages = msgs);
    }
  }

  onReplay(topic: DlqTopic): void {
    if (confirm(`Are you sure you want to replay all messages from "${topic.name}"?`)) {
      this.isLoadingTopics = true; 
      this.dlqService.retryAllForTopic(topic.name)
        .pipe(finalize(() => this.loadTopics())) 
        .subscribe(response => {
          console.log('Replay response:', response);
          this.selectedTopic = null; 
          this.messages = [];
        });
    }
  }

  onPurge(topic: DlqTopic): void {
    if (confirm(`DANGER: Are you sure you want to permanently delete all messages from "${topic.name}"?`)) {
      this.isLoadingTopics = true;
      this.dlqService.purgeTopic(topic.name)
        .pipe(finalize(() => this.loadTopics()))
        .subscribe(response => {
          console.log('Purge response:', response);
          this.selectedTopic = null;
          this.messages = [];
        });
    }
  }
}
