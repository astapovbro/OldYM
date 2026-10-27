package ru.oldym;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.json.*;
import org.w3c.dom.*;

public final class Api {
    public static final String BASE="https://api.music.yandex.net";
    private final String token;
    public Api(String t) { token=t; }
    public static String enc(String s) throws Exception { return URLEncoder.encode(s,"UTF-8"); }
    public Object call(String path,String form) throws Exception {
        JSONObject data=new JSONObject(Net.text(BASE+path,token,form));
        if(data.has("error"))throw new IOException("Сервис вернул ошибку: "+data.getJSONObject("error").optString("message","нет доступа"));
        return data.get("result");
    }
    public JSONObject status() throws Exception {return (JSONObject)call("/account/status",null);}
    public static final class Track {
        public String id,title,artist,cover;
        public int duration;
        public JSONObject json;
        public Track(JSONObject j) throws Exception {
            JSONObject full=j.optJSONObject("track");if(full!=null)j=full;
            json=j;id=j.getString("id");title=j.optString("title","Без названия");duration=j.optInt("durationMs",0);
            JSONArray artists=j.optJSONArray("artists");StringBuilder b=new StringBuilder();
            if(artists!=null)for(int i=0;i<artists.length();i++) {if(i>0)b.append(", ");b.append(artists.getJSONObject(i).optString("name"));}
            artist=b.length()>0?b.toString():"Неизвестный исполнитель";cover=j.optString("coverUri","");
        }
        public String key() {return id.replaceAll("[^A-Za-z0-9_-]","_");}
        public String coverUrl(int size) {
            if(cover.length()==0)return null;
            String address=cover.replace("%%",size+"x"+size);
            if(address.startsWith("//"))return "https:"+address;
            if(address.startsWith("http://"))return "https://"+address.substring(7);
            if(address.startsWith("https://"))return address;
            return "https://"+address;
        }
    }
    public static ArrayList<Track> tracks(JSONArray list) throws Exception {
        ArrayList<Track> out=new ArrayList<Track>();
        if(list==null)return out;
        for(int i=0;i<list.length();i++)out.add(new Track(list.getJSONObject(i)));
        return out;
    }
    public ArrayList<Track> details(List<String> ids) throws Exception {
        ArrayList<Track> result=new ArrayList<Track>();
        for(int start=0;start<ids.size();start+=50) {
            StringBuilder joined=new StringBuilder();for(int i=start;i<Math.min(start+50,ids.size());i++){if(joined.length()>0)joined.append(',');joined.append(ids.get(i));}
            result.addAll(tracks((JSONArray)call("/tracks","track-ids="+enc(joined.toString()))));
        }
        return result;
    }
    public ArrayList<String> likedIds(String uid) throws Exception {
        JSONObject result=(JSONObject)call("/users/"+enc(uid)+"/likes/tracks",null);
        JSONArray list=result.getJSONObject("library").getJSONArray("tracks");
        ArrayList<String> ids=new ArrayList<String>();
        for(int i=0;i<list.length();i++)ids.add(list.getJSONObject(i).getString("id"));
        return ids;
    }
    public ArrayList<Track> likes(String uid) throws Exception {return details(likedIds(uid));}
    /** Change a track's "Liked" state on the current user's account. */
    public void setTrackLiked(String uid,String trackId,boolean liked) throws Exception {
        String action=liked?"add-multiple":"remove";
        Object result=call("/users/"+enc(uid)+"/likes/tracks/"+action,"track-ids="+enc(trackId));
        if(result==null||Boolean.FALSE.equals(result))throw new IOException("Сервис не подтвердил изменение избранного");
    }
    public JSONArray playlists(String uid) throws Exception {return (JSONArray)call("/users/"+enc(uid)+"/playlists/list",null);}
    public ArrayList<Track> playlist(String uid,String kind) throws Exception {
        JSONObject result=(JSONObject)call("/users/"+enc(uid)+"/playlists/"+enc(kind),null);
        JSONArray list=result.optJSONArray("tracks");ArrayList<Track> out=new ArrayList<Track>();
        if(list==null)throw new IOException("Сервис не прислал состав плейлиста");
        ArrayList<String> unresolved=new ArrayList<String>();
        for(int i=0;i<list.length();i++){JSONObject j=list.getJSONObject(i);if(j.has("track"))out.add(new Track(j));else unresolved.add(j.getString("id"));}
        if(!unresolved.isEmpty())return details(idsFrom(list));
        return out;
    }
    private static ArrayList<String> idsFrom(JSONArray a)throws Exception {
        ArrayList<String> ids=new ArrayList<String>();for(int i=0;i<a.length();i++)ids.add(a.getJSONObject(i).getString("id"));return ids;
    }
    /** Personalized Yandex Music radio ("My Wave"). The legacy rotor endpoint returns one small batch. */
    public ArrayList<Track> myWave() throws Exception {
        JSONObject result=(JSONObject)call("/rotor/station/user:onyourwave/tracks?settings2=true",null);
        return parseWave(result);
    }
    /** Load the next batch after the last track already shown. */
    public ArrayList<Track> myWave(String queueTrackId) throws Exception {
        if(queueTrackId==null||queueTrackId.length()==0)return myWave();
        JSONObject result=(JSONObject)call("/rotor/station/user:onyourwave/tracks?settings2=true&queue="+enc(queueTrackId),null);
        return parseWave(result);
    }
    private ArrayList<Track> parseWave(JSONObject result) throws Exception {
        JSONArray sequence=result.optJSONArray("sequence");
        if(sequence==null)sequence=result.optJSONArray("tracks");
        if(sequence==null)throw new IOException("Сервис не вернул очередь «Моей волны»");
        ArrayList<Track> out=new ArrayList<Track>();
        for(int i=0;i<sequence.length();i++) {
            JSONObject item=sequence.optJSONObject(i);
            if(item==null)continue;
            JSONObject track=item.optJSONObject("track");
            if(track==null && item.has("id"))track=item;
            if(track!=null)out.add(new Track(track));
        }
        if(out.isEmpty())throw new IOException("«Моя волна» не вернула доступных треков");
        return out;
    }
    public ArrayList<Track> search(String query,int page) throws Exception {
        JSONObject result=(JSONObject)call("/search?type=track&page="+page+"&text="+enc(query),null);
        JSONObject tracks=result.optJSONObject("tracks");return tracks(tracks==null?null:tracks.optJSONArray("results"));
    }
    private static String tag(Document d,String name) throws Exception {
        NodeList nodes=d.getElementsByTagName(name);if(nodes.getLength()==0)throw new IOException("Нет поля "+name);
        Node child=nodes.item(0).getFirstChild();if(child==null)throw new IOException("Пустое поле "+name);return child.getNodeValue();
    }
    public String audio(Track track) throws Exception {
        JSONArray options=(JSONArray)call("/tracks/"+enc(track.id)+"/download-info",null);
        JSONObject best=null;int bitrate=0;
        for(int i=0;i<options.length();i++) {
            JSONObject o=options.getJSONObject(i);
            if("mp3".equals(o.optString("codec")) && !o.optBoolean("preview",false)) {
                int n=o.optInt("bitrateInKbps",128);
                if(best==null || (n<=128 && (bitrate>128||n>bitrate))) {best=o;bitrate=n;}
            }
        }
        if(best==null)throw new IOException("Полный MP3 недоступен этому аккаунту или треку");
        String address=best.getString("downloadInfoUrl");if(address.startsWith("http://"))address="https://"+address.substring(7);
        if(best.optBoolean("direct",false))return address;
        String xml=Net.text(address,null,null);
        if(xml.indexOf("<!DOCTYPE")>=0 || xml.indexOf("<!ENTITY")>=0)throw new IOException("Небезопасный XML");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setExpandEntityReferences(false);
        Document doc=f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
        String host=tag(doc,"host"),path=tag(doc,"path"),ts=tag(doc,"ts"),s=tag(doc,"s");
        if(!host.matches("[A-Za-z0-9.-]+")||!path.startsWith("/"))throw new IOException("Неверный адрес аудио");
        byte[] hash=MessageDigest.getInstance("MD5").digest(("XGRlBW9FXlekgbPrRHuSiA"+path.substring(1)+s).getBytes("UTF-8"));
        StringBuilder sign=new StringBuilder();for(byte b:hash)sign.append(String.format(Locale.US,"%02x",b&255));
        return "https://"+host+"/get-mp3/"+sign+"/"+ts+path;
    }
}
