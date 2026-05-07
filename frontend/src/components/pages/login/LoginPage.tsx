import { UserCircle, Loader2 } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../../../api'
import { TextField } from '../../shared/form/TextField'
import { MutationError } from '../../shared/MutationError'

type LoginForm = { username: string; password: string }

export function LoginPage() {
  const queryClient = useQueryClient()
  const form = useForm<LoginForm>({
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
        <form
          className="form-grid single"
          onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
        >
          <TextField
            label="Tai khoan"
            registration={form.register('username', { required: true })}
          />
          <TextField
            label="Mat khau"
            type="password"
            registration={form.register('password', { required: true })}
          />
          <MutationError error={mutation.error} />
          <button
            className="button primary"
            type="submit"
            disabled={mutation.isPending}
          >
            {mutation.isPending ? (
              <Loader2 className="spin" size={18} />
            ) : (
              <UserCircle size={18} />
            )}{' '}
            Dang nhap
          </button>
        </form>
      </section>
    </main>
  )
}
