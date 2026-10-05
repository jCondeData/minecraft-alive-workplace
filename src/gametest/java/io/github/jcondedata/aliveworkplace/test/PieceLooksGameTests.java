package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.PieceLooks;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * 23.10a, house looks: a plains Guard House as a village grows it, a Village Hall whose owner leads the village, and a
 * builder at his bench. Through the hall's House looks page, the owner picks the desert outside and the builder rebuilds
 * the outside over the house, the room (grindstone, chest and what's in it, bed) left exactly as it was; a stranger and a
 * village with no leader are refused, server side; with nothing in the chests the builder waits for the materials, room
 * untouched; the rebuild and the choice survive a save and reload halfway; and every new sentence reads.
 */
public class PieceLooksGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 26);
	private static final BlockPos BENCH = new BlockPos(4, 2, 6);
	private static final BlockPos[] CHESTS = {new BlockPos(4, 2, 3), new BlockPos(6, 2, 3), new BlockPos(8, 2, 3)};
	private static final BlockPos BUILDER = new BlockPos(5, 2, 8);
	private static final BlockPos HOUSE = new BlockPos(13, 2, 10);
	/** The Guard House's grindstone (its job block) and chest, in the template's coordinates. */
	private static final BlockPos GRINDSTONE = new BlockPos(2, 1, 7);
	private static final BlockPos ROOM_CHEST = new BlockPos(2, 1, 6);

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, ServerPlayer owner,
						   BlueprintData.Placement placement, Villager builder, Map<BlockPos, BlockState> room) {
	}

	/** A plains Guard House placed as the village generator places it, a hall led by {@code owner}, a builder and three chests. */
	private static Village village(GameTestHelper helper, boolean withOwner) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), Rotation.NONE, Mirror.NONE);
		StructureTemplate template = level.getStructureManager().get(VillagePieces.template("plains", "guard_house")).orElseThrow();
		template.placeInWorld(level, placement.origin(), placement.origin(), new StructurePlaceSettings().setIgnoreEntities(true)
			.addProcessor(JigsawReplacementProcessor.INSTANCE), RandomSource.create(7), 2);
		// something of the guard's in the room's chest: it stays where it is
		Container chest = (Container) level.getBlockEntity(world(placement, ROOM_CHEST));
		chest.clearContent();
		chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		if (withOwner) {
			entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		for (BlockPos c : CHESTS) {
			helper.setBlock(c, Blocks.CHEST);
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, BUILDER);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		return new Village(level, helper.absolutePos(HALL), entity, owner, placement, builder, room(level, placement));
	}

	private static BlockPos world(BlueprintData.Placement placement, BlockPos local) {
		return placement.origin().offset(StructureTemplate.transform(local, placement.mirror(), placement.rotation(), BlockPos.ZERO));
	}

	/** The room as it stands: every block in it. */
	private static Map<BlockPos, BlockState> room(ServerLevel level, BlueprintData.Placement placement) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (BlockPos local : BlockPos.betweenClosed(0, 0, 0, 8, 9, 9)) {
			if (VillagePieces.inRoom(local)) {
				BlockPos at = world(placement, local);
				out.put(at.immutable(), level.getBlockState(at));
			}
		}
		return out;
	}

	private static void assertRoomKept(GameTestHelper helper, Village v) {
		for (Map.Entry<BlockPos, BlockState> e : v.room().entrySet()) {
			BlockState now = v.level().getBlockState(e.getKey());
			helper.assertTrue(now.getBlock() == e.getValue().getBlock(), "the room changed at " + helper.relativePos(e.getKey()) + ": "
				+ e.getValue() + " -> " + now);
		}
		helper.assertTrue(v.level().getBlockState(world(v.placement(), GRINDSTONE)).is(Blocks.GRINDSTONE), "the job block is gone");
		Container chest = v.level().getBlockEntity(world(v.placement(), ROOM_CHEST)) instanceof Container c ? c : null;
		helper.assertTrue(chest != null && chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 3,
			"the room's chest lost what was in it: " + (chest == null ? "no chest" : chest.getItem(0)));
	}

	/** Stocks the chests with what the desert outside still needs. */
	private static void stock(GameTestHelper helper, Village v, String look) {
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
		helper.assertTrue(!stacks.isEmpty() && stacks.size() <= 27, "the desert outside needs " + stacks.size() + " stacks");
		Container first = (Container) helper.getBlockEntity(CHESTS[0]);
		for (int i = 0; i < stacks.size(); i++) {
			first.setItem(i, stacks.get(i));
		}
	}

	/** The hall's screen on the House looks page (its Builds button), as {@code player} opens it. */
	private static ChoiceMenu looksPage(GameTestHelper helper, Village v, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, v.hall());
		helper.assertTrue(menu.icon(VillageHallScreen.BUILDS).is(Items.BRICKS)
			&& lore(menu.icon(VillageHallScreen.BUILDS)).contains("Click: choose how the village's houses look"), "the Builds button: "
			+ lore(menu.icon(VillageHallScreen.BUILDS)));
		menu.press(VillageHallScreen.BUILDS, player);
		helper.assertTrue(menu.icon(4).is(Items.OAK_DOOR), "not the House looks page: " + menu.icon(4));
		return menu;
	}

	/** The page's button for our house (its name and where it stands). */
	private static int houseSlot(GameTestHelper helper, ChoiceMenu menu, Village v) {
		BlockPos o = v.placement().origin();
		String at = "At " + o.getX() + ", " + o.getY() + ", " + o.getZ();
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			ItemStack icon = menu.icon(slot);
			if (name(icon).equals("Guard House") && lore(icon).contains(at)) {
				return slot;
			}
		}
		throw new GameTestAssertException("the page doesn't list the Guard House " + at);
	}

	private static String name(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name == null ? "" : name.getString();
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	/** Opens the page, clicks the house and the {@code look}'s outside, as {@code player}. */
	private static void choose(GameTestHelper helper, Village v, ServerPlayer player, String look) {
		ChoiceMenu menu = looksPage(helper, v, player);
		menu.press(houseSlot(helper, menu, v), player);
		int slot = PieceLooks.FIRST_LOOK + VillageHouses.STYLES.indexOf(look);
		helper.assertTrue(name(menu.icon(slot)).equals(VillagePieces.styleName(look).getString() + " outside"), "the look's button: " + name(menu.icon(slot)));
		menu.press(slot, player);
	}

	private static VillagePieces.Piece piece(GameTestHelper helper, Village v) {
		return PieceLooks.pieces(v.level(), v.hall()).stream().filter(p -> p.placement().origin().equals(v.placement().origin())).findFirst()
			.orElseThrow(() -> new GameTestAssertException("the house isn't found"));
	}

	/** The owner picks the desert outside on the hall's page; the builder rebuilds it, the room left as it was. */
	//$ gametest_ticks_batch AREA '12000' '"pieceLookRebuild"'
	@GameTest(template = AREA, timeoutTicks = 12000, batch = "pieceLookRebuild")
	public void ownerGivesAHouseANewOutside(GameTestHelper helper) {
		Village v = village(helper, true);
		VillagePieces.Piece found = piece(helper, v);
		helper.assertTrue(found.house().equals("guard_house") && found.style().equals("plains") && found.look().equals("plains"),
			"found as " + found.house() + " " + found.style() + "/" + found.look());
		stock(helper, v, "desert");
		ChoiceMenu page = looksPage(helper, v, v.owner());
		helper.assertTrue(lore(page.icon(houseSlot(helper, page, v))).contains("Outside: Plains"), "lore: " + lore(page.icon(houseSlot(helper, page, v))));
		choose(helper, v, v.owner(), "desert");
		BuildSite site = PieceLooks.rebuild(v.level(), found);
		helper.assertTrue(site != null && v.builder().getUUID().equals(site.builder()), "the builder isn't rebuilding it: " + site);
		helper.assertTrue(site.structure().equals(VillagePieces.outsideId("desert", "guard_house")), "site: " + site.structure());
		helper.assertTrue(v.entity().pieceLooks().size() == 1 && v.entity().pieceLooks().get(0).look().equals("desert"), "kept: " + v.entity().pieceLooks());
		BuildPlan plan = site.plan(v.level());
		helper.assertTrue(plan != null, "no plan");
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.CLEAR, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				helper.assertTrue(!v.room().containsKey(step.pos()), "the rebuild touches the room at " + helper.relativePos(step.pos()));
			}
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(v.level()).get(site.id()) == null, "still rebuilding: " + site.stage() + " "
				+ Math.round(site.progress(plan) * 100) + "% missing=" + site.missing() + " status=" + site.status());
			List<BlockPos> unfinished = plan.unfinished(v.level());
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " outside blocks wrong, e.g. "
				+ unfinished.stream().limit(3).map(p -> helper.relativePos(p) + "=" + v.level().getBlockState(p)).toList());
			assertRoomKept(helper, v);
			helper.assertTrue(VillagePieces.lookAt(v.level(), v.placement()).equals(Optional.of("desert")), "looks like " + VillagePieces.lookAt(v.level(), v.placement()));
			VillagePieces.Piece now = piece(helper, v);
			helper.assertTrue(now.house().equals("guard_house") && now.style().equals("plains") && now.look().equals("desert"),
				"now " + now.house() + " " + now.style() + "/" + now.look());
			ChoiceMenu after = looksPage(helper, v, v.owner());
			List<String> lines = lore(after.icon(houseSlot(helper, after, v)));
			helper.assertTrue(lines.contains("Outside: Desert") && lines.contains("Built as a Plains house"), "after: " + lines);
		});
	}

	/** A stranger clicking a look is refused by the server, and so is anyone in a village nobody leads; a friend may. */
	//$ gametest_ticks_batch AREA '200' '"pieceLookStranger"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "pieceLookStranger")
	public void onlyTheLeaderChoosesALook(GameTestHelper helper) {
		Village v = village(helper, false);
		VillagePieces.Piece found = piece(helper, v);
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		// nobody leads the village yet
		helper.assertTrue(PieceLooks.choose(v.level(), v.hall(), stranger, found, "desert") == PieceLooks.Outcome.NO_LEADER, "no leader: allowed");
		// the hall's owner leads it: a stranger's click on the page changes nothing
		UUID leader = UUID.randomUUID();
		v.entity().setOwner(leader, "Jesse");
		choose(helper, v, stranger, "desert");
		helper.assertTrue(PieceLooks.rebuild(v.level(), found) == null, "a stranger started a rebuild");
		helper.assertTrue(v.entity().pieceLooks().isEmpty(), "a stranger's look was kept: " + v.entity().pieceLooks());
		helper.assertTrue(PieceLooks.choose(v.level(), v.hall(), stranger, found, "desert") == PieceLooks.Outcome.NOT_ALLOWED, "not refused");
		String said = PieceLooks.message(v.level(), v.hall(), found, "desert", PieceLooks.Outcome.NOT_ALLOWED).getString();
		helper.assertTrue(said.equals("Only the village's leader, Jesse, and their friends can choose how its houses look."), "said: " + said);
		// the leader's friend may
		io.github.jcondedata.aliveworkplace.build.Friends.get(v.level().getServer()).add(leader, stranger.getUUID(), "friend");
		try {
			helper.assertTrue(PieceLooks.choose(v.level(), v.hall(), stranger, found, "desert") == PieceLooks.Outcome.STARTED, "a friend was refused");
		} finally {
			io.github.jcondedata.aliveworkplace.build.Friends.get(v.level().getServer()).remove(leader, stranger.getUUID());
		}
		BuildSite site = PieceLooks.rebuild(v.level(), found);
		helper.assertTrue(site != null, "the friend's choice started nothing");
		Builders.cancel(v.level(), site);
		helper.succeed();
	}

	/** Nothing in the chests: the builder waits for the desert outside's materials, says so on the page, the room untouched. */
	//$ gametest_ticks_batch AREA '4000' '"pieceLookMissing"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "pieceLookMissing")
	public void aRebuildWaitsForMaterials(GameTestHelper helper) {
		Village v = village(helper, true);
		VillagePieces.Piece found = piece(helper, v);
		choose(helper, v, v.owner(), "desert");
		BuildSite site = PieceLooks.rebuild(v.level(), found);
		helper.assertTrue(site != null, "no rebuild started");
		helper.succeedWhen(() -> {
			helper.assertTrue(!site.missing().isEmpty(), "not waiting yet: " + site.stage() + " status=" + site.status());
			helper.assertTrue(site.missing().containsKey(Items.CUT_SANDSTONE) || site.missing().containsKey(Items.SANDSTONE)
				|| site.missing().containsKey(Items.SMOOTH_SANDSTONE), "waiting for " + site.missing());
			assertRoomKept(helper, v);
			ChoiceMenu page = looksPage(helper, v, v.owner());
			List<String> lines = lore(page.icon(houseSlot(helper, page, v)));
			helper.assertTrue(lines.stream().anyMatch(l -> l.startsWith("Waiting for ")) && lines.stream().anyMatch(l -> l.startsWith("Being rebuilt with the Desert outside")),
				"page: " + lines);
			Builders.cancel(v.level(), site);
		});
	}

	/** Halfway through, the site and the hall's choice are saved and loaded again: nothing is lost, and the rebuild finishes. */
	//$ gametest_ticks_batch AREA '12000' '"pieceLookSave"'
	@GameTest(template = AREA, timeoutTicks = 12000, batch = "pieceLookSave")
	public void aRebuildSurvivesSaveAndReload(GameTestHelper helper) {
		Village v = village(helper, true);
		VillagePieces.Piece found = piece(helper, v);
		stock(helper, v, "taiga");
		choose(helper, v, v.owner(), "taiga");
		BuildSite site = PieceLooks.rebuild(v.level(), found);
		helper.assertTrue(site != null, "no rebuild started");
		BuildPlan plan = site.plan(v.level());
		String[] result = {null};
		helper.onEachTick(() -> {
			if (result[0] != null || site.stage() != BuildPlan.Stage.STRUCTURE || site.progress(plan) < 0.3f) {
				return;
			}
			float before = site.progress(plan);
			BuildSite loaded = BuildSite.load(site.save());
			BuildPlan again = loaded == null ? null : loaded.plan(v.level());
			if (again == null) {
				result[0] = "the site didn't load: " + loaded;
				return;
			}
			float after = loaded.progress(again);
			// the hall, saved and loaded: the choice is kept, and the house still shows, half one outside and half the other
			CompoundTag tag = v.entity().saveWithFullMetadata(v.level().registryAccess());
			VillageHallBlockEntity copy = (VillageHallBlockEntity) BlockEntity.loadStatic(v.hall(), v.level().getBlockState(v.hall()), tag, v.level().registryAccess());
			tag.remove("piece_looks");
			VillageHallBlockEntity old = (VillageHallBlockEntity) BlockEntity.loadStatic(v.hall(), v.level().getBlockState(v.hall()), tag, v.level().registryAccess());
			ChoiceMenu page = looksPage(helper, v, v.owner());
			List<String> lines = lore(page.icon(houseSlot(helper, page, v)));
			result[0] = !loaded.structure().equals(site.structure()) ? "loaded as " + loaded.structure()
				: after < before ? "progress dropped: " + before + " -> " + after
				: copy == null || !copy.pieceLooks().equals(v.entity().pieceLooks()) ? "the hall's choice: " + (copy == null ? null : copy.pieceLooks())
				: old == null || !old.pieceLooks().isEmpty() ? "an old hall loaded with " + (old == null ? null : old.pieceLooks())
				: lines.stream().noneMatch(l -> l.startsWith("Being rebuilt with the Taiga outside")) ? "page mid-rebuild: " + lines
				: "";
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(result[0] != null, "never halfway: " + site.stage() + " " + Math.round(site.progress(plan) * 100) + "% missing=" + site.missing());
			helper.assertTrue(result[0].isEmpty(), result[0]);
			helper.assertTrue(BuildSiteManager.get(v.level()).get(site.id()) == null, "still rebuilding: " + site.stage() + " "
				+ Math.round(site.progress(plan) * 100) + "% status=" + site.status() + " detail=" + (site.detail() == null ? null : site.detail().getString())
				+ " missing=" + site.missing() + " retry=" + site.retryLeft() + " skipped=" + site.skipped() + " builder at "
				+ helper.relativePos(v.builder().blockPosition()) + " unfinished=" + plan.unfinished(v.level()).stream().limit(4)
				.map(p -> helper.relativePos(p) + "=" + v.level().getBlockState(p)).toList());
			helper.assertTrue(plan.unfinished(v.level()).isEmpty(), "outside unfinished: " + plan.unfinished(v.level()).size());
			assertRoomKept(helper, v);
			helper.assertTrue(VillagePieces.lookAt(v.level(), v.placement()).equals(Optional.of("taiga")), "looks like " + VillagePieces.lookAt(v.level(), v.placement()));
		});
	}

	/** Every sentence the page and its messages show is in the language file. */
	//$ gametest_ticks_batch AREA '200' '"pieceLookWords"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "pieceLookWords")
	public void everyLookSentenceReads(GameTestHelper helper) {
		Village v = village(helper, true);
		VillagePieces.Piece found = piece(helper, v);
		List<Component> said = new ArrayList<>();
		for (PieceLooks.Outcome outcome : PieceLooks.Outcome.values()) {
			said.add(PieceLooks.message(v.level(), v.hall(), found, "snowy", outcome));
		}
		ChoiceMenu menu = looksPage(helper, v, v.owner());
		collect(menu, said);
		menu.press(houseSlot(helper, menu, v), v.owner());
		collect(menu, said);
		for (String house : VillagePieces.houses()) {
			said.add(VillagePieces.houseName(house));
		}
		said.add(io.github.jcondedata.aliveworkplace.blueprint.Blueprints.displayName(VillagePieces.outsideId("savanna", "clinic")));
		helper.assertTrue(io.github.jcondedata.aliveworkplace.blueprint.Blueprints.displayName(VillagePieces.outsideId("savanna", "clinic")).getString()
			.equals("Clinic: Savanna outside"), "the site's name");
		// the page with no houses near, and no leader
		v.entity().setOwner(null, "");
		ChoiceMenu none = VillageHallScreen.forTest(v.owner(), v.hall());
		none.press(VillageHallScreen.BUILDS, v.owner());
		collect(none, said);
		List<String> missing = new ArrayList<>();
		for (String key : List.of("screen.aliveworkplace.piece_looks.none", "screen.aliveworkplace.piece_looks.none_hint")) {
			if (!Language.getInstance().has(key)) {
				missing.add(key); // (the page with no houses near the hall)
			}
		}
		for (Component c : said) {
			keys(c, missing);
		}
		helper.assertTrue(missing.isEmpty(), "not in en_us.json: " + missing);
		helper.succeed();
	}

	// ---- QA (qa-1005-0833): the outcomes a player's click can meet, and a change of mind mid-rebuild ----------------

	/** Fills chest {@code index} with all the {@code look}'s outside needs, counted as if the chests were empty. */
	private static void stockIn(GameTestHelper helper, Village v, String look, int index) {
		List<List<ItemStack>> saved = new ArrayList<>();
		for (BlockPos c : CHESTS) {
			Container chest = (Container) helper.getBlockEntity(c);
			List<ItemStack> held = new ArrayList<>();
			for (int i = 0; i < chest.getContainerSize(); i++) {
				held.add(chest.getItem(i).copy());
				chest.setItem(i, ItemStack.EMPTY);
			}
			saved.add(held);
		}
		stock(helper, v, look);
		Container first = (Container) helper.getBlockEntity(CHESTS[0]);
		List<ItemStack> made = new ArrayList<>();
		for (int i = 0; i < first.getContainerSize(); i++) {
			made.add(first.getItem(i).copy());
		}
		for (int c = 0; c < CHESTS.length; c++) {
			Container chest = (Container) helper.getBlockEntity(CHESTS[c]);
			for (int i = 0; i < chest.getContainerSize(); i++) {
				chest.setItem(i, c == index ? made.get(i) : saved.get(c).get(i));
			}
		}
	}

	private static int inChests(GameTestHelper helper, net.minecraft.world.item.Item item) {
		int n = 0;
		for (BlockPos c : CHESTS) {
			Container chest = (Container) helper.getBlockEntity(c);
			for (int i = 0; i < chest.getContainerSize(); i++) {
				n += chest.getItem(i).is(item) ? chest.getItem(i).getCount() : 0;
			}
		}
		return n;
	}

	/** Clicking the outside the house already has starts nothing, keeps nothing new, and says so in a plain sentence. */
	//$ gametest_ticks_batch AREA '200' '"qaPieceLookSame"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaPieceLookSame")
	public void pickingTheOutsideItHasStartsNothing(GameTestHelper helper) {
		Village v = village(helper, true);
		VillagePieces.Piece found = piece(helper, v);
		choose(helper, v, v.owner(), "plains");
		helper.assertTrue(PieceLooks.rebuild(v.level(), found) == null, "picking its own outside started a rebuild");
		helper.assertTrue(PieceLooks.choose(v.level(), v.hall(), v.owner(), found, "plains") == PieceLooks.Outcome.SAME, "not SAME");
		String said = PieceLooks.message(v.level(), v.hall(), found, "plains", PieceLooks.Outcome.SAME).getString();
		helper.assertTrue(said.equals("The Guard House already has the Plains outside."), "said: " + said);
		assertRoomKept(helper, v);
		helper.succeed();
	}

	/** With no builder near, a pick starts nothing and the player is told why, with the distance as a number. */
	//$ gametest_ticks_batch AREA '200' '"qaPieceLookNoBuilder"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaPieceLookNoBuilder")
	public void withNoBuilderAPickIsExplained(GameTestHelper helper) {
		Village v = village(helper, true);
		VillagePieces.Piece found = piece(helper, v);
		v.builder().discard();
		PieceLooks.Outcome outcome = PieceLooks.choose(v.level(), v.hall(), v.owner(), found, "desert");
		helper.assertTrue(outcome == PieceLooks.Outcome.NO_BUILDER, "outcome " + outcome);
		helper.assertTrue(PieceLooks.rebuild(v.level(), found) == null, "a rebuild started with no builder");
		String said = PieceLooks.message(v.level(), v.hall(), found, "desert", outcome).getString();
		helper.assertTrue(said.equals("No builder of this village can reach the Guard House: give one a bench within "
			+ Builders.MAX_SITE_DISTANCE + " blocks of it."), "said: " + said);
		helper.assertTrue(VillagePieces.lookAt(v.level(), v.placement()).equals(Optional.of("plains")), "the house changed");
		helper.succeed();
	}

	/** An unknown style (a forged click) is refused and changes nothing. */
	//$ gametest_ticks_batch AREA '200' '"qaPieceLookForged"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaPieceLookForged")
	public void anUnknownStyleIsRefused(GameTestHelper helper) {
		Village v = village(helper, true);
		VillagePieces.Piece found = piece(helper, v);
		for (String look : List.of("", "nether", "PLAINS", "desert/../plains")) {
			PieceLooks.Outcome outcome = PieceLooks.choose(v.level(), v.hall(), v.owner(), found, look);
			helper.assertTrue(!outcome.ok(), "'" + look + "' was accepted: " + outcome);
		}
		helper.assertTrue(PieceLooks.rebuild(v.level(), found) == null, "a forged style started a rebuild");
		helper.assertTrue(v.entity().pieceLooks().isEmpty(), "a forged style was kept: " + v.entity().pieceLooks());
		helper.succeed();
	}

	/**
	 * The owner picks desert, the builder starts, then he clicks desert again (told it's already happening) and changes
	 * his mind to taiga (and stocks what it needs then): the house ends with the taiga outside, the room kept, one choice kept on the hall, one site.
	 */
	//$ gametest_ticks_batch AREA '16000' '"qaPieceLookChange"'
	@GameTest(template = AREA, timeoutTicks = 16000, batch = "qaPieceLookChange")
	public void changingTheLookMidRebuildEndsWithTheLastPick(GameTestHelper helper) {
		Village v = village(helper, true);
		VillagePieces.Piece found = piece(helper, v);
		stockIn(helper, v, "desert", 1);
		choose(helper, v, v.owner(), "desert");
		BuildSite first = PieceLooks.rebuild(v.level(), found);
		helper.assertTrue(first != null, "no desert rebuild started");
		PieceLooks.Outcome again = PieceLooks.choose(v.level(), v.hall(), v.owner(), found, "desert");
		helper.assertTrue(again == PieceLooks.Outcome.BUILDING, "clicking desert again: " + again);
		String said = PieceLooks.message(v.level(), v.hall(), found, "desert", again).getString();
		helper.assertTrue(said.equals("The Guard House is already being rebuilt with the Desert outside."), "said: " + said);
		BuildPlan firstPlan = first.plan(v.level());
		BuildSite[] second = {null};
		helper.onEachTick(() -> {
			if (second[0] == null && first.stage() == BuildPlan.Stage.STRUCTURE && first.progress(firstPlan) >= 0.25f) {
				// what the taiga outside needs now, as the page's "Waiting for" would tell him (the desert clearing took
				// down some of the plains blocks the taiga outside could have kept)
				stockIn(helper, v, "taiga", 2);
				choose(helper, v, v.owner(), "taiga");
				second[0] = PieceLooks.rebuild(v.level(), found);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(second[0] != null, "never changed: desert at " + first.stage() + " " + Math.round(first.progress(firstPlan) * 100) + "%");
			helper.assertTrue(second[0] != first && second[0].structure().equals(VillagePieces.outsideId("taiga", "guard_house")),
				"the change of mind: " + second[0].structure());
			helper.assertTrue(BuildSiteManager.get(v.level()).get(first.id()) == null, "the desert rebuild is still open");
			BuildPlan plan = second[0].plan(v.level());
			helper.assertTrue(BuildSiteManager.get(v.level()).get(second[0].id()) == null, "still rebuilding: " + second[0].stage() + " "
				+ Math.round(second[0].progress(plan) * 100) + "% missing=" + second[0].missing() + " status=" + second[0].status()
				+ " bag=" + ModAttachments.BUILDER_BAG.getOrCreate(v.builder()).count(Items.STONE) + " stone, "
				+ ModAttachments.BUILDER_BAG.getOrCreate(v.builder()).count(Items.GLASS_PANE) + " panes, "
				+ ModAttachments.BUILDER_BAG.getOrCreate(v.builder()).count(Items.CAMPFIRE) + " campfires; in chests "
				+ inChests(helper, Items.STONE) + " stone, " + inChests(helper, Items.GLASS_PANE) + " panes, " + inChests(helper, Items.CAMPFIRE) + " campfires");
			List<BlockPos> unfinished = plan.unfinished(v.level());
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " outside blocks wrong, e.g. "
				+ unfinished.stream().limit(3).map(p -> helper.relativePos(p) + "=" + v.level().getBlockState(p)).toList());
			assertRoomKept(helper, v);
			helper.assertTrue(VillagePieces.lookAt(v.level(), v.placement()).equals(Optional.of("taiga")), "looks like " + VillagePieces.lookAt(v.level(), v.placement()));
			helper.assertTrue(v.entity().pieceLooks().size() == 1 && v.entity().pieceLooks().get(0).look().equals("taiga"), "kept: " + v.entity().pieceLooks());
		});
	}

	private static void collect(ChoiceMenu menu, List<Component> out) {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ItemStack icon = menu.icon(slot);
			Component name = icon.get(DataComponents.CUSTOM_NAME);
			if (name != null) {
				out.add(name);
			}
			ItemLore lore = icon.get(DataComponents.LORE);
			if (lore != null) {
				out.addAll(lore.lines());
			}
		}
	}

	private static void keys(Component c, List<String> missing) {
		if (c.getContents() instanceof TranslatableContents t) {
			if (!Language.getInstance().has(t.getKey()) && t.getFallback() == null) {
				missing.add(t.getKey());
			}
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component a) {
					keys(a, missing);
				}
			}
		}
		for (Component sibling : c.getSiblings()) {
			keys(sibling, missing);
		}
	}
}
