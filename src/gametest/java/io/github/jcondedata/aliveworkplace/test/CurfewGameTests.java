package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Curfew;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

/**
 * Curfew and The Lamplighters (ROADMAP 30.9), the effect {@code curfew}: at dusk the grown villagers go to bed (a bard,
 * whose own hours keep him up till 12500, among them) while the guard stays on watch; a zombie's blow on a villager
 * asleep in bed does nothing, and hurts without the edict; raids and bandit camps half as likely, reformed too; a trade
 * refused at night ("Curfew: come back in the morning."), allowed by day and after the reform; a festival over at dusk
 * with no fireworks. Each test that moves the clock has its own batch and puts the time back.
 */
public class CurfewGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final ResourceLocation CURFEW = AliveWorkplace.id("curfew");
	private static final ResourceLocation OPEN_GATES = AliveWorkplace.id("open_gates");

	/** It loads with its text, icon, boost, cost, Open Gates excluded and a three-step reform that takes the cost away. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void curfewLoadsWithTheLamplighters(GameTestHelper helper) {
		Edicts.Edict curfew = Edicts.get(CURFEW).orElse(null);
		helper.assertTrue(curfew != null && curfew.name().getString().equals("Curfew") && curfew.icon().equals(ResourceLocation.withDefaultNamespace("bell")),
			"Curfew: " + curfew);
		helper.assertTrue(curfew.boost().size() == 1 && curfew.boost().get(0) instanceof CivicEffects.CurfewRules b && b.raids() == 0.5f && b.safeNights() && !b.stayIn(),
			"boost: " + curfew.boost());
		helper.assertTrue(curfew.cost().size() == 1 && curfew.cost().get(0) instanceof CivicEffects.CurfewRules c && c.raids() == 1f && !c.safeNights() && c.stayIn(),
			"cost: " + curfew.cost());
		helper.assertTrue(curfew.excludes().equals(List.of(OPEN_GATES)), "excludes: " + curfew.excludes());
		Reforms.Reform lamps = curfew.reform().orElse(null);
		helper.assertTrue(lamps != null && lamps.name().getString().equals("The Lamplighters") && lamps.effects().isEmpty() && lamps.steps().size() == 3,
			"reform: " + lamps);
		step(helper, lamps.steps().get(0), "minecraft:lantern", 24, 5);
		step(helper, lamps.steps().get(1), "minecraft:glowstone", 8, 4);
		Reforms.Step slay = lamps.steps().get(2);
		helper.assertTrue(slay.kind() == VillageQuests.Kind.SLAY && slay.count() == 8 && slay.reward() == 6, "step 3: " + slay);
		helper.assertTrue(Component.translatable("message.aliveworkplace.curfew.no_trade").getString().equals("Curfew: come back in the morning."),
			"the refusal reads " + Component.translatable("message.aliveworkplace.curfew.no_trade").getString());

		// Summed: factors multiply; either flag set anywhere counts; nothing: as usual.
		CivicEffects.Sum sum = new CivicEffects.Sum(List.of(
			new CivicEffects.Active(new CivicEffects.CurfewRules(0.5f, true, false, List.of()), Component.empty()),
			new CivicEffects.Active(new CivicEffects.CurfewRules(0.5f, false, true, List.of()), Component.empty())));
		helper.assertTrue(sum.raids() == 0.25f && sum.safeNights() && sum.stayIn(), "summed: " + sum.raids() + ", " + sum.safeNights() + ", " + sum.stayIn());
		helper.assertTrue(CivicEffects.Sum.EMPTY.raids() == 1f && !CivicEffects.Sum.EMPTY.safeNights() && !CivicEffects.Sum.EMPTY.stayIn(), "no edicts: as usual");
		helper.succeed();
	}

	private static void step(GameTestHelper helper, Reforms.Step step, String item, int count, int reward) {
		helper.assertTrue(step.kind() == VillageQuests.Kind.BRING && step.item().equals(item) && step.count() == count && step.reward() == reward,
			"step: " + step + ", expected " + count + " " + item + " for " + reward);
	}

	/**
	 * At dusk a farmer and a bard by their beds: without the edict the bard keeps his own hours (he plays till 12500);
	 * under Curfew both are asleep in their beds while the guard is on watch.
	 */
	//$ gametest_ticks_batch AREA '600' '"curfewBedtime"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "curfewBedtime")
	public void villagersAreInBedByDusk(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos farmerBed = bed(helper, new BlockPos(3, 2, 4), true);
		BlockPos bardBed = bed(helper, new BlockPos(7, 2, 4), true);
		BlockPos guardBed = bed(helper, new BlockPos(15, 2, 4), true);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity entity = ready(helper);
			level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + 11000); // the evening: everyone's hours say idle
			Villager farmer = sleeper(helper, VillagerProfession.FARMER, new BlockPos(3, 2, 4), farmerBed);
			Villager bard = sleeper(helper, ModVillagers.BARD, new BlockPos(7, 2, 4), bardBed);
			Villager guard = sleeper(helper, ModVillagers.GUARD, new BlockPos(15, 2, 4), guardBed);
			helper.runAfterDelay(5, () -> dusk(level));
			helper.runAfterDelay(45, () -> {
				helper.assertTrue(!bard.isSleeping() && !bard.getBrain().isActive(Activity.REST), "without the edict the bard went to bed at "
					+ level.getDayTime() % VillageNeeds.DAY + ": " + bard.getBrain().getActiveNonCoreActivity());
				entity.setEdicts(List.of(new Edicts.InForce(CURFEW.toString(), Chronicle.day(level))));
				CivicEffects.forget();
				dusk(level);
				helper.succeedWhen(() -> {
					helper.assertTrue(Curfew.asleepInBed(farmer) && farmer.getSleepingPos().orElseThrow().equals(farmerBed), "the farmer isn't in bed: "
						+ farmer.getBrain().getActiveNonCoreActivity() + ", " + farmer.blockPosition() + " for a bed at " + farmerBed);
					helper.assertTrue(Curfew.asleepInBed(bard) && bard.getSleepingPos().orElseThrow().equals(bardBed), "the bard isn't in bed: "
						+ bard.getBrain().getActiveNonCoreActivity() + ", " + bard.blockPosition() + " for a bed at " + bardBed);
					helper.assertTrue(!guard.isSleeping() && !guard.getBrain().isActive(Activity.REST), "the guard left the watch: " + guard.getBrain().getActiveNonCoreActivity());
					helper.assertTrue(!Curfew.keepsIn(guard) && Curfew.keepsIn(farmer), "kept in: guard " + Curfew.keepsIn(guard) + ", farmer " + Curfew.keepsIn(farmer));
				});
			});
		});
	}

	/** A zombie strikes a villager asleep in bed at night: nothing under Curfew (and its reform), a wound without it. */
	//$ gametest_ticks_batch AREA '100' '"curfewSafeSleep"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "curfewSafeSleep")
	public void aZombieCannotHurtAVillagerAsleepInBed(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos bed = bed(helper, new BlockPos(5, 2, 5));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity entity = ready(helper);
			level.setDayTime(Chronicle.day(level) * VillageNeeds.DAY - 6000); // midnight
			Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
			villager.setNoAi(true);
			villager.startSleeping(bed);
			Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 8));
			zombie.setNoAi(true);
			CivicEffects.forget();

			entity.setEdicts(List.of(new Edicts.InForce(CURFEW.toString(), Chronicle.day(level))));
			float full = villager.getHealth();
			zombie.doHurtTarget(villager);
			helper.assertTrue(villager.getHealth() == full && villager.isSleeping(), "under Curfew the zombie hurt them: " + villager.getHealth() + " of " + full);
			entity.setReforms(List.of(new Reforms.Progress(CURFEW.toString(), 3, true, 0)));
			villager.invulnerableTime = 0;
			zombie.doHurtTarget(villager);
			helper.assertTrue(villager.getHealth() == full, "reformed, the zombie hurt them: " + villager.getHealth() + " of " + full);

			entity.setReforms(List.of());
			entity.setEdicts(List.of());
			villager.invulnerableTime = 0;
			zombie.doHurtTarget(villager);
			helper.assertTrue(villager.getHealth() < full, "without the edict the zombie did nothing: " + villager.getHealth() + " of " + full);
			villager.discard();
			zombie.discard();
			helper.succeed();
		});
	}

	/** Raids and bandit camps half as likely under Curfew, and still half reformed; as usual without it. */
	//$ gametest_ticks_batch AREA '100' '"curfewRaids"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "curfewRaids")
	public void curfewHalvesTheRaidChance(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			float raid = VillageRaids.chance(level, hall, 12);
			float camp = BanditCamps.dailyChance(level, hall);
			helper.assertTrue(Math.abs(raid - VillageRaids.nightlyChance(12)) < 1e-6 && Math.abs(camp - BanditCamps.DAILY_CHANCE) < 1e-6,
				"usual: " + raid + ", " + camp);
			entity.setEdicts(List.of(new Edicts.InForce(CURFEW.toString(), 1)));
			helper.assertTrue(Math.abs(VillageRaids.chance(level, hall, 12) - raid / 2) < 1e-6, "raids under Curfew: " + VillageRaids.chance(level, hall, 12));
			helper.assertTrue(Math.abs(BanditCamps.dailyChance(level, hall) - camp / 2) < 1e-6, "camps under Curfew: " + BanditCamps.dailyChance(level, hall));
			entity.setReforms(List.of(new Reforms.Progress(CURFEW.toString(), 3, true, 0)));
			helper.assertTrue(Math.abs(VillageRaids.chance(level, hall, 12) - raid / 2) < 1e-6, "raids reformed: " + VillageRaids.chance(level, hall, 12));
			helper.assertTrue(Math.abs(BanditCamps.dailyChance(level, hall) - camp / 2) < 1e-6, "camps reformed: " + BanditCamps.dailyChance(level, hall));
			// The nights count as safe in the village's wellbeing, reformed too; by day as usual.
			level.setDayTime(Chronicle.day(level) * VillageNeeds.DAY - 6000);
			helper.assertTrue(Curfew.safeNow(level, hall), "the night isn't safe");
			level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + 6000);
			helper.assertTrue(!Curfew.safeNow(level, hall), "the day counted as a safe night");
			// Market traders arriving at noon stay until dusk, not the usual 10000 ticks; reformed they stay as usual.
			helper.assertTrue(Curfew.marketStay(level, hall, MarketDays.STAY) == MarketDays.STAY, "reformed, the traders stay " + Curfew.marketStay(level, hall, MarketDays.STAY));
			entity.setReforms(List.of());
			helper.assertTrue(Curfew.marketStay(level, hall, MarketDays.STAY) == 6000, "under Curfew the traders stay " + Curfew.marketStay(level, hall, MarketDays.STAY));
			helper.succeed();
		});
	}

	/** A trade refused at night under Curfew (a shake of the head, no trade screen), allowed by day and after the reform. */
	//$ gametest_ticks_batch AREA '100' '"curfewTrades"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "curfewTrades")
	public void aTradeIsRefusedAtNightUnderCurfew(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity entity = ready(helper);
			Villager librarian = librarian(helper);
			ServerPlayer player = player(helper);
			entity.setEdicts(List.of(new Edicts.InForce(CURFEW.toString(), 1)));
			dusk(level);
			helper.assertTrue(!(interact(librarian, player) instanceof MerchantMenu) && librarian.getUnhappyCounter() > 0, "traded at night under Curfew: "
				+ player.containerMenu);
			// Morning: the shop is open.
			level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + 2000);
			helper.assertTrue(interact(librarian, player) instanceof MerchantMenu, "no trade by day: " + player.containerMenu);
			player.closeContainer();
			// Reformed by The Lamplighters: trading goes on at night.
			entity.setReforms(List.of(new Reforms.Progress(CURFEW.toString(), 3, true, 0)));
			dusk(level);
			helper.assertTrue(interact(librarian, player) instanceof MerchantMenu, "no trade at night after the reform: " + player.containerMenu);
			player.closeContainer();
			helper.succeed();
		});
	}

	/** A festival under Curfew is over at dusk with no fireworks (the 400 ticks after the fireworks' hour); reformed it goes on. */
	//$ gametest_ticks_batch AREA '600' '"curfewFestival"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "curfewFestival")
	public void aFestivalUnderCurfewEndsAtDuskWithoutFireworks(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		boolean festivals = Festivals.ENABLED;
		Festivals.ENABLED = true;
		Leftovers.after(helper, () -> {
			Festivals.ENABLED = festivals;
			Festivals.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			entity.setEdicts(List.of(new Edicts.InForce(CURFEW.toString(), 1)));
			level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + Festivals.FIREWORKS + 100);
			entity.setFestivalDay(Chronicle.day(level));
			entity.setFeastDay(Chronicle.day(level));
			Festivals.round(level, hall, entity, 8);
			helper.assertTrue(Festivals.isOn(level, hall) && !Curfew.fireworks(entity), "the festival isn't on");
			AABB sky = new AABB(hall).inflate(24, 64, 24);
			helper.runAfterDelay(400, () -> {
				List<FireworkRocketEntity> rockets = level.getEntitiesOfClass(FireworkRocketEntity.class, sky);
				helper.assertTrue(rockets.isEmpty(), rockets.size() + " fireworks went up under Curfew");
				level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + Curfew.DUSK + 10);
				helper.assertTrue(!Festivals.isOn(level, hall), "the festival went on past dusk under Curfew");
				// Reformed: fireworks, and the festival goes on to its usual end.
				entity.setReforms(List.of(new Reforms.Progress(CURFEW.toString(), 3, true, 0)));
				helper.assertTrue(Festivals.isOn(level, hall) && Curfew.fireworks(entity), "reformed, the festival is over at dusk");
				helper.succeed();
			});
		});
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	/** Just after dusk today. */
	private static void dusk(ServerLevel level) {
		level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + Curfew.DUSK + 10);
	}

	/** A red bed whose foot is at {@code foot} and head to the north; returns the head's absolute position. */
	private static BlockPos bed(GameTestHelper helper, BlockPos foot) {
		return bed(helper, foot, false);
	}

	/**
	 * The same, in a glass cell two wide (the bed and a spot east of its foot) when {@code walled}, so its sleeper can't
	 * wander off before bedtime.
	 */
	private static BlockPos bed(GameTestHelper helper, BlockPos foot, boolean walled) {
		if (walled) {
			for (int y = 2; y <= 3; y++) {
				for (int x = foot.getX() - 1; x <= foot.getX() + 2; x++) {
					for (int z = foot.getZ() - 2; z <= foot.getZ() + 1; z++) {
						if (x == foot.getX() - 1 || x == foot.getX() + 2 || z == foot.getZ() - 2 || z == foot.getZ() + 1) {
							helper.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
						}
					}
				}
			}
		}
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.north(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
		return helper.absolutePos(foot.north());
	}

	/** A grown villager of {@code job} (level 2, so it keeps it) standing by the foot ({@code foot}) of their own bed at {@code head}. */
	private static Villager sleeper(GameTestHelper helper, VillagerProfession job, BlockPos foot, BlockPos head) {
		Villager villager = helper.spawn(EntityType.VILLAGER, foot.east());
		villager.setVillagerData(villager.getVillagerData().setProfession(job).setLevel(2));
		villager.setVillagerXp(10);
		villager.refreshBrain(helper.getLevel()); // as a villager taking a job does: the job's hours (a bard's, a guard's)
		helper.getLevel().getPoiManager().take(t -> t.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME), (t, p) -> p.equals(head), head, 1);
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), head));
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
		CivicEffects.forget();
		return librarian;
	}

	/** {@code player} right-clicks {@code villager}: the screen they have open afterwards. */
	private static Object interact(Villager villager, ServerPlayer player) {
		player.moveTo(villager.getX() + 1, villager.getY(), villager.getZ(), 90f, 0f);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.interactOn(villager, InteractionHand.MAIN_HAND);
		return player.containerMenu;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos at = helper.absolutePos(new BlockPos(9, 2, 9));
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		return player;
	}

	/** A village of radius 16; the clock, every cache and the radius put back after the test. */
	private static void village(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		long time = helper.getLevel().getDayTime();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			helper.getLevel().setDayTime(time);
			VillageNeeds.forget();
			CivicEffects.forget();
			Moods.forget();
		});
	}

	/** The hall after its first round: a Village with nothing in force and no reform, every cache asked again. */
	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setRank(VillageRanks.Rank.VILLAGE);
		hall.setEdicts(List.of());
		hall.setReforms(List.of());
		hall.setFestivalDay(-1);
		VillageNeeds.forget();
		CivicEffects.forget();
		Moods.forget();
		return hall;
	}
}
