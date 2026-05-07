import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2, Plus } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../../../api'
import { Modal } from '../../shared/Modal'
import { TextField } from '../../shared/form/TextField'
import { TextArea } from '../../shared/form/TextArea'
import { MutationError } from '../../shared/MutationError'
import { fieldError } from '../../../utils'

const orderSchema = z.object({
  customer_name: z.string().min(1, 'Nhập tên khách'),
  customer_phone: z.string().min(1, 'Nhập SĐT'),
  address: z.string().min(1, 'Nhập địa chỉ'),
  product_details: z.string().min(1, 'Nhập sản phẩm'),
  total_price: z.coerce.number().min(0, 'Tổng tiền không âm'),
})

type OrderFormInput = z.input<typeof orderSchema>
type OrderFormValues = z.output<typeof orderSchema>

export function CreateOrderModal({ onClose }: { onClose: () => void }) {
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
      <form
        className="form-grid"
        onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
      >
        <TextField
          label="Tên khách"
          registration={form.register('customer_name')}
          error={fieldError(form.formState.errors.customer_name?.message)}
        />
        <TextField
          label="SĐT"
          registration={form.register('customer_phone')}
          error={fieldError(form.formState.errors.customer_phone?.message)}
        />
        <TextField
          label="Địa chỉ"
          registration={form.register('address')}
          error={fieldError(form.formState.errors.address?.message)}
        />
        <TextArea
          label="Sản phẩm"
          registration={form.register('product_details')}
          error={fieldError(form.formState.errors.product_details?.message)}
        />
        <TextField
          label="Tổng tiền"
          type="number"
          registration={form.register('total_price')}
          error={fieldError(form.formState.errors.total_price?.message)}
        />
        <MutationError error={mutation.error} />
        <div className="modal-actions">
          <button className="button ghost" type="button" onClick={onClose}>
            Hủy
          </button>
          <button
            className="button primary"
            type="submit"
            disabled={mutation.isPending}
          >
            {mutation.isPending ? (
              <Loader2 className="spin" size={18} />
            ) : (
              <Plus size={18} />
            )}{' '}
            Lưu đơn
          </button>
        </div>
      </form>
    </Modal>
  )
}
