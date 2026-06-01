# 北京交通大学就餐仿真系统前端

基于 Vue 3 + Vite + Axios + HTML5 Canvas + ECharts 实现。页面按规格说明书拆成三个核心界面：

- 界面 A：参数配置页
- 界面 B：实时仿真监控页
- 界面 C：结果分析报告页

已内置北京交通大学/计算机科学与技术学院蓝色校徽横幅，用作顶部品牌区与首页水印。

## 运行方式

```bash
npm install
npm run dev
```

浏览器打开：

```bash
http://localhost:5173
```

## 后端联调

复制环境变量示例：

```bash
cp .env.example .env
```

按实际 Spring Boot 服务修改：

```bash
VITE_API_BASE_URL=
VITE_WS_URL=ws://localhost:8080/ws/simulation
VITE_ENABLE_LOCAL_FALLBACK=true
```

开发环境中，`/api` 默认由 Vite 代理到 `http://localhost:8080`。前端点击“开始仿真”时会请求：

```bash
POST /api/simulation/start
```

请求体：

```json
{
  "studentCount": 1500,
  "windowCount": 10,
  "simDurationTick": 3600
}
```

WebSocket 期望接收精简快照：

```json
{
  "tick": 125,
  "students": [
    { "id": 1, "x": 120, "y": 45, "s": "PATHFINDING" }
  ],
  "windows": [
    { "id": "W1", "qLen": 12 }
  ]
}
```

如果后端暂时没写完，首页运行模式选“只本地演示”即可直接展示完整流程。

## 项目结构

```text
bjtu-dining-sim-frontend/
├─ package.json
├─ vite.config.js
├─ index.html
├─ .env.example
├─ README.md
└─ src/
   ├─ main.js
   ├─ App.vue
   ├─ assets/
   │  └─ cs_logo_2025_blue.png
   ├─ components/
   │  ├─ BrandHeader.vue
   │  ├─ CanvasLegend.vue
   │  └─ MetricCard.vue
   ├─ composables/
   │  └─ useCanvasRenderer.js
   ├─ services/
   │  ├─ api.js
   │  └─ localSimulator.js
   ├─ styles/
   │  └─ global.css
   ├─ utils/
   │  ├─ constants.js
   │  ├─ download.js
   │  ├─ layout.js
   │  └─ validators.js
   └─ views/
      ├─ ConfigPage.vue
      ├─ MonitorPage.vue
      └─ ReportPage.vue
```

## 模块说明

### views/ConfigPage.vue
参数配置首页。负责输入预计人数、窗口数量、模拟 Tick、座位数、渲染阈值等参数，并完成合法性校验。

### views/MonitorPage.vue
实时监控页。负责接收 WebSocket 或本地模拟器快照，并将数据交给 Canvas 渲染层。

### views/ReportPage.vue
结果分析页。负责 ECharts 折线图、柱状图、关键指标卡片、建议文案、CSV/JSON 导出。

### composables/useCanvasRenderer.js
Canvas 绘图引擎。绘制食堂平面、窗口队列、座位区、端盘等座区、学生状态点/小人。

### services/api.js
后端联调层。封装 HTTP 初始化接口和 WebSocket 连接。

### services/localSimulator.js
无后端演示用的本地仿真器。实现学生生成、最短队列选择、队列满载流失、打饭、等座、就餐、离开等简化流程。
