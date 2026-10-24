package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardWork;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.BitSet;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1024-0353, 2026-10-24): the Village Hall, the Steward, homes and beds, from the player guide
 * (docs/guide/village-hall.md, steward.md, stewards-desk.md, city-plan.md, command-steward-explain.md) and ROADMAP 27.5,
 * 27.6, 27.8 and 27.10, not from the code. Clicks go the way a client sends them ({@link QaStageFourGameTests#press} on
 * blocks, {@link QaStageSixGameTests#clickVillager} on villagers, a real connection for what the player reads).
 */
public class QaHallHomesGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	/** A raw translation key in a line a player reads. */
	static final Pattern RAW_KEY = Pattern.compile("\\b[a-z_]+\\.aliveworkplace\\.[a-z_.0-9]+");

	/** A hall of its own: halls other batches left in reach removed, the village's reach 16 while the test runs. */
	static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos rel) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		VillageHalls.forgetNearest();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
			VillageNeeds.forget();
		});
		helper.setBlock(rel, ModBlocks.VILLAGE_HALL);
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
	}

	/** A bed whose head (its point of interest) is at {@code head}, its foot to the south. */
	static void bed(GameTestHelper helper, BlockPos head) {
		helper.setBlock(head.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(head, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
	}

	static Villager adult(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		return v;
	}

	static int bedsShort(GameTestHelper helper, BlockPos hallRel) {
		return VillageAdvice.bedsShort(VillageHalls.census(helper.getLevel(), helper.absolutePos(hallRel)));
	}

	static String joined(List<Component> seen) {
		StringBuilder out = new StringBuilder();
		for (Component c : seen) {
			out.append(c.getString()).append('\n');
		}
		return out.toString();
	}

	/** Every line read is rendered: no raw key, no {@code %s} left over. */
	static void allRendered(GameTestHelper helper, List<Component> seen) {
		for (Component c : seen) {
			String text = c.getString();
			helper.assertFalse(text.contains("%s") || text.contains("%1$s") || RAW_KEY.matcher(text).find(), "not rendered: " + text);
		}
	}

	// --- Right-clicks on the hall -----------------------------------------------------------------------------------

	/**
	 * village-hall.md and city-plan.md, from the main hand in survival: a Name Tag names the village (the tag isn't used up),
	 * a City Plan and a Village Ledger bind to it and say so with its new name, and empty hands open the hall's screen.
	 * The name survives the hall being saved and loaded, and the plan bound after the renaming carries it.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaHallMainHand"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaHallMainHand")
	public void theHallTakesANameTagAPlanAndALedgerFromTheMainHand(GameTestHelper helper) {
		BlockPos rel = new BlockPos(3, 2, 3);
		VillageHallBlockEntity entity = hall(helper, rel);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(rel);
		helper.runAfterDelay(2, () -> {
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qahm"));
			ServerPlayer p = c.player();
			ItemStack tag = new ItemStack(Items.NAME_TAG);
			tag.set(DataComponents.CUSTOM_NAME, Component.literal("Oakmere"));
			p.setItemInHand(InteractionHand.MAIN_HAND, tag);
			QaStageFourGameTests.press(helper, p, rel);
			helper.assertTrue("Oakmere".equals(VillageHalls.name(level, abs).getString()), "not renamed: " + VillageHalls.name(level, abs).getString()
				+ ", saw " + QaStageFourGameTests.keys(c.seen()));
			Component named = QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.hall.named");
			helper.assertTrue(named != null, "no line for the name, saw " + QaStageFourGameTests.keys(c.seen()));
			QaStageFourGameTests.assertRendered(helper, named, "Oakmere");
			helper.assertTrue(p.getMainHandItem().is(Items.NAME_TAG) && p.getMainHandItem().getCount() == 1, "the tag was used up: " + p.getMainHandItem());
			helper.assertTrue(p.containerMenu == p.inventoryMenu, "the name tag opened the hall's screen too");

			c.seen().clear();
			ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
			p.setItemInHand(InteractionHand.MAIN_HAND, plan);
			QaStageFourGameTests.press(helper, p, rel);
			var bound = p.getMainHandItem().get(ModComponents.CITY_PLAN_HALL);
			helper.assertTrue(bound != null && bound.hall().pos().equals(abs), "the plan isn't bound to the hall: " + bound + ", saw " + QaStageFourGameTests.keys(c.seen()));
			helper.assertTrue("Oakmere".equals(bound.name().getString()), "the plan names " + bound.name().getString());
			Component planLine = QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.city_plan.bound");
			helper.assertTrue(planLine != null, "no line for the plan, saw " + QaStageFourGameTests.keys(c.seen()));
			QaStageFourGameTests.assertRendered(helper, planLine, "Oakmere");

			c.seen().clear();
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.VILLAGE_LEDGER));
			QaStageFourGameTests.press(helper, p, rel);
			var ledger = p.getMainHandItem().get(ModComponents.LEDGER);
			helper.assertTrue(ledger != null && ledger.hall().pos().equals(abs), "the ledger isn't bound: " + ledger + ", saw " + QaStageFourGameTests.keys(c.seen()));
			Component ledgerLine = QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.ledger.bound");
			helper.assertTrue(ledgerLine != null, "no line for the ledger, saw " + QaStageFourGameTests.keys(c.seen()));
			QaStageFourGameTests.assertRendered(helper, ledgerLine, "Oakmere");

			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			QaStageFourGameTests.press(helper, p, rel);
			helper.assertTrue(p.containerMenu != p.inventoryMenu, "empty hands didn't open the hall's screen");
			p.closeContainer();

			// saved and loaded (a restart, or the chunk unloaded): still Oakmere
			CompoundTag saved = entity.saveWithoutMetadata(level.registryAccess());
			entity.loadWithComponents(saved, level.registryAccess());
			helper.assertTrue("Oakmere".equals(VillageHalls.name(level, abs).getString()), "the name was lost on reload: " + VillageHalls.name(level, abs).getString());
			helper.succeed();
		});
	}

	// --- Beds: exactly enough, and one short ---------------------------------------------------------------------

	/**
	 * village-hall.md "a bed for everyone", the "beds" tip and the Steward's {@code beds_short}: three grown-ups and
	 * three beds are short of none (no tip); a fourth villager wanting a bed makes one short, the tip saying 1; a bed
	 * broken with three villagers is one short too, and put back none.
	 */
	//$ gametest_ticks_batch HUGE '80' '"qaBedsBoundary"'
	@GameTest(template = HUGE, timeoutTicks = 80, batch = "qaBedsBoundary")
	public void exactlyEnoughBedsAndOneShortFromBothSides(GameTestHelper helper) {
		BlockPos hallRel = new BlockPos(15, 2, 15);
		hall(helper, hallRel);
		BlockPos[] heads = {new BlockPos(10, 2, 10), new BlockPos(13, 2, 10), new BlockPos(16, 2, 10)};
		for (BlockPos head : heads) {
			bed(helper, head);
		}
		for (int i = 0; i < 3; i++) {
			adult(helper, new BlockPos(10 + 3 * i, 2, 20));
		}
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(hallRel));
			helper.assertTrue(census.villagers() == 3 && census.beds() == 3, "setup: " + census.villagers() + " villagers, " + census.beds() + " beds");
			helper.assertTrue(bedsShort(helper, hallRel) == 0, "3 beds for 3 are short of " + bedsShort(helper, hallRel));
			helper.assertTrue(VillageAdvice.tips(level, helper.absolutePos(hallRel)).stream().noneMatch(t -> t.key().equals("beds")), "a beds tip with a bed each");

			Villager fourth = adult(helper, new BlockPos(20, 2, 20));
			helper.assertTrue(bedsShort(helper, hallRel) == 1, "4 villagers, 3 beds: short of " + bedsShort(helper, hallRel));
			VillageAdvice.Tip tip = VillageAdvice.tips(level, helper.absolutePos(hallRel)).stream().filter(t -> t.key().equals("beds")).findFirst().orElse(null);
			helper.assertTrue(tip != null && String.valueOf(tip.args()[0]).equals("1"), "no beds tip saying 1: " + (tip == null ? "none" : tip.title().getString()));
			fourth.discard();
			helper.destroyBlock(heads[1]);
		});
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(bedsShort(helper, hallRel) == 1, "a bed broken: short of " + bedsShort(helper, hallRel));
			bed(helper, heads[1]);
		});
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(bedsShort(helper, hallRel) == 0, "the bed put back: short of " + bedsShort(helper, hallRel));
			helper.succeed();
		});
	}

	/**
	 * "Everyone within 64 blocks belongs to it" (16 here): along a straight line, a villager and his bed exactly at the
	 * reach both count (none short); one block beyond, neither does (still none short); the villager stepping back in
	 * while his bed stays beyond is one short.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaBedsReach"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaBedsReach")
	public void aBedAtTheReachCountsAndOneBeyondDoesNot(GameTestHelper helper) {
		BlockPos hallRel = new BlockPos(2, 2, 15);
		hall(helper, hallRel);
		bed(helper, hallRel.offset(16, 0, 0)); // its head exactly at the reach, straight east
		bed(helper, hallRel.offset(17, 0, 4)); // one beyond
		adult(helper, hallRel.offset(16, 0, -2));
		Villager beyond = adult(helper, hallRel.offset(17, 0, 8));
		helper.runAfterDelay(5, () -> {
			VillageHalls.Census census = VillageHalls.census(helper.getLevel(), helper.absolutePos(hallRel));
			helper.assertTrue(census.villagers() == 1, "villagers at and beyond the reach: " + census.villagers() + " counted, wanted 1");
			helper.assertTrue(census.beds() == 1, "beds at and beyond the reach: " + census.beds() + " counted, wanted 1");
			helper.assertTrue(bedsShort(helper, hallRel) == 0, "short of " + bedsShort(helper, hallRel));
			beyond.moveTo(helper.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(hallRel.offset(15, 0, 8))));
			helper.assertTrue(bedsShort(helper, hallRel) == 1, "he stepped in, his bed beyond: short of " + bedsShort(helper, hallRel));
			helper.succeed();
		});
	}

	/**
	 * Two villagers wanting one bed: only one claims it, its place is taken, the village is a bed short and one is housed;
	 * the bed broken, both are short.
	 */
	//$ gametest_ticks_batch HUGE '1400' '"qaTwoForOneBed"'
	@GameTest(template = HUGE, timeoutTicks = 1400, batch = "qaTwoForOneBed")
	public void twoVillagersForOneBed(GameTestHelper helper) {
		BlockPos hallRel = new BlockPos(15, 2, 15);
		hall(helper, hallRel);
		BlockPos head = new BlockPos(10, 2, 10);
		bed(helper, head);
		Villager a = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 13));
		Villager b = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 13));
		ServerLevel level = helper.getLevel();
		BlockPos bedAbs = helper.absolutePos(head);
		long[] brokenAt = {-1};
		helper.onEachTick(() -> {
			if (brokenAt[0] >= 0) {
				if (helper.getTick() >= brokenAt[0] + 40) {
					helper.assertTrue(bedsShort(helper, hallRel) == 2, "the bed broken: short of " + bedsShort(helper, hallRel));
					helper.assertTrue(level.getPoiManager().getType(bedAbs).isEmpty(), "the broken bed is still a home");
					helper.succeed(); // who still counts as housed: QaHallHomesBugTests.aBrokenBedHousesNobody
				}
				return;
			}
			boolean aHas = a.getBrain().getMemory(MemoryModuleType.HOME).filter(g -> g.pos().equals(bedAbs)).isPresent();
			boolean bHas = b.getBrain().getMemory(MemoryModuleType.HOME).filter(g -> g.pos().equals(bedAbs)).isPresent();
			helper.assertFalse(aHas && bHas, "both villagers claim the one bed");
			if (aHas || bHas) {
				helper.assertTrue(level.getPoiManager().getFreeTickets(bedAbs) == 0, "a claimed bed still has a free place");
				helper.assertTrue(bedsShort(helper, hallRel) == 1, "2 villagers, 1 bed: short of " + bedsShort(helper, hallRel));
				helper.assertTrue(VillageNeeds.count(level, helper.absolutePos(hallRel)).housed() == 1,
					"housed: " + VillageNeeds.count(level, helper.absolutePos(hallRel)).housed());
				brokenAt[0] = helper.getTick();
				helper.destroyBlock(head);
			}
		});
	}

	// --- What the player reads about beds ---------------------------------------------------------------------------

	/**
	 * command-steward-explain.md ("Who can use it: everyone"): a survival player by the hall of a village two beds short
	 * gets the header and every rule, all rendered, with the beds rule's reason "2 villagers have no bed".
	 */
	//$ gametest_ticks_batch AREA '60' '"qaExplainBeds"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaExplainBeds")
	public void explainSaysWhyTwoVillagersWantBeds(GameTestHelper helper) {
		BlockPos hallRel = new BlockPos(3, 2, 3);
		hall(helper, hallRel);
		adult(helper, new BlockPos(1, 2, 5));
		adult(helper, new BlockPos(5, 2, 5));
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(bedsShort(helper, hallRel) == 2, "setup: short of " + bedsShort(helper, hallRel));
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaex"));
			ServerPlayer p = c.player();
			p.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(hallRel.east(2))));
			c.seen().clear();
			p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(), "workplace steward explain");
			String all = joined(c.seen());
			helper.assertTrue(c.seen().size() > 5, "explain said too little: " + all);
			allRendered(helper, c.seen());
			helper.assertTrue(all.contains("2 villagers have no bed"), "no reason for the beds rule: " + all.substring(0, Math.min(all.length(), 600)));
			helper.succeed();
		});
	}

	// --- The Steward ---------------------------------------------------------------------------------------------

	/**
	 * steward.md through the real clicks: with Stewards off, a sneak-click with the plan says so (rendered), keeps him a
	 * Builder and the plan, and opens no trade screen; switched on, an Apprentice builder is turned down naming him and
	 * the level he needs; a Journeyman is appointed and told so with his name and the village's.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaStewardClicks"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaStewardClicks")
	public void stewardAppointmentsThroughTheRealClick(GameTestHelper helper) {
		BlockPos hallRel = new BlockPos(3, 2, 3);
		VillageHallBlockEntity entity = hall(helper, hallRel);
		entity.setCustomName(Component.literal("Ashwick"));
		Villager young = adult(helper, hallRel.east(2));
		young.setCustomName(Component.literal("Pip"));
		young.setVillagerData(young.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(2));
		Villager bram = adult(helper, hallRel.south(2));
		bram.setCustomName(Component.literal("Bram"));
		StewardGameTests.seasoned(bram);
		helper.runAfterDelay(5, () -> {
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qast"));
			ServerPlayer p = c.player();
			ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
			CityPlanItem.bind(helper.getLevel(), p, plan, helper.absolutePos(hallRel));
			p.setShiftKeyDown(true);
			p.setItemInHand(InteractionHand.MAIN_HAND, plan);

			boolean was = Stewards.ENABLED;
			Stewards.ENABLED = false;
			try {
				c.seen().clear();
				QaStageSixGameTests.clickVillager(p, bram, c.seen());
				helper.assertTrue(bram.getVillagerData().getProfession() == ModVillagers.BUILDER, "off: appointed " + bram.getVillagerData().getProfession());
				Component off = QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.steward.off");
				helper.assertTrue(off != null, "off: not told, saw " + QaStageFourGameTests.keys(c.seen()));
				QaStageFourGameTests.assertRendered(helper, off);
				helper.assertTrue(p.getMainHandItem().is(ModItems.CITY_PLAN), "off: the plan went");
				helper.assertTrue(p.containerMenu == p.inventoryMenu, "off: a trade screen opened");
			} finally {
				Stewards.ENABLED = was;
			}

			c.seen().clear();
			QaStageSixGameTests.clickVillager(p, young, c.seen());
			helper.assertTrue(young.getVillagerData().getProfession() == ModVillagers.BUILDER, "an Apprentice was appointed");
			Component unseasoned = QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.steward.unseasoned");
			helper.assertTrue(unseasoned != null, "the Apprentice's refusal not said, saw " + QaStageFourGameTests.keys(c.seen()));
			QaStageFourGameTests.assertRendered(helper, unseasoned, "Pip");
			helper.assertTrue(p.containerMenu == p.inventoryMenu, "a trade screen opened on the refusal");

			c.seen().clear();
			QaStageSixGameTests.clickVillager(p, bram, c.seen());
			helper.assertTrue(bram.getVillagerData().getProfession() == ModVillagers.STEWARD, "Bram isn't the Steward, saw " + QaStageFourGameTests.keys(c.seen()));
			Component appointed = QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.steward.appointed");
			helper.assertTrue(appointed != null, "not told, saw " + QaStageFourGameTests.keys(c.seen()));
			QaStageFourGameTests.assertRendered(helper, appointed, "Bram", "Ashwick");
			helper.assertTrue(p.containerMenu == p.inventoryMenu, "a trade screen opened on the appointment");
			allRendered(helper, c.seen());
			helper.succeed();
		});
	}

	/**
	 * steward.md "Breaking the hall ends the job", mid-round: appointed by a real sneak-click, he sets out on his morning
	 * rounds holding the plan; the hall broken then, he is jobless, no longer holds the plan and forgets the hall.
	 */
	//$ gametest_ticks_batch HUGE '1400' '"qaStewardMidRound"'
	@GameTest(template = HUGE, timeoutTicks = 1400, batch = "qaStewardMidRound")
	public void theHallBrokenMidRoundEndsTheStewardsJob(GameTestHelper helper) {
		BlockPos hallRel = new BlockPos(15, 2, 15);
		Leftovers.stewardSites(helper);
		VillageHallBlockEntity entity = hall(helper, hallRel);
		ServerLevel level = helper.getLevel();
		level.setDayTime((level.getDayTime() / 24000L + 1) * 24000L + 2500L); // a working morning
		BlockPos hallAbs = helper.absolutePos(hallRel);
		BitSet cells = new BitSet();
		cells.set(CityPlan.cellAt(hallAbs, hallAbs.offset(12, 0, -12)));
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Far homes", "").paint(0, cells));
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, hallRel.south(2)));
		boolean[] broken = {false};
		boolean[] appointed = {false};
		helper.runAfterDelay(3, () -> {
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qamr"));
			ServerPlayer p = c.player();
			ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
			CityPlanItem.bind(level, p, plan, hallAbs);
			p.setShiftKeyDown(true);
			p.setItemInHand(InteractionHand.MAIN_HAND, plan);
			QaStageSixGameTests.clickVillager(p, steward, c.seen());
			helper.assertTrue(steward.getVillagerData().getProfession() == ModVillagers.STEWARD, "not appointed, saw " + QaStageFourGameTests.keys(c.seen()));
			p.setShiftKeyDown(false);
			appointed[0] = true;
		});
		helper.onEachTick(() -> {
			if (appointed[0] && !broken[0] && StewardWork.holdsPlan(steward) && steward.distanceToSqr(hallAbs.getCenter()) > 9) {
				broken[0] = true;
				level.destroyBlock(hallAbs, true);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0], "he never set out on his rounds with the plan; at " + steward.blockPosition().subtract(hallAbs));
			helper.assertTrue(steward.isAlive(), "the steward died");
			helper.assertTrue(steward.getVillagerData().getProfession() == VillagerProfession.NONE, "still " + steward.getVillagerData().getProfession());
			helper.assertTrue(!StewardWork.holdsPlan(steward), "still holds the plan with no hall");
			helper.assertTrue(steward.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(), "still remembers the hall");
		});
	}

	/**
	 * stewards-desk.md and city-plan.md: the owner sets Run the village; a stranger can't set Rest; the mode survives the
	 * hall being saved and loaded; an old hall saved with no plan asks first; and the hall mined and put down again keeps
	 * the plan and its mode on the item ("break the hall and the plan is kept on the hall item").
	 */
	//$ gametest_ticks_batch AREA '80' '"qaDeskMode"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaDeskMode")
	public void theDeskModeSurvivesAReloadAndTheHallItem(GameTestHelper helper) {
		BlockPos hallRel = new BlockPos(3, 2, 3);
		VillageHallBlockEntity entity = hall(helper, hallRel);
		ServerLevel level = helper.getLevel();
		BlockPos hallAbs = helper.absolutePos(hallRel);
		boolean selfRun = StewardDesk.SELF_RUN;
		StewardDesk.SELF_RUN = true;
		Leftovers.after(helper, () -> StewardDesk.SELF_RUN = selfRun);
		helper.runAfterDelay(3, () -> {
			QaStageFiveGameTests.Client owner = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qadm"));
			QaStageFiveGameTests.Client stranger = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qads"));
			entity.setOwner(owner.player().getUUID(), owner.player().getGameProfile().getName());
			helper.assertTrue(StewardDesk.mode(level, hallAbs) == CityPlan.Mode.ASK, "a new hall doesn't ask first: " + StewardDesk.mode(level, hallAbs));
			BitSet cells = new BitSet();
			cells.set(CityPlan.cellAt(hallAbs, hallAbs.offset(4, 0, 4)));
			entity.setPlan(entity.plan().addZone("homes", "Homes 1", "").paint(0, cells));
			helper.assertTrue(StewardDesk.setMode(level, hallAbs, owner.player(), CityPlan.Mode.RUN), "the owner can't set Run the village");
			helper.assertFalse(StewardDesk.setMode(level, hallAbs, stranger.player(), CityPlan.Mode.REST), "a stranger set Rest");
			helper.assertTrue(StewardDesk.mode(level, hallAbs) == CityPlan.Mode.RUN, "mode after the stranger: " + StewardDesk.mode(level, hallAbs));

			CompoundTag saved = entity.saveWithoutMetadata(level.registryAccess());
			entity.loadWithComponents(saved, level.registryAccess());
			helper.assertTrue(StewardDesk.mode(level, hallAbs) == CityPlan.Mode.RUN, "the mode was lost on reload: " + StewardDesk.mode(level, hallAbs));
			helper.assertTrue(entity.plan().zones().size() == 1, "the zone was lost on reload");

			CompoundTag old = saved.copy();
			old.remove("plan");
			entity.loadWithComponents(old, level.registryAccess());
			helper.assertTrue(StewardDesk.mode(level, hallAbs) == CityPlan.Mode.ASK, "an old hall with no plan saved: " + StewardDesk.mode(level, hallAbs));
			entity.loadWithComponents(saved, level.registryAccess());

			level.destroyBlock(hallAbs, true);
			List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(hallAbs).inflate(2), e -> e.getItem().is(ModBlocks.VILLAGE_HALL.asItem()));
			helper.assertTrue(drops.size() == 1, "the hall dropped " + drops.size() + " hall items");
			ItemStack hallItem = drops.get(0).getItem().copy();
			drops.get(0).discard();
			CityPlan carried = hallItem.get(ModComponents.CITY_PLAN);
			helper.assertTrue(carried != null && carried.mode() == CityPlan.Mode.RUN && carried.zones().size() == 1,
				"the hall item lost the plan or its mode: " + carried);
			ServerPlayer p = owner.player();
			p.setItemInHand(InteractionHand.MAIN_HAND, hallItem);
			QaStageFourGameTests.press(helper, p, hallRel.below());
			helper.assertTrue(level.getBlockState(hallAbs).is(ModBlocks.VILLAGE_HALL), "the hall wasn't put down again");
			helper.assertTrue(StewardDesk.mode(level, hallAbs) == CityPlan.Mode.RUN, "put down again: " + StewardDesk.mode(level, hallAbs));
			helper.succeed();
		});
	}
}
