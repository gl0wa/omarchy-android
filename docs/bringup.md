# Bringup runbook (grows as phases land)

## Phase A-1: first headless Arch ARM boot (NOT YET DONE)

Planned steps (Mac, Apple Silicon):
1. Install QEMU provider. Lean: patched virgl build so Phase B needs no
   reinstall (needs approval — installs user-level brew formulae):
   `brew install knazarov/qemu-virgl/qemu-virgl`
   Fallback headless-only: `brew install qemu`. Fallback GUI: UTM manual VM.
2. Fetch + verify rootfs (GPG key `68B3537F39A313B3E574D06777193F152BDBE6A6`):
   `http://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz`
   (+`.md5`, +`.sig`), per https://archlinuxarm.org/platforms/armv8/generic
3. Assemble qcow2 (`qemu-img create/convert`), extract tarball with
   `bsdtar -xpf` as root, seed virtio/net/ssh config.
4. Boot `qemu-system-aarch64 -M virt,accel=hvf -cpu host -kernel <Image> …`
   headless (`-nographic` or `-display none -serial …`), virtio-blk + virtio-net
   (user NAT first), capture console to `artifacts/diagnostics/<date>-boot/`.
5. `./dev/shell` in, `./dev/diagnose` snapshot (systemd, net, storage).

Success: systemd reaches multi-user, network DHCP works, qcow2 persists
across reboots, shell access works.

## Phase B: virgl accel + Hyprland (gated on Phase A + non-llvmpipe)
- Boot with `-device virtio-gpu-gl-pci`, run `./dev/test-gpu`
  (`/dev/dri/*`, `dmesg`, `glxinfo -B`, `eglinfo`, `vulkaninfo`).
  MUST NOT be llvmpipe.
- Only then: minimal Hyprland (`start-hyprland`, `AQ_DRM_DEVICES`,
  Foot, keyboard/pointer), logs via `./dev/test-desktop`.

## Phase C/D: Linux crosvm/Cuttlefish, then Pixel
- Not started. Pixel requires `docs/pixel-test-plan.md` first.
