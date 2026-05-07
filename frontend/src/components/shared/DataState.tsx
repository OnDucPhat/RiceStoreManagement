import { Loader2 } from 'lucide-react'
import { errorMessage } from '../../utils'

export function DataState({ isLoading, error }: { isLoading?: boolean; error?: unknown }) {
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
        <Loader2 size={18} /> {errorMessage(error)}
      </div>
    )
  }
  return null
}
