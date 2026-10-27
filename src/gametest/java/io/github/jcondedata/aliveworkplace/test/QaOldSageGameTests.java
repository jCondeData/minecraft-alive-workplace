package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.LegendSites;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.OldSage;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchTree;
import io.github.jcondedata.aliveworkplace.research.ResearchTrees;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * QA for 29.14, the Old Sage, written from the spec: the hint comes on the second miss and not the first, and a right
 * answer starts the count again; the Sage takes one answer from a stack and an answer kept in the off hand; the quest
 * survives a save and reload half done; the rumor reaches the village's owner with the hut's direction and nobody
 * else; with Legends off no hut is set down; every draw is three different riddles of the eight; the Iron Pact's golem
 * cap and its 5-day round; Runes of Warding at level I.
 */
public class QaOldSageGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 2);
	private static final BlockPos MIDDLE = new BlockPos(15, 1, 15);
	private static final ResourceLocation TREE = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "ancient_lore");

	private static BlockPos hall(GameTestHelper helper) {
		return helper.absolutePos(HALL);
	}

	private static VillageHallBlockEntity entity(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getBlockEntity(HALL);
	}

	/** A player who keeps every line they are told (chat and system), on the server's player list until the test ends. */
	static ServerPlayer listener(GameTestHelper helper, List<Component> seen, String name) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				seen.add(message);
				super.sendSystemMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	private static void setUp(GameTestHelper helper, ServerPlayer owner) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		entity(helper).setOwner(owner.getUUID(), owner.getName().getString());
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		Caravans.Data.get(level).setWants(hall, Component.literal("Sagewood"), List.of());
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			Caravans.Data.get(level).remove(hall);
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
				record.forgetCaptive(v.getUUID());
				v.discard();
			}
			for (LegendRecord.Captive c : record.captives()) {
				if (c.hall().equals(Optional.of(hall))) {
					record.forgetCaptive(c.villager());
				}
			}
			level.getEntitiesOfClass(IronGolem.class, helper.getBounds().inflate(16), g -> true).forEach(IronGolem::discard);
			OldSage.forget();
			Research.forget();
			CivicEffects.forget();
			Legends.ENABLED = true;
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
	}

	/** Only the Old Sage on the roster while {@code body} runs. */
	private static void staged(GameTestHelper helper, java.util.function.Consumer<Legend> body) {
		Legend sage = Legends.get(OldSage.ID).orElse(null);
		helper.assertTrue(sage != null, "old_sage.json not loaded");
		Legends.setForTest(Map.of(sage.id(), sage));
		body.accept(sage);
	}

	private static Villager hut(GameTestHelper helper) {
		Villager v = OldSage.placeHut(helper.getLevel(), hall(helper), helper.absolutePos(MIDDLE), RandomSource.create(14));
		helper.assertTrue(v != null, "the hut wasn't set down");
		return v;
	}

	private static void lore(GameTestHelper helper, Map<String, Integer> topics) {
		ResearchTree tree = ResearchTrees.get(TREE).orElse(null);
		helper.assertTrue(tree != null, "ancient_lore not loaded");
		Map<String, Integer> out = new HashMap<>();
		topics.forEach((k, v) -> out.put(ResearchTrees.levelKey(tree, tree.topic(k).orElseThrow()), v));
		entity(helper).setResearch(new Research.State(Map.copyOf(out), Optional.empty(), 0, false));
		CivicEffects.forget();
	}

	private static List<String> strings(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	/** Miss one: a shake of the head and no hint. Miss two: the hint for this riddle. A right answer starts the count again. */
	//$ gametest_ticks_batch AREA '100' '"qaSageHint"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSageHint")
	public void theHintComesOnTheSecondMissAndAnAnswerResetsIt(GameTestHelper helper) {
		List<Component> heard = new ArrayList<>();
		ServerPlayer owner = listener(helper, heard, "SageAsker");
		setUp(helper, owner);
		staged(helper, legend -> {
			Villager sage = hut(helper);
			ModAttachments.SAGE_RIDDLES.set(sage, new OldSage.Riddles(List.of(2, 6, 8), 0, 0));
			String hint2 = Component.translatable("message.aliveworkplace.sage.hint.2").getString();
			String hint6 = Component.translatable("message.aliveworkplace.sage.hint.6").getString();
			heard.clear();
			owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
			LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			helper.assertTrue(strings(heard).stream().anyMatch(s -> s.contains("shakes their head")), "no shake of the head: " + strings(heard));
			helper.assertTrue(strings(heard).stream().noneMatch(s -> s.contains("A hint")), "a hint after one miss: " + strings(heard));
			helper.assertTrue(owner.getMainHandItem().is(Items.DIRT), "a wrong item was taken");
			heard.clear();
			LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			helper.assertTrue(strings(heard).stream().anyMatch(s -> s.contains("A hint") && s.contains(hint2)), "no compass hint on the second miss: " + strings(heard));
			// the right answer: the next riddle, and its count starts at nought
			heard.clear();
			owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COMPASS));
			LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			String riddle6 = Component.translatable("message.aliveworkplace.sage.riddle.6").getString();
			helper.assertTrue(strings(heard).stream().anyMatch(s -> s.contains("Just so")), "no praise for the answer: " + strings(heard));
			helper.assertTrue(strings(heard).stream().anyMatch(s -> s.contains("Riddle 2 of 3") && s.contains(riddle6)), "the second riddle wasn't asked: " + strings(heard));
			helper.assertTrue(ModAttachments.SAGE_RIDDLES.get(sage).misses() == 0, "the misses weren't reset: " + ModAttachments.SAGE_RIDDLES.get(sage));
			heard.clear();
			owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
			LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			helper.assertTrue(strings(heard).stream().noneMatch(s -> s.contains("A hint")), "the old misses carried over to the next riddle: " + strings(heard));
			LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			helper.assertTrue(strings(heard).stream().anyMatch(s -> s.contains(hint6)), "the torch hint: " + strings(heard));
			helper.assertFalse(LegendRecord.get(helper.getLevel()).captive(sage.getUUID()).orElseThrow().freed(), "freed after one answer");
		});
		helper.succeed();
	}

	/** One compass from a stack of three; an answer in the off hand with the main hand empty; a wrong off-hand item is kept. */
	//$ gametest_ticks_batch AREA '100' '"qaSageHands"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSageHands")
	public void theSageTakesOneFromAStackAndFromTheOffHand(GameTestHelper helper) {
		List<Component> heard = new ArrayList<>();
		ServerPlayer owner = listener(helper, heard, "SageHands");
		setUp(helper, owner);
		staged(helper, legend -> {
			Villager sage = hut(helper);
			ModAttachments.SAGE_RIDDLES.set(sage, new OldSage.Riddles(List.of(2, 3, 5), 0, 0));
			owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COMPASS, 3));
			LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			helper.assertTrue(owner.getMainHandItem().is(Items.COMPASS) && owner.getMainHandItem().getCount() == 2, "not one of three taken: " + owner.getMainHandItem());
			helper.assertTrue(ModAttachments.SAGE_RIDDLES.get(sage).solved() == 1, "the compass didn't answer");
			// a second compass isn't the clock
			LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			helper.assertTrue(ModAttachments.SAGE_RIDDLES.get(sage).solved() == 1 && owner.getMainHandItem().getCount() == 2, "a compass answered the clock");
			// the clock in the off hand, the main hand empty
			owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.CLOCK));
			LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			helper.assertTrue(ModAttachments.SAGE_RIDDLES.get(sage).solved() == 2 && owner.getOffhandItem().isEmpty(), "the off-hand clock: " + ModAttachments.SAGE_RIDDLES.get(sage));
			// a shield in the off hand for the sponge riddle: only the riddle again, nothing taken, no miss
			owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
			heard.clear();
			LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			OldSage.Riddles now = ModAttachments.SAGE_RIDDLES.get(sage);
			helper.assertTrue(now.solved() == 2 && now.misses() == 0 && owner.getOffhandItem().is(Items.SHIELD), "the off-hand shield counted: " + now);
			helper.assertTrue(strings(heard).stream().anyMatch(s -> s.contains("Riddle 3 of 3")), "the riddle wasn't asked again: " + strings(heard));
			// a wet sponge is a sponge
			owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WET_SPONGE));
			LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			helper.assertTrue(LegendRecord.get(helper.getLevel()).captive(sage.getUUID()).orElseThrow().freed(), "three answers didn't free the Sage");
		});
		helper.succeed();
	}

	/** Half way through the quest, with a miss: the Sage saved and loaded asks the same riddle, with the same count. */
	//$ gametest_ticks_batch AREA '100' '"qaSageSaved"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSageSaved")
	public void theQuestSurvivesASaveAndReload(GameTestHelper helper) {
		List<Component> heard = new ArrayList<>();
		ServerPlayer owner = listener(helper, heard, "SageSaver");
		setUp(helper, owner);
		staged(helper, legend -> {
			Villager sage = hut(helper);
			OldSage.Riddles drawn = ModAttachments.SAGE_RIDDLES.get(sage);
			int first = drawn.asked().get(0);
			ItemStack answer = switch (first) {
				case 1 -> new ItemStack(Items.MAP);
				case 2 -> new ItemStack(Items.COMPASS);
				case 3 -> new ItemStack(Items.CLOCK);
				case 4 -> new ItemStack(Items.BOOK);
				case 5 -> new ItemStack(Items.SPONGE);
				case 6 -> new ItemStack(Items.TORCH);
				case 7 -> new ItemStack(Items.WATER_BUCKET);
				default -> new ItemStack(Items.EMERALD);
			};
			owner.setItemInHand(InteractionHand.MAIN_HAND, answer);
			LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ROTTEN_FLESH));
			LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			OldSage.Riddles before = ModAttachments.SAGE_RIDDLES.get(sage);
			helper.assertTrue(before.solved() == 1 && before.misses() == 1, "before saving: " + before);
			CompoundTag saved = sage.saveWithoutId(new CompoundTag());
			Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
			loaded.load(saved);
			OldSage.Riddles after = ModAttachments.SAGE_RIDDLES.get(loaded);
			helper.assertTrue(before.equals(after), "the quest changed in a save: " + before + " -> " + after);
			loaded.discard();
		});
		helper.succeed();
	}

	/** The rumor: the owner hears the distance, the direction and the village; a player who isn't the village's hears nothing. */
	//$ gametest_ticks_batch AREA '100' '"qaSageRumor"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSageRumor")
	public void theRumorReachesOnlyTheVillagesPlayers(GameTestHelper helper) {
		List<Component> heard = new ArrayList<>();
		List<Component> stranger = new ArrayList<>();
		ServerPlayer owner = listener(helper, heard, "SageOwner");
		listener(helper, stranger, "SageStranger");
		setUp(helper, owner);
		staged(helper, legend -> {
			heard.clear();
			stranger.clear();
			hut(helper);
			// the hut at (15, 15) from the hall at (2, 2): south-east, about 18 blocks
			List<String> told = strings(heard).stream().filter(s -> s.contains("rumor")).toList();
			helper.assertTrue(told.size() == 1, "the owner heard the rumor " + told.size() + " times: " + strings(heard));
			String line = told.get(0);
			String dir = io.github.jcondedata.aliveworkplace.legend.Pathfinder.direction(hall(helper), helper.absolutePos(MIDDLE)).getString();
			helper.assertTrue(dir.toLowerCase().contains("south") && dir.toLowerCase().contains("east"), "the direction word for south-east: " + dir);
			helper.assertTrue(line.contains(VillageHalls.name(helper.getLevel(), hall(helper)).getString()) && line.contains(dir) && line.contains("about 18 blocks"), "the rumor: " + line);
			helper.assertTrue(strings(stranger).stream().noneMatch(s -> s.contains("rumor")), "a stranger heard the rumor: " + strings(stranger));
		});
		helper.succeed();
	}

	/** With Legends switched off, a village with 5 research levels doesn't qualify, and the morning round sets no hut down. */
	//$ gametest_ticks_batch AREA '100' '"qaSageOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSageOff")
	public void withLegendsOffNoHut(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		setUp(helper, owner);
		ServerLevel level = helper.getLevel();
		staged(helper, legend -> {
			entity(helper).setResearch(new Research.State(Map.of("swift_hands", 3, "hearth", 2), Optional.empty(), 0, false));
			helper.assertTrue(OldSage.qualifies(level, hall(helper)), "5 levels should qualify with Legends on");
			Legends.ENABLED = false;
			try {
				helper.assertFalse(OldSage.qualifies(level, hall(helper)), "qualifies with Legends off");
				int before = LegendRecord.get(level).captives().size();
				level.setDayTime(Chronicle.day(level) * VillageNeeds.DAY + LegendGuests.MORNING_FROM + 50);
				OldSage.round(level, hall(helper));
				helper.assertTrue(LegendRecord.get(level).captives().size() == before, "a hut with Legends off");
			} finally {
				Legends.ENABLED = true;
			}
		});
		helper.succeed();
	}

	/** Every draw (500 fixed seeds) is three different riddles numbered 1 to 8, and each of the eight comes up. */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void everyDrawIsThreeDifferentRiddlesOfTheEight(GameTestHelper helper) {
		Set<Integer> seen = new HashSet<>();
		for (int seed = 0; seed < 500; seed++) {
			OldSage.Riddles r = OldSage.draw(RandomSource.create(seed));
			helper.assertTrue(r.asked().size() == 3 && r.asked().stream().distinct().count() == 3 && r.solved() == 0 && r.misses() == 0, "draw " + seed + ": " + r);
			for (int n : r.asked()) {
				helper.assertTrue(n >= 1 && n <= 8, "riddle number " + n);
				seen.add(n);
			}
		}
		helper.assertTrue(seen.size() == 8, "riddles never drawn: " + seen);
		for (int n = 1; n <= 8; n++) {
			String riddle = Component.translatable("message.aliveworkplace.sage.riddle." + n).getString();
			String hint = Component.translatable("message.aliveworkplace.sage.hint." + n).getString();
			helper.assertTrue(!riddle.startsWith("message.") && !hint.startsWith("message."), "riddle " + n + " or its hint isn't in the lang file");
		}
		helper.succeed();
	}

	/**
	 * The Iron Pact: 15 villagers get one golem and no second, the 16th lets a second come; the morning round brings one only
	 * on a day that is a multiple of 5; Runes of Warding I makes raids a fifth less likely.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaSagePact"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaSagePact")
	public void ironPactCapAndRoundAndWardingOne(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		setUp(helper, owner);
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < 15; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(6 + i % 5, 2, 8 + 2 * (i / 5)));
		}
		helper.runAfterDelay(2, () -> {
			BlockPos hall = hall(helper);
			float raid = VillageRaids.chance(level, hall, 30);
			lore(helper, Map.of("runes_of_warding", 1));
			float warded = VillageRaids.chance(level, hall, 30);
			helper.assertTrue(Math.abs(warded - raid * 0.8f) < 1e-5f, "Runes of Warding I: " + raid + " -> " + warded);
			lore(helper, Map.of("iron_pact", 1));
			// the round: a day not a multiple of 5 brings nobody
			long n = level.getDayTime() / VillageNeeds.DAY + 10;
			level.setDayTime(n * VillageNeeds.DAY + LegendGuests.MORNING_FROM + 50);
			while (Chronicle.day(level) % 5 != 0) {
				n++;
				level.setDayTime(n * VillageNeeds.DAY + LegendGuests.MORNING_FROM + 50);
			}
			long pactDay = n;
			level.setDayTime((pactDay + 1) * VillageNeeds.DAY + LegendGuests.MORNING_FROM + 50);
			helper.assertTrue(Chronicle.day(level) % 5 != 0, "the day isn't off the pact's round: " + Chronicle.day(level));
			OldSage.round(level, hall);
			int golems = level.getEntitiesOfClass(IronGolem.class, VillageHalls.area(hall), IronGolem::isAlive).size();
			helper.assertTrue(golems == 0, "a pact golem on day " + Chronicle.day(level));
			level.setDayTime(pactDay * VillageNeeds.DAY + LegendGuests.MORNING_FROM + 50);
			OldSage.round(level, hall);
			golems = level.getEntitiesOfClass(IronGolem.class, VillageHalls.area(hall), IronGolem::isAlive).size();
			helper.assertTrue(golems == 1, "the pact's day brought " + golems + " golems");
			OldSage.round(level, hall);
			helper.assertTrue(OldSage.pactGolem(level, hall) == null, "15 villagers got a second golem");
			helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 16));
			helper.assertTrue(OldSage.pactGolem(level, hall) != null, "16 villagers got no second golem");
			helper.assertTrue(OldSage.pactGolem(level, hall) == null, "a third golem for 16 villagers");
			helper.succeed();
		});
	}
}
