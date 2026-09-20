# M1 host APK (D-3c) — minimal custom-guest launcher for the Pixel.
#
# What: android app driving framework VirtualMachineManager custom-image APIs
# (same calls Terminal makes) with OUR Arch kernel+disk + GpuConfig
# virglrenderer/virgl2. All virtualization APIs via reflection (they are
# @hide/SystemApi, absent from android.jar). No AndroidX, Java only.
#
# Requires (host): JDK 21 (/opt/homebrew/opt/openjdk@21), Android SDK with
# platforms;android-36 + build-tools;36.0.0 (see pixel bringup notes).
# Requires (device, all reversible, unrooted): install, two permission grants,
# hidden_api_policy=1, ANGLE opt-in for our package (virgl host GL).
#
# Build & deploy: ./assemble (uses Gradle wrapper, downloads dist on first run)
# Then: adb shell am start -n dev.omarchy.m1host/.MainActivity (auto-starts VM)
# Logs: `adb logcat -s M1HOST` and in-app text view; console via run-as
# (app is debuggable): run-as dev.omarchy.m1host cat files/vm-console.log
