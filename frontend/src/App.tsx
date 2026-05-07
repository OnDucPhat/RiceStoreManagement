import { useEffect } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Loader2 } from 'lucide-react'
import { api, unauthorizedEventName } from './api'
import { defaultPath } from './utils'

import { ProtectedRoute } from './components/layout/ProtectedRoute'
import { AdminLayout } from './components/layout/AdminLayout'

import { LoginPage } from './components/pages/login/LoginPage'
import { DashboardPage } from './components/pages/dashboard/DashboardPage'
import { OrdersPage } from './components/pages/orders/OrdersPage'
import { ProductsPage } from './components/pages/products/ProductsPage'
import { InventoryPage } from './components/pages/inventory/InventoryPage'
import { LoyaltyPage } from './components/pages/loyalty/LoyaltyPage'
import { HandoverPage } from './components/pages/handover/HandoverPage'
import { ShipperPage } from './components/pages/shipper/ShipperPage'
import { ChatPage } from './components/pages/chat/ChatPage'

function App() {
  const queryClient = useQueryClient()
  const authQuery = useQuery({ queryKey: ['auth', 'me'], queryFn: () => api.me(), retry: false })
  const user = authQuery.data ?? null

  useEffect(() => {
    function handleUnauthorized() {
      queryClient.setQueryData(['auth', 'me'], null)
      queryClient.removeQueries({ queryKey: ['orders'] })
      queryClient.removeQueries({ queryKey: ['products'] })
      queryClient.removeQueries({ queryKey: ['users'] })
      queryClient.removeQueries({ queryKey: ['shipper-orders'] })
    }

    window.addEventListener(unauthorizedEventName, handleUnauthorized)
    return () => window.removeEventListener(unauthorizedEventName, handleUnauthorized)
  }, [queryClient])

  if (authQuery.isLoading) {
    return (
      <main className="auth-shell">
        <section className="auth-card">
          <Loader2 className="spin" size={22} />
          <strong>Dang kiem tra dang nhap...</strong>
        </section>
      </main>
    )
  }

  return (
    <Routes>
      <Route
        path="/"
        element={<Navigate to={user ? defaultPath(user) : '/login'} replace />}
      />
      <Route
        path="/login"
        element={user ? <Navigate to={defaultPath(user)} replace /> : <LoginPage />}
      />
      <Route
        element={
          <ProtectedRoute user={user} roles={['ADMIN']}>
            <AdminLayout user={user} />
          </ProtectedRoute>
        }
      >
        <Route path="/admin" element={<DashboardPage />} />
        <Route path="/admin/orders" element={<OrdersPage />} />
        <Route path="/admin/products" element={<ProductsPage />} />
        <Route path="/admin/handover" element={<HandoverPage />} />
        <Route path="/admin/inventory" element={<InventoryPage />} />
        <Route path="/admin/loyalty" element={<LoyaltyPage />} />
      </Route>
      <Route
        path="/shipper"
        element={
          <ProtectedRoute user={user} roles={['ADMIN', 'SHIPPER']}>
            <ShipperPage user={user} />
          </ProtectedRoute>
        }
      />
      <Route path="/chat" element={<ChatPage />} />
      <Route
        path="*"
        element={<Navigate to={user ? defaultPath(user) : '/login'} replace />}
      />
    </Routes>
  )
}

export default App
