package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Create or edit one waypoint: name, position, color and timer. */
public class WaypointEditScreen extends Screen {
    private final Waypoint original; // null = creating a new one
    private final int returnPage;

    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private EditBox colorBox;
    private EditBox timerBox;
    private String error = "";

    public WaypointEditScreen(Waypoint original, int returnPage) {
        super(Component.literal(original == null ? "New Waypoint" : "Edit Waypoint"));
        this.original = original;
        this.returnPage = returnPage;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        // starting values
        String name;
        int x;
        int y;
        int z;
        String color;
        String timer;
        if (original != null) {
            name = original.name();
            x = original.x();
            y = original.y();
            z = original.z();
            color = original.color() == WaypointsMod.CHROMA ? "chroma"
                    : String.format("%06X", original.color() & 0xFFFFFF);
            timer = original.expiresAt() > 0
                    ? String.valueOf(Math.max(1, (original.expiresAt() - System.currentTimeMillis()) / 1000))
                    : "0";
        } else {
            Minecraft mc = Minecraft.getInstance();
            BlockPos p = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
            name = WaypointsMod.nextWaypointName(WaypointsMod.currentWorldId(), WaypointsMod.currentDimension());
            x = p.getX();
            y = p.getY();
            z = p.getZ();
            color = "red";
            timer = "0";
        }

        nameBox = new EditBox(this.font, cx - 30, 40, 180, 20, Component.literal("Name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(name);
        this.addRenderableWidget(nameBox);

        xBox = numberBox(cx - 30, 70, 56, x, "X");
        yBox = numberBox(cx + 30, 70, 56, y, "Y");
        zBox = numberBox(cx + 90, 70, 56, z, "Z");

        colorBox = new EditBox(this.font, cx - 30, 100, 100, 20, Component.literal("Color"));
        colorBox.setMaxLength(12);
        colorBox.setValue(color);
        this.addRenderableWidget(colorBox);

        this.addRenderableWidget(Button.builder(Component.literal("Next"),
                btn -> colorBox.setValue(WaypointsMod.nextColorName(colorBox.getValue())))
                .bounds(cx + 74, 100, 40, 20).build());

        timerBox = numberBox(cx - 30, 130, 100, 0, "Timer");
        timerBox.setValue(timer);

        this.addRenderableWidget(Button.builder(Component.literal("Save"), btn -> save())
                .bounds(cx - 150, 168, 70, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> back())
                .bounds(cx - 74, 168, 70, 20).build());

        if (original != null) {
            this.addRenderableWidget(Button.builder(Component.literal("Delete"), btn -> {
                WaypointsMod.remove(original);
                back();
            }).bounds(cx + 2, 168, 70, 20).build());
        }
    }

    private EditBox numberBox(int x, int y, int w, int value, String label) {
        EditBox box = new EditBox(this.font, x, y, w, 20, Component.literal(label));
        box.setMaxLength(9);
        box.setValue(String.valueOf(value));
        this.addRenderableWidget(box);
        return box;
    }

    private void back() {
        WaypointScreen.open(returnPage);
    }

    private void save() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            error = "Please type a name.";
            return;
        }

        int x;
        int y;
        int z;
        int secs;
        try {
            x = Integer.parseInt(xBox.getValue().trim());
            y = Integer.parseInt(yBox.getValue().trim());
            z = Integer.parseInt(zBox.getValue().trim());
        } catch (NumberFormatException e) {
            error = "X, Y and Z must be whole numbers.";
            return;
        }
        try {
            String t = timerBox.getValue().trim();
            secs = t.isEmpty() ? 0 : Integer.parseInt(t);
        } catch (NumberFormatException e) {
            error = "Timer must be a number of seconds (0 = forever).";
            return;
        }

        Integer color = WaypointsMod.parseColor(colorBox.getValue().trim());
        if (color == null) {
            error = "Color: use a name (red, green...), hex like ff8800, or chroma.";
            return;
        }

        long expires = secs > 0 ? System.currentTimeMillis() + secs * 1000L : 0L;
        String dim = original != null ? original.dimension() : WaypointsMod.currentDimension();
        WaypointsMod.upsert(original, new Waypoint(name, x, y, z, color, dim, expires,
                original != null ? original.worldId() : WaypointsMod.currentWorldId()));
        back();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int cx = this.width / 2;
        graphics.text(this.font, this.title.getString(), cx - 150, 20, 0xFFFFFFFF, true);
        graphics.text(this.font, "Name", cx - 150, 46, 0xFFFFFFFF, true);
        graphics.text(this.font, "Position", cx - 150, 76, 0xFFFFFFFF, true);
        graphics.text(this.font, "Color", cx - 150, 106, 0xFFFFFFFF, true);
        graphics.text(this.font, "Timer (sec)", cx - 150, 136, 0xFFFFFFFF, true);
        graphics.text(this.font, "0 = never disappears", cx + 76, 136, 0xFF999999, true);

        // live color preview
        Integer c = WaypointsMod.parseColor(colorBox.getValue().trim());
        if (c != null) {
            int rgb = c == WaypointsMod.CHROMA
                    ? WaypointsMod.resolveColor(new Waypoint("", 0, 0, 0, WaypointsMod.CHROMA, "", 0L, ""))
                    : (c & 0xFFFFFF);
            graphics.fill(cx + 120, 104, cx + 140, 116, 0xFF000000 | rgb);
        }

        if (!error.isEmpty()) {
            graphics.text(this.font, error, cx - 150, 196, 0xFFFF5555, true);
        }
    }
}
