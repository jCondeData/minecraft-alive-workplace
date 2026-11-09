package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.FamilyBook;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Mourning;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * QA (lane-e, 2026-11-09), B1087 from its spec: a villager a zombie turns isn't dead in the Book of Families nor
 * mourned, and once cured their family links (own entry, children's parent, partner, in the book and on the living
 * villagers) follow the cured villager. Cases TurnedVillagerFamilyGameTests leaves out: turned and cured twice (with a
 * child away through both turns), the hall saved and loaded while she is a zombie villager, both spouses turned and
 * cured, and a grandchild's family tree reaching the cured grandmother.
 *
 * <p>The spec's "drowned into a Drowned" case is not tested: vanilla 1.21.1's {@code ZombieVillager.convertsInWater()}
 * returns false (checked in the mapped jar's bytecode), so a zombie villager never becomes a Drowned.
 */
public class QaTurnedFamily1109eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/**
	 * Ada (Bea's wife, Pip's mother) is turned, cured, turned again and cured again. The book holds one Ada, alive, born
	 * the same day, and Pip's mother and Bea's wife are the final Ada (book and villagers). Rob, away through both turns
	 * (his record names the first Ada), is put right by the round once he is back.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaTurnedTwice1109e"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaTurnedTwice1109e")
	public void aVillagerTurnedAndCuredTwiceKeepsHerFamily(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager ada = villager(helper, new BlockPos(5, 2, 6), "Ada");
		Villager bea = villager(helper, new BlockPos(9, 2, 6), "Bea");
		Villager pip = villager(helper, new BlockPos(13, 2, 6), "Pip");
		UUID adaId = ada.getUUID();
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = ready(helper);
			FamilyBook.Book book = hall.families();
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(ada, today - 40);
			QaCuredVillager1109eGameTests.marry(ada, bea, today - 20);
			ModAttachments.PARENTS.set(pip, new Families.Parents(Component.literal("Ada"), Component.literal("Bea"), "", "", true, false, false, adaId, bea.getUUID()));
			FamilyBook.round(level, helper.absolutePos(HALL), hall);
			FamilyBook.Person before = book.get(adaId);
			helper.assertTrue(before != null && !before.dead(), "setup: Ada isn't in the book: " + before);
			long born = before.born();
			int family = before.family();

			ZombieVillager first = TurnedVillagerFamilyGameTests.turn(helper, ada, new BlockPos(5, 2, 4));
			TurnedVillagerFamilyGameTests.cure(first);
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(first.isRemoved(), "setup: the first zombie villager Ada wasn't cured");
				Villager once = named(helper, "Ada");
				FamilyBook.round(level, helper.absolutePos(HALL), hall);
				ZombieVillager second = TurnedVillagerFamilyGameTests.turn(helper, once, new BlockPos(5, 2, 8));
				helper.assertTrue(book.people().stream().noneMatch(FamilyBook.Person::dead),
					"turned a second time, someone is dated dead: " + book.people());
				TurnedVillagerFamilyGameTests.cure(second);
				helper.runAfterDelay(5, () -> {
					helper.assertTrue(second.isRemoved(), "setup: the second zombie villager Ada wasn't cured");
					Villager twice = named(helper, "Ada");
					UUID id = twice.getUUID();
					helper.assertTrue(!id.equals(adaId) && !id.equals(once.getUUID()), "setup: the cured Ada kept an old id");
					FamilyBook.round(level, helper.absolutePos(HALL), hall);

					List<FamilyBook.Person> adas = book.people().stream().filter(p -> p.name().getString().equals("Ada")).toList();
					helper.assertTrue(adas.size() == 1, "after two turns the book holds " + adas.size() + " Adas: " + adas);
					FamilyBook.Person her = adas.get(0);
					helper.assertTrue(her.id().equals(id) && !her.dead() && her.born() == born && her.family() == family,
						"the book's Ada isn't the twice-cured villager, alive, born day " + born + ", family " + family + ": " + her + " (she is " + id + ")");
					helper.assertTrue(book.people().stream().noneMatch(FamilyBook.Person::dead), "after two cures, someone is dated dead: " + book.people());
					helper.assertTrue(id.equals(book.now(adaId)), "the book's first Ada now: " + book.now(adaId) + ", expected " + id);
					helper.assertTrue(id.equals(Families.parents(pip).motherId()) && id.equals(book.get(pip.getUUID()).mother()),
						"Pip's mother after two cures: " + Families.parents(pip).motherId() + " / " + book.get(pip.getUUID()).mother() + " (she is " + id + ")");
					Couples.Partner wife = ModAttachments.PARTNER.get(bea);
					Couples.Partner husband = ModAttachments.PARTNER.get(twice);
					helper.assertTrue(wife != null && id.equals(wife.id()) && wife.married() && id.equals(book.get(bea.getUUID()).partner()),
						"Bea's wife after two cures: " + wife + " / book " + book.get(bea.getUUID()).partner() + " (she is " + id + ")");
					helper.assertTrue(husband != null && bea.getUUID().equals(husband.id()) && bea.getUUID().equals(her.partner()),
						"the twice-cured Ada's partner isn't Bea: " + husband + " / book " + her.partner());
					helper.assertTrue(!ModAttachments.GRIEF.has(pip) && !ModAttachments.GRIEF.has(bea) && !Mourning.grieving(pip, today)
							&& !ModAttachments.WIDOWED_DAY.has(bea),
						"Pip or Bea grieves (or Bea is widowed) for Ada, turned twice and cured: " + ModAttachments.GRIEF.get(pip)
							+ ", " + ModAttachments.GRIEF.get(bea) + ", widowed " + ModAttachments.WIDOWED_DAY.get(bea));

					// Rob was away through both turns: his record names the first Ada.
					Villager rob = villager(helper, new BlockPos(13, 2, 9), "Rob");
					ModAttachments.PARENTS.set(rob, new Families.Parents(Component.literal("Ada"), Component.literal("Bea"), "", "", true, false, false, adaId, bea.getUUID()));
					FamilyBook.round(level, helper.absolutePos(HALL), hall);
					helper.assertTrue(id.equals(Families.parents(rob).motherId()) && id.equals(book.get(rob.getUUID()).mother()),
						"Rob, away through both turns: mother " + Families.parents(rob).motherId() + " / book " + book.get(rob.getUUID()).mother()
							+ ", expected the twice-cured Ada " + id);
					helper.succeed();
				});
			});
		});
	}

	/**
	 * Ada is turned; the hall (and its book) is saved and loaded while she is a zombie villager; then she is cured. The
	 * reloaded book still follows her: her entry, Pip's mother and Bea's wife are the cured Ada, and nobody is dead.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaTurnedReload1109e"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaTurnedReload1109e")
	public void aHallReloadedWhileSheIsAZombieVillagerStillFollowsHerCure(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager ada = villager(helper, new BlockPos(5, 2, 6), "Ada");
		Villager bea = villager(helper, new BlockPos(9, 2, 6), "Bea");
		Villager pip = villager(helper, new BlockPos(13, 2, 6), "Pip");
		UUID adaId = ada.getUUID();
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = ready(helper);
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(ada, today - 40);
			QaCuredVillager1109eGameTests.marry(ada, bea, today - 20);
			ModAttachments.PARENTS.set(pip, new Families.Parents(Component.literal("Ada"), Component.literal("Bea"), "", "", true, false, false, adaId, bea.getUUID()));
			FamilyBook.round(level, helper.absolutePos(HALL), hall);
			helper.assertTrue(hall.families().get(adaId) != null, "setup: Ada isn't in the book");
			long born = hall.families().get(adaId).born();

			ZombieVillager zombie = TurnedVillagerFamilyGameTests.turn(helper, ada, new BlockPos(5, 2, 4));
			// The world is saved and loaded: the hall's book comes back from its NBT only.
			CompoundTag saved = hall.saveWithFullMetadata(level.registryAccess());
			hall.families().load(new CompoundTag(), level.registryAccess());
			helper.assertTrue(hall.families().size() == 0, "setup: the book wasn't emptied before the load");
			hall.loadWithComponents(saved, level.registryAccess());
			FamilyBook.Book book = hall.families();
			helper.assertTrue(book.get(zombie.getUUID()) != null && !book.get(zombie.getUUID()).dead() && book.get(adaId) == null,
				"reloaded while turned, the book lost the zombie villager Ada: " + book.people());
			helper.assertTrue(zombie.getUUID().equals(book.now(adaId)), "reloaded while turned, the first Ada now: " + book.now(adaId));

			TurnedVillagerFamilyGameTests.cure(zombie);
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(zombie.isRemoved(), "setup: the zombie villager Ada wasn't cured");
				Villager cured = named(helper, "Ada");
				UUID id = cured.getUUID();
				FamilyBook.round(level, helper.absolutePos(HALL), hall);
				FamilyBook.Person her = book.get(id);
				helper.assertTrue(her != null && !her.dead() && her.born() == born && book.get(zombie.getUUID()) == null,
					"after a reload mid-turn, the book's Ada isn't the cured villager: " + her + "; zombie entry " + book.get(zombie.getUUID()));
				helper.assertTrue(book.people().stream().noneMatch(FamilyBook.Person::dead), "after a reload mid-turn, someone is dated dead: " + book.people());
				helper.assertTrue(id.equals(Families.parents(pip).motherId()) && id.equals(book.get(pip.getUUID()).mother()),
					"after a reload mid-turn, Pip's mother: " + Families.parents(pip).motherId() + " / " + book.get(pip.getUUID()).mother() + " (she is " + id + ")");
				Couples.Partner wife = ModAttachments.PARTNER.get(bea);
				helper.assertTrue(wife != null && id.equals(wife.id()) && id.equals(book.get(bea.getUUID()).partner()) && bea.getUUID().equals(her.partner()),
					"after a reload mid-turn, Bea's wife: " + wife + " / book " + book.get(bea.getUUID()).partner() + " / hers " + her.partner());
				helper.assertTrue(!ModAttachments.GRIEF.has(pip) && !ModAttachments.GRIEF.has(bea),
					"after a reload mid-turn, someone grieves: " + ModAttachments.GRIEF.get(pip) + ", " + ModAttachments.GRIEF.get(bea));
				helper.succeed();
			});
		});
	}

	/**
	 * Ada and Bea, married, Pip's parents, are both turned, then cured one after the other. Each names the other as
	 * partner (book and villagers), married; neither is widowed or dated dead; nobody grieves; Pip's parents are both.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaBothTurned1109e"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBothTurned1109e")
	public void bothSpousesTurnedAndCuredAreStillMarriedToEachOther(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager ada = villager(helper, new BlockPos(5, 2, 6), "Ada");
		Villager bea = villager(helper, new BlockPos(9, 2, 6), "Bea");
		Villager pip = villager(helper, new BlockPos(13, 2, 6), "Pip");
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = ready(helper);
			FamilyBook.Book book = hall.families();
			long today = Chronicle.day(level);
			QaCuredVillager1109eGameTests.marry(ada, bea, today - 20);
			ModAttachments.PARENTS.set(pip, new Families.Parents(Component.literal("Ada"), Component.literal("Bea"), "", "", true, false, false, ada.getUUID(), bea.getUUID()));
			FamilyBook.round(level, helper.absolutePos(HALL), hall);

			ZombieVillager zAda = TurnedVillagerFamilyGameTests.turn(helper, ada, new BlockPos(5, 2, 4));
			ZombieVillager zBea = TurnedVillagerFamilyGameTests.turn(helper, bea, new BlockPos(9, 2, 4));
			helper.assertTrue(book.people().stream().noneMatch(FamilyBook.Person::dead), "both turned, someone is dated dead: " + book.people());
			TurnedVillagerFamilyGameTests.cure(zAda);
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(zAda.isRemoved(), "setup: the zombie villager Ada wasn't cured");
				TurnedVillagerFamilyGameTests.cure(zBea);
				helper.runAfterDelay(5, () -> {
					helper.assertTrue(zBea.isRemoved(), "setup: the zombie villager Bea wasn't cured");
					Villager a = named(helper, "Ada");
					Villager b = named(helper, "Bea");
					FamilyBook.round(level, helper.absolutePos(HALL), hall);
					Couples.Partner aP = ModAttachments.PARTNER.get(a);
					Couples.Partner bP = ModAttachments.PARTNER.get(b);
					helper.assertTrue(aP != null && aP.married() && b.getUUID().equals(aP.id()),
						"the cured Ada's partner isn't the cured Bea: " + aP + " (Bea is " + b.getUUID() + ")");
					helper.assertTrue(bP != null && bP.married() && a.getUUID().equals(bP.id()),
						"the cured Bea's partner isn't the cured Ada: " + bP + " (Ada is " + a.getUUID() + ")");
					FamilyBook.Person ap = book.get(a.getUUID());
					FamilyBook.Person bp = book.get(b.getUUID());
					helper.assertTrue(ap != null && bp != null && !ap.dead() && !bp.dead() && b.getUUID().equals(ap.partner()) && a.getUUID().equals(bp.partner()),
						"in the book the cured couple aren't each other's partners, alive: " + ap + " / " + bp);
					helper.assertTrue(book.people().stream().noneMatch(FamilyBook.Person::dead), "both cured, someone is dated dead: " + book.people());
					helper.assertTrue(!ModAttachments.WIDOWED_DAY.has(a) && !ModAttachments.WIDOWED_DAY.has(b)
							&& !ModAttachments.LATE_PARTNER.has(a) && !ModAttachments.LATE_PARTNER.has(b),
						"a cured spouse is widowed: Ada " + ModAttachments.WIDOWED_DAY.get(a) + "/" + ModAttachments.LATE_PARTNER.get(a)
							+ ", Bea " + ModAttachments.WIDOWED_DAY.get(b) + "/" + ModAttachments.LATE_PARTNER.get(b));
					helper.assertTrue(!ModAttachments.GRIEF.has(a) && !ModAttachments.GRIEF.has(b) && !ModAttachments.GRIEF.has(pip),
						"someone grieves for the cured couple: " + ModAttachments.GRIEF.get(a) + ", " + ModAttachments.GRIEF.get(b) + ", " + ModAttachments.GRIEF.get(pip));
					Families.Parents pp = Families.parents(pip);
					helper.assertTrue(a.getUUID().equals(pp.motherId()) && b.getUUID().equals(pp.fatherId())
							&& a.getUUID().equals(book.get(pip.getUUID()).mother()) && b.getUUID().equals(book.get(pip.getUUID()).father()),
						"Pip's parents aren't the cured couple: " + pp.motherId() + "/" + pp.fatherId() + ", book " + book.get(pip.getUUID()));
					helper.succeed();
				});
			});
		});
	}

	/**
	 * Ada is Pip's mother, Pip is Tia's. Ada is turned and cured: Tia's grandparents in the book are the cured Ada, Ada's
	 * grandchildren include Tia, and Tia's generation and the founder of her line are as before (the cured Ada if it was Ada).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaTurnedGrandmother1109e"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaTurnedGrandmother1109e")
	public void aCuredGrandmotherIsStillHerGrandchildsGrandmother(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager ada = villager(helper, new BlockPos(5, 2, 6), "Ada");
		Villager pip = villager(helper, new BlockPos(9, 2, 6), "Pip");
		Villager tia = villager(helper, new BlockPos(13, 2, 6), "Tia");
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = ready(helper);
			FamilyBook.Book book = hall.families();
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(ada, today - 60);
			LifeStages.setAdultSince(pip, today - 30);
			ModAttachments.PARENTS.set(pip, new Families.Parents(Component.literal("Ada"), Component.empty(), "", "", true, false, false, ada.getUUID(), null));
			ModAttachments.PARENTS.set(tia, new Families.Parents(Component.literal("Pip"), Component.empty(), "", "", true, false, false, pip.getUUID(), null));
			FamilyBook.round(level, helper.absolutePos(HALL), hall);
			FamilyBook.Person tiaBefore = book.get(tia.getUUID());
			helper.assertTrue(tiaBefore != null && FamilyBook.grandparents(book, tiaBefore).stream().anyMatch(p -> p.id().equals(ada.getUUID())),
				"setup: Tia's grandparents before the turn: " + (tiaBefore == null ? null : FamilyBook.grandparents(book, tiaBefore)));
			int generation = FamilyBook.generation(book, tiaBefore);
			UUID founderBefore = FamilyBook.founder(book, tiaBefore).id();

			ZombieVillager zombie = TurnedVillagerFamilyGameTests.turn(helper, ada, new BlockPos(5, 2, 4));
			TurnedVillagerFamilyGameTests.cure(zombie);
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(zombie.isRemoved(), "setup: the zombie villager Ada wasn't cured");
				Villager cured = named(helper, "Ada");
				UUID id = cured.getUUID();
				FamilyBook.round(level, helper.absolutePos(HALL), hall);
				FamilyBook.Person t = book.get(tia.getUUID());
				FamilyBook.Person her = book.get(id);
				helper.assertTrue(her != null && !her.dead(), "the cured Ada isn't alive in the book: " + her);
				List<FamilyBook.Person> grand = FamilyBook.grandparents(book, t);
				helper.assertTrue(grand.size() == 1 && grand.get(0).id().equals(id),
					"Tia's grandparents after Ada's cure: " + grand + " (the cured Ada is " + id + ")");
				helper.assertTrue(FamilyBook.grandchildren(book, her).stream().anyMatch(p -> p.id().equals(tia.getUUID())),
					"the cured Ada's grandchildren don't include Tia: " + FamilyBook.grandchildren(book, her));
				UUID founder = founderBefore.equals(ada.getUUID()) ? id : founderBefore;
				helper.assertTrue(FamilyBook.generation(book, t) == generation && FamilyBook.founder(book, t).id().equals(founder),
					"Tia's line after Ada's cure: generation " + FamilyBook.generation(book, t) + " (was " + generation + "), founder "
						+ FamilyBook.founder(book, t) + " (expected " + founder + "; the cured Ada is " + id + ")");
				helper.succeed();
			});
		});
	}

	// --- Helpers ----------------------------------------------------------------------------------------------------

	/** The one living villager named {@code name} near the test. */
	private static Villager named(GameTestHelper helper, String name) {
		List<Villager> found = helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(2),
			v -> v.isAlive() && v.hasCustomName() && v.getCustomName().getString().equals(name));
		helper.assertTrue(found.size() == 1, "setup: living villagers named " + name + ": " + found.size());
		return found.get(0);
	}

	/** A Village Hall (the hall's radius 16 for the test), on Hard (the difficulty is put back when the test ends). */
	private static void village(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Difficulty was = level.getDifficulty();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		Leftovers.after(helper, () -> level.getServer().setDifficulty(was, true));
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
			VillageNeeds.forget();
			Moods.forget();
		});
		VillageHalls.forgetNearest();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setPersistenceRequired();
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		v.setVillagerXp(20);
		return v;
	}

	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		VillageNeeds.forget();
		Moods.forget();
		return hall;
	}
}
