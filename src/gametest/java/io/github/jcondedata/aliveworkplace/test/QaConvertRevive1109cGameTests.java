package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.Retirement;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (qa-lane-c-1109-0655): the corners of B1081 (a villager a zombie turns and a player cures keeps what the mod knew
 * of them, the marriage included) and B1082 (after an Undertaker's revival both partners agree: married to each other
 * again when the widow(er) is still alone, else the revived one single and free to court) that
 * {@code QaCuredVillager1109e*} and {@code QaRevivedPartner1109eBugTests} don't reach. Written from the bug reports and
 * the graves page, through vanilla's own conversion (a zombie's kill on Hard, the cure finishing) and {@code Graves.revive}.
 */
public class QaConvertRevive1109cGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:build_area";
	static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000001109c");

	// ---- B1081: turned and cured ---------------------------------------------------------------------------------

	/**
	 * A grown, schooled, retired widow with parents on record, a gift, job counts and 3 hearts for a player: the zombie
	 * villager she becomes carries all of it, and so does the villager she's cured into.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaConv1109cMany"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaConv1109cMany")
	public void aCuredVillagerKeepsEverySortOfSavedRecordAndSoDoesTheZombieBetween(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager ada = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(2, 2, 2), "Ada");
		UUID mother = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
		Families.Parents parents = new Families.Parents(Component.literal("Mira"), Component.literal("Odo"), "minecraft:farmer", "minecraft:mason",
			true, true, false, mother, null);
		ModAttachments.PARENTS.set(ada, parents);
		ModAttachments.GIFTED.set(ada, "none");
		ModAttachments.SCHOOLED.set(ada, true);
		ModAttachments.LESSONS.set(ada, 4);
		ModAttachments.RETIRED.set(ada, new Retirement.Retired(12, "minecraft:farmer", 3));
		ModAttachments.TREES_FELLED.set(ada, 41);
		ModAttachments.ITEMS_CRAFTED.set(ada, 17);
		ModAttachments.ADULT_SINCE.set(ada, 9L);
		ModAttachments.WIDOWED_DAY.set(ada, 30L);
		ModAttachments.LATE_PARTNER.set(ada, new Couples.LatePartner(UUID.fromString("00000000-0000-0000-0000-0000000000b2"), Component.literal("Bram")));
		ModAttachments.SOCIAL_CLASS.set(ada, QaCuredVillager1109eGameTests.BURGHER);
		QaCuredVillager1109eGameTests.befriend(ada, PLAYER, 300);
		turn(helper, ada, zombie -> {
			List<String> zLost = lost(zombie, parents);
			helper.assertTrue(zLost.isEmpty(), "the zombie villager Ada became doesn't carry what the mod knew of her: " + String.join("; ", zLost));
			cure(helper, zombie, "Ada", cured -> {
				List<String> gone = lost(cured, parents);
				helper.assertTrue(gone.isEmpty(), "cured, Ada lost: " + String.join("; ", gone));
				helper.succeed();
			});
		});
	}

	/**
	 * Ada, married to Bea, is turned; the zombie villager is saved and loaded again (the chunk unloaded, the server
	 * restarted) before the cure: the loaded zombie still carries her family and marriage, Bea still names her, and cured
	 * the two are married to each other.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaConv1109cReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaConv1109cReload")
	public void aZombieSavedAndLoadedBeforeTheCureStillComesBackMarried(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Villager ada = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(2, 2, 2), "Ada");
		Villager bea = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 2), "Bea");
		ModAttachments.FAMILY_NAME.set(ada, 7);
		ModAttachments.ADULT_SINCE.set(ada, 5L);
		QaCuredVillager1109eGameTests.befriend(ada, PLAYER, 500);
		QaCuredVillager1109eGameTests.marry(ada, bea, 3);
		turn(helper, ada, zombie -> {
			// what the mod has on the zombie lands in its saved data the way a chunk save writes it
			CompoundTag tag = zombie.saveWithoutId(new CompoundTag());
			tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ZOMBIE_VILLAGER).toString());
			UUID id = zombie.getUUID();
			zombie.discard();
			helper.runAfterDelay(2, () -> {
				Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
				helper.assertTrue(loaded instanceof ZombieVillager && loaded.getUUID().equals(id), "setup: the zombie villager didn't load back: " + loaded);
				ZombieVillager back = (ZombieVillager) loaded;
				back.setNoAi(true);
				helper.assertTrue(level.addFreshEntity(back), "setup: the loaded zombie villager couldn't be added");
				Couples.Partner zp = ModAttachments.PARTNER.get(back);
				helper.assertTrue(Integer.valueOf(7).equals(ModAttachments.FAMILY_NAME.get(back)) && points(back) == 500
						&& zp != null && zp.married() && zp.id().equals(bea.getUUID()),
					"loaded from its save, the zombie villager lost Ada's records: family " + ModAttachments.FAMILY_NAME.get(back)
						+ ", points " + points(back) + ", partner " + zp);
				Couples.Partner bp = ModAttachments.PARTNER.get(bea);
				helper.assertTrue(bp != null && bp.id().equals(id), "while Ada is a zombie villager Bea doesn't name her: " + bp);
				cure(helper, back, "Ada", cured -> {
					Couples.Partner hers = ModAttachments.PARTNER.get(cured);
					Couples.Partner his = ModAttachments.PARTNER.get(bea);
					helper.assertTrue(Integer.valueOf(7).equals(ModAttachments.FAMILY_NAME.get(cured)) && Long.valueOf(5L).equals(ModAttachments.ADULT_SINCE.get(cured))
							&& Friendship.points(cured, PLAYER) == 500,
						"cured after a reload, Ada lost: family " + ModAttachments.FAMILY_NAME.get(cured) + ", adult since "
							+ ModAttachments.ADULT_SINCE.get(cured) + ", points " + Friendship.points(cured, PLAYER));
					helper.assertTrue(hers != null && hers.married() && hers.id().equals(bea.getUUID()) && his != null && his.married()
							&& his.id().equals(cured.getUUID()) && !ModAttachments.WIDOWED_DAY.has(bea) && !ModAttachments.LATE_PARTNER.has(bea),
						"cured after a reload, Ada and Bea aren't married to each other: Ada's " + hers + ", Bea's " + his
							+ " (cured Ada " + cured.getUUID() + "), Bea widowed " + ModAttachments.WIDOWED_DAY.get(bea));
					helper.succeed();
				});
			});
		});
	}

	/** Ada and Bea, married, are both turned, one after the other, and both cured: still married to each other. */
	//$ gametest_ticks_batch AREA '240' '"qaConv1109cBoth"'
	@GameTest(template = AREA, timeoutTicks = 240, batch = "qaConv1109cBoth")
	public void aCoupleBothTurnedAndBothCuredAreStillMarriedToEachOther(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager ada = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(2, 2, 2), "Ada");
		Villager bea = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 2), "Bea");
		QaCuredVillager1109eGameTests.marry(ada, bea, 3);
		turn(helper, ada, za -> turn(helper, bea, zb -> {
			Couples.Partner zap = ModAttachments.PARTNER.get(za);
			Couples.Partner zbp = ModAttachments.PARTNER.get(zb);
			helper.assertTrue(zap != null && zap.id().equals(zb.getUUID()) && zbp != null && zbp.id().equals(za.getUUID()),
				"as two zombie villagers they don't name each other: Ada's " + zap + " (zombie Bea " + zb.getUUID() + "), Bea's " + zbp
					+ " (zombie Ada " + za.getUUID() + ")");
			cure(helper, za, "Ada", ca -> cure(helper, zb, "Bea", cb -> {
				Couples.Partner hers = ModAttachments.PARTNER.get(ca);
				Couples.Partner his = ModAttachments.PARTNER.get(cb);
				helper.assertTrue(hers != null && hers.married() && hers.id().equals(cb.getUUID()) && his != null && his.married()
						&& his.id().equals(ca.getUUID()) && !ModAttachments.WIDOWED_DAY.has(ca) && !ModAttachments.WIDOWED_DAY.has(cb),
					"both cured, Ada and Bea aren't married to each other: Ada's " + hers + " (Bea " + cb.getUUID() + "), Bea's " + his
						+ " (Ada " + ca.getUUID() + "), widowed " + ModAttachments.WIDOWED_DAY.has(ca) + "/" + ModAttachments.WIDOWED_DAY.has(cb));
				helper.succeed();
			}));
		}));
	}

	/** Ada is turned and the zombie villager is killed, not cured: Bea is widowed then, mourning Ada, and free to court. */
	//$ gametest_ticks_batch AREA '200' '"qaConv1109cSlain"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaConv1109cSlain")
	public void aZombieSpouseKilledLeavesTheOtherWidowedMourningThem(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager ada = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(2, 2, 2), "Ada");
		Villager bea = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 2), "Bea");
		QaCuredVillager1109eGameTests.marry(ada, bea, 3);
		turn(helper, ada, zombie -> {
			helper.assertTrue(ModAttachments.PARTNER.has(bea) && !ModAttachments.WIDOWED_DAY.has(bea), "setup: the bite already widowed Bea");
			zombie.kill();
			Couples.LatePartner late = ModAttachments.LATE_PARTNER.get(bea);
			helper.assertTrue(!ModAttachments.PARTNER.has(bea) && ModAttachments.WIDOWED_DAY.has(bea) && late != null && late.name().getString().equals("Ada"),
				"Ada's zombie killed, Bea isn't widowed mourning Ada: partner " + ModAttachments.PARTNER.get(bea) + ", widowed "
					+ ModAttachments.WIDOWED_DAY.get(bea) + ", late partner " + (late == null ? "none" : late.name().getString()));
			helper.assertTrue(single(bea), "widowed Bea can't court again");
			helper.succeed();
		});
	}

	/**
	 * Ada still names Bea, but Bea has married Carl (Ada's record is stale): Ada turned and cured leaves Bea and Carl
	 * married to each other, and Bea never widowed.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaConv1109cStale"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaConv1109cStale")
	public void aVillagerTurnedWhileTheirSpouseHasRemarriedLeavesTheNewCoupleAlone(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager ada = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(2, 2, 2), "Ada");
		Villager bea = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 2), "Bea");
		Villager carl = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 6), "Carl");
		ModAttachments.PARTNER.set(ada, new Couples.Partner(bea.getUUID(), bea.getDisplayName(), 3, true));
		QaCuredVillager1109eGameTests.marry(bea, carl, 20);
		turn(helper, ada, zombie -> cure(helper, zombie, "Ada", cured -> {
			Couples.Partner bp = ModAttachments.PARTNER.get(bea);
			Couples.Partner cp = ModAttachments.PARTNER.get(carl);
			helper.assertTrue(bp != null && bp.married() && bp.id().equals(carl.getUUID()) && cp != null && cp.id().equals(bea.getUUID())
					&& !ModAttachments.WIDOWED_DAY.has(bea),
				"Ada's turn and cure broke up Bea and Carl: Bea's partner " + bp + ", Carl's " + cp + ", Bea widowed " + ModAttachments.WIDOWED_DAY.get(bea));
			helper.succeed();
		}));
	}

	// ---- B1082: brought back by an Undertaker ---------------------------------------------------------------------

	/**
	 * Ada dies, her widow Bea stays alone, the Undertaker brings Ada back: married to each other again, Bea no longer a
	 * widow nor mourning, and neither of them free to court.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRev1109cAlone"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRev1109cAlone")
	public void aRevivedWifeWhoseWidowIsAloneIsMarriedAgainAndNobodyMourns(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.graves(helper);
		ServerLevel level = helper.getLevel();
		Villager ada = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(2, 2, 2), "Ada");
		Villager bea = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 2), "Bea");
		QaCuredVillager1109eGameTests.marry(ada, bea, 3);
		helper.runAfterDelay(2, () -> {
			ada.kill();
			helper.assertTrue(!ModAttachments.PARTNER.has(bea) && ModAttachments.WIDOWED_DAY.has(bea), "setup: Ada's death didn't widow Bea");
			helper.runAfterDelay(30, () -> { // the Undertaker comes later, Ada's body long gone
				Villager back = revive(helper, level, new BlockPos(2, 2, 2));
				assertMarried(helper, back, bea, "revived Ada and her widow Bea");
				helper.assertTrue(!ModAttachments.WIDOWED_DAY.has(bea) && !ModAttachments.LATE_PARTNER.has(bea),
					"married again, Bea is still widowed (day " + ModAttachments.WIDOWED_DAY.get(bea) + ") or mourning Ada");
				helper.assertTrue(!single(back) && !single(bea), "married again, Ada or Bea is free to court: " + single(back) + "/" + single(bea));
				helper.succeed();
			});
		});
	}

	/**
	 * Ada dies, Bea marries Carl, Ada is brought back: Ada is single and may court again; Bea and Carl are still married
	 * to each other and Bea isn't widowed by any of it.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRev1109cRemarried"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRev1109cRemarried")
	public void aRevivedWifeWhoseWidowRemarriedIsSingleCanCourtAndLeavesTheNewCoupleBe(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.graves(helper);
		ServerLevel level = helper.getLevel();
		Villager ada = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(2, 2, 2), "Ada");
		Villager bea = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 2), "Bea");
		Villager carl = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 6), "Carl");
		QaCuredVillager1109eGameTests.marry(ada, bea, 3);
		helper.runAfterDelay(2, () -> {
			ada.kill();
			// Bea's wedding to Carl ends her mourning, as a wedding does
			ModAttachments.WIDOWED_DAY.remove(bea);
			ModAttachments.LATE_PARTNER.remove(bea);
			QaCuredVillager1109eGameTests.marry(bea, carl, 20);
			helper.runAfterDelay(30, () -> {
				Villager back = revive(helper, level, new BlockPos(2, 2, 2));
				helper.assertTrue(!ModAttachments.PARTNER.has(back), "revived Ada still has a partner though Bea married Carl: " + ModAttachments.PARTNER.get(back));
				helper.assertTrue(single(back), "revived Ada, single, can't court again");
				Couples.Partner bp = ModAttachments.PARTNER.get(bea);
				Couples.Partner cp = ModAttachments.PARTNER.get(carl);
				helper.assertTrue(bp != null && bp.married() && bp.id().equals(carl.getUUID()) && bp.since() == 20
						&& cp != null && cp.married() && cp.id().equals(bea.getUUID()) && !ModAttachments.WIDOWED_DAY.has(bea),
					"Ada's revival touched Bea and Carl: Bea's " + bp + ", Carl's " + cp + ", Bea widowed " + ModAttachments.WIDOWED_DAY.get(bea));
				helper.succeed();
			});
		});
	}

	/**
	 * Ada dies; her widow Bea starts courting Dan; Ada is brought back: Ada is single (Bea is someone else's now) and Bea
	 * still courts Dan. And Ada, courting Eli when she died, comes back courting him again when he's alone.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRev1109cCourting"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRev1109cCourting")
	public void aRevivedVillagerCourtsAgainOnlyWhenTheOtherIsStillAlone(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.graves(helper);
		ServerLevel level = helper.getLevel();
		Villager ada = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(1, 2, 1), "Ada");
		Villager bea = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(1, 2, 6), "Bea");
		Villager dan = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(3, 2, 6), "Dan");
		Villager fay = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 1), "Fay");
		Villager eli = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 6), "Eli");
		QaCuredVillager1109eGameTests.marry(ada, bea, 3);
		court(fay, eli, 8);
		helper.runAfterDelay(2, () -> {
			ada.kill();
			fay.kill();
			helper.assertTrue(!ModAttachments.PARTNER.has(bea) && !ModAttachments.PARTNER.has(eli), "setup: the deaths didn't leave Bea and Eli alone");
			ModAttachments.WIDOWED_DAY.remove(bea);
			ModAttachments.LATE_PARTNER.remove(bea);
			court(bea, dan, 10);
			helper.runAfterDelay(30, () -> {
				Villager adaBack = revive(helper, level, new BlockPos(1, 2, 1));
				helper.assertTrue(!ModAttachments.PARTNER.has(adaBack) && single(adaBack),
					"revived Ada, whose widow courts Dan, isn't single: " + ModAttachments.PARTNER.get(adaBack));
				Couples.Partner bp = ModAttachments.PARTNER.get(bea);
				helper.assertTrue(bp != null && !bp.married() && bp.id().equals(dan.getUUID()), "Ada's revival broke Bea's courtship with Dan: " + bp);
				Villager fayBack = revive(helper, level, new BlockPos(6, 2, 1));
				Couples.Partner hers = ModAttachments.PARTNER.get(fayBack);
				Couples.Partner his = ModAttachments.PARTNER.get(eli);
				helper.assertTrue(hers != null && !hers.married() && hers.id().equals(eli.getUUID()) && his != null && !his.married()
						&& his.id().equals(fayBack.getUUID()) && !ModAttachments.WIDOWED_DAY.has(eli),
					"revived Fay and Eli, who was alone, aren't courting each other again: Fay's " + hers + ", Eli's " + his
						+ ", Eli widowed " + ModAttachments.WIDOWED_DAY.get(eli));
				helper.succeed();
			});
		});
	}

	/**
	 * Ada, never married, dies and is brought back: no partner, free to court, and the married neighbors Bea and Carl
	 * aren't touched.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRev1109cUnwed"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRev1109cUnwed")
	public void aRevivedUnmarriedVillagerComesBackWithNoPartner(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.graves(helper);
		ServerLevel level = helper.getLevel();
		Villager ada = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(2, 2, 2), "Ada");
		Villager bea = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 2), "Bea");
		Villager carl = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 6), "Carl");
		QaCuredVillager1109eGameTests.marry(bea, carl, 4);
		helper.runAfterDelay(2, () -> {
			ada.kill();
			helper.runAfterDelay(30, () -> {
				Villager back = revive(helper, level, new BlockPos(2, 2, 2));
				helper.assertTrue(!ModAttachments.PARTNER.has(back) && !ModAttachments.WIDOWED_DAY.has(back) && single(back),
					"revived unmarried Ada has a partner or a widowhood: " + ModAttachments.PARTNER.get(back) + ", " + ModAttachments.WIDOWED_DAY.get(back));
				Couples.Partner bp = ModAttachments.PARTNER.get(bea);
				Couples.Partner cp = ModAttachments.PARTNER.get(carl);
				helper.assertTrue(bp != null && bp.id().equals(carl.getUUID()) && cp != null && cp.id().equals(bea.getUUID()),
					"Ada's revival touched Bea and Carl: " + bp + " / " + cp);
				helper.succeed();
			});
		});
	}

	/**
	 * Ada dies, is brought back (married to Bea again), dies again and is brought back again: married to Bea again each
	 * time, both agreeing. The spent first grave brings nobody back a second time.
	 */
	//$ gametest_ticks_batch AREA '160' '"qaRev1109cTwice"'
	@GameTest(template = AREA, timeoutTicks = 160, batch = "qaRev1109cTwice")
	public void aWifeRevivedTwiceIsMarriedAgainEachTimeAndASpentGraveBringsNobody(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.graves(helper);
		ServerLevel level = helper.getLevel();
		Villager ada = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(2, 2, 2), "Ada");
		Villager bea = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 2), "Bea");
		QaCuredVillager1109eGameTests.marry(ada, bea, 3);
		helper.runAfterDelay(2, () -> {
			ada.kill();
			helper.runAfterDelay(30, () -> {
				BlockPos first = graveNear(helper, level, new BlockPos(2, 2, 2));
				Villager once = Graves.revive(level, first);
				helper.assertTrue(once != null && once.isAlive(), "setup: Ada wasn't brought back the first time");
				assertMarried(helper, once, bea, "revived once, Ada and Bea");
				helper.assertTrue(Graves.revive(level, first) == null, "the spent grave brought someone back a second time");
				helper.assertTrue(level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(2),
					v -> v.isAlive() && v.getCustomName() != null && v.getCustomName().getString().equals("Ada")).size() == 1, "two Adas after reviving a spent grave");
				assertMarried(helper, once, bea, "after the spent grave, Ada and Bea");
				once.setNoAi(true);
				once.kill();
				helper.assertTrue(!ModAttachments.PARTNER.has(bea) && ModAttachments.WIDOWED_DAY.has(bea), "Ada's second death didn't widow Bea");
				helper.runAfterDelay(30, () -> {
					List<BlockPos> graves = Graves.near(level, first, 2);
					helper.assertTrue(graves.size() == 1, "setup: graves where Ada died the second time: " + graves.size());
					Villager twice = reviveAt(helper, level, graves.get(0));
					assertMarried(helper, twice, bea, "revived twice, Ada and Bea");
					helper.assertTrue(!ModAttachments.WIDOWED_DAY.has(bea) && !ModAttachments.LATE_PARTNER.has(bea), "revived twice, Bea still mourns Ada");
					helper.succeed();
				});
			});
		});
	}

	// ---- helpers --------------------------------------------------------------------------------------------------

	/** What of {@link #aCuredVillagerKeepsEverySortOfSavedRecordAndSoDoesTheZombieBetween}'s records {@code e} lacks. */
	private static List<String> lost(Entity e, Families.Parents parents) {
		List<String> lost = new ArrayList<>();
		check(lost, "parents", parents, ModAttachments.PARENTS.get(e));
		check(lost, "gift", "none", ModAttachments.GIFTED.get(e));
		check(lost, "schooled", true, ModAttachments.SCHOOLED.get(e));
		check(lost, "lessons", 4, ModAttachments.LESSONS.get(e));
		check(lost, "retired", new Retirement.Retired(12, "minecraft:farmer", 3), ModAttachments.RETIRED.get(e));
		check(lost, "trees felled", 41, ModAttachments.TREES_FELLED.get(e));
		check(lost, "items crafted", 17, ModAttachments.ITEMS_CRAFTED.get(e));
		check(lost, "adult since", 9L, ModAttachments.ADULT_SINCE.get(e));
		check(lost, "widowed day", 30L, ModAttachments.WIDOWED_DAY.get(e));
		Couples.LatePartner late = ModAttachments.LATE_PARTNER.get(e);
		check(lost, "late partner", "Bram", late == null ? null : late.name().getString());
		check(lost, "class", QaCuredVillager1109eGameTests.BURGHER, ModAttachments.SOCIAL_CLASS.get(e));
		check(lost, "friendship points", 300, points(e));
		return lost;
	}

	/** The {@link #PLAYER}'s friendship points saved on {@code e} (a villager or the zombie villager they became). */
	static int points(Entity e) {
		Friendship.Data data = ModAttachments.FRIENDSHIP.get(e);
		Friendship.Bond bond = data == null ? null : data.players().get(PLAYER);
		return bond == null ? 0 : bond.points();
	}

	private static void check(List<String> lost, String what, Object want, Object got) {
		if (!Objects.equals(want, got)) {
			lost.add(what + " " + want + " -> " + got);
		}
	}

	/** {@code victim} killed by a zombie on Hard (vanilla turns every such villager); {@code then} gets the zombie villager. */
	static void turn(GameTestHelper helper, Villager victim, Consumer<ZombieVillager> then) {
		ServerLevel level = helper.getLevel();
		Difficulty was = level.getDifficulty();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		Leftovers.after(helper, () -> level.getServer().setDifficulty(was, true));
		helper.runAfterDelay(2, () -> {
			java.util.Set<UUID> before = new java.util.HashSet<>();
			level.getEntitiesOfClass(ZombieVillager.class, helper.getBounds().inflate(2), Entity::isAlive).forEach(z -> before.add(z.getUUID()));
			Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
			zombie.setNoAi(true);
			zombie.setCanPickUpLoot(false);
			victim.hurt(level.damageSources().mobAttack(zombie), 1000f);
			zombie.discard();
			List<ZombieVillager> turned = level.getEntitiesOfClass(ZombieVillager.class, helper.getBounds().inflate(2),
				z -> z.isAlive() && !before.contains(z.getUUID()));
			helper.assertTrue(turned.size() == 1, "setup: " + victim.getCustomName().getString() + " didn't become one zombie villager: " + turned.size());
			ZombieVillager zv = turned.get(0);
			zv.setNoAi(true);
			helper.runAfterDelay(1, () -> then.accept(zv)); // past the tick the death's carry finishes in
		});
	}

	/** {@code zv} cured as a golden apple does, the wait cut to a tick; {@code then} gets the villager named {@code name}. */
	static void cure(GameTestHelper helper, ZombieVillager zv, String name, Consumer<Villager> then) {
		ServerLevel level = helper.getLevel();
		try {
			var start = ZombieVillager.class.getDeclaredMethod("startConverting", UUID.class, int.class);
			start.setAccessible(true);
			start.invoke(zv, null, 1);
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("can't start the cure: " + e);
		}
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(zv.isRemoved(), "setup: the zombie villager wasn't cured");
			List<Villager> back = level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(2),
				v -> v.isAlive() && v.hasCustomName() && v.getCustomName().getString().equals(name));
			helper.assertTrue(back.size() == 1, "setup: cured villagers named " + name + ": " + back.size());
			back.get(0).setNoAi(true);
			then.accept(back.get(0));
		});
	}

	static void court(Villager a, Villager b, long day) {
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), day, false));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), day, false));
	}

	/** Whether {@code v} may start courting ({@code Couples.single}, the hall's round's own test). */
	static boolean single(Villager v) {
		try {
			var m = Couples.class.getDeclaredMethod("single", Villager.class);
			m.setAccessible(true);
			return (boolean) m.invoke(null, v);
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("can't ask Couples.single: " + e);
		}
	}

	static BlockPos graveNear(GameTestHelper helper, ServerLevel level, BlockPos rel) {
		List<BlockPos> graves = Graves.near(level, helper.absolutePos(rel), 2);
		helper.assertTrue(graves.size() == 1, "setup: graves by " + rel + ": " + graves.size());
		return graves.get(0);
	}

	/** The grave by {@code rel} revived as an Undertaker does it. */
	static Villager revive(GameTestHelper helper, ServerLevel level, BlockPos rel) {
		return reviveAt(helper, level, graveNear(helper, level, rel));
	}

	/** The grave at the absolute {@code pos} revived as an Undertaker does it. */
	static Villager reviveAt(GameTestHelper helper, ServerLevel level, BlockPos pos) {
		Villager back = Graves.revive(level, pos);
		helper.assertTrue(back != null && back.isAlive(), "setup: nobody was brought back from " + pos);
		back.setNoAi(true);
		return back;
	}

	static void assertMarried(GameTestHelper helper, Villager a, Villager b, String who) {
		Couples.Partner pa = ModAttachments.PARTNER.get(a);
		Couples.Partner pb = ModAttachments.PARTNER.get(b);
		helper.assertTrue(pa != null && pa.married() && pa.id().equals(b.getUUID()) && pb != null && pb.married() && pb.id().equals(a.getUUID()),
			who + " aren't married to each other: " + a.getCustomName().getString() + "'s partner " + pa + " (other is " + b.getUUID() + "), "
				+ b.getCustomName().getString() + "'s " + pb + " (other is " + a.getUUID() + ")");
	}
}
