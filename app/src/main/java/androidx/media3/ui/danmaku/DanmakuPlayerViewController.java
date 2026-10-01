package androidx.media3.ui.danmaku;

import android.net.Uri;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;
import okhttp3.OkHttpClient;

/** Coordinates danmaku rendering with a {@link PlayerView}. */
@MainThread
@UnstableApi
public final class DanmakuPlayerViewController {

  private final DanmakuController controller;
  @Nullable private DanmakuView danmakuView;
  @Nullable private PlayerView playerView;
  private boolean playerBound;

  public DanmakuPlayerViewController() {
    controller = new DanmakuController();
  }

  @Nullable
  protected FrameLayout getOverlayFrameLayout(@NonNull PlayerView playerView) {
    return playerView.getOverlayFrameLayout();
  }

  public void bind(@NonNull PlayerView playerView) {
    this.playerView = playerView;
    Player player = playerView.getPlayer();
    FrameLayout overlay = getOverlayFrameLayout(playerView);
    if (danmakuView == null) {
      DanmakuView view = new DanmakuView(playerView.getContext());
      FrameLayout.LayoutParams params =
          new FrameLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
      if (overlay != null) {
        overlay.addView(view, params);
      } else {
        playerView.addView(view, params);
      }
      danmakuView = view;
    }
    controller.setView(danmakuView);
    bindPlayer(player);
  }

  private void bindPlayer(@Nullable Player player) {
    if (playerBound) {
      return;
    }
    playerBound = true;
    controller.setPlayer(player);
  }

  public void setOkHttpClient(@Nullable OkHttpClient client) {
    controller.setOkHttpClient(client);
  }

  public void setEnabled(boolean enabled) {
    controller.setEnabled(enabled);
  }

  public void setConfig(@NonNull DanmakuConfig config) {
    controller.setConfig(config);
  }

  public void setDataSource(@Nullable Uri uri) {
    PlayerView view = playerView;
    if (!playerBound && view != null) {
      bindPlayer(view.getPlayer());
    }
    controller.setDataSource(uri);
  }

  public void sendNow(@NonNull String text) {
    controller.sendNow(text);
  }

  public void close() {
    controller.setPlayer(null);
    controller.setView(null);
    controller.release();
    playerView = null;
    danmakuView = null;
    playerBound = false;
  }
}
