package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Waypoint list: create, edit or delete waypoints. Open with J or /wp menu. */
public class WaypointScreen extends Screen {
    private static final int PER_PAGE = 6;
    private static final int ROW_H = 24;
    private static final int TOP = 112;

    private final int page;

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

    @Override
    protected void init() {
        List<Waypoint> all = WaypointsMod.menuWaypoints();
        final int p = currentPage();
        int start = p * PER_PAGE;
        int end = Math.min(all.size(), start + PER_PAGE);
        int cx = this.width / 2;
        int y = TOP;

        this.addRenderableWidget(Button.builder(Component.literal("+ New Waypoint"),
                btn -> Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(null, p)))
                .bounds(cx - 160, 28, 110, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Dimension: " + WaypointsMod.menuDimensionLabel()), btn -> {
            WaypointsMod.cycleMenuDimension();
            open(0);
        }).bounds(cx - 44, 28, 204, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Labels: " + (WaypointsMod.showWaypointLabels ? "ON" : "OFF")), btn -> {
            WaypointsMod.showWaypointLabels = !WaypointsMod.showWaypointLabels;
            open(p);
        }).bounds(cx - 160, 54, 76, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Beams: " + (WaypointsMod.showWaypointBeams ? "ON" : "OFF")), btn -> {
            WaypointsMod.showWaypointBeams = !WaypointsMod.showWaypointBeams;
            open(p);
        }).bounds(cx - 80, 54, 76, 20).build());
        String thicknessName = WaypointsMod.beamThickness == 0 ? "Normal" : WaypointsMod.beamThickness == 1 ? "Thick" : "Extra Thick";
        this.addRenderableWidget(Button.builder(Component.literal("Beam: " + thicknessName), btn -> {
            WaypointsMod.beamThickness = (WaypointsMod.beamThickness + 1) % 3;
            open(p);
        }).bounds(cx, 54, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Logo: " + WaypointsMod.markerSizeLabel()), btn -> {
            WaypointsMod.markerSizeMode = (WaypointsMod.markerSizeMode + 1) % 3;
            open(p);
        }).bounds(cx + 84, 54, 76, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Logo color: " + WaypointsMod.markerColorLabel()), btn -> {
            WaypointsMod.markerColorMode = (WaypointsMod.markerColorMode + 1) % 7;
            open(p);
        }).bounds(cx - 160, 80, 156, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Text color: " + WaypointsMod.markerTextColorLabel()), btn -> {
            WaypointsMod.markerTextColorMode = (WaypointsMod.markerTextColorMode + 1) % 5;
            open(p);
        }).bounds(cx, 80, 160, 20).build());

        for (int i = start; i < end; i++) {
            final Waypoint w = all.get(i);
            this.addRenderableWidget(Button.builder(Component.literal("Edit"),
                    btn -> Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(w, p)))
                    .bounds(cx + 56, y, 50, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("Delete"), btn -> {
                WaypointsMod.remove(w);
                open(p);
            }).bounds(cx + 110, y, 50, 20).build());

            y += ROW_H;
        }

        int bottom = TOP + PER_PAGE * ROW_H + 8;

        this.addRenderableWidget(Button.builder(Component.literal("<"), btn -> {
            if (p > 0) open(p - 1);
        }).bounds(cx - 160, bottom, 30, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal(">"), btn -> {
            if (p < pages() - 1) open(p + 1);
        }).bounds(cx - 126, bottom, 30, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Close"), btn -> this.onClose())
                .bounds(cx + 90, bottom, 70, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int cx = this.width / 2;
        graphics.text(this.font, "Waypoints  (page " + (currentPage() + 1) + "/" + pages() + ")",
                cx - 20, 8, 0xFFFFFFFF, true);
        graphics.text(this.font, "World/server: current connection only", cx - 160, 102, 0xFFAAAAAA, true);

        List<Waypoint> all = WaypointsMod.menuWaypoints();
        if (all.isEmpty()) {
            graphics.text(this.font, "No waypoints in " + WaypointsMod.menuDimensionLabel() + ".",
                    cx - 160, TOP + 4, 0xFFAAAAAA, true);
        }

        int start = currentPage() * PER_PAGE;
        int end = Math.min(all.size(), start + PER_PAGE);
        int y = TOP;
        for (int i = start; i < end; i++) {
            Waypoint w = all.get(i);
            int rgb = WaypointsMod.resolveColor(w);
            graphics.fill(cx - 160, y + 2, cx - 150, y + 12, 0xFF000000 | rgb);
            graphics.text(this.font, w.name(), cx - 144, y + 1, 0xFFFFFFFF, true);
            String dimName = w.dimension() == null ? "Unknown" : w.dimension().replace("minecraft:", "");
            if (dimName.equals("the_nether")) dimName = "Nether";
            else if (dimName.equals("the_end")) dimName = "End";
            else if (dimName.equals("overworld")) dimName = "Overworld";
            String info = "[" + dimName + "] " + w.x() + " " + w.y() + " " + w.z();
            if (w.expiresAt() > 0) {
                long left = Math.max(0, (w.expiresAt() - System.currentTimeMillis()) / 1000);
                info += "  (" + left + "s left)";
            }
            graphics.text(this.font, info, cx - 144, y + 11, 0xFF999999, true);
            y += ROW_H;
        }
    }
}
