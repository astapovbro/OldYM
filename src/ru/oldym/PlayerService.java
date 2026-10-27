package ru.oldym;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class PlayerService extends Service {
    public final class LocalBinder extends Binder { public PlayerService service(){return PlayerService.this;} }
    private final LocalBinder binder=new LocalBinder();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler();
    private MediaPlayer player;
    private ArrayList<Api.Track> queue=new ArrayList<Api.Track>();
    private int index=-1;
    private volatile int generation;
    public volatile String message="Ничего не играет";
    public volatile boolean loading;
    public Runnable changed;
    public Api.Track current(){return index>=0&&index<queue.size()?queue.get(index):null;}
    public IBinder onBind(Intent intent){return binder;}
    public int onStartCommand(Intent intent,int flags,int id){return START_NOT_STICKY;}
    public void notifyChange(){handler.post(new Runnable(){public void run(){if(changed!=null)changed.run();}});}
    private void foreground(String text) {
        Notification n=new Notification(android.R.drawable.ic_media_play,text,System.currentTimeMillis());
        Intent open=new Intent(this,MainActivity.class);open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        n.setLatestEventInfo(this,"OldYM",text,PendingIntent.getActivity(this,0,open,0));
        n.flags|=Notification.FLAG_ONGOING_EVENT;startForeground(7,n);
    }
    public void play(ArrayList<Api.Track> items,int position){
        if(position<0||position>=items.size())return;
        queue=new ArrayList<Api.Track>(items);index=position;load();
    }
    private void release(){if(player!=null){player.release();player=null;}}
    private void load(){
        final Api.Track track=current();if(track==null)return;
        final int ticket=++generation;release();loading=true;message="Подготовка трека…";foreground(track.title);notifyChange();
        worker.execute(new Runnable(){public void run(){
            try {
                File offline=Store.file(PlayerService.this,track);
                File audio=offline;
                if(!offline.isFile()) {
                    File cache=new File(getCacheDir(),"playing.mp3"),part=new File(getCacheDir(),"playing.part");
                    part.delete();
                    String token=getSharedPreferences("account",0).getString("token","");
                    if(token.length()==0)throw new IOException("Сначала войди в аккаунт");
                    Net.init(getAssets().open("roots.pem"));
                    final String address=new Api(token).audio(track);
                    Net.download(address,part,new Net.Progress(){
                        long last;
                        public boolean cancelled(){return generation!=ticket;}
                        public void update(long bytes){if(bytes-last>262144){last=bytes;message="Буферизация: "+(bytes/1024)+" КБ";notifyChange();}}
                    });
                    if(generation!=ticket){part.delete();return;}
                    cache.delete();if(!part.renameTo(cache))throw new IOException("Не удалось сохранить буфер");audio=cache;
                }
                final File local=audio;
                handler.post(new Runnable(){public void run(){
                    if(generation!=ticket)return;
                    try {
                        player=new MediaPlayer();player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        player.setWakeMode(PlayerService.this,PowerManager.PARTIAL_WAKE_LOCK);
                        FileInputStream source=new FileInputStream(local);
                        try{player.setDataSource(source.getFD());}finally{source.close();}
                        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener(){public void onPrepared(MediaPlayer mp){
                            if(generation!=ticket)return;loading=false;message="";mp.start();notifyChange();
                        }});
                        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer mp){next();}});
                        player.setOnErrorListener(new MediaPlayer.OnErrorListener(){public boolean onError(MediaPlayer mp,int a,int b){
                            fail("Не удалось воспроизвести файл ("+a+", "+b+")");return true;
                        }});
                        player.prepareAsync();
                    } catch(Exception e){fail(e.getMessage());}
                }});
            }catch(final Exception e){handler.post(new Runnable(){public void run(){if(generation==ticket)fail(e.getMessage());}});}
        }});
    }
    private void fail(String text){loading=false;message=text==null?"Ошибка воспроизведения":text;release();stopForeground(true);notifyChange();}
    public void next(){if(index+1<queue.size()){index++;load();}else{stop();}}
    public void previous(){if(index>0){index--;load();}else if(player!=null&&!loading)player.seekTo(0);}
    public void toggle(){try{if(player==null)return;if(loading)return;if(player.isPlaying()){player.pause();message="Пауза";}else{player.start();message="";}notifyChange();}catch(IllegalStateException e){fail("Плеер недоступен");}}
    public boolean isPlaying(){try{return player!=null&&player.isPlaying();}catch(Exception e){return false;}}
    public int position(){try{return !loading&&player!=null?player.getCurrentPosition():0;}catch(Exception e){return 0;}}
    public int duration(){try{return !loading&&player!=null?player.getDuration():0;}catch(Exception e){return 0;}}
    public void seek(int ms){try{if(!loading&&player!=null)player.seekTo(ms);}catch(Exception ignored){}}
    public void stop(){generation++;loading=false;release();message="Остановлено";stopForeground(true);notifyChange();}
    public void onCreate(){super.onCreate();registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));}
    private final BroadcastReceiver noisy=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(isPlaying())toggle();}};
    public void onDestroy(){generation++;release();worker.shutdownNow();unregisterReceiver(noisy);super.onDestroy();}
}
