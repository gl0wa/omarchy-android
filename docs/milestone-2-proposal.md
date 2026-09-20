# Milestone 2 proposal: Omarchy ARM guest work on Mac + Pixel retry trigger
(Status 2026-09-20: M1 Mac side complete; Pixel 3D blocked on OS build —
this proposal updated accordingly. No implementation yet.)

## Milestone 1 recap (Mac side, 2026-09-20)
Proven under QEMU/UTM on Apple Silicon: ArchARM aarch64 + systemd + DHCP net +
qcow2 persistence + SSH + Mesa virgl (ANGLE/Metal, GLES 3.0, non-llvmpipe) +
Hyprland + Foot + USB keyboard/pointer. Reproducible via `dev/*`.

## Pixel results that reshape Milestone 2 (see docs/pixel-test-plan.md)
- PROVEN unrooted on Pixel 8 Pro / CP41.260828.004.A8: custom kernel boot,
  Arch multi-user, ttyS0 console, raw disks, full app/API path (display,
  input, console streams), MPAM quirk + fix.
- BLOCKED: custom-guest 3D on this build (virglrenderer→SIGABRT,
  gfxstream→zero contexts; Terminal itself is 2D-only). Retry when a newer
  OS ships qualified virglrenderer (watch Terminal APK for the sentinel).
- Therefore Milestone 2 splits: (a) guest-side Omarchy work, fully provable
  on Mac NOW; (b) device enablement, gated on OS update, with `host/apk`
  ready to re-test in one install.

## Explicitly NOT validated on real Android AVF (open, needs newer OS / later work)
- pKVM isolation, protected-VM boot, pvmfw/DICE, per-VM secrets
- TAP/tethering at scale for third-party apps (priv-gated on this build)
- virgl2/gfxstream 3D contexts on device (absent on this build)
- Performance/power characteristics (moot until 3D exists)

## Proposed Milestone 2 scope
1. **Guest Omarchy image pipeline on Mac** (unblocked NOW): ArchARM + Omarchy
   ARM packages into the qcow2 via overlays + package list, validated under
   UTM with virgl — same harness as M1.
2. **Input/display polish on Mac**: keybind conflicts, scaling, layouts.
3. **Device retry trigger (no active work)**: when an OS update lands, check
   Terminal APK for `virglrenderer` strings → reinstall `host/apk` → rerun
   GPU probe. One evening, not a project.
4. **Pixel test plan stays** (`docs/pixel-test-plan.md`) as the device runbook.

## Deliberately out of Milestone 2
Audio, clipboard sharing, Android intents, battery, notifications, GPU
performance tuning, protected-VM hardening. Those follow once the desktop
boots on device.
