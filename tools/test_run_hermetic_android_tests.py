import importlib.util
import sys
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location(
    "android_test_runner", Path(__file__).with_name("run-hermetic-android-tests.py")
)
assert SPEC is not None and SPEC.loader is not None
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


class VisiblePinnedTaskPackagesTest(unittest.TestCase):
    def test_finds_pip_task_when_package_precedes_mode(self):
        activity_dump = (
            "  Task{acde #609 type=standard A=10170:com.google.android.youtube U=0 "
            "visible=true visibleRequested=true mode=pinned bounds=Rect(10, 20 - 300, 500)}"
        )

        self.assertEqual(["com.google.android.youtube"], runner.visible_pinned_task_packages(activity_dump))

    def test_ignores_unpinned_tasks_and_top_activity(self):
        activity_dump = """\
topResumedActivity=ActivityRecord{abc u0 com.example.app/.MainActivity t1}
Task{acde #101 A=10123:com.example.app U=0 visible=true mode=fullscreen}
"""

        self.assertEqual([], runner.visible_pinned_task_packages(activity_dump))

    def test_returns_each_pinned_package_once(self):
        activity_dump = """\
Task{one #1 A=10170:com.example.video U=0 mode=pinned visible=true}
Task{two #2 A=10170:com.example.video U=0 mode=pinned visible=true}
Task{three #3 A=10171:com.example.other U=0 mode=fullscreen visible=true}
"""

        self.assertEqual(["com.example.video"], runner.visible_pinned_task_packages(activity_dump))


if __name__ == "__main__":
    unittest.main()
