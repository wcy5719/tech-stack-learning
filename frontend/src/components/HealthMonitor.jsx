import { useState, useEffect } from 'react'
import axios from 'axios'
import './HealthMonitor.css'

const HealthMonitor = () => {
  const [health, setHealth] = useState(null)
  const [loading, setLoading] = useState(true)
  const [autoRefresh, setAutoRefresh] = useState(true)
  const [refreshInterval, setRefreshInterval] = useState(5000)

  useEffect(() => {
    loadHealth()
    if (autoRefresh) {
      const interval = setInterval(loadHealth, refreshInterval)
      return () => clearInterval(interval)
    }
  }, [autoRefresh, refreshInterval])

  const loadHealth = async () => {
    try {
      const response = await axios.get('/actuator/health')
      setHealth(response.data)
      setLoading(false)
    } catch (error) {
      console.error('Failed to load health:', error)
      setLoading(false)
    }
  }

  const getStatusIcon = (status) => {
    switch (status) {
      case 'UP': return '✅'
      case 'DOWN': return '❌'
      case 'UNKNOWN': return '❓'
      default: return '⚠️'
    }
  }

  const getStatusColor = (status) => {
    switch (status) {
      case 'UP': return '#10b981'
      case 'DOWN': return '#ef4444'
      case 'UNKNOWN': return '#f59e0b'
      default: return '#9ca3af'
    }
  }

  return (
    <div className="monitor-container">
      <div className="monitor-header">
        <div>
          <h2>📊 系统监控面板</h2>
          <p>实时监控系统健康状态和性能指标</p>
        </div>
        <div className="monitor-controls">
          <label className="toggle-switch">
            <input
              type="checkbox"
              checked={autoRefresh}
              onChange={(e) => setAutoRefresh(e.target.checked)}
            />
            <span className="slider"></span>
            <span className="label-text">自动刷新</span>
          </label>
          <select
            value={refreshInterval}
            onChange={(e) => setRefreshInterval(Number(e.target.value))}
          >
            <option value={3000}>3秒</option>
            <option value={5000}>5秒</option>
            <option value={10000}>10秒</option>
            <option value={30000}>30秒</option>
          </select>
          <button onClick={loadHealth} className="refresh-btn">
            🔄 立即刷新
          </button>
        </div>
      </div>

      {loading ? (
        <div className="loading-state">
          <div className="spinner"></div>
          <p>加载系统状态...</p>
        </div>
      ) : (
        <>
          <div className={`overall-status ${health?.status}`}>
            <div className="status-badge">
              <span className="status-icon">{getStatusIcon(health?.status)}</span>
              <h3>系统状态: {health?.status || 'UNKNOWN'}</h3>
            </div>
          </div>

          <div className="health-grid">
            <div className="health-card">
              <h3>🎯 服务组件</h3>
              {health?.components ? (
                <div className="component-list">
                  {Object.entries(health.components).map(([key, value]) => (
                    <div key={key} className="component-item">
                      <div className="component-header">
                        <span className="component-name">{key}</span>
                        <span
                          className="component-status"
                          style={{ color: getStatusColor(value.status) }}
                        >
                          {getStatusIcon(value.status)} {value.status}
                        </span>
                      </div>
                      {value.details && (
                        <div className="component-details">
                          {Object.entries(value.details).map(([dKey, dValue]) => (
                            <div key={dKey} className="detail-row">
                              <span className="detail-label">{dKey}:</span>
                              <span className="detail-value">{dValue}</span>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              ) : (
                <p className="no-data">暂无组件数据</p>
              )}
            </div>

            <div className="health-card">
              <h3>📈 系统指标</h3>
              <div className="metrics-list">
                <div className="metric-item">
                  <span className="metric-label">应用名称</span>
                  <span className="metric-value">
                    {health?.groups?.includes('readiness') ? '就绪' : '运行中'}
                  </span>
                </div>
                <div className="metric-item">
                  <span className="metric-label">最后检查</span>
                  <span className="metric-value">{new Date().toLocaleTimeString()}</span>
                </div>
              </div>
            </div>
          </div>

          <div className="architecture-info">
            <h3>🏗️ 系统架构信息</h3>
            <div className="arch-grid">
              <div className="arch-item">
                <div className="arch-icon">🌐</div>
                <h4>接入层</h4>
                <p>Nginx + 限流 + 黑白名单</p>
              </div>
              <div className="arch-item">
                <div className="arch-icon">🔐</div>
                <h4>网关层</h4>
                <p>JWT认证 + 路由 + 熔断</p>
              </div>
              <div className="arch-item">
                <div className="arch-icon">🤖</div>
                <h4>业务层</h4>
                <p>AI客服 + 二级缓存</p>
              </div>
              <div className="arch-item">
                <div className="arch-icon">⚡</div>
                <h4>推理层</h4>
                <p>vLLM + GPU集群</p>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  )
}

export default HealthMonitor
