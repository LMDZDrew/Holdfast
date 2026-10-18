package dev.holdfast.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Readable pressure cue for the server's existing single-input window. Never judges an escape. */
final class GrabOverlay {
    private GrabOverlay() {}
    static void draw(GuiGraphics g,int width,int height,float partial) {
        var mc=Minecraft.getInstance();
        float elapsed=mc.level.getGameTime()-ClientState.promptStartClientTick+partial;
        float remaining=Math.max(0,Math.min(1,1-elapsed/ClientState.promptWindow));
        int cx=width/2,y=height/2+26,bw=210,x=cx-bw/2;
        int accent=remaining>.35f?0xFFCBAD79:0xFFE16B53;
        int pulse=90+(int)(35*(.5+.5*Math.sin(elapsed*(remaining>.35f?.35:.8))));
        g.fill(x-8,y-8,x+bw+8,y+56,0xB00F1110);
        // Restrained corner brackets and a segmented countdown: pressure stays legible over a moving walker.
        for(int side:new int[]{-1,1}) {
            int edge=cx+side*(bw/2+8);
            g.fill(Math.min(edge,edge-side*15),y-8,Math.max(edge,edge-side*15),y-6,accent);
            g.fill(edge-(side>0?2:0),y-8,edge+(side<0?2:0),y+7,accent);
        }
        g.drawCenteredString(mc.font,Component.translatable("hud.holdfast_core.grip"),cx,y,0xFFD8D4C9);
        Component action=Component.translatable(ClientState.promptGround?"hud.holdfast_core.kick_free":"hud.holdfast_core.shove",
                ClientCore.ACTION.getTranslatedKeyMessage());
        g.drawCenteredString(mc.font,action,cx,y+13,0xFFFFFFFF);
        int cells=24,lit=(int)Math.ceil(remaining*cells);
        for(int i=0;i<cells;i++) {
            int left=x+i*bw/cells,right=x+(i+1)*bw/cells-2;
            g.fill(left,y+29,right,y+34,i<lit?accent:0xFF383C38);
        }
        g.fill(x,y+37,x+bw,y+38,(pulse<<24)|(accent&0xFFFFFF));
        g.drawCenteredString(mc.font,Component.translatable("hud.holdfast_core.escape_once"),cx,y+43,0xFFB9BCAF);
    }
}
