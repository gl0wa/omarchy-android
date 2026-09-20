# Milestone 2 proposal: Omarchy ARM (NOT started — no implementation yet)

## Milestone 1 recap (Mac side, 2026-09-20)
Proven under QEMU/UTM on Apple Silicon: ArchARM aarch64 + systemd + DHCP net +
qcow2 persistence + SSH + Mesa virgl (ANGLE/Metal, GLES 3.0, non-llvmpipe) +
Hyprland + Foot + USB keyboard/pointer. Reproducible via `dev/*`.

## Explicitly NOT validated on real Android AVF (all requires Pixel, Phase D)
- pKVM isolation, protected-VM boot, pvmfw/DICE, per-VM secrets
- `VirtualizationService` / `vm run` with our kernel+rootfs
- TAP/tethering, single-VM `avf_tap_fixed` limit, SELinux vsock/Surface rules
- virgl2 vs gfxstream availability/capability/perf on device
- `arm64.nompam` / `SERIAL_OF_PLATFORM` quirks, `dummy-virt` DTB behavior
- Anything about power, thermals, or 16K pages on device

## Proposed Milestone 2 scope: Omarchy-style desktop on the proven base
1. **Guestische image pipeline**: ArchARM + Omarchy ARM packages (Hyprland
   ecosystem, Quickshell, themes, launcher) installed reproducibly into the
   qcow2 via an overlay + package list (extend `guest/overlays/`), still
   validated under UTM first.
2. **Input polish**: remap conflicting binds (Super+Q vs macOS Cmd+Q),
   pointer/keyboard layout, display scaling for the virtual monitor.
3. **AVF packaging spike (needs Linux, Phase C)**: custom kernel/initrd JSON
   for `vm run`, crosvm `--gpu backend=virglrenderer` parity check vs UTM.
4. **Pixel test plan first** (`docs/pixel-test-plan.md` per AGENTS.md), then
   Phase D: pick virgl2 vs gfxstream guest stack based on on-device evidence.

## Deliberately out of Milestone 2
Audio, clipboard sharing, Android intents, battery, notifications, GPU
performance tuning, protected-VM hardening. Those follow once the desktop
boots on device.
