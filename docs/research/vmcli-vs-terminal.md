# `vm run` CLI vs Terminal app/API path (Pixel 8 Pro, Android 17 QPR2 beta, 2026-09-20)

Question: "What capability does Terminal's app/API path have that the vm CLI
path does not expose?" Answer, all code/device-verified:

## A. Our `vm run` Arch invocation (cids 2048-2052, requester = shell uid 2000)
crosvm gets: kernel, initrd, params, 1 vCPU (`--cpus num-cores=1` despite
`match_host`), mem, virtio-blk (raw only — qcow2 opened as raw bytes),
balloon, serial+virtio-console on file transports, DT overlay, vsock socket
(created but no device attached).
NOT present (verified absent from 5 command lines): `--gpu`, `--net`/tap,
`--input`, `--android-display-service`, vsock device.
Root causes (AOSP main source): CLI schema `libs/vmconfig VmConfig` has no
gpu/display/network/input fields; serde silently drops them; `run.rs` never
sets them; virtmgr `crosvm.rs` skips the `--gpu` block on `gpuConfig=None`.
Console: needs TTY for `vm console`; file transports (`--console`,
`--console-in`) work and carried all our evidence.

## B. Terminal's Debian invocation (cid 2054+, requester = app uid 10272)
crosvm gets everything A has, PLUS:
- `--gpu=egl=true,gles=true,surfaceless=true,displays=[[mode=windowed[1280,720],
  dpi=[160,160],refresh-rate=60]]` — NO backend (2D). Set from Terminal's own
  `ConfigJson` defaults via framework `VirtualMachineCustomImageConfig`
  (GpuConfig/DisplayConfig builders — present in shipped dex).
- `--android-display-service=cid:2054` + app SurfaceViews (DisplayActivity)
  and cursor channel.
- `--input` ×4 (keyboard/mouse/touch/trackpad via InputForwarder).
- `--net tap-fd=55` (TAP via `networkSupported=true` — CLI can never set it).
- 3× `--block` (system + writable + backup/extra disks), u-boot bootloader
  (Debian boots via bootloader, not direct kernel).
- vsock + ttyd bridge to device loopback (app-forwarded; token-gated ports),
  guest agent (linux_vm_manager), GuestLog journal forwarding to logcat,
  sommelier unit (skipped without `wayland_bridge` cmdline), `weston_force_pixman`.
Guest: Debian trixie-ish, kernel 6.12.92-android16, Mesa 25.0.7, droid user
with passwordless sudo, `/dev/dri/card0+renderD128`, DRM caps
`-virgl +edid +resource_blob +host_visible -context_init`, Vulkan GPU0 =
llvmpipe. No eglinfo/glxinfo (mesa-utils not installed).

## Architectural difference (one paragraph)
The CLI is a debug subset: it can only fill `VirtualMachineRawConfig` fields
its JSON schema knows (boot + block + console). Everything that makes a VM
a *device* — GPU backend, display surface, input, TAP, vsock forwarding —
exists ONLY behind framework `VirtualMachine*Config` builders, which require
an app context (Surface from a window, UID-scoped permissions
`MANAGE/USE_CUSTOM_VIRTUAL_MACHINE`, SELinux app domain). Terminal is that
app; `vm` is not. There is no flag combination that promotes the CLI path.

## What this means for Arch + GPU (D-3c)
The host side FULLY supports `--gpu=backend=virglrenderer,context-types=virgl2`
(virtmgr `crosvm.rs` emits it verbatim when configured). Smallest credible
route: a minimal APK calling the same framework APIs Terminal calls, with our
kernel + raw disk + GpuConfig{virglrenderer/virgl2} + DisplayConfig. Open risk:
third-party SELinux surface sharing (PocketVM needed KernelSU sepolicy rules;
Terminal is priv-app). Fallback if blocked: check whether Terminal itself
accepts a custom disk/kernel (settings UI?) — keeps the priv-app domain.
