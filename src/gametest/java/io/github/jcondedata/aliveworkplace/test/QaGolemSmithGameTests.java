package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA for 29.15, the Golem Smith, written from the spec: a Smith on strike or with Legends off builds nothing and takes
 * nothing; the cap holds at 9 villagers and opens at the 10th; the cost may lie across the chests by the smithing table,
 * and exactly the cost is taken; a stone hoe is no iron hoe; a Wall Sentry has twice a golem's health and its name over
 * its head; the forge's choice and last day survive a save; the player's sneak-click tells them what comes next and
 * what it costs, and only an empty-handed sneak-click chooses.
 */
public class QaGolemSmithGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final BlockPos TABLE = new BlockPos(10, 2, 10);

	private static boolean moods;
	private static boolean needs;

	private static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Caravans.Data.get(level).setWants(hall, Component.literal("Forgeton"), List.of());
		moods = Moods.ENABLED;
		needs = LegendNeeds.ENABLED;
		Moods.ENABLED = false;
		LegendNeeds.ENABLED = false;
		Leftovers.after(helper, () -> {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			Legends.ENABLED = true;
			Caravans.Data.get(level).remove(hall);
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(96), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			for (IronGolem g : level.getEntitiesOfClass(IronGolem.class, helper.getBounds().inflate(32), g -> true)) {
				g.discard();
			}
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
	}

	private static Legend staged(GameTestHelper helper) {
		Legends.reload(helper.getLevel().getServer().getResourceManager());
		Legend smith = Legends.get(GolemSmith.ID).orElseThrow(() -> new AssertionError("golem_smith.json didn't load"));
		Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
		map.put(GolemSmith.ID, smith);
		Legends.setForTest(map);
		return smith;
	}

	private static Villager smith(GameTestHelper helper, Legend legend) {
		helper.setBlock(TABLE, Blocks.SMITHING_TABLE);
		helper.setBlock(TABLE.east(), Blocks.CHEST);
		Villager v = helper.spawn(EntityType.VILLAGER, TABLE.north(2));
		Legends.make(helper.getLevel(), v, legend, "test");
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(TABLE)));
		return v;
	}

	private static void villagers(GameTestHelper helper, int n, int z) {
		for (int i = 0; i < n; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(14 + i % 6, 2, z + 2 * (i / 6)));
		}
	}

	private static void haulerCost(Container chest) {
		chest.setItem(0, new ItemStack(Items.IRON_BLOCK, 4));
		chest.setItem(1, new ItemStack(Items.CARVED_PUMPKIN, 1));
		chest.setItem(2, new ItemStack(Items.CHEST, 1));
	}

	private static LegendData striking(LegendData d, long since) {
		return new LegendData(d.id(), d.name(), d.guest(), d.hall(), d.since(), d.lastDay(), d.unmet(), since, d.lastLuxury(), d.way(), d.checked());
	}

	/** On strike: no golem, nothing taken, the usual mending pace. Legends off: the round builds nothing. Back on: a golem. */
	//$ gametest_ticks_batch AREA '100' '"qaSmithStrike"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSmithStrike")
	public void aStrikeOrLegendsOffBuildNothing(GameTestHelper helper) {
		setUp(helper);
		Legend legend = staged(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager smith = smith(helper, legend);
		villagers(helper, 4, 4);
		Container chest = helper.getBlockEntity(TABLE.east());
		haulerCost(chest);
		LegendData data = ModAttachments.LEGEND.get(smith);
		helper.assertTrue(data != null && data.settled() && !data.onStrike(), "not a settled smith: " + data);
		ModAttachments.LEGEND.set(smith, striking(data, Chronicle.day(level)));
		helper.assertTrue(GolemSmith.forgeOne(level, hall, smith) == null, "a golem from a smith on strike");
		GolemSmith.round(level, hall);
		helper.assertTrue(GolemSmith.forged(level, hall) == 0, "the round built a golem during a strike");
		helper.assertTrue(chest.countItem(Items.IRON_BLOCK) == 4 && chest.countItem(Items.CHEST) == 1, "a strike took the costs");
		helper.assertTrue(GolemSmith.mendFactor(smith) == 1f, "a striking smith mends twice as fast");
		ModAttachments.LEGEND.set(smith, striking(data, -1L));
		Legends.ENABLED = false;
		try {
			GolemSmith.round(level, hall);
			helper.assertTrue(GolemSmith.forged(level, hall) == 0 && chest.countItem(Items.IRON_BLOCK) == 4, "a golem with Legends off");
			helper.assertTrue(GolemSmith.mendFactor(smith) == 1f, "twice the mending with Legends off");
		} finally {
			Legends.ENABLED = true;
		}
		GolemSmith.round(level, hall);
		helper.assertTrue(GolemSmith.forged(level, hall) == 1 && chest.countItem(Items.IRON_BLOCK) == 0, "no golem once the strike ended: " + GolemSmith.forged(level, hall));
		helper.succeed();
	}

	/** 9 villagers (the smith and 8): one golem and no second even after 2 days; the 10th villager opens the second. */
	//$ gametest_ticks_batch AREA '100' '"qaSmithCap"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSmithCap")
	public void theCapHoldsAtNineAndOpensAtTen(GameTestHelper helper) {
		setUp(helper);
		Legend legend = staged(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager smith = smith(helper, legend);
		villagers(helper, 8, 4);
		Container chest = helper.getBlockEntity(TABLE.east());
		chest.setItem(0, new ItemStack(Items.IRON_BLOCK, 12));
		chest.setItem(1, new ItemStack(Items.CARVED_PUMPKIN, 3));
		chest.setItem(2, new ItemStack(Items.CHEST, 3));
		helper.assertTrue(GolemSmith.forgeOne(level, hall, smith) != null, "9 villagers: the first golem");
		long today = Chronicle.day(level);
		ModAttachments.GOLEM_FORGE.set(smith, new GolemSmith.Forge("hauler", today - 2));
		helper.assertTrue(GolemSmith.forgeOne(level, hall, smith) == null, "a second golem for 9 villagers");
		helper.assertTrue(chest.countItem(Items.IRON_BLOCK) == 8, "the cap took the costs: " + chest.countItem(Items.IRON_BLOCK));
		helper.assertTrue(GolemSmith.forge(smith).lastDay() == today - 2, "a refused golem moved the forge's day");
		villagers(helper, 1, 10);
		helper.assertTrue(GolemSmith.forgeOne(level, hall, smith) != null, "10 villagers: no second golem");
		helper.assertTrue(GolemSmith.forged(level, hall) == 2, "forged: " + GolemSmith.forged(level, hall));
		helper.succeed();
	}

	/**
	 * A Wall Sentry from costs spread over two chests by the table: exactly the cost is taken, it has twice a golem's
	 * health and its name over its head; a stone hoe doesn't make a farmhand.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaSmithCosts"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSmithCosts")
	public void costsAcrossTwoChestsAndTheSentrysHealth(GameTestHelper helper) {
		setUp(helper);
		Legend legend = staged(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager smith = smith(helper, legend);
		villagers(helper, 4, 4);
		helper.setBlock(TABLE.west(), Blocks.CHEST);
		Container east = helper.getBlockEntity(TABLE.east());
		Container west = helper.getBlockEntity(TABLE.west());
		east.setItem(0, new ItemStack(Items.IRON_BLOCK, 3));
		east.setItem(1, new ItemStack(Items.STONE_HOE));
		west.setItem(0, new ItemStack(Items.IRON_BLOCK, 3));
		west.setItem(1, new ItemStack(Items.CARVED_PUMPKIN, 2));
		ModAttachments.GOLEM_FORGE.set(smith, new GolemSmith.Forge("farmhand", -1L));
		helper.assertTrue(GolemSmith.forgeOne(level, hall, smith) == null, "a stone hoe made a farmhand");
		helper.assertTrue(east.countItem(Items.IRON_BLOCK) + west.countItem(Items.IRON_BLOCK) == 6, "a refused farmhand took iron");
		ModAttachments.GOLEM_FORGE.set(smith, new GolemSmith.Forge("sentry", -1L));
		west.setItem(2, new ItemStack(Items.SHIELD));
		IronGolem sentry = GolemSmith.forgeOne(level, hall, smith);
		helper.assertTrue(sentry != null, "the cost across two chests made no sentry");
		helper.assertTrue(east.countItem(Items.IRON_BLOCK) + west.countItem(Items.IRON_BLOCK) == 2, "not 4 iron blocks taken: "
			+ (6 - east.countItem(Items.IRON_BLOCK) - west.countItem(Items.IRON_BLOCK)));
		helper.assertTrue(west.countItem(Items.CARVED_PUMPKIN) == 1 && west.countItem(Items.SHIELD) == 0 && east.countItem(Items.STONE_HOE) == 1, "the other costs");
		IronGolem plain = EntityType.IRON_GOLEM.create(level);
		double usual = plain.getAttributeBaseValue(Attributes.MAX_HEALTH);
		plain.discard();
		helper.assertTrue(sentry.getMaxHealth() == 2 * usual && sentry.getHealth() == sentry.getMaxHealth(), "the sentry's health: " + sentry.getHealth() + "/" + sentry.getMaxHealth());
		String title = Component.translatable("entity.aliveworkplace.golem.sentry").getString();
		helper.assertTrue(sentry.isCustomNameVisible() && sentry.getCustomName() != null && sentry.getCustomName().getString().equals(title), "the name: " + sentry.getCustomName());
		helper.assertTrue(sentry.isPlayerCreated(), "a forged golem isn't marked player-made (it would fight the village's players)");
		helper.succeed();
	}

	/** The forge's choice and last day survive a save; an unknown choice in an old save falls back to the hauler. */
	//$ gametest_ticks_batch AREA '100' '"qaSmithSaved"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSmithSaved")
	public void theForgeSurvivesASave(GameTestHelper helper) {
		setUp(helper);
		Legend legend = staged(helper);
		ServerLevel level = helper.getLevel();
		Villager smith = smith(helper, legend);
		ModAttachments.GOLEM_FORGE.set(smith, new GolemSmith.Forge("sentry", 41L));
		Villager loaded = EntityType.VILLAGER.create(level);
		loaded.load(smith.saveWithoutId(new CompoundTag()));
		GolemSmith.Forge forge = GolemSmith.forge(loaded);
		helper.assertTrue(forge.role() == GolemSmith.Role.SENTRY && forge.lastDay() == 41L, "after a save: " + forge);
		helper.assertTrue(new GolemSmith.Forge("dragon", 3L).role() == GolemSmith.Role.HAULER, "an unknown choice isn't the hauler");
		helper.assertTrue(GolemSmith.forge(EntityType.VILLAGER.create(level)).role() == GolemSmith.Role.HAULER, "a new forge doesn't start at the hauler");
		loaded.discard();
		helper.succeed();
	}

	/** Sneak-click with an empty hand: the next golem and its cost in chat. A click with an item, or not sneaking, chooses nothing. */
	//$ gametest_ticks_batch AREA '100' '"qaSmithChoose"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSmithChoose")
	public void theSneakClickTellsWhatComesNextAndWhatItCosts(GameTestHelper helper) {
		setUp(helper);
		Legend legend = staged(helper);
		ServerLevel level = helper.getLevel();
		Villager smith = smith(helper, legend);
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = QaOldSageGameTests.listener(helper, heard, "SmithChooser");
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, smith, null);
		helper.assertTrue(GolemSmith.forge(smith).role() == GolemSmith.Role.HAULER, "a sneak-click holding iron chose: " + GolemSmith.forge(smith));
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		heard.clear();
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, smith, null);
		helper.assertTrue(GolemSmith.forge(smith).role() == GolemSmith.Role.FARMHAND, "the sneak-click: " + GolemSmith.forge(smith));
		String farmhand = Component.translatable("entity.aliveworkplace.golem.farmhand").getString();
		String hoe = Items.IRON_HOE.getDescription().getString();
		List<String> said = heard.stream().map(Component::getString).toList();
		helper.assertTrue(said.stream().anyMatch(s -> s.contains("will build a " + farmhand + " next") && s.contains("4 iron blocks, a carved pumpkin")
			&& s.contains(hoe) && s.contains("smithing table")), "the line: " + said);
		player.setShiftKeyDown(false);
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, smith, null);
		helper.assertTrue(GolemSmith.forge(smith).role() == GolemSmith.Role.FARMHAND, "a plain click chose: " + GolemSmith.forge(smith));
		helper.succeed();
	}
}
