import { type User, type UserRole } from '../../api'
import { Navigate } from 'react-router-dom'
import { defaultPath } from '../../utils'

export function ProtectedRoute({
  user,
  roles,
  children,
}: {
  user: User | null
  roles: UserRole[]
  children: React.ReactNode
}) {
  if (!user) {
    return <Navigate to="/login" replace />
  }
  if (!roles.includes(user.role)) {
    return <Navigate to={defaultPath(user)} replace />
  }
  return children
}
