package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.ClassesPage;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.mail.RouteData;
import io.github.jcondedata.aliveworkplace.people.ClassLook;
import io.github.jcondedata.aliveworkplace.people.ClassMigration;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.people.ClassPerks;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Retirement;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.WorkerLimits;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * QA (Check tier, 2026-10-26), written from the ROADMAP's words for 34.6 (classes at the Village Hall), 34.18 (dressed
 * by class, in game: the packet) and 34.20 (retirement and apprentices), filling the gaps the builder's tests leave:
 * <ul>
 *   <li>34.6: a want counted on the class page ("A varied diet: 2 of 5" under the wants), the "What next?" class tips
 *   most households first and capped at 3 with a fifth candidate, the class moods at 2 and 3 missing needs (-10, -15),
 *   the rise's chat line read by a player 31 blocks off and not by one 33 off;</li>
 *   <li>34.18: a child's look on tracking (their class, stage CHILD), a zombie villager never sent a look, nothing on
 *   tracking with {@code classOutfits} off, the packet surviving the wire;</li>
 *   <li>34.20: the elder's baby and the elder's working child passed over for another jobless villager; the class the job
 *   allows (a Teacher's successor an Artisan, never a Peasant); an Apprentice and a Journeyman elder's successors both
 *   Apprentice; a site picked up mid-shadowing holding the hand-over; a builder's queue and a daycare pair holding the
 *   start; the field, tree farm, orchard and courier routes carried over; a retired elder reloaded still refusing every
 *   job; a successor who takes another job cancelling the shadowing; with {@code elderRetirement} off, no "would like
 *   to retire" line; every sentence these show resolving in en_us.json.</li>
 * </ul>
 */
public class QaClassHallRetirementGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String SMALL = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final String VARIED = "{\"type\": \"diet\", \"kind\": \"varied\"}";
	private static final String PLAIN = "{\"type\": \"diet\", \"kind\": \"plain\"}";
	private static final String CITY = "{\"type\": \"village_rank\", \"rank\": \"city\"}";
	private static final String TOWN = "{\"type\": \"village_rank\", \"rank\": \"town\"}";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	// =================================================================================================================
	// 34.6: Classes at the Village Hall
	// =================================================================================================================

	/** A hall at {@link #HALL}, classes on; the class files, hooks and radius put back afterwards. */
	private static VillageHallBlockEntity classVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		SocialClasses.ENABLED = true;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
			ClassNeeds.services = io.github.jcondedata.aliveworkplace.hall.Services.HOOK;
			ClassNeeds.luxuryEvery = io.github.jcondedata.aliveworkplace.people.Luxuries.HOOK;
			SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
			SocialClasses.forget();
			ClassPerks.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
		hall.setRank(VillageRanks.Rank.HAMLET);
		return hall;
	}

	/** Peasant, Artisan (needs and wants as given) and Burgher (needs as given). */
	private static void ladder(String artisanNeeds, String artisanWants, String burgherNeeds) {
		Map<ResourceLocation, JsonElement> files = new HashMap<>();
		files.put(AliveWorkplace.id("peasant"), JsonParser.parseString("{\"tier\": 0, \"tax\": 1}"));
		files.put(AliveWorkplace.id("artisan"), JsonParser.parseString("{\"tier\": 1, \"tax\": 1.5, \"needs\": [" + artisanNeeds + "], \"wants\": [" + artisanWants + "]}"));
		files.put(AliveWorkplace.id("burgher"), JsonParser.parseString("{\"tier\": 2, \"tax\": 2.5, \"needs\": [" + burgherNeeds + "]}"));
		SocialClasses.load(files);
	}

	private static Villager person(GameTestHelper helper, BlockPos pos, String name, String cls) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		if (cls != null) {
			SocialClasses.seed(v, SocialClasses.get(AliveWorkplace.id(cls)));
		}
		return v;
	}

	private static void eats(Villager villager, String... meals) {
		ModAttachments.RECENT_MEALS.set(villager, Arrays.stream(meals).map(ResourceLocation::withDefaultNamespace).toList());
	}

	private static void marry(Villager a, Villager b) {
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), 1, true));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), 1, true));
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
	}

	private static int points(List<LegendPowers.MoodReason> moods, String reason) {
		return moods.stream().filter(m -> key(m.reason()).equals("mood.aliveworkplace.reason." + reason)).mapToInt(LegendPowers.MoodReason::points).sum();
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static ItemStack button(ChoiceMenu menu, String name) {
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			ItemStack icon = menu.icon(slot);
			if (!icon.isEmpty() && icon.getHoverName().getString().equals(name)) {
				return icon;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Words a player reads: no raw key, no "%s", and none of the British spellings the owner asked to avoid. */
	private static void readable(GameTestHelper helper, String what, String text) {
		helper.assertTrue(!text.isBlank() && !text.contains("aliveworkplace.") && !text.contains("%s") && !text.contains("%1$s"), what + " reads: " + text);
		for (String british : List.of("colour", "defence", "armour", "jeweller", "cancelled", "grey", "honour", "neighbour", "favourite", "centre")) {
			helper.assertTrue(!text.toLowerCase().contains(british), what + " is not American English (" + british + "): " + text);
		}
	}

	/**
	 * The class page lists the class's wants as well as its needs, each with how many households have it: five Peasant
	 * households, all eating a plain diet and two a varied one, read "A varied diet: 2 of 5" under the wants (34.6, "Fine Clothes: 2 of 5").
	 */
	//$ gametest_batch AREA '"qaClassHallWants"'
	@GameTest(template = AREA, batch = "qaClassHallWants")
	public void aWantIsCountedOverFiveHouseholds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		classVillage(helper);
		ladder(PLAIN, VARIED, CITY);
		List<Villager> people = new ArrayList<>();
		String[] names = {"Ann", "Bo", "Cy", "Di", "Ed"};
		for (int i = 0; i < names.length; i++) {
			Villager v = person(helper, new BlockPos(3 + 2 * i, 2, 3), names[i], "peasant");
			eats(v, i < 2 ? new String[] {"bread", "baked_potato", "cooked_cod"} : new String[] {"bread", "baked_potato", "bread"}); // varied, or plain (2 kinds)
			people.add(v);
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos at = helper.absolutePos(HALL);
		ChoiceMenu menu = VillageHallScreen.forTest(player, at);
		int tab = HallPages.slot(ClassesPage.PAGE);
		helper.assertTrue(tab >= 0, "no Classes tab");
		helper.assertTrue(lore(menu.icon(tab)).contains("Peasant households: 5"), "the tab: " + lore(menu.icon(tab)));
		menu.press(tab, player);
		List<String> artisan = lore(button(menu, "Artisan"));
		int wantsAt = artisan.indexOf("Wants (extras):");
		int countAt = artisan.indexOf("  A varied diet: 2 of 5");
		helper.assertTrue(wantsAt >= 0 && countAt > wantsAt, "the want counted under the wants: " + artisan);
		helper.assertTrue(artisan.contains("  A plain diet: 5 of 5"), "the need counted too: " + artisan);
		for (String line : artisan) {
			readable(helper, "the Artisan button", line);
		}
		people.forEach(Villager::discard);
		player.discard();
		helper.succeed();
	}

	/**
	 * "What next?" gets up to three class tips, most households first: four Peasant households lacking two ranks, two of them a
	 * varied diet and one a plain one, and an Artisan household lacking a City give five candidates; the three biggest show, 4, 4 and 2.
	 */
	//$ gametest_batch AREA '"qaClassHallTips"'
	@GameTest(template = AREA, batch = "qaClassHallTips")
	public void threeTipsAtMostAndTheBiggestFirst(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		classVillage(helper);
		ladder(String.join(", ", PLAIN, VARIED, CITY, TOWN), "", CITY);
		Villager[] peasants = {
			person(helper, new BlockPos(3, 2, 3), "Ann", "peasant"), person(helper, new BlockPos(5, 2, 3), "Bo", "peasant"),
			person(helper, new BlockPos(7, 2, 3), "Cy", "peasant"), person(helper, new BlockPos(9, 2, 3), "Di", "peasant")};
		eats(peasants[0], "bread", "baked_potato", "cooked_cod");
		eats(peasants[1], "bread", "baked_potato", "cooked_cod");
		eats(peasants[2], "bread", "baked_potato", "bread"); // plain, not varied
		eats(peasants[3], "bread", "bread", "bread");
		Villager dee = person(helper, new BlockPos(3, 2, 7), "Dee", "artisan");
		Villager eli = person(helper, new BlockPos(5, 2, 7), "Eli", "artisan");
		marry(dee, eli);
		eats(dee, "bread", "baked_potato", "cooked_cod");
		eats(eli, "bread", "baked_potato", "cooked_cod");
		BlockPos at = helper.absolutePos(HALL);
		List<ClassesPage.ClassTip> tips = ClassesPage.classTips(level, at);
		helper.assertTrue(tips.stream().map(ClassesPage.ClassTip::households).toList().equals(List.of(4, 4, 2)),
			"the three biggest of five, most households first: " + tips.stream().map(t -> t.households() + " " + ClassesPage.needName(t.need()).getString()).toList());
		List<String> advice = VillageAdvice.tips(level, at).stream().filter(t -> t.key().startsWith("class"))
			.map(t -> t.title().getString() + " | " + t.how().getString()).toList();
		helper.assertTrue(advice.size() == 3 && advice.get(0).startsWith("4 Peasant households want ") && advice.get(2).startsWith("2 Peasant households want A varied diet to rise to Artisan"),
			"What next? reads: " + advice);
		for (String line : advice) {
			readable(helper, "a class tip", line);
		}
		List.of(peasants).forEach(Villager::discard);
		List.of(dee, eli).forEach(Villager::discard);
		helper.succeed();
	}

	/** Each missing need is -5: two missing are -10 and three are -15, the cap reached exactly (34.6). */
	//$ gametest_batch AREA '"qaClassHallMoodSteps"'
	@GameTest(template = AREA, batch = "qaClassHallMoodSteps")
	public void twoMissingNeedsAreMinusTenAndThreeMinusFifteen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		classVillage(helper);
		BlockPos at = helper.absolutePos(HALL);
		long today = Chronicle.day(level);
		ladder(String.join(", ", VARIED, CITY), "", PLAIN);
		Villager two = person(helper, new BlockPos(4, 2, 4), "Una", "artisan");
		eats(two, "bread", "bread", "bread");
		SocialClasses.check(level, at, List.of(two), today, 8);
		List<LegendPowers.MoodReason> moods = SocialClasses.moods(two, today);
		helper.assertTrue(points(moods, "class_lacking") == -10, "two needs missing, -10: " + moods);
		ladder(String.join(", ", VARIED, CITY, TOWN), "", PLAIN);
		Villager three = person(helper, new BlockPos(8, 2, 4), "Vic", "artisan");
		eats(three, "bread", "bread", "bread");
		SocialClasses.check(level, at, List.of(three), today, 8);
		moods = SocialClasses.moods(three, today);
		helper.assertTrue(points(moods, "class_lacking") == -15, "three needs missing, -15: " + moods);
		Component reason = moods.stream().filter(m -> key(m.reason()).equals("mood.aliveworkplace.reason.class_lacking")).findFirst().orElseThrow().reason();
		readable(helper, "the lacking mood", reason.getString());
		List.of(two, three).forEach(Villager::discard);
		helper.succeed();
	}

	/**
	 * A rise is told in chat to players within 32 blocks of the household's door: one 31 blocks off reads "Odo and Pia
	 * rose in the world and joined the Artisan class!", one 33 off reads nothing of it.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaClassHallRiseChat"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaClassHallRiseChat")
	public void theRiseIsToldWithinThirtyTwoBlocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = classVillage(helper);
		ladder(VARIED, "", CITY);
		Villager a = person(helper, new BlockPos(4, 2, 4), "Odo", "peasant");
		Villager b = person(helper, new BlockPos(4, 2, 5), "Pia", "peasant");
		marry(a, b);
		eats(a, "bread", "baked_potato", "cooked_cod");
		eats(b, "bread", "baked_potato", "cooked_cod");
		List<Component> nearHeard = new ArrayList<>();
		List<Component> farHeard = new ArrayList<>();
		BlockPos door = a.blockPosition();
		QaClassMigrationGameTests.listener(helper, nearHeard, new Vec3(door.getX() + 0.5 + 31, door.getY() + 0.5, door.getZ() + 0.5), "Near");
		QaClassMigrationGameTests.listener(helper, farHeard, new Vec3(door.getX() + 0.5 + 33.5, door.getY() + 0.5, door.getZ() + 0.5), "Far");
		long time = level.getDayTime();
		level.setDayTime(time + 10 * VillageNeeds.DAY);
		Leftovers.after(helper, () -> level.setDayTime(time));
		long today = Chronicle.day(level);
		BlockPos at = helper.absolutePos(HALL);
		SocialClasses.check(level, at, List.of(a, b), today - 1, 8);
		SocialClasses.Result rise = SocialClasses.check(level, at, List.of(a, b), today, 8);
		helper.assertTrue(rise.changes().size() == 1 && rise.changes().get(0).rose(), "the couple didn't rise: " + rise.changes());
		List<String> near = nearHeard.stream().map(Component::getString).filter(s -> s.contains("rose in the world")).toList();
		List<String> far = farHeard.stream().map(Component::getString).filter(s -> s.contains("rose in the world")).toList();
		helper.assertTrue(near.size() == 1 && (near.get(0).equals("Odo and Pia rose in the world and joined the Artisan class!")
			|| near.get(0).equals("Pia and Odo rose in the world and joined the Artisan class!")), "31 blocks off heard: " + near);
		helper.assertTrue(far.isEmpty(), "33 blocks off heard: " + far);
		Chronicle.Entry entry = hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.CLASS).reduce((x, y) -> y).orElseThrow();
		readable(helper, "the rise's chronicle line", entry.text().getString());
		List.of(a, b).forEach(Villager::discard);
		helper.succeed();
	}

	// =================================================================================================================
	// 34.18: Dressed by class, in game
	// =================================================================================================================

	/**
	 * A player who starts tracking a Burgher's child is sent their class and the CHILD stage; a zombie villager beside them
	 * is never sent a look (the layers are never drawn on zombie villagers).
	 */
	//$ gametest_ticks_batch SMALL '200' '"qaClassLookChild"'
	@GameTest(template = SMALL, timeoutTicks = 200, batch = "qaClassLookChild")
	public void aChildIsSentTheirClassAndAZombieVillagerNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<ClassLook.Look> sent = new ArrayList<>();
		boolean enabled = ClassLook.ENABLED;
		ClassLook.ENABLED = true;
		Villager kit = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		kit.setNoAi(true);
		kit.setAge(-24000);
		SocialClasses.seed(kit, SocialClasses.get(AliveWorkplace.id("burgher")));
		ZombieVillager zombie = helper.spawn(EntityType.ZOMBIE_VILLAGER, new BlockPos(3, 2, 3));
		zombie.setNoAi(true);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ClassLook.listener = (to, look) -> {
			if (to == player) {
				sent.add(look);
			}
		};
		Leftovers.after(helper, () -> {
			ClassLook.listener = null;
			ClassLook.ENABLED = enabled;
			level.getServer().getPlayerList().remove(player);
		});
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 2)));
		player.teleportTo(level, at.x, at.y, at.z, 0, 0);
		helper.startSequence().thenWaitUntil(() -> {
			player.connection.chunkSender.onChunkBatchReceivedByClient(64f);
			level.getChunkSource().move(player);
			helper.assertTrue(sent.stream().anyMatch(l -> l.entityId() == kit.getId()), "no look sent for the child on tracking");
			helper.assertTrue(io.github.jcondedata.aliveworkplace.platform.Platform.get().tracking(zombie).contains(player), "the player doesn't track the zombie villager yet");
		}).thenExecute(() -> {
			ClassLook.Look look = sent.stream().filter(l -> l.entityId() == kit.getId()).findFirst().orElseThrow();
			helper.assertTrue(look.lifeStage() == LifeStages.Stage.CHILD && look.cls().equals(Optional.of(AliveWorkplace.id("burgher")))
				&& look.outfit().equals(Optional.of(AliveWorkplace.id("textures/entity/villager/class/burgher.png"))), "the child's look: " + look);
			helper.assertTrue(sent.stream().noneMatch(l -> l.entityId() == zombie.getId()), "a zombie villager was sent a class look");
		}).thenSucceed();
	}

	/** With {@code classOutfits} off, a player who starts tracking a Noble is sent nothing. */
	//$ gametest_ticks_batch SMALL '200' '"qaClassLookOff"'
	@GameTest(template = SMALL, timeoutTicks = 200, batch = "qaClassLookOff")
	public void nothingOnTrackingWithClassOutfitsOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<ClassLook.Look> sent = new ArrayList<>();
		boolean enabled = ClassLook.ENABLED;
		WorkplaceConfig.parse("{\"classOutfits\": false}").apply();
		Villager ada = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		ada.setNoAi(true);
		SocialClasses.seed(ada, SocialClasses.get(AliveWorkplace.id("noble")));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ClassLook.listener = (to, look) -> {
			if (to == player) {
				sent.add(look);
			}
		};
		Leftovers.after(helper, () -> {
			ClassLook.listener = null;
			new WorkplaceConfig().apply();
			ClassLook.ENABLED = enabled;
			level.getServer().getPlayerList().remove(player);
		});
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 2)));
		player.teleportTo(level, at.x, at.y, at.z, 0, 0);
		helper.startSequence().thenWaitUntil(() -> {
			player.connection.chunkSender.onChunkBatchReceivedByClient(64f);
			level.getChunkSource().move(player);
			helper.assertTrue(io.github.jcondedata.aliveworkplace.platform.Platform.get().tracking(ada).contains(player), "the player doesn't track Ada yet");
		}).thenExecute(() -> {
			helper.assertTrue(sent.isEmpty(), "a look sent on tracking with classOutfits off: " + sent);
			SocialClasses.seed(ada, SocialClasses.get(AliveWorkplace.id("artisan")));
			helper.assertTrue(sent.isEmpty(), "a look sent on a change with classOutfits off: " + sent);
		}).thenSucceed();
	}

	/** The look survives the wire: written and read back it is the same, with and without a class and an outfit. */
	//$ gametest_batch 'net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE' '"qaClassLookWire"'
	@GameTest(template = net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE, batch = "qaClassLookWire")
	public void theLookSurvivesTheWire(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<ClassLook.Look> looks = List.of(
			new ClassLook.Look(42, Optional.of(AliveWorkplace.id("noble")), Optional.of(AliveWorkplace.id("textures/entity/villager/class/noble.png")), LifeStages.Stage.ELDER.ordinal()),
			new ClassLook.Look(7, Optional.empty(), Optional.empty(), LifeStages.Stage.CHILD.ordinal()),
			new ClassLook.Look(123456, Optional.of(AliveWorkplace.id("peasant")), Optional.empty(), LifeStages.Stage.GROWN.ordinal()));
		for (ClassLook.Look look : looks) {
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
			ClassLook.Look.CODEC.encode(buf, look);
			ClassLook.Look back = ClassLook.Look.CODEC.decode(buf);
			helper.assertTrue(back.equals(look) && buf.readableBytes() == 0, "the look came back as " + back + " from " + look);
		}
		helper.succeed();
	}

	// =================================================================================================================
	// 34.20: Retirement and apprentices
	// =================================================================================================================

	private static void village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
			Retirement.ENABLED = true;
			VillageNeeds.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
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

	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		VillageNeeds.forget();
		Moods.forget();
		return hall;
	}

	private static void childOf(Villager child, Villager parent) {
		ModAttachments.PARENTS.set(child, new Families.Parents(Component.literal("Ann"), parent.getDisplayName(), "", "", !child.isBaby(),
			false, false, null, parent.getUUID()));
	}

	private static List<String> card(ServerLevel level, BlockPos hall, Villager villager) {
		ItemLore lore = VillageHallScreen.person(level, hall, villager).get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static List<String> chronicle(VillageHallBlockEntity hall) {
		return hall.chronicle().stream().map(e -> e.text().getString()).toList();
	}

	/**
	 * The successor is the elder's own grown child without a job: a baby child of his and a grown child of his who works
	 * are both passed over, and the next jobless grown villager (Ida, no kin) learns from him instead.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRetireNotBabyNorWorkingChild"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRetireNotBabyNorWorkingChild")
	public void aBabyOrWorkingChildIsPassedOver(GameTestHelper helper) {
		village(helper);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.FARMER, 4);
		Villager kit = villager(helper, new BlockPos(7, 2, 4), "Kit", VillagerProfession.NONE, 1);
		kit.setAge(-24000);
		Villager jon = villager(helper, new BlockPos(7, 2, 6), "Jon", VillagerProfession.FLETCHER, 2);
		Villager ida = villager(helper, new BlockPos(9, 2, 6), "Ida", VillagerProfession.NONE, 1);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 1210);
			LifeStages.setAdultSince(jon, today - 30);
			LifeStages.setAdultSince(ida, today - 30);
			childOf(kit, bram);
			childOf(jon, bram);
			Retirement.round(level, hallPos, List.of(bram, kit, jon, ida));
			helper.assertTrue(Retirement.shadow(kit) == null, "a baby is learning Bram's trade");
			helper.assertTrue(Retirement.shadow(jon) == null && jon.getVillagerData().getProfession() == VillagerProfession.FLETCHER, "Bram's working son was taken off his job");
			Retirement.Shadow shadow = Retirement.shadow(ida);
			helper.assertTrue(shadow != null && shadow.elder().equals(bram.getUUID()), "Ida, the only jobless grown villager, isn't learning from Bram: " + shadow);
			helper.succeed();
		});
	}

	/**
	 * Any jobless grown villager "of a class the job allows" (34.8): a Teacher (an Artisan's job) is never handed to a
	 * jobless Peasant; with only a Peasant the hall says he'd like to retire, and once a jobless Artisan is there she learns.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRetireClassAllowed"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRetireClassAllowed")
	public void theSuccessorIsOfAClassTheJobAllows(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		boolean classes = SocialClasses.ENABLED;
		SocialClasses.ENABLED = true;
		SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
		Leftovers.after(helper, () -> {
			SocialClasses.ENABLED = classes;
			SocialClasses.forget();
			ClassPerks.forget();
		});
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", ModVillagers.TEACHER, 3);
		Villager pea = villager(helper, new BlockPos(7, 2, 6), "Pea", VillagerProfession.NONE, 1);
		helper.runAfterDelay(5, () -> {
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = ready(helper);
			hall.setRank(VillageRanks.Rank.HAMLET);
			SocialClasses.seed(bram, SocialClasses.get(AliveWorkplace.id("artisan")));
			SocialClasses.seed(pea, SocialClasses.get(AliveWorkplace.id("peasant")));
			long today = Chronicle.day(level);
			helper.assertTrue(!ClassMigration.quiet(level, hallPos, today), "the village is settling in: nobody would retire");
			LifeStages.setAdultSince(bram, today - 1210);
			LifeStages.setAdultSince(pea, today - 30);
			Retirement.round(level, hallPos, List.of(bram, pea));
			helper.assertTrue(Retirement.shadow(pea) == null, "a Peasant is learning the Teacher's trade");
			helper.assertTrue(card(level, hallPos, bram).contains("Bram would like to retire: the village needs a jobless villager to take over"),
				"Bram's card: " + card(level, hallPos, bram));
			Villager art = villager(helper, new BlockPos(9, 2, 6), "Art", VillagerProfession.NONE, 1);
			SocialClasses.seed(art, SocialClasses.get(AliveWorkplace.id("artisan")));
			LifeStages.setAdultSince(art, today - 30);
			Retirement.round(level, hallPos, List.of(bram, pea, art));
			helper.assertTrue(Retirement.shadow(pea) == null && Retirement.shadow(art) != null && Retirement.shadow(art).elder().equals(bram.getUUID()),
				"the jobless Artisan should learn from Bram: Pea " + Retirement.shadow(pea) + ", Art " + Retirement.shadow(art));
			helper.succeed();
		});
	}

	/**
	 * One level below the elder's, at least Apprentice: an Apprentice elder's successor is an Apprentice (not a Novice), and
	 * a Journeyman's, three days later, is an Apprentice too; the chronicle says "as Apprentice" both times.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRetireLevels"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRetireLevels")
	public void anApprenticeAndAJourneymanBothHandToAnApprentice(GameTestHelper helper) {
		village(helper);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.CLERIC, 2);
		Villager odo = villager(helper, new BlockPos(5, 2, 9), "Odo", VillagerProfession.CARTOGRAPHER, 3);
		Villager tom = villager(helper, new BlockPos(7, 2, 6), "Tom", VillagerProfession.NONE, 1);
		Villager ida = villager(helper, new BlockPos(9, 2, 6), "Ida", VillagerProfession.NONE, 1);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = ready(helper);
			long clock = level.getDayTime();
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 1230);
			LifeStages.setAdultSince(odo, today - 1210);
			LifeStages.setAdultSince(tom, today - 20);
			LifeStages.setAdultSince(ida, today - 20);
			List<Villager> all = List.of(bram, odo, tom, ida);
			try {
				Retirement.round(level, hallPos, all);
				Villager first = Retirement.shadow(tom) != null ? tom : ida;
				Villager second = first == tom ? ida : tom;
				helper.assertTrue(Retirement.shadow(first) != null && Retirement.shadow(first).elder().equals(bram.getUUID()), "Bram (the oldest) not first");
				level.setDayTime(clock + 2 * VillageNeeds.DAY);
				Retirement.round(level, hallPos, all);
				helper.assertTrue(Retirement.isRetired(bram) && first.getVillagerData().getProfession() == VillagerProfession.CLERIC
					&& first.getVillagerData().getLevel() == 2, "an Apprentice's successor: " + first.getVillagerData());
				level.setDayTime(clock + 5 * VillageNeeds.DAY);
				Retirement.round(level, hallPos, all);
				helper.assertTrue(Retirement.shadow(second) != null && Retirement.shadow(second).elder().equals(odo.getUUID()), "Odo didn't start 3 days after");
				level.setDayTime(clock + 7 * VillageNeeds.DAY);
				Retirement.round(level, hallPos, all);
				helper.assertTrue(Retirement.isRetired(odo) && second.getVillagerData().getProfession() == VillagerProfession.CARTOGRAPHER
					&& second.getVillagerData().getLevel() == 2, "a Journeyman's successor: " + second.getVillagerData());
				List<String> lines = chronicle(hall);
				String a = first.getName().getString();
				String b = second.getName().getString();
				helper.assertTrue(lines.contains(a + " took over the Cleric trade from Bram, as Apprentice")
					&& lines.contains(b + " took over the Cartographer trade from Odo, as Apprentice"), "the chronicle: " + lines);
				for (String line : lines) {
					readable(helper, "the chronicle", line);
				}
			} finally {
				level.setDayTime(clock);
			}
			helper.succeed();
		});
	}

	/**
	 * Never while they hold something for a player or a build: a builder's queue (no site yet) and Pokémon pairs in a
	 * daycare each hold the start; and a site he picks up while his successor shadows him holds the hand-over until it's
	 * done, then it goes ahead.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRetireHoldsMidway"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRetireHoldsMidway")
	public void aQueueOrPairsHoldTheStartAndASiteHoldsTheHandOver(GameTestHelper helper) {
		village(helper);
		Leftovers.buildSites(helper);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.MASON, 3);
		Villager tom = villager(helper, new BlockPos(7, 2, 6), "Tom", VillagerProfession.NONE, 1);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			long clock = level.getDayTime();
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 1210);
			LifeStages.setAdultSince(tom, today - 20);
			List<Villager> all = List.of(bram, tom);
			UUID owner = UUID.nameUUIDFromBytes("owner".getBytes());
			try {
				// A queue: a build lined up for him, nothing started.
				BuildSite queued = Builders.enqueue(level, bram, owner, "Jesse", TEST_HUT,
					new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(1, 2, 14)), Rotation.NONE, Mirror.NONE));
				helper.assertTrue(!ModAttachments.BUILDER_JOB.has(bram), "the queue started a site");
				Retirement.round(level, hallPos, all);
				helper.assertTrue(Retirement.shadow(tom) == null, "retiring with a build in his queue");
				BuildSiteManager.get(level).remove(queued.id());
				// Pokémon pairs in his daycare.
				ModAttachments.DAYCARE_PAIRS.set(bram, List.of(new DaycareKeepers.Pair(owner, "Jesse", new CompoundTag(), new CompoundTag(), 0, today)));
				Retirement.round(level, hallPos, all);
				helper.assertTrue(Retirement.shadow(tom) == null, "retiring with a pair in his daycare");
				ModAttachments.DAYCARE_PAIRS.remove(bram);
				// Nothing held: Tom starts learning.
				Retirement.round(level, hallPos, all);
				helper.assertTrue(Retirement.shadow(tom) != null, "not started with nothing held");
				// He takes on a site while Tom shadows him: no hand-over on day 2.
				ModAttachments.BUILDER_JOB.set(bram, new BuilderJob(UUID.randomUUID()));
				level.setDayTime(clock + 2 * VillageNeeds.DAY);
				Retirement.round(level, hallPos, all);
				helper.assertTrue(!Retirement.isRetired(bram) && bram.getVillagerData().getProfession() == VillagerProfession.MASON
					&& tom.getVillagerData().getProfession() == VillagerProfession.NONE, "handed over while he held a site: Bram " + bram.getVillagerData() + ", Tom " + tom.getVillagerData());
				// The site done: the next round hands over.
				ModAttachments.BUILDER_JOB.remove(bram);
				level.setDayTime(clock + 3 * VillageNeeds.DAY);
				Retirement.round(level, hallPos, all);
				helper.assertTrue(Retirement.isRetired(bram) && tom.getVillagerData().getProfession() == VillagerProfession.MASON, "no hand-over after the site was done: Tom " + tom.getVillagerData());
			} finally {
				level.setDayTime(clock);
			}
			helper.succeed();
		});
	}

	/** The job's assignments carried over: field, tree farm, orchard and courier routes go from the elder to the successor. */
	//$ gametest_ticks_batch AREA '100' '"qaRetireAssignments"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRetireAssignments")
	public void theFieldsAndRoutesAreCarriedOver(GameTestHelper helper) {
		village(helper);
		BlockPos composter = new BlockPos(3, 2, 3);
		helper.setBlock(composter, Blocks.COMPOSTER);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.NONE, 1);
		Villager tom = villager(helper, new BlockPos(7, 2, 6), "Tom", VillagerProfession.NONE, 1);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, bram, helper.absolutePos(composter), PoiTypes.FARMER, VillagerProfession.FARMER);
			bram.setVillagerData(bram.getVillagerData().setLevel(3));
			long clock = level.getDayTime();
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 1210);
			LifeStages.setAdultSince(tom, today - 20);
			BlockPos o = helper.absolutePos(BlockPos.ZERO);
			FieldJob field = new FieldJob(new BoundingBox(o.getX(), o.getY(), o.getZ(), o.getX() + 4, o.getY() + 1, o.getZ() + 4));
			FieldJob trees = new FieldJob(new BoundingBox(o.getX() + 6, o.getY(), o.getZ(), o.getX() + 9, o.getY() + 3, o.getZ() + 4));
			FieldJob orchard = new FieldJob(new BoundingBox(o.getX(), o.getY(), o.getZ() + 6, o.getX() + 4, o.getY() + 3, o.getZ() + 9), true);
			List<RouteData> routes = List.of(new RouteData(Optional.of(o.offset(1, 1, 1)), Optional.of(o.offset(9, 1, 9)), List.of(net.minecraft.world.item.Items.WHEAT)));
			ModAttachments.FARM_FIELD.set(bram, field);
			ModAttachments.TREE_FARM.set(bram, trees);
			ModAttachments.ORCHARD.set(bram, orchard);
			ModAttachments.COURIER_ROUTES.set(bram, routes);
			List<Villager> all = List.of(bram, tom);
			try {
				Retirement.round(level, hallPos, all);
				level.setDayTime(clock + 2 * VillageNeeds.DAY);
				Retirement.round(level, hallPos, all);
				helper.assertTrue(Retirement.isRetired(bram) && tom.getVillagerData().getProfession() == VillagerProfession.FARMER, "no hand-over: " + tom.getVillagerData());
				helper.assertTrue(field.equals(ModAttachments.FARM_FIELD.get(tom)) && trees.equals(ModAttachments.TREE_FARM.get(tom))
					&& orchard.equals(ModAttachments.ORCHARD.get(tom)) && routes.equals(ModAttachments.COURIER_ROUTES.get(tom)),
					"assignments not carried: field " + ModAttachments.FARM_FIELD.get(tom) + ", trees " + ModAttachments.TREE_FARM.get(tom)
						+ ", orchard " + ModAttachments.ORCHARD.get(tom) + ", routes " + ModAttachments.COURIER_ROUTES.get(tom));
				helper.assertTrue(!ModAttachments.FARM_FIELD.has(bram) && !ModAttachments.TREE_FARM.has(bram) && !ModAttachments.ORCHARD.has(bram)
					&& !ModAttachments.COURIER_ROUTES.has(bram), "Bram kept assignments");
			} finally {
				level.setDayTime(clock);
			}
			helper.succeed();
		});
	}

	/**
	 * A retired elder never takes a job again, also after a save: loaded again, he refuses a workstation's job and a job by
	 * order, is never a successor, and his trades are still there for players.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRetireReloaded"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRetireReloaded")
	public void aRetiredElderReloadedStillTakesNoJob(GameTestHelper helper) {
		village(helper);
		BlockPos lectern = new BlockPos(3, 2, 3);
		helper.setBlock(lectern, Blocks.LECTERN);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.FARMER, 4);
		Villager tom = villager(helper, new BlockPos(7, 2, 6), "Tom", VillagerProfession.NONE, 1);
		Villager odo = villager(helper, new BlockPos(9, 2, 6), "Odo", VillagerProfession.LIBRARIAN, 2);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			long clock = level.getDayTime();
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 1210);
			LifeStages.setAdultSince(tom, today - 20);
			LifeStages.setAdultSince(odo, today - 20);
			int trades = bram.getOffers().size();
			try {
				Retirement.round(level, hallPos, List.of(bram, tom, odo));
				level.setDayTime(clock + 2 * VillageNeeds.DAY);
				Retirement.round(level, hallPos, List.of(bram, tom, odo));
				helper.assertTrue(Retirement.isRetired(bram), "Bram didn't retire");
				CompoundTag tag = bram.saveWithoutId(new CompoundTag());
				bram.discard();
				Villager again = EntityType.VILLAGER.create(level);
				again.load(tag);
				again.setNoAi(true);
				level.addFreshEntity(again);
				helper.assertTrue(Retirement.isRetired(again) && again.getVillagerData().getProfession() == VillagerProfession.NONE, "reloaded: " + again.getVillagerData());
				helper.assertTrue(again.getOffers().size() == trades && trades > 0, "trades after the reload: " + again.getOffers().size() + " of " + trades);
				again.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(lectern)));
				again.setVillagerData(again.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
				helper.assertTrue(again.getVillagerData().getProfession() == VillagerProfession.NONE, "reloaded, he took the lectern's job: " + again.getVillagerData());
				WorkerLimits.order(again, VillagerProfession.FARMER);
				helper.assertTrue(again.getVillagerData().getProfession() == VillagerProfession.NONE, "reloaded, he took a job by order: " + again.getVillagerData());
				helper.assertTrue(Retirement.successor(hallPos, odo, List.of(again, odo), Chronicle.day(level)) == null, "reloaded, he's chosen to take over");
				helper.assertTrue(card(level, hallPos, again).contains("Retired Farmer · mentors the young"), "his card: " + card(level, hallPos, again));
				again.discard();
			} finally {
				level.setDayTime(clock);
			}
			helper.succeed();
		});
	}

	/** A successor who takes another job while shadowing is off it: on day 2 the elder still works and nobody took over. */
	//$ gametest_ticks_batch AREA '100' '"qaRetireSuccessorTakesAJob"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRetireSuccessorTakesAJob")
	public void aSuccessorWhoTakesAnotherJobCancelsTheShadowing(GameTestHelper helper) {
		village(helper);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.FLETCHER, 3);
		Villager tom = villager(helper, new BlockPos(7, 2, 6), "Tom", VillagerProfession.NONE, 1);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			long clock = level.getDayTime();
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 1210);
			LifeStages.setAdultSince(tom, today - 20);
			try {
				Retirement.round(level, hallPos, List.of(bram, tom));
				helper.assertTrue(Retirement.shadow(tom) != null, "Tom isn't learning");
				tom.setVillagerData(tom.getVillagerData().setProfession(VillagerProfession.MASON).setLevel(1));
				tom.setVillagerXp(1);
				level.setDayTime(clock + 2 * VillageNeeds.DAY);
				Retirement.round(level, hallPos, List.of(bram, tom));
				helper.assertTrue(!Retirement.isRetired(bram) && bram.getVillagerData().getProfession() == VillagerProfession.FLETCHER
					&& tom.getVillagerData().getProfession() == VillagerProfession.MASON && Retirement.shadow(tom) == null,
					"after Tom became a Mason: Bram " + bram.getVillagerData() + " retired " + Retirement.isRetired(bram) + ", Tom " + tom.getVillagerData());
				helper.assertTrue(card(level, hallPos, bram).contains("Bram would like to retire: the village needs a jobless villager to take over"),
					"with nobody left to take over, the hall says so: " + card(level, hallPos, bram));
			} finally {
				level.setDayTime(clock);
			}
			helper.succeed();
		});
	}

	/** {@code elderRetirement} off (from the file): no one starts, and the hall never says an elder would like to retire. */
	//$ gametest_ticks_batch AREA '100' '"qaRetireOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRetireOff")
	public void switchedOffNobodyStartsAndTheHallSaysNothing(GameTestHelper helper) {
		village(helper);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.FLETCHER, 3);
		Villager odo = villager(helper, new BlockPos(5, 2, 9), "Odo", VillagerProfession.LIBRARIAN, 3);
		Villager tom = villager(helper, new BlockPos(7, 2, 6), "Tom", VillagerProfession.NONE, 1);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 1210);
			LifeStages.setAdultSince(odo, today - 1220);
			LifeStages.setAdultSince(tom, today - 20);
			WorkplaceConfig.parse("{\"elderRetirement\": false}").apply();
			Retirement.round(level, hallPos, List.of(bram, odo));
			Retirement.round(level, hallPos, List.of(bram, odo, tom));
			helper.assertTrue(Retirement.shadow(tom) == null && !Retirement.isRetired(bram) && !Retirement.isRetired(odo), "retiring with elderRetirement off");
			for (Villager v : List.of(bram, odo)) {
				helper.assertTrue(card(level, hallPos, v).stream().noneMatch(l -> l.contains("would like to retire")), "the card with it off: " + card(level, hallPos, v));
			}
			new WorkplaceConfig().apply();
			helper.succeed();
		});
	}

	/** Every line 34.20 shows a player has words in en_us.json, in American English, with its arguments filled. */
	//$ gametest_batch 'net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE' '"qaRetireWords"'
	@GameTest(template = net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE, batch = "qaRetireWords")
	public void everyRetirementLineReads(GameTestHelper helper) {
		Component bram = Component.literal("Bram");
		Component tom = Component.literal("Tom");
		Component farmer = Component.literal("Farmer");
		List<Component> lines = List.of(
			Component.translatable("chronicle.aliveworkplace.retire.learning", tom, farmer, bram),
			Component.translatable("chronicle.aliveworkplace.retire.retired", bram, farmer, 1212),
			Component.translatable("chronicle.aliveworkplace.retire.handover", tom, farmer, bram, Component.translatable("merchant.level.2")),
			Component.translatable("status.aliveworkplace.retire.learning", "Bram"),
			Component.translatable("status.aliveworkplace.retire.taught", "Bram"),
			Component.translatable("screen.aliveworkplace.hall.retired", farmer),
			Component.translatable("screen.aliveworkplace.hall.retire_waiting", bram),
			Component.translatable("message.aliveworkplace.job.retired", bram));
		for (Component line : lines) {
			String key = key(line);
			helper.assertTrue(Language.getInstance().has(key), "no words for " + key);
			readable(helper, key, line.getString());
			helper.assertTrue(line.getString().contains("Bram") || line.getString().contains("Farmer"), key + " lost its arguments: " + line.getString());
		}
		helper.assertTrue(Component.translatable("status.aliveworkplace.retire.learning", "Bram").getString().equals("learning from Bram")
			&& Component.translatable("status.aliveworkplace.retire.taught", "Bram").getString().equals("taught by Bram"), "the words over their heads");
		helper.assertTrue(Component.translatable("merchant.level.2").getString().equals("Apprentice"), "level 2 isn't Apprentice");
		helper.succeed();
	}
}
