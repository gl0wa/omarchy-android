# omarchy-android — Milestone 1: Arch ARM + accelerated Hyprland under AVF-like VMs

Long-term goal: Omarchy-style Arch Linux ARM64 desktop inside Android via
Android Virtualization Framework (AVF). **Milestone 1 scope is only the
foundation**: aarch64 Arch Linux + systemd + networking + persistent storage +
accelerated virtual GPU (Mesa, NOT llvmpipe) + Hyprland + Foot + input.
Omarchy/Quickshell/themes/audio/clipboard/intents are explicitly out of scope.

## Current state (2026-09-20)
Milestone 1 Mac side COMPLETE (virgl + Hyprland + Foot + input under UTM).
Pixel side: custom Arch boots unrooted under AVF/pKVM (app + `vm` paths),
BUT this OS build (CP41.260828.004.A8) has no functional custom-guest 3D
(virglrenderer→crosvm SIGABRT, gfxstream→zero contexts) — Hyprland on Pixel
blocked on the OS, not on our stack. See `docs/bringup.md`,
`docs/status.md`, `docs/pixel-test-plan.md`.

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
./dev/test-desktop # minimal Hyprland + Foot via seatd/desktop user, captures logs
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
