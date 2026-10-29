package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Waypoint list in the dark Feather-style layout. Open with J or /wp menu.
 * Dimension buttons (Overworld / Nether / End / All dimensions) filter the list; toggles control labels,
 * beams, beam thickness and logo size.
 *
 * Normal Minecraft buttons sit invisibly in the right places and handle clicking;
 * everything you SEE is drawn on top of them in extractRenderState.
 */
public class WaypointScreen extends Screen {
    private static final int PER_PAGE = 5;
    private static final int ROW_H = 24;

    // ---- theme (same as the edit screen) ----
    private static final int PANEL = 0xFF0E1114;
    private static final int CARD = 0xFF151A20;
    private static final int CARD_HOVER = 0xFF1D242C;
    private static final int CARD_BORDER = 0xFF262D36;
    private static final int TEXT = 0xFFE5E7EB;
    private static final int MUTED = 0xFF8B94A1;
    private static final int ORANGE = 0xFFF97316;
    private static final int ORANGE_HOVER = 0xFFFB8A3C;
    private static final int ORANGE_DARK = 0xFF4A230B;

    private static final String[] DIM_LABELS = {"Overworld", "Nether", "End", "All dimensions"};
    private static final int[] DIM_W = {70, 56, 44, 118};

    private final int page;
    private int cx;
    private int oy;

    public WaypointScreen(int page) {
        super(Component.literal("Waypoints"));
        this.page = page;
    }

    static void openMenu() {
        open(0);
    }

    static void open(int page) {
        Minecraft.getInstance().gui.setScreen(new WaypointScreen(page));
    }

    private int pages() {
        int n = WaypointsMod.menuWaypoints().size();
        return Math.max(1, (n + PER_PAGE - 1) / PER_PAGE);
    }

    private int currentPage() {
        return Math.max(0, Math.min(page, pages() - 1));
    }

    /** Which dimension button is lit: the one you picked, or the dimension you are standing in. */
    private int selectedTab() {
        int mode = WaypointsMod.menuDimensionMode;
        if (mode >= 1 && mode <= 4) return mode - 1;
        String d = WaypointsMod.currentDimension();
        if ("minecraft:overworld".equals(d)) return 0;
        if ("minecraft:the_nether".equals(d)) return 1;
        if ("minecraft:the_end".equals(d)) return 2;
        return -1;
    }

    private static String thicknessName() {
        return WaypointsMod.beamThickness == 0 ? "Normal" : WaypointsMod.beamThickness == 1 ? "Thick" : "XThick";
    }

    // ------------------------------------------------------------------ widgets (invisible click targets)
    @Override
    protected void init() {
        cx = this.width / 2;
        oy = Math.max(2, (this.height - 256) / 2);

        List<Waypoint> all = WaypointsMod.menuWaypoints();
        final int p = currentPage();
        int start = p * PER_PAGE;
        int end = Math.min(all.size(), start + PER_PAGE);

        click(cx + 138, oy + 2, 20, 14, this::onClose);
        click(cx - 150, oy + 22, 110, 18,
                () -> Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(null, p)));

        // dimension buttons
        int dx = cx - 150;
        for (int i = 0; i < DIM_LABELS.length; i++) {
            final int mode = i + 1; // 1 Overworld, 2 Nether, 3 End, 4 All
            click(dx, oy + 44, DIM_W[i], 18, () -> {
                WaypointsMod.menuDimensionMode = mode;
                open(0);
            });
            dx += DIM_W[i] + 4;
        }

        // settings toggles
        click(cx - 150, oy + 66, 68, 16, () -> {
            WaypointsMod.showWaypointLabels = !WaypointsMod.showWaypointLabels;
            open(p);
        });
        click(cx - 78, oy + 66, 68, 16, () -> {
            WaypointsMod.showWaypointBeams = !WaypointsMod.showWaypointBeams;
            open(p);
        });
        click(cx - 6, oy + 66, 84, 16, () -> {
            WaypointsMod.beamThickness = (WaypointsMod.beamThickness + 1) % 3;
            open(p);
        });
        click(cx + 82, oy + 66, 68, 16, () -> {
            WaypointsMod.markerSizeMode = (WaypointsMod.markerSizeMode + 1) % 3;
            open(p);
        });

        // rows
        for (int i = start; i < end; i++) {
            final Waypoint w = all.get(i);
            int y = oy + 88 + (i - start) * ROW_H;
            click(cx + 44, y + 3, 52, 16, () -> Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(w, p)));
            click(cx + 100, y + 3, 50, 16, () -> {
                WaypointsMod.remove(w);
                open(p);
            });
        }

        // paging + close
        click(cx - 150, oy + 216, 30, 18, () -> {
            if (p > 0) open(p - 1);
        });
        click(cx - 116, oy + 216, 30, 18, () -> {
            if (p < pages() - 1) open(p + 1);
        });
        click(cx + 80, oy + 216, 70, 18, this::onClose);
    }

    private void click(int x, int y, int w, int h, Runnable action) {
        this.addRenderableWidget(Button.builder(Component.literal(""), btn -> action.run())
                .bounds(x, y, w, h).build());
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        List<Waypoint> all = WaypointsMod.menuWaypoints();
        int p = currentPage();
        int start = p * PER_PAGE;
        int end = Math.min(all.size(), start + PER_PAGE);

        // panel (covers the plain widgets underneath)
        rrect(g, cx - 158, oy + 16, cx + 158, oy + 250, PANEL);

        // title, page, close
        g.text(this.font, "WAYPOINTS", cx - 158, oy + 5, TEXT, true);
        String pageText = "page " + (p + 1) + "/" + pages();
        g.text(this.font, pageText, cx + 130 - this.font.width(pageText), oy + 5, MUTED, false);
        boolean closeHover = inside(mouseX, mouseY, cx + 138, oy + 2, 20, 14);
        rrect(g, cx + 138, oy + 2, cx + 158, oy + 16, closeHover ? ORANGE_HOVER : ORANGE);
        rrect(g, cx + 139, oy + 3, cx + 157, oy + 15, 0xFF1A1208);
        g.text(this.font, "x", cx + 148 - this.font.width("x") / 2, oy + 5, ORANGE, false);

        // new waypoint
        boolean newHover = inside(mouseX, mouseY, cx - 150, oy + 22, 110, 18);
        rrect(g, cx - 150, oy + 22, cx - 40, oy + 40, newHover ? ORANGE_HOVER : ORANGE);
        g.text(this.font, "+ New Waypoint", cx - 95 - this.font.width("+ New Waypoint") / 2, oy + 27, 0xFFFFFFFF, true);

        // dimension buttons
        int sel = selectedTab();
        int dx = cx - 150;
        for (int i = 0; i < DIM_LABELS.length; i++) {
            boolean selected = sel == i;
            boolean hover = inside(mouseX, mouseY, dx, oy + 44, DIM_W[i], 18);
            rrect(g, dx, oy + 44, dx + DIM_W[i], oy + 62, selected ? ORANGE : CARD_BORDER);
            rrect(g, dx + 1, oy + 45, dx + DIM_W[i] - 1, oy + 61, selected ? ORANGE_DARK : (hover ? CARD_HOVER : CARD));
            int tw = this.font.width(DIM_LABELS[i]);
            g.text(this.font, DIM_LABELS[i], dx + (DIM_W[i] - tw) / 2, oy + 49, selected ? TEXT : MUTED, false);
            dx += DIM_W[i] + 4;
        }

        // settings toggles
        chip(g, mouseX, mouseY, cx - 150, oy + 66, 68, "Labels: " + (WaypointsMod.showWaypointLabels ? "ON" : "OFF"), WaypointsMod.showWaypointLabels);
        chip(g, mouseX, mouseY, cx - 78, oy + 66, 68, "Beams: " + (WaypointsMod.showWaypointBeams ? "ON" : "OFF"), WaypointsMod.showWaypointBeams);
        chip(g, mouseX, mouseY, cx - 6, oy + 66, 84, "Beam: " + thicknessName(), false);
        chip(g, mouseX, mouseY, cx + 82, oy + 66, 68, "Logo: " + WaypointsMod.markerSizeLabel(), false);

        // list
        if (all.isEmpty()) {
            g.text(this.font, "No waypoints here yet.", cx - 150, oy + 94, MUTED, false);
            g.text(this.font, "Click + New Waypoint, or press B in the world.", cx - 150, oy + 106, MUTED, false);
        }
        for (int i = start; i < end; i++) {
            Waypoint w = all.get(i);
            int y = oy + 88 + (i - start) * ROW_H;
            boolean rowHover = inside(mouseX, mouseY, cx - 150, y, 300, 22);
            rrect(g, cx - 150, y, cx + 150, y + 22, rowHover ? CARD_HOVER : CARD);
            g.fill(cx - 144, y + 6, cx - 134, y + 16, 0xFF000000 | WaypointsMod.resolveColor(w));
            g.text(this.font, w.name(), cx - 128, y + 3, TEXT, false);
            g.text(this.font, w.x() + " " + w.y() + " " + w.z(), cx - 128, y + 12, MUTED, false);

            boolean editHover = inside(mouseX, mouseY, cx + 44, y + 3, 52, 16);
            rrect(g, cx + 44, y + 3, cx + 96, y + 19, CARD_BORDER);
            rrect(g, cx + 45, y + 4, cx + 95, y + 18, editHover ? CARD_HOVER : PANEL);
            g.text(this.font, "Edit", cx + 70 - this.font.width("Edit") / 2, y + 7, TEXT, false);

            boolean delHover = inside(mouseX, mouseY, cx + 100, y + 3, 50, 16);
            rrect(g, cx + 100, y + 3, cx + 150, y + 19, delHover ? 0xFF9B2C2C : 0xFF7F1D1D);
            g.text(this.font, "Delete", cx + 125 - this.font.width("Delete") / 2, y + 7, 0xFFFEE2E2, false);
        }

        // paging + close
        smallButton(g, mouseX, mouseY, cx - 150, oy + 216, 30, "<");
        smallButton(g, mouseX, mouseY, cx - 116, oy + 216, 30, ">");
        smallButton(g, mouseX, mouseY, cx + 80, oy + 216, 70, "Close");

        g.text(this.font, WaypointsMod.menuDimensionLabel(), cx - 80, oy + 221, MUTED, false);
    }

    private void chip(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String label, boolean on) {
        boolean hover = inside(mx, my, x, y, w, 16);
        rrect(g, x, y, x + w, y + 16, on ? ORANGE : CARD_BORDER);
        rrect(g, x + 1, y + 1, x + w - 1, y + 15, on ? ORANGE_DARK : (hover ? CARD_HOVER : CARD));
        g.text(this.font, label, x + (w - this.font.width(label)) / 2, y + 4, on ? TEXT : MUTED, false);
    }

    private void smallButton(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String label) {
        boolean hover = inside(mx, my, x, y, w, 18);
        rrect(g, x, y, x + w, y + 18, CARD_BORDER);
        rrect(g, x + 1, y + 1, x + w - 1, y + 17, hover ? CARD_HOVER : CARD);
        g.text(this.font, label, x + (w - this.font.width(label)) / 2, y + 5, TEXT, false);
    }

    /** Rectangle with slightly rounded corners. */
    private static void rrect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1 + 2, y1, x2 - 2, y2, color);
        g.fill(x1, y1 + 1, x1 + 2, y2 - 1, color);
        g.fill(x2 - 2, y1 + 1, x2, y2 - 1, color);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
