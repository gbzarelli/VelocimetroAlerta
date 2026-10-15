import importlib.util
import sys
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location(
    "android_test_runner", Path(__file__).with_name("run-hermetic-android-tests.py")
)
runner = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = runner
SPEC.loader.exec_module(runner)


class RuntimePermissionParsingTest(unittest.TestCase):
    def test_api23_missing_runtime_permission_rows_mean_denied(self):
        output = """\
requested permissions:
  android.permission.ACCESS_COARSE_LOCATION
  android.permission.ACCESS_FINE_LOCATION
User 0: installed=true
  runtime permissions:
"""

        state = runner.parse_runtime_permissions_output(output, 23)

        self.assertEqual(
            state,
            {
                "android.permission.ACCESS_COARSE_LOCATION": (False, frozenset()),
                "android.permission.ACCESS_FINE_LOCATION": (False, frozenset()),
            },
        )

    def test_api23_legacy_rows_preserve_granted_location_permission(self):
        output = """\
  runtime permissions:
    android.permission.ACCESS_FINE_LOCATION: granted=true
"""

        state = runner.parse_runtime_permissions_output(output, 23)

        self.assertEqual(state["android.permission.ACCESS_FINE_LOCATION"], (True, frozenset()))
        self.assertEqual(state["android.permission.ACCESS_COARSE_LOCATION"], (False, frozenset()))

    def test_api24_empty_runtime_section_means_requested_location_permissions_are_denied(self):
        output = """\
requested permissions:
  android.permission.ACCESS_COARSE_LOCATION
  android.permission.ACCESS_FINE_LOCATION
install permissions:
  android.permission.VIBRATE: granted=true
User 0: installed=true
  runtime permissions:
"""

        state = runner.parse_runtime_permissions_output(output, 24)

        self.assertEqual(
            state,
            {
                "android.permission.ACCESS_COARSE_LOCATION": (False, frozenset()),
                "android.permission.ACCESS_FINE_LOCATION": (False, frozenset()),
            },
        )

    def test_api24_legacy_rows_preserve_granted_location_permission(self):
        output = """\
requested permissions:
  android.permission.ACCESS_COARSE_LOCATION
  android.permission.ACCESS_FINE_LOCATION
install permissions:
User 0: installed=true
  runtime permissions:
    android.permission.ACCESS_FINE_LOCATION: granted=true
    android.permission.ACCESS_COARSE_LOCATION: granted=true
"""

        state = runner.parse_runtime_permissions_output(output, 24)

        self.assertEqual(state["android.permission.ACCESS_FINE_LOCATION"], (True, frozenset()))
        self.assertEqual(state["android.permission.ACCESS_COARSE_LOCATION"], (True, frozenset()))

    def test_modern_rows_preserve_permission_flags(self):
        output = """\
  android.permission.ACCESS_FINE_LOCATION: granted=true, flags=[ USER_SET | USER_SENSITIVE_WHEN_GRANTED ]
"""

        state = runner.parse_runtime_permissions_output(output, 35)

        self.assertEqual(
            state["android.permission.ACCESS_FINE_LOCATION"],
            (True, frozenset({"user-set", "USER_SENSITIVE_WHEN_GRANTED"})),
        )

    def test_permission_flags_are_skipped_when_legacy_shell_lacks_commands(self):
        self.assertFalse(runner.permission_flags_supported("usage: cmd package\n  grant PACKAGE PERMISSION"))

    def test_permission_flags_are_enabled_when_shell_exposes_commands(self):
        output = "set-permission-flags PACKAGE PERMISSION\nclear-permission-flags PACKAGE PERMISSION"

        self.assertTrue(runner.permission_flags_supported(output))


if __name__ == "__main__":
    unittest.main()
