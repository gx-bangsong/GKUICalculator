**审核草稿，尚不可发布。** 本稿候选为 `a9b22ade601a2c6b71bb878022ad1cb3f2e0746b`，不是发布分支已经完成的构建。

- 上一正式版：`v20261031.2052` → `4df687afca570c23dbb8948f1b08341e487f352a`。
- 核对时远程发布分支 `lineage-23.2` 仍为 `4df687afca570c23dbb8948f1b08341e487f352a`。开发候选不是其祖先，功能**尚未合入发布分支**；不能发布旧分支 APK 却套用此功能摘要。
- 同候选 SHA 的 [build 44848669030](https://github.com/gx-bangsong/GKUICalculator/actions/runs/44848669030) 成功，仅是 build workflow 成功，不是本候选 production release 或完整回归成功。
- [分数专项 44848669029](https://github.com/gx-bangsong/GKUICalculator/actions/runs/44848669029)：隔离数值通过；开关/持久化、复制、两种顺序文字/空白起滑及关闭自动导出辅助检查通过；真实 25% 建窗失败（约 50%），签名身份检查失败，因此专项整体失败。
- [广泛 UI 44848668956](https://github.com/gx-bangsong/GKUICalculator/actions/runs/44848668956) 失败，未宣称完整回归通过。
- 分数真机验收基准为 `c5755d727d7cccc9e80274262ee9d7a5c2427e85`，不是本候选的真机验收。
- 本候选未完成原开发 APK → 新 APK 的覆盖安装及数据/“显示分数”偏好保留验证；正式候选的升级数据验证同样待补。
- 本次说明/workflow 改动产生的最终提交，以及未来合入发布分支的构建 SHA，均须重新核对测试；不能沿用上述 SHA 的结论冒充新 SHA 已通过。
- 未创建新 Release 或 tag；未移动旧 tag，未修改已发布说明。新 tag 名称以实际正式构建提取的 versionName 为准，本稿不猜测版本号。
