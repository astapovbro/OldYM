package ru.oldym;

import android.content.Context;
import java.io.*;

/** Private crash report. No tokens, HTTP bodies, exception messages or account data. */
final class CrashLog {
    private static boolean installed;
    static synchronized void install(final Context context) {
        if (installed) return;
        installed = true;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread thread, Throwable problem) {
                final Throwable original = problem;
                try {
                    FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), "last-crash.txt"));
                    try {
                        PrintWriter writer = new PrintWriter(out);
                        writer.println("RetroMusic 0.4 / Android " + android.os.Build.VERSION.RELEASE);
                        int causes = 0;
                        while (problem != null && causes++ < 4) {
                            writer.println(problem.getClass().getName());
                            StackTraceElement[] frames = problem.getStackTrace();
                            for (int i = 0; i < Math.min(frames.length, 24); i++) writer.println("  at " + frames[i]);
                            problem = problem.getCause();
                        }
                        writer.flush();
                    } finally { out.close(); }
                } catch (Throwable ignored) { }
                if (previous != null) previous.uncaughtException(thread, original);
                else { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(10); }
            }
        });
    }
    static String consume(Context context) {
        File file = new File(context.getFilesDir(), "last-crash.txt");
        if (!file.isFile()) return null;
        try {
            InputStream in = new FileInputStream(file);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try { byte[] data = new byte[1024]; int n; while ((n = in.read(data)) != -1 && out.size() < 16384) out.write(data, 0, n); }
            finally { in.close(); }
            file.delete(); return new String(out.toByteArray(), "UTF-8");
        } catch (Exception ignored) { return null; }
    }
}
