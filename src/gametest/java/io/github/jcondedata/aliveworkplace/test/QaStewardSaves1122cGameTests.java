package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.city.Renewals;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardJobs;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.FamilyBook;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA lane C (2026-11-22), B821 from its bug text: the Village Hall's Steward record ({@code steward}), the Steward's desk
 * ({@code steward_desk}) and the Book of Families ({@code families}) load leniently: an entry that can't be read is
 * skipped with a warning and the rest of the record loads. {@link QaStewardSavesOneBadEntry1102BugTests} covers one
 * empty entry in each list or map; these cover several bad entries at once (first, middle and last), an entry whose one
 * field has the wrong type, nested lists (a proposal's jobs), the record saved again and reloaded, the warning, and in the
 * world, what the player meets after such a load: declined rules stay declined, proposal ids carry on, the renewal under
 * way is still known.
 */
public class QaStewardSaves1122cGameTests implements FabricGameTest {
	private static final BlockState HALL = ModBlocks.VILLAGE_HALL.defaultBlockState();

	private static ResourceLocation rl(String path) {
		return ResourceLocation.fromNamespaceAndPath("aliveworkplace", path);
	}

	private static BlueprintData.Placement at(ServerLevel level, BlockPos pos) {
		return new BlueprintData.Placement(level.dimension().location(), pos, Rotation.NONE, Mirror.NONE);
	}

	private static StewardDesk.Proposal proposal(ServerLevel level, BlockPos pos, int id, String rule, List<StewardJobs.Job> jobs) {
		return new StewardDesk.Proposal(id, rl(rule), false, rl("well"), at(level, pos.offset(id, 0, 0)), "homes", "", "steward.aliveworkplace.why.beds",
			List.of(3L, (long) id), 2L, 0, false, jobs, "");
	}

	private static Renewals.Renewal renewal(ServerLevel level, BlockPos pos, int n) {
		return new Renewals.Renewal(rl("scan/old_" + n), at(level, pos.offset(0, 0, n)), rl("cottage"), at(level, pos.offset(4, 0, n)),
			List.of(new Renewals.Occupant(new UUID(7, n), "")), List.of(new Renewals.Occupant(new UUID(8, n), "minecraft:farmer")), new UUID(9, n), n % 2 == 0);
	}

	/** A desk with three proposals (one with two jobs), two declines, every day set and two renewals under way. */
	private static StewardDesk.State desk(ServerLevel level, BlockPos pos) {
		List<StewardJobs.Job> jobs = List.of(new StewardJobs.Job(new UUID(3, 1), ResourceLocation.withDefaultNamespace("farmer"), pos.east()),
			new StewardJobs.Job(new UUID(3, 2), ResourceLocation.withDefaultNamespace("mason"), pos.west()));
		Map<String, Long> declined = new LinkedHashMap<>();
		declined.put(rl("more_beds").toString(), 40L);
		declined.put(rl("steward/wall").toString(), 50L);
		return new StewardDesk.State(List.of(proposal(level, pos, 5, "qa_a", List.of()), proposal(level, pos, 6, "qa_b", jobs),
			proposal(level, pos, 7, "qa_c", List.of())), declined, 7, 8, 12, 3, 4, 6,
			new Renewals.Book(List.of(renewal(level, pos, 1), renewal(level, pos, 2)), 5));
	}

	private static StewardWishes.Wish wish(String rule, int priority) {
		return new StewardWishes.Wish(rl(rule), new StewardRules.Effect(StewardRules.Kind.BUILD, Optional.of(rl("well")), Optional.of("homes"),
			Optional.empty(), Optional.empty()), priority, "steward.aliveworkplace.why.beds", List.of(3L));
	}

	private static StewardWishes.State wishes() {
		Map<String, StewardWishes.Used> used = new LinkedHashMap<>();
		used.put(rl("more_beds").toString(), new StewardWishes.Used(3, 8));
		used.put(rl("qa_b").toString(), new StewardWishes.Used(1, 2));
		return new StewardWishes.State(9, List.of(wish("qa_a", 70), wish("qa_b", 50), wish("qa_c", 30)), used);
	}

	private static FamilyBook.Person person(UUID id, String name, int family, @org.jetbrains.annotations.Nullable UUID mother,
											@org.jetbrains.annotations.Nullable UUID partner, String trade) {
		return new FamilyBook.Person(id, Component.literal(name), family, 2, mother != null, -1, mother, null, partner, trade);
	}

	private static final UUID ANA = new UUID(1, 1), BEN = new UUID(2, 2), CAL = new UUID(3, 3), DEE = new UUID(4, 4), ELI = new UUID(5, 5);

	private static void fillBook(FamilyBook.Book book) {
		book.put(person(ANA, "Ana", 1, null, BEN, "minecraft:farmer"));
		book.put(person(BEN, "Ben", 1, null, ANA, "minecraft:mason"));
		book.put(person(CAL, "Cal", 1, ANA, null, ""));
		book.put(person(DEE, "Dee", 2, null, ELI, "minecraft:cleric"));
		book.put(person(ELI, "Eli", 2, null, DEE, "aliveworkplace:builder"));
	}

	/** A hall's whole save with the three records filled in. */
	private static CompoundTag saved(ServerLevel level, BlockPos pos) {
		VillageHallBlockEntity a = new VillageHallBlockEntity(pos, HALL);
		a.setStewardDesk(desk(level, pos));
		a.setStewardWishes(wishes());
		fillBook(a.families());
		return a.saveWithoutMetadata(level.registryAccess());
	}

	private static CompoundTag copyOf(ListTag list, int i) {
		return ((CompoundTag) list.get(i)).copy();
	}

	/**
	 * Damages each record in {@code tag} in several places at once: an empty entry first, an entry with one field of the
	 * wrong type in the middle, an entry missing a required field last; the same in the maps (a value of the wrong kind).
	 */
	private static void damage(CompoundTag tag) {
		CompoundTag desk = tag.getCompound("steward_desk");
		ListTag proposals = desk.getList("proposals", 10);
		CompoundTag wrongId = copyOf(proposals, 0);
		wrongId.putString("id", "five");
		CompoundTag noPlacement = copyOf(proposals, 2);
		noPlacement.remove("placement");
		// a good proposal's jobs: one job that can't be read beside the two good ones (the proposal itself is kept)
		ListTag jobs = ((CompoundTag) proposals.get(1)).getList("jobs", 10);
		jobs.add(1, new CompoundTag());
		proposals.add(0, new CompoundTag());
		proposals.add(2, wrongId);
		proposals.add(noPlacement);
		CompoundTag declined = desk.getCompound("declined");
		declined.putString(rl("broken").toString(), "soon");
		declined.put(rl("listed").toString(), new ListTag());
		CompoundTag book = desk.getCompound("renewals");
		ListTag active = book.getList("active", 10);
		CompoundTag wrongSite = copyOf(active, 0);
		wrongSite.putString("site", "nowhere");
		active.add(0, new CompoundTag());
		active.add(wrongSite);

		CompoundTag steward = tag.getCompound("steward");
		ListTag wishes = steward.getList("wishes", 10);
		CompoundTag wrongPriority = copyOf(wishes, 1);
		wrongPriority.putString("priority", "high");
		CompoundTag noEffect = copyOf(wishes, 2);
		noEffect.remove("do");
		wishes.add(0, new CompoundTag());
		wishes.add(2, wrongPriority);
		wishes.add(noEffect);
		CompoundTag used = steward.getCompound("used");
		used.put(rl("number").toString(), IntTag.valueOf(3));
		CompoundTag badUse = new CompoundTag();
		badUse.putString("times", "thrice");
		badUse.putLong("last_day", 4);
		used.put(rl("bad_use").toString(), badUse);

		ListTag families = tag.getList("families", 10);
		CompoundTag wrongId2 = copyOf(families, 0);
		wrongId2.putString("id", "ana");
		CompoundTag noName = copyOf(families, 1);
		noName.remove("name");
		families.add(0, new CompoundTag());
		families.add(3, wrongId2);
		families.add(noName);
	}

	private static List<String> lost(VillageHallBlockEntity hall, StewardDesk.State desk, StewardWishes.State wishes) {
		List<String> out = new ArrayList<>();
		if (!hall.stewardDesk().equals(desk)) {
			StewardDesk.State d = hall.stewardDesk();
			out.add("the desk: proposals " + d.proposals().stream().map(p -> p.id() + " (" + p.jobs().size() + " jobs)").toList() + " (want 5, 6 with 2 jobs, 7), declined "
				+ d.declined() + ", told " + d.told() + ", ran " + d.ran() + ", next " + d.next() + ", days " + d.jobsDay() + "/" + d.researchDay() + "/" + d.shopDay()
				+ ", renewals " + d.renewals().active().size() + " of 2 (last day " + d.renewals().lastDay() + ")");
		}
		if (!hall.stewardWishes().equals(wishes)) {
			StewardWishes.State w = hall.stewardWishes();
			out.add("the Steward's record: day " + w.day() + ", wishes " + w.wishes().stream().map(x -> x.rule().getPath()).toList()
				+ " (want qa_a, qa_b, qa_c), used " + w.used().keySet());
		}
		List<String> missing = new ArrayList<>();
		for (UUID id : List.of(ANA, BEN, CAL, DEE, ELI)) {
			if (hall.families().get(id) == null) {
				missing.add(id.toString());
			}
		}
		if (!missing.isEmpty() || hall.families().size() != 5) {
			out.add("the Book of Families: " + hall.families().size() + " of 5 people, missing " + missing);
		} else {
			FamilyBook.Person cal = hall.families().get(CAL);
			FamilyBook.Person dee = hall.families().get(DEE);
			if (!ANA.equals(cal.mother()) || !ELI.equals(dee.partner()) || !cal.name().getString().equals("Cal") || !dee.trade().equals("minecraft:cleric")) {
				out.add("the Book of Families lost a link: Cal's mother " + cal.mother() + ", Dee's partner " + dee.partner());
			}
		}
		return out;
	}

	/**
	 * Several unreadable entries in each list and map of the three records at once (first, middle and last; an empty
	 * entry, one field of the wrong type, a required field missing; in the maps a value of the wrong kind; in a good
	 * proposal one bad job): every good entry and every plain field comes back, in order, exactly as saved; each record
	 * loads as if the bad entries were never there.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaSeveralDamagedEntriesInEachRecordAreSkippedAndTheRestLoads(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		CompoundTag tag = saved(level, pos);
		VillageHallBlockEntity clean = new VillageHallBlockEntity(pos, HALL);
		clean.loadWithComponents(tag.copy(), level.registryAccess());
		List<String> setUp = lost(clean, desk(level, pos), wishes());
		helper.assertTrue(setUp.isEmpty(), "set-up: the clean save doesn't load back as saved: " + setUp);
		CompoundTag hurt = tag.copy();
		damage(hurt);
		VillageHallBlockEntity b = new VillageHallBlockEntity(pos, HALL);
		b.loadWithComponents(hurt, level.registryAccess());
		List<String> lost = lost(b, desk(level, pos), wishes());
		helper.assertTrue(lost.isEmpty(), "several damaged entries cost good ones: " + String.join("; ", lost));
		helper.succeed();
	}

	/**
	 * After loading a damaged hall, the hall is saved again (the next autosave) and loaded again (the next restart): the
	 * good entries are all still there, the bad ones are gone from the file (nothing is kept to fail again), and the
	 * other entries of the hall's save are untouched.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaADamagedHallSavedAgainKeepsItsGoodEntriesAcrossASecondReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		CompoundTag hurt = saved(level, pos);
		damage(hurt);
		VillageHallBlockEntity first = new VillageHallBlockEntity(pos, HALL);
		first.loadWithComponents(hurt, level.registryAccess());
		CompoundTag again = first.saveWithoutMetadata(level.registryAccess());
		helper.assertTrue(again.getCompound("steward_desk").getList("proposals", 10).size() == 3,
			"the save after a damaged load has " + again.getCompound("steward_desk").getList("proposals", 10).size() + " proposals, not the 3 good ones");
		helper.assertTrue(again.getCompound("steward").getList("wishes", 10).size() == 3,
			"the save after a damaged load has " + again.getCompound("steward").getList("wishes", 10).size() + " wishes, not the 3 good ones");
		helper.assertTrue(again.getList("families", 10).size() == 5,
			"the save after a damaged load has " + again.getList("families", 10).size() + " people, not the 5 good ones");
		VillageHallBlockEntity second = new VillageHallBlockEntity(pos, HALL);
		second.loadWithComponents(again, level.registryAccess());
		List<String> lost = lost(second, desk(level, pos), wishes());
		helper.assertTrue(lost.isEmpty(), "saved again and reloaded, the good entries are lost: " + String.join("; ", lost));
		helper.succeed();
	}

	/**
	 * "Skipped with a warning": each unreadable entry dropped from the Steward's records and the Book of Families is
	 * logged (a warning naming what was dropped), so a server owner can see the save was damaged; a clean load warns
	 * nothing.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEachDroppedEntryIsLoggedAsAWarning(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		CompoundTag tag = saved(level, pos);
		List<String> logged = java.util.Collections.synchronizedList(new ArrayList<>());
		String name = "qaStewardSaves1122c";
		var appender = new org.apache.logging.log4j.core.appender.AbstractAppender(name, null, null, true,
			org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
			@Override
			public void append(org.apache.logging.log4j.core.LogEvent event) {
				if (Thread.currentThread() == level.getServer().getRunningThread()
					&& event.getLevel() == org.apache.logging.log4j.Level.WARN) {
					logged.add(event.getMessage().getFormattedMessage());
				}
			}
		};
		var root = ((org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false)).getConfiguration().getRootLogger();
		appender.start();
		root.addAppender(appender, null, null);
		List<String> cleanLoad;
		try {
			new VillageHallBlockEntity(pos, HALL).loadWithComponents(tag.copy(), level.registryAccess());
			cleanLoad = new ArrayList<>(logged);
			logged.clear();
			CompoundTag hurt = tag.copy();
			damage(hurt);
			new VillageHallBlockEntity(pos, HALL).loadWithComponents(hurt, level.registryAccess());
		} finally {
			root.removeAppender(name);
			appender.stop();
		}
		helper.assertTrue(cleanLoad.stream().noneMatch(m -> m.contains("Dropped an entry")), "a clean hall warned: " + cleanLoad);
		Map<String, Integer> want = new LinkedHashMap<>();
		want.put("steward proposal", 3);
		want.put("steward proposal job", 1);
		want.put("steward declined rule", 2);
		want.put("renewal under way", 2);
		want.put("steward wish", 3);
		want.put("steward rule use", 2);
		want.put("family book person", 3);
		List<String> short_ = new ArrayList<>();
		want.forEach((what, n) -> {
			long got = logged.stream().filter(m -> m.contains("saved " + what + " that no longer loads")).count();
			if (got != n) {
				short_.add(what + ": " + got + " of " + n);
			}
		});
		helper.assertTrue(short_.isEmpty(), "dropped entries without their warning (warnings by kind, got of want): " + short_ + "; logged " + logged.size());
		helper.succeed();
	}

	/**
	 * In the world, after the hall loads a damaged desk (bad entries beside the good ones): the rules the player declined
	 * stay declined (the Steward doesn't offer them again), a new proposal takes the saved next id (12), not 1, declining
	 * it keeps it declined across another save and load, and the renewal under way is still the hall's.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaStewardSaves1122cWorld"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaStewardSaves1122cWorld")
	public void qaAfterADamagedLoadDeclinesHoldAndProposalIdsCarryOn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		long day = StewardWishes.day(level);
		level.setBlockAndUpdate(pos, HALL);
		try {
			helper.assertTrue(level.getBlockEntity(pos) instanceof VillageHallBlockEntity, "set-up: no hall entity");
			VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(pos);
			CompoundTag tag = hall.saveWithoutMetadata(level.registryAccess());
			StewardDesk.State d = desk(level, pos);
			Map<String, Long> declined = new LinkedHashMap<>();
			declined.put(rl("more_beds").toString(), day + 40);
			declined.put(rl("steward/wall").toString(), day + 50);
			StewardDesk.State good = new StewardDesk.State(List.of(d.proposals().get(0)), declined, d.told(), d.ran(), 12, d.jobsDay(), d.researchDay(),
				d.shopDay(), new Renewals.Book(List.of(d.renewals().active().get(0)), 5));
			VillageHallBlockEntity scratch = new VillageHallBlockEntity(pos, HALL);
			scratch.setStewardDesk(good);
			CompoundTag deskTag = scratch.saveWithoutMetadata(level.registryAccess()).getCompound("steward_desk");
			deskTag.getList("proposals", 10).add(0, new CompoundTag());
			deskTag.getCompound("declined").put(rl("broken").toString(), StringTag.valueOf("soon"));
			deskTag.getCompound("renewals").getList("active", 10).add(new CompoundTag());
			tag.put("steward_desk", deskTag);
			hall.loadWithComponents(tag, level.registryAccess());

			helper.assertTrue(StewardDesk.offer(level, pos, wish("more_beds", 60), rl("well"), at(level, pos.north(9)), "homes", false).isEmpty(),
				"a rule the player declined is offered again after the damaged load");
			Optional<StewardDesk.Proposal> fresh = StewardDesk.offer(level, pos, wish("qa_fresh", 60), rl("well"), at(level, pos.north(9)), "homes", false);
			helper.assertTrue(fresh.isPresent(), "an undeclined rule isn't offered: " + StewardDesk.of(level, pos));
			helper.assertTrue(fresh.get().id() == 12, "the new proposal is number " + fresh.get().id() + ", not 12 (ids restarted)");
			helper.assertTrue(StewardDesk.of(level, pos).get(5).isPresent(), "the good proposal 5 is gone from the desk");
			List<Renewals.Renewal> renewals = Renewals.active(level, pos);
			helper.assertTrue(renewals.size() == 1 && renewals.get(0).site().equals(new UUID(9, 1)),
				"the renewal under way is forgotten: " + renewals.size() + " renewals");
			helper.assertTrue(StewardDesk.decline(level, pos, null, 12), "declining proposal 12 failed");
			helper.assertTrue(StewardDesk.offer(level, pos, wish("qa_fresh", 60), rl("well"), at(level, pos.north(9)), "homes", false).isEmpty(),
				"a rule declined just now is offered again");

			VillageHallBlockEntity reloaded = new VillageHallBlockEntity(pos, HALL);
			reloaded.loadWithComponents(hall.saveWithoutMetadata(level.registryAccess()), level.registryAccess());
			StewardDesk.State after = reloaded.stewardDesk();
			helper.assertTrue(after.next() == 13, "after another save and load the next id is " + after.next() + ", not 13");
			helper.assertTrue(after.declined().containsKey(rl("qa_fresh").toString()) && after.declined().containsKey(rl("more_beds").toString())
				&& after.declined().containsKey(rl("steward/wall").toString()) && after.declined().size() == 3,
				"after another save and load the declines are " + after.declined());
			helper.assertTrue(after.renewals().active().size() == 1 && after.proposals().size() == 1 && after.proposals().get(0).id() == 5,
				"after another save and load: " + after.proposals().size() + " proposals, " + after.renewals().active().size() + " renewals");
		} finally {
			level.removeBlock(pos, false);
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			if (level.getPoiManager().getType(pos).isPresent()) {
				level.getPoiManager().remove(pos);
			}
			VillageHalls.forgetNearest();
		}
		helper.succeed();
	}

	/**
	 * The Book of Families with a damaged turn list too ({@code familiesTurned}, B1087): one unreadable turn beside a good
	 * one; the good turn still leads the old id to the new one, the people all load, and the noted families stay noted.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaABadTurnInTheBookOfFamiliesKeepsTheGoodTurnAndThePeople(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		VillageHallBlockEntity a = new VillageHallBlockEntity(pos, HALL);
		fillBook(a.families());
		UUID turnedEli = new UUID(55, 55);
		helper.assertTrue(a.families().turn(ELI, turnedEli), "set-up: Eli couldn't be turned");
		CompoundTag tag = a.saveWithoutMetadata(level.registryAccess());
		helper.assertTrue(tag.get("familiesTurned") instanceof ListTag, "set-up: no turn list saved: " + tag.getAllKeys());
		CompoundTag hurt = tag.copy();
		ListTag turns = hurt.getList("familiesTurned", 10);
		CompoundTag badTurn = copyOf(turns, 0);
		badTurn.putString("to", "someone");
		turns.add(0, badTurn);
		turns.add(new CompoundTag());
		hurt.getList("families", 10).add(new CompoundTag());
		VillageHallBlockEntity b = new VillageHallBlockEntity(pos, HALL);
		b.loadWithComponents(hurt, level.registryAccess());
		helper.assertTrue(b.families().size() == 5, b.families().size() + " of 5 people back beside a bad turn");
		helper.assertTrue(turnedEli.equals(b.families().now(ELI)), "the good turn is lost: Eli's id now " + b.families().now(ELI));
		helper.assertTrue(b.families().get(turnedEli) != null && turnedEli.equals(b.families().get(DEE).partner()),
			"Eli's entry or Dee's partner didn't follow the turn");
		helper.succeed();
	}
}
