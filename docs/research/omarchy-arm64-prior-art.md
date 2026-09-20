# ARM64 Omarchy prior art and package availability

Inspected 2026-09-20. This is supporting research for `omarchy-arm64.md`,
not a record of this project's desktop runtime validation. Live package
availability, fork assertions, and our own empirical results are separate.

## Pinned implementations inspected

| Project / pin | Relevant implementation | Reuse decision |
|---|---|---|
| [omarchy-arm](https://github.com/alexisraitano-myffu/omarchy-arm/tree/579f15c699dab01e2b3b12e2c4d2503873359be9) | `install.sh`, `install/arm/packages.*`, `pacman.sh`, `settings.sh`; installs onto an existing ArchARM machine and resolves package names against its repositories | Reuse explicit substitutions and separation of optional packages; do not execute its whole installer |
| [Try Omarchy](https://github.com/omacom/try-omarchy/tree/58cbac574f9b6ab7454cee9771c752f5e48bce8e) | `guest/spec.json`, `packages.lock.json`, `scripts/materialize-omarchy.sh`, `configure-rootfs.sh`; pinned upstream source, ARM package closure, package-style settings materialization | Best close-upstream reference for filesystem/session integration; do not import its host runtime or hardware bridges |
| [omarchy-arm-utm](https://github.com/ggalancs/omarchy-arm-utm/tree/bf5ca3e9d2631220e5093d84728dc415685872ae) | `build-omarchy-arm.sh`: live package intersection, native Hyprland ABI repair, settings installation | Useful evidence of package gaps; monolithic image builder conflicts with our layer discipline and existing known-good image |

The current Omacom organization also has
[omarchy-mac](https://github.com/omacom/omarchy-mac), aimed at Apple Silicon
hardware. Its hardware/boot assumptions are not this virtual machine's.
This research did not audit that project's implementation; its existence is
not evidence that an arbitrary installer is safe here.

Try Omarchy's inspected spec pins upstream Omarchy commit
`0534987009061cbe2dacdde4ad564092ab698d12`, labels release `4.0.3`, and keeps
Quattro's Lua Hyprland configuration, QML shell, and theme trees. It declares
virtio-gpu-gl → virgl → ANGLE/Metal, matching the broad shape of our proven
M1 graphics path. This is corroborating implementation evidence, not a
reason to replace our working kernel, compositor, QEMU, or GPU packages.

## Live ARM package inventory

Read the Arch Linux ARM [extra database](http://mirror.archlinuxarm.org/aarch64/extra/extra.db)
on the inspection date. Database SHA256:
`9d2bba137b8f19ce1b3cef6e4b63b3b0a12960566469e8127df49780d81f744e`.
Parsed evidence is saved locally at
`artifacts/diagnostics/2026-09-20-omarchy-research/archarm-extra-inventory.json`.
These are database metadata, not proof of an installable dependency closure
or successful runtime. The mirror's HTTPS certificate did not match its
hostname; inventory used its documented HTTP endpoint. Actual package
installation must retain pacman's signature verification.

| Component | Classification | Observed extra package | Role / smallest adaptation |
|---|---|---|---|
| Hyprland | NATIVE ARM64 | `hyprland 0.56.2-3`, aarch64 | Preserve working installed compositor; use upstream Lua config supported by current Hyprland |
| Quickshell | NATIVE ARM64 | `quickshell 0.3.1-1`, aarch64 | Real shell runtime; test actual upstream QML imports and launcher |
| Qt base / declarative | NATIVE ARM64 | `qt6-base 6.11.2-3`, `qt6-declarative 6.11.2-2` | Quickshell dependencies |
| Qt Wayland / SVG / multimedia / imageformats | NATIVE ARM64 | each `6.11.2-1` | Native platform plugin and shell media/icon support; install imports actually used |
| Foot | NATIVE ARM64 | `foot 1.28.0-2` | Already proven terminal; retain |
| UWSM | ARCH-INDEPENDENT | `uwsm 0.27.0-1`, any | Omarchy session environment and systemd lifecycle |
| xdg-terminal-exec | ARCH-INDEPENDENT | `xdg-terminal-exec 0.14.3-2`, any | Required for upstream terminal dispatch; no local wrapper needed |
| SDDM | NATIVE ARM64 | `sddm 0.21.0-7` | Available login manager; optional if existing managed session gives equivalent reboot behavior |
| Hyprland portal | NATIVE ARM64 | `xdg-desktop-portal-hyprland 1.4.1-2` | Desktop integration; optional screen-sharing picker can use portal fallback |
| Polkit agent | NATIVE ARM64 | `hyprpolkitagent 0.1.3-10` | Authentication UI if enabled |
| Idle / lock | NATIVE ARM64 | `hypridle 0.1.8-2`, `hyprlock 0.9.6-3` | Available; test independently from core launch |
| Nerd Font | NEEDS SUBSTITUTE | `ttf-jetbrains-mono-nerd 3.5.1-2`, any | Substitute upstream trimmed `ttf-jetbrains-mono-nerd-basic` |
| Noto / emoji | ARCH-INDEPENDENT | `noto-fonts 1:2026.09.01-1`, `noto-fonts-emoji 1:2.051-1` | Text / emoji correctness |
| Font Awesome | ARCH-INDEPENDENT | `woff2-font-awesome 7.3.1-1` | Icon font |
| Omarchy custom font, themes, Lua/QML, icons | ARCH-INDEPENDENT | Upstream source assets | Materialize package file mappings rather than finding ARM binaries |
| Yaru icons | UNKNOWN | Absent from inspected extra DB | Fork lists AUR recipe; inspect/build separately only if core icons require it |
| Gum / jq | NATIVE ARM64 | `gum 2.0.1-1`, `jq 1.8.2-1` | Script and menu helpers |
| Browser | OPTIONAL / NATIVE ARM64 | `chromium 153.0.8010.36-1` | No need to port an x86 browser to validate shell |
| Neovim | OPTIONAL / NEEDS SUBSTITUTE | Fork maps `nvim` → `neovim` | Separate editor configuration from binary availability |
| Omarchy runtime/settings packages | NEEDS PATCH | Architecture-independent content, custom packaging | Install pinned upstream files into proper locations without copying x86 pacman repositories |
| AUR helper, mise, AI CLIs | OPTIONAL | Try Omarchy pins ARM64 yay and mise assets | Not required to prove core desktop; avoid provisioning toolchains on first pass |
| Bootloader, microcode, GPU-vendor tuning | OPTIONAL | Hardware-specific upstream leaves | Skip; keep stock ARM kernel and proven virgl path |

Do not generalize “absent from extra” to “has no ARM source.” The arm fork's
`packages.unavailable` includes source-buildable first-party software and
architecture-independent `omarchy-nvim`, despite its comment claiming no
ARM source. Its metadata is an installer policy, not an architectural verdict.

## Concrete fork findings that change implementation choices

1. `omarchy-arm` preserves ArchARM repository URLs while applying cosmetic
   pacman settings. This is the correct principle. Its broad `apply-system`
   and `settings.sh` phases still change system integration, firewall and user
   provisioning; they are too broad for our existing control VM.
2. Its `packages.aur-required` lists `xdg-terminal-exec`, `mise-bin`, and
   `ufw-docker`. **The first entry is stale:** the live extra database now
   supplies `xdg-terminal-exec`. Mise and Docker firewall integration are
   requirements of that fork's complete provisioning path, not of Quickshell.
3. Try Omarchy's materializer maps commands to `/usr/bin`, data to
   `/usr/share/omarchy`, user configuration from `config`, UWSM environment,
   user units, Wayland session entry, fontconfig, custom `omarchy.ttf`, and
   application icons. Merely copying a checkout into a home directory misses
   these package-owned integration points. Reuse the mapping selectively.
4. Try Omarchy ships its own terminal-exec compatibility script. Prefer the
   now-available distribution package, avoiding that wrapper's restricted
   desktop-file implementation.
5. Try Omarchy pins/builds Hyprland `0.56.1`, aquamarine and hyprtoolkit with
   a graphics patch. The UTM builder also carries repair logic for temporary
   ArchARM soname mismatches. These are historical dependency-closure fixes,
   not mandatory ARM adaptations. Our proven `0.56.2` is stronger evidence.
6. The UTM builder intersects upstream packages with available packages and
   can skip absent names. A minimal installer here should instead fail on a
   missing **core** dependency and explicitly enumerate optional omissions.
7. Fork documentation reports x86-only Omarchy package endpoints returning
   404. Our HTTP requests to the ARM Omarchy repository and pinned mirror
   returned 403; that is not independent confirmation of a 404/nonexistence.
   Preserve ArchARM repositories regardless; no need to depend on these URLs.

## Recommended minimal reuse

Keep a pinned upstream Omarchy checkout plus a small, auditable integration
layer. Use ArchARM packages for native code; package upstream data and
commands locally or install an explicit file manifest; seed only the dedicated
desktop user's configuration; choose one theme and initialize its generated
files; install fonts, session environment and actual shell units. Do not run
upstream system provisioning, hardware detection, or package refresh wholesale.

Record every deviation (package-name substitutions, VM monitor/keybinding
settings, skipped optional services/apps, and any session wrapper). Keep
updates explicit until the upstream refresh path is audited: a working first
boot is insufficient if a later refresh overwrites pacman repositories or
re-enables x86 hardware setup. Test the real shell and terminal launch path,
then reboot and repeat renderer checks. No source-build work is currently
justified for the core Quickshell/Foot stack by package availability alone.
