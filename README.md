# omarchy-android — accelerated Omarchy on ARM64

**Milestone 2 core desktop complete (2026-09-21):** upstream Omarchy 4.0.4,
Quickshell, Hyprland, Foot, launcher, themes and keyboard shortcuts run on the
existing Arch Linux ARM UTM VM and return after reboot. Mesa remains
`virgl (ANGLE / Apple M4 Pro / Metal)`, **Accelerated: yes**.

This is a minimal desktop profile, not the full Omarchy ISO application suite.
See [bring-up](docs/bringup.md#milestone-2-omarchy-arm64-on-the-existing-utm-vm),
[ARM64 matrix and deviations](docs/research/omarchy-arm64.md), and
[project evidence](docs/status.md). The M1 reference has an APFS CoW rollback copy.

Pixel custom Arch boot works unrooted, but build CP41.260828.004.A8 has no working
custom-guest 3D backend. No Pixel changes were made during M2. Retest only after
an appropriate OTA; [evidence probe](docs/research/pixel-probe-usage.md).

```sh
./dev/install-omarchy packages  # after the documented M1 checkpoint
./dev/install-omarchy core
./dev/install-omarchy session   # starts/restarts the desktop
./dev/test-omarchy              # actual desktop + accelerated GLX validation
```

The active disk is inside the UTM bundle. Do not rebuild that bundle from the
older guest/image disk over this completed environment.

## Quickstart (Mac)
Requires: Apple Silicon Mac, Homebrew (`brew install qemu`), UTM, podman,
JDK 21 (`brew install openjdk@21`), Android SDK (`~/Library/Android/sdk`
via cmdline-tools; platforms android-36 + android-37.2-beta2, build-tools 36).

```sh
./dev/build        # fetch ArchARM tarball (GPG-verified), assemble ext4→qcow2, stock kernel
./dev/boot         # headless ARM64 VM (HVF, virtio-blk/net); M1_GPU=2d adds unaccelerated virtio-gpu
./dev/shell        # SSH as root (key auth); M1_SSH_PORT=2223 for the UTM VM
./dev/diagnose     # systemd/net/storage/kernel snapshot
./dev/sync-kernel  # re-extract /boot from qcow2 after any in-guest kernel upgrade (REQUIRED before next boot)
./dev/test-gpu     # DRM/Mesa/EGL/Vulkan report (must NOT be llvmpipe)
./dev/test-desktop # M1 only; refuses to overwrite installed Omarchy
host/utm/make-bundle  # (re)build omarchy-m1.utm from current qcow2; then Cmd+Q/reopen UTM, Start
```

## Layout
- `docs/` — architecture, status, bringup, pixel test plan, research notes
- `dev/` — reproducible VM commands (build/boot/shell/diagnose/sync-kernel/test-gpu/test-desktop)
- `guest/` — rootfs inputs, kernel boot files, overlays (desktop, avf-probe)
- `host/utm/` — UTM bundle generator; `host/apk/` — Pixel host APK (gradle, `assemble`)
- `artifacts/` — ignored diagnostic captures (never committed)

## What is / isn't proven on real AVF (Pixel 8 Pro, CP41.260828.004.A8)
PROVEN unrooted: custom 7.2.6 kernel boot, Arch multi-user + sshd, ttyS0
console, raw disks, app/API path with display+input config, MPAM quirk +
fix, console/log stream capture. BLOCKED by OS build: any custom-guest 3D
(virglrenderer SIGABRTs host crosvm; gfxstream serves no contexts) —
Terminal's own GPU is backend-less 2D. Retry 3D on newer OS builds.
