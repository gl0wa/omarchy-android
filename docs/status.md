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

## Current blocker
No GPU in the VM yet: stock brew QEMU has no `virtio-gpu-gl` (displays:
none/curses/cocoa/dbus; GPU devices: plain `virtio-gpu-pci` only), so guest
has no `/dev/dri` and Mesa would be llvmpipe. Phase B needs a GL-capable
QEMU — UTM (installed, bundles ANGLE/Metal virgl patches) is the candidate;
`knazarov/qemu-virgl` tap rejected (pins QEMU from Dec 2021, source build).

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

## Next experiment
Phase B-1: `pacman -Syu` + install Mesa/Hyprland/Foot packaging set
(`mesa vulkan-virtio vulkan-icd-loader libdrm wayland mesa-utils
vulkan-tools hyprland xorg-xwayland foot foot-terminfo ttf-dejavu pciutils`)
in the running headless VM (packaging validation needs no GPU), then boot
the same qcow2 under UTM with `virtio-gpu-gl` and run `./dev/test-gpu`.

## Deferred work
Omarchy install/debug, Quickshell, themes, launchers, audio, clipboard,
intents, battery, notifications; Venus/Vulkan; Cuttlefish; Pixel test plan
(not needed until Phases A–C pass).
