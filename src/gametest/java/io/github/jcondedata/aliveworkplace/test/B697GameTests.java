package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * B697: without Cobblemon (this run) a jobless villager doesn't take a Pokémon job by himself (QaNoCobblemonBlocksGameTests
 * covers each block); these check the other half of the promise: the block stays free, and a villager saved with the job
 * keeps it and his post.
 */
public class B697GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos STATION = new BlockPos(11, 2, 11);
	private static final BlockPos STANDING = new BlockPos(12, 2, 12);

	private static String job(Villager v) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()).toString();
	}

	/** A jobless villager by a Training Post leaves it free: no job, no claim on the post. */
	//$ gametest_ticks_batch AREA '400' '"b697Free"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b697Free")
	public void aTrainingPostStaysFreeWithoutCobblemon(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.assertTrue(!Stations.available(ModVillagers.TRAINER), "setup: this run has Cobblemon");
		helper.setDayTime(2000);
		helper.setBlock(STATION, ModBlocks.TRAINING_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
		helper.runAtTickTime(300, () -> {
			helper.assertTrue(v.getVillagerData().getProfession() == VillagerProfession.NONE, "the jobless villager became " + job(v));
			helper.assertTrue(StationsSpecGameTests.site(v).isEmpty(), "the jobless villager claimed the post: " + StationsSpecGameTests.site(v));
			helper.assertTrue(Stations.freeAt(helper.getLevel(), helper.absolutePos(STATION)), "the Training Post isn't free");
			helper.succeed();
		});
	}

	/** A Trainer saved before (with some experience), loaded without Cobblemon by his post, is still a Trainer at his post. */
	//$ gametest_ticks_batch AREA '400' '"b697Saved"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b697Saved")
	public void aSavedTrainerKeepsHisJobAndPost(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(STATION, ModBlocks.TRAINING_POST);
		Villager before = helper.spawn(EntityType.VILLAGER, STANDING);
		before.setVillagerData(before.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(2));
		before.setVillagerXp(20);
		CompoundTag saved = new CompoundTag();
		before.saveWithoutId(saved);
		before.discard();
		Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
		v.load(saved);
		v.setPos(helper.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(STANDING)));
		helper.runAtTickTime(300, () -> {
			helper.assertTrue(v.getVillagerData().getProfession() == ModVillagers.TRAINER, "the saved Trainer is now " + job(v));
			helper.assertTrue(StationsSpecGameTests.site(v).map(p -> p.equals(helper.absolutePos(STATION))).orElse(false),
				"the saved Trainer has no post: " + StationsSpecGameTests.site(v));
			helper.succeed();
		});
	}
}
