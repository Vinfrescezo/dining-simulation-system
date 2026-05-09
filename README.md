🏢 BJTU-Dining-Simulation | 北交大智慧食堂数字孪生仿真系统
本系统是一个基于 有限状态机 (FSM) 驱动的食堂就餐流数字孪生仿真平台。系统通过后端 Spring Boot 驱动复杂的学生行为逻辑，并利用 Vue 3 + Canvas API 实现高频实时监控看板。

🌟 项目亮点
精细化状态机控制：严格遵循学生行为生命周期逻辑，涵盖 PATHFINDING（寻路）、QUEUEING（排队）、ORDERING（打饭）、WAITING（等座）、EATING（就餐）及 LEAVING（离开）全流程。

职责分离架构：后端采用解耦设计，将物理移动引擎（MovementEngine）、资源调度（ResourceManager）与仿真服务（SimulationService）独立，具备极高的可维护性。

工业级看板设计：前端采用现代 UI 设计语言，支持实时 Tick 统计、窗口负载可视化、座位占用热力图以及动态系统日志流。

实时数据同步：基于定时任务（Scheduled Tasks）与 RESTful 轮询机制，确保前后端数据每秒高频同步。

🛠️ 技术栈
后端 (Backend)
核心框架：Java 17 / Spring Boot 3.x

辅助工具：Lombok (简化代码)

调度引擎：Spring Scheduled (Tick 驱动)

数据结构：CopyOnWriteArrayList (确保高并发场景下的数据一致性)

前端 (Frontend)
框架：Vue 3 (Composition API)

构建工具：Vite

图形渲染：HTML5 Canvas API

样式处理：CSS3 Flexbox / Grid & Scoped CSS

📂 项目结构
后端目录结构
Plaintext
src/main/java/com/bjtu/dining_simulation/
├── config/        # 仿真参数配置 (步长、概率分布等)
├── control/       # API 控制器 (提供状态查询接口)
├── logic/         # 核心物理引擎 (坐标插值计算)
├── model/         # 数据实体 (学生、窗口、座位)
└── service/       # 业务逻辑层 (状态机驱动与资源管理)
前端目录结构
Plaintext
src/
├── components/    # 核心组件 (DiningDashboard.vue)
├── App.vue        # 入口页面
└── main.js        # Vue 配置
🚀 快速开始
1. 启动后端 (Spring Boot)
确保已安装 JDK 17+ 和 Maven。

在 VS Code 中打开后端目录。

运行 DiningSimulationApplication.java。

默认 API 地址：http://localhost:8080/api/status。

2. 启动前端 (Vue 3)
进入前端目录：cd frontend。

安装依赖：npm install。

启动开发服务器：npm run dev。

在浏览器访问：http://localhost:5173。

🧠 核心逻辑：有限状态机 (FSM)
系统内部运行着一套严密的逻辑流：

判定流失：若所有窗口排队人数超过 maxCapacity，新生实体直接销毁。

动态寻路：根据分配的窗口坐标，利用 MovementEngine 进行平滑坐标插值。

等座唤醒：若打饭结束无余位，学生进入 WAITING_FOR_SEAT 状态，由座位释放信号实时唤醒。

资源回收：学生离开出口后，系统自动清理内存引用，实现完整的仿真生命周期管理。

📸 系统预览
(此处建议放上你运行时的屏幕截图，会非常亮眼！)

🤝 贡献与反馈
本系统为北交大计算机实训项目。如有任何改进建议，欢迎提交 Pull Request 或通过 VS Code 调试控制台反馈。
