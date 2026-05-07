import { Phone, Search, Gift, Loader2 } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../../api'
import { PageHeader } from '../../shared/PageHeader'
import { PanelTitle } from '../../shared/PanelTitle'
import { DataState } from '../../shared/DataState'
import { MutationError } from '../../shared/MutationError'
import { LoyaltyTopList } from './LoyaltyTopList'
import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'

export function LoyaltyPage() {
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
      <PageHeader
        title="Tích điểm"
        subtitle="Tra cứu điểm tích lũy theo số điện thoại. Điểm = tổng kg gạo đã mua."
      />
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
                  <strong className="loyalty-points">
                    {Number(loyalty.total_points).toFixed(0)} điểm
                  </strong>
                  <small>(~ {Number(loyalty.total_points).toFixed(0)} kg gạo)</small>
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
                    <button
                      className="button ghost"
                      type="button"
                      onClick={() => setConfirmGiftPhone(null)}
                    >
                      Hủy
                    </button>
                    <button
                      className="button primary"
                      type="button"
                      disabled={giftMutation.isPending}
                      onClick={() => giftMutation.mutate(loyalty.phone)}
                    >
                      {giftMutation.isPending ? (
                        <Loader2 className="spin" size={18} />
                      ) : (
                        <Gift size={18} />
                      )}{' '}
                      Xác nhận tặng quà
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
            <div className="empty-state">Không tìm thấy SĐT {lookedUpPhone}.</div>
          )}
        </section>
        <section className="panel">
          <LoyaltyTopList
            onSelect={(phone) => {
              setSearchPhone(phone)
              setLookedUpPhone(phone)
            }}
          />
        </section>
      </div>
    </section>
  )
}
