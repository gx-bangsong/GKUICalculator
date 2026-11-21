# 外部选中文本的原智能“转换”建议：可行性调查

## 结论摘要

**目前不能承诺实现“普通安装 GKUI 后，替换 Via 原有智能‘转换’建议”的首选目标。** Android 11 没有面向普通第三方 APK 的通用“默认单位／币种转换建议处理器”公开注册入口。具体一加 7T Pro / Via 的调用链仍须以该设备证据确认；这不等于断言该设备一定使用了显式 Intent，或所有 OEM 均绝无定制接入可能。

本轮只做观察，没有新增接收 Activity、Intent filter、默认搜索／浏览器处理器、PROCESS_TEXT 入口，也未改提示、切换、历史、键盘或计算模型。

## 一、已知真机信息与尚未知字段

用户复现：一加 7T Pro、Android 11、Via 中选中 `10 km` → 点击原菜单“转换” → 地址栏／搜索框显示 `换算 10 km`。

| 字段 | 当前证据 |
|---|---|
| 选中文本 | 用户确认 `10 km` |
| 可见菜单标题 | 用户确认“转换” |
| 可见查询内容 | 用户确认 `换算 10 km`；**不是已捕获的 SearchManager.QUERY extra** |
| Via 包名、版本、WebView 版本、OxygenOS build | 未取得；不能用模拟器或最新 Via 版本替代 |
| 建议生成者 | 未确认：可能涉及系统／OEM 分类器、WebView 菜单层或 Via 自身；不能仅凭未单独安装 ASI 排除系统分类 |
| 原始 action / data / package / component / extras | 未取得，不补写成 ACTION_WEB_SEARCH、某个 Google URL 或显式组件 |
| 点击后是否浏览器内部 loadUrl／搜索 | 未确认；地址栏结果不足以区分内部处理、Intent 或中间转发 |

## 二、源码能证实的调用层次（不是该真机的已捕获调用栈）

### 1. 谁可以生成建议

Android 11 的 TextClassificationManager 可把请求交给系统／OEM 服务；`TextView` 和 `WebView` 可以使用默认分类器，也可由**宿主 App 为自己的实例**配置分类器。系统服务由系统配置的包／服务选择，不要求用户另行安装某个名为 ASI 的 App。[S1–S3]

因此，“用户没有单独安装 ASI”不能确认由谁生成建议；本调查不限定 Pixel／ASI。

### 2. 菜单标题不能决定 Intent 类型

Android 11 的开源分类器从模型返回的 RemoteActionTemplate 构造 action、data、type、categories 和 extras，再解析活动；部分解析结果会在创建 PendingIntent 前设置 component。Chromium 的智能动作路径调用分类结果的 listener／Intent，附加动作可以发送 RemoteAction 的 PendingIntent。[S4–S6]

同一个 Chromium 版本中，**普通网页搜索**是另一条路径：构造 `ACTION_WEB_SEARCH`，将选中文本放入 `SearchManager.QUERY`；PROCESS_TEXT 也是独立路径。[S6]

这些源码证明存在多条不同调用方式，**不能证明用户那一次“转换”走的是其中哪一条**。特别是：

- 最终打开 Google／Via，不足以反推原始请求显式或隐式。
- dumpsys／日志中的最终 component 可能是解析后写入的，不能据此声称模型最初返回了显式 component。
- `换算 10 km` 可能是生成或后续处理后的查询内容；没有原始请求就不能认定生成位置。

### 3. 普通安装 APK 能否成为原建议的默认处理器

- **替换全系统分类器：不是普通 APK 的公开接入点。** Android 11 的 TextClassifierService 属于 System API，服务由系统配置选择；没有面向用户的公开“默认转换分类器”角色。仅声明服务／permission 不会让系统或 Via 选择 GKUI。`BIND_TEXTCLASSIFIER_SERVICE` 是保护系统绑定服务的 signature 权限；不能把“声明一个需要该权限的服务”误当作已获得系统选择资格。调试性的 shell／DeviceConfig 覆盖也不是本任务允许的普通安装接入方式。[S2,S3]
- **在 GKUI 中调用 setTextClassifier：只影响 GKUI 的上下文／控件，不会修改 Via 的 WebView。** 若 Via 开发者主动合作，可在其自身选区菜单／分类动作中提供明确的 GKUI 调用协议，但这要求浏览器一方改动与双方约定，不是 GKUI 单方接管。[S1,S2]
- **接收现有 Intent：必须先知道真实协议。** 若以后确实捕获到一个允许第三方解析、范围足够窄的隐式协议，才值得做最小接收验证；目前没有这样的证据。
- **通用搜索／网页接管不在范围内。** Android 11 Intent filter 不能按 `SearchManager.QUERY` extra 的“换算”前缀选择接收者；不能靠注册所有 WEB_SEARCH 再自行筛词来满足“只接管转换”。泛匹配 Google 搜索页／网页链接或充当默认浏览器同样不是本任务的方案。[S7]

## 三、Android 11 模拟器实测

**未取得可验证的原智能建议复现与点击调用链，不能写成“模拟器原生建议不存在”，更不能写成“接管成功”。**

本地工作区无 adb／Android 模拟器，改用现有手动 CI 观察分支，指定 API 30（Android 11）、default 系统镜像、Pixel 2 硬件配置。注意这不是用户的一加 ROM，也没有用户同版 Via APK。

- [第一轮观察 44691138503](https://github.com/gx-bangsong/GKUICalculator/actions/runs/44691138503)，观察代码 SHA `e225d7debc3691daf7e9e3ea2db112b92a69c84d`：主／测试 APK 编译成功。artifact 文件列表确认产生原生文本截图（39095 bytes）和 WebView 截图（40658 bytes）；但回收的报告仅 53 bytes、未得到可解析的观察 JSON，workflow 为 FAILURE。无法读取这些图像本身及原始报告，**不把“文件存在”当成已看到原建议或已选中完整文本的证据**。
- [第二轮观察 44691515156](https://github.com/gx-bangsong/GKUICalculator/actions/runs/44691515156)，观察代码 SHA `844d1044e4a1fb85204fa5775f7c55972aeb7fc5`：调整了报告留存方式；进入模拟器测试阶段后长时间未回收可用报告，达到本次调查预算后主动取消，最终 CANCELLED。没有再修改系统尝试重现，也未继续无限重跑。
- 因而本轮没有拿到模拟器的实际 WebView 版本、分类结果、原建议 action/data/component 或点击目标。不以测试代码中“计划记录”的字段冒充已采集数据。

探针只使用系统现有分类器、原生可选择 TextView 和内嵌系统 WebView；没有安装替代分类器、添加转换接收器、变更默认处理器或修改分类系统来制造“接管成功”。宿主是仅测试用的界面，不是 Via 复现，不是 GKUI 接入验证。后续若手动复用探针，已为 Gradle 观察阶段加 240 秒上限；该限时改动未再次运行，不声明测试通过。

**调查边界到此为止：现有证据只有公开机制与用户提供的真机现象，不能拼成一条已确认的 OnePlus／Via 调用链。**

## 四、如何补齐一加／Via 证据（不要求 Root）

如需继续精确定位，可先采集下面这些只读信息；本轮未把它们当作已经从用户设备取得的结果。

```sh
adb shell getprop ro.build.fingerprint
adb shell dumpsys webviewupdate
adb shell dumpsys package mark.via
adb shell dumpsys package mark.via.gp
adb shell dumpsys textclassification
adb shell cmd package query-services --brief -a android.service.textclassifier.TextClassifierService
```

Via 公开说明也支持在“设置 → 关于”点击 Via 标识查看含 WebView 版本的调试信息。[S8]

然后只复现 `10 km`，并在点击原建议前开始记录：

```sh
adb logcat -v threadtime -s ActivityTaskManager:I ActivityManager:I
# 点击“转换”后另取一次：
adb shell dumpsys activity activities
adb shell dumpsys activity intents
```

需要区分：原始 action/data/extras、解析后的 component、PendingIntent 创建者和最终显示的网页查询。系统日志可能只显示 `(has extras)`，也可能不暴露 Via 内部导航；**缺少 START 日志不证明一定是内部 loadUrl**。只读 ADB 也不保证能取到另一 App 的完整原始请求。若仍无原始字段，应停在“未知”，通过 Via 开发者调试构建／合作定位，而不是继续修改系统试图拦截。日志可能含其他应用信息，提供前应裁去与本次测试无关的内容。

## 五、备选方案（仅说明，未实施）

`ACTION_PROCESS_TEXT` 是公开机制：支持该菜单扩展的 App 可新增一个由 GKUI Activity 提供的普通文本处理入口，传递 `EXTRA_PROCESS_TEXT`（CharSequence）及 `EXTRA_PROCESS_TEXT_READONLY`。经用户确认后，可以单独设计为“接收选中文本 → 预览识别的数值／单位／币种 → 明确确认后进入转换页面”，保护原有未完成输入。[S9]

它能做的是**新增入口**；不能保证替换、重定向、优先于或删除原有智能“转换”建议，也不保证所有浏览器／WebView 均显示该入口。本轮没有注册或实现它，等待用户确认，不把备选方案当成首选目标已实现。

## 来源与版本边界

- [S1 Android 11 WebView.java：setTextClassifier / getTextClassifier](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-11.0.0_r1/core/java/android/webkit/WebView.java#L2346-L2359)
- [S2 Android 11 TextClassificationManager.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-11.0.0_r1/core/java/android/view/textclassifier/TextClassificationManager.java)；[系统服务选择](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-11.0.0_r1/services/core/java/com/android/server/textclassifier/TextClassificationManagerService.java#L688-L712)
- [S3 Android 11 TextClassifierService.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-11.0.0_r1/core/java/android/service/textclassifier/TextClassifierService.java#L60-L89)；[权限声明](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-11.0.0_r1/core/res/AndroidManifest.xml#L3416-L3422)
- [S4 Android 11 TemplateIntentFactory.java](https://github.com/aosp-mirror-neo/platform_external_libtextclassifier/blob/android-11.0.0_r1/java/src/com/android/textclassifier/common/intent/TemplateIntentFactory.java#L85-L113)
- [S5 Android 11 LabeledIntent.java：resolve 与 PendingIntent](https://github.com/aosp-mirror-neo/platform_external_libtextclassifier/blob/android-11.0.0_r1/java/src/com/android/textclassifier/common/intent/LabeledIntent.java#L105-L144)；[TextClassifierImpl 主动作构造](https://github.com/aosp-mirror-neo/platform_external_libtextclassifier/blob/android-11.0.0_r1/java/src/com/android/textclassifier/TextClassifierImpl.java#L643-L672)
- [S6 Chromium 86.0.4240.198 SelectionPopupControllerImpl.java](https://github.com/chromium/chromium/blob/86.0.4240.198/content/public/android/java/src/org/chromium/content/browser/selection/SelectionPopupControllerImpl.java#L1047-L1064)；[普通搜索路径](https://github.com/chromium/chromium/blob/86.0.4240.198/content/public/android/java/src/org/chromium/content/browser/selection/SelectionPopupControllerImpl.java#L1166-L1184)；[附加智能动作 PendingIntent.send](https://github.com/chromium/chromium/blob/86.0.4240.198/content/public/android/java/src/org/chromium/content/browser/selection/AdditionalMenuItemProviderImpl.java#L96-L105)。此版本仅说明 Android 11 时期的开源机制，不代表用户安装的 WebView 版本。
- [S7 Android 11 IntentFilter.java：匹配 action/type/scheme/authority/path/categories，不按搜索 query extra 匹配](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-11.0.0_r1/core/java/android/content/IntentFilter.java#L1579-L1652)
- [S8 Via 官方翻译仓库与 WebView 说明，查阅版本 7b3e20e5e4f57b9ae2fc85ad37f559822edf1ad5](https://github.com/tuyafeng/Via/blob/7b3e20e5e4f57b9ae2fc85ad37f559822edf1ad5/README.md)。仓库自述用于本地化，未提供足以核实该版本私有调用链的完整浏览器实现。
- [S9 Android Intent 公共 API：ACTION_PROCESS_TEXT](https://developer.android.com/reference/android/content/Intent#ACTION_PROCESS_TEXT)
