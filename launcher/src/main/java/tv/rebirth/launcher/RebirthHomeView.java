package tv.rebirth.launcher;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.View;

public final class RebirthHomeView extends View {

    public interface ActionListener {
        void onTileClicked(LauncherModel.Tile tile);
        void onTileLongClicked(LauncherModel.Tile tile);
        void onDirectChannelNumber(int lcn);
    }

    private static final int[] APP_RES_IDS = new int[] {
        R.drawable.ic_app_youtube,
        R.drawable.ic_app_prime,
        R.drawable.ic_app_dazn,
        R.drawable.ic_app_disney,
        R.drawable.ic_app_raiplay,
        0
    };

    private static final int[] CH_RES_IDS = new int[] {
        R.drawable.ic_ch_rai1,
        R.drawable.ic_ch_rai3,
        R.drawable.ic_ch_la7,
        R.drawable.ic_ch_focus,
        R.drawable.ic_ch_raistoria,
        R.drawable.ic_ch_raiscuola,
        R.drawable.ic_ch_raiyoyo,
        R.drawable.ic_ch_boing,
        R.drawable.ic_ch_cartoonito
    };

    private final LauncherModel model;
    private final ActionListener listener;

    private final Paint bgPaint = new Paint();
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tvIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF cardRect = new RectF();
    private final RectF focusRect = new RectF();
    private final RectF badgeRect = new RectF();
    private final Path tvAntennaPath = new Path();
    private final BitmapFactory.Options bitmapOpts = new BitmapFactory.Options();

    private final LinearGradient[] appGradients = new LinearGradient[6];
    private final RectF[] appIconRects = new RectF[6];
    private final RectF[] channelIconRects = new RectF[9];
    private Bitmap[] appBitmaps;
    private Bitmap[] channelBitmaps;

    private String clockText = "20:15";
    private String freeRamText = "418 MB free";
    private long lastFreeRamMb = -1L;
    private boolean longPressHandled = false;

    private final Runnable commitDigitsRunnable = new Runnable() {
        @Override
        public void run() {
            int lcn = model.consumeDigitBuffer();
            invalidate();
            if (lcn > 0 && listener != null) {
                listener.onDirectChannelNumber(lcn);
            }
        }
    };

    public RebirthHomeView(Context context, LauncherModel model, ActionListener listener) {
        super(context);
        this.model = model;
        this.listener = listener;
        setFocusable(true);
        setFocusableInTouchMode(true);

        bgPaint.setColor(0xFF070A10);
        borderPaint.setStyle(Paint.Style.STROKE);

        Typeface boldTf = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        Typeface mediumTf = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

        titlePaint.setTypeface(boldTf);
        titlePaint.setTextAlign(Paint.Align.CENTER);

        subPaint.setTypeface(mediumTf);
        subPaint.setTextAlign(Paint.Align.CENTER);

        headerPaint.setTypeface(boldTf);

        tvIconPaint.setStyle(Paint.Style.STROKE);
        tvIconPaint.setStrokeWidth(2.4f);
        tvIconPaint.setStrokeCap(Paint.Cap.ROUND);
        tvIconPaint.setColor(0xFFFFFFFF);

        for (int i = 0; i < appIconRects.length; i++) {
            appIconRects[i] = new RectF();
        }
        for (int i = 0; i < channelIconRects.length; i++) {
            channelIconRects[i] = new RectF();
        }
        bitmapOpts.inScaled = false;

        initGradients();
        ensureBitmapsLoaded();
    }

    private void initGradients() {
        LauncherModel.Tile[] apps = model.getRowTiles(LauncherModel.ROW_APPS);
        for (int i = 0; i < apps.length; i++) {
            float x = 64f + i * 195f;
            float y = 158f;
            appGradients[i] = new LinearGradient(
                x, y, x + 178f, y + 126f,
                apps[i].bgColorStart, apps[i].bgColorEnd, Shader.TileMode.CLAMP
            );
        }
    }

    private Bitmap decodeIcon(Resources res, int resId) {
        bitmapOpts.inPreferredConfig = Bitmap.Config.HARDWARE;
        Bitmap bmp = BitmapFactory.decodeResource(res, resId, bitmapOpts);
        if (bmp == null) {
            bitmapOpts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            bmp = BitmapFactory.decodeResource(res, resId, bitmapOpts);
        }
        return bmp;
    }

    public void ensureBitmapsLoaded() {
        if (appBitmaps != null && channelBitmaps != null) return;
        Resources res = getResources();

        if (appBitmaps == null) {
            appBitmaps = new Bitmap[APP_RES_IDS.length];
        }
        for (int i = 0; i < APP_RES_IDS.length; i++) {
            if (APP_RES_IDS[i] != 0) {
                Bitmap bmp = decodeIcon(res, APP_RES_IDS[i]);
                appBitmaps[i] = bmp;
                if (bmp != null) {
                    float cx = (64f + i * 195f) + 89f;
                    float bw = bmp.getWidth();
                    float bh = bmp.getHeight();
                    float bx = cx - bw * 0.5f;
                    float by = 206f - bh * 0.5f;
                    appIconRects[i].set(bx, by, bx + bw, by + bh);
                }
            }
        }

        if (channelBitmaps == null) {
            channelBitmaps = new Bitmap[CH_RES_IDS.length];
        }
        for (int i = 0; i < CH_RES_IDS.length; i++) {
            Bitmap bmp = decodeIcon(res, CH_RES_IDS[i]);
            channelBitmaps[i] = bmp;
            if (bmp != null) {
                float cx = (64f + i * 129.5f) + 58f;
                float bw = bmp.getWidth();
                float bh = bmp.getHeight();
                float bx = cx - bw * 0.5f;
                float by = 418f - bh * 0.5f;
                channelIconRects[i].set(bx, by, bx + bw, by + bh);
            }
        }
    }

    public void releaseBitmaps() {
        if (appBitmaps != null) {
            for (int i = 0; i < appBitmaps.length; i++) {
                if (appBitmaps[i] != null && !appBitmaps[i].isRecycled()) {
                    appBitmaps[i].recycle();
                }
                appBitmaps[i] = null;
            }
            appBitmaps = null;
        }
        if (channelBitmaps != null) {
            for (int i = 0; i < channelBitmaps.length; i++) {
                if (channelBitmaps[i] != null && !channelBitmaps[i].isRecycled()) {
                    channelBitmaps[i].recycle();
                }
                channelBitmaps[i] = null;
            }
            channelBitmaps = null;
        }
    }

    public void updateStatus(String clock, long freeRamMb) {
        boolean changed = false;
        if (clock != null && !clock.equals(this.clockText)) {
            this.clockText = clock;
            changed = true;
        }
        if (freeRamMb != this.lastFreeRamMb) {
            this.lastFreeRamMb = freeRamMb;
            this.freeRamText = freeRamMb + " MB free";
            changed = true;
        }
        if (changed) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        ensureBitmapsLoaded();

        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0f, 0f, w, h, bgPaint);

        canvas.save();
        if (w != 1280 || h != 720) {
            canvas.scale(w / 1280f, h / 720f);
        }

        drawTopHeader(canvas);
        drawAppsRow(canvas);
        drawChannelsRow(canvas);
        drawFooter(canvas);
        drawDigitOverlayIfActive(canvas);

        canvas.restore();
    }

    private void drawTopHeader(Canvas canvas) {
        headerPaint.setTextAlign(Paint.Align.LEFT);
        headerPaint.setTextSize(18f);
        headerPaint.setColor(0xFFF8FAFC);
        canvas.drawText("REBIRTH", 64f, 54f, headerPaint);

        subPaint.setTextAlign(Paint.Align.LEFT);
        subPaint.setTextSize(14f);
        subPaint.setColor(0xFF64748B);
        canvas.drawText("JVC 32\" (720p)", 158f, 54f, subPaint);

        // Micro Top Strip (5 pills)
        LauncherModel.Tile[] topTiles = model.getRowTiles(LauncherModel.ROW_TOP_STRIP);
        boolean topRowFocused = (model.getFocusedRow() == LauncherModel.ROW_TOP_STRIP);
        float x = 536f;
        for (int i = 0; i < topTiles.length; i++) {
            LauncherModel.Tile t = topTiles[i];
            float pillW = (i == 3) ? 84f : 74f;
            boolean focused = topRowFocused && (model.getFocusedCol() == i);

            cardRect.set(x, 31f, x + pillW, 59f);
            cardPaint.setShader(null);
            cardPaint.setColor(t.bgColorStart);
            cardPaint.setAlpha(focused ? 255 : 175);
            canvas.drawRoundRect(cardRect, 6f, 6f, cardPaint);

            borderPaint.setStrokeWidth(focused ? 2.5f : 1f);
            borderPaint.setColor(focused ? 0xFFF8FAFC : 0xFF1E293B);
            canvas.drawRoundRect(cardRect, 6f, 6f, borderPaint);

            titlePaint.setTextAlign(Paint.Align.CENTER);
            titlePaint.setTextSize(11f);
            titlePaint.setColor(focused ? 0xFFFFFFFF : (i == 3 ? 0xFF4ADE80 : 0xFFCBD5E1));
            canvas.drawText(t.title, x + pillW * 0.5f, 49f, titlePaint);

            x += pillW + 6f;
        }
        cardPaint.setAlpha(255);

        // RAM Pill
        cardRect.set(954f, 30f, 1112f, 60f);
        cardPaint.setColor(0xFF0F172A);
        canvas.drawRoundRect(cardRect, 15f, 15f, cardPaint);
        borderPaint.setStrokeWidth(1f);
        borderPaint.setColor(0xFF1E293B);
        canvas.drawRoundRect(cardRect, 15f, 15f, borderPaint);

        badgePaint.setColor(0xFF22C55E);
        canvas.drawCircle(972f, 45f, 4f, badgePaint);

        titlePaint.setTextAlign(Paint.Align.LEFT);
        titlePaint.setTextSize(12f);
        titlePaint.setColor(0xFF94A3B8);
        canvas.drawText(freeRamText, 984f, 49f, titlePaint);

        // Clock
        headerPaint.setTextAlign(Paint.Align.RIGHT);
        headerPaint.setTextSize(22f);
        headerPaint.setColor(0xFFF8FAFC);
        canvas.drawText(clockText, 1216f, 53f, headerPaint);
    }

    private void drawAppsRow(Canvas canvas) {
        headerPaint.setTextAlign(Paint.Align.LEFT);
        headerPaint.setTextSize(12f);
        headerPaint.setColor(0xFF64748B);
        canvas.drawText("STREAMING & TV", 64f, 138f, headerPaint);

        LauncherModel.Tile[] apps = model.getRowTiles(LauncherModel.ROW_APPS);
        boolean rowFocused = (model.getFocusedRow() == LauncherModel.ROW_APPS);

        for (int i = 0; i < apps.length; i++) {
            LauncherModel.Tile t = apps[i];
            boolean focused = rowFocused && (model.getFocusedCol() == i);

            float x = 64f + i * 195f;
            float y = 158f;
            float w = (i == 5) ? 177f : 178f;
            float h = 126f;

            if (focused) {
                focusRect.set(x - 6f, y - 6f, x + w + 6f, y + h + 6f);
                borderPaint.setStrokeWidth(3.5f);
                borderPaint.setColor(0xFFF8FAFC);
                canvas.drawRoundRect(focusRect, 16f, 16f, borderPaint);
            }

            cardRect.set(x, y, x + w, y + h);
            cardPaint.setShader(appGradients[i]);
            canvas.drawRoundRect(cardRect, 13f, 13f, cardPaint);
            cardPaint.setShader(null);

            borderPaint.setStrokeWidth(1.2f);
            borderPaint.setColor(focused ? t.accentColor : 0xFF1E293B);
            canvas.drawRoundRect(cardRect, 13f, 13f, borderPaint);

            float cx = x + w * 0.5f;
            if (i < 5 && appBitmaps != null && appBitmaps[i] != null) {
                canvas.drawBitmap(appBitmaps[i], null, appIconRects[i], bitmapPaint);
            } else if (i == 5) {
                // Vector Live TV icon
                cardRect.set(cx - 18f, y + 34f, cx + 18f, y + 56f);
                cardPaint.setColor(0xFF0284C7);
                canvas.drawRoundRect(cardRect, 4f, 4f, cardPaint);
                canvas.drawRoundRect(cardRect, 4f, 4f, tvIconPaint);
                tvAntennaPath.reset();
                tvAntennaPath.moveTo(cx - 8f, y + 23f);
                tvAntennaPath.lineTo(cx, y + 33f);
                tvAntennaPath.lineTo(cx + 8f, y + 23f);
                canvas.drawPath(tvAntennaPath, tvIconPaint);
                canvas.drawLine(cx - 8f, y + 61f, cx + 8f, y + 61f, tvIconPaint);

                titlePaint.setTextAlign(Paint.Align.CENTER);
                titlePaint.setTextSize(20f);
                titlePaint.setColor(0xFFFFFFFF);
                canvas.drawText("TV", cx, y + 92f, titlePaint);
            }

            subPaint.setTextAlign(Paint.Align.CENTER);
            subPaint.setTextSize(12f);
            subPaint.setColor(focused ? 0xFFF8FAFC : (i == 5 ? 0xFF7DD3FC : 0xFF94A3B8));
            canvas.drawText(t.subtitle, cx, y + 108f, subPaint);
        }
    }

    private void drawChannelsRow(Canvas canvas) {
        headerPaint.setTextAlign(Paint.Align.LEFT);
        headerPaint.setTextSize(12f);
        headerPaint.setColor(0xFF64748B);
        canvas.drawText("CANALI RAPIDI TV (INFO  -  SCIENZA  -  CARTONI)", 64f, 346f, headerPaint);

        LauncherModel.Tile[] chs = model.getRowTiles(LauncherModel.ROW_CHANNELS);
        boolean rowFocused = (model.getFocusedRow() == LauncherModel.ROW_CHANNELS);

        for (int i = 0; i < chs.length; i++) {
            LauncherModel.Tile t = chs[i];
            boolean focused = rowFocused && (model.getFocusedCol() == i);

            float x = 64f + i * 129.5f;
            float y = 364f;
            float w = 116f;
            float h = 104f;

            if (focused) {
                focusRect.set(x - 5f, y - 5f, x + w + 5f, y + h + 5f);
                borderPaint.setStrokeWidth(3.2f);
                borderPaint.setColor(0xFFF8FAFC);
                canvas.drawRoundRect(focusRect, 15f, 15f, borderPaint);
            }

            cardRect.set(x, y, x + w, y + h);
            cardPaint.setShader(null);
            cardPaint.setColor(t.bgColorStart);
            canvas.drawRoundRect(cardRect, 12f, 12f, cardPaint);

            borderPaint.setStrokeWidth(1.3f);
            borderPaint.setColor(focused ? t.accentColor : 0xFF1E293B);
            canvas.drawRoundRect(cardRect, 12f, 12f, borderPaint);

            // LCN Number Badge
            float badgeW = (t.lcn >= 10) ? 28f : 24f;
            badgeRect.set(x + 10f, y + 10f, x + 10f + badgeW, y + 28f);
            badgePaint.setColor(t.accentColor);
            canvas.drawRoundRect(badgeRect, 4f, 4f, badgePaint);

            titlePaint.setTextAlign(Paint.Align.CENTER);
            titlePaint.setTextSize(11f);
            titlePaint.setColor(t.lcn == 46 ? 0xFF09090B : 0xFFFFFFFF);
            canvas.drawText(t.lcnBadgeText, badgeRect.centerX(), y + 23f, titlePaint);

            float cx = x + w * 0.5f;
            if (channelBitmaps != null && channelBitmaps[i] != null) {
                canvas.drawBitmap(channelBitmaps[i], null, channelIconRects[i], bitmapPaint);
            }

            subPaint.setTextAlign(Paint.Align.CENTER);
            subPaint.setTextSize(10.5f);
            subPaint.setColor(focused ? 0xFFF8FAFC : 0xFF94A3B8);
            canvas.drawText(t.subtitle, cx, y + 91f, subPaint);
        }
    }

    private void drawFooter(Canvas canvas) {
        borderPaint.setStrokeWidth(1f);
        borderPaint.setColor(0xFF1E293B);
        canvas.drawLine(64f, 628f, 1216f, 628f, borderPaint);

        subPaint.setTextAlign(Paint.Align.LEFT);
        subPaint.setTextSize(13f);
        subPaint.setColor(0xFF64748B);
        canvas.drawText("[OK] Apri App o Canale   |   [1..99] Numero canale diretto   |   [Tieni OK] Force-Stop App & Info", 64f, 660f, subPaint);

        subPaint.setTextAlign(Paint.Align.RIGHT);
        subPaint.setColor(0xFF475569);
        canvas.drawText("1280x720 Native  |  Zero-Bloat Engine", 1216f, 660f, subPaint);
    }

    private void drawDigitOverlayIfActive(Canvas canvas) {
        int digits = model.getDigitBuffer();
        if (digits <= 0) return;

        cardRect.set(520f, 40f, 760f, 110f);
        cardPaint.setColor(0xEE0F172A);
        canvas.drawRoundRect(cardRect, 14f, 14f, cardPaint);
        borderPaint.setStrokeWidth(2.5f);
        borderPaint.setColor(0xFF38BDF8);
        canvas.drawRoundRect(cardRect, 14f, 14f, borderPaint);

        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setTextSize(34f);
        titlePaint.setColor(0xFFFFFFFF);
        canvas.drawText(model.getDigitOverlayText(), 640f, 86f, titlePaint);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            int digit = keyCode - KeyEvent.KEYCODE_0;
            removeCallbacks(commitDigitsRunnable);
            model.appendDigit(digit);
            invalidate();
            postDelayed(commitDigitsRunnable, 1200L);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (model.moveFocus(LauncherModel.DIR_LEFT)) invalidate();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (model.moveFocus(LauncherModel.DIR_RIGHT)) invalidate();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (model.moveFocus(LauncherModel.DIR_UP)) invalidate();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (model.moveFocus(LauncherModel.DIR_DOWN)) invalidate();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (model.getDigitBuffer() > 0) {
                removeCallbacks(commitDigitsRunnable);
                commitDigitsRunnable.run();
                return true;
            }
            if (event.getRepeatCount() == 0) {
                longPressHandled = false;
            } else if (!longPressHandled && event.getEventTime() - event.getDownTime() >= 300L) {
                longPressHandled = true;
                if (listener != null) {
                    listener.onTileLongClicked(model.getFocusedTile());
                }
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (listener != null) {
                listener.onTileLongClicked(model.getFocusedTile());
            }
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (!longPressHandled && listener != null) {
                listener.onTileClicked(model.getFocusedTile());
            }
            longPressHandled = false;
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }
}
