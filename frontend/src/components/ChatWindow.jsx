import { useState, useEffect, useRef } from 'react'
import axios from 'axios'
import './ChatWindow.css'

const ChatWindow = ({ userId, sessionId }) => {
  const [messages, setMessages] = useState([])
  const [inputMessage, setInputMessage] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [connectionMode, setConnectionMode] = useState('http')
  const [wsStatus, setWsStatus] = useState('disconnected')
  const messagesEndRef = useRef(null)
  const wsRef = useRef(null)

  useEffect(() => {
    if (connectionMode === 'websocket') {
      connectWebSocket()
    }
    return () => {
      if (wsRef.current) {
        wsRef.current.close()
      }
    }
  }, [connectionMode])

  useEffect(() => {
    scrollToBottom()
  }, [messages])

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  const connectWebSocket = () => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}/ws/chat`
    
    const ws = new WebSocket(wsUrl)
    wsRef.current = ws

    ws.onopen = () => {
      setWsStatus('connected')
    }

    ws.onmessage = (event) => {
      try {
        const response = JSON.parse(event.data)
        setMessages(prev => [...prev, {
          role: 'assistant',
          content: response.response,
          timestamp: new Date().toLocaleTimeString(),
          intent: response.intent
        }])
      } catch (error) {
        console.error('WebSocket message parse error:', error)
      }
      setIsLoading(false)
    }

    ws.onclose = () => {
      setWsStatus('disconnected')
    }

    ws.onerror = () => {
      setWsStatus('error')
    }
  }

  const sendMessage = async () => {
    if (!inputMessage.trim() || isLoading) return

    const userMessage = {
      role: 'user',
      content: inputMessage,
      timestamp: new Date().toLocaleTimeString()
    }
    setMessages(prev => [...prev, userMessage])
    setIsLoading(true)

    if (connectionMode === 'websocket' && wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({
        userId,
        message: inputMessage,
        sessionId,
        multiTurn: true
      }))
    } else {
      try {
        const response = await axios.post('/api/v1/chat', {
          userId,
          message: inputMessage,
          sessionId,
          multiTurn: true
        })

        setMessages(prev => [...prev, {
          role: 'assistant',
          content: response.data.response,
          timestamp: new Date().toLocaleTimeString(),
          intent: response.data.intent,
          responseTime: response.data.responseTimeMs
        }])
      } catch (error) {
        setMessages(prev => [...prev, {
          role: 'assistant',
          content: '抱歉，处理您的请求时出现错误: ' + (error.response?.data?.message || error.message),
          timestamp: new Date().toLocaleTimeString(),
          isError: true
        }])
      }
    }

    setInputMessage('')
    setIsLoading(false)
  }

  const handleKeyPress = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  const quickQuestions = [
    '如何查询订单？',
    '退款流程是什么？',
    '商品价格多少？',
    '有什么优惠活动？'
  ]

  return (
    <div className="chat-container">
      <div className="chat-header">
        <div className="chat-info">
          <h2>💬 智能客服对话</h2>
          <span className="session-id">会话ID: {sessionId}</span>
        </div>
        <div className="connection-controls">
          <select
            value={connectionMode}
            onChange={(e) => setConnectionMode(e.target.value)}
            className="mode-select"
          >
            <option value="http">HTTP 模式</option>
            <option value="websocket">WebSocket 模式</option>
          </select>
          {connectionMode === 'websocket' && (
            <span className={`ws-status ${wsStatus}`}>
              {wsStatus === 'connected' ? '🟢 已连接' : 
               wsStatus === 'error' ? '🔴 连接错误' : '⚪ 未连接'}
            </span>
          )}
        </div>
      </div>

      <div className="messages-container">
        {messages.length === 0 && (
          <div className="welcome-message">
            <div className="welcome-icon">🤖</div>
            <h3>欢迎使用 AI 智能客服！</h3>
            <p>我是您的AI客服助手，可以为您解答各种问题。请选择以下快捷问题或直接输入您的问题：</p>
            <div className="quick-questions">
              {quickQuestions.map((q, i) => (
                <button
                  key={i}
                  className="quick-btn"
                  onClick={() => {
                    setInputMessage(q)
                  }}
                >
                  {q}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((msg, index) => (
          <div
            key={index}
            className={`message ${msg.role} ${msg.isError ? 'error' : ''}`}
          >
            <div className="message-avatar">
              {msg.role === 'user' ? '👤' : '🤖'}
            </div>
            <div className="message-content">
              <div className="message-bubble">
                <p>{msg.content}</p>
              </div>
              <div className="message-meta">
                <span className="message-time">{msg.timestamp}</span>
                {msg.intent && <span className="message-intent">意图: {msg.intent}</span>}
                {msg.responseTime && <span className="message-time">响应: {msg.responseTime}ms</span>}
              </div>
            </div>
          </div>
        ))}

        {isLoading && (
          <div className="message assistant">
            <div className="message-avatar">🤖</div>
            <div className="message-content">
              <div className="message-bubble loading">
                <div className="loading-dots">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
              </div>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      <div className="input-container">
        <textarea
          value={inputMessage}
          onChange={(e) => setInputMessage(e.target.value)}
          onKeyPress={handleKeyPress}
          placeholder="输入您的问题... (按Enter发送，Shift+Enter换行)"
          rows={2}
          disabled={isLoading}
        />
        <button
          onClick={sendMessage}
          disabled={isLoading || !inputMessage.trim()}
          className="send-btn"
        >
          发送 📤
        </button>
      </div>
    </div>
  )
}

export default ChatWindow
