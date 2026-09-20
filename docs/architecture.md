# Architecture (updated 2026-09-20: Mac path proven, Pixel path blocked on OS build)

```
Mac (PROVEN)                Pixel 8 Pro / CP41.260828.004.A8 (TESTED, BLOCKED)
────────────                ──────────────────────────────────────────────────
ArchARM rootfs ──┐
stock Image ─────┼─► QEMU    host/apk ──► AVF app API ──► pKVM ──► crosvm ──► Arch (boots ✓)
virtio-blk/net ──┘   (HVF)   (unrooted,                        virtio-gpu present,
                         │    reflection)                      3D backends dead:
                    Mesa virgl ──► Hyprland + Foot ✓            virglrenderer→SIGABRT,
                                                               gfxstream→0 contexts
                                                               → llvmpipe ✗
```

## Layers
1. **host/Android/AVF** — Mac HVF (proven) ; Pixel pKVM (boots guests, no 3D).
2. **VM boot infra** — `dev/boot` (QEMU) ; `vm run` JSON (blk/console only) ;
   `host/apk` (full app/API path: gpu/display/input/net/vsock-capable).
3. **kernel/guest devices** — stock `linux-aarch64` (7.2.6). Pixel notes:
   serial is 8250 `ttyS0`; app-path match_host needs `arm64.nompam` (MPAM).
4. **rootfs** — trusted `ArchLinuxARM-aarch64-latest.tar.gz` (+GPG `.sig`
   verified in `dev/build`); raw (not qcow2) for AVF; resize2fs -M to fit.
5. **graphics** — Mac: DRM → virtio-gpu-gl → Mesa virgl → EGL/GLES ✓.
   Pixel: virtio-gpu device exists, host 3DContexts none → llvmpipe ✗.
6. **compositor** — Hyprland (aquamarine), DRM primary, GLES 3.0+, no Vulkan.
7. **apps** — Foot only for M1.

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
- AVF custom-guest GPU: `gpu.backend=virglrenderer`, `context_types=[virgl2]`
  is honored by virtmgr (emits `--gpu`) but THIS build's crosvm SIGABRTs
  (rutabaga backend support not compiled in); gfxstream inits but serves no
  3D contexts. Retry on newer OS builds.
- `vm run` CLI: kernel/initrd/blk/console only (serde drops gpu/net/input).
- QEMU Mac GPU: `-device virtio-gpu-gl-pci` (`gl=es` via ANGLE/Metal in
  patched builds; stock brew QEMU = software only).

## What lives where (target)
- `guest/rootfs/` — tarball fetch + package list + overlay build inputs
- `guest/kernel/` — stock Image reference; custom config only if needed
- `guest/image/` — qcow2 assembly (never committed)
- `dev/*` — build/boot/shell/diagnose/test-gpu/test-desktop entry points
- `artifacts/diagnostics/<date>-<topic>/` — ignored logs (dmesg, glxinfo…)
