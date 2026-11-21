# 恢复系统 Monet 取色（不改变已验收布局）

## 定位

`git log -p -- res/values-v31/color.xml res/values-night-v31/color.xml res/values-v34/color.xml res/values-night-v34/color.xml res/values/themes.xml`
定位首次覆盖为 `4f70e710df26fbf53e64dffd2c9fb9aebe3823ff`（refresh calculator UI）。
只恢复颜色语义，不回退该提交的界面，不改变几何、字号、分屏、历史手势、分数或 edge-to-edge。

## 映射

- API 31–33：沿用 Material 的系统动态 tonal palette；显示区/历史/数字/科学键使用 neutral 表面，运算符与选中 pill 使用 secondary container，功能键 primary container，等号与结果使用 tertiary。
- API 34+：对应系统 surface / surface_container / primary、secondary container / tertiary 与各自 on-* 颜色。
- 浅/深色分别映射；单位/币种 pill、交换和工具图标的 tint、分隔线及 ripple 均通过原有语义资源接入，不改 drawable 几何。
- 按键公共文字 selector 读取按键 theme 的 colorOnContainer，不再让等号/运算符/功能键共用写死的文字色。禁用键保留独立 neutral 色。
- API 30 静态暖色完整保留，新增语义资源在 unqualified values 提供静态别名。没有新增权限或取色引擎。
- 未修改系统栏衔接逻辑、历史窗口布局或 edge-to-edge 配置。

## 专项验证

`monet-colors.yml` 仅在本开发分支的颜色相关改动或手动触发时执行专项，不改既有 CI/发布流程。API 30 验证静态浅/深；API 31、34 在一次性模拟器中设置红、蓝两套 SystemUI Monet seed，各验证浅/深。
测试读取真实系统 accent，最终断言两套系统色以及应用等号/选中 pill 确实不同，而非只切换深浅模式。检查文字对比度并保存分数、科学键盘、历史、单位/币种、菜单与原生对话框截图及颜色 JSON。
截图与同任务 APK 一起上传，所有结果须以实际 CI 为准，未执行/失败不得标记通过。模拟器截图不替代真机验收或真实 25% 建窗验证。
