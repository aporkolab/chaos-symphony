export interface CreateOrderCommand {
  customerId: string;
  total: number;
  currency: string;
  shippingAddress?: string;
}

export interface OrderResponse {
  orderId: string;
  status: string;
  reviewReason?: string;
}

export interface Order {
  id: string;
  status: string;
  total: number;
  currency: string;
  createdAt: string;
}
