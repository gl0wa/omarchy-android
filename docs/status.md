# Status — project memory (update on every meaningful experiment)

## Milestone 2 — active (2026-09-20)
Explicit user authorization now includes Omarchy; older M1 scope exclusions below
are historical. Pixel remains untouched and its host GPU blocker remains deferred.

Baseline revalidated: ArchARM 7.2.6, systemd running, native Wayland Foot clients,
1280x800 Virtual-1, USB pointer/keyboard, and GLX `Accelerated: yes` with
`virgl (ANGLE (Apple, Apple M4 Pro, OpenGL 4.1 Metal - 90.5))`.
Evidence: `artifacts/diagnostics/2026-09-20-m2-baseline/` (GPU, GLX, clients,
input inventory, packages, system). UTM UI input reached Foot.

Rollback: clean guest shutdown, verified disk closed, APFS CoW copy of the entire
UTM bundle to `artifacts/checkpoints/m1-2026-09-20/omarchy-m1.utm`;
`qemu-img check` passed. Reference repository commit
`04caf82f243a7c7e3fc545fa6eddd96dcfed1c32`; boot checksums saved beside bundle.
Reproduce future checkpoints with `./dev/checkpoint-utm NAME` while stopped.
The working disk was expanded offline from 6 to 24 GiB and ext4 grown online;
checkpoint remains at 6 GiB. No kernel or GPU changes at this stage.

Research: current upstream has Quickshell + Foot and Lua Hyprland configuration;
released v4.0.4 and official ARM-aware package recipes are under inspection.
Installation has not yet begun.

## Historical Milestone 1 record
Milestone 1: aarch64 Arch + systemd + net + persistent storage + accelerated
vGPU (non-llvmpipe) + Hyprland + Foot + keyboard/pointer, emulator-first.
**MAC SIDE COMPLETE 2026-09-20** (all criteria met under QEMU/UTM on the
MacBook; real-Android-AVF validation explicitly deferred to Phase D).

## Verified working (2026-09-20, Phase A)
- QEMU 11.1.1 (stock brew) + HVF boots ArchLinuxARM-aarch64 (tarball
  2026-08-05, MD5 `23eec863…`) to systemd `running`, serial login prompt.
- Stock `linux-aarch64` kernel direct-boot (`-kernel Image`,
  `root=/dev/vda`); no custom kernel needed (currently 7.2.6-1).
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
- Desktop harness validated on llvmpipe, then superseded by Phase B:
  Hyprland + Foot run accelerated on virgl (below); the llvmpipe run only
  proved harness mechanics.

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

## Current state (no blocker — next steps are gated on external events)
Milestone 1 (Mac side): DONE. Interactive check passed (typing works,
Super+Return opens Foot; Super+Q unusable = macOS Cmd+Q host collision).
Pixel (Phase D): custom-guest BOOT proven unrooted; 3D BLOCKED by OS build
(see D-3c verdict below). Next: (a) Milestone 2 guest work on Mac whenever
wanted; (b) Pixel GPU retry when a newer OS ships qualified virglrenderer
(`host/apk` reinstall + probe, ~1 evening).
D-3c verdict 2026-09-20 — custom host APK (host/apk, unrooted,
permissions granted, reflection over @hide APIs) proves the app path end to
end AND finds the platform boundary. VERDICT: NO accelerated graphics for
custom guests on this build (CP41.260828.004.A8):
- virglrenderer backend → host crosvm SIGABRT (`Failed to create virtio gpu
  worker thread: invalid rutabaga build parameters`; no virglrenderer .so
  on device). Repeated across 5 runs, all backend attempts die in v_gpu.
- gfxstream backend → VM boots, virtio-gpu present (`+virgl +context_init`,
  2 capsets, fb0, all 5 input devices) BUT host serves zero 3D contexts:
  Mesa `No virgl contexts available on host` → llvmpipe GL 4.6/GLES 3.2;
  Vulkan `Failed to detect any valid GPUs` (no Venus).
- Bonus findings: app path honors match_host → Tensor MPAM panics stock
  kernel at t=0 (`d538a481`) — `arm64.nompam` fixes (PocketVM corroborated);
  app console via getConsoleOutput/getLogOutput streams works (run-as
  readable); TAP still priv-gated for third-party apps.
Phone restored: APK uninstalled, ANGLE + hidden_api_policy reverted, no VMs;
4.6 GB /data/local/tmp staging preserved. Full story: docs/pixel-test-plan.md.

## Evidence (2026-09-20 EOD)
- Host: M4 Pro arm64, macOS 26.6, 48 GB; brew/adb/podman present; stock QEMU
  11.1.1, Android SDK (36 + 37.2-beta2, build-tools 36), JDK 21, Gradle 8.9.
- AVF is ARM64-only; pKVM needs physical hardware (AOSP docs).
- crosvm needs Linux/KVM — cannot run on macOS (moot now: Pixel answers AVF).
- `vm run` JSON has no gpu/display/network fields (serde drops them) —
  code + device-verified, not just documented.
- ArchARM alive; stock kernel has virtio-gpu module; Hyprland/Foot/Mesa/
  vulkan-virtio packaged for aarch64; Hyprland needs GLES 3.0, not Vulkan.
- Pixel 8 Pro / CP41.260828.004.A8, UNROOTED throughout: custom 7.2.6 boot,
  Arch multi-user, ttyS0, raw disks, app/API GPU+display+input config honored
  by virtmgr — but host crosvm has no functional 3D (SIGABRT / zero contexts).

## Current hypothesis (closed)
Was: emulator-first ladder to Pixel GPU. Now: Mac reference COMPLETE;
Pixel 3D is purely a function of OS build (needs shipped virglrenderer).
No open technical unknowns in OUR stack — remaining work is Milestone 2
guest packaging (Mac-provable) + a cheap retry trigger on OS updates.

## Experiments attempted
| date | experiment | result |
|------|------------|--------|
| 2026-09-20 | D-3b: Terminal virglrenderer+gfxstream sentinels, ANGLE opt-in, VM restarts | NO EFFECT (both inert; shipped APK v17 lacks sentinel code — dex-verified). dmesg -virgl -context_init persists. Device + screenshots + ANGLE reverted afterwards |
| 2026-09-20 | D-3b baseline via automated input-text+screencap+vision loop | Debian droid/pwless-sudo, kernel 6.12.92-android16, Mesa 25.0.7, /dev/dri present, Vulkan llvmpipe. Automation lessons: %s+CAPS gets IME-mangled (use keyevent 62 for space); never `--` with input text; MINUS via keyevent 69 |
| 2026-09-20 | D session 1: `vm run` unrooted custom boot | H1 silent (ttyAMA0) → H2 boots (ttyS0): AVF serial is 8250. No MPAM panic on CLI path (default CPU). `linux,dummy-virt`, 1 vCPU |
| 2026-09-20 | D session 1: disk formats | qcow2 opened as RAW bytes (3.95G vda, no superblock → emergency shell). Fix: raw + resize2fs -M (4.3G) → full multi-user + sshd (h4-console.log, 165 OKs) |
| 2026-09-20 | D session 1: in-guest avf-probe service | one-shot dumps systemd/net/DRM/Mesa to ttyS0+hvc0 → lands in --console log; no interactive shell needed. Saved as guest/overlays/avf-probe/ |
| 2026-09-20 | D session 1: D-3 via `vm run` (gpu virgl2 + display JSON, `-n` flag) | gpu AND network AND vsock ALL absent from crosvm line (schema drops them). eglinfo llvmpipe. Verdict: CLI serves blk/console only |
| 2026-09-20 | D-3c: host/apk built (SDK 37.2-beta2, JDK 21, AGP 8.7.3, reflection) | installed + permissions granted unrooted + hidden_api_policy + ANGLE opt-in; VM create/run/console all work; direct /data/local/tmp paths accepted |
| 2026-09-20 | D-3c: TAP via third-party app | FAILED: `Failed to create a TAP interface` → useNetwork(false); priv-gated, revisit separately (not needed for GPU) |
| 2026-09-20 | D-3c: app path without GPU (NOGPU isolation) | console flows; guest PANICS at t=0: MPAM `d538a481` (match_host exposes Tensor MPAM) → `arm64.nompam` fixes (PocketVM corroborated) |
| 2026-09-20 | D-3c: virglrenderer/virgl2 via app | host crosvm SIGABRT every run: `Failed to create virtio gpu worker thread: invalid rutabaga build parameters`; no virglrenderer .so on device |
| 2026-09-20 | D-3c: gfxstream via app (one variable) | boots; virtio-gpu `+virgl +context_init`, 2 capsets, fb0, all inputs — but `No virgl contexts available on host` → llvmpipe; Vulkan finds no GPUs (no Venus). Saved as app-gfxstream-console.log |
| 2026-09-20 | D-3c cleanup | force-stop kills VMs (verified empty); APK uninstalled; ANGLE + hidden_api_policy reverted; 4.6 GB /data/local/tmp staging preserved per instructions |
| 2026-09-20 | D-3b baseline via automated input-text+screencap+vision loop | Debian droid/pwless-sudo, kernel 6.12.92-android16, Mesa 25.0.7, /dev/dri present, Vulkan llvmpipe. Automation lessons: %s+CAPS gets IME-mangled (use keyevent 62 for space); never `--` with input text; MINUS via keyevent 69 |
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

## Next steps (nothing actionable today)
1. Milestone 2 guest work on Mac (Omarchy packaging under UTM) — whenever wanted.
2. Pixel GPU retry trigger: on OS update, pull Terminal APK, grep dex for
   `virglrenderer` → if present, `./host/apk/assemble` + GPU probe (~1 evening).
   Do NOT re-run random `vm run` GPU flags (proven inert).
3. Phase C (Linux crosvm/Cuttlefish): DEPRIORITIZED — Pixel app path answers
   AVF questions directly; revisit only if a KVM-specific behavior needs it.

## Deferred work
Omarchy install/debug, Quickshell, themes, launchers, audio, clipboard,
intents, battery, notifications; protected-VM hardening; TAP for third-party
apps; performance/power (moot until device 3D exists).

## M2 incremental results (2026-09-20)
- Native package layer: ArchARM Quickshell 0.3.1, Qt 6.11.2, UWSM 0.27,
  portals, fonts and shell utilities installed; existing kernel/Mesa/Hyprland
  unchanged. GPU checks before/after remain accelerated.
- Core: pinned upstream v4.0.4 `c668141e9c42b13c80c9ca4ea108e11708c5e8a5`,
  selective unchanged source files in `/usr/share/omarchy`, command symlinks
  in `/usr/local/bin`, Tokyo Night theme, Omarchy font and user configs.
  `Hyprland --verify-config` says config ok; no compositor source patch.
- Session: tty1 development autologin + UWSM; actual Quickshell loaded,
  IPC ping `ok`, themed bar/background/root menu/apps menu render; Foot launched
  both by Super+Return and selecting Apps → Foot. Foot is native Wayland.
  Restarting getty replaces compositor/shell successfully.
- GLX after Omarchy: `Accelerated: yes`, virgl/ANGLE Apple M4 Pro, Mesa 26.2.3.
- UTM intercepts host shortcuts (Cmd+Space; Ctrl+Alt is capture toggle).
  Added Ctrl+Shift+Space menu / A apps / Return terminal / W close. Menu shortcut
  and keyboard selection validated via UTM UI. Built-in stay-awake selected:
  development desktop account has no unlock password, as in M1.
- Expected limitation: Quickshell has no NetworkManager/BlueZ backend in this
  wired networkd VM (network itself works); these UI controls are not enabled.
  Optional bundled apps and system provisioning intentionally not installed.
- Independent review found retry/backup/evidence-path issues; fixed backup guards,
  checkpoint completion marker, unique diagnostics, theme preservation, dynamic UID.
- Reboot and final reproducibility validation pending.
