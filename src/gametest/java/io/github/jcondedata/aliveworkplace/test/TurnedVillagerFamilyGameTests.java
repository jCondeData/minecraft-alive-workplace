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
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * B1087: a villager a zombie turns isn't dead in the Book of Families nor mourned (cure them), and once cured (a new
 * entity, with a new UUID) the book and their family name the cured villager: their own entry, their children's parent,
 * their partner's. A zombie villager never cured (killed, or despawned) is dated dead in the book.
 */
public class TurnedVillagerFamilyGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/**
	 * Ada (Bea's wife, Pip's mother) is turned on Hard: the book still has her alive (as the zombie villager), Pip doesn't
	 * grieve. Cured: the book's Ada is the cured villager, born the same day; Pip's mother and Bea's wife are her, in the
	 * book and on them, and after a save and reload. Rob, away while it happened (his record names the old Ada), is put
	 * right by the hall's round.
	 */
	//$ gametest_ticks_batch AREA '200' '"turnedFamilyCured"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "turnedFamilyCured")
	public void aTurnedMotherIsNotDeadAndHerFamilyFollowsHerCure(GameTestHelper helper) {
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
			List<Component> seen = new java.util.ArrayList<>();
			net.minecraft.server.level.ServerPlayer player = WonderFinishedGameTests.listener(helper, seen, "Neighbor");
			BlockPos at = helper.absolutePos(new BlockPos(11, 2, 4));
			player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);

			ZombieVillager zombie = turn(helper, ada, new BlockPos(5, 2, 4));
			helper.assertTrue(seen.stream().noneMatch(c -> c.getString().contains("has died")),
				"the village is told Ada died, who was turned: " + seen.stream().map(Component::getString).toList());
			UUID zombieId = zombie.getUUID();
			FamilyBook.Person turned = book.get(zombieId);
			helper.assertTrue(book.get(adaId) == null && turned != null && !turned.dead() && book.people().stream().noneMatch(FamilyBook.Person::dead),
				"turned, Ada is dated dead in the book (or her entry didn't follow her): " + book.people());
			helper.assertTrue(!ModAttachments.GRIEF.has(pip) && !Mourning.grieving(pip, today) && !ModAttachments.GRIEF.has(bea),
				"Pip or Bea grieves for Ada, who was turned, not killed: " + ModAttachments.GRIEF.get(pip) + ", " + ModAttachments.GRIEF.get(bea));
			helper.assertTrue(zombieId.equals(Families.parents(pip).motherId()) && zombieId.equals(book.get(pip.getUUID()).mother()),
				"turned, Pip's mother is still the old Ada: " + Families.parents(pip).motherId() + " / " + book.get(pip.getUUID()).mother());

			cure(zombie);
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(zombie.isRemoved(), "setup: the zombie villager wasn't cured");
				List<Villager> back = level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(2),
					v -> v.isAlive() && v.hasCustomName() && v.getCustomName().getString().equals("Ada"));
				helper.assertTrue(back.size() == 1, "setup: cured villagers named Ada: " + back.size());
				Villager cured = back.get(0);
				UUID id = cured.getUUID();
				FamilyBook.round(level, helper.absolutePos(HALL), hall);

				FamilyBook.Person her = book.get(id);
				helper.assertTrue(her != null && !her.dead() && her.born() == born && her.name().getString().equals("Ada")
						&& book.get(adaId) == null && book.get(zombieId) == null,
					"the book's Ada isn't the cured villager: " + her + "; old " + book.get(adaId) + ", zombie " + book.get(zombieId));
				helper.assertTrue(id.equals(Families.parents(pip).motherId()) && id.equals(book.get(pip.getUUID()).mother())
						&& FamilyBook.children(book, id).stream().anyMatch(p -> p.id().equals(pip.getUUID())),
					"Pip's mother isn't the cured Ada: " + Families.parents(pip).motherId() + " / " + book.get(pip.getUUID()).mother() + " (she is " + id + ")");
				Couples.Partner wife = ModAttachments.PARTNER.get(bea);
				helper.assertTrue(wife != null && id.equals(wife.id()) && id.equals(book.get(bea.getUUID()).partner()) && bea.getUUID().equals(her.partner()),
					"Bea's wife isn't the cured Ada: " + wife + " / " + book.get(bea.getUUID()).partner() + " / hers " + her.partner());

				// Saved and loaded: the book and Pip keep the links.
				CompoundTag tag = new CompoundTag();
				book.save(tag, level.registryAccess());
				FamilyBook.Book loaded = new FamilyBook.Book();
				loaded.load(tag, level.registryAccess());
				helper.assertTrue(loaded.get(id) != null && !loaded.get(id).dead() && id.equals(loaded.get(pip.getUUID()).mother())
						&& id.equals(loaded.now(adaId)) && id.equals(loaded.now(zombieId)),
					"after a reload, the book lost the cured Ada: " + loaded.people() + ", old id now " + loaded.now(adaId));
				Villager pipLoaded = EntityType.VILLAGER.create(level);
				pipLoaded.load(pip.saveWithoutId(new CompoundTag()));
				helper.assertTrue(id.equals(Families.parents(pipLoaded).motherId()), "after a reload, Pip's mother: " + Families.parents(pipLoaded).motherId());
				// An old save's book (no turns) loads with none.
				FamilyBook.Book old = new FamilyBook.Book();
				CompoundTag oldTag = tag.copy();
				oldTag.remove("familiesTurned");
				old.load(oldTag, level.registryAccess());
				helper.assertTrue(adaId.equals(old.now(adaId)) && old.get(id) != null, "an old save's book: " + old.now(adaId));

				// Rob was away (unloaded) all along: his record names the old Ada; the hall's round puts it right.
				Villager rob = villager(helper, new BlockPos(13, 2, 9), "Rob");
				ModAttachments.PARENTS.set(rob, new Families.Parents(Component.literal("Ada"), Component.literal("Bea"), "", "", true, false, false, adaId, bea.getUUID()));
				FamilyBook.round(level, helper.absolutePos(HALL), hall);
				helper.assertTrue(id.equals(Families.parents(rob).motherId()) && id.equals(book.get(rob.getUUID()).mother()),
					"Rob's mother after the round: " + Families.parents(rob).motherId() + " / " + book.get(rob.getUUID()).mother());
				helper.succeed();
			});
		});
	}

	/**
	 * Ada and Cy are turned and never cured: the zombie villager Ada is killed, Cy's despawns. The book dates both their
	 * deaths today; nobody grieves (the village lost them when they were turned).
	 */
	//$ gametest_ticks_batch AREA '200' '"turnedFamilyLost"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "turnedFamilyLost")
	public void aZombieVillagerNeverCuredIsDatedDead(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager ada = villager(helper, new BlockPos(5, 2, 6), "Ada");
		Villager cy = villager(helper, new BlockPos(9, 2, 6), "Cy");
		Villager pip = villager(helper, new BlockPos(13, 2, 6), "Pip");
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = ready(helper);
			FamilyBook.Book book = hall.families();
			long today = Chronicle.day(level);
			ModAttachments.PARENTS.set(pip, new Families.Parents(Component.literal("Ada"), Component.literal("Cy"), "", "", true, false, false, ada.getUUID(), cy.getUUID()));
			FamilyBook.round(level, helper.absolutePos(HALL), hall);
			ZombieVillager zAda = turn(helper, ada, new BlockPos(5, 2, 4));
			ZombieVillager zCy = turn(helper, cy, new BlockPos(9, 2, 4));
			helper.assertTrue(book.people().stream().noneMatch(FamilyBook.Person::dead), "turned, someone is dated dead: " + book.people());

			zAda.kill();
			zCy.discard(); // as a despawn does
			FamilyBook.Person a = book.get(zAda.getUUID());
			FamilyBook.Person c = book.get(zCy.getUUID());
			helper.assertTrue(a != null && a.dead() && a.died() == today && a.name().getString().equals("Ada"), "the zombie villager Ada killed: " + a);
			helper.assertTrue(c != null && c.dead() && c.died() == today && c.name().getString().equals("Cy"), "the zombie villager Cy despawned: " + c);
			helper.assertTrue(book.get(pip.getUUID()) != null && !book.get(pip.getUUID()).dead() && !ModAttachments.GRIEF.has(pip)
				&& zAda.getUUID().equals(book.get(pip.getUUID()).mother()), "Pip: " + book.get(pip.getUUID()));
			helper.succeed();
		});
	}

	// --- Helpers ----------------------------------------------------------------------------------------------------

	/**
	 * {@code victim} killed by a zombie (spawned at {@code zombieAt}) on Hard ({@link #village}), where vanilla turns every
	 * such villager: the zombie villager they become.
	 */
	static ZombieVillager turn(GameTestHelper helper, Villager victim, BlockPos zombieAt) {
		ServerLevel level = helper.getLevel();
		String name = victim.getCustomName().getString();
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, zombieAt);
		zombie.setNoAi(true);
		zombie.setCanPickUpLoot(false);
		victim.hurt(level.damageSources().mobAttack(zombie), 1000f);
		zombie.discard();
		List<ZombieVillager> turned = level.getEntitiesOfClass(ZombieVillager.class, helper.getBounds().inflate(2),
			z -> z.isAlive() && z.hasCustomName() && z.getCustomName().getString().equals(name));
		helper.assertTrue(victim.isRemoved() && turned.size() == 1, "setup: " + name + " didn't become one zombie villager: " + turned.size());
		ZombieVillager zv = turned.get(0);
		zv.setNoAi(true);
		zv.setPersistenceRequired();
		return zv;
	}

	/** Cures {@code zombie} as a golden apple does, the wait cut to a tick. */
	static void cure(ZombieVillager zombie) {
		try {
			var start = ZombieVillager.class.getDeclaredMethod("startConverting", UUID.class, int.class);
			start.setAccessible(true);
			start.invoke(zombie, null, 1);
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("can't start the cure: " + e);
		}
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
