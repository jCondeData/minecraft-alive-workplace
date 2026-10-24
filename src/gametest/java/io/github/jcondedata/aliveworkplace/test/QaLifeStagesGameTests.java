package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.ClassMigration;
import io.github.jcondedata.aliveworkplace.people.ClassPerks;
import io.github.jcondedata.aliveworkplace.people.FamilyBook;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Retirement;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c, 2026-10-24): adversarial tests for elders, retirement, class-gated jobs and family trees (ROADMAP 34.8,
 * 34.19, 34.20, 34.21), written from the spec's "Done when": the player's own entry points (a sneak-click with a job's
 * item in the off hand, a real Name Tag), boundaries from both sides (the elder day and one below), an elder dying or
 * taking on a build while their successor shadows them, a successor of the class the job needs, and a parent who leaves
 * the village without dying. The world's clock is moved on within one tick and put back, as in {@link RetirementGameTests}.
 */
public class QaLifeStagesGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/**
	 * Retirement starts on exactly the day an elder becomes one: an elder of grown day {@code villagerElderDays - 1} keeps
	 * working with a jobless villager free, and on day {@code villagerElderDays} the jobless villager starts shadowing.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLifeRetireBoundary"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLifeRetireBoundary")
	public void qaRetirementStartsOnTheDayTheyBecomeAnElder(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.FARMER, 3);
		Villager tom = villager(helper, new BlockPos(7, 2, 6), "Tom", VillagerProfession.NONE, 1);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			long today = Chronicle.day(level);
			int days = LifeStages.ELDER_DAYS;
			LifeStages.setAdultSince(tom, today - 20);
			LifeStages.setAdultSince(bram, today - (days - 1));
			Retirement.round(level, hallPos, List.of(bram, tom));
			helper.assertTrue(!LifeStages.isElder(bram) && Retirement.shadow(tom) == null && bram.getVillagerData().getProfession() == VillagerProfession.FARMER,
				"retiring one day before being an elder (grown " + (days - 1) + " of " + days + "): shadow " + Retirement.shadow(tom));
			LifeStages.setAdultSince(bram, today - days);
			Retirement.round(level, hallPos, List.of(bram, tom));
			helper.assertTrue(LifeStages.isElder(bram) && Retirement.shadow(tom) != null && bram.getUUID().equals(Retirement.shadow(tom).elder()),
				"not retiring on the day he became an elder: " + Retirement.shadow(tom));
			helper.succeed();
		});
	}

	/**
	 * The elder dies while their successor shadows them: the successor is free again (no "learning from Bram", no job of
	 * Bram's), and another elder of the village can start retiring with them; their hand-over then gives Odo's trade.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLifeMasterDies"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLifeMasterDies")
	public void qaAnElderWhoDiesWhileTeachingFreesTheirSuccessor(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.FARMER, 3);
		Villager odo = villager(helper, new BlockPos(5, 2, 9), "Odo", VillagerProfession.FLETCHER, 3);
		Villager tom = villager(helper, new BlockPos(7, 2, 6), "Tom", VillagerProfession.NONE, 1);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			long clock = level.getDayTime();
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 1230); // the oldest: first to retire
			LifeStages.setAdultSince(odo, today - 1210);
			LifeStages.setAdultSince(tom, today - 20);
			List<Villager> all = List.of(bram, odo, tom);
			try {
				Retirement.round(level, hallPos, all);
				helper.assertTrue(Retirement.shadow(tom) != null && bram.getUUID().equals(Retirement.shadow(tom).elder()), "Tom isn't shadowing Bram: " + Retirement.shadow(tom));
				level.setDayTime(clock + VillageNeeds.DAY);
				bram.kill(); // a day into the shadowing
				helper.assertTrue(!bram.isAlive(), "Bram didn't die");
				Retirement.round(level, hallPos, all);
				Retirement.Shadow now = Retirement.shadow(tom);
				helper.assertTrue(now == null || !bram.getUUID().equals(now.elder()), "Tom still shadows the dead Bram: " + now);
				helper.assertTrue(!lore(level, hallPos, tom).contains("learning from Bram"), "Tom's card after Bram died: " + lore(level, hallPos, tom));
				helper.assertTrue(tom.getVillagerData().getProfession() != VillagerProfession.FARMER, "Tom took the dead Bram's trade");
				// The village goes on: Odo starts retiring with Tom (this round or the next), and two days later hands over.
				Retirement.round(level, hallPos, all);
				helper.assertTrue(Retirement.shadow(tom) != null && odo.getUUID().equals(Retirement.shadow(tom).elder()), "Odo can't retire after Bram died: " + Retirement.shadow(tom));
				long since = Retirement.shadow(tom).since();
				level.setDayTime(clock + (since - today + Retirement.SHADOW_DAYS) * VillageNeeds.DAY);
				Retirement.round(level, hallPos, all);
				helper.assertTrue(tom.getVillagerData().getProfession() == VillagerProfession.FLETCHER && Retirement.isRetired(odo),
					"Odo's hand-over after Bram's death: " + tom.getVillagerData());
			} finally {
				level.setDayTime(clock);
			}
			helper.succeed();
		});
	}

	/**
	 * Never while they hold something, during the shadowing too: an elder who takes on a builder's site on the second day
	 * doesn't hand over (still the Farmer, the successor still jobless and learning) until the site is done with.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLifeHoldMidShadow"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLifeHoldMidShadow")
	public void qaAnElderWhoTakesOnASiteMidShadowWaitsToHandOver(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.FARMER, 3);
		Villager tom = villager(helper, new BlockPos(7, 2, 6), "Tom", VillagerProfession.NONE, 1);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			long clock = level.getDayTime();
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 1230);
			LifeStages.setAdultSince(tom, today - 20);
			List<Villager> all = List.of(bram, tom);
			try {
				Retirement.round(level, hallPos, all);
				helper.assertTrue(Retirement.shadow(tom) != null, "the shadowing didn't start");
				level.setDayTime(clock + Retirement.SHADOW_DAYS * VillageNeeds.DAY);
				ModAttachments.BUILDER_JOB.set(bram, new BuilderJob(UUID.nameUUIDFromBytes("qa-site".getBytes())));
				helper.assertTrue("site".equals(Retirement.holds(level, bram)), "the site isn't held: " + Retirement.holds(level, bram));
				Retirement.round(level, hallPos, all);
				helper.assertTrue(!Retirement.isRetired(bram) && bram.getVillagerData().getProfession() == VillagerProfession.FARMER
					&& tom.getVillagerData().getProfession() == VillagerProfession.NONE, "handed over while holding a site: Bram " + bram.getVillagerData() + ", Tom " + tom.getVillagerData());
				ModAttachments.BUILDER_JOB.remove(bram);
				level.setDayTime(clock + (Retirement.SHADOW_DAYS + 1) * VillageNeeds.DAY);
				Retirement.round(level, hallPos, all);
				helper.assertTrue(Retirement.isRetired(bram) && tom.getVillagerData().getProfession() == VillagerProfession.FARMER,
					"no hand-over once the site was done with: Tom " + tom.getVillagerData() + ", shadow " + Retirement.shadow(tom));
			} finally {
				level.setDayTime(clock);
			}
			helper.succeed();
		});
	}

	/**
	 * The successor must be of a class the job allows (34.8 and 34.20), with classes on: a Burgher Scholar elder with only a
	 * jobless Peasant keeps working and the hall says he would like to retire; with a jobless Burgher too, she is chosen.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLifeClassSuccessor"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLifeClassSuccessor")
	public void qaASuccessorMustBeOfAClassTheJobAllows(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		classesOn(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = ready(helper);
			hall.setClasses(ClassMigration.VERSION, 0); // seeded long ago: not settling in
			long today = Chronicle.day(level);
			helper.assertTrue(!ClassMigration.quiet(level, hallPos, today), "the village is still settling in");
			Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", ModVillagers.SCHOLAR, 3);
			Villager tom = villager(helper, new BlockPos(7, 2, 6), "Tom", VillagerProfession.NONE, 1);
			SocialClasses.seed(bram, SocialClasses.get(AliveWorkplace.id("burgher")));
			SocialClasses.seed(tom, SocialClasses.get(AliveWorkplace.id("peasant")));
			LifeStages.setAdultSince(bram, today - 1230);
			LifeStages.setAdultSince(tom, today - 20);
			Retirement.round(level, hallPos, List.of(bram, tom));
			helper.assertTrue(Retirement.shadow(tom) == null, "a Peasant chosen to take over a Scholar's trade");
			helper.assertTrue(lore(level, hallPos, bram).contains("Bram would like to retire: the village needs a jobless villager to take over"),
				"Bram's card with only a Peasant: " + lore(level, hallPos, bram));
			Villager ida = villager(helper, new BlockPos(9, 2, 6), "Ida", VillagerProfession.NONE, 1);
			SocialClasses.seed(ida, SocialClasses.get(AliveWorkplace.id("burgher")));
			LifeStages.setAdultSince(ida, today - 30);
			Retirement.round(level, hallPos, List.of(bram, tom, ida));
			helper.assertTrue(Retirement.shadow(ida) != null && bram.getUUID().equals(Retirement.shadow(ida).elder()) && Retirement.shadow(tom) == null,
				"the Burgher not chosen: Ida " + Retirement.shadow(ida) + ", Tom " + Retirement.shadow(tom));
			helper.succeed();
		});
	}

	/**
	 * Higher jobs need higher classes through the player's real click: a sneak-right-click with the Scholar's paper in the
	 * off hand (main hand empty) refuses a Peasant in so many words and leaves her jobless; a plain right-click gives no
	 * job at all; a Burgher sneak-clicked the same way becomes the Scholar.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLifeOffHandPaper"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLifeOffHandPaper")
	public void qaThePaperInTheOffHandIsGatedByClass(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		village(helper);
		classesOn(helper);
		BlockPos lectern = new BlockPos(4, 2, 4);
		BlockPos second = new BlockPos(4, 2, 14);
		helper.setBlock(lectern, Blocks.LECTERN);
		helper.setBlock(second, Blocks.LECTERN);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			ready(helper).setClasses(ClassMigration.VERSION, 0);
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = listener(helper, seen);
			Villager dara = villager(helper, lectern.south(), "Dara", VillagerProfession.NONE, 1);
			SocialClasses.seed(dara, SocialClasses.get(AliveWorkplace.id("peasant")));
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.PAPER));
			player.setShiftKeyDown(true);
			click(player, dara);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.NONE, "the Peasant took a job from the off hand's paper: " + dara.getVillagerData());
			helper.assertTrue(seen.stream().anyMatch(c -> key(c).equals("message.aliveworkplace.job.class_needed")),
				"the Peasant's refusal not said: " + seen.stream().map(QaLifeStagesGameTests::key).toList());
			helper.assertTrue(seen.stream().noneMatch(c -> key(c).equals("message.aliveworkplace.job.chosen")), "told the Peasant took the job");

			Villager bram = villager(helper, second.south(), "Bram", VillagerProfession.NONE, 1);
			SocialClasses.seed(bram, SocialClasses.get(AliveWorkplace.id("burgher")));
			player.setShiftKeyDown(false);
			click(player, bram);
			helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.NONE, "a plain right-click gave a job: " + bram.getVillagerData());
			player.setShiftKeyDown(true);
			click(player, bram);
			helper.assertTrue(bram.getVillagerData().getProfession() == ModVillagers.SCHOLAR, "the Burgher didn't take the off hand's paper: " + bram.getVillagerData());
			helper.assertTrue(bram.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(helper.absolutePos(second)::equals).isPresent(),
				"the Burgher's lectern: " + bram.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			player.setShiftKeyDown(false);
			helper.succeed();
		});
	}



	/**
	 * The hall never renames a villager (34.21): a villager the player names with a real Name Tag keeps that name exactly
	 * through the hall's rounds, the hall shows it with the family name ("Daisy (Ashby)"), and a second Name Tag's name
	 * shows next with the same family; a villager with no name is given none. A child whose mother was renamed still takes
	 * her family, by id.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLifeNameTag"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLifeNameTag")
	public void qaANameTagsNameIsNeverChangedByTheFamilyBook(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = ready(helper);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(GameType.SURVIVAL);
			Villager dara = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 6));
			dara.setNoAi(true);
			Villager plain = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 6));
			plain.setNoAi(true);
			nameTag(player, dara, "Daisy");
			FamilyBook.round(level, hallPos, hall);
			int family = FamilyBook.family(dara);
			String surname = FamilyBook.familyName(family).getString();
			helper.assertTrue(family > 0 && dara.getCustomName() != null && dara.getCustomName().getString().equals("Daisy"),
				"Daisy's name after the round: " + dara.getCustomName() + ", family " + family);
			helper.assertTrue(name(level, hallPos, dara).equals("Daisy (" + surname + ")"), "the hall's name: " + name(level, hallPos, dara));
			helper.assertTrue(!plain.hasCustomName() && FamilyBook.family(plain) > 0, "an unnamed villager named by the hall: " + plain.getCustomName());
			FamilyBook.round(level, hallPos, hall);
			helper.assertTrue(dara.getCustomName().getString().equals("Daisy") && !plain.hasCustomName(), "renamed by a second round");

			// Renamed again: the new name shows, the family is the same, and the book has her new name.
			nameTag(player, dara, "Dee");
			FamilyBook.round(level, hallPos, hall);
			helper.assertTrue(dara.getCustomName().getString().equals("Dee") && FamilyBook.family(dara) == family, "Dee's name or family: " + dara.getCustomName() + " " + FamilyBook.family(dara));
			helper.assertTrue(name(level, hallPos, dara).equals("Dee (" + surname + ")"), "the hall after the second tag: " + name(level, hallPos, dara));
			helper.assertTrue(hall.families().get(dara.getUUID()) != null && hall.families().get(dara.getUUID()).name().getString().equals("Dee"),
				"the book's name: " + hall.families().get(dara.getUUID()));

			// Her child, recorded under her old name and her id, takes her family.
			Villager pip = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 9));
			pip.setNoAi(true);
			pip.setCustomName(Component.literal("Pip"));
			ModAttachments.PARENTS.set(pip, new Families.Parents(Component.literal("Daisy"), Component.literal("Tomas"), "", "", true,
				false, false, dara.getUUID(), UUID.nameUUIDFromBytes("qa-tomas".getBytes())));
			FamilyBook.round(level, hallPos, hall);
			helper.assertTrue(FamilyBook.family(pip) == family, "the child of a renamed mother: family " + FamilyBook.family(pip) + ", hers " + family);
			helper.assertTrue(FamilyBook.children(hall.families(), dara.getUUID()).stream().anyMatch(p -> p.id().equals(pip.getUUID())), "Pip not Dee's child in the book");
			helper.succeed();
		});
	}

	/**
	 * A parent removed from the village without dying (gone from the world, as by a chunk unloading or a caravan) is not
	 * marked dead in the Book of Families: the book keeps them living, and their child's parents are still both known.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLifeParentRemoved"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLifeParentRemoved")
	public void qaAParentRemovedWithoutDyingIsNotMarkedDead(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = ready(helper);
			Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.NONE, 1);
			Villager ilse = villager(helper, new BlockPos(7, 2, 6), "Ilse", VillagerProfession.NONE, 1);
			Villager dara = villager(helper, new BlockPos(9, 2, 6), "Dara", VillagerProfession.NONE, 1);
			ModAttachments.PARENTS.set(dara, new Families.Parents(Component.literal("Ilse"), Component.literal("Bram"), "", "", true,
				false, false, ilse.getUUID(), bram.getUUID()));
			FamilyBook.round(level, hallPos, hall);
			FamilyBook.Person entry = hall.families().get(dara.getUUID());
			helper.assertTrue(entry != null && FamilyBook.parents(hall.families(), entry).size() == 2, "Dara's parents in the book: " + entry);
			bram.discard();
			FamilyBook.round(level, hallPos, hall);
			FamilyBook.Person gone = hall.families().get(bram.getUUID());
			helper.assertTrue(gone != null && !gone.dead(), "Bram, removed but not dead: " + gone);
			helper.assertTrue(FamilyBook.parents(hall.families(), hall.families().get(dara.getUUID())).size() == 2, "Dara lost a parent when Bram left");
			// And a real death is marked.
			ilse.kill();
			helper.assertTrue(hall.families().get(ilse.getUUID()) != null && hall.families().get(ilse.getUUID()).dead(), "Ilse's death not marked");
			helper.succeed();
		});
	}

	// --- Helpers ----------------------------------------------------------------------------------------------------

	private static void village(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Retirement.ENABLED = true;
			VillageNeeds.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	/** Our shipped class files, classes on; put back afterwards. */
	private static void classesOn(GameTestHelper helper) {
		SocialClasses.ENABLED = true;
		SocialClasses.load(SocialClasses.files(helper.getLevel().getServer().getResourceManager()));
		ClassPerks.forget();
		Leftovers.after(helper, () -> {
			new WorkplaceConfig().apply();
			ClassPerks.forget();
		});
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job, int lvl) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(lvl));
		if (job != VillagerProfession.NONE) {
			v.setVillagerXp(1);
		}
		return v;
	}

	/** A jobless villager with a chosen UUID, so the order among candidates is known. */
	private static Villager withId(GameTestHelper helper, BlockPos pos, String name, UUID id) {
		ServerLevel level = helper.getLevel();
		Villager v = EntityType.VILLAGER.create(level);
		BlockPos at = helper.absolutePos(pos);
		v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		v.setUUID(id);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		helper.assertTrue(level.addFreshEntity(v), "the villager " + name + " wasn't added");
		return v;
	}

	private static void nameTag(ServerPlayer player, Villager villager, String name) {
		ItemStack tag = new ItemStack(Items.NAME_TAG);
		tag.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		player.setItemInHand(InteractionHand.MAIN_HAND, tag);
		tag.interactLivingEntity(player, villager, InteractionHand.MAIN_HAND);
	}

	/** A right-click as the game sends it: the main hand's turn, then the off hand's if the first passed. */
	private static void click(ServerPlayer player, Villager villager) {
		net.minecraft.world.InteractionResult r = UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, villager, null);
		if (r == net.minecraft.world.InteractionResult.PASS) {
			UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.OFF_HAND, villager, null);
		}
	}

	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		VillageNeeds.forget();
		Moods.forget();
		return hall;
	}

	/** A player who keeps every chat and action-bar line they're shown. */
	private static ServerPlayer listener(GameTestHelper helper, List<Component> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "qa-life-listener"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message);
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	private static String key(Component c) {
		if (c.getContents() instanceof TranslatableContents t) {
			return t.getKey();
		}
		return c.getSiblings().isEmpty() ? "" : key(c.getSiblings().get(0));
	}

	private static String name(ServerLevel level, BlockPos hall, Villager villager) {
		return VillageHallScreen.person(level, hall, villager).getHoverName().getString();
	}

	private static List<String> lore(ServerLevel level, BlockPos hall, Villager villager) {
		ItemLore lore = VillageHallScreen.person(level, hall, villager).get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}
}
