package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.guard.Relief;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * QA (lane-a, 2026-11-07) of B990 from its spec and decision note: hired help (a mercenary band hired at the hall, relief
 * riders (33.17), a story's visitor) leaves the hall's villager, housed, fed and lit counts, the wellbeing, the work pace
 * and the growth cap as they were; a hired Guard still counts among the village's guards. Read through the hall screen
 * a player opens: the Wellbeing (cake) button's lines and the People button's growth line.
 */
public class QaHiredHelpCounts1107cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final int WELLBEING = 5;
	private static final int PEOPLE = 1;

	// ---- set-up --------------------------------------------------------------------------------------------------

	/** A hall at noon, its village 16 blocks round, opened for tests; the band and riders cleared after. */
	static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		boolean open = Expansions.openForTests;
		int radius = VillageHalls.RADIUS;
		int cap = VillageGrowth.CAP;
		int edge = Relief.EDGE;
		VillageHalls.RADIUS = 16;
		Expansions.openForTests = true;
		Leftovers.after(helper, () -> {
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(24), Mercenaries::isMercenary)) {
				if (v.getVehicle() != null) {
					v.getVehicle().discard();
				}
				v.discard();
			}
			VillageHalls.RADIUS = radius;
			VillageGrowth.CAP = cap;
			Relief.EDGE = edge;
			Relief.forget();
			Expansions.openForTests = open;
			VillageNeeds.forget();
			level.setDayTime(time);
		});
		level.setDayTime(6000);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(HALL);
	}

	static BlockPos bed(GameTestHelper helper, BlockPos foot) {
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		return helper.absolutePos(foot.south());
	}

	/** {@code n} still villagers of the village's own, each in their own bed (z 9-10), standing at z 12. */
	static List<Villager> ownHoused(GameTestHelper helper, int n) {
		List<Villager> own = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			BlockPos bed = bed(helper, new BlockPos(1 + 2 * i, 2, 9));
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(1 + 2 * i, 2, 12));
			v.setNoAi(true);
			helper.assertTrue(CampBeds.claim(helper.getLevel(), v, bed), "setup: villager " + i + " couldn't take a bed");
			own.add(v);
		}
		return own;
	}

	static ServerPlayer player(GameTestHelper helper, BlockPos hall) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(hall.getX() + 0.5, hall.getY() + 1, hall.getZ() + 0.5);
		Leftovers.after(helper, () -> {
			player.getInventory().clearContent();
			helper.getLevel().getServer().getPlayerList().remove(player);
		});
		return player;
	}

	/** {@code player} hires a band at the hall, as the Mercenaries button does; returns the band, kept still. */
	static List<Villager> hire(GameTestHelper helper, ServerPlayer player, BlockPos hall, boolean still) {
		ServerLevel level = helper.getLevel();
		player.getInventory().add(new ItemStack(Items.EMERALD, Mercenaries.PRICE_EMERALDS));
		Component said = Mercenaries.hire(level, hall, player);
		List<Villager> band = Mercenaries.near(level, hall);
		helper.assertTrue(band.size() == Mercenaries.BAND, "setup: no band hired: " + said.getString());
		band.forEach(v -> v.setNoAi(still));
		return band;
	}

	// ---- what the player reads ------------------------------------------------------------------------------------

	static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	/** The Wellbeing button as the hall screen shows it: its title, then its lines. */
	static List<String> wellbeing(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		VillageNeeds.forget();
		ItemStack cake = VillageHallScreen.forTest(player, hall).icon(WELLBEING);
		helper.assertTrue(cake.is(Items.CAKE), "no Wellbeing button on the hall screen: " + cake);
		List<String> lines = new ArrayList<>();
		lines.add(cake.getHoverName().getString());
		lines.addAll(lore(cake));
		return lines;
	}

	/** The line of the Wellbeing button that starts with {@code start} ("Sleeping in a bed", "Fed today", "Guards"). */
	static String line(GameTestHelper helper, List<String> lines, String start) {
		return lines.stream().filter(l -> l.startsWith(start)).findFirst()
			.orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("no '" + start + "' line in " + lines));
	}

	static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	/** The People button's last line: what keeps the village from growing. */
	static String growthLine(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		VillageNeeds.forget();
		ItemStack people = VillageHallScreen.forTest(player, hall).icon(PEOPLE);
		helper.assertTrue(people.is(Items.EMERALD), "no People button on the hall screen: " + people);
		List<String> lines = lore(people);
		helper.assertTrue(!lines.isEmpty(), "the People button has no lines");
		return lines.get(lines.size() - 1);
	}

	/** Every Wellbeing line except the title, the pace and the guards line equal, and the pace no slower. */
	static void sameNeeds(GameTestHelper helper, List<String> before, List<String> now, String when) {
		for (String start : new String[] {"Fed today", "Sleeping in a bed"}) {
			String was = line(helper, before, start);
			String is = line(helper, now, start);
			helper.assertTrue(was.equals(is), when + " the hall reads '" + is + "' (before: '" + was + "')");
		}
	}

	// ---- the tests ------------------------------------------------------------------------------------------------

	/**
	 * Relief riders (33.17) from an allied village arrive at a village of two, each in their own bed: the Wellbeing button
	 * still reads 'Sleeping in a bed: 2 of 2' and 'Fed today: 2 of 2', the lit beds stay 'of 2', the three riders stand
	 * guard ('Guards: 3'), and the wellbeing is no lower.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHhc1107Relief"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHhc1107Relief")
	public void reliefRidersLeaveTheVillagesCountsAsTheyWere(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Relief.EDGE = 4; // they muster 4 blocks from the hall, inside the village
		ownHoused(helper, 2);
		ServerPlayer player = player(helper, hall);
		helper.runAfterDelay(3, () -> {
			List<String> before = wellbeing(helper, player, hall);
			helper.assertTrue(line(helper, before, "Sleeping in a bed").equals(text("screen.aliveworkplace.hall.housed", 2, 2)), "setup: " + before);
			helper.assertTrue(line(helper, before, "Guards").startsWith(text("screen.aliveworkplace.hall.safety", 0, "#", 2).split("#")[0]), "setup: " + before);
			Relief.arrive(level, new Relief.Band(GlobalPos.of(level.dimension(), hall), GlobalPos.of(level.dimension(), hall.offset(200, 0, 0)),
				Component.literal("Oakford"), 3, level.getGameTime(), level.getGameTime(), false));
			List<Villager> riders = Relief.riders(level, hall);
			helper.assertTrue(riders.size() == 3, "setup: riders " + riders.size());
			for (Villager rider : riders) {
				helper.assertTrue(VillageHalls.contains(hall, rider.blockPosition()), "setup: a rider outside the village at " + rider.blockPosition());
			}
			List<String> now = wellbeing(helper, player, hall);
			sameNeeds(helper, before, now, "with 3 relief riders in the village");
			String safety = line(helper, now, "Guards");
			String lit = line(helper, before, "Guards").replaceFirst("^[^·]*", "");
			helper.assertTrue(safety.equals(line(helper, before, "Guards").replaceFirst("\\d+", "3")),
				"with 3 relief riders the safety line reads '" + safety + "' (before '" + line(helper, before, "Guards") + "'; expected 3 guards and the lit beds '" + lit + "' unchanged)");
			VillageNeeds.Needs needs = VillageNeeds.count(level, hall);
			helper.assertTrue(needs.villagers() == 2 && needs.housed() == 2 && needs.adults() == 2, "with the riders the village counts " + needs);
			helper.succeed();
		});
	}

	/**
	 * The growth cap from both sides (Hamlet, cap set to the village's own size): with the cap at 3, a village of two plus
	 * a band of three isn't full; with the cap at 2 it is full, band or no band ('The village is full (2 villagers)').
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHhc1107Cap"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHhc1107Cap")
	public void aHiredBandDoesntFillTheVillagesGrowthCap(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		ownHoused(helper, 2);
		bed(helper, new BlockPos(9, 2, 9)); // a free bed: room to grow
		ServerPlayer player = player(helper, hall);
		helper.runAfterDelay(3, () -> {
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setRank(VillageRanks.Rank.HAMLET);
			String full2 = text("screen.aliveworkplace.hall.growth.full", 0, 0, 2);
			VillageGrowth.CAP = 2;
			String atCap = growthLine(helper, player, hall);
			helper.assertTrue(atCap.equals(full2), "setup: a village of two with the cap at 2 reads '" + atCap + "', not '" + full2 + "'");
			VillageGrowth.CAP = 3;
			String below = growthLine(helper, player, hall);
			helper.assertTrue(!below.startsWith("The village is full"), "setup: a village of two with the cap at 3 reads '" + below + "'");
			hire(helper, player, hall, true);
			entity.setRank(VillageRanks.Rank.HAMLET);
			String withBand = growthLine(helper, player, hall);
			helper.assertTrue(withBand.equals(below), "with a band of 3 hired, a village of two (cap 3) reads '" + withBand + "' (before: '" + below + "')");
			VillageGrowth.Blocker blocker = VillageGrowth.blocker(level, hall, VillageNeeds.count(level, hall), entity.lastBirth());
			helper.assertTrue(blocker != VillageGrowth.Blocker.FULL, "with a band hired the village counts as full (cap 3, two of its own)");
			VillageGrowth.CAP = 2;
			String fullWithBand = growthLine(helper, player, hall);
			helper.assertTrue(fullWithBand.equals(full2), "a band hired at the cap of 2: '" + fullWithBand + "', not '" + full2 + "'");
			helper.succeed();
		});
	}

	/**
	 * Fed and lit: one of the two villagers hungry and one bed lit by glowstone, so the button reads 'Fed today: 1 of 2'
	 * and 'lit beds: 1 of 2'. A band hired in lamplight by the hall, fed or not, changes neither line; the band's three
	 * Guards show as the village's guards and the wellbeing doesn't drop.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHhc1107FedLit"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHhc1107FedLit")
	public void aHiredBandLeavesTheFedAndLitCountsAsTheyWere(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		// Two beds far from the hall, 8 apart: only the first lit (glowstone by its head); the band stands in lamplight.
		List<Villager> own = new ArrayList<>();
		for (int x : new int[] {1, 9}) {
			BlockPos bed = bed(helper, new BlockPos(x, 2, 14));
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, 17));
			v.setNoAi(true);
			helper.assertTrue(CampBeds.claim(level, v, bed), "setup: a villager couldn't take a bed");
			own.add(v);
		}
		helper.setBlock(new BlockPos(0, 2, 15), Blocks.GLOWSTONE); // by the first bed's head only
		helper.setBlock(new BlockPos(5, 2, 8), Blocks.GLOWSTONE); // where the band stands, by the hall
		ModAttachments.LAST_MEAL.set(own.get(1), level.getGameTime() - VillageNeeds.DAY - 100);
		ServerPlayer player = player(helper, hall);
		BlockPos litBed = helper.absolutePos(new BlockPos(1, 2, 15));
		BlockPos standing = helper.absolutePos(new BlockPos(5, 2, 7));
		// The light engine catches up a few ticks after the glowstone is placed: wait for it (checked each tick), then check once.
		int[] ticks = {0};
		helper.onEachTick(() -> {
			if (ticks[0] < 0) {
				return; // checked already
			}
			boolean lit = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, litBed) >= 8
				&& level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, standing) >= 8;
			if (lit && ticks[0] >= 3) {
				ticks[0] = -1;
				fedAndLit(helper, level, hall, player);
				helper.succeed();
			} else if (++ticks[0] > 100) {
				ticks[0] = -1;
				helper.fail("setup: the glowstone's light never reached the bed and the hall");
			}
		});
	}

	private static void fedAndLit(GameTestHelper helper, ServerLevel level, BlockPos hall, ServerPlayer player) {
		{
			List<String> before = wellbeing(helper, player, hall);
			helper.assertTrue(line(helper, before, "Fed today").equals(text("screen.aliveworkplace.hall.fed", 1, 2)), "setup: " + before);
			helper.assertTrue(line(helper, before, "Guards").equals(text("screen.aliveworkplace.hall.safety", 0, 1, 2)), "setup: " + before);
			List<Villager> band = hire(helper, player, hall, true);
			// One of the band hungry, the others fed: counted, they'd change 'Fed today' either way.
			ModAttachments.LAST_MEAL.set(band.get(0), level.getGameTime() - VillageNeeds.DAY - 100);
			List<String> now = wellbeing(helper, player, hall);
			sameNeeds(helper, before, now, "with a band of 3 hired");
			String safety = line(helper, now, "Guards");
			helper.assertTrue(safety.equals(text("screen.aliveworkplace.hall.safety", 3, 1, 2)),
				"with a band of 3 hired the safety line reads '" + safety + "', not '" + text("screen.aliveworkplace.hall.safety", 3, 1, 2) + "'");
			int was = Integer.parseInt(before.get(0).replaceAll("\\D", ""));
			int is = Integer.parseInt(now.get(0).replaceAll("\\D", ""));
			helper.assertTrue(is >= was, "hiring a band of guards lowered '" + before.get(0) + "' to '" + now.get(0) + "'");
		}
	}

	/**
	 * One of the band takes the village's free bed (it's his home now): he still isn't the village's, so the hall reads
	 * 'Sleeping in a bed: 2 of 2' (not 3 of 3) and the village counts two villagers.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHhc1107BandBed"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHhc1107BandBed")
	public void aMercenaryInABedIsStillNotTheVillagesOwn(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		ownHoused(helper, 2);
		BlockPos spare = bed(helper, new BlockPos(9, 2, 9));
		ServerPlayer player = player(helper, hall);
		helper.runAfterDelay(3, () -> {
			List<String> before = wellbeing(helper, player, hall);
			List<Villager> band = hire(helper, player, hall, true);
			Villager sleeper = band.get(0);
			helper.assertTrue(CampBeds.claim(level, sleeper, spare), "setup: the mercenary couldn't take the free bed");
			helper.assertTrue(VillageNeeds.bed(level, sleeper) != null, "setup: the mercenary has no bed");
			List<String> now = wellbeing(helper, player, hall);
			String housed = line(helper, now, "Sleeping in a bed");
			helper.assertTrue(housed.equals(text("screen.aliveworkplace.hall.housed", 2, 2)),
				"a mercenary in the village's spare bed: the hall reads '" + housed + "', not 'Sleeping in a bed: 2 of 2'");
			sameNeeds(helper, before, now, "with a mercenary in a bed");
			VillageNeeds.Needs needs = VillageNeeds.count(level, hall);
			helper.assertTrue(needs.villagers() == 2 && needs.housed() == 2, "with a mercenary in a bed the village counts " + needs);
			helper.succeed();
		});
	}

	/**
	 * The other side: the village's own are never taken for visitors. Its own Guard (at a post, named 'Mercenary' and
	 * dressed in iron like the band) and a villager carrying the story tag with no job count as villagers, housed and
	 * (the Guard) a guard, with and without a band hired beside them: 'Sleeping in a bed: 4 of 4' and 'Guards: 1', then
	 * 'Guards: 4' with the band.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHhc1107OwnGuard"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHhc1107OwnGuard")
	public void theVillagesOwnGuardIsNeverTakenForHiredHelp(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		List<Villager> own = ownHoused(helper, 4);
		BlockPos post = new BlockPos(12, 2, 3);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager guard = own.get(2);
		io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, guard, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		guard.setCustomName(Component.translatable("entity.aliveworkplace.mercenary"));
		guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		own.get(3).addTag(io.github.jcondedata.aliveworkplace.story.Arcs.MOB_TAG); // jobless: the village's own
		ServerPlayer player = player(helper, hall);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(guard.getVillagerData().getProfession() == ModVillagers.GUARD, "setup: the guard has no job");
			List<String> before = wellbeing(helper, player, hall);
			helper.assertTrue(line(helper, before, "Sleeping in a bed").equals(text("screen.aliveworkplace.hall.housed", 4, 4)),
				"the village's own four (a Guard, a story-tagged villager) read '" + line(helper, before, "Sleeping in a bed") + "'");
			helper.assertTrue(line(helper, before, "Guards").startsWith(text("screen.aliveworkplace.hall.safety", 1, "#", 4).split("#")[0]),
				"the village's own Guard: '" + line(helper, before, "Guards") + "'");
			VillageNeeds.Needs mine = VillageNeeds.count(level, hall);
			helper.assertTrue(mine.villagers() == 4 && mine.adults() == 4 && mine.housed() == 4 && mine.guards() == 1, "own village: " + mine);
			hire(helper, player, hall, true);
			List<String> now = wellbeing(helper, player, hall);
			sameNeeds(helper, before, now, "with the band beside the village's own Guard");
			helper.assertTrue(line(helper, now, "Guards").startsWith(text("screen.aliveworkplace.hall.safety", 4, "#", 4).split("#")[0]),
				"the village's Guard and a band of 3: '" + line(helper, now, "Guards") + "'");
			VillageNeeds.Needs both = VillageNeeds.count(level, hall);
			helper.assertTrue(both.villagers() == 4 && both.housed() == 4 && both.guards() == 4, "with the band: " + both);
			helper.succeed();
		});
	}

	/**
	 * A story's visitor (a Farmer brought by a story, not the village's own) stands in the village, hungry and without a
	 * bed: the hall still reads 'Sleeping in a bed: 2 of 2' and 'Fed today: 2 of 2', and he's no guard ('Guards: 0'):
	 * only a hired Guard stands guard.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHhc1107Story"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHhc1107Story")
	public void aStorysVisitorIsLeftOutOfTheCountsAndIsNoGuard(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		ownHoused(helper, 2);
		ServerPlayer player = player(helper, hall);
		helper.runAfterDelay(3, () -> {
			List<String> before = wellbeing(helper, player, hall);
			Villager visitor = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 4));
			visitor.setNoAi(true);
			visitor.setVillagerData(visitor.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER).setLevel(2));
			visitor.setVillagerXp(20);
			visitor.addTag(io.github.jcondedata.aliveworkplace.story.Arcs.MOB_TAG);
			ModAttachments.LAST_MEAL.set(visitor, level.getGameTime() - VillageNeeds.DAY - 100);
			List<String> now = wellbeing(helper, player, hall);
			sameNeeds(helper, before, now, "with a story's visitor in the village");
			helper.assertTrue(line(helper, now, "Guards").equals(line(helper, before, "Guards")),
				"a story's Farmer in the village: the safety line reads '" + line(helper, now, "Guards") + "' (before '" + line(helper, before, "Guards") + "')");
			VillageNeeds.Needs needs = VillageNeeds.count(level, hall);
			helper.assertTrue(needs.villagers() == 2 && needs.guards() == 0, "with a story's Farmer the village counts " + needs);
			helper.succeed();
		});
	}

	/**
	 * A band hired at noon: one of them is killed, then the rest's time runs out and they leave on their own (the
	 * villagers' real tick); at each step the Wellbeing button's fed and housed lines read as before the hire, and once
	 * they're gone the guards line too. A second band hired afterwards leaves them as they were as well.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaHhc1107Leave"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaHhc1107Leave")
	public void countsAreExactlyAsBeforeWhenTheBandDiesOrLeaves(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		ownHoused(helper, 2);
		ServerPlayer player = player(helper, hall);
		List<List<String>> before = new ArrayList<>();
		helper.runAfterDelay(3, () -> {
			before.add(wellbeing(helper, player, hall));
			List<Villager> band = hire(helper, player, hall, false);
			band.get(0).kill();
			List<String> now = wellbeing(helper, player, hall);
			sameNeeds(helper, before.get(0), now, "a band with one killed");
			helper.assertTrue(line(helper, now, "Guards").startsWith(text("screen.aliveworkplace.hall.safety", 2, "#", 2).split("#")[0]),
				"two of the band left: '" + line(helper, now, "Guards") + "'");
			for (Villager merc : Mercenaries.near(level, hall)) {
				ModAttachments.MERCENARY_UNTIL.set(merc, level.getGameTime() + 5);
			}
		});
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(Mercenaries.near(level, hall).isEmpty(), "the band didn't leave when its time was up: " + Mercenaries.near(level, hall).size());
			List<String> after = wellbeing(helper, player, hall);
			helper.assertTrue(after.equals(before.get(0)), "after the band left the Wellbeing button reads " + after + " (before the hire: " + before.get(0) + ")");
			hire(helper, player, hall, true);
			List<String> again = wellbeing(helper, player, hall);
			sameNeeds(helper, before.get(0), again, "a second band hired");
			helper.succeed();
		});
	}
}
