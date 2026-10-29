package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Create / edit one waypoint with a dark Feather-style layout: name, X/Y/Z, dimension buttons
 * (Overworld / Nether / End / All dimensions), a color picker (saturation-brightness grid, hue strip,
 * hex box, Chroma toggle, Reset, recent colors), timer and logo letter.
 *
 * How it works: normal Minecraft buttons and text boxes sit invisibly in the right places and handle all
 * clicking and typing; everything you actually SEE is drawn on top of them in extractRenderState.
 */
public class WaypointEditScreen extends Screen {
    // ---- theme ----
    private static final int PANEL = 0xFF0E1114;
    private static final int CARD = 0xFF151A20;
    private static final int CARD_HOVER = 0xFF1D242C;
    private static final int CARD_BORDER = 0xFF262D36;
    private static final int TEXT = 0xFFE5E7EB;
    private static final int MUTED = 0xFF8B94A1;
    private static final int ORANGE = 0xFFF97316;
    private static final int ORANGE_HOVER = 0xFFFB8A3C;
    private static final int ORANGE_DARK = 0xFF4A230B;

    // ---- color picker geometry ----
    private static final int SV_COLS = 16;
    private static final int SV_ROWS = 8;
    private static final int SV_CELL = 8;      // click cell size
    private static final int SV_DRAW = 4;      // drawn in 4x4 blocks for a smoother gradient
    private static final int HUE_CELLS = 16;
    private static final int HUE_CELL_H = 4;

    // ---- dimensions ----
    private static final String[] DIM_LABELS = {"Overworld", "Nether", "End", "All dimensions"};
    private static final String[] DIM_IDS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end", "all"};
    private static final int[] DIM_W = {70, 56, 44, 118};

    /** Recently saved colors (kept until you quit the game). */
    private static final List<Integer> RECENT = new ArrayList<>();

    private final Waypoint original; // null = creating a new one
    private final int returnPage;

    // starting values (kept so the screen survives being re-initialised, e.g. on window resize)
    private final String initName;
    private final String initX;
    private final String initY;
    private final String initZ;
    private final String initTimer;
    private final String initMarker;
    private final String keepDimension;

    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private EditBox hexBox;
    private EditBox timerBox;
    private EditBox markerBox;

    private float hue = 0f;
    private float sat = 1f;
    private float val = 1f;
    private boolean chroma = false;
    private int dimMode;            // 0 Overworld, 1 Nether, 2 End, 3 All, -1 = keep the original (modded) dimension
    private String lastHex = "";
    private String error = "";

    private int cx;
    private int oy;

    public WaypointEditScreen(Waypoint original, int returnPage) {
        super(Component.literal(original == null ? "New Waypoint" : "Edit Waypoint"));
        this.original = original;
        this.returnPage = returnPage;

        if (original != null) {
            initName = original.name();
            initX = String.valueOf(original.x());
            initY = String.valueOf(original.y());
            initZ = String.valueOf(original.z());
            initTimer = original.expiresAt() > 0
                    ? String.valueOf(Math.max(1, (original.expiresAt() - System.currentTimeMillis()) / 1000))
                    : "0";
            initMarker = original.safeMarkerText();
            keepDimension = original.dimension();
            if (original.color() == WaypointsMod.CHROMA) {
                chroma = true;
            } else {
                setFromRgb(original.color() & 0xFFFFFF);
            }
        } else {
            Minecraft mc = Minecraft.getInstance();
            BlockPos p = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
            initName = WaypointsMod.nextWaypointName();
            initX = String.valueOf(p.getX());
            initY = String.valueOf(p.getY());
            initZ = String.valueOf(p.getZ());
            initTimer = "0";
            initMarker = "W";
            keepDimension = WaypointsMod.currentDimension();
        }

        String normalized = WaypointsMod.normalizeDimension(keepDimension);
        dimMode = -1;
        for (int i = 0; i < DIM_IDS.length; i++) {
            if (DIM_IDS[i].equals(normalized)) dimMode = i;
        }
    }

    /** Opened by the B key: a blank "New Waypoint" menu, already filled with your position and dimension. */
    static void openNew() {
        Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(null, -1));
    }

    /** Plain dark overlay instead of Minecraft's blurred world, so the menu looks sharp and clear. */
    public void extractBlurredBackground(GuiGraphicsExtractor g) {
    }

    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    // ------------------------------------------------------------------ layout / widgets
    @Override
    protected void init() {
        cx = this.width / 2;
        oy = Math.max(2, (this.height - 256) / 2);

        // close (X) and go back
        click(cx + 138, oy + 2, 20, 14, this::back);
        click(cx - 150, oy + 22, 62, 16, this::back);

        nameBox = makeBox(nameBox, cx - 150, oy + 44, 300, 18, initName, 32, "Name");
        xBox = makeBox(xBox, cx - 150, oy + 66, 96, 18, initX, 9, "X");
        yBox = makeBox(yBox, cx - 48, oy + 66, 96, 18, initY, 9, "Y");
        zBox = makeBox(zBox, cx + 54, oy + 66, 96, 18, initZ, 9, "Z");

        // dimension buttons
        int dx = cx - 150;
        for (int i = 0; i < DIM_LABELS.length; i++) {
            final int mode = i;
            click(dx, oy + 88, DIM_W[i], 18, () -> dimMode = mode);
            dx += DIM_W[i] + 4;
        }

        // color picker click targets
        for (int r = 0; r < SV_ROWS; r++) {
            for (int c = 0; c < SV_COLS; c++) {
                final int col = c;
                final int row = r;
                click(cx - 150 + c * SV_CELL, oy + 124 + r * SV_CELL, SV_CELL, SV_CELL, () -> pickSv(col, row));
            }
        }
        for (int i = 0; i < HUE_CELLS; i++) {
            final int idx = i;
            click(cx - 16, oy + 124 + i * HUE_CELL_H, 10, HUE_CELL_H, () -> pickHue(idx));
        }
        click(cx + 28, oy + 109, 60, 13, () -> chroma = !chroma);
        click(cx + 90, oy + 109, 60, 13, this::resetColor);
        for (int i = 0; i < RECENT.size(); i++) {
            final int idx = i;
            click(cx + 2 + i * 16, oy + 181, 14, 10, () -> pickRecent(idx));
        }

        hexBox = makeBox(hexBox, cx + 2, oy + 152, 100, 16, String.format(Locale.ROOT, "%06X", currentRgb()), 7, "HEX");
        lastHex = hexBox.getValue();

        timerBox = makeBox(timerBox, cx - 100, oy + 198, 56, 16, initTimer, 6, "0");
        markerBox = makeBox(markerBox, cx - 6, oy + 198, 28, 16, initMarker, 1, "W");

        // save / delete
        int saveWidth = original != null ? 216 : 300;
        click(cx - 150, oy + 220, saveWidth, 20, this::save);
        if (original != null) {
            click(cx + 72, oy + 220, 78, 20, () -> {
                WaypointsMod.remove(original);
                back();
            });
        }
    }

    private void click(int x, int y, int w, int h, Runnable action) {
        this.addRenderableWidget(Button.builder(Component.literal(""), btn -> action.run())
                .bounds(x, y, w, h).build());
    }

    private EditBox makeBox(EditBox old, int x, int y, int w, int h, String initial, int maxLength, String label) {
        EditBox box = new EditBox(this.font, x, y, w, h, Component.literal(label));
        box.setMaxLength(maxLength);
        box.setValue(old != null ? old.getValue() : initial);
        this.addRenderableWidget(box);
        return box;
    }

    private void back() {
        if (returnPage < 0) {
            this.onClose(); // opened with B: go straight back to the game
        } else {
            WaypointScreen.open(returnPage);
        }
    }

    // ------------------------------------------------------------------ color logic
    private void pickSv(int col, int row) {
        sat = (col + 0.5f) / SV_COLS;
        val = 1f - (row + 0.5f) / SV_ROWS;
        chroma = false;
        syncHex();
    }

    private void pickHue(int idx) {
        hue = (idx + 0.5f) / HUE_CELLS;
        chroma = false;
        syncHex();
    }

    private void pickRecent(int idx) {
        if (idx < 0 || idx >= RECENT.size()) return;
        setFromRgb(RECENT.get(idx));
        chroma = false;
        syncHex();
    }

    private void resetColor() {
        hue = 0f;
        sat = 1f;
        val = 1f;
        chroma = false;
        syncHex();
    }

    private void syncHex() {
        if (hexBox == null) return;
        String hex = String.format(Locale.ROOT, "%06X", currentRgb());
        hexBox.setValue(hex);
        lastHex = hex;
    }

    private void applyHex(String text) {
        String t = text.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() != 6) return;
        try {
            setFromRgb(Integer.parseInt(t, 16));
            chroma = false;
        } catch (NumberFormatException ignored) {
        }
    }

    private int currentRgb() {
        return hsvToRgb(hue, sat, val);
    }

    private void setFromRgb(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0f) h = 0f;
        else if (max == r) h = (((g - b) / d) % 6f) / 6f;
        else if (max == g) h = (((b - r) / d) + 2f) / 6f;
        else h = (((r - g) / d) + 4f) / 6f;
        if (h < 0f) h += 1f;
        hue = h;
        sat = max == 0f ? 0f : d / max;
        val = max;
    }

    private static int hsvToRgb(float h, float s, float v) {
        float hh = (h - (float) Math.floor(h)) * 6f;
        int i = (int) hh;
        float f = hh - i;
        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float t = v * (1f - s * (1f - f));
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
    }

    // ------------------------------------------------------------------ saving
    private void save() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            error = "Please type a name.";
            return;
        }

        int x;
        int y;
        int z;
        try {
            x = Integer.parseInt(xBox.getValue().trim());
            y = Integer.parseInt(yBox.getValue().trim());
            z = Integer.parseInt(zBox.getValue().trim());
        } catch (NumberFormatException e) {
            error = "X, Y and Z must be whole numbers.";
            return;
        }

        int secs;
        try {
            String t = timerBox.getValue().trim();
            secs = t.isEmpty() ? 0 : Integer.parseInt(t);
        } catch (NumberFormatException e) {
            error = "Timer must be a number of seconds (0 = forever).";
            return;
        }

        int color = chroma ? WaypointsMod.CHROMA : currentRgb();
        String marker = markerBox.getValue().trim();
        if (marker.isEmpty()) marker = "W";
        marker = marker.toUpperCase(Locale.ROOT);

        long expires = secs > 0 ? System.currentTimeMillis() + secs * 1000L : 0L;
        String dim = dimMode >= 0 ? DIM_IDS[dimMode] : keepDimension;
        String worldId = original != null ? original.worldId() : WaypointsMod.currentWorldId();

        WaypointsMod.upsert(original, new Waypoint(name, x, y, z, color, dim, expires, worldId, marker));

        if (!chroma) {
            Integer rgb = color;
            RECENT.remove(rgb);
            RECENT.add(0, rgb);
            while (RECENT.size() > 6) RECENT.remove(RECENT.size() - 1);
        }
        back();
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        // typing a hex code updates the picker
        String curHex = hexBox.getValue();
        if (!curHex.equals(lastHex)) {
            lastHex = curHex;
            applyHex(curHex);
        }

        // panel (covers the plain widgets underneath)
        rrect(g, cx - 158, oy + 16, cx + 158, oy + 250, PANEL);

        // title + close
        g.text(this.font, original == null ? "NEW WAYPOINT" : "EDIT WAYPOINT", cx - 158, oy + 5, TEXT, true);
        boolean closeHover = inside(mouseX, mouseY, cx + 138, oy + 2, 20, 14);
        rrect(g, cx + 138, oy + 2, cx + 158, oy + 16, closeHover ? ORANGE_HOVER : ORANGE);
        rrect(g, cx + 139, oy + 3, cx + 157, oy + 15, 0xFF1A1208);
        g.text(this.font, "x", cx + 148 - this.font.width("x") / 2, oy + 5, ORANGE, false);

        // go back
        boolean backHover = inside(mouseX, mouseY, cx - 150, oy + 22, 62, 16);
        rrect(g, cx - 150, oy + 22, cx - 88, oy + 38, backHover ? CARD_HOVER : CARD);
        String backLabel = returnPage < 0 ? "< Cancel" : "< Go back";
        g.text(this.font, backLabel, cx - 119 - this.font.width(backLabel) / 2, oy + 26, MUTED, false);

        // text boxes
        drawBox(g, nameBox, cx - 150, oy + 44, 300, 18, "Name");
        drawBox(g, xBox, cx - 150, oy + 66, 96, 18, "X");
        drawBox(g, yBox, cx - 48, oy + 66, 96, 18, "Y");
        drawBox(g, zBox, cx + 54, oy + 66, 96, 18, "Z");
        g.text(this.font, "X", cx - 150 + 96 - 10, oy + 71, MUTED, false);
        g.text(this.font, "Y", cx - 48 + 96 - 10, oy + 71, MUTED, false);
        g.text(this.font, "Z", cx + 54 + 96 - 10, oy + 71, MUTED, false);

        // dimension buttons
        int dx = cx - 150;
        for (int i = 0; i < DIM_LABELS.length; i++) {
            boolean selected = dimMode == i;
            boolean hover = inside(mouseX, mouseY, dx, oy + 88, DIM_W[i], 18);
            rrect(g, dx, oy + 88, dx + DIM_W[i], oy + 106, selected ? ORANGE : CARD_BORDER);
            rrect(g, dx + 1, oy + 89, dx + DIM_W[i] - 1, oy + 105, selected ? ORANGE_DARK : (hover ? CARD_HOVER : CARD));
            int tw = this.font.width(DIM_LABELS[i]);
            g.text(this.font, DIM_LABELS[i], dx + (DIM_W[i] - tw) / 2, oy + 93, selected ? TEXT : MUTED, false);
            dx += DIM_W[i] + 4;
        }

        // color header, chroma toggle, reset
        g.text(this.font, "COLOR", cx - 150, oy + 112, TEXT, true);
        g.text(this.font, "Chroma", cx + 28, oy + 112, MUTED, false);
        rrect(g, cx + 66, oy + 110, cx + 88, oy + 120, chroma ? ORANGE : 0xFF39414B);
        int knobX = chroma ? cx + 78 : cx + 67;
        g.fill(knobX, oy + 111, knobX + 9, oy + 119, 0xFFF3F4F6);
        boolean resetHover = inside(mouseX, mouseY, cx + 90, oy + 109, 60, 13);
        rrect(g, cx + 90, oy + 109, cx + 150, oy + 122, resetHover ? CARD_HOVER : CARD);
        g.text(this.font, "Reset color", cx + 120 - this.font.width("Reset color") / 2, oy + 112, MUTED, false);

        // saturation / brightness grid
        int gx = cx - 150;
        int gy = oy + 124;
        int blocksX = SV_COLS * SV_CELL / SV_DRAW;
        int blocksY = SV_ROWS * SV_CELL / SV_DRAW;
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                float s = (bx + 0.5f) / blocksX;
                float v = 1f - (by + 0.5f) / blocksY;
                g.fill(gx + bx * SV_DRAW, gy + by * SV_DRAW, gx + (bx + 1) * SV_DRAW, gy + (by + 1) * SV_DRAW,
                        0xFF000000 | hsvToRgb(hue, s, v));
            }
        }
        int markX = gx + Math.round(sat * SV_COLS * SV_CELL);
        int markY = gy + Math.round((1f - val) * SV_ROWS * SV_CELL);
        ring(g, markX, markY);

        // hue strip
        for (int i = 0; i < HUE_CELLS; i++) {
            g.fill(cx - 16, gy + i * HUE_CELL_H, cx - 6, gy + (i + 1) * HUE_CELL_H,
                    0xFF000000 | hsvToRgb((i + 0.5f) / HUE_CELLS, 1f, 1f));
        }
        int hueY = gy + Math.round(hue * HUE_CELLS * HUE_CELL_H);
        g.fill(cx - 18, hueY - 1, cx - 4, hueY + 1, 0xFFFFFFFF);

        // selected color preview + hex
        g.text(this.font, "Selected color", cx + 2, oy + 124, MUTED, false);
        int previewRgb = chroma
                ? WaypointsMod.resolveColor(new Waypoint("", 0, 0, 0, WaypointsMod.CHROMA, "", 0L, "", "W"))
                : currentRgb();
        rrect(g, cx + 2, oy + 134, cx + 102, oy + 148, 0xFF000000 | previewRgb);
        drawBox(g, hexBox, cx + 2, oy + 152, 100, 16, "HEX");

        // recent colors
        g.text(this.font, "Recent colors", cx + 2, oy + 172, MUTED, false);
        for (int i = 0; i < RECENT.size(); i++) {
            rrect(g, cx + 2 + i * 16, oy + 181, cx + 16 + i * 16, oy + 191, 0xFF000000 | RECENT.get(i));
        }

        // timer + logo
        g.text(this.font, "Timer (s)", cx - 150, oy + 202, MUTED, false);
        drawBox(g, timerBox, cx - 100, oy + 198, 56, 16, "0");
        g.text(this.font, "Logo", cx - 34, oy + 202, MUTED, false);
        drawBox(g, markerBox, cx - 6, oy + 198, 28, 16, "W");
        g.text(this.font, "0 = forever", cx + 30, oy + 202, MUTED, false);

        // save / delete
        int saveWidth = original != null ? 216 : 300;
        boolean saveHover = inside(mouseX, mouseY, cx - 150, oy + 220, saveWidth, 20);
        rrect(g, cx - 150, oy + 220, cx - 150 + saveWidth, oy + 240, saveHover ? ORANGE_HOVER : ORANGE);
        String saveLabel = original == null ? "Create Waypoint" : "Save Waypoint";
        g.text(this.font, saveLabel, cx - 150 + saveWidth / 2 - this.font.width(saveLabel) / 2, oy + 226, 0xFFFFFFFF, true);
        if (original != null) {
            boolean delHover = inside(mouseX, mouseY, cx + 72, oy + 220, 78, 20);
            rrect(g, cx + 72, oy + 220, cx + 150, oy + 240, delHover ? 0xFF9B2C2C : 0xFF7F1D1D);
            g.text(this.font, "Delete", cx + 111 - this.font.width("Delete") / 2, oy + 226, 0xFFFEE2E2, false);
        }

        if (!error.isEmpty()) {
            g.text(this.font, error, cx - 150, oy + 242, 0xFFFF6B6B, false);
        }
    }

    private void drawBox(GuiGraphicsExtractor g, EditBox box, int x, int y, int w, int h, String placeholder) {
        boolean focused = box.isFocused();
        rrect(g, x, y, x + w, y + h, focused ? ORANGE : CARD_BORDER);
        rrect(g, x + 1, y + 1, x + w - 1, y + h - 1, CARD);
        String v = box.getValue();
        int ty = y + (h - 8) / 2;
        if (v.isEmpty() && !focused) {
            g.text(this.font, placeholder, x + 8, ty, MUTED, false);
        } else {
            g.text(this.font, v, x + 8, ty, TEXT, false);
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int caretX = x + 8 + this.font.width(v);
                g.fill(caretX, y + 3, caretX + 1, y + h - 3, TEXT);
            }
        }
    }

    /** Rectangle with slightly rounded corners. */
    private static void rrect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1 + 2, y1, x2 - 2, y2, color);
        g.fill(x1, y1 + 1, x1 + 2, y2 - 1, color);
        g.fill(x2 - 2, y1 + 1, x2, y2 - 1, color);
    }

    /** Small white ring used as the picker marker. */
    private static void ring(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x - 3, y - 3, x + 4, y - 2, 0xFF000000);
        g.fill(x - 3, y + 3, x + 4, y + 4, 0xFF000000);
        g.fill(x - 3, y - 2, x - 2, y + 3, 0xFF000000);
        g.fill(x + 3, y - 2, x + 4, y + 3, 0xFF000000);
        g.fill(x - 2, y - 2, x + 3, y - 1, 0xFFFFFFFF);
        g.fill(x - 2, y + 2, x + 3, y + 3, 0xFFFFFFFF);
        g.fill(x - 2, y - 1, x - 1, y + 2, 0xFFFFFFFF);
        g.fill(x + 2, y - 1, x + 3, y + 2, 0xFFFFFFFF);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
