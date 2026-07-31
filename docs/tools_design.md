# 生活计算工具栏 —— 整体方案设计（待确认）

> 目标：为 LineageOS 计算器（`com.android.calculator2`，基于 ExactCalculator）
> 新增"生活计算"功能（单位换算 / 汇率 / 房贷 / 个税 / BMI / 日期），
> 交互参考输入法工具栏：在【结果显示区】与【数字键盘区】之间新增一条**常驻工具栏**，
> 就地切换；点"更多"展开一个**覆盖键盘区**的面板，选中后自动收起。
>
> 本文档只做设计，不含可编译代码。确认后再分阶段产出代码。

### 决策确认（已与需求方对齐）
| 项 | 决策 |
|---|---|
| 新增代码语言 | **Java**（与现有 `com.android.calculator2` 全 Java 一致） |
| 覆盖式展开实现 | **FrameLayout overlay（根 MotionLayout 最后一个子 View）+ 属性动画**；不动现有 MotionScene |
| 工具栏排序 | **按使用频率**（SharedPreferences 计数，前 N 高频上工具栏） |
| 汇率数据源 | **参考 `com.yangdai.calc`（YangDai2003/Multi-Calculator-Android）同款**：欧洲央行 ECB 每日参考汇率 XML；并在其基础上补齐缓存/离线降级/更新时间标注 |
| 目标版本 | minSdk 31 / targetSdk 35 / compileSdk 35（取自 build.gradle.kts） |

---

## 0. 现状速读（决定方案的关键事实）

- **主布局** `res/layout/activity_calculator.xml`：根是自定义 `DisplayMotionLayout`
  （继承 `MotionLayout`），子 View 用 ConstraintLayout 约束 + **百分比高度**
  （`res/values/ratios.xml`）三段排列：
  `display(0.30) → advanced_pad(0.20) → input_pad(0.50)`，另含离屏的
  `history_frame`（下拉历史用）与 `animation_helper`。
- **MotionLayout 用途**：目前只用于"下拉显示历史"（`OnSwipe` 把 `history_frame`
  从屏幕上方拉下来，`display/pads` 下移）。`start_state` 为空，`end_state` 重新约束
  display/advanced/input/history。
- **显示区** `display_two_line.xml` / `display_one_line.xml`：`CalculatorDisplay`
  内含 `toolbar`（AppCompat Toolbar + mode 文本）、`formula_scroll_view`+
  `CalculatorFormula`（公式）、`CalculatorResult`（结果）、`drag_handle`。
- **键盘**：`input_pad.xml`（数字 + 运算符）、`advanced_pad.xml`（科学函数）。
  所有按键 `android:onClick="onButtonClick"`，统一在 `Calculator.onButtonClick(View)` 路由。
  按键风格 `PadButtonStyle`（`HapticButton`，MD3 Expressive），主题 `Theme.Button.*`。
- **引擎**：`Evaluator`（单例）/`CalculatorExpr`/`BoundedRational`/`UnifiedReal` ——
  **任意精度核心，本次绝不修改**。读取当前值可用：
  - 结果文本：`CalculatorResult.getFullText(false)`（无效时返回 `""`）。
  - 公式文本：`mFormulaText.getText()` 或 `Evaluator.getExpr(MAIN_INDEX)`。
- **主题**：`Theme.Material3Expressive.DayNight` + 动态取色（DynamicColors）。
  已引入 `material:1.14.0-alpha09`（`Chip`/`ChipGroup` 可用）、`gridlayout`、MotionLayout。
- **Android 版本**：`minSdk=31, targetSdk=35, compileSdk=35`；Java 17（项目以 Java 为主，
  含 Kotlin 插件但源码均为 `.java`）。**本次新增代码统一用 Java**，与现有风格一致。
- **无测试目录**：需新增 `src/test`（纯 JVM JUnit4）并在 `build.gradle.kts` 加测试源集与依赖。
- **包名**：`com.android.calculator2`；新增功能统一放在子包 `com.android.calculator2.tools`。

---

## 1. 总体架构

### 1.1 设计原则
1. **引擎零侵入**：不碰 `CalculatorExpr/Evaluator/BoundedRational/UnifiedReal`。
   新功能并行接入；计算器原模式只是 `ToolMode` 之一（`CalculatorMode`，透传原逻辑）。
2. **可插拔**：每个工具实现统一 `ToolMode` 接口，向 `ToolManager` 注册；
   新增工具不改主界面 / 主布局。
3. **数据驱动**：单位系数 / 汇率配置 / 税率表全部外置（`assets/tools/*.json`）。
4. **复用**：工具模式复用显示区（formula/result）展示输入/输出，复用数字键盘输入数字，
   复用 MD3 主题/按钮风格/深色模式。

### 1.2 模块划分与目录树（新增）
```
src/com/android/calculator2/
├── Calculator.java                 （改：onCreate 挂载工具栏；onButtonClick 路由；onBackPressed 收面板）
│
└── tools/                          （新增子包，全部新功能）
    ├── ToolMode.java               （工具统一接口）
    ├── ToolManager.java            （注册/激活/切换/频率统计/数值带入）
    ├── ToolHost.java               （工具访问宿主显示区与输入的能力）
    ├── ToolId.java                 （工具 id 常量）
    │
    ├── ui/
    │   ├── ToolBarView.java        （收起态：横向 chips + "更多"按钮 + 状态指示）
    │   ├── ToolPanelOverlay.java   （展开态：覆盖键盘区的网格面板 + 动画）
    │   └── ToolControlHost.java    （显示区内"工具控制行"容器，承载各工具的选择器）
    │
    ├── model/                      （纯逻辑，BigDecimal，可单测，不依赖 Android UI）
    │   ├── UnitConversion.java         （线性换算 value*factor）
    │   ├── TemperatureConversion.java  （非线性：仿射变换 K=C+273.15 等）
    │   ├── MortgageCalculator.java     （等额本息 / 等额本金）
    │   ├── IncomeTaxCalculator.java    （累计预扣法，税率表驱动）
    │   ├── BmiCalculator.java
    │   └── DateDelta.java
    │
    ├── data/                       （数据驱动 + 联网缓存）
    │   ├── UnitRepository.java         （读 assets/tools/units.json）
    │   ├── ExchangeRateConfig.java     （读 assets/tools/exchange_rate_config.json）
    │   ├── ExchangeRateRepository.java （ECB XML 联网+缓存+离线降级，IO 线程）
    │   ├── RateCache.java              （本地缓存，标注更新时间）
    │   └── EcbRateParser.java          （XmlPullParser 解析 ECB Cube 节点，可单测）
    │   └── TaxTable.java               （读 assets/tools/tax_cn.json）
    │
    └── modes/                      （各 ToolMode 实现）
        ├── CalculatorMode.java         （默认：透传到原 Evaluator，"更多"里不出现）
        ├── UnitConverterMode.java
        ├── CurrencyConverterMode.java
        ├── MortgageMode.java
        ├── TaxMode.java
        ├── BmiMode.java
        └── DateMode.java

src/test/java/com/android/calculator2/tools/model/   （新增 JUnit4）
├── TemperatureConversionTest.java
├── UnitConversionTest.java
├── MortgageCalculatorTest.java
├── IncomeTaxCalculatorTest.java
└── ExchangeRateParserTest.java

assets/tools/                       （新增，数据驱动配置）
├── units.json
├── exchange_rate_config.json
└── tax_cn.json

res/
├── layout/        activity_calculator.xml(改) display_two_line.xml(改) display_one_line.xml(改)
│                  tool_bar.xml(新) tool_panel_overlay.xml(新) tool_control_*(新)
├── layout-land/   activity_calculator.xml(改) tool_bar.xml(新, 横屏版)
├── values/        ratios.xml(改) dimens.xml(改) strings.xml(改) attr.xml(新 attr)
│                  themes.xml/styles.xml(新增 chip / 工具样式)
├── xml/           activity_calculator_scene.xml(改：把 tool_bar 纳入约束集)
└── drawable/      ic_tool_more.xml(新) 各工具图标(新) panel_background(新)
```

---

## 2. 主布局改造（核心）

### 2.1 竖屏：四段式（display / tool_bar / advanced_pad / input_pad）+ 覆盖层
新增 `tool_bar` 插在 `display` 与 `advanced_pad` 之间；新增 `tool_panel_overlay`
作为根 MotionLayout 的**最后一个子 View**（Z 序最高，覆盖 pads 区域）。

**比例重排（`res/values/ratios.xml`）**
```
display            0.30   （不变）
tool_bar           0.08   （新增，常驻条）
advanced_pad       0.17   （原 0.20，略缩）
input_pad          0.45   （原 0.50，略缩）
─────────────────────────
合计               1.00
overlay 覆盖区 = advanced_pad + input_pad = 0.62
```

**竖屏文字示意图**
```
收起态（默认 / 某工具已选中）          展开态（点"更多"后）
┌───────────────────────────┐         ┌───────────────────────────┐
│  [toolbar] mode  DEG ▾    │         │  [toolbar] mode  DEG ▾    │
│                           │         │                           │
│        结果显示区          │         │        结果显示区          │
│   formula (输入/公式)      │         │   formula (输入/公式)      │
│   result  (输出/结果)      │         │   result  (输出/结果)      │
├───────────────────────────┤         ├───────────────────────────┤
│ 工具栏(常驻,可横滑)        │         │ 工具栏(常驻)     [更多 ▲]│ ← 按钮变收起态
│ [单位][汇率][长度]… [更多▾]│         ├───────────────────────────┤
├───────────────────────────┤         │ ┌─ 覆盖面板(overlay) ─────┐│
│  [工具控制行]              │         │ │ 全部工具（网格 3~4 列）  ││
│  from ▾  ⇄  to ▾          │         │ │ 单位 汇率 长度 重量 温度 ││
├───────────────────────────┤         │ │ 体积 时间 速度 数据 角度 ││
│  advanced_pad(科学键)      │         │ │ 房贷  个税  BMI  日期 …  ││
├───────────────────────────┤  ===▶   │ │   (点击任一项)            ││
│  input_pad(数字键盘)       │         │ └─────────────────────────┘│
│  7 8 9 ÷                   │         │  （键盘被覆盖，不顶出屏幕） │
│  ...                       │         │                           │
└───────────────────────────┘         └───────────────────────────┘
   ↑ overlay GONE,不拦截触摸              ↑ overlay 从工具栏下沿向下滑入，
     键盘正常可用                          半透明遮罩+卡片，选中即收起
```

### 2.2 横屏：display / tool_bar / (advanced_pad | input_pad) + 覆盖层
AOSP 横屏是 display 在上、advanced 与 input **左右并排**（竖直 guideline 分隔）。
方案：tool_bar 作为**整宽横条**放在 display 与 pads 之间（与竖屏概念一致，便于复用同一
`ToolBarView`），overlay 覆盖整个 pads 并排区。

**横屏比例（新增 `res/values-land/ratios.xml`）**
```
display   0.28
tool_bar  0.10   （横屏高度紧张，条略高以放下 chips）
pads 区   0.62   （advanced | input 左右并排，原 guideline 复用）
```

**横屏文字示意图**
```
收起态                                  展开态
┌─────────────────────────────────────┐  ┌─────────────────────────────────────┐
│          结果显示区 (display)         │  │          结果显示区 (display)         │
├─────────────────────────────────────┤  ├─────────────────────────────────────┤
│ 工具栏 [单位][汇率][长度]…   [更多▾] │  │ 工具栏 …                    [更多 ▲]│
├──────────────────┬──────────────────┤  ├──────────────────────────────────────┤
│ advanced_pad     │ input_pad        │  │ ┌─ 覆盖面板(overlay，整宽网格) ──────┐ │
│ 科学函数         │ 数字键盘         │  │ │ 单位 汇率 长度 重量 温度 体积 ...  │ │
│ sin cos tan ...  │ 7 8 9 ÷          │  │ │ 房贷 个税 BMI 日期 ...             │ │
│                  │ 4 5 6 ×          │  │ │  (横屏列数更多，4~6 列)             │ │
│                  │ ...              │  │ └────────────────────────────────────┘ │
└──────────────────┴──────────────────┘  └──────────────────────────────────────┘
```

### 2.3 显示区改造（`display_two_line.xml` / `display_one_line.xml`）
在 `formula`/`result` 之上、`toolbar` 之下，新增一行 **`tool_control_slot`**
（FrameLayout 容器，默认 `GONE`）。激活某工具时，`ToolManager` 把该工具的"控制视图"
（如单位/币种选择器）inflate 进该 slot 并显示；切回计算器模式时清空并 `GONE`。
这样**各工具的特有控件就地挂在显示区里**，显示区的 formula/result 仍由工具驱动展示数值。

---

## 3. 覆盖式展开动画 —— 方案选型与理由

### 候选与取舍
| 方案 | 评估 | 取舍 |
|---|---|---|
| **A. FrameLayout 层叠 + 属性动画**（overlay 作为根最后一个子 View） | overlay 本就 Z 序最高、天然覆盖 pads；translationY/alpha 动画完全可控；不与现有 MotionScene 冲突；实现量小 | **✅ 采用** |
| B. 复用根 MotionLayout 再加一个 Transition | 根 MotionLayout 已被"下拉历史"的 `OnSwipe` 占用，再加状态机/触发器极易互相干扰，且 MotionScene 维护成本高 | ❌ |
| C. BottomSheet | 锚点在屏幕底部、从底往上吸，与"从工具栏下沿向下展开、覆盖键盘"的语义不符，且与 edge-to-edge 导航栏内边距处理别扭 | ❌ |

### 采用方案（A）细节
- `tool_panel_overlay`（`FrameLayout`/`MaterialCardView`）作为根 MotionLayout **最后一个子 View**：
  - 约束：`top_toBottomOf=@id/tool_bar`，`bottom_toBottomOf=parent`，左右贴边
    → 它天然盖住 advanced_pad + input_pad（竖屏）/ 整个 pads 区（横屏）。
  - 初始：`visibility=GONE`，`translationY=+overlayHeight`（推到约束位置下方、屏外），
    `alpha=0`，`scaleY` 略小（0.96）以增强"展开"感。
- 展开：`setVisibility(VISIBLE)` → `ValueAnimator`/`ObjectAnimator` 同时驱动
  `translationY → 0`、`alpha 0→1`、`scaleY 0.96→1`，时长 `@android:integer/config_mediumAnimTime`
  （≈300ms），插值器 `FastOutSlowIn`（MD3 standard easing）。
- 收起：反向动画，`onEnd` 置 `GONE`。
- **"更多"按钮状态指示**：展开时其图标 `rotation 0→180`（▾ 变 ▲）或换图标 +
  `chip` 选中态背景；用 `animate().rotationBy(180f)`。
- **触摸拦截**：overlay `VISIBLE` 时自身消费触摸（默认即拦截其区域内事件），
  避免误触被覆盖的键盘；点击空白或系统返回键即收起（`onBackPressed` 路由）。
- **与历史手势解耦**：overlay 只在工具模式相关交互出现；进入历史 MotionScene
  过渡前确保 overlay 已收起（`GONE`），避免双重动画。

---

## 4. ToolMode / ToolManager / ToolHost 抽象

### 4.1 `ToolMode` 接口（统一契约）
```java
public interface ToolMode {
    /** 稳定 id，用于持久化选中态/频率统计（见 ToolId）。 */
    @NonNull String getId();
    /** 显示名（res id，走 strings.xml 多语言）。 */
    @StringRes int getNameRes();
    /** 图标（res id）。 */
    @DrawableRes int getIconRes();
    /** 是否需要联网（仅汇率返回 true，用于权限/设置开关判断）。 */
    boolean isOnline();

    /** 进入该模式：把控制视图挂入 controlSlot，可消费带入值 carryValue（可能为 null）。 */
    void onActivate(@NonNull ToolHost host, @Nullable String carryValue);
    /** 离开该模式：清理 controlSlot、取消请求。 */
    void onDeactivate(@NonNull ToolHost host);

    /**
     * 处理数字键盘输入（工具模式激活时，Calculator.onButtonClick 把
     * 数字/小数点/删除/清空 路由到此；运算符/等号在工具模式下隐藏或忽略）。
     */
    void onDigit(int digit);
    void onDecimalPoint();
    void onDelete();
    void onClear();
}
```

### 4.2 `ToolHost`（工具访问宿主的能力，由 Calculator 实现）
```java
public interface ToolHost {
    @NonNull Context getContext();
    /** 设置显示区"公式行"文本（工具的输入值展示）。 */
    void setToolFormula(@NonNull CharSequence text);
    /** 设置显示区"结果行"文本（工具的输出展示）。 */
    void setToolResult(@NonNull CharSequence text);
    /** 取当前显示的可带入数值（结果优先，回退公式），解析失败返回 null。 */
    @Nullable String getCurrentDisplayNumber();
    @NonNull Evaluator getEvaluator();   // 只读引用，工具不修改主表达式
    /** 工具内联网/异步回调切回主线程用。 */
    void runOnUiThread(@NonNull Runnable r);
}
```
> 说明：`setToolFormula/setToolResult` 直接写 `mFormulaText/mResultText` 的文本，
> **不经过 Evaluator**，因此不碰引擎；切回计算器模式时恢复 Evaluator 驱动的正常显示。

### 4.3 `ToolManager`（注册/切换/频率/带入）
```java
public class ToolManager {
    void register(@NonNull ToolMode mode);            // 启动时注册全部内置工具
    @NonNull List<ToolMode> getAll();                 // overlay 网格用
    @NonNull List<ToolMode> getPinned();              // 工具栏 chips 用（按频率排序，见 §6.2）
    @Nullable ToolMode getActive();
    boolean isActive();                               // 是否处于工具模式（非 CalculatorMode）

    /** 切换：读带入值 → onDeactivate(旧) → onActivate(新, carry) → 更新选中态/频率 → 刷新工具栏。 */
    void activate(@NonNull String id);
    void backToCalculator();                          // 回到 CalculatorMode，恢复原显示

    void handlePadClick(int viewId);                  // 工具模式下数字键盘路由入口
    boolean isPanelExpanded();
    void togglePanel();                               // 展开/收起 overlay
}
```

---

## 5. 各工具模块设计（要点）

| 工具 | 输入 | 输出 | 关键逻辑（model/，可单测） | 备注 |
|---|---|---|---|---|
| 单位换算 | 数字 + from/to 单位 | 换算值 | `UnitConversion`（线性 `v*factor`）；`TemperatureConversion`（仿射，单独处理） | 离线，数据驱动 `units.json` |
| 汇率换算 | 金额 + from/to 币种 | 换算金额 + 更新时间 | `ExchangeRateRepository`（ECB XML，IO 线程 + `RateCache` + 离线降级）+ `EcbRateParser` + `convert()` | 联网；ECB XML；基准 EUR 交叉汇率；`INTERNET` |
| 房贷 | 贷款额/利率/期数/方式 | 月供/总利息 | `MortgageCalculator`（等额本息 / 等额本金，`BigDecimal`） | 离线 |
| 个税 | 税前/专项扣除/累计 | 应纳税额/税后 | `IncomeTaxCalculator`（累计预扣法，`tax_cn.json` 税率表驱动） | 离线，税率可配置 |
| BMI | 身高/体重 | BMI 值 + 等级 | `BmiCalculator` | 离线 |
| 日期 | 起止日期 | 相隔天数 | `DateDelta`（`java.time`） | 离线 |

**显示区复用约定**：工具模式下，`formula` 行显示"输入值（带单位/币种）"，
`result` 行显示"换算/计算结果"；工具特有控件（单位/币种下拉、方式切换）放在
显示区的 `tool_control_slot`。数字键盘继续用于输入数字（隐藏运算符/等号键或忽略其点击）。

---

## 6. 体验增强

### 6.1 数值带入策略（模式切换时把结果区数值带入新工具）
1. 切换前调用 `host.getCurrentDisplayNumber()`：
   - 优先取 `CalculatorResult.getFullText(false)`（结果态的数值）；
   - 为空则回退解析 `mFormulaText.getText()`（输入态的纯数字）；
   - 经 `KeyMaps.translateResult` 反向规整（如全角/特殊减号），去千分位，`new BigDecimal` 解析。
2. 解析成功 → 作为 `carryValue` 传给新工具的 `onActivate`，工具把它作为初始输入金额/数值。
3. 解析失败或为空 → `carryValue=null`，工具用默认值（如 0 或上次值）。
4. **边界**：仅当目标是"接收一个数值"的工具（汇率/单位/BMI/房贷本金等）才带入；
   日期/个税等多参数工具忽略带入值。

### 6.2 工具栏排序（按使用频率，二选一其一）
- `ToolManager` 在 `SharedPreferences` 记录每个工具被 `activate` 的次数。
- `getPinned()` 返回"前 N 个最高频 + 首次运行的默认集合"，作为工具栏 chips 顺序。
- 频率相同时按内置默认序稳定排序。N 可配置（dimen/资源，默认 5）。
- （扩展位：如需用户自定义顺序，可后续在设置页提供拖拽编辑，写入 prefs 覆盖频率序。）

---

## 7. 数据驱动配置结构样例（`assets/tools/*.json`）

### 7.1 单位表 `units.json`（线性 + 温度非线性）
```json
{
  "categories": [
    {
      "id": "length", "name": "@string/tool_units_length",
      "base": "m",
      "units": [
        { "id": "m",  "name": "@string/unit_m",  "factor": 1.0 },
        { "id": "km", "name": "@string/unit_km", "factor": 1000.0 },
        { "id": "cm", "name": "@string/unit_cm", "factor": 0.01 },
        { "id": "in", "name": "@string/unit_in", "factor": 0.0254 },
        { "id": "ft", "name": "@string/unit_ft", "factor": 0.3048 },
        { "id": "mile","name":"@string/unit_mile","factor": 1609.344 }
      ]
    },
    {
      "id": "temperature", "name": "@string/tool_units_temp",
      "base": "C",
      "kind": "affine",
      "units": [
        { "id": "C", "name": "@string/unit_c", "toBase": "v",            "fromBase": "v" },
        { "id": "F", "name": "@string/unit_f", "toBase": "(v-32)*5/9",   "fromBase": "v*9/5+32" },
        { "id": "K", "name": "@string/unit_k", "toBase": "v-273.15",     "fromBase": "v+273.15" }
      ]
    }
  ]
}
```
> 解析：线性类用 `factor`（`toBase=v*factor`, `fromBase=v/factor`，在 `UnitConversion` 内统一）；
> 温度等非线性用 `toBase/fromBase` 表达式，`TemperatureConversion` 内置 C/F/K 仿射实现
> （表达式仅作配置可读性，实际硬编码保证精度与可测）。`@string/xxx` 占位由仓库按 locale 解析。

### 7.2 汇率配置 `exchange_rate_config.json`（参考 `com.yangdai.calc`，ECB XML）
> 数据源同款：欧洲央行 ECB 每日参考汇率，XML，基准 EUR（EUR=1.0）。
> 节点形如 `<Cube currency="USD" rate="1.0823"/>`（`Cube` 且属性数=2）。
> 换算公式（交叉汇率，以 EUR 为桥）：`结果 = (金额 / fromRate) * toRate`。
> `com.yangdai.calc` 原版仅联网、无缓存；本设计在其基础上补齐**磁盘缓存 + 离线降级 + 更新时间标注**。
```json
{
  "source": "ecb_daily_xml",
  "api_url": "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml",
  "base_currency": "EUR",
  "parser": {
    "type": "ecb_cube",
    "tag": "Cube",
    "attr_currency": "currency",
    "attr_rate": "rate",
    "expect_attr_count": 2,
    "base_self_rate": 1.0
  },
  "publication": {
    "timezone": "CET",
    "publish_hour": 15,
    "note": "CET ≥ publish_hour 视为当天已发布，否则取前一发布日"
  },
  "currencies": [
    { "id": "CNY", "name": "@string/cur_cny", "symbol": "¥" },
    { "id": "USD", "name": "@string/cur_usd", "symbol": "$" },
    { "id": "EUR", "name": "@string/cur_eur", "symbol": "€" },
    { "id": "JPY", "name": "@string/cur_jpy", "symbol": "¥" },
    { "id": "GBP", "name": "@string/cur_gbp", "symbol": "£" }
  ],
  "cache_ttl_minutes": 60,
  "fallback_rates": { "base_currency": "EUR", "USD": 1.08, "CNY": 7.85, "JPY": 165.0, "GBP": 0.85 }
}
```
> `ExchangeRateRepository`（IO 单线程）：`HttpURLConnection` GET → `EcbRateParser`
> （`XmlPullParser` 取 `Cube[currency,rate]`，置 base=1.0）→ 写 `RateCache`（文件，
> 含发布时间戳）→ 失败/无网读缓存 → 再失败用 `fallback_rates`；UI 始终显示"更新时间"
> （按 `publication` 规则换算发布日）。换算走 `model/` 纯函数 `convert(amount, fromRate, toRate)`。

### 7.3 个税税率表 `tax_cn.json`（累计预扣法，可配置）
```json
{
  "id": "cn_iit_2024",
  "brackets": [
    { "up_to": 36000,     "rate": 0.03, "quick_deduction": 0 },
    { "up_to": 144000,    "rate": 0.10, "quick_deduction": 2520 },
    { "up_to": 300000,    "rate": 0.20, "quick_deduction": 16920 },
    { "up_to": 420000,    "rate": 0.25, "quick_deduction": 31920 },
    { "up_to": 660000,    "rate": 0.30, "quick_deduction": 52920 },
    { "up_to": 960000,    "rate": 0.35, "quick_deduction": 85920 },
    { "up_to": 99999999,  "rate": 0.45, "quick_deduction": 181920 }
  ],
  "monthly_threshold": 5000.0,
  "note": "@string/tax_note_update_brackets"
}
```
> `IncomeTaxCalculator` 按"累计预扣预缴"计算：累计预扣预缴税额 = (累计预扣预缴应纳税所得额 ×
> 税率 − 速算扣除数)；用 `BigDecimal`，可单测。

---

## 8. 横竖屏适配思路（汇总）
- 竖屏：tool_bar 在 display 与 advanced_pad 之间；overlay 覆盖 advanced+input。
- 横屏：tool_bar 为整宽横条在 display 与 pads 并排区之间；overlay 覆盖整宽 pads 区，
  网格列数更多（`res/values-land/` 用更大 `spanCount`）。
- 比例分别放 `values/ratios.xml` 与 `values-land/ratios.xml`；
  `ToolBarView`/`ToolPanelOverlay` 内部尺寸走 dimens，横竖屏各自覆盖。
- MotionScene（`xml/` 与 `xml-land/`）需把 `tool_bar` 纳入 `end_state` 约束集，
  使"下拉历史"过渡时 tool_bar 随 pads 协同移动；overlay 在过渡前确保 `GONE`。
- edge-to-edge：根已对 systemBars 做 padding，overlay 贴 parent 边即可，无需额外处理。

---

## 9. 引擎隔离与单元测试
- **隔离**：所有工具计算走 `tools/model/`（`BigDecimal` + `java.time`），**不调用**
  `Evaluator/CalculatorExpr/BoundedRational` 的写入方法；`ToolHost.getEvaluator()` 仅暴露只读引用。
- **测试源集**（`build.gradle.kts` 增加）：
  ```kotlin
  sourceSets { getByName("test") { java.srcDirs("src/test") } }
  dependencies { testImplementation("junit:junit:4.13.2") }
  ```
- **用例**（纯 JVM）：
  - `TemperatureConversionTest`：C↔F↔K 往返、0/100/绝对零度、精度边界。
  - `UnitConversionTest`：长度/重量线性换算、base 往返一致性。
  - `MortgageCalculatorTest`：等额本息月供公式、总利息；等额本金首/末月。
  - `IncomeTaxCalculatorTest`：跨档累计、速算扣除、刚好等于阈值。
  - `ExchangeRateParserTest`：用 ECB 样例 XML 字符串解析（`EcbRateParser`）、
    验证 base=1.0、缺属性 Cube 被跳过、异常输入降级；另测 `convert(amount,from,to)` 交叉汇率。

---

## 10. 资源 / 主题 / 深色模式规范（对齐现有）
- strings 全部进 `res/values/strings.xml`（带 `<!-- [CHAR_LIMIT=...] -->` 注释），
  中文放 `values-zh-rCN/`，沿用现有 key 前缀风格（`tool_*` / `unit_*` / `cur_*`）。
- 工具栏 chips 用 MD3 `Chip`（`Widget.Material3.Chip`/`AssistChip`/`FilterChip`），
  选中态走 `?colorSecondaryContainer`；"更多"按钮单列样式。
- overlay 卡片背景 `?colorSurfaceContainerHigh`（MD3 attr），圆角对齐 `display_corner_radius`。
- 新增 attr（`res/values/attr.xml`）与深色支持：颜色用 MD3 动态 attr，避免硬编码；
  仅工具特有色（如汇率在线指示）补 `values-night`。
- 图标用矢量 drawable（`@string` 标注 contentDescription），与现有 `ic_del` 风格一致。

---

## 11. 需手动处理的项（确认方案后我会在代码阶段标注 TODO）
1. **Manifest**：新增 `<uses-permission android:name="android.permission.INTERNET"/>`
   （仅汇率用）。ECB 接口为 **https**，无需 `networkSecurityConfig` cleartext 例外。
2. **汇率数据源**：已确认采用 ECB 每日参考汇率 XML（`com.yangdai.calc` 同款）。
   `fallback_rates` 为离线兜底，币种集可按需在 `exchange_rate_config.json` 增减。
3. **布局挂载点确认**：tool_bar 与 overlay 插入根 MotionLayout；显示区 `tool_control_slot`
   插入 `display_two_line.xml` / `display_one_line.xml`。
4. **设置开关**：设置菜单（现仅 Licenses）需新增"汇率自动更新"开关项（prefs），
   我提供 prefs 读写，菜单项挂载由你确认位置（或我一并在 `activity_calculator.xml` 菜单加）。
5. **税率/汇率更新**：税率表会随政策变化，数据外置后由你维护 `assets/tools/tax_cn.json`。

---

## 12. 分阶段交付计划（确认后执行）
- **阶段 1**：主布局改造（竖屏四段 + overlay 挂载点）+ `ToolBarView`/`ToolPanelOverlay`
  + 覆盖展开动画 + `ToolMode/ToolManager/ToolHost` 骨架 + `CalculatorMode` 透传。
- **阶段 2**：单位换算（含温度非线性）+ `units.json` + `model/` + 单测。
- **阶段 3**：汇率换算（ECB XML 联网 + `EcbRateParser` + `RateCache` + 离线降级 + 更新时间）+
  `exchange_rate_config.json` + 解析单测 + 设置开关（汇率自动更新）。
- **阶段 4**：房贷 / 个税 / BMI / 日期 + `tax_cn.json` + 单测 + 数值带入/频率排序完善。
- **阶段 5**：横屏适配（`layout-land` / `values-land` / `xml-land`）+ 深色/资源规整 + 联调。

---
*文档版本：v1（设计稿，待确认）*

---

## 13. 实现进度

- [x] **阶段 1（已完成）**：主布局四段化（竖屏 display/tool_bar/advanced/input + overlay；
      横屏 tool_bar 整宽 + overlay 覆盖并排 pads）；`ToolBarView`（chips + 旋转态"更多"）；
      `ToolPanelOverlay`（覆盖式展开/收起，translationY+alpha，`fast_out_slow_in`）；
      `ToolMode`/`ToolHost`/`ToolManager` 抽象 + 频率排序 + 数值带入 + 显示区交接；
      `CalculatorMode` 透传；6 个工具以 `ComingSoonMode` 占位（打通选择/切换/带回/输入路由）。
      引擎零改动；MotionScene 已把 `tool_bar` 纳入历史过渡约束链。
- [x] **阶段 2（已完成）**：单位换算。`model/UnitConversion`（线性，base 因子）、
      `model/TemperatureConversion`（C/F/K 仿射）；`data/{UnitDef,UnitCategory,UnitTable,UnitRepository}`
      + `assets/tools/units.json`（长度/面积/体积/重量/温度，系数字符串保精度，`@string` 分类名本地化）；
      `modes/UnitConverterMode` 复用显示区（formula=输入值+单位，result=结果+单位），控制行挂载
      `tool_control_slot`（类别/来源/目标 Spinner + 交换）；`ToolHost` 增加 `getToolControlSlot()`；
      显示区两套布局加 slot；`Calculator` 接入真实工具。纯逻辑单测 22 例（温度非线性 + 单位线性/往返/边界）。
- [x] **阶段 3（已完成）**：汇率换算（参考 `com.yangdai.calc` 的 ECB 数据源，并补齐缓存/离线/开关）。
      `data/EcbRateParser`（DOM 解析 ECB `Cube[currency,rate]`，EUR=1，JVM 可测，含 XXE 加固）+
      `EcbResult`/`CachedRates`/`RateCache`（磁盘 JSON 缓存，存发布日期与抓取时间）+
      `ExchangeRateConfig`（读 `exchange_rate_config.json`，端点/币种/兜底外置，失败降级内置默认）+
      `ExchangeRateRepository`（IO 单线程；缓存优先→TTL 过期联网刷新→失败读缓存→无缓存用 fallback；
      `ConnectivityManager` 判联网；更新时间标签；`calc_tools` prefs 开关 `auto_update_rates`）+
      `model/CurrencyConversion`（EUR 基准交叉汇率）。`modes/CurrencyConverterMode` 复用显示区，控制行挂载
      `tool_control_slot`（来源/目标 Spinner+交换+更新时间标签），异步取汇率。`AndroidManifest` 加 `INTERNET`
      （仅汇率用）；选项菜单加可勾选"汇率自动更新"。解析与换算单测 12 例。
- [x] **阶段 4（已完成）**：房贷 / 个税 / BMI / 日期，全部真实实现替换占位。
      `model/MortgageCalculator`（等额本息：迭代幂避免 BigDecimal.pow 的 scale 爆炸；等额本金：首月/月减/总利息
      `P·r·(n+1)/2`；零利率兜底）、`model/IncomeTaxCalculator`（累计预扣预缴，按月累计穿越税率档）、
      `model/BmiCalculator`（中国成人分级）、`model/DateDelta`（`java.time` 天数+Period，自动处理反序）；
      `data/TaxBracket`/`TaxTable`（纯 JSON 解析 + `load` + 内置兜底税率表）+ `assets/tools/tax_cn.json`；
      `ToolInputFields`（多字段+活跃字段，复用数字键盘）+ `FieldToolMode`（抽象基类：字段视图/活跃高亮/键盘路由/挂载）；
      `MortgageMode`/`TaxMode`/`BmiMode`（继承 FieldToolMode）+ `DateMode`（DatePicker）。`ComingSoonMode` 已删除。
      纯逻辑单测 24 例（房贷等额本息/本金精确值+零利率+ ballpark、个税典型/免税/跨档、BMI 四档+边界、日期天数/反序、税率表解析）。
- [x] **阶段 5（已完成）**：联调与打磨。
      - **多字段显示区适配**：`ToolMode.wantsExpandedDisplay()` + `ToolHost.setExpandedDisplay()`；竖屏下房贷/个税/BMI/汇率激活时把显示区 0.30→0.42、input_pad 0.45→0.33（经 `ConstraintLayout.LayoutParams.matchConstraintPercentHeight` 运行时改约束百分比），切回计算器自动还原；**横屏不动**（pads 左右并排共享高度，改单 pad 会错位）。
      - **深色模式**：工具栏/面板/字段/图标全部走 MD3 动态 attr（`?colorSurface`/`colorOnSurface*`/`colorPrimary`）与 `tool_panel_background_color`（已含 night 变体），自动适配。
      - **横竖屏**：`values-land/ratios.xml` 与两套 MotionScene 已含 tool_bar；overlay 网格列数按方向 3/4；控制行横屏整宽。
      - **中文本地化**：`values-zh-rCN/strings.xml` 补齐全部 54 条工具文案（工具名/单位类别/币种无关/房贷/个税/BMI/日期/汇率）。
      - **可访问性**：chips/按钮/交换/日期均带 contentDescription；活跃字段以粗体+主色区分。

---

## 14. 实现总览（阶段 1–5 全部完成）

- **工具**（全部真实实现，无占位）：计算器（透传）、单位换算、汇率换算、房贷、个税、BMI、日期。
- **可插拔架构**：`ToolMode`/`ToolManager`/`ToolHost`；新增工具只需 `register()`。
- **引擎零侵入**：所有计算走 `tools/model/`（BigDecimal + java.time），未改 `Evaluator/CalculatorExpr/BoundedRational`。
- **数据驱动**：`assets/tools/{units,exchange_rate_config,tax_cn}.json`，外置可更新。
- **汇率**：ECB XML（参考 `com.yangdai.calc`）+ 磁盘缓存 + TTL + 离线降级 + 更新时间 + 自动更新开关 + `INTERNET`。
- **单测**：9 个测试类、~58 例（温度/单位/ECB 解析/汇率/房贷/个税/BMI/日期/税率表），纯 JVM。
- **本地化**：英文（默认）+ 简体中文。

### 已知限制 / 待你本地确认
- 沙箱无 JDK/Android SDK，**未实跑构建与测试**；请本地 `./gradlew testDebugUnitTest assembleDebug` 验证。
- 动态显示区扩展依赖运行时改约束百分比，在 MotionLayout 上**理论上生效**（rest 态用 live LayoutParams），但未经设备验证；若与"下拉历史"手势同用可能有一次跳变（已确保面板先收起）。
- 汇率 `fallback_rates` 为占位近似值，建议替换为真实快照；税率表随政策变化需维护 `tax_cn.json`。
- 工具图标暂为纯文字（`getIconRes` 返回 0），可后续按工具补矢量图标。
