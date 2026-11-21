# 正式发布工作流与版本说明

## 唯一执行源

实际工作流为 [release.yml](../.github/workflows/release.yml)，构建脚本为
[`scripts/ci-build.sh`](../scripts/ci-build.sh)。不再维护一份容易漂移的完整 YAML 副本。
当前发布分支是 `lineage-23.2`，不是开发工作分支。仅在其 push（排除纯 Markdown/docs）或手动触发时运行。
手动触发也必须选中发布分支；不要为预览说明触发签名构建。

本轮只修改发布说明生成，不改签名配置、包名、版本号算法或产品功能。

## 随源码提交的说明模板

- `releases/next.json`：显式指定上一正式版 tag、发布分支和中文摘要文件。
- `releases/next.md`：按最终产品差异去重的中文摘要、安装说明、验证范围与已知限制。
- `scripts/render-release-notes.py`：合成最终正文，技术元数据和自动 PR 明细统一放到末尾折叠区。
- `releases/RELEASE-DRAFT.md`：本次离线核对稿，不是已经发布的 Release。

下一次发布前更新 `previous_tag`，只保留自该 tag 起实际新增的用户改进；排错、截图、重试、测试基础设施提交不逐条写成产品功能。
无改动、比较基线过时、build-info 与构建 SHA 不同或 tag 已存在时停止，不重打旧 tag。

工作流从经过校验的 `dist/build-info.json` 读取产物信息，并使用构建脚本输出的完整 commit：

```bash
# 自动明细仅进入附录，起点和终点均显式指定。
gh api --method POST "repos/$GITHUB_REPOSITORY/releases/generate-notes" \
  -f tag_name="$RELEASE_TAG" -f target_commitish="$BUILD_SHA" \
  -f previous_tag_name="$PREVIOUS_TAG" --jq .body > "$RUNNER_TEMP/pr-appendix.md"
python3 scripts/render-release-notes.py --sha "$BUILD_SHA" \
  --build-info dist/build-info.json --appendix "$RUNNER_TEMP/pr-appendix.md" \
  --output "$RUNNER_TEMP/release-notes.md"
gh release create "$RELEASE_TAG" dist/* \
  --target "$BUILD_SHA" --draft --title "$RELEASE_TAG" \
  --notes-file "$RUNNER_TEMP/release-notes.md"
```

不再使用 `gh release create --generate-notes` 追加顶层流水账；不依赖默认分支 HEAD 作为标签目标。
工作流只创建**待审核草稿**，不会自动公开发布。操作者确认同 SHA 测试、已知限制、签名与数据保留证据后，另行明确批准公开；本轮不执行该操作。
仅在工作分支修改本文件不会运行 production release；也不应把功能未合入发布分支的开发候选写成正式版已交付。

## 安装与数据模板（必须与正文一致）

正式包 `com.android.calculator2` 与开发/debug 包 `com.android.calculator2.dev` 可并存。
应用数据、历史和偏好各自独立，不自动共享或迁移。**不要指导用户先卸载开发版**。
同包名升级须签名兼容且版本不降；正式签名校验与版本递增仍由原构建脚本负责。
开发包临时调试签名可能不同，不能保证互相覆盖；签名冲突时不要用卸载或清数据绕过。

## 发布前清单

1. 核实上一正式版 tag 的真实 commit，确认候选功能已合入 `lineage-23.2`，记录最终构建完整 SHA。
2. 审核 `next.md` 对该比较范围是否准确，确认无重复、回退功能、排错流水账或未经验证承诺。
3. 对**同一完整 SHA**核对必要数值、UI、安装/数据保留测试与人工验收。草稿默认写“尚未提供”，不会自动把 assembleRelease 成功升级为全回归通过。
4. 用 APK 实际元数据核对包名、versionCode、签名与校验值；保留 APK、`.sha256`、`build-info.json` 和精确源码链接。
5. 已知真实 25% 建窗失败、既有单元测试编译问题及广泛 UI 失败要如实保留。其他 SHA 的真机反馈只作其自身验收记录。
6. 公开前补充最终 SHA 的证据与限制；不得改写已发布 Release 历史，不移动旧 tag。

## 本地预览（不构建、不签名、不创建 Release/tag）

```bash
python3 scripts/render-release-notes.py \
  --sha <完整候选SHA> --verification releases/candidate-verification.md \
  --appendix releases/candidate-pr-appendix.md --output /tmp/release-notes.md
python3 -m unittest discover -s tests -p 'test_release_notes.py'
```

工作流继续使用既有 `KEYSTORE_FILE`（Base64 内容）、`KEYSTORE_PASS`、`KEY_ALIAS`、`KEY_PASS` secrets；
没有新增或更换签名，也没有更改 `scripts/ci-build.sh` 和 Gradle 版本号来源。
