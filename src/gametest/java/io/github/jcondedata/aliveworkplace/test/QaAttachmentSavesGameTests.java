package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.cup.CupDays;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.legend.GrandRebuild;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (lane E, saves): the saved villager data no other test saves and reads back (the Grand Architect's rebuild, the
 * Cup delegate, the berry farmer's goal and plot, the blessed child, the last foal, the habitat lure and day, the golem's
 * role) comes back the same after the villager is saved with the chunk and loaded again; and the record-shaped ones
 * read from a save written before a field existed get that field's default.
 */
public class QaAttachmentSavesGameTests implements FabricGameTest {

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void villagerDataComesBackTheSameAfterASave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager v = EntityType.VILLAGER.create(level);
		UUID site = new UUID(0x1234L, 0x5678L);
		GrandRebuild.State architect = new GrandRebuild.State(42L, true, Optional.of(site));
		CupDays.Delegate delegate = new CupDays.Delegate(new BlockPos(100, 64, -200), new UUID(7L, 9L), 3, new BlockPos(-5, 70, 8), 17L);
		ResourceLocation goal = ResourceLocation.fromNamespaceAndPath("minecraft", "sweet_berries");
		FieldJob plot = new FieldJob(new BoundingBox(1, 60, 2, 9, 61, 12), true);
		ModAttachments.ARCHITECT.set(v, architect);
		ModAttachments.CUP_DELEGATE.set(v, delegate);
		ModAttachments.BERRY_GOAL.set(v, goal);
		ModAttachments.BERRY_PLOT.set(v, plot);
		ModAttachments.BLESSED_CHILD.set(v, 0.375);
		ModAttachments.LAST_FOAL.set(v, 123456789L);
		ModAttachments.HABITAT_LURE.set(v, "cobblemon:poke_snack");
		ModAttachments.HABITAT_DAY.set(v, 31L);

		Villager back = EntityType.VILLAGER.create(level);
		back.load(v.saveWithoutId(new CompoundTag()));
		helper.assertTrue(architect.equals(ModAttachments.ARCHITECT.get(back)), "the rebuild: " + ModAttachments.ARCHITECT.get(back));
		helper.assertTrue(delegate.equals(ModAttachments.CUP_DELEGATE.get(back)), "the Cup delegate: " + ModAttachments.CUP_DELEGATE.get(back));
		helper.assertTrue(goal.equals(ModAttachments.BERRY_GOAL.get(back)), "the berry goal: " + ModAttachments.BERRY_GOAL.get(back));
		helper.assertTrue(plot.equals(ModAttachments.BERRY_PLOT.get(back)), "the berry plot: " + ModAttachments.BERRY_PLOT.get(back));
		helper.assertTrue(Double.valueOf(0.375).equals(ModAttachments.BLESSED_CHILD.get(back)), "the blessed child: " + ModAttachments.BLESSED_CHILD.get(back));
		helper.assertTrue(Long.valueOf(123456789L).equals(ModAttachments.LAST_FOAL.get(back)), "the last foal: " + ModAttachments.LAST_FOAL.get(back));
		helper.assertTrue("cobblemon:poke_snack".equals(ModAttachments.HABITAT_LURE.get(back)), "the habitat lure: " + ModAttachments.HABITAT_LURE.get(back));
		helper.assertTrue(Long.valueOf(31L).equals(ModAttachments.HABITAT_DAY.get(back)), "the habitat day: " + ModAttachments.HABITAT_DAY.get(back));

		Villager plain = EntityType.VILLAGER.create(level);
		plain.load(EntityType.VILLAGER.create(level).saveWithoutId(new CompoundTag()));
		helper.assertTrue(!ModAttachments.ARCHITECT.has(plain) && !ModAttachments.CUP_DELEGATE.has(plain) && !ModAttachments.BERRY_PLOT.has(plain),
			"a villager saved with none of it has some");
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anIronGolemsRoleComesBackAfterASave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		IronGolem golem = EntityType.IRON_GOLEM.create(level);
		ModAttachments.GOLEM_ROLE.set(golem, "hauler");
		IronGolem back = EntityType.IRON_GOLEM.create(level);
		back.load(golem.saveWithoutId(new CompoundTag()));
		helper.assertTrue("hauler".equals(ModAttachments.GOLEM_ROLE.get(back)), "the golem's role: " + ModAttachments.GOLEM_ROLE.get(back));
		helper.succeed();
	}

	/** The record-shaped data as an older save holds it: fields written later are missing. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void recordsSavedWithoutTheirLaterFieldsGetTheDefaults(GameTestHelper helper) {
		GrandRebuild.State state = parse(GrandRebuild.State.CODEC, new CompoundTag());
		helper.assertTrue(GrandRebuild.State.EMPTY.equals(state), "an empty rebuild: " + state);
		CompoundTag paused = new CompoundTag();
		paused.putBoolean("paused", true);
		GrandRebuild.State onlyPaused = parse(GrandRebuild.State.CODEC, paused);
		helper.assertTrue(onlyPaused.paused() && onlyPaused.lastDay() == -1 && onlyPaused.site().isEmpty(), "a paused-only rebuild: " + onlyPaused);

		CompoundTag tier = new CompoundTag();
		tier.putInt("tier", 2);
		CupDays.Delegate delegate = parse(CupDays.Delegate.CODEC, tier);
		helper.assertTrue(delegate.tier() == 2 && delegate.home().equals(BlockPos.ZERO) && delegate.day() == 0L, "a delegate with only its tier: " + delegate);

		CompoundTag job = new CompoundTag();
		job.put("box", BoundingBox.CODEC.encodeStart(NbtOps.INSTANCE, new BoundingBox(0, 0, 0, 3, 1, 3)).getOrThrow());
		FieldJob plot = parse(FieldJob.CODEC, job);
		helper.assertTrue(!plot.adopted() && plot.box().equals(new BoundingBox(0, 0, 0, 3, 1, 3)), "a plot saved before 'adopted': " + plot);
		helper.succeed();
	}

	private static <T> T parse(Codec<T> codec, CompoundTag tag) {
		return codec.parse(NbtOps.INSTANCE, tag).getOrThrow();
	}
}
