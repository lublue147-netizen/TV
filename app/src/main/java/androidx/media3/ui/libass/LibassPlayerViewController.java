package androidx.media3.ui.libass;

import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.libass.LibassPlaybackSession;
import androidx.media3.exoplayer.libass.LibassSubtitleController;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;

import java.util.function.Consumer;

public class LibassPlayerViewController {

    public LibassPlayerViewController(Player player, LibassPlaybackSession session, LibassSubtitleController controller) {
    }

    public void bind(PlayerView playerView) {
    }

    public void close() {
    }

    public void setStyleOverride(@Nullable CaptionStyleCompat style, @Nullable String fontFamily) {
    }

    public void setSecondarySubtitleViewConfigurator(Consumer<SubtitleView> configurator) {
    }
}
