package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * SCENE=colony (ROADMAP 33.10): Thornholm, a village that builds in Sandstone, has settlers on the road to Newbrook,
 * 48 blocks east (the scene's distance: a real colony is 256 blocks out at least). The camera waits over the empty spot;
 * the journey ends and the settlers make camp facing home, the Village Hall and the Travel Post in front of the wagon;
 * Tomas the builder takes the camp's Blueprint Table and starts the Sandstone Starter Cottage on one side of the camp,
 * with the Storehouse queued on the other, and the first walls go up. The owner has the letter. The scene's shortcuts:
 * the journey is 6 seconds, the builder has his materials free ({@code workplaceFreeMaterials}: the scene shows the
 * arrival, not the first caravan) and lays a block every 2 ticks.
 */
final class ColonyScene {
	private static final int DX = 48;
	private int tick;
	private volatile BlockPos hall;
	private volatile BlockPos spot;
	/** 33.23's {@code founder_colony}: the order carries the Founder's blessing, and the scene ends at his statue. */
	private final boolean blessed;

	ColonyScene() {
		this(false);
	}

	ColonyScene(boolean blessed) {
		this.blessed = blessed;
	}

	void tick(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.guiScale().set(2);
			mc.resizeDisplay();
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, server);
				level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, server);
				level.setDayTime(1000);
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "forceload add -32 -48 96 48");
			});
		}
		if (tick == 380) {
			server.execute(() -> stage(server));
		}
		// The spot, empty: the settlers are still on the road.
		if (tick == 440) {
			ScreenshotHarness.shot(mc, "01_the_spot");
			server.execute(() -> {
				RealmData.Order order = order(server);
				Showcase.check(order != null && order.state().equals(RealmData.ON_ROAD), "the settlers are on the road to Newbrook");
			});
		}
		// They've arrived: the camp, the hall and the post, the settlers as they were.
		if (tick == 600) {
			ScreenshotHarness.shot(mc, "02_camp_made");
			server.execute(() -> {
				ServerLevel level = server.overworld();
				RealmData realms = RealmData.get(server);
				List<RealmData.Founded> founded = realms.foundedBy(GlobalPos.of(level.dimension(), hall));
				Showcase.check(order(server) == null && founded.size() == 1, "the settlers arrived and founded the colony");
				if (founded.isEmpty()) {
					return;
				}
				BlockPos colony = founded.get(0).hall().pos();
				Showcase.check(level.getBlockEntity(colony) instanceof VillageHallBlockEntity e && VillageHalls.name(level, colony).getString().equals("Newbrook")
					&& player(server).getUUID().equals(e.owner()), "the hall is Newbrook's, owned by Thornholm's owner");
				Showcase.check(Caravans.Data.get(level).isSister(hall, colony), "Thornholm and Newbrook are sister villages");
				Villager tomas = named(level, "Tomas");
				Villager dara = named(level, "Dara");
				Showcase.check(tomas != null && tomas.getVillagerData().getProfession() == ModVillagers.BUILDER
					&& dara != null && dara.getVillagerData().getProfession() == VillagerProfession.FARMER, "Tomas and Dara stepped out as they were");
				boolean letter = false;
				for (int i = 0; i < player(server).getInventory().getContainerSize(); i++) {
					letter |= player(server).getInventory().getItem(i).has(DataComponents.WRITTEN_BOOK_CONTENT);
				}
				Showcase.check(letter, "the owner has the settlers' letter");
			});
		}
		// 33.23: the Founder's blessing in both chronicles, and his statue beside the camp, looked at from in front
		if (blessed && tick == 610) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				List<RealmData.Founded> founded = RealmData.get(server).foundedBy(GlobalPos.of(level.dimension(), hall));
				if (founded.isEmpty()) {
					return;
				}
				BlockPos colony = founded.get(0).hall().pos();
				boolean theirs = level.getBlockEntity(colony) instanceof VillageHallBlockEntity e
					&& e.chronicle().stream().anyMatch(x -> x.text().getString().contains("with the Founder's blessing"));
				boolean ours = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e
					&& e.chronicle().stream().anyMatch(x -> x.text().getString().contains("with the Founder's blessing"));
				Showcase.check(theirs && ours, "both chronicles say the colony was founded with the Founder's blessing");
				BlockPos copper = null;
				for (BlockPos p : BlockPos.betweenClosed(colony.offset(-40, -6, -40), colony.offset(40, 12, 40))) {
					if (level.getBlockState(p).is(Blocks.WAXED_CUT_COPPER)) {
						copper = p.immutable();
						break;
					}
				}
				Showcase.check(copper != null, "the Founder's statue stands beside the camp");
				if (copper != null) {
					ServerPlayer player = player(server);
					player.teleportTo(level, copper.getX() - 7.5, copper.getY() - 1, copper.getZ() + 0.5, -90, 10);
				}
			});
		}
		if (blessed && tick == 680) {
			ScreenshotHarness.shot(mc, "03_founder_statue");
		}
		if (blessed && tick == 700) {
			mc.stop();
			return;
		}
		// The builder at work on the Sandstone cottage, the Storehouse next.
		if (tick == 760) {
			ScreenshotHarness.shot(mc, "03_builder_at_work");
			server.execute(() -> {
				ServerLevel level = server.overworld();
				Villager tomas = named(level, "Tomas");
				BuildSite site = tomas == null ? null : Builders.activeSite(level, tomas);
				ResourceLocation cottage = BlueprintStyles.styled(StarterBlueprints.STARTER_COTTAGE.id(), "sandstone");
				ResourceLocation storehouse = BlueprintStyles.styled(StarterBlueprints.STOREHOUSE.id(), "sandstone");
				Showcase.check(site != null && site.structure().equals(cottage) && Builders.queue(level, tomas).stream().anyMatch(s -> s.structure().equals(storehouse)),
					"Tomas builds the Sandstone cottage, the Sandstone storehouse queued (" + (site == null ? null : site.structure()) + ")");
			});
		}
		// The first walls.
		if (tick == 1500) {
			ScreenshotHarness.shot(mc, "04_first_walls");
			server.execute(() -> {
				ServerLevel level = server.overworld();
				Villager tomas = named(level, "Tomas");
				BuildSite site = tomas == null ? null : Builders.activeSite(level, tomas);
				int sandstone = 0;
				if (site != null) {
					BoundingBox box = BlueprintLibrary.get(level, site.structure()).map(b -> BlueprintOutline.bounds(site.placement(), b.size()))
						.orElse(new BoundingBox(site.placement().origin()));
					for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
						if (level.getBlockState(p).is(Blocks.SANDSTONE) || level.getBlockState(p).is(Blocks.CUT_SANDSTONE) || level.getBlockState(p).is(Blocks.SMOOTH_SANDSTONE)) {
							sandstone++;
						}
					}
				}
				Showcase.check(sandstone >= 10, "the first sandstone walls are up (" + sandstone + " blocks)");
			});
		}
		if (tick == 1520) {
			mc.stop();
		}
	}

	private RealmData.Order order(MinecraftServer server) {
		return RealmData.get(server).orderOf(GlobalPos.of(server.overworld().dimension(), hall));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	private static Villager named(ServerLevel level, String name) {
		for (Villager v : level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(-32, -64, -48, 96, 0, 48))) {
			if (v.isAlive() && v.getDisplayName().getString().equals(name)) {
				return v;
			}
		}
		return null;
	}

	/**
	 * Thornholm's hall, owned by the player and on record as building in Sandstone; Tomas (a Journeyman builder) and Dara
	 * (a farmer) on the road to Newbrook with the supplies, there in 6 seconds; the player over the spot, looking east.
	 */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		hall = ground(level, 0, 0);
		spot = ground(level, DX, 0);
		level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.EAST));
		ServerPlayer player = player(server);
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setCustomName(Component.literal("Thornholm"));
		entity.setOwner(player.getUUID(), player.getGameProfile().getName());
		// Thornholm builds in Sandstone: a finished Sandstone cottage on its record.
		ResourceLocation sandstone = BlueprintStyles.styled(StarterBlueprints.STARTER_COTTAGE.id(), "sandstone");
		BuildSiteManager.get(level).recordFinished(sandstone,
			new BlueprintData.Placement(level.dimension().location(), hall.offset(-14, 0, -5), Rotation.NONE, Mirror.NONE), player.getUUID());
		// The settlers on the road, as the road keeps them.
		CompoundTag tomas = settler(level, "Tomas", ModVillagers.BUILDER, 2, 20);
		CompoundTag dara = settler(level, "Dara", VillagerProfession.FARMER, 1, 0);
		Map<Item, Integer> supplies = new LinkedHashMap<>();
		supplies.put(Items.OAK_LOG, 64);
		supplies.put(Items.OAK_PLANKS, 64);
		supplies.put(Items.COBBLESTONE, 64);
		supplies.put(Items.BREAD, 32);
		supplies.put(Items.TORCH, 16);
		supplies.put(Items.GLASS_PANE, 12);
		supplies.put(Items.WHITE_BED, 3);
		long now = level.getGameTime();
		RealmData.get(server).putOrder(new RealmData.Order(GlobalPos.of(level.dimension(), hall), spot, Optional.of(Component.literal("Newbrook")),
			RealmData.ON_ROAD, now - 2000, now + 120, 3200, now - 3000, List.of(), Optional.of(tomas.getUUID("UUID")), supplies, new ArrayList<>(List.of(tomas, dara)), blessed));
		// west of the spot, looking east over it: the camp will face us, the builds either side
		BlockPos stand = ground(level, DX - 21, 0);
		player.teleportTo(level, stand.getX() + 0.5, stand.getY() + 8, stand.getZ() + 0.5, -90, 20);
		player.getAbilities().flying = true;
		player.getAbilities().mayfly = true;
		player.onUpdateAbilities();
	}

	private static BlockPos ground(ServerLevel level, int x, int z) {
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	/** A settler made and saved the way the road keeps them, never in the world here. */
	private static CompoundTag settler(ServerLevel level, String name, VillagerProfession job, int jobLevel, int xp) {
		Villager villager = EntityType.VILLAGER.create(level);
		villager.setCustomName(Component.literal(name));
		villager.setVillagerData(villager.getVillagerData().setProfession(job).setLevel(jobLevel));
		villager.setVillagerXp(xp);
		villager.setPersistenceRequired();
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		return tag;
	}
}
