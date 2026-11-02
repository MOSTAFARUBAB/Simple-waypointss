package com.rubab.simplewaypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.UUID;

public final class WaypointScreen extends Screen {
    public WaypointScreen() { super(Component.literal("Simple Waypoints")); }
    @Override protected void init() {
        int cx=width/2, y=44;
        for (Waypoint w : WaypointStore.all().stream().limit(8).toList()) {
            addRenderableWidget(Button.builder(Component.literal((w.visible()?"● ":"○ ")+w.name()+"  ["+(int)w.x()+", "+(int)w.y()+", "+(int)w.z()+"]"),
                b -> WaypointStore.toggle(w)).bounds(cx-150,y,300,20).build());
            y+=24;
        }
        addRenderableWidget(Button.builder(Component.literal("Add waypoint here"),b->addHere()).bounds(cx-100,height-44,200,20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"),b->onClose()).bounds(cx-100,height-22,200,18).build());
    }
    private void addHere() {
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null)return;
        var p=mc.player.position();
        String dim=mc.level.dimension().location().toString();
        WaypointStore.add(new Waypoint(UUID.randomUUID(),"Waypoint "+(WaypointStore.all().size()+1),dim,p.x,p.y,p.z,0x55DDEE,true));
        rebuildWidgets();
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        renderBackground(g,mx,my,delta);
        g.drawCenteredString(font,title,width/2,18,0xFFFFFF);
        g.drawCenteredString(font,"Click a waypoint to show or hide it",width/2,30,0xAAAAAA);
        super.render(g,mx,my,delta);
    }
}
