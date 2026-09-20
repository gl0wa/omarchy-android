# Status — project memory (update on every meaningful experiment)

## Current milestone
Milestone 1: aarch64 Arch + systemd + net + persistent storage + accelerated
vGPU (non-llvmpipe) + Hyprland + Foot + keyboard/pointer, emulator-first.

## Verified working (2026-09-20, Phase A)
- QEMU 11.1.1 (stock brew) + HVF boots ArchLinuxARM-aarch64 (tarball
  2026-08-05, MD5 `23eec863…`) to systemd `running`, serial login prompt.
- Stock `linux-aarch64` kernel 7.1.6-1 direct-boot (`-kernel Image`,
  `root=/dev/vda`); no custom kernel needed so far.
- virtio-blk (`/dev/vda` 6G qcow2) + virtio-net-pci (DHCP 10.0.2.15, default
  route, external ping + DNS ok). No failed systemd units.
- SSH: `alarm`/`alarm` (password) and `root` via injected ed25519 key
  (`~/.ssh/omarchy_android_ed25519`). Root password login blocked by sshd
  default; minimal image has NO sudo — admin via root key.
- Persistence: marker file survived graceful `systemctl poweroff` → reboot.
- Reproducible via `./dev/build`, `./dev/boot`, `./dev/shell`, `./dev/diagnose`.
- Packaging (no aarch64 gaps): `pacman -Syu` ok after `pacman-key --init` +
  `--populate archlinuxarm`; installed mesa 26.2.3, vulkan-virtio 26.2.3,
  hyprland 0.56.2, foot 1.28.0, mesa-utils, vulkan-tools, pciutils.

## Current blocker
UTM GPU VM (`omarchy-m1`, virtio-gpu-gl-pci, bundle crafted from UTM source
schema) is registered but not started: `utmctl` gets OSStatus -1743
(AppleEvents TCC consent missing). Waiting on one-time macOS Automation
grant (user approved). Fallback: press Start in the UTM window manually.

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
Fastest credible path: Phase A DONE on this Mac → Phase B virgl accel via
UTM (validate `/dev/dri` + Mesa `virgl`, non-llvmpipe) → minimal Hyprland +
Foot in the same VM → Phase C crosvm/Cuttlefish on Linux → Phase D Pixel for
real AVF. QEMU-on-Mac validates packaging; it proves nothing about pKVM.

## Experiments attempted
| date | experiment | result |
|------|------------|--------|
| 2026-09-20 | host inventory + 4-way parallel upstream research | done, recorded in `docs/research/development-environment.md` |
| 2026-09-20 | Phase A-1: stock `brew install qemu`, tarball fetch+MD5, ext4 via podman container (root, no sudo), debugfs key inject, qcow2, direct kernel boot | BOOT OK first try; systemd running; net + DNS ok; SSH ok; poweroff→reboot persistence ok |
| 2026-09-20 | `dev/boot` backgrounding via `eval` + multiline var | FAILED (fd inheritance hung tool call, quotes broke `-append`); fixed with shell function + plain `&` + file redirs |
| 2026-09-20 | root/`root` SSH password | FAILED (sshd default blocks); `alarm`/`alarm` ok, root key ok |
| 2026-09-20 | `pacman-key --init/populate`, `-Syu`, install mesa/hyprland/foot set | OK (mesa 26.2.3, hyprland 0.56.2, foot 1.28.0) |
| 2026-09-20 | UTM GPU path: `host/utm/make-bundle` crafts `omarchy-m1.utm` (virtio-gpu-gl-pci, HVF, kernel/initrd drives, port 2223) from UTM source schema | bundle valid, registered in UTM; start blocked on TCC (-1743) |

## Next experiment
Phase B-2: start `omarchy-m1` in UTM (needs Automation grant), SSH to port
2223, run `M1_SSH_PORT=2223 ./dev/test-gpu` — expect `/dev/dri/card0` +
Mesa `virgl` (NOT llvmpipe). If UTM passes `venus=true` and its QEMU/host
rejects it, disable via `defaults write com.utmapp.UTM QEMUVulkanDriver -int 1`.

## Deferred work
Omarchy install/debug, Quickshell, themes, launchers, audio, clipboard,
intents, battery, notifications; Venus/Vulkan; Cuttlefish; Pixel test plan
(not needed until Phases A–C pass).
