# AFKFarm 1.0.2（NeoForge + Fabric 多版本，PCL 客户端部分验证）

> 1.0.2 是**移植/兼容版本**，核心玩法与 1.0.1 相同：F7 开关 AFK 视角旋转（每 10 秒右转 5°）、使用/放置键收成熟马铃薯/胡萝卜并补种、主手工具破坏、种子切换、break/plant 分 tick、2 tick 后校验、不重试。  
> PCL 客户端验证：1.21.11 NeoForge、26.2 NeoForge、26.2 Fabric 已通过；26.1.2 NeoForge/Fabric、1.21.11 Fabric 未测；Forge 未交付。

## 交付的 6 个 jar

| 加载器 | Minecraft | Java | jar | 验证 |
|---|---|---|---|---|
| NeoForge | 1.21.11 | 21 | `AFKFarm-1.0.2-neoforge-1.21.11.jar` | ✅ PCL |
| NeoForge | 26.1.2（元数据覆盖 26.1–26.1.2） | 25 | `AFKFarm-1.0.2-neoforge-26.1.2.jar` | ⚠️ 未测 |
| NeoForge | 26.2 | 25 | `AFKFarm-1.0.2-neoforge-26.2.jar` | ✅ PCL |
| Fabric | 1.21.11 | 21 | `AFKFarm-1.0.2-fabric-1.21.11.jar` | ⚠️ 未测 |
| Fabric | 26.1.2（元数据覆盖 26.1–26.1.2） | 25 | `AFKFarm-1.0.2-fabric-26.1.2.jar` | ⚠️ 未测 |
| Fabric | 26.2 | 25 | `AFKFarm-1.0.2-fabric-26.2.jar` | ✅ PCL |

**PCL 客户端验证状态（用户实测）：**

- ✅ 1.21.11 NeoForge
- ✅ 26.2 NeoForge
- ✅ 26.2 Fabric
- ⚠️ 26.1.2 NeoForge、26.1.2 Fabric、1.21.11 Fabric：未测
- Forge：未交付

位置：仓库的 `dist/`（本地开发时为 `D:\game\Minecraft\dev\afkfarm-multiloader\dist\`）。  
**没有 Forge jar**；Forge 相关工程未纳入 1.0.2 交付，仓库只包含 NeoForge/Fabric 的 6 个目标。

## 1.0.2 相对 1.0.1 的改动

### 1. 交付矩阵

- 1.0.1：仅 NeoForge 26.2，1 个 jar。
- 1.0.2：NeoForge/Fabric × 1.21.11/26.1.2/26.2，共 6 个 jar。
- 26.1 组只构建 26.1.2，但元数据声明覆盖 `26.1–26.1.2`；1.21.11 和 26.2 各自独立。
- 不是“一个 jar 通吃”；是每个加载器、每个版本组分别编译的 per-loader jar。

### 2. 代码结构

- 抽出共享核心 `ClientHarvest`：AFK 开关、旋转、目标识别、状态机、种子查找、切换、日志。
- 新增 `VersionAdapter`，只封装各版本真正不同的 4 类 API：
  - 界面是否打开
  - 容器槽位交换
  - action bar 消息
  - 聊天消息
- 加载器入口拆成：
  - NeoForge：`NeoForgeEntry`
  - Fabric：`FabricEntry` + `MinecraftUseMixin`
  - Forge：未完成
- 状态机常量/逻辑保持不变：`IDLE -> AWAIT_PLANT -> AWAIT_VERIFY`、`PLANT_DELAY_TICKS=1`、`VERIFY_DELAY_TICKS=2`、`HARVEST_COOLDOWN_TICKS=4`、失败不重试。

### 3. 各版本 API 适配

| 功能 | 1.21.11 | 26.1.2 | 26.2 |
|---|---|---|---|
| 界面是否打开 | `Minecraft.screen` | `Minecraft.screen` | `Minecraft.gui.screen()` |
| 槽位交换 | `handleInventoryMouseClick(..., ClickType.SWAP, ...)` | `handleContainerInput(..., ContainerInput.SWAP, ...)` | 同 26.1.2 |
| action bar | `displayClientMessage(component, true)` | `sendOverlayMessage(component)` | 同 26.1.2 |
| 聊天消息 | `displayClientMessage(component, false)` | `sendSystemMessage(component)` | 同 26.1.2 |
| 破坏方块 | `startDestroyBlock(pos, face)` | 同左 | 同左 |
| 选中槽位 | `Inventory.getSelectedSlot/setSelectedSlot` | 同左 | 同左 |
| 种子搜索 | `Inventory.getItem(0..35)` | 同左 | 同左 |
| 键位分类 | `KeyMapping.Category.MISC` | 同左 | 同左 |

补充：
- 1.0.1 的 26.2 `startDestroyBlock` 修复被推广到 1.21.11/26.1.2；这三个版本都有该方法。
- 1.21.11/26.1.2 没有 `Inventory.getNonEquipmentItems()`，所以 1.0.2 改为遍历 `getItem(0..35)`；结果等价。
- 1.21.11 没有 `Player.sendSystemMessage(Component)`，聊天改用 `displayClientMessage(component, false)`。

### 4. Fabric 特有改动

- 新增 `MinecraftUseMixin`：注入 `Minecraft.startUseItem` HEAD，调用共享 `onUseKeyClicked`；只有命中可收作物时才取消原版 use。等价于 NeoForge 的 `InputEvent.InteractionKeyMappingTriggered`。
- 键位注册：
  - 1.21.11：`KeyBindingHelper.registerKeyBinding`
  - 26.1/26.2：Fabric API 把 keybinding 模块改名为 key-mapping，使用 `KeyMappingHelper.registerKeyMapping`
  - 通过 `FabricKeyRegistration` 反射选择可用 helper，避免为 1.21.11/26.x 各写一份入口。
- 构建：
  - 1.21.11：`fabric-loom-remap` + `officialMojangMappings()`，jar 会 remap 到 intermediary。
  - 26.1.2/26.2：`fabric-loom`（非混淆环境），不使用 Mojang mappings，jar 保持官方名；Java 25。
- 依赖：
  - 1.21.11 编译使用完整 Fabric API `0.141.6+1.21.11`。
  - 26.1.2/26.2 编译只依赖 `fabric-lifecycle-events-v1`（`4.1.1+df84eb3d4c` / `4.1.4+29b6eb019e`）；运行时仍需要完整 Fabric API。
  - `fabric.mod.json` 声明 `fabric-api` 依赖，缺 Fabric API 不会加载。
- Fabric Loader 统一声明 `>=0.19.5`。

### 5. NeoForge 特有改动

- 26.2 的入口类从 1.0.1 的 `AFKFarm` 改为共享 `NeoForgeEntry`；键位/事件逻辑不变。
- 1.21.11、26.1.2、26.2 使用同一套 `RegisterKeyMappingsEvent` / `ClientTickEvent.Post` / `InputEvent.InteractionKeyMappingTriggered` 注册方式。
- 26.2 NeoForge 1.0.2 已由用户用 PCL 客户端验证通过；与 1.0.1 的区别主要是日志格式（见下）。

### 6. 日志格式变化

`AFK_FARM_TICK` 从：

```text
AFK_FARM_TICK keyDown=... screen=... overlay=... paused=...
```

改为：

```text
AFK_FARM_TICK keyDown=... screenOpen=... paused=...
```

原因：`overlay` 在 1.21.11/26.1.2/26.2 的访问方式不同（`Minecraft.getOverlay` vs `Gui.overlay`），而它只用于日志；为避免版本适配，1.0.2 删除了该字段。  
`AFK_PHASE`、`AFK_PLANT_LOST`、`AFK_FARM_TARGET` 等日志保持不变。

### 7. 元数据/资源包格式

- mod 版本：`1.0.2`
- NeoForge：
  - 1.21.11：MC `[1.21.11,1.21.12)`，NeoForge `[21.11.48,)`，FML `[10,)`
  - 26.1.2：MC `[26.1,26.2)`，NeoForge `[26.1.0,)`，FML `[11,)`
  - 26.2：MC `[26.2,26.3)`，NeoForge `[26.2.0,)`，FML `[11,)`
- Fabric：
  - 1.21.11：MC `>=1.21.11 <1.21.12`，Java `>=21`
  - 26.1.2：MC `>=26.1 <26.2`，Java `>=25`
  - 26.2：MC `>=26.2 <26.3`，Java `>=25`
- 资源包格式：1.21.11 `75`，26.1.2 `84`，26.2 `88`。

### 8. 验证状态

- **已验证**：用户用 PCL 正常启动并测试了 1.21.11 NeoForge、26.2 NeoForge、26.2 Fabric。
- **未测**：26.1.2 NeoForge、26.1.2 Fabric、1.21.11 Fabric（没有验证记录）。
- **未做**：完整功能回归、专用服务器测试、Forge 测试。
- **Forge 未构建**：没有 Forge 1.0.2 jar；相关工程未完成。
- 26.1.2 NeoForge/Fabric、1.21.11 Fabric 仍是未测路径；Fabric 26.x 反射键位注册已在 26.2 Fabric 通过。
- 26.2 NeoForge 1.0.2 已 PCL 客户端验证；1.0.1 的旧日志仍可作为行为对照。

## 测试状态与交接

### NeoForge

已有实例：
- `1.21.11-NeoForge_21.11.48`（Java 21）
- `26.1.2-NeoForge_26.1.2.122`（Java 25）
- `26.2-NeoForge_26.2.0.88`（Java 25）

把对应 jar 放进实例 `mods`：
- 26.2：若还保留 `AFKFarm-1.0.1-neoforge-26.2.jar`，先移除/备份；当前测试环境已只保留 1.0.2。
- 1.21.11 / 26.1.2：直接放入对应 1.0.2 jar。
- 服务端保持原版，不需要装本 mod。

### Fabric

需要 Fabric Loader `0.19.5`（或更新）和对应 Fabric API：
- 1.21.11：Fabric API `0.141.6+1.21.11`，Java 21
- 26.1.2：Fabric API `0.155.3+26.1.2`，Java 25
- 26.2：Fabric API `0.161.0+26.2`，Java 25

把对应 1.0.2 Fabric jar 和 Fabric API 放进 `mods`。  
26.x Fabric 是**非混淆环境**，不要额外装 mappings；1.21.11 是 remap 构建。

### 重点观察

- F7 是否注册成功（1.21.11 走 `KeyBindingHelper`，26.x 走 `KeyMappingHelper` 反射）。
- 单次点击使用键是否只触发一次收割，且不放置手持方块。
- 按住使用键是否按 4 tick 冷却重复。
- 胡萝卜/马铃薯成熟时：break、下一 tick plant、2 tick 后 verify；失败是否只记录 `AFK_PLANT_LOST` 且不重试。
- 种子切换后工具是否回到原选中槽。
- AFK 开启后是否每 10 秒右转 5°。
- `AFK_FARM_TICK` 现在只有 `screenOpen`，没有 `overlay`。

## 构建入口

- NeoForge：
  ```powershell
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File D:\game\Minecraft\dev\afkfarm-multiloader\build-neoforge.ps1 -Target 26.2
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File D:\game\Minecraft\dev\afkfarm-multiloader\build-neoforge.ps1 -Target 26.1
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File D:\game\Minecraft\dev\afkfarm-multiloader\build-neoforge.ps1 -Target 1.21.11
  ```
- Fabric：
  - 工作目录：`projects/fabric-1.21.11`、`fabric-26.1`、`fabric-26.2`
  - JDK 25 运行 Gradle；1.21.11 通过 toolchain 21 编译。
  - `gradle build --no-daemon`
- Forge：未完成，不构建。
