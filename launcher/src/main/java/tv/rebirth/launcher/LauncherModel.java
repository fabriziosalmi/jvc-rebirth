package tv.rebirth.launcher;

public final class LauncherModel {

    public static final int ROW_TOP_STRIP = 0;
    public static final int ROW_APPS = 1;
    public static final int ROW_CHANNELS = 2;

    public static final int DIR_LEFT = 0;
    public static final int DIR_RIGHT = 1;
    public static final int DIR_UP = 2;
    public static final int DIR_DOWN = 3;

    public static final int ACTION_LAUNCH_PACKAGE = 1;
    public static final int ACTION_SWITCH_HDMI = 2;
    public static final int ACTION_TUNE_LCN = 3;
    public static final int ACTION_FREE_RAM = 4;
    public static final int ACTION_OPEN_SETTINGS = 5;

    public static final String LIVE_TV_PKG = "com.mediatek.wwtv.tvcenter";
    public static final String LIVE_TV_ACTIVITY = "com.mediatek.wwtv.tvcenter.nav.TurnkeyUiMainActivity";
    public static final String ACTION_MTK_SELECT_CHANNEL = "com.mediatek.tv.selectchannel";
    public static final String ACTION_MTK_CHANNEL_UPDOWN = "com.mediatek.tv.channelupdown";
    public static final String ACTION_MTK_SELECT_SOURCE = "com.mediatek.select.source";
    public static final String EXTRA_MTK_SOURCE_NAME = "com.mediatek.select.sourcename";

    public static final String[] BACKGROUND_KILL_TARGETS = new String[] {
        "com.google.android.youtube.tv",
        "com.amazon.amazonvideo.livingroom",
        "com.dazn",
        "com.disney.disneyplus",
        "it.rainet.androidtv",
        "com.android.vending"
    };

    public static final class Tile {
        public final int row;
        public final int col;
        public final int actionType;
        public final String title;
        public final String subtitle;
        public final String targetPackageOrInput;
        public final int lcn;
        public final String lcnBadgeText;
        public final String passthroughUri;
        public final int bgColorStart;
        public final int bgColorEnd;
        public final int accentColor;

        public Tile(int row, int col, int actionType, String title, String subtitle,
                    String targetPackageOrInput, int lcn,
                    int bgColorStart, int bgColorEnd, int accentColor) {
            this.row = row;
            this.col = col;
            this.actionType = actionType;
            this.title = title;
            this.subtitle = subtitle;
            this.targetPackageOrInput = targetPackageOrInput;
            this.lcn = lcn;
            this.lcnBadgeText = (lcn > 0) ? String.valueOf(lcn) : "";
            this.passthroughUri = (actionType == ACTION_SWITCH_HDMI && targetPackageOrInput != null && targetPackageOrInput.length() > 0)
                ? buildPassthroughInputUri(targetPackageOrInput)
                : "";
            this.bgColorStart = bgColorStart;
            this.bgColorEnd = bgColorEnd;
            this.accentColor = accentColor;
        }
    }

    private final Tile[] topStripTiles;
    private final Tile[] appTiles;
    private final Tile[] channelTiles;

    private int focusedRow = ROW_APPS;
    private int focusedCol = 0;
    private int digitBuffer = 0;

    public LauncherModel() {
        this.topStripTiles = new Tile[] {
            new Tile(ROW_TOP_STRIP, 0, ACTION_SWITCH_HDMI, "HDMI 1", "HW4",
                "com.mediatek.tvinput/.hdmi.HDMIInputService/HW4", 0, 0xFF0F172A, 0xFF0F172A, 0xFF38BDF8),
            new Tile(ROW_TOP_STRIP, 1, ACTION_SWITCH_HDMI, "HDMI 2", "HW5",
                "com.mediatek.tvinput/.hdmi.HDMIInputService/HW5", 0, 0xFF0F172A, 0xFF0F172A, 0xFF38BDF8),
            new Tile(ROW_TOP_STRIP, 2, ACTION_SWITCH_HDMI, "HDMI 3", "HW6",
                "com.mediatek.tvinput/.hdmi.HDMIInputService/HW6", 0, 0xFF0F172A, 0xFF0F172A, 0xFF38BDF8),
            new Tile(ROW_TOP_STRIP, 3, ACTION_FREE_RAM, "Free RAM", "Boost",
                "", 0, 0xFF052E16, 0xFF052E16, 0xFF4ADE80),
            new Tile(ROW_TOP_STRIP, 4, ACTION_OPEN_SETTINGS, "Settings", "Android TV",
                "com.android.tv.settings", 0, 0xFF0F172A, 0xFF0F172A, 0xFF94A3B8)
        };

        this.appTiles = new Tile[] {
            new Tile(ROW_APPS, 0, ACTION_LAUNCH_PACKAGE, "YouTube", "HD 720p",
                "com.google.android.youtube.tv", 0, 0xFF2E1014, 0xFF121318, 0xFFEF4444),
            new Tile(ROW_APPS, 1, ACTION_LAUNCH_PACKAGE, "Prime Video", "Prime Video",
                "com.amazon.amazonvideo.livingroom", 0, 0xFF0C2B45, 0xFF0A131F, 0xFF00A8E1),
            new Tile(ROW_APPS, 2, ACTION_LAUNCH_PACKAGE, "DAZN", "DAZN Live",
                "com.dazn", 0, 0xFF1E1E24, 0xFF121216, 0xFFFFFFFF),
            new Tile(ROW_APPS, 3, ACTION_LAUNCH_PACKAGE, "Disney+", "Disney+",
                "com.disney.disneyplus", 0, 0xFF0A1936, 0xFF090D18, 0xFF38BDF8),
            new Tile(ROW_APPS, 4, ACTION_LAUNCH_PACKAGE, "RaiPlay", "RaiPlay",
                "it.rainet.androidtv", 0, 0xFF0D2142, 0xFF0B111E, 0xFF2563EB),
            new Tile(ROW_APPS, 5, ACTION_LAUNCH_PACKAGE, "TV", "DIGITALE DVB-T2",
                LIVE_TV_PKG, 0, 0xFF0369A1, 0xFF0F172A, 0xFF38BDF8)
        };

        this.channelTiles = new Tile[] {
            new Tile(ROW_CHANNELS, 0, ACTION_TUNE_LCN, "Rai 1", "Rai 1 HD", LIVE_TV_PKG, 1, 0xFF0C192C, 0xFF0C192C, 0xFF1D4ED8),
            new Tile(ROW_CHANNELS, 1, ACTION_TUNE_LCN, "Rai 3", "Rai 3 HD", LIVE_TV_PKG, 3, 0xFF091E16, 0xFF091E16, 0xFF15803D),
            new Tile(ROW_CHANNELS, 2, ACTION_TUNE_LCN, "LA7", "LA7 HD", LIVE_TV_PKG, 7, 0xFF1F140E, 0xFF1F140E, 0xFFEA580C),
            new Tile(ROW_CHANNELS, 3, ACTION_TUNE_LCN, "Focus", "Scienza", LIVE_TV_PKG, 35, 0xFF1C1117, 0xFF1C1117, 0xFFBE185D),
            new Tile(ROW_CHANNELS, 4, ACTION_TUNE_LCN, "Rai Storia", "Storia & Doc", LIVE_TV_PKG, 54, 0xFF17160F, 0xFF17160F, 0xFFCA8A04),
            new Tile(ROW_CHANNELS, 5, ACTION_TUNE_LCN, "Rai Scuola", "STEM & Scienza", LIVE_TV_PKG, 57, 0xFF111827, 0xFF111827, 0xFF0F766E),
            new Tile(ROW_CHANNELS, 6, ACTION_TUNE_LCN, "Rai Yoyo", "Rai Yoyo", LIVE_TV_PKG, 43, 0xFF1E112A, 0xFF1E112A, 0xFF9333EA),
            new Tile(ROW_CHANNELS, 7, ACTION_TUNE_LCN, "Boing", "Boing", LIVE_TV_PKG, 40, 0xFF0D1B2A, 0xFF0D1B2A, 0xFF2563EB),
            new Tile(ROW_CHANNELS, 8, ACTION_TUNE_LCN, "Cartoonito", "Cartoonito", LIVE_TV_PKG, 46, 0xFF1F1709, 0xFF1F1709, 0xFFEAB308)
        };
    }

    public Tile[] getRowTiles(int row) {
        if (row == ROW_TOP_STRIP) return topStripTiles;
        if (row == ROW_APPS) return appTiles;
        return channelTiles;
    }

    public int getFocusedRow() {
        return focusedRow;
    }

    public int getFocusedCol() {
        return focusedCol;
    }

    public Tile getFocusedTile() {
        return getRowTiles(focusedRow)[focusedCol];
    }

    public void setFocus(int row, int col) {
        if (row < ROW_TOP_STRIP) row = ROW_TOP_STRIP;
        if (row > ROW_CHANNELS) row = ROW_CHANNELS;
        Tile[] rowTiles = getRowTiles(row);
        if (col < 0) col = 0;
        if (col >= rowTiles.length) col = rowTiles.length - 1;
        this.focusedRow = row;
        this.focusedCol = col;
    }

    public boolean moveFocus(int direction) {
        int oldRow = focusedRow;
        int oldCol = focusedCol;
        Tile[] currentRowTiles = getRowTiles(focusedRow);

        if (direction == DIR_LEFT) {
            if (focusedCol > 0) {
                focusedCol--;
            }
        } else if (direction == DIR_RIGHT) {
            if (focusedCol < currentRowTiles.length - 1) {
                focusedCol++;
            }
        } else if (direction == DIR_UP) {
            if (focusedRow > ROW_TOP_STRIP) {
                int targetRow = focusedRow - 1;
                focusedCol = mapColumnProportionally(focusedCol, currentRowTiles.length, getRowTiles(targetRow).length);
                focusedRow = targetRow;
            }
        } else if (direction == DIR_DOWN) {
            if (focusedRow < ROW_CHANNELS) {
                int targetRow = focusedRow + 1;
                focusedCol = mapColumnProportionally(focusedCol, currentRowTiles.length, getRowTiles(targetRow).length);
                focusedRow = targetRow;
            }
        }
        return (oldRow != focusedRow) || (oldCol != focusedCol);
    }

    public static int mapColumnProportionally(int srcCol, int srcCount, int dstCount) {
        if (srcCount <= 1 || dstCount <= 1) return 0;
        if (srcCol <= 0) return 0;
        if (srcCol >= srcCount - 1) return dstCount - 1;
        int mapped = Math.round(((float) srcCol / (float) (srcCount - 1)) * (dstCount - 1));
        if (mapped < 0) return 0;
        if (mapped >= dstCount) return dstCount - 1;
        return mapped;
    }

    public int appendDigit(int digit) {
        if (digit < 0 || digit > 9) return digitBuffer;
        if (digitBuffer == 0 && digit == 0) return 0;
        int next = digitBuffer * 10 + digit;
        if (next > 999) {
            digitBuffer = digit;
        } else {
            digitBuffer = next;
        }
        return digitBuffer;
    }

    public int consumeDigitBuffer() {
        int val = digitBuffer;
        digitBuffer = 0;
        return val;
    }

    public int getDigitBuffer() {
        return digitBuffer;
    }

    public static void formatClockChars(int hour, int minute, char[] out) {
        if (hour < 0) hour = 0;
        if (hour > 23) hour = hour % 24;
        if (minute < 0) minute = 0;
        if (minute > 59) minute = minute % 60;
        out[0] = (char) ('0' + (hour / 10));
        out[1] = (char) ('0' + (hour % 10));
        out[2] = ':';
        out[3] = (char) ('0' + (minute / 10));
        out[4] = (char) ('0' + (minute % 10));
    }

    public static int formatRamChars(long freeMb, char[] out) {
        if (freeMb < 0L) freeMb = 0L;
        if (freeMb > 9999L) freeMb = 9999L;
        int v = (int) freeMb;
        int idx = 0;
        if (v >= 1000) {
            out[idx++] = (char) ('0' + (v / 1000));
            v %= 1000;
            out[idx++] = (char) ('0' + (v / 100));
            v %= 100;
            out[idx++] = (char) ('0' + (v / 10));
            out[idx++] = (char) ('0' + (v % 10));
        } else if (v >= 100) {
            out[idx++] = (char) ('0' + (v / 100));
            v %= 100;
            out[idx++] = (char) ('0' + (v / 10));
            out[idx++] = (char) ('0' + (v % 10));
        } else if (v >= 10) {
            out[idx++] = (char) ('0' + (v / 10));
            out[idx++] = (char) ('0' + (v % 10));
        } else {
            out[idx++] = (char) ('0' + v);
        }
        out[idx++] = ' ';
        out[idx++] = 'M';
        out[idx++] = 'B';
        out[idx++] = ' ';
        out[idx++] = 'f';
        out[idx++] = 'r';
        out[idx++] = 'e';
        out[idx++] = 'e';
        return idx;
    }

    public static int formatDigitOverlayChars(int digits, char[] out) {
        if (digits <= 0) return 0;
        out[0] = 'C';
        out[1] = 'H';
        out[2] = ' ';
        int idx = 3;
        if (digits >= 100) {
            out[idx++] = (char) ('0' + (digits / 100));
            int rem = digits % 100;
            out[idx++] = (char) ('0' + (rem / 10));
            out[idx++] = (char) ('0' + (rem % 10));
        } else if (digits >= 10) {
            out[idx++] = (char) ('0' + (digits / 10));
            out[idx++] = (char) ('0' + (digits % 10));
        } else {
            out[idx++] = (char) ('0' + digits);
        }
        return idx;
    }

    public static String buildPassthroughInputUri(String inputId) {
        return "content://android.media.tv/passthrough/" + inputId.replace("/", "%2F");
    }

    public static String buildChannelUri(long channelId) {
        return "content://android.media.tv/channel/" + channelId;
    }
}
