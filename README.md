# omarchy-android — Milestone 1: Arch ARM + accelerated Hyprland under AVF-like VMs

Long-term goal: Omarchy-style Arch Linux ARM64 desktop inside Android via
Android Virtualization Framework (AVF). **Milestone 1 scope is only the
foundation**: aarch64 Arch Linux + systemd + networking + persistent storage +
accelerated virtual GPU (Mesa, NOT llvmpipe) + Hyprland + Foot + input.
Omarchy/Quickshell/themes/audio/clipboard/intents are explicitly out of scope.

## Current state (2026-09-20)
Research complete; first boot not yet attempted. See:
- `docs/research/development-environment.md` — emulator-first path decision
- `docs/architecture.md` — layer map
- `docs/status.md` — live memory (blocker, hypothesis, next experiment)
- `docs/bringup.md` — runbook (grows as phases land)

## Quickstart (Phase A, Mac)
Requires: Apple Silicon Mac, Homebrew, UTM (or patched QEMU — see bringup).
Arch rootfs download + first headless boot are not yet automated; follow
`docs/bringup.md`. Once automated, the interface will be:

```sh
./dev/build        # fetch/assemble minimal Arch ARM guest artifacts
./dev/boot         # boot headless ARM64 VM (HVF, virtio-blk/net)
./dev/shell        # SSH into the guest
./dev/diagnose     # collect dmesg/systemd/net/storage snapshot
./dev/test-gpu     # DRM/Mesa/EGL/Vulkan report (must NOT be llvmpipe)
./dev/test-desktop # launch minimal Hyprland + Foot, capture logs
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
