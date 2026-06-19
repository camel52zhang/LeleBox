package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.palette.graphics.Palette;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ViewWallBinding;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.FileUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;

import pl.droidsonroids.gif.GifDrawable;

public class CustomWallView extends FrameLayout implements DefaultLifecycleObserver {

    private static final int[] WALL_PAPERS = {0, R.drawable.wallpaper_1, R.drawable.wallpaper_2, R.drawable.wallpaper_3, R.drawable.wallpaper_4};
    private static final int[] WALL_COLORS = {0, 0xFF40C090, 0xFF4870E0, 0xFF48B0C0, 0xFF404040};
    private static final int TYPE_RES = 0;
    private static final int TYPE_GIF = 1;
    private static final int TYPE_VIDEO = 2;
    private ViewWallBinding binding;
    private GifDrawable drawable;
    private PlayerView video;
    private ExoPlayer player;

    public CustomWallView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isInEditMode()) return;
        binding = ViewWallBinding.inflate(LayoutInflater.from(getContext()), this, true);
        ((ComponentActivity) getContext()).getLifecycle().addObserver(this);
        refresh();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() == ConfigEvent.Type.WALL) refresh();
    }

    private void refresh() {
        stop();
        load();
        theme();
    }

    private void stop() {
        if (player != null && player.isPlaying()) {
            player.stop();
            player.clearMediaItems();
        }
        if (video != null) {
            video.setPlayer(null);
            video.setVisibility(GONE);
        }
        if (drawable != null) {
            drawable.stop();
            drawable.recycle();
            drawable = null;
        }
    }

    private void load() {
        int wall = Setting.getWall();
        int type = Setting.getWallType();
        if (isBuiltIn(wall, type)) loadRes(WALL_PAPERS[wall]);
        else if (type == TYPE_VIDEO) loadVideo(FileUtil.getWall(wall));
        else if (type == TYPE_GIF) loadGif(FileUtil.getWall(wall));
        else loadImage();
    }

    private void theme() {
        int newColor = getWallColor();
        int oldColor = Setting.getWallColor();
        if (newColor == oldColor) return;
        Setting.putWallColor(newColor);
        if (Setting.getThemeColor() == 0) RefreshEvent.theme();
    }

    private void loadRes(int resId) {
        // 绘制柔和渐变背景 + Lelebox 水印
        int w = 1080;
        int h = 1920;
        Bitmap bg = createGradientBackground(w, h);
        // 叠加 Lelebox 水印
        Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.wallpaper_1);
        if (logo != null) {
            bg = overlayLogo(bg, logo, 0.15f);
            logo.recycle();
        }
        binding.image.setImageBitmap(bg);
    }

    private void loadImage() {
        Drawable cache = cache();
        if (cache != null) {
            Bitmap drawableToBitmap = drawableToBitmap(cache);
            if (drawableToBitmap != null) {
                Bitmap blurred = blurBitmap(drawableToBitmap, 25);
                Bitmap finalBitmap = dimBitmap(blurred, 0.45f);
                binding.image.setImageBitmap(finalBitmap);
                if (blurred != finalBitmap) blurred.recycle();
                if (finalBitmap != drawableToBitmap) drawableToBitmap.recycle();
            } else {
                binding.image.setImageDrawable(cache);
            }
        } else {
            loadRes(R.drawable.wallpaper_1);
        }
    }

    private void loadVideo(File file) {
        ensurePlayer();
        ensureVideoView();
        video.setPlayer(player);
        video.setVisibility(VISIBLE);
        binding.image.setImageDrawable(cache());
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
        player.prepare();
    }

    private void loadGif(File file) {
        drawable = gif(file);
        if (drawable != null) binding.image.setImageDrawable(drawable);
        else loadImage();
    }

    private Drawable cache() {
        File file = FileUtil.getWallCache();
        return file.exists() ? Drawable.createFromPath(file.getAbsolutePath()) : null;
    }

    private GifDrawable gif(File file) {
        try {
            return new GifDrawable(file);
        } catch (IOException e) {
            return null;
        }
    }

    private void ensurePlayer() {
        if (player != null) return;
        player = new ExoPlayer.Builder(getContext()).build();
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        player.setPlayWhenReady(true);
        player.mute();
    }

    private void ensureVideoView() {
        if (video != null) return;
        video = (PlayerView) LayoutInflater.from(getContext()).inflate(R.layout.view_wall_video, this, false);
        addView(video, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private boolean hasVideo() {
        return player != null && video != null && video.getVisibility() == VISIBLE && player.getMediaItemCount() > 0;
    }

    private int getWallColor() {
        int wall = Setting.getWall();
        int type = Setting.getWallType();
        if (isBuiltIn(wall, type)) return WALL_COLORS[wall];
        File file = FileUtil.getWallCache();
        return file.exists() ? paletteColor(file) : WALL_COLORS[1];
    }

    private int paletteColor(File file) {
        Bitmap bitmap = decodeBitmap(file);
        if (bitmap == null) return WALL_COLORS[1];
        Palette palette = Palette.from(bitmap).maximumColorCount(8).generate();
        bitmap.recycle();
        return swatchColor(palette);
    }

    private Bitmap decodeBitmap(File file) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 8;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
    }

    private int swatchColor(Palette palette) {
        Palette.Swatch swatch = palette.getVibrantSwatch();
        if (swatch == null) swatch = palette.getDominantSwatch();
        return swatch != null ? swatch.getRgb() : WALL_COLORS[1];
    }

    /**
     * 高斯模糊处理 - 使用 Stack Blur 算法（性能好，效果接近 RenderScript）
     * @param source 原始位图
     * @param radius 模糊半径（建议 8-25）
     * @return 模糊后的位图
     */
    private Bitmap blurBitmap(Bitmap source, float radius) {
        if (source == null) return null;
        int width = source.getWidth();
        int height = source.getHeight();
        // 缩小以大幅提升模糊性能
        int scale = Math.max(1, Math.min(width, height) / 300);
        int sw = width / scale;
        int sh = height / scale;
        Bitmap scaled = Bitmap.createScaledBitmap(source, sw, sh, true);
        // 使用 Stack Blur 算法（内部会创建可变副本，scaled 可能被复用或返回新对象）
        Bitmap blurred = stackBlur(scaled, (int) radius);
        // 缩回原始尺寸（放大时自然产生额外模糊感）
        Bitmap result = Bitmap.createScaledBitmap(blurred, width, height, true);
        // 回收中间位图（注意：stackBlur 可能返回 scaled 本身或其可变副本）
        if (blurred != result) blurred.recycle();
        if (scaled != blurred) scaled.recycle();
        return result;
    }

    /**
     * 创建柔和深色渐变背景 - 护眼配色
     * 使用深青蓝到深靛蓝的对角渐变，适合长时间观看
     */
    private Bitmap createGradientBackground(int w, int h) {
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        // 渐变色：深暖棕(#2D2418) → 深暖灰棕(#251E18) → 暖黑(#1A1612)
        // 中等亮度暖色调，有影院氛围不过暗，长时间观看舒适
        int[] colors = {
                        0xFF6B5B3E,  // 左上：暖棕色（提亮）
                        0xFF5C4D36,  // 中间：暖灰棕
                        0xFF4A3C2A   // 右下：深暖棕（不再纯黑）
        };
        float[] positions = {0.0f, 0.5f, 1.0f};
        android.graphics.LinearGradient gradient = new android.graphics.LinearGradient(
                0, 0, w, h, colors, positions, android.graphics.Shader.TileMode.CLAMP);
        Paint paint = new Paint();
        paint.setShader(gradient);
        canvas.drawRect(0, 0, w, h, paint);
        return bitmap;
    }

    /**
     * 在背景右下角半透明叠加 Lelebox Logo 水印
     */
    private Bitmap overlayLogo(Bitmap bg, Bitmap logo, float alpha) {
        int bw = bg.getWidth();
        int bh = bg.getHeight();
        // Logo 缩放到背景宽度的 25%
        int logoW = (int) (bw * 0.25);
        int logoH = (int) ((float) logo.getHeight() / logo.getWidth() * logoW);
        Bitmap scaledLogo = Bitmap.createScaledBitmap(logo, logoW, logoH, true);
        Canvas canvas = new Canvas(bg);
        Paint paint = new Paint();
        paint.setAlpha((int) (alpha * 255));
        // 放在右下角，留边距
        int margin = (int) (bw * 0.05);
        int left = bw - logoW - margin;
        int top = bh - logoH - margin;
        canvas.drawBitmap(scaledLogo, left, top, paint);
        scaledLogo.recycle();
        return bg;
    }

    /**
     * Stack Blur 算法 - O(n) 复杂度的高质量模糊实现
     * 参考: http://www.quasimondo.com/StackBlurForCanvas/StackBlurDemo.html
     */
    private Bitmap stackBlur(Bitmap source, int radius) {
        if (radius < 1) return source;
        int w = source.getWidth();
        int h = source.getHeight();
        // 创建可变副本，避免对 immutable bitmap 调用 setPixels 崩溃
        Bitmap result = source.isMutable() ? source : source.copy(Bitmap.Config.ARGB_8888, true);
        if (result == null) return source;
        int[] pixels = new int[w * h];
        result.getPixels(pixels, 0, w, 0, 0, w, h);
        int[] temp = new int[w * h];
        int divSum = (radius + 1) * (radius + 1);
        int[] dv = new int[256 * divSum];
        for (int i = 0; i < dv.length; i++) {
            dv[i] = i / divSum;
        }
        int radiusPlus1 = radius + 1;
        int[] sumTable = new int[256 * radiusPlus1];
        for (int i = 0; i < sumTable.length; i++) {
            sumTable[i] = i / radiusPlus1;
        }

        // 水平方向模糊
        for (int y = 0; y < h; y++) {
            int sumRed = 0, sumGreen = 0, sumBlue = 0, sumAlpha = 0;
            for (int i = -radius; i <= radius; i++) {
                int p = pixels[Math.max(0, Math.min(w - 1, i)) + y * w];
                sumAlpha += (p >> 24) & 0xff;
                sumRed += (p >> 16) & 0xff;
                sumGreen += (p >> 8) & 0xff;
                sumBlue += p & 0xff;
            }
            for (int x = 0; x < w; x++) {
                temp[x + y * w] = ((dv[sumAlpha] << 24) | (dv[sumRed] << 16) | (dv[sumGreen] << 8) | dv[sumBlue]);
                int left = x - radius;
                int right = x + radius + 1;
                if (left >= 0) {
                    int p = pixels[left + y * w];
                    sumAlpha -= (p >> 24) & 0xff;
                    sumRed -= (p >> 16) & 0xff;
                    sumGreen -= (p >> 8) & 0xff;
                    sumBlue -= p & 0xff;
                }
                if (right < w) {
                    int p = pixels[right + y * w];
                    sumAlpha += (p >> 24) & 0xff;
                    sumRed += (p >> 16) & 0xff;
                    sumGreen += (p >> 8) & 0xff;
                    sumBlue += p & 0xff;
                }
            }
        }

        // 垂直方向模糊
        for (int x = 0; x < w; x++) {
            int sumRed = 0, sumGreen = 0, sumBlue = 0, sumAlpha = 0;
            for (int i = -radius; i <= radius; i++) {
                int p = temp[x + Math.max(0, Math.min(h - 1, i)) * w];
                sumAlpha += (p >> 24) & 0xff;
                sumRed += (p >> 16) & 0xff;
                sumGreen += (p >> 8) & 0xff;
                sumBlue += p & 0xff;
            }
            for (int y = 0; y < h; y++) {
                pixels[x + y * w] = ((dv[sumAlpha] << 24) | (dv[sumRed] << 16) | (dv[sumGreen] << 8) | dv[sumBlue]);
                int top = y - radius;
                int bottom = y + radius + 1;
                if (top >= 0) {
                    int p = temp[x + top * w];
                    sumAlpha -= (p >> 24) & 0xff;
                    sumRed -= (p >> 16) & 0xff;
                    sumGreen -= (p >> 8) & 0xff;
                    sumBlue -= p & 0xff;
                }
                if (bottom < h) {
                    int p = temp[x + bottom * w];
                    sumAlpha += (p >> 24) & 0xff;
                    sumRed += (p >> 16) & 0xff;
                    sumGreen += (p >> 8) & 0xff;
                    sumBlue += p & 0xff;
                }
            }
        }

        result.setPixels(pixels, 0, w, 0, 0, w, h);
        return result;
    }

    /**
     * 给位图叠加暗色蒙版，降低亮度使前景内容更清晰
     * @param source 输入位图
     * @param alpha 蒙版不透明度（0=全透明，1=全黑，建议 0.35~0.55）
     * @return 处理后的位图
     */
    private Bitmap dimBitmap(Bitmap source, float alpha) {
        if (source == null) return null;
        int w = source.getWidth();
        int h = source.getHeight();
        Bitmap output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        // 绘制原图
        canvas.drawBitmap(source, 0, 0, null);
        // 叠加半透明黑色蒙版
        Paint dimPaint = new Paint();
        dimPaint.setColor(0xFF000000);
        dimPaint.setAlpha((int) (alpha * 255));
        canvas.drawRect(0, 0, w, h, dimPaint);
        return output;
    }

    /**
     * 将 Drawable 转换为 Bitmap
     */
    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) return null;
        if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
            return ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap().copy(Bitmap.Config.ARGB_8888, true);
        }
        int w = drawable.getIntrinsicWidth();
        int h = drawable.getIntrinsicHeight();
        if (w <= 0) w = 1080;
        if (h <= 0) h = 1920;
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, w, h);
        drawable.draw(canvas);
        return bitmap;
    }

    private boolean isBuiltIn(int wall, int type) {
        return type == TYPE_RES && wall > 0 && wall < WALL_PAPERS.length;
    }

    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        EventBus.getDefault().register(this);
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        if (drawable != null) drawable.start();
        if (!hasVideo()) return;
        video.setPlayer(player);
        player.play();
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        if (drawable != null) drawable.pause();
        if (!hasVideo()) return;
        video.setPlayer(null);
        player.pause();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        EventBus.getDefault().unregister(this);
        if (drawable != null) drawable.recycle();
        if (video != null) removeView(video);
        if (player != null) player.release();
        drawable = null;
        binding = null;
        player = null;
        video = null;
    }
}

