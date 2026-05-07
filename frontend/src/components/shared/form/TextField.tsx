import { type UseFormRegisterReturn } from 'react-hook-form'

type FieldProps = {
  label: string
  registration: UseFormRegisterReturn
  error?: string
  type?: string
}

export function TextField({ label, registration, error, type = 'text' }: FieldProps) {
  return (
    <label className="field">
      <span>{label}</span>
      <input type={type} {...registration} />
      {error ? <small className="form-error">{error}</small> : null}
    </label>
  )
}
