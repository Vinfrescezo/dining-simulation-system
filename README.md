食堂就餐仿真系统 - 源代码技术文档1. 项目概述本系统是一个基于 Spring Boot 框架开发的离散事件仿真系统，旨在模拟大学食堂（如 BJTU 食堂）在高峰时段的学生流转、排队打饭、找座就餐及流失情况。系统采用“前端实时渲染 + 后端逻辑计算”的架构，通过 WebSocket 实现数据的实时推送。2. 目录结构与类说明

📂 config (核心配置)负责系统的全局规则设定与通信配置。SimulationConfig.java: 全局配置中心。存储了诸如门口坐标、移动速度、排队上限、耐心阈值等静态参数。WebSocketConfig.java: 配置 WebSocket 终点，确保后端计算的每一帧数据（坐标、状态）能实时同步到前端界面。

📂 controller (接口层)系统对外暴露的“水龙头”。SimulationController.java: 接收前端发送的控制指令，如“开始仿真”、“重置系统”等，是前后端交互的唯一入口。

📂 dto (数据传输对象)定义了前后端通信的“协议格式”，起到解耦和安全防护作用。StartConfigDTO.java: 接收前端配置参数（如总人数、窗口数）。StartResponseDTO.java: 后端启动成功的反馈。SimulationReportDTO.java: 仿真结束后的统计报告数据（流失率、平均等待时间等）。

📂 engine (物理逻辑引擎)负责仿真世界中的“数学与物理”逻辑，不涉及业务状态变化。TrafficEngine.java: 人流发生器。基于双峰分布曲线计算每一时刻应产生的新生数量，并执行排队人数上限拦截（流失逻辑）。MovementEngine.java: 位移计算器。负责学生在地图上的平滑移动计算（基于 $x, y$ 坐标的向量移动）。WaitlistEngine.java: 候补管理。处理当没有座位时，端着盘子的学生在候餐区的队列管理。

📂 machine (状态机)负责仿真世界中的“智能体大脑”。StudentStateMachine.java: 学生行为状态机。这是系统最核心的逻辑，定义了学生从 入场 -> 寻路 -> 排队 -> 点餐 -> 等座 -> 吃饭 -> 离场 的所有状态切换逻辑以及耐心耗尽的流失逻辑。

📂 model (实体模型)仿真世界的“基础物质”。Student.java: 定义学生属性（坐标、状态、ID、耐心值、剩余时间等）。Window.java: 定义打饭窗口属性（坐标、队列情况、当前服务对象）。Seat.java: 定义座位属性（坐标、占用状态、所属学生 ID）。

📂 service (业务编排层)负责协调各个引擎和状态机工作。SimulationService.java: 仿真管家。包含主循环逻辑（Tick），驱动所有 Engine 和 StateMachine 按时间步进工作。ResourceManager.java: 资源管理器。负责窗口初始化、座位布局生成以及“最短队列”窗口的实时搜索。3. 核心逻辑流程图系统运行遵循以下时钟步进（Tick）循环：数据注入：Controller 接收 StartConfigDTO 参数。人流控制：TrafficEngine 根据时间曲线决定是否生成学生。行为驱动：StudentStateMachine 检查每个学生，如果排队太久则设为 LOST。物理位移：MovementEngine 根据学生当前状态计算下一帧坐标。数据同步：SimulationService 将当前所有实体状态打包，通过 WebSocket 推送。4. 技术亮点解耦设计：物理移动（Engine）与行为决策（Machine）分离。高可配置性：通过 SimulationConfig 实现了不改代码即可调整仿真参数。健壮性：通过 DTO 确保了非法输入不会干扰底层仿真逻辑。