package androidx.media3.exoplayer.trackselection;

import android.content.Context;
import androidx.annotation.NonNull;

public class SecondaryTextTrackSelector {

    public static class Factory implements TrackSelector.Factory {
        private final TrackSelector.Factory delegate;

        public Factory(TrackSelector.Factory delegate) {
            this.delegate = delegate;
        }

        @NonNull
        @Override
        public TrackSelector createTrackSelector(@NonNull Context context) {
            return delegate.createTrackSelector(context);
        }
    }
}
