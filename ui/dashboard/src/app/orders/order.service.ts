import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateOrderCommand, Order, OrderResponse, PagedOrdersResponse } from './order.model';

@Injectable({
  providedIn: 'root'
})
export class OrderService {
  private orderApiUrl = '/api/orders'; 
  private replayApiUrl = '/api/replay';
  
  private noCacheHeaders = new HttpHeaders({
    'Cache-Control': 'no-cache, no-store, must-revalidate',
    'Pragma': 'no-cache'
  });

  constructor(private http: HttpClient) { }

  getOrders(page: number = 0, size: number = 20): Observable<PagedOrdersResponse> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<PagedOrdersResponse>(this.orderApiUrl, { 
      headers: this.noCacheHeaders,
      params 
    });
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