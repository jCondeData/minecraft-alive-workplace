package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Conscription;
import io.github.jcondedata.aliveworkplace.hall.Curfew;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.hall.WorkHorn;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Tonics;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;

/**
 * QA for Milestone 30 from the specs (30.3, 30.5, 30.9, 30.10, 30.11, 30.15-30.17), the angles the feature tests leave
 * out: a friend of the owner (not only the owner or a stranger), every time boundary from both sides (dusk and dawn,
 * midday, noon, a rush's and a tonic's last tick, the turn of the day), two edicts that exclude each other, a second
 * tonic of a different strength, the charter's sneak gesture through the game's own event, the guild cap at Town and
 * City, and every edict's and reform's texts read from the lang file. Each test has its own batch and clears first.
 */
public class QaEdictsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final String LONG_SHIFTS = AliveWorkplace.id("long_shifts").toString();
	private static final String CURFEW = AliveWorkplace.id("curfew").toString();
	private static final String CONSCRIPTION = AliveWorkplace.id("conscription").toString();
	private static final float EPSILON = 1e-5f;

	/** 30.3: the owner's friend may proclaim and (after 3 days) lift in an owned village; unfriended, they are refused. */
	//$ gametest_ticks_batch AREA '100' '"qaEdictFriend"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaEdictFriend")
	public void qaAFriendProclaimsAndLiftsInAnOwnedVillage(GameTestHelper helper) {
		VillageHallBlockEntity hall = village(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos pos = helper.absolutePos(HALL);
			ServerPlayer friend = player(helper);
			UUID owner = UUID.randomUUID();
			hall.setOwner(owner, "Jesse");
			Friends friends = Friends.get(level.getServer());
			friends.add(owner, friend.getUUID(), friend.getGameProfile().getName());
			Leftovers.after(helper, () -> friends.remove(owner, friend.getUUID()));
			Edicts.Result proclaimed = Edicts.proclaim(level, pos, friend, Edicts.find(LONG_SHIFTS).orElseThrow());
			helper.assertTrue(proclaimed.done(), "the owner's friend was refused: " + proclaimed.message().getString());
			helper.assertTrue(hall.edicts().size() == 1 && hall.edicts().get(0).id().equals(LONG_SHIFTS), "in force: " + hall.edicts());
			hall.setEdicts(List.of(new Edicts.InForce(LONG_SHIFTS, Chronicle.day(level) - 3)));
			helper.assertTrue(Edicts.lift(level, pos, friend, LONG_SHIFTS).done() && hall.edicts().isEmpty(), "the friend couldn't lift it");

			// No longer a friend: a stranger again.
			friends.remove(owner, friend.getUUID());
			Edicts.Result refused = Edicts.proclaim(level, pos, friend, Edicts.find(LONG_SHIFTS).orElseThrow());
			helper.assertTrue(!refused.done() && refused.message().getString().startsWith("Only Jesse, their friends and the village's Lords can proclaim edicts in "),
				"an ex-friend: " + refused.message().getString());
			helper.assertTrue(hall.edicts().isEmpty(), "proclaimed by an ex-friend");
			helper.succeed();
		});
	}

	/** 30.9: Curfew excludes Open Gates, both ways round, even with a free slot; the sentence names both by their lang names. */
	//$ gametest_ticks_batch AREA '100' '"qaCurfewGates"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCurfewGates")
	public void qaCurfewAndOpenGatesCantBeInForceTogether(GameTestHelper helper) {
		VillageHallBlockEntity hall = village(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos pos = helper.absolutePos(HALL);
			hall.setRank(VillageRanks.Rank.CITY);
			Edicts.Edict curfew = Edicts.find("curfew").orElseThrow();
			Edicts.Edict gates = Edicts.find("open_gates").orElseThrow();
			helper.assertTrue(curfew.name().getString().equals("Curfew") && gates.name().getString().equals("Open Gates"),
				"names: " + curfew.name().getString() + ", " + gates.name().getString());
			helper.assertTrue(Edicts.proclaim(level, pos, null, curfew).done(), "Curfew refused");
			Edicts.Result second = Edicts.proclaim(level, pos, null, gates);
			helper.assertTrue(!second.done() && second.message().getString().equals("Open Gates can't be in force alongside Curfew."),
				"Open Gates after Curfew: " + second.message().getString());
			hall.setEdicts(List.of());
			helper.assertTrue(Edicts.proclaim(level, pos, null, gates).done(), "Open Gates refused alone");
			Edicts.Result other = Edicts.proclaim(level, pos, null, curfew);
			helper.assertTrue(!other.done() && other.message().getString().equals("Curfew can't be in force alongside Open Gates."),
				"Curfew after Open Gates: " + other.message().getString());
			helper.assertTrue(hall.edicts().size() == 1, "both in force: " + hall.edicts());
			helper.succeed();
		});
	}

	/**
	 * 30.9, through the player's own right-click: under Curfew a librarian trades a tick before dusk, refuses at dusk
	 * and a tick before dawn, and trades again at dawn; Curfew lifted, they trade at night.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCurfewEdges"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCurfewEdges")
	public void qaCurfewTradesAtTheEdgesOfTheNight(GameTestHelper helper) {
		VillageHallBlockEntity hall = village(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			Villager librarian = librarian(helper);
			ServerPlayer player = player(helper);
			hall.setEdicts(List.of(new Edicts.InForce(CURFEW, 1)));
			CivicEffects.forget();
			long start = (Chronicle.day(level) - 1) * VillageNeeds.DAY;
			helper.assertTrue(Language.getInstance().getOrDefault("message.aliveworkplace.curfew.no_trade").equals("Curfew: come back in the morning."),
				"the refusal's sentence");

			level.setDayTime(start + Curfew.DUSK - 1);
			helper.assertTrue(trades(librarian, player), "refused a tick before dusk");
			level.setDayTime(start + Curfew.DUSK);
			helper.assertTrue(!trades(librarian, player), "traded at dusk under Curfew");
			level.setDayTime(start + VillageNeeds.DAY - 1);
			helper.assertTrue(!trades(librarian, player), "traded a tick before dawn under Curfew");
			level.setDayTime(start + VillageNeeds.DAY);
			helper.assertTrue(trades(librarian, player), "refused at dawn");

			// Lifted: night trading as ever.
			hall.setEdicts(List.of());
			CivicEffects.forget();
			level.setDayTime(start + VillageNeeds.DAY + Curfew.DUSK + 10);
			helper.assertTrue(trades(librarian, player), "refused at night without Curfew");
			helper.succeed();
		});
	}

	/**
	 * 30.9: under Curfew nobody sets out from midday (a tick before: they may); a grown villager is kept in from dusk (a
	 * tick before: not) and a child never; reformed by The Lamplighters, nobody has to stay in.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCurfewMidday"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCurfewMidday")
	public void qaCurfewKeepsTripsHomeFromMiddayAndFolkInFromDusk(GameTestHelper helper) {
		VillageHallBlockEntity hall = village(helper);
		Villager miner = grown(helper, new BlockPos(6, 2, 6), ModVillagers.MINER);
		Villager child = grown(helper, new BlockPos(8, 2, 6), VillagerProfession.NONE);
		child.setAge(-24000);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			hall.setEdicts(List.of(new Edicts.InForce(CURFEW, 1)));
			CivicEffects.forget();
			long start = (Chronicle.day(level) - 1) * VillageNeeds.DAY;
			level.setDayTime(start + Curfew.MIDDAY - 1);
			helper.assertTrue(!Curfew.noTrips(miner), "no trips before midday");
			level.setDayTime(start + Curfew.MIDDAY);
			helper.assertTrue(Curfew.noTrips(miner), "a trip at midday under Curfew");
			level.setDayTime(start + Curfew.DUSK - 1);
			helper.assertTrue(!Curfew.keepsIn(miner), "kept in before dusk");
			level.setDayTime(start + Curfew.DUSK);
			helper.assertTrue(Curfew.keepsIn(miner), "not kept in at dusk");
			helper.assertTrue(!Curfew.keepsIn(child), "a child is kept in by the edict (children aren't the grown villagers it sends to bed)");

			hall.setReforms(List.of(new Reforms.Progress(CURFEW, 3, true, 0)));
			CivicEffects.forget();
			helper.assertTrue(!Curfew.keepsIn(miner) && !Curfew.noTrips(miner), "reformed, still kept in");
			helper.assertTrue(Curfew.safeNow(level, helper.absolutePos(HALL)), "reformed, the night no longer counts as safe");
			helper.succeed();
		});
	}

	/**
	 * 30.10: after a raid under Conscription a builder's work stays stopped a tick before noon and starts again at noon;
	 * reformed by The Militia Drill, the morning after is a normal day.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaConscriptNoon"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaConscriptNoon")
	public void qaWorkStartsAgainOnTheTickOfNoon(GameTestHelper helper) {
		VillageHallBlockEntity hall = village(helper);
		Villager builder = grown(helper, new BlockPos(6, 2, 6), ModVillagers.BUILDER);
		Leftovers.after(helper, Conscription::forget);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			hall.setEdicts(List.of(new Edicts.InForce(CONSCRIPTION, Chronicle.day(level))));
			CivicEffects.forget();
			long start = (Chronicle.day(level) - 1) * VillageNeeds.DAY;
			long noon = start + Conscription.NOON;
			level.setDayTime(start + 1000);
			Conscription.forget();
			helper.assertTrue(!Conscription.workStopped(builder), "stopped with no raid");
			hall.keepWorkStoppedUntil(noon); // a raid in the night just over
			Conscription.forget();
			helper.assertTrue(Conscription.workStopped(builder), "at work the morning after a raid");
			level.setDayTime(noon - 1);
			Conscription.forget();
			helper.assertTrue(Conscription.workStopped(builder), "at work a tick before noon");
			level.setDayTime(noon);
			Conscription.forget();
			helper.assertTrue(!Conscription.workStopped(builder), "still stopped at noon");

			// Reformed: the morning after is a normal day.
			level.setDayTime(start + VillageNeeds.DAY + 1000);
			hall.keepWorkStoppedUntil(start + VillageNeeds.DAY + Conscription.NOON);
			hall.setReforms(List.of(new Reforms.Progress(CONSCRIPTION, 3, true, 0)));
			CivicEffects.forget();
			Conscription.forget();
			helper.assertTrue(!Conscription.workStopped(builder), "reformed, the morning after still stops work");
			helper.succeed();
		});
	}

	/** 30.11: a rush counts on its last tick and not on the next: the pace is 1/1.5, then back to 1. */
	//$ gametest_ticks_batch AREA '100' '"qaHornLastTick"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHornLastTick")
	public void qaARushEndsOnItsLastTick(GameTestHelper helper) {
		VillageHallBlockEntity hall = village(helper);
		Villager farmer = grown(helper, new BlockPos(6, 2, 6), VillagerProfession.FARMER);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			long now = level.getGameTime();
			hall.startRush(Chronicle.day(level), now + 1);
			forget();
			helper.assertTrue(WorkHorn.rushing(level, hall) && Math.abs(WorkHorn.pace(farmer) - 1f / 1.5f) < EPSILON, "no rush on its last tick: " + WorkHorn.pace(farmer));
			hall.startRush(Chronicle.day(level), now);
			forget();
			helper.assertTrue(!WorkHorn.rushing(level, hall) && WorkHorn.pace(farmer) == 1f, "a rush past its end: " + WorkHorn.pace(farmer));
			helper.assertTrue(WorkHorn.RUSH_TICKS == 6000, "a rush lasts 5 minutes: " + WorkHorn.RUSH_TICKS);
			helper.succeed();
		});
	}

	/**
	 * 30.11: the owner's friend calls a rush in an owned village (the farmer rushes); the next day, unfriended, the same
	 * player is refused and nobody rushes.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHornFriend"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHornFriend")
	public void qaAFriendCallsARushInAnOwnedVillage(GameTestHelper helper) {
		VillageHallBlockEntity hall = village(helper);
		Villager farmer = grown(helper, new BlockPos(6, 2, 6), VillagerProfession.FARMER);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			ServerPlayer friend = player(helper);
			UUID owner = UUID.randomUUID();
			hall.setOwner(owner, "Jesse");
			Friends friends = Friends.get(level.getServer());
			friends.add(owner, friend.getUUID(), friend.getGameProfile().getName());
			Leftovers.after(helper, () -> friends.remove(owner, friend.getUUID()));
			WorkHorn.Result rush = WorkHorn.blow(level, friend, helper.absolutePos(HALL));
			helper.assertTrue(rush.outcome() == WorkHorn.Outcome.RUSH, "a friend's horn: " + rush);
			forget();
			helper.assertTrue(Math.abs(WorkHorn.pace(farmer) - 1f / 1.5f) < EPSILON, "the farmer didn't rush: " + WorkHorn.pace(farmer));

			friends.remove(owner, friend.getUUID());
			level.setDayTime(level.getDayTime() + VillageNeeds.DAY);
			hall.startRush(hall.hornDay(), 0);
			long day = hall.hornDay();
			WorkHorn.Result refused = WorkHorn.blow(level, friend, helper.absolutePos(HALL));
			helper.assertTrue(refused.outcome() == WorkHorn.Outcome.NOT_ALLOWED && hall.hornDay() == day && !WorkHorn.rushing(level, hall),
				"an ex-friend's horn: " + refused);
			helper.succeed();
		});
	}

	/** 30.11: blown a tick before the day ends, the horn is refused until the day turns, and answered on its first tick. */
	//$ gametest_ticks_batch AREA '100' '"qaHornDayTurn"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHornDayTurn")
	public void qaTheHornIsReadyAgainWhenTheDayTurns(GameTestHelper helper) {
		VillageHallBlockEntity hall = village(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			ServerPlayer player = player(helper);
			long end = Chronicle.day(level) * VillageNeeds.DAY;
			level.setDayTime(end - 2);
			long day = Chronicle.day(level);
			helper.assertTrue(WorkHorn.blow(level, player, helper.absolutePos(HALL)).outcome() == WorkHorn.Outcome.RUSH, "first blow");
			level.setDayTime(end - 1);
			WorkHorn.Result late = WorkHorn.blow(level, player, helper.absolutePos(HALL));
			helper.assertTrue(late.outcome() == WorkHorn.Outcome.USED_TODAY && late.message().getString().equals("The village has already answered the horn today."),
				"a tick before the day ends: " + late);
			level.setDayTime(end);
			WorkHorn.Result next = WorkHorn.blow(level, player, helper.absolutePos(HALL));
			helper.assertTrue(next.outcome() == WorkHorn.Outcome.RUSH && hall.hornDay() == day + 1, "the new day's first tick: " + next + ", day " + hall.hornDay());
			helper.succeed();
		});
	}

	/**
	 * 30.15: a farmer who drank Harvest Cordial (25%, a day) and then the test pack's glow-berry tonic (40%, 6000 ticks)
	 * has only the second: never both at once; another Cordial puts back 25% for a full day.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaTonicSwap"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaTonicSwap")
	public void qaASecondTonicReplacesTheFirstAndNeverStacks(GameTestHelper helper) {
		Leftovers.clear(helper);
		quiet(helper);
		Villager farmer = grown(helper, new BlockPos(6, 2, 6), VillagerProfession.FARMER);
		farmer.setCustomName(Component.literal("Dara"));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			ServerPlayer player = player(helper);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HARVEST_CORDIAL, 2));
			Tonics.Offer first = Tonics.offer(player, farmer, player.getMainHandItem());
			helper.assertTrue(first.outcome() == Tonics.Outcome.DRUNK && Math.abs(tonicPart(farmer) - 1f / 1.25f) < EPSILON, "the Cordial: " + first.outcome() + " " + Pace.of(farmer));
			ItemStack berries = new ItemStack(Items.GLOW_BERRIES);
			String name = Tonics.of(berries).name().getString();
			Tonics.Offer second = Tonics.offer(player, farmer, berries);
			helper.assertTrue(second.outcome() == Tonics.Outcome.DRUNK && berries.isEmpty(), "the berries: " + second.outcome() + ", " + berries);
			helper.assertTrue(second.message().getString().equals("Dara drinks the " + name + ": 40% faster for 5 minutes."),
				"said: " + second.message().getString());
			Tonics.Drunk drunk = ModAttachments.TONIC.get(farmer);
			helper.assertTrue(drunk.id().equals("aliveworkplace_test:test_cordial") && drunk.until() == level.getGameTime() + 6000, "drunk: " + drunk);
			helper.assertTrue(Math.abs(tonicPart(farmer) - 1f / 1.4f) < EPSILON && Math.abs(Pace.factor(farmer) - 1f / 1.4f) < EPSILON,
				"stacked or wrong: " + Pace.of(farmer));
			Tonics.offer(player, farmer, player.getMainHandItem());
			helper.assertTrue(Math.abs(Pace.factor(farmer) - 1f / 1.25f) < EPSILON && ModAttachments.TONIC.get(farmer).until() == level.getGameTime() + 24000,
				"the Cordial again: " + ModAttachments.TONIC.get(farmer) + " " + Pace.of(farmer));
			helper.assertTrue(player.getMainHandItem().isEmpty(), "two Cordials drunk, left: " + player.getMainHandItem());
			helper.succeed();
		});
	}

	/** 30.15: a tonic works on its last tick (1 tick left, 25% faster) and not on the next (no pace, no status line). */
	//$ gametest_ticks_batch AREA '100' '"qaTonicLastTick"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaTonicLastTick")
	public void qaATonicWearsOffOnItsLastTick(GameTestHelper helper) {
		Leftovers.clear(helper);
		quiet(helper);
		Villager miner = grown(helper, new BlockPos(6, 2, 6), ModVillagers.MINER);
		helper.runAfterDelay(5, () -> {
			long now = helper.getLevel().getGameTime();
			String brew = AliveWorkplace.id("miners_brew").toString();
			ModAttachments.TONIC.set(miner, new Tonics.Drunk(brew, now + 1));
			helper.assertTrue(Tonics.ticksLeft(miner) == 1 && Math.abs(Tonics.pace(miner) - 1f / 1.25f) < EPSILON, "on its last tick: " + Tonics.ticksLeft(miner));
			helper.assertTrue(Pace.describe(miner) != null && Pace.describe(miner).getString().startsWith("25% faster (Miner's Brew"), "status: " + Pace.describe(miner));
			ModAttachments.TONIC.set(miner, new Tonics.Drunk(brew, now));
			helper.assertTrue(Tonics.ticksLeft(miner) == 0 && Tonics.pace(miner) == 1f && Pace.describe(miner) == null, "after its end: " + Tonics.ticksLeft(miner));
			helper.succeed();
		});
	}

	/**
	 * 30.17, through the game's use-entity event: a plain right-click of a Master with a charter charters nobody (and
	 * keeps the charter); a sneak-right-click makes them Guild Master and uses one up.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCharterSneak"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCharterSneak")
	public void qaTheCharterNeedsASneakRightClick(GameTestHelper helper) {
		VillageHallBlockEntity hall = village(helper);
		Villager dara = master(helper, new BlockPos(6, 2, 6), "Dara", ModVillagers.BUILDER);
		helper.runAfterDelay(5, () -> {
			hall.setRank(VillageRanks.Rank.VILLAGE);
			Guilds.forget();
			ServerPlayer player = player(helper);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.GUILD_CHARTER, 2));
			player.setShiftKeyDown(false);
			player.moveTo(dara.getX() + 1, dara.getY(), dara.getZ(), 90f, 0f);
			UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, dara, null);
			helper.assertTrue(!ModAttachments.GUILD_MASTER.has(dara) && hall.guilds().isEmpty() && player.getMainHandItem().getCount() == 2,
				"a plain right-click chartered: " + hall.guilds());
			player.setShiftKeyDown(true);
			InteractionResult result = UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, dara, null);
			helper.assertTrue(result == InteractionResult.SUCCESS, "the sneak-right-click: " + result);
			helper.assertTrue(ModAttachments.GUILD_MASTER.has(dara) && hall.guilds().size() == 1 && player.getMainHandItem().getCount() == 1,
				"not chartered by a sneak-right-click: " + hall.guilds() + ", " + player.getMainHandItem());
			helper.succeed();
		});
	}

	/** 30.17: a Town may have 2 guilds (the third refused), a City 3 (the fourth refused, with its sentence). */
	//$ gametest_ticks_batch AREA '100' '"qaGuildCap"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGuildCap")
	public void qaATownHasTwoGuildsAndACityThree(GameTestHelper helper) {
		VillageHallBlockEntity hall = village(helper);
		Villager builder = master(helper, new BlockPos(4, 2, 4), "Dara", ModVillagers.BUILDER);
		Villager miner = master(helper, new BlockPos(6, 2, 4), "Bram", ModVillagers.MINER);
		Villager lumberjack = master(helper, new BlockPos(8, 2, 4), "Fenn", ModVillagers.LUMBERJACK);
		Villager toolsmith = master(helper, new BlockPos(10, 2, 4), "Gus", VillagerProfession.TOOLSMITH);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			String village = VillageHalls.name(level, helper.absolutePos(HALL)).getString();
			hall.setRank(VillageRanks.Rank.TOWN);
			Guilds.forget();
			helper.assertTrue(Guilds.grant(level, builder).outcome() == Guilds.Outcome.GRANTED, "a Town's first");
			helper.assertTrue(Guilds.grant(level, miner).outcome() == Guilds.Outcome.GRANTED, "a Town's second");
			Guilds.Offer third = Guilds.grant(level, lumberjack);
			helper.assertTrue(third.outcome() == Guilds.Outcome.FULL && third.message().getString().equals(village + " is a Town and may have 2 guilds; it has them all."),
				"a Town's third: " + third.outcome() + " " + third.message().getString());
			hall.setRank(VillageRanks.Rank.CITY);
			helper.assertTrue(Guilds.grant(level, lumberjack).outcome() == Guilds.Outcome.GRANTED, "a City's third");
			Guilds.Offer fourth = Guilds.grant(level, toolsmith);
			helper.assertTrue(fourth.outcome() == Guilds.Outcome.FULL && fourth.message().getString().equals(village + " is a City and may have 3 guilds; it has them all."),
				"a City's fourth: " + fourth.outcome() + " " + fourth.message().getString());
			helper.assertTrue(hall.guilds().size() == 3 && !ModAttachments.GUILD_MASTER.has(toolsmith), "guilds: " + hall.guilds());
			helper.succeed();
		});
	}

	/** 30.17 (B376): a full Village's refusal counts its one guild in the singular ("may have 1 guild", not "1 guilds"). */
	//$ gametest_ticks_batch AREA '100' '"qaGuildOne"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGuildOne")
	public void qaAFullVillageSaysOneGuild(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos pos = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			helper.getLevel().removeBlock(pos, false);
			VillageHalls.RADIUS = radius;
			Guilds.forget();
			CivicEffects.forget();
			VillageNeeds.forget();
		});
		Villager builder = master(helper, new BlockPos(4, 2, 4), "Dara", ModVillagers.BUILDER);
		Villager miner = master(helper, new BlockPos(6, 2, 4), "Bram", ModVillagers.MINER);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(pos);
			hall.setGuilds(List.of());
			hall.setRank(VillageRanks.Rank.VILLAGE);
			Guilds.forget();
			String village = VillageHalls.name(level, pos).getString();
			helper.assertTrue(Guilds.grant(level, builder).outcome() == Guilds.Outcome.GRANTED, "a Village's first");
			Guilds.Offer full = Guilds.grant(level, miner);
			helper.assertTrue(full.outcome() == Guilds.Outcome.FULL && full.message().getString().equals(village + " is a Village and may have 1 guild; it has them all."),
				"said: " + full.message().getString());
			helper.succeed();
		});
	}

	/**
	 * 30.3/30.5/30.9/30.10: every edict's name and description and every reform's name, line and step titles read as
	 * English (no raw lang key), and every sentence these features send is in the lang file.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryEdictAndReformTextIsInTheLangFile(GameTestHelper helper) {
		List<String> raw = new ArrayList<>();
		for (Edicts.Edict edict : Edicts.all()) {
			if (!edict.id().getNamespace().equals(AliveWorkplace.MOD_ID)) {
				continue;
			}
			check(raw, edict.id() + " name", edict.name());
			check(raw, edict.id() + " description", edict.description());
			edict.reform().ifPresent(r -> {
				check(raw, edict.id() + " reform name", r.name());
				check(raw, edict.id() + " reform line", r.line());
			});
		}
		helper.assertTrue(Edicts.find("long_shifts").orElseThrow().reform().isPresent() && Edicts.find("curfew").orElseThrow().reform().isPresent()
			&& Edicts.find("conscription").orElseThrow().reform().isPresent(), "an M30 edict without its reform");
		Language lang = Language.getInstance();
		for (String key : List.of("message.aliveworkplace.curfew.no_trade", "message.aliveworkplace.work_horn.used", "message.aliveworkplace.work_horn.rush",
			"message.aliveworkplace.work_horn.not_allowed", "message.aliveworkplace.work_horn.no_village", "message.aliveworkplace.tonic.drunk",
			"message.aliveworkplace.tonic.no_use", "message.aliveworkplace.tonic.disabled", "message.aliveworkplace.guild.full",
			"message.aliveworkplace.guild.chartered", "message.aliveworkplace.guild.succession", "message.aliveworkplace.edict.excluded",
			"message.aliveworkplace.edict.too_soon", "message.aliveworkplace.edict.no_slot")) {
			if (!lang.has(key)) {
				raw.add(key);
			}
		}
		helper.assertTrue(raw.isEmpty(), "raw or missing texts: " + raw);
		helper.succeed();
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	private static void check(List<String> raw, String what, Component text) {
		String s = text.getString();
		if (s.isBlank() || s.startsWith("edict.") || s.startsWith("reform.") || s.contains("aliveworkplace.")) {
			raw.add(what + "=" + s);
		}
	}

	/**
	 * A hall of radius 16 in the area's middle (nobody owns it, a Hamlet, nothing in force) in the morning, no other
	 * hall near; afterwards the hall is taken away and the clock, radius and caches put back.
	 */
	private static VillageHallBlockEntity village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		long time = level.getDayTime();
		level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + 1000);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		boolean moods = Moods.ENABLED;
		Moods.ENABLED = false;
		Leftovers.after(helper, () -> {
			level.removeBlock(hall, false);
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			Moods.ENABLED = moods;
			Guilds.forget();
			forget();
			VillageNeeds.forget();
		});
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setRank(VillageRanks.Rank.HAMLET);
		entity.setEdicts(List.of());
		entity.setReforms(List.of());
		forget();
		return entity;
	}

	/** No hall near and no mood (the pace's numbers alone), for the tonic tests. */
	private static void quiet(GameTestHelper helper) {
		Leftovers.halls(helper);
		boolean moods = Moods.ENABLED;
		Moods.ENABLED = false;
		forget();
		Leftovers.after(helper, () -> {
			Moods.ENABLED = moods;
			forget();
		});
	}

	/** A grown villager of {@code job} standing still (level 2, so they keep the job). */
	private static Villager grown(GameTestHelper helper, BlockPos at, VillagerProfession job) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setVillagerData(villager.getVillagerData().setProfession(job).setLevel(2));
		villager.setVillagerXp(10);
		villager.setNoAi(true);
		return villager;
	}

	/** A Master (level 5) of {@code job} named {@code name}, standing still. */
	private static Villager master(GameTestHelper helper, BlockPos at, String name, VillagerProfession job) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setNoAi(true);
		villager.setCustomName(Component.literal(name));
		villager.setVillagerData(villager.getVillagerData().setProfession(job).setLevel(5));
		return villager;
	}

	/** A librarian by the hall selling 3 bookshelves for 20 emeralds (fixed offers). */
	private static Villager librarian(GameTestHelper helper) {
		Villager librarian = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
		librarian.setNoAi(true);
		librarian.setVillagerData(librarian.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(2));
		MerchantOffers offers = new MerchantOffers();
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 20), new ItemStack(Items.BOOKSHELF, 3), 12, 5, 0.05f));
		librarian.setOffers(offers);
		return librarian;
	}

	/** {@code player} right-clicks {@code villager} with an empty hand: whether the trade screen opened (closed again after). */
	private static boolean trades(Villager villager, ServerPlayer player) {
		player.moveTo(villager.getX() + 1, villager.getY(), villager.getZ(), 90f, 0f);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.interactOn(villager, InteractionHand.MAIN_HAND);
		boolean open = player.containerMenu instanceof MerchantMenu;
		player.closeContainer();
		villager.setUnhappyCounter(0);
		return open;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos at = helper.absolutePos(new BlockPos(12, 2, 11));
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	/** The tonic's part of {@code villager}'s pace (1: none). */
	private static float tonicPart(Villager villager) {
		return Pace.of(villager).faster().stream().filter(p -> p.source() == Pace.TONIC).map(Pace.Part::factor).findFirst().orElse(1f);
	}

	private static void forget() {
		CivicEffects.forget();
		Moods.forget();
	}
}
