# Pixel test plan — Phase D (REQUIRED before anything touches the phone)

Device: Pixel 8 / 8 Pro, Android 17, daily driver → **no root, no bootloader
unlock, no KernelSU/Magisk, no SELinux changes, no system modification.**
All interaction via `adb` + the official `vm` debug tool + files under
`/data/local/tmp` (world-writable scratch, deleted afterwards).

```
Mac M4 Pro                    Pixel 8 Pro / Android 17
  → UTM/QEMU          →       Android AVF/pKVM (non-protected VM)
  → Arch ARM64        →       SAME Arch image (Image 7.2.6 + ext4 rootfs)
  → virtio-gpu        →       virtio-gpu (PCI, per AVF device model)
  → virgl/ANGLE/Metal →       virgl2 / gfxstream  ← THE UNKNOWN
  → Mesa              →       SAME Mesa (virgl Gallium in image)
  → Hyprland          →       Hyprland (headless proof first)
  → Foot              →       Foot (process-level proof first)
        ✓                         ?
```

## Why physical hardware is now necessary
Everything left unproven is AVF/device-specific and cannot be established
under QEMU-on-Mac (see `docs/research/development-environment.md`):
1. Does `vm run` accept our kernel + Arch rootfs as a custom guest?
2. Which GPU backend does AVF serve a custom guest — documented `virgl2`
   or undocumented gfxstream — and does our Mesa produce a non-llvmpipe
   renderer against it?
3. AVF console/network plumbing for our image (earlycon, ttyS0, TAP).
A pass proves the AVF layer; a fail is attributed to layer 1/2, never to
the guest stack (already proven on Mac).

## Hypotheses (one variable at a time)
- H1: Our `Image` (7.2.6, stock ArchARM, `root=/dev/vda rw rootwait
  console=ttyAMA0,115200`) boots as a non-protected AVF custom guest to a
  systemd login on the serial console. (May additionally need
  `arm64.nompam` and/or `8250.nr_uarts=4 earlycon` per PocketVM notes —
  each tried singly, in that order.)
- H2: With the Debian-style JSON (`disks[]` writable + `network:true`),
  the guest gets virtio-blk/net and reaches multi-user with SSH-able net
  or at least a working interactive serial console.
- H3: With `gpu:{backend:virglrenderer,context_types:[virgl2]}` (+`display`),
  `/dev/dri` appears and `eglinfo` reports `virgl`, not llvmpipe.
  (If virgl2 fails, ONE follow-up: gfxstream backend — only if H1/H2 pass.)
- H4 (contingent): Hyprland starts headless-accelerated; Foot mapped
  (same `dev/test-desktop` proof as Mac, via serial console + hyprctl).

## Required Android version/features
- Pixel 6+ with `ro.boot.hypervisor.vm.supported=1`
  (protected flag irrelevant — all tests `protected:false`).
- `com.android.virt` APEX with `/apex/com.android.virt/bin/vm` present.
- `platform_version:~1.0` (matches our JSON; read back from device if unsure).
- USB debugging authorized; `adb` on Mac (present).

## Root / unlock / modifications: NONE
- No `adb root` initially (PocketVM evidence: `vm run` works as shell
  `u:r:shell:s0`). AOSP android-15 `custom_vm.md` shows `adb root` for the
  headless flow — contradiction flagged: try unrooted first, record result.
  If unrooted `vm run` is refused, STOP and reassess (do not root a daily
  driver silently — ask first).
- No bootloader unlock, no KernelSU/Magisk, no SELinux rules, no app
  install, no permission grants. Only `adb push` to `/data/local/tmp`
  (revertible by `rm`).

## Exactly what will be installed/changed (all under /data/local/tmp)
1. `Image` (our 7.2.6 kernel, ~50 MB) — from `guest/kernel/boot/`.
2. `initramfs-linux.img` (~15 MB) — from `guest/kernel/boot/` (H1 only).
3. `arch-pixel.img` — disk image derived from our qcow2 (H2+; see below).
4. `vm_config.json` variants (H1→H4, one at a time).
Phone system image, bootloader, and user data: untouched.

### Disk image prep (on Mac, before pushing)
Try in order, stop at first AVF accepts:
a. qcow2 as-is (`guest/image/archroot.qcow2`, ~2–4 GB) — PocketVM says the
   app API sniffs Qcow2; `vm` CLI acceptance UNKNOWN, one experiment.
b. raw sparse: `qemu-img convert -O raw`, then `tar -cS` (sparse) per
   AOSP doc, `adb push` tarball, untar on device, `rm` tarball, `chmod a+w`.
Push is one-time (~minutes on USB); revert = `adb shell rm`.

## How to revert
`adb shell rm -f /data/local/tmp/Image /data/local/tmp/initramfs-linux.img
/data/local/tmp/arch-pixel.img /data/local/tmp/vm_config*.json`
plus `adb shell "vm list"` / `vm stop` if anything lingers. No trace left.

## Commands/tests (in order; stop at first blocking failure)
```
# D-0 capability probe (zero modification, establishes baseline)
adb devices  # authorized?
adb shell getprop ro.product.model ro.build.version.release
adb shell getprop ro.boot.hypervisor.vm.supported ro.boot.hypervisor.protected_vm.supported ro.boot.hypervisor.version
adb shell ls -l /apex/com.android.virt/bin/vm
adb shell /apex/com.android.virt/bin/vm help  # record subcommands (run? list? console? stop?)

# D-1 headless kernel boot (H1) — kernel+initrd only, no disk
cat > vm_config_h1.json <<EOF
{"kernel":"/data/local/tmp/Image","initrd":"/data/local/tmp/initramfs-linux.img",
 "params":"root=/dev/vda rw rootwait console=ttyAMA0,115200",
 "protected":false,"platform_version":"~1.0","memory_mib":2048,
 "debuggable":true,"console_out":true,"connect_console":true,"console_input_device":"ttyS0"}
EOF
adb push guest/kernel/boot/Image /data/local/tmp/Image
adb push guest/kernel/boot/initramfs-linux.img /data/local/tmp/initramfs-linux.img
adb push vm_config_h1.json /data/local/tmp/
adb shell /apex/com.android.virt/bin/vm run /data/local/tmp/vm_config_h1.json
# expect: VM starts; console shows kernel boot (or earlycon/MPAM failure → apply ONE fix)
adb shell -t /apex/com.android.virt/bin/vm console  # interactive serial console

# D-2 full Arch boot (H2) — add writable disk + network, initramfs or direct root
# (params root=/dev/vda; if initramfs conflicts, drop initrd field — one variable)
# expect: systemd multi-user on console; ip addr shows TAP interface

# D-3 GPU probe (H3) — D-2 JSON + "gpu":{"backend":"virglrenderer","context_types":["virgl2"]},"display":{"refresh_rate":"30"}
# expect: /dev/dri/card0+renderD128; eglinfo GBM renderer virgl (NOT llvmpipe)
# evidence: run guest equivalents of dev/test-gpu over the serial console

# D-4 desktop (H4, contingent) — same dev/test-desktop flow over serial console
# expect: Hyprland + foot processes, hyprctl clients shows foot (no visible display needed for proof)
```

## What success/failure tells us
- D-0 fail (no hypervisor props / no `vm` binary): device can't do AVF custom
  guests → STOP, reassess (different device or Cuttlefish Phase C first).
- D-1 fail at `vm run` (permission): unrooted path blocked → STOP, ask before
  any privileged attempt (daily-driver constraint).
- D-1 boot fail (panic/silent): apply PocketVM quirks singly
  (`arm64.nompam`, `SERIAL_OF_PLATFORM` already in our kernel,
  `8250.nr_uarts=4 earlycon`); each result updates H1.
- D-2 fail (no blk/net): guest driver vs AVF virtio transport issue → compare
  `vm --dump-device-tree` PCI layout vs our modules; may need initramfs
  module list change (guest-side, non-invasive to phone).
- D-3 virgl2 fail: try `context_types` variants, then gfxstream backend
  (needs guest Mesa gfxstream ICD — check image first); if both fail, record
  exact AVF-served capsets (`dmesg`, `vulkaninfo`) — still valuable data.
- D-3 pass with virgl: **Milestone 1 fully proven under real AVF** (modulo
  protected-VM hardening, explicitly out of scope).
- Any pass: capture console logs to `artifacts/diagnostics/<date>-pixel/`.

## Session 1 results (2026-09-20, Pixel 8 Pro husky, build CP41.260828.004.A8, unrooted)
All D-0→D-2 run with ZERO privileged operations (`vm run` works as shell).
Logs: `artifacts/diagnostics/2026-09-20-pixel/`.

- D-0 ✓: `vm.supported=1`, `protected_vm.supported=1`, `kvm.arm-protected`,
  `/dev/kvm` exists, `vm` tool has run/list/console/info. Contrary to the
  android-15 doc, NO `adb root` needed for any of this.
- D-1 ✓ (after 1 fix): H1 (`console=ttyAMA0`) silent → H2 (`console=ttyS0`)
  boots 7.2.6 to initramfs (`Machine model: linux,dummy-virt`, 1 vCPU).
  Lesson: AVF/crosvm serial is 8250 `ttyS0`, not pl011 `ttyAMA0`. No MPAM
  panic, no `arm64.nompam` needed.
- D-2 ✓ (after 1 fix): qcow2 pushed as-is is opened RAW by `vm run`
  (3.95 GB vda, no superblock → emergency shell). Fix: raw + resize2fs -M
  (4.3 GB) → full Arch boot to multi-user, sshd up, gettys on ttyS0+hvc0.
  Evidence: `h4-console.log` (165 OKs, Graphical Interface reached).
- D-3 ✗ (architectural, not a bug): `gpu:{virglrenderer/virgl2}` (+display)
  SILENTLY IGNORED — arch-h5 crosvm cmdline has no `--gpu`, no `/dev/dri`,
  eglinfo llvmpipe. Same for `network:true` AND `-n` flag (no NIC, only lo)
  and vsock (no device). I.e. unrooted `vm run` serves kernel/initrd/disk/
  serial/balloon only. Console in/out works via `--console/--console-in`
  files; interactive `vm console` needs a TTY (blocked over plain adb).
- In-guest probe that made this possible: `guest/overlays/avf-probe/`
  (`m1-avf-probe.sh` + one-shot service dumping systemd/net/DRM/Mesa to
  both serial consoles → lands in the `--console` log, no shell needed).

## D-3b verdict (2026-09-20 — all four stop-condition questions answered)
1. Accelerated graphics in supported Terminal on THIS Pixel? **NO.**
   Vulkan GPU0 = llvmpipe (LLVM 19.1.7, Mesa 25.0.7); dmesg `-virgl
   -context_init`; both sentinels + ANGLE opt-in inert across VM restarts.
2. Actual path? Terminal app → GpuConfig WITHOUT backend (2D: egl/gles/
   surfaceless, windowed 1280x720 display service, 4 inputs, tap net,
   vsock/ttyd bridge, GuestLog journal) → guest DRM (blob caps, no 3D) →
   llvmpipe. Debian kernel 6.12.92-android16, droid user, passwordless sudo.
3. vm-run vs Terminal difference? See docs/research/vmcli-vs-terminal.md:
   CLI schema cannot express gpu/display/net/input (serde drops); only the
   app/API path provides Surface, TAP, input, vsock. No flag promotes CLI.
4. Smallest credible route to Arch + same GPU? **D-3c: minimal custom APK**
   driving framework VirtualMachineManager custom-image APIs with
   GpuConfig{backend=virglrenderer, context_types=[virgl2]} + DisplayConfig
   (platform supports it; Terminal dex references setGpuConfig). Needs
   Android SDK build env on Mac. Open risk: third-party SELinux surface
   sharing (PocketVM needed KernelSU rules). Check first whether Terminal
   itself accepts custom disk/kernel (keeps priv-app domain).
Evidence: artifacts/diagnostics/2026-09-20-terminal/ (crosvm baseline line,
/dev/dri + llvmpipe + kernel screenshots). Phone reverted (sentinels,
screenshots, ANGLE) except preserved /data/local/tmp staging.
Rationale (layer attribution): current Android documents GPU ONLY via the
Terminal app (`/sdcard/linux/virglrenderer` file + ANGLE for Terminal).
Proving virgl under stock Debian isolates "AVF GPU works on this device"
from "custom Arch guest + GPU" (needs app-API `VirtualMachineManager`
path — PocketVM-style APK, permissions grantable unrooted, SELinux
surface rules TBD). Do NOT push further custom-guest GPU JSONs via
`vm run` — evidence says the tool drops those fields.

## Device staging (left on phone after session 1, ~4.6 GB, all under /data/local/tmp)
m1-Image, m1-initramfs.img, m1-arch.raw (raw Arch rootfs, writable disk),
vm_config_h*.json, vm-h*.log, vm-run*.out. Revert: `adb shell rm -f
/data/local/tmp/m1-* /data/local/tmp/vm_*`. No VMs running (verified via
`vm list`). Nothing outside /data/local/tmp touched.
