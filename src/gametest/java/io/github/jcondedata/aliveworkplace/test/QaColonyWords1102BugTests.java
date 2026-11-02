package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.colony.ColonyArrival;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1102-1853), NOT registered: a colony founded by ONE settler (the other died before they set out; the
 * order still leaves when one is left, Settlers.tick only calls it off when nobody is). The owner's line reads
 * "Tomas have founded Newbrook, ..." (message.aliveworkplace.colony.arrived "%1$s have founded" has no one-settler
 * form) and the mother's chronicle "Our settlers Tomas founded Newbrook, ..." (chronicle...colony.mother).
 */
public class QaColonyWords1102BugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:colony_area";

	//$ gametest_ticks_batch AREA '300' '"qaColWords1102OneArrives"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaColWords1102OneArrives")
	public void qaOneSettlerFoundsAColonyInTheSingular(GameTestHelper helper) {
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
		BlockPos at = new BlockPos(22, 2, 1);
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(at);
		List<Component> heard = new ArrayList<>();
		ServerPlayer owner = RealmGameTests.listener(helper, heard, "colony-words-owner");
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
		});
		Villager x = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		x.setCustomName(Component.literal("Tomas"));
		x.setVillagerData(x.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(2));
		CompoundTag tomas = new CompoundTag();
		x.saveWithoutId(tomas);
		x.discard();
		Map<Item, Integer> supplies = new LinkedHashMap<>();
		supplies.put(Items.OAK_LOG, 64);
		supplies.put(Items.OAK_PLANKS, 64);
		supplies.put(Items.COBBLESTONE, 64);
		supplies.put(Items.BREAD, 32);
		supplies.put(Items.TORCH, 16);
		supplies.put(Items.GLASS_PANE, 12);
		supplies.put(Items.WHITE_BED, 3);
		GlobalPos mother = GlobalPos.of(level.dimension(), hall);
		BlockPos spot = helper.absolutePos(new BlockPos(22, 2, 24));
		helper.runAfterDelay(2, () -> {
			long now = level.getGameTime();
			RealmData.get(level.getServer()).putOrder(new RealmData.Order(mother, spot, Optional.of(Component.literal("Newbrook")), RealmData.ON_ROAD,
				now - 100, now, Colonies.COST * 100, now - 200, List.of(), Optional.of(tomas.getUUID("UUID")), supplies, List.of(tomas)));
		});
		helper.succeedWhen(() -> {
			RealmData realms = RealmData.get(level.getServer());
			helper.assertTrue(realms.orders().isEmpty() && realms.foundedBy(mother).size() == 1, "not founded yet");
			List<String> lines = heard.stream().map(Component::getString).toList();
			List<String> ours = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e.chronicle().stream().map(c -> c.text().getString()).toList() : List.of();
			String report = " | what the owner heard: " + lines + " | the mother's chronicle: " + ours;
			helper.assertTrue(lines.stream().anyMatch(l -> l.matches("Tomas has founded Newbrook, \\d+ blocks south of Thornholm\\. A letter is on its way\\.")),
				"want 'Tomas has founded Newbrook, ...'" + report);
			helper.assertTrue(ours.stream().noneMatch(l -> l.startsWith("Our settlers Tomas founded")), "want 'Our settler Tomas founded ...'" + report);
		});
	}
}
