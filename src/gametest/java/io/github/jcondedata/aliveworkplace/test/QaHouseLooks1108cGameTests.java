package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.PieceLooks;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.world.VillageHouses;
import io.github.jcondedata.aliveworkplace.world.VillagePieces;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (qa-1108c), house looks (23.10a, docs/wiki/house-looks.md), adversarial: the house torn down between the page
 * listing it and the pick; the builder's bench at exactly the 128-block reach and one block past it; a busy builder's
 * queue at four and at five waiting builds; a finished rebuild leaving every item in the room's chest, the job block and
 * the bed (still the resident's home) exactly as they were; and who may choose (an operator in a village nobody leads, a
 * friend of someone else, a friend who was removed again). The village's setup is PieceLooksGameTests' (a plains Guard
 * House as the village grows it, a hall, a builder at his bench and three supply chests).
 */
public class QaHouseLooks1108cGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:huge_area";
	static final BlockPos HALL = new BlockPos(2, 2, 26);
	static final BlockPos BENCH = new BlockPos(4, 2, 6);
	static final BlockPos[] CHESTS = {new BlockPos(4, 2, 3), new BlockPos(6, 2, 3), new BlockPos(8, 2, 3)};
	static final BlockPos BUILDER = new BlockPos(5, 2, 8);
	static final BlockPos HOUSE = new BlockPos(13, 2, 10);
	/** The Guard House's grindstone (its job block) and chest, in the template's coordinates. */
	static final BlockPos GRINDSTONE = new BlockPos(2, 1, 7);
	static final BlockPos ROOM_CHEST = new BlockPos(2, 1, 6);

	record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, ServerPlayer owner,
				   BlueprintData.Placement placement, Villager builder, StructureTemplate template) {
	}

	/** PieceLooksGameTests' village: a plains Guard House, a hall ({@code withOwner}: led by the returned owner), a builder, three chests. */
	static Village village(GameTestHelper helper, boolean withOwner) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.buildSites(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), Rotation.NONE, Mirror.NONE);
		StructureTemplate template = level.getStructureManager().get(VillagePieces.template("plains", "guard_house")).orElseThrow();
		template.placeInWorld(level, placement.origin(), placement.origin(), new StructurePlaceSettings().setIgnoreEntities(true)
			.addProcessor(JigsawReplacementProcessor.INSTANCE), RandomSource.create(7), 2);
		Container chest = (Container) level.getBlockEntity(world(placement, ROOM_CHEST));
		chest.clearContent();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, new ArrayList<>(), "LooksLeader");
		if (withOwner) {
			entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		for (BlockPos c : CHESTS) {
			helper.setBlock(c, Blocks.CHEST);
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, BUILDER);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		Leftovers.after(helper, () -> {
			for (BuildSite site : List.copyOf(BuildSiteManager.get(level).all())) {
				if (builder.getUUID().equals(site.builder())) {
					BuildSiteManager.get(level).remove(site.id());
				}
			}
		});
		return new Village(level, helper.absolutePos(HALL), entity, owner, placement, builder, template);
	}

	static BlockPos world(BlueprintData.Placement placement, BlockPos local) {
		return placement.origin().offset(StructureTemplate.transform(local, placement.mirror(), placement.rotation(), BlockPos.ZERO));
	}

	static VillagePieces.Piece piece(Village v) {
		return PieceLooks.pieces(v.level(), v.hall()).stream().filter(p -> p.placement().origin().equals(v.placement().origin())).findFirst()
			.orElseThrow(() -> new GameTestAssertException("the page doesn't find the Guard House"));
	}

	static String name(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name == null ? "" : name.getString();
	}

	static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	/** The hall's House looks page as {@code player} opens it (the Builds button), then the Guard House's view. */
	static ChoiceMenu houseView(GameTestHelper helper, Village v, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, v.hall());
		menu.press(VillageHallScreen.BUILDS, player);
		helper.assertTrue(menu.icon(4).is(Items.OAK_DOOR), "not the House looks page: " + menu.icon(4));
		BlockPos o = v.placement().origin();
		String at = "At " + o.getX() + ", " + o.getY() + ", " + o.getZ();
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (name(menu.icon(slot)).equals("Guard House") && lore(menu.icon(slot)).contains(at)) {
				menu.press(slot, player);
				return menu;
			}
		}
		throw new GameTestAssertException("the page doesn't list the Guard House " + at);
	}

	/** Clicks the {@code look}'s outside on an open house view. */
	static void click(GameTestHelper helper, ChoiceMenu view, ServerPlayer player, String look) {
		int slot = PieceLooks.FIRST_LOOK + VillageHouses.STYLES.indexOf(look);
		helper.assertTrue(name(view.icon(slot)).equals(VillagePieces.styleName(look).getString() + " outside"), "the look's button: " + name(view.icon(slot)));
		view.press(slot, player);
	}

	/** Takes the whole house down: every block of its template, the room and its chest too. */
	static void tearDown(Village v) {
		if (v.level().getBlockEntity(world(v.placement(), ROOM_CHEST)) instanceof Container c) {
			c.clearContent();
		}
		var size = v.template().getSize();
		for (BlockPos local : BlockPos.betweenClosed(0, 0, 0, size.getX() - 1, size.getY() - 1, size.getZ() - 1)) {
			v.level().setBlock(world(v.placement(), local), Blocks.AIR.defaultBlockState(), 2);
		}
	}

	/** {@code count} other builds handed to the builder: the first one he is on, the rest waiting. */
	static List<BuildSite> busy(GameTestHelper helper, Village v, int waiting) {
		List<BuildSite> out = new ArrayList<>();
		for (int i = 0; i <= waiting; i++) {
			BlueprintData.Placement elsewhere = new BlueprintData.Placement(v.level().dimension().location(),
				helper.absolutePos(new BlockPos(2 + i, 2, 2)).above(40), Rotation.NONE, Mirror.NONE);
			out.add(i == 0
				? Builders.start(v.level(), v.builder(), v.owner(), VillagePieces.outsideId("snowy", "clinic"), elsewhere)
				: Builders.enqueue(v.level(), v.builder(), v.owner(), VillagePieces.outsideId("snowy", "clinic"), elsewhere));
		}
		return out;
	}

	// ---- 1. the house torn down after the page listed it -------------------------------------------------------------

	/** The page lists the house, then it is torn down; the pick says "That house isn't there any more." and nothing starts. */
	//$ gametest_ticks_batch AREA '200' '"qaLooksTornDown"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLooksTornDown")
	public void aHouseTornDownAfterTheListingIsGone(GameTestHelper helper) {
		Village v = village(helper, true);
		VillagePieces.Piece found = piece(v);
		ChoiceMenu view = houseView(helper, v, v.owner());
		tearDown(v);
		click(helper, view, v.owner(), "desert");
		helper.assertTrue(PieceLooks.rebuild(v.level(), found) == null, "a rebuild started on the empty lot");
		helper.assertTrue(Builders.activeSite(v.level(), v.builder()) == null, "the builder took a job");
		helper.assertTrue(v.entity().pieceLooks().isEmpty(), "a look was kept for a house that's gone: " + v.entity().pieceLooks());
		PieceLooks.Outcome outcome = PieceLooks.choose(v.level(), v.hall(), v.owner(), found, "desert");
		helper.assertTrue(outcome == PieceLooks.Outcome.GONE, "outcome " + outcome);
		String said = PieceLooks.message(v.level(), v.hall(), found, "desert", outcome, v.owner()).getString();
		helper.assertTrue(said.equals("That house isn't there any more."), "said: " + said);
		helper.assertTrue(PieceLooks.pieces(v.level(), v.hall()).stream().noneMatch(p -> p.placement().origin().equals(v.placement().origin())),
			"the page still lists the torn-down house");
		helper.succeed();
	}

	// ---- 2. the builder's reach at its edge --------------------------------------------------------------------------

	/** Outside the village, a bench exactly 128 blocks (across the ground) from the house takes the rebuild; at 129 the pick is refused. */
	//$ gametest_ticks_batch AREA '200' '"qaLooksReach"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLooksReach")
	public void aBenchAt128BlocksReachesTheHouseAnd129DoesNot(GameTestHelper helper) {
		Village v = village(helper, true);
		ServerLevel level = v.level();
		VillagePieces.Piece found = piece(v);
		helper.setBlock(BENCH, Blocks.AIR);
		// the house's middle, as a build's reach is measured (its outline's centre, across the ground)
		Blueprint outside = BlueprintLibrary.get(level, VillagePieces.outsideId("desert", "guard_house")).orElseThrow();
		BlockPos centre = Builders.spot(level, VillagePieces.outsideId("desert", "guard_house"), v.placement());
		helper.assertTrue(outside.size().getX() > 0, "no outside");
		helper.assertTrue(Builders.MAX_SITE_DISTANCE == 128, "the reach isn't the wiki's 128: " + Builders.MAX_SITE_DISTANCE);
		BlockPos at128 = new BlockPos(centre.getX() + 128, v.placement().origin().getY(), centre.getZ());
		BlockPos at129 = at128.east();
		Leftovers.after(helper, () -> {
			level.setBlock(at128, Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(at129, Blocks.AIR.defaultBlockState(), 3);
		});
		helper.assertFalse(io.github.jcondedata.aliveworkplace.work.Reach.inSameVillage(level, at128, centre), "setup: the far bench is in the village");
		level.setBlock(at128, ModBlocks.BUILDERS_BENCH.defaultBlockState(), 3);
		Builders.employ(level, v.builder(), at128);
		ChoiceMenu view = houseView(helper, v, v.owner());
		click(helper, view, v.owner(), "desert");
		BuildSite site = PieceLooks.rebuild(level, found);
		helper.assertTrue(site != null && v.builder().getUUID().equals(site.builder()), "a bench 128 blocks off didn't take it: " + site);
		Builders.cancel(level, site);
		helper.assertTrue(PieceLooks.rebuild(level, found) == null, "setup: the cancel left the site");
		// one block farther
		level.setBlock(at128, Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(at129, ModBlocks.BUILDERS_BENCH.defaultBlockState(), 3);
		Builders.employ(level, v.builder(), at129);
		helper.assertTrue(Builders.benchPos(v.builder()).equals(Optional.of(at129)), "setup: bench " + Builders.benchPos(v.builder()));
		PieceLooks.Outcome outcome = PieceLooks.choose(level, v.hall(), v.owner(), found, "taiga");
		helper.assertTrue(outcome == PieceLooks.Outcome.NO_BUILDER, "a bench 129 blocks off: " + outcome);
		helper.assertTrue(PieceLooks.rebuild(level, found) == null, "a rebuild started out of reach");
		String said = PieceLooks.message(level, v.hall(), found, "taiga", outcome, v.owner()).getString();
		helper.assertTrue(said.equals("No builder of this village can reach the Guard House: give one a bench within 128 blocks of it."), "said: " + said);
		helper.succeed();
	}

	// ---- 3. a busy builder's queue -----------------------------------------------------------------------------------

	/** The only builder busy with four builds waiting: the pick waits as the fifth; with five waiting, nothing more is queued. */
	//$ gametest_ticks_batch AREA '200' '"qaLooksQueue"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLooksQueue")
	public void aBusyBuilderQueuesUpToFiveWaitingBuilds(GameTestHelper helper) {
		Village v = village(helper, true);
		ServerLevel level = v.level();
		VillagePieces.Piece found = piece(v);
		List<BuildSite> others = busy(helper, v, Builders.MAX_QUEUE - 1);
		helper.assertTrue(Builders.queue(level, v.builder()).size() == 4, "setup: waiting " + Builders.queue(level, v.builder()).size());
		ChoiceMenu view = houseView(helper, v, v.owner());
		click(helper, view, v.owner(), "desert");
		BuildSite site = PieceLooks.rebuild(level, found);
		helper.assertTrue(site != null && site.isQueued() && v.builder().getUUID().equals(site.builder()), "not queued with the builder: " + site);
		helper.assertTrue(Builders.queue(level, v.builder()).size() == 5, "waiting " + Builders.queue(level, v.builder()).size());
		helper.assertTrue(Builders.activeSite(level, v.builder()) == others.get(0), "the builder dropped his current build");
		String said = PieceLooks.message(level, v.hall(), found, "desert", PieceLooks.Outcome.QUEUED, v.owner()).getString();
		String builder = v.builder().getDisplayName().getString();
		helper.assertTrue(said.equals(builder + " will rebuild the Guard House with the Desert outside after their current builds."), "said: " + said);
		helper.assertTrue(v.entity().pieceLooks().size() == 1 && v.entity().pieceLooks().get(0).look().equals("desert"), "kept: " + v.entity().pieceLooks());
		// the same builder with five waiting: the queue is full and the house's pick adds nothing
		Builders.cancel(level, site);
		BlueprintData.Placement fifth = new BlueprintData.Placement(level.dimension().location(),
			helper.absolutePos(new BlockPos(12, 2, 2)).above(40), Rotation.NONE, Mirror.NONE);
		Builders.enqueue(level, v.builder(), v.owner(), VillagePieces.outsideId("snowy", "clinic"), fifth);
		helper.assertTrue(Builders.queue(level, v.builder()).size() == 5, "setup: waiting " + Builders.queue(level, v.builder()).size());
		PieceLooks.Outcome outcome = PieceLooks.choose(level, v.hall(), v.owner(), found, "savanna");
		helper.assertTrue(!outcome.ok(), "a sixth waiting build was taken: " + outcome);
		helper.assertTrue(PieceLooks.rebuild(level, found) == null, "a rebuild was queued past five");
		helper.assertTrue(Builders.queue(level, v.builder()).size() == 5, "waiting " + Builders.queue(level, v.builder()).size());
		helper.assertTrue(VillagePieces.lookAt(level, v.placement()).equals(Optional.of("plains")), "the house changed");
		helper.succeed();
	}

	// ---- 4. a finished rebuild: every item, the job block and the bed -------------------------------------------------

	/** Stocks the first supply chest with all the {@code look}'s outside needs. */
	static void stock(GameTestHelper helper, Village v, String look) {
		net.minecraft.resources.ResourceLocation id = VillagePieces.outsideId(look, "guard_house");
		Blueprint outside = BlueprintLibrary.get(v.level(), id).orElseThrow(() -> new GameTestAssertException("no outside blueprint"));
		var report = BlueprintSupplies.check(v.level(), new BlueprintData(id, Optional.of(outside.size()), Optional.of(v.placement())))
			.orElseThrow(() -> new GameTestAssertException("no supply report"));
		List<ItemStack> stacks = new ArrayList<>();
		for (var m : report.missing()) {
			for (int left = m.count(); left > 0; left -= m.item().getDefaultMaxStackSize()) {
				stacks.add(new ItemStack(m.item(), Math.min(left, m.item().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(!stacks.isEmpty() && stacks.size() <= 27, "the outside needs " + stacks.size() + " stacks");
		Container first = (Container) helper.getBlockEntity(CHESTS[0]);
		for (int i = 0; i < stacks.size(); i++) {
			first.setItem(i, stacks.get(i));
		}
	}

	/**
	 * The room's chest holds several stacks (sandstone the desert outside needs among them, and a named sword), and a
	 * resident calls the room's bed home. After the desert outside is finished every slot of the chest holds exactly what
	 * it held, the grindstone and bed stand as they stood (same block, same facing), and the bed is still her home.
	 */
	//$ gametest_ticks_batch AREA '14000' '"qaLooksKept"'
	@GameTest(template = AREA, timeoutTicks = 14000, batch = "qaLooksKept")
	public void aFinishedRebuildKeepsEveryItemTheJobBlockAndTheBed(GameTestHelper helper) {
		Village v = village(helper, true);
		ServerLevel level = v.level();
		VillagePieces.Piece found = piece(v);
		Container chest = (Container) level.getBlockEntity(world(v.placement(), ROOM_CHEST));
		chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
		chest.setItem(4, new ItemStack(Items.CUT_SANDSTONE, 17));
		chest.setItem(5, new ItemStack(Items.SANDSTONE, 64));
		chest.setItem(13, new ItemStack(Items.BREAD, 5));
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.set(DataComponents.CUSTOM_NAME, Component.literal("Guard's Sword"));
		chest.setItem(26, sword);
		List<ItemStack> before = new ArrayList<>();
		for (int i = 0; i < chest.getContainerSize(); i++) {
			before.add(chest.getItem(i).copy());
		}
		// the bed in the room, and whom it houses
		BlockPos bed = null;
		Map<BlockPos, BlockState> kept = new HashMap<>();
		for (BlockPos local : BlockPos.betweenClosed(0, 0, 0, 8, 9, 9)) {
			if (!VillagePieces.inRoom(local)) {
				continue;
			}
			BlockPos at = world(v.placement(), local).immutable();
			BlockState state = level.getBlockState(at);
			kept.put(at, state);
			if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
				bed = at;
			}
		}
		helper.assertTrue(bed != null, "setup: the Guard House's room has no bed");
		BlockPos bedAt = bed;
		BlockState grindstone = level.getBlockState(world(v.placement(), GRINDSTONE));
		helper.assertTrue(grindstone.is(Blocks.GRINDSTONE), "setup: no grindstone");
		Villager resident = helper.spawn(EntityType.VILLAGER, helper.relativePos(bedAt.above()));
		level.getPoiManager().take(h -> true, (h, p) -> p.equals(bedAt), bedAt, 1);
		resident.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bedAt));
		stock(helper, v, "desert");
		ChoiceMenu view = houseView(helper, v, v.owner());
		click(helper, view, v.owner(), "desert");
		BuildSite site = PieceLooks.rebuild(level, found);
		helper.assertTrue(site != null, "no rebuild started");
		BuildPlan plan = site.plan(level);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still rebuilding: " + site.stage() + " "
				+ Math.round(site.progress(plan) * 100) + "% missing=" + site.missing() + " status=" + site.status());
			helper.assertTrue(VillagePieces.lookAt(level, v.placement()).equals(Optional.of("desert")), "looks like " + VillagePieces.lookAt(level, v.placement()));
			for (int i = 0; i < before.size(); i++) {
				ItemStack now = chest.getItem(i);
				helper.assertTrue(ItemStack.matches(now, before.get(i)), "the room's chest, slot " + i + ": " + before.get(i) + " -> " + now);
			}
			for (Map.Entry<BlockPos, BlockState> e : kept.entrySet()) {
				helper.assertTrue(level.getBlockState(e.getKey()).getBlock() == e.getValue().getBlock(), "the room changed at "
					+ helper.relativePos(e.getKey()) + ": " + e.getValue() + " -> " + level.getBlockState(e.getKey()));
			}
			helper.assertTrue(level.getBlockState(world(v.placement(), GRINDSTONE)).equals(grindstone), "the job block: "
				+ grindstone + " -> " + level.getBlockState(world(v.placement(), GRINDSTONE)));
			BlockState bedNow = level.getBlockState(bedAt);
			helper.assertTrue(bedNow.getBlock() == kept.get(bedAt).getBlock()
				&& bedNow.getValue(BedBlock.FACING) == kept.get(bedAt).getValue(BedBlock.FACING), "the bed: " + kept.get(bedAt) + " -> " + bedNow);
			helper.assertTrue(resident.isAlive() && resident.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).equals(Optional.of(bedAt)),
				"the bed no longer houses her: home " + resident.getBrain().getMemory(MemoryModuleType.HOME));
		});
	}

	// ---- 5. who may choose ---------------------------------------------------------------------------------------------

	/**
	 * A village nobody leads: an operator may choose, anyone else is told there's no leader. A led village: a friend of
	 * someone else (not the leader) is refused and nothing changes; the leader's friend may change the look; once removed
	 * as a friend, the same player's next pick is refused and the running rebuild stays; an operator may change it again.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLooksWho"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLooksWho")
	public void onlyTheLeaderTheirFriendsAndOperatorsChoose(GameTestHelper helper) {
		Village v = village(helper, false);
		ServerLevel level = v.level();
		VillagePieces.Piece found = piece(v);
		ServerPlayer op = WonderFinishedGameTests.listener(helper, new ArrayList<>(), "LooksOp");
		ServerPlayer visitor = WonderFinishedGameTests.listener(helper, new ArrayList<>(), "LooksVisitor");
		ServerPlayer other = WonderFinishedGameTests.listener(helper, new ArrayList<>(), "LooksOther");
		var ops = level.getServer().getPlayerList().getOps();
		ops.add(new ServerOpListEntry(op.getGameProfile(), 4, false));
		Friends friends = Friends.get(level.getServer());
		UUID leader = v.owner().getUUID();
		Leftovers.after(helper, () -> {
			level.getServer().getPlayerList().deop(op.getGameProfile());
			friends.remove(leader, visitor.getUUID());
			friends.remove(other.getUUID(), visitor.getUUID());
		});
		helper.assertTrue(op.hasPermissions(2) && !visitor.hasPermissions(2), "setup: permissions");
		// nobody leads the village
		click(helper, houseView(helper, v, visitor), visitor, "desert");
		helper.assertTrue(PieceLooks.rebuild(level, found) == null && v.entity().pieceLooks().isEmpty(), "a visitor chose in a village nobody leads");
		helper.assertTrue(PieceLooks.choose(level, v.hall(), visitor, found, "desert") == PieceLooks.Outcome.NO_LEADER, "not refused as no leader");
		helper.assertTrue(PieceLooks.message(level, v.hall(), found, "desert", PieceLooks.Outcome.NO_LEADER, visitor).getString()
			.equals("This village has no leader yet: switch on the hall's protection to lead it."), "the no-leader line");
		click(helper, houseView(helper, v, op), op, "desert");
		BuildSite first = PieceLooks.rebuild(level, found);
		helper.assertTrue(first != null && first.structure().equals(VillagePieces.outsideId("desert", "guard_house")), "an operator was refused: " + first);
		Builders.cancel(level, first);
		// the village is led: a friend of someone else is still a stranger here
		v.entity().setOwner(leader, "LooksLeader");
		friends.add(other.getUUID(), visitor.getUUID(), "LooksVisitor");
		click(helper, houseView(helper, v, visitor), visitor, "taiga");
		helper.assertTrue(PieceLooks.rebuild(level, found) == null, "someone else's friend started a rebuild");
		helper.assertTrue(PieceLooks.choose(level, v.hall(), visitor, found, "taiga") == PieceLooks.Outcome.NOT_ALLOWED, "someone else's friend wasn't refused");
		helper.assertTrue(PieceLooks.message(level, v.hall(), found, "taiga", PieceLooks.Outcome.NOT_ALLOWED, visitor).getString()
			.equals("Only the village's leader, LooksLeader, and their friends can choose how its houses look."), "the not-allowed line");
		// the leader's friend
		friends.add(leader, visitor.getUUID(), "LooksVisitor");
		click(helper, houseView(helper, v, visitor), visitor, "taiga");
		BuildSite taiga = PieceLooks.rebuild(level, found);
		helper.assertTrue(taiga != null && taiga.structure().equals(VillagePieces.outsideId("taiga", "guard_house")), "the leader's friend was refused: " + taiga);
		// no longer a friend: refused, and the taiga rebuild goes on
		friends.remove(leader, visitor.getUUID());
		PieceLooks.Outcome outcome = PieceLooks.choose(level, v.hall(), visitor, found, "snowy");
		helper.assertTrue(outcome == PieceLooks.Outcome.NOT_ALLOWED, "a removed friend: " + outcome);
		helper.assertTrue(PieceLooks.rebuild(level, found) == taiga && BuildSiteManager.get(level).get(taiga.id()) != null, "the refused pick touched the rebuild");
		helper.assertTrue(v.entity().pieceLooks().size() == 1 && v.entity().pieceLooks().get(0).look().equals("taiga"), "kept: " + v.entity().pieceLooks());
		// an operator in a led village
		outcome = PieceLooks.choose(level, v.hall(), op, found, "snowy");
		helper.assertTrue(outcome.ok(), "an operator in a led village: " + outcome);
		BuildSite snowy = PieceLooks.rebuild(level, found);
		helper.assertTrue(snowy != null && snowy.structure().equals(VillagePieces.outsideId("snowy", "guard_house"))
			&& BuildSiteManager.get(level).get(taiga.id()) == null, "the operator's change of mind: " + snowy);
		Builders.cancel(level, snowy);
		helper.succeed();
	}
}
