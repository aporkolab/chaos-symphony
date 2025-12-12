import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { OrderService } from './order.service';
import { finalize, interval, Subscription, switchMap } from 'rxjs';
import { CommonModule } from '@angular/common';
import { Order, OrderResponse, PagedOrdersResponse } from './order.model';

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './orders.component.html',
  styleUrls: ['./orders.component.css']
})
export class OrdersComponent implements OnInit, OnDestroy {

  orderForm: FormGroup;
  isLoading = false;
  
  
  orders: Order[] = [];
  currentPage = 0;
  pageSize = 15;
  totalElements = 0;
  totalPages = 0;
  hasNext = false;
  hasPrevious = false;
  
  private pollSubscription?: Subscription;

  constructor(
    private fb: FormBuilder,
    private orderService: OrderService
  ) {
    this.orderForm = this.fb.group({
      customerId: ['customer-123', Validators.required],
      total: [100.00, [Validators.required, Validators.min(1)]],
      currency: ['USD', Validators.required],
      shippingAddress: ['']
    });
  }

  ngOnInit(): void {
    this.loadOrders();
    
    
    this.pollSubscription = interval(3000).pipe(
      switchMap(() => this.orderService.getOrders(this.currentPage, this.pageSize))
    ).subscribe({
      next: (response) => this.updateFromResponse(response),
      error: (err) => console.error('Failed to poll orders', err)
    });
  }

  ngOnDestroy(): void {
    this.pollSubscription?.unsubscribe();
  }

  loadOrders(): void {
    this.isLoading = true;
    this.orderService.getOrders(this.currentPage, this.pageSize)
      .pipe(finalize(() => this.isLoading = false))
      .subscribe({
        next: (response) => this.updateFromResponse(response),
        error: (err) => console.error('Failed to load orders', err)
      });
  }

  private updateFromResponse(response: PagedOrdersResponse): void {
    this.orders = response.content;
    this.currentPage = response.page;
    this.totalElements = response.totalElements;
    this.totalPages = response.totalPages;
    this.hasNext = response.hasNext;
    this.hasPrevious = response.hasPrevious;
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages) {
      this.currentPage = page;
      this.loadOrders();
    }
  }

  nextPage(): void {
    if (this.hasNext) {
      this.goToPage(this.currentPage + 1);
    }
  }

  previousPage(): void {
    if (this.hasPrevious) {
      this.goToPage(this.currentPage - 1);
    }
  }

  firstPage(): void {
    this.goToPage(0);
  }

  lastPage(): void {
    this.goToPage(this.totalPages - 1);
  }

  
  get pageNumbers(): number[] {
    const pages: number[] = [];
    const maxVisible = 5;
    let start = Math.max(0, this.currentPage - Math.floor(maxVisible / 2));
    let end = Math.min(this.totalPages, start + maxVisible);
    
    if (end - start < maxVisible) {
      start = Math.max(0, end - maxVisible);
    }
    
    for (let i = start; i < end; i++) {
      pages.push(i);
    }
    return pages;
  }

  onSubmit(): void {
    if (this.orderForm.invalid) {
      return;
    }

    this.isLoading = true;
    this.orderService.createOrder(this.orderForm.value)
      .pipe(finalize(() => this.isLoading = false))
      .subscribe({
        next: (response: OrderResponse) => {
          
          this.currentPage = 0;
          this.loadOrders();
          this.orderForm.patchValue({ total: Math.round(100 + Math.random() * 2000) });
        },
        error: (err) => {
          console.error('Failed to create order', err);
        }
      });
  }

  replay(): void {
    if (confirm('This will reset the consumer group for the orchestrator. Are you sure?')) {
      this.isLoading = true;
      this.orderService.replayLastFiveMinutes()
        .pipe(finalize(() => this.isLoading = false))
        .subscribe({
          next: () => console.log('Replay command sent successfully.'),
          error: (err) => console.error('Failed to send replay command', err)
        });
    }
  }

  approveOrder(orderId: string): void {
    this.isLoading = true;
    this.orderService.approveOrder(orderId)
      .pipe(finalize(() => this.isLoading = false))
      .subscribe({
        next: () => {
          console.log('Order approved:', orderId);
          this.loadOrders();
        },
        error: (err) => console.error('Failed to approve order', err)
      });
  }

  rejectOrder(orderId: string): void {
    const reason = prompt('Rejection reason (optional):');
    this.isLoading = true;
    this.orderService.rejectOrder(orderId, reason || undefined)
      .pipe(finalize(() => this.isLoading = false))
      .subscribe({
        next: () => {
          console.log('Order rejected:', orderId);
          this.loadOrders();
        },
        error: (err) => console.error('Failed to reject order', err)
      });
  }

  getStatusIcon(status: string): string {
    switch (status) {
      case 'PENDING_REVIEW': return '⚠️';
      case 'COMPLETED': return '✅';
      case 'REJECTED': return '❌';
      default: return '🔄';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'PENDING_REVIEW': return 'Review Required';
      case 'COMPLETED': return 'Completed';
      case 'REJECTED': return 'Rejected';
      default: return 'Processing';
    }
  }
}
