package dev.omarchy.m1host;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * M1 host: launches our Arch guest with GPU via framework AVF APIs.
 * Everything virtualization-related goes through reflection (@hide/SystemApi).
 * All steps + full stack traces go to logcat (M1HOST) and the on-screen log.
 */
public class MainActivity extends Activity {
    private static final String TAG = "M1HOST";
    private static final String PKG = "android.system.virtualmachine.";

    private TextView logView;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean running;

    // Staged on device by earlier adb pushes (Phase D session 1).
    private static final String KERNEL = "/data/local/tmp/m1-Image";
    private static final String INITRD = "/data/local/tmp/m1-initramfs.img";
    private static final String DISK = "/data/local/tmp/m1-arch.raw";
    private static final String[] PARAMS = {
        "root=/dev/vda", "rw", "rootwait", "console=ttyS0,115200", "earlycon",
        // App path honors match_host -> guest sees Tensor MPAM -> without this
        // the kernel dies in init_cpu_features (MRS MPAMIDR_EL1, d538a481).
        // (PocketVM documented the same quirk; vm-run path never hit it.)
        "arm64.nompam"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Button btn = new Button(this);
        btn.setText("Start Arch GPU VM");
        btn.setOnClickListener(v -> startVmThread());
        logView = new TextView(this);
        logView.setTextIsSelectable(true);
        logView.setTextSize(11);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(logView);
        root.addView(btn);
        root.addView(scroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
        setContentView(root);
        log("M1 host ready. files: " + getFilesDir());
        // Auto-start shortly after launch (fewer taps during experiments).
        ui.postDelayed(this::startVmThread, 1500);
    }

    private void log(String s) {
        Log.i(TAG, s);
        final String line = s;
        ui.post(() -> {
            logView.append(line + "\n");
            // Keep the view bounded.
            CharSequence t = logView.getText();
            if (t.length() > 60000) {
                logView.setText(t.subSequence(t.length() - 45000, t.length()));
            }
        });
    }

    private void startVmThread() {
        if (running) {
            log("already running");
            return;
        }
        running = true;
        new Thread(() -> {
            try {
                boot();
            } catch (Throwable t) {
                log("FATAL: " + t);
                for (StackTraceElement e : t.getStackTrace()) log("  at " + e);
                Throwable c = t.getCause();
                while (c != null) {
                    log("Caused by: " + c);
                    c = c.getCause();
                }
            } finally {
                running = false;
            }
        }, "m1-vm").start();
    }

    // ---- reflection helpers ----
    private static Class<?> cls(String name) throws Exception {
        return Class.forName(name);
    }

    private static Object call(Object recv, String method, Object... args)
            throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a instanceof Boolean) types[i] = boolean.class;
            else if (a instanceof Integer) types[i] = int.class;
            else if (a instanceof Long) types[i] = long.class;
            else types[i] = a.getClass();
        }
        Method m = recv.getClass().getMethod(method, types);
        m.setAccessible(true);
        return m.invoke(recv, args);
    }

    private static void dumpMethods(String className) {
        try {
            StringBuilder sb = new StringBuilder(className).append(" methods:");
            for (Method m : cls(className).getMethods()) {
                sb.append("\n  ").append(m.getName()).append("(");
                Class<?>[] ps = m.getParameterTypes();
                for (int i = 0; i < ps.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(ps[i].getSimpleName());
                }
                sb.append(")");
            }
            Log.i(TAG, sb.toString());
        } catch (Throwable t) {
            Log.i(TAG, "dumpMethods " + className + " failed: " + t);
        }
    }

    private void copyToPrivate(String srcPath, File dst) throws Exception {
        log("copy " + srcPath + " -> " + dst + " (" + new File(srcPath).length() + "B)");
        try (InputStream in = new java.io.FileInputStream(srcPath);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[1 << 20];
            long total = 0, last = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
                if (total - last > (1L << 31)) {
                    last = total;
                    log("  ... " + (total >> 20) + " MiB");
                }
            }
        }
        log("copy done");
    }

    private String stage(String srcPath, String name) throws Exception {
        File src = new File(srcPath);
        if (src.canRead()) {
            log("using staged file directly: " + srcPath);
            return srcPath;
        }
        log("cannot read " + srcPath + " as app uid -> copying to private dir");
        File dst = new File(getFilesDir(), name);
        if (!dst.exists()) copyToPrivate(srcPath, dst);
        return dst.getAbsolutePath();
    }

    private void boot() throws Exception {
        dumpMethods(PKG + "VirtualMachineManager");
        dumpMethods(PKG + "VirtualMachineConfig$Builder");
        dumpMethods(PKG + "VirtualMachineCustomImageConfig$Builder");
        dumpMethods(PKG + "VirtualMachineCustomImageConfig$GpuConfig$Builder");
        dumpMethods(PKG + "VirtualMachine");

        String kernel = stage(KERNEL, "m1-Image");
        String initrd = stage(INITRD, "m1-initramfs.img");
        String disk = stage(DISK, "m1-arch.raw");

        // --- custom image config ---
        Object cb = cls(PKG + "VirtualMachineCustomImageConfig$Builder")
                .getConstructor().newInstance();
        call(cb, "setName", "arch-gpu");
        call(cb, "setKernelPath", kernel);
        call(cb, "setInitrdPath", initrd);
        for (String p : PARAMS) call(cb, "addParam", p);
        Object rwDisk = cls(PKG + "VirtualMachineCustomImageConfig$Disk")
                .getMethod("RWDisk", String.class).invoke(null, disk);
        call(cb, "addDisk", rwDisk);
        // Display 1280x720 like Terminal.
        Object db = cls(PKG + "VirtualMachineCustomImageConfig$DisplayConfig$Builder")
                .getConstructor().newInstance();
        call(db, "setWidth", 1280);
        call(db, "setHeight", 720);
        call(db, "setHorizontalDpi", 160);
        call(db, "setVerticalDpi", 160);
        call(db, "setRefreshRate", 60);
        // Display 1280x720 like Terminal. Toggle for isolation experiments:
        // env M1_NO_GPU is read from a private flag file (simpler: constant).
        final boolean noGpu = new File(getFilesDir(), "NOGPU").exists();
        if (!noGpu) {
        call(cb, "setDisplayConfig", call(db, "build"));
        } else {
            log("NOGPU flag present: skipping display+gpu config (isolation)");
        }
        call(cb, "useKeyboard", true);
        call(cb, "useMouse", true);
        call(cb, "useTouch", true);
        call(cb, "useTrackpad", true);
        call(cb, "useSwitches", true);
        // NOTE: useNetwork(true) fails for third-party apps on this build:
        // "Failed to create a TAP interface" (tethering/TAP is priv-app
        // gated). GPU proof doesn't need net; revisit separately.
        call(cb, "useNetwork", false);
        // GPU: virglrenderer + virgl2 (Terminal defaults egl/gles/surfaceless already true).
        // Skipped entirely when <files>/NOGPU exists (isolation experiments).
        if (!noGpu) {
            Object gb = cls(PKG + "VirtualMachineCustomImageConfig$GpuConfig$Builder")
                    .getConstructor().newInstance();
            // Backend under test is read from <files>/BACKEND (default virglrenderer).
            // One variable at a time: virglrenderer already proven to SIGABRT
            // this build's crosvm ("invalid rutabaga build parameters").
            String backend = readFlagFile("BACKEND", "virglrenderer");
            String[] contexts = backend.equals("gfxstream")
                    ? new String[]{"gfxstream-vulkan", "gfxstream-composer"}
                    : new String[]{"virgl2"};
            log("gpu backend under test: " + backend);
            call(gb, "setBackend", backend);
            call(gb, "setContextTypes", (Object) contexts);
            call(cb, "setGpuConfig", call(gb, "build"));
        }
        Object customCfg = call(cb, "build");
        log("custom image config built");

        // --- outer config ---
        Class<?> cfgCls = cls(PKG + "VirtualMachineConfig");
        Object cfgB = cls(PKG + "VirtualMachineConfig$Builder")
                .getConstructor(android.content.Context.class).newInstance(this);
        call(cfgB, "setCustomImageConfig", customCfg);
        call(cfgB, "setProtectedVm", false);
        call(cfgB, "setMemoryBytes", 4096L * 1024 * 1024);
        call(cfgB, "setCpuTopology", 1); // CPU_TOPOLOGY_MATCH_HOST
        call(cfgB, "setConsoleInputDevice", "ttyS0");
        call(cfgB, "setVmOutputCaptured", true);
        call(cfgB, "setVmConsoleInputSupported", true);
        call(cfgB, "setConnectVmConsole", true);
        call(cfgB, "setDebugLevel", 1); // DEBUG_LEVEL_FULL
        Object cfg = call(cfgB, "build");
        log("outer config built");

        Object vmm = cls(PKG + "VirtualMachineManager")
                .getConstructor(android.content.Context.class).newInstance(this);
        Object vm;
        try {
            vm = call(vmm, "create", "arch-gpu", cfg);
            log("vm created");
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Name reuse: same name as a deleted VM is an error; try get().
            log("create failed: " + e.getCause() + " -> trying get(arch-gpu)");
            vm = call(vmm, "get", "arch-gpu");
            if (vm == null) throw new RuntimeException(e.getCause());
            log("reusing existing vm; reconfiguring");
            call(vm, "setConfig", cfg);
        }
        call(vm, "run");
        log("vm.run() returned; status=" + call(vm, "getStatus"));

        // Pump guest serial console + captured VM log to UI + private files.
        File outFile = new File(getFilesDir(), "vm-console.log");
        File logFile = new File(getFilesDir(), "vm-log.log");
        log("console -> " + outFile + " ; vm log -> " + logFile);
        final Object vmRef = vm;
        new Thread(() -> pump("LOG", vmRef, "getLogOutput", logFile), "m1-log").start();
        pump("CON", vm, "getConsoleOutput", outFile);
        log("console stream ended; status=" + call(vm, "getStatus"));
    }

    private String readFlagFile(String name, String def) {
        try {
            File f = new File(getFilesDir(), name);
            if (!f.exists()) return def;
            byte[] b = new byte[(int) Math.min(f.length(), 256)];
            try (InputStream in = new java.io.FileInputStream(f)) {
                int n = in.read(b);
                if (n > 0) return new String(b, 0, n, "UTF-8").trim();
            }
        } catch (Throwable t) {
            log("flag read failed: " + t);
        }
        return def;
    }

    private void pump(String tag, Object vm, String getter, File outFile) {
        try (InputStream in = (InputStream) call(vm, getter);
             OutputStream out = new FileOutputStream(outFile, true)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                final String chunk = "[" + tag + "] "
                        + new String(buf, 0, n, "UTF-8");
                ui.post(() -> {
                    logView.append(chunk);
                    CharSequence t = logView.getText();
                    if (t.length() > 60000) {
                        logView.setText(t.subSequence(t.length() - 45000, t.length()));
                    }
                });
            }
        } catch (Throwable t) {
            log(tag + " pump ended: " + t);
        }
    }
}
