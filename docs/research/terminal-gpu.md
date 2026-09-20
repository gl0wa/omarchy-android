# Terminal-app GPU mechanism (AOSP main ≈ Android 17, researched 2026-09-20)

All primary-source (android.googlesource.com + ANGLE docs). `main` branch ≈
what ships in Android 17 QPRs; nothing QPR2-specific found.

## How Terminal enables GPU (host side)
`VmLauncherService.overrideConfigIfNecessary()` (TerminalApp):
- Sentinel file `/sdcard/linux/virglrenderer` (presence only, contents never
  read; `getSdcardPathForTesting()` = public external-storage dir `linux`).
  On hit → `GpuConfig{backend=virglrenderer, egl=true, gles=true, glx=false,
  surfaceless=true, vulkan=false, contextTypes=[virgl2]}` + "VirGL is enabled" toast.
- Sibling sentinel `.../gfxstream` → gfxstream backend (egl/gles/glx off,
  vulkan on, contexts gfxstream-vulkan+composer; code comment says config
  comes from Cuttlefish — emulator-oriented, treat as fallback only).
- Display + keyboard/mouse/touch injected ONLY if `Flags.terminalGuiSupport()`
  (aconfig) AND a display surface exists. `DisplayActivity` (two SurfaceViews,
  main + cursor) pushes surfaces via `ICrosvmAndroidDisplayService.setSurface()`.
- ANGLE is a documented manual opt-in for the Terminal package:
  `settings put global angle_debug_package org.chromium.angle`,
  `angle_gl_driver_selection_pkgs com.android.virtualization.terminal`,
  `angle_gl_driver_selection_values angle`.
  Rationale (inference): host virglrenderer needs GLES; ANGLE routes the
  Terminal process's GL through ANGLE→Vulkan→Tensor driver. Code carries
  `TODO: check if ANGLE is enabled` — sentinel alone triggers GPU config.
- `enable_display` is GUEST-side (string appears nowhere in TerminalApp):
  a script inside the guest image; contents unknown until we inspect Debian.

Sources:
- `.../Virtualization/+/main/docs/custom_vm.md` ("Run GUI apps", "Hardware acceleration")
- `.../android/TerminalApp/java/com/android/virtualization/terminal/VmLauncherService.kt`
- `.../android/TerminalApp/java/com/android/virtualization/terminal/ImageArchive.kt`
- `.../android/TerminalApp/java/com/android/virtualization/terminal/{MainActivity,DisplayActivity,DisplayProvider}.kt`
- `chromium/angle.git +/HEAD/doc/DevSetupAndroid.md`
- `source.android.com/docs/core/virtualization/usecases` (Terminal disabled by default; Debian image from Google servers; non-protected VM; ttyd web terminal; tethering network)

## Why `vm run` can never do GPU (code-verified — closes our D-3 mystery)
- CLI JSON schema `libs/vmconfig/src/lib.rs::VmConfig` has NO gpu/display/
  network/input/sharedPath fields; serde has no `deny_unknown_fields` →
  our `gpu`/`network` keys were silently dropped.
- `to_parcelable()` → `gpuConfig=None, displayConfig=None, networkSupported=false`.
- virtmgr `crosvm.rs` emits `--gpu=backend=...,context-types=...` (+
  `--gpu-display`, `--android-display-service`) ONLY when gpuConfig present.
- Only the app/API path (framework `VirtualMachineCustomImageConfig`) can
  set GpuConfig/DisplayConfig/networkSupported + supply the app-window
  `Surface` + input forwarding + TAP + vsock/ttyd port machinery.
- Terminal VM config source: its own `ConfigJson.kt` (kernel/initrd/disks/
  network/input/audio/display/gpu/sharedPath) → framework Builders.
  `enable_display` origin: guest image, not AOSP host code.

## Version notes
- `main` docs: native GUI via `source enable_display` + Display button.
- android15-qpr2 docs: older Wayland/VNC workaround (sway/wayvnc/xwayland).
- No `VmLauncherApp` in current tree (Terminal-only Debian flow now).
- No `persist.*`/`ro.*` GPU switch; no DeviceConfig for GPU in TerminalApp.
- Pixel 6/6Pro note: `fastboot oem pkvm enable` (not relevant to Pixel 8).

## Implication for D-3c (custom Arch + GPU)
Candidates, smallest-first:
1. Terminal provisions Debian; GPA path is per-VM config + sentinel + ANGLE.
   If Terminal accepts a custom kernel/disk while keeping ITS config
   pipeline, Arch inherits GPU. (Check Terminal settings/custom-image support.)
2. Else: minimal custom APK driving `VirtualMachineManager` custom-image APIs
   (same calls Terminal makes), permissions `MANAGE/USE_CUSTOM_VIRTUAL_MACHINE`
   are development-granted via adb — unrooted OK. Open question: SELinux
   surface sharing for third-party app (PocketVM needed KernelSU rules).
3. Else: adapt/fork PocketVM (already solves app+JNI+SELinux, needs root for
   its SELinux module — conflicts with daily-driver constraint; last resort).
