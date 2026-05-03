export type OrderStatus = 'PENDING' | 'DELIVERED_WAITING_HANDOVER' | 'COMPLETED'
export type OrderSource = 'MESSENGER' | 'MANUAL' | 'WEB'
export type UserRole = 'ADMIN' | 'SHIPPER'

export interface Order {
  id: number
  customer_name: string
  customer_phone: string
  address: string
  product_details: string
  total_price: number
  source: OrderSource
  status: OrderStatus
  shipper_id?: number | null
}

export interface RiceProduct {
  id: number
  name: string
  characteristics: string
  price_per_kg: number
  cost_per_kg: number
  profit_per_kg: number
  stock_kg: number
  active: boolean
  created_at: string
  updated_at: string
}

export interface StockEntry {
  id: number
  product_id: number
  product_name: string
  quantity_kg: number
  cost_per_kg: number
  imported_at: string
}

export interface CustomerLoyalty {
  id: number
  phone: string
  total_points: number
  purchase_count: number
  last_reset_at: string | null
  created_at: string
  updated_at: string
}

export interface User {
  id: number
  username: string
  role: UserRole
}

export interface ChatMessageResponse {
  session_id: string
  reply: string
  order_id?: number | null
  order_created: boolean
  outcome: string
}

export interface ApiErrorBody {
  message?: string
  error?: string
}

export interface LoginResponse {
  token: string
  user: User
}

const TOKEN_KEY = 'auth_token'
const jsonHeaders = { 'Content-Type': 'application/json' }
const unauthorizedEventName = 'auth:unauthorized'
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').trim().replace(/\/+$/, '')

function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

function buildUrl(path: string): string {
  return apiBaseUrl ? `${apiBaseUrl}${path}` : path
}

function getAuthHeaders(): Record<string, string> {
  const token = getToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = {
    ...getAuthHeaders(),
    ...(init?.headers || {}),
  }
  const response = await fetch(buildUrl(path), { ...init, headers })
  if (!response.ok) {
    if (response.status === 401) {
      clearToken()
      window.dispatchEvent(new CustomEvent(unauthorizedEventName))
    }
    let message = `Request failed (${response.status})`
    try {
      const body = (await response.json()) as ApiErrorBody
      message = body.message || body.error || message
    } catch {
      // Keep default message when the server returns no JSON body.
    }
    throw new Error(message)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

export const api = {
  async login(input: { username: string; password: string }) {
    const response = await request<LoginResponse>('/api/auth/login', {
      method: 'POST',
      headers: jsonHeaders,
      body: JSON.stringify(input),
    })
    setToken(response.token)
    return response.user
  },
  me() {
    return request<User>('/api/auth/me')
  },
  async logout() {
    await request<void>('/api/auth/logout', { method: 'POST' })
    clearToken()
  },
  orders(status?: OrderStatus) {
    const query = status ? `?status=${status}` : ''
    return request<Order[]>(`/api/orders${query}`)
  },
  createOrder(input: {
    customer_name: string
    customer_phone: string
    address: string
    product_details: string
    total_price: number
  }) {
    return request<Order>('/api/orders', {
      method: 'POST',
      headers: jsonHeaders,
      body: JSON.stringify(input),
    })
  },
  assignShipper(orderId: number, shipperId: number) {
    return request<Order>(`/api/orders/${orderId}/shipper`, {
      method: 'PUT',
      headers: jsonHeaders,
      body: JSON.stringify({ shipper_id: shipperId }),
    })
  },
  createRetailOrder(input: {
    customer_name: string
    customer_phone?: string
    loyalty_phone?: string
    items: Array<{ product_id: number; quantity_kg: number }>
  }) {
    return request<Order>('/api/orders/retail', {
      method: 'POST',
      headers: jsonHeaders,
      body: JSON.stringify(input),
    })
  },
  products(activeOnly?: boolean) {
    const query = activeOnly ? '?active_only=true' : ''
    return request<RiceProduct[]>(`/api/rice-products${query}`)
  },
  createProduct(input: ProductInput) {
    return request<RiceProduct>('/api/rice-products', {
      method: 'POST',
      headers: jsonHeaders,
      body: JSON.stringify(input),
    })
  },
  updateProduct(id: number, input: ProductInput) {
    return request<RiceProduct>(`/api/rice-products/${id}`, {
      method: 'PUT',
      headers: jsonHeaders,
      body: JSON.stringify(input),
    })
  },
  setProductActive(id: number, active: boolean) {
    return request<RiceProduct>(`/api/rice-products/${id}/active`, {
      method: 'PATCH',
      headers: jsonHeaders,
      body: JSON.stringify({ active }),
    })
  },
  users(role?: UserRole) {
    const query = role ? `?role=${role}` : ''
    return request<User[]>(`/api/users${query}`)
  },
  shipperOrders(shipperId: number, status?: OrderStatus) {
    const query = status ? `?status=${status}` : ''
    return request<Order[]>(`/api/shippers/${shipperId}/orders${query}`)
  },
  markDelivered(orderId: number, shipperId: number) {
    return request<Order>(`/api/orders/${orderId}/deliver`, {
      method: 'PUT',
      headers: jsonHeaders,
      body: JSON.stringify({ shipper_id: shipperId }),
    })
  },
  confirmHandover(input: { admin_id: number; shipper_id: number; order_ids: number[] }) {
    return request<Order[]>('/api/handover/confirm', {
      method: 'POST',
      headers: jsonHeaders,
      body: JSON.stringify(input),
    })
  },
  sendChatMessage(input: { session_id: string; message: string }) {
    return request<ChatMessageResponse>('/api/chat/messages', {
      method: 'POST',
      headers: jsonHeaders,
      body: JSON.stringify(input),
    })
  },
  importStock(input: { product_id: number; quantity_kg: number; cost_per_kg: number }) {
    return request<RiceProduct>('/api/inventory/import', {
      method: 'POST',
      headers: jsonHeaders,
      body: JSON.stringify(input),
    })
  },
  stockHistory(productId: number) {
    return request<StockEntry[]>(`/api/inventory/history/${productId}`)
  },
  loyaltyAll() {
    return request<CustomerLoyalty[]>('/api/loyalty')
  },
  loyaltyByPhone(phone: string) {
    return request<CustomerLoyalty>(`/api/loyalty/${encodeURIComponent(phone)}`)
  },
  giveGift(phone: string) {
    return request<CustomerLoyalty>(`/api/loyalty/${encodeURIComponent(phone)}/give-gift`, {
      method: 'POST',
    })
  },
}

export interface ProductInput {
  name: string
  characteristics: string
  price_per_kg: number
  cost_per_kg: number
  active?: boolean
}

export { unauthorizedEventName }

export function parseProductDetails(value: string): string {
  if (!value) return ''
  try {
    const parsed = JSON.parse(value) as Record<string, unknown>
    if (Array.isArray(parsed.items)) {
      return parsed.items
        .map((item) => {
          const row = item as Record<string, unknown>
          return `${row.rice_type ?? 'Gạo'} - ${row.quantity ?? ''}`.trim()
        })
        .join('; ')
    }
    const riceType = parsed.rice_type
    const quantity = parsed.quantity
    if (riceType || quantity) {
      return `${riceType ?? 'Gạo'} - ${quantity ?? ''}`.trim()
    }
  } catch {
    return value
  }
  return value
}
