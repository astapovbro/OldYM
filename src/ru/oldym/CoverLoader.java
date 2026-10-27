package ru.oldym;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.widget.ImageView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One image worker, a bounded queue and sampled decoding for small Dalvik heaps. */
final class CoverLoader {
    private static final int CACHE_BYTES = 384 * 1024;
    private final Context context;
    private final Handler main = new Handler();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 20,
        TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(8),
        new ThreadPoolExecutor.DiscardOldestPolicy());
    private final LinkedHashMap<String, Bitmap> memory =
        new LinkedHashMap<String, Bitmap>(16, 0.75f, true);
    private int bytes;
    private volatile int generation;
    private volatile boolean closed;

    CoverLoader(Context c) { context = c.getApplicationContext(); }

    private static int weight(Bitmap b) { return b.getRowBytes() * b.getHeight(); }
    private synchronized Bitmap cached(String key) { return memory.get(key); }
    private synchronized void remember(String key, Bitmap image) {
        Bitmap previous = memory.put(key, image);
        if (previous != null) bytes -= weight(previous);
        bytes += weight(image);
        Iterator<Map.Entry<String, Bitmap>> it = memory.entrySet().iterator();
        while (bytes > CACHE_BYTES && it.hasNext()) {
            bytes -= weight(it.next().getValue());
            it.remove();
        }
        // A removed bitmap can still be shown by a visible ImageView. Do not recycle it.
    }
    synchronized void trim() { memory.clear(); bytes = 0; }
    void cancelPending() { generation++; worker.getQueue().clear(); main.removeCallbacksAndMessages(null); }
    void close() { closed = true; cancelPending(); worker.shutdownNow(); trim(); }

    private File file(Api.Track track, int size) {
        return new File(context.getCacheDir(), "art_" + track.key() + "_" + size + ".jpg");
    }
    private static Bitmap decode(File file, int size) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        options.inSampleSize = 1;
        while (options.outWidth / options.inSampleSize > size * 2
            || options.outHeight / options.inSampleSize > size * 2) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap image = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (image != null && (image.getWidth() > size || image.getHeight() > size)) {
            float scale = Math.min((float)size / image.getWidth(), (float)size / image.getHeight());
            Bitmap small = Bitmap.createScaledBitmap(image,
                Math.max(1, (int)(image.getWidth() * scale)),
                Math.max(1, (int)(image.getHeight() * scale)), true);
            if (small != image) image.recycle();
            image = small;
        }
        return image;
    }
    private byte[] downloadCover(Api.Track track, int requestedSize, String requestedUrl) throws Exception {
        int[] sizes = requestedSize >= 200 ? new int[] { requestedSize, 100, 60 }
            : requestedSize > 60 ? new int[] { requestedSize, 60 }
            : new int[] { requestedSize };
        Exception last = null;
        for (int i = 0; i < sizes.length; i++) {
            String address = sizes[i] == requestedSize ? requestedUrl : track.coverUrl(sizes[i]);
            if (address == null) continue;
            try {
                // Covers requested by the player are larger than list thumbnails. Allow
                // a bigger response and fall back to a smaller server image when needed.
                return Net.bytes(address, null, null, 1024 * 1024);
            } catch (Exception e) {
                last = e;
            }
        }
        if (last != null) throw last;
        throw new java.io.IOException("Для трека нет доступной обложки");
    }

    void load(final Api.Track track, ImageView image, final int size) {
        final String key = track.id + ":" + size;
        image.setTag(key);
        image.setImageResource(android.R.drawable.ic_media_play);
        Bitmap ready = cached(key);
        if (ready != null) { image.setImageBitmap(ready); return; }
        final String url = track.coverUrl(size);
        final File disk = file(track, size);
        if ((!disk.isFile() && url == null) || closed) return;
        final int ticket = generation;
        final WeakReference<ImageView> target = new WeakReference<ImageView>(image);
        worker.execute(new Runnable() { public void run() {
            File part = null;
            try {
                if (closed || ticket != generation) return;
                ImageView current = target.get();
                if (current == null || !key.equals(current.getTag())) return;
                current = null;
                if (!disk.isFile()) {
                    Net.init(context.getAssets().open("roots.pem"));
                    byte[] data = downloadCover(track, size, url);
                    if (closed || ticket != generation) return;
                    part = new File(disk.getAbsolutePath() + ".part");
                    FileOutputStream out = new FileOutputStream(part);
                    try { out.write(data); } finally { out.close(); }
                    if (!part.renameTo(disk)) return;
                }
                if (closed || ticket != generation) return;
                final Bitmap result = decode(disk, size);
                if (result == null) { disk.delete(); return; }
                if (closed || ticket != generation) { result.recycle(); return; }
                remember(key, result);
                main.post(new Runnable() { public void run() {
                    ImageView view = target.get();
                    if (!closed && ticket == generation && view != null && key.equals(view.getTag()))
                        view.setImageBitmap(result);
                }});
            } catch (OutOfMemoryError exhausted) {
                trim();
            } catch (Exception ignored) {
                // An unavailable cover must not stop playback or close the app.
            } finally { if (part != null) part.delete(); }
        }});
    }
}
