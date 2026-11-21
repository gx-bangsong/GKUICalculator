# 本轮改动清单（待确认）

## 范围

仅改自动发布说明、说明生成器、相关回归及文档。未改产品源码/资源、签名文件、Gradle 或版本号算法、`scripts/ci-build.sh`。未发布、未创建/移动 tag、未改写旧 Release。

## 文件与行为

- `releases/next.md`：中文版本摘要；以 `v20261031.2052` 到核对候选的最终差异归并界面、工具、输入、分数、小窗等内容；安装/数据区别与限制单列。
- `releases/next.json`：显式比较起点、发布分支和摘要路径。
- `releases/candidate-verification.md`：记录开发候选完整 SHA、发布分支未合入状态、同 SHA 构建/专项/广泛 UI 结果、升级数据验证缺口。
- `releases/candidate-pr-appendix.md`：GitHub generate-notes 的预览，显式起点 `v20261031.2052`、终点候选完整 SHA。预览未返回 PR 条目，不自行编造。
- `releases/RELEASE-DRAFT.md`：由上述输入合成的完整 Release 核对稿；不是新正式版本。
- `scripts/render-release-notes.py`：中文正文优先，技术元数据与 PR 明细在末尾折叠；拒绝短 SHA、非祖先比较起点、无差异发布和 APK 元数据 SHA 不一致。
- `.github/workflows/release.yml`：调用相同生成器，明确 previous_tag_name/target_commitish；标签显式绑定构建输出 SHA，拒绝覆盖旧 tag，发布操作改为**仅创建待审草稿**。保留原构建/签名/版本计算步骤；在构建前限制发布分支。
- `docs/WORKFLOW.md`：移除过期完整 YAML 副本与“先卸载开发版”的错误指引，引用真实 workflow，并提供同步调用模板及发布前检查清单。
- `tests/test_release_notes.py`：说明生成器和 workflow 约束的本地回归。

## 已执行检查

- 5 项本地说明生成/拒绝条件测试通过。
- 真实 Git tag 与候选的祖先关系检查通过；完整草稿生成成功。
- `git diff --check` 通过。
- 未运行正式构建或 production release workflow；这不是产品完整回归。

## 确认点

1. 确认中文更新摘要及已知限制的措辞。
2. 确认后续 workflow 仅生成 GitHub 待审草稿，不直接公开发布。
3. 当前发布分支仍是上一正式版，需另行合入开发功能后才有可发布的最终构建 SHA；本轮不合并分支、不预定新 tag/版本号。
4. 发布前须为最终 SHA 重新补齐必要测试、签名及安装/数据保留证据，不能照搬开发候选的部分通过结果。
