import io
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

import check_repo_standards  # noqa: E402


class CheckRepoStandardsTest(unittest.TestCase):

    def _init_clean_repo(self, root: Path) -> None:
        readme = root / "README.md"
        readme.write_text("# Argus Smart Cane\nClean ASCII and Cyrillic: \u0442\u0440\u043e\u0441\u0442\u044c - \u0442\u0435\u043b\u0435\u0444\u043e\u043d.\n", encoding="utf-8")

        ino = root / "firmware" / "argus_cane" / "argus_cane.ino"
        ino.parent.mkdir(parents=True, exist_ok=True)
        ino.write_text(
            "void setup() {\n"
            "  btSerial.begin(9600);\n"
            "  btSerial.stopListening();\n"
            "}\n",
            encoding="utf-8",
        )

        bt_kt = root / "android" / "app" / "src" / "main" / "java" / "com" / "project" / "argus" / "data" / "BluetoothCaneClient.kt"
        bt_kt.parent.mkdir(parents=True, exist_ok=True)
        bt_kt.write_text(
            "package com.project.argus.data\n\n"
            "/**\n"
            " * UNIDIRECTIONAL: NO OutputStream.write() or send() calls exist.\n"
            " */\n"
            "class BluetoothCaneClient {\n"
            "    // socket.outputStream.write(0) is forbidden and only mentioned in comment\n"
            "    fun readOnly(): Int = 1\n"
            "}\n",
            encoding="utf-8",
        )

        domain_kt = root / "android" / "app" / "src" / "main" / "java" / "com" / "project" / "argus" / "domain" / "model" / "CaneTelemetry.kt"
        domain_kt.parent.mkdir(parents=True, exist_ok=True)
        domain_kt.write_text(
            "package com.project.argus.domain.model\n\n"
            "data class CaneTelemetry(val distanceCm: Int)\n",
            encoding="utf-8",
        )

    def _run_main(self, root: Path) -> tuple[int, str]:
        buf = io.StringIO()
        with redirect_stdout(buf), redirect_stderr(buf):
            code = check_repo_standards.main(["--root", str(root)])
        return code, buf.getvalue()

    def test_clean_mini_repo_passes_all_checks(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_clean_repo(root)
            code, output = self._run_main(root)
            self.assertEqual(0, code, msg=output)

    def test_skipped_directories_are_ignored(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_clean_repo(root)
            for ignored in (".git", "build", ".gradle", ".idea"):
                bad = root / ignored / "bad.md"
                bad.parent.mkdir(parents=True, exist_ok=True)
                bad.write_text("Bad char: \u0451 \u2014 \ufffd\n", encoding="utf-8")
            code, output = self._run_main(root)
            self.assertEqual(0, code, msg=output)

    def test_invalid_utf8_fails_with_path_and_line(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_clean_repo(root)
            broken = root / "docs" / "broken.md"
            broken.parent.mkdir(parents=True, exist_ok=True)
            broken.write_bytes(b"Line 1 ok\nLine 2 bad: \xff\xfe\n")
            code, output = self._run_main(root)
            self.assertEqual(1, code)
            self.assertIn("docs/broken.md:2:", output)

    def test_replacement_char_ufffd_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_clean_repo(root)
            bad = root / "docs" / "mojibake.md"
            bad.parent.mkdir(parents=True, exist_ok=True)
            bad.write_text("ok\nmojibake \ufffd here\n", encoding="utf-8")
            code, output = self._run_main(root)
            self.assertEqual(1, code)
            self.assertIn("docs/mojibake.md:2:", output)

    def test_all_forbidden_typography_codepoints_fail(self) -> None:
        forbidden_chars = [
            "\u0451",
            "\u0401",
            "\u2014",
            "\u2013",
            "\u00ab",
            "\u00bb",
            "\u201c",
            "\u201d",
            "\u2018",
            "\u2019",
        ]
        for ch in forbidden_chars:
            with self.subTest(codepoint=f"U+{ord(ch):04X}"):
                with tempfile.TemporaryDirectory() as tmp:
                    root = Path(tmp)
                    self._init_clean_repo(root)
                    doc = root / "docs" / "typo.md"
                    doc.parent.mkdir(parents=True, exist_ok=True)
                    doc.write_text(f"first line\nbad {ch} symbol\n", encoding="utf-8")
                    code, output = self._run_main(root)
                    self.assertEqual(1, code)
                    self.assertIn("docs/typo.md:2:", output)

    def test_bluetooth_write_calls_in_kotlin_code_fail(self) -> None:
        snippets = [
            "val s = socket.outputStream",
            "stream.write(byteArrayOf(1))",
            "channel.send(payload)",
            'val msg = "debug ${socket.outputStream}"',
        ]
        for snippet in snippets:
            with self.subTest(snippet=snippet):
                with tempfile.TemporaryDirectory() as tmp:
                    root = Path(tmp)
                    self._init_clean_repo(root)
                    bad_kt = root / "android" / "app" / "src" / "main" / "BadBt.kt"
                    bad_kt.write_text(f"package test\nfun bad() {{\n    {snippet}\n}}\n", encoding="utf-8")
                    code, output = self._run_main(root)
                    self.assertEqual(1, code)
                    self.assertIn("android/app/src/main/BadBt.kt:3:", output)

    def test_missing_or_commented_stop_listening_in_firmware_fails(self) -> None:
        for fake_body in (
            "void setup() {\n  // btSerial.stopListening();\n}\n",
            'void setup() {\n  Serial.println("btSerial.stopListening();");\n}\n',
        ):
            with self.subTest(fake_body=fake_body):
                with tempfile.TemporaryDirectory() as tmp:
                    root = Path(tmp)
                    self._init_clean_repo(root)
                    ino = root / "firmware" / "argus_cane" / "argus_cane.ino"
                    ino.write_text(fake_body, encoding="utf-8")
                    code, output = self._run_main(root)
                    self.assertEqual(1, code)
                    self.assertIn("firmware/argus_cane/argus_cane.ino", output)

    def test_android_or_androidx_import_in_domain_layer_fails(self) -> None:
        for bad_import in ("import android.content.Context", "import androidx.compose.runtime.Composable"):
            with self.subTest(bad_import=bad_import):
                with tempfile.TemporaryDirectory() as tmp:
                    root = Path(tmp)
                    self._init_clean_repo(root)
                    leaking = (
                        root
                        / "android"
                        / "app"
                        / "src"
                        / "main"
                        / "java"
                        / "com"
                        / "project"
                        / "argus"
                        / "domain"
                        / "LeakyService.kt"
                    )
                    leaking.write_text(f"package com.project.argus.domain\n{bad_import}\ninterface LeakyService\n", encoding="utf-8")
                    code, output = self._run_main(root)
                    self.assertEqual(1, code)
                    self.assertIn("android/app/src/main/java/com/project/argus/domain/LeakyService.kt:2:", output)

    def test_cli_invocation_returns_expected_exit_codes(self) -> None:
        script_path = SCRIPTS_DIR / "check_repo_standards.py"
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_clean_repo(root)
            ok_proc = subprocess.run(
                [sys.executable, str(script_path), "--root", str(root)],
                capture_output=True,
                text=True,
                encoding="utf-8",
            )
            self.assertEqual(0, ok_proc.returncode, msg=ok_proc.stdout + ok_proc.stderr)

            (root / "README.md").write_text("Bad dash \u2014 here\n", encoding="utf-8")
            fail_proc = subprocess.run(
                [sys.executable, str(script_path), "--root", str(root)],
                capture_output=True,
                text=True,
                encoding="utf-8",
            )
            self.assertEqual(1, fail_proc.returncode)
            self.assertIn("README.md:1:", fail_proc.stdout + fail_proc.stderr)


if __name__ == "__main__":
    unittest.main()
