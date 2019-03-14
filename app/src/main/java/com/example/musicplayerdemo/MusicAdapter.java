package com.example.musicplayerdemo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class MusicAdapter extends ArrayAdapter<MusicItem> {//滑动列表适配器

    public MusicAdapter( Context context, int resource,  List<MusicItem> itemList) {
        super(context, resource, itemList);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        MusicItem item=getItem(position);
        ViewHolder viewHolder=null;
        if (convertView==null){
            viewHolder=new ViewHolder();
            convertView= LayoutInflater.from(parent.getContext()).inflate(R.layout.item_layout,parent,false);
            viewHolder.musicImage=convertView.findViewById(R.id.music_image);
            viewHolder.musicName=convertView.findViewById(R.id.music_name);
            viewHolder.musicAuthor=convertView.findViewById(R.id.music_author);
            convertView.setTag(viewHolder);
        }
        else {
            viewHolder=(ViewHolder)convertView.getTag();
        }
        viewHolder.musicImage.setImageBitmap(item.image);//显示歌曲默认封面位图
        viewHolder.musicName.setText(item.musicName);//显示歌曲文件名
        viewHolder.musicName.setBackgroundColor(item.color);//显示背景颜色
        viewHolder.musicAuthor.setText(item.author);//显示歌曲艺术家
        return convertView;
    }

    class ViewHolder{
        ImageView musicImage;
        TextView musicName;
        TextView musicAuthor;
    }
}
