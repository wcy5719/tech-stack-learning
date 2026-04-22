# AI 智能客服系统 - 前端

## 技术栈

- **React 18** - UI框架
- **Vite 5** - 构建工具
- **Axios** - HTTP客户端
- **原生 WebSocket** - 实时通信

## 功能特性

### 💬 智能客服
- HTTP 和 WebSocket 双模式支持
- 实时对话界面
- 快捷问题按钮
- 意图识别显示
- 响应时间监控
- 消息加载动画

### 📊 系统监控
- 实时健康检查
- 自动刷新机制
- 系统组件状态
- 架构信息展示

### ⚙️ 管理面板
- 缓存统计查看
- 一键清空缓存
- IP 地址查询
- 黑名单管理

## 快速开始

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build
```

## 连接配置

开发服务器已配置代理，自动转发请求到后端：
- `/api` → `http://localhost:8080`
- `/ws` → `ws://localhost:8080`
- `/admin` → `http://localhost:8080`
- `/actuator` → `http://localhost:8080`

## 系统要求

- Node.js 18+
- 后端服务运行在 localhost:8080
