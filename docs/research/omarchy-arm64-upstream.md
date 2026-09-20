# Current upstream Omarchy architecture — 2026-09-20

Supporting source audit for [the ARM64 implementation decision](omarchy-arm64.md).
This document records source findings, not desktop validation.

## Revisions and release selection

- Canonical repository is now [omacom/omarchy](https://github.com/omacom/omarchy); basecamp redirects.
- Default `quattro` HEAD observed: `45748a2812f42e32f915b053caf4074e150e2048`.
- Latest release tag observed: **v4.0.4**, commit **`c668141e9c42b13c80c9ca4ea108e11708c5e8a5`**. Prefer this released pin over rolling HEAD. Both source trees retain a stale `version` file saying `4.0.0.alpha`; the packaging recipe establishes the actual release version.
- Packaging repository observed: [omacom/omarchy-pkgs](https://github.com/omacom/omarchy-pkgs/tree/4b60e4cd95972c16fbf3da634522a955cf7bf36c), commit `4b60e4cd95972c16fbf3da634522a955cf7bf36c`.
- Ignored local audit material: `artifacts/research/omarchy-upstream` (HEAD plus fetched v4.0.4), `omarchy-v4.0.4-source.tar.gz` (git archive), package recipes and downloaded repository databases beside it.

## Actual desktop and installation flow

Current Omarchy is **Quickshell + Hyprland Lua + Foot**, not the historical Waybar/Walker stack. Its [shell documentation](https://github.com/omacom/omarchy/blob/c668141e9c42b13c80c9ca4ea108e11708c5e8a5/shell/README.md) describes one long-running process hosting the bar, background, menu/launcher, panels, clipboard, notifications, lock, polkit agent, and service plugins. `shell/shell.qml` is the entry point; `config/omarchy/shell.json` provides initial state. `omarchy-shell shell ping` must return `ok`; merely starting a process proves less.

The [file layout](https://github.com/omacom/omarchy/blob/c668141e9c42b13c80c9ca4ea108e11708c5e8a5/docs/file-layout.md) now separates two packages:

1. `omarchy-settings` seeds `/etc/skel`, system defaults, icons/fonts, shell environment, and session/display-manager configuration.
2. `omarchy` supplies `/usr/bin/omarchy-*`, runtime tree `/usr/share/omarchy`, themes, shell QML, migrations and finalization helpers. Full application set is separately pacstrapped by the ISO from `install/omarchy-base.packages`.
3. User creation copies skel; `omarchy-provision-user` finalizes runtime-dependent configuration. Explicit `omarchy-reinstall-configs` clobbers existing user configuration.
4. SDDM session entry starts `uwsm start -g -1 -e -D Hyprland hyprland.desktop`. UWSM sources `default/uwsm/env.d/10-omarchy`; ordinary login shells source `/etc/profile.d/omarchy.sh`. Both reach `default/bash/env-bootstrap`.
5. `config/hypr/hyprland.lua` loads bootstrap, defaults, then user overrides. Autostart imports environment into D-Bus/systemd and launches `omarchy-launch-shell`, first-run provisioning, power-profile initialization, monitor watcher, and udiskie.
6. `omarchy-launch-shell` supervises Quickshell and preserves its output in the journal under `omarchy-shell`. `omarchy-shell` itself is an IPC wrapper, not a launcher.

Hyprland Lua arrived in [0.55](https://hypr.land/news/update55/). Our 0.56.2 reference is new enough for the format; test specific current calls empirically. No compositor replacement is justified merely by `.lua` being new to this project.

Default core bindings include Super+Return (terminal), menu bindings, workspace and window management. Optional app bindings are explicitly switchable using `omarchy_preinstalled_bindings = false`. Host macOS shortcuts can intercept Super combinations; preserve the M1 known-good input test.

## Upstream ARM support is real, publication is incomplete

The current [omarchy recipe](https://github.com/omacom/omarchy-pkgs/blob/4b60e4cd95972c16fbf3da634522a955cf7bf36c/pkgbuilds/omarchy/PKGBUILD) already has `arch=('x86_64' 'aarch64')`. ARM dependencies replace the x86 Limine/Snapper stack with iwd/NetworkManager. The [settings recipe](https://github.com/omacom/omarchy-pkgs/blob/4b60e4cd95972c16fbf3da634522a955cf7bf36c/pkgbuilds/omarchy-settings/PKGBUILD) excludes Limine/mkinitcpio configuration, x86 zram/oomd tuning, and Limine autostart on ARM. Quickshell's recipe also supports both architectures. Omarchy's own Hyprland recipe is ARM 0.56.2-3.

However, downloaded databases on 2026-09-20 showed:

| Feed | Observed ARM content |
|---|---|
| `https://pkgs.omarchy.org/stable/aarch64/omarchy.db` | 12 optional packages, no core Omarchy/settings/Quickshell |
| `https://pkgs.omarchy.org/rc/aarch64/omarchy.db` | Same small set |
| `https://pkgs.omarchy.org/edge/aarch64/omarchy.db` | Omarchy/settings 4.0.2-1, Quickshell git 0.3.0.r20.g28771c7-3, keyring, many extras |

Thus source ARM declarations are not evidence that a complete latest stable binary installation exists. Do not globally enable edge to solve one dependency; it could replace the reference graphics stack.

For selective signed downloads, the pinned upstream keyring trusts fingerprint `40DFB630FF42BCFFB047046CF0134EE680CAC571`. Its `omarchy.gpg` SHA512 in the audited recipe is `f9455c60f30984d73ddebecf55a1b6dcb3afba84638c5d78130494bb828f53cfa19d393c27e43514d5847b37dcd0a5601ba39b213c2185ff153001b7221c9c8c`. Bootstrap from pinned source, check hashes/fingerprint, populate the guest keyring, and verify detached signatures. Never solve this with `SigLevel=Never`.

Edge Quickshell file observed: `quickshell-git-0.3.0.r20.g28771c7-3-aarch64.pkg.tar.zst`, SHA256 `2eaad9da6150bdfd3fd60b9aefb428a3f70ccebddfa8187474a6cf702df74d4f`. Pinned Quickshell source: `28771c7c74b42e20afca0b1b63980cb46515537c`. This is a candidate only: Qt ABI compatibility must be checked before installing.

## Dependency and assumption matrix

| Component | Class | Concrete disposition |
|---|---|---|
| Hyprland/Mesa/Foot | NATIVE ARM64 | Existing reference proves execution; retain accelerated virgl and stock kernel. |
| Omarchy scripts, Lua/QML, themes | ARCH-INDEPENDENT | Install released source unmodified; correct system/user paths matter. |
| Quickshell | NATIVE ARM64 | Upstream ARM package exists on edge; test Qt ABI and required modules. |
| Qt Quick/declarative, SVG, multimedia, Wayland | NATIVE ARM64 | Shell imports QtQuick, Controls, Layouts, Effects, Shapes, Multimedia. Query guest packages and render actual UI. |
| Quickshell modules | UNKNOWN until runtime | Networking, Bluetooth, Hyprland, Pipewire, Mpris, Notifications, Pam, Polkit, SystemTray, UPower, Wayland and Io are imported. Version/module presence matters more than package name. |
| Foot terminal config | ARCH-INDEPENDENT | Includes generated state `~/.local/state/omarchy/current/theme/foot.ini`; theme must be initialized. |
| Nerd/icon fonts | NEEDS SUBSTITUTE if exact package absent | JetBrainsMono Nerd Font, Noto/emoji, bundled `default/fonts/omarchy/omarchy.ttf`, Yaru icons. Font substitution must retain Nerd glyphs, not simply monospace text. |
| UWSM, SDDM, portals, keyring, PipeWire | NATIVE ARM64 | Upstream session infrastructure; bring in after isolated compositor/shell smoke test. |
| GNU utilities, jq, gum, python, git, curl | NATIVE ARM64 | Helpers use these; install core needed subset before invoking them. |
| NetworkManager migration | OPTIONAL | Existing networkd/SSH are control infrastructure. Do not run `install/hardware/network.sh` blindly: it disables networkd and retires stock DHCP configuration. |
| Omarchy settings post-install | NEEDS PATCH or selective installation | Preserves ArchARM identity/PAM/NSS only when device tree contains `apple,`; QEMU virt fails that predicate despite ARM architecture. Avoid destructive post-install overrides. |
| Limine, Snapper, Btrfs, linux-omarchy, multilib | OPTIONAL | Wrong boot/filesystem model for direct-kernel ext4 VM. Upstream ARM recipe already excludes several of these. Keep original kernel/initrd and sync workflow. |
| Intel/AMD/NVIDIA/Asahi GPU detection | OPTIONAL | Hardware scripts detect PCI vendors; virtual GPU requires no Intel/AMD/Asahi replacement. Existing virgl remains the control. |
| Laptop firmware, DKMS, T2, thermal/RGB tools | OPTIONAL | Skip; not relevant to virtual hardware or desktop identity. |
| Proprietary apps, AI clients, gaming | OPTIONAL | Skip missing packages. Core package list is not a minimum dependency graph. |
| Package updates/migrations | NEEDS PATCH or explicit gate | Full Omarchy update orchestration assumes its repos, complete packages, snapshots and OS; must not silently upgrade a selective VM installation. |

Omarchy's generated theme state is separate from user configuration: `~/.local/state/omarchy/current/`. Its theme engine generates terminal, Hyprland, shell and app settings from bundled themes/templates. `OMARCHY_THEME_HEADLESS=1 omarchy-theme-set 'Tokyo Night'` is the upstream path for pre-session initialization; verify which optional setters execute and record skipped dependencies rather than deleting arbitrary failures.

## Recommended implementation

Choose a small compatibility installer around **released upstream v4.0.4**, retaining the exact upstream source tree. Install only native runtime dependencies, expected file locations, user configuration, fonts/icons and theme state. Keep VM kernel, initramfs, graphics, networking and filesystem unchanged. This selectively stages the same files the upstream ARM-aware package recipes stage, without their broad hardware/OS finalization. It is a compatibility layer, not a shell rewrite.

A full upstream package install may become the lower-maintenance route once stable ARM publication and generic-ARM settings preservation are complete. The immediately upstreamable issue is the settings post-install's narrow Apple-device-tree exemption: generic ArchARM/QEMU should also be able to retain its distro/network/security identity. Record installation omissions as deviations rather than claiming full distribution parity.

Validate shell IPC, native Foot, launcher, fonts/icons, expected bar, bound key dispatch, a real input event, and reboot. Capture renderer after each graphics-relevant package change. A usable-looking Qt shell is insufficient if it falls back to software rendering or the compositor reference regresses.
