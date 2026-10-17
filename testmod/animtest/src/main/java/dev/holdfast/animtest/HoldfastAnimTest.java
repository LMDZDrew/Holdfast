package dev.holdfast.animtest;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.util.Map;

/**
 * TEST HARNESS ONLY. Loads the exported Holdfast walker, crawler and player clips so they can be checked in game.
 * Nothing here decides gameplay: no hits, bites, escapes or kills. Every log line starts with "[holdfast-test]".
 */
@Mod(HoldfastAnimTest.MODID)
public final class HoldfastAnimTest {
    public static final String MODID = "holdfast_animtest";
    /** Namespace of the exported assets (art/characters/export/assets/holdfast). */
    public static final String ASSETS = "holdfast";
    public static final Logger LOG = LogUtils.getLogger();

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final RegistryObject<EntityType<TestWalker>> TEST_WALKER = ENTITIES.register("test_walker",
            () -> EntityType.Builder.<TestWalker>of(TestWalker::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build(MODID + ":test_walker"));

    /**
     * Test weapons: preview weapon geometry in Player Animator's hand frame (art/characters/source/gen_test_items.py),
     * so held-item orientation can be judged against the geometry the clips were solved for. Not real weapons.
     */
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final Map<String, RegistryObject<Item>> TEST_WEAPONS = Map.of(
            "knife", testItem("test_knife"), "bat", testItem("test_bat"), "axe", testItem("test_axe"),
            "machete", testItem("test_machete"), "crutch", testItem("test_crutch"));

    private static RegistryObject<Item> testItem(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties().stacksTo(1)));
    }

    public HoldfastAnimTest() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(this::onAttributes);
        modBus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(TestCommands::register);
        MinecraftForge.EVENT_BUS.addListener(TestCommands::onServerTick);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.holdfast.animtest.client.ClientInit.init(modBus));
        LOG.info("[holdfast-test] test harness constructed (walker clips {}, crawler clips {}, player clips {}, pairs {})",
                Clips.WALKER.size(), Clips.CRAWLER.size(), Clips.PLAYER.size(), Clips.PAIRS.size());
    }

    private void onAttributes(EntityAttributeCreationEvent event) {
        event.put(TEST_WALKER.get(), Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0D).build());
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
    }
}
