package dev.holdfast.core.compat.tacz;

import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.client.input.MeleeKey;
import dev.holdfast.core.client.ClientCore;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;

/** Optional client-only arbitration when TaCZ shove and Holdfast context share a physical key. */
public final class TaczClientActions {
    private TaczClientActions() {}
    public static void register() {
        // TaCZ draws its own first-person gun in RenderHandEvent; suppress that pass behind container GUIs.
        // The inventory's player/item previews are separate renders and remain available.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false,
                net.minecraftforge.client.event.RenderHandEvent.class, event -> {
            var mc=net.minecraft.client.Minecraft.getInstance();
            if(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>
                    && mc.player!=null && mc.player.getMainHandItem().getItem() instanceof com.tacz.guns.api.item.IGun)
                event.setCanceled(true);
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,false,GunMeleeEvent.class,event -> {
            if(event.getLogicalSide()==LogicalSide.CLIENT
                    && ClientCore.ACTION.getKey().equals(MeleeKey.MELEE_KEY.getKey()) && ClientCore.contextAvailable())
                event.setCanceled(true); // TaCZ posts this before its melee packet, sound and animation.
        });
    }
}
