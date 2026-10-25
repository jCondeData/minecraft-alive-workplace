package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.guard.RaidDens;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.LeaderBracelets;
import io.github.jcondedata.aliveworkplace.trainer.MegaBracelets;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.wonder.StadiumDays;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-10-24) for Milestone 38's raid dens and Megas, written from the spec (ROADMAP 38.2 to 38.6 and
 * docs/design/M38.md), not the code. RaidDenCompatTests, MegaTrainerCompatTests and MegaStoneCompatTests prove the Done
 * whens; these prove the edges round them:
 * <ul>
 * <li>38.2: the 160-block reach from both sides (straight and on the diagonal, so it's a distance, not a square); a den
 * found again is recorded once and keeps the day it was first seen; a den turned into another block is dropped; two halls
 * near one den both list it; the Defence page shows the seven nearest, nearest first, and its empty line; every tier and
 * type reads as a sentence; with M38's gate shut nothing is found or shown.</li>
 * <li>38.3 and 38.4 were taken out by 38.1a: the Defence page says nothing of wardens or challenges.</li>
 * <li>38.5: a Trainer Leader Mega Evolves only in a City (not a Hamlet, Village or Town, nor with no hall), a Stadium
 * visitor does even from a Town, an Expert doesn't and a Master always does.</li>
 * <li>38.6: the bracelet's two chat lines read right with their real arguments, and a player with a full inventory
 * still gets the bracelet (dropped at their feet), once.</li>
 * </ul>
 * Each test runs in its own batch and puts back every block and switch it changed, passed or failed.
 */
public class QaDenMegaCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos HALL = new BlockPos(2, 2, 2);

	// ---- 38.2 helpers -------------------------------------------------------------------------------------------------

	/** A den crystal at the absolute {@code pos} (its chunk loaded for it), found as the chunk-load path finds it. */
	private static void denAt(GameTestHelper helper, BlockPos pos, BlockState state, List<BlockPos> placed) {
		ServerLevel level = helper.getLevel();
		level.setBlockAndUpdate(pos, state);
		placed.add(pos);
		RaidDens.chunkLoaded(level, level.getChunkAt(pos));
		RaidDens.tick(level);
	}

	/** Takes every block this test set away again at its end, passed or failed. */
	private static List<BlockPos> cleanup(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		List<BlockPos> placed = new ArrayList<>();
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.after(helper, () -> {
			for (BlockPos p : placed) {
				level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
			}
			for (BlockPos p : placed) {
				RaidDens.near(level, p); // the next look drops what's gone
			}
		});
		return placed;
	}

	private static BlockPos hall(GameTestHelper helper) {
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(HALL);
	}

	/**
	 * The first of {@code ways} in which the point {@code steps} along it from {@code hall} has no Village Hall within
	 * {@link RaidDens#REACH} (this test's hall is just out of reach there, so any hall found is another test's).
	 */
	private static int[] clearWay(GameTestHelper helper, BlockPos hall, int[][] ways, int steps) {
		for (int[] way : ways) {
			BlockPos at = hall.offset(steps * way[0], 0, steps * way[1]);
			if (RaidDens.hallNear(helper.getLevel(), at).isEmpty()) {
				return way;
			}
		}
		throw new net.minecraft.gametest.framework.GameTestAssertException("setup: other tests' halls are within reach of "
			+ steps + " blocks off this hall every way");
	}

	private static boolean known(GameTestHelper helper, BlockPos den) {
		return RaidDens.Sites.get(helper.getLevel()).dens().stream().anyMatch(d -> d.pos().equals(den));
	}

	private static Optional<RaidDens.Den> listed(GameTestHelper helper, BlockPos hall, BlockPos den) {
		return RaidDens.near(helper.getLevel(), hall).stream().filter(d -> d.pos().equals(den)).findFirst();
	}

	private static List<String> page(GameTestHelper helper, BlockPos hall, int slot) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		DefencePage.render(menu, helper.getLevel(), hall);
		List<String> lines = RaidDenCompatTests.lines(menu.icon(slot));
		helper.getLevel().getServer().getPlayerList().remove(player);
		return lines;
	}

	/** Every line on the Defence page, slot by slot. */
	private static List<List<String>> wholePage(GameTestHelper helper, BlockPos hall) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		DefencePage.render(menu, helper.getLevel(), hall);
		List<List<String>> out = new ArrayList<>();
		for (int slot = 0; slot < 54; slot++) {
			ItemStack icon;
			try {
				icon = menu.icon(slot);
			} catch (IndexOutOfBoundsException e) {
				break;
			}
			out.add(icon == null || icon.isEmpty() ? List.of() : RaidDenCompatTests.lines(icon));
		}
		helper.getLevel().getServer().getPlayerList().remove(player);
		return out;
	}

	// ---- 38.2 ---------------------------------------------------------------------------------------------------------

	/**
	 * "Within 160 blocks of a hall", from both sides: a den 160 blocks east is the village's, one 161 blocks east isn't
	 * (not even recorded); on the diagonal, 113 east and 113 south (159.8 blocks) is, 114 and 114 (161.2) isn't, so the
	 * reach is a distance and not a 160-block square. East and south-east unless another test's hall reaches there (then
	 * the first other way none does).
	 */
	//$ gametest_batch AREA '"qaDenReach"'
	@GameTest(template = AREA, batch = "qaDenReach")
	public void theDenReachIs160BlocksEachWay(GameTestHelper helper) {
		List<BlockPos> placed = cleanup(helper);
		BlockPos hall = hall(helper);
		BlockState den = RaidDenCompatTests.den("tier_two", "fire", true);
		// Halls other tests in this run left standing count as halls too (B417: one within 160 blocks of the den 161 blocks
		// east recorded it in CI), so the dens go the way no other hall reaches: "the only hall" stays true.
		int[] way = clearWay(helper, hall, new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}, 161);
		int[] diag = clearWay(helper, hall, new int[][] {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}, 114);
		BlockPos in = hall.offset(160 * way[0], 0, 160 * way[1]);
		BlockPos out = hall.offset(161 * way[0], 0, 161 * way[1]);
		BlockPos diagIn = hall.offset(113 * diag[0], 0, 113 * diag[1]);
		BlockPos diagOut = hall.offset(114 * diag[0], 0, 114 * diag[1]);
		for (BlockPos p : List.of(in, out, diagIn, diagOut)) {
			denAt(helper, p, den, placed);
		}
		helper.assertTrue(listed(helper, hall, in).isPresent(), "a den exactly 160 blocks off isn't listed");
		helper.assertTrue(listed(helper, hall, diagIn).isPresent(), "a den 159.8 blocks off on the diagonal isn't listed");
		helper.assertTrue(listed(helper, hall, out).isEmpty(), "a den 161 blocks off is listed");
		helper.assertTrue(listed(helper, hall, diagOut).isEmpty(), "a den 161.2 blocks off on the diagonal is listed (a square, not a distance)");
		helper.assertFalse(known(helper, out), "a den 161 blocks from the only hall was recorded");
		helper.assertFalse(known(helper, diagOut), "a den 161.2 blocks from the only hall was recorded");
		helper.succeed();
	}

	/**
	 * "Recorded once": a den whose chunk loads again days later (and whose block entity is seen again) stays one entry
	 * with the day it was first found; a change of state is read in but keeps that day.
	 */
	//$ gametest_batch AREA '"qaDenOnce"'
	@GameTest(template = AREA, batch = "qaDenOnce")
	public void aDenFoundAgainIsRecordedOnce(GameTestHelper helper) {
		List<BlockPos> placed = cleanup(helper);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		PartnerShowsCompatTests.after(helper, () -> level.setDayTime(time));
		BlockPos hall = hall(helper);
		BlockPos at = hall.offset(6, 0, 4);
		denAt(helper, at, RaidDenCompatTests.den("tier_three", "fire", true), placed);
		long first = Chronicle.day(level);
		RaidDens.Den den = listed(helper, hall, at).orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("the den isn't listed"));
		helper.assertTrue(den.day() == first, "found on day " + den.day() + ", today is " + first);
		level.setDayTime(time + 3 * 24000L);
		helper.assertTrue(Chronicle.day(level) == first + 3, "the day didn't move on");
		RaidDens.chunkLoaded(level, level.getChunkAt(at));
		RaidDens.blockEntityLoaded(level.getBlockEntity(at), level);
		RaidDens.tick(level);
		RaidDens.seen(level, at);
		long entries = RaidDens.Sites.get(level).dens().stream().filter(d -> d.pos().equals(at)).count();
		helper.assertTrue(entries == 1, entries + " entries for one den");
		List<RaidDens.Den> near = RaidDens.near(level, hall).stream().filter(d -> d.pos().equals(at)).toList();
		helper.assertTrue(near.size() == 1 && near.get(0).day() == first, "after loading again: " + near + " (first seen day " + first + ")");
		level.setBlockAndUpdate(at, RaidDenCompatTests.den("tier_five", "water", false));
		RaidDens.Den now = listed(helper, hall, at).orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("the changed den isn't listed"));
		helper.assertTrue(now.tier() == 5 && now.type().equals("water") && !now.active() && now.day() == first, "the changed den: " + now);
		String found = page(helper, hall, DefencePage.DENS[0]).stream().filter(l -> l.startsWith("Found on day")).findFirst().orElse("none");
		helper.assertTrue(found.equals("Found on day " + first), "the page's day line: " + found);
		helper.succeed();
	}

	/** "A den broken or gone is dropped": one turned into another block (stone), not only air, goes from the list and the save. */
	//$ gametest_batch AREA '"qaDenReplaced"'
	@GameTest(template = AREA, batch = "qaDenReplaced")
	public void aDenTurnedToStoneIsDropped(GameTestHelper helper) {
		List<BlockPos> placed = cleanup(helper);
		BlockPos hall = hall(helper);
		BlockPos at = hall.offset(5, 0, 5);
		denAt(helper, at, RaidDenCompatTests.den("tier_one", "grass", true), placed);
		helper.assertTrue(listed(helper, hall, at).isPresent(), "the den isn't listed");
		helper.getLevel().setBlockAndUpdate(at, Blocks.STONE.defaultBlockState());
		helper.assertTrue(listed(helper, hall, at).isEmpty(), "a den turned to stone is still listed");
		helper.assertFalse(known(helper, at), "a den turned to stone is still saved");
		List<String> empty = page(helper, hall, DefencePage.DENS[3]);
		helper.assertTrue(empty.size() >= 2 && empty.get(0).equals("No raid den known")
			&& empty.get(1).equals("Raid dens within 160 blocks show here once someone has been near them"), "the empty den row: " + empty);
		helper.succeed();
	}

	/** "A den is listed for every hall within 160 blocks of it": two halls 80 blocks either side both see it; a third 161 off doesn't. */
	//$ gametest_batch AREA '"qaDenTwoHalls"'
	@GameTest(template = AREA, batch = "qaDenTwoHalls")
	public void twoHallsNearOneDenBothListIt(GameTestHelper helper) {
		List<BlockPos> placed = cleanup(helper);
		ServerLevel level = helper.getLevel();
		BlockPos first = hall(helper);
		BlockPos den = first.offset(80, 0, 0);
		BlockPos second = first.offset(160, 0, 0);
		BlockPos third = den.offset(161, 0, 0);
		for (BlockPos h : List.of(second, third)) {
			level.setBlockAndUpdate(h, ModBlocks.VILLAGE_HALL.defaultBlockState());
			placed.add(h);
		}
		denAt(helper, den, RaidDenCompatTests.den("tier_four", "dragon", true), placed);
		helper.assertTrue(listed(helper, first, den).isPresent(), "the first hall doesn't list the den");
		helper.assertTrue(listed(helper, second, den).isPresent(), "the second hall doesn't list the den");
		helper.assertTrue(listed(helper, third, den).isEmpty(), "a hall 161 blocks off lists the den");
		List<String> secondPage = page(helper, second, DefencePage.DENS[0]);
		helper.assertTrue(!secondPage.isEmpty() && secondPage.get(0).equals("Raid den, tier 4, Dragon, active"), "the second hall's den row: " + secondPage);
		helper.succeed();
	}

	/**
	 * The Defence page's den row holds seven: with eight dens known, the seven nearest show, nearest first, each with its
	 * own line, and the farthest doesn't; nothing on the page reads as a raw key, and nothing speaks of Den Wardens or
	 * the village's own challenges (taken out by 38.1a).
	 */
	//$ gametest_batch AREA '"qaDenSeven"'
	@GameTest(template = AREA, batch = "qaDenSeven")
	public void theDefencePageShowsTheSevenNearest(GameTestHelper helper) {
		List<BlockPos> placed = cleanup(helper);
		BlockPos hall = hall(helper);
		String[] types = {"fire", "water", "grass", "electric", "ice", "dragon", "ghost", "rock"};
		List<String> expect = new ArrayList<>();
		for (int i = 0; i < types.length; i++) {
			// Placed farthest first, so the page's order can't be the order they were found in.
			int n = types.length - 1 - i;
			BlockPos at = hall.offset(12 + n * 9, 0, 0);
			denAt(helper, at, RaidDenCompatTests.den("tier_one", types[n], true), placed);
		}
		for (int n = 0; n < types.length; n++) {
			expect.add("Raid den, tier 1, " + Character.toUpperCase(types[n].charAt(0)) + types[n].substring(1) + ", active");
		}
		List<List<String>> lines = wholePage(helper, hall);
		List<String> shown = new ArrayList<>();
		for (int slot : DefencePage.DENS) {
			shown.add(lines.get(slot).isEmpty() ? "(empty)" : lines.get(slot).get(0));
		}
		helper.assertTrue(shown.equals(expect.subList(0, 7)), "the den row: " + shown + ", expected " + expect.subList(0, 7));
		List<String> problems = new ArrayList<>();
		for (int slot = 0; slot < lines.size(); slot++) {
			for (String l : lines.get(slot)) {
				String low = l.toLowerCase(java.util.Locale.ROOT);
				if (l.equals(expect.get(7))) {
					problems.add("the eighth den is shown in slot " + slot);
				}
				if (l.contains("aliveworkplace.") || l.contains("%") || l.contains("cobblemon.type")) {
					problems.add("slot " + slot + " raw: " + l);
				}
				if (low.contains("warden") || low.contains("challenge")) {
					problems.add("slot " + slot + " still speaks of 38.3/38.4: " + l);
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** Every tier and every type the den block can have reads as "Raid den, tier N, Type, active/resting", in words. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void everyTierAndTypeReadsAsASentence(GameTestHelper helper) {
		BlockState state = RaidDenCompatTests.den("tier_one", "fire", true);
		Property<?> types = state.getBlock().getStateDefinition().getProperty("raid_type");
		Property<?> tiers = state.getBlock().getStateDefinition().getProperty("raid_tier");
		helper.assertTrue(types != null && tiers != null, "the den block has no raid_type or raid_tier");
		List<String> tierNames = names(tiers);
		helper.assertTrue(tierNames.equals(RaidDens.TIERS), "the den's tiers " + tierNames + " aren't the seven the mod reads " + RaidDens.TIERS);
		List<String> wrong = new ArrayList<>();
		int count = 0;
		for (String type : names(types)) {
			for (String tier : tierNames) {
				for (boolean active : new boolean[] {true, false}) {
					BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
					RaidDens.Den den = new RaidDens.Den(at, RaidDens.TIERS.indexOf(tier) + 1, type, active, 1);
					String line = RaidDens.describe(den).getString();
					if (!line.matches("Raid den, tier [1-7], [A-Z][a-z]+, (active|resting)") || !line.contains("tier " + (RaidDens.TIERS.indexOf(tier) + 1) + ",")) {
						wrong.add(type + "/" + tier + ": " + line);
					}
					count++;
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "lines that don't read right: " + wrong);
		for (String key : List.of("raid_den", "raid_den_active", "raid_den_resting", "raid_den_active_hint", "raid_den_resting_hint", "raid_den_found",
				"no_raid_den", "no_raid_den_hint")) {
			helper.assertTrue(Language.getInstance().has("screen.aliveworkplace.defence." + key), "no English for " + key);
		}
		helper.assertTrue(count >= 7 * 2 * 18, "only " + count + " lines checked");
		helper.succeed();
	}

	private static List<String> names(Property<?> property) {
		return names2(property);
	}

	private static <T extends Comparable<T>> List<String> names2(Property<T> property) {
		return property.getPossibleValues().stream().map(property::getName).toList();
	}

	/** M38's gate shut (as for players until 2.2): a den near a hall isn't found, listed or shown; opened again, it is. */
	//$ gametest_batch AREA '"qaDenGate"'
	@GameTest(template = AREA, batch = "qaDenGate")
	public void withTheGateShutNoDenIsFound(GameTestHelper helper) {
		List<BlockPos> placed = cleanup(helper);
		boolean open = Expansions.openForTests;
		PartnerShowsCompatTests.after(helper, () -> Expansions.openForTests = open);
		BlockPos hall = hall(helper);
		BlockPos at = hall.offset(4, 0, 6);
		Expansions.openForTests = false;
		try {
			helper.assertFalse(Expansions.isOpen("M38"), "M38 is still open");
			denAt(helper, at, RaidDenCompatTests.den("tier_two", "ice", true), placed);
			RaidDens.blockEntityLoaded(helper.getLevel().getBlockEntity(at), helper.getLevel());
			RaidDens.tick(helper.getLevel());
			helper.assertTrue(RaidDens.near(helper.getLevel(), hall).isEmpty(), "dens listed with M38 shut");
			for (List<String> lines : wholePage(helper, hall)) {
				for (String l : lines) {
					helper.assertFalse(l.startsWith("Raid den") || l.equals("No raid den known"), "the Defence page shows dens with M38 shut: " + l);
				}
			}
		} finally {
			Expansions.openForTests = open;
		}
		helper.assertFalse(known(helper, at), "a den was recorded while M38 was shut");
		RaidDens.chunkLoaded(helper.getLevel(), helper.getLevel().getChunkAt(at));
		RaidDens.tick(helper.getLevel());
		helper.assertTrue(listed(helper, hall, at).isPresent(), "opened again, the den isn't found when its chunk loads");
		helper.succeed();
	}

	// ---- 38.5 ---------------------------------------------------------------------------------------------------------

	private static String team(Villager v) {
		List<Pokemon> t = CobblemonTrainers.teamOf(v);
		return MegaTrainerCompatTests.stones(t) + " stones in " + MegaTrainerCompatTests.show(t);
	}

	/**
	 * The rank boundary: a Trainer Leader of a Hamlet, a Village or a Town holds no Mega Stone and wears no bracelet, of a
	 * City exactly one; a Leader with no hall in reach none; a Stadium visitor from a Town does; a City's Expert doesn't;
	 * a Hamlet's Master does (as before 38.5).
	 */
	//$ gametest_batch AREA '"qaMegaRanks"'
	@GameTest(template = AREA, batch = "qaMegaRanks")
	public void onlyACitysLeaderMegaEvolves(GameTestHelper helper) {
		try {
			helper.assertTrue(MegaBracelets.MOD && MegaBracelets.extraMegas(), "Mega Showdown or villageMegas missing");
			VillageHallBlockEntity hall = MegaTrainerCompatTests.hall(helper, VillageRanks.Rank.HAMLET);
			List<String> wrong = new ArrayList<>();
			int x = 2;
			for (VillageRanks.Rank rank : VillageRanks.Rank.values()) {
				hall.setRank(rank);
				VillageHalls.forgetNearest();
				helper.assertTrue(VillageRanks.of(helper.getLevel(), helper.absolutePos(MegaTrainerCompatTests.HALL)) == rank, "the hall isn't a " + rank);
				Villager leader = MegaTrainerCompatTests.villager(helper, new BlockPos(x++, 2, 3), ModVillagers.TRAINER_LEADER, 2);
				boolean city = rank == VillageRanks.Rank.CITY;
				long stones = MegaTrainerCompatTests.stones(CobblemonTrainers.teamOf(leader));
				if (stones != (city ? 1 : 0) || MegaBracelets.wears(leader) != city) {
					wrong.add(rank + " Leader: " + team(leader) + ", bracelet " + MegaBracelets.wears(leader));
				}
				if (rank == VillageRanks.Rank.TOWN) {
					Villager visitor = MegaTrainerCompatTests.villager(helper, new BlockPos(x++, 2, 5), ModVillagers.TRAINER_LEADER, 2);
					ModAttachments.STADIUM_VISITOR.set(visitor, new StadiumDays.Visitor(helper.absolutePos(MegaTrainerCompatTests.HALL), UUID.randomUUID(),
						Optional.empty(), new UUID(0, 0), 0, false, 0));
					if (MegaTrainerCompatTests.stones(CobblemonTrainers.teamOf(visitor)) != 1 || !MegaBracelets.wears(visitor)) {
						wrong.add("a Stadium visitor in a Town: " + team(visitor) + ", bracelet " + MegaBracelets.wears(visitor));
					}
				}
				if (rank == VillageRanks.Rank.CITY) {
					Villager expert = MegaTrainerCompatTests.villager(helper, new BlockPos(x++, 2, 5), ModVillagers.TRAINER, 4);
					if (MegaTrainerCompatTests.stones(CobblemonTrainers.teamOf(expert)) != 0 || MegaBracelets.wears(expert)) {
						wrong.add("a City's Expert: " + team(expert) + ", bracelet " + MegaBracelets.wears(expert));
					}
				}
				if (rank == VillageRanks.Rank.HAMLET) {
					Villager master = MegaTrainerCompatTests.villager(helper, new BlockPos(x++, 2, 5), ModVillagers.TRAINER, 5);
					if (MegaTrainerCompatTests.stones(CobblemonTrainers.teamOf(master)) != 1 || !MegaBracelets.wears(master)) {
						wrong.add("a Hamlet's Master: " + team(master) + ", bracelet " + MegaBracelets.wears(master));
					}
				}
			}
			helper.setBlock(MegaTrainerCompatTests.HALL, Blocks.AIR);
			VillageHalls.forgetNearest();
			Villager homeless = MegaTrainerCompatTests.villager(helper, new BlockPos(7, 2, 7), ModVillagers.TRAINER_LEADER, 2);
			if (MegaTrainerCompatTests.stones(CobblemonTrainers.teamOf(homeless)) != 0 || MegaBracelets.wears(homeless)) {
				wrong.add("a Leader with no hall: " + team(homeless) + ", bracelet " + MegaBracelets.wears(homeless));
			}
			helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		} finally {
			MegaTrainerCompatTests.done(helper);
		}
		helper.succeed();
	}

	// ---- 38.6 ---------------------------------------------------------------------------------------------------------

	/** A real player on the server whose chat lines are kept. */
	private static ServerPlayer listener(GameTestHelper helper, List<Component> heard) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "QaMega"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				heard.add(message);
				super.sendSystemMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 2, 3))));
		player.getInventory().clearContent();
		return player;
	}

	private static List<String> said(List<Component> heard, String start) {
		return heard.stream().map(Component::getString).filter(s -> s.startsWith(start)).toList();
	}

	/**
	 * The bracelet's lines as the player reads them: after the first Leader of two, "Trainer Leaders of <realm> beaten: 1
	 * of 2. ..." (once, not again for beating the same Leader twice); after the second, "You've beaten every Trainer Leader
	 * of <realm>! The hall of <capital> gives you a Mega Bracelet." and the bracelet, once.
	 */
	//$ gametest_batch AREA '"qaMegaBraceletSays"'
	@GameTest(template = AREA, batch = "qaMegaBraceletSays")
	public void theBraceletsLinesReadRight(GameTestHelper helper) {
		MegaStoneCompatTests.Land r = MegaStoneCompatTests.realm(helper);
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = listener(helper, heard);
		var server = helper.getLevel().getServer();
		try {
			Trainers.battleOver(server, r.first().getUUID(), player.getUUID(), true);
			Trainers.battleOver(server, r.first().getUUID(), player.getUUID(), true);
			List<String> progress = said(heard, "Trainer Leaders of ");
			helper.assertTrue(progress.size() == 1, "progress lines after beating the capital's Leader twice: " + progress + " (all: "
				+ heard.stream().map(Component::getString).toList() + ")");
			String realm = io.github.jcondedata.aliveworkplace.realm.Realms.title(server, r.realm()).getString();
			String capital = io.github.jcondedata.aliveworkplace.realm.Realms.name(server, r.realm().capital()).getString();
			helper.assertTrue(progress.get(0).equals("Trainer Leaders of " + realm + " beaten: 1 of 2. Beat them all for a Mega Bracelet from the capital's hall."),
				"the progress line: " + progress.get(0));
			helper.assertTrue(MegaStoneCompatTests.bracelets(player) == 0, "a bracelet after one Leader of two");
			Trainers.battleOver(server, r.second().getUUID(), player.getUUID(), true);
			List<String> given = said(heard, "You've beaten every Trainer Leader");
			helper.assertTrue(given.size() == 1 && given.get(0).equals("You've beaten every Trainer Leader of " + realm + "! The hall of " + capital
				+ " gives you a Mega Bracelet."), "the given line: " + given);
			helper.assertTrue(MegaStoneCompatTests.bracelets(player) == 1, MegaStoneCompatTests.bracelets(player) + " bracelets, expected 1");
			for (Component c : heard) {
				helper.assertFalse(c.getString().contains("aliveworkplace.") || c.getString().contains("%"), "a raw line: " + c.getString());
			}
			Trainers.battleOver(server, r.second().getUUID(), player.getUUID(), true);
			helper.assertTrue(said(heard, "You've beaten every Trainer Leader").size() == 1 && said(heard, "Trainer Leaders of ").size() == 1,
				"lines again after beating a Leader once more: " + heard.stream().map(Component::getString).toList());
		} finally {
			server.getPlayerList().remove(player);
			MegaStoneCompatTests.cleanUp(helper, r);
		}
		helper.succeed();
	}

	/** A player whose inventory is full still gets the bracelet (at their feet, not lost), and only one. */
	//$ gametest_batch AREA '"qaMegaBraceletFull"'
	@GameTest(template = AREA, batch = "qaMegaBraceletFull")
	public void aFullInventoryStillGetsTheBracelet(GameTestHelper helper) {
		MegaStoneCompatTests.Land r = MegaStoneCompatTests.realm(helper);
		ServerPlayer player = r.player();
		var server = helper.getLevel().getServer();
		var item = MegaStoneCompatTests.ms("mega_bracelet");
		try {
			player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 2, 3))));
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			}
			Trainers.battleOver(server, r.first().getUUID(), player.getUUID(), true);
			Trainers.battleOver(server, r.second().getUUID(), player.getUUID(), true);
			Trainers.battleOver(server, r.second().getUUID(), player.getUUID(), true);
			List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8), e -> e.getItem().is(item));
			int total = MegaStoneCompatTests.bracelets(player) + dropped.stream().mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertTrue(total == 1, total + " bracelets for a player with a full inventory (held " + MegaStoneCompatTests.bracelets(player)
				+ ", dropped " + dropped.size() + "), expected 1");
			helper.assertTrue(LeaderBracelets.given(server, player.getUUID(), r.realm().id()), "the bracelet isn't recorded");
			dropped.forEach(ItemEntity::discard);
		} finally {
			MegaStoneCompatTests.cleanUp(helper, r);
		}
		helper.succeed();
	}
}
