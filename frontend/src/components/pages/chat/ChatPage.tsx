import { useState, useRef, useEffect } from 'react'
import { Bot, Send } from 'lucide-react'
import { useMutation } from '@tanstack/react-query'
import { api } from '../../../api'
import { createSessionId, replacePending, type ChatMessage, errorMessage } from '../../../utils'

export function ChatPage() {
  const sessionKey = 'rice_store_chat_session_id'
  const [sessionId, setSessionId] = useState(() => localStorage.getItem(sessionKey) || createSessionId())
  const [message, setMessage] = useState('')
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: 'welcome',
      role: 'bot',
      text: 'Chào bạn, bạn muốn đặt loại gạo nào? Bạn có thể nhắn luôn số kg, địa chỉ giao hàng và SĐT để mình ghi đơn nhanh hơn nhé.',
    },
  ])
  const mutation = useMutation({
    mutationFn: api.sendChatMessage,
    onSuccess: (data) => {
      setSessionId(data.session_id)
      localStorage.setItem(sessionKey, data.session_id)
      setMessages((current) =>
        replacePending(
          current,
          data.reply || 'Mình chưa xử lí được tin nhắn này, bạn nhắn lại giúp mình nhé.'
        )
      )
    },
    onError: () => {
      setMessages((current) =>
        replacePending(
          current,
          'Hiện tại hệ thống đang chập chờn. Bạn thử nhắn lại hoặc gọi 0342504323 giúp mình nhé.'
        )
      )
    },
  })
  const messagesEndRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [messages])

  function submitChat(text: string) {
    const trimmed = text.trim()
    if (!trimmed || mutation.isPending) return
    setMessage('')
    setMessages((current) => [
      ...current,
      { id: crypto.randomUUID(), role: 'user', text: trimmed },
      { id: `pending-${Date.now()}`, role: 'bot', text: 'Đang trả lời...', pending: true },
    ])
    mutation.mutate({ session_id: sessionId, message: trimmed })
  }

  return (
    <main className="chat-shell">
      <header className="chat-header">
        <div className="chat-brand">
          <div className="chat-logo">
            <Bot size={24} />
          </div>
          <div>
            <h1>Đặt gạo nhanh</h1>
            <p>Cửa hàng gạo gia đình</p>
          </div>
        </div>
      </header>
      <section className="chat-messages" aria-live="polite">
        {messages.map((item) => (
          <div
            key={item.id}
            className={`chat-bubble ${item.role} ${item.pending ? 'pending' : ''}`}
          >
            {item.text}
          </div>
        ))}
        <div ref={messagesEndRef} aria-hidden="true" />
      </section>
      <section className="quick-actions">
        {[
          'Tôi muốn đặt gạo',
          'Tư vấn giúp tôi loại gạo ngon dễ ăn',
          'Cho tôi xem các loại gạo đang bán',
        ].map((item) => (
          <button key={item} className="chip" type="button" onClick={() => submitChat(item)}>
            {item}
          </button>
        ))}
      </section>
      {mutation.error ? (
        <div className="toast-error">{errorMessage(mutation.error)}</div>
      ) : null}
      <form
        className="chat-composer"
        onSubmit={(event) => {
          event.preventDefault()
          submitChat(message)
        }}
      >
        <textarea
          value={message}
          onChange={(event) => setMessage(event.target.value)}
          placeholder="Nhắn loại gạo, số kg, địa chỉ, SĐT..."
          rows={1}
        />
        <button
          className="button primary"
          type="submit"
          disabled={mutation.isPending || !message.trim()}
        >
          <Send size={18} /> Gửi
        </button>
      </form>
    </main>
  )
}
