package com.example.musicplayerdemo;

import android.graphics.Bitmap;

public class MusicItem {//数据实体类
    String musicName;//子项显示的歌曲名
    int color;//子项的颜色
    String author;//子项中歌曲的艺术家
    Bitmap image;//子项中歌曲的默认图片
    public MusicItem(String musicName,int color,String author,Bitmap image){
        this.musicName=musicName;
        this.color=color;
        this.author=author;
        this.image=image;
    }
}
