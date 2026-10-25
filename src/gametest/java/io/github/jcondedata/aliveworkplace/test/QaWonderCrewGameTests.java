package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderCrews;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderStages;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.wonder.WondersPage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA for ROADMAP 35.5 (Crews: every builder on one Wonder), written from the spec: the {@code wonderCrew} cap from both
 * sides (2 with four idle builders, and the default 12 counting the lead), the village's edge for a builder's Blueprint
 * Table, a builder on a build of its own coming only once it ends (and, with builds still queued, only once called),
 * the Master Architect leading over an equal Master, the highest-level member leading the next stage and named on the
 * stone and the hall, a crew of three sharing no column on any tick while a member's table is broken mid-stage.
 */
public class QaWonderCrewGameTests implements FabricGameTest {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final ResourceLocation WHOLE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "wonder/test_folly");
	private static final ResourceLocation DRAWN = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "wonder_drawn/test_folly");
	private static final BlockPos HALL = new BlockPos(2, 2, 27);
	private static final BlockPos MIDDLE = new BlockPos(14, 2, 14);

	record Village(ServerLevel level, ServerPlayer owner, BlockPos hall, List<Villager> builders, List<BlockPos> benches) {
	}

	/** A Town owned by a player, with {@code count} builders employed at Blueprint Tables in a row by the hall. */
	static Village village(GameTestHelper helper, int count, int delay) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int was = level.getGameRules().getRule(ModGameRules.BUILD_DELAY).get();
		boolean helped = level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).get();
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(was, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(helped, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), "Owner");
		hall.setRank(VillageRanks.Rank.TOWN);
		Village v = new Village(level, player, helper.absolutePos(HALL), new ArrayList<>(), new ArrayList<>());
		for (int i = 0; i < count; i++) {
			builder(helper, v, new BlockPos(5 + 2 * i, 2, 28), new BlockPos(5 + 2 * i, 2, 26));
		}
		return v;
	}

	/** One more builder for {@code v}, employed at a Blueprint Table at {@code bench}. */
	static Villager builder(GameTestHelper helper, Village v, BlockPos bench, BlockPos at) {
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, at);
		Builders.employ(v.level(), builder, helper.absolutePos(bench));
		v.builders().add(builder);
		v.benches().add(helper.absolutePos(bench));
		return builder;
	}

	/** The folly's plan placed with its middle at {@link #MIDDLE}, every stage's goods in, handed to {@code to}. */
	static WonderSite begin(GameTestHelper helper, Village v, Villager to) {
		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());
		ItemStack plan = WonderPlanItem.of(v.level(), v.hall(), folly, Component.literal("Testholm"));
		BlueprintData data = BlueprintItem.data(plan).orElseThrow();
		BlockPos origin = helper.absolutePos(MIDDLE).subtract(StructureTemplate.transform(new BlockPos(4, 0, 4), Mirror.NONE, Rotation.NONE, BlockPos.ZERO));
		plan.set(ModComponents.BLUEPRINT, data.withPlacement(Optional.of(new BlueprintData.Placement(v.level().dimension().location(), origin, Rotation.NONE, Mirror.NONE))));
		BlueprintData.Placement where = BlueprintItem.data(plan).flatMap(BlueprintData::placement).orElseThrow();
		WonderGoodsKit.stockAll(v.level(), FOLLY, where);
		helper.assertTrue(WonderStages.handOver(v.owner(), to, plan) == InteractionResult.SUCCESS, "the folly's plan wasn't taken");
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
		WonderCrews.forget(wonder);
	}

	static WonderSite now(ServerLevel level, UUID id) {
		return WonderManager.get(level).site(id).orElse(null);
	}

	/** The lead's name as the stone's crew line shows it (the first argument of {@code wonder.status.crew}). */
	static String stoneLead(ServerLevel level, WonderSite site) {
		Component status = WonderStages.statusText(level, site.stone().orElseThrow());
		String found = crewArg(status, "message.aliveworkplace.wonder.status.crew");
		return found == null ? "<no crew line: " + status.getString() + ">" : found;
	}

	private static String crewArg(Component c, String key) {
		if (c.getContents() instanceof TranslatableContents t) {
			if (t.getKey().equals(key) && t.getArgs().length > 0) {
				Object a = t.getArgs()[0];
				return a instanceof Component comp ? comp.getString() : String.valueOf(a);
			}
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component a) {
					String in = crewArg(a, key);
					if (in != null) {
						return in;
					}
				}
			}
		}
		for (Component s : c.getSiblings()) {
			String in = crewArg(s, key);
			if (in != null) {
				return in;
			}
		}
		return null;
	}

	/** The hall's Wonders page card for the folly, as its lore reads. */
	static String hallCard(ServerLevel level, BlockPos hall) {
		StringBuilder out = new StringBuilder();
		for (WondersPage.Card card : WondersPage.cards(level, hall)) {
			ItemStack icon = WondersPage.card(level, hall, card);
			ItemLore lore = icon.get(DataComponents.LORE);
			if (lore != null && card.site().isPresent()) {
				lore.lines().forEach(l -> out.append(l.getString()).append(" | "));
			}
		}
		return out.toString();
	}

	/** Every tick: no two members (nor a member and the lead) on one column of any part. Returns the most on one part. */
	private static int columnsCheck(GameTestHelper helper, ServerLevel level, WonderSite site) {
		int most = 0;
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (UUID id : site.parts()) {
			BuildSite part = sites.get(id);
			if (part == null || part.isDone()) {
				continue;
			}
			BuildPlan plan = part.plan(level);
			if (plan == null) {
				continue;
			}
			Map<UUID, Long> columns = part.crewColumns();
			helper.assertTrue(new HashSet<>(columns.values()).size() == columns.size(), "two members hold one column: " + columns + " at " + level.getGameTime());
			Map<Long, UUID> on = new HashMap<>();
			List<UUID> members = part.helpers(level.getGameTime());
			most = Math.max(most, members.size() + 1);
			for (UUID m : members) {
				BlockPos claim = part.claim(m);
				if (claim != null) {
					UUID other = on.put(plan.column(claim), m);
					helper.assertTrue(other == null, "two members work one column at " + helper.relativePos(claim).toShortString() + " (" + part.stage() + ")");
				}
			}
			if (part.leadClaim() != null && !members.isEmpty()) {
				UUID other = on.put(plan.column(part.leadClaim()), part.builder());
				helper.assertTrue(other == null, "a member works the lead's column at " + helper.relativePos(part.leadClaim()).toShortString());
			}
		}
		return most;
	}

	/** Fills chests round the stone with all the folly takes, a few spare of each (as WonderCrewGameTests does). */
	private static void stock(GameTestHelper helper, ServerLevel level, BlockPos stone) {
		io.github.jcondedata.aliveworkplace.build.BlueprintSupplies.Checklist list = io.github.jcondedata.aliveworkplace.build.BlueprintSupplies.checklist(level,
			new BlueprintData(WHOLE, BlueprintLibrary.get(level, WHOLE).map(Blueprint::size), Optional.empty())).orElseThrow();
		int dx = Integer.signum(stone.getX() - helper.absolutePos(MIDDLE).getX());
		int dz = Integer.signum(stone.getZ() - helper.absolutePos(MIDDLE).getZ());
		List<BlockPos> chests = List.of(stone.offset(2 * dx, 0, 0), stone.offset(0, 0, 2 * dz), stone.offset(2 * dx, 0, 2 * dz), stone.offset(3 * dx, 0, 0));
		chests.forEach(p -> level.setBlockAndUpdate(p, Blocks.CHEST.defaultBlockState()));
		int c = 0;
		int slot = 0;
		for (io.github.jcondedata.aliveworkplace.build.BlueprintSupplies.Line line : list.lines()) {
			net.minecraft.world.item.Item item = line.item();
			for (int left = line.need() + 4; left > 0; left -= item.getDefaultMaxStackSize()) {
				if (slot == 27) {
					c++;
					slot = 0;
				}
				helper.assertTrue(c < chests.size(), "the folly needs more than " + chests.size() + " chests");
				((net.minecraft.world.Container) level.getBlockEntity(chests.get(c))).setItem(slot++, new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
			}
		}
	}

	private static List<BlockPos> unlikeButTheFinial(ServerLevel level, BlueprintData.Placement at) {
		BlockPos finial = at.origin().offset(StructureTemplate.transform(new BlockPos(4, 18, 4), at.mirror(), at.rotation(), BlockPos.ZERO));
		Blueprint drawn = BlueprintLibrary.get(level, DRAWN).orElseThrow();
		return BuildPlan.create(drawn, at, level, 0).unfinished(level).stream().filter(p -> !p.equals(finial)).toList();
	}

	/**
	 * {@code wonderCrew} 2 with four idle builders: the crew is the lead and exactly one member on every tick, the two
	 * never on one column; raised to 12 mid-stage, the other two come and the crew is four.
	 */
	//$ gametest_ticks_batch TALL '3000' '"qaCrewCapTwo"'
	@GameTest(template = TALL, timeoutTicks = 3000, batch = "qaCrewCapTwo")
	public void aCapOfTwoTakesOneMemberAndTheRestComeWhenItRises(GameTestHelper helper) {
		Village v = village(helper, 4, 4);
		ServerLevel level = v.level();
		int limit = WonderCrews.LIMIT;
		WonderCrews.LIMIT = 2;
		Leftovers.after(helper, () -> WonderCrews.LIMIT = limit);
		WonderSite begun = begin(helper, v, v.builders().get(0));
		UUID id = begun.id();
		long start = level.getGameTime();
		AtomicInteger most = new AtomicInteger();
		AtomicInteger after = new AtomicInteger();
		AtomicBoolean raised = new AtomicBoolean(false);
		helper.onEachTick(() -> {
			WonderSite now = now(level, id);
			if (now == null || now.done()) {
				return;
			}
			int crew = WonderCrews.crew(level, now).size();
			columnsCheck(helper, level, now);
			if (!raised.get()) {
				helper.assertTrue(crew <= 2, "a crew of " + crew + " with wonderCrew 2 at tick " + (level.getGameTime() - start));
				most.set(Math.max(most.get(), crew));
				if (level.getGameTime() - start >= 600) {
					helper.assertTrue(most.get() == 2, "with wonderCrew 2 and three idle builders, nobody joined the lead: most " + most.get());
					WonderCrews.LIMIT = 12;
					raised.set(true);
				}
			} else {
				after.set(Math.max(after.get(), crew));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(raised.get(), "not yet raised");
			helper.assertTrue(after.get() == 4, "raised to 12, the crew is " + after.get() + " of the village's four builders");
			helper.assertTrue(WonderStages.statusText(level, now(level, id).stone().orElseThrow()).getString().contains("12"),
				"the stone doesn't show the new cap of 12: " + WonderStages.statusText(level, now(level, id).stone().orElseThrow()).getString());
		});
	}

	/**
	 * The default {@code wonderCrew} is 12, lead included: eleven idle builders join the lead, the twelfth is refused and
	 * keeps no job; the stone reads 12 of 12.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaCrewCapTwelve"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaCrewCapTwelve")
	public void theDefaultCapOfTwelveCountsTheLeadAndRefusesTheThirteenth(GameTestHelper helper) {
		Village v = village(helper, 13, 4);
		ServerLevel level = v.level();
		helper.assertTrue(WonderCrews.LIMIT == 12, "wonderCrew isn't its default 12: " + WonderCrews.LIMIT);
		WonderSite site = begin(helper, v, v.builders().get(0));
		helper.assertTrue(WonderCrews.limit(level, site) == 12, "the folly's crew limit isn't 12: " + WonderCrews.limit(level, site));
		BuildSite part = Builders.activeSite(level, v.builders().get(0));
		helper.assertTrue(part != null && site.id().equals(part.wonder()), "the lead didn't start stage I: " + part);
		for (int i = 1; i <= 11; i++) {
			Villager b = v.builders().get(i);
			b.tickCount = 20;
			BuildSite joined = Builders.activeSite(level, b);
			helper.assertTrue(joined != null && site.id().equals(joined.wonder()), "idle builder " + i + " didn't join a crew of " + WonderCrews.crew(level, site).size());
		}
		helper.assertTrue(WonderCrews.crew(level, site).size() == 12, "the crew is " + WonderCrews.crew(level, site).size() + ", not 12");
		Villager last = v.builders().get(12);
		for (int t = 0; t < 40; t++) {
			last.tickCount = t;
			BuildSite joined = Builders.activeSite(level, last);
			helper.assertTrue(joined == null || !site.id().equals(joined.wonder()), "a thirteenth builder joined a full crew");
		}
		helper.assertTrue(ModAttachments.BUILDER_JOB.get(last) == null, "the refused builder holds a job: " + ModAttachments.BUILDER_JOB.get(last));
		helper.assertTrue(WonderCrews.crew(level, site).size() == 12, "the crew went past 12: " + WonderCrews.crew(level, site).size());
		String status = WonderStages.statusText(level, site.stone().orElseThrow()).getString();
		helper.assertTrue(status.contains("12") , "the stone doesn't count the crew of 12: " + status);
		helper.succeed();
	}

	/**
	 * A builder whose Blueprint Table is one block beyond the village's reach never joins, not even once every builder
	 * is called; one whose table is exactly at the reach does.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaCrewEdge"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaCrewEdge")
	public void aTableAtTheVillagesEdgeJoinsAndOneBlockBeyondNever(GameTestHelper helper) {
		Village v = village(helper, 1, 4);
		ServerLevel level = v.level();
		WonderSite site = begin(helper, v, v.builders().get(0));
		helper.assertTrue(Builders.activeSite(level, v.builders().get(0)) != null, "the lead didn't start stage I");
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 10;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		Villager beyond = builder(helper, v, HALL.offset(11, 0, 0), HALL.offset(11, 0, -2));
		Villager at = builder(helper, v, HALL.offset(10, 0, 0), HALL.offset(10, 0, -2));
		helper.assertTrue(Builders.benchPos(at).orElseThrow().distSqr(v.hall()) == 100 && Builders.benchPos(beyond).orElseThrow().distSqr(v.hall()) == 121,
			"the tables aren't 10 and 11 blocks from the hall");
		for (int t = 0; t < 40; t++) {
			beyond.tickCount = t;
			BuildSite joined = Builders.activeSite(level, beyond);
			helper.assertTrue(joined == null, "a builder whose table is 11 blocks from the hall (the village reaches 10) joined: " + joined);
		}
		WonderCrews.callEveryone(level, site);
		for (int t = 0; t < 40; t++) {
			beyond.tickCount = t;
			helper.assertTrue(Builders.activeSite(level, beyond) == null, "a builder beyond the village came when every builder was called");
		}
		at.tickCount = 20;
		BuildSite joined = Builders.activeSite(level, at);
		helper.assertTrue(joined != null && site.id().equals(joined.wonder()), "a builder whose table is exactly at the village's reach didn't join: " + joined);
		helper.assertTrue(WonderCrews.crew(level, now(level, site.id())).size() == 2, "the crew isn't the lead and the edge builder: "
			+ WonderCrews.crew(level, now(level, site.id())).size());
		helper.succeed();
	}

	/**
	 * A builder on a build of its own isn't taken off it, call or not. With another build of its own queued: when the one
	 * under way ends, uncalled, it starts the queued one; called, it comes to the Wonder instead.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaCrewBusy"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaCrewBusy")
	public void aBuilderOnItsOwnBuildComesWhenItEndsOnlyOnceCalled(GameTestHelper helper) {
		Village v = village(helper, 3, 4);
		ServerLevel level = v.level();
		WonderSite site = begin(helper, v, v.builders().get(0));
		helper.assertTrue(Builders.activeSite(level, v.builders().get(0)) != null, "the lead didn't start stage I");
		BuildSiteManager sites = BuildSiteManager.get(level);
		List<BuildSite> own = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			BuildSite s = sites.create(v.owner().getUUID(), "Owner", WHOLE,
				new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2 + 7 * (i % 2), 2, 2 + 30 * (i / 2))), Rotation.NONE, Mirror.NONE));
			own.add(s);
			Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(s.id()));
		}
		Villager[] busy = {v.builders().get(1), v.builders().get(2)};
		for (int b = 0; b < 2; b++) {
			BuildSite underWay = own.get(2 * b);
			BuildSite queued = own.get(2 * b + 1);
			underWay.setBuilder(busy[b].getUUID());
			underWay.setQueued(false);
			ModAttachments.BUILDER_JOB.set(busy[b], new BuilderJob(underWay.id(), false));
			queued.setBuilder(busy[b].getUUID());
			queued.setQueued(true);
		}
		// uncalled: busy[0] stays on its own build; when it ends, the queued one is next
		for (int t = 0; t < 40; t++) {
			busy[0].tickCount = t;
			BuildSite s = Builders.activeSite(level, busy[0]);
			helper.assertTrue(s != null && s.id().equals(own.get(0).id()), "uncalled, a builder on its own build was taken off it: " + s);
		}
		sites.remove(own.get(0).id()); // its build ends
		busy[0].tickCount = 20;
		BuildSite next = Builders.activeSite(level, busy[0]);
		helper.assertTrue(next != null && next.id().equals(own.get(1).id()), "uncalled, a builder with a build queued went to the Wonder, not its own: " + next);
		// called: busy[1] isn't taken off its build either, but when it ends it comes to the Wonder, its queued build waiting
		v.owner().teleportTo(site.stone().orElseThrow().getX() + 0.5, site.stone().orElseThrow().getY() + 1, site.stone().orElseThrow().getZ() + 0.5);
		helper.assertTrue(WonderCrews.call(v.owner()) == 1, "the owner's call wasn't made");
		for (int t = 0; t < 40; t++) {
			busy[1].tickCount = t;
			BuildSite s = Builders.activeSite(level, busy[1]);
			helper.assertTrue(s != null && s.id().equals(own.get(2).id()), "called, a builder was taken off a build of its own under way: " + s);
		}
		sites.remove(own.get(2).id());
		busy[1].tickCount = 7;
		BuildSite came = Builders.activeSite(level, busy[1]);
		helper.assertTrue(came != null && site.id().equals(came.wonder()) && ModAttachments.BUILDER_JOB.get(busy[1]).helper(),
			"called, a builder whose own build ended didn't come to the Wonder: " + came);
		helper.assertTrue(sites.get(own.get(3).id()) != null && sites.get(own.get(3).id()).isQueued()
			&& busy[1].getUUID().equals(sites.get(own.get(3).id()).builder()), "the called builder's queued build wasn't kept for later");
		helper.succeed();
	}

	/**
	 * The Master Architect of the village leads the Wonder though the plan was handed to another Master builder (an
	 * equal level, so no tie-break would pick the architect): the stone and the hall name the architect.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaCrewArchitect"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaCrewArchitect")
	public void theMasterArchitectLeadsOverAnEqualMasterAndIsNamed(GameTestHelper helper) {
		Village v = village(helper, 2, 4);
		ServerLevel level = v.level();
		Villager master = v.builders().get(0);
		Villager architect = v.builders().get(1);
		master.setVillagerData(master.getVillagerData().setLevel(5));
		architect.setVillagerData(architect.getVillagerData().setLevel(5));
		master.setCustomName(Component.literal("Mason Plain"));
		architect.setCustomName(Component.literal("Archie Tect"));
		Legend legend = Legends.get(Wonders.MASTER_ARCHITECT).orElse(null);
		helper.assertTrue(legend != null, "the Master Architect legend isn't loaded");
		boolean legends = Legends.ENABLED;
		boolean wonderLegends = Wonders.LEGENDS;
		Legends.ENABLED = true;
		Wonders.LEGENDS = true;
		Leftovers.after(helper, () -> {
			Legends.ENABLED = legends;
			Wonders.LEGENDS = wonderLegends;
			Legends.clear(architect);
		});
		Legends.make(level, architect, legend, "test", Optional.of(v.hall()));
		helper.assertTrue(Builders.isBuilder(architect) && Builders.benchPos(architect).isPresent(), "the architect is no longer a builder at a table");
		WonderSite site = begin(helper, v, master);
		WonderSite now = now(level, site.id());
		helper.assertTrue(now.builder().equals(Optional.of(architect.getUUID())), "the Wonder's lead isn't the Master Architect: "
			+ now.builder().map(u -> u.equals(master.getUUID()) ? "the Master it was handed to" : u.toString()));
		helper.assertTrue(stoneLead(level, now).equals("Archie Tect"), "the stone names " + stoneLead(level, now) + " as the lead, not the Master Architect");
		String card = hallCard(level, v.hall());
		helper.assertTrue(card.contains("Archie Tect") && !card.contains("Mason Plain"), "the hall's Wonders card doesn't name the architect as lead: " + card);
		helper.succeed();
	}

	/**
	 * A crew of three raises the whole folly with no two on one column on any tick, while a member's Blueprint Table is
	 * broken mid-stage (a STRUCTURE part); the Expert of the crew leads every stage after the first and the stone names them; the folly
	 * ends as drawn.
	 */
	//$ gametest_ticks_batch TALL '24000' '"qaCrewOfThree"'
	@GameTest(template = TALL, timeoutTicks = 24000, batch = "qaCrewOfThree")
	public void aCrewOfThreeSharesNoColumnAndTheExpertLeadsLaterStages(GameTestHelper helper) {
		Village v = village(helper, 3, 4);
		ServerLevel level = v.level();
		Villager first = v.builders().get(0);
		Villager loser = v.builders().get(1);
		Villager expert = v.builders().get(2);
		expert.setVillagerData(expert.getVillagerData().setLevel(4));
		expert.setCustomName(Component.literal("Ex Pert"));
		WonderSite begun = begin(helper, v, first);
		stock(helper, level, begun.stone().orElseThrow());
		BlueprintData.Placement at = begun.placement().orElseThrow();
		UUID id = begun.id();
		AtomicBoolean broken = new AtomicBoolean(false);
		AtomicInteger most = new AtomicInteger();
		AtomicLong brokeAt = new AtomicLong(-1);
		AtomicInteger brokeStage = new AtomicInteger();
		List<String> leads = new ArrayList<>();
		helper.onEachTick(() -> {
			if (level.getGameTime() % 200 == 0) {
				helper.setDayTime(2000);
			}
			WonderSite now = now(level, id);
			if (now == null || now.done()) {
				return;
			}
			most.set(Math.max(most.get(), WonderCrews.crew(level, now).size()));
			columnsCheck(helper, level, now);
			if (now.stage() >= 2 && !now.parts().isEmpty() && !leads.contains("stage" + now.stage())) {
				leads.add("stage" + now.stage());
				helper.assertTrue(now.builder().equals(Optional.of(expert.getUUID())), "stage " + now.stage() + "'s lead isn't the crew's Expert: "
					+ now.builder().map(u -> u.equals(first.getUUID()) ? "the first lead" : u.equals(loser.getUUID()) ? "the tableless one" : u.toString()));
				helper.assertTrue(stoneLead(level, now).equals("Ex Pert"), "stage " + now.stage() + ": the stone names " + stoneLead(level, now));
			}
			if (!broken.get() && !now.parts().isEmpty()) {
				BuildSite part = BuildSiteManager.get(level).get(now.parts().get(0));
				if (part != null && part.stage() == BuildPlan.Stage.STRUCTURE && part.placed() >= 10 && WonderCrews.crew(level, now).size() == 3
					&& ModAttachments.BUILDER_JOB.get(loser) != null && ModAttachments.BUILDER_JOB.get(loser).helper()) {
					brokeStage.set(now.stage());
					level.destroyBlock(v.benches().get(1), false); // the member's Blueprint Table broken mid-stage
					broken.set(true);
					brokeAt.set(level.getGameTime());
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken.get(), "the member's table was never broken mid-stage (crew most " + most.get() + ", done " + (now(level, id) != null && now(level, id).done()) + ")");
			WonderSite now = now(level, id);
			helper.assertTrue(now != null && now.done(), "the folly isn't finished: stage " + (now == null ? "-" : now.stage() + ", "
				+ Math.round(WonderStages.stageProgress(level, now) * 100) + "%"));
			helper.assertTrue(most.get() == 3, "the crew was never all three: " + most.get());
			helper.assertTrue(!leads.isEmpty(), "no later stage was seen");
			List<BlockPos> left = unlikeButTheFinial(level, at);
			helper.assertTrue(left.isEmpty(), left.size() + " blocks unlike the drawing, e.g. " + left.stream().limit(4).map(p -> helper.relativePos(p).toShortString()).toList());
			io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[test] qa crew of three: table broken at stage {} tick {}, later stages led by the Expert {}",
				brokeStage.get(), brokeAt.get(), leads);
		});
	}
}
