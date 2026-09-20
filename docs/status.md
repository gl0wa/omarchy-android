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
- Tarball GPG-verified: good sig from `Arch Linux ARM Build System`, key
  `68B3537F…2BDBE6A6` (fingerprint match; WoT N/A). `dev/build` verifies in-container.
- Kernel-sync procedure: guest `-Syu` 7.1.6→7.2.6 broke net (modules vs booted
  kernel); `dev/sync-kernel` dumps /boot from qcow2 offline. Booting 7.2.6 now.
- 2D negative control (`M1_GPU=2d`, virtio-gpu-pci): `/dev/dri/card0` +
  `renderD128` appear; `dev/test-gpu` reports llvmpipe GLES 3.2 (expected —
  proves harness + DRM path, NOT the milestone).
- Desktop harness validated on llvmpipe: Hyprland starts (DRM backend,
  Xwayland up), `foot` client mapped+visible, native Wayland (`xwayland: 0`),
  `hyprctl clients` works via seatd + `desktop` user. Renderer is the ONLY
  missing piece (must become virgl, not llvmpipe).

## Verified working (2026-09-20, Phase B — ACCELERATED)
- UTM `omarchy-m1` (8G RAM, virtio-gpu-gl-pci, HVF) boots ArchARM 7.2.6.
  (Two automation routes exhausted earlier: `utmctl` TCC -1743 with no
  prompt; `QEMULauncher` SIGTRAP as XPC binary. Manual Start worked.)
- `dev/test-gpu` on port 2223: Mesa **`virgl (ANGLE (Apple, Apple M4 Pro,
  OpenGL 4.1 Metal))`**, GLES 3.0 — NOT llvmpipe. Graphics criterion MET.
  (Vulkan: no valid GPUs — venus unavailable on this UTM path; Hyprland
  doesn't need Vulkan.)
- `dev/test-desktop` on virgl: Hyprland + Xwayland + `foot` (mapped, visible,
  native Wayland `xwayland: 0`); monitor Virtual-1 1280x800 active.
- Input stack: libinput sees `qemu-usb-tablet`, `qemu-usb-mouse`,
  `qemu-usb-keyboard` (+gpio-keys).

## Current blocker
Final interactive confirmation needs human eyes/hands (one minute): the UTM
window should now show Hyprland + Foot instead of tty1. Click in the window,
type in Foot, try Super+Return (new Foot) / Super+Q (close). Report result —
that closes Milestone 1 on the Mac side (Pixel/AVF validation stays Phase D).

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
| 2026-09-20 | OBS: SSH banner timeout after -Syu → HYPO: kernel/modules mismatch (7.1.6 booted, 7.2.6 on disk, virtio_net=m) → EXP: `dev/sync-kernel` + reboot | CONFIRMED: 7.2.6 boots, net back. Procedure documented |
| 2026-09-20 | `M1_GPU=2d` + `dev/test-gpu` | /dev/dri ok, llvmpipe as expected (negative control); fixed verdict false-positive (PCI desc ≠ renderer) |
| 2026-09-20 | `QEMULauncher` direct exec to bypass UTM app | FAILED (SIGTRAP in secinit — XPC-service binary, not directly runnable) |
| 2026-09-20 | Manual UTM Start of `omarchy-m1` | FAILED: `Could not open 'rw'` — root cause: UTM splits AdditionalArguments on whitespace, our `-append` value split into separate argv tokens; fixed (double-quote wrap) + stable VM UUID |
| 2026-09-20 | Same error after fix; UTM overview still showed 2 GB vs 8 GB on disk | ROOT CAUSE: running UTM caches parsed config, never re-reads file → quit (Cmd+Q) + reopen fixed it. Lesson: always verify displayed config after rebuild |
| 2026-09-20 | `M1_SSH_PORT=2223 ./dev/test-gpu` on UTM GPU VM | **ACCELERATED: `virgl (ANGLE (Apple M4 Pro, OpenGL 4.1 Metal))`, GLES 3.0. Milestone graphics criterion MET.** Vulkan: no GPUs (no venus on this path; not required) |
| 2026-09-20 | `M1_SSH_PORT=2223 ./dev/test-desktop` on virgl | Hyprland + Xwayland + foot (native Wayland, mapped/visible); libinput: usb-tablet/mouse/keyboard present |
| 2026-09-20 | `dev/test-desktop` on llvmpipe (harness validation) | Hyprland UP, Xwayland UP, foot mapped+visible native Wayland; fixed 3 script bugs (chmod /run/user/0 via set -eu; RUNDIR as root; nested quoting → provisioned launcher file) |

## Next experiment
Phase B-2: ONE manual Start of `omarchy-m1` in UTM → `M1_SSH_PORT=2223
./dev/test-gpu` (expect Mesa `virgl`, `Accelerated: yes`) → `M1_SSH_PORT=2223
./dev/test-desktop` (same harness, accelerated) → keyboard/pointer check.
If UTM passes `venus=true` and its QEMU/host rejects it, disable via
`defaults write com.utmapp.UTM QEMUVulkanDriver -int 1`.

## Deferred work
Omarchy install/debug, Quickshell, themes, launchers, audio, clipboard,
intents, battery, notifications; Venus/Vulkan; Cuttlefish; Pixel test plan
(not needed until Phases A–C pass).
