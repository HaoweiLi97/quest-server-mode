import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

/** Changes physical panel power without sending Android a sleep event. */
public final class DisplayPanel {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && !java.util.Arrays.asList("power-api", "list", "hold", "off", "on").contains(args[0])) {
            throw new IllegalArgumentException("Expected list, hold, off, on, or power-api");
        }
        if (args.length > 0 && "power-api".equals(args[0])) {
            for (Method method : Class.forName("android.os.IPowerManager").getMethods()) {
                String name = method.getName();
                if (name.equals("acquireWakeLock") || name.equals("releaseWakeLock") || name.equals("wakeUp")) {
                    System.out.println(method);
                }
            }
            return;
        }
        Class<?> surface = Class.forName("android.view.SurfaceControl");
        if (args.length == 0) {
            for (Method method : surface.getDeclaredMethods()) {
                String name = method.getName();
                if (name.contains("PhysicalDisplay") || name.contains("DisplayPower") || name.contains("InternalDisplay")) {
                    System.out.println(method);
                }
            }
            return;
        }
        Class<?> factory = Class.forName("com.android.internal.os.ClassLoaderFactory");
        Method create = factory.getDeclaredMethod("createClassLoader", String.class, String.class,
            String.class, ClassLoader.class, int.class, boolean.class, String.class);
        ClassLoader loader = (ClassLoader) create.invoke(null, System.getenv("SYSTEMSERVERCLASSPATH"),
            null, null, ClassLoader.getSystemClassLoader(), 0, true, null);
        Class<?> control = loader.loadClass("com.android.server.display.DisplayControl");
        Method load = Runtime.class.getDeclaredMethod("loadLibrary0", Class.class, String.class);
        load.setAccessible(true);
        load.invoke(Runtime.getRuntime(), control, "android_servers");
        Method idsMethod = control.getDeclaredMethod("getPhysicalDisplayIds");
        Method tokenMethod = control.getDeclaredMethod("getPhysicalDisplayToken", long.class);
        Method powerMethod = surface.getDeclaredMethod("setDisplayPowerMode", Class.forName("android.os.IBinder"), int.class);
        long[] ids = (long[]) idsMethod.invoke(null);
        if ("hold".equals(args[0])) {
            Class<?> binder = Class.forName("android.os.IBinder");
            Object service = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "power");
            Object power = Class.forName("android.os.IPowerManager$Stub").getMethod("asInterface", binder).invoke(null, service);
            Class<?> api = Class.forName("android.os.IPowerManager");
            Object token = Class.forName("android.os.Binder").newInstance();
            Method acquire = api.getMethod("acquireWakeLock", binder, int.class, String.class,
                String.class, Class.forName("android.os.WorkSource"), String.class, int.class,
                Class.forName("android.os.IWakeLockCallback"));
            acquire.invoke(power, token, 0x1000000a, "QuestServer:PanelOffPerformance", "com.android.shell", null, null, 0, null);
            Method wake = api.getMethod("wakeUp", long.class, int.class, String.class, String.class);
            Method interactive = api.getMethod("isInteractive");
            Method uptime = Class.forName("android.os.SystemClock").getMethod("uptimeMillis");
            Object pid = Class.forName("android.os.Process").getMethod("myPid").invoke(null);
            Files.write(Paths.get("/data/local/tmp/quest-server-mode.pid"), pid.toString().getBytes(StandardCharsets.UTF_8));
            try {
                boolean announced = false;
                while (true) {
                    if (!((Boolean) interactive.invoke(power))) {
                        wake.invoke(power, uptime.invoke(null), 2, "quest-server-mode", "com.android.shell");
                        Thread.sleep(500);
                    }
                    for (long id : ids) powerMethod.invoke(null, tokenMethod.invoke(null, id), 0);
                    if (!announced) {
                        System.out.println("SERVER_MODE_READY pid=" + pid + " physical_panel=off logical_state=awake");
                        announced = true;
                    }
                    Thread.sleep(5000);
                }
            } finally {
                api.getMethod("releaseWakeLock", binder, int.class).invoke(power, token, 0);
                for (long id : ids) powerMethod.invoke(null, tokenMethod.invoke(null, id), 2);
            }
        }
        int mode = "off".equals(args[0]) ? 0 : 2;
        for (long id : ids) {
            if ("list".equals(args[0])) {
                System.out.println("DISPLAY=" + id);
                continue;
            }
            Object token = tokenMethod.invoke(null, id);
            powerMethod.invoke(null, token, mode);
            System.out.println("DISPLAY=" + id + " POWER_MODE=" + mode);
        }
    }
}
