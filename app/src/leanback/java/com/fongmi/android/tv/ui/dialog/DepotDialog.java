package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.databinding.DialogHistoryBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.ui.adapter.DepotAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DepotDialog extends BaseAlertDialog implements DepotAdapter.OnClickListener {

    private DialogHistoryBinding binding;
    private DepotAdapter adapter;

    public static DepotDialog create() {
        return new DepotDialog();
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogHistoryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(R.string.dialog_depot_title).setView(getBinding().getRoot());
    }

    private int getCount() {
        return adapter.getItemCount() > 20 ? 2 : 1;
    }

    @Override
    protected void initView() {
        adapter = new DepotAdapter(this);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(false);
        int spanCount = getCount();
        binding.recycler.setLayoutManager(new GridLayoutManager(requireContext(), spanCount));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(spanCount, 16));
        binding.recycler.setAdapter(adapter.addAll(VodConfig.get().getDepots()));
        binding.recycler.scrollToPosition(adapter.getPosition());
    }

    @Override
    public void onItemClick(Depot item) {
        dismiss();
        Config config = Config.find(item.getUrl(), item.getName(), Config.VOD);
        if (requireActivity() instanceof ConfigListener cl) cl.setConfig(config);
        else VodConfig.load(config, new Callback());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0) dismiss();
        else setWidth(getCount() == 2 ? 0.6f : 0.4f);
    }
}
