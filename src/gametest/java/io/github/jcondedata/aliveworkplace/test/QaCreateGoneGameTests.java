package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.mechanic.MachineJob;
import io.github.jcondedata.aliveworkplace.mechanic.MachineWork;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane E, 2026-10-24): a world played with Create (Milestone 39) opened again after Create was taken out of the pack.
 * This suite's runtime has no Create, so it is the world after the removal. From the spec: "the mod must run without
 * them" (CLAUDE.md) and "don't break existing saves": nothing a villager carried for a Create machine is lost, and a
 * builder working on one of the Create builds is never left stuck on it.
 */
public class QaCreateGoneGameTests implements FabricGameTest {
	private static final String BIG_AREA = "aliveworkplace_test:big_area";
	private static final String AREA = "aliveworkplace_test:build_area";

	/** The wheat on the ground around the test area, and in {@code chest}. */
	private static int wheatAround(GameTestHelper helper, Container chest) {
		return chest.countItem(Items.WHEAT) + helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8),
			e -> e.isAlive() && e.getItem().is(Items.WHEAT)).stream().mapToInt(e -> e.getItem().getCount()).sum();
	}

	/**
	 * A farmer saved on the way to the village's millstone with 12 wheat (the Create save) is loaded where Create is gone:
	 * the trip can't go on, so the 12 wheat come back (into the chest by the composter, or at the farmer's feet), none
	 * kept in the record and none lost. The farmer goes through a real save and load of the entity.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa_create_gone_trip"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_create_gone_trip")
	public void aMachineTripSavedWithCreateGivesItsWheatBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.assertTrue(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("create"), "Create is loaded in the plain suite");
		ServerLevel level = helper.getLevel();
		BlockPos composter = new BlockPos(2, 2, 2);
		helper.setBlock(composter, Blocks.COMPOSTER);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Stations.assign(level, farmer, helper.absolutePos(composter), VillagerProfession.FARMER);
		helper.assertTrue(farmer.getVillagerData().getProfession() == VillagerProfession.FARMER, "setup: no farmer at the composter");
		ModAttachments.MACHINE_JOB.set(farmer, new MachineJob(helper.absolutePos(new BlockPos(5, 2, 2)), AliveWorkplace.id("millstone"),
			MachineJob.Stage.FEED, List.of(new ItemStack(Items.WHEAT, 12))));
		// The world saved and opened again: the farmer as the save holds them
		CompoundTag saved = new CompoundTag();
		farmer.save(saved);
		farmer.discard();
		Villager loaded = (Villager) EntityType.loadEntityRecursive(saved, level, e -> e);
		helper.assertTrue(loaded != null && level.addFreshEntity(loaded), "the saved farmer didn't load");
		helper.assertTrue(MachineWork.busy(loaded), "setup: the trip didn't come back with the save: " + ModAttachments.MACHINE_JOB.get(loaded));
		helper.succeedWhen(() -> {
			helper.assertTrue(!MachineWork.busy(loaded), "the farmer still holds the trip to a millstone that can't exist: "
				+ ModAttachments.MACHINE_JOB.get(loaded));
			helper.assertTrue(wheatAround(helper, chest) == 12, "the 12 wheat carried came to " + wheatAround(helper, chest));
			loaded.discard();
			helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(Items.WHEAT))
				.forEach(ItemEntity::discard);
		});
	}

	/**
	 * A builder was building the Watermill when Create was taken out: the site (saved by id) still loads and the builder
	 * finishes it from the store, the parts no block exists for any more skipped, instead of standing on the site for good.
	 */
	//$ gametest_ticks_batch BIG_AREA '12000' '"qa_create_gone_build"'
	@GameTest(template = BIG_AREA, timeoutTicks = 12000, batch = "qa_create_gone_build")
	public void aCreateBuildStartedWithCreateIsFinishedWithoutIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean helps = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(helps, level.getServer());
		});
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(5, 2, 5)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, StarterBlueprints.WATERMILL.id(), placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			// A blueprint that can't be read any more must not leave a site behind for the builder to stand on
			helper.succeedWhen(() -> helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"the Watermill can't be planned without Create, yet its site stays: " + site.stage() + " " + site.status()));
			return;
		}
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(1 + i % 4, 2, 0);
			helper.setBlock(barrel, Blocks.BARREL);
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		for (Item item : plan.materials().keySet()) {
			String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getNamespace();
			if (id.equals("create")) {
				throw new GameTestAssertException("a Create item is wanted without Create: " + item);
			}
		}
		helper.succeedWhen(() -> helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
			"the Watermill is still being built without Create: " + site.stage() + " " + site.status() + " missing " + site.missing()));
	}
}
