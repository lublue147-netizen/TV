package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.databinding.AdapterDepotBinding;

import java.util.ArrayList;
import java.util.List;

public class DepotAdapter extends RecyclerView.Adapter<DepotAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Depot> mItems;

    public DepotAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {
        void onItemClick(Depot item);
    }

    public DepotAdapter addAll(List<Depot> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
        return this;
    }

    public int getPosition() {
        for (int i = 0; i < mItems.size(); i++) {
            if (mItems.get(i).getUrl().equals(VodConfig.getUrl())) return i;
        }
        return 0;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterDepotBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Depot item = mItems.get(position);
        boolean active = item.getUrl().equals(VodConfig.getUrl());
        holder.binding.text.setText(active ? "✓ " + item.getName() : item.getName());
        holder.binding.text.setSelected(active);
        holder.binding.text.setOnClickListener(v -> listener.onItemClick(item));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterDepotBinding binding;

        public ViewHolder(@NonNull AdapterDepotBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
