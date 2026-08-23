# Sable 子层级物理精度丢失：分析与修复方案规划

> 状态：分析完成（源码/二进制/日志三层核实，sable 2.0.5），修复未实施
> 日期：2026-08（本次复核）
> 范围：只做分析与方案规划，不含代码

---

## 1. 问题定性

sable 子层级（sublevel）的方块内容存放在**父世界的"plotyard"（地块场）区域**，该区域刻意远离世界原点。
子层级所有物理对象（刚体、碰撞体、接触点）在该坐标域下进入 Rapier 原生引擎，而引擎内部使用 **f32** 存储位置。

当坐标量级达到 500 万 ~ 2048 万块时，f32 的量化步长（ULP）达到 0.5 ~ 2 块：

| 距世界原点距离 | f32 ULP（块） | 可分辨的最小位移 | 后果 |
|---|---|---|---|
| ≤ 2²⁴ = 16,777,216 | ≤ 1 | ≥ 1 块/tick（≥20 m/s） | 低速运动被量子化 |
| 5,000,000（本项目小行星带内环） | 0.5 | ≥ 0.5 块/tick（≥10 m/s） | 低速漂移、步进抖动 |
| 10,000,000（本项目小行星带外环） | 1 | ≥ 1 块/tick | 明显抖动 |
| 20,480,000（plotyard 默认原点） | 2 | 奇整数坐标不可表示 | 碰撞/接触/约束灾难性误差 |

> 1 块 = 1 m；MC 刻速 20 tick/s，速度 v 块/tick = 20v m/s。
> f32 位置更新 `pos += v·dt`：当 `v/20 < ULP` 时位移被完全吞掉。
> plotyard 20,480,000 处 ULP=2 块 → **低于 40 m/s 的运动完全不可表示**；500 万块处 ULP=0.5 块 → 低于 10 m/s 的运动被吞。

**本项目特殊性**：玩家活动区固定在小行星带圆环（半径 500 万 ~ 1000 万块，见 `PlayerSpawnPoint.INNER_RADIUS/OUTER_RADIUS`），
实测登录日志确认玩家出生在 (4,999,722, 143, 62)。也就是说**本项目所有子层级物理都运行在 f32 危险区**，
即使忽略 plotyard 的 2048 万块，刚体本身的 500 万 ~ 1000 万块坐标也足以导致精度严重下降。

---

## 2. 证据链

### 2.1 plotyard 坐标域（Java 源码，sable 2.0.5）

- `SubLevelContainer.DEFAULT_ORIGIN = 10000`（plot 坐标），`DEFAULT_LOG_PLOT_SIZE = 7`
- plot = 2⁷ = 128 chunks = **2048 块**；plotyard 块坐标原点 = 10000 × 2048 = **20,480,000 块**
  （源码注释本意是"30 million blocks out"，`Mth.ceil(30_000_000.0 / 2048)` 被注释掉，实际用 10000 plot = 20.48M 块）
- 子层级全部方块存于 plotyard 的 chunk（`LevelPlot` 直接以全局 chunk 坐标存 `PlotChunkHolder`）；
  站在子层级上的实体其世界坐标就是 plotyard 坐标（2048 万量级）
- 容器构造：`ServerLevelMixin`/`ClientLevelMixin` 用 `DEFAULT_ORIGIN, DEFAULT_ORIGIN` 建容器

### 2.2 物理上传无任何重基（Java 源码）

`RapierPhysicsPipeline`（rapier-src 与 neoforge-src 同源）：
- `handleChunkSectionAddition()`：把 plotyard 的**全局 chunk 坐标**（~1,280,000 chunk 坐标，块坐标 2048 万）
  直接传给原生 `addChunk(scene, x, y, z, ...)` —— **无原点平移**
- `onStatsChanged()`：`setLocalBounds` 用 plot 全局 bounds；`setCenterOfMass` 传全局 centerOfMass
- `add(ServerSubLevel, pose)`：刚体 pose 用 `logicalPose()`（父世界坐标，玩家附近）
- Java 侧把"父世界坐标"（刚体 pose）与"plotyard 坐标"（collider chunk）混在同一原生场景，坐标域无归一化

### 2.3 原生引擎是 f32（二进制核实）

`run/.sable/natives/sable_rapier_x86_64_windows.dll`（1,658,368 字节）：
- 可读字符串仅含标准 `rapier3d` crate 名，**无 `rapier3d-f64`** → `Real = f32`
- 无 `rebase` / `origin` / `plotyard` 相关字符串 → **原生侧也未做坐标重基**
- （"f64" 命中仅为 Rust 原语类型名 `f32f64`，非 f64 变体）

### 2.4 项目现状：重基配置是"空壳"

- `SpaceSimulationConfig` 已定义 `rapierRebaseEnabled / rapierRebaseOriginX/Y/Z`（"Sable Rapier Fix" 段）
- 但**全仓库无任何消费代码**：mixin 清单（`space_simulation.mixins.json`）无重基条目，git 历史无实现
- `SpaceSimulation.isRapierPresent()` 注释提到"提前加载 Rapier3D 会让 mixin 报告 loaded too early 而跳过重基包装"，
  但该方法无调用方，属遗留死代码 → 结论：**重基修复只停留在配置骨架，从未落地**
- 本项目飞行物理（`FlightPhysics`/`PlayerAcceleration`/`ClientTickHandler`）**完全不感知子层级**：
  src 中除已禁用的 `SubLevelGenerator.txt` 外无任何 sable API 引用

### 2.5 实验记录（run/ 日志）

- `sable203/sable205` 系列：玩家在 500 万块处（小行星带出生点）—— f32 ULP 0.5 块
- `spheretest` 系列：玩家被传送到 15 万 / 45 万 / 50 万 / 100 万 / 500 万 / 1000 万 / 2000 万块
  （`2.00000005E7`）—— 是此前"距离原点不同距离下精度/渲染表现"的系统性实验
- `run/logs/latest.log`：最近一次登录 (-4,378,439, -7, 4,065,702) —— 也处于 400 万+ 量级

---

## 3. 精度丢失的三层分解

### L1 原生物理域（最严重，根因）
rapier f32 直接持有 plotyard 全局坐标（2048 万块，ULP=2 块）与刚体父世界坐标（500~1000 万块，ULP=0.5~1 块）：
- 碰撞检测/接触点/穿透深度全部量子化到 0.5~2 块步长
- `pos += v·dt` 低速位移被吞 → 静止偏移、步进式跳动、低速卡死
- 约束/关节锚点、力矩臂同样量化 → 飞船姿态不稳定
- **这是"物理模拟精度严重下降"的直接来源**

### L2 Java 逻辑域（坐标域错位）
实体/玩家站在子层级上时，其 MC 坐标就是 plotyard 坐标（2048 万）。double 精度本身没问题（ULP≈7e-9），但：
- 一切未经 `Sable.HELPER.projectOutOfSubLevel` / `distanceSquaredWithSubLevels` 的距离/交互逻辑
  （原版 `distanceToSqr`、拾取、AI、声音衰减，以及本项目 `FlightPhysics` 中直接用原始坐标的判定）
  与父世界对象相差 2047.5 万块 → 交互失灵
- 任何 `(float)` 转换路径（部分 `Mth` 调用、HUD、粒子）在 2048 万处直接崩溃
- sable 已对大量原版路径打了 mixin（`ActiveSableCompanion`），但**第三方 mod 与自研代码不在此列**

### L3 渲染域（视觉抖动）
客户端相机/模型矩阵以 float 运算，玩家在 plotyard（2048 万块）时：
- `(float)camX + float 矩阵` 抖动 ±2 块（fancy/Sodium 渲染路径；vanilla 路径 sable 用相对坐标有 mixin 部分缓解）
- 表现为"方块在抖/飞船与方块错位"，是"物理精度下降"最直观的观感来源

---

## 4. 修复方案规划

### 方案 A（首选，项目内落地）：场景级浮点原点重基（rapierfix 完整实现）

**思路**：维护一个按 chunk 对齐的场景原点 O（world 坐标整数），在 **JNI 边界全量平移**：
进引擎的坐标 `-O`，出引擎的坐标 `+O`。Java 侧 pose/渲染/网络保持世界坐标 double 不变。

- 原点取值：首个物理对象位置自动对齐 chunk，或复用现有 `rapierRebaseOriginX/Y/Z` 手动指定
- 重基后引擎内坐标量级 < ~100 万块 → f32 ULP < 0.0625 块（6 cm），恢复可用精度
- **必须全覆盖的 JNI 方法清单**（漏一个即静默错位）：
  `createSubLevel / createBox / addChunk / removeChunk / changeBlock / setCenterOfMass /
  setLocalBounds / teleportObject / getPose(回读+O) / applyForce(位置-COM 差值域) /
  applyForceAndTorque / addLinearAngularVelocities(速度域不变) / createRope / setRopeAttachment /
  addRopePointAtStart / addKinematicContraption / setKinematicContraptionTransform(centerOfMass 平移) /
  addKinematicContraptionChunkSection / constraint 系(localAnchor 域) / clearCollisions(回读点+O)`
- **关键陷阱**：sable Java 侧在同一场景混用两个坐标域——plotyard 坐标（chunk/bounds/COM）与
  父世界坐标（刚体 pose）。重基必须统一为"全部世界坐标 - O"后再入引擎，否则刚体与 collider 的
  相对关系错乱（飞船方块与飞船刚体分离）
- 实现载体：mixin 包装 `Rapier3D`（static native 方法全部是 `public static`，可 `@Inject` 到调用处
  或在 `RapierPhysicsPipeline` 层面拦截更稳妥——因为碰撞回读语义 `localPoint + rotationPoint` 依赖
  COM 也处于引擎内域，两层方案需保证一致）
- **回归验证**：重基开/关对比，在 500 万块与 2000 万块处各建测试飞船：
  - 低速推进（1~5 m/s）位移是否连续（关闭时应完全被吞）
  - `clearCollisions` 回读接触点 + rotationPoint 还原全局坐标是否精确
  - 与父世界地形（global chunk）碰撞是否正常（global chunk 坐标同样 -O）

### 方案 B（过渡/对比实验）：调整 plotyard 原点

- mixin `ServerLevelMixin`/`ClientLevelMixin` 的容器构造，用自定义 origin 替代 `DEFAULT_ORIGIN`
  （如按 500 万块活动区对齐：origin ≈ 5,000,000 / 2048 ≈ 2442）
- 效果：collider 精度从 ULP 2 块 → 0.5 块，仅缓解 4 倍，**治标不治本**（500 万块处仍 <10 m/s 不可表示）
- 风险：plot 靠近活动区会与父世界真实地形 chunk 冲突（plot 需 force-load ticket，与玩家周边地形重叠）
- 定位：A 的前置实验开关 / A 实施前的快速收益

### 方案 C（逻辑层，低成本必做）：子层级感知的距离/坐标

- 本项目 `FlightPhysics`/`PlayerAcceleration`/`ClientTickHandler` 中所有距离/速度/交互判定，
  在子层级存在时改用 `Sable.HELPER.distanceSquaredWithSubLevels` / `projectOutOfSubLevel` /
  `getVelocity`（sable 2.0.5 `ActiveSableCompanion` 已提供全量实现，`Sable.HELPER` 静态入口）
- 用 `Sable.HELPER.getContaining` 探测坐标是否位于子层级内，避免对非子层级路径的开销
- 这是所有"使用子层级的 mod"的标准做法（sable 自带的 Create/Aeronautics 兼容层同款）

### 方案 D（渲染层，按需）：子层级渲染抖动修正

- 检查 fancy/Sodium 渲染路径（`SubLevelMeshBuilder`、`SubLevelRenderDispatcher`、Sodium
  `SubLevelRenderSectionManager`）在 plotyard 坐标下的 float 运算；玩家在 2048 万块处的
  地形/方块渲染抖动需客户端 mixin 修正（以相对相机坐标构建矩阵）
- 若最终不保留"玩家进入 plotyard"场景（子层级只被远处观看/由服务器驱动），可降级为低优先级

### 方案 E（上游，长期）：f64 或原生侧重基

- 向 ryanhcode/sable 提 issue/PR：原生侧在 `addChunk` 时做 plotyard→局部重基，或切换
  `rapier3d-f64`；本项目需 fork 维护 Rust 工具链，超出当前范围，仅记录为长期选项

---

## 5. 建议路线

1. **验证实验（1~2 小时，零代码风险）**：Java 侧 float 数值模拟 500 万/2048 万块处的
   `pos += v·dt` 积分，输出"位移吞没阈值 vs 速度"曲线，固化 L1 的量化数字；
   同时按方案 B 快速改 origin 实测 collider 行为（可选）
2. **方案 A 落地**：实现 JNI 边界全量重基（复用现有 `rapierRebase*` 配置骨架，
   删除 `isRapierPresent()` 死代码与"loaded too early"注释矛盾——mixin 需在 Rapier3D
   首次类加载前注入，用 `@Mixin(Rapier3D.class)` + 低优先级 early 注入策略）
3. **方案 C 落地**：本项目飞行物理接入 `Sable.HELPER` 子层级感知
4. **方案 D 按需**：修复渲染抖动（先确认 Sodium 路径是否随 A 自动缓解——A 不改客户端，
   渲染抖动需独立处理）
5. **方案 E 记录**：整理证据链（本文件）作为上游 issue 素材

## 6. 关键文件索引

| 文件 | 作用 |
|---|---|
| `build/sable-analysis/common-src/.../SubLevelContainer.java` | plotyard 原点/尺寸定义 |
| `build/sable-analysis/neoforge-src/.../RapierPhysicsPipeline.java` | 物理上传路径（无重基） |
| `build/sable-analysis/rapier-src/.../Rapier3D.java` | JNI 桥（重基注入点） |
| `run/.sable/natives/sable_rapier_x86_64_windows.dll` | f32 引擎（二进制证据） |
| `src/.../config/SpaceSimulationConfig.java` | 重基配置骨架（待消费） |
| `src/.../SpaceSimulation.java` | 遗留探测死代码 |
| `src/.../player/PlayerSpawnPoint.java` | 活动区 500~1000 万块（问题放大因素） |
