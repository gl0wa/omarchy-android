# omarchy-android — Milestone 1: Arch ARM + accelerated Hyprland under AVF-like VMs

Long-term goal: Omarchy-style Arch Linux ARM64 desktop inside Android via
Android Virtualization Framework (AVF). **Milestone 1 scope is only the
foundation**: aarch64 Arch Linux + systemd + networking + persistent storage +
accelerated virtual GPU (Mesa, NOT llvmpipe) + Hyprland + Foot + input.
Omarchy/Quickshell/themes/audio/clipboard/intents are explicitly out of scope.

## Current state (2026-09-20)
Milestone 1 Mac side COMPLETE. QEMU headless + UTM accelerated paths both
work; see `docs/bringup.md` runbook and `docs/status.md` memory.

## Quickstart (Mac)
Requires: Apple Silicon Mac, Homebrew (`brew install qemu`), UTM, podman.

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
- `docs/` — architecture, status, bringup, research notes
- `dev/` — reproducible host commands (stubs until phases land)
- `guest/` — rootfs/kernel/overlay/image inputs (populated in Phase A)
- `host/` — host-side configs (populated when Linux/Cuttlefish phases land)
- `artifacts/` — ignored diagnostic captures (never committed)

## What is / isn't proven on real AVF
Nothing yet. QEMU-on-Mac phases prove packaging only. Real AVF/pKVM claims
(pKVM isolation, pvmfw, TAP/tethering, virgl2-vs-gfxstream on device) all
REQUIRE the Pixel and are deferred to Phase D with a test plan first.
