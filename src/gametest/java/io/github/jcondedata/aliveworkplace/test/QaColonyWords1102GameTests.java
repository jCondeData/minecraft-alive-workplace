package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.colony.ColonyCharterItem;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1102-1853), ROADMAP 33.8-33.10's player-visible sentences at counts of one and two: the settlers' order
 * sent short ("still miss 1 log, 1 bread, 1 bed"), the Colonies tab's "Waiting for", "They leave with what there is in
 * 1 day", "1 settler", the requests board's lines, and "On the road to Newbrook, there in a minute" / "in 2 minutes".
 * Each test is alone in its batch, colonies on.
 */
public class QaColonyWords1102GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final BlockPos STOREHOUSE = new BlockPos(13, 2, 9);
	private static final BlockPos CHEST = new BlockPos(14, 2, 9);

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, ServerPlayer player) {
		GlobalPos mother() {
			return GlobalPos.of(level.dimension(), hall);
		}

		RealmData.Order order() {
			return RealmData.get(level.getServer()).orderOf(mother());
		}
	}

	/** A City's hall owned by a survival player, its village 8 blocks round, colonies on; Bram and Tomas can be spared. */
	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		boolean enabled = Colonies.ENABLED;
		VillageRanks.Rank needed = Colonies.RANK;
		int every = VillageNeeds.CHECK_EVERY;
		int radius = VillageHalls.RADIUS;
		int wait = Settlers.WAIT_TICKS;
		int delay = Settlers.MUSTER_DELAY;
		Colonies.ENABLED = true;
		Colonies.RANK = VillageRanks.Rank.CITY;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		VillageHalls.RADIUS = 8;
		Settlers.MUSTER_DELAY = -1;
		RealmData.get(level.getServer()).clearColonies();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			Colonies.ENABLED = enabled;
			Colonies.RANK = needed;
			VillageNeeds.CHECK_EVERY = every;
			VillageHalls.RADIUS = radius;
			Settlers.WAIT_TICKS = wait;
			Settlers.MUSTER_DELAY = delay;
			RealmData.get(level.getServer()).clearColonies();
			Caravans.Data.get(level).remove(hall);
		});
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setOwner(player.getUUID(), "Owner");
		entity.setRank(VillageRanks.Rank.CITY);
		villager(helper, new BlockPos(9, 2, 9), "Bram", ModVillagers.BUILDER, 1);
		villager(helper, new BlockPos(9, 2, 13), "Hale", ModVillagers.BUILDER, 3);
		villager(helper, new BlockPos(10, 2, 11), "Tomas", VillagerProfession.NONE, 1);
		villager(helper, new BlockPos(13, 2, 13), "Finn", VillagerProfession.NONE, 1);
		return new Village(level, hall, entity, player);
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job, int jobLevel) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		if (job != VillagerProfession.NONE) {
			v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(jobLevel));
			v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(pos)));
			v.refreshBrain(helper.getLevel());
		}
		return v;
	}

	/** The Storehouse's chest with every supply but {@code short} logs, bread and beds. */
	private static void shortBy(GameTestHelper helper, int by) {
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		ItemStack[] stacks = {new ItemStack(Items.OAK_LOG, 64 - by), new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.COBBLESTONE, 64),
			new ItemStack(Items.BREAD, 32 - by), new ItemStack(Items.TORCH, 16), new ItemStack(Items.GLASS_PANE, 12)};
		for (int i = 0; i < stacks.length; i++) {
			chest.setItem(i, stacks[i]);
		}
		for (int i = 0; i < 3 - by; i++) {
			chest.setItem(stacks.length + i, new ItemStack(Items.RED_BED));
		}
	}

	private static ItemStack charter(Village v) {
		ItemStack stack = ColonyCharterItem.of(v.level(), v.hall());
		stack.set(ModComponents.COLONY_CHARTER, stack.get(ModComponents.COLONY_CHARTER).withSpot(v.hall().offset(400, 0, 0)));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("Newbrook"));
		return stack;
	}

	private static List<String> strings(List<Component> lines) {
		return lines.stream().map(Component::getString).toList();
	}

	/** Sent short by {@code by} of logs, bread and beds: the sentence, the tab's lines, the requests board. */
	private static void sentShort(GameTestHelper helper, int by, String supplies) {
		Village v = village(helper);
		shortBy(helper, by);
		Settlers.WAIT_TICKS = by * 24000; // so the tab's "leave anyway" line says `by` days
		Settlers.Sent sent = Settlers.send(v.level(), v.hall(), v.player(), charter(v));
		String said = sent.message().getString();
		helper.assertTrue(sent.ok() && said.equals("Bram and Tomas will set out for Newbrook. The Storehouses still miss " + supplies
			+ ": bring it, or they leave in 3 days with what there is."), "sent short by " + by + ": " + said);
		RealmData.Order order = v.order();
		helper.assertTrue(order != null, "no order");
		List<String> lines = strings(Settlers.lines(v.level(), order));
		helper.assertTrue(lines.contains("Waiting for: " + supplies), "the tab's lines: " + lines);
		String leave = by == 1 ? "They leave with what there is in 1 day" : "They leave with what there is in " + by + " days";
		helper.assertTrue(lines.contains(leave), "the tab's lines (want '" + leave + "'): " + lines);
		helper.assertTrue(lines.contains("Settlers: Bram and Tomas"), "the tab's lines: " + lines);
		int beds = 3 - by;
		String packed = "Packed: " + (64 - by) + " logs, 64 planks, 64 cobblestone, " + (32 - by) + " bread, 16 torches, 12 glass panes, " + beds
			+ (beds == 1 ? " bed" : " beds");
		helper.assertTrue(lines.contains(packed), "the tab's lines (want '" + packed + "'): " + lines);
		List<String> board = Settlers.requests(v.level(), helper.absolutePos(STOREHOUSE)).stream().map(r -> r.what().getString()).toList();
		String log = by == 1 ? "1 log" : by + " logs";
		String bed = by == 1 ? "1 bed" : by + " beds";
		helper.assertTrue(board.contains(log) && board.contains(bed), "the requests board (want '" + log + "', '" + bed + "'): " + board);
		helper.succeed();
	}

	/** Short by one log, one bread and one bed: "1 log, 1 bread, 1 bed", "in 1 day", the board's "1 log" and "1 bed". */
	//$ gametest_ticks_batch AREA '100' '"qaColWords1102ShortOne"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaColWords1102ShortOne")
	public void qaShortByOneOfEachSaysOneLogOneBreadOneBed(GameTestHelper helper) {
		sentShort(helper, 1, "1 log, 1 bread, 1 bed");
	}

	/** Short by two of each: "2 logs, 2 bread, 2 beds", "in 2 days". */
	//$ gametest_ticks_batch AREA '100' '"qaColWords1102ShortTwo"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaColWords1102ShortTwo")
	public void qaShortByTwoOfEachSaysTwoLogsTwoBreadTwoBeds(GameTestHelper helper) {
		sentShort(helper, 2, "2 logs, 2 bread, 2 beds");
	}

	/** An order on the road: 1200 ticks or less left is "there in a minute", one tick more "in 2 minutes", none "arriving". */
	//$ gametest_ticks_batch AREA '20' '"qaColWords1102Road"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaColWords1102Road")
	public void qaOnTheRoadSaysAMinuteThenTwoMinutes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GlobalPos mother = GlobalPos.of(level.dimension(), helper.absolutePos(HALL));
		long now = level.getGameTime();
		String[][] cases = {{"1", "On the road to Newbrook, there in a minute"}, {"1200", "On the road to Newbrook, there in a minute"},
			{"1201", "On the road to Newbrook, there in 2 minutes"}, {"2400", "On the road to Newbrook, there in 2 minutes"},
			{"2401", "On the road to Newbrook, there in 3 minutes"}, {"0", "On the road to Newbrook, arriving"}};
		for (String[] c : cases) {
			RealmData.Order order = new RealmData.Order(mother, helper.absolutePos(HALL).offset(400, 0, 0), Optional.of(Component.literal("Newbrook")),
				RealmData.ON_ROAD, now - 100, now + Long.parseLong(c[0]), 3200, now - 200, List.of(), Optional.empty(), Map.of(), List.of());
			String said = Settlers.status(level, order).getString();
			helper.assertTrue(said.equals(c[1]), c[0] + " ticks left: '" + said + "', want '" + c[1] + "'");
		}
		helper.succeed();
	}

	/** One settler on the road (the other died before they set out): the tab says "1 settler", two say "2 settlers". */
	//$ gametest_ticks_batch AREA '40' '"qaColWords1102OneSettler"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaColWords1102OneSettler")
	public void qaOneSettlerOnTheRoadIsOneSettler(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		GlobalPos mother = GlobalPos.of(level.dimension(), helper.absolutePos(HALL));
		long now = level.getGameTime();
		CompoundTag tomas = saved(helper, "Tomas");
		CompoundTag dara = saved(helper, "Dara");
		RealmData.Order one = new RealmData.Order(mother, helper.absolutePos(HALL).offset(400, 0, 0), Optional.of(Component.literal("Newbrook")),
			RealmData.ON_ROAD, now - 100, now + 6000, 3200, now - 200, List.of(), Optional.empty(), Map.of(), List.of(tomas));
		RealmData.Order two = new RealmData.Order(mother, helper.absolutePos(HALL).offset(400, 0, 0), Optional.of(Component.literal("Newbrook")),
			RealmData.ON_ROAD, now - 100, now + 6000, 3200, now - 200, List.of(), Optional.empty(), Map.of(), List.of(tomas, dara));
		List<String> oneLines = strings(Settlers.lines(level, one));
		List<String> twoLines = strings(Settlers.lines(level, two));
		helper.assertTrue(oneLines.contains("1 settler"), "one settler: " + oneLines);
		helper.assertTrue(twoLines.contains("2 settlers"), "two settlers: " + twoLines);
		helper.succeed();
	}

	private static CompoundTag saved(GameTestHelper helper, String name) {
		Villager x = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		x.setCustomName(Component.literal(name));
		CompoundTag tag = new CompoundTag();
		x.saveWithoutId(tag);
		x.discard();
		return tag;
	}
}
