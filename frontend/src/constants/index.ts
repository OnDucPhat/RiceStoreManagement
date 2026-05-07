import { type OrderStatus, type OrderSource } from '../api'

export const orderStatuses: OrderStatus[] = ['PENDING', 'DELIVERED_WAITING_HANDOVER', 'COMPLETED']

export const statusLabels: Record<OrderStatus, string> = {
  PENDING: 'Chờ giao',
  DELIVERED_WAITING_HANDOVER: 'Chờ bàn giao',
  COMPLETED: 'Hoàn tất',
}

export const sourceLabels: Record<OrderSource, string> = {
  MESSENGER: 'Messenger',
  MANUAL: 'Tại cửa hàng',
  WEB: 'Web chat',
}
