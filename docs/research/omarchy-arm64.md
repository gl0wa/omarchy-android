# Omarchy ARM64 — Milestone 2 implementation and compatibility matrix

Validated 2026-09-20/21 on the existing Apple M4 Pro UTM guest. **Core desktop
works, including reboot, without a compositor, Mesa, Quickshell or upstream
Omarchy source patch.** This is a minimal desktop profile, not the full ISO's
application/system-management installation.

## Source and installation decision

Pin [Omarchy v4.0.4](https://github.com/omacom/omarchy/tree/c668141e9c42b13c80c9ca4ea108e11708c5e8a5),
commit `c668141e9c42b13c80c9ca4ea108e11708c5e8a5`. The default branch was
`quattro`, with newer changes and a stale alpha version file; use the released
commit rather than inferring release status from that file.

Choose **C: unchanged upstream + small VM integration layer**. Current upstream
uses Lua Hyprland configuration, Quickshell for bar/menu/background/services,
Foot, UWSM and SDDM. ArchARM already supplies the native dependencies. Upstream
ARM-aware PKGBUILDs provide packaging precedent, but the stable ARM feed lacked
core packages at inspection and the edge core lagged at 4.0.2. Generic QEMU ARM
also does not match the settings package's Apple hardware exemption from global
OS/PAM/NSS edits. We therefore copy selected source payloads, not the ISO's
system provisioning. No unofficial ARM binary feed or AUR package is required.

Detailed primary-source audit: [upstream](omarchy-arm64-upstream.md).
Audited ARM ports and live package inventory: [prior art](omarchy-arm64-prior-art.md).
These include current omarchy-arm, omacom/try-omarchy and omacom/omarchy-mac.
Their historical ABI downgrades and broad hardware settings are not inherited.

## Core compatibility matrix

| Component / upstream | Installation | ARM classification | Observed status / workaround | Upstreamable? |
|---|---|---|---|---|
| ArchARM kernel/systemd/network | Existing M1 image | NATIVE ARM64 | 7.2.6, healthy networkd + SSH; unchanged | None needed |
| Mesa/virtio-gpu | Existing ArchARM mesa 26.2.3 | NATIVE ARM64 | virgl/ANGLE, GLX Accelerated: yes; no Vulkan on this host | None needed |
| Hyprland | ArchARM 0.56.2-3 | NATIVE ARM64 | Upstream Lua config parses; native compositor works | None needed |
| Quickshell | ArchARM 0.3.1-1 | NATIVE ARM64 | Unchanged upstream shell loads and IPC responds | None needed |
| Qt 6 modules | ArchARM 6.11.2 family | NATIVE ARM64 | Qt Quick, Wayland, multimedia, SVG/imageformats work | None needed |
| Omarchy core/shell/scripts | Pinned Git archive → /usr/share/omarchy | ARCH-INDEPENDENT | Actual bar, wallpaper, menus, notifications/services loaded | Upstream packaging already supports ARM |
| Foot + terminal dispatch | ArchARM Foot 1.28.0-2, xdg-terminal-exec, UWSM | NATIVE ARM64 | Native Wayland; menu and keyboard launch verified | None needed |
| Bash prompt/utilities | ArchARM bash, starship, zoxide, fzf, gum, jq, etc. | NATIVE ARM64 | Upstream Bash configuration and prompt work | None needed |
| UWSM/session environment | ArchARM 0.27.0-1 + upstream env file | NATIVE ARM64 | tty1 autologin → UWSM → Hyprland; survives restart/reboot | VM policy only |
| SDDM login | Upstream default, not installed here | OPTIONAL | Replaced by existing-development-account tty login | VM policy only |
| JetBrains Mono Nerd basic | ArchARM full ttf-jetbrains-mono-nerd | NEEDS SUBSTITUTE | Same font family, larger package; text/icons render | Package dependency alternative possible |
| Omarchy font + Tokyo Night theme | Upstream font/templates/assets | ARCH-INDEPENDENT | Correct menu glyphs, colors, wallpaper, themed Foot | None needed |
| Yaru icon theme | Absent in inspected ArchARM extra | NEEDS SUBSTITUTE | Adwaita icon theme; visible core menu/app icons verified | Profile choice, no source patch |
| Portal/polkit/PipeWire | ArchARM packages | NATIVE ARM64 | Session starts; screen sharing/audio not acceptance-tested | None needed |
| NetworkManager/BlueZ UI backends | Not enabled | OPTIONAL | networkd wired networking retained; Wi-Fi/BT controls unavailable | VM profile limitation |
| Limine/UKI/Snapper/x86 kernel packages | ISO-specific | OPTIONAL | Skip; direct boot + APFS checkpoint retained | Already ARM-aware upstream |
| GPU/laptop hardware provisioning | Upstream hardware scripts | OPTIONAL | Skip; virtual GPU configuration retained | Profile choice |
| Native launcher apps database | Upstream shell, installed .desktop files | ARCH-INDEPENDENT | Apps → Foot launches successfully | None needed |
| Proprietary bundled apps | Various upstream/AUR/release assets | UNKNOWN / OPTIONAL | Not installed; no unsupported binary assumptions accepted | Investigate only on demand |

No core component currently requires **NEEDS PATCH**. Availability of every
optional app is not a claim of this milestone. Explicitly omitted examples:
1Password, Spotify, Signal, Obsidian, gaming/Steam, Docker, editor/browser suites,
Voxtype, fingerprint setup, laptop utilities and the Omarchy appliance updater.
Some have ARM ports; omission here does not mean they cannot run on ARM.
The upstream audit records x86 download/boot assumptions in the relevant scripts.

## Every deviation from upstream

| Upstream behavior | Our change and reason | ARM-specific? / maintenance |
|---|---|---|
| ISO and paired omarchy/settings packages own system configuration | Source pin + selective payload in /usr/share/omarchy, commands linked in /usr/local/bin | Temporary integration until suitable stable ARM packages; explicit layers to review per release |
| Full application set | Native core package manifest only | Scope policy; low maintenance |
| SDDM entry starts UWSM | tty1 development autologin starts UWSM with explicit Lua config | VM-specific; no desktop source patch |
| Default monitor auto scaling, GTK 2x | User monitors.lua selects scale 1 / GTK 1 | VM display policy; ordinary supported override |
| Preinstalled-app bindings enabled | Supported omarchy_preinstalled_bindings=false | Scope policy; ordinary config flag |
| Cmd/Super bindings may be intercepted by macOS | Add Ctrl+Shift+Return/Space/A/W for terminal/menu/apps/close | Mac-specific; upstream defaults retained |
| Full first-run provisioning | Mark first-run-user skipped after selective setup | Explicit VM profile; no hardware/network/app provisioning |
| Idle lock on an account with a password | Built-in stay-awake selected for existing passwordless development account | VM-only; no authentication changes; configure a real login before personal use |
| JetBrains basic/Yaru packages | Full JetBrains Nerd/Adwaita | Two package substitutions; no binary patch |

All upstream source files under /usr/share/omarchy are copied unchanged. The
user configuration contains the overrides. The independent review confirmed
this approach and identified retry/backup/evidence concerns, which were fixed.
There is no permanent Omarchy fork. Optional scripts remain present but their
full-system install/update functions are outside this minimal profile; do not
use them to upgrade the VM as though it were an Omarchy ISO installation.

## Reproduction and validation

See [the M2 runbook](../bringup.md#milestone-2-omarchy-arm64-on-the-existing-utm-vm).
`dev/install-omarchy` exposes packages/core/session stages; each records its own
logs and runs GPU checks before and after. `dev/test-omarchy` checks live shell
IPC/surfaces, native Foot, actual launcher layer, input inventory, configuration
errors, system health, and explicit accelerated GLX. It saves screenshots.
`dev/test-desktop` refuses to overwrite Omarchy with M1 configs.

Final successful run:
`artifacts/diagnostics/2026-09-21-omarchy-validation-VLobP7/`.
GLX: `virgl (ANGLE (Apple, Apple M4 Pro, OpenGL 4.1 Metal - 90.5))`,
`Accelerated: yes`, Mesa `26.2.3`. Boot ID after reboot:
`7d5d169c-649f-4ec9-afc6-2e7710c7fd21` (previous ID captured separately).
UTM keyboard test after reboot: Ctrl+Shift+Return opened Foot; typing `pwd`
returned `/home/desktop`. Menu keyboard selection launched Foot before reboot;
launcher and native Foot were rechecked by the final automated run afterward.
Core-stage rerun succeeded and preserved user configuration/theme state.

Expected log limitations: missing NetworkManager and BlueZ backends; one portal
app-ID warning; no Vulkan device. None changes the verified GLES/Wayland path.

## Next milestone

Preserve this ARM64 desktop as the guest reference. After a newer Android build,
use [the Pixel evidence runbook](pixel-probe-usage.md) and existing APK/probe flow
to check host acceleration. Transfer the guest only when real contexts and
accelerated Mesa are demonstrated. No Pixel modifications were made during M2.
