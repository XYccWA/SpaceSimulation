# SpaceSimulation 科技树与进度系统设计

> 状态：**设计定稿，尚未实现**（2026-10-02 逐项与用户确认裁定）
> 设计基准：NeoForge 21.1.234 / Minecraft 1.21.1 / Sable 2.0.5 / Sable Companion 1.6.0
> mod_version 基准：0.3.1-beta
> **本文档是跨对话实现的唯一基准。** 后续每个批次建议开一个独立对话，对话开头先读 §11 的对应批次条目。

---

## 0. 结论摘要

这是一个**残骸回收型**科技树，不是文明演进型。玩家是一台高级文明的智能采矿机器人，所属殖民船在恒星系内被敌人击毁，玩家获得权限留守等待支援。因此：

- 化学能阶段**整体跳过**（真空、无生物质、无化石燃料），主线是「用残骸把文明等级重新爬回来」
- 科技解锁走**蓝图恢复**（数据核心来自残骸），不是研发
- 开局资产是**围绕出生点一同生成的大碎块残骸场**，玩法起点是"搜刮"而非"挖矿"
- 推进与武器**全部挂在 Sable 子关卡上**，不是玩家个人装备
- 终局目标是**建造星际信标求援**

**当前实现优先级**：非战斗内容（矿物处理 / 能源 / 推进 / 建造）。武器线与敌人**暂缓**，但设计保留。

---

## 1. 世界观与设计前提

| 项 | 设定 |
|---|---|
| 玩家身份 | 较高级文明派到本恒星系殖民船上的**智能采矿机器人** |
| 开局事件 | 殖民船被恒星系中的敌人**击毁** |
| 玩家处境 | 获得留守权限，等待后续支援 |
| 终局目标 | 建造**星际信标**求援，并守住直到支援抵达 |
| 敌人 | 已存在且足以击毁殖民船；本阶段**暂缓实现** |

### 1.1 由设定直接推出的设计后果

1. **化学能整段砍掉**：燃烧室 + 蒸汽轮机、煤油/RP-1 链（碳质球粒热解改产石墨/碳纤维等**材料**）、液氧煤油主推进、硝石推进剂 —— 全部不做。
2. **氢氧必须保留**，但降级为**工质与化工中间体**：
   - 液氢 = 核热发动机工质 / 聚变燃料 / 燃料电池
   - 液氧 = 氧化剂 / 焊接切割 / 应急推进
   - 水 = 电解原料 / 辐射屏蔽 / 工质储备
3. **玩家自身的自由飞行 = 机器人内置推进器**。现有 `FlightPhysics` 的「无工质自由飞行」不是漏洞，而是设定本身。这与「真燃料/Δv 只约束载具、不约束玩家」的既有裁定自洽。
4. **机器人自身的能量/维护约束由另一模组代劳**，本模组**暂不实现**。
5. **武器线起点提前到 T0**（采矿激光/切割器/破岩炸药即最初武器），但因为敌人暂缓，武器线整体后置。

---

## 2. 技术地基（已核实）

### 2.1 Sable 2.0.5 API

以下全部从 `sable-neoforge-1.21.1-2.0.5-sources.jar` 实际核对过，可直接依赖。

| 类 / 方法 | 用途 |
|---|---|
| `dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor` | **推进/武器方块的标准入口** |
| ├ `sable$tick(ServerSubLevel)` | 每服务端游戏 tick 回调 |
| └ `sable$physicsTick(ServerSubLevel, RigidBodyHandle, double timeStep)` | 每**物理** tick 回调（一 tick 可能多次），施力必须在这里 |
| `dev.ryanhcode.sable.api.block.propeller.BlockEntitySubLevelPropellerActor` | 螺旋桨推进范例：`getPropeller()` + `applyForces()` |
| `dev.ryanhcode.sable.api.block.BlockEntitySubLevelReactionWheel` | 反作用轮（姿态控制）现成 |
| `dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider` | 升力/阻力，**全部乘以当地气压** |
| `dev.ryanhcode.sable.api.block.BlockSubLevelCustomCenterOfMass` | 自定义质心（配平、偏置喷口） |
| `dev.ryanhcode.sable.api.physics.force.ForceGroups` | 力分组：`GRAVITY` `DRAG` `LEVITATION` `BALLOON_LIFT` `PROPULSION` `LIFT` `MAGNETIC_FORCE`（**带 GUI 可视化**） |
| `subLevel.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get())` → `.applyAndRecordPointForce(Vector3d pos, Vector3d impulse)` | 在指定局部点施加冲量 |
| `dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle` | `of(ServerSubLevel)` / `applyImpulseAtPoint` / `applyLinearAndAngularImpulse` / `applyTorqueImpulse` / `getLinearVelocity(Vector3d dest)` / `addLinearAndAngularVelocity` / `teleport(pos, orient)` / `isValid()` |
| `dev.ryanhcode.sable.api.physics.mass.MassTracker` | `getMass()` / `getCenterOfMass()` / `getInertiaTensor()` / `addBlockMass(...)`（增量接口） |
| `dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level)` / `ServerSubLevelContainer` | 容器 |
| `dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, bounds)` | **组装子关卡**（`AsteroidEntityifier` 已在用） |
| `dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED` | 强加载票据 |
| `dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.UNLOADED` | 卸载原因 |
| `dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext` | **子关卡序列化/落盘** |
| `SablePrePhysicsTickEvent` / `SablePostPhysicsTickEvent` | 物理 tick 前后事件 |
| `DimensionPhysicsData.getAirPressure(level, pos)` | 当地气压 |

**源码查阅方式**（后续对话自查 API 用）：

```
D:\.gradle\caches\modules-2\files-2.1\dev.ryanhcode.sable\sable-neoforge-1.21.1\2.0.5\b480c4ea20f26dbbccaaf63cc502bc9a6fdfc26c\sable-neoforge-1.21.1-2.0.5-sources.jar
D:\.gradle\caches\modules-2\files-2.1\dev.ryanhcode.sable\sable-sable_rapier-1.21.1\2.0.5\b79ddb4cc6b2fedb5cd7b1a31efc58c8bbd0d05f\sable-sable_rapier-1.21.1-2.0.5-sources.jar
```

### 2.2 现有代码资产

| 文件 | 现状与作用 |
|---|---|
| `asteroid/entity/AsteroidEntityifier.java` | 小行星实体化。`STAGING_ORIGIN = (2048, 0, 200_000)`；把结构模板写进 sable plot 的 embedded level；**每 tick 用 `RigidBodyHandle.teleport` 把物理体推到解析轨道位置**；**不落盘**：离开窗口 `removeSubLevel`，重进按结构模板重建 |
| `orbital/Gravity.java` | 太阳引力场，`acceleration(x,y,z,out)` 输出**块/tick²**；`mu()` 走配置 |
| `orbital/PlayerOrbitServer.java` | 玩家轨道服务器权威积分与预测对账 |
| `util/FlightPhysics.java` | `ACCELERATION = 0.06 块/tick²`、`MAX_SPEED = 10 块/tick`、`ROLL_SPEED = 5°/tick` |
| `util/SunRadius.java` | 太阳半径由世界种子派生，`MIN_RADIUS = 50_000`、`MAX_RADIUS = 100_000` |
| `damage/SolarHeatDamage.java` | 高温区 `(R, 1.1R]`，越近伤害越高（1.0 → 20.0，10 tick 结算）；`d ≤ R` 强制处死 |
| `damage/PlayerAccelerationDamage.java` | 过载/撞击伤害（推进的「惯性阻尼场」要复用它） |
| `config/SpaceSimulationConfig.java` | `orbitalMu`（默认 6.25e6）、`asteroidMuFollowPlayerOrbital`、`asteroidEntityifyMaxLoaded`、`asteroidMaxAbsY` 等 |
| `modItem/SpaceSimulationItem.java` | 20 种矿砂 + 13 种金属锭 + 6 种合金锭 + `dust` |
| `modBlock/SpaceSimulationBlock.java` | 20 种矿石 + `dust_block` |
| `dataGen/SpaceSimulationRecipesProvider.java` | **空的**（`buildRecipes` 只调 super）→ 配方断链 |
| `rapierfix/RapierOriginManager.java`、`mixin/rapierfix/RapierPhysicsPipelineMixin.java` | 场景原点重基（百万格尺度必需） |
| `asteroid/AsteroidUniverse*.java`、`AsteroidOrbit.java` | 小行星**解析开普勒轨道**（自然态） |

### 2.3 关键公式与数值

单位约定：`1 块 = 1 m`，`1 tick = 0.05 s`；`块/tick² × 400 = m/s²`，`块/tick × 20 = m/s`。

| 量 | 值 |
|---|---|
| 太阳引力参数 μ | `6.25e6` 块³/tick²（= 2.5e9 m³/s²） |
| 玩家速度上限 | `10 块/tick = 200 m/s` |
| 玩家推力（现状） | `0.06 块/tick²`（= 24 m/s²） |
| 圆轨道速度 | `v_circ(r) = √(μ/r)` |
| 逃逸速度 | `v_esc(r) = √(2μ/r)` |
| 推重比 = 1 的边界半径 | **`r_min(a) = √(μ/a)`** |
| 稳定圆轨道要求 v_circ ≤ 10 | **r ≥ 62,500 格** |
| 纯速度逃逸要求 v_esc ≤ 10 | **r ≥ 125,000 格** |
| 太阳半径 R | `[50,000, 100,000]`，高温区 `(R, 1.1R]` |
| 玩家出生轨道 | 100 万 ~ 150 万格 |

**深井结论（设计已确认"不给任何救援"）**：

| 太阳半径 | 高温区 | 该处 v_circ | 该处 v_esc | 结果 |
|---|---|---|---|---|
| ≈50,000 | 50k–55k | 10.7–11.2 **> 10** | 15.1–15.8 | **进入即被引力拖入太阳，物理上无法自救** |
| ≈100,000 | 100k–110k | 7.5–7.9 ≤ 10 | 10.6–11.2 | 可维持轨道，能硬顶出来 |

引擎的 `r_min = √(μ/a)`：

| 引擎 | a（块/tick²） | r_min |
|---|---|---|
| 离子 | 0.0005 | **111,800 格**（此半径内推不过引力） |
| 液氧煤油 | 0.06 | 10,210 格 |
| 核热 | 0.10 | 7,906 格 |
| 聚变直驱 | 0.15 | 6,455 格 |

---

## 3. 冻结裁定表

> 以下为用户逐项拍板的裁定，**实现时不得自行更改**；如需变更必须先与用户确认。

| # | 主题 | 裁定 |
|---|---|---|
| 1 | 能源单位 | 自建 **SE** 能量单位 + **FE 桥接** |
| 2 | 能源上限 | 完整做到**反物质**（T0–T6 全实现） |
| 3 | 新增矿物 | 只补关键断链：**钴、锂、锆、稀土、铀/钍**；铂族走**副产**，不新增铂矿 |
| 4 | 氦-3 | 不新增矿物，走「**太阳风富集型风化层**」小行星类型 |
| 5 | 反物质 | **设定物**，只能由设施制备，无自然矿 |
| 6 | 推进约束 | **真燃料 + Δv 硬约束**（燃料质量真实进入 `MassTracker`） |
| 7 | 速度上限 | **不抬高**，统一 200 m/s；引擎差异只在**推力 / 比冲 / 燃料** |
| 8 | 限速方式 | **推力随速度剃减的软限制**：`F_eff = F_max × max(0, 1 − (v/v_max)²)` |
| 9 | 反物质引擎 | 实为**曲率跃迁**（非推力）；配套**跃迁信标**与**中继站** |
| 10 | 离线传播 | 保持现有**开普勒滑行**，离线不耗燃料 |
| 11 | 深井（r < 12.5 万格） | **不给任何救援手段**，靠引擎硬顶 |
| 12 | 载体 | 推进与武器**全在 Sable 子关卡**上，不是玩家装备 |
| 13 | 小行星引力 | 自然态走**解析轨道**；**改造为飞船后按真实引力** |
| 14 | 小行星持久化 | PILOTED **整颗子关卡落盘**（`SubLevelSchematicSerializationContext`） |
| 15 | 小行星可逆性 | **单向不可逆**（拆光设备也不退回解析轨道） |
| 16 | 残骸布局 | 殖民船以**大碎块围绕玩家出生点一同生成** |
| 17 | 敌人/武器 | **暂缓**，先做非战斗内容（设计保留） |
| 18 | 机器人能量 | **由另一模组代劳**，本模组暂不实现 |
| 19 | 化学能 | **整段跳过**（氢氧保留为工质/化工中间体） |

---

## 4. 科技树主干

```
T0 残骸回收 ──→ T1 设施重建 ──→ T2 电力与合金 ──→ T3 精炼与制造
（拆碎块）      （电解制氢氧）    （燃料电池/光伏）   （VIM/EBM/萃取）
                                                          │
                        T6 超越与求援 ←── T5 聚变 ←── T4 裂变
                        （星际信标）     （氦-3/超导）   （修复反应堆）
```

| 级 | 主题 | 材料线 | 能源线 | 推进线 | 武力线 |
|---|---|---|---|---|---|
| **T0** | 残骸回收 | 拆解殖民船碎块；恢复采矿激光/切割器/扫描 | 残骸里的备用电池 | 机器人内置推进器（SE） | 采矿激光、切割器、破岩炸药 |
| **T1** | 设施重建 | 破碎-研磨-选矿线；水电解制氢氧；铁镍、铝、硅 | 燃料电池组 | 过氧化氢 RCS | 同上强化 |
| **T2** | 电力与合金 | 电解精炼；钛、镁、铬 | 光伏 + 蓄电池 | 小推力化学（氢氧，工质定位） | 多管炮 |
| **T3** | 精炼与制造 | 溶剂萃取、Kroll、VIM/EBM；钨钼铌钽铼**铂族**；SE 电网 | 高温气冷机组 | 氢氧机、离子推进 | 电磁轨道炮 |
| **T4** | 裂变 | 裂变燃料、激光晶体 | **修复殖民船反应堆**、RTG | 核热 NERVA（液氢工质）、核电 NEP | 激光炮、核脉冲装置 |
| **T5** | 聚变 | 超导磁体、惯性阻尼场 | 托卡马克聚变堆（氦-3） | 聚变直驱、等离子体射流 | 粒子束炮 |
| **T6** | 超越与求援 | 反物质制备 | 湮灭室 | **曲率跃迁** | 湮灭炮；**星际信标** |

### 4.1 两条闭环（设计骨架）

- **采矿闭环**：武器击碎小行星 → 方块/矿砂 → 破碎→研磨→选矿→焙烧→浸出→还原→精炼 → 合金 → 更强的引擎与武器 → 能打更硬的目标。
- **探索闭环**：推进性能 → 抵达更远小行星带 → 更稀有的矿（铼、铂族、铀钍、氦-3）→ 更高阶能源与材料 → 更强的推进。
- **副产回路**：铜电解阳极泥 → **铂族**；硫化物焙烧 → **硫酸** → 湿法冶金本身。让各工序互为前提。

---

## 5. 矿物与材料体系

### 5.1 现有资产（**不要重复注册**）

**矿石方块 20 种**（`SpaceSimulationBlock`）：

- 金属类 11：`chalcocite_ore` 辉铜矿、`kamacite_ore` 铁纹石、`taenite_ore` 镍纹石、`chromite_ore` 铬铁矿、`ilmenite_ore` 钛铁矿、`forsterite_ore` 镁橄榄石、`wolframite_ore` 钨锰矿、`columbite_ore` 铌铁矿、`molybdenite_ore` 辉钼矿、`tantalite_ore` 钽铁矿、`rheniite_ore` 辉铼矿
- 硅质类 4：`olivine_ore` 橄榄石、`pyroxene_ore` 辉石、`plagioclase_ore` 斜长石、`quartz_ore` 石英
- 碳质类 5：`carbonaceous_ore` 碳质球粒、`phyllosilicate_ore` 层状硅酸盐、`carbonate_ore` 碳酸盐、`troilite_ore` 陨硫铁、`magnetite_ore` 磁铁矿
- 另有 `dust_block` 浮土块

**矿砂 20 种**：上述 20 种各对应一个 `*_sand`（`SpaceSimulationItem`），另有 `dust`。

**金属锭 13 种**：`copper_ingot`、`iron_ingot`、`nickel_ingot`、`chromium_ingot`、`titanium_ingot`、`magnesium_ingot`、`tungsten_ingot`、`niobium_ingot`、`molybdenum_ingot`、`tantalum_ingot`、`rhenium_ingot`、`platinum_ingot`、`rhodium_ingot`

**合金锭 6 种**（注释里已写明用途，直接对应推进系统部件）：

| 锭 | 代码注释里的用途 |
|---|---|
| `iron_nickel_alloy_ingot` | 铁+镍天然合金，基础结构材料 |
| `chromium_nickel_iron_alloy_ingot` | Incoloy 890 型，**发动机涡轮泵壳体**，耐 750℃ |
| `tungsten_rhenium_alloy_ingot` | **火箭喷管喉衬**，承受 >2000℃ 燃气冲刷 |
| `nickel_rhenium_alloy_ingot` | **涡轮叶片**，提升 650–850℃ 力学性能 |
| `platinum_rhodium_alloy_ingot` | **发动机喷管**，耐 1500–1600℃ 富氧烧蚀 |
| `gh4061_alloy_ingot` | 铁镍铬基+钴钨钼，**大推力发动机涡轮球壳**，抗富氧烧蚀 55MPa |

> ⚠️ **落差提示**：`gh4061` 的注释点名「钴钨钼」，但当前**没有钴的任何来源**。这是必须补的断链之一。

### 5.2 新增矿物（6 种，均为现实矿物）

命名遵循现有风格：方块 `{name}_ore`、物品 `{name}_sand`。

| 矿物 | 英文名 | 化学式 | 引入理由 |
|---|---|---|---|
| 辉砷钴矿 | `cobaltite` | CoAsS | 补**钴**（GH4061 需要）；砷作副产（环保处理玩法） |
| 锂辉石 | `spodumene` | LiAlSi₂O₆ | 补**锂**（电池、热控工质、聚变氚增殖） |
| 锆石 | `zircon` | ZrSiO₄ | 补**锆**（核包壳）与**铪**（超高温） |
| 独居石 | `monazite` | (Ce,La,Nd,Th)PO₄ | 补**稀土**（钕铁硼永磁、激光晶体）+ 钍 |
| 沥青铀矿 | `uraninite` | UO₂ | 裂变燃料 |
| 钍石 | `thorite` | ThSiO₄ | 增殖燃料 |

**氦-3**：不新增矿物。新增一种小行星类型「太阳风富集型风化层」，其 `dust` / 浮土在特定带内富集氦-3，需 T3 级「同位素富集塔」提取。

**反物质**：不新增矿物，只能由 T6 设施制备（近日轨道收集环 + 超导磁约束储存）。

### 5.3 完整处理链（九道工序）

统一流程：**采掘 → 破碎 → 研磨 → 分级 → 选矿 → 焙烧/浸出 → 还原/电解 → 精炼 → 合金化/成型**

| 矿石 | 工艺路线 | 主产物 | 副产（喂链） |
|---|---|---|---|
| 辉铜矿 Cu₂S | 浮选→焙烧→熔炼→**电解精炼** | 铜 | **阳极泥 → 铂/铑** |
| 铁纹石/镍纹石 (Fe,Ni) | 磁选→直接还原→羰基法分离 | 铁、镍、铁镍合金 | 钴（微量伴生） |
| 磁铁矿 Fe₃O₄ | 磁选→直接还原 | 铁 | — |
| 铬铁矿 FeCr₂O₄ | 重选→碳热还原→电解 | 铬、铬铁 | 铁 |
| 钛铁矿 FeTiO₃ | 氯化法→TiCl₄→**Kroll 镁热还原** | 海绵钛 → VAR 钛锭 | 铁、氯循环 |
| 镁橄榄石 Mg₂SiO₄ | 高温碳热还原（镁蒸气冷凝） | 镁 | 硅铁 → 硅 |
| 钨锰矿 (Fe,Mn)WO₄ | 碱浸→钨酸铵→氢还原 | 钨 | 锰 |
| 铌铁矿 / 钽铁矿 | HF 浸出→溶剂萃取分离 | 铌、钽 | 锰 |
| 辉钼矿 MoS₂ | 浮选→焙烧→氢还原 | 钼 | **硫 → 硫酸** |
| 辉铼矿 ReS₂ | 焙烧→萃取→氢还原 | 铼 | 硫 |
| 石英 SiO₂ | 碳热还原→西门子法 | 冶金硅 → 多晶硅 | — |
| 斜长石 | 碱浸→氧化铝→**霍尔-埃鲁电解** | **铝** | 硅渣、钠 |
| 陨硫铁 FeS | 焙烧 | 硫 → 硫酸、铁 | SO₂ |
| 碳质球粒 | 热解 | **碳 / 石墨 / 碳纤维**、CO | 水、有机物 |
| 层状硅酸盐 | 加热脱水 | **水**、硅酸盐渣 | — |
| 碳酸盐 | 煅烧 | CaO / MgO、CO₂ | — |
| **辉砷钴矿** | 焙烧→浸出→萃取 | **钴** | 砷（需处理） |
| **锂辉石** | 焙烧→酸浸→碳酸锂 | **锂** | 铝硅酸盐渣 |
| **锆石** | 氯化→分离 | **锆、铪** | 硅 |
| **独居石** | 碱浸→萃取分离 | **稀土（Nd/Ce/La）** | 钍、磷酸盐 |
| **沥青铀矿** | 浸出→萃取→还原 | **铀** | 镭（微量） |
| **钍石** | 碱浸→萃取 | **钍** | 稀土 |

### 5.4 必须补的断链

1. **铂族无来源**：`platinum_ingot` / `rhodium_ingot` 已注册但没有任何矿石或副产路径 → 通过**铜电解阳极泥**与**镍冶炼副产**补齐。
2. **钴无来源**：GH4061 需要 → 通过新增辉砷钴矿补齐。
3. **硅/铝/硫/碳无提取路径**：石英、斜长石、陨硫铁、碳质球粒已存在，但只有矿物没有工艺 → 补齐。
4. **钨钼铌钽铼的还原路径缺失**。
5. **配方系统整体为空**（`SpaceSimulationRecipesProvider`）→ 全部要写。

---

## 6. 能源系统（SE）

### 6.1 单位与桥接

- **SE**（Space Energy）为本模组主能量单位；`1 SE = 4 FE`（可配置）。
- 提供 **FE → SE** 与 **SE → FE** 双向转换方块/能力，使本模组可与其他科技模组互通。
- 实现方式建议：NeoForge `Capabilities.EnergyStorage` 注册自有 capability，同时挂载 `IEnergyStorage` 适配器。

### 6.2 能源世代

| 代 | 能源 | 多方块 | 燃料/工质 | 解锁级 |
|---|---|---|---|---|
| 1 | 残骸备用电池 | 电池组 | 无（充电） | T0 |
| 2 | 电化学 | 燃料电池组、光伏阵列、蓄电池组 | 氢/氧、光照、锂 | T1–T2 |
| 3 | 热电 | 高温气冷机组、RTG | 金属氧化物 + 冷却回路；钚-238 | T3–T4 |
| 4 | 核裂变 | 裂变堆（铀/钍增殖） | U-235 / Th-232 | T4 |
| 5 | 核聚变 | 托卡马克（超导磁体 NbTi/Nb₃Sn） | 氘氚 / D-He₃ | T5 |
| 6 | 湮灭 | 磁约束湮灭室 | 反物质 | T6 |

---

## 7. 推进系统

> **载体**：一切推进都挂在 **Sable 子关卡**上。玩家自身的自由飞行为机器人内置推进器（不在此系统内）。

### 7.1 实现模型

每个引擎方块 = `BlockEntity implements BlockEntitySubLevelActor`，在 `sable$physicsTick` 中：

1. 检查燃料/工质与 SE 供电，不足则关闭
2. `F = 标称推力 × 功率因子`
3. **软限速**：`F_eff = F × max(0, 1 − (v/v_max)²)`（v 取自 `handle.getLinearVelocity`，v_max = 200 m/s）
4. `冲量 = F_eff × timeStep`，在喷口局部位置施加：
   ```java
   subLevel.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get())
            .applyAndRecordPointForce(nozzleLocalPos, impulseVector);
   ```
   喷口位置偏置 → 自动产生力矩（万向摆动免费得到）
5. 扣燃料：`ṁ = F_eff / v_e`，`v_e = Isp × 9.81`；**燃料质量真实进入 `MassTracker`** → 推力加速度随燃料消耗上升（齐奥尔科夫斯基真实成立）

### 7.2 引擎表

| 级 | 引擎 | 推进剂 | Isp (s) | 推力 a（块/tick²） | 定位 |
|---|---|---|---|---|---|
| T0 | 机器人内置 | SE 电能 | — | 0.06（＝现状） | 仅推自己 |
| T1 | 冷气推进器 | N₂ / H₂O | 70 | 0.005 | EVA 精细机动 |
| T1 | 固体火箭 | 采矿炸药衍生 | 220 | 0.09 | 一次性助推、导弹 |
| T2 | 单组元 | H₂O₂ | 160 | 0.01 | RCS 姿控 |
| T2 | 氢氧机（小） | LOX / LH₂ | 450 | 0.03 | 早期主推进（工质定位） |
| T3 | 氢氧机 | LOX / LH₂ | 450 | 0.05 | 主推进 |
| T3 | 离子推进 | Xe / Ar / Bi | 3500 | 0.0005 | 省燃料；**11.2 万格内推不过引力** |
| T4 | 核热 NERVA | LH₂ + 裂变 | 850 | 0.10 | 深井主力 |
| T4 | 核电 NEP | Xe + 裂变电 | 6000 | 0.002 | 长航程 |
| T5 | 聚变直驱 | D-He₃ | 25000 | 0.15 | 贴太阳 |
| T5 | 等离子体射流 | He₃ + Li | 50000 | 0.08 | 星际高效 |
| T6 | **曲率跃迁** | 反物质 | — | — | 跃迁（非推力） |

### 7.3 真空约束

`BlockSubLevelLiftProvider` 的升力与阻力**全部乘以 `DimensionPhysicsData.getAirPressure`**。本模组主世界是真空 → **螺旋桨、风扇、机翼、气球推力恒等于零**。

**结论**：推进必须**自带氧化剂**（火箭）。螺旋桨类仅在小行星表面/有大气环境可用。

### 7.4 曲率跃迁（T6）

- 组成：跃迁核心（反物质供能）+ 导航计算机（硅+稀土）+ 目标信标
- 约束：不能在强引力井内冷启动（需逃逸窗口）；盲跳误差随距离平方增长，有信标则精确
- 消耗：反物质 + 巨量 SE + 冷却周期
- 配套：T4–T5 **跃迁中继站**，把长航线拆成可跳段
- 定位：唯一能把「百万格航程 = 83 分钟」压掉的手段，也是深井里最后的自救牌

### 7.5 与现有系统的耦合

| 系统 | 耦合方式 |
|---|---|
| 过载 | 复用 `PlayerAccelerationDamage`；「惯性阻尼场」（T5）同时负责限速与防过载 |
| 热 | `SolarHeatDamage` 扩展为「太阳辐照 + 引擎废热」，配辐射散热板 + 冷却回路 |
| 服务器权威 | 燃料与推力由服务器结算，客户端按输入 seq 预测对账（`PlayerOrbitServer` 现成） |
| 离线 | 保持开普勒滑行，**不耗燃料** |
| 原点重基 | 百万格 + 200 m/s 会加剧 `RapierOriginManager` 重基频率，上线前先压稳 |

---

## 8. 武器系统（**暂缓实现**，设计保留）

> 用户裁定：敌人与武器线暂缓，先做非战斗内容。以下为保留设计，**不要在本阶段实现**。

| 武器 | 级 | 多方块组成 | 弹种/耗材 | 定位 |
|---|---|---|---|---|
| 采矿激光（民用） | T0 | 激光头 + 电容 | 电力 | 采矿；可转军用 |
| 破岩炸药 | T0 | 化工 | 炸药 | 采矿爆破 |
| 速射近防炮 | T2 | 炮座 + 供弹 + 冷却 | 钨合金穿甲弹 | 小型目标拦截 |
| 电磁轨道炮 | T3 | 电容阵列 + 钨铼加速导轨 + 散热 + 火控雷达 | 钨芯/贫铀动能弹 | 击穿小行星、舰船 |
| 导弹/火箭发射架 | T3 | 发射轨 + 装填机 + 推进剂罐 | 固体/液体推进剂 | 面杀伤 |
| 激光炮 | T4 | 掺钕增益介质 + 铑反射镜 + 大电容组 | 电力 + 冷却剂 | 精确熔穿 |
| 核脉冲装置 | T4/T5 | 裂变/聚变弹头 + 投射器 | 铀钚 / 氘氚 / 氦-3 | 大范围碎星 |
| 粒子束炮 | T5 | 超导磁体环 + 液氦循环 + 加速腔 | 电力 + 液氦 | 远距离穿透 |
| 湮灭炮 | T6 | 磁约束储存环 + 湮灭室 | 反物质 | 终局 |

**关键机制**：

1. **开火 = 对自身船体施反冲冲量**（在 `sable$physicsTick` 中）。小艇需要 RCS 补偿后坐力；要塞级平台可以无脑齐射 —— 形成**体量分级**。
2. **弹丸可以是子关卡**：用 `SubLevelAssemblyHelper.assembleBlocks` 组装（可带自己的引擎做制导）。
3. **命中小行星 = 子关卡对子关卡**：打碎的方块直接进选矿链。
4. **无后坐力方案**：`ForceGroups.MAGNETIC_FORCE` 电磁反冲补偿（T5）。
5. 配套防御：超导磁屏蔽、激光拦截、GH4061/钨铼装甲板。

---

## 9. 状态机与落盘

### 9.1 三态

现有 `AsteroidEntityifier` 是**运动学体**（每 tick `teleport` 到解析轨道位置），物理力会被 teleport 覆盖 → 必须切换驱动模式才能让改造后的小行星受物理。

| 状态 | 驱动 | 受力 | 落盘 | 适用 |
|---|---|---|---|---|
| **NATURAL** | 解析开普勒 + 每 tick `teleport` | 无 | **不落盘**（按结构模板重建） | 远处未改造小行星 |
| **DRIFT** | 物理积分 | 太阳引力 | **落盘** | 出生残骸碎块、无核心的漂流残骸 |
| **PILOTED** | 物理积分 | 太阳引力 + 推进 + 后坐力 | **落盘** | 玩家舰船、被改造的小行星 |

### 9.2 状态转换

- **NATURAL → PILOTED**：在该小行星上完成「控制核心」多方块（T3+）并装上引擎
- **可逆性**：**单向不可逆**。拆光设备只是变成 DRIFT，永不退回解析轨道
- **切换瞬间的三个必做项**：
  1. **速度继承**：把物理体线速度/角速度初始化为解析轨道在该点的速度矢量。**否则速度归零 → 小行星立刻被太阳拽走**
  2. **停止 teleport**：`Instance` 需要驱动模式字段
  3. **立即落盘**：写入 `SubLevelSchematicSerializationContext`

### 9.3 落盘策略

- **只有 NATURAL 不落盘**；DRIFT 与 PILOTED 都必须落盘
- 原因：出生残骸场必须落盘，否则玩家拆了半个货舱、离开再回来会按模板复原 → **无限刷材料**
- 风险：`AsteroidEntityifier` 注释明确写「不落盘」就是为了避免堆积数万方块的结构副本。**建议限制同时存在的落盘子关卡数量**（可复用 `asteroidEntityifyMaxLoaded` 思路），并在达到上限时拒绝新的改造请求（给出明确提示）

---

## 10. 出生残骸场

### 10.1 生成方式

**大碎块围绕玩家出生点一同生成**，每个碎块就是一个 Sable 子关卡。

**复用 `AsteroidEntityifier` 的管线**：`STAGING_ORIGIN = (2048, 0, 200_000)` → 落结构模板 → `assembleBlocks` 搬进 plot。只把结构模板从「小行星」换成「殖民船舱段」。

**碎块初速度 = 玩家轨道速度** → 自然一起绕日，相对缓慢漂移（不需要额外机制把它们钉在出生点）。

### 10.2 碎块类型

| 碎块 | 回收产出 | 解锁 | 备注 |
|---|---|---|---|
| 舰桥段 | 数据核心 | 蓝图 T1–T2 | 开局第一目标 |
| 加工舱 | 破碎机、机床残件 | 矿物处理线 | 可修复设备 |
| 货舱 | 合金锭、板材、线缆 | 起步材料 | **替代挖矿起步** |
| 动力舱 | 损坏反应堆、屏蔽材料 | T4 种子 | 长期目标 |
| 引擎舱 | 喷管、涡轮泵、推力室 | T3 引擎种子 | 长期目标 |
| 生保舱 | 结构件、水 | 水/电解 | 氢氧链起点 |

**方块限制**：小行星/残骸结构**只用模组自带方块**（`space_simulation:*`）；如需新方块（如舱壁、管道、破损外壳）也应注册在模组命名空间下，**不要引入 `minecraft:*` 矿物方块**。

### 10.3 蓝图系统

- 数据核心 = 可交互物品/方块，右键打开「蓝图恢复」界面
- 状态存储：建议用 NeoForge **Attachment**（`SpaceSimulationAttachments` 已存在）或 SavedData 存全局解锁集合
- 语义：玩家**知道怎么做**，但缺材料与设施 → 蓝图只解锁**配方可见性**与**设备可建造性**

---

## 11. 实现批次与任务分解

> **用法**：每个批次建议开一个独立对话。对话开头把本批次的「目标 / 涉及文件 / 依赖 / 验收」贴给 AI。

### 批次 0：配方基础（**最高优先，已有待办**）

- **目标**：打通现有矿石 → 材料的配方断链，让生存模式可用
- **涉及**：`dataGen/SpaceSimulationRecipesProvider.java`（现为空）、`SpaceSimulationBlockLootTableProvider`、`SpaceSimulationItemTags` / `SpaceSimulationBlockTags`
- **内容**：
  - 矿石方块掉落对应的 `*_sand`（而非原矿）
  - 矿砂 → 冶炼 → 金属锭（先做简单熔炉配方，后续批次替换为多方块工艺）
  - 铁镍合金、铬镍铁、钨铼、镍铼、铂铑、GH4061 六种合金的配方（先占位）
  - 物品标签整理（`ore_item`、`ore_block` 已存在）
- **依赖**：无
- **验收**：生存模式可从矿石得到全部 13 种金属锭与 6 种合金锭

### 批次 1：SE 能量系统

- **目标**：能量单位、存储、传输、FE 双向桥接
- **新增包**：`org.xyccwa.space_simulation.energy`
- **内容**：SE capability、线缆/电网、电池方块、FE 适配器、能量读写的配置项
- **依赖**：批次 0
- **验收**：能量可在方块间传输；FE 与 SE 双向转换正确；配置可调换算比

### 批次 2：矿物处理 I（破碎 → 选矿）

- **目标**：机械处理段
- **内容**：颚式破碎机、球磨机、分级筛、磁选机、浮选池（均为普通单方块或小型多方块）
- **依赖**：批次 1
- **验收**：矿石 → 矿砂 → 矿粉 → 精矿的完整链路可跑通

### 批次 3：矿物处理 II（焙烧 → 浸出 → 萃取）

- **目标**：火法与湿法冶金段
- **内容**：焙烧窑、浸出槽、溶剂萃取塔、尾气回收（SO₂ → 硫酸）
- **依赖**：批次 2
- **验收**：硫化物矿可产出硫/硫酸；**硫酸成为湿法冶金的必需中间体**

### 批次 4：还原、电解与合金

- **目标**：金属提取与合金化
- **内容**：氢还原炉、电解槽、霍尔-埃鲁电解槽、Kroll 反应器、真空感应熔炼炉（VIM）、电子束熔炼炉（EBM）、区域熔炼炉、铸造/轧制
- **依赖**：批次 3
- **验收**：13 种金属锭全部可由工艺路线产出；**铂族通过铜电解阳极泥回收**；6 种合金可产

### 批次 5：水电解与氢氧链

- **目标**：氢氧作为工质与化工中间体
- **内容**：水电解槽、低温液化塔（LOX/LH₂）、储罐、燃料电池组
- **依赖**：批次 4（需要电力与结构材料）
- **验收**：水 → 氢 + 氧 → 液化储存 → 燃料电池发电闭环可跑通

### 批次 6：蓝图与数据核心

- **目标**：解锁系统
- **内容**：数据核心物品/方块、蓝图恢复界面、解锁状态存储、配方可见性联动
- **依赖**：批次 1
- **验收**：未解锁的配方在 JEI/界面中不可见或显示为锁定；消耗数据核心后可解锁

### 批次 7：出生残骸场

- **目标**：T0 的实际内容
- **内容**：6 类舱段结构模板、围绕出生点生成、复用 `AsteroidEntityifier` 的 STAGING + plot 管线、**落盘**
- **依赖**：批次 0；与批次 9 的落盘机制有交集
- **验收**：新世界出生时周围有大碎块；碎块可进入、可落块、离开重进后**保持已被拆改的状态**

### 批次 8：拆解与回收

- **目标**：开局玩法闭环
- **内容**：拆解工具/设备、碎块方块 → 部件与材料、数据核心拾取
- **依赖**：批次 7
- **验收**：不挖矿也能从残骸获得起步材料与首份蓝图

### 批次 9：状态机、子关卡引力与落盘

- **目标**：让物理真正作用在子关卡上
- **内容**：
  - `ForceGroups.GRAVITY` 每物理 tick 对 DRIFT/PILOTED 子关卡施太阳引力（`Gravity.acceleration` × 质量）
  - `Instance` 增加驱动模式字段；NATURAL 保持 teleport，其余停止
  - 切换瞬间的速度继承
  - `SubLevelSchematicSerializationContext` 落盘与重新加载
  - 落盘数量上限与提示
- **依赖**：批次 7（需要先有碎块这个用例）
- **验收**：DRIFT 碎块受太阳引力自然绕日；改造后落盘；重登后位置与状态正确；自然态小行星行为**完全不变**
- **风险**：见 §12 第 1、3 条

### 批次 10：推进系统

- **目标**：引擎与载具动力
- **内容**：引擎方块（`BlockEntitySubLevelActor` + `ForceGroups.PROPULSION`）、储箱、燃料质量动态更新、软限速、反作用轮与 RCS
- **依赖**：批次 5（燃料）、批次 9（引力与状态机）
- **验收**：可建造一艘能自主变轨的飞船；燃料耗尽后不再加速；速度接近 200 m/s 时推力衰减；Δv 与齐奥尔科夫斯基方程吻合

### 批次 11：载具建造

- **目标**：把子系统串成完整载具
- **内容**：飞船装配坞、控制核心多方块、建造 UI、配平（`BlockSubLevelCustomCenterOfMass`）
- **依赖**：批次 10
- **验收**：可组装一艘带引擎、储箱、控制核心的完整飞船并驾驶

### 批次 12+（**暂缓**）

- 裂变能源（修复殖民船反应堆）
- 聚变能源与氦-3 富集
- 反物质与曲率跃迁
- 武器线（§8）
- 敌人体系
- **星际信标**（终局目标）

---

## 12. 风险与前置依赖

| # | 风险 | 说明与建议 |
|---|---|---|
| 1 | **子关卡引力是全局开销** | 每物理 tick 对每个子关卡施力，实体化小行星多时物理管线负载上升。建议只对 DRIFT/PILOTED 施力，并做数量上限 |
| 2 | **存档体积** | `AsteroidEntityifier` 注释明确「不落盘」就是为了避免堆积数万方块的结构副本。整颗落盘会打破这个前提 → 限制同时落盘数量，并考虑压缩 |
| 3 | **重基压力** | 百万格 + 200 m/s 会明显加剧 `RapierOriginManager` 的重基频率（已实测一次重基曾阻塞主线程 0.9–1.1 秒，现为分帧）。**推进系统上线前必须先压稳这层** |
| 4 | **单向不可逆是玩家不可撤销的操作** | 一旦改造就永久成为物理体。上手时必须有明确警告 |
| 5 | **燃料质量动态更新** | `MassTracker` 提供 `addBlockMass` 增量接口，但储箱装燃料不改变方块结构，需要一套「按填充状态提供质量」的机制；这是批次 10 的主要技术难点 |
| 6 | **速度上限的施加** | 软剃减（已定）比硬截断稳定，但仍需每物理 tick 读速度，注意与 Rapier 求解器的交互 |
| 7 | **自然态小行星不得受影响** | 批次 9 的改动必须保证 NATURAL 路径行为与现在完全一致（回归风险最高处） |

---

## 13. 未决项与建议

| 项 | 状态 |
|---|---|
| 蓝图解锁的存储方案（Attachment vs SavedData） | 待批次 6 定 |
| SE 与 FE 的换算比（暂定 1 SE = 4 FE） | 待定，可配置 |
| 落盘子关卡数量上限的具体数值 | 待批次 9 实测后定 |
| 敌人体系与武器线的具体设计 | **暂缓**，待非战斗内容完成后重启 |
| 机器人自身能量/维护 | **由另一模组代劳**，本模组不做 |
| 终端物料平衡表（每种机器具体消耗/产出数值） | 待各批次实现时逐项定 |

---

## 附录 A：现有资产快速索引

```
src/main/java/org/xyccwa/space_simulation/
├── asteroid/entity/AsteroidEntityifier.java   # 实体化管线（STAGING + plot）
├── asteroid/AsteroidUniverse*.java            # 解析开普勒轨道
├── orbital/Gravity.java                       # 太阳引力（块/tick²）
├── orbital/PlayerOrbitServer.java             # 玩家服务器权威积分
├── util/FlightPhysics.java                    # 0.06 / 10.0
├── util/SunRadius.java                        # 50k–100k
├── damage/SolarHeatDamage.java                # 高温区 (R, 1.1R]
├── damage/PlayerAccelerationDamage.java       # 过载（惯性阻尼场复用）
├── config/SpaceSimulationConfig.java          # orbitalMu = 6.25e6 等
├── modItem/SpaceSimulationItem.java           # 20 矿砂 + 13 金属 + 6 合金
├── modBlock/SpaceSimulationBlock.java         # 20 矿石 + dust_block
├── dataGen/SpaceSimulationRecipesProvider.java # 空 → 批次 0
├── attachment/SpaceSimulationAttachments.java # 可复用于蓝图解锁状态
├── rapierfix/RapierOriginManager.java         # 原点重基
└── mixin/rapierfix/RapierPhysicsPipelineMixin.java
```

## 附录 B：构建与验证

```powershell
# 构建（Gradle 用户主目录在 D:\.gradle）
.\gradlew compileJava -g D:\.gradle
.\gradlew processResources -g D:\.gradle
```

- 数据包监听器必须写**静态方法引用**（`Class::method`），lambda 实测不触发
- 游戏内命令输出**不会进日志**，实测时用临时数据包 `minecraft:load` 函数标签 + `LOGGER.info`，测完删除（删除前先 `git status` 确认，**绝不对父目录递归删除**）
- 推送内容只含实际有用的代码/资源，临时测试脚本与调试数据包**不得入库**
