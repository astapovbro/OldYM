package ru.oldym;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import android.widget.RemoteViews;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class PlayerService extends Service {
    public final class LocalBinder extends Binder {
        public PlayerService service(){return PlayerService.this;}
    }

    private static final int NOTIFICATION_ID = 7;
    private final LocalBinder binder = new LocalBinder();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService waveWorker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler();
    private MediaPlayer player;
    private ArrayList<Api.Track> queue = new ArrayList<Api.Track>();
    private int index = -1;
    private volatile int generation;

    // My Wave queue state from the original service. Keep this intact: MainActivity
    // uses playWave(items, position, cursorTrackId) to continue the radio queue.
    private boolean waveQueue, waveLoading, waveFinished, waitingForWave;
    private int waveSession;
    private String waveCursorId = "";
    private final HashSet<String> waveTrackIds = new HashSet<String>();

    private SystemMediaControls mediaControls;
    private boolean foregroundActive;
    private boolean mediaStopped = true;

    public volatile String message = "Ничего не играет";
    public volatile boolean loading;
    public Runnable changed;

    public Api.Track current(){
        return index >= 0 && index < queue.size() ? queue.get(index) : null;
    }

    public IBinder onBind(Intent intent){return binder;}

    public int onStartCommand(Intent intent,int flags,int id){
        if(intent != null){
            String action = intent.getAction();
            if(SystemMediaControls.ACTION_TOGGLE.equals(action)) toggle();
            else if(SystemMediaControls.ACTION_NEXT.equals(action)) next();
            else if(SystemMediaControls.ACTION_PREVIOUS.equals(action)) previous();
        }
        return START_NOT_STICKY;
    }

    public void notifyChange(){
        handler.post(new Runnable(){
            public void run(){
                Api.Track track = current();
                if(mediaControls != null){
                    if(mediaStopped) mediaControls.update(null, false, false);
                    else mediaControls.update(track, isPlaying(), loading || waitingForWave);
                }
                if(foregroundActive) updateNotification(track);
                if(changed != null) changed.run();
            }
        });
    }

    private PendingIntent serviceAction(String action,int requestCode){
        Intent command = new Intent(this, PlayerService.class);
        command.setAction(action);
        return PendingIntent.getService(
            this, requestCode, command, PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    private void foreground(Api.Track track){
        foregroundActive = true;
        updateNotification(track);
    }

    private void updateNotification(Api.Track track){
        if(!foregroundActive) return;

        String title = track == null ? "OldYM" : track.title;
        String artist = track == null ? "" : track.artist;
        Notification notification = new Notification(
            android.R.drawable.ic_media_play, title, System.currentTimeMillis()
        );
        RemoteViews view = new RemoteViews(
            getPackageName(), R.layout.player_notification
        );
        view.setImageViewResource(R.id.player_notification_icon, R.drawable.icon);
        view.setTextViewText(R.id.player_notification_title, title);
        view.setTextViewText(R.id.player_notification_artist, artist);
        view.setTextViewText(R.id.player_notification_previous, "|<");
        view.setTextViewText(
            R.id.player_notification_toggle, isPlaying() ? "||" : ">"
        );
        view.setTextViewText(R.id.player_notification_next, ">|");
        view.setOnClickPendingIntent(
            R.id.player_notification_previous,
            serviceAction(SystemMediaControls.ACTION_PREVIOUS, 4102)
        );
        view.setOnClickPendingIntent(
            R.id.player_notification_toggle,
            serviceAction(SystemMediaControls.ACTION_TOGGLE, 4103)
        );
        view.setOnClickPendingIntent(
            R.id.player_notification_next,
            serviceAction(SystemMediaControls.ACTION_NEXT, 4104)
        );

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        notification.contentIntent = PendingIntent.getActivity(
            this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT
        );
        notification.contentView = view;
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        startForeground(NOTIFICATION_ID, notification);
    }

    public void play(ArrayList<Api.Track> items,int position){
        startQueue(items, position, false, null);
    }

    public void playWave(ArrayList<Api.Track> items,int position,String cursorTrackId){
        startQueue(items, position, true, cursorTrackId);
    }

    private void startQueue(ArrayList<Api.Track> items,int position,
                            boolean fromWave,String cursorTrackId){
        if(items == null || position < 0 || position >= items.size()) return;
        waveSession++;
        waveQueue = fromWave;
        waveLoading = false;
        waveFinished = false;
        waitingForWave = false;
        waveTrackIds.clear();
        queue = new ArrayList<Api.Track>(items);
        for(int i = 0; i < queue.size(); i++){
            Api.Track t = queue.get(i);
            if(t != null && t.id != null) waveTrackIds.add(t.id);
        }
        waveCursorId = fromWave
            ? (cursorTrackId == null || cursorTrackId.length() == 0
                ? queue.get(queue.size() - 1).id : cursorTrackId)
            : "";
        index = position;
        load();
    }

    private void release(){
        if(player != null){
            player.release();
            player = null;
        }
    }

    private void load(){
        final Api.Track track = current();
        if(track == null) return;

        final int ticket = ++generation;
        release();
        loading = true;
        mediaStopped = false;
        message = "Подготовка трека…";
        foreground(track);
        notifyChange();

        worker.execute(new Runnable(){
            public void run(){
                try{
                    File offline = Store.file(PlayerService.this, track);
                    File audio = offline;
                    if(!offline.isFile()){
                        File cache = new File(getCacheDir(), "playing.mp3");
                        File part = new File(getCacheDir(), "playing.part");
                        part.delete();
                        String token = getSharedPreferences("account",0)
                            .getString("token", "");
                        if(token.length() == 0)
                            throw new IOException("Сначала войди в аккаунт");
                        Net.init(getAssets().open("roots.pem"));
                        final String address = new Api(token).audio(track);
                        Net.download(address, part, new Net.Progress(){
                            long last;
                            public boolean cancelled(){return generation != ticket;}
                            public void update(long bytes){
                                if(bytes - last > 262144){
                                    last = bytes;
                                    message = "Буферизация: " + (bytes / 1024) + " КБ";
                                    notifyChange();
                                }
                            }
                        });
                        if(generation != ticket){part.delete();return;}
                        cache.delete();
                        if(!part.renameTo(cache))
                            throw new IOException("Не удалось сохранить буфер");
                        audio = cache;
                    }

                    final File local = audio;
                    handler.post(new Runnable(){
                        public void run(){
                            if(generation != ticket) return;
                            try{
                                player = new MediaPlayer();
                                player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                                player.setWakeMode(
                                    PlayerService.this,
                                    PowerManager.PARTIAL_WAKE_LOCK
                                );
                                FileInputStream source = new FileInputStream(local);
                                try{player.setDataSource(source.getFD());}
                                finally{source.close();}

                                player.setOnPreparedListener(
                                    new MediaPlayer.OnPreparedListener(){
                                        public void onPrepared(MediaPlayer mp){
                                            if(generation != ticket) return;
                                            loading = false;
                                            message = "";
                                            mp.start();
                                            notifyChange();
                                        }
                                    }
                                );
                                player.setOnCompletionListener(
                                    new MediaPlayer.OnCompletionListener(){
                                        public void onCompletion(MediaPlayer mp){next();}
                                    }
                                );
                                player.setOnErrorListener(
                                    new MediaPlayer.OnErrorListener(){
                                        public boolean onError(MediaPlayer mp,int a,int b){
                                            fail("Не удалось воспроизвести файл ("+a+", "+b+")");
                                            return true;
                                        }
                                    }
                                );
                                player.prepareAsync();
                            }catch(Exception e){fail(e.getMessage());}
                        }
                    });
                }catch(final Exception e){
                    handler.post(new Runnable(){
                        public void run(){
                            if(generation == ticket) fail(e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void fail(String text){
        loading = false;
        mediaStopped = true;
        message = text == null ? "Ошибка воспроизведения" : text;
        release();
        foregroundActive = false;
        stopForeground(true);
        if(mediaControls != null) mediaControls.update(null, false, false);
        notifyChange();
    }

    public void next(){
        if(index + 1 < queue.size()){
            index++;
            load();
            maybeLoadMoreWave();
        }else if(waveQueue && !waveFinished){
            waitingForWave = true;
            message = "Загружаю продолжение «Моей волны»…";
            notifyChange();
            requestMoreWave();
        }else{
            stop();
        }
    }

    public void previous(){
        if(index > 0){
            index--;
            load();
        }else if(player != null && !loading){
            player.seekTo(0);
        }
    }

    private void maybeLoadMoreWave(){
        if(waveQueue && !waveFinished && !waveLoading && index >= 0
            && queue.size() - index <= 3) requestMoreWave();
    }

    private void requestMoreWave(){
        if(!waveQueue || waveLoading || waveFinished) return;
        if(waveCursorId == null || waveCursorId.length() == 0){
            waveFinished = true;
            return;
        }
        waveLoading = true;
        final int session = waveSession;
        final String cursor = waveCursorId;
        final String token = getSharedPreferences("account",0)
            .getString("token", "");

        waveWorker.execute(new Runnable(){
            public void run(){
                ArrayList<Api.Track> received = null;
                Exception failure = null;
                try{
                    if(token.length() == 0)
                        throw new IOException("Сначала войди в аккаунт");
                    Net.init(getAssets().open("roots.pem"));
                    received = new Api(token).myWave(cursor);
                }catch(Exception e){failure = e;}

                final ArrayList<Api.Track> tracks = received;
                final Exception problem = failure;
                handler.post(new Runnable(){
                    public void run(){
                        if(session != waveSession || !waveQueue) return;
                        waveLoading = false;
                        if(problem != null){
                            message = "Не удалось загрузить продолжение «Моей волны»";
                            waitingForWave = false;
                            notifyChange();
                            return;
                        }
                        if(tracks == null || tracks.isEmpty()){
                            waveFinished = true;
                            finishWaveIfWaiting("В «Моей волне» пока нет новых треков");
                            return;
                        }
                        int added = 0;
                        String lastId = waveCursorId;
                        for(int i = 0; i < tracks.size(); i++){
                            Api.Track track = tracks.get(i);
                            if(track == null || track.id == null || track.id.length() == 0)
                                continue;
                            lastId = track.id;
                            if(waveTrackIds.add(track.id)){
                                queue.add(track);
                                added++;
                            }
                        }
                        waveCursorId = lastId;
                        if(added == 0){
                            waveFinished = true;
                            finishWaveIfWaiting("Новых треков в «Моей волне» нет");
                            return;
                        }
                        if(waitingForWave && index + 1 < queue.size()){
                            waitingForWave = false;
                            index++;
                            load();
                        }else{
                            notifyChange();
                            maybeLoadMoreWave();
                        }
                    }
                });
            }
        });
    }

    private void finishWaveIfWaiting(String text){
        if(waitingForWave){
            waitingForWave = false;
            loading = false;
            mediaStopped = true;
            message = text;
            release();
            foregroundActive = false;
            stopForeground(true);
            if(mediaControls != null) mediaControls.update(null, false, false);
            notifyChange();
        }else{
            notifyChange();
        }
    }

    public void toggle(){
        try{
            if(player == null || loading) return;
            if(player.isPlaying()){
                player.pause();
                message = "Пауза";
            }else{
                player.start();
                message = "";
            }
            notifyChange();
        }catch(IllegalStateException e){
            fail("Плеер недоступен");
        }
    }

    public boolean isPlaying(){
        try{return player != null && player.isPlaying();}
        catch(Exception e){return false;}
    }

    public int position(){
        try{return !loading && player != null ? player.getCurrentPosition() : 0;}
        catch(Exception e){return 0;}
    }

    public int duration(){
        try{return !loading && player != null ? player.getDuration() : 0;}
        catch(Exception e){return 0;}
    }

    public void seek(int ms){
        try{if(!loading && player != null) player.seekTo(ms);}
        catch(Exception ignored){}
    }

    public void stop(){
        generation++;
        waveSession++;
        waveQueue = false;
        waveLoading = false;
        waveFinished = true;
        waitingForWave = false;
        loading = false;
        mediaStopped = true;
        release();
        message = "Остановлено";
        foregroundActive = false;
        stopForeground(true);
        if(mediaControls != null) mediaControls.update(null, false, false);
        notifyChange();
    }

    public void onCreate(){
        super.onCreate();
        mediaControls = new SystemMediaControls(this);
        mediaControls.start();
        registerReceiver(
            noisy,
            new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        );
    }

    private final BroadcastReceiver noisy = new BroadcastReceiver(){
        public void onReceive(Context c,Intent i){
            if(isPlaying()) toggle();
        }
    };

    public void onDestroy(){
        generation++;
        waveSession++;
        release();
        worker.shutdownNow();
        waveWorker.shutdownNow();
        if(mediaControls != null){
            mediaControls.stop();
            mediaControls = null;
        }
        unregisterReceiver(noisy);
        super.onDestroy();
    }
}
