package dev.holdfast.core.registry;

import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.entity.WalkerEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class CoreEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HoldfastCore.MODID);

    public static final RegistryObject<EntityType<WalkerEntity>> WALKER = ENTITIES.register("walker",
            () -> EntityType.Builder.<WalkerEntity>of(WalkerEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build(HoldfastCore.MODID + ":walker"));

    private CoreEntities() {}

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(WALKER.get(), WalkerEntity.createAttributes().build());
    }
}
