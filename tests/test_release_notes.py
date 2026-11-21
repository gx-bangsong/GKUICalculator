import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('notes', 'scripts/render-release-notes.py')
notes = importlib.util.module_from_spec(spec)
spec.loader.exec_module(notes)
SHA = 'a' * 40
BASE = 'b' * 40


class NotesTest(unittest.TestCase):
    def setUp(self):
        self.config = {'previous_tag': 'v-test', 'release_branch': 'lineage-23.2',
                       'summary': 'releases/next.md'}

    @patch.object(notes.subprocess, 'run')
    @patch.object(notes, 'git', side_effect=[SHA, BASE])
    def test_summary_first_metadata_and_pr_folded(self, git, run):
        text = notes.render(self.config, SHA, appendix='PR-EXAMPLE')
        self.assertTrue(text.startswith(Path(self.config['summary']).read_text().strip() + '\n\n<details>'))
        self.assertLess(text.index('<details>'), text.index('PR-EXAMPLE'))
        self.assertLess(text.index('<details>'), text.index('实际构建/核对 SHA'))
        self.assertIn('v-test...' + SHA, text)
        self.assertIn('尚未提供该 SHA 的测试记录', text)
        self.assertTrue(text.endswith('</details>\n'))
        run.assert_called_once_with(['git', 'merge-base', '--is-ancestor', BASE, SHA], check=True)

    def test_reject_short_sha(self):
        with self.assertRaises(ValueError):
            notes.render(self.config, 'abc123')

    @patch.object(notes.subprocess, 'run')
    @patch.object(notes, 'git', side_effect=[SHA, BASE])
    def test_reject_other_build_sha(self, git, run):
        with self.assertRaisesRegex(ValueError, 'differs'):
            notes.render(self.config, SHA, build={'commit': BASE})

    @patch.object(notes.subprocess, 'run')
    @patch.object(notes, 'git', side_effect=[BASE, BASE])
    def test_reject_no_changes(self, git, run):
        with self.assertRaisesRegex(ValueError, 'No changes'):
            notes.render(self.config, BASE)

    def test_workflow_is_public_and_sha_bound(self):
        text = Path('.github/workflows/release.yml').read_text()
        self.assertIn('--target "$BUILD_SHA"', text)
        self.assertNotIn('--draft', text)
        self.assertNotIn('draft: true', text)
        self.assertNotIn('environment:', text)
        self.assertIn('previous_tag_name="$PREVIOUS_TAG"', text)
        self.assertNotIn('--generate-notes', text)
        self.assertIn('refs/tags/$RELEASE_TAG', text)
        self.assertNotIn('please uninstall', text)
