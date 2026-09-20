# Architecture

```
Phase A (Mac, now)          Phase C (Linux)              Phase D (Pixel)
─────────────────           ───────────────              ───────────────
ArchARM rootfs ──┐
stock Image ─────┼─► QEMU    custom kernel ──► crosvm ──► AVF vm run ──► pKVM
virtio-blk/net ──┘   (HVF)   virtio-gpu virgl   vm JSON    virgl2/gfxstream?
                         │          │                    │
                    Mesa virgl   Mesa virgl/venus     Mesa virgl/venus
                         │          │                    │
                    Hyprland   Hyprland/smoke       Hyprland + Foot
                    (aquamarine DRM/KMS primary, GLES 3.0+, Vulkan NOT required)
```

## Layers
1. **host/Android/AVF** — Mac HVF now; Linux+KVM later; pKVM only on Pixel.
2. **VM boot infra** — `dev/boot` (QEMU `virt`+HVF) now; `vm <json>` later.
3. **kernel/guest devices** — stock `linux-aarch64` Image (virtio-pci/blk/net/
   console/input, `DRM_VIRTIO_GPU=m`, vsock). Custom kernel only on evidence.
4. **rootfs** — trusted `ArchLinuxARM-aarch64-latest.tar.gz` (+`.sig`), minimal
   package set: systemd, openssh, mesa, vulkan-virtio, hyprland, foot.
5. **graphics** — DRM (`/dev/dri/card0+renderD128`) → virtio-gpu → Mesa
   (virgl Gallium, venus ICD) → EGL/GLES. llvmpipe = failure.
6. **compositor** — Hyprland (aquamarine), DRM primary, `AQ_DRM_DEVICES`,
   `start-hyprland` as non-root on TTY seat.
7. **apps** — Foot (Wayland-native) only for M1.

## Key interfaces
- Guest GPU device: virtio-gpu PCI (`1AF4:1050`), kernel `virtio_gpu`+DRM.
- AVF custom-guest GPU (documented): `gpu.backend=virglrenderer`,
  `context_types=[virgl2]`. gfxstream path LIKELY but undocumented.
- QEMU Mac GPU: `-device virtio-gpu-gl-pci` (`gl=es` via ANGLE/Metal in
  patched builds; stock brew QEMU = software only).
- crosvm GPU (Linux): `--gpu backend=virglrenderer[,vulkan=true]`.

## What lives where (target)
- `guest/rootfs/` — tarball fetch + package list + overlay build inputs
- `guest/kernel/` — stock Image reference; custom config only if needed
- `guest/image/` — qcow2 assembly (never committed)
- `dev/*` — build/boot/shell/diagnose/test-gpu/test-desktop entry points
- `artifacts/diagnostics/<date>-<topic>/` — ignored logs (dmesg, glxinfo…)
