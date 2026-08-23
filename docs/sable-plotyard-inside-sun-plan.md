# 把 sable plotyard 移入太阳内部：实施方案

> 状态：**已实现并修复一次崩溃**（2026-08-21）
> 设计基准：**最小太阳半径 R_min = 200,000 块**（用户指定；`plotyardMinSunRadius` 配置，
> 太阳球心=世界原点）
> 目标：子层级实际方块（plotyard）完全位于任意世界的太阳内部，从根上消除
> plotyard 坐标域（2048 万块）的 f32 精度丢失

## 0. 实现状态

- ✅ mixin `SubLevelContainerMixin`（构造器 TAIL + @Shadow @Final @Mutable 字段覆盖）已生效
- ✅ `PlotYardPlacement` 计算放置参数（L、origin）
- ✅ 实测：进入世界正常、组装子层级正常、日志输出放置参数
- ✅ **崩溃修复**（2026-08-21 21:59 实测）：plotyard 覆盖世界原点导致
  `simulated`（航空学）的 PhysicsStaff 拖动/PhysicsAssembler 装配崩溃——
  `validateAnchors` 拒绝 "(0,0,0) 世界锚点哨兵落在 plotgrid 内"。修复：
  **网格避开原点**（块坐标从 2048 起，origin=1 plot）
- ✅ **拖动/装配已由用户实测通过**（2026-08-21 22:0x）
- 🔬 **精度尝试性修复（rapierfix）已实现，待用户实测**（见下文 §7）

---

## 1. 需求

1. **实际子层级方块位置（plotyard）完全位于太阳内部**——任何世界、任何时间。
2. 不同世界太阳大小可能不同，**以最小太阳大小（200,000 块）为设计标准**，
   保证即使太阳恰好是最小值，plotyard 也不会超出太阳边界。
3. 不改变子层级的**逻辑位置**（玩家看到的飞船位置）、玩家交互、存档数据。

---

## 2. 参数计算（数学约束，已按"避开原点"修订）

### 2.1 网格几何（sable 2.0.5 默认）

- `DEFAULT_LOG_PLOT_SIZE = 7`：一个 plot = 2⁷ chunks = 2048 块
- 网格边长 = 2^logSideLength × 2048 块（logSideLength 默认 7 → 128 × 128 plots）
- 全局 plot 号 = 本地 plot 号 + origin（`allocateSubLevel`: `createSubLevel(x + originX, ...)`）
- plot 的块范围 = `[plotPos << 11, (plotPos+1) << 11)`

### 2.2 约束一：完全在太阳内（R_min = 200,000）

网格块范围 `[2048, (1+2^L)·2048)`（以原点避开方案为准，见 2.3），
**最远角点距离 = (1+2^L)·2048×√2 ≤ R**：

| L | 网格（块） | 角点距离 | 需 R ≥ | 同时 plot 数 |
|---|---|---|---|---|
| 7 | [2048, 264,192) | 373,583 | 373,583 | 16,384 |
| **6** | **[2048, 133,120)** | **188,260** | **188,260** | **4,096** |
| 5 | [2048, 67,584) | 95,578 | 95,578 | 1,024 |
| 4 | [2048, 34,816) | 49,238 | 49,238 | 256 |
| 3 | [2048, 18,432) | 26,067 | 26,067 | 64 |
| 2 | [2048, 10,240) | 14,481 | 14,481 | 16 |
| 1 | [2048, 6,144) | 8,688 | 8,688 | 4 |

**默认 200,000 → L=6（4096 plots），角点 188,260 ≤ 200,000 ✓（余量 11,740）**

### 2.3 约束二（硬约束，崩溃修复）：避开世界原点

**plotyard 网格绝不能覆盖 (0,0,0)**。多个 mod 把 (0,0,0) 当作"世界锚点"哨兵传给
sable 约束（实测 `simulated` 1.3.1：`PhysicsStaffServerHandler.java:341` 拖动子层级、
`PhysicsAssemblerBlockEntity.java:337` 装配，均 `FreeConstraintConfiguration(ZERO, ...)`）。
sable 的 `PhysicsConstraintConfiguration.validateAnchors` 检查"锚点在 plotgrid 内但
body 不是子层级"即抛异常（"the first body of this constraint is not a sub-level..."）。

**原对称布局（origin=-64，网格 [-131072,131072)）覆盖原点 → (0,0,0) 落入本地
plot (64,64) → 拖动子层级崩溃。**

**修复：origin=1 plot → 网格块范围从 2048 起，(0,0,0) 的 plotX = -1 ∉ [0,2^L)，
永远不落在网格内**。代价：L=6（非 7），容量 4096 plots，f32 ULP @188,260 ≈ 0.03 块
（3 cm），仍较默认（2 块）提升约 64 倍。

### 2.4 y 轴球面约束（自动满足）

球面在 xz 距离 r 处允许 y = ±√(R²−r²)。最远角（r≈188,260）处 y ∈ ±67,526。
世界方块 y ∈ [−1,024, 2,048]（本项目 overworld）远在球面内 ✓。

### 2.5 动态防御（不同世界不同太阳）

实现：`designRadius = plotyardMinSunRadius`（用户定义"太阳最小大小"），
L = max{ l ∈ [1,7] : (1+2^l)·2048·√2 ≤ designRadius }，origin 恒为 1。
R < 8,688 → 无法放置 → 日志警告并回退 sable 默认（2048 万块）。
实际 `solarKillRadius` 小于 designRadius 时仅告警（网格按设计半径放置）。

---

## 3. 可行性证明（为什么不影响逻辑位置与玩家）

### 3.1 逻辑位置由 pose 编码，与 plotyard 绝对位置解耦（源码核实）

`Pose3dc.transformPosition(local)` = `R · (local − rotationPoint) · scale + position`。

组装时（`SubLevelAssemblyHelper.assembleBlocks`）：
- 方块从锚点 anchor（玩家处）平移到 `plotAnchor = plot.getCenterBlock()`（plotyard 中心）→ 方块相对 plotAnchor 的偏移 = 原几何
- `logicalPose.position = anchor − plotAnchor + COM`（COM ≈ plotAnchor）→ position ≈ anchor
- `logicalPose.rotationPoint = COM`（≈ plotAnchor）

因此无论 plotyard 在哪：
`逻辑位置 = transformPosition(plotyard_pos) = R·(plotyard_pos − plotAnchor) + anchor` → 恒等于
"anchor 附近 + 原几何偏移"。**飞船显示/交互位置不变**，plotyard 挪到太阳内部后映射自动成立。

### 3.2 玩家站在子层级上时坐标在"逻辑域"，不在 plotyard（源码核实）

`sable.mixin.entity.entities_stick_sublevels.EntityMixin.sable$updateSubLevelPosition`：
每 tick `setPos(subLevel.logicalPose().transformPosition(sable$plotPosition))`——
**实体（含玩家）的 MC 坐标被保持在逻辑位置（玩家活动区 500 万~1000 万块）**，
`plotPosition`（plotyard 坐标）只作为内部投影用。

结论：**玩家站上飞船时坐标不在太阳内** → `SunKillHandler`（太阳击杀）、
`PlayerSpawnPoint`（登录/重生判定）**均无需修改** ✓
（未粘附子层级的第三方实体若出现在 plotyard，仅玩家有太阳判定，无影响。）

### 3.3 渲染

sable 的 fancy/vanilla 子层级渲染按 pose 变换（渲染在逻辑位置），plotyard 原始方块不直接渲染；
玩家相机在逻辑域 → 太阳球渲染（WorldSphereRenderer）正常，无需修改。
（需实测确认无任何渲染路径泄漏 plotyard 原始位置。）

### 3.4 存档兼容（自动迁移，源码核实）

- `ServerLevelPlot.save()`：`plot_x = plotPos.x − origin.x`、chunk 用 `toLocal()` 存**局部坐标**
- 子层级逻辑 pose 随子层级 NBT 持久化
- → origin 改变后，已保存子层级按新 origin 重建 plot，**方块相对 plot 不变、逻辑位置不变**，自动迁移 ✓
- 旧 `sable_sub_level_force_load_tickets.dat` 残留 ticket 无 plot 匹配会被忽略（无副作用，可手动清理）

---

## 4. 实施清单（不写代码，列出改动点）

### 4.1 核心：覆盖 plot 容器 origin

**注入点（推荐）**：mixin `dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer` 与
`ClientSubLevelContainer` 的构造器 `<init>(Level, int logSideLength, int logPlotSize, int originX, int originZ)`，
`@ModifyArgs @At("HEAD")` 强制覆盖 `originX/originZ`（及防御性 logSideLength）。
- 优点：目标是 sable 库类、签名稳定，**无 mixin 应用顺序问题**（类首次加载时注入即生效）；
  不碰 sable 的 `ServerLevelMixin.sable$createPlotContainer`（mixin 生成方法，注入脆弱）。
- 备选（不推荐）：mixin sable 的 `ServerLevelMixin`/`ClientLevelMixin` 的 `sable$createPlotContainer`。

### 4.2 参数计算工具

- 读 `SpaceSimulationConfig.solarKillRadius` 实际值 → 按 §2.5 公式算 `L`/`origin`
- 配置新增（可选）：`plotyardMinSunRadius`（设计基准，默认 200,000，用户已定）
- 边界：R < 2,897 → 日志 ERROR + 回退默认值（宁出界不崩溃，提示调大太阳）

### 4.3 验证计划

1. **位置验证**：进入世界后，用 sable 命令/调试输出确认第一个子层级 plot 的
   全局块坐标在 `[-131,072, 131,072)` 内（期望 ≈ 第一个 plot (-64,-64) 中心 (-130,048, -130,048)）
2. **太阳内边界验证**：遍历网格 4 角（±131,071），`x²+z² < 200,000²` 断言
3. **逻辑位置回归**：在玩家面前组装子层级，确认飞船出现在锚点附近（不在太阳内）
4. **玩家站立回归**：站上飞船，确认坐标在活动区、不被太阳击杀、重生正常
5. **精度对比**：低速（1~5 m/s）推动飞船，对比旧 plotyard（2048 万）的步进/静止偏移
6. **存档回归**：保存/退出/重进，子层级位置与逻辑位置保持

---

## 5. 精度收益与残余问题

### 收益（本次改动直接解决）

- plotyard 坐标域：2048 万块（f32 ULP = 2 块）→ ≤ 13.1 万块（f32 ULP ≈ 0.0039 块 ≈ 3.9 mm）
  **精度提升约 512 倍**，子层级 collider/接触/约束恢复可用精度
- 方案简单（一个构造器 mixin + 参数计算），无 JNI 边界重基风险，不动 Java 侧坐标语义

### 残余问题（不属于本次改动，需后续方案）

1. **刚体 pose 域**：飞船刚体在 rapier 中仍以逻辑坐标（玩家活动区 500 万~1000 万块，
   f32 ULP 0.5~1 块）存储 → 飞船移动/旋转仍有 0.5 块级量子化、低速 <10 m/s 位移被吞。
   解决：方案 A（JNI 边界场景级重基）或上游 f64（见 `sable-sublevel-precision-analysis.md` §4）
2. **渲染域（L3）**：玩家在 500 万块处的 float 相机矩阵抖动（±0.5 块级），
   与 plotyard 位置无关，需方案 D 客户端渲染修正
3. 建议顺序：本方案（低成本、高收益）→ 方案 A 解决刚体域 → 方案 D 解决渲染域

---

## 6. 关键结论速查

| 问题 | 结论 |
|---|---|
| 默认网格能否放进 200,000 太阳？ | 能：L=6（4096 plots），角点 188,260 < 200,000 |
| 参数怎么改？ | origin = (1, 1) plot，logSideLength=6（避开原点约束）|
| 不同世界太阳不同？ | 以 plotyardMinSunRadius（默认 200,000）为设计基准，L 按角点 ≤ 设计半径取最大；实际太阳更小仅告警 |
| 飞船显示位置会变吗？ | 不会（logicalPose 编码映射，与 plotyard 解耦，§3.1） |
| 玩家站上飞船会被太阳击杀吗？ | 不会（实体坐标保持在逻辑域，§3.2），SunKillHandler 无需改 |
| 旧存档会坏吗？ | 不会（相对 plot 坐标存储，自动迁移，§3.4） |
| 为什么避开世界原点？ | (0,0,0) 是约束"世界锚点"哨兵（simulated 拖动/装配），覆盖原点即崩溃（§2.3） |
| 精度提升多少？ | plotyard 域 f32 ULP 2 块 → 0.03 块（约 64 倍） |
| 残余问题？ | 刚体 pose 域（500 万块）由 rapierfix 处理（§7）；渲染域（L3）待方案 D |

---

## 7. 精度尝试性修复：rapierfix（场景级浮点原点重基）

> 状态：**已实现（独立文件夹隔离），启动验证通过（mixin 配置加载无注入错误），
> 待用户实测**。2026-08-21

### 7.1 背景：原生引擎坐标域（Rust 源码核实，sable_rapier 2.0.5）

- 刚体平移：**f32 世界坐标**（`createSubLevel` 的 pose 直接 `as Real` 截断）——唯一精度问题源
- 子层级 collider：octree 方块 = **i32 plotyard 局部坐标**（相对 plot bounds min），无精度问题
- COM：**f64**（DVec3）；约束锚点：**f64**（pos − COM）；碰撞点回读：相对 COM 的局部量 + f64 COM 还原——全部精确
- 父世界地形：**i32 世界块坐标**（整数精确，但必须与刚体处于同一"平移域"才能碰撞）

### 7.2 方案

每维度维护"场景原点" O（对齐 16 块 = chunk 边界，首个世界域坐标自动设定，
或配置 `rapierRebaseOriginX/Y/Z` 手动指定），世界域坐标进出原生引擎时 ±O：

| JNI 边界（包装点） | 处理 |
|---|---|
| `createSubLevel` pose（刚体创建） | −O |
| `teleportObject` | −O |
| `getPose` 回读（readPose） | +O |
| `addChunk`/`removeChunk` global=true（父世界地形） | −O（chunk 坐标 −O/16）|
| `changeBlock` 父世界方块 | −O（plotgrid 内方块不平移）|
| plot 局部域（setLocalBounds/addChunk plot 分支/COM/约束/碰撞点/速度） | **不碰**（原生已精确）|

引擎内坐标量级 < 数十万块 → f32 ULP 恢复到毫米~厘米级，飞船低速运动/碰撞精度恢复。

### 7.3 隔离方式（单独文件夹）

- 实现：`src/main/java/org/xyccwa/space_simulation/rapierfix/`（状态管理，非 mixin 包）
  + `src/main/java/org/xyccwa/space_simulation/mixin/rapierfix/`（包装 mixin）
- 独立 mixin 配置：`src/main/resources/space_simulation.rapier.mixins.json`
  （mods.toml 单独 [[mixins]] 块注册；目标类不存在时 required=false 自动跳过；
  实现**不引用任何 rapier 包类型**——字符串 mixin 目标，无编译期依赖）
- **一键关闭**：`SpaceSimulationConfig.rapierRebaseEnabled=false`
- **整体移除**：删 mods.toml 块 + 两个目录 + mixin json（无残留依赖）

### 7.4 已知限制（尝试性修复范围外）

- Create 机构（KinematicContraption）与 RapierBoxHandle 未重基：机构与重基后的飞船
  在引擎内域不一致 → 机构间/机构-飞船物理不会互碰（视觉正常，无崩溃）
- 渲染域（L3，玩家在 500 万块处的 float 相机矩阵抖动）不属本方案

### 7.5 验证方法

- 低速（1~5 m/s）推动飞船：修复前位移被吞/步进，修复后应连续平滑
- 飞船与小行星/地形碰撞：接触稳定无抖动
- `rapierRebaseEnabled=false` 对比：恢复旧行为（验证隔离开关有效）
