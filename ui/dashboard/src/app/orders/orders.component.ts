import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { OrderService } from './order.service';
import { finalize, interval, Subscription, switchMap } from 'rxjs';
import { CommonModule } from '@angular/common';

interface OrderResponse {
  orderId: string;
  status: string;
  reviewReason?: string;
}

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
  recentOrders: OrderResponse[] = [];
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
      switchMap(() => this.orderService.getOrders())
    ).subscribe({
      next: (orders) => this.updateOrders(orders),
      error: (err) => console.error('Failed to poll orders', err)
    });
  }

  ngOnDestroy(): void {
    this.pollSubscription?.unsubscribe();
  }

  loadOrders(): void {
    this.orderService.getOrders().subscribe({
      next: (orders) => this.updateOrders(orders),
      error: (err) => console.error('Failed to load orders', err)
    });
  }

  private updateOrders(orders: any[]): void {
    
    this.recentOrders = orders
      .slice(-10)
      .reverse()
      .map(o => ({
        orderId: o.id || o.orderId,
        status: o.status,
        reviewReason: o.reviewReason
      }));
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
          
          this.recentOrders.unshift(response);
          if (this.recentOrders.length > 10) {
            this.recentOrders.pop();
          }
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
}
