package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA for 34.19's slower walk as B771 rebuilt it (a transient modifier on the elder's movement speed, kept by the
 * villager's own tick), from the spec: an elder, ageless or not, walks 15% slower; whether someone is an elder is never
 * saved. The edges LifeStageGameTests leaves out: the tick puts the pace on (and takes it off) without anyone asking,
 * asking again never stacks it, an ageless elder keeps it, and a save never carries it.
 */
public class QaElderPace1102GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** A villager grown {@code grownDaysAgo} days, his AI on (the pace is kept by his AI tick). */
	private static Villager villager(GameTestHelper helper, BlockPos at, long grownDaysAgo) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		LifeStages.setAdultSince(v, Chronicle.day(helper.getLevel()) - grownDaysAgo);
		Leftovers.after(helper, v::discard);
		return v;
	}

	private static AttributeInstance speed(Villager v) {
		return v.getAttribute(Attributes.MOVEMENT_SPEED);
	}

	private static float factor(Villager v) {
		AttributeInstance s = speed(v);
		return (float) (s.getValue() / s.getBaseValue());
	}

	private static void ages(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean ages = LifeStages.AGES;
		boolean ageless = LifeStages.AGELESS;
		Leftovers.after(helper, () -> {
			LifeStages.AGES = ages;
			LifeStages.AGELESS = ageless;
		});
		LifeStages.AGES = true;
		LifeStages.AGELESS = true;
	}

	/** Nobody calls pace: within a second of ticks the elder's own tick slows him, the younger one stays at full speed. */
	//$ gametest_ticks_batch AREA '100' '"qaElderPaceTick1102"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaElderPaceTick1102")
	public void qaTheElderIsSlowedByHisOwnTick(GameTestHelper helper) {
		ages(helper);
		Villager bram = villager(helper, new BlockPos(2, 2, 2), LifeStages.ELDER_DAYS + 10);
		Villager tom = villager(helper, new BlockPos(5, 2, 2), 30);
		helper.succeedWhen(() -> {
			helper.assertTrue(speed(bram).hasModifier(LifeStages.ELDER_PACE), "the elder's tick didn't slow him");
			helper.assertTrue(Math.abs(factor(bram) - LifeStages.ELDER_WALK) < 0.001f, "the elder walks at " + factor(bram) + " of his speed");
			helper.assertTrue(!speed(tom).hasModifier(LifeStages.ELDER_PACE) && factor(tom) == 1f, "the younger villager is slowed: " + factor(tom));
		});
	}

	/** A villager who isn't an elder any more (his day set back): his own tick takes the pace off again. */
	//$ gametest_ticks_batch AREA '100' '"qaElderPaceOff1102"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaElderPaceOff1102")
	public void qaThePaceComesOffWhenHeIsNoElder(GameTestHelper helper) {
		ages(helper);
		Villager bram = villager(helper, new BlockPos(2, 2, 2), LifeStages.ELDER_DAYS + 10);
		LifeStages.pace(bram);
		helper.assertTrue(speed(bram).hasModifier(LifeStages.ELDER_PACE), "setup: the elder isn't slowed");
		LifeStages.setAdultSince(bram, Chronicle.day(helper.getLevel()) - 5);
		helper.succeedWhen(() -> helper.assertTrue(!speed(bram).hasModifier(LifeStages.ELDER_PACE) && factor(bram) == 1f,
			"a villager grown 5 days still walks at " + factor(bram)));
	}

	/** Asked again and again (every walk asks): one modifier, the same 0.922, never 0.922 squared. */
	//$ gametest_ticks_batch AREA '60' '"qaElderPaceTwice1102"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaElderPaceTwice1102")
	public void qaAskingAgainNeverSlowsHimTwice(GameTestHelper helper) {
		ages(helper);
		Villager bram = villager(helper, new BlockPos(2, 2, 2), LifeStages.ELDER_DAYS + 10);
		for (int i = 0; i < 5; i++) {
			LifeStages.pace(bram);
		}
		long ours = speed(bram).getModifiers().stream().map(AttributeModifier::id).filter(LifeStages.ELDER_PACE::equals).count();
		helper.assertTrue(ours == 1, "the elder has " + ours + " pace modifiers");
		helper.assertTrue(Math.abs(factor(bram) - LifeStages.ELDER_WALK) < 0.001f, "asked five times, he walks at " + factor(bram));
		helper.succeed();
	}

	/** An ageless elder (the Evergreen Charm) is still an elder and walks just as slowly. */
	//$ gametest_ticks_batch AREA '60' '"qaElderPaceAgeless1102"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaElderPaceAgeless1102")
	public void qaAnAgelessElderStillWalksSlower(GameTestHelper helper) {
		ages(helper);
		Villager bram = villager(helper, new BlockPos(2, 2, 2), LifeStages.ELDER_DAYS + 500);
		ModAttachments.AGELESS.set(bram, true);
		LifeStages.pace(bram);
		helper.assertTrue(LifeStages.isAgeless(bram), "setup: not ageless");
		helper.assertTrue(Math.abs(factor(bram) - LifeStages.ELDER_WALK) < 0.001f, "the ageless elder walks at " + factor(bram));
		helper.succeed();
	}

	/**
	 * Saved and loaded: the save holds no pace modifier (whether he's an elder is worked out, never saved), the loaded
	 * villager is slowed once by his own tick.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaElderPaceSave1102"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaElderPaceSave1102")
	public void qaASaveNeverCarriesThePace(GameTestHelper helper) {
		ages(helper);
		Villager bram = villager(helper, new BlockPos(2, 2, 2), LifeStages.ELDER_DAYS + 10);
		LifeStages.pace(bram);
		helper.assertTrue(speed(bram).hasModifier(LifeStages.ELDER_PACE), "setup: the elder isn't slowed");
		CompoundTag saved = bram.saveWithoutId(new CompoundTag());
		helper.assertTrue(!saved.toString().contains(LifeStages.ELDER_PACE.toString()), "the save carries the pace: " + LifeStages.ELDER_PACE);
		Villager back = EntityType.VILLAGER.create(helper.getLevel());
		back.load(saved);
		back.setUUID(java.util.UUID.randomUUID());
		back.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 2, 2.5)));
		helper.getLevel().addFreshEntity(back);
		Leftovers.after(helper, back::discard);
		helper.assertTrue(LifeStages.isElder(back), "setup: the loaded villager isn't an elder");
		helper.succeedWhen(() -> {
			long ours = speed(back).getModifiers().stream().map(AttributeModifier::id).filter(LifeStages.ELDER_PACE::equals).count();
			helper.assertTrue(ours == 1 && Math.abs(factor(back) - LifeStages.ELDER_WALK) < 0.001f,
				"the loaded elder has " + ours + " pace modifiers and walks at " + factor(back));
		});
	}
}
