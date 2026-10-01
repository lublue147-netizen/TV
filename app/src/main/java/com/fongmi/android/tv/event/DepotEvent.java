package com.fongmi.android.tv.event;

import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

public record DepotEvent(Config config, List<Depot> items) {

    public static void post(Config config, List<Depot> items) {
        EventBus.getDefault().post(new DepotEvent(config, items));
    }
}
