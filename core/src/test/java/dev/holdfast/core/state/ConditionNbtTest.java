package dev.holdfast.core.state;

import dev.holdfast.core.rules.BodyRegion;
import dev.holdfast.core.rules.Difficulty;
import dev.holdfast.core.rules.InfectionRules;
import dev.holdfast.core.rules.InjuryRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/** Persistence: the saved condition survives a save/load through real NBT bytes (relog and restart path). */
class ConditionNbtTest {
    private static CompoundTag throughBytes(CompoundTag t) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.write(t, new DataOutputStream(bytes));
        return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }

    @Test
    void biteInjuryAndClockSurviveSaveAndLoad() throws IOException {
        PlayerCondition c = new PlayerCondition();
        for (int i = 0; i < 1234; i++) c.tick();
        c.bitten(BodyRegion.ARM, Difficulty.SEVERE);
        c.blockedBite();
        c.setMovement(InjuryRules.Movement.LIMP_SEVERE);
        for (int i = 0; i < 600; i++) c.tick();

        PlayerCondition back = new PlayerCondition();
        back.load(throughBytes(c.save()));
        assertEquals(c.onlineTicks(), back.onlineTicks());
        assertTrue(back.infected());
        assertEquals(BodyRegion.ARM, back.bite().region());
        assertEquals(1234, back.bite().startTick());
        assertEquals(Difficulty.SEVERE, back.bite().difficulty());
        assertEquals(1, back.blockedBites());
        assertEquals(InjuryRules.Movement.LIMP_SEVERE, back.movement());
        assertEquals(1, back.wounds().get(BodyRegion.ARM));
        assertEquals(c.infectionProgress(), back.infectionProgress(), 1e-12);
        assertTrue(InfectionRules.amputationEligible(back.bite(), back.onlineTicks()));
    }

    @Test
    void secondBiteDoesNotRestartTheDecline() {
        PlayerCondition c = new PlayerCondition();
        c.bitten(BodyRegion.LEG, Difficulty.DEFAULT);
        for (int i = 0; i < 100; i++) c.tick();
        c.bitten(BodyRegion.NECK, Difficulty.DEFAULT);
        assertEquals(BodyRegion.LEG, c.bite().region());
        assertEquals(0, c.bite().startTick());
        assertEquals(2, c.wounds().size());
    }

    @Test
    void emptyOrCorruptTagLoadsClean() {
        PlayerCondition c = new PlayerCondition();
        CompoundTag t = new CompoundTag();
        t.putString("movement", "NOT_A_STATE");
        c.load(t);
        assertFalse(c.infected());
        assertEquals(InjuryRules.Movement.NORMAL, c.movement());
    }
}
