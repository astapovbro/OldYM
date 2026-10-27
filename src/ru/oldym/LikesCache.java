package ru.oldym;

import android.content.Context;
import java.io.*;
import java.util.ArrayList;
import org.json.*;

/** Account-scoped, compact disk cache: track IDs and already fetched pages. */
final class LikesCache {
    static final class State {
        String uid;
        long savedAt;
        int next;
        ArrayList<String> ids = new ArrayList<String>();
        ArrayList<Api.Track> tracks = new ArrayList<Api.Track>();
        State copy() {
            State s = new State(); s.uid = uid; s.savedAt = savedAt; s.next = next;
            s.ids = new ArrayList<String>(ids); s.tracks = new ArrayList<Api.Track>(tracks);
            return s;
        }
    }
    private static File file(Context c, String uid) {
        return new File(c.getCacheDir(), "likes_" + uid.replaceAll("[^A-Za-z0-9_-]", "_") + ".json");
    }
    static State read(Context c, String uid) {
        try {
            File f = file(c, uid);
            if (!f.isFile() || f.length() > 2 * 1024 * 1024) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InputStream in = new FileInputStream(f);
            try { byte[] buffer = new byte[4096]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n); }
            finally { in.close(); }
            JSONObject data = new JSONObject(new String(out.toByteArray(), "UTF-8"));
            if (!uid.equals(data.optString("uid"))) return null;
            State state = new State(); state.uid = uid;
            state.savedAt = data.getLong("savedAt"); state.next = data.getInt("next");
            JSONArray ids = data.getJSONArray("ids");
            for (int i = 0; i < ids.length(); i++) state.ids.add(ids.getString(i));
            state.tracks = Api.tracks(data.getJSONArray("tracks"));
            if (state.next < 0 || state.next > ids.length()) return null;
            return state;
        } catch (Exception ignored) { return null; }
    }
    static synchronized void invalidate(Context c, String uid) {
        try { File f=file(c,uid); if(f.isFile())f.delete(); } catch(Exception ignored) {}
    }
    static synchronized void write(Context c, State state) throws Exception {
        JSONObject data = new JSONObject();
        data.put("uid", state.uid); data.put("savedAt", System.currentTimeMillis());
        data.put("next", state.next);
        JSONArray ids = new JSONArray(); for (String id : state.ids) ids.put(id); data.put("ids", ids);
        JSONArray tracks = new JSONArray(); for (Api.Track t : state.tracks) tracks.put(t.json); data.put("tracks", tracks);
        File target = file(c, state.uid), part = new File(target.getAbsolutePath() + ".part");
        FileOutputStream out = new FileOutputStream(part);
        try { out.write(data.toString().getBytes("UTF-8")); } finally { out.close(); }
        if (!part.renameTo(target)) { part.delete(); throw new IOException("Не удалось сохранить список"); }
    }
}
