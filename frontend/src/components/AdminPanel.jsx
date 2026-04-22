import { useState, useEffect } from 'react'
import axios from 'axios'
import './AdminPanel.css'

const AdminPanel = () => {
  const [activeTab, setActiveTab] = useState('cache')
  const [cacheStats, setCacheStats] = useState(null)
  const [ipStatus, setIpStatus] = useState({ ip: '', inBlacklist: false, inWhitelist: false, requestCount: 0 })
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState({ type: '', text: '' })

  useEffect(() => {
    if (activeTab === 'cache') {
      loadCacheStats()
    }
  }, [activeTab])

  const loadCacheStats = async () => {
    try {
      const response = await axios.get('/admin/cache/stats')
      setCacheStats(response.data)
    } catch (error) {
      showMessage('error', '加载缓存统计失败')
    }
  }

  const clearCache = async () => {
    setLoading(true)
    try {
      await axios.post('/admin/cache/clear')
      showMessage('success', '缓存已清空')
      loadCacheStats()
    } catch (error) {
      showMessage('error', '清空缓存失败')
    }
    setLoading(false)
  }

  const checkIp = async () => {
    if (!ipStatus.ip) {
      showMessage('warning', '请输入IP地址')
      return
    }
    try {
      const [blackRes, countRes] = await Promise.all([
        axios.get('/admin/ip/blacklist', { params: { ip: ipStatus.ip } }),
        axios.get('/admin/ip/count', { params: { ip: ipStatus.ip } }).catch(() => ({ data: 0 }))
      ])
      setIpStatus(prev => ({
        ...prev,
        inBlacklist: blackRes.data.includes('在黑名单中'),
        inWhitelist: false,
        requestCount: countRes.data
      }))
    } catch (error) {
      showMessage('error', '查询IP状态失败')
    }
  }

  const addToBlacklist = async () => {
    if (!ipStatus.ip) {
      showMessage('warning', '请输入IP地址')
      return
    }
    try {
      await axios.post('/admin/ip/blacklist/add', null, { params: { ip: ipStatus.ip } })
      showMessage('success', `IP ${ipStatus.ip} 已加入黑名单`)
      checkIp()
    } catch (error) {
      showMessage('error', '添加黑名单失败')
    }
  }

  const removeFromBlacklist = async () => {
    if (!ipStatus.ip) {
      showMessage('warning', '请输入IP地址')
      return
    }
    try {
      await axios.delete('/admin/ip/blacklist/remove', { params: { ip: ipStatus.ip } })
      showMessage('success', `IP ${ipStatus.ip} 已从黑名单移除`)
      checkIp()
    } catch (error) {
      showMessage('error', '移除黑名单失败')
    }
  }

  const showMessage = (type, text) => {
    setMessage({ type, text })
    setTimeout(() => setMessage({ type: '', text: '' }), 3000)
  }

  return (
    <div className="admin-container">
      <div className="admin-header">
        <h2>⚙️ 系统管理面板</h2>
        <p>管理缓存、IP黑白名单和系统配置</p>
      </div>

      {message.text && (
        <div className={`message-box ${message.type}`}>
          {message.type === 'success' && '✅'}
          {message.type === 'error' && '❌'}
          {message.type === 'warning' && '⚠️'} {message.text}
        </div>
      )}

      <div className="admin-tabs">
        <button
          className={`tab ${activeTab === 'cache' ? 'active' : ''}`}
          onClick={() => setActiveTab('cache')}
        >
          💾 缓存管理
        </button>
        <button
          className={`tab ${activeTab === 'ip' ? 'active' : ''}`}
          onClick={() => setActiveTab('ip')}
        >
          🌐 IP管理
        </button>
      </div>

      {activeTab === 'cache' && (
        <div className="admin-section">
          <h3>缓存统计</h3>
          <div className="stats-grid">
            <div className="stat-card">
              <div className="stat-label">本地缓存命中率</div>
              <div className="stat-value">
                {cacheStats?.localCacheStats?.hitRate || '0%'}
              </div>
            </div>
            <div className="stat-card">
              <div className="stat-label">缓存状态</div>
              <div className="stat-value active">运行中</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">布隆过滤器</div>
              <div className="stat-value active">{cacheStats?.bloomFilterStatus || 'Active'}</div>
            </div>
          </div>
          <button className="action-btn danger" onClick={clearCache} disabled={loading}>
            {loading ? '清空中...' : '🗑️ 清空所有缓存'}
          </button>
        </div>
      )}

      {activeTab === 'ip' && (
        <div className="admin-section">
          <h3>IP 地址管理</h3>
          <div className="ip-controls">
            <input
              type="text"
              value={ipStatus.ip}
              onChange={(e) => setIpStatus(prev => ({ ...prev, ip: e.target.value }))}
              placeholder="输入IP地址 (如: 192.168.1.100)"
            />
            <button onClick={checkIp}>🔍 查询状态</button>
            <button className="btn-danger" onClick={addToBlacklist}>
              🚫 加入黑名单
            </button>
            <button className="btn-success" onClick={removeFromBlacklist}>
              ✅ 移出黑名单
            </button>
          </div>

          {ipStatus.ip && (
            <div className="ip-status">
              <h4>IP: {ipStatus.ip}</h4>
              <div className="status-grid">
                <div className="status-item">
                  <span>黑名单状态:</span>
                  <span className={ipStatus.inBlacklist ? 'status-bad' : 'status-good'}>
                    {ipStatus.inBlacklist ? '🚫 在黑名单中' : '✅ 不在黑名单'}
                  </span>
                </div>
                <div className="status-item">
                  <span>请求次数:</span>
                  <span className="status-value">{ipStatus.requestCount}</span>
                </div>
              </div>
            </div>
          )}

          <div className="ip-presets">
            <h4>快速操作</h4>
            <div className="preset-buttons">
              <button onClick={() => { setIpStatus(prev => ({ ...prev, ip: '192.168.1.100' })); addToBlacklist(); }}>
                屏蔽恶意IP
              </button>
              <button onClick={() => { setIpStatus(prev => ({ ...prev, ip: '127.0.0.1' })); removeFromBlacklist(); }}>
                放行本地IP
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default AdminPanel
