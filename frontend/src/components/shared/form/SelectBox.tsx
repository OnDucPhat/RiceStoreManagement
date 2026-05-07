import { type User } from '../../../api'

export function SelectBox({
  label,
  value,
  onChange,
  users,
  disabled,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  users: User[]
  disabled?: boolean
}) {
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
