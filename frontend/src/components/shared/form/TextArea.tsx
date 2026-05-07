import { type UseFormRegisterReturn } from 'react-hook-form'

type FieldProps = {
  label: string
  registration: UseFormRegisterReturn
  error?: string
}

export function TextArea({ label, registration, error }: Omit<FieldProps, 'type'>) {
  return (
    <label className="field wide">
      <span>{label}</span>
      <textarea rows={3} {...registration} />
      {error ? <small className="form-error">{error}</small> : null}
    </label>
  )
}
