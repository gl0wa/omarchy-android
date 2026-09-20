# Bringup runbook (grows as phases land)

## Phase A-1: first headless Arch ARM boot — DONE 2026-09-20

Recipe (all encoded in `dev/*`, Mac Apple Silicon):
1. `brew install qemu` (stock 11.1.1; headless only — no virgl in this build).
2. Fetch `http://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz`,
   verify MD5 `23eec86365b24f7913c403e8f4e8719b` (GPG `.sig` check deferred —
   no `gpg` on stock macOS; TODO: verify inside container).
3. Start podman machine; in a native-arm64 debian container (root, no sudo):
   `bsdtar -xpf` tarball → `mkfs.ext4 -d` 6G image → debugfs inject host SSH
   pubkey as `/root/.ssh/authorized_keys` → `e2fsck`.
4. `qemu-img convert -O qcow2` → `guest/image/archroot.qcow2`; extract
   `boot/Image` + `boot/initramfs-linux.img` → `guest/kernel/boot/`.
5. `./dev/boot` → `qemu-system-aarch64 -M virt -cpu host -accel hvf`,
   `root=/dev/vda rw rootwait console=ttyAMA0,115200`, virtio-blk + user NAT
   (`hostfwd=tcp::2222-:22`). `./dev/shell` (root key), `./dev/diagnose`.

Notes:
- `dev/build` rebuilds qcow2 from the pristine raw image — guest-side changes
  are WIPED on rebuild. Deliberate (reproducible fresh image).
- Credentials: `alarm`/`alarm` over SSH; `root` key-only
  (`~/.ssh/omarchy_android_ed25519`, injected at build). Minimal image has no
  `sudo`; use the root key. `root`/`root` password does NOT work (sshd default).
- QEMU backgrounding lesson: never `eval` a multiline command with `&`
  (fd inheritance hangs the caller, quotes break); use a shell function with
  file redirections (see `dev/boot` history).
- Kernel upgrades: after ANY in-guest `linux-aarch64` upgrade, run
  `./dev/sync-kernel` (dumps /boot/Image+initramfs from qcow2 via debugfs in
  a container) BEFORE next boot. Symptom of mismatch: TCP connects, SSH
  banner never arrives (virtio_net module won't load against old kernel).

## Phase A-2: 2D GPU baseline + desktop harness (DONE 2026-09-20)

- `M1_GPU=2d ./dev/boot` adds `-device virtio-gpu-pci -vga none` → guest
  shows `/dev/dri/card0+renderD128`; `./dev/test-gpu` reports llvmpipe
  (negative control — verdict logic keys ONLY on Mesa renderer strings,
  never on PCI/dmesg device names).
- `./dev/test-desktop` (seatd + `desktop` user + provisioned
  `launch-hyprland.sh`): Hyprland starts on llvmpipe, Xwayland up, `foot`
  mapped+visible as native Wayland client. Lesson: never nest `$(id -u)` /
  `set -eu` inside `su -c` double quotes — provision literal files instead.

## Phase B: virgl accel + Hyprland (DONE 2026-09-20, interactive part manual)

1. `host/utm/make-bundle` crafts `omarchy-m1.utm` (virtio-gpu-gl-pci, HVF,
   8G RAM, kernel/initrd drives, USB tablet/kbd/mouse, SSH 2223) from UTM's
   source schema. Lessons:
   - UTM splits `AdditionalArguments` on whitespace (quote-aware) — wrap the
     multi-word `-append` value in literal double quotes or QEMU tries to
     open `rw` as an image.
   - UTM caches parsed config in memory: after ANY rebuild, Cmd+Q + reopen
     and verify displayed Memory before Start.
   - Keep the VM UUID stable across rebuilds (registration is by UUID).
2. `M1_SSH_PORT=2223 ./dev/test-gpu` → `virgl (ANGLE (Apple M4 Pro,
   OpenGL 4.1 Metal))`, GLES 3.0. NOT llvmpipe. (No Vulkan/venus on this
   path — fine, Hyprland needs GLES 3.0+ only.)
3. `M1_SSH_PORT=2223 ./dev/test-desktop` → Hyprland + Xwayland + Foot
   (native Wayland, mapped/visible), libinput tablet/mouse/keyboard.
4. Interactive (human): UTM window shows Hyprland+Foot; typing works;
   Super+Return opens a terminal. NOTE: Super acts as macOS Command, so
   Super+Q quits UTM itself (host collision, not a guest bug).

## Phase C/D: Linux crosvm/Cuttlefish, then Pixel
- Not started. Pixel requires `docs/pixel-test-plan.md` first.
