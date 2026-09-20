# Cheap Pixel AVF evidence check after an OTA

`./dev/probe-pixel-avf` inventories supplied text logs without starting a VM,
installing an APK, changing settings, or writing anything on the phone. Python 3
is the only offline dependency; optional identity collection requires an already
installed `adb` and an authorized device. Nothing is installed automatically.

```sh
# Safe offline baseline; no adb calls.
./dev/probe-pixel-avf artifacts/diagnostics/2026-09-20-pixel

# Analyze a single experiment instead of conflating multiple runs.
./dev/probe-pixel-avf artifacts/diagnostics/2026-09-20-pixel/app-gfxstream-console.log --json

# Explicit optional physical-device read: adb shell getprop only.
# Use a fresh evidence directory for every OTA/build.
mkdir -p artifacts/diagnostics/YYYY-MM-DD-pixel-ota
./dev/probe-pixel-avf artifacts/diagnostics/YYYY-MM-DD-pixel-ota \
  --collect-identity --serial DEVICE_SERIAL --json \
  > artifacts/diagnostics/YYYY-MM-DD-pixel-ota/identity.json
```

The last command may start the local adb server. It never runs `adb root`,
`push`, `install`, `settings`, permission grants, VM commands, or guest tests.
Unplugged, unauthorized, ambiguous, or timed-out devices leave live identity
UNKNOWN and include the adb error. Omit `--collect-identity` for guaranteed
absence of device access. Current identity is reported separately from historical
logs: attaching a newer build does not make old evidence apply to the new build.

The inventory accepts `.log`, `.txt`, and `.out`, at most 20 MiB per file,
recursively, skipping symlinks. Images and JSON reports are not interpreted.
It prints PASS/FAIL/UNKNOWN with source filenames and line numbers, or JSON
with `--json`. Exit status 0 means the inventory ran, **not** that graphics
passed; consume `checks["accelerated Mesa"].status` in automation. A missing
input is an argument error.

Interpret the checks narrowly:

- **Build:** a logged `ro.build.fingerprint=...` or
  `ro.build.version.incremental=...` identifies the historical build.
  Live identity contains the current fingerprint, release, patch and device.
- **AVF capability / pKVM advertised:** explicit normalized property lines
  such as `ro.boot.hypervisor.vm.supported=1` establish advertised support.
  A protected-VM support property or `kvm.arm-protected` string does not prove
  a protected guest booted. **Protected guest boot stays UNKNOWN**: this project
  has only non-protected guest evidence and no protected-boot attestation parser.
- **Custom-image boot:** requires the repository's `M1-AVF-PROBE` marker and
  a multi-user/graphical systemd target in the same supplied log. It establishes
  that this custom guest reached that target, not every milestone requirement.
- **Display configured:** a crosvm display argument records configuration,
  not visible output, working input, or a successful Surface connection.
- **Virtio GPU:** actual guest driver detection/initialization is required.
  Device presence and advertised `+virgl` alone cannot establish 3D.
- **virglrenderer / gfxstream backend:** a crosvm `--gpu` argument naming the
  backend establishes selection only. A filename such as
  `app-gfxstream-console.log` is deliberately insufficient evidence. Preserve
  the host launch command and errors with any future experiment.
- **3D guest contexts:** no-host-context / rutabaga build failures are negative
  evidence. A qualifying accelerated renderer is positive evidence. Capset
  IDs or context-init feature bits alone remain UNKNOWN.
- **Renderer:** PASS means an actual GL renderer or Vulkan device name was
  captured. Consult the accompanying text; llvmpipe still passes this inventory
  check while failing acceleration.
- **Accelerated Mesa:** software renderer names produce FAIL. A virgl,
  Virtio GPU, or Venus renderer requires nearby preceding `Accelerated: yes`
  output (within 15 lines, normally `glxinfo -B`) to produce PASS. EGL-only
  evidence without that explicit flag remains UNKNOWN. Retain complete,
  unedited `glxinfo -B` output for any claimed success.

Mixed PASS and FAIL observations become UNKNOWN with a conflict note; this is
particularly relevant to combined directories. Check a single experiment log
and its exact build before deciding that an OTA fixed anything. Repeated software
renderers alongside an accelerated renderer also require inspection. Output is
a conservative text inventory, not attestation; truncated or unrecognized
formats remain UNKNOWN.

For the retained September 20 Pixel console logs the offline result is custom
boot and virtio GPU PASS, 3D contexts and accelerated Mesa FAIL, with llvmpipe
captured. Build properties, backend launch lines, display and protected boot
cannot be reconstructed from those console logs alone. The recorded conclusions
and missing host-side details remain in [the Pixel test plan](../pixel-test-plan.md).

An OTA is a reason to collect a new identity and inspect host capabilities, not
to repeat invasive experiments automatically. Actual tests remain governed by
[the Pixel test plan](../pixel-test-plan.md), including its stop conditions and
preserved staging. Keep custom-image/app, Terminal, and CLI results separate;
use the existing guest overlay probe for new console evidence. This command
provides no path to start those tests. Update `docs/status.md` when a new
experiment changes the project's understanding.
