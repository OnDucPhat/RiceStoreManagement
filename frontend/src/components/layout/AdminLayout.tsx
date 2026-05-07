import {
  Home,
  ShoppingBag,
  Package,
  ClipboardCheck,
  Warehouse,
  Gift,
  Truck,
  MessageCircle,
  LogOut,
} from 'lucide-react'
import { NavLink, Outlet } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api, type User } from '../../api'

export function AdminLayout({ user }: { user: User | null }) {
  const queryClient = useQueryClient()
  const logoutMutation = useMutation({
    mutationFn: api.logout,
    onSettled: () => {
      queryClient.setQueryData(['auth', 'me'], null)
      queryClient.removeQueries({ queryKey: ['orders'] })
      queryClient.removeQueries({ queryKey: ['products'] })
      queryClient.removeQueries({ queryKey: ['users'] })
      queryClient.removeQueries({ queryKey: ['shipper-orders'] })
    },
  })

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">米</div>
          <div>
            <strong>RiceStore</strong>
            <span>Quản lý cửa hàng</span>
          </div>
        </div>
        <nav className="nav-list">
          <NavLink to="/admin" end>
            <Home size={18} /> Tổng quan
          </NavLink>
          <NavLink to="/admin/orders">
            <ShoppingBag size={18} /> Đơn hàng
          </NavLink>
          <NavLink to="/admin/products">
            <Package size={18} /> Loại gạo
          </NavLink>
          <NavLink to="/admin/handover">
            <ClipboardCheck size={18} /> Bàn giao
          </NavLink>
          <NavLink to="/admin/inventory">
            <Warehouse size={18} /> Nhập kho
          </NavLink>
          <NavLink to="/admin/loyalty">
            <Gift size={18} /> Tích điểm
          </NavLink>
          <NavLink to="/shipper">
            <Truck size={18} /> Shipper
          </NavLink>
          <NavLink to="/chat">
            <MessageCircle size={18} /> Chat khách
          </NavLink>
        </nav>
        <div className="sidebar-user">
          <span>{user?.username}</span>
          <button
            className="nav-logout"
            type="button"
            onClick={() => logoutMutation.mutate()}
          >
            <LogOut size={18} /> Dang xuat
          </button>
        </div>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
