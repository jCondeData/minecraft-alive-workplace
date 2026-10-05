package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.guard.MilitiaCombat;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Conscription;
import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Conscription and The Militia Drill (ROADMAP 30.10), the effects {@code militia} and {@code work_stops_in_raids}: in a
 * raid a farmer and a builder take up the militia's sword and strike the raiders near them (not without a raid, and
 * the sword is gone when it ends, never from a chest); a child and an ill villager don't fight; a builder doesn't build
 * during the raid nor the morning after until noon; reformed, a builder far from the raiders keeps building and the
 * morning after is a normal day; a conscript saved mid-raid loads without the sword and with what they held; the
 * switch off: no militia, no stop. Each test that moves the clock has its own batch and puts the time back.
 */
public class ConscriptionGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final ResourceLocation CONSCRIPTION = AliveWorkplace.id("conscription");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	/** It loads with its texts, icon, boost, cost and a three-step reform whose effect replaces the cost; the Book says them. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void conscriptionLoadsWithTheMilitiaDrill(GameTestHelper helper) {
		Edicts.Edict edict = Edicts.get(CONSCRIPTION).orElse(null);
		helper.assertTrue(edict != null && edict.name().getString().equals("Conscription")
			&& edict.icon().equals(ResourceLocation.withDefaultNamespace("stone_sword")), "Conscription: " + edict);
		helper.assertTrue(edict.description().getString().startsWith("In a raid every grown villager who isn't ill takes up a stone sword"),
			"description: " + edict.description().getString());
		helper.assertTrue(edict.boost().size() == 1 && edict.boost().get(0) instanceof CivicEffects.Militia m && m.damage() == 3f && m.range() == 24,
			"boost: " + edict.boost());
		helper.assertTrue(edict.cost().size() == 1 && edict.cost().get(0) instanceof CivicEffects.WorkStops w && w.untilNoon() && w.near().isEmpty(),
			"cost: " + edict.cost());
		Reforms.Reform drill = edict.reform().orElse(null);
		helper.assertTrue(drill != null && drill.name().getString().equals("The Militia Drill") && drill.steps().size() == 3, "reform: " + drill);
		helper.assertTrue(drill.line().getString().startsWith("Drilled at the dummies, the militia fights without stopping the village"),
			"its line: " + drill.line().getString());
		helper.assertTrue(drill.effects().size() == 1 && drill.effects().get(0) instanceof CivicEffects.WorkStops r && !r.untilNoon()
			&& r.near().equals(Optional.of(24)), "reformed: " + drill.effects());
		step(helper, drill.steps().get(0), "minecraft:iron_sword", 32, 6);
		step(helper, drill.steps().get(1), "minecraft:shield", 32, 5);
		Reforms.Step slay = drill.steps().get(2);
		helper.assertTrue(slay.kind() == VillageQuests.Kind.SLAY && slay.count() == 48 && slay.reward() == 8, "step 3: " + slay);

		// The Book's words for each effect.
		said(helper, edict.boost().get(0), "in a raid every grown villager who isn't ill fights with a stone sword (3 damage a blow, more if Strong) against raiders within 24 blocks");
		said(helper, edict.cost().get(0), "all work stops during a raid and until noon the next day");
		said(helper, drill.effects().get(0), "in a raid, work stops only for villagers with a raider within 24 blocks");
		said(helper, new CivicEffects.WorkStops(false, Optional.empty(), List.of()), "all work stops during a raid");

		// Summed: the strictest stop (any without "near" stops everyone; any until noon counts), the strongest militia.
		CivicEffects.Sum sum = new CivicEffects.Sum(List.of(
			new CivicEffects.Active(new CivicEffects.WorkStops(false, Optional.of(24), List.of()), Component.empty()),
			new CivicEffects.Active(new CivicEffects.WorkStops(true, Optional.empty(), List.of()), Component.empty()),
			new CivicEffects.Active(new CivicEffects.Militia(3f, 24, List.of()), Component.empty()),
			new CivicEffects.Active(new CivicEffects.Militia(5f, 16, List.of()), Component.empty())));
		CivicEffects.WorkStops stops = sum.workStops();
		helper.assertTrue(stops != null && stops.untilNoon() && stops.near().isEmpty(), "summed stop: " + stops);
		CivicEffects.Militia militia = sum.militia();
		helper.assertTrue(militia != null && militia.damage() == 5f && militia.range() == 24, "summed militia: " + militia);
		CivicEffects.Sum near = new CivicEffects.Sum(List.of(
			new CivicEffects.Active(new CivicEffects.WorkStops(false, Optional.of(24), List.of()), Component.empty()),
			new CivicEffects.Active(new CivicEffects.WorkStops(false, Optional.of(12), List.of()), Component.empty())));
		helper.assertTrue(near.workStops().near().equals(Optional.of(24)), "the widest near: " + near.workStops());
		helper.assertTrue(CivicEffects.Sum.EMPTY.workStops() == null && CivicEffects.Sum.EMPTY.militia() == null, "no edicts: nothing");
		// The militia's sword is known by its mark, a plain stone sword isn't.
		helper.assertTrue(Conscription.isMilitiaSword(Conscription.sword()) && !Conscription.isMilitiaSword(new ItemStack(Items.STONE_SWORD)),
			"the militia's sword isn't told from a plain one");
		helper.succeed();
	}

	private static void step(GameTestHelper helper, Reforms.Step step, String item, int count, int reward) {
		helper.assertTrue(step.kind() == VillageQuests.Kind.BRING && step.item().equals(item) && step.count() == count && step.reward() == reward,
			"step: " + step + ", expected " + count + " " + item + " for " + reward);
	}

	private static void said(GameTestHelper helper, CivicEffects.Effect effect, String expected) {
		Component said = EdictBook.describe(effect);
		helper.assertTrue(said != null && said.getString().equals(expected), "the Book says \"" + (said == null ? null : said.getString()) + "\", not \"" + expected + "\"");
	}

	/**
	 * At night, a farmer and a builder each penned with a zombie: no raid, no swords and no blows; once the raid is on both
	 * hold the militia's sword and each strikes their zombie; when it's over the swords are gone, and the stone sword in
	 * the chest by the hall was never touched.
	 */
	//$ gametest_ticks_batch AREA '900' '"conscriptionFight"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "conscriptionFight")
	public void aFarmerAndABuilderFightTheRaidersOnlyInARaid(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(11, 2, 4), Blocks.CHEST);
		pen(helper, 1, 9);
		pen(helper, 15, 9);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity entity = ready(helper);
			Container chest = helper.getBlockEntity(new BlockPos(11, 2, 4));
			chest.setItem(0, new ItemStack(Items.STONE_SWORD));
			entity.setEdicts(List.of(new Edicts.InForce(CONSCRIPTION.toString(), Chronicle.day(level))));
			night(level);
			Villager farmer = grown(helper, VillagerProfession.FARMER, new BlockPos(2, 2, 11));
			Villager builder = grown(helper, ModVillagers.BUILDER, new BlockPos(16, 2, 11));
			Zombie near = raider(helper, new BlockPos(4, 2, 11));
			Zombie far = raider(helper, new BlockPos(18, 2, 11));
			float full = near.getMaxHealth();
			forget();
			helper.runAfterDelay(60, () -> {
				helper.assertTrue(near.getHealth() == full && far.getHealth() == full, "struck without a raid: " + near.getHealth() + ", " + far.getHealth());
				helper.assertTrue(!Conscription.isMilitiaSword(farmer.getMainHandItem()) && !Conscription.isMilitiaSword(builder.getMainHandItem()),
					"a sword without a raid: " + farmer.getMainHandItem() + ", " + builder.getMainHandItem() + "; ours "
						+ VillageRaids.active(helper.absolutePos(HALL)) + ", vanilla " + level.getRaidAt(helper.absolutePos(HALL)));
				helper.assertTrue(Conscription.militia(farmer) == null, "a conscript without a raid");
				VillageRaids.track(level, helper.absolutePos(HALL), 2);
				forget();
				AtomicBoolean farmerStruck = new AtomicBoolean();
				AtomicBoolean builderStruck = new AtomicBoolean();
				AtomicBoolean armed = new AtomicBoolean();
				AtomicBoolean over = new AtomicBoolean();
				// (succeedWhen's check runs every tick: it also notes the blows as they land)
				helper.succeedWhen(() -> {
					if (!over.get()) {
						farmerStruck.compareAndSet(false, near.getLastHurtByMob() == farmer);
						builderStruck.compareAndSet(false, far.getLastHurtByMob() == builder);
						armed.compareAndSet(false, Conscription.isMilitiaSword(farmer.getMainHandItem()) && Conscription.isMilitiaSword(builder.getMainHandItem()));
					}
					helper.assertTrue(armed.get(), "not armed in the raid: " + farmer.getMainHandItem() + ", " + builder.getMainHandItem());
					helper.assertTrue(farmerStruck.get() && builderStruck.get(), "struck: the farmer " + farmerStruck.get() + " ("
						+ farmer.getBrain().getActiveNonCoreActivity() + ", " + near.getHealth() + "), the builder " + builderStruck.get() + " ("
						+ builder.getBrain().getActiveNonCoreActivity() + ", " + far.getHealth() + ")");
					if (!over.getAndSet(true)) {
						near.discard();
						far.discard();
						VillageRaids.forget();
						forget();
					}
					helper.assertTrue(!Conscription.isMilitiaSword(farmer.getMainHandItem()) && !Conscription.isMilitiaSword(builder.getMainHandItem())
						&& !Conscription.isMilitiaSword(farmer.getOffhandItem()), "the swords stayed after the raid: " + farmer.getMainHandItem() + ", " + builder.getMainHandItem());
					helper.assertTrue(chest.countItem(Items.STONE_SWORD) == 1, "the chest's sword was taken: " + chest.countItem(Items.STONE_SWORD));
				});
			});
		});
	}

	/** In a raid a child and an ill villager, each penned with a zombie, don't take up a sword or fight. */
	//$ gametest_ticks_batch AREA '300' '"conscriptionHide"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "conscriptionHide")
	public void aChildAndAnIllVillagerDontFight(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		pen(helper, 1, 9);
		pen(helper, 15, 9);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity entity = ready(helper);
			entity.setEdicts(List.of(new Edicts.InForce(CONSCRIPTION.toString(), Chronicle.day(level))));
			night(level);
			Villager child = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 11));
			child.setAge(-24000);
			Villager ill = grown(helper, VillagerProfession.FARMER, new BlockPos(16, 2, 11));
			ModAttachments.ILL_SINCE.set(ill, level.getGameTime());
			Zombie one = raider(helper, new BlockPos(4, 2, 11));
			Zombie two = raider(helper, new BlockPos(18, 2, 11));
			float full = one.getMaxHealth();
			VillageRaids.track(level, helper.absolutePos(HALL), 2);
			forget();
			helper.runAfterDelay(150, () -> {
				helper.assertTrue(Conscription.militia(child) == null && Conscription.militia(ill) == null, "a child or the ill called up");
				helper.assertTrue(!MilitiaCombat.isFighting(child) && !MilitiaCombat.isFighting(ill), "a child or the ill fighting");
				helper.assertTrue(!Conscription.isMilitiaSword(child.getMainHandItem()) && !Conscription.isMilitiaSword(ill.getMainHandItem()), "a sword for a child or the ill");
				helper.assertTrue(one.getHealth() == full && two.getHealth() == full, "the zombies were struck: " + one.getHealth() + ", " + two.getHealth());
				one.discard();
				two.discard();
				VillageRaids.forget();
				helper.succeed();
			});
		});
	}

	/**
	 * A builder at work by day: once a raid is on nothing more gets built and they leave their work; a raid in the night
	 * keeps the village's work stopped the next morning until noon, and after noon the build goes on.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"conscriptionWorkStops"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "conscriptionWorkStops")
	public void noWorkDuringARaidNorTheMorningAfterUntilNoon(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 32);
		helper.setBlock(new BlockPos(14, 2, 14), ModBlocks.VILLAGE_HALL);
		Build build = build(helper);
		Zombie zombie = caged(helper, new BlockPos(26, 2, 26));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(new BlockPos(14, 2, 14));
			VillageHallBlockEntity entity = ready(helper, hall);
			entity.setEdicts(List.of(new Edicts.InForce(CONSCRIPTION.toString(), Chronicle.day(level))));
			forget();
			// Let the build begin, then the raid comes.
			helper.runAfterDelay(200, () -> {
				helper.assertTrue(build.villager().getBrain().isActive(Activity.WORK), "the builder isn't at work: " + build.villager().getBrain().getActiveNonCoreActivity());
				VillageRaids.track(level, hall, 1);
				forget();
				helper.runAfterDelay(40, () -> {
					int left = build.unfinished();
					helper.assertTrue(Conscription.workStopped(build.villager()) && !build.villager().getBrain().isActive(Activity.WORK),
						"still at work in the raid: " + build.villager().getBrain().getActiveNonCoreActivity());
					helper.runAfterDelay(200, () -> {
						helper.assertTrue(build.unfinished() == left, "built in the raid: " + left + " left, now " + build.unfinished());
						helper.assertTrue(!build.villager().getBrain().isActive(Activity.WORK), "back at work in the raid");
						// The raid goes on into the night: the village's work stays stopped till noon tomorrow.
						long day = Chronicle.day(level);
						level.setDayTime((day - 1) * VillageNeeds.DAY + 20000);
						forget();
						helper.assertTrue(Conscription.workStopped(build.villager()), "no stop at night in the raid");
						long noon = day * VillageNeeds.DAY + Conscription.NOON;
						helper.assertTrue(entity.raidWorkUntil() == noon, "work may start again at " + entity.raidWorkUntil() + ", not " + noon);
						zombie.discard();
						VillageRaids.forget();
						level.setDayTime(day * VillageNeeds.DAY + 3000); // the morning after
						forget();
						helper.runAfterDelay(200, () -> {
							helper.assertTrue(Conscription.workStopped(build.villager()) && !build.villager().getBrain().isActive(Activity.WORK),
								"at work the morning after the raid: " + build.villager().getBrain().getActiveNonCoreActivity());
							helper.assertTrue(build.unfinished() == left, "built the morning after: " + left + " left, now " + build.unfinished());
							level.setDayTime(day * VillageNeeds.DAY + Conscription.NOON + 100); // past noon
							forget();
							helper.assertTrue(!Conscription.workStopped(build.villager()), "still stopped after noon");
							helper.succeedWhen(() -> helper.assertTrue(build.unfinished() < left, "nothing built after noon: " + build.unfinished()
								+ " left, " + build.villager().getBrain().getActiveNonCoreActivity()));
						});
					});
				});
			});
		});
	}

	/**
	 * Reformed by The Militia Drill: in a raid a builder with no raider within 24 blocks keeps building while a villager
	 * by the raider stops; and after a night raid the morning is a normal day.
	 */
	//$ gametest_ticks_batch HUGE '1400' '"conscriptionReformed"'
	@GameTest(template = HUGE, timeoutTicks = 1400, batch = "conscriptionReformed")
	public void reformedABuilderFarFromTheRaidersKeepsBuilding(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 32);
		helper.setBlock(new BlockPos(14, 2, 14), ModBlocks.VILLAGE_HALL);
		Build build = build(helper);
		// The raider up on a glass perch in the far corner, more than 24 blocks from anywhere the builder works.
		helper.setBlock(new BlockPos(29, 13, 29), Blocks.GLASS);
		helper.setBlock(new BlockPos(29, 17, 29), Blocks.STONE); // out of the sun, which would burn it by day
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(new BlockPos(14, 2, 14));
			VillageHallBlockEntity entity = ready(helper, hall);
			entity.setEdicts(List.of(new Edicts.InForce(CONSCRIPTION.toString(), Chronicle.day(level))));
			entity.setReforms(List.of(new Reforms.Progress(CONSCRIPTION.toString(), 3, true, 0)));
			Zombie zombie = raider(helper, new BlockPos(29, 14, 29));
			Villager neighbour = helper.spawn(EntityType.VILLAGER, new BlockPos(27, 2, 27));
			neighbour.setNoAi(true);
			neighbour.setVillagerData(neighbour.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
			VillageRaids.track(level, hall, 1);
			forget();
			AtomicInteger start = new AtomicInteger(-1);
			helper.runAfterDelay(100, () -> {
				helper.assertTrue(Conscription.workStopped(neighbour), "the villager by the raider works on");
				helper.assertTrue(!Conscription.workStopped(build.villager()), "the builder far from the raider stopped");
				start.set(build.unfinished());
				helper.runAfterDelay(300, () -> {
					helper.assertTrue(build.unfinished() < start.get(), "nothing built in the raid, reformed: " + build.unfinished() + " of " + start.get()
						+ " left, " + build.villager().getBrain().getActiveNonCoreActivity());
					// A night raid, then the dawn: a normal day even with the old morning-after still on the hall.
					long day = Chronicle.day(level);
					level.setDayTime((day - 1) * VillageNeeds.DAY + 20000);
					forget();
					helper.assertTrue(entity.raidWorkUntil() == 0, "reformed, the raid kept the morning: " + entity.raidWorkUntil());
					entity.setRaidWorkUntil(day * VillageNeeds.DAY + Conscription.NOON);
					zombie.discard();
					neighbour.discard();
					VillageRaids.forget();
					level.setDayTime(day * VillageNeeds.DAY + 500); // dawn
					forget();
					helper.assertTrue(!Conscription.workStopped(build.villager()), "reformed, no work at dawn after the raid");
					// Without the reform that same morning is stopped.
					entity.setReforms(List.of());
					forget();
					helper.assertTrue(Conscription.workStopped(build.villager()), "unreformed, work went on the morning after");
					helper.succeed();
				});
			});
		});
	}

	/**
	 * A conscript holding wheat is armed in the raid (the wheat in the off hand); saved mid-raid and loaded, they have no
	 * sword and hold the wheat again; the hall keeps when work may start again. With the edicts switched off: no militia,
	 * no stop, the sword put away.
	 */
	//$ gametest_ticks_batch AREA '300' '"conscriptionSave"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "conscriptionSave")
	public void aConscriptSavedMidRaidLoadsWithoutTheSword(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		boolean edicts = Edicts.ENABLED;
		Leftovers.after(helper, () -> Edicts.setEnabled(edicts));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		pen(helper, 1, 9);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			entity.setEdicts(List.of(new Edicts.InForce(CONSCRIPTION.toString(), Chronicle.day(level))));
			night(level);
			Villager farmer = grown(helper, VillagerProfession.FARMER, new BlockPos(2, 2, 11));
			farmer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WHEAT, 3));
			VillageRaids.track(level, hall, 1);
			forget();
			helper.runAfterDelay(30, () -> {
				helper.assertTrue(Conscription.isMilitiaSword(farmer.getMainHandItem()) && farmer.getOffhandItem().is(Items.WHEAT)
					&& farmer.getOffhandItem().getCount() == 3, "armed: " + farmer.getMainHandItem() + " / " + farmer.getOffhandItem());
				helper.assertTrue(Conscription.workStopped(farmer) && entity.raidWorkUntil() > 0, "no stop in the raid: " + entity.raidWorkUntil());
				// Saved mid-raid and loaded again.
				CompoundTag tag = farmer.saveWithoutId(new CompoundTag());
				Villager copy = EntityType.VILLAGER.create(level);
				copy.load(tag);
				copy.setUUID(java.util.UUID.randomUUID());
				copy.setNoAi(true);
				level.addFreshEntity(copy);
				helper.assertTrue(!Conscription.isMilitiaSword(copy.getMainHandItem()) && copy.getMainHandItem().is(Items.WHEAT)
					&& copy.getMainHandItem().getCount() == 3 && copy.getOffhandItem().isEmpty(), "loaded: " + copy.getMainHandItem() + " / " + copy.getOffhandItem());
				copy.discard();
				CompoundTag hallTag = entity.saveWithFullMetadata(level.registryAccess());
				VillageHallBlockEntity reloaded = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), hallTag, level.registryAccess());
				helper.assertTrue(reloaded != null && reloaded.raidWorkUntil() == entity.raidWorkUntil(), "the hall forgot: "
					+ (reloaded == null ? null : reloaded.raidWorkUntil()) + " for " + entity.raidWorkUntil());
				hallTag.remove("raidWorkUntil");
				VillageHallBlockEntity old = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), hallTag, level.registryAccess());
				helper.assertTrue(old != null && old.raidWorkUntil() == 0, "a hall saved before: " + (old == null ? null : old.raidWorkUntil()));
				// The edicts switched off: no conscript, no stop; the sword goes back.
				Edicts.setEnabled(false);
				forget();
				helper.assertTrue(Conscription.militia(farmer) == null && !Conscription.workStopped(farmer), "edicts off, still called up or stopped");
				helper.succeedWhen(() -> helper.assertTrue(!Conscription.isMilitiaSword(farmer.getMainHandItem()) && farmer.getMainHandItem().is(Items.WHEAT),
					"edicts off, still armed: " + farmer.getMainHandItem() + " / " + farmer.getOffhandItem()));
			});
		});
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	/** A builder at their bench by a chest of the test hut's materials, the build begun. */
	private record Build(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan) {
		int unfinished() {
			return plan.unfinished(level).size();
		}
	}

	private static Build build(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		BlockPos bench = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 25));
		chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 55));
		chest.setItem(2, new ItemStack(Items.OAK_DOOR));
		chest.setItem(3, new ItemStack(Items.TORCH));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, villager, helper.absolutePos(bench));
		BuildSite site = Builders.start(level, villager, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no test hut");
		return new Build(level, villager, site, plan);
	}

	/** A zombie that stands still in a glass cell under a stone roof (so it neither wanders, strikes nor burns), one of the raid's raiders. */
	private static Zombie caged(GameTestHelper helper, BlockPos at) {
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				for (int y = 0; y <= 2; y++) {
					if (x != 0 || z != 0 || y == 2) {
						helper.setBlock(at.offset(x, y, z), Blocks.GLASS);
					}
				}
			}
		}
		helper.setBlock(at.above(3), Blocks.STONE); // out of the sun, which would burn it by day
		return raider(helper, at);
	}

	/** A zombie that stands still, tagged as one of the raid's raiders. */
	private static Zombie raider(GameTestHelper helper, BlockPos at) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, at);
		zombie.setNoAi(true);
		zombie.setPersistenceRequired();
		zombie.addTag(VillageRaids.TAG);
		return zombie;
	}

	/** A glass pen 6 by 5 with its west corner at x {@code x0}, z {@code z0} (a villager and a zombie inside stay together). */
	private static void pen(GameTestHelper helper, int x0, int z0) {
		for (int y = 2; y <= 3; y++) {
			for (int x = x0 - 1; x <= x0 + 5; x++) {
				for (int z = z0 - 1; z <= z0 + 4; z++) {
					if (x == x0 - 1 || x == x0 + 5 || z == z0 - 1 || z == z0 + 4) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
					}
				}
			}
		}
	}

	/** A grown villager of {@code job} (level 2, so they keep it). */
	private static Villager grown(GameTestHelper helper, VillagerProfession job, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setVillagerData(villager.getVillagerData().setProfession(job).setLevel(2));
		villager.setVillagerXp(10);
		villager.refreshBrain(helper.getLevel());
		return villager;
	}

	/** Night, after dusk today. */
	private static void night(ServerLevel level) {
		level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + 14000);
	}

	private static void forget() {
		CivicEffects.forget();
		Conscription.forget();
	}

	/** A village of radius 16; the clock, the raids, every cache and the radius put back after the test. */
	private static void village(GameTestHelper helper) {
		village(helper, 16);
	}

	/** The same with a village of {@code size} blocks' radius (a build's whole site in it). */
	private static void village(GameTestHelper helper, int size) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = size;
		long time = helper.getLevel().getDayTime();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			helper.getLevel().setDayTime(time);
			VillageRaids.forget();
			VillageNeeds.forget();
			CivicEffects.forget();
			Conscription.forget();
			Moods.forget();
		});
	}

	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		return ready(helper, helper.absolutePos(HALL));
	}

	/** The hall after its first round: a Village with nothing in force, no reform and no raid's morning, every cache asked again. */
	private static VillageHallBlockEntity ready(GameTestHelper helper, BlockPos hall) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
		entity.setRank(VillageRanks.Rank.VILLAGE);
		entity.setEdicts(List.of());
		entity.setReforms(List.of());
		entity.setRaidWorkUntil(0);
		entity.setFestivalDay(-1);
		VillageRaids.forget(); // no raid left from an earlier test at this spot
		VillageNeeds.forget();
		forget();
		Moods.forget();
		return entity;
	}
}
