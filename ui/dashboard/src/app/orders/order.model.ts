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
  reviewReason?: string;
}

export interface PagedOrdersResponse {
  content: Order[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}
