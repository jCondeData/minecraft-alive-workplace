package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers;
import io.github.jcondedata.aliveworkplace.people.Retirement;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.threat.Plague;
import io.github.jcondedata.aliveworkplace.table.TableLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * QA stage 1 (2026-10-23): saved data and config keys that no other test saved and reloaded or flipped. Expectations
 * come from docs/wiki/plague.md (a Remedy keeps a villager well for 10 days), docs/wiki/config-switches.md
 * (retrainPrices: each 0 to 1000, a short list filled from the defaults; leaderboardDay: monday to sunday) and the
 * attachments' own comments in ModAttachments.
 */
public class QaStageOneGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** Saves and reloads {@code v} the way a world save does; the copy has a fresh UUID so both may coexist. */
	private static Villager reload(GameTestHelper helper, Villager v) {
		ServerLevel world = helper.getLevel();
		CompoundTag tag = v.saveWithoutId(new CompoundTag());
		v.discard();
		Villager copy = EntityType.VILLAGER.create(world);
		copy.load(tag);
		copy.setUUID(UUID.randomUUID());
		world.addFreshEntity(copy);
		copy.setNoAi(true);
		return copy;
	}

	private static Villager farmer(GameTestHelper helper) {
		Villager v = helper.spawn(EntityType.VILLAGER, 2, 2, 2);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		return v;
	}

	/** A Remedy keeps a villager well for 10 days: that survives a save, and a hex or the plague still bounce off. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void remedyWellnessSurvivesReload(GameTestHelper helper) {
		ServerLevel world = helper.getLevel();
		Villager v = farmer(helper);
		boolean wasIll = Plague.cure(world, v);
		helper.assertTrue(!wasIll, "a well villager reported as having been ill");
		long now = world.getGameTime();
		helper.assertTrue(Sickness.isImmune(v, now), "not well after the Remedy");
		Villager back = reload(helper, v);
		helper.assertTrue(ModAttachments.WELL_UNTIL.get(back) != null, "well_until lost on reload");
		helper.assertTrue(Sickness.isImmune(back, now), "the Remedy's 10 days were lost on reload");
		helper.assertTrue(Sickness.isImmune(back, now + Plague.WELL_FOR - 1), "well for less than 10 days");
		helper.assertTrue(!Sickness.isImmune(back, now + Plague.WELL_FOR + 1), "well for more than 10 days");
		Sickness.hex(world, back);
		helper.assertTrue(!Sickness.isIll(back), "a hex took hold of a villager kept well by a Remedy, after a reload");
		helper.assertTrue(!Sickness.catchPlague(world, back) && !Sickness.isIll(back), "plague caught by a villager kept well, after a reload");
		helper.succeed();
	}

	/** A Remedy given to a hexed villager cures the hex and leaves them well. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void remedyCuresAHexAndThenProtects(GameTestHelper helper) {
		ServerLevel world = helper.getLevel();
		Villager v = farmer(helper);
		Sickness.hex(world, v);
		helper.assertTrue(Sickness.isIll(v), "the hex did not take on a healthy villager");
		helper.assertTrue(Plague.cure(world, v), "cure() did not report the hexed villager as ill");
		helper.assertTrue(!Sickness.isIll(v), "still ill after a Remedy");
		Villager back = reload(helper, v);
		helper.assertTrue(!Sickness.isIll(back), "the illness came back on reload");
		Sickness.hex(world, back);
		helper.assertTrue(!Sickness.isIll(back), "re-hexed during the 10 well days");
		helper.succeed();
	}

	/** Lure spots keep their order and are capped at MAX_SPOTS (the oldest goes) across a save. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void lureSpotsSurviveReloadAndStayCapped(GameTestHelper helper) {
		Villager v = farmer(helper);
		BlockPos a = new BlockPos(100, 70, 100);
		BlockPos b = new BlockPos(-5, -3, 7);
		BlockPos c = new BlockPos(30000000, 5, -30000000);
		BlockPos d = new BlockPos(1, 2, 3);
		HabitatKeepers.addSpot(v, a);
		HabitatKeepers.addSpot(v, b);
		HabitatKeepers.addSpot(v, c);
		HabitatKeepers.addSpot(v, b); // marking an old one again moves it to the end, no duplicate
		Villager back = reload(helper, v);
		helper.assertTrue(HabitatKeepers.spots(back).equals(List.of(a, c, b)), "spots after reload: " + HabitatKeepers.spots(back));
		HabitatKeepers.addSpot(back, d);
		helper.assertTrue(HabitatKeepers.spots(back).equals(List.of(c, b, d)),
			"over the cap the oldest should go (" + HabitatKeepers.MAX_SPOTS + "): " + HabitatKeepers.spots(back));
		helper.succeed();
	}

	/** The gem grower's remembered beds survive a save, in order, with negative coordinates. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void gemBedsSurviveReload(GameTestHelper helper) {
		Villager v = farmer(helper);
		List<BlockPos> beds = List.of(new BlockPos(-100, -20, 55), new BlockPos(0, 0, 0), new BlockPos(7, 300, -9));
		ModAttachments.GEM_BEDS.set(v, beds);
		Villager back = reload(helper, v);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.gem.GemGrowers.beds(back).equals(beds),
			"beds after reload: " + io.github.jcondedata.aliveworkplace.gem.GemGrowers.beds(back));
		helper.succeed();
	}

	/** An elder's "passed day" and a waiting retirement are saved. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void passedDayAndRetireWaitingSurviveReload(GameTestHelper helper) {
		Villager v = farmer(helper);
		ModAttachments.PASSED_DAY.set(v, 12345L);
		ModAttachments.RETIRE_WAITING.set(v, true);
		Villager back = reload(helper, v);
		helper.assertTrue(Long.valueOf(12345L).equals(ModAttachments.PASSED_DAY.get(back)), "passed_day after reload: " + ModAttachments.PASSED_DAY.get(back));
		helper.assertTrue(ModAttachments.RETIRE_WAITING.getOrElse(back, false), "retire_waiting lost on reload");
		boolean listed = false;
		for (Component line : Retirement.hallLines(helper.getLevel(), back)) {
			if (line.getContents() instanceof TranslatableContents t && t.getKey().equals("screen.aliveworkplace.hall.retire_waiting")) {
				listed = true;
			}
		}
		helper.assertTrue(listed == Retirement.ENABLED, "a waiting elder should be on the hall's list exactly when retirement is on; listed=" + listed);
		helper.succeed();
	}

	/** retrainPrices: each 0 to 1000, a short list is filled from the defaults, null and junk fall back. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void retrainPricesAreClampedAndFilled(GameTestHelper helper) {
		WorkplaceConfig c = WorkplaceConfig.parse("{\"retrainPrices\": [-5, 2000, 7]}");
		helper.assertTrue(c.retrainPrices.equals(List.of(0, 1000, 7, 35, 50)), "clamped/filled: " + c.retrainPrices);
		WorkplaceConfig empty = WorkplaceConfig.parse("{\"retrainPrices\": []}");
		helper.assertTrue(empty.retrainPrices.equals(List.of(5, 10, 20, 35, 50)), "empty list: " + empty.retrainPrices);
		WorkplaceConfig nul = WorkplaceConfig.parse("{\"retrainPrices\": null}");
		helper.assertTrue(nul.retrainPrices.equals(List.of(5, 10, 20, 35, 50)), "null list: " + nul.retrainPrices);
		WorkplaceConfig longer = WorkplaceConfig.parse("{\"retrainPrices\": [1, 2, 3, 4, 5, 6, 7]}");
		helper.assertTrue(longer.retrainPrices.size() == 5, "a long list should be cut to the 5 levels: " + longer.retrainPrices);
		helper.succeed();
	}

	/** leaderboardDay: monday to sunday in any case; anything else is Sunday. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void leaderboardDayParses(GameTestHelper helper) {
		helper.assertTrue(WorkplaceConfig.parse("{\"leaderboardDay\": \"Friday\"}").leaderboardDay.equals("friday"), "Friday");
		helper.assertTrue(WorkplaceConfig.parse("{\"leaderboardDay\": \"monday\"}").leaderboardDay.equals("monday"), "monday");
		helper.assertTrue(WorkplaceConfig.parse("{\"leaderboardDay\": \"funday\"}").leaderboardDay.equals("sunday"), "funday -> sunday");
		helper.assertTrue(WorkplaceConfig.parse("{\"leaderboardDay\": \"\"}").leaderboardDay.equals("sunday"), "empty -> sunday");
		helper.assertTrue(WorkplaceConfig.parse("{\"leaderboardDay\": null}").leaderboardDay.equals("sunday"), "null -> sunday");
		helper.assertTrue(WorkplaceConfig.parse("{}").leaderboardDay.equals("sunday"), "default");
		helper.succeed();
	}

	/** The table's store/take-out packets from a player who is far from the table (spoofed position) or hold junk are refused. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void tablePacketsRefuseFarPlayersAndJunk(GameTestHelper helper) {
		BlockPos rel = new BlockPos(2, 2, 2);
		helper.setBlock(rel, ModBlocks.BLUEPRINT_TABLE);
		BlockPos table = helper.absolutePos(rel);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			player.setGameMode(GameType.SURVIVAL);
			player.getInventory().clearContent();
			ItemStack bp = BlueprintItem.create(StarterBlueprints.MARKET_STALL.id(), null);
			player.getInventory().setItem(0, bp.copy());
			player.getInventory().selected = 0;
			player.moveTo(new Vec3(table.getX() + 30.5, table.getY(), table.getZ() + 0.5));
			helper.assertTrue(TableLibrary.store(player, table, true) == 0, "stored from 30 blocks away");
			helper.assertTrue(player.getInventory().getItem(0).getCount() == 1, "the blueprint left the inventory though nothing was stored");
			player.moveTo(new Vec3(table.getX() + 1.5, table.getY(), table.getZ() + 0.5));
			player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
			helper.assertTrue(TableLibrary.store(player, table, false) == 0, "a diamond was stored as a blueprint");
			helper.assertTrue(player.getInventory().getItem(0).getCount() == 3, "diamonds vanished");
			player.getInventory().setItem(0, bp.copy());
			helper.assertTrue(TableLibrary.store(player, table, false) == 1, "the held blueprint was not stored");
			player.moveTo(new Vec3(table.getX() + 30.5, table.getY(), table.getZ() + 0.5));
			helper.assertFalse(TableLibrary.takeOut(player, table, bp), "took out from 30 blocks away");
			player.moveTo(new Vec3(table.getX() + 1.5, table.getY(), table.getZ() + 0.5));
			helper.assertFalse(TableLibrary.takeOut(player, table, new ItemStack(Items.DIAMOND)), "a diamond came out of the table");
			helper.assertFalse(TableLibrary.takeOut(player, table, ItemStack.EMPTY), "an empty sample took something out");
			helper.assertFalse(TableLibrary.takeOut(player, table.above(5), bp), "took out from a position with no table");
			helper.assertTrue(TableLibrary.takeOut(player, table, bp.copyWithCount(64)), "the stored blueprint could not be taken with a spoofed count");
			int held = 0;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				held += player.getInventory().getItem(i).getCount();
			}
			helper.assertTrue(held == 1, "a spoofed count of 64 gave " + held + " items");
			helper.succeed();
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
	}

	/** Simple counters, days, lists and a stack the inventory flagged as never saved and reloaded come back as they were. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void remainingSavedDataSurvivesReload(GameTestHelper helper) {
		Villager v = farmer(helper);
		net.minecraft.resources.ResourceLocation g1 = net.minecraft.resources.ResourceLocation.parse("aliveworkplace:amethyst");
		net.minecraft.resources.ResourceLocation g2 = net.minecraft.resources.ResourceLocation.parse("minecraft:emerald");
		ModAttachments.PROFESSOR_STONE.set(v, 99_000_000_000L); // beyond an int: a day count in ticks can pass 2^31
		ModAttachments.GUESTS_TODAY.set(v, 3);
		ModAttachments.FESTIVAL_BANNER_DAY.set(v, 41L);
		ModAttachments.ILL_EASED_UNTIL.set(v, 5_000_000_000L);
		ModAttachments.PHARMACY_CURE_DAY.set(v, 17L);
		ModAttachments.HONEY_LOGS.set(v, List.of(new BlockPos(-3, 64, 9), new BlockPos(5, 70, -1)));
		ModAttachments.GEM_ORDERS.set(v, List.of(g1, g2, g1));
		ModAttachments.GEMS_PICKED.set(v, 12);
		ModAttachments.BERRIES_PICKED.set(v, 7);
		ModAttachments.ADULT_SINCE.set(v, 4L);
		ModAttachments.PLUNDER.set(v, new ItemStack(Items.EMERALD, 5));
		Villager b = reload(helper, v);
		helper.assertTrue(Long.valueOf(99_000_000_000L).equals(ModAttachments.PROFESSOR_STONE.get(b)), "professor_stone: " + ModAttachments.PROFESSOR_STONE.get(b));
		helper.assertTrue(Integer.valueOf(3).equals(ModAttachments.GUESTS_TODAY.get(b)), "guests_today: " + ModAttachments.GUESTS_TODAY.get(b));
		helper.assertTrue(Long.valueOf(41L).equals(ModAttachments.FESTIVAL_BANNER_DAY.get(b)), "festival_banner_day");
		helper.assertTrue(Long.valueOf(5_000_000_000L).equals(ModAttachments.ILL_EASED_UNTIL.get(b)), "ill_eased_until: " + ModAttachments.ILL_EASED_UNTIL.get(b));
		helper.assertTrue(Long.valueOf(17L).equals(ModAttachments.PHARMACY_CURE_DAY.get(b)), "pharmacy_cure_day");
		helper.assertTrue(ModAttachments.HONEY_LOGS.getOrElse(b, List.of()).equals(List.of(new BlockPos(-3, 64, 9), new BlockPos(5, 70, -1))), "honey_logs");
		helper.assertTrue(ModAttachments.GEM_ORDERS.getOrElse(b, List.of()).equals(List.of(g1, g2, g1)), "gem_orders: " + ModAttachments.GEM_ORDERS.getOrElse(b, List.of()));
		helper.assertTrue(Integer.valueOf(12).equals(ModAttachments.GEMS_PICKED.get(b)), "gems_picked");
		helper.assertTrue(Integer.valueOf(7).equals(ModAttachments.BERRIES_PICKED.get(b)), "berries_picked");
		helper.assertTrue(Long.valueOf(4L).equals(ModAttachments.ADULT_SINCE.get(b)), "adult_since");
		ItemStack plunder = ModAttachments.PLUNDER.get(b);
		helper.assertTrue(plunder != null && plunder.is(Items.EMERALD) && plunder.getCount() == 5, "plunder: " + plunder);
		helper.succeed();
	}

	/** Every ranged config key: the lowest and highest int the file can hold land inside the documented range, never beyond. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyRangedConfigKeyIsClampedAtTheExtremes(GameTestHelper helper) {
		StringBuilder low = new StringBuilder("{");
		StringBuilder high = new StringBuilder("{");
		for (String name : WorkplaceConfig.RANGES.keySet()) {
			low.append('"').append(name).append("\": ").append(Integer.MIN_VALUE).append(',');
			high.append('"').append(name).append("\": ").append(Integer.MAX_VALUE).append(',');
		}
		low.append("\"configVersion\": 99}");
		high.append("\"configVersion\": 99}");
		WorkplaceConfig lo = WorkplaceConfig.parse(low.toString());
		WorkplaceConfig hi = WorkplaceConfig.parse(high.toString());
		int checked = 0;
		for (var e : WorkplaceConfig.RANGES.entrySet()) {
			var r = e.getValue();
			Object l = lo.getInt(e.getKey());
			Object h = hi.getInt(e.getKey());
			helper.assertTrue(l instanceof Integer li && li == r.min(), e.getKey() + " at Integer.MIN_VALUE gave " + l + ", range " + r.min() + ".." + r.max());
			helper.assertTrue(h instanceof Integer hv && hv == r.max(), e.getKey() + " at Integer.MAX_VALUE gave " + h + ", range " + r.min() + ".." + r.max());
			checked++;
		}
		helper.assertTrue(checked > 20, "only " + checked + " ranged keys");
		helper.succeed();
	}

	/**
	 * docs/wiki/elders.md: an elder whose time had come and whom an Undertaker revives gets `passed_day` = that day and
	 * 400 more days (counted from the day, to 0 and no further); one whose time hasn't come keeps their own count; the
	 * count survives a save.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aRevivedElderGetsFourHundredMoreDaysAndKeepsThemOnReload(GameTestHelper helper) {
		ServerLevel world = helper.getLevel();
		long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(world);
		boolean passing = LifeStages.PASSING;
		LifeStages.PASSING = true;
		try {
			Villager due = farmer(helper);
			LifeStages.setAdultSince(due, today - LifeStages.ELDER_DAYS - LifeStages.PASSING_DAYS - 5);
			helper.assertTrue(LifeStages.daysLeft(due, today) == 0, "an elder past their 400 days should be at 0: " + LifeStages.daysLeft(due, today));
			LifeStages.onRevived(world, due);
			helper.assertTrue(LifeStages.daysLeft(due, today) == LifeStages.PASSING_DAYS, "revived: " + LifeStages.daysLeft(due, today));
			helper.assertTrue(LifeStages.daysLeft(due, today + 10) == LifeStages.PASSING_DAYS - 10, "10 days on: " + LifeStages.daysLeft(due, today + 10));
			helper.assertTrue(LifeStages.daysLeft(due, today + LifeStages.PASSING_DAYS + 50) == 0, "past the 400 more days should be 0");
			Villager back = reload(helper, due);
			helper.assertTrue(LifeStages.daysLeft(back, today + 1) == LifeStages.PASSING_DAYS - 1, "after reload: " + LifeStages.daysLeft(back, today + 1));
			Villager early = farmer(helper);
			LifeStages.setAdultSince(early, today - LifeStages.ELDER_DAYS - 100);
			long before = LifeStages.daysLeft(early, today);
			LifeStages.onRevived(world, early);
			helper.assertTrue(before == LifeStages.PASSING_DAYS - 100 && LifeStages.daysLeft(early, today) == before,
				"an elder whose time hadn't come had " + before + " and now " + LifeStages.daysLeft(early, today));
			Villager young = farmer(helper);
			LifeStages.setAdultSince(young, today - 10);
			LifeStages.onRevived(world, young);
			helper.assertTrue(LifeStages.daysLeft(young, today) == -1 && ModAttachments.PASSED_DAY.get(young) == null, "a grown villager was given passed_day");
		} finally {
			LifeStages.PASSING = passing;
		}
		helper.succeed();
	}
}
