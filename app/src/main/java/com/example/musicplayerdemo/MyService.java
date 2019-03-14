package com.example.musicplayerdemo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.support.v4.app.NotificationCompat;
import java.io.File;

public class MyService extends Service {

    MediaPlayer mediaPlayer=new MediaPlayer();//MediaPlayer
    public File[] musicFileArray=FirstActivity.musicFileArray;//存放音乐的File数组
    public static int i=0;//当前播放歌曲的广义角标
    public static boolean isPause=true;//是否暂停判断变量
    musicListener listener=new musicListener();//监听器（广义回调接口）
    ServiceBroadcastReceiver receiver;//广播接收器

    public static final int NOTIFICATION_ID=1;//通知ID

    public static final int PLAY_MUSIC=1;//播放音乐指令
    public static final int PAUSE_MUSIC=2;//暂停音乐指令
    public static final int SEEKTO_MUSIC=3;//跳转进度指令
    public static final int LAST_MUSIC=4;//跳转上一曲指令
    public static final int NEXT_MUSIC=5;//跳转下一曲指令
    public static final int CHANGE_AS_LIST=6;//根据点击滑动列表跳转歌曲指令

    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onCreate() {
        super.onCreate();
        IntentFilter filter=new IntentFilter("android.music.service");
        receiver=new ServiceBroadcastReceiver();
        registerReceiver(receiver,filter);//注册广播
        try
        {
            mediaPlayer.setDataSource(musicFileArray[i].getPath());//初始化MediaPlayer
            mediaPlayer.prepare();
            listener.changeSeekBar();//更新进度条与其关联的显示时间.歌曲名的TextView
            startForeground(NOTIFICATION_ID,getNotification(FirstActivity.getMusicName(),0,1));//前台服务（不会被异常回收）
            listener.setNotification();//更新通知
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    class musicListener {
        public void playMusic(){//播放歌曲
            isPause=false;//赋值 暂停判断变量
            new Thread(){//播放歌曲时因需一直循环进行判断，故为耗时操作，应在子线程中运行（需一直循环判断：歌曲进度、暂停判断变量、进条被正被手动改变判断变量）
                public void run() {
                    try
                    {
                        mediaPlayer.start();//开始播放
                        while (true){//当给播放时一直循环判断
                            if (((int)(((mediaPlayer.getCurrentPosition()*1.00)/mediaPlayer.getDuration())*100)==100)){//当歌曲播放到末尾时进行下一首
                                mediaPlayer.reset();//MediaPlayer回到刚刚创建的状态
                                mediaPlayer.setDataSource((musicFileArray[(++i)%musicFileArray.length]).getPath());//设置下一首歌曲文件路径并初始化
                                mediaPlayer.prepare();
                                mediaPlayer.start();//开始播放
                                FirstActivity.itemList.get((i+musicFileArray.length-1)%musicFileArray.length).color=Color.parseColor("#FFFFFF");//当歌曲更换时，更改与本次更换关联的两个滑动列表子项数据实体的颜色属性
                                FirstActivity.itemList.get((i+musicFileArray.length)%musicFileArray.length).color=Color.parseColor("#FFD700");
                                changeListView();//通过发送广播，在活动的线程中刷新列表（UI只能在对应活动的主线程中更新）
                            }
                           if (isPause){//通过暂停判断变量 判断是否被用户设置暂停
                                mediaPlayer.pause();//暂停播放
                                break;
                            }
                            if (!FirstActivity.isStartTracking){//当用户没有拖动SeekBar时持续通过发送广播，通知活动的主线程更新SeekBar与其关联的显示时间/歌曲名的TextView（当用户拖动SeekBar时不应更新SeekBar，防止滑动条跳转）
                                changeSeekBar();
                            }
                            setNotification();
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }
            }.start();
        }

        public void pauseMusic(){//通过提供更改判暂停判断变量的方法来控制播放
            isPause=true;
        }

        public void seekMusic(Intent intent){//跳转音乐进度
            if (isPause){//当为暂停状态时
                mediaPlayer.start();//考虑开启服务后未点击播放就拉动SeekBar，然后通过通知跳转不能播放的情况（seekTo()只能对执行过start()的歌曲跳转）
                mediaPlayer.seekTo((int)((intent.getIntExtra("Seek",0)/100.0)*mediaPlayer.getDuration()));//根据广播的Intent携带的进度值跳转进度
                mediaPlayer.pause();//保持暂停
                setNotification();//更新通知
                FirstActivity.isStartTracking=false;//赋值 SeekBar正被拖动判断变量
            }
            else {//当为播放状态时
                mediaPlayer.seekTo((int)((intent.getIntExtra("Seek",0)/100.0)*mediaPlayer.getDuration()));//直接跳转（不用暂停，也不用更新通知，因为playMusic()中播放音乐时一直在循环更新通知）
                FirstActivity.isStartTracking=false;//赋值 SeekBar正被拖动判断变量
            }
        }

        public void lastOrNextMusic(){//跳转歌曲
            changeListView();//当歌曲更换时更改与本次更换关联的两个滑动列表子项数据实体的颜色属性
            try
            {
                if (isPause){//当为暂停状态时
                    mediaPlayer.release();//释放与MediaPlay对象相关资源
                    mediaPlayer=new MediaPlayer();
                    mediaPlayer.setDataSource(musicFileArray[i].getPath());//装载对应的跳转歌曲并初始化
                    mediaPlayer.prepare();
                    changeSeekBar();//更新进度条
                    setNotification();//设置通知
                }
                else {//当为播放状态
                    isPause=true;//通过改变 暂停判断变量来停止播放歌曲的线程
                    mediaPlayer.release();
                    mediaPlayer=new MediaPlayer();
                    mediaPlayer.setDataSource(musicFileArray[i].getPath());//装载对应的跳转歌曲并初始化
                    mediaPlayer.prepare();
                    playMusic();//播放歌曲
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        public void changeSeekBar(){//通过发送广播，通知主线程改变SeekBar的进度与其关联的TextView上的时间/歌曲名
            Intent intent=new Intent("android.music.activity");
            intent.putExtra("Type",FirstActivity.UPDATE_SEEKBAR);
            intent.putExtra("Total",mediaPlayer.getDuration());
            intent.putExtra("Current",mediaPlayer.getCurrentPosition());
            sendBroadcast(intent);
        }

        public void changeListView(){//通过发送广播，通知主线程根线列表控件
            Intent intent=new Intent("android.music.activity");
            intent.putExtra("Type",FirstActivity.UPDATE_LISTVIEW);
            sendBroadcast(intent);
        }

        public void setNotification(){//更新通知上显示的信息
            getNotificationManager().notify(NOTIFICATION_ID,getNotification(FirstActivity.getMusicName(),mediaPlayer.getCurrentPosition(),mediaPlayer.getDuration()));
        }
    }

    class ServiceBroadcastReceiver extends BroadcastReceiver{

        public void onReceive(Context context, Intent intent) {
            int type=intent.getIntExtra("Type",0);
            switch (type){
                case PLAY_MUSIC://当收到广播为播放音乐指令
                       listener.playMusic();
                    break;
                case PAUSE_MUSIC://当收到广播为暂停音乐指令
                    listener.pauseMusic();
                    break;
                case SEEKTO_MUSIC://当收到广播为跳转进度指令
                    listener.seekMusic(intent);
                    break;
                case LAST_MUSIC://当收到广播为跳转上一曲指令
                    i=(i+musicFileArray.length-1)%musicFileArray.length;
                    FirstActivity.itemList.get((i+musicFileArray.length+1)%musicFileArray.length).color=Color.parseColor("#FFFFFF");
                    FirstActivity.itemList.get(i).color=Color.parseColor("#FFD700");
                    listener.lastOrNextMusic();
                    break;
                case NEXT_MUSIC://当收到广播为跳转下一曲指令
                    i=(i+musicFileArray.length+1)%musicFileArray.length;
                    FirstActivity.itemList.get((i+musicFileArray.length-1)%musicFileArray.length).color=Color.parseColor("#FFFFFF");
                    FirstActivity.itemList.get(i).color=Color.parseColor("#FFD700");
                    listener.lastOrNextMusic();
                    break;
                case CHANGE_AS_LIST://根据点击滑动列表跳转歌曲指令
                    listener.lastOrNextMusic();
                    break;
                }
            }
        }

    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
        mediaPlayer.release();//释放资源
        i=0;//还原歌曲广义计数值
    }

    public NotificationManager getNotificationManager(){//获取通知管理器（需要用以显示通知）
        return (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    }

    public Notification getNotification(String musicName,int current,int total){//获取通知
        String CHANNEL_ONE_ID = "com.primedu.cn";
        String CHANNEL_ONE_NAME = "Channel One";
        NotificationChannel notificationChannel = null;
        if (Build.VERSION.SDK_INT >=26) {//适配8.0
            notificationChannel = new NotificationChannel(CHANNEL_ONE_ID,
                    CHANNEL_ONE_NAME, NotificationManager.IMPORTANCE_LOW);
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.setShowBadge(true);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(notificationChannel);
        }
        Intent intent=new Intent(this,FirstActivity.class);
        PendingIntent pi= PendingIntent.getActivity(this,0,intent,0);
        NotificationCompat.Builder builder=new NotificationCompat.Builder(this);
        builder.setContentTitle("音乐播放器Demo")
                .setContentIntent(pi)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(),R.mipmap.ic_launcher));
        int progress=(int)((mediaPlayer.getCurrentPosition()*1.0/mediaPlayer.getDuration())*100.0);//计算进度条填充长度
        builder.setContentText("歌曲:"+musicName+"    进度："+FirstActivity.getTimeStr(current)+"/"+FirstActivity.getTimeStr(total));//设置当前歌曲名与播放的进度
        builder.setProgress(100,progress,false);

        if (Build.VERSION.SDK_INT>=26){
            return builder.setChannelId(CHANNEL_ONE_ID).build();
        }
        else
            return builder.build();
    }
}
