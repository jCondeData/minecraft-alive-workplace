package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Decorations;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderCelebration;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderStages;
import io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.wonder.WondersPage;
import io.github.jcondedata.aliveworkplace.wonder.powers.BeautyPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.DeclaredPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.MoodPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.WonderPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.WonderPowers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * A finished Wonder (ROADMAP 35.8): the folly raised through its plan, every stage put up, is announced to every
 * player, celebrated with a festival that evening whose fireworks are the bigger show, written in its village's and its
 * caravan partner's chronicles and marked on the Village Map with a gold banner; its test {@code mood} and
 * {@code beauty} powers are on. Breaking 15% of it turns them off at the next day's count (the hall and the stone say
 * "damaged"), the village's builder repairs it from the stockpile (with {@code builderRepairs} off) and they come back.
 * The count goes by {@code wonderStanding}, counts weathered copper as the copper it was, and stops with Wonders off;
 * finished Wonders and their upkeep survive a save and reload, and old saves read as standing whole; the power types are
 * registered by id.
 */
public class WonderFinishedGameTests implements FabricGameTest {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final BlockPos HALL = new BlockPos(2, 2, 27);
	private static final BlockPos MIDDLE = new BlockPos(14, 2, 14);

	private record Village(ServerLevel level, ServerPlayer owner, List<Component> heard, BlockPos hall, Villager builder) {
	}

	/** A Town owned by a player who keeps what they're told, with one builder employed at a bench by the hall. */
	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer()));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = listener(helper, heard, "wonder-owner");
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), "Owner");
		hall.setRank(VillageRanks.Rank.TOWN);
		BlockPos bench = new BlockPos(5, 2, 28);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 26));
		Builders.employ(level, builder, helper.absolutePos(bench));
		return new Village(level, player, heard, helper.absolutePos(HALL), builder);
	}

	/** The folly's plan placed with its middle at {@link #MIDDLE} and handed to the village's builder, as a player does. */
	private static WonderSite begin(GameTestHelper helper, Village v) {
		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());
		ItemStack plan = WonderPlanItem.of(v.level(), v.hall(), folly, Component.literal("Testholm"));
		BlueprintData data = BlueprintItem.data(plan).orElseThrow();
		BlockPos origin = helper.absolutePos(MIDDLE).subtract(StructureTemplate.transform(new BlockPos(4, 0, 4), Mirror.NONE, Rotation.NONE, BlockPos.ZERO));
		plan.set(ModComponents.BLUEPRINT, data.withPlacement(Optional.of(new BlueprintData.Placement(v.level().dimension().location(), origin, Rotation.NONE, Mirror.NONE))));
		BlueprintData.Placement where = BlueprintItem.data(plan).flatMap(BlueprintData::placement).orElseThrow();
		WonderGoodsKit.stockAll(v.level(), FOLLY, where); // every stage's goods in the stockpile
		helper.assertTrue(WonderStages.handOver(v.owner(), v.builder(), plan) == InteractionResult.SUCCESS, "the plan wasn't handed over");
		WonderSite site = WonderManager.get(v.level()).in(v.hall()).get(0);
		UUID id = site.id();
		Leftovers.after(helper, () -> forget(v.level(), id));
		return site;
	}

	private static void forget(ServerLevel level, UUID wonder) {
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSite s : List.copyOf(sites.all())) {
			if (wonder.equals(s.wonder())) {
				sites.remove(s.id());
			}
		}
		WonderManager.get(level).remove(wonder);
	}

	/**
	 * Puts up every stage of the Wonder {@code id} at once, each part's blocks where its plan has them, its site done and
	 * gone as a builder's finished part is, and moves the Wonder on (the goods are in the stockpile) until it's finished.
	 */
	private static WonderSite raise(GameTestHelper helper, ServerLevel level, UUID id) {
		WonderManager manager = WonderManager.get(level);
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (int round = 0; round < 12 && manager.site(id).map(WonderSite::goingUp).orElse(false); round++) {
			for (UUID partId : manager.site(id).orElseThrow().parts()) {
				BuildSite part = sites.get(partId);
				if (part == null || part.isDone()) {
					continue;
				}
				BuildPlan plan = part.plan(level);
				for (BuildPlan.Stage stage : new BuildPlan.Stage[] {BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION}) {
					for (BuildPlan.Step s : plan.steps(stage)) {
						level.setBlock(s.pos(), s.state(), 2 | 16);
					}
				}
				for (int i = 0; i < 20 && !part.isDone(); i++) {
					part.finishList();
				}
				sites.remove(partId);
			}
			WonderStages.tick(level);
		}
		WonderSite done = manager.site(id).orElseThrow();
		helper.assertTrue(done.done(), "the folly wasn't finished: " + done);
		return done;
	}

	private static boolean said(List<Component> heard, String key) {
		return heard.stream().anyMatch(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals(key));
	}

	private static List<String> chronicle(ServerLevel level, BlockPos hall) {
		return ((VillageHallBlockEntity) level.getBlockEntity(hall)).chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.WONDER)
			.map(e -> e.text().getString()).toList();
	}

	/** No line a player reads is a raw translation key. */
	private static void translated(GameTestHelper helper, List<Component> lines) {
		for (Component c : lines) {
			String text = c.getString();
			helper.assertTrue(!text.contains("aliveworkplace.") && !text.contains("%s"), "untranslated: " + text);
		}
	}

	/**
	 * The folly raised through its plan: its mood and beauty powers on; 15% of it broken, the next day's count turns them
	 * off, the hall's card and the stone say "damaged", the builder repairs it (with builderRepairs off) and they come back.
	 */
	//$ gametest_ticks_batch TALL '16000' '"wonderFinishedUpkeep"'
	@GameTest(template = TALL, timeoutTicks = 16000, batch = "wonderFinishedUpkeep")
	public void aFinishedFollysPowersStopWhenDamagedAndComeBackRepaired(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		int beautyBefore = Decorations.beauty(level, v.hall());
		helper.assertTrue(Wonders.power(level, v.hall(), MoodPower.TYPE).isEmpty(), "a mood power before the folly is finished");
		WonderSite begun = begin(helper, v);
		UUID id = begun.id();
		WonderSite done = raise(helper, level, id);
		helper.assertTrue(done.empowered() && done.kept().standing() == 100, "the finished folly: " + done.kept());
		Optional<WonderPower> mood = Wonders.power(level, v.hall(), MoodPower.TYPE);
		helper.assertTrue(mood.isPresent() && mood.get() instanceof MoodPower m && m.points(true) == 4 && !m.realm(), "the folly's mood power: " + mood);
		helper.assertTrue(Decorations.beauty(level, v.hall()) == beautyBefore + 2, "the folly's 2 beauty: " + beautyBefore + " → " + Decorations.beauty(level, v.hall()));
		Moods.Mood happy = Moods.work(level, v.builder());
		helper.assertTrue(happy.good().stream().anyMatch(c -> c.getString().equals("proud of the Test Folly")), "the builder's reasons: "
			+ happy.good().stream().map(Component::getString).toList());

		// 15% of it broken (a fixed random pick), then the next day's count
		List<WonderUpkeep.Block> blocks = WonderUpkeep.blocks(level, done);
		helper.assertTrue(blocks.size() > 100, "the folly's blocks: " + blocks.size());
		List<WonderUpkeep.Block> pick = new ArrayList<>(blocks);
		net.minecraft.Util.shuffle(pick, RandomSource.create(35008L));
		int broken = (int) Math.ceil(blocks.size() * 0.15);
		for (WonderUpkeep.Block b : pick.subList(0, broken)) {
			level.setBlock(b.pos(), Blocks.AIR.defaultBlockState(), 2 | 16);
		}
		helper.assertTrue(WonderManager.get(level).site(id).orElseThrow().empowered(), "the powers went off before the count");
		long nextDay = (level.getDayTime() / 24000 + 1) * 24000;
		level.setDayTime(nextDay + 2000);
		AtomicInteger phase = new AtomicInteger();
		helper.onEachTick(() -> {
			if (level.getDayTime() > nextDay + 8000) {
				level.setDayTime(nextDay + 2000); // the builders' working hours, on the same day
			}
			WonderSite now = WonderManager.get(level).site(id).orElse(null);
			if (now == null) {
				return;
			}
			if (phase.get() == 0 && now.kept().damaged()) {
				helper.assertTrue(now.kept().standing() <= 85 && now.kept().counted() == nextDay / 24000 + 1, "the count: " + now.kept());
				helper.assertTrue(Wonders.power(level, v.hall(), MoodPower.TYPE).isEmpty() && Decorations.beauty(level, v.hall()) == beautyBefore,
					"the powers stay on while it's damaged");
				helper.assertTrue(Moods.work(level, v.builder()).good().stream().noneMatch(c -> c.getString().contains("Test Folly")), "still proud of a damaged folly");
				helper.assertTrue(said(v.heard(), "message.aliveworkplace.wonder.damaged"), "the owner wasn't told: " + v.heard());
				helper.assertTrue(chronicle(level, v.hall()).stream().anyMatch(l -> l.startsWith("The Test Folly was damaged: only ")), "chronicle: " + chronicle(level, v.hall()));
				String stone = WonderStages.statusText(level, now.stone().orElseThrow()).getString();
				helper.assertTrue(stone.contains("Damaged: " + now.kept().standing() + "% of it stands (90% needed)"), "the stone says: " + stone);
				ItemStack card = WondersPage.card(level, v.hall(), WondersPage.cards(level, v.hall()).stream().filter(c -> c.wonder().id().equals(FOLLY)).findFirst().orElseThrow());
				List<Component> lore = card.get(DataComponents.LORE).lines();
				helper.assertTrue(lore.stream().anyMatch(c -> c.getString().startsWith("Damaged: ")) && lore.stream().anyMatch(c -> c.getString().contains("(stopped)")),
					"the hall's card: " + lore.stream().map(Component::getString).toList());
				translated(helper, lore);
				phase.set(1);
			}
			if (phase.get() == 1 && BuildSiteManager.get(level).all().stream().anyMatch(s -> id.equals(s.wonder()) && s.isRepair() && v.builder().getUUID().equals(s.builder()))) {
				phase.set(2); // the builder took the repair on
			}
			if (phase.get() == 2 && !now.kept().damaged()) {
				phase.set(3);
			}
		});
		helper.succeedWhen(() -> {
			WonderSite now = WonderManager.get(level).site(id).orElseThrow();
			helper.assertTrue(phase.get() == 3, "phase " + phase.get() + ": " + now.kept() + ", repairs "
				+ BuildSiteManager.get(level).all().stream().filter(s -> id.equals(s.wonder())).map(s -> s.stage() + " " + s.placed()).toList());
			helper.assertTrue(now.kept().standing() >= 90, "repaired to " + now.kept().standing() + "%");
			helper.assertTrue(Wonders.power(level, v.hall(), MoodPower.TYPE).isPresent() && Decorations.beauty(level, v.hall()) == beautyBefore + 2, "the powers came back");
			helper.assertTrue(said(v.heard(), "message.aliveworkplace.wonder.repairing") && said(v.heard(), "message.aliveworkplace.wonder.repaired"),
				"the owner's lines: " + v.heard().stream().map(Component::getString).toList());
			helper.assertTrue(chronicle(level, v.hall()).contains("The Test Folly was repaired, and its powers returned"), "chronicle: " + chronicle(level, v.hall()));
			translated(helper, v.heard());
		});
	}

	/**
	 * The folly finished: every player on the server is told (one far away too), its festival is that evening with the
	 * bigger show, the chronicles of its village and of its caravan partner record it, and the Village Map has its gold banner.
	 */
	//$ gametest_ticks_batch TALL '200' '"wonderFinishedTold"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "wonderFinishedTold")
	public void aFinishedWonderIsToldCelebratedAndChronicled(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		BlockPos partnerRel = new BlockPos(27, 2, 27);
		helper.setBlock(partnerRel, ModBlocks.VILLAGE_HALL);
		BlockPos partner = helper.absolutePos(partnerRel);
		Caravans.Data caravans = Caravans.Data.get(level);
		helper.assertTrue(caravans.toggleRoute(v.hall(), partner, 10), "no caravan route");
		Leftovers.after(helper, () -> {
			if (caravans.routesFrom(v.hall()).contains(partner)) {
				caravans.toggleRoute(v.hall(), partner, 10);
			}
		});
		List<Component> far = new ArrayList<>();
		ServerPlayer away = listener(helper, far, "far-away");
		away.teleportTo(v.hall().getX() + 5000, 100, v.hall().getZ());
		WonderSite done = raise(helper, level, begin(helper, v).id());
		long today = Chronicle.day(level);

		String village = VillageHalls.name(level, v.hall()).getString();
		String told = village + " has finished the Test Folly!";
		helper.assertTrue(v.heard().stream().anyMatch(c -> c.getString().equals(told)), "the owner wasn't told \"" + told + "\": "
			+ v.heard().stream().map(Component::getString).toList());
		helper.assertTrue(far.stream().anyMatch(c -> c.getString().equals(told)), "a player 5,000 blocks away wasn't told: " + far.stream().map(Component::getString).toList());
		helper.assertTrue(chronicle(level, v.hall()).contains("The Test Folly was finished, its Foundation Stone laid on day " + done.begun()),
			"the village's chronicle: " + chronicle(level, v.hall()));
		helper.assertTrue(chronicle(level, partner).contains(village + " finished the Test Folly"), "the partner's chronicle: " + chronicle(level, partner));

		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		helper.assertTrue(hall.festivalDay() == today && done.kept().festival() == today, "the festival: day " + hall.festivalDay() + ", kept " + done.kept());
		helper.assertTrue(WonderCelebration.celebrating(level, v.hall()).isPresent() && WonderCelebration.celebrating(level, partner).isEmpty(), "whose show");
		int rockets = WonderCelebration.show(level, v.hall(), v.hall(), RandomSource.create(358L));
		List<FireworkRocketEntity> up = level.getEntitiesOfClass(FireworkRocketEntity.class, new AABB(helper.absolutePos(MIDDLE)).inflate(40));
		helper.assertTrue(rockets == WonderCelebration.ROCKETS && up.size() >= rockets, "the Wonder's show: " + rockets + " rockets, " + up.size() + " up");
		helper.assertTrue(up.stream().anyMatch(r -> r.getY() > helper.absolutePos(MIDDLE).getY() + 15), "no rocket from the folly's top");
		helper.assertTrue(WonderCelebration.show(level, partner, partner, RandomSource.create(1L)) == 0, "the partner had the Wonder's show");
		up.forEach(net.minecraft.world.entity.Entity::discard);

		List<VillageMaps.Mark> marks = VillageMaps.marks(level, v.hall(), 64);
		helper.assertTrue(marks.stream().anyMatch(m -> m.kind() == VillageMaps.Kind.WONDER && m.name().getString().equals("Test Folly")
			&& VillageMaps.Kind.WONDER.color == net.minecraft.world.item.DyeColor.YELLOW), "no gold banner on the map: " + marks);
		helper.assertTrue(VillageMaps.kindOf(ResourceLocation.parse("aliveworkplace_test:wonder/test_folly/stage_1_0")).isEmpty(), "a part on the map");
		translated(helper, v.heard());
		helper.succeed();
	}

	/**
	 * B138: the builder finishing a part of a Wonder tells the owner about the Wonder's stage, never "finished building
	 * Stage 1 0! The blueprint is back in the supply chest." A part with others of its stage still open says how many
	 * stand; the stage's last part says the stage is finished; the Wonder's very last part says nothing itself, as
	 * everyone hears the Wonder is finished.
	 */
	//$ gametest_ticks_batch TALL '200' '"wonderPartTold"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "wonderPartTold")
	public void aFinishedWonderPartIsToldAsAPartOfItsStage(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		WonderManager manager = WonderManager.get(level);
		BuildSiteManager sites = BuildSiteManager.get(level);
		UUID id = begin(helper, v).id();
		for (int i = 0; i < 5 && manager.site(id).orElseThrow().parts().isEmpty(); i++) {
			WonderStages.tick(level);
		}
		WonderSite site = manager.site(id).orElseThrow();
		helper.assertTrue(site.stage() == 1 && site.parts().size() == 1, "stage I didn't start: " + site);
		// Stage I given a second part, as the Great Wall's and the Cathedral's stages have several.
		BuildSite first = sites.get(site.parts().get(0));
		BuildSite second = sites.create(first.owner(), "Owner", first.structure(), first.placement());
		second.setWonder(id);
		manager.setParts(id, List.of(first.id(), second.id()));
		String who = v.builder().getDisplayName().getString();

		finishPart(level, v.builder(), first);
		List<String> lines = v.heard().stream().map(Component::getString).toList();
		String partLine = who + " finished a part of the Test Folly: 1 of 2 parts of stage 1 (The Ground Story) stand.";
		helper.assertTrue(lines.contains(partLine), "the first part: \"" + partLine + "\" not in " + lines);

		v.heard().clear();
		finishPart(level, v.builder(), second);
		lines = v.heard().stream().map(Component::getString).toList();
		String stageLine = who + " finished stage 1 of 3 of the Test Folly: The Ground Story.";
		helper.assertTrue(lines.contains(stageLine), "the stage's last part: \"" + stageLine + "\" not in " + lines);
		helper.assertTrue(!said(v.heard(), "message.aliveworkplace.wonder.part_built"), "the stage's last part told as one of several: " + lines);

		// Stages II and III, each part finished by the builder; the Wonder's last part leaves the telling to the Wonder.
		List<Component> all = new ArrayList<>(v.heard());
		for (int round = 0; round < 12 && manager.site(id).map(WonderSite::goingUp).orElse(false); round++) {
			WonderStages.tick(level);
			WonderSite now = manager.site(id).orElseThrow();
			for (UUID partId : now.parts()) {
				BuildSite part = sites.get(partId);
				if (part == null || part.isDone()) {
					continue;
				}
				v.heard().clear();
				finishPart(level, v.builder(), part);
				lines = v.heard().stream().map(Component::getString).toList();
				if (now.stage() == 2) {
					helper.assertTrue(lines.contains(who + " finished stage 2 of 3 of the Test Folly: The Upper Story."), "stage II: " + lines);
				} else {
					helper.assertTrue(lines.stream().noneMatch(l -> l.startsWith(who + " finished")), "the Wonder's last part told by the builder too: " + lines);
				}
				all.addAll(v.heard());
			}
		}
		helper.assertTrue(manager.site(id).orElseThrow().done(), "the folly wasn't finished");
		all.addAll(v.heard());
		String village = VillageHalls.name(level, v.hall()).getString();
		helper.assertTrue(all.stream().anyMatch(c -> c.getString().equals(village + " has finished the Test Folly!")), "the finish wasn't announced: "
			+ all.stream().map(Component::getString).toList());
		helper.assertTrue(!said(all, "message.aliveworkplace.finished"), "a part told as a building with its blueprint back: "
			+ all.stream().map(Component::getString).toList());
		// B143: no part is chronicled as a building ("Odo finished the Stage 1 0") nor kept among the village's buildings
		List<String> built = ((VillageHallBlockEntity) level.getBlockEntity(v.hall())).chronicle().stream()
			.filter(e -> e.kind() == Chronicle.Kind.BUILT).map(e -> e.text().getString()).toList();
		helper.assertTrue(built.isEmpty(), "a Wonder part chronicled as a building: " + built);
		List<String> records = sites.finishedNear(level, v.hall(), 64).stream().map(f -> f.structure().toString())
			.filter(s -> s.contains("wonder/")).toList();
		helper.assertTrue(records.isEmpty(), "a Wonder part recorded among the village's buildings: " + records);
		// ...and a second Wonder still may not go down over the finished one
		WonderSite finished = manager.site(id).orElseThrow();
		ItemStack plan = WonderPlanItem.of(level, v.hall(), Wonders.loaded().get(FOLLY), Component.literal("Testholm"));
		net.minecraft.core.Vec3i size = BlueprintItem.data(plan).get().size().get();
		Optional<Component> over = io.github.jcondedata.aliveworkplace.wonder.WonderPlans.placeRefusal(level, WonderPlanItem.plan(plan).get(),
			finished.placement().orElseThrow(), size);
		helper.assertTrue(over.map(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : "").orElse("")
			.equals("message.aliveworkplace.wonder_plan.over_wonder"), "a plan over the finished Wonder: " + over.map(Component::getString));
		translated(helper, all);
		helper.succeed();
	}

	/**
	 * B143: a Wonder's parts finished by the builder write no "X finished the <part>" line in the village's chronicle
	 * (with the part's raw name) and aren't put on the village's books as buildings; the chronicle holds only the
	 * Wonder's own lines (begun, each stage dedicated, finished). An ordinary building finished by the same builder still
	 * writes its line and goes on the books.
	 */
	//$ gametest_ticks_batch TALL '200' '"wonderPartChronicle"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "wonderPartChronicle")
	public void aWonderPartWritesNoBuildingLineInTheChronicle(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		WonderManager manager = WonderManager.get(level);
		BuildSiteManager sites = BuildSiteManager.get(level);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(v.hall());
		UUID id = begin(helper, v).id();
		List<String> partNames = new ArrayList<>();
		for (int round = 0; round < 12 && manager.site(id).map(WonderSite::goingUp).orElse(false); round++) {
			WonderStages.tick(level);
			for (UUID partId : manager.site(id).orElseThrow().parts()) {
				BuildSite part = sites.get(partId);
				if (part == null || part.isDone()) {
					continue;
				}
				partNames.add(io.github.jcondedata.aliveworkplace.blueprint.Blueprints.displayName(part.structure()).getString());
				finishPart(level, v.builder(), part);
				helper.assertTrue(sites.finishedNear(level, part.placement().origin(), 1).stream().noneMatch(f -> f.placement().equals(part.placement())),
					"a Wonder's part went on the books as a building: " + part.structure());
			}
		}
		helper.assertTrue(manager.site(id).orElseThrow().done(), "the folly wasn't finished");
		helper.assertTrue(partNames.size() >= 3, "the builder finished too few parts: " + partNames);
		List<String> all = hall.chronicle().stream().map(e -> e.text().getString()).toList();
		helper.assertTrue(hall.chronicle().stream().noneMatch(e -> e.kind() == Chronicle.Kind.BUILT), "a building line for a Wonder's part: " + all);
		for (String name : partNames) {
			helper.assertTrue(all.stream().noneMatch(l -> l.contains(name)), "the part's raw name \"" + name + "\" in the chronicle: " + all);
		}
		List<String> wonder = chronicle(level, v.hall());
		helper.assertTrue(wonder.stream().anyMatch(l -> l.contains("laid the Foundation Stone of the Test Folly")), "no begun line: " + all);
		helper.assertTrue(wonder.stream().anyMatch(l -> l.startsWith("Stage 1, The Ground Storey, of the Test Folly was dedicated")), "no dedication line: " + all);
		helper.assertTrue(wonder.stream().anyMatch(l -> l.startsWith("The Test Folly was finished")), "no finished line: " + all);
		for (Chronicle.Entry e : hall.chronicle()) {
			helper.assertTrue(!e.text().getString().contains("aliveworkplace.") && !e.text().getString().contains("%s"), "untranslated: " + e.text().getString());
		}

		// An ordinary building by the same builder still writes its line and goes on the books.
		ResourceLocation pillar = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_pillar");
		BlueprintData.Placement where = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(24, 2, 26)), Rotation.NONE, Mirror.NONE);
		BuildSite house = sites.create(v.owner().getUUID(), "Owner", pillar, where);
		Leftovers.after(helper, () -> {
			sites.remove(house.id());
			sites.forgetFinished(where);
		});
		finishPart(level, v.builder(), house);
		String line = v.builder().getDisplayName().getString() + " finished the " + io.github.jcondedata.aliveworkplace.blueprint.Blueprints.displayName(pillar).getString();
		helper.assertTrue(hall.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.BUILT && e.text().getString().equals(line)),
			"the ordinary building's line \"" + line + "\" not in " + hall.chronicle().stream().map(e -> e.text().getString()).toList());
		helper.assertTrue(sites.finishedNear(level, where.origin(), 1).stream().anyMatch(f -> f.placement().equals(where)), "the ordinary building isn't on the books");
		helper.succeed();
	}

	/** {@code part}'s steps done and its finish run as the builder's, as BuilderWork does at the part's last step. */
	private static void finishPart(ServerLevel level, Villager builder, BuildSite part) {
		for (int i = 0; i < 20 && !part.isDone(); i++) {
			part.finishList();
		}
		Builders.finish(level, builder, part);
	}

	/** Finished Wonders and their upkeep survive a save and reload; the powers come from what was loaded; an old save reads standing whole. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void finishedWondersSurviveASaveAndReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var registries = level.registryAccess();
		BlockPos hall = new BlockPos(4300, 70, 4000);
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(FOLLY, hall, UUID.randomUUID(), "Jesse", 3);
		manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), hall.offset(10, 0, 0), Rotation.CLOCKWISE_90, Mirror.NONE), hall.offset(9, 0, -1));
		manager.finish(site.id(), 7);
		manager.setKept(site.id(), new WonderSite.Kept(97, 8, false, 7));
		try {
			if (!level.hasChunk((hall.getX() + 10) >> 4, hall.getZ() >> 4)) {
				helper.assertTrue(WonderUpkeep.countNow(level, site.id()) == -1, "a Wonder counted with its chunks unloaded");
				helper.assertTrue(manager.site(site.id()).orElseThrow().kept().standing() == 97, "an unloaded Wonder's count changed");
			}
			WonderSite before = manager.site(site.id()).orElseThrow();
			CompoundTag saved = manager.save(new CompoundTag(), registries);
			WonderManager back = WonderManager.load(saved, registries);
			helper.assertTrue(back.site(site.id()).orElseThrow().equals(before), "read back: " + back.site(site.id()) + " vs " + before);
			// the powers come from the loaded copy, as after a restart
			level.getDataStorage().set(WonderManager.NAME, back);
			try {
				helper.assertTrue(Wonders.power(level, hall, MoodPower.TYPE).map(p -> ((MoodPower) p).points(true)).equals(Optional.of(4))
					&& Wonders.power(level, hall, BeautyPower.TYPE).isPresent(), "no powers from the loaded Wonder");
				back.setKept(site.id(), new WonderSite.Kept(80, 9, true, 7));
				helper.assertTrue(Wonders.power(level, hall, MoodPower.TYPE).isEmpty(), "a damaged Wonder's power after the reload");
				WonderManager again = WonderManager.load(back.save(new CompoundTag(), registries), registries);
				helper.assertTrue(again.site(site.id()).orElseThrow().kept().equals(new WonderSite.Kept(80, 9, true, 7)), "damaged read back: " + again.site(site.id()));
			} finally {
				level.getDataStorage().set(WonderManager.NAME, manager);
				Wonders.forgetPowers();
			}
			// saved before 35.8: no standing, counted, damaged or festival
			CompoundTag old = saved.copy();
			// this test's own entry: another test's Wonder may be on the same level's list, ahead of it (B204)
			net.minecraft.nbt.ListTag sites = old.getList("sites", 10);
			CompoundTag entry = null;
			for (int i = 0; i < sites.size(); i++) {
				if (site.id().equals(net.minecraft.nbt.NbtUtils.loadUUID(sites.getCompound(i).get("id")))) {
					entry = sites.getCompound(i);
				}
			}
			helper.assertTrue(entry != null, "this Wonder isn't in the save");
			for (String key : List.of("standing", "counted", "festival")) { // (damaged false, the default, isn't written)
				helper.assertTrue(entry.contains(key), "not saved: " + key);
				entry.remove(key);
			}
			helper.assertTrue(!entry.contains("damaged"), "a default written");
			WonderSite plain = WonderManager.load(old, registries).site(site.id()).orElseThrow();
			helper.assertTrue(plain.kept().equals(WonderSite.Kept.WHOLE) && plain.empowered(), "an old save: " + plain.kept());
		} finally {
			manager.remove(site.id());
		}
		helper.succeed();
	}

	/**
	 * The count, without builders: the whole folly stands at 100%; 15% broken is damaged under wonderStanding 90, not
	 * under 50; weathered and waxed copper stand as the copper drawn; with Wonders off there are no powers and no count.
	 */
	//$ gametest_batch TALL '"wonderStanding"'
	@GameTest(template = TALL, batch = "wonderStanding")
	public void theCountGoesByWonderStandingCopperAndTheSwitch(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.finished(helper);
		BlockPos hall = helper.absolutePos(HALL);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(FOLLY, hall, null, "", 1);
		BlockPos origin = helper.absolutePos(MIDDLE).offset(-4, 0, -4);
		manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE), origin.offset(-1, 0, -1));
		manager.finish(site.id(), 1);
		int standing = WonderUpkeep.STANDING;
		boolean on = Wonders.ENABLED;
		Leftovers.after(helper, () -> {
			WonderUpkeep.STANDING = standing;
			Wonders.ENABLED = on;
			manager.remove(site.id());
		});
		WonderSite done = manager.site(site.id()).orElseThrow();
		List<WonderUpkeep.Block> blocks = WonderUpkeep.blocks(level, done);
		for (WonderUpkeep.Block b : blocks) {
			level.setBlock(b.pos(), b.state(), 2 | 16);
		}
		helper.assertTrue(WonderUpkeep.countNow(level, site.id()) == 100, "the whole folly");
		List<WonderUpkeep.Block> pick = new ArrayList<>(blocks);
		net.minecraft.Util.shuffle(pick, RandomSource.create(15L));
		for (WonderUpkeep.Block b : pick.subList(0, (int) Math.ceil(blocks.size() * 0.15))) {
			level.setBlock(b.pos(), Blocks.AIR.defaultBlockState(), 2 | 16);
		}
		int left = WonderUpkeep.countNow(level, site.id());
		helper.assertTrue(left <= 85 && manager.site(site.id()).orElseThrow().kept().damaged() && Wonders.power(level, hall, MoodPower.TYPE).isEmpty(),
			"15% broken under 90: " + left + "%, " + manager.site(site.id()).orElseThrow().kept());
		WonderUpkeep.STANDING = 50;
		WonderUpkeep.countNow(level, site.id());
		helper.assertTrue(!manager.site(site.id()).orElseThrow().kept().damaged() && Wonders.power(level, hall, MoodPower.TYPE).isPresent(), "under wonderStanding 50");
		Wonders.ENABLED = false;
		helper.assertTrue(Wonders.power(level, hall, MoodPower.TYPE).isEmpty() && Wonders.power(level, hall, BeautyPower.TYPE).isEmpty(), "powers with Wonders off");
		helper.assertTrue(!WonderUpkeep.anyDamaged(level), "a Wonder to repair with Wonders off");
		Wonders.ENABLED = on;
		WonderUpkeep.STANDING = standing;

		// copper as drawn, weathered or waxed, still stands; another block doesn't
		BlockState copper = Blocks.CUT_COPPER_STAIRS.defaultBlockState();
		WonderUpkeep.Block roof = new WonderUpkeep.Block(BlockPos.ZERO, copper, null);
		helper.assertTrue(WonderUpkeep.stands(Blocks.OXIDIZED_CUT_COPPER_STAIRS.defaultBlockState(), roof)
			&& WonderUpkeep.stands(Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS.defaultBlockState(), roof)
			&& WonderUpkeep.stands(Blocks.WAXED_CUT_COPPER_STAIRS.defaultBlockState(), roof), "weathered copper doesn't stand");
		helper.assertTrue(!WonderUpkeep.stands(Blocks.STONE_STAIRS.defaultBlockState(), roof) && !WonderUpkeep.stands(Blocks.AIR.defaultBlockState(), roof), "stone stands for copper");
		helper.assertTrue(WonderUpkeep.stands(Blocks.WHITE_WOOL.defaultBlockState(), new WonderUpkeep.Block(BlockPos.ZERO, Blocks.RED_WOOL.defaultBlockState(),
			Blocks.WHITE_WOOL.defaultBlockState())), "a seat in the colours it was drawn in");

		// the status lines a player reads
		WonderSite now = manager.site(site.id()).orElseThrow();
		translated(helper, List.of(WonderStages.standingLine(now), WonderStages.statusText(level, now.stone().orElseThrow())));
		for (String line : Wonders.listLines(level.getServer()).stream().map(Component::getString).toList()) {
			helper.assertTrue(!line.contains("aliveworkplace."), "untranslated: " + line);
		}
		helper.succeed();
	}

	/**
	 * The power types are registered by id: our 18, mood and beauty read their numbers (and refuse bad ones), a type of a
	 * Wonder's own registered later reads a file naming it, an unknown one fails the file; every type has its line.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void powerTypesAreRegisteredById(GameTestHelper helper) {
		helper.assertTrue(WonderPowers.types().size() >= 18 && WonderPowers.types().containsAll(WonderPowers.DECLARED)
			&& WonderPowers.types().contains("mood") && WonderPowers.types().contains("beauty"), "types: " + WonderPowers.types());
		WonderPower realm = WonderPowers.parse(json("{\"type\": \"mood\", \"scope\": \"realm\", \"amount\": 8, \"home_amount\": 12}"));
		helper.assertTrue(realm instanceof MoodPower m && m.realm() && m.points(false) == 8 && m.points(true) == 12, "the Colossus's mood: " + realm);
		helper.assertTrue(WonderPowers.parse(json("{\"type\": \"beauty\", \"points\": 8}")).equals(new BeautyPower(8)), "beauty");
		WonderPower range = WonderPowers.parse(json("{\"type\": \"caravan_range\", \"factor\": 2}"));
		helper.assertTrue(range instanceof DeclaredPower d && d.number("factor", 1) == 2 && !d.realm(), "a declared type: " + range);
		helper.assertTrue(WonderPowers.parse(json("{\"type\": \"harvest\", \"scope\": \"realm\", \"extra_every\": 4}")).realm(), "a declared realm power");
		for (String bad : List.of("{\"type\": \"mood\", \"scope\": \"world\", \"amount\": 4}", "{\"type\": \"mood\"}", "{\"type\": \"beauty\", \"points\": -1}",
			"{\"type\": \"moood\"}")) {
			try {
				WonderPowers.parse(json(bad));
				helper.fail("read: " + bad);
			} catch (IllegalArgumentException expected) {
				// refused, with the reason the file is logged with
			}
		}
		WonderPowers.register("aliveworkplace_test_glow", j -> new DeclaredPower("aliveworkplace_test_glow", j));
		Map<ResourceLocation, Wonder> loaded = Wonders.load(Map.of(ResourceLocation.parse("owner_pack:wonders/glow.json"),
			WonderDataGameTests.GOOD.replace("\"type\": \"mood\", \"scope\": \"village\", \"amount\": 4", "\"type\": \"aliveworkplace_test_glow\"")));
		Wonder glow = loaded.get(ResourceLocation.parse("owner_pack:glow"));
		helper.assertTrue(glow != null && glow.powers().get(0).power().type().equals("aliveworkplace_test_glow"), "a registered type's file: " + loaded + " " + Wonders.problems());
		for (String type : List.of("mood", "beauty")) {
			helper.assertTrue(!Component.translatable("wonder.aliveworkplace.power." + type, 1).getString().contains("aliveworkplace."), "no line for " + type);
		}
		for (String type : WonderPowers.DECLARED) {
			String line = new DeclaredPower(type, new JsonObject()).describe().getString();
			helper.assertTrue(!line.contains("aliveworkplace."), "no line for " + type);
		}
		helper.assertTrue(!new MoodPower(true, 8, 12).describe().getString().contains("aliveworkplace.")
			&& !new MoodPower(true, 8, 8).describe().getString().contains("aliveworkplace."), "the realm mood's lines");
		helper.succeed();
	}

	/** A player who keeps every line they're sent: chat, system lines and server-wide announcements. */
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
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	private static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}
}
