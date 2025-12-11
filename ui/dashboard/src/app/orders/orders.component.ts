import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { OrderService } from './order.service';
import { finalize } from 'rxjs';
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
export class OrdersComponent implements OnInit {

  orderForm: FormGroup;
  isLoading = false;
  recentOrders: OrderResponse[] = [];

  constructor(
    private fb: FormBuilder,
    private orderService: OrderService
  ) {
    this.orderForm = this.fb.group({
      customerId: ['customer-123', Validators.required],
      total: [100.00, [Validators.required, Validators.min(1)]],
      currency: ['USD', Validators.required]
    });
  }

  ngOnInit(): void {
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
}
