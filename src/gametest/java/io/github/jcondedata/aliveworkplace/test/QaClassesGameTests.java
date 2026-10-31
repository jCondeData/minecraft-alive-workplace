package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.ClassJobs;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.people.ClassPerks;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (Check tier, lane-c-1024-1553), written from the ROADMAP's words for 34.2 (the class engine) and 34.8 (higher jobs
 * need higher classes) rather than from the code: rises need {@code classRiseDays} dawns <em>running</em>, a couple falls
 * together, children follow a falling household, progress survives a reload mid-rise, a hall round counts 8 households,
 * the config's day counts and their bounds; and for the jobs: the exact gated list (vanilla, Tailor and Vintner open),
 * each class boundary from both sides through the job items, the job a villager already works at, the rendered refusal,
 * a hall too far off, the hall's greyed entry naming the class, and nobody fired
 * when they fall.
 */
public class QaClassesGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	private static final int SKY = 140;

	private static ResourceLocation ours(String path) {
		return AliveWorkplace.id(path);
	}

	/** A hall at {@link #HALL} (reach 16), classes on with our shipped files; everything shared put back afterwards. */
	private static VillageHallBlockEntity village(GameTestHelper helper, boolean hall) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		SocialClasses.ENABLED = true;
		SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
		ClassPerks.forget();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
			ClassNeeds.services = io.github.jcondedata.aliveworkplace.hall.Services.HOOK;
			ClassNeeds.luxuryEvery = io.github.jcondedata.aliveworkplace.people.Luxuries.HOOK;
			SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
			SocialClasses.forget();
			ClassPerks.forget();
		});
		if (!hall) {
			return null;
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
		entity.setRank(VillageRanks.Rank.HAMLET);
		return entity;
	}

	/** For {@link QaClassesBugTests}: the same village and villagers. */
	static void setUpForBugs(GameTestHelper helper) {
		village(helper, true);
	}

	static Villager spawn(GameTestHelper helper, BlockPos pos, String name, String cls) {
		return villager(helper, pos, name, cls);
	}

	static String jobIdOf(Villager v) {
		return jobId(v);
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name, String cls) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		if (cls != null) {
			SocialClasses.seed(v, cls(cls));
		}
		return v;
	}

	private static SocialClasses.SocialClass cls(String path) {
		return SocialClasses.get(ours(path));
	}

	private static String id(Villager v) {
		ResourceLocation c = ModAttachments.SOCIAL_CLASS.get(v);
		return c == null ? "none" : c.getPath();
	}

	private static int tier(Villager v) {
		SocialClasses.SocialClass c = SocialClasses.of(v);
		return c == null ? -1 : c.tier();
	}

	private static void marry(Villager a, Villager b) {
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), 1, true));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), 1, true));
	}

	private static void eats(Villager villager, String... meals) {
		ModAttachments.RECENT_MEALS.set(villager, Arrays.stream(meals).map(ResourceLocation::withDefaultNamespace).toList());
	}

	private static void varied(Villager v) {
		eats(v, "bread", "baked_potato", "cooked_cod");
	}

	private static void breadOnly(Villager v) {
		eats(v, "bread", "bread", "bread");
	}

	/** A ladder of four classes for the dawn tests, each with the one need given. */
	private static void ladder(String artisanNeed, String burgherNeed, String nobleNeed) {
		Map<ResourceLocation, JsonElement> files = new HashMap<>();
		files.put(ours("peasant"), JsonParser.parseString("{\"tier\": 0, \"tax\": 1}"));
		files.put(ours("artisan"), JsonParser.parseString("{\"tier\": 1, \"tax\": 1.5, \"needs\": [" + artisanNeed + "]}"));
		files.put(ours("burgher"), JsonParser.parseString("{\"tier\": 2, \"tax\": 2.5, \"needs\": [" + burgherNeed + "]}"));
		files.put(ours("noble"), JsonParser.parseString("{\"tier\": 3, \"tax\": 4, \"needs\": [" + nobleNeed + "]}"));
		SocialClasses.load(files);
	}

	private static final String VARIED = "{\"type\": \"diet\", \"kind\": \"varied\"}";
	private static final String PLAIN = "{\"type\": \"diet\", \"kind\": \"plain\"}";
	private static final String CITY = "{\"type\": \"village_rank\", \"rank\": \"city\"}";

	private static SocialClasses.Result dawn(GameTestHelper helper, List<Villager> who, long day) {
		return SocialClasses.check(helper.getLevel(), helper.absolutePos(HALL), who, day, 8);
	}

	// ---- 34.2: the class engine ----

	/** "Met classRiseDays (2) dawns running": met, missed, met is not two running; a fourth dawn met makes two. */
	//$ gametest_batch AREA '"qaClassRiseRunning"'
	@GameTest(template = AREA, batch = "qaClassRiseRunning")
	public void aMissedDawnBetweenTwoGoodOnesIsNotTwoRunning(GameTestHelper helper) {
		village(helper, true);
		ladder(VARIED, CITY, CITY);
		Villager v = villager(helper, new BlockPos(4, 2, 4), "Ada", "peasant");
		varied(v);
		dawn(helper, List.of(v), 1);
		helper.assertTrue(id(v).equals("peasant"), "rose after one dawn: " + id(v));
		breadOnly(v);
		dawn(helper, List.of(v), 2);
		varied(v);
		dawn(helper, List.of(v), 3);
		helper.assertTrue(id(v).equals("peasant"), "met, missed, met counted as 2 running: " + id(v) + " " + SocialClasses.progress(v));
		dawn(helper, List.of(v), 4);
		helper.assertTrue(id(v).equals("artisan"), "dawns 3 and 4 met running, still " + id(v) + " " + SocialClasses.progress(v));
		v.discard();
		helper.succeed();
	}

	/**
	 * One step a day at the default 2 rise days: a Peasant household meeting every class's needs is an Artisan after 2
	 * dawns (never a Burgher that day), and never climbs two classes in one dawn on the way up.
	 */
	//$ gametest_batch AREA '"qaClassOneStepDefault"'
	@GameTest(template = AREA, batch = "qaClassOneStepDefault")
	public void meetingTheBurghersNeedsAsAPeasantRisesOnlyToArtisan(GameTestHelper helper) {
		village(helper, true);
		ladder(PLAIN, PLAIN, PLAIN);
		Villager v = villager(helper, new BlockPos(4, 2, 4), "Bo", "peasant");
		varied(v);
		List<Integer> tiers = new ArrayList<>();
		for (long day = 1; day <= 10; day++) {
			dawn(helper, List.of(v), day);
			tiers.add(tier(v));
		}
		helper.assertTrue(tiers.get(0) == 0 && tiers.get(1) == 1, "after 2 dawns a Peasant meeting every need is an Artisan only: " + tiers);
		for (int i = 1; i < tiers.size(); i++) {
			helper.assertTrue(tiers.get(i) - tiers.get(i - 1) <= 1 && tiers.get(i) >= tiers.get(i - 1), "more than one step in a dawn: " + tiers);
		}
		helper.assertTrue(tiers.get(tiers.size() - 1) == 3, "never reached Noble in 10 dawns with every need met: " + tiers);
		v.discard();
		helper.succeed();
	}

	/** A couple where one stops meeting their own class's need falls together after 3 dawns; a single beside them keeps theirs. */
	//$ gametest_batch AREA '"qaClassCoupleFalls"'
	@GameTest(template = AREA, batch = "qaClassCoupleFalls")
	public void aCoupleFallsTogetherWhenOneLacksANeed(GameTestHelper helper) {
		village(helper, true);
		ladder(VARIED, CITY, CITY);
		Villager a = villager(helper, new BlockPos(4, 2, 4), "Cai", "artisan");
		Villager b = villager(helper, new BlockPos(6, 2, 4), "Dee", "artisan");
		Villager single = villager(helper, new BlockPos(10, 2, 4), "Eli", "artisan");
		marry(a, b);
		varied(a);
		breadOnly(b);
		varied(single);
		List<Villager> all = List.of(a, b, single);
		dawn(helper, all, 1);
		dawn(helper, all, 2);
		helper.assertTrue(id(a).equals("artisan") && id(b).equals("artisan"), "fell before 3 dawns: " + id(a) + " " + id(b));
		dawn(helper, all, 3);
		helper.assertTrue(id(a).equals("peasant") && id(b).equals("peasant"),
			"a need fails for the couple when it fails for one: after 3 dawns both should be Peasants: " + id(a) + ", " + id(b));
		helper.assertTrue(id(single).equals("artisan"), "the single who met the need fell: " + id(single));
		all.forEach(Villager::discard);
		helper.succeed();
	}

	/** A widow (married to a villager who's gone) is a household of one and still rises on their own needs. */
	//$ gametest_batch AREA '"qaClassWidow"'
	@GameTest(template = AREA, batch = "qaClassWidow")
	public void aWidowRisesOnTheirOwn(GameTestHelper helper) {
		village(helper, true);
		ladder(VARIED, CITY, CITY);
		Villager v = villager(helper, new BlockPos(4, 2, 4), "Fay", "peasant");
		ModAttachments.PARTNER.set(v, new Couples.Partner(UUID.randomUUID(), Component.literal("Gone"), 1, true));
		varied(v);
		dawn(helper, List.of(v), 1);
		dawn(helper, List.of(v), 2);
		helper.assertTrue(id(v).equals("artisan"), "a widow never rises: " + id(v) + " " + SocialClasses.progress(v));
		v.discard();
		helper.succeed();
	}

	/** Children follow their household down too: when the couple sleeping in their building falls, so does the child. */
	//$ gametest_batch AREA '"qaClassChildFalls"'
	@GameTest(template = AREA, batch = "qaClassChildFalls")
	public void aChildFallsWithTheirHousehold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper, true);
		ladder(VARIED, CITY, CITY);
		ResourceLocation house = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_class_family_house");
		BlockPos home = helper.absolutePos(new BlockPos(2, 2, 2)).above(SKY);
		level.getStructureManager().getOrCreate(house).fillFromWorld(level, home.above(40), new Vec3i(6, 5, 6), false, Blocks.AIR);
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), home, Rotation.NONE, Mirror.NONE);
		sites.recordFinished(house, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		Villager mother = villager(helper, new BlockPos(4, 2, 4), "Gia", "artisan");
		Villager father = villager(helper, new BlockPos(6, 2, 4), "Hob", "artisan");
		Villager child = villager(helper, new BlockPos(5, 2, 6), "Ivo", null);
		child.setBaby(true);
		marry(mother, father);
		Beds.home(mother, home.offset(1, 1, 1));
		Beds.home(father, home.offset(2, 1, 1));
		Beds.home(child, home.offset(4, 1, 4));
		varied(mother);
		varied(father);
		List<Villager> all = List.of(mother, father, child);
		dawn(helper, all, 1);
		helper.assertTrue(id(child).equals("artisan"), "the child didn't take the household's class: " + id(child));
		breadOnly(father);
		for (long day = 2; day <= 4; day++) {
			dawn(helper, all, day);
		}
		helper.assertTrue(id(mother).equals("peasant") && id(father).equals("peasant"), "the household didn't fall: " + id(mother) + " " + id(father));
		dawn(helper, all, 5);
		helper.assertTrue(id(child).equals("peasant"), "the child kept the class their household lost: " + id(child));
		all.forEach(Villager::discard);
		helper.succeed();
	}

	/** A save and reload between the two dawns of a rise keeps the first: the dawn after the reload lifts them. */
	//$ gametest_batch AREA '"qaClassReloadMidRise"'
	@GameTest(template = AREA, batch = "qaClassReloadMidRise")
	public void aReloadMidRiseKeepsTheDawnAlreadyMet(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper, true);
		ladder(VARIED, CITY, CITY);
		Villager v = villager(helper, new BlockPos(4, 2, 4), "Jo", "peasant");
		varied(v);
		dawn(helper, List.of(v), 1);
		helper.assertTrue(SocialClasses.progress(v).met() == 1, "the first dawn wasn't counted: " + SocialClasses.progress(v));
		CompoundTag tag = v.saveWithoutId(new CompoundTag());
		v.discard();
		Villager back = EntityType.VILLAGER.create(level);
		back.load(tag);
		helper.assertTrue(level.addFreshEntity(back), "the reloaded villager didn't go back into the world");
		back.setNoAi(true);
		helper.assertTrue(id(back).equals("peasant") && SocialClasses.progress(back).met() == 1, "lost on reload: " + id(back) + " " + SocialClasses.progress(back));
		dawn(helper, List.of(back), 1);
		helper.assertTrue(SocialClasses.progress(back).met() == 1, "the same dawn counted again after the reload: " + SocialClasses.progress(back));
		dawn(helper, List.of(back), 2);
		helper.assertTrue(id(back).equals("artisan"), "the dawn after the reload didn't lift them: " + id(back) + " " + SocialClasses.progress(back));
		back.discard();
		helper.succeed();
	}

	/** The hall's real round counts 8 households a round: 20 singles take a finding round, then 8, 8 and 4, then nothing. */
	//$ gametest_batch AREA '"qaClassSlices"'
	@GameTest(template = AREA, batch = "qaClassSlices")
	public void aHallRoundCountsEightHouseholds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = village(helper, true);
		ladder(VARIED, CITY, CITY);
		List<Villager> people = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			// (3, 3) not (2, 2): the corner villager stood 17 blocks diagonal from the 16-block hall, outside the village (B549)
			Villager v = villager(helper, new BlockPos(3 + (i % 5) * 2, 2, 3 + (i / 5) * 2), "S" + i, "peasant");
			varied(v);
			people.add(v);
		}
		BlockPos at = helper.absolutePos(HALL);
		List<Integer> counted = new ArrayList<>();
		for (int round = 0; round < 10; round++) {
			SocialClasses.Result r = SocialClasses.round(level, at, hall, 501);
			if (r == null) {
				break;
			}
			counted.add(r.counted());
		}
		helper.assertTrue(counted.equals(List.of(0, 8, 8, 4)), "households counted each hall round: " + counted);
		long met = people.stream().filter(v -> SocialClasses.progress(v).met() == 1).count();
		helper.assertTrue(met == 20, "not everyone counted once on the day: " + met);
		people.forEach(Villager::discard);
		helper.succeed();
	}

	/**
	 * Never below Peasant, even when a data pack gives the floor class a need its household lacks: they stay Peasants,
	 * and with nowhere to fall there are no missed dawns to count.
	 */
	//$ gametest_batch AREA '"qaClassFloorNeed"'
	@GameTest(template = AREA, batch = "qaClassFloorNeed")
	public void theFloorClassNeverCountsMissedDawns(GameTestHelper helper) {
		village(helper, true);
		Map<ResourceLocation, JsonElement> files = new HashMap<>();
		files.put(ours("peasant"), JsonParser.parseString("{\"tier\": 0, \"tax\": 1, \"needs\": [" + VARIED + "]}"));
		files.put(ours("artisan"), JsonParser.parseString("{\"tier\": 1, \"tax\": 1.5, \"needs\": [" + CITY + "]}"));
		SocialClasses.load(files);
		Villager v = villager(helper, new BlockPos(4, 2, 4), "Mo", "peasant");
		breadOnly(v);
		for (long day = 1; day <= 5; day++) {
			dawn(helper, List.of(v), day);
		}
		helper.assertTrue(id(v).equals("peasant"), "fell below the floor: " + id(v));
		helper.assertTrue(SocialClasses.progress(v).missed() == 0, "a Peasant counted missed dawns with nowhere to fall: " + SocialClasses.progress(v));
		v.discard();
		helper.succeed();
	}

	/** The config's day counts are read: {@code classFallDays} 1 drops on the first bad dawn; 0 days is not "rise at once". */
	//$ gametest_batch AREA '"qaClassConfigDays"'
	@GameTest(template = AREA, batch = "qaClassConfigDays")
	public void theConfigsDayCountsAreReadAndBounded(GameTestHelper helper) {
		village(helper, true);
		WorkplaceConfig.parse("{\"classFallDays\": 1}").apply();
		SocialClasses.ENABLED = true;
		ladder(VARIED, CITY, CITY);
		Villager v = villager(helper, new BlockPos(4, 2, 4), "Kai", "artisan");
		breadOnly(v);
		dawn(helper, List.of(v), 1);
		helper.assertTrue(id(v).equals("peasant"), "classFallDays 1 didn't drop them on the first bad dawn: " + id(v));

		WorkplaceConfig.parse("{\"classRiseDays\": 0, \"classFallDays\": 0}").apply();
		SocialClasses.ENABLED = true;
		helper.assertTrue(SocialClasses.RISE_DAYS >= 1 && SocialClasses.FALL_DAYS >= 1, "0 days taken as is: " + SocialClasses.RISE_DAYS + " " + SocialClasses.FALL_DAYS);
		Villager w = villager(helper, new BlockPos(8, 2, 4), "Lu", "peasant");
		breadOnly(w);
		dawn(helper, List.of(w), 1);
		helper.assertTrue(id(w).equals("peasant"), "rose without meeting the Artisan's need: " + id(w));
		v.discard();
		w.discard();
		helper.succeed();
	}

	// ---- 34.8: higher jobs need higher classes ----

	private static String key(Component c) {
		if (c.getContents() instanceof TranslatableContents t) {
			return t.getKey();
		}
		return c.getSiblings().isEmpty() ? "" : key(c.getSiblings().get(0));
	}

	private static TranslatableContents translatable(Component c) {
		if (c.getContents() instanceof TranslatableContents t) {
			return t;
		}
		for (Component s : c.getSiblings()) {
			TranslatableContents t = translatable(s);
			if (t != null) {
				return t;
			}
		}
		return null;
	}

	private static ServerPlayer listener(GameTestHelper helper, List<Component> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "qa-class-listener"), false);
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

	/** The villager stands in the hall's village (so a pass isn't just a villager outside its reach). */
	private static void inVillage(GameTestHelper helper, Villager v) {
		helper.assertTrue(VillageHalls.nearest(helper.getLevel(), v.blockPosition()).isPresent(), v.getName().getString() + " stands outside the hall's village");
	}

	private static VillagerProfession job(Villager v) {
		return v.getVillagerData().getProfession();
	}

	private static String jobId(Villager v) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(job(v)).toString();
	}

	/** Every registered job against the spec's list: the Artisan's 12, the Burgher's 4, everyone else (vanilla, Tailor, Vintner) open. */
	//$ gametest_batch AREA '"qaClassJobList"'
	@GameTest(template = AREA, batch = "qaClassJobList")
	public void theGatedJobsAreExactlyTheSpecsList(GameTestHelper helper) {
		village(helper, false);
		Set<String> artisan = Set.of("tinkerer", "chef", "netherworker", "nurse", "teacher", "shopkeeper", "innkeeper", "printer",
			"ball_smith", "tutor", "pokemon_trader", "fossil_scientist");
		Set<String> burgher = Set.of("scholar", "undertaker", "jeweller", "trainer_leader");
		for (String j : artisan) {
			helper.assertTrue(ClassJobs.needed(ours(j)) == cls("artisan"), j + " needs " + ClassJobs.needed(ours(j)));
		}
		for (String j : burgher) {
			helper.assertTrue(ClassJobs.needed(ours(j)) == cls("burgher"), j + " needs " + ClassJobs.needed(ours(j)));
		}
		List<String> wrong = new ArrayList<>();
		int open = 0;
		for (VillagerProfession p : BuiltInRegistries.VILLAGER_PROFESSION) {
			ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(p);
			boolean gated = key.getNamespace().equals(AliveWorkplace.MOD_ID) && (artisan.contains(key.getPath()) || burgher.contains(key.getPath()));
			if (!gated && ClassJobs.needed(p) != null) {
				wrong.add(key + " needs " + ClassJobs.needed(p).id());
			}
			if (!gated) {
				open++;
			}
		}
		helper.assertTrue(wrong.isEmpty(), "jobs gated that the spec leaves open: " + wrong);
		helper.assertTrue(ClassJobs.needed(ModVillagers.TAILOR) == null && ClassJobs.needed(ModVillagers.VINTNER) == null
			&& ClassJobs.needed(VillagerProfession.LIBRARIAN) == null && ClassJobs.needed(VillagerProfession.CLERIC) == null,
			"Tailor, Vintner or a vanilla job gated");
		AliveWorkplace.LOG.info("[test] qa classes: {} open jobs checked, {} gated", open, artisan.size() + burgher.size());
		helper.succeed();
	}

	/**
	 * The refusal a player reads, rendered from en_us.json with its real arguments: who, their class, the job and the class
	 * it needs, with nothing left untranslated.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaClassRefusalText"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaClassRefusalText")
	public void theRefusalReadsWithItsRealWords(GameTestHelper helper) {
		village(helper, true);
		helper.setBlock(new BlockPos(4, 2, 4), Blocks.LECTERN);
		helper.runAfterDelay(2, () -> {
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = listener(helper, seen);
			Villager dara = villager(helper, new BlockPos(4, 2, 5), "Dara", "peasant");
			inVillage(helper, dara);
			Stations.choose(player, dara, new ItemStack(Items.PAPER));
			Component said = seen.stream().filter(c -> key(c).equals("message.aliveworkplace.job.class_needed")).findFirst().orElse(null);
			helper.assertTrue(said != null, "no refusal: " + seen.stream().map(Component::getString).toList());
			String text = said.getString();
			AliveWorkplace.LOG.info("[test] qa classes: the refusal reads \"{}\"", text);
			helper.assertTrue(text.equals("Dara is Peasant class; Scholar needs Burgher or better"), "the refusal reads: " + text);
			helper.assertTrue(job(dara) == VillagerProfession.NONE, "refused but got the job: " + jobId(dara));
			helper.succeed();
		});
	}

	/**
	 * The Artisan boundary from both sides through the job item (a book by a lectern: the Teacher): a Peasant is refused,
	 * an Artisan, a Burgher and a Noble each take it.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaClassArtisanBoundary"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaClassArtisanBoundary")
	public void anArtisanJobIsOpenToArtisansAndUpOnly(GameTestHelper helper) {
		village(helper, true);
		BlockPos[] lecterns = {new BlockPos(4, 2, 4), new BlockPos(10, 2, 4), new BlockPos(4, 2, 10), new BlockPos(10, 2, 10)};
		for (BlockPos p : lecterns) {
			helper.setBlock(p, Blocks.LECTERN);
		}
		helper.runAfterDelay(2, () -> {
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			Villager peasant = villager(helper, lecterns[0].south(), "Pea", "peasant");
			inVillage(helper, peasant);
			Stations.choose(player, peasant, new ItemStack(Items.BOOK));
			helper.assertTrue(job(peasant) == VillagerProfession.NONE, "a Peasant took the Teacher's book: " + jobId(peasant));
			peasant.discard();
			String[] classes = {"artisan", "burgher", "noble"};
			for (int i = 0; i < classes.length; i++) {
				Villager v = villager(helper, lecterns[i + 1].south(), classes[i], classes[i]);
				inVillage(helper, v);
				Stations.choose(player, v, new ItemStack(Items.BOOK));
				helper.assertTrue(job(v) == ModVillagers.TEACHER, "a " + classes[i] + " can't be a Teacher: " + jobId(v));
			}
			helper.succeed();
		});
	}

	/** The Burgher boundary: an Artisan is refused the Undertaker's golden apple and the Jeweller's nugget; a Burgher takes each. */
	//$ gametest_ticks_batch AREA '60' '"qaClassBurgherBoundary"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaClassBurgherBoundary")
	public void aBurgherJobRefusesAnArtisan(GameTestHelper helper) {
		village(helper, true);
		BlockPos stand = new BlockPos(4, 2, 4);
		BlockPos cutter = new BlockPos(10, 2, 4);
		helper.setBlock(stand, Blocks.BREWING_STAND);
		helper.setBlock(cutter, Blocks.STONECUTTER);
		helper.runAfterDelay(2, () -> {
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			check(helper, player, stand, new ItemStack(Items.GOLDEN_APPLE), ModVillagers.UNDERTAKER);
			check(helper, player, cutter, new ItemStack(Items.GOLD_NUGGET), ModVillagers.JEWELLER);
			helper.succeed();
		});
	}

	private static void check(GameTestHelper helper, ServerPlayer player, BlockPos station, ItemStack item, VillagerProfession wanted) {
		Villager artisan = villager(helper, station.south(), "Art", "artisan");
		inVillage(helper, artisan);
		Stations.choose(player, artisan, item.copy());
		helper.assertTrue(job(artisan) == VillagerProfession.NONE, "an Artisan became " + jobId(artisan) + " with " + item);
		artisan.discard();
		Villager burgher = villager(helper, station.south(), "Bur", "burgher");
		Stations.choose(player, burgher, item.copy());
		helper.assertTrue(job(burgher) == wanted, "a Burgher with " + item + " became " + jobId(burgher));
	}

	/**
	 * Open to Peasants: the Tailor (string at a loom) and the Vintner (berries at a cauldron), and the vanilla job at a gated
	 * job's block (a compass at the cartography table), while the Netherworker's netherrack there is refused.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaClassPeasantOpen"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaClassPeasantOpen")
	public void peasantsMayBeTailorsVintnersAndVanillaJobs(GameTestHelper helper) {
		village(helper, true);
		BlockPos loom = new BlockPos(4, 2, 4);
		BlockPos cauldron = new BlockPos(10, 2, 4);
		BlockPos table = new BlockPos(4, 2, 10);
		helper.setBlock(loom, Blocks.LOOM);
		helper.setBlock(cauldron, Blocks.CAULDRON);
		helper.setBlock(table, Blocks.CARTOGRAPHY_TABLE);
		helper.runAfterDelay(2, () -> {
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			Villager tailor = villager(helper, loom.south(), "Tai", "peasant");
			inVillage(helper, tailor);
			Stations.choose(player, tailor, new ItemStack(Items.STRING));
			helper.assertTrue(job(tailor) == ModVillagers.TAILOR, "a Peasant can't be a Tailor: " + jobId(tailor));
			Villager vintner = villager(helper, cauldron.south(), "Vin", "peasant");
			inVillage(helper, vintner);
			Stations.choose(player, vintner, new ItemStack(Items.SWEET_BERRIES));
			helper.assertTrue(job(vintner) == ModVillagers.VINTNER, "a Peasant can't be a Vintner: " + jobId(vintner));
			Villager carto = villager(helper, table.south(), "Car", "peasant");
			inVillage(helper, carto);
			Stations.choose(player, carto, new ItemStack(Items.NETHERRACK));
			helper.assertTrue(job(carto) == VillagerProfession.NONE, "a Peasant became a Netherworker: " + jobId(carto));
			Stations.choose(player, carto, new ItemStack(Items.COMPASS));
			helper.assertTrue(job(carto) == VillagerProfession.CARTOGRAPHER, "a Peasant can't be a Cartographer: " + jobId(carto));
			helper.succeed();
		});
	}

	/** A Peasant Librarian handed paper at the lectern they already work at is refused the Scholar's job and stays a Librarian there. */
	//$ gametest_ticks_batch AREA '60' '"qaClassOwnBlock"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaClassOwnBlock")
	public void theGateHoldsAtTheBlockTheyAlreadyWorkAt(GameTestHelper helper) {
		village(helper, true);
		BlockPos lectern = new BlockPos(4, 2, 4);
		helper.setBlock(lectern, Blocks.LECTERN);
		helper.runAfterDelay(2, () -> {
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			Villager v = villager(helper, lectern.south(), "Lib", "peasant");
			inVillage(helper, v);
			Stations.choose(player, v, new ItemStack(Items.LAPIS_LAZULI));
			helper.assertTrue(job(v) == VillagerProfession.LIBRARIAN, "not a Librarian to start with: " + jobId(v));
			Stations.choose(player, v, new ItemStack(Items.PAPER));
			helper.assertTrue(job(v) == VillagerProfession.LIBRARIAN, "the Peasant Librarian became " + jobId(v));
			helper.assertTrue(v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(helper.absolutePos(lectern)::equals).isPresent(),
				"lost their lectern: " + v.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.succeed();
		});
	}

	/** A hall that doesn't reach them is no village: a Peasant beyond its reach takes the Scholar's paper. */
	//$ gametest_ticks_batch AREA '60' '"qaClassFarHall"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaClassFarHall")
	public void aHallOutOfReachGatesNothing(GameTestHelper helper) {
		village(helper, true);
		VillageHalls.RADIUS = 4; // the hall at (14, 2, 14) reaches 4 blocks: the lectern at (2, 2, 2) is outside
		BlockPos lectern = new BlockPos(2, 2, 2);
		helper.setBlock(lectern, Blocks.LECTERN);
		helper.runAfterDelay(2, () -> {
			Villager v = villager(helper, lectern.south(), "Far", "peasant");
			helper.assertTrue(VillageHalls.nearest(helper.getLevel(), v.blockPosition()).isEmpty(), "the hall still reaches them");
			Stations.choose(helper.makeMockServerPlayerInLevel(), v, new ItemStack(Items.PAPER));
			helper.assertTrue(job(v) == ModVillagers.SCHOLAR, "gated outside the hall's reach: " + jobId(v));
			helper.succeed();
		});
	}

	/** The hall's free-workstation entry, greyed for a Peasant, names the class it needs (Artisan); for a Noble it isn't greyed. */
	//$ gametest_ticks_batch AREA '60' '"qaClassHallNames"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaClassHallNames")
	public void theHallsGreyedEntryNamesTheClassNeeded(GameTestHelper helper) {
		village(helper, true);
		BlockPos counter = new BlockPos(8, 2, 4);
		helper.setBlock(counter, ModBlocks.SHOP_COUNTER);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			Villager peasant = villager(helper, new BlockPos(4, 2, 10), "Pea", "peasant");
			ChoiceMenu menu = ChoiceMenu.detached(player, m -> VillageHallScreen.renderJobs(m, level, hall, peasant, 0));
			ItemLore lore = menu.icon(VillageHallScreen.FIRST_ROW).get(DataComponents.LORE);
			Component line = lore == null ? null : lore.lines().stream().filter(l -> key(l).equals("screen.aliveworkplace.hall.job_needs_class")).findFirst().orElse(null);
			helper.assertTrue(line != null, "not greyed: " + (lore == null ? "no lore" : lore.lines().stream().map(QaClassesGameTests::key).toList()));
			String text = line.getString();
			helper.assertTrue(text.equals("Needs Artisan class or better"), "the greyed entry reads: " + text);
			Villager noble = villager(helper, new BlockPos(6, 2, 10), "Nob", "noble");
			ChoiceMenu theirs = ChoiceMenu.detached(player, m -> VillageHallScreen.renderJobs(m, level, hall, noble, 0));
			ItemLore nobleLore = theirs.icon(VillageHallScreen.FIRST_ROW).get(DataComponents.LORE);
			helper.assertTrue(nobleLore == null || nobleLore.lines().stream().noneMatch(l -> key(l).equals("screen.aliveworkplace.hall.job_needs_class")),
				"greyed for a Noble");
			theirs.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(noble.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(helper.absolutePos(counter)::equals).isPresent(),
				"the Noble wasn't given the counter: " + noble.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.succeed();
		});
	}

	/**
	 * Nobody is fired: an Artisan Teacher whose household falls to Peasant at the hall's dawns keeps the job, and is
	 * marked as below it.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaClassFallKeepsJob"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaClassFallKeepsJob")
	public void aTeacherWhoFallsToPeasantKeepsTheJob(GameTestHelper helper) {
		village(helper, true);
		BlockPos lectern = new BlockPos(4, 2, 4);
		helper.setBlock(lectern, Blocks.LECTERN);
		helper.runAfterDelay(2, () -> {
			Villager v = villager(helper, lectern.south(), "Tea", "artisan");
			inVillage(helper, v);
			Stations.choose(helper.makeMockServerPlayerInLevel(), v, new ItemStack(Items.BOOK));
			helper.assertTrue(job(v) == ModVillagers.TEACHER, "the Artisan isn't a Teacher: " + jobId(v));
			breadOnly(v); // no home, no services, a plain diet: the Artisan's needs fail
			for (long day = 1; day <= 3; day++) {
				dawn(helper, List.of(v), day);
			}
			helper.assertTrue(id(v).equals("peasant"), "didn't fall after 3 bad dawns: " + id(v) + " " + SocialClasses.progress(v));
			helper.assertTrue(job(v) == ModVillagers.TEACHER, "fired on falling: " + jobId(v));
			helper.assertTrue(ClassJobs.above(v) == cls("artisan"), "not marked as below their job: " + ClassJobs.above(v));
			helper.succeed();
		});
	}
}
