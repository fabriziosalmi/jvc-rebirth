package tv.rebirth.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LauncherModelTest {

    @Test
    public void testInitialFocusAndAppsOrder() {
        LauncherModel model = new LauncherModel();
        assertEquals(LauncherModel.ROW_APPS, model.getFocusedRow());
        assertEquals(0, model.getFocusedCol());

        LauncherModel.Tile[] apps = model.getRowTiles(LauncherModel.ROW_APPS);
        assertEquals(6, apps.length);
        assertEquals("YouTube", apps[0].title);
        assertEquals("YouTube", apps[0].subtitle);
        assertEquals("com.google.android.youtube.tv", apps[0].targetPackageOrInput);

        assertEquals("Prime Video", apps[1].title);
        assertEquals("com.amazon.amazonvideo.livingroom", apps[1].targetPackageOrInput);

        assertEquals("DAZN", apps[2].title);
        assertEquals("com.dazn", apps[2].targetPackageOrInput);

        assertEquals("Disney+", apps[3].title);
        assertEquals("com.disney.disneyplus", apps[3].targetPackageOrInput);

        assertEquals("RaiPlay", apps[4].title);
        assertEquals("it.rainet.androidtv", apps[4].targetPackageOrInput);

        // TV must be at the far right (index 5)
        assertEquals("TV", apps[5].title);
        assertEquals("com.mediatek.wwtv.tvcenter", apps[5].targetPackageOrInput);
    }

    @Test
    public void testQuickChannelsRowOrderAndLcns() {
        LauncherModel model = new LauncherModel();
        LauncherModel.Tile[] chs = model.getRowTiles(LauncherModel.ROW_CHANNELS);
        assertEquals(9, chs.length);

        int[] expectedLcns = new int[] { 1, 3, 7, 35, 54, 57, 43, 40, 46 };
        String[] expectedNames = new String[] {
            "Rai 1", "Rai 3", "LA7", "Focus", "Rai Storia", "Rai Scuola", "Rai Yoyo", "Boing", "Cartoonito"
        };

        for (int i = 0; i < expectedLcns.length; i++) {
            assertEquals(expectedLcns[i], chs[i].lcn);
            assertEquals(expectedNames[i], chs[i].title);
            assertEquals(LauncherModel.ACTION_TUNE_LCN, chs[i].actionType);
        }
    }

    @Test
    public void testHorizontalDpadNavigationAndWrapAround() {
        LauncherModel model = new LauncherModel();
        assertEquals(0, model.getFocusedCol());

        // Move right across all 6 app tiles
        for (int c = 1; c < 6; c++) {
            assertTrue(model.moveFocus(LauncherModel.DIR_RIGHT));
            assertEquals(c, model.getFocusedCol());
        }
        assertEquals("TV", model.getFocusedTile().title);

        // Moving right past the last column (5 = TV) wraps around to 0 (YouTube)
        assertTrue(model.moveFocus(LauncherModel.DIR_RIGHT));
        assertEquals(0, model.getFocusedCol());
        assertEquals("YouTube", model.getFocusedTile().title);

        // Moving left from 0 (YouTube) wraps around in 1 click to 5 (TV)
        assertTrue(model.moveFocus(LauncherModel.DIR_LEFT));
        assertEquals(5, model.getFocusedCol());
        assertEquals("TV", model.getFocusedTile().title);
    }

    @Test
    public void testProportionalVerticalNavigationBetweenRows() {
        LauncherModel model = new LauncherModel();
        // Start at YouTube (row 1, col 0) -> DOWN -> Rai 1 (row 2, col 0)
        assertTrue(model.moveFocus(LauncherModel.DIR_DOWN));
        assertEquals(LauncherModel.ROW_CHANNELS, model.getFocusedRow());
        assertEquals(0, model.getFocusedCol());
        assertEquals("Rai 1", model.getFocusedTile().title);

        // Move to far-right channel Cartoonito (row 2, col 8) -> UP -> TV (row 1, col 5)
        model.setFocus(LauncherModel.ROW_CHANNELS, 8);
        assertEquals("Cartoonito", model.getFocusedTile().title);
        assertTrue(model.moveFocus(LauncherModel.DIR_UP));
        assertEquals(LauncherModel.ROW_APPS, model.getFocusedRow());
        assertEquals(5, model.getFocusedCol());
        assertEquals("TV", model.getFocusedTile().title);

        // From TV (row 1, col 5) -> UP -> Settings (row 0, col 4)
        assertTrue(model.moveFocus(LauncherModel.DIR_UP));
        assertEquals(LauncherModel.ROW_TOP_STRIP, model.getFocusedRow());
        assertEquals(4, model.getFocusedCol());
        assertEquals("Settings", model.getFocusedTile().title);
    }

    @Test
    public void testNumericKeypadChannelEntryBuffer() {
        LauncherModel model = new LauncherModel();
        assertEquals(0, model.getDigitBuffer());
        assertEquals(0, model.appendDigit(0)); // Leading zero ignored

        assertEquals(4, model.appendDigit(4));
        assertEquals(43, model.appendDigit(3));
        assertEquals(43, model.consumeDigitBuffer());
        assertEquals(0, model.getDigitBuffer());

        // 3 digits max (1..999), 4th digit starts fresh
        model.appendDigit(1);
        model.appendDigit(2);
        model.appendDigit(3);
        assertEquals(123, model.getDigitBuffer());
        assertEquals(5, model.appendDigit(5));
    }

    @Test
    public void testVestelHdmiAndChannelUriBuilders() {
        LauncherModel model = new LauncherModel();
        LauncherModel.Tile[] top = model.getRowTiles(LauncherModel.ROW_TOP_STRIP);

        assertEquals(
            "content://android.media.tv/passthrough/com.mediatek.tvinput%2F.hdmi.HDMIInputService%2FHW4",
            LauncherModel.buildPassthroughInputUri(top[0].targetPackageOrInput)
        );
        assertEquals(
            "content://android.media.tv/passthrough/com.mediatek.tvinput%2F.hdmi.HDMIInputService%2FHW5",
            LauncherModel.buildPassthroughInputUri(top[1].targetPackageOrInput)
        );
        assertEquals(
            "content://android.media.tv/passthrough/com.mediatek.tvinput%2F.hdmi.HDMIInputService%2FHW6",
            LauncherModel.buildPassthroughInputUri(top[2].targetPackageOrInput)
        );
        assertEquals(
            "content://android.media.tv/channel/42",
            LauncherModel.buildChannelUri(42L)
        );
        assertEquals(
            "content://android.media.tv/passthrough/com.mediatek.tvinput%2F.hdmi.HDMIInputService%2FHW4",
            top[0].passthroughUri
        );
        assertEquals(
            "com.mediatek.wwtv.tvcenter.nav.TurnkeyUiMainActivity",
            LauncherModel.LIVE_TV_ACTIVITY
        );
        assertEquals(
            "com.mediatek.tv.selectchannel",
            LauncherModel.ACTION_MTK_SELECT_CHANNEL
        );
        assertEquals(
            "com.mediatek.tv.channelupdown",
            LauncherModel.ACTION_MTK_CHANNEL_UPDOWN
        );
        assertEquals(
            "com.mediatek.select.source",
            LauncherModel.ACTION_MTK_SELECT_SOURCE
        );
    }

    @Test
    public void testZeroAllocationsInRenderAndTelemetryBytecode() throws Exception {
        String classesDir = System.getProperty("rebirth.classes.dir", "build/classes");
        Map<String, List<String>> viewMethods = disassembleClass(classesDir + "/tv/rebirth/launcher/RebirthHomeView.class");
        String[] zeroAllocViewMethods = new String[] {
            "onDraw",
            "drawTopHeader",
            "drawAppsRow",
            "drawChannelsRow",
            "drawFooter",
            "drawFooterKeycap",
            "drawDigitOverlayIfActive",
            "updateStatus"
        };
        for (String m : zeroAllocViewMethods) {
            assertZeroAllocationsInMethod("RebirthHomeView." + m, viewMethods.get(m));
        }

        Map<String, List<String>> modelMethods = disassembleClass(classesDir + "/tv/rebirth/launcher/LauncherModel.class");
        String[] zeroAllocModelMethods = new String[] {
            "moveFocus",
            "getFocusedTile",
            "getRowTiles",
            "appendDigit",
            "consumeDigitBuffer",
            "formatClockChars",
            "formatRamChars",
            "formatDigitOverlayChars"
        };
        for (String m : zeroAllocModelMethods) {
            assertZeroAllocationsInMethod("LauncherModel." + m, modelMethods.get(m));
        }

        Map<String, List<String>> activityMethods = disassembleClass(classesDir + "/tv/rebirth/launcher/MainActivity.class");
        String[] zeroAllocActivityMethods = new String[] {
            "refreshHeaderTelemetry",
            "getAvailableMemoryMb",
            "launchLiveTv",
            "tuneToChannel",
            "sendMtkSelectChannelBroadcast",
            "sendMtkChannelUpDownBroadcast",
            "findCachedLaunchIntent"
        };
        for (String m : zeroAllocActivityMethods) {
            assertZeroAllocationsInMethod("MainActivity." + m, activityMethods.get(m));
        }
    }

    @Test
    public void testZeroAllocationCharFormatters() {
        char[] clock = new char[5];
        LauncherModel.formatClockChars(9, 5, clock);
        assertEquals("09:05", new String(clock));
        LauncherModel.formatClockChars(21, 47, clock);
        assertEquals("21:47", new String(clock));

        char[] ram = new char[16];
        int len1 = LauncherModel.formatRamChars(418L, ram);
        assertEquals("418 MB free", new String(ram, 0, len1));
        int len2 = LauncherModel.formatRamChars(92L, ram);
        assertEquals("92 MB free", new String(ram, 0, len2));

        char[] digits = new char[8];
        int dlen1 = LauncherModel.formatDigitOverlayChars(7, digits);
        assertEquals("CH 7", new String(digits, 0, dlen1));
        int dlen2 = LauncherModel.formatDigitOverlayChars(501, digits);
        assertEquals("CH 501", new String(digits, 0, dlen2));
    }

    @Test
    public void testNavigationAndFocusMicrobenchmark() {
        LauncherModel model = new LauncherModel();
        // Warmup 50,000 ops
        for (int i = 0; i < 50000; i++) {
            model.moveFocus(i & 3);
            LauncherModel.Tile t = model.getFocusedTile();
            assertNotNull(t);
        }

        int iterations = 1000000;
        long startNs = System.nanoTime();
        int checksum = 0;
        for (int i = 0; i < iterations; i++) {
            model.moveFocus(i & 3);
            LauncherModel.Tile t = model.getFocusedTile();
            checksum ^= t.actionType + t.col + t.row;
        }
        long elapsedNs = System.nanoTime() - startNs;
        double nsPerOp = (double) elapsedNs / (double) iterations;
        System.out.println(String.format(
            "[BENCHMARK] 1,000,000 D-Pad focus + tile lookups: %.2f ms total (%.2f ns/op, checksum=%d)",
            elapsedNs / 1e6, nsPerOp, checksum
        ));
        // Must execute in well under 100 ns/op (60fps frame budget is 16,666,667 ns)
        assertTrue("Expected < 100 ns/op, got " + nsPerOp, nsPerOp < 100.0);
    }

    private static void assertZeroAllocationsInMethod(String methodLabel, List<String> instructions) {
        assertNotNull("Method not found in bytecode: " + methodLabel, instructions);
        assertFalse("Empty bytecode for: " + methodLabel, instructions.isEmpty());
        for (String line : instructions) {
            assertFalse(methodLabel + " contains object allocation: " + line, line.contains(" new "));
            assertFalse(methodLabel + " contains primitive array allocation: " + line, line.contains("newarray"));
            assertFalse(methodLabel + " contains reference array allocation: " + line, line.contains("anewarray"));
            assertFalse(methodLabel + " contains StringBuilder allocation: " + line, line.contains("StringBuilder"));
            assertFalse(methodLabel + " contains String.valueOf allocation: " + line, line.contains("String.valueOf"));
            assertFalse(methodLabel + " contains Long.valueOf boxing: " + line, line.contains("Long.valueOf"));
            assertFalse(methodLabel + " contains CPU SkPath rasterization: " + line, line.contains("android/graphics/Path"));
        }
    }

    private static Map<String, List<String>> disassembleClass(String classFilePath) throws Exception {
        String javaHome = System.getProperty("java.home");
        String javapBin = javaHome + "/bin/javap";
        Process proc = new ProcessBuilder(javapBin, "-c", "-p", classFilePath).start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        Map<String, List<String>> methods = new HashMap<String, List<String>>();
        String currentMethod = null;
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.endsWith(");") && trimmed.contains("(")) {
                int parenIdx = trimmed.indexOf('(');
                String beforeParen = trimmed.substring(0, parenIdx).trim();
                int lastSpace = beforeParen.lastIndexOf(' ');
                currentMethod = (lastSpace >= 0) ? beforeParen.substring(lastSpace + 1) : beforeParen;
                methods.put(currentMethod, new ArrayList<String>());
            } else if (currentMethod != null && (trimmed.contains(":") || trimmed.contains("//"))) {
                methods.get(currentMethod).add(line);
            }
        }
        proc.waitFor();
        return methods;
    }
}
