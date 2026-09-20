#!/bin/bash
# SessionStart hook for Claude Code on the web.
#
# This project builds with the Android SDK, which is fetched from dl.google.com.
# That host is refused by the environment's network policy, so the SDK cannot be
# installed and nothing can be compiled locally. Rather than fail, the hook states
# the situation, re-probes it in case the policy changed, and runs the checks that
# do work without an SDK.
#
# Never fails the session: every path exits 0.
set -uo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
    exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}" || exit 0

echo "Gallery: Android project, Kotlin + Compose, Russian UI."

# Re-probe rather than assume: if the policy is ever widened, say so.
if curl -sS -o /dev/null --max-time 8 -I https://dl.google.com/android/repository/repository2-3.xml 2>/dev/null; then
    echo "  dl.google.com is reachable; the Android SDK can be installed and local builds are possible."
    if [ -n "${ANDROID_HOME:-}" ] && command -v sdkmanager >/dev/null 2>&1; then
        yes | sdkmanager "platforms;android-35" "build-tools;35.0.0" >/dev/null 2>&1 &&
            echo "  SDK packages installed." ||
            echo "  sdkmanager failed; fall back to CI."
    else
        echo "  No ANDROID_HOME or sdkmanager here; install the command line tools before building."
    fi
else
    echo "  dl.google.com is blocked by the network policy: no Android SDK, so gradle assembleDebug,"
    echo "  lintDebug and unit tests CANNOT run locally. Compilation is verified only by GitHub Actions."
fi

echo "  Tag refs cannot be pushed from here (the git proxy answers 403 for refs/tags/*)."
echo "  Release the APK by running the 'Release APK' workflow with a version; it creates the tag."
echo "  Run ./tools/verify.sh before every push: it catches resource, drawable and icon mistakes"
echo "  in seconds instead of a four-minute CI round trip."
echo

if [ -x ./tools/verify.sh ]; then
    ./tools/verify.sh || echo "verify.sh reports problems in the current tree; see above."
fi

exit 0
