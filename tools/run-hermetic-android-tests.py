#!/usr/bin/env python3
"""Run Android instrumentation tests one method at a time with host-managed permissions."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ANDROID_TEST_ROOT = ROOT / "mobile/src/androidTest/java"
APP_METADATA = ROOT / "mobile/build/outputs/apk/debug/output-metadata.json"
TEST_METADATA = ROOT / "mobile/build/outputs/apk/androidTest/debug/output-metadata.json"
APP_APK = ROOT / "mobile/build/outputs/apk/debug/mobile-debug.apk"
TEST_APK = ROOT / "mobile/build/outputs/apk/androidTest/debug/mobile-debug-androidTest.apk"
RESULT_ROOT = ROOT / "mobile/build/hermetic-android-tests"

RUNTIME_PERMISSIONS = {
    "Manifest.permission.ACCESS_COARSE_LOCATION": ("android.permission.ACCESS_COARSE_LOCATION", 23),
    "Manifest.permission.ACCESS_FINE_LOCATION": ("android.permission.ACCESS_FINE_LOCATION", 23),
    "Manifest.permission.BLUETOOTH_SCAN": ("android.permission.BLUETOOTH_SCAN", 31),
    "Manifest.permission.BLUETOOTH_CONNECT": ("android.permission.BLUETOOTH_CONNECT", 31),
    "Manifest.permission.POST_NOTIFICATIONS": ("android.permission.POST_NOTIFICATIONS", 33),
}


@dataclass(frozen=True)
class TestCase:
    class_name: str
    method_name: str
    min_sdk: int
    granted: frozenset[str]
    denied: frozenset[str]


def _parse_permission_array(annotation: str, key: str) -> frozenset[str]:
    match = re.search(rf"\b{key}\s*=\s*\[([^\]]*)\]", annotation, re.DOTALL)
    if not match:
        return frozenset()
    names = re.findall(r"Manifest\.permission\.[A-Z0-9_]+", match.group(1))
    unknown = sorted(set(names) - RUNTIME_PERMISSIONS.keys())
    if unknown:
        raise ValueError(f"Unsupported runtime permissions: {', '.join(unknown)}")
    return frozenset(RUNTIME_PERMISSIONS[name][0] for name in names)


def discover_tests() -> list[TestCase]:
    cases: list[TestCase] = []
    for source in sorted(ANDROID_TEST_ROOT.rglob("*.kt")):
        content = source.read_text(encoding="utf-8")
        package_match = re.search(r"^package\s+([\w.]+)", content, re.MULTILINE)
        class_match = re.search(r"^class\s+(\w+)", content, re.MULTILINE)
        if not package_match or not class_match:
            continue
        package = package_match.group(1)
        class_name = class_match.group(1)
        class_prefix = content[: class_match.start()]
        class_sdk = max(
            (int(value) for value in re.findall(r"@SdkSuppress\s*\(\s*minSdkVersion\s*=\s*(\d+)", class_prefix)),
            default=1,
        )

        pending_annotations: list[str] = []
        annotation_parentheses = 0
        for line in content.splitlines():
            stripped = line.strip()
            if stripped.startswith("@") or (pending_annotations and annotation_parentheses > 0):
                pending_annotations.append(stripped)
                annotation_parentheses += stripped.count("(") - stripped.count(")")
                continue
            method = re.match(r"fun\s+(\w+)\s*\(", stripped)
            if method:
                if any(annotation.startswith("@Test") for annotation in pending_annotations):
                    annotation_text = "\n".join(pending_annotations)
                    runtime_annotation = re.search(
                        r"@RuntimePermissionState\s*\((.*?)\)", annotation_text, re.DOTALL
                    )
                    permissions = runtime_annotation.group(1) if runtime_annotation else ""
                    method_sdk = max(
                        (int(value) for value in re.findall(r"@SdkSuppress\s*\(\s*minSdkVersion\s*=\s*(\d+)", annotation_text)),
                        default=1,
                    )
                    cases.append(
                        TestCase(
                            class_name=f"{package}.{class_name}",
                            method_name=method.group(1),
                            min_sdk=max(class_sdk, method_sdk),
                            granted=_parse_permission_array(permissions, "granted"),
                            denied=_parse_permission_array(permissions, "denied"),
                        )
                    )
                pending_annotations.clear()
                annotation_parentheses = 0
                continue
            if stripped and not stripped.startswith(("//", "/*", "*")):
                pending_annotations.clear()
                annotation_parentheses = 0
    return cases


def run(adb: str, serial: str, args: list[str], *, capture: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [adb, "-s", serial, *args],
        cwd=ROOT,
        check=False,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.STDOUT if capture else None,
    )


def adb_shell(adb: str, serial: str, *args: str) -> str:
    result = run(adb, serial, ["shell", *args])
    if result.returncode != 0:
        raise RuntimeError(f"adb command failed on {serial}: {' '.join(args)}\n{result.stdout}")
    return result.stdout.strip()


def permission_flags_supported(package_manager_help: str) -> bool:
    return "set-permission-flags" in package_manager_help and "clear-permission-flags" in package_manager_help


def parse_runtime_permissions_output(output: str, sdk: int) -> dict[str, tuple[bool, frozenset[str]]]:
    state: dict[str, tuple[bool, frozenset[str]]] = {}
    for permission in (value[0] for value in RUNTIME_PERMISSIONS.values()):
        match = re.search(
            rf"^\s*{re.escape(permission)}:\s*granted=(true|false),\s*flags=\[([^\]]*)\]",
            output,
            re.MULTILINE,
        )
        if match:
            flags = frozenset(
                "user-set" if flag.strip() == "USER_SET" else
                "user-fixed" if flag.strip() == "USER_FIXED" else
                flag.strip()
                for flag in match.group(2).split("|")
                if flag.strip()
            )
            state[permission] = (match.group(1) == "true", flags)
            continue
        legacy_match = re.search(
            rf"^\s*{re.escape(permission)}:\s*granted=(true|false)\s*$",
            output,
            re.MULTILINE,
        )
        if legacy_match:
            state[permission] = (legacy_match.group(1) == "true", frozenset())

    requested_section = re.search(
        r"^\s*requested permissions:\s*(.*?)^\s*(?:install permissions:|User \d+:)",
        output,
        re.MULTILINE | re.DOTALL,
    )
    requested = set(
        re.findall(r"^\s*(android\.permission\.[A-Z0-9_]+)\s*$", requested_section.group(1), re.MULTILINE)
    ) if requested_section else set()
    for permission, introduced_in in (value for value in RUNTIME_PERMISSIONS.values()):
        if sdk >= introduced_in and permission in requested:
            state.setdefault(permission, (False, frozenset()))
    if sdk == 23:
        for permission in (
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_FINE_LOCATION",
        ):
            state.setdefault(permission, (False, frozenset()))
    return state


def read_runtime_permissions(adb: str, serial: str, package: str) -> dict[str, tuple[bool, frozenset[str]]]:
    output = adb_shell(adb, serial, "dumpsys", "package", package)
    sdk = int(adb_shell(adb, serial, "getprop", "ro.build.version.sdk"))
    return parse_runtime_permissions_output(output, sdk)


def set_permission(adb: str, serial: str, package: str, permission: str, granted: bool) -> None:
    command = "grant" if granted else "revoke"
    result = run(adb, serial, ["shell", "pm", command, package, permission])
    if result.returncode != 0:
        raise RuntimeError(f"Could not set {permission} granted={granted} on {serial}: {result.stdout}")


def set_permission_flags(
    adb: str, serial: str, package: str, permission: str, flags: frozenset[str], supported: bool
) -> None:
    if not supported:
        return
    for flag in ("user-set", "user-fixed"):
        command = "set" if flag in flags else "clear"
        result = run(adb, serial, ["shell", "pm", f"{command}-permission-flags", package, permission, flag])
        if result.returncode != 0:
            raise RuntimeError(f"Could not restore {flag} for {permission} on {serial}: {result.stdout}")


def configure_case(
    adb: str,
    serial: str,
    package: str,
    sdk: int,
    original: dict[str, tuple[bool, frozenset[str]]],
    case: TestCase,
    flags_supported: bool,
) -> None:
    for permission, (was_granted, original_flags) in original.items():
        introduced_in = next(min_sdk for _, (name, min_sdk) in RUNTIME_PERMISSIONS.items() if name == permission)
        if sdk < introduced_in:
            continue
        expected = was_granted
        if permission in case.granted:
            expected = True
        if permission in case.denied:
            expected = False
        set_permission(adb, serial, package, permission, expected)
        desired_flags = original_flags if permission not in case.granted | case.denied else frozenset()
        set_permission_flags(adb, serial, package, permission, desired_flags, flags_supported)


def restore_permissions(
    adb: str,
    serial: str,
    package: str,
    original: dict[str, tuple[bool, frozenset[str]]],
    flags_supported: bool,
) -> None:
    for permission, (was_granted, flags) in original.items():
        set_permission(adb, serial, package, permission, was_granted)
        set_permission_flags(adb, serial, package, permission, flags, flags_supported)


def installed_package(metadata_path: Path) -> str:
    return json.loads(metadata_path.read_text(encoding="utf-8"))["applicationId"]


def selected_devices(adb: str, requested_sdks: set[int], requested_devices: set[str] | None) -> list[tuple[str, int]]:
    output = subprocess.run([adb, "devices", "-l"], cwd=ROOT, check=True, text=True, stdout=subprocess.PIPE).stdout
    devices: list[tuple[str, int]] = []
    for line in output.splitlines()[1:]:
        fields = line.split()
        if len(fields) < 2 or fields[1] != "device" or not fields[0].startswith("emulator-"):
            continue
        serial = fields[0]
        if requested_devices and serial not in requested_devices:
            continue
        sdk_text = adb_shell(adb, serial, "getprop", "ro.build.version.sdk")
        sdk = int(sdk_text)
        if sdk in requested_sdks:
            devices.append((serial, sdk))
    return devices


def visible_pinned_task_packages(activity_dump: str) -> list[str]:
    """Return packages whose visible activity task is currently in Android PiP mode."""
    packages: list[str] = []
    for line in activity_dump.splitlines():
        if not re.search(r"\bTask\{", line) or not re.search(r"\bmode=pinned\b", line):
            continue
        match = re.search(r"\bA=\d+:([A-Za-z0-9_.]+)", line)
        if match and match.group(1) not in packages:
            packages.append(match.group(1))
    return packages


def dismiss_foreground_picture_in_picture_app(
    adb: str, serial: str, protected_packages: set[str]
) -> None:
    """Stop pre-existing PiP tasks that could cover instrumentation test controls."""
    activity_dump = adb_shell(adb, serial, "dumpsys", "activity", "activities")
    for package in visible_pinned_task_packages(activity_dump):
        if package in protected_packages or package.startswith(("android.", "com.android.")):
            continue
        print(f"Stopping pre-existing PiP app before instrumentation: {package}")
        adb_shell(adb, serial, "am", "force-stop", package)


def install_apks(adb: str, serial: str) -> None:
    for apk in (APP_APK, TEST_APK):
        result = run(adb, serial, ["install", "-r", str(apk)])
        if result.returncode != 0:
            raise RuntimeError(f"Could not install {apk.name} on {serial}: {result.stdout}")


def run_case(adb: str, serial: str, sdk: int, test_package: str, case: TestCase, log_path: Path) -> bool:
    test_id = f"{case.class_name}#{case.method_name}"
    command = [
        "shell",
        "am",
        "instrument",
        "-w",
        "-e",
        "class",
        test_id,
        f"{test_package}/androidx.test.runner.AndroidJUnitRunner",
    ]
    result = run(adb, serial, command)
    log_path.write_text(result.stdout or "", encoding="utf-8")
    successful = (
        result.returncode == 0
        and "shortMsg=Process crashed" not in result.stdout
        and re.search(r"\bOK \(\d+ tests?\)", result.stdout) is not None
    )
    if successful:
        print(f"PASS api{sdk} {test_id}")
        return True
    print(f"FAIL api{sdk} {test_id}", file=sys.stderr)
    print(result.stdout[-5000:] if result.stdout else "(no instrumentation output)", file=sys.stderr)
    return False


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", default="35,36", help="comma-separated emulator API levels (default: 35,36)")
    parser.add_argument("--device", action="append", help="restrict to an emulator serial; may be repeated")
    parser.add_argument("--test", action="append", help="run a single Class#method; may be repeated")
    parser.add_argument("--list", action="store_true", help="print discovered test methods and permission states")
    parser.add_argument("--no-build", action="store_true", help="use existing debug and androidTest APKs")
    parsed = parser.parse_args()

    cases = discover_tests()
    if parsed.test:
        requested_tests = set(parsed.test)
        cases = [case for case in cases if f"{case.class_name.rsplit('.', 1)[-1]}#{case.method_name}" in requested_tests]
        missing_tests = requested_tests - {
            f"{case.class_name.rsplit('.', 1)[-1]}#{case.method_name}" for case in cases
        }
        if missing_tests:
            raise RuntimeError(f"Unknown instrumentation test(s): {', '.join(sorted(missing_tests))}")
    if parsed.list:
        for case in cases:
            print(
                f"{case.class_name}#{case.method_name} minSdk={case.min_sdk} "
                f"granted={','.join(sorted(case.granted)) or '-'} denied={','.join(sorted(case.denied)) or '-'}"
            )
        print(f"{len(cases)} instrumentation methods")
        return 0

    if not cases:
        raise RuntimeError(f"No instrumentation tests found under {ANDROID_TEST_ROOT}")

    adb = str(Path.home() / "Library/Android/sdk/platform-tools/adb")
    if not Path(adb).exists():
        adb = "adb"
    requested_sdks = {int(value.strip()) for value in parsed.sdk.split(",") if value.strip()}
    devices = selected_devices(adb, requested_sdks, set(parsed.device) if parsed.device else None)
    found_sdks = {sdk for _, sdk in devices}
    missing = requested_sdks - found_sdks
    if missing:
        raise RuntimeError(f"No online emulator found for requested API(s): {', '.join(map(str, sorted(missing)))}")

    if not parsed.no_build:
        subprocess.run(["./gradlew", ":mobile:assembleDebug", ":mobile:assembleDebugAndroidTest"], cwd=ROOT, check=True)
    if not all(path.is_file() for path in (APP_APK, TEST_APK, APP_METADATA, TEST_METADATA)):
        raise RuntimeError("Debug APK, test APK, or APK metadata is missing; run without --no-build")

    app_package = installed_package(APP_METADATA)
    test_package = installed_package(TEST_METADATA)
    RESULT_ROOT.mkdir(parents=True, exist_ok=True)
    failures = 0
    total = 0
    for serial, sdk in devices:
        dismiss_foreground_picture_in_picture_app(adb, serial, {app_package, test_package})
        install_apks(adb, serial)
        flags_supported = permission_flags_supported(adb_shell(adb, serial, "cmd", "package", "help"))
        original = read_runtime_permissions(adb, serial, app_package)
        if not original:
            raise RuntimeError(f"Could not read runtime permission state for {app_package} on {serial}")
        for case in cases:
            if sdk < case.min_sdk:
                print(f"SKIP api{sdk} {case.class_name}#{case.method_name} (requires API {case.min_sdk})")
                continue
            total += 1
            adb_shell(adb, serial, "am", "force-stop", app_package)
            adb_shell(adb, serial, "am", "force-stop", test_package)
            try:
                configure_case(adb, serial, app_package, sdk, original, case, flags_supported)
                passed = run_case(
                    adb,
                    serial,
                    sdk,
                    test_package,
                    case,
                    RESULT_ROOT / f"{serial}_{case.class_name.rsplit('.', 1)[-1]}_{case.method_name}.log",
                )
                if not passed:
                    failures += 1
            except Exception as error:  # keep running other independent cases
                failures += 1
                print(f"FAIL api{sdk} {case.class_name}#{case.method_name}: {error}", file=sys.stderr)
            finally:
                adb_shell(adb, serial, "am", "force-stop", app_package)
                adb_shell(adb, serial, "am", "force-stop", test_package)
                restore_permissions(adb, serial, app_package, original, flags_supported)

    print(f"Hermetic instrumentation results: {total - failures}/{total} passed")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
