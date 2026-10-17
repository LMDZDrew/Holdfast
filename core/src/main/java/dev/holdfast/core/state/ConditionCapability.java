package dev.holdfast.core.state;

import dev.holdfast.core.HoldfastCore;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

public final class ConditionCapability implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<PlayerCondition> CONDITION = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation ID = new ResourceLocation(HoldfastCore.MODID, "condition");

    private final PlayerCondition condition = new PlayerCondition();
    private final LazyOptional<PlayerCondition> holder = LazyOptional.of(() -> condition);

    public static PlayerCondition of(Player player) {
        return player.getCapability(CONDITION).orElseGet(PlayerCondition::new);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        return CONDITION.orEmpty(cap, holder);
    }

    @Override
    public CompoundTag serializeNBT() {
        return condition.save();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        condition.load(tag);
    }
}
