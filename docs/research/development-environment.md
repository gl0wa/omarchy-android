# Development environment — feasibility and path choice

Date: 2026-09-20. Host: Apple M4 Pro (arm64), macOS 26.6, 48 GB.
Goal of this doc: decide what can be developed on this Mac, what needs
an Android emulator/Cuttlefish/Linux box, and what genuinely needs the
physical Pixel. Statuses: CONFIRMED / LIKELY / UNKNOWN / REQUIRES PHYSICAL DEVICE.

## 1. Host ground truth (CONFIRMED, observed 2026-09-20)

- `uname -m` = arm64, chip Apple M4 Pro, macOS 26.6 (build 25G72).
- `brew` present (`/opt/homebrew/bin/brew`). `adb` 36.0.0 present via
  `android-platform-tools`. `scrcpy`, `podman`, `rust`, `gh` present.
- Installed during Milestone 1: stock `qemu` 11.1.1 (brew), Android SDK
  cmdline-tools + platforms android-36 + android-37.2-beta2 + build-tools
  36.0.0 (`~/Library/Android/sdk`), JDK 21 (`/opt/homebrew/opt/openjdk@21`),
  Gradle 8.9 (project-local dist under `host/apk/`).
- NOT present: `docker`, `lima`, emulator/`avdmanager` (no need so far).
- `UTM.app` installed (embeds QEMU as `qemu-aarch64-softmmu.framework`,
  plus ANGLE/Metal GPU patches; no standalone `qemu-system-aarch64` binary).
- Repo `gl0wa/omarchy-android` exists, empty (`main`, no commits, origin set).
  Nothing built yet.

## 2. AVF fundamentals (CONFIRMED, AOSP docs)

- AVF reference implementation is ARM64-only; x86_64/Cuttlefish is
  functional testing only, without guest protection:
  https://source.android.com/docs/core/virtualization
  https://source.android.com/docs/core/virtualization/architecture
- pKVM (EL2, stage-2 isolation, per-VM secrets via pvmfw/DICE) only exists
  on physical ARM64 hardware. Cuttlefish explicitly supports only
  non-protected VMs:
  https://android.googlesource.com/platform/packages/modules/Virtualization/+/HEAD/docs/getting_started.md
- Custom Linux guests are officially supported via `VirtualMachineManager` /
  `VirtualMachineCustomImageConfig` (Java, `@hide`) and the `vm` debug CLI
  (`vm run <json>` with kernel/initrd or u-boot+disk, `protected:false`):
  https://android.googlesource.com/platform/packages/modules/Virtualization/+/refs/tags/android-15.0.0_r17/docs/custom_vm.md
  https://source.android.com/docs/core/virtualization/virtualization-service
- The only **documented** custom-guest GPU backend is
  `"gpu": {"backend":"virglrenderer","context_types":["virgl2"]}` for
  graphical (Ferrochrome/Debian) examples in `custom_vm.md` (android-15 tag).
  Guest expectation: `CONFIG_DRM_VIRTIO_GPU`, Mesa virgl Gallium, EGL/OpenGL.
- gfxstream (`libgfxstream_backend.so`) exists in AOSP and the Terminal app's
  "Graphics Acceleration" toggle suggests a gfxstream path for custom guests,
  but it is NOT documented in `custom_vm.md`. Treat as LIKELY-available but
  unproven until tested on device. UPDATE 2026-09-20: TESTED on Pixel 8 Pro /
  CP41.260828.004.A8 via custom host APK — virglrenderer backend SIGABRTs
  host crosvm (rutabaga params unbuilt), gfxstream serves zero 3D contexts
  (llvmpipe). Neither path accelerates on this build; see pixel-test-plan.
- PocketVM (https://github.com/okhsunrog/pocketvm) confirms the practical
  shape: app -> `VirtualizationService` -> per-app `virtmgr` -> `crosvm` from
  `com.android.virt` APEX, guests unprotected, virtio on PCI, `vm run` works
  as shell without root, app fork needs `MANAGE_VIRTUAL_MACHINE` +
  `USE_CUSTOM_VIRTUAL_MACHINE` grants + hidden-API policy + KernelSU SELinux
  rules. Verified only on Pixel 8 Pro / Android 17. No emulator path provided.

## 3. Option verdicts

### 3a. QEMU-system-aarch64 + HVF on this Mac — USE FIRST (CONFIRMED viable)

- QEMU 9.2+ documents `virtio-gpu` backends 2D / virglrenderer (`gl`) /
  rutabaga (`rutabaga`), guest needs `CONFIG_DRM_VIRTIO_GPU`:
  https://www.qemu.org/docs/master/system/devices/virtio/virtio-gpu.html
- Stock Homebrew `qemu` (11.1.1, not installed) lacks macOS OpenGL/virgl by
  design. Accelerated path on macOS needs a patched build:
  `brew install knazarov/qemu-virgl/qemu-virgl` (ANGLE -> Metal, `gl=es`),
  or UTM (bundles the same class of patches, `virtio-gpu-gl-pci` default):
  https://github.com/quickemu-project/quickemu/wiki/09-macOS-Host-Support
  https://docs.getutm.app/guest-support/linux
- Expected guest result (per UTM maintainer reports): Mesa `virgl`
  (`Accelerated: yes`, GLES 3.0/3.2, compat GL 2.1) via
  Mesa <-> virglrenderer <-> GLES <-> macOS framework. This PROVES
  rootfs/kernel/systemd/Mesa/Hyprland packaging; it does NOT prove AVF.
  https://github.com/utmapp/UTM/issues/4285
- UTM 5.0 beta adds Venus Vulkan 1.3 on Linux guests, but macOS 26 has a known
  `HV_UNSUPPORTED` VM-start bug (workaround: disable Vulkan). Keep Vulkan OFF
  on this host until baseline virgl works:
  https://github.com/utmapp/UTM/issues/7150
- QEMU upstream Mar-2026 work decouples Venus from the OpenGL display backend
  (missing on macOS/Cocoa) — Venus-on-Mac is still experimental:
  https://lists.nongnu.org/archive/html/qemu-devel/2026-03/msg03281.html
- Conclusion: Phase A (headless boot/systemd/net/storage) works with stock
  QEMU or UTM; Phase B (virgl accel) needs the `qemu-virgl` tap or UTM.

### 3b. crosvm directly on this Mac — NOT POSSIBLE (CONFIRMED)

- crosvm requires Linux KVM (`/dev/kvm`): "A Linux 4.14 or newer kernel with
  KVM support is required": https://crosvm.dev/book/running_crosvm/requirements.html
  Supported hypervisors: KVM (Linux/Android), WHPX/HAXM (Windows), Gunyah/
  GenieZone (Android). No HVF/macOS target.
  https://crosvm.dev/book/hypervisors.html
- Only known macOS experiment boots to earlycon with stubbed GPU — not usable.
- Consequence: crosvm-specific validation (flags, `virglrenderer` vs
  `gfxstream` backends, `vulkan=true`, `wsi=vk`) needs a Linux host (see 3d).

### 3c. Official Android Emulator on this Mac — LOW VALUE (CONFIRMED)

- AOSP's AVF-on-emulator flow (`goldfish.md`) assumes a **Linux + x86_64 +
  KVM + `-qemu -cpu host` nested-virt** host. On macOS the emulator uses
  Hypervisor.Framework, not KVM, and Apple HVF nested-virt (EL2) is new,
  M3+-only, with QEMU HVF nesting still out-of-tree (Apr-2026 patch series):
  https://android.googlesource.com/platform/packages/modules/Virtualization/+/68c39f8a7b6dea258171b514fedf43e84de7b2be/docs/getting_started/goldfish.md
  https://developer.android.com/studio/run/emulator-acceleration
  https://lists.nongnu.org/archive/html/qemu-devel/2026-04/msg05896.html
- `MicrodroidDemo` notes it works only on Cuttlefish:
  https://source.android.com/docs/core/virtualization/tryavf
- An emulator pass would prove `VirtualMachineManager` wiring at best, never
  pKVM/pvmfw/stage-2/device-assignment behavior. Deprioritize.

### 3d. Cuttlefish — USEFUL BUT NEEDS LINUX (CONFIRMED)

- Host requirement: Linux x86/ARM64 with KVM (`/dev/kvm`, `kvm`/`cvdnetwork`/
  `render` groups, `cuttlefish-base/user` debs, `launch_cvd`):
  https://source.android.com/docs/devices/cuttlefish/get-started
- Supports `vm run <json>` custom kernel/initrd and Microdroid `vm run-app`
  flows for functional (non-protected) testing — the canonical tip-of-tree
  AOSP device vs the app-dev-oriented emulator:
  https://android.googlesource.com/platform/packages/modules/Virtualization/+/refs/heads/android14-release/docs/getting_started/index.md
- Does NOT validate our Arch/Mesa/Hyprland packaging (guest is
  Android/Microdroid-oriented). Does NOT run on macOS directly.
- Nested option: this M4 Pro + macOS 15+ supports nested virt in
  Lima/UTM Linux guests (M3+ gate; UTM 4.6 notes), so a Lima/UTM Linux VM
  exposing `/dev/kvm` could host crosvm/Cuttlefish builds locally — but with
  virt overhead and still no protection. A remote ARM64+KVM Linux box is the
  frictionless alternative. Untried and now DEPRIORITIZED (2026-09-20): the
  Pixel app path answers AVF questions directly without a Linux detour.

### 3e. PocketVM without hardware — PARTIAL (CONFIRMED)

- CAN do without a phone: build mainline kernel + Rust init, `libforwarder`
  JNI (`cargo ndk`), APK (Gradle/JDK21/SDK plat 37), static ttyd, image prep
  (`qemu-img convert`, never raw `resize`), JSON schema review, DTB/config
  inspection.
- CANNOT do without a phone: `vm run`, boot, vsock/SSH/ttyd, SELinux/TAP/
  tethering (single-VM `avf_tap_fixed` limit), display/GPU, MPAM panic and
  `SERIAL_OF_PLATFORM` console quirks. All verified runs are on-device.

## 4. Guest stack (CONFIRMED, upstream)

- Arch Linux ARM is alive (rootfs listing Aug-2026, `linux-aarch64 7.2.6-1`,
  `hyprland 0.56.1-3`, `foot 1.27.0-1`, `mesa 1:26.2.2-1`):
  http://os.archlinuxarm.org/os/ — tarball
  `http://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz`
  (+`.md5`, +`.sig`, GPG key `68B3537F39A313B3E574D06777193F152BDBE6A6`),
  install doc https://archlinuxarm.org/platforms/armv8/generic
- Stock `linux-aarch64` config already has what we need, mostly as modules:
  `CONFIG_DRM_VIRTIO_GPU=m`, `CONFIG_DRM_VIRTIO_GPU_KMS=y`, `VIRTIO_PCI=y`,
  `VIRTIO_MMIO=m`, `VIRTIO_BLK=y`, `VIRTIO_NET=m`, `VIRTIO_CONSOLE=y`,
  `VIRTIO_INPUT=m`, `VSOCKETS/VIRTIO_VSOCKETS=m`, `EFI_STUB=y`:
  https://github.com/archlinuxarm/PKGBUILDs/blob/master/core/linux-aarch64/config
  Implication: prefer stock kernel; ensure initramfs/modules include virtio.
- Guest Mesa: `mesa` (virgl Gallium + zink) + `vulkan-virtio` (Venus ICD) +
  `vulkan-icd-loader` + `libdrm` + `mesa-utils`/`vulkan-tools` for diagnostics:
  https://wiki.archlinux.org/title/OpenGL https://wiki.archlinux.org/title/Vulkan
  https://docs.mesa3d.org/drivers/venus.html
- Hyprland: modern releases use in-house `aquamarine` (not wlroots):
  backends DRM/KMS primary, headless mandatory, Wayland-nested fallback.
  "Virtio-GPU provides an emulated screen output. Therefore you can use
  Hyprland normally with it, with no extra effort":
  https://wiki.hypr.land/configuring/extra/virtual-gpu
  Vulkan NOT required — Hyprland renders via GLES (needs OpenGL ES 3.0+):
  `src/render/OpenGL.cpp` requests GBM/DEVICE EGL + GLES 3.2→3.0.
  Minimal VM env: `XDG_RUNTIME_DIR`, TTY seat (systemd-logind), run via
  `start-hyprland` as non-root, `AQ_DRM_DEVICES=/dev/dri/card0`,
  fallback `AQ_NO_MODIFIERS=1` if modeset/dmabuf fails.
- Diagnostics that prove accel: `ls /dev/dri` (expect `card0+renderD128`),
  `dmesg | grep -i virtio_gpu/drm`, `glxinfo -B` must NOT say `llvmpipe`
  (expect `virgl`/`Virtio GPU`, `Accelerated: yes`; or `zink (Virtio-GPU
  Venus …)` for GL-on-Vulkan), `vulkaninfo` must show `Virtio-GPU Venus`,
  `/usr/share/vulkan/icd.d/virtio_icd.json` present.

## 5. Ranked plan (decision)

1. Phase A — this Mac, QEMU/UTM headless (days): ArchARM rootfs + stock
   kernel, systemd, virtio-blk/net, SSH/vsock-or-TCP, persistent qcow2.
   Proves layers 2–4 without AVF.
2. Phase B — this Mac, `qemu-virgl` tap or UTM with `virtio-gpu-gl`
   (Phase A + GPU): `/dev/dri`, Mesa `virgl` (non-llvmpipe), then minimal
   Hyprland + Foot + keyboard/pointer. Proves layers 5–7 packaging without AVF.
3. Phase C — Linux host (nested Lima/UTM VM on this M4 or remote ARM64+KVM):
   build/run crosvm (`--gpu backend=virglrenderer[,vulkan=true]`), then
   Cuttlefish `vm run` custom-guest flow. Proves AVF API packaging, still
   non-protected.
4. Phase D — Pixel (only when A–C pass): pKVM protected boot, pvmfw/DICE,
   TAP/tethering, virtio-gpu virgl2 vs gfxstream on real AVF, perf/power.
   Requires `docs/pixel-test-plan.md` first per project rules.

## 6. Status buckets (updated 2026-09-20 after Mac + Pixel experiments)

CONFIRMED (was prediction, now evidence — see docs/status.md, pixel-test-plan)
- Host: QEMU-on-Mac + HVF + UTM virtio-gpu-gl yields guest Mesa `virgl`
  (ANGLE/Metal, GLES 3.0) sufficient for Hyprland + Foot + input.
- `vm run` JSON schema has no gpu/display/network fields (serde drops them);
  only the app/API path can set them (code + device-verified).
- Custom AVF guests get NO accelerated graphics on Pixel 8 Pro /
  CP41.260828.004.A8: virglrenderer backend → host crosvm SIGABRT (rutabaga
  not built with backend support, no .so on device); gfxstream backend →
  device present but zero host 3D contexts (llvmpipe; no Venus). Shipped
  Terminal v17 APK lacks the sentinel mechanism (dex-verified).
- AVF serial console is 8250 `ttyS0` (not pl011 ttyAMA0); `vm run` works
  unrooted as shell; raw images work, qcow2 is opened as raw bytes;
  app-path match_host exposes Tensor MPAM → `arm64.nompam` required.
- Prior CONFIRMED items still hold: AVF ARM64-only; crosvm Linux-only;
  Cuttlefish/emulator need Linux+KVM; ArchARM packaging complete.

LIKELY (still untested)
- Nested Lima/UTM Linux VM on this M4 can expose /dev/kvm for Phase C
  (M3+ nested-virt gate met; not yet attempted — and deprioritized: the
  Pixel app path already answers AVF questions directly).

UNKNOWN
- Whether Venus/Vulkan will work on this macOS 26 host (UTM 5.0 beta claims
  1.3 support but reports HV_UNSUPPORTED; upstream decoupling patches pending).
- Which virgl modifiers/atomic-modeset quirks Hyprland/aquamarine will hit
  under virgl (workarounds `AQ_NO_MODIFIERS=1` etc. documented but untested).
- Exact ArchARM EDK2/DTB boot recipe under QEMU `virt`+HVF (to be established
  in Phase A).

REQUIRES PHYSICAL DEVICE (partially answered 2026-09-20, rest still open)
- Answered: `vm run` serves blk/console only; virgl2 vs gfxstream on THIS
  build = SIGABRT vs zero-contexts (no accel); MPAM panic + `arm64.nompam`
  need reproduced; `dummy-virt` DTB + ttyS0/earlycon behavior confirmed.
- Still open: pKVM isolation, pvmfw verification, per-VM secrets, stage-2
  protection, protected-VM boot of arbitrary rootfs; real TAP/tethering at
  scale; performance/power characteristics (moot until 3D exists).
