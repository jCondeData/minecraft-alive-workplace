package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1103-0052), verifying B841 from its spec: a colony founded by one settler reads in the singular, in the
 * owner's chat ("Tomas has founded Newbrook, ...") and the mother's chronicle ("Our settler Tomas founded ..."), the
 * Founder's blessed form too, and the colony's own chronicle ("by a settler from Thornholm: Tomas", B853); two settlers
 * stay plural. Every line the arrival writes is checked for a raw key or an
 * unfilled %s, and en_us.json for every key the arrival can pick.
 */
public class QaColonyWordsVerify1103GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:colony_area";

	private static final String[] KEYS = {
		"message.aliveworkplace.colony.arrived", "message.aliveworkplace.colony.arrived.one",
		"chronicle.aliveworkplace.colony.mother", "chronicle.aliveworkplace.colony.mother.one",
		"chronicle.aliveworkplace.colony.mother_blessed", "chronicle.aliveworkplace.colony.mother_blessed.one",
		"chronicle.aliveworkplace.colony.founded", "chronicle.aliveworkplace.colony.founded_blessed",
		"chronicle.aliveworkplace.colony.founded.one", "chronicle.aliveworkplace.colony.founded_blessed.one"};

	/** A lone builder Tomas, unblessed: the singular in chat and in the mother's chronicle, no blessing. */
	//$ gametest_ticks_batch AREA '300' '"qaColWords1103One"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaColWords1103One")
	public void qaOneSettlerReadsHasFoundedAndOurSettler(GameTestHelper helper) {
		arrival(helper, List.of("Tomas"), false);
	}

	/** A lone builder Tomas with the Founder's blessing: "Our settler Tomas founded ..., with the Founder's blessing". */
	//$ gametest_ticks_batch AREA '300' '"qaColWords1103OneBlessed"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaColWords1103OneBlessed")
	public void qaOneBlessedSettlerReadsInTheSingular(GameTestHelper helper) {
		arrival(helper, List.of("Tomas"), true);
	}

	/** The control: Tomas and Dara stay plural in both the chat and the chronicle. */
	//$ gametest_ticks_batch AREA '300' '"qaColWords1103Two"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaColWords1103Two")
	public void qaTwoSettlersStayPlural(GameTestHelper helper) {
		arrival(helper, List.of("Tomas", "Dara"), false);
	}

	private static void arrival(GameTestHelper helper, List<String> names, boolean blessed) {
		for (String key : KEYS) {
			helper.assertTrue(Language.getInstance().has(key), "en_us.json lacks " + key);
		}
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		boolean wasEnabled = Colonies.ENABLED;
		int every = VillageNeeds.CHECK_EVERY;
		int radius = VillageHalls.RADIUS;
		Colonies.ENABLED = true;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		VillageHalls.RADIUS = 8;
		RealmData.get(level.getServer()).clearColonies();
		AABB box = helper.getBounds();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		BlockPos at = new BlockPos(3, 2, 22);
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(at);
		List<Component> heard = new ArrayList<>();
		ServerPlayer owner = RealmGameTests.listener(helper, heard, "colony-words-1103");
		owner.setPos(hall.getX() + 0.5, hall.getY(), hall.getZ() + 3.5);
		VillageHallBlockEntity entity = helper.getBlockEntity(at);
		entity.setCustomName(Component.literal("Thornholm"));
		entity.setOwner(owner.getUUID(), "Owner");
		Leftovers.after(helper, () -> {
			Colonies.ENABLED = wasEnabled;
			VillageNeeds.CHECK_EVERY = every;
			VillageHalls.RADIUS = radius;
			RealmData realms = RealmData.get(level.getServer());
			Caravans.Data caravans = Caravans.Data.get(level);
			for (RealmData.Founded f : realms.founded()) {
				caravans.remove(f.hall().pos());
				TravelNetwork network = TravelNetwork.get(level.getServer());
				for (BlockPos p : BlockPos.betweenClosed(f.hall().pos().offset(-8, -2, -8), f.hall().pos().offset(8, 2, 8))) {
					network.remove(GlobalPos.of(level.dimension(), p.immutable()));
				}
			}
			caravans.remove(hall);
			caravans.clearRoad(hall);
			realms.clearColonies();
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (box.inflate(16).contains(site.placement().origin().getCenter())) {
					sites.remove(site.id());
				}
			}
			for (Villager v : level.getEntitiesOfClass(Villager.class, box.inflate(16))) {
				v.discard();
			}
			// a blessed colony's statue may stand past the area's walls: taken down again (as RealmLegendsGameTests does)
			for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX - 24, box.minY, box.minZ - 24), BlockPos.containing(box.maxX + 24, box.maxY + 4, box.maxZ + 24))) {
				if (!box.contains(p.getCenter()) && statueBlock(level, p)) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
				}
			}
		});
		List<CompoundTag> settlers = new ArrayList<>();
		for (int i = 0; i < names.size(); i++) {
			Villager x = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2 + i));
			x.setCustomName(Component.literal(names.get(i)));
			x.setVillagerData(x.getVillagerData().setProfession(i == 0 ? ModVillagers.BUILDER : VillagerProfession.FARMER).setLevel(2));
			CompoundTag tag = new CompoundTag();
			x.saveWithoutId(tag);
			x.discard();
			settlers.add(tag);
		}
		Map<Item, Integer> supplies = new LinkedHashMap<>();
		supplies.put(Items.OAK_LOG, 64);
		supplies.put(Items.BREAD, 32);
		supplies.put(Items.WHITE_BED, 3);
		GlobalPos mother = GlobalPos.of(level.dimension(), hall);
		BlockPos spot = helper.absolutePos(new BlockPos(22, 2, 22));
		helper.runAfterDelay(2, () -> {
			long now = level.getGameTime();
			RealmData.get(level.getServer()).putOrder(new RealmData.Order(mother, spot, Optional.of(Component.literal("Newbrook")), RealmData.ON_ROAD,
				now - 100, now, Colonies.COST * 100, now - 200, List.of(), Optional.of(settlers.get(0).getUUID("UUID")), supplies, List.copyOf(settlers), blessed));
		});
		boolean one = names.size() == 1;
		String who = String.join(" and ", names);
		String chat = who + (one ? " has" : " have") + " founded Newbrook, [^%]+ of Thornholm\\. A letter is on its way\\.";
		String ours = (one ? "Our settler " : "Our settlers ") + who + " founded Newbrook, ";
		// B853: the colony's own chronicle in the singular too ("by a settler from Thornholm: Tomas")
		String founded = (blessed ? " with the Founder's blessing, by " : " by ") + (one ? "a settler" : "settlers") + " from Thornholm: " + who;
		String wrongFounded = (one ? "settlers" : "a settler") + " from Thornholm";
		helper.succeedWhen(() -> {
			RealmData realms = RealmData.get(level.getServer());
			helper.assertTrue(realms.orders().isEmpty() && realms.foundedBy(mother).size() == 1, "not founded yet");
			BlockPos colony = realms.foundedBy(mother).get(0).hall().pos();
			List<String> lines = heard.stream().map(Component::getString).toList();
			List<String> mine = chronicle(level, hall);
			List<String> theirs = chronicle(level, colony);
			String report = " | the owner heard: " + lines + " | the mother's chronicle: " + mine + " | the colony's: " + theirs;
			helper.assertTrue(lines.stream().anyMatch(l -> l.matches(chat)), "want chat '" + chat + "'" + report);
			helper.assertTrue(mine.stream().anyMatch(l -> l.startsWith(ours) && (blessed == l.endsWith(", with the Founder's blessing"))),
				"want the mother's chronicle '" + ours + "...'" + (blessed ? " with the blessing" : " without it") + report);
			helper.assertTrue(mine.stream().noneMatch(l -> l.startsWith(one ? "Our settlers " : "Our settler " + who)), "the wrong number in the mother's chronicle" + report);
			helper.assertTrue(lines.stream().noneMatch(l -> l.contains(one ? who + " have founded" : who + " has founded")), "the wrong number in chat" + report);
			helper.assertTrue(theirs.stream().anyMatch(l -> l.startsWith("Founded on day ") && l.endsWith(founded)), "want the colony's chronicle '..." + founded + "'" + report);
			helper.assertTrue(theirs.stream().noneMatch(l -> l.contains(wrongFounded)), "the wrong number in the colony's chronicle" + report);
			for (String l : concat(lines, mine, theirs)) {
				helper.assertTrue(!l.contains("aliveworkplace.") && !l.contains("%s") && !l.contains("%1$") && !l.contains("%2$") && !l.contains("%3$") && !l.contains("%4$"),
					"a raw key or unfilled argument: '" + l + "'");
			}
			helper.getLevel().getServer().sendSystemMessage(Component.literal("[qa 1103] colony words " + names + (blessed ? " blessed" : "") + report));
		});
	}

	private static List<String> concat(List<String> a, List<String> b, List<String> c) {
		List<String> all = new ArrayList<>(a);
		all.addAll(b);
		all.addAll(c);
		return all;
	}

	private static List<String> chronicle(ServerLevel level, BlockPos hall) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e.chronicle().stream().map(x -> x.text().getString()).toList() : List.of();
	}

	private static boolean statueBlock(ServerLevel level, BlockPos p) {
		var s = level.getBlockState(p);
		return s.is(Blocks.WAXED_CUT_COPPER) || s.is(Blocks.CHISELED_STONE_BRICKS) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.CRACKED_STONE_BRICKS)
			|| s.is(Blocks.STONE_BRICK_STAIRS) || s.is(Blocks.STONE_BRICK_SLAB) || s.is(Blocks.STONE_BRICK_WALL) || s.is(Blocks.ANDESITE) || s.is(Blocks.ANDESITE_WALL)
			|| s.is(Blocks.POLISHED_ANDESITE) || s.is(Blocks.POLISHED_ANDESITE_STAIRS) || s.is(Blocks.LANTERN) || s.is(Blocks.STONE_BUTTON) || s.is(Blocks.STONE_SLAB);
	}
}
