import { errorMessage } from '../../utils'

export function MutationError({ error }: { error?: unknown }) {
  if (!error) return null
  return <div className="form-error wide">{errorMessage(error)}</div>
}
