import { useState, useEffect } from 'react'
import ChatWindow from './components/ChatWindow'
import AdminPanel from './components/AdminPanel'
import HealthMonitor from './components/HealthMonitor'
import './App.css'

function App() {
  const [currentView, setCurrentView] = useState('chat')
  const [userId, setUserId] = useState('')
  const [sessionId, setSessionId] = useState('session_' + Date.now())

  useEffect(() => {
    const savedUser = localStorage.getItem('ai_user_id')
    if (savedUser) {
      setUserId(savedUser)
    }
  }, [])

  const handleLogin = (id) => {
    setUserId(id)
    localStorage.setItem('ai_user_id', id)
  }

  return (
    <div className="app-container">
      <header className="app-header">
        <div className="header-content">
          <div className="logo">
            <span className="logo-icon">🤖</span>
            <h1>AI 智能客服系统</h1>
          </div>
          <nav className="nav-tabs">
            <button
              className={`nav-tab ${currentView === 'chat' ? 'active' : ''}`}
              onClick={() => setCurrentView('chat')}
            >
              💬 智能客服
            </button>
            <button
              className={`nav-tab ${currentView === 'monitor' ? 'active' : ''}`}
              onClick={() => setCurrentView('monitor')}
            >
              📊 系统监控
            </button>
            <button
              className={`nav-tab ${currentView === 'admin' ? 'active' : ''}`}
              onClick={() => setCurrentView('admin')}
            >
              ⚙️ 管理面板
            </button>
          </nav>
        </div>
        {userId && (
          <div className="user-info">
            <span>用户: {userId}</span>
            <button onClick={() => { setUserId(''); localStorage.removeItem('ai_user_id'); }}>
              退出
            </button>
          </div>
        )}
      </header>

      <main className="app-main">
        {!userId ? (
          <div className="login-container">
            <div className="login-card">
              <div className="login-icon">👤</div>
              <h2>欢迎使用 AI 智能客服</h2>
              <p>请输入您的用户ID开始对话</p>
              <form onSubmit={(e) => {
                e.preventDefault()
                const form = e.target
                const id = form.elements['userId'].value
                if (id.trim()) handleLogin(id.trim())
              }}>
                <input
                  type="text"
                  name="userId"
                  placeholder="请输入用户ID"
                  required
                  autoFocus
                />
                <button type="submit" className="login-btn">进入系统</button>
              </form>
            </div>
          </div>
        ) : (
          <>
            {currentView === 'chat' && <ChatWindow userId={userId} sessionId={sessionId} />}
            {currentView === 'monitor' && <HealthMonitor />}
            {currentView === 'admin' && <AdminPanel />}
          </>
        )}
      </main>

      <footer className="app-footer">
        <p>© 2026 AI 智能客服系统 | Spring Boot 3.5.11 | Spring AI 2.0.0-M4</p>
      </footer>
    </div>
  )
}

export default App
