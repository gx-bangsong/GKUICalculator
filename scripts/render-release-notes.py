#!/usr/bin/env python3
"""Render reviewed Chinese notes; never publish, build, sign, or modify tags."""
import argparse
import json
from pathlib import Path
import re
import subprocess


def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()


def render(config, sha, build=None, appendix='', verification='尚未提供该 SHA 的测试记录；发布前必须核对。'):
    if not re.fullmatch(r'[0-9a-f]{40}', sha):
        raise ValueError('Expected full 40-character build SHA')
    if git('rev-parse', sha + '^{commit}') != sha:
        raise ValueError('SHA does not resolve to the requested commit')
    previous = config['previous_tag']
    base = git('rev-parse', 'refs/tags/' + previous + '^{commit}')
    subprocess.run(['git', 'merge-base', '--is-ancestor', base, sha], check=True)
    if sha == base:
        raise ValueError('No changes since previous release; do not re-release the old tag')
    if build and build.get('commit') != sha:
        raise ValueError('build-info.json commit differs from actual build SHA')
    summary = Path(config['summary']).read_text().strip()
    metadata = [f'- 上一正式版：`{previous}`（`{base}`）', f'- 实际构建/核对 SHA：`{sha}`',
                f'- 发布分支：`{config["release_branch"]}`',
                f'- 明确比较范围：`{previous}...{sha}`']
    if build:
        for key in ('applicationId', 'versionCode', 'versionName', 'signer_sha256', 'apk_sha256', 'source_url'):
            metadata.append(f'- {key}：`{build[key]}`')
    else:
        metadata.append('- 尚无本候选正式构建元数据；tag、版本号、正式签名及 APK 校验值均待实际构建确认，不预填。')
    return (summary + '\n\n<details>\n<summary>技术元数据与自动明细附录</summary>\n\n'
            + '### 本候选核对记录\n\n' + verification.strip() + '\n\n' + '\n'.join(metadata) + '\n\n### 自动 PR 明细（附录，不作为产品更新摘要）\n\n'
            + (appendix.strip() or '尚未请求自动明细；未据提交标题推断新增功能。')
            + '\n\n</details>\n')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--config', default='releases/next.json')
    p.add_argument('--sha', required=True)
    p.add_argument('--build-info')
    p.add_argument('--appendix')
    p.add_argument('--verification')
    p.add_argument('--output', required=True)
    a = p.parse_args()
    kwargs = {}
    if a.build_info:
        kwargs['build'] = json.loads(Path(a.build_info).read_text())
    if a.appendix:
        kwargs['appendix'] = Path(a.appendix).read_text()
    if a.verification:
        kwargs['verification'] = Path(a.verification).read_text()
    Path(a.output).write_text(render(json.loads(Path(a.config).read_text()), a.sha, **kwargs))


if __name__ == '__main__':
    main()
