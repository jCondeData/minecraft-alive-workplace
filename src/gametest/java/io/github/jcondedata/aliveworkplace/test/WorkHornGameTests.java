package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.hall.WorkHorn;
import io.github.jcondedata.aliveworkplace.hall.WorkHornItem;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The Work Horn (ROADMAP 30.11): held up like a goat horn, it sounds and the village rushes, every grown villager's pace
 * 1/1.5 for the rush and back after; once a village a day ("The village has already answered the horn today."); worn
 * out (10 less happy) from the rush's end until dawn; with Long Shifts and partners the pace stops at the cap; a
 * stranger's horn does nothing in an owned village; the hall keeps the rush through a save; {@code workHorns} off: no
 * rush. Each test has its own batch, clears the area first and takes its hall away after.
 */
public class WorkHornGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final String LONG_SHIFTS = AliveWorkplace.id("long_shifts").toString();
	private static final float EPSILON = 1e-5f;

	/**
	 * Blown by the hall's owner: a grown farmer works at 1/1.5 of the time while the rush lasts and the child not at all;
	 * when it ends the pace is back and the farmer is worn out, 10 less happy, until dawn. The Book's last row reads
	 * rushing, then used.
	 */
	//$ gametest_ticks_batch AREA '300' '"hornRush"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "hornRush")
	public void theRushSpeedsTheVillageUpThenWearsItOut(GameTestHelper helper) {
		Village v = village(helper);
		long rush = WorkHorn.RUSH_TICKS;
		boolean moods = Moods.ENABLED;
		Leftovers.after(helper, () -> {
			WorkHorn.RUSH_TICKS = rush;
			Moods.ENABLED = moods;
		});
		Villager farmer = grown(helper, new BlockPos(4, 2, 4));
		Villager child = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 4));
		child.setAge(-24000);
		child.setNoAi(true);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = v.hall();
			hall.setOwner(v.player().getUUID(), v.player().getGameProfile().getName());
			Moods.ENABLED = false; // the pace's numbers alone (the mood's own pace would come and go)
			forget();
			float before = Pace.factor(farmer);
			helper.assertTrue(part(farmer) == 1f, "a rush before the horn: " + Pace.of(farmer));

			// The player's own entry point: held up as a goat horn, then it sounds.
			ItemStack horn = new ItemStack(ModItems.WORK_HORN);
			helper.assertTrue(ModItems.WORK_HORN.getUseAnimation(horn) == UseAnim.TOOT_HORN
				&& ModItems.WORK_HORN.getUseDuration(horn, v.player()) == WorkHornItem.HOLD_TICKS, "not held like a goat horn");
			WorkHorn.RUSH_TICKS = 60;
			v.player().setItemInHand(InteractionHand.MAIN_HAND, horn);
			ModItems.WORK_HORN.finishUsingItem(horn, level, v.player());
			long today = Chronicle.day(level);
			helper.assertTrue(hall.hornDay() == today && hall.rushUntil() == level.getGameTime() + 60,
				"no rush: day " + hall.hornDay() + ", until " + hall.rushUntil() + " at " + level.getGameTime());
			helper.assertTrue(v.player().getCooldowns().isOnCooldown(ModItems.WORK_HORN), "no cooldown after it sounded");
			forget();
			helper.assertTrue(Math.abs(part(farmer) - 1f / 1.5f) < EPSILON, "the horn's part: " + Pace.of(farmer));
			helper.assertTrue(Math.abs(Pace.factor(farmer) - before / 1.5f) < EPSILON, "rushing: " + Pace.factor(farmer) + " for " + before);
			helper.assertTrue(part(child) == 1f, "the child rushes: " + Pace.of(child));
			String described = Pace.describe(farmer).getString();
			helper.assertTrue(described.contains("the Work Horn's rush"), "status: " + described);
			helper.assertTrue(lore(v).stream().anyMatch(l -> l.startsWith("Rushing: 1 min left")), "the Book: " + lore(v));
			helper.assertTrue(ModAttachments.WORN_OUT.has(farmer) && !ModAttachments.WORN_OUT.has(child), "who answered");
			helper.assertTrue(!WorkHorn.wornOut(level, farmer), "worn out during the rush");

			helper.runAfterDelay(70, () -> {
				forget();
				helper.assertTrue(part(farmer) == 1f && Math.abs(Pace.factor(farmer) - before) < EPSILON, "after the rush: " + Pace.of(farmer));
				helper.assertTrue(lore(v).contains("Used today: ready again tomorrow"), "the Book after: " + lore(v));
				Moods.ENABLED = true;
				helper.assertTrue(WorkHorn.wornOut(level, farmer), "not worn out after the rush");
				Moods.Mood worn = Moods.work(level, farmer);
				helper.assertTrue(worn.bad().stream().anyMatch(c -> c.getString().equals("worn out")), "no reason: " + worn.bad());
				WorkHorn.WornOut kept = ModAttachments.WORN_OUT.get(farmer);
				ModAttachments.WORN_OUT.remove(farmer);
				int rested = Moods.work(level, farmer).score();
				ModAttachments.WORN_OUT.set(farmer, kept);
				helper.assertTrue(worn.score() == Math.max(0, rested - 10), "10 less happy: " + worn.score() + " vs " + rested);
				// Just before dawn still worn out; at dawn rested.
				long dawn = kept.untilDayTime();
				helper.assertTrue(dawn % VillageNeeds.DAY == 0 && dawn > level.getDayTime(), "dawn: " + dawn + " at " + level.getDayTime());
				level.setDayTime(dawn - 1);
				helper.assertTrue(WorkHorn.wornOut(level, farmer), "rested before dawn");
				level.setDayTime(dawn);
				helper.assertTrue(!WorkHorn.wornOut(level, farmer) && !ModAttachments.WORN_OUT.has(farmer), "still worn out at dawn");
				helper.assertTrue(Moods.work(level, farmer).bad().stream().noneMatch(c -> c.getString().equals("worn out")), "the reason stays");
				helper.succeed();
			});
		});
	}

	/** A second blow the same day is refused with its sentence; the next day the horn calls a rush again. */
	//$ gametest_ticks_batch AREA '100' '"hornOnce"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hornOnce")
	public void onceAVillageADay(GameTestHelper helper) {
		Village v = village(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = v.hall();
			helper.assertTrue(lore(v).contains("Ready: blow it in the village for a rush"), "the Book before: " + lore(v));
			WorkHorn.Result first = WorkHorn.blow(level, v.player(), v.player().blockPosition());
			helper.assertTrue(first.outcome() == WorkHorn.Outcome.RUSH, "first: " + first);
			helper.assertTrue(first.message() != null && first.message().getString().endsWith(" answers the horn: 0 villagers rush to work!"), "said: " + first.message());
			long until = hall.rushUntil();
			WorkHorn.Result second = WorkHorn.blow(level, v.player(), v.player().blockPosition());
			helper.assertTrue(second.outcome() == WorkHorn.Outcome.USED_TODAY && second.message() != null
				&& second.message().getString().equals("The village has already answered the horn today."), "second: " + second);
			helper.assertTrue(hall.rushUntil() == until, "the second blow moved the rush");
			level.setDayTime(level.getDayTime() + VillageNeeds.DAY);
			WorkHorn.Result tomorrow = WorkHorn.blow(level, v.player(), v.player().blockPosition());
			helper.assertTrue(tomorrow.outcome() == WorkHorn.Outcome.RUSH && hall.hornDay() == Chronicle.day(level), "the next day: " + tomorrow);
			// Far from any hall: nothing hears it.
			WorkHorn.Result nowhere = WorkHorn.blow(level, v.player(), helper.absolutePos(HALL).offset(500, 0, 500));
			helper.assertTrue(nowhere.outcome() == WorkHorn.Outcome.NO_VILLAGE
				&& nowhere.message().getString().equals("No village hears the horn here: blow it near a Village Hall."), "nowhere: " + nowhere);
			helper.succeed();
		});
	}

	/** Long Shifts, partners (a stand-in source: Pokémon need Cobblemon) and the rush together stop at the cap, also a lower one. */
	//$ gametest_ticks_batch AREA '100' '"hornCap"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hornCap")
	public void theCapHoldsWithLongShiftsAndPartners(GameTestHelper helper) {
		Village v = village(helper);
		boolean moods = Moods.ENABLED;
		int cap = Pace.MAX_PERCENT;
		Pace.register(new Pace.Source("partners", Pace.Kind.BONUS, x -> 0.55f, x -> Component.literal("Machop and Geodude")));
		Leftovers.after(helper, () -> {
			Pace.register(Pace.PARTNERS);
			Moods.ENABLED = moods;
			Pace.MAX_PERCENT = cap;
		});
		Villager farmer = grown(helper, new BlockPos(4, 2, 4));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = v.hall();
			hall.setRank(VillageRanks.Rank.HAMLET);
			hall.setEdicts(List.of(new Edicts.InForce(LONG_SHIFTS, Chronicle.day(level))));
			Moods.ENABLED = false;
			Pace.MAX_PERCENT = 200;
			forget();
			helper.assertTrue(WorkHorn.blow(level, v.player(), v.player().blockPosition()).outcome() == WorkHorn.Outcome.RUSH, "no rush");
			forget();
			Pace.Breakdown pace = Pace.of(farmer);
			List<String> ids = pace.faster().stream().map(p -> p.source().id()).toList();
			helper.assertTrue(ids.contains("partners") && ids.contains("edicts") && ids.contains("work_horn"), "sources: " + ids);
			// Penalties (a hall not kept well in a bare test area) still come after the cap.
			float penalties = pace.slower().stream().map(Pace.Part::factor).reduce(1f, (a, b) -> a * b);
			helper.assertTrue(pace.capped() && pace.bonuses() < 0.5f && Math.abs(pace.factor() - 0.5f * penalties) < EPSILON,
				"over the cap: factor " + pace.factor() + ", bonuses " + pace.bonuses() + ", penalties " + penalties);
			Pace.MAX_PERCENT = 150;
			helper.assertTrue(Math.abs(Pace.factor(farmer) - 100f / 150f * penalties) < EPSILON, "a cap of 150: " + Pace.factor(farmer));
			helper.succeed();
		});
	}

	/**
	 * In a village someone else owns, a stranger's horn sounds and calls no rush (and they are told why); in a village
	 * nobody owns anyone may call one.
	 */
	//$ gametest_ticks_batch AREA '100' '"hornStranger"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hornStranger")
	public void aStrangersHornDoesNothingInAnOwnedVillage(GameTestHelper helper) {
		Village v = village(helper);
		boolean moods = Moods.ENABLED;
		Leftovers.after(helper, () -> Moods.ENABLED = moods);
		Villager farmer = grown(helper, new BlockPos(4, 2, 4));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = v.hall();
			hall.setOwner(UUID.randomUUID(), "Jesse");
			Moods.ENABLED = false;
			forget();
			float before = Pace.factor(farmer);
			ItemStack horn = new ItemStack(ModItems.WORK_HORN);
			v.player().setItemInHand(InteractionHand.MAIN_HAND, horn);
			ModItems.WORK_HORN.finishUsingItem(horn, level, v.player());
			forget();
			helper.assertTrue(hall.hornDay() == -1 && hall.rushUntil() == 0, "a stranger's rush: " + hall.rushUntil());
			helper.assertTrue(part(farmer) == 1f && Pace.factor(farmer) == before, "the stranger sped them up: " + Pace.of(farmer));
			helper.assertTrue(!ModAttachments.WORN_OUT.has(farmer), "worn out by a stranger");
			WorkHorn.Result refused = WorkHorn.blow(level, v.player(), v.player().blockPosition());
			helper.assertTrue(refused.outcome() == WorkHorn.Outcome.NOT_ALLOWED && refused.message() != null
				&& refused.message().getString().startsWith("Only Jesse and their friends can call a rush in "), "refused: " + refused);
			// Nobody's village: anyone may.
			hall.setOwner(null, "");
			helper.assertTrue(WorkHorn.blow(level, v.player(), v.player().blockPosition()).outcome() == WorkHorn.Outcome.RUSH, "a village nobody owns");
			helper.succeed();
		});
	}

	/**
	 * The console (no player: the season run of 30.22 calls its rush a day so) may call a rush in a village somebody
	 * owns, once a day like anyone, and the villagers are worn out by it as by a player's.
	 */
	//$ gametest_ticks_batch AREA '100' '"hornConsole"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hornConsole")
	public void theConsoleCallsARushInAnOwnedVillage(GameTestHelper helper) {
		Village v = village(helper);
		Villager farmer = grown(helper, HALL.offset(2, 0, 2));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = v.hall();
			hall.setOwner(UUID.randomUUID(), "Jesse");
			WorkHorn.Result rush = WorkHorn.blow(level, null, helper.absolutePos(HALL));
			helper.assertTrue(rush.outcome() == WorkHorn.Outcome.RUSH, "the console's horn: " + rush);
			helper.assertTrue(hall.hornDay() == Chronicle.day(level) && WorkHorn.rushing(level, hall), "no rush: " + hall.rushUntil());
			helper.assertTrue(ModAttachments.WORN_OUT.has(farmer), "the farmer didn't answer");
			WorkHorn.Result again = WorkHorn.blow(level, null, helper.absolutePos(HALL));
			helper.assertTrue(again.outcome() == WorkHorn.Outcome.USED_TODAY, "twice a day: " + again);
			helper.succeed();
		});
	}

	/** Saved mid-rush, the hall keeps the day and the rush's end and a villager keeps being worn out; a hall saved before 30.11 has neither. */
	//$ gametest_ticks_batch AREA '100' '"hornSave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hornSave")
	public void aRushSurvivesASave(GameTestHelper helper) {
		Village v = village(helper);
		Villager farmer = grown(helper, new BlockPos(4, 2, 4));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = v.hall();
			BlockPos pos = helper.absolutePos(HALL);
			WorkHorn.blow(level, v.player(), v.player().blockPosition());
			CompoundTag tag = hall.saveWithFullMetadata(level.registryAccess());
			VillageHallBlockEntity reloaded = (VillageHallBlockEntity) BlockEntity.loadStatic(pos, level.getBlockState(pos), tag, level.registryAccess());
			helper.assertTrue(reloaded != null && reloaded.hornDay() == hall.hornDay() && reloaded.rushUntil() == hall.rushUntil()
				&& WorkHorn.rushing(level, reloaded), "the hall forgot: " + (reloaded == null ? null : reloaded.rushUntil()) + " for " + hall.rushUntil());
			tag.remove("hornDay");
			tag.remove("rushUntil");
			VillageHallBlockEntity old = (VillageHallBlockEntity) BlockEntity.loadStatic(pos, level.getBlockState(pos), tag, level.registryAccess());
			helper.assertTrue(old != null && old.hornDay() == -1 && old.rushUntil() == 0 && !WorkHorn.rushing(level, old), "a hall saved before");
			CompoundTag saved = farmer.saveWithoutId(new CompoundTag());
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(saved);
			helper.assertTrue(ModAttachments.WORN_OUT.get(copy) != null && ModAttachments.WORN_OUT.get(copy).equals(ModAttachments.WORN_OUT.get(farmer)),
				"the villager forgot: " + ModAttachments.WORN_OUT.get(copy));
			copy.discard();
			helper.succeed();
		});
	}

	/** {@code workHorns} off (on by default): the horn calls no rush, and a rush under way stops counting; the Book says so. */
	//$ gametest_ticks_batch AREA '100' '"hornOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hornOff")
	public void workHornsOffCallsNoRush(GameTestHelper helper) {
		Village v = village(helper);
		boolean enabled = WorkHorn.ENABLED;
		boolean moods = Moods.ENABLED;
		Leftovers.after(helper, () -> {
			WorkHorn.ENABLED = enabled;
			Moods.ENABLED = moods;
		});
		Villager farmer = grown(helper, new BlockPos(4, 2, 4));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = v.hall();
			helper.assertTrue(WorkplaceConfig.parse("{}").workHorns && !WorkplaceConfig.parse("{\"workHorns\": false}").workHorns, "the config switch");
			Moods.ENABLED = false;
			WorkHorn.ENABLED = false;
			forget();
			ItemStack horn = new ItemStack(ModItems.WORK_HORN);
			ModItems.WORK_HORN.finishUsingItem(horn, level, v.player());
			helper.assertTrue(hall.hornDay() == -1 && hall.rushUntil() == 0 && part(farmer) == 1f, "a rush with horns off");
			helper.assertTrue(lore(v).contains("Work Horns are switched off on this server"), "the Book: " + lore(v));
			WorkHorn.ENABLED = true;
			helper.assertTrue(WorkHorn.blow(level, v.player(), v.player().blockPosition()).outcome() == WorkHorn.Outcome.RUSH, "on again");
			forget();
			helper.assertTrue(part(farmer) < 1f, "no rush when on");
			WorkHorn.ENABLED = false;
			helper.assertTrue(part(farmer) == 1f && !WorkHorn.rushing(level, hall), "a rush under way still counts with horns off");
			helper.succeed();
		});
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	private record Village(VillageHallBlockEntity hall, ServerPlayer player) {
	}

	/**
	 * A hall of radius 16 in the area's middle (nobody owns it yet) and a player by it, in the morning; afterwards the
	 * hall is taken away and the clock, radius and caches put back.
	 */
	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		long time = level.getDayTime();
		level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + 1000);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		Leftovers.after(helper, () -> {
			level.getServer().getPlayerList().remove(player);
			level.removeBlock(hall, false);
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			forget();
			VillageNeeds.forget();
		});
		return new Village((VillageHallBlockEntity) level.getBlockEntity(hall), player);
	}

	/** A grown farmer standing still (level 2, so they keep the job). */
	private static Villager grown(GameTestHelper helper, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		villager.setVillagerXp(10);
		villager.setNoAi(true);
		return villager;
	}

	/** The horn's part of {@code villager}'s pace (1: none). */
	private static float part(Villager villager) {
		return Pace.of(villager).faster().stream().filter(p -> p.source() == Pace.WORK_HORN).map(Pace.Part::factor).findFirst().orElse(1f);
	}

	/** The Work Horn's lines on the Book of Edicts' last row. */
	private static List<String> lore(Village v) {
		ItemStack icon = EdictBook.forTest(v.player(), helper(v)).icon(EdictBook.HORN);
		ItemLore lore = icon.get(DataComponents.LORE);
		return !icon.is(ModItems.WORK_HORN) || lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static BlockPos helper(Village v) {
		return v.hall().getBlockPos();
	}

	private static void forget() {
		CivicEffects.forget();
		Moods.forget();
	}
}
