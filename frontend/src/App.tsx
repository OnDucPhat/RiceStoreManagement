import {
  AlertCircle,
  Archive,
  Bot,
  CheckCircle2,
  ClipboardCheck,
  Gift,
  Home,
  Loader2,
  LogOut,
  MessageCircle,
  Package,
  Phone,
  Plus,
  Search,
  Send,
  ShoppingBag,
  Truck,
  UserCircle,
  Warehouse,
} from 'lucide-react'
import { QRCodeCanvas } from 'qrcode.react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useForm, type UseFormRegisterReturn } from 'react-hook-form'
import { Navigate, NavLink, Outlet, Route, Routes } from 'react-router-dom'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  api,
  type Order,
  type OrderSource,
  type OrderStatus,
  parseProductDetails,
  type ProductInput,
  type RiceProduct,
  type StockEntry,
  unauthorizedEventName,
  type User,
  type UserRole,
} from './api'

const orderStatuses: OrderStatus[] = ['PENDING', 'DELIVERED_WAITING_HANDOVER', 'COMPLETED']
const statusLabels: Record<OrderStatus, string> = {
  PENDING: 'Chờ giao',
  DELIVERED_WAITING_HANDOVER: 'Chờ bàn giao',
  COMPLETED: 'Hoàn tất',
}
const sourceLabels: Record<OrderSource, string> = {
  MESSENGER: 'Messenger',
  MANUAL: 'Tại cửa hàng',
  WEB: 'Web chat',
}

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
      <Route path="/" element={<Navigate to={user ? defaultPath(user) : '/login'} replace />} />
      <Route path="/login" element={user ? <Navigate to={defaultPath(user)} replace /> : <LoginPage />} />
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
      <Route path="*" element={<Navigate to={user ? defaultPath(user) : '/login'} replace />} />
    </Routes>
  )
}

function LoginPage() {
  const queryClient = useQueryClient()
  const form = useForm<{ username: string; password: string }>({
    defaultValues: { username: '', password: '' },
  })
  const mutation = useMutation({
    mutationFn: api.login,
    onSuccess: (user) => {
      queryClient.setQueryData(['auth', 'me'], user)
    },
  })

  return (
    <main className="auth-shell">
      <section className="auth-card">
        <div className="auth-mark">
          <UserCircle size={28} />
        </div>
        <div>
          <h1>Dang nhap RiceStore</h1>
          <p>Admin quan ly cua hang, shipper xem don duoc giao.</p>
        </div>
        <form className="form-grid single" onSubmit={form.handleSubmit((values) => mutation.mutate(values))}>
          <TextField label="Tai khoan" registration={form.register('username', { required: true })} />
          <TextField label="Mat khau" type="password" registration={form.register('password', { required: true })} />
          <MutationError error={mutation.error} />
          <button className="button primary" type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? <Loader2 className="spin" size={18} /> : <UserCircle size={18} />} Dang nhap
          </button>
        </form>
      </section>
    </main>
  )
}

function ProtectedRoute({ user, roles, children }: { user: User | null; roles: UserRole[]; children: React.ReactNode }) {
  if (!user) {
    return <Navigate to="/login" replace />
  }
  if (!roles.includes(user.role)) {
    return <Navigate to={defaultPath(user)} replace />
  }
  return children
}

function AdminLayout({ user }: { user: User | null }) {
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
          <button className="nav-logout" type="button" onClick={() => logoutMutation.mutate()}>
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

function DashboardPage() {
  const ordersQuery = useQuery({ queryKey: ['orders'], queryFn: () => api.orders() })
  const productsQuery = useQuery({ queryKey: ['products'], queryFn: () => api.products() })
  const orders = ordersQuery.data ?? []
  const products = productsQuery.data ?? []
  const stats = useMemo(() => buildDashboardStats(orders, products), [orders, products])

  return (
    <section className="page">
      <PageHeader title="Tổng quan" subtitle="Tình hình đơn hàng và sản phẩm đang bán hôm nay." />
      <DataState isLoading={ordersQuery.isLoading || productsQuery.isLoading} error={ordersQuery.error || productsQuery.error} />
      <div className="stats-grid">
        <StatCard label="Đơn chờ giao" value={stats.pending} tone="warning" />
        <StatCard label="Chờ bàn giao" value={stats.waitingHandover} tone="info" />
        <StatCard label="Đơn hoàn tất" value={stats.completed} tone="success" />
        <StatCard label="Doanh thu hoàn tất" value={formatMoney(stats.revenue)} tone="neutral" />
        <StatCard label="Loại gạo đang bán" value={stats.activeProducts} tone="neutral" />
      </div>
      <div className="two-column">
        <section className="panel">
          <PanelTitle title="Đơn cần chú ý" icon={<AlertCircle size={18} />} />
          <OrderListCompact orders={orders.filter((order) => order.status !== 'COMPLETED').slice(0, 8)} />
        </section>
        <section className="panel">
          <PanelTitle title="Sản phẩm nổi bật" icon={<Package size={18} />} />
          <div className="product-mini-list">
            {products
              .filter((product) => product.active)
              .slice(0, 8)
              .map((product) => (
                <div key={product.id} className="mini-row">
                  <strong>{product.name}</strong>
                  <span>{formatMoney(product.price_per_kg)}/kg</span>
                </div>
              ))}
          </div>
        </section>
      </div>
    </section>
  )
}

function OrdersPage() {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<OrderStatus | 'ALL'>('ALL')
  const [search, setSearch] = useState('')
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [showRetailCreate, setShowRetailCreate] = useState(false)
  const [shipperByOrder, setShipperByOrder] = useState<Record<number, string>>({})
  const ordersQuery = useQuery({
    queryKey: ['orders', status],
    queryFn: () => api.orders(status === 'ALL' ? undefined : status),
  })
  const shippersQuery = useQuery({ queryKey: ['users', 'SHIPPER'], queryFn: () => api.users('SHIPPER') })
  const assignMutation = useMutation({
    mutationFn: ({ orderId, shipperId }: { orderId: number; shipperId: number }) => api.assignShipper(orderId, shipperId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      queryClient.invalidateQueries({ queryKey: ['shipper-orders'] })
    },
  })
  const orders = filterOrders(ordersQuery.data ?? [], search)

  return (
    <section className="page">
      <PageHeader
        title="Đơn hàng"
        subtitle="Tạo đơn, lọc trạng thái, xem chi tiết và gán shipper."
        action={
          <div className="action-group">
            <button className="button primary" type="button" onClick={() => setShowRetailCreate(true)}>
              <Plus size={18} /> Tạo đơn tại quầy
            </button>
            <button className="button secondary" type="button" onClick={() => setShowCreate(true)}>
              <Plus size={18} /> Tạo đơn online
            </button>
          </div>
        }
      />
      <div className="toolbar">
        <div className="segmented">
          <button className={status === 'ALL' ? 'active' : ''} onClick={() => setStatus('ALL')} type="button">
            Tất cả
          </button>
          {orderStatuses.map((item) => (
            <button key={item} className={status === item ? 'active' : ''} onClick={() => setStatus(item)} type="button">
              {statusLabels[item]}
            </button>
          ))}
        </div>
        <label className="search-box">
          <Search size={18} />
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm tên, SĐT, địa chỉ..." />
        </label>
      </div>
      <DataState isLoading={ordersQuery.isLoading} error={ordersQuery.error || assignMutation.error} />
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Mã</th>
              <th>Khách hàng</th>
              <th>Sản phẩm</th>
              <th>Tổng tiền</th>
              <th>Nguồn</th>
              <th>Trạng thái</th>
              <th>Shipper</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {orders.map((order) => (
              <tr key={order.id}>
                <td>#{order.id}</td>
                <td>
                  <strong>{order.customer_name}</strong>
                  <small>{order.customer_phone}</small>
                  <small>{order.address}</small>
                </td>
                <td>{parseProductDetails(order.product_details)}</td>
                <td>{formatMoney(order.total_price)}</td>
                <td>{sourceLabels[order.source]}</td>
                <td>
                  <StatusBadge status={order.status} />
                </td>
                <td>
                  <div className="assign-control">
                    <select
                      value={String(order.shipper_id ?? shipperByOrder[order.id] ?? '')}
                      disabled={order.status !== 'PENDING' || assignMutation.isPending}
                      onChange={(event) => {
                        const shipperId = event.target.value
                        setShipperByOrder((current) => ({ ...current, [order.id]: shipperId }))
                        if (shipperId) {
                          assignMutation.mutate({ orderId: order.id, shipperId: Number(shipperId) })
                        }
                      }}
                    >
                      <option value="">Chọn shipper</option>
                      {(shippersQuery.data ?? []).map((shipper) => (
                        <option key={shipper.id} value={shipper.id}>
                          {shipper.username}
                        </option>
                      ))}
                    </select>
                  </div>
                </td>
                <td>
                  <button className="button ghost" type="button" onClick={() => setSelectedOrder(order)}>
                    Chi tiết
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {orders.length === 0 && !ordersQuery.isLoading ? <EmptyState text="Chưa có đơn phù hợp." /> : null}
      {showCreate ? <CreateOrderModal onClose={() => setShowCreate(false)} /> : null}
      {showRetailCreate ? <CreateRetailOrderModal onClose={() => setShowRetailCreate(false)} /> : null}
      {selectedOrder ? <OrderDetailDrawer order={selectedOrder} onClose={() => setSelectedOrder(null)} /> : null}
    </section>
  )
}

const orderSchema = z.object({
  customer_name: z.string().min(1, 'Nhập tên khách'),
  customer_phone: z.string().min(1, 'Nhập SĐT'),
  address: z.string().min(1, 'Nhập địa chỉ'),
  product_details: z.string().min(1, 'Nhập sản phẩm'),
  total_price: z.coerce.number().min(0, 'Tổng tiền không âm'),
})

type OrderFormInput = z.input<typeof orderSchema>
type OrderFormValues = z.output<typeof orderSchema>

function CreateOrderModal({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient()
  const form = useForm<OrderFormInput, unknown, OrderFormValues>({
    resolver: zodResolver(orderSchema),
    defaultValues: {
      customer_name: '',
      customer_phone: '',
      address: '',
      product_details: '',
      total_price: 0,
    },
  })
  const mutation = useMutation({
    mutationFn: api.createOrder,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      onClose()
    },
  })

  return (
    <Modal title="Tạo đơn thủ công" onClose={onClose}>
      <form className="form-grid" onSubmit={form.handleSubmit((values) => mutation.mutate(values))}>
        <TextField label="Tên khách" registration={form.register('customer_name')} error={fieldError(form.formState.errors.customer_name?.message)} />
        <TextField label="SĐT" registration={form.register('customer_phone')} error={fieldError(form.formState.errors.customer_phone?.message)} />
        <TextField label="Địa chỉ" registration={form.register('address')} error={fieldError(form.formState.errors.address?.message)} />
        <TextArea label="Sản phẩm" registration={form.register('product_details')} error={fieldError(form.formState.errors.product_details?.message)} />
        <TextField label="Tổng tiền" type="number" registration={form.register('total_price')} error={fieldError(form.formState.errors.total_price?.message)} />
        <MutationError error={mutation.error} />
        <div className="modal-actions">
          <button className="button ghost" type="button" onClick={onClose}>
            Hủy
          </button>
          <button className="button primary" type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? <Loader2 className="spin" size={18} /> : <Plus size={18} />} Lưu đơn
          </button>
        </div>
      </form>
    </Modal>
  )
}

type RetailOrderLine = {
  productId: number
  productName: string
  pricePerKg: number
  quantityKg: number
  lineTotal: number
}

function CreateRetailOrderModal({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient()
  const productsQuery = useQuery({ queryKey: ['products', true], queryFn: () => api.products(true) })
  const [lines, setLines] = useState<RetailOrderLine[]>([])
  const [customerName, setCustomerName] = useState('')
  const [customerPhone, setCustomerPhone] = useState('')
  const [loyaltyPhone, setLoyaltyPhone] = useState('')
  const [quantityInputs, setQuantityInputs] = useState<Record<number, string>>({})
  const [errors, setErrors] = useState<Record<string, string>>({})

  const totalPrice = useMemo(() => lines.reduce((sum, l) => sum + l.lineTotal, 0), [lines])

  function addProduct(product: RiceProduct) {
    const qty = parseFloat(quantityInputs[product.id] || '0')
    if (!qty || qty <= 0) return
    setLines((prev) => {
      const existing = prev.find((l) => l.productId === product.id)
      if (existing) {
        const newQty = existing.quantityKg + qty
        return prev.map((l) =>
          l.productId === product.id
            ? { ...l, quantityKg: newQty, lineTotal: newQty * l.pricePerKg }
            : l
        )
      }
      return [
        ...prev,
        {
          productId: product.id,
          productName: product.name,
          pricePerKg: product.price_per_kg,
          quantityKg: qty,
          lineTotal: qty * product.price_per_kg,
        },
      ]
    })
    setQuantityInputs((prev) => ({ ...prev, [product.id]: '' }))
  }

  function removeLine(productId: number) {
    setLines((prev) => prev.filter((l) => l.productId !== productId))
  }

  function validate(): boolean {
    const errs: Record<string, string> = {}
    if (!customerName.trim()) errs.customerName = 'Nhập tên khách'
    if (lines.length === 0) errs.lines = 'Chọn ít nhất 1 sản phẩm'
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  const mutation = useMutation({
    mutationFn: () =>
      api.createRetailOrder({
        customer_name: customerName.trim(),
        customer_phone: customerPhone.trim() || undefined,
        loyalty_phone: loyaltyPhone.trim() || undefined,
        items: lines.map((l) => ({ product_id: l.productId, quantity_kg: l.quantityKg })),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      queryClient.invalidateQueries({ queryKey: ['products'] })
      onClose()
    },
  })

  return (
    <Modal title="Tạo đơn tại quầy" onClose={onClose}>
      <div className="retail-order-form">
        {/* Product picker */}
        <div className="retail-product-picker">
          <h4>Chọn sản phẩm</h4>
          <DataState isLoading={productsQuery.isLoading} error={productsQuery.error} />
          <div className="retail-product-list">
            {(productsQuery.data ?? []).map((product) => (
              <div key={product.id} className="retail-product-row">
                <div className="retail-product-info">
                  <strong>{product.name}</strong>
                  <span className="retail-price">{formatMoney(product.price_per_kg)}/kg</span>
                  <span className="retail-stock">Còn {Number(product.stock_kg ?? 0).toFixed(1)} kg</span>
                </div>
                <div className="retail-product-add">
                  <input
                    type="number"
                    min="0.1"
                    step="0.1"
                    placeholder="kg"
                    value={quantityInputs[product.id] ?? ''}
                    onChange={(e) =>
                      setQuantityInputs((prev) => ({ ...prev, [product.id]: e.target.value }))
                    }
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault()
                        addProduct(product)
                      }
                    }}
                  />
                  <button
                    className="button primary small"
                    type="button"
                    onClick={() => addProduct(product)}
                  >
                    +
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Selected items */}
        <div className="retail-order-lines">
          <h4>Đơn hàng</h4>
          {errors.lines ? <p className="field-error">{errors.lines}</p> : null}
          {lines.length === 0 ? (
            <p className="empty-hint">Chưa chọn sản phẩm nào.</p>
          ) : (
            <table className="retail-lines-table">
              <thead>
                <tr>
                  <th>Sản phẩm</th>
                  <th>Đơn giá</th>
                  <th>Khối lượng</th>
                  <th>Thành tiền</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {lines.map((line) => (
                  <tr key={line.productId}>
                    <td>{line.productName}</td>
                    <td>{formatMoney(line.pricePerKg)}</td>
                    <td>{line.quantityKg.toFixed(1)} kg</td>
                    <td>{formatMoney(line.lineTotal)}</td>
                    <td>
                      <button
                        className="button ghost small danger"
                        type="button"
                        onClick={() => removeLine(line.productId)}
                      >
                        x
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={3}><strong>Tổng cộng</strong></td>
                  <td colSpan={2}><strong className="retail-total">{formatMoney(totalPrice)}</strong></td>
                </tr>
              </tfoot>
            </table>
          )}
        </div>

        {/* Customer info */}
        <div className="retail-customer-info">
          <h4>Thông tin khách hàng</h4>
          <div className="form-grid">
            <SimpleField
              label="Tên khách *"
              value={customerName}
              onChange={setCustomerName}
              error={errors.customerName}
            />
            <SimpleField
              label="SĐT"
              value={customerPhone}
              onChange={setCustomerPhone}
            />
            <SimpleField
              label="SĐT tích điểm"
              value={loyaltyPhone}
              onChange={setLoyaltyPhone}
              placeholder="Bỏ trống = dùng SĐT trên"
            />
          </div>
        </div>

        <MutationError error={mutation.error} />
        <div className="modal-actions">
          <button className="button ghost" type="button" onClick={onClose}>
            Hủy
          </button>
          <button
            className="button success"
            type="button"
            disabled={mutation.isPending}
            onClick={() => {
              if (validate()) mutation.mutate()
            }}
          >
            {mutation.isPending ? <Loader2 className="spin" size={18} /> : <CheckCircle2 size={18} />} Hoàn tất thanh toán
          </button>
        </div>
      </div>
    </Modal>
  )
}

function ProductsPage() {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<RiceProduct | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const productsQuery = useQuery({ queryKey: ['products'], queryFn: () => api.products() })
  const activeMutation = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) => api.setProductActive(id, active),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['products'] }),
  })

  return (
    <section className="page">
      <PageHeader
        title="Loại gạo"
        subtitle="Quản lý giá bán, giá vốn, lợi nhuận và trạng thái bán."
        action={
          <button className="button primary" type="button" onClick={() => setShowCreate(true)}>
            <Plus size={18} /> Thêm loại gạo
          </button>
        }
      />
      <DataState isLoading={productsQuery.isLoading} error={productsQuery.error || activeMutation.error} />
      <div className="product-grid">
        {(productsQuery.data ?? []).map((product) => (
          <article key={product.id} className="product-card">
            <div className="card-row">
              <h3>{product.name}</h3>
              <span className={`pill ${product.active ? 'pill-green' : 'pill-muted'}`}>{product.active ? 'Đang bán' : 'Tạm ẩn'}</span>
            </div>
            <p>{product.characteristics}</p>
            <div className="price-grid">
              <span>
                Giá bán <strong>{formatMoney(product.price_per_kg)}</strong>
              </span>
              <span>
                Giá vốn <strong>{formatMoney(product.cost_per_kg)}</strong>
              </span>
              <span>
                Lãi/kg <strong>{formatMoney(product.profit_per_kg)}</strong>
              </span>
              <span>
                Tồn kho <strong>{Number(product.stock_kg ?? 0).toFixed(1)} kg</strong>
              </span>
            </div>
            <div className="card-actions">
              <button className="button ghost" type="button" onClick={() => setEditing(product)}>
                Sửa
              </button>
              <button
                className="button ghost"
                type="button"
                disabled={activeMutation.isPending}
                onClick={() => activeMutation.mutate({ id: product.id, active: !product.active })}
              >
                {product.active ? 'Tạm ẩn' : 'Bật bán'}
              </button>
            </div>
          </article>
        ))}
      </div>
      {(productsQuery.data ?? []).length === 0 && !productsQuery.isLoading ? <EmptyState text="Chưa có loại gạo." /> : null}
      {showCreate ? <ProductModal onClose={() => setShowCreate(false)} /> : null}
      {editing ? <ProductModal product={editing} onClose={() => setEditing(null)} /> : null}
    </section>
  )
}

const productSchema = z.object({
  name: z.string().min(1, 'Nhập tên gạo'),
  characteristics: z.string().min(1, 'Nhập đặc tính'),
  price_per_kg: z.coerce.number().min(0, 'Giá bán không âm'),
  cost_per_kg: z.coerce.number().min(0, 'Giá vốn không âm'),
  active: z.boolean(),
})

type ProductFormInput = z.input<typeof productSchema>
type ProductFormValues = z.output<typeof productSchema>

function ProductModal({ product, onClose }: { product?: RiceProduct; onClose: () => void }) {
  const queryClient = useQueryClient()
  const form = useForm<ProductFormInput, unknown, ProductFormValues>({
    resolver: zodResolver(productSchema),
    defaultValues: product
      ? {
          name: product.name,
          characteristics: product.characteristics,
          price_per_kg: product.price_per_kg,
          cost_per_kg: product.cost_per_kg,
          active: product.active,
        }
      : {
          name: '',
          characteristics: '',
          price_per_kg: 0,
          cost_per_kg: 0,
          active: true,
        },
  })
  const mutation = useMutation({
    mutationFn: (values: ProductInput) => (product ? api.updateProduct(product.id, values) : api.createProduct(values)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] })
      onClose()
    },
  })

  return (
    <Modal title={product ? 'Sửa loại gạo' : 'Thêm loại gạo'} onClose={onClose}>
      <form className="form-grid" onSubmit={form.handleSubmit((values) => mutation.mutate(values))}>
        <TextField label="Tên gạo" registration={form.register('name')} error={fieldError(form.formState.errors.name?.message)} />
        <TextArea label="Đặc tính" registration={form.register('characteristics')} error={fieldError(form.formState.errors.characteristics?.message)} />
        <TextField label="Giá bán/kg" type="number" registration={form.register('price_per_kg')} error={fieldError(form.formState.errors.price_per_kg?.message)} />
        <TextField label="Giá vốn/kg" type="number" registration={form.register('cost_per_kg')} error={fieldError(form.formState.errors.cost_per_kg?.message)} />
        <label className="toggle-row">
          <input type="checkbox" {...form.register('active')} />
          Đang bán
        </label>
        <MutationError error={mutation.error} />
        <div className="modal-actions">
          <button className="button ghost" type="button" onClick={onClose}>
            Hủy
          </button>
          <button className="button primary" type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? <Loader2 className="spin" size={18} /> : <CheckCircle2 size={18} />} Lưu
          </button>
        </div>
      </form>
    </Modal>
  )
}

function InventoryPage() {
  const queryClient = useQueryClient()
  const productsQuery = useQuery({ queryKey: ['products'], queryFn: () => api.products() })
  const products = productsQuery.data ?? []
  const [selectedProductId, setSelectedProductId] = useState('')
  const [qtyKg, setQtyKg] = useState('')
  const [costPerKg, setCostPerKg] = useState('')
  const [historyProductId, setHistoryProductId] = useState<number | null>(null)
  const historyQuery = useQuery({
    queryKey: ['stock-history', historyProductId],
    queryFn: () => api.stockHistory(historyProductId!),
    enabled: historyProductId !== null,
  })

  const selectedProduct = products.find((p) => p.id === Number(selectedProductId)) ?? null
  const previewNewCost = (() => {
    if (!selectedProduct || !qtyKg || !costPerKg) return null
    const oldStock = Number(selectedProduct.stock_kg ?? 0)
    const oldCost = Number(selectedProduct.cost_per_kg)
    const qty = Number(qtyKg)
    const newCostImport = Number(costPerKg)
    if (qty <= 0) return null
    if (oldStock === 0) return newCostImport
    return (oldStock * oldCost + qty * newCostImport) / (oldStock + qty)
  })()

  const importMutation = useMutation({
    mutationFn: () =>
      api.importStock({
        product_id: Number(selectedProductId),
        quantity_kg: Number(qtyKg),
        cost_per_kg: Number(costPerKg),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] })
      queryClient.invalidateQueries({ queryKey: ['stock-history', Number(selectedProductId)] })
      setQtyKg('')
      setCostPerKg('')
    },
  })

  return (
    <section className="page">
      <PageHeader title="Nhập kho" subtitle="Nhập hàng và theo dõi tồn kho từng loại gạo." />
      <DataState isLoading={productsQuery.isLoading} error={productsQuery.error} />
      <div className="two-column">
        <section className="panel">
          <PanelTitle title="Nhập hàng" icon={<Archive size={18} />} />
          <div className="form-grid single">
            <label className="field">
              <span>Loại gạo</span>
              <select value={selectedProductId} onChange={(e) => { setSelectedProductId(e.target.value); setHistoryProductId(e.target.value ? Number(e.target.value) : null) }}>
                <option value="">Chọn loại gạo</option>
                {products.map((p) => (
                  <option key={p.id} value={p.id}>{p.name} (tồn: {Number(p.stock_kg ?? 0).toFixed(1)} kg)</option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>Số kg nhập</span>
              <input type="number" min="0.1" step="0.1" value={qtyKg} onChange={(e) => setQtyKg(e.target.value)} placeholder="100" />
            </label>
            <label className="field">
              <span>Giá vốn lần này (đ/kg)</span>
              <input type="number" min="0" step="100" value={costPerKg} onChange={(e) => setCostPerKg(e.target.value)} placeholder="12000" />
            </label>
            {selectedProduct && qtyKg && costPerKg && (
              <div className="import-preview">
                <div className="preview-row">
                  <span>Tồn hiện tại</span>
                  <strong>{Number(selectedProduct.stock_kg ?? 0).toFixed(1)} kg</strong>
                </div>
                <div className="preview-row">
                  <span>Giá vốn hiện tại</span>
                  <strong>{formatMoney(selectedProduct.cost_per_kg)}/kg</strong>
                </div>
                <div className="preview-row accent">
                  <span>Tồn sau nhập</span>
                  <strong>{(Number(selectedProduct.stock_kg ?? 0) + Number(qtyKg)).toFixed(1)} kg</strong>
                </div>
                {previewNewCost !== null && (
                  <div className="preview-row accent">
                    <span>Giá vốn bình quân mới</span>
                    <strong>{formatMoney(previewNewCost)}/kg</strong>
                  </div>
                )}
              </div>
            )}
            <MutationError error={importMutation.error} />
            <button
              className="button primary"
              type="button"
              disabled={!selectedProductId || !qtyKg || !costPerKg || importMutation.isPending}
              onClick={() => importMutation.mutate()}
            >
              {importMutation.isPending ? <Loader2 className="spin" size={18} /> : <Plus size={18} />} Nhập kho
            </button>
          </div>
        </section>
        <section className="panel">
          <PanelTitle title="Tồn kho hiện tại" icon={<Warehouse size={18} />} />
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Loại gạo</th>
                  <th>Tồn (kg)</th>
                  <th>Giá vốn</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {products.map((p) => (
                  <tr key={p.id} className={!p.active ? 'row-muted' : ''}>
                    <td><strong>{p.name}</strong></td>
                    <td>
                      <span className={Number(p.stock_kg ?? 0) <= 0 ? 'pill pill-muted' : 'pill pill-green'}>
                        {Number(p.stock_kg ?? 0).toFixed(1)} kg
                      </span>
                    </td>
                    <td>{formatMoney(p.cost_per_kg)}</td>
                    <td>
                      <button className="button ghost" type="button" onClick={() => setHistoryProductId(p.id === historyProductId ? null : p.id)}>
                        Lịch sử
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {historyProductId !== null && (
            <div className="history-panel">
              <h3>Lịch sử nhập – {products.find((p) => p.id === historyProductId)?.name}</h3>
              <DataState isLoading={historyQuery.isLoading} error={historyQuery.error} />
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr><th>Ngày nhập</th><th>Số kg</th><th>Giá vốn</th></tr>
                  </thead>
                  <tbody>
                    {(historyQuery.data ?? []).map((entry: StockEntry) => (
                      <tr key={entry.id}>
                        <td>{new Date(entry.imported_at).toLocaleString('vi-VN')}</td>
                        <td>{Number(entry.quantity_kg).toFixed(1)} kg</td>
                        <td>{formatMoney(entry.cost_per_kg)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {(historyQuery.data ?? []).length === 0 && !historyQuery.isLoading && <EmptyState text="Chưa có lịch sử nhập kho." />}
            </div>
          )}
        </section>
      </div>
    </section>
  )
}

function LoyaltyPage() {
  const [searchPhone, setSearchPhone] = useState('')
  const [lookedUpPhone, setLookedUpPhone] = useState<string | null>(null)
  const [confirmGiftPhone, setConfirmGiftPhone] = useState<string | null>(null)
  const queryClient = useQueryClient()

  const lookupQuery = useQuery({
    queryKey: ['loyalty-phone', lookedUpPhone],
    queryFn: () => api.loyaltyByPhone(lookedUpPhone!),
    enabled: lookedUpPhone !== null,
    retry: false,
  })

  const giftMutation = useMutation({
    mutationFn: (phone: string) => api.giveGift(phone),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['loyalty-phone', confirmGiftPhone] })
      setConfirmGiftPhone(null)
    },
  })

  function handleSearch(e: React.FormEvent) {
    e.preventDefault()
    const trimmed = searchPhone.trim()
    if (trimmed) setLookedUpPhone(trimmed)
  }

  const loyalty = lookupQuery.data ?? null

  return (
    <section className="page">
      <PageHeader title="Tích điểm" subtitle="Tra cứu điểm tích lũy theo số điện thoại. Điểm = tổng kg gạo đã mua." />
      <div className="two-column">
        <section className="panel">
          <PanelTitle title="Tra cứu SĐT" icon={<Phone size={18} />} />
          <form className="form-grid single" onSubmit={handleSearch}>
            <label className="field">
              <span>Số điện thoại</span>
              <input
                type="tel"
                value={searchPhone}
                onChange={(e) => setSearchPhone(e.target.value)}
                placeholder="0901234567"
              />
            </label>
            <button className="button primary" type="submit">
              <Search size={18} /> Tra cứu
            </button>
          </form>
          <DataState isLoading={lookupQuery.isLoading} error={lookupQuery.error} />
          {loyalty && (
            <div className="loyalty-card">
              <div className="loyalty-phone">
                <Phone size={20} />
                <strong>{loyalty.phone}</strong>
              </div>
              <div className="loyalty-stats">
                <div className="loyalty-stat">
                  <span>Tổng điểm</span>
                  <strong className="loyalty-points">{Number(loyalty.total_points).toFixed(0)} điểm</strong>
                  <small>(≈ {Number(loyalty.total_points).toFixed(0)} kg gạo)</small>
                </div>
                <div className="loyalty-stat">
                  <span>Lần mua</span>
                  <strong>{loyalty.purchase_count} lần</strong>
                </div>
                {loyalty.last_reset_at && (
                  <div className="loyalty-stat">
                    <span>Tặng quà lần cuối</span>
                    <strong>{new Date(loyalty.last_reset_at).toLocaleDateString('vi-VN')}</strong>
                  </div>
                )}
              </div>
              {confirmGiftPhone === loyalty.phone ? (
                <div className="gift-confirm">
                  <p>Xác nhận đã tặng quà và reset điểm về 0?</p>
                  <div className="modal-actions">
                    <button className="button ghost" type="button" onClick={() => setConfirmGiftPhone(null)}>Hủy</button>
                    <button
                      className="button primary"
                      type="button"
                      disabled={giftMutation.isPending}
                      onClick={() => giftMutation.mutate(loyalty.phone)}
                    >
                      {giftMutation.isPending ? <Loader2 className="spin" size={18} /> : <Gift size={18} />} Xác nhận tặng quà
                    </button>
                  </div>
                  <MutationError error={giftMutation.error} />
                </div>
              ) : (
                <button
                  className="button primary"
                  type="button"
                  onClick={() => setConfirmGiftPhone(loyalty.phone)}
                >
                  <Gift size={18} /> Đã tặng quà (reset điểm)
                </button>
              )}
            </div>
          )}
          {lookupQuery.isError && lookedUpPhone && !lookupQuery.isLoading && (
            <EmptyState text={`Không tìm thấy SĐT ${lookedUpPhone}.`} />
          )}
        </section>
        <section className="panel">
          <LoyaltyTopList onSelect={(phone) => { setSearchPhone(phone); setLookedUpPhone(phone) }} />
        </section>
      </div>
    </section>
  )
}

function LoyaltyTopList({ onSelect }: { onSelect: (phone: string) => void }) {
  const allQuery = useQuery({ queryKey: ['loyalty-all'], queryFn: () => api.loyaltyAll() })
  const all = allQuery.data ?? []
  return (
    <>
      <PanelTitle title="Danh sách tích điểm" icon={<Gift size={18} />} />
      <DataState isLoading={allQuery.isLoading} error={allQuery.error} />
      <div className="table-wrap">
        <table>
          <thead>
            <tr><th>SĐT</th><th>Điểm</th><th>Lần mua</th><th></th></tr>
          </thead>
          <tbody>
            {all.map((item) => (
              <tr key={item.id}>
                <td><strong>{item.phone}</strong></td>
                <td>{Number(item.total_points).toFixed(0)}</td>
                <td>{item.purchase_count}</td>
                <td>
                  <button className="button ghost" type="button" onClick={() => onSelect(item.phone)}>
                    Chi tiết
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {all.length === 0 && !allQuery.isLoading && <EmptyState text="Chưa có khách tích điểm." />}
    </>
  )
}

function HandoverPage() {
  const queryClient = useQueryClient()
  const [adminId, setAdminId] = useState('')
  const [shipperId, setShipperId] = useState('')
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const adminsQuery = useQuery({ queryKey: ['users', 'ADMIN'], queryFn: () => api.users('ADMIN') })
  const shippersQuery = useQuery({ queryKey: ['users', 'SHIPPER'], queryFn: () => api.users('SHIPPER') })
  const ordersQuery = useQuery({ queryKey: ['orders', 'DELIVERED_WAITING_HANDOVER'], queryFn: () => api.orders('DELIVERED_WAITING_HANDOVER') })
  const orders = (ordersQuery.data ?? []).filter((order) => !shipperId || order.shipper_id === Number(shipperId))
  const selectedOrders = orders.filter((order) => selectedIds.includes(order.id))
  const mutation = useMutation({
    mutationFn: () =>
      api.confirmHandover({
        admin_id: Number(adminId),
        shipper_id: Number(shipperId),
        order_ids: selectedIds,
      }),
    onSuccess: () => {
      setSelectedIds([])
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      queryClient.invalidateQueries({ queryKey: ['shipper-orders'] })
    },
  })
  const payload = shipperId && selectedIds.length > 0 ? JSON.stringify({ shipperId: Number(shipperId), orderIds: selectedIds }) : ''

  return (
    <section className="page">
      <PageHeader title="Bàn giao tiền" subtitle="Chốt các đơn shipper đã giao và đang giữ tiền mặt." />
      <DataState isLoading={adminsQuery.isLoading || shippersQuery.isLoading || ordersQuery.isLoading} error={adminsQuery.error || shippersQuery.error || ordersQuery.error || mutation.error} />
      <section className="panel handover-panel">
        <div className="form-row">
          <SelectBox label="Admin nhận tiền" value={adminId} onChange={setAdminId} users={adminsQuery.data ?? []} />
          <SelectBox
            label="Shipper bàn giao"
            value={shipperId}
            onChange={(value) => {
              setShipperId(value)
              setSelectedIds([])
            }}
            users={shippersQuery.data ?? []}
          />
        </div>
        <div className="handover-summary">
          <span>Đã chọn {selectedIds.length} đơn</span>
          <strong>{formatMoney(sumOrders(selectedOrders))}</strong>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th></th>
                <th>Mã</th>
                <th>Khách hàng</th>
                <th>Sản phẩm</th>
                <th>Tổng tiền</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((order) => (
                <tr key={order.id}>
                  <td>
                    <input
                      type="checkbox"
                      checked={selectedIds.includes(order.id)}
                      onChange={() => setSelectedIds((current) => toggleId(current, order.id))}
                    />
                  </td>
                  <td>#{order.id}</td>
                  <td>
                    <strong>{order.customer_name}</strong>
                    <small>{order.customer_phone}</small>
                  </td>
                  <td>{parseProductDetails(order.product_details)}</td>
                  <td>{formatMoney(order.total_price)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {orders.length === 0 ? <EmptyState text="Không có đơn chờ bàn giao cho shipper này." /> : null}
        <div className="handover-footer">
          <div className="qr-preview">{payload ? <QRCodeCanvas value={payload} size={132} /> : <ClipboardCheck size={48} />}</div>
          <button
            className="button primary"
            type="button"
            disabled={!adminId || !shipperId || selectedIds.length === 0 || mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? <Loader2 className="spin" size={18} /> : <CheckCircle2 size={18} />} Xác nhận bàn giao
          </button>
        </div>
      </section>
    </section>
  )
}

function ShipperPage({ user }: { user: User | null }) {
  const queryClient = useQueryClient()
  const [shipperId, setShipperId] = useState(() => (user?.role === 'SHIPPER' ? String(user.id) : ''))
  const [status, setStatus] = useState<OrderStatus | 'ALL'>('ALL')
  const shippersQuery = useQuery({
    queryKey: ['users', 'SHIPPER'],
    queryFn: () => api.users('SHIPPER'),
    enabled: user?.role === 'ADMIN',
  })
  const visibleShippers = user?.role === 'ADMIN' ? (shippersQuery.data ?? []) : user ? [user] : []
  const selectedShipperId = Number(shipperId)
  const ordersQuery = useQuery({
    queryKey: ['shipper-orders', selectedShipperId, status],
    queryFn: () => api.shipperOrders(selectedShipperId, status === 'ALL' ? undefined : status),
    enabled: Boolean(selectedShipperId),
  })
  const deliverMutation = useMutation({
    mutationFn: (orderId: number) => api.markDelivered(orderId, selectedShipperId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['shipper-orders'] })
      queryClient.invalidateQueries({ queryKey: ['orders'] })
    },
  })
  const orders = ordersQuery.data ?? []
  const deliveredIds = orders.filter((order) => order.status === 'DELIVERED_WAITING_HANDOVER').map((order) => order.id)
  const qrPayload = selectedShipperId && deliveredIds.length ? JSON.stringify({ shipperId: selectedShipperId, orderIds: deliveredIds }) : ''
  const shipperPickerDisabled = user?.role !== 'ADMIN'

  return (
    <main className="shipper-shell">
      <header className="shipper-header">
        <div>
          <h1>Giao hàng</h1>
          <p>Danh sách đơn được phân công cho shipper.</p>
        </div>
        {user?.role === 'ADMIN' ? (
          <NavLink className="button ghost" to="/admin">
            Admin
          </NavLink>
        ) : (
          <LogoutButton />
        )}
      </header>
      <section className="shipper-control">
        <SelectBox label="Chọn shipper" value={shipperId} onChange={setShipperId} users={visibleShippers} disabled={shipperPickerDisabled} />
        <div className="segmented full">
          <button className={status === 'ALL' ? 'active' : ''} onClick={() => setStatus('ALL')} type="button">
            Tất cả
          </button>
          <button className={status === 'PENDING' ? 'active' : ''} onClick={() => setStatus('PENDING')} type="button">
            Chờ giao
          </button>
          <button
            className={status === 'DELIVERED_WAITING_HANDOVER' ? 'active' : ''}
            onClick={() => setStatus('DELIVERED_WAITING_HANDOVER')}
            type="button"
          >
            Đã giao
          </button>
        </div>
      </section>
      <DataState isLoading={ordersQuery.isLoading || shippersQuery.isLoading} error={ordersQuery.error || shippersQuery.error || deliverMutation.error} />
      <section className="delivery-list">
        {orders.map((order) => (
          <article key={order.id} className="delivery-card">
            <div className="card-row">
              <h2>Đơn #{order.id}</h2>
              <StatusBadge status={order.status} />
            </div>
            <p>
              <strong>{order.customer_name}</strong> - {order.customer_phone}
            </p>
            <p>{order.address}</p>
            <p>{parseProductDetails(order.product_details)}</p>
            <strong>{formatMoney(order.total_price)}</strong>
            <button
              className="button primary block touch"
              type="button"
              disabled={order.status !== 'PENDING' || deliverMutation.isPending}
              onClick={() => deliverMutation.mutate(order.id)}
            >
              {deliverMutation.isPending ? <Loader2 className="spin" size={22} /> : <Truck size={22} />} Đã giao và đã thu tiền
            </button>
          </article>
        ))}
      </section>
      {selectedShipperId && orders.length === 0 && !ordersQuery.isLoading ? <EmptyState text="Shipper này chưa có đơn." /> : null}
      <section className="handover-mobile">
        <h2>QR bàn giao cuối ca</h2>
        <p>{deliveredIds.length} đơn đang chờ bàn giao, tổng {formatMoney(sumOrders(orders.filter((order) => deliveredIds.includes(order.id))))}</p>
        <div className="qr-large">{qrPayload ? <QRCodeCanvas value={qrPayload} size={220} /> : <ClipboardCheck size={72} />}</div>
      </section>
    </main>
  )
}

type ChatMessage = { id: string; role: 'bot' | 'user'; text: string; pending?: boolean }

function ChatPage() {
  const sessionKey = 'rice_store_chat_session_id'
  const [sessionId, setSessionId] = useState(() => localStorage.getItem(sessionKey) || createSessionId())
  const [message, setMessage] = useState('')
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: 'welcome',
      role: 'bot',
      text: 'Chào bạn, bạn muốn đặt loại gạo nào? Bạn có thể nhắn luôn số kg, địa chỉ giao hàng và SĐT để mình ghi đơn nhanh hơn nhé.',
    },
  ])
  const mutation = useMutation({
    mutationFn: api.sendChatMessage,
    onSuccess: (data) => {
      setSessionId(data.session_id)
      localStorage.setItem(sessionKey, data.session_id)
      setMessages((current) => replacePending(current, data.reply || 'Mình chưa xử lí được tin nhắn này, bạn nhắn lại giúp mình nhé.'))
    },
    onError: () => {
      setMessages((current) =>
        replacePending(current, 'Hiện tại hệ thống đang chập chờn. Bạn thử nhắn lại hoặc gọi 0342504323 giúp mình nhé.'),
      )
    },
  })
  const messagesEndRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [messages])

  function submitChat(text: string) {
    const trimmed = text.trim()
    if (!trimmed || mutation.isPending) return
    setMessage('')
    setMessages((current) => [
      ...current,
      { id: crypto.randomUUID(), role: 'user', text: trimmed },
      { id: `pending-${Date.now()}`, role: 'bot', text: 'Đang trả lời...', pending: true },
    ])
    mutation.mutate({ session_id: sessionId, message: trimmed })
  }

  return (
    <main className="chat-shell">
      <header className="chat-header">
        <div className="chat-brand">
          <div className="chat-logo">
            <Bot size={24} />
          </div>
          <div>
          <h1>Đặt gạo nhanh</h1>
          <p>Cửa hàng gạo gia đình</p>
        </div>
        </div>
      </header>
      <section className="chat-messages" aria-live="polite">
        {messages.map((item) => (
          <div key={item.id} className={`chat-bubble ${item.role} ${item.pending ? 'pending' : ''}`}>
            {item.text}
          </div>
        ))}
        <div ref={messagesEndRef} aria-hidden="true" />
      </section>
      <section className="quick-actions">
        {['Tôi muốn đặt gạo', 'Tư vấn giúp tôi loại gạo ngon dễ ăn', 'Cho tôi xem các loại gạo đang bán'].map((item) => (
          <button key={item} className="chip" type="button" onClick={() => submitChat(item)}>
            {item}
          </button>
        ))}
      </section>
      {mutation.error ? <div className="toast-error">{errorMessage(mutation.error)}</div> : null}
      <form
        className="chat-composer"
        onSubmit={(event) => {
          event.preventDefault()
          submitChat(message)
        }}
      >
        <textarea value={message} onChange={(event) => setMessage(event.target.value)} placeholder="Nhắn loại gạo, số kg, địa chỉ, SĐT..." rows={1} />
        <button className="button primary" type="submit" disabled={mutation.isPending || !message.trim()}>
          <Send size={18} /> Gửi
        </button>
      </form>
    </main>
  )
}

function PageHeader({ title, subtitle, action }: { title: string; subtitle: string; action?: React.ReactNode }) {
  return (
    <header className="page-header">
      <div>
        <h1>{title}</h1>
        <p>{subtitle}</p>
      </div>
      {action}
    </header>
  )
}

function StatCard({ label, value, tone }: { label: string; value: string | number; tone: 'warning' | 'info' | 'success' | 'neutral' }) {
  return (
    <article className={`stat-card ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  )
}

function PanelTitle({ title, icon }: { title: string; icon: React.ReactNode }) {
  return (
    <div className="panel-title">
      {icon}
      <h2>{title}</h2>
    </div>
  )
}

function OrderListCompact({ orders }: { orders: Order[] }) {
  if (orders.length === 0) return <EmptyState text="Không có đơn cần chú ý." />
  return (
    <div className="compact-list">
      {orders.map((order) => (
        <div key={order.id} className="mini-row">
          <div>
            <strong>#{order.id} {order.customer_name}</strong>
            <span>{parseProductDetails(order.product_details)}</span>
          </div>
          <StatusBadge status={order.status} />
        </div>
      ))}
    </div>
  )
}

function StatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`status ${status.toLowerCase()}`}>{statusLabels[status]}</span>
}

function DataState({ isLoading, error }: { isLoading?: boolean; error?: unknown }) {
  if (isLoading) {
    return (
      <div className="inline-state">
        <Loader2 className="spin" size={18} /> Đang tải dữ liệu...
      </div>
    )
  }
  if (error) {
    return (
      <div className="inline-state error">
        <AlertCircle size={18} /> {errorMessage(error)}
      </div>
    )
  }
  return null
}

function MutationError({ error }: { error?: unknown }) {
  if (!error) return null
  return <div className="form-error wide">{errorMessage(error)}</div>
}

function LogoutButton() {
  const queryClient = useQueryClient()
  const mutation = useMutation({
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
    <button className="button ghost" type="button" onClick={() => mutation.mutate()} disabled={mutation.isPending}>
      <LogOut size={18} /> Dang xuat
    </button>
  )
}

function EmptyState({ text }: { text: string }) {
  return <div className="empty-state">{text}</div>
}

function Modal({ title, children, onClose }: { title: string; children: React.ReactNode; onClose: () => void }) {
  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <section className="modal" role="dialog" aria-modal="true" aria-label={title} onMouseDown={(event) => event.stopPropagation()}>
        <div className="modal-header">
          <h2>{title}</h2>
          <button className="icon-button" type="button" onClick={onClose} aria-label="Đóng">
            ×
          </button>
        </div>
        {children}
      </section>
    </div>
  )
}

function OrderDetailDrawer({ order, onClose }: { order: Order; onClose: () => void }) {
  return (
    <div className="drawer-backdrop" role="presentation" onMouseDown={onClose}>
      <aside className="drawer" role="dialog" aria-modal="true" aria-label="Chi tiết đơn hàng" onMouseDown={(event) => event.stopPropagation()}>
        <div className="modal-header">
          <h2>Đơn #{order.id}</h2>
          <button className="icon-button" type="button" onClick={onClose} aria-label="Đóng">
            ×
          </button>
        </div>
        <dl className="details-list">
          <dt>Khách hàng</dt>
          <dd>{order.customer_name}</dd>
          <dt>SĐT</dt>
          <dd>{order.customer_phone}</dd>
          <dt>Địa chỉ</dt>
          <dd>{order.address}</dd>
          <dt>Sản phẩm</dt>
          <dd>{parseProductDetails(order.product_details)}</dd>
          <dt>Chi tiết gốc</dt>
          <dd>{order.product_details}</dd>
          <dt>Tổng tiền</dt>
          <dd>{formatMoney(order.total_price)}</dd>
          <dt>Nguồn</dt>
          <dd>{sourceLabels[order.source]}</dd>
          <dt>Trạng thái</dt>
          <dd><StatusBadge status={order.status} /></dd>
          <dt>Shipper ID</dt>
          <dd>{order.shipper_id ?? 'Chưa gán'}</dd>
        </dl>
      </aside>
    </div>
  )
}

type FieldProps = {
  label: string
  registration: UseFormRegisterReturn
  error?: string
  type?: string
}

function TextField({ label, registration, error, type = 'text' }: FieldProps) {
  return (
    <label className="field">
      <span>{label}</span>
      <input type={type} {...registration} />
      {error ? <small className="form-error">{error}</small> : null}
    </label>
  )
}

function SimpleField({ label, value, onChange, error, type = 'text', placeholder }: {
  label: string
  value: string
  onChange: (value: string) => void
  error?: string
  type?: string
  placeholder?: string
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <input type={type} value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} />
      {error ? <small className="form-error">{error}</small> : null}
    </label>
  )
}

function TextArea({ label, registration, error }: Omit<FieldProps, 'type'>) {
  return (
    <label className="field wide">
      <span>{label}</span>
      <textarea rows={3} {...registration} />
      {error ? <small className="form-error">{error}</small> : null}
    </label>
  )
}

function SelectBox({ label, value, onChange, users, disabled }: { label: string; value: string; onChange: (value: string) => void; users: User[]; disabled?: boolean }) {
  return (
    <label className="field">
      <span>{label}</span>
      <select value={value} onChange={(event) => onChange(event.target.value)} disabled={disabled}>
        <option value="">Chọn</option>
        {users.map((user) => (
          <option key={user.id} value={user.id}>
            {user.username}
          </option>
        ))}
      </select>
    </label>
  )
}

function buildDashboardStats(orders: Order[], products: RiceProduct[]) {
  return {
    pending: orders.filter((order) => order.status === 'PENDING').length,
    waitingHandover: orders.filter((order) => order.status === 'DELIVERED_WAITING_HANDOVER').length,
    completed: orders.filter((order) => order.status === 'COMPLETED').length,
    revenue: sumOrders(orders.filter((order) => order.status === 'COMPLETED')),
    activeProducts: products.filter((product) => product.active).length,
  }
}

function filterOrders(orders: Order[], search: string) {
  const normalized = search.trim().toLowerCase()
  if (!normalized) return orders
  return orders.filter((order) =>
    [order.customer_name, order.customer_phone, order.address, order.product_details, String(order.id)]
      .filter(Boolean)
      .some((value) => value.toLowerCase().includes(normalized)),
  )
}

function sumOrders(orders: Order[]) {
  return orders.reduce((sum, order) => sum + Number(order.total_price || 0), 0)
}

function toggleId(values: number[], id: number) {
  return values.includes(id) ? values.filter((value) => value !== id) : [...values, id]
}

function formatMoney(value: number | string) {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(Number(value || 0))
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Có lỗi xảy ra'
}

function fieldError(message: unknown) {
  return typeof message === 'string' ? message : undefined
}

function defaultPath(user: User) {
  return user.role === 'SHIPPER' ? '/shipper' : '/admin'
}

function createSessionId() {
  const id = crypto.randomUUID ? crypto.randomUUID() : `web-${Date.now()}-${Math.random().toString(16).slice(2)}`
  localStorage.setItem('rice_store_chat_session_id', id)
  return id
}

function replacePending(messages: ChatMessage[], text: string) {
  const next = [...messages]
  const index = next.findIndex((item) => item.pending)
  const message: ChatMessage = { id: crypto.randomUUID(), role: 'bot', text }
  if (index >= 0) {
    next[index] = message
    return next
  }
  return [...next, message]
}

export default App
