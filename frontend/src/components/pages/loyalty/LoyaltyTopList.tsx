import { Gift } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../../api'
import { PanelTitle } from '../../shared/PanelTitle'
import { DataState } from '../../shared/DataState'
import { EmptyState } from '../../shared/EmptyState'

export function LoyaltyTopList({ onSelect }: { onSelect: (phone: string) => void }) {
  const allQuery = useQuery({ queryKey: ['loyalty-all'], queryFn: () => api.loyaltyAll() })
  const all = allQuery.data ?? []
  return (
    <>
      <PanelTitle title="Danh sách tích điểm" icon={<Gift size={18} />} />
      <DataState isLoading={allQuery.isLoading} error={allQuery.error} />
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>SĐT</th>
              <th>Điểm</th>
              <th>Lần mua</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {all.map((item) => (
              <tr key={item.id}>
                <td><strong>{item.phone}</strong></td>
                <td>{Number(item.total_points).toFixed(0)}</td>
                <td>{item.purchase_count}</td>
                <td>
                  <button
                    className="button ghost"
                    type="button"
                    onClick={() => onSelect(item.phone)}
                  >
                    Chi tiết
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {all.length === 0 && !allQuery.isLoading ? (
        <EmptyState text="Chưa có khách tích điểm." />
      ) : null}
    </>
  )
}
