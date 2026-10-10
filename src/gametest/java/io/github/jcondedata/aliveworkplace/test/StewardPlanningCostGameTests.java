package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.city.StewardWork;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * B85: the Steward's planning, spread over the second and counted once. At the hall his planning comes in two parts
 * (his wishes and their plot searches, then his desk half a second later), the morning's ranking counts the village a
 * piece a second first and comes out as ranking it at once would, and one planning second looks up the Blueprint Tables
 * once, again only every {@link Plots#TABLES_EVERY} ticks, while a new build or a changed plan still starts the search
 * over at once.
 */
public class StewardPlanningCostGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final BlockPos TABLE = new BlockPos(2, 2, 10);
	private static final ResourceLocation WELL = AliveWorkplace.id("well");
	private static final ResourceLocation STALL = AliveWorkplace.id("market_stall");
	private static final ResourceLocation RULE = AliveWorkplace.id("test/cost_well");
	private static final ResourceLocation RULE2 = AliveWorkplace.id("test/cost_stall");
	private static final BlockPos SPOT = new BlockPos(11, 2, 13);

	private record Village(ServerLevel level, BlockPos hall, ServerPlayer owner, Villager steward) {
		VillageHallBlockEntity entity() {
			return (VillageHallBlockEntity) level.getBlockEntity(hall);
		}
	}

	/** Flat grass, a hall with its owner, a Blueprint Table and its builder, a Homes zone east of the hall and a Steward. */
	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 40, level.getMinBuildHeight(), a.getZ() - 40, a.getX() + 70, level.getMaxBuildHeight(), a.getZ() + 70);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : new ArrayList<>(manager.all())) {
			if (near.isInside(site.placement().origin())) {
				manager.remove(site.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (near.isInside(f.placement().origin())) {
				manager.forgetFinished(f.placement());
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BitSet cells = new BitSet();
		for (int x = 6; x <= 29; x++) {
			for (int z = 2; z <= 25; z++) {
				cells.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
		Villager builder = helper.spawn(EntityType.VILLAGER, TABLE.east());
		Builders.employ(level, builder, helper.absolutePos(TABLE));
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.south(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		return new Village(level, hall, owner, steward);
	}

	private static StewardWishes.Wish wish(ResourceLocation rule, ResourceLocation blueprint) {
		return new StewardWishes.Wish(rule, new StewardRules.Effect(StewardRules.Kind.BUILD, Optional.of(blueprint), Optional.of("homes"),
			Optional.empty(), Optional.empty()), 50, "steward.aliveworkplace.why.beds", List.of(3L));
	}

	private static String key(Component line) {
		return line != null && line.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(line);
	}

	private static List<String> rules(List<StewardWishes.Wish> wishes) {
		return wishes.stream().map(w -> w.rule() + "@" + w.priority() + w.numbers()).toList();
	}

	/**
	 * The morning's ranking, spread: a piece of the village counted each planning second (his line says he's reading,
	 * the desk waits), then the same wishes ranking at once gives; ranked once a day, kept through save and reload, and
	 * started again on a new day.
	 */
	//$ gametest_ticks_batch AREA '40' '"stewardCostRank"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardCostRank")
	public void theSpreadMorningRanksTheSameWishesAsRankingAtOnce(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		VillageHallBlockEntity entity = v.entity();
		long day = StewardWishes.day(level);
		StewardWishes.State before = entity.stewardWishes();
		List<StewardWishes.Wish> expected = StewardWishes.rank(StewardRules.all(), StewardConditions.Facts.of(level, v.hall()), before, day);
		helper.assertTrue(!expected.isEmpty(), "setup: no rule holds in the test village");
		for (int i = 0; i < StewardConditions.Facts.PIECES; i++) {
			Component line = StewardWork.PLANNER.plan(level, v.steward(), v.hall(), 0);
			helper.assertTrue(key(line).equals("message.aliveworkplace.steward.state.reading"), "counting, his line: " + key(line));
			helper.assertTrue(entity.stewardWishes().day() != day, "ranked after " + (i + 1) + " of " + StewardConditions.Facts.PIECES + " pieces");
			helper.assertTrue(StewardWork.PLANNER.plan(level, v.steward(), v.hall(), StewardWork.SECOND_PART) != null, "no line from the desk's part");
			helper.assertTrue(StewardDesk.openSites(level, v.hall()).isEmpty() && StewardDesk.of(level, v.hall()).proposals().isEmpty(),
				"the desk worked before the wishes were ranked");
		}
		Component line = StewardWork.PLANNER.plan(level, v.steward(), v.hall(), 0);
		StewardWishes.State ranked = entity.stewardWishes();
		helper.assertTrue(ranked.day() == day, "not ranked after every piece was counted");
		helper.assertTrue(rules(ranked.wishes()).equals(rules(expected)), "spread " + rules(ranked.wishes()) + " vs at once " + rules(expected));
		helper.assertTrue(key(line).equals("message.aliveworkplace.steward.state.wish"), "his line: " + key(line));
		// only parts 0 and SECOND_PART plan
		for (int part = 1; part < StewardWork.PLAN_EVERY; part++) {
			if (part != StewardWork.SECOND_PART) {
				helper.assertTrue(StewardWork.PLANNER.plan(level, v.steward(), v.hall(), part) == null, "planned on part " + part);
			}
		}
		// once a day: more calls keep the same wishes
		StewardWork.PLANNER.plan(level, v.steward(), v.hall(), 0);
		helper.assertTrue(entity.stewardWishes().wishes().equals(ranked.wishes()), "ranked twice in a day");
		// saved and loaded: kept, and not ranked again
		CompoundTag tag = entity.saveWithoutMetadata(level.registryAccess());
		entity.setStewardWishes(StewardWishes.State.EMPTY);
		entity.loadWithComponents(tag, level.registryAccess());
		helper.assertTrue(entity.stewardWishes().equals(ranked), "lost on reload: " + entity.stewardWishes());
		helper.assertTrue(key(StewardWork.PLANNER.plan(level, v.steward(), v.hall(), 0)).equals("message.aliveworkplace.steward.state.wish"),
			"counting again after a reload");
		// a new day: counted again first
		long time = level.getDayTime();
		try {
			level.setDayTime(time + 24000L);
			helper.assertTrue(key(StewardWork.PLANNER.plan(level, v.steward(), v.hall(), 0)).equals("message.aliveworkplace.steward.state.reading"),
				"the new day's wishes ranked without counting");
			for (int i = 0; i < StewardConditions.Facts.PIECES; i++) {
				StewardWork.PLANNER.plan(level, v.steward(), v.hall(), 0);
			}
			helper.assertTrue(entity.stewardWishes().day() == day + 1, "the new day's wishes not ranked: " + entity.stewardWishes().day());
		} finally {
			level.setDayTime(time);
		}
		helper.succeed();
	}

	/** In two parts his planning does what it did at once: Run the village starts a proposal in the desk's part; Rest plans nothing. */
	//$ gametest_ticks_batch AREA '40' '"stewardCostDesk"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardCostDesk")
	public void theDesksPartStartsTheProposalAsPlanningAtOnceDid(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		boolean selfRun = StewardDesk.SELF_RUN;
		try {
			StewardDesk.SELF_RUN = true;
			v.entity().setPlan(v.entity().plan().withMode(CityPlan.Mode.RUN));
			long day = StewardWishes.day(level);
			v.entity().setStewardWishes(new StewardWishes.State(day, List.of(), Map.of())); // today's wishes ranked: none
			Plots.Plot plot = Plots.check(level, v.hall(), v.entity().plan(), "homes", WELL, helper.absolutePos(SPOT), Rotation.COUNTERCLOCKWISE_90,
				Mirror.NONE).plot().orElseThrow(() -> new AssertionError("setup: the spot doesn't fit"));
			helper.assertTrue(StewardDesk.offer(level, v.hall(), wish(RULE, WELL), plot.blueprint(), plot.placement(), plot.zone(), false).isPresent(),
				"setup: not proposed");
			Component first = StewardWork.PLANNER.plan(level, v.steward(), v.hall(), 0);
			helper.assertTrue(key(first).equals("message.aliveworkplace.steward.state.reading"), "his line: " + key(first));
			helper.assertTrue(StewardDesk.openSites(level, v.hall()).isEmpty(), "the wishes' part worked the desk");
			StewardWork.PLANNER.plan(level, v.steward(), v.hall(), StewardWork.SECOND_PART);
			helper.assertTrue(StewardDesk.openSites(level, v.hall()).size() == 1, "the desk's part started " + StewardDesk.openSites(level, v.hall()).size() + " builds");
			helper.assertTrue(StewardDesk.of(level, v.hall()).ran() == day, "the morning's run not noted");
			// Rest: both parts plan nothing
			helper.assertTrue(StewardDesk.setMode(level, v.hall(), v.owner(), CityPlan.Mode.REST), "Rest refused");
			for (int part : new int[] {0, StewardWork.SECOND_PART}) {
				helper.assertTrue(key(StewardWork.PLANNER.plan(level, v.steward(), v.hall(), part)).equals("message.aliveworkplace.steward.state.resting"),
					"part " + part + " while resting");
			}
		} finally {
			StewardDesk.SELF_RUN = selfRun;
			StewardDesk.openSites(level, v.hall()).forEach(s -> BuildSiteManager.get(level).remove(s.id()));
		}
		helper.succeed();
	}

	/**
	 * One planning second with two build wishes looks up the Blueprint Tables once (it used to be once a wish and once a
	 * searching proposal), and the next seconds not again till {@link Plots#TABLES_EVERY} ticks have gone; a new build or
	 * a changed plan still starts the searches over at once, and a table taken away is noticed within that time.
	 */
	//$ gametest_ticks_batch AREA '200' '"stewardCostTables"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "stewardCostTables")
	public void onePlanningSecondLooksUpTheTablesOnce(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		long day = StewardWishes.day(level);
		v.entity().setStewardWishes(new StewardWishes.State(day, List.of(wish(RULE, WELL), wish(RULE2, STALL)), Map.of()));
		Plots.Request well = StewardWishes.plotFor(level, v.hall(), wish(RULE, WELL)).orElseThrow();
		Plots.forget(level, v.hall()); // nothing kept from earlier tests at this spot
		int before = Plots.tableLookups();
		StewardWork.PLANNER.plan(level, v.steward(), v.hall());
		helper.assertTrue(Plots.tableLookups() - before == 1, "one planning second looked up the tables " + (Plots.tableLookups() - before) + " times");
		Plots.Search first = Plots.request(level, v.hall(), well).orElseThrow();
		long start = level.getGameTime();
		helper.runAfterDelay(1, () -> {
			int next = Plots.tableLookups();
			StewardWork.PLANNER.plan(level, v.steward(), v.hall(), 0);
			StewardWork.PLANNER.plan(level, v.steward(), v.hall(), StewardWork.SECOND_PART);
			helper.assertTrue(Plots.tableLookups() == next, "looked up the tables again a tick later");
			helper.assertTrue(Plots.request(level, v.hall(), well).orElseThrow() == first, "the search started over with nothing changed");
			// a new build in the plan's area: searched again at once
			BuildSite site = BuildSiteManager.get(level).create(UUID.randomUUID(), "Someone", STALL,
				new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(
					io.github.jcondedata.aliveworkplace.mc.Ids.of(level.dimension()), helper.absolutePos(new BlockPos(20, 2, 20)), Rotation.NONE, Mirror.NONE));
			Plots.Search afterBuild;
			try {
				afterBuild = Plots.request(level, v.hall(), well).orElseThrow();
				helper.assertTrue(afterBuild != first, "a build went up but the old search was kept");
			} finally {
				BuildSiteManager.get(level).remove(site.id());
			}
			// a changed plan: searched again at once
			BitSet one = new BitSet();
			one.set(CityPlan.cellAt(v.hall(), helper.absolutePos(new BlockPos(29, 2, 25))));
			v.entity().setPlan(v.entity().plan().erase(one));
			Plots.Search afterPlan = Plots.request(level, v.hall(), well).orElseThrow();
			helper.assertTrue(afterPlan != afterBuild, "the plan changed but the old search was kept");
			// the table taken away: the same tick keeps the search, within TABLES_EVERY ticks it starts over
			helper.setBlock(TABLE, Blocks.AIR);
			helper.assertTrue(Plots.request(level, v.hall(), well).orElseThrow() == afterPlan, "setup: the tables looked up every ask");
			helper.runAfterDelay(Plots.TABLES_EVERY, () -> {
				int lookups = Plots.tableLookups();
				StewardWork.PLANNER.plan(level, v.steward(), v.hall(), 0);
				helper.assertTrue(Plots.tableLookups() - lookups == 1, "after " + (level.getGameTime() - start) + " ticks the tables were looked up "
					+ (Plots.tableLookups() - lookups) + " times");
				helper.assertTrue(Plots.request(level, v.hall(), well).orElseThrow() != afterPlan, "the table went but the old search was kept");
				helper.succeed();
			});
		});
	}
}
