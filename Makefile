# ==============================================================================
# Luteal - Build & Release Automation
# ==============================================================================

# Extract version metadata from app/build.gradle.kts
VERSION      := $(shell grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts)
VERSION_CODE := $(shell grep -oP 'versionCode\s*=\s*\K[0-9]+' app/build.gradle.kts)
COMMIT_HASH  := $(shell git rev-parse HEAD 2>/dev/null)
BRANCH       := $(shell git rev-parse --abbrev-ref HEAD 2>/dev/null)

# Locate apksigner in Android SDK or system PATH
APKSIGNER    := $(shell which apksigner 2>/dev/null || find $(ANDROID_HOME) $(ANDROID_SDK_ROOT) $(HOME)/Android/Sdk -name apksigner -type f 2>/dev/null | sort -V | tail -n 1)

DIST_DIR     := dist
RELEASE_APK  := $(DIST_DIR)/luteal-v$(VERSION).apk
ADB          ?= adb
DEVICE       ?=
ANCHOR_DATE  ?= $(shell date -u +%F)

.PHONY: help dev dev-install release-seed release-build release-check bump test clean

help:
	@echo "Luteal Build & Release Targets:"
	@echo "  make dev             - Build debug APK (fr.luteal.app.dev, Luteal Dev)"
	@echo "  make dev-install     - Build and install debug APK onto connected device"
	@echo "  make release-seed    - Generate demo backup and open release import preview"
	@echo "  make release-build   - Build signed release APK -> $(RELEASE_APK) and print F-Droid metadata"
	@echo "  make release-check   - Verify signature and Git metadata of $(RELEASE_APK)"
	@echo "  make bump VERSION=1.x.y CODE=n - Update versionName and versionCode in build.gradle.kts"
	@echo "  make test            - Run unit test suite"
	@echo "  make clean           - Clean Gradle build outputs and $(DIST_DIR)/"

# --- Development ---

dev:
	@echo "==> Building debug APK (Luteal Dev)..."
	./gradlew assembleDebug

dev-install:
	@echo "==> Installing debug APK (Luteal Dev) on connected device..."
	./gradlew installDebug
	@echo "==> Installed package: fr.luteal.app.dev"

release-seed:
	@bash ./scripts/seed-release.sh "$(ADB)" "$(DEVICE)" "fr.luteal.app" "$(ANCHOR_DATE)"

# --- Testing ---

test:
	@echo "==> Running unit tests..."
	./gradlew testDebugUnitTest

# --- Release ---

release-build:
	@echo "==> Checking release prerequisites..."
	@if [ ! -f keystore.properties ]; then \
		echo "ERROR: keystore.properties not found in project root. Cannot produce signed release."; \
		exit 1; \
	fi
	@if [ -n "$$(git status --porcelain)" ]; then \
		echo "WARNING: Working tree has uncommitted changes! Release might not match repository state."; \
	fi
	@echo "==> Building Release APK for version $(VERSION) (code $(VERSION_CODE))..."
	./gradlew assembleRelease
	@mkdir -p $(DIST_DIR)
	@cp app/build/outputs/apk/release/*.apk $(RELEASE_APK)
	@echo ""
	@echo "==> Release APK generated: $(RELEASE_APK)"
	@echo ""
	@$(MAKE) --no-print-directory release-check

release-check:
	@if [ ! -f "$(RELEASE_APK)" ]; then \
		echo "ERROR: Release APK not found at $(RELEASE_APK)"; \
		exit 1; \
	fi
	@echo "==> Verifying $(RELEASE_APK)..."
	@echo "--- Git Commit in APK ---"
	@unzip -p $(RELEASE_APK) META-INF/version-control-info.textproto 2>/dev/null || echo "No VCS info found."
	@echo "--- APK Signature & Fingerprint ---"
	@if [ -n "$(APKSIGNER)" ] && [ -x "$(APKSIGNER)" ]; then \
		$(APKSIGNER) verify --print-certs $(RELEASE_APK) | grep -E "Signer #|SHA-256"; \
	else \
		echo "apksigner not found; skipping certificate fingerprint output."; \
	fi
	@echo ""
	@echo "================================================================="
	@echo "  F-Droid Metadata Block (for fdroiddata/metadata/fr.luteal.app.yml):"
	@echo "================================================================="
	@echo "    - versionName: $(VERSION)"
	@echo "      versionCode: $(VERSION_CODE)"
	@echo "      commit: $(COMMIT_HASH)"
	@echo "      subdir: app"
	@echo "      gradle:"
	@echo "        - yes"
	@echo "================================================================="

# --- Version Bumping ---

bump:
	@if [ -z "$(VERSION)" ] || [ -z "$(CODE)" ]; then \
		echo "Usage: make bump VERSION=1.2.2 CODE=6"; \
		exit 1; \
	fi
	@echo "==> Bumping to versionName=$(VERSION), versionCode=$(CODE)..."
	@sed -i -E 's/versionCode = [0-9]+/versionCode = $(CODE)/' app/build.gradle.kts
	@sed -i -E 's/versionName = "[^"]+"/versionName = "$(VERSION)"/' app/build.gradle.kts
	@echo "==> Updated app/build.gradle.kts."
	@git diff app/build.gradle.kts

# --- Cleaning ---

clean:
	./gradlew clean
	rm -rf $(DIST_DIR)
