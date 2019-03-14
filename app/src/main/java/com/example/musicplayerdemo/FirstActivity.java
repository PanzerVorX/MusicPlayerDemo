package com.example.musicplayerdemo;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public class FirstActivity extends AppCompatActivity implements View.OnClickListener{

    MediaMetadataRetriever mediaMetadataRetriever;
    Button playButton,lastButton,nextButton;
    TextView currentTime,totalTime,musicNameText;
    SeekBar seekBar;
    activityListener listener=new activityListener();
    changeReceiver receiver;
    public ListView listView;
    MusicAdapter adapter;
    public static List<MusicItem>itemList=new ArrayList<>();//数据链表
    public static Boolean isStartTracking=false;//SeekBar正被拖动判断变量
    public static File[] musicFileArray;//音乐文件数组
    int total,current;//总/当前时间
    long exitTime=0;//回退键计时变量
    public static final int UPDATE_SEEKBAR=9;//更新SeekBar指令
    public static final int UPDATE_LISTVIEW=8;//刷新滑动列表指令

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.first_layout);

        //适配滑动列表
        listView=(ListView)findViewById(R.id.listview);
        adapter=new MusicAdapter(this,R.layout.item_layout,itemList);
        listView.setAdapter(adapter);

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){//申请写入SD卡的权限
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
        }
        else {
            initList();//初始化数据链表
            adapter.notifyDataSetChanged();
            Intent intent=new Intent(this,MyService.class);
            startService(intent);//开启服务（获取权限后才能开启服务，因为会将活动从SD卡读取的结果复制到服务的变量中）
        }

        //获取控件
        playButton=(Button)findViewById(R.id.play_button);//播放/暂停按钮
        lastButton=(Button)findViewById(R.id.last_button);//上一曲按钮
        nextButton=(Button)findViewById(R.id.next_button);//下一曲按钮
        currentTime=(TextView)findViewById(R.id.current_time);//显示当前时间的TextView
        totalTime=(TextView)findViewById(R.id.total_time);//显示总时间的TextView
        musicNameText=(TextView)findViewById(R.id.musicname);//显示歌曲名的TextView
        seekBar=(SeekBar)findViewById(R.id.seekbar);//SeekBar

        //注册监听器
        playButton.setOnClickListener(this);
        lastButton.setOnClickListener(this);
        nextButton.setOnClickListener(this);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {//onProgressChanged()在SeekBar进度改变时调用，其中Boolean型参数为 用户操作判断变量
                if (fromUser){//当为用户操作时
                    int time=(int)((seekBar.getProgress()*1.0/100)*total);
                    currentTime.setText(getTimeStr(time));//改变当前时间
                }
            }

            public void onStartTrackingTouch(SeekBar seekBar) {//onStartTrackingTouch()在开始拖动SeekBar时调用
                isStartTracking=true;//赋值 SeekBar正被拖动判断变量，使服务中播放歌曲的线程停止更新SeekBar （避免用户拖动与播放时自动更新SeekBar冲突，使得SeekBar跳转现象）
            }

            public void onStopTrackingTouch(SeekBar seekBar) {//onStopTrackingTouch()在SeekBar改变完毕（刚放下）时调用
                //通过发送广播，通知服务让MediaPlayer跳转至指定的歌曲进度
                listener.seekToMusic();
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {//ListView的点击事件

            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position!=MyService.i%musicFileArray.length){//通过发送广播，通知服务跳转至点击的指定歌曲
                    itemList.get(MyService.i%musicFileArray.length).color=Color.parseColor("#FFFFFF");//当歌曲更换时，更改与更改本次更换关联的两个滑动列表子项数据实体的颜色属性
                    itemList.get(position).color=Color.parseColor("#FFD700");
                    MyService.i=position;//将当前歌曲的广义角标换位点击的子项角标
                    listener.changeAsList();
                }
            }
        });

        IntentFilter filter=new IntentFilter("android.music.activity");
        receiver=new changeReceiver ();
        registerReceiver(receiver,filter);//注册广播

        if (!MyService.isPause){//防止服务在播放音频时活动被回收，之后打开活动重新创建，按钮显示的播放字样（本应显示暂停字样）
            playButton.setText("暂停");
        }
    }

    public void onClick(View v) {//按钮的点击事件
        switch (v.getId()){
            case R.id.play_button://播放/暂停按钮
                if (MyService.isPause){//当为暂停状态时
                    listener.startMusic();
                }
                else {//当为播放状态时
                    listener.pauseMusic();
                }
                break;
            case R.id.last_button://下一曲按钮
                listener.lastMusic();
                break;
            case R.id.next_button://上一曲按钮
                listener.nextMusic();
                break;
        }
    }

    class activityListener{

        public void startMusic(){
            Intent intent=new Intent("android.music.service");//通过发送广播，通知服务让MediaPlayer播放歌曲
            intent.putExtra("Type",MyService.PLAY_MUSIC);
            sendBroadcast(intent);
            playButton.setText("暂停");
        }

        public void pauseMusic(){
            Intent intent=new Intent("android.music.service");//通过发送广播，通知服务让MediaPlayer暂停播放
            intent.putExtra("Type",MyService.PAUSE_MUSIC);
            sendBroadcast(intent);
            playButton.setText("播放");
        }

        public void lastMusic(){
            Intent intent=new Intent("android.music.service");//通过发送广播，通知服务让MediaPlayer跳转至下一曲
            intent.putExtra("Type",MyService.LAST_MUSIC);
            sendBroadcast(intent);
        }

        public void nextMusic(){
            Intent intent1=new Intent("android.music.service");//通过发送广播，通知服务让MediaPlayer跳转至上一曲
            intent1.putExtra("Type",MyService.NEXT_MUSIC);
            sendBroadcast(intent1);
        }

        public void seekToMusic(){
            Intent changeIntent=new Intent("android.music.service");//通过发送广播，通知服务让MediaPlayer跳转至指定的歌曲进度
            changeIntent.putExtra("Type",MyService.SEEKTO_MUSIC);
            changeIntent.putExtra("Seek",seekBar.getProgress());
            sendBroadcast(changeIntent);
        }

        public void changeAsList(){
            Intent intent=new Intent("android.music.service");//通过发送广播，通知服务跳转至点击的指定歌曲
            intent.putExtra("Type",MyService.CHANGE_AS_LIST);
            sendBroadcast(intent);
        }
    }

    class changeReceiver extends BroadcastReceiver{//广播接收器

        public void onReceive(Context context, Intent intent) {
            int type=intent.getIntExtra("Type",0);//获取广播中Intent所携带的指令
            switch (type){
                case UPDATE_SEEKBAR://更新SeekBar指令
                    current=intent.getIntExtra("Current",0);//获取Intent中所携带的总/当前时间值
                    total=intent.getIntExtra("Total",1);
                    currentTime.setText(getTimeStr(current));//更新显示时间的TextView
                    totalTime.setText(getTimeStr(total));
                    seekBar.setProgress((int)((current*1.0/total)*100));//更新SeekBar进度条
                    musicNameText.setText(getMusicName());
                    Log.d("AAA",getMusicName());
                    break;
                case UPDATE_LISTVIEW://刷新列表指令
                    adapter.notifyDataSetChanged();
                    break;
            }
        }
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {//申请权限的回调方法
        switch (requestCode){
            case 1:
                if (grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    initList();
                    adapter.notifyDataSetChanged();
                    Intent intent=new Intent(this,MyService.class);
                    startService(intent);//开启服务（获取权限后才能开启服务，因为会将活动从SD卡读取的结果复制到服务的变量中）
                }
                else{
                    Toast.makeText(this,"未获取对SD卡操作的权限",Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
        }
    }

    public static String getTimeStr(int time){//获取时间格式
        int min=(int)((time/1000.0)/60);
        int sec=(int)((time/1000.0)%60);
        String minStr,secStr;
        if (min<10){
            minStr="0"+Integer.toString(min);
        }
        else {
            minStr=Integer.toString(min);
        }
        if (sec<10){
            secStr="0"+Integer.toString(sec);
        }
        else {
            secStr=Integer.toString(sec);
        }
        return minStr+":"+secStr;
    }

    public static String getMusicName(){//获取当前歌曲名
        String str=FirstActivity.itemList.get(MyService.i%musicFileArray.length).musicName;
        String musicNameStr=str.substring(0,str.indexOf("."));
        return musicNameStr;
    }

    public void initList(){//初始化数据链表
        //File file=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        File file=new File(Environment.getExternalStorageDirectory(),"TestMusic");//获取歌曲文件的虚拟文件File
        musicFileArray=file.listFiles();//获取对应文件中的所有子文件：File的listFiles()，返回值为File[]
        mediaMetadataRetriever=new MediaMetadataRetriever();
        for (int j=0;j<musicFileArray.length;j++){
            String path=musicFileArray[j].getPath();
            mediaMetadataRetriever.setDataSource(path);//MediaMetadataRetriever获取多媒体文件的信息（指定文件的路径）
            String author = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);//获取指定音频文件的歌曲作者：MediaMetadataRetriever的extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            if (author!=null){
                author="艺术家："+author;
            }
            else {
                author="艺术家：未知";
            }

            byte[]bytes=mediaMetadataRetriever.getEmbeddedPicture();//获取指定音频文件的默认位图的字节数组：MediaMetadataRetriever的getEmbeddedPicture()
            Bitmap bitmap=null;
            if (bytes!=null){
                bitmap= BitmapFactory.decodeByteArray(bytes,0,bytes.length);
            }

            int color;
            if (j==MyService.i%musicFileArray.length) {
                color=Color.parseColor("#FFD700");
            }
            else {
                color=Color.parseColor("#FFFFFF");
            }
            /*
            BufferedReader reader=null;
            StringBuilder builder=new StringBuilder();
            String line=null;
            try
            {
                FileInputStream fileInputStream=new FileInputStream(musicFileArray[j]);
                reader=new BufferedReader(new InputStreamReader(fileInputStream,"GBK"));
                while ((line=reader.readLine())!=null){
                    builder.append(line);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            finally
            {
                try
                {
                    if (reader!=null){
                        reader.close();
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }

            String data=builder.toString();
            BufferedWriter writer=null;
            try
            {
                FileOutputStream outputStream=openFileOutput("data",MODE_PRIVATE);
                writer=new BufferedWriter(new OutputStreamWriter(outputStream,"Unicode"));
                writer.write(data);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            finally
            {
                try
                {
                    if (writer!=null)
                    {
                        writer.close();
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
            */
            String musicName=path.substring(path.lastIndexOf("/")+1);
            MusicItem item=new MusicItem(musicName,color,author,bitmap);
            itemList.add(item);
        }
    }

    public void onBackPressed() {//Back键退出程序
        if (System.currentTimeMillis()-exitTime>2000){//连续按下两次Back键退出
            Toast.makeText(FirstActivity.this,"再次点击退出程序",Toast.LENGTH_SHORT).show();
            exitTime=System.currentTimeMillis();
        }
        else {
            MyService.isPause=true;
            try
            {
                Thread.sleep(50);//等待服务中播放歌曲的子线程停止，避免消除前台服务的通知时，子线程仍在运行导致再次更新通知
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            Intent intent=new Intent(this,MyService.class);
            stopService(intent);//停止服务
            finish();
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        itemList.clear();//清空数据链表
        unregisterReceiver(receiver);
    }
}
