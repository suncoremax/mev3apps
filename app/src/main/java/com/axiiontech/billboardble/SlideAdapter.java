package com.axiiontech.billboardble;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SlideAdapter extends RecyclerView.Adapter<SlideAdapter.VH> {

    public static class Slide {
        public String id;
        public String txt;
        public int durMs;
        public boolean hasImage;

        public Slide(String id, String txt, int durMs, boolean hasImage) {
            this.id = id;
            this.txt = txt;
            this.durMs = durMs;
            this.hasImage = hasImage;
        }
    }

    public interface OnDeleteClick {
        void onDelete(Slide slide);
    }

    private final List<Slide> slides = new ArrayList<>();
    private final OnDeleteClick onDeleteClick;

    public SlideAdapter(OnDeleteClick onDeleteClick) {
        this.onDeleteClick = onDeleteClick;
    }

    public void setSlides(List<Slide> newSlides) {
        slides.clear();
        slides.addAll(newSlides);
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return slides.isEmpty();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_slide, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Slide s = slides.get(position);
        holder.icon.setText(s.hasImage ? "🖼" : "🅣");
        holder.text.setText(s.hasImage
                ? holder.itemView.getContext().getString(R.string.slide_image)
                : (s.txt == null || s.txt.isEmpty() ? "(খালি লেখা)" : s.txt));
        int seconds = Math.round(s.durMs / 1000f);
        holder.meta.setText(seconds + " সেকেন্ড");
        holder.delete.setOnClickListener(v -> onDeleteClick.onDelete(s));
    }

    @Override
    public int getItemCount() {
        return slides.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView icon, text, meta;
        Button delete;

        VH(View v) {
            super(v);
            icon = v.findViewById(R.id.rowIcon);
            text = v.findViewById(R.id.rowText);
            meta = v.findViewById(R.id.rowMeta);
            delete = v.findViewById(R.id.rowDelete);
        }
    }
}
