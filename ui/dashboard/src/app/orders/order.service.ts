import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateOrderCommand, Order, OrderResponse } from './order.model';

@Injectable({
  providedIn: 'root'
})
export class OrderService {
  private orderApiUrl = '/api/orders'; 
  private replayApiUrl = '/api/replay'; 

  constructor(private http: HttpClient) { }

  getOrders(): Observable<Order[]> {
    return this.http.get<Order[]>(this.orderApiUrl);
  }

  createOrder(command: CreateOrderCommand): Observable<OrderResponse> {
    return this.http.post<OrderResponse>(this.orderApiUrl, command);
  }

  approveOrder(orderId: string): Observable<any> {
    return this.http.post(`${this.orderApiUrl}/${orderId}/approve`, {});
  }

  rejectOrder(orderId: string, reason?: string): Observable<any> {
    return this.http.post(`${this.orderApiUrl}/${orderId}/reject`, { reason: reason || 'Rejected by admin' });
  }
  
  
  replayLastFiveMinutes(): Observable<void> {
    const request = {
      consumerGroupId: 'orchestrator-order-created', 
      duration: 'PT5M' 
    };
    return this.http.post<void>(this.replayApiUrl, request);
  }
}