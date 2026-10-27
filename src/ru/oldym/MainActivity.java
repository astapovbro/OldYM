package ru.oldym;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class MainActivity extends Activity {
    private int INK, MUTED, PAPER, YELLOW;
    private final Handler handler=new Handler();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService downloads=Executors.newSingleThreadExecutor();
    private CoverLoader covers;
    private TrackAdapter trackAdapter;
    private ListView trackList;
    private LikesCache.State likesState;
    private boolean likesBusy;
    private Button moreLikes,moreSearch,moreWave,nowLike;
    private boolean searchBusy,waveBusy;
    private final HashSet<String> likedTrackIds=new HashSet<String>();
    private boolean likedIdsLoaded,likeStateKnown,likeBusy;
    private String likeTrackId="",likeBusyTrackId="";
    private LinearLayout root,body,mini;
    private TextView heading,subheading,miniTitle,miniDetail,nowStatus,elapsed,total;
    private Button miniPlay,nowPlay;
    private SeekBar seek;
    private ImageView nowCover;
    private String screen="home",uid="",login="",listTitle="",query="";
    private ArrayList<Api.Track> visible=new ArrayList<Api.Track>();
    private int screenVersion,searchPage;
    private PlayerService service;
    private boolean bound,destroyed,dragging;
    private int authSession;
    private AlertDialog deviceDialog;
    private final HashSet<String> downloading=new HashSet<String>();
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){service=((PlayerService.LocalBinder)binder).service();bound=true;service.changed=changed;updatePlayer();}
        public void onServiceDisconnected(ComponentName name){bound=false;service=null;}
    };
    private final Runnable changed=new Runnable(){public void run(){updatePlayer();}};
    private final Runnable ticker=new Runnable(){public void run(){if(!destroyed){updatePlayer();handler.postDelayed(this,700);}}};
    public void onCreate(Bundle state){
        super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);
        CrashLog.install(getApplicationContext());
        covers=new CoverLoader(this);
        INK=getResources().getColor(R.color.ink);
        MUTED=getResources().getColor(R.color.muted);
        PAPER=getResources().getColor(R.color.paper);
        YELLOW=getResources().getColor(R.color.accent);
        getWindow().setBackgroundDrawable(new ColorDrawable(PAPER));
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        uid=prefs().getString("uid","");login=prefs().getString("login","");
        Intent intent=new Intent(this,PlayerService.class);startService(intent);bindService(intent,connection,Context.BIND_AUTO_CREATE);
        if(uid.length()>0&&prefs().getString("token","").length()>0){home();}else{welcome();}
        handler.post(ticker);
        final String crash=CrashLog.consume(this);
        if(crash!=null)handler.post(new Runnable(){public void run(){if(!destroyed)new AlertDialog.Builder(MainActivity.this).setTitle(getString(R.string.crash_title)).setMessage(crash).setPositiveButton(getString(R.string.crash_copy),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){((android.text.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setText(crash);}}).setNegativeButton(getString(R.string.ui_006),null).show();}});
    }
    private android.content.SharedPreferences prefs(){return getSharedPreferences("account",0);}
    private Api api(){return new Api(prefs().getString("token",""));}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private void bold(TextView t){t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);}
    private GradientDrawable fill(int top,int bottom){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{top,bottom});return g;}
    private StateListDrawable press(int normal,int active){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_pressed},new ColorDrawable(active));d.addState(new int[0],new ColorDrawable(normal));return d;}
    private Button button(String title,final Runnable action){Button b=new Button(this);b.setText(title);b.setTextSize(16);b.setTextColor(INK);b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){action.run();}});return b;}
    private void divider(LinearLayout parent){View v=new View(this);v.setBackgroundColor(0xffd2d2d2);parent.addView(v,new LinearLayout.LayoutParams(-1,dp(1)));}
    private void shell(String title,String subtitle,String active){
        screenVersion++;nowPlay=null;nowCover=null;nowStatus=null;seek=null;nowLike=null;likeTrackId="";likeStateKnown=false;
        if(covers!=null)covers.cancelPending();
        trackAdapter=null;trackList=null;moreLikes=null;moreSearch=null;moreWave=null;
        root=column();root.setBackgroundColor(PAPER);setContentView(root);
        LinearLayout bar=row();bar.setPadding(dp(10),0,dp(7),0);bar.setBackgroundDrawable(fill(0xff282828,0xff171717));
        View back;
        if("home".equals(active)){
            ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.logo);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);back=logo;
        }else{
            TextView arrow=text("‹",32,YELLOW);arrow.setGravity(Gravity.CENTER);back=arrow;
        }
        bar.addView(back,new LinearLayout.LayoutParams(dp(36),-1));
        back.setOnClickListener(new View.OnClickListener(){public void onClick(View v){home();}});
        LinearLayout labels=column();labels.setGravity(Gravity.CENTER_VERTICAL);
        heading=text(title,20,0xfff5f5f5);subheading=text(subtitle,12,0xffbdbdbd);labels.addView(heading);labels.addView(subheading);
        if(subtitle.length()==0)subheading.setVisibility(View.GONE);
        bar.addView(labels,new LinearLayout.LayoutParams(0,-1,1));
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(55)));
        if(!"player".equals(active)){
            LinearLayout tabs=row();String[] names={getString(R.string.ui_001),getString(R.string.ui_002),getString(R.string.ui_003)};
            for(int i=0;i<3;i++){
                final int tab=i;boolean selected=(i==0&&"home".equals(active))||(i==1&&"search".equals(active))||(i==2&&"settings".equals(active));
                TextView t=text(names[i],13,selected?INK:0xffdddddd);t.setGravity(Gravity.CENTER);t.setBackgroundDrawable(selected?new ColorDrawable(YELLOW):fill(0xff454545,0xff303030));
                tabs.addView(t,new LinearLayout.LayoutParams(0,dp(42),1));
                t.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(tab==0)home();else if(tab==1)search();else settings();}});
            }
            root.addView(tabs);
        }
        body=column();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        mini=row();mini.setPadding(dp(12),dp(5),dp(5),dp(5));mini.setBackgroundColor(0xff252525);
        LinearLayout info=column();miniTitle=text("",14,0xffeeeeee);bold(miniTitle);miniTitle.setSingleLine(true);miniDetail=text("",11,0xffbbbbbb);miniDetail.setSingleLine(true);info.addView(miniTitle);info.addView(miniDetail);
        mini.addView(info,new LinearLayout.LayoutParams(0,-2,1));info.setOnClickListener(new View.OnClickListener(){public void onClick(View v){player();}});
        miniPlay=button("▶",new Runnable(){public void run(){if(service!=null)service.toggle();}});mini.addView(miniPlay,new LinearLayout.LayoutParams(dp(54),dp(44)));
        root.addView(mini);updatePlayer();
    }
    private TextView note(String value){TextView t=text(value,14,MUTED);t.setPadding(dp(16),dp(14),dp(16),dp(14));t.setLineSpacing(dp(3),1);body.addView(t);return t;}
    private void item(LinearLayout parent,String icon,String title,String detail,final Runnable action){
        LinearLayout r=row();r.setPadding(dp(14),dp(13),dp(12),dp(13));r.setMinimumHeight(dp(72));r.setBackgroundDrawable(press(PAPER,0xffffec80));
        TextView symbol=text(icon,27,0xff555555);symbol.setGravity(Gravity.CENTER);r.addView(symbol,new LinearLayout.LayoutParams(dp(36),dp(40)));
        LinearLayout labels=column();labels.setPadding(dp(12),0,0,0);TextView t=text(title,19,INK);labels.addView(t);
        if(detail.length()>0)labels.addView(text(detail,13,MUTED));r.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        r.addView(text("›",30,MUTED),new LinearLayout.LayoutParams(dp(18),-2));parent.addView(r);divider(parent);
        r.setOnClickListener(new View.OnClickListener(){public void onClick(View v){action.run();}});
    }
    private LinearLayout scroll(){ScrollView s=new ScrollView(this);LinearLayout list=column();s.addView(list);body.addView(s,new LinearLayout.LayoutParams(-1,-1));return list;}
    private boolean logged(){if(uid.length()>0&&prefs().getString("token","").length()>0)return true;loginDialog();return false;}
    private interface Task {Object run() throws Exception;}
    private interface Done {void run(Object value) throws Exception;}
    private void task(final Task job,final Done done){
        final int version=screenVersion;final ProgressDialog dialog=new ProgressDialog(this);dialog.setMessage(getString(R.string.ui_004));dialog.setCancelable(false);dialog.show();
        worker.execute(new Runnable(){public void run(){
            Object result=null;Exception failure=null;
            try{Net.init(getAssets().open("roots.pem"));result=job.run();}catch(Exception e){failure=e;}
            final Object value=result;final Exception error=failure;
            handler.post(new Runnable(){public void run(){
                if(!destroyed)dialog.dismiss();if(destroyed||version!=screenVersion)return;
                if(error!=null){error(error);return;}try{done.run(value);}catch(Exception e){error(e);}
            }});
        }});
    }
    private void error(Exception e){if(destroyed)return;new AlertDialog.Builder(this).setTitle(getString(R.string.ui_005)).setMessage(e.getMessage()==null?e.toString():e.getMessage()).setPositiveButton(getString(R.string.ui_006),null).show();}
    private void toast(String t){Toast.makeText(this,t,Toast.LENGTH_LONG).show();}
    private void welcome(){
        screen="welcome";screenVersion++;if(covers!=null)covers.cancelPending();
        trackAdapter=null;trackList=null;moreLikes=null;moreSearch=null;moreWave=null;nowPlay=null;nowCover=null;nowStatus=null;seek=null;
        root=column();root.setBackgroundColor(PAPER);setContentView(root);
        LinearLayout bar=row();bar.setPadding(dp(10),0,dp(7),0);bar.setBackgroundDrawable(fill(0xff282828,0xff171717));
        ImageView logo = new ImageView(this);logo.setImageResource(R.drawable.logo);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);bar.addView(logo,new LinearLayout.LayoutParams(dp(46),-1));
        LinearLayout labels=column();labels.setGravity(Gravity.CENTER_VERTICAL);
        heading=text(getString(R.string.ui_083),20,0xfff5f5f5);subheading=text(getString(R.string.ui_084),12,0xffbdbdbd);
        labels.addView(heading);labels.addView(subheading);
        bar.addView(labels,new LinearLayout.LayoutParams(0,-1,1));
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(55)));
        body=column();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout list=scroll();
        TextView hero=text(getString(R.string.ui_085),19,INK);bold(hero);hero.setPadding(dp(16),dp(18),dp(16),dp(6));list.addView(hero);
        TextView why=text(getString(R.string.ui_086),14,MUTED);why.setPadding(dp(16),0,dp(16),dp(16));why.setLineSpacing(dp(3),1);list.addView(why);
        Button code=button(getString(R.string.ui_087),new Runnable(){public void run(){deviceLogin();}});
        code.setTextSize(16);list.addView(code,new LinearLayout.LayoutParams(-1,dp(48)));
        Button manual=button(getString(R.string.ui_088),new Runnable(){public void run(){tokenDialog();}});
        manual.setTextSize(16);list.addView(manual,new LinearLayout.LayoutParams(-1,dp(44)));
        Button later=button(getString(R.string.ui_089),new Runnable(){public void run(){home();}});
        later.setTextSize(14);list.addView(later,new LinearLayout.LayoutParams(-1,dp(40)));
        TextView foot=text(getString(R.string.ui_090),12,MUTED);foot.setPadding(dp(16),dp(16),dp(16),dp(24));foot.setLineSpacing(dp(3),1);list.addView(foot);
    }
    private void home(){
        screen="home";shell(getString(R.string.ui_007),getString(R.string.ui_008),"home");LinearLayout list=scroll();
        item(list,"♫",getString(R.string.wave_title),getString(R.string.wave_detail),new Runnable(){public void run(){loadMyWave();}});
        item(list,"♥",getString(R.string.ui_009),getString(R.string.ui_010),new Runnable(){public void run(){loadLikes();}});
        item(list,"=",getString(R.string.ui_011),getString(R.string.ui_012),new Runnable(){public void run(){loadPlaylists();}});
        int count=0;try{count=Store.all(this).size();}catch(Exception ignored){}
        item(list,"↓",getString(R.string.ui_013),count+getString(R.string.ui_014),new Runnable(){public void run(){offline();}});
        item(list,"▶",getString(R.string.ui_015),getString(R.string.ui_016),new Runnable(){public void run(){player();}});
        item(list,"•",uid.length()==0?getString(R.string.ui_017):login,uid.length()==0?getString(R.string.ui_018):getString(R.string.ui_019),new Runnable(){public void run(){settings();}});
        TextView foot=text(getString(R.string.ui_020),12,MUTED);foot.setPadding(dp(18),dp(20),dp(18),dp(12));list.addView(foot);
    }
    private void loadMyWave(){
        if(!logged())return;
        final Api client=api();
        task(new Task(){public Object run()throws Exception{return client.myWave();}},new Done(){
            @SuppressWarnings("unchecked") public void run(Object value){
                ArrayList<Api.Track> tracks=(ArrayList<Api.Track>)value;
                showTracks(getString(R.string.wave_title),tracks,false);
                if(subheading!=null)subheading.setText(getString(R.string.wave_loaded));
            }
        });
    }
    private void loadMoreWave(){
        if(waveBusy||!"tracks".equals(screen)||!getString(R.string.wave_title).equals(listTitle)||visible.isEmpty())return;
        if(moreWave!=null&&!moreWave.isEnabled())return;
        if(trackAdapter==null||trackList==null)return;
        waveBusy=true;
        if(moreWave!=null){moreWave.setEnabled(false);moreWave.setText(getString(R.string.ui_004));}
        final int version=screenVersion;
        final String token=prefs().getString("token","");
        final String lastTrackId=visible.get(visible.size()-1).id;
        worker.execute(new Runnable(){public void run(){
            ArrayList<Api.Track> received=null;Exception failure=null;
            try{Net.init(getAssets().open("roots.pem"));received=new Api(token).myWave(lastTrackId);}catch(Exception e){failure=e;}
            final ArrayList<Api.Track> tracks=received;final Exception problem=failure;
            handler.post(new Runnable(){public void run(){
                if(destroyed||version!=screenVersion)return;
                waveBusy=false;
                if(problem!=null){updateWaveFooter();error(problem);return;}
                if(tracks==null||tracks.isEmpty()){if(moreWave!=null){moreWave.setText("Больше треков нет");moreWave.setEnabled(false);}return;}
                HashSet<String> seen=new HashSet<String>();
                for(int i=0;i<visible.size();i++)seen.add(visible.get(i).id);
                int added=0;
                for(int i=0;i<tracks.size();i++){
                    Api.Track track=tracks.get(i);
                    if(track!=null&&track.id!=null&&!seen.contains(track.id)){visible.add(track);seen.add(track.id);added++;}
                }
                if(added==0){if(moreWave!=null){moreWave.setText("Новых треков нет");moreWave.setEnabled(false);}return;}
                if(trackAdapter!=null)trackAdapter.notifyDataSetChanged();
                updateWaveFooter();
            }});
        }});
    }
    private void updateWaveFooter(){
        if(moreWave==null)return;
        moreWave.setEnabled(!waveBusy);
        moreWave.setText(waveBusy?getString(R.string.ui_004):"Загрузить ещё 5");
    }
    private void loadLikes(){
        if(!logged())return;
        final String user=uid;final Api client=api();
        task(new Task(){public Object run()throws Exception{
            LikesCache.State cached=LikesCache.read(MainActivity.this,user);
            if(cached!=null && System.currentTimeMillis()-cached.savedAt<10*60*1000L)return cached;
            LikesCache.State state=new LikesCache.State();state.uid=user;
            try{state.ids=client.likedIds(user);int end=Math.min(20,state.ids.size());
                state.tracks.addAll(client.details(state.ids.subList(0,end)));state.next=end;
                LikesCache.write(MainActivity.this,state);return state;
            }catch(Exception e){if(cached!=null)return cached;throw e;}
        }},new Done(){public void run(Object v){
            likesState=(LikesCache.State)v;likesBusy=false;
            likedTrackIds.clear();likedTrackIds.addAll(likesState.ids);likedIdsLoaded=true;
            showTracks(getString(R.string.ui_009),likesState.tracks,false);
            if(trackList==null)return;
            screen="likes";subheading.setText(likesState.ids.size()+getString(R.string.ui_023));
            trackList.setOnScrollListener(new AbsListView.OnScrollListener(){
                public void onScroll(AbsListView list,int first,int count,int total){}
                public void onScrollStateChanged(AbsListView list,int state){
                    if(state==SCROLL_STATE_IDLE && list.getLastVisiblePosition()>=visible.size()-3)loadNextLikes();
                }
            });
            updateLikesFooter();
        }});
    }
    private void updateLikesFooter(){
        if(moreLikes==null || likesState==null)return;
        moreLikes.setVisibility(likesState.next<likesState.ids.size()?View.VISIBLE:View.GONE);
        moreLikes.setEnabled(!likesBusy);
        moreLikes.setText(getString(likesBusy?R.string.ui_004:R.string.likes_more));
    }
    private void loadNextLikes(){
        if(!"likes".equals(screen)||likesBusy||likesState==null||likesState.next>=likesState.ids.size())return;
        final LikesCache.State state=likesState;final int version=screenVersion;
        final int end=Math.min(state.next+20,state.ids.size());final Api client=api();
        final ArrayList<String> ids=new ArrayList<String>(state.ids.subList(state.next,end));
        likesBusy=true;updateLikesFooter();
        worker.execute(new Runnable(){public void run(){
            ArrayList<Api.Track> found=null;Exception failure=null;
            try{found=client.details(ids);}catch(Exception e){failure=e;}
            final ArrayList<Api.Track> tracks=found;final Exception problem=failure;
            handler.post(new Runnable(){public void run(){
                if(destroyed||version!=screenVersion||state!=likesState)return;
                likesBusy=false;
                if(problem!=null){updateLikesFooter();toast(problem.getMessage());return;}
                state.tracks.addAll(tracks);state.next=end;trackAdapter.notifyDataSetChanged();updateLikesFooter();
                final LikesCache.State copy=state.copy();
                worker.execute(new Runnable(){public void run(){try{LikesCache.write(MainActivity.this,copy);}catch(Exception ignored){}}});
            }});
        }});
    }
    private void loadPlaylists(){
        if(!logged())return;task(new Task(){public Object run()throws Exception{
            JSONArray result=api().playlists(uid);Object saved=api().call("/users/"+Api.enc(uid)+"/likes/playlists",null);
            if(saved instanceof JSONArray){JSONArray a=(JSONArray)saved;for(int i=0;i<a.length();i++){JSONObject j=a.getJSONObject(i);JSONObject p=j.optJSONObject("playlist");result.put(p==null?j:p);}}
            return result;
        }},new Done(){public void run(Object v)throws Exception{
            screen="playlists";shell(getString(R.string.ui_011),getString(R.string.ui_021),"library");JSONArray a=(JSONArray)v;LinearLayout list=scroll();HashSet<String> seen=new HashSet<String>();
            for(int i=0;i<a.length();i++){
                final JSONObject p=a.getJSONObject(i);JSONObject owner=p.optJSONObject("owner");final String ownerId=owner==null?uid:owner.optString("uid",uid);final String kind=p.optString("kind","");
                if(kind.length()==0||seen.contains(ownerId+":"+kind))continue;seen.add(ownerId+":"+kind);
                item(list,"≡",p.optString("title",getString(R.string.ui_022)),p.optInt("trackCount",0)+getString(R.string.ui_023),new Runnable(){public void run(){
                    task(new Task(){public Object run()throws Exception{return api().playlist(ownerId,kind);}},new Done(){@SuppressWarnings("unchecked") public void run(Object v){showTracks(p.optString("title",getString(R.string.ui_022)),(ArrayList<Api.Track>)v,false);}});
                }});
            }
            if(seen.isEmpty()){TextView t=text(getString(R.string.ui_024),16,MUTED);t.setPadding(dp(16),dp(20),0,0);list.addView(t);}
        }});
    }
    private void offline(){try{showTracks(getString(R.string.ui_013),Store.all(this),true);}catch(Exception e){error(e);}}
    private void showTracks(String title,ArrayList<Api.Track> tracks,final boolean offline){
        final boolean searchResultsScreen=!offline&&title.equals(getString(R.string.ui_049)+query);
        final boolean waveResultsScreen=!offline&&title.equals(getString(R.string.wave_title));
        screen=offline?"offline":"tracks";visible=tracks;listTitle=title;shell(title,tracks.size()+getString(R.string.ui_023),"library");
        if(tracks.isEmpty()){note(offline?getString(R.string.ui_025):getString(R.string.ui_026));return;}
        LinearLayout toolbar=row();toolbar.setPadding(dp(8),dp(3),dp(8),dp(3));
        toolbar.addView(button(getString(R.string.ui_027),new Runnable(){public void run(){
            ArrayList<Api.Track> shuffled=new ArrayList<Api.Track>(visible);Collections.shuffle(shuffled);play(shuffled,0);
        }}),new LinearLayout.LayoutParams(0,dp(43),1));
        if(!offline)toolbar.addView(button(getString(R.string.ui_028),new Runnable(){public void run(){confirmAll();}}),new LinearLayout.LayoutParams(dp(90),dp(43)));
        body.addView(toolbar);
        final ArrayList<Api.Track> items=tracks;
        trackList=new ListView(this);trackList.setCacheColorHint(PAPER);
        trackList.setDivider(new ColorDrawable(0xffd2d2d2));trackList.setDividerHeight(dp(1));
        if(searchResultsScreen){
            moreSearch=button(searchBusy?getString(R.string.ui_004):"Загрузить ещё 20",new Runnable(){public void run(){loadMoreSearch();}});
            moreSearch.setVisibility(View.VISIBLE);trackList.addFooterView(moreSearch,null,false);
        }else if(waveResultsScreen){
            moreWave=button(waveBusy?getString(R.string.ui_004):"Загрузить ещё 5",new Runnable(){public void run(){loadMoreWave();}});
            moreWave.setVisibility(View.VISIBLE);trackList.addFooterView(moreWave,null,false);
        }else{
            moreLikes=button(getString(R.string.likes_more),new Runnable(){public void run(){loadNextLikes();}});
            moreLikes.setVisibility(View.GONE);trackList.addFooterView(moreLikes,null,false);
        }
        trackAdapter=new TrackAdapter(items,offline);trackList.setAdapter(trackAdapter);
        trackList.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> p,View v,int position,long id){if(position<items.size())play(items,position);}});
        trackList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener(){public boolean onItemLongClick(AdapterView<?> p,View v,int position,long id){if(position>=items.size())return false;trackMenu(items.get(position),offline);return true;}});
        body.addView(trackList,new LinearLayout.LayoutParams(-1,0,1));
        // Keep all remote track lists paged as the user reaches the end.
        if(searchResultsScreen){
            trackList.setOnScrollListener(new AbsListView.OnScrollListener(){
                public void onScroll(AbsListView list,int first,int count,int total){}
                public void onScrollStateChanged(AbsListView list,int state){
                    if(state==SCROLL_STATE_IDLE && list.getLastVisiblePosition()>=visible.size()-3)loadMoreSearch();
                }
            });
        }else if(waveResultsScreen){
            trackList.setOnScrollListener(new AbsListView.OnScrollListener(){
                public void onScroll(AbsListView list,int first,int count,int total){}
                public void onScrollStateChanged(AbsListView list,int state){
                    if(state==SCROLL_STATE_IDLE && list.getLastVisiblePosition()>=visible.size()-3)loadMoreWave();
                }
            });
        }
    }
    private static final class TrackRow {
        ImageView cover;TextView title,artist,more;Api.Track track;
    }
    private final class TrackAdapter extends BaseAdapter {
        private final ArrayList<Api.Track> items;private final boolean offline;
        TrackAdapter(ArrayList<Api.Track> tracks,boolean local){items=tracks;offline=local;}
        public int getCount(){return items.size();}
        public Object getItem(int p){return items.get(p);}
        public long getItemId(int p){return p;}
        public View getView(int position,View old,android.view.ViewGroup parent){
            final TrackRow holder;
            if(old==null){
                holder=new TrackRow();LinearLayout r=row();r.setPadding(dp(9),dp(7),dp(5),dp(7));r.setMinimumHeight(dp(66));
                holder.cover=new ImageView(MainActivity.this);holder.cover.setScaleType(ImageView.ScaleType.CENTER_CROP);holder.cover.setBackgroundColor(0xffd6d6d6);
                r.addView(holder.cover,new LinearLayout.LayoutParams(dp(48),dp(48)));
                LinearLayout labels=column();labels.setPadding(dp(11),0,dp(4),0);
                holder.title=text("",16,INK);bold(holder.title);holder.title.setSingleLine(true);labels.addView(holder.title);
                holder.artist=text("",13,MUTED);holder.artist.setSingleLine(true);labels.addView(holder.artist);
                r.addView(labels,new LinearLayout.LayoutParams(0,-2,1));holder.more=text("...",25,MUTED);holder.more.setGravity(Gravity.CENTER);
                r.addView(holder.more,new LinearLayout.LayoutParams(dp(38),dp(48)));
                holder.more.setOnClickListener(new View.OnClickListener(){public void onClick(View v){trackMenu(holder.track,offline);}});
                r.setTag(holder);old=r;
            }else holder=(TrackRow)old.getTag();
            holder.track=items.get(position);holder.title.setText(holder.track.title);holder.artist.setText(holder.track.artist);
            loadCover(holder.track,holder.cover,60);return old;
        }
    }
    private void play(ArrayList<Api.Track> tracks,int position){if(service==null){toast(getString(R.string.ui_029));return;}service.play(tracks,position);player();}
    private void trackMenu(final Api.Track t,final boolean offline){
        String[] options=offline?new String[]{getString(R.string.ui_030),getString(R.string.ui_031)}:new String[]{getString(R.string.ui_030),getString(R.string.ui_032)};
        new AlertDialog.Builder(this).setTitle(t.title).setItems(options,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
            if(which==0){ArrayList<Api.Track> a=new ArrayList<Api.Track>();a.add(t);play(a,0);}else if(offline){try{Store.delete(MainActivity.this,t);offline();}catch(Exception e){error(e);}}else download(t,true);
        }}).show();
    }
    private void confirmAll(){
        if("likes".equals(screen)&&likesState!=null&&likesState.next<likesState.ids.size()){toast(getString(R.string.download_loaded_only));}
        final ArrayList<Api.Track> batch=new ArrayList<Api.Track>(visible);
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_033)+batch.size()+getString(R.string.ui_034)).setMessage(getString(R.string.ui_035))
            .setPositiveButton(getString(R.string.ui_036),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){for(Api.Track t:batch)download(t,false);}}).setNegativeButton(getString(R.string.ui_037),null).show();
    }
    private void download(final Api.Track t,final boolean announce){
        if(!logged())return;if(downloading.contains(t.id)){toast(getString(R.string.ui_038));return;}
        downloading.add(t.id);final String token=prefs().getString("token","");if(announce)toast(getString(R.string.ui_039)+t.title);
        downloads.execute(new Runnable(){public void run(){
            Exception failure=null;File part=null;
            try{
                Net.init(getAssets().open("roots.pem"));File finalFile=Store.file(MainActivity.this,t);
                if(!finalFile.isFile()){
                    part=new File(finalFile.getAbsolutePath()+".part");Net.download(new Api(token).audio(t),part,new Net.Progress(){public void update(long n){}public boolean cancelled(){return destroyed;}});
                    if(!part.renameTo(finalFile))throw new IOException(getString(R.string.ui_040));
                }
                Store.save(MainActivity.this,t);
            }catch(Exception e){failure=e;if(part!=null)part.delete();}
            final Exception error=failure;handler.post(new Runnable(){public void run(){downloading.remove(t.id);if(destroyed)return;if(error!=null){toast(t.title+": "+error.getMessage());}else{toast(getString(R.string.ui_041)+t.title);}}});
        }});
    }
    private void loadCover(final Api.Track t,final ImageView image,final int size){
        covers.load(t,image,size);
    }
    private void player(){
        screen="player";shell(getString(R.string.ui_015),"","player");mini.setVisibility(View.GONE);
        Api.Track track=service==null?null:service.current();if(track==null){note(getString(R.string.ui_042));return;}
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout content=column();content.setPadding(dp(18),dp(18),dp(18),dp(14));scroll.addView(content);body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        int width=getResources().getDisplayMetrics().widthPixels-dp(36);int art=Math.min(width,dp(250));
        nowCover=new ImageView(this);nowCover.setScaleType(ImageView.ScaleType.CENTER_CROP);nowCover.setBackgroundColor(0xffc8c8c8);nowCover.setImageResource(android.R.drawable.ic_media_play);
        LinearLayout.LayoutParams artParams=new LinearLayout.LayoutParams(art,art);artParams.gravity=Gravity.CENTER_HORIZONTAL;artParams.bottomMargin=dp(18);content.addView(nowCover,artParams);loadCover(track,nowCover,240);
        TextView title=text(track.title,22,INK);bold(title);content.addView(title);content.addView(text(track.artist,18,MUTED));
        likeTrackId=track.id;likeStateKnown=likedIdsLoaded;final String playerTrackId=track.id;
        nowLike=button(likeStateKnown?(likedTrackIds.contains(playerTrackId)?getString(R.string.like_remove):getString(R.string.like_add)):getString(R.string.like_check),new Runnable(){public void run(){toggleCurrentLike();}});
        nowLike.setEnabled(likeStateKnown&&!likeBusy);LinearLayout.LayoutParams likeParams=new LinearLayout.LayoutParams(-1,dp(48));likeParams.topMargin=dp(8);likeParams.bottomMargin=dp(5);content.addView(nowLike,likeParams);
        if(!likedIdsLoaded)refreshLikedIds(playerTrackId,screenVersion,uid);
        nowStatus=text(service.message,12,MUTED);
        nowStatus.setPadding(0,dp(5),0,dp(4));
        nowStatus.setVisibility(
            service.message!=null && service.message.toLowerCase().contains("буферизация")
            ? View.VISIBLE : View.GONE
        );
        content.addView(nowStatus);
        LinearLayout progress=row();elapsed=text("0:00",12,INK);total=text(time(track.duration),12,INK);seek=new SeekBar(this);seek.setMax(1000);
        GradientDrawable pg=fill(YELLOW,0xffffba00);pg.setCornerRadius(dp(3));GradientDrawable bg=fill(0xffb8b8b8,0xffd8d8d8);bg.setCornerRadius(dp(3));
        android.graphics.drawable.ClipDrawable clip=new android.graphics.drawable.ClipDrawable(pg,Gravity.LEFT,android.graphics.drawable.ClipDrawable.HORIZONTAL);
        LayerDrawable layer=new LayerDrawable(new Drawable[]{bg,clip}){
            @Override public void draw(Canvas canvas){
                int save=canvas.save();
                canvas.translate(0,-MainActivity.this.dp(5));
                try{super.draw(canvas);}finally{canvas.restoreToCount(save);}
            }
        };
        layer.setId(0,android.R.id.background);layer.setId(1,android.R.id.progress);seek.setProgressDrawable(layer);
        progress.addView(elapsed);progress.addView(seek,new LinearLayout.LayoutParams(0,dp(40),1));progress.addView(total);content.addView(progress);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){dragging=true;}public void onStopTrackingTouch(SeekBar b){dragging=false;if(service!=null)service.seek(service.duration()*b.getProgress()/1000);}public void onProgressChanged(SeekBar b,int value,boolean user){}});
        LinearLayout controls=row();controls.addView(button("|◀",new Runnable(){public void run(){service.previous();player();}}),new LinearLayout.LayoutParams(0,dp(62),1));
        nowPlay=button(service.isPlaying()?"❚❚":"▶",new Runnable(){public void run(){service.toggle();}});nowPlay.setTextSize(26);controls.addView(nowPlay,new LinearLayout.LayoutParams(0,dp(62),1));
        controls.addView(button("▶|",new Runnable(){public void run(){service.next();player();}}),new LinearLayout.LayoutParams(0,dp(62),1));content.addView(controls);
        updatePlayer();
    }
    private void updateLikeButton(){
        if(nowLike==null||likeTrackId.length()==0)return;
        if(likeBusy&&likeTrackId.equals(likeBusyTrackId)){nowLike.setText(getString(R.string.like_working));nowLike.setEnabled(false);return;}
        nowLike.setText(likedTrackIds.contains(likeTrackId)?getString(R.string.like_remove):getString(R.string.like_add));
        nowLike.setEnabled(likeStateKnown&&!likeBusy);
    }
    private void refreshLikedIds(final String targetTrack,final int version,final String targetUid){
        final String token=prefs().getString("token","");
        worker.execute(new Runnable(){public void run(){
            ArrayList<String> ids=null;Exception failure=null;
            try{Net.init(getAssets().open("roots.pem"));ids=new Api(token).likedIds(targetUid);}catch(Exception e){failure=e;}
            final ArrayList<String> loaded=ids;final Exception problem=failure;
            handler.post(new Runnable(){public void run(){
                if(destroyed||!targetUid.equals(uid))return;
                if(problem==null&&loaded!=null){likedTrackIds.clear();likedTrackIds.addAll(loaded);likedIdsLoaded=true;}
                if(version==screenVersion&&"player".equals(screen)&&targetTrack.equals(likeTrackId)){
                    likeStateKnown=true;updateLikeButton();
                }
            }});
        }});
    }
    private void toggleCurrentLike(){
        if(!logged()||!likeStateKnown||likeTrackId.length()==0||likeBusy)return;
        final String trackId=likeTrackId;final String targetUid=uid;final String token=prefs().getString("token","");
        final boolean add=!likedTrackIds.contains(trackId);
        likeBusy=true;likeBusyTrackId=trackId;updateLikeButton();
        worker.execute(new Runnable(){public void run(){
            Exception failure=null;
            try{Net.init(getAssets().open("roots.pem"));new Api(token).setTrackLiked(targetUid,trackId,add);}catch(Exception e){failure=e;}
            final Exception problem=failure;
            handler.post(new Runnable(){public void run(){
                if(destroyed)return;
                if(trackId.equals(likeBusyTrackId)){likeBusy=false;likeBusyTrackId="";}
                if(problem==null){
                    if(add)likedTrackIds.add(trackId);else likedTrackIds.remove(trackId);
                    LikesCache.invalidate(MainActivity.this,targetUid);
                    toast(getString(add?R.string.like_added:R.string.like_removed));
                }else toast(getString(R.string.like_failed)+" "+(problem.getMessage()==null?problem.toString():problem.getMessage()));
                if("player".equals(screen)&&nowLike!=null){if(trackId.equals(likeTrackId))likeStateKnown=true;updateLikeButton();}
            }});
        }});
    }
    private static String time(int ms){int s=ms/1000;return (s/60)+":"+(s%60<10?"0":"")+(s%60);}
    private void updatePlayer(){
        if(mini==null)return;Api.Track current=service==null?null:service.current();mini.setVisibility(current==null||"player".equals(screen)?View.GONE:View.VISIBLE);
        if(current==null)return;miniTitle.setText(current.title);miniDetail.setText(current.artist==null?"":current.artist);
        miniDetail.setVisibility(current.artist == null || current.artist.length() == 0 ? View.GONE : View.VISIBLE);miniPlay.setText(service.isPlaying()?"||":"▶");
        if("player".equals(screen)&&nowStatus!=null){
            if(nowCover!=null&&!String.valueOf(nowCover.getTag()).startsWith(current.id+":")){player();return;}
            String status=service.message;
            boolean showBuffer=status!=null&&status.toLowerCase().contains("буферизация");
            nowStatus.setText(showBuffer?status:"");
            nowStatus.setVisibility(showBuffer?View.VISIBLE:View.GONE);
            nowPlay.setText(service.isPlaying()?"||":"▶");int duration=service.duration();elapsed.setText(time(service.position()));total.setText(time(duration>0?duration:current.duration));if(!dragging)seek.setProgress(duration>0?(int)(1000L*service.position()/duration):0);
        }
    }
    private void search(){
        screen="search";shell(getString(R.string.ui_044),getString(R.string.ui_045),"search");LinearLayout controls=row();controls.setPadding(dp(8),dp(8),dp(8),0);
        final EditText field=new EditText(this);field.setTextSize(16);field.setSingleLine(true);field.setHint(getString(R.string.ui_046));field.setText(query);controls.addView(field,new LinearLayout.LayoutParams(0,dp(48),1));
        controls.addView(button(getString(R.string.ui_047),new Runnable(){public void run(){query=field.getText().toString().trim();searchPage=0;runSearch();}}));body.addView(controls);note(getString(R.string.ui_048));
    }
    private void runSearch(){
        if(!logged())return;if(query.length()==0)return;
        searchPage=0;searchBusy=false;
        task(new Task(){public Object run()throws Exception{return api().search(query,searchPage);}},new Done(){@SuppressWarnings("unchecked")public void run(Object v){showTracks(getString(R.string.ui_049)+query,(ArrayList<Api.Track>)v,false);}});
    }
    @SuppressWarnings("unchecked")
    private void loadMoreSearch(){
        if(searchBusy||query.length()==0||!"tracks".equals(screen)||trackAdapter==null||trackList==null)return;
        if(moreSearch!=null&&!moreSearch.isEnabled())return;
        searchBusy=true;
        if(moreSearch!=null){moreSearch.setEnabled(false);moreSearch.setText(getString(R.string.ui_004));}
        final int requestedPage=searchPage+1;
        final String requestedQuery=query;
        final int version=screenVersion;
        final String token=prefs().getString("token","");
        worker.execute(new Runnable(){public void run(){
            ArrayList<Api.Track> received=null;Exception failure=null;
            try{Net.init(getAssets().open("roots.pem"));received=new Api(token).search(requestedQuery,requestedPage);}catch(Exception e){failure=e;}
            final ArrayList<Api.Track> extra=received;final Exception problem=failure;
            handler.post(new Runnable(){public void run(){
                if(destroyed)return;
                if(version!=screenVersion){searchBusy=false;return;}
                searchBusy=false;
                if(!requestedQuery.equals(query)||!"tracks".equals(screen))return;
                if(problem!=null){updateSearchFooter();error(problem);return;}
                if(extra==null||extra.isEmpty()){
                    if(moreSearch!=null){moreSearch.setText("Больше результатов нет");moreSearch.setEnabled(false);}
                    return;
                }
                HashSet<String> seen=new HashSet<String>();
                for(int i=0;i<visible.size();i++)if(visible.get(i)!=null)seen.add(visible.get(i).id);
                int added=0;
                for(int i=0;i<extra.size();i++){
                    Api.Track track=extra.get(i);
                    if(track!=null&&track.id!=null&&!seen.contains(track.id)){visible.add(track);seen.add(track.id);added++;}
                }
                searchPage=requestedPage;
                if(added==0){if(moreSearch!=null){moreSearch.setText("Больше результатов нет");moreSearch.setEnabled(false);}return;}
                if(trackAdapter!=null)trackAdapter.notifyDataSetChanged();
                if(subheading!=null)subheading.setText(visible.size()+getString(R.string.ui_023));
                updateSearchFooter();
            }});
        }});
    }
    private void updateSearchFooter(){
        if(moreSearch==null)return;
        moreSearch.setEnabled(!searchBusy);
        moreSearch.setText(searchBusy?getString(R.string.ui_004):"Загрузить ещё 20");
    }
    private void settings(){
        screen="settings";shell(getString(R.string.ui_003),getString(R.string.ui_050),"settings");LinearLayout list=scroll();
        item(list,"•",uid.length()>0?login:getString(R.string.ui_017),getString(R.string.ui_051),new Runnable(){public void run(){loginDialog();}});
        item(list,"↻",getString(R.string.ui_052),getString(R.string.ui_053),new Runnable(){public void run(){
            task(new Task(){public Object run()throws Exception{Net.Response r=Net.open(Api.BASE+"/account/status",null,null);try{if(r.code!=200&&r.code!=401&&r.code!=403)throw new IOException("HTTP "+r.code);return getString(R.string.ui_054)+r.code;}finally{r.close();}}},new Done(){public void run(Object v){new AlertDialog.Builder(MainActivity.this).setTitle(getString(R.string.ui_055)).setMessage((String)v).setPositiveButton(getString(R.string.ui_056),null).show();}});
        }});
        if(uid.length()>0)item(list,"×",getString(R.string.ui_058),getString(R.string.ui_059),new Runnable(){public void run(){prefs().edit().clear().commit();uid="";login="";likedTrackIds.clear();likedIdsLoaded=false;likesState=null;if(service!=null)service.stop();welcome();}});
        TextView info=text(getString(R.string.ui_060),13,MUTED);info.setPadding(dp(16),dp(16),dp(16),dp(20));list.addView(info);
    }
    private void loginDialog(){
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_061))
            .setItems(new String[]{getString(R.string.ui_062), getString(R.string.ui_063)},new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface d,int which){if(which==0)deviceLogin();else tokenDialog();}
            }).setNegativeButton(getString(R.string.ui_037),null).show();
    }
    private void saveAccount(String token,JSONObject status)throws Exception{
        JSONObject account=status.getJSONObject("account");String user=account.optString("uid","");
        if(user.length()==0)throw new IOException(getString(R.string.ui_064));
        uid=user;login=account.optString("displayName",account.optString("login",getString(R.string.ui_065)));
        likedTrackIds.clear();likedIdsLoaded=false;likesState=null;
        prefs().edit().putString("token",token).putString("uid",uid).putString("login",login).commit();
        home();toast(getString(R.string.ui_066));
    }
    private void deviceLogin(){
        task(new Task(){public Object run()throws Exception{return DeviceAuth.request();}},new Done(){
            public void run(Object value)throws Exception{showDeviceCode((JSONObject)value);}
        });
    }
    private void showDeviceCode(final JSONObject code)throws Exception{
        final int ticket=++authSession;
        final String url=DeviceAuth.verificationUrl(code);
        final String deviceCode=code.getString("device_code");
        final long deadline=SystemClock.elapsedRealtime()+Math.max(30,code.optInt("expires_in",300))*1000L;
        final int[] interval={Math.max(5,code.optInt("interval",5))*1000};
        LinearLayout box=column();box.setPadding(dp(18),dp(10),dp(18),dp(10));
        box.addView(text(getString(R.string.ui_067),15,INK));
        TextView link=text(url,17,INK);link.setPadding(0,dp(8),0,dp(8));box.addView(link);
        TextView digits=text(code.getString("user_code"),30,INK);bold(digits);digits.setGravity(Gravity.CENTER);box.addView(digits);
        final TextView status=text(getString(R.string.ui_068),14,MUTED);status.setPadding(0,dp(12),0,0);box.addView(status);
        deviceDialog=new AlertDialog.Builder(this).setTitle(getString(R.string.ui_069)).setView(box)
            .setNegativeButton(getString(R.string.ui_037),null).setNeutralButton(getString(R.string.ui_070),null).create();
        deviceDialog.setOnDismissListener(new DialogInterface.OnDismissListener(){public void onDismiss(DialogInterface d){if(ticket==authSession)authSession++;}});
        deviceDialog.show();deviceDialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener(){public void onClick(View v){
            try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(Exception e){toast(getString(R.string.ui_071));}
        }});
        final Runnable poll=new Runnable(){public void run(){
            if(destroyed||ticket!=authSession)return;
            if(SystemClock.elapsedRealtime()>=deadline){status.setText(getString(R.string.ui_072));return;}
            worker.execute(new Runnable(){public void run(){
                JSONObject response=null,account=null;String token=null;Exception failure=null;
                try{Net.init(getAssets().open("roots.pem"));response=DeviceAuth.poll(deviceCode);
                    token=response.optString("access_token","");if(token.length()>0)account=new Api(token).status();
                }catch(Exception e){failure=e;}
                final JSONObject result=response,profile=account;final String access=token;final Exception error=failure;
                handler.post(new Runnable(){public void run(){
                    if(destroyed||ticket!=authSession)return;
                    if(error!=null){status.setText(getString(R.string.ui_073));schedule();return;}
                    if(profile!=null){try{deviceDialog.dismiss();saveAccount(access,profile);}catch(Exception e){error(e);}return;}
                    String kind=result.optString("error","");
                    if("authorization_pending".equals(kind)){status.setText(getString(R.string.ui_074));schedule();}
                    else if("slow_down".equals(kind)){interval[0]+=5000;schedule();}
                    else{status.setText(getString(R.string.ui_075)+kind+getString(R.string.ui_076));}
                }
                private void schedule(){handler.postDelayed(new Runnable(){public void run(){pollAgain(ticket);}},interval[0]);}
                });
            }});
        }};
        authPoll=poll;handler.postDelayed(poll,interval[0]);
    }
    private Runnable authPoll;
    private void pollAgain(int ticket){if(!destroyed&&ticket==authSession&&authPoll!=null)authPoll.run();}
    private void tokenDialog(){
        LinearLayout box=column();box.setPadding(dp(16),dp(8),dp(16),0);TextView instruction=text(getString(R.string.ui_077),14,INK);box.addView(instruction);
        final EditText token=new EditText(this);token.setSingleLine(true);token.setHint(getString(R.string.ui_078));token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);box.addView(token);
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_079)).setView(box).setNegativeButton(getString(R.string.ui_037),null).setPositiveButton(getString(R.string.ui_080),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
            final String value=token.getText().toString().trim();if(value.length()<10||!value.matches("[A-Za-z0-9_\\-.]+")){toast(getString(R.string.ui_081));return;}
            task(new Task(){public Object run()throws Exception{return new Api(value).status();}},new Done(){public void run(Object v)throws Exception{
                saveAccount(value,(JSONObject)v);
            }});
        }}).show();
    }
    public void onBackPressed(){if(!"home".equals(screen))home();else super.onBackPressed();}
    public void onLowMemory(){super.onLowMemory();if(covers!=null)covers.trim();}
    public void onDestroy(){destroyed=true;authSession++;if(deviceDialog!=null)deviceDialog.dismiss();handler.removeCallbacksAndMessages(null);if(bound){if(service!=null)service.changed=null;unbindService(connection);}worker.shutdownNow();downloads.shutdownNow();if(covers!=null)covers.close();super.onDestroy();}
}
