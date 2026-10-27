package ru.oldym;

import android.content.Context;
import android.os.Environment;
import java.io.*;
import java.util.*;
import org.json.*;

public final class Store {
    private Store() {}
    public static File directory(Context c) throws IOException {
        File root;
        if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
            root=new File(Environment.getExternalStorageDirectory(),"Android/data/ru.oldym/files/music");
        else root=new File(c.getFilesDir(),"music");
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Не удалось создать папку музыки");
        return root;
    }
    public static File file(Context c,Api.Track t)throws IOException {return new File(directory(c),t.key()+".mp3");}
    public static File cover(Context c,Api.Track t){return new File(c.getCacheDir(),"cover_"+t.key()+".jpg");}
    public static synchronized void save(Context c,Api.Track t)throws Exception {
        ArrayList<Api.Track> tracks=all(c);boolean found=false;
        for(Api.Track other:tracks)if(other.id.equals(t.id))found=true;
        if(!found)tracks.add(t);
        JSONArray a=new JSONArray();for(Api.Track item:tracks)a.put(item.json);
        c.getSharedPreferences("offline",0).edit().putString("tracks",a.toString()).commit();
    }
    public static synchronized ArrayList<Api.Track> all(Context c)throws Exception {
        JSONArray a=new JSONArray(c.getSharedPreferences("offline",0).getString("tracks","[]"));
        ArrayList<Api.Track> out=new ArrayList<Api.Track>();
        for(int i=0;i<a.length();i++){Api.Track t=new Api.Track(a.getJSONObject(i));if(file(c,t).isFile())out.add(t);}
        return out;
    }
    public static synchronized void delete(Context c,Api.Track t)throws Exception {
        File f=file(c,t);if(f.exists()&&!f.delete())throw new IOException("Не удалось удалить трек");
        ArrayList<Api.Track> tracks=all(c);JSONArray a=new JSONArray();
        for(Api.Track other:tracks)if(!other.id.equals(t.id))a.put(other.json);
        c.getSharedPreferences("offline",0).edit().putString("tracks",a.toString()).commit();
    }
}
