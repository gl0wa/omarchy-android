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

## Test session checklist (day of)
- [ ] Phone charged 50%+, USB-C data cable, `adb devices` authorized
- [ ] Mac has current `guest/kernel/boot/*` + `guest/image/archroot.qcow2`
- [ ] Run D-0 → paste results here before pushing anything
- [ ] One hypothesis at a time; logs after every step; update `docs/status.md`
