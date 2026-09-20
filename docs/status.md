# Status — project memory (update on every meaningful experiment)

## Current milestone
Milestone 1: aarch64 Arch + systemd + net + persistent storage + accelerated
vGPU (non-llvmpipe) + Hyprland + Foot + keyboard/pointer, emulator-first.

## Verified working
- (nothing booted yet) Research + host inventory + path decision only.

## Current blocker
No ARM64 guest has booted yet. Immediate need: QEMU aarch64 provider on this
Mac (stock brew QEMU not installed; UTM present but not scripted; patched
`qemu-virgl` tap not installed).

## Evidence
- Host: M4 Pro arm64, macOS 26.6, 48 GB; brew/adb/podman present; no
  qemu/emulator/SDK. See `docs/research/development-environment.md`.
- AVF is ARM64-only; pKVM needs physical hardware (AOSP docs).
- crosvm needs Linux/KVM — cannot run on macOS.
- Custom AVF guests document virglrenderer/virgl2; gfxstream undocumented.
- ArchARM alive; stock `linux-aarch64` has virtio-gpu as module; Hyprland/
  Foot/Mesa/vulkan-virtio all packaged for aarch64; Hyprland needs GLES 3.0,
  not Vulkan.

## Current hypothesis
Fastest credible path: Phase A headless ArchARM under QEMU/HVF on this Mac →
Phase B virgl accel via `qemu-virgl` tap or UTM → Phase C crosvm/Cuttlefish
on Linux → Phase D Pixel for real AVF. QEMU-on-Mac validates packaging; it
proves nothing about pKVM.

## Experiments attempted
| date | experiment | result |
|------|------------|--------|
| 2026-09-20 | host inventory + 4-way parallel upstream research | done, recorded in `docs/research/development-environment.md` |

## Next experiment
Phase A-1: install a QEMU provider and boot ArchARM to systemd login prompt
(headless, virtio-blk/net). Candidates: (a) `brew install qemu` (stock,
software-GPU, fine for headless), (b) `knazarov/qemu-virgl/qemu-virgl`
(patched, needed for Phase B anyway), (c) script UTM via `utmctl`.
Decide (lean: patched tap directly so Phase B needs no reinstall), fetch
`ArchLinuxARM-aarch64-latest.tar.gz` + verify `.sig`, assemble qcow2, boot
`virt`+HVF headless, capture console + `dev/diagnose`.

## Deferred work
Omarchy install/debug, Quickshell, themes, launchers, audio, clipboard,
intents, battery, notifications; Venus/Vulkan; Cuttlefish; Pixel test plan
(not needed until Phases A–C pass).
