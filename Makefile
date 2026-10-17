.DEFAULT_GOAL := help
.PHONY: help doctor build clean emulator-open emulators-open install run test test-unit test-simulator test-runner test-elevation test-elevation-replay compare-elevation test-e2e verify gpx-realtime gpx-fast

APP_ID ?= br.com.helpdev.velocimetroalerta.v2
MAIN_ACTIVITY ?= br.com.helpdev.velocimetroalerta.MainActivity
DEVICE ?= emulator-5564
PORT ?= 5564
AVD ?= Pixel_8_API_36_store_capture
GPX ?= tools/gpx/Pedalada_vespertina.gpx
GPX_FAST_FACTOR ?= 12
TEST_SDKS ?= 24,36
E2E_DEVICES ?= emulator-5554 emulator-5564
DEBUG_APK := mobile/build/outputs/apk/debug/mobile-debug.apk

help: ## Show available development commands
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z0-9_-]+:.*## / {printf "  make %-18s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

doctor: ## Show local build and emulator tool versions
	@java -version
	@python3 --version
	@adb version
	@emulator -version

build: ## Assemble the Android debug APK
	./gradlew :mobile:assembleDebug

clean: ## Remove Gradle build outputs
	./gradlew clean

emulator-open: ## Open or wait for the selected emulator (DEVICE, PORT, AVD)
	@if adb -s "$(DEVICE)" get-state >/dev/null 2>&1; then \
		echo "$(DEVICE) is already online"; \
	else \
		command -v emulator >/dev/null || { echo "Android emulator executable not found" >&2; exit 1; }; \
		echo "Starting $(AVD) on port $(PORT) ($(DEVICE))"; \
		emulator -avd "$(AVD)" -port "$(PORT)" >/tmp/velocimetro-emulator-$(PORT).log 2>&1 & \
	fi
	@adb -s "$(DEVICE)" wait-for-device
	@for attempt in $$(seq 1 120); do \
		if [ "$$(adb -s "$(DEVICE)" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then \
			echo "$(DEVICE) is ready"; exit 0; \
		fi; sleep 1; \
	done; echo "Timed out waiting for $(DEVICE) to boot" >&2; exit 1

emulators-open: ## Open both API 24 and API 36 emulators used by E2E tests
	$(MAKE) emulator-open DEVICE=emulator-5554 PORT=5554 AVD=Velocimetro_API_24
	$(MAKE) emulator-open DEVICE=emulator-5564 PORT=5564 AVD=Pixel_8_API_36_store_capture

install: build ## Install debug APK on DEVICE
	$(MAKE) emulator-open DEVICE="$(DEVICE)" PORT="$(PORT)" AVD="$(AVD)"
	adb -s "$(DEVICE)" install -r "$(DEBUG_APK)"

run: install ## Build, install, and launch the app on DEVICE
	adb -s "$(DEVICE)" shell am start -W -n "$(APP_ID)/$(MAIN_ACTIVITY)"

test-unit: ## Run Android/JVM unit tests
	./gradlew :mobile:testDebugUnitTest

test-simulator: ## Run GPX simulator parser and timing tests
	python3 -m unittest tools.test_gpx_simulator -v

test-runner: ## Run hermetic Android instrumentation runner tests
	python3 -m unittest tools.test_run_hermetic_android_tests -v

test-elevation: ## Run elevation estimator, pause policy, and offline comparator tests
	python3 -m unittest tools.test_analyze_elevation_gpx tools.elevation_validation.test_compare -v
	./gradlew :mobile:testDebugUnitTest --tests '*ElevationEstimatorTest' --tests '*ElevationTrackingSessionTest' --tests '*MonotonicFixDeduplicatorTest' --tests '*AutomaticPauseControllerTest'

test-elevation-replay: ## Replay the three private GPX files through the Kotlin estimator
	@test -f tools/gpx/issue-1/19463623301.gpx -a -f tools/gpx/issue-1/20049386131.gpx -a -f tools/gpx/issue-1/20338280241.gpx || { printf '%s\n' 'Missing private GPX files under tools/gpx/issue-1/'; exit 1; }
	ELEVATION_GPX_DIR=$(CURDIR)/tools/gpx/issue-1 ./gradlew :mobile:testDebugUnitTest --tests '*ElevationGpxReplayTest'

compare-elevation: ## Compare three local Strava GPX files using aggregate output
	python3 -m tools.elevation_validation.compare --format markdown

test: test-simulator test-runner test-elevation test-unit ## Run local unit tests (does not require emulators)

test-e2e: emulators-open ## Run the full hermetic instrumentation suite on API 24 and API 36
	python3 tools/run-hermetic-android-tests.py --sdk "$(TEST_SDKS)" $(foreach device,$(E2E_DEVICES),--device $(device))

verify: test test-e2e ## Run unit tests and full emulator instrumentation suite

gpx-realtime: run ## Launch the app and replay the GPX at its recorded timing (loops until Ctrl+C)
	python3 tools/gpx_simulator.py --gpx "$(GPX)" --device "$(DEVICE)" --speed 1 --loop

gpx-fast: run ## Launch the app and replay the GPX 12x faster (factor configurable)
	python3 tools/gpx_simulator.py --gpx "$(GPX)" --device "$(DEVICE)" --speed "$(GPX_FAST_FACTOR)" --loop
