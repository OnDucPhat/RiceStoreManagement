import { type Order, type RiceProduct } from '../api'

export function formatMoney(value: number | string): string {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(Number(value || 0))
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Có lỗi xảy ra'
}

export function fieldError(message: unknown): string | undefined {
  return typeof message === 'string' ? message : undefined
}

export function defaultPath(user: { role: string }): string {
  return user.role === 'SHIPPER' ? '/shipper' : '/admin'
}

export function buildDashboardStats(orders: Order[], products: RiceProduct[]) {
  return {
    pending: orders.filter((order) => order.status === 'PENDING').length,
    waitingHandover: orders.filter((order) => order.status === 'DELIVERED_WAITING_HANDOVER').length,
    completed: orders.filter((order) => order.status === 'COMPLETED').length,
    revenue: sumOrders(orders.filter((order) => order.status === 'COMPLETED')),
    activeProducts: products.filter((product) => product.active).length,
  }
}

export function filterOrders(orders: Order[], search: string) {
  const normalized = search.trim().toLowerCase()
  if (!normalized) return orders
  return orders.filter((order) =>
    [order.customer_name, order.customer_phone, order.address, order.product_details, String(order.id)]
      .filter(Boolean)
      .some((value) => value.toLowerCase().includes(normalized)),
  )
}

export function sumOrders(orders: Order[]): number {
  return orders.reduce((sum, order) => sum + Number(order.total_price || 0), 0)
}

export function toggleId(values: number[], id: number): number[] {
  return values.includes(id) ? values.filter((value) => value !== id) : [...values, id]
}

export function createSessionId(): string {
  const id = crypto.randomUUID ? crypto.randomUUID() : `web-${Date.now()}-${Math.random().toString(16).slice(2)}`
  localStorage.setItem('rice_store_chat_session_id', id)
  return id
}

export type ChatMessage = { id: string; role: 'bot' | 'user'; text: string; pending?: boolean }

export function replacePending(messages: ChatMessage[], text: string): ChatMessage[] {
  const next = [...messages]
  const index = next.findIndex((item) => item.pending)
  const message: ChatMessage = { id: crypto.randomUUID(), role: 'bot', text }
  if (index >= 0) {
    next[index] = message
    return next
  }
  return [...next, message]
}

export { parseProductDetails } from '../api'
