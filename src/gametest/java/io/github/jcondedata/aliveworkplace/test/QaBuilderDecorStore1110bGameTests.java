package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.store.StorehouseBlockEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * QA (qa-lane-b-1110-1837) of B1148, from its spec and the wiki (candles-leather.md: the builder "waits by his bench ...
 * while one is spare in the village (he fetches it)"): a blueprint's item frame that is in neither the bench's chest nor
 * made by anyone, but spare in the village Storehouse, is fetched from there and hung, and the Storehouse is left
 * without it.
 */
public class QaBuilderDecorStore1110bGameTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final UUID JESSE = UUID.fromString("5f1b2c3d-0000-4000-8000-0000000b1110");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos ROW = new BlockPos(8, 2, 10);
	private static final BlockPos FACE = ROW.south();
	private static final BlockPos STOREHOUSE = new BlockPos(26, 2, 26);
	private static final BlockPos STORE_CHEST = new BlockPos(23, 2, 26);

	/** The frame only in the Storehouse: fetched, hung, and the Storehouse's frame is gone (nothing doubled). */
	//$ gametest_ticks_batch HUGE '3000' '"qa1110bDecorStore"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1110bDecorStore")
	public void aFrameSpareInTheStorehouseIsFetchedAndHung(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.storehouses(helper);
		Leftovers.village(helper, 48);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean wasFree = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int wasDelay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean wasHelp = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(wasFree, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(wasDelay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(wasHelp, level.getServer());
		});

		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		BlockPos store = helper.absolutePos(STOREHOUSE);
		if (level.getPoiManager().getType(store).isEmpty()) {
			PoiTypes.forState(level.getBlockState(store)).ifPresent(type -> level.getPoiManager().add(store.immutable(), type));
		}
		((StorehouseBlockEntity) helper.getBlockEntity(STOREHOUSE)).setOwner(JESSE, "Jesse");
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Container storeChest = helper.getBlockEntity(STORE_CHEST);
		storeChest.setItem(0, new ItemStack(Items.ITEM_FRAME, 1));

		ResourceLocation id = frameBlueprint(helper);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(JESSE, "Jesse"));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, JESSE, "Jesse", id, placement);
		if (site == null || site.plan(level) == null) {
			throw new GameTestAssertException("the frame blueprint didn't start: " + id);
		}
		UUID siteId = site.id();
		helper.succeedWhen(() -> {
			BuildSite open = BuildSiteManager.get(level).get(siteId);
			helper.assertTrue(open == null, "still building: stage " + (open == null ? "" : open.stage() + ", " + open.status() + ", missing " + open.missing()));
			int hung = level.getEntitiesOfClass(ItemFrame.class, new AABB(helper.absolutePos(FACE)).inflate(0.5), Entity::isAlive).size();
			helper.assertTrue(hung == 1, hung + " frames hang; expected the Storehouse's 1");
			helper.assertTrue(storeChest.countItem(Items.ITEM_FRAME) == 0, "the Storehouse still holds its frame (" + storeChest.countItem(Items.ITEM_FRAME) + "), so the hung one came from nothing");
		});
	}

	/** One oak plank at ROW with an item frame on its south face, saved from the world. */
	private static ResourceLocation frameBlueprint(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(ROW, Blocks.OAK_PLANKS);
		ItemFrame frame = new ItemFrame(level, helper.absolutePos(FACE), Direction.SOUTH);
		helper.assertTrue(frame.survives(), "setup: the frame can't hang");
		level.addFreshEntity(frame);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qa1110b_store_frame_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(1, 1, 2), true, Blocks.STRUCTURE_VOID);
		frame.discard();
		helper.setBlock(ROW, Blocks.AIR);
		return id;
	}
}
