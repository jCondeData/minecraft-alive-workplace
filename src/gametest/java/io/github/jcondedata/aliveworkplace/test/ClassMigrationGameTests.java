package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.ClassesPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.inn.Traveller;
import io.github.jcondedata.aliveworkplace.people.ClassJobs;
import io.github.jcondedata.aliveworkplace.people.ClassMigration;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.people.ClassPerks;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.FamilyBook;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Retirement;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.ranch.Daycare;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Switching classes on for every village at once (ROADMAP 34.22). Every test stages the same village as a save from
 * before 1.8 (a hall without {@code classesVersion}, villagers without a class, an age or a family name): a Scholar in a
 * plain house, a married couple in a grade II house with their child, a builder mid-build in a cottage, a rancher with a
 * player's Pokémon in daycare, fourteen more villagers without a home, a mercenary and an inn guest, round a hall of
 * City rank with a Market Square, a school and a chapel. Each test runs alone in its batch and puts back what it changed.
 */
public class ClassMigrationGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	/** How far above the test area the recorded houses stand. */
	private static final int SKY = 150;

	/** The staged village. */
	record Village(VillageHallBlockEntity hall, BlockPos hallPos, Villager scholar, Villager wife, Villager husband, Villager child,
				   Villager builder, Villager rancher, List<Villager> others, Villager mercenary, Villager guest, BuildSite site) {
		List<Villager> residents() {
			List<Villager> out = new ArrayList<>(List.of(scholar, wife, husband, child, builder, rancher));
			out.addAll(others);
			return out;
		}

		List<Villager> grown() {
			return residents().stream().filter(v -> !v.isBaby()).toList();
		}
	}

	private static ResourceLocation ours(String path) {
		return AliveWorkplace.id(path);
	}

	private static String cls(Villager v) {
		ResourceLocation c = ModAttachments.SOCIAL_CLASS.get(v);
		return c == null ? "none" : c.getPath();
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static void sleepsAt(ServerLevel level, Villager villager, BlockPos bed) {
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
	}

	/** Fed just now on a varied diet. */
	private static void wellFed(ServerLevel level, Villager v) {
		ModAttachments.LAST_MEAL.set(v, level.getGameTime());
		ModAttachments.RECENT_MEALS.set(v, List.of(ResourceLocation.withDefaultNamespace("bread"), ResourceLocation.withDefaultNamespace("baked_potato"),
			ResourceLocation.withDefaultNamespace("cooked_cod")));
	}

	private static ResourceLocation emptyBlueprint(GameTestHelper helper, String path, Vec3i size) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", path);
		helper.getLevel().getStructureManager().getOrCreate(id).fillFromWorld(helper.getLevel(), sky(helper).above(40), size, false, Blocks.AIR);
		return id;
	}

	private static BlockPos sky(GameTestHelper helper) {
		return helper.absolutePos(new BlockPos(2, 2, 2)).above(SKY);
	}

	private static void finished(GameTestHelper helper, ResourceLocation id, BlockPos origin) {
		BuildSiteManager sites = BuildSiteManager.get(helper.getLevel());
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		sites.recordFinished(id, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
	}

	/** The village as a save from before 1.8 left it; classes on (GameTests have them off), the real class files. */
	private static Village oldVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		SocialClasses.ENABLED = true;
		SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
		SocialClasses.forget();
		ClassNeeds.services = (l, hall, home) -> Set.of(ours("school"), ours("chapel")); // a school and a chapel reach every home
		List<BuildSite> sites = new ArrayList<>();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply(); // classes off again (GameTests), the grace back to 7
			ClassNeeds.services = io.github.jcondedata.aliveworkplace.hall.Services.HOOK;
			ClassNeeds.luxuryEvery = io.github.jcondedata.aliveworkplace.people.Luxuries.HOOK;
			SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
			SocialClasses.forget();
			sites.forEach(s -> BuildSiteManager.get(level).remove(s.id()));
		});

		// The hall, saved before 1.8: none of the new fields.
		BlockPos hallPos = helper.absolutePos(HALL);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(hallPos);
		hall.setRank(VillageRanks.Rank.CITY);
		hall.setNeeds(new VillageNeeds.Needs(20, 20, 20, 20, 20, 0, 0, 1f)); // its own round waits its turn
		CompoundTag old = hall.saveWithFullMetadata(level.registryAccess());
		old.remove("classesVersion");
		old.remove("classGraceUntil");
		old.remove("families");
		hall.loadWithComponents(old, level.registryAccess());
		hall.setRank(VillageRanks.Rank.CITY);

		// The village's builds: a Market Square, a grade II house for the couple, a cottage for the builder.
		finished(helper, StarterBlueprints.MARKET_SQUARE.id(), helper.absolutePos(new BlockPos(20, 2, 20)));
		BlockPos townhouse = sky(helper);
		BlockPos cottage = sky(helper).offset(14, 0, 0);
		finished(helper, emptyBlueprint(helper, "class_migration_house_2", new Vec3i(7, 5, 7)), townhouse);
		finished(helper, emptyBlueprint(helper, "class_migration_cottage", new Vec3i(6, 5, 6)), cottage);

		Villager scholar = villager(helper, new BlockPos(2, 2, 2), "Sela");
		scholar.setVillagerData(scholar.getVillagerData().setProfession(ModVillagers.SCHOLAR).setLevel(3));
		sleepsAt(level, scholar, sky(helper).offset(40, 1, 40)); // a plain house no builder built
		wellFed(level, scholar);

		Villager wife = villager(helper, new BlockPos(4, 2, 2), "Dara");
		Villager husband = villager(helper, new BlockPos(6, 2, 2), "Tomas");
		ModAttachments.PARTNER.set(wife, new Couples.Partner(husband.getUUID(), husband.getDisplayName(), 1, true));
		ModAttachments.PARTNER.set(husband, new Couples.Partner(wife.getUUID(), wife.getDisplayName(), 1, true));
		sleepsAt(level, wife, townhouse.offset(1, 1, 1));
		sleepsAt(level, husband, townhouse.offset(2, 1, 1));
		wellFed(level, wife);
		wellFed(level, husband);
		Villager child = villager(helper, new BlockPos(5, 2, 4), "Mira");
		child.setBaby(true);
		child.setAge(-24000 * 10); // stays a child through the test
		sleepsAt(level, child, townhouse.offset(4, 1, 4));
		ModAttachments.PARENTS.set(child, new Families.Parents(wife.getDisplayName(), husband.getDisplayName(), "", "", false, false, false,
			wife.getUUID(), husband.getUUID()));

		Villager builder = villager(helper, new BlockPos(8, 2, 2), "Bram");
		builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(4));
		sleepsAt(level, builder, cottage.offset(2, 1, 2));
		wellFed(level, builder);
		BuildSite site = BuildSiteManager.get(level).create(UUID.randomUUID(), "Jesse", StarterBlueprints.WELL.id(),
			new BlueprintData.Placement(level.dimension().location(), sky(helper).offset(30, 0, 0), Rotation.NONE, Mirror.NONE));
		sites.add(site);
		ModAttachments.BUILDER_JOB.set(builder, new BuilderJob(site.id()));

		Villager rancher = villager(helper, new BlockPos(10, 2, 2), "Odo");
		rancher.setVillagerData(rancher.getVillagerData().setProfession(ModVillagers.RANCHER).setLevel(2));
		CompoundTag pokemon = new CompoundTag();
		pokemon.putString("Species", "cobblemon:eevee");
		ModAttachments.DAYCARE.set(rancher, List.of(new Daycare.Boarder(UUID.randomUUID(), "Jesse", pokemon, 12L)));

		List<Villager> others = new ArrayList<>();
		for (int i = 0; i < 14; i++) {
			others.add(villager(helper, new BlockPos(2 + 2 * (i % 6), 2, 6 + 2 * (i / 6)), "Filler" + i));
		}
		Villager mercenary = villager(helper, new BlockPos(12, 2, 12), "Sword");
		ModAttachments.MERCENARY_UNTIL.set(mercenary, level.getGameTime() + 100_000);
		Villager guest = villager(helper, new BlockPos(10, 2, 12), "Wanderer");
		ModAttachments.TRAVELLER.set(guest, new Traveller(level.getGameTime(), 3));
		return new Village(hall, hallPos, scholar, wife, husband, child, builder, rancher, others, mercenary, guest, site);
	}

	/** The hall's rounds on {@code today} until its first seeding is done (at most 10). */
	private static int seedAll(ServerLevel level, Village v, long today) {
		int rounds = 0;
		while (v.hall().classesVersion() < ClassMigration.VERSION && rounds < 10) {
			ClassMigration.round(level, v.hallPos(), v.hall(), today);
			rounds++;
		}
		return rounds;
	}

	private static List<Chronicle.Entry> classEntries(VillageHallBlockEntity hall) {
		return hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.CLASS).toList();
	}

	/** Each villager's class, progress, age and family name: what seeding writes. */
	private static Map<UUID, String> snapshot(List<Villager> who) {
		Map<UUID, String> out = new HashMap<>();
		for (Villager v : who) {
			out.put(v.getUUID(), cls(v) + " " + SocialClasses.progress(v) + " " + LifeStages.adultSince(v) + " " + FamilyBook.family(v));
		}
		return out;
	}

	/** A player who keeps every chat line they're shown, standing at {@code at}. */
	private static ServerPlayer listener(GameTestHelper helper, List<Component> seen, Vec3 at) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "class-switch"), false);
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
		player.setGameMode(GameType.SPECTATOR);
		player.teleportTo(at.x, at.y, at.z);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	// 1. Seeding: 16 a round, the rules, a save part-way, the chronicle and chat, and a second round that changes nothing.

	/**
	 * The old village is seeded 16 a round: a reload after the first round loses nothing and re-seeds nobody; the second
	 * round finishes it. The couple and their child are Burghers (a grade II home, fed, a varied diet, school and chapel,
	 * the Market Square: everything but luxuries), the builder in his cottage an Artisan, the Scholar in a plain house and
	 * everyone homeless Peasants; nobody is a Noble. Grown-ups get ages within the last 1000 days (nobody an elder), everyone
	 * a family name (the child its parent's); the mercenary and the inn guest are left alone. One chronicle entry with the
	 * counts and one chat line to the player in the village; a third round changes nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"classMigrationSeed"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "classMigrationSeed")
	public void anOldVillageIsSeededSixteenARound(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = oldVillage(helper);
		List<Component> heard = new ArrayList<>();
		listener(helper, heard, Vec3.atCenterOf(v.hallPos().offset(-3, 0, -3)));
		long today = Chronicle.day(level);
		helper.assertTrue(v.hall().classesVersion() == 0 && v.hall().classGraceUntil() == 0, "a hall saved before 1.8 loads unseeded");
		helper.assertTrue(v.residents().stream().noneMatch(ModAttachments.SOCIAL_CLASS::has), "nobody has a class yet");

		ClassMigration.Round first = ClassMigration.round(level, v.hallPos(), v.hall(), today);
		long seeded = v.residents().stream().filter(ModAttachments.SOCIAL_CLASS::has).count();
		helper.assertTrue(first != null && !first.finished() && (first.seeded() == 16 || first.seeded() == 17) && seeded == first.seeded(),
			"16 a round (a couple is never split): " + first + ", " + seeded + " of 20");
		helper.assertTrue(v.hall().classesVersion() == 0 && classEntries(v.hall()).isEmpty() && heard.isEmpty(), "nothing told until everyone's seeded");

		// A save part-way: the hall and every villager through NBT, then marked as though a dawn had counted those seeded.
		CompoundTag hallTag = v.hall().saveWithFullMetadata(level.registryAccess());
		v.hall().loadWithComponents(hallTag, level.registryAccess());
		helper.assertTrue(v.hall().classesVersion() == 0, "still unseeded after the reload");
		List<Villager> reloaded = new ArrayList<>();
		Map<UUID, Villager> byId = new HashMap<>();
		for (Villager old : v.residents()) {
			CompoundTag tag = old.saveWithoutId(new CompoundTag());
			old.discard();
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(tag);
			level.addFreshEntity(copy);
			reloaded.add(copy);
			byId.put(copy.getUUID(), copy);
		}
		Village r = new Village(v.hall(), v.hallPos(), byId.get(v.scholar().getUUID()), byId.get(v.wife().getUUID()), byId.get(v.husband().getUUID()),
			byId.get(v.child().getUUID()), byId.get(v.builder().getUUID()), byId.get(v.rancher().getUUID()),
			v.others().stream().map(o -> byId.get(o.getUUID())).toList(), v.mercenary(), v.guest(), v.site());
		List<Villager> firstSeeded = reloaded.stream().filter(ModAttachments.SOCIAL_CLASS::has).toList();
		helper.assertTrue(firstSeeded.size() == seeded, "the reload kept who was seeded: " + firstSeeded.size());
		Map<UUID, String> before = snapshot(firstSeeded);
		for (Villager s : firstSeeded) {
			ModAttachments.CLASS_PROGRESS.set(s, new SocialClasses.Progress(0, 0, 1, today)); // seeding again would clear it
		}

		ClassMigration.Round second = ClassMigration.round(level, r.hallPos(), r.hall(), today);
		helper.assertTrue(second != null && second.finished() && second.seeded() == 20 - seeded, "the second round seeds the rest: " + second);
		for (Villager s : firstSeeded) {
			helper.assertTrue(SocialClasses.progress(s).fed() == 1, s.getName().getString() + " was seeded again after the reload");
			helper.assertTrue(snapshot(List.of(s)).get(s.getUUID()).startsWith(before.get(s.getUUID()).split(" ")[0]), "kept their class");
		}
		helper.assertTrue(r.hall().classesVersion() == ClassMigration.VERSION && r.hall().classGraceUntil() == today + 7,
			"version 1, settling in until day " + (today + 7) + ": " + r.hall().classGraceUntil());

		// The classes.
		helper.assertTrue(cls(r.wife()).equals("burgher") && cls(r.husband()).equals("burgher"), "the couple: " + cls(r.wife()) + ", " + cls(r.husband()));
		helper.assertTrue(cls(r.child()).equals("burgher"), "the child takes the household's: " + cls(r.child()));
		helper.assertTrue(cls(r.builder()).equals("artisan"), "the builder in his cottage: " + cls(r.builder()));
		helper.assertTrue(cls(r.scholar()).equals("peasant"), "the Scholar in a plain house: " + cls(r.scholar()));
		helper.assertTrue(cls(r.rancher()).equals("peasant"), "the rancher, unfed and homeless: " + cls(r.rancher()));
		helper.assertTrue(r.others().stream().allMatch(o -> cls(o).equals("peasant")), "the homeless are Peasants");
		helper.assertTrue(r.residents().stream().noneMatch(o -> cls(o).equals("noble")), "Nobles are earned");
		helper.assertTrue(cls(r.mercenary()).equals("none") && cls(r.guest()).equals("none") && LifeStages.adultSince(r.guest()) == null
			&& FamilyBook.family(r.mercenary()) == 0, "the mercenary and the inn guest are skipped");

		// Ages and family names.
		Set<Long> ages = new java.util.HashSet<>();
		for (Villager g : r.grown()) {
			Long since = LifeStages.adultSince(g);
			helper.assertTrue(since != null && since <= today && since > today - ClassMigration.AGE_SPREAD, g.getName().getString() + " grew up on day " + since);
			helper.assertTrue(!LifeStages.isElder(g, today), "nobody is an elder at the switch");
			helper.assertTrue(FamilyBook.family(g) > 0, g.getName().getString() + " has no family name");
			ages.add(since);
		}
		helper.assertTrue(ages.size() >= 10, "ages are spread: " + ages.size() + " different among 19");
		helper.assertTrue(LifeStages.adultSince(r.child()) == null, "a child has no grown-up age");
		helper.assertTrue(FamilyBook.family(r.child()) == FamilyBook.family(r.wife()), "the child takes the first parent's family name");
		helper.assertTrue(ClassMigration.ageDays(r.wife().getUUID()) == ClassMigration.ageDays(r.wife().getUUID()) && today - LifeStages.adultSince(r.wife()) == ClassMigration.ageDays(r.wife().getUUID()),
			"the age comes from the UUID");

		// Told once: the chronicle and the player in the village.
		List<Chronicle.Entry> entries = classEntries(r.hall());
		helper.assertTrue(entries.size() == 1, "one chronicle entry: " + entries.size());
		String line = entries.get(0).text().getString();
		String name = VillageHalls.name(level, r.hallPos()).getString();
		helper.assertTrue(line.equals("The people of " + name + " found their places: 16 peasants, 1 artisan and 3 burghers"), "the chronicle says: " + line);
		List<String> said = heard.stream().map(Component::getString).filter(s -> s.contains("found their places")).toList();
		helper.assertTrue(said.size() == 1 && said.get(0).startsWith("The people of " + name + " found their places: 16 peasants, 1 artisan and 3 burghers. They have 7 days to settle in"),
			"one chat line: " + said);

		// A third round changes nothing.
		Map<UUID, String> after = snapshot(r.residents());
		long chronicle = r.hall().chronicle().size();
		ClassMigration.Round third = ClassMigration.round(level, r.hallPos(), r.hall(), today);
		helper.assertTrue(third == null && snapshot(r.residents()).equals(after) && r.hall().chronicle().size() == chronicle
			&& r.hall().classGraceUntil() == today + 7 && heard.stream().filter(c -> c.getString().contains("found their places")).count() == 1,
			"a second round changed something: " + third);
		helper.succeed();
	}

	// 2. Nothing a player built or earned changes.

	/**
	 * Seeding fires nobody (the Scholar, a Burgher's job, stays a Scholar below his class and keeps working), the hall stays
	 * a City, the day's takings never drop (a Peasant pays what everyone paid before), the builder keeps his site exactly as
	 * it was, and the rancher keeps the player's Pokémon.
	 */
	//$ gametest_ticks_batch AREA '200' '"classMigrationUntouched"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "classMigrationUntouched")
	public void nothingAPlayerBuiltOrEarnedChanges(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = oldVillage(helper);
		long today = Chronicle.day(level);
		List<Villager> workers = List.of(v.scholar(), v.builder(), v.rancher());
		List<Villager> jobless = v.grown().stream().filter(g -> !workers.contains(g)).toList();
		float sharesBefore = ClassPerks.tax(workers, jobless).shares();
		CompoundTag siteBefore = v.site().save();
		List<Daycare.Boarder> boardersBefore = ModAttachments.DAYCARE.get(v.rancher());
		Map<Villager, String> jobsBefore = new HashMap<>();
		for (Villager g : v.residents()) {
			jobsBefore.put(g, g.getVillagerData().getProfession() + " " + g.getVillagerData().getLevel());
		}
		seedAll(level, v, today);
		helper.assertTrue(v.hall().classesVersion() == 1, "seeded");
		for (Villager g : v.residents()) {
			helper.assertTrue(jobsBefore.get(g).equals(g.getVillagerData().getProfession() + " " + g.getVillagerData().getLevel()),
				g.getName().getString() + "'s job changed: " + g.getVillagerData().getProfession());
		}
		helper.assertTrue(v.scholar().getVillagerData().getProfession() == ModVillagers.SCHOLAR && ClassJobs.above(v.scholar()) != null
			&& ClassJobs.above(v.scholar()).id().equals(ours("burgher")), "the Scholar keeps his job, marked below its class");
		helper.assertTrue(v.hall().rank() == VillageRanks.Rank.CITY, "the hall is still a City: " + v.hall().rank());
		float sharesAfter = ClassPerks.tax(workers, jobless).shares();
		helper.assertTrue(sharesAfter >= sharesBefore, "the takings dropped: " + sharesBefore + " to " + sharesAfter);
		helper.assertTrue(ClassPerks.share(v.scholar(), SocialClasses.get(ours("peasant"))) == ClassPerks.share(v.scholar(), null),
			"a Peasant pays what everyone paid before");
		helper.assertTrue(BuildSiteManager.get(level).get(v.site().id()) == v.site() && v.site().save().equals(siteBefore),
			"the build site was touched");
		helper.assertTrue(ModAttachments.BUILDER_JOB.get(v.builder()) != null && ModAttachments.BUILDER_JOB.get(v.builder()).siteId().equals(v.site().id()),
			"the builder left his site");
		helper.assertTrue(boardersBefore.equals(ModAttachments.DAYCARE.get(v.rancher())), "the daycare's boarders changed");
		helper.succeed();
	}

	// 3. Settling in.

	/**
	 * For 7 days after seeding nobody falls (the builder, an Artisan without Work Clothes, stays one and his mood isn't
	 * told he lacks them), nobody retires (an elder farmer with a jobless son) and the Classes page says "Settling in: 7
	 * days left" with each class's luxuries; from day 8 the builder falls after 3 dawns and the elder starts retiring.
	 */
	//$ gametest_ticks_batch AREA '200' '"classMigrationGrace"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "classMigrationGrace")
	public void aWeekToSettleIn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = oldVillage(helper);
		long today = Chronicle.day(level);
		helper.assertTrue(ClassesPage.settlingButton(level, v.hallPos()) == null, "no settling in before the switch");
		seedAll(level, v, today);
		helper.assertTrue(cls(v.builder()).equals("artisan"), "the builder starts an Artisan: " + cls(v.builder()));

		// The hall's page.
		var button = ClassesPage.settlingButton(level, v.hallPos());
		helper.assertTrue(button != null && button.getHoverName().getString().equals("Settling in: 7 days left"), "the clock: " + (button == null ? null : button.getHoverName().getString()));
		List<String> lines = ClassMigration.settlingLines(level, v.hallPos(), today).stream().map(Component::getString).toList();
		helper.assertTrue(lines.contains("Until then nobody falls a class, luxuries aren't needed yet and nobody retires."), "what it means: " + lines);
		helper.assertTrue(lines.contains("Artisan households will need " + ClassesPage.luxuryName(ours("work_clothes")).getString()), "the Artisans' luxury: " + lines);
		helper.assertTrue(lines.stream().anyMatch(l -> l.startsWith("Burgher households will need ")) && lines.stream().anyMatch(l -> l.startsWith("Noble households will need ")),
			"each class's luxuries: " + lines);
		helper.assertTrue(ClassMigration.settlingLines(level, v.hallPos(), today + 6).get(0).getString().equals("Settling in: 1 day left"), "the last day");
		helper.assertTrue(ClassMigration.settlingLines(level, v.hallPos(), today + 7).isEmpty(), "over on day 8");

		// Nobody falls for 7 dawns.
		List<Villager> alone = List.of(v.builder());
		for (long day = today; day < today + 7; day++) {
			wellFed(level, v.builder());
			SocialClasses.check(level, v.hallPos(), alone, day, 8);
			helper.assertTrue(cls(v.builder()).equals("artisan") && SocialClasses.progress(v.builder()).missed() == 0, "day " + day + ": he fell in the grace");
		}
		SocialClasses.Standing standing = ModAttachments.CLASS_STANDING.get(v.builder());
		helper.assertTrue(standing != null && standing.missing() == 0, "Work Clothes counted as lacking in the grace: " + standing);
		// From day 8: three dawns without Work Clothes and he comes down.
		for (long day = today + 7; day < today + 9; day++) {
			wellFed(level, v.builder());
			SocialClasses.check(level, v.hallPos(), alone, day, 8);
		}
		helper.assertTrue(cls(v.builder()).equals("artisan") && ModAttachments.CLASS_STANDING.get(v.builder()).missing() >= 1, "two dawns after the grace: " + cls(v.builder()));
		wellFed(level, v.builder());
		SocialClasses.check(level, v.hallPos(), alone, today + 9, 8);
		helper.assertTrue(cls(v.builder()).equals("peasant"), "the third dawn after the grace he falls: " + cls(v.builder()));

		// Nobody retires while settling in; afterwards they do.
		Villager elder = v.others().get(0);
		Villager son = v.others().get(1);
		elder.setVillagerData(elder.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(3));
		LifeStages.setAdultSince(elder, Chronicle.day(level) - 1380);
		ModAttachments.PARENTS.set(son, new Families.Parents(elder.getDisplayName(), Component.literal("Ilse"), "", "", true, false, false, elder.getUUID(), null));
		List<Villager> grown = v.grown();
		v.hall().setClasses(1, Chronicle.day(level) + 3);
		Retirement.round(level, v.hallPos(), grown);
		helper.assertTrue(grown.stream().noneMatch(g -> ModAttachments.RETIRE_SHADOW.has(g)), "someone started retiring while the village settles in");
		v.hall().setClasses(1, Chronicle.day(level));
		Retirement.round(level, v.hallPos(), grown);
		helper.assertTrue(ModAttachments.RETIRE_SHADOW.has(son), "after the grace the elder's son starts learning his trade");
		helper.succeed();
	}

	// 4. A hall loaded later, and a newcomer.

	/**
	 * A hall first loaded 60 days after the update is seeded the same way with its own grace (until day 67 of its clock)
	 * and ages counted back from then; a villager who arrives afterwards is seeded at the next round, without a second
	 * chronicle entry.
	 */
	//$ gametest_ticks_batch AREA '200' '"classMigrationLater"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "classMigrationLater")
	public void aHallLoadedLaterGetsItsOwnGrace(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = oldVillage(helper);
		// First the hall's own round (as it runs in the world) leaves it alone in GameTests, and seeds once switched on.
		ClassMigration.round(level, v.hallPos(), v.hall());
		helper.assertTrue(v.residents().stream().noneMatch(ModAttachments.SOCIAL_CLASS::has), "the hall's own round seeded in a GameTest");
		ClassMigration.AUTO = true;
		ClassMigration.round(level, v.hallPos(), v.hall());
		long seededNow = v.residents().stream().filter(ModAttachments.SOCIAL_CLASS::has).count();
		helper.assertTrue(seededNow >= 16, "the hall's own round seeds 16: " + seededNow);
		ClassMigration.AUTO = false;
		new WorkplaceConfig().apply();
		SocialClasses.ENABLED = true;
		helper.assertTrue(!ClassMigration.AUTO, "back off in GameTests");
		long later = Chronicle.day(level) + 60;
		for (Villager r : v.residents()) {
			ModAttachments.SOCIAL_CLASS.remove(r); // as if the hall were first loaded 60 days on
			ModAttachments.ADULT_SINCE.remove(r);
		}
		seedAll(level, v, later);
		helper.assertTrue(v.hall().classesVersion() == 1 && v.hall().classGraceUntil() == later + 7, "its own grace: " + v.hall().classGraceUntil());
		for (Villager g : v.grown()) {
			long since = LifeStages.adultSince(g);
			helper.assertTrue(since <= later && since > later - ClassMigration.AGE_SPREAD, "ages counted back from the day it loaded: " + since);
		}
		Villager newcomer = villager(helper, new BlockPos(12, 2, 10), "Newcomer");
		wellFed(level, newcomer);
		ClassMigration.Round r = ClassMigration.round(level, v.hallPos(), v.hall(), later + 2);
		helper.assertTrue(r != null && r.seeded() == 1 && !r.finished() && cls(newcomer).equals("peasant") && LifeStages.adultSince(newcomer) != null
			&& FamilyBook.family(newcomer) > 0, "the newcomer is seeded: " + r + " " + cls(newcomer));
		helper.assertTrue(classEntries(v.hall()).size() == 1 && v.hall().classGraceUntil() == later + 7, "no second entry, the same grace");
		helper.succeed();
	}

	// 5. villageClasses off.

	/**
	 * With {@code villageClasses} off nothing is seeded and nothing settles in; switched on it seeds; off again, the
	 * classes stay saved but nothing reads them (no job gates, no class pay, no settling in), and on again nothing is
	 * seeded twice.
	 */
	//$ gametest_ticks_batch AREA '200' '"classMigrationOff"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "classMigrationOff")
	public void villageClassesOffBringsBackTodaysBehaviour(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = oldVillage(helper);
		long today = Chronicle.day(level);
		SocialClasses.ENABLED = false;
		for (int i = 0; i < 3; i++) {
			helper.assertTrue(ClassMigration.round(level, v.hallPos(), v.hall(), today) == null, "a round seeded with classes off");
		}
		helper.assertTrue(v.hall().classesVersion() == 0 && v.residents().stream().noneMatch(ModAttachments.SOCIAL_CLASS::has)
			&& LifeStages.adultSince(v.wife()) == null, "nothing written with classes off");
		helper.assertTrue(!ClassMigration.quiet(level, v.hallPos(), today), "nothing held back with classes off");
		SocialClasses.ENABLED = true;
		seedAll(level, v, today);
		Map<UUID, String> seeded = snapshot(v.residents());
		SocialClasses.ENABLED = false;
		helper.assertTrue(ClassMigration.daysLeft(level, v.hallPos(), today) == 0 && ClassesPage.settlingButton(level, v.hallPos()) == null
			&& !ClassMigration.quiet(level, v.hallPos(), today), "settling in with classes off");
		helper.assertTrue(ClassJobs.needed(ModVillagers.SCHOLAR) == null && ClassPerks.classOf(v.wife()) == null && ClassesPage.classLine(v.wife()) == null,
			"classes read with classes off");
		helper.assertTrue(snapshot(v.residents()).equals(seeded), "the classes stay saved");
		SocialClasses.ENABLED = true;
		helper.assertTrue(ClassMigration.round(level, v.hallPos(), v.hall(), today + 1) == null && snapshot(v.residents()).equals(seeded)
			&& classEntries(v.hall()).size() == 1, "on again: nothing seeded twice");
		helper.succeed();
	}

	// 6. The operators' command.

	/** {@code /workplace classes status} says how far it got; {@code reseed} sends the hall back to 0 and the rounds seed it again; a player who isn't an operator can't. */
	//$ gametest_ticks_batch AREA '200' '"classMigrationCommand"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "classMigrationCommand")
	public void operatorsCanCheckAndReseed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = oldVillage(helper);
		// The hall's POI is registered at the end of the tick it's placed in, and the command finds the hall by it: wait a
		// tick, with classes off meanwhile so the hall's own round can't seed anyone first.
		SocialClasses.ENABLED = false;
		helper.runAfterDelay(2, () -> {
			SocialClasses.ENABLED = true;
			command(helper, level, v);
		});
	}

	private static void command(GameTestHelper helper, ServerLevel level, Village v) {
		long today = Chronicle.day(level);
		var commands = level.getServer().getCommands();
		String name = VillageHalls.name(level, v.hallPos()).getString();
		List<String> said = new ArrayList<>();
		commands.performPrefixedCommand(source(level, v.hallPos(), said, 2), "workplace classes status");
		helper.assertTrue(said.contains(name + " is finding its places: 0 of 20 villagers have a class so far.") && said.contains("Classes: nobody yet"),
			"status before: " + said);
		ClassMigration.round(level, v.hallPos(), v.hall(), today);
		said.clear();
		commands.performPrefixedCommand(source(level, v.hallPos(), said, 2), "workplace classes status");
		helper.assertTrue(said.stream().anyMatch(s -> s.matches(java.util.regex.Pattern.quote(name) + " is finding its places: 1[67] of 20 villagers have a class so far\\.")),
			"status part-way: " + said);
		seedAll(level, v, today);
		said.clear();
		commands.performPrefixedCommand(source(level, v.hallPos(), said, 2), "workplace classes status");
		helper.assertTrue(said.contains(name + " is settling in: " + (v.hall().classGraceUntil() - Chronicle.day(level)) + " days left.")
			&& said.contains("Classes: 16 peasants, 1 artisan and 3 burghers"), "status settling in: " + said);

		said.clear();
		commands.performPrefixedCommand(source(level, v.hallPos(), said, 0), "workplace classes reseed");
		helper.assertTrue(v.hall().classesVersion() == 1 && ModAttachments.SOCIAL_CLASS.has(v.wife()), "a player who isn't an operator reseeded");
		said.clear();
		commands.performPrefixedCommand(source(level, v.hallPos(), said, 2), "workplace classes reseed");
		helper.assertTrue(said.contains(name + " will find its places again: 20 villagers lost their class and are given one over the next rounds."), "reseed: " + said);
		helper.assertTrue(v.hall().classesVersion() == 0 && v.residents().stream().noneMatch(ModAttachments.SOCIAL_CLASS::has)
			&& v.residents().stream().noneMatch(r -> ModAttachments.CLASS_PROGRESS.has(r)), "reseed clears the classes and the hall's version");
		helper.assertTrue(LifeStages.adultSince(v.wife()) != null && FamilyBook.family(v.wife()) > 0, "ages and family names are kept");
		long since = LifeStages.adultSince(v.wife());
		seedAll(level, v, today + 3);
		helper.assertTrue(cls(v.wife()).equals("burgher") && LifeStages.adultSince(v.wife()) == since && v.hall().classGraceUntil() == today + 10,
			"seeded again with a new grace: " + cls(v.wife()) + " " + v.hall().classGraceUntil());
		helper.succeed();
	}

	private static CommandSourceStack source(ServerLevel level, BlockPos at, List<String> said, int permission) {
		CommandSource out = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				said.add(message.getString());
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		return new CommandSourceStack(out, Vec3.atCenterOf(at.south(2)), Vec2.ZERO, level, permission, "test", Component.literal("test"), level.getServer(), null);
	}

	// 7. Every new sentence.

	/** Every sentence 34.22 adds reads as written. */
	//$ gametest_batch EMPTY_STRUCTURE '"classMigrationTexts"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "classMigrationTexts")
	public void everyNewSentenceReads(GameTestHelper helper) {
		Map<String, String> texts = new java.util.LinkedHashMap<>();
		texts.put(t("chronicle.aliveworkplace.class.seeded", "Thornholm", "14 peasants, 6 artisans and 2 burghers"),
			"The people of Thornholm found their places: 14 peasants, 6 artisans and 2 burghers");
		texts.put(t("message.aliveworkplace.class.seeded", "Thornholm", "14 peasants", 7),
			"The people of Thornholm found their places: 14 peasants. They have 7 days to settle in: the Village Hall's Classes page says what each class will need.");
		texts.put(t("message.aliveworkplace.class.seeded_one", "Thornholm", "14 peasants", 1),
			"The people of Thornholm found their places: 14 peasants. They have a day to settle in: the Village Hall's Classes page says what each class will need.");
		texts.put(t("message.aliveworkplace.class.seeded_no_grace", "Thornholm", "14 peasants", 0), "The people of Thornholm found their places: 14 peasants.");
		texts.put(t("class.aliveworkplace.peasant.count.one", 1), "1 peasant");
		texts.put(t("class.aliveworkplace.peasant.count", 14), "14 peasants");
		texts.put(t("class.aliveworkplace.artisan.count.one", 1), "1 artisan");
		texts.put(t("class.aliveworkplace.artisan.count", 6), "6 artisans");
		texts.put(t("class.aliveworkplace.burgher.count.one", 1), "1 burgher");
		texts.put(t("class.aliveworkplace.burgher.count", 2), "2 burghers");
		texts.put(t("class.aliveworkplace.noble.count.one", 1), "1 noble");
		texts.put(t("class.aliveworkplace.noble.count", 2), "2 nobles");
		texts.put(t("screen.aliveworkplace.classes.settling", 5), "Settling in: 5 days left");
		texts.put(t("screen.aliveworkplace.classes.settling_one"), "Settling in: 1 day left");
		texts.put(t("screen.aliveworkplace.classes.settling_about"), "Until then nobody falls a class, luxuries aren't needed yet and nobody retires.");
		texts.put(t("screen.aliveworkplace.classes.will_need", "Artisan", "Work Clothes"), "Artisan households will need Work Clothes");
		texts.put(t("command.aliveworkplace.classes.no_hall"), "There's no Village Hall here.");
		texts.put(t("command.aliveworkplace.classes.off", "Thornholm"), "Classes are switched off on this server; Thornholm keeps its classes for when they're back on.");
		texts.put(t("command.aliveworkplace.classes.seeding", "Thornholm", 16, 36), "Thornholm is finding its places: 16 of 36 villagers have a class so far.");
		texts.put(t("command.aliveworkplace.classes.settling", "Thornholm", 5), "Thornholm is settling in: 5 days left.");
		texts.put(t("command.aliveworkplace.classes.settled", "Thornholm"), "Thornholm has settled into its classes.");
		texts.put(t("command.aliveworkplace.classes.counts", "14 peasants"), "Classes: 14 peasants");
		texts.put(t("command.aliveworkplace.classes.nobody"), "nobody yet");
		texts.put(t("command.aliveworkplace.classes.newcomers", 2), "2 newcomers will get a class at the hall's next round.");
		texts.put(t("command.aliveworkplace.classes.reseeded", "Thornholm", 22),
			"Thornholm will find its places again: 22 villagers lost their class and are given one over the next rounds.");
		texts.put(t("aliveworkplace.config.classGraceDays"), "Days to Settle In");
		texts.put(t("aliveworkplace.config.classGraceDays.tooltip"),
			"Days a village settles in after classes are switched on there: nobody falls a class, luxuries aren't needed yet and nobody retires.");
		for (Map.Entry<String, String> e : texts.entrySet()) {
			helper.assertTrue(e.getKey().equals(e.getValue()), "reads \"" + e.getKey() + "\", not \"" + e.getValue() + "\"");
		}
		// A data pack's class with no words of its own: "3 Guildmaster".
		SocialClasses.SocialClass custom = new SocialClasses.SocialClass(ResourceLocation.fromNamespaceAndPath("somepack", "guildmaster"), 5,
			Component.literal("Guildmaster"), 1f, java.util.Optional.empty(), List.of(), List.of(), List.of(), List.of());
		helper.assertTrue(ClassMigration.count(custom, 3).getString().equals("3 Guildmaster"), "a pack's class: " + ClassMigration.count(custom, 3).getString());
		WorkplaceConfig config = WorkplaceConfig.parse("{\"classGraceDays\": 3}");
		helper.assertTrue(new WorkplaceConfig().classGraceDays == 7 && config.classGraceDays == 3
			&& WorkplaceConfig.parse("{\"classGraceDays\": 99}").classGraceDays == 30, "classGraceDays: 7 by default, 0 to 30");
		helper.succeed();
	}

	private static String t(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}
}
