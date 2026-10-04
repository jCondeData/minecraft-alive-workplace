package io.github.jcondedata.aliveworkplace.mine;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/** Hiring miners for quarries: hand-over, status, finishing and cancelling. */
public final class Miners {
	/** A quarry must be within this many blocks of the miner's bench. */
	public static final int MAX_DISTANCE = 64;

	public static boolean isMiner(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.MINER;
	}

	@Nullable
	public static QuarrySite activeSite(ServerLevel level, Villager villager) {
		BuilderJob job = ModAttachments.MINER_JOB.get(villager);
		if (job == null) {
			return null;
		}
		QuarrySite site = QuarrySiteManager.get(level).get(job.siteId());
		if (site == null) {
			ModAttachments.MINER_JOB.remove(villager);
		} else if (site.looksStretched()) {
			stopStretched(level, site);
			return null;
		}
		return site;
	}

	/**
	 * Stops a quarry an old version stretched over the ground around the Miner's Bench (see
	 * {@link QuarrySite#looksStretched}), before the miner digs any more of it. The marker comes back blank: its corners
	 * would be the stretched ones.
	 */
	static void stopStretched(ServerLevel level, QuarrySite site) {
		io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.warn("Stopped quarry {} of {}: it had been stretched over its Miner's Bench at {} by a bug before 0.45.0",
			site.id(), site.ownerName(), site.bench());
		ItemStack blank = new ItemStack(ModItems.QUARRY_MARKER);
		blank.set(ModComponents.QUARRY, new QuarryData(Optional.empty(), Optional.empty(), Optional.empty(), site.depth()));
		Villager villager = site.miner() != null && level.getEntity(site.miner()) instanceof Villager v ? v : null;
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner());
		if (owner != null) {
			tell(owner, Component.translatable("message.aliveworkplace.quarry.stretched",
				villager != null ? villager.getDisplayName() : Component.translatable("entity.minecraft.villager")), ChatFormatting.YELLOW);
		}
		cancel(level, site, blank);
	}

	/** Player right-clicked a miner while holding a Quarry Marker. */
	public static InteractionResult assign(ServerPlayer player, Villager villager, ItemStack stack) {
		ServerLevel level = Players.level(player);
		if (!Friends.mayCommand(player, villager)) {
			Employer employer = ModAttachments.BUILDER_EMPLOYER.get(villager);
			tell(player, Component.translatable("message.aliveworkplace.not_your_builder", villager.getDisplayName(),
				employer != null ? employer.name() : "?", player.getGameProfile().getName()), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		QuarryData data = QuarryMarkerItem.data(stack);
		int floor = level.getMinBuildHeight() + 5;
		Optional<BoundingBox> area = data.area(floor);
		if (area.isEmpty()) {
			tell(player, Component.translatable("message.aliveworkplace.quarry.not_marked"), ChatFormatting.YELLOW);
			return InteractionResult.CONSUME;
		}
		if (!data.dimension().get().equals(Ids.of(level.dimension()))) {
			tell(player, Component.translatable("message.aliveworkplace.assign.wrong_dimension"), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		QuarrySite existing = activeSite(level, villager);
		if (existing != null) {
			tell(player, Component.translatable("message.aliveworkplace.quarry.busy", villager.getDisplayName()), ChatFormatting.YELLOW);
			return InteractionResult.CONSUME;
		}
		Optional<BlockPos> bench = Builders.benchPos(villager);
		if (bench.isEmpty()) {
			tell(player, Component.translatable("message.aliveworkplace.quarry.no_bench"), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		if (!QuarryData.fits(area.get().getXSpan(), area.get().getZSpan(), data.isStripMine())) {
			tell(player, QuarryMarkerItem.tooBig(area.get().getXSpan(), area.get().getZSpan(), data.isStripMine()), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		BoundingBox box = clampToWorld(level, area.get());
		java.util.OptionalInt shaft = data.shaftTop(floor);
		BlockPos center = box.getCenter();
		// Down a shaft, the way there is straight down: only the distance across counts.
		double distance = Math.sqrt(shaft.isPresent() ? new BlockPos(center.getX(), bench.get().getY(), center.getZ()).distSqr(bench.get())
			: center.distSqr(bench.get()));
		if (distance > MAX_DISTANCE) {
			tell(player, Component.translatable("message.aliveworkplace.assign.too_far", (int) distance, MAX_DISTANCE), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		if (box.isInside(bench.get())) {
			tell(player, Component.translatable("message.aliveworkplace.quarry.over_bench"), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		Friends.hire(player, villager);
		QuarrySite site = start(level, villager, player, box, data.depth());
		if (data.isStripMine()) {
			site.setStripMine(true);
		}
		if (shaft.isPresent()) {
			site.setShaft(shaftStart(level, site, shaft.getAsInt()));
		}
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		level.playSound(null, villager, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		if (site.hasShaft()) {
			tell(player, Component.translatable("message.aliveworkplace.quarry.started_shaft", villager.getDisplayName(), box.minY(), box.getXSpan(),
				box.getZSpan(), site.shaftLength() + 2, SupplyContainers.RADIUS), ChatFormatting.GREEN);
		} else {
			tell(player, Component.translatable("message.aliveworkplace.quarry.started", villager.getDisplayName(), box.getXSpan(), box.getZSpan(),
				box.getYSpan(), SupplyContainers.RADIUS), ChatFormatting.GREEN);
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * Where a ladder shaft starts: the marked height, or lower where the corner it comes down at is lower ground (it
	 * starts at the first block that isn't open air, instead of standing a ladder tower up to the marked height).
	 */
	public static int shaftStart(ServerLevel level, QuarrySite site, int top) {
		BlockPos column = site.shaftColumn();
		int y = top;
		while (y > site.box().maxY() + 1) {
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(new BlockPos(column.getX(), y, column.getZ()));
			if (!state.isAir() && !(state.canBeReplaced() && state.getFluidState().isEmpty())) {
				break;
			}
			y--;
		}
		return y;
	}

	/** Never dig into the bottom few layers of the world (void, bedrock). */
	static BoundingBox clampToWorld(ServerLevel level, BoundingBox box) {
		int floor = level.getMinBuildHeight() + 5;
		return new BoundingBox(box.minX(), Math.max(floor, box.minY()), box.minZ(), box.maxX(), Math.max(floor, box.maxY()), box.maxZ());
	}

	/** Creates the quarry and gives the miner the job. Also used by tests. */
	public static QuarrySite start(ServerLevel level, Villager villager, @Nullable Player owner, BoundingBox box, int depth) {
		QuarrySite site = QuarrySiteManager.get(level).create(
			owner != null ? owner.getUUID() : villager.getUUID(),
			owner != null ? owner.getGameProfile().getName() : "",
			Ids.of(level.dimension()), clampToWorld(level, box), depth);
		site.setMiner(villager.getUUID());
		site.setBench(Builders.benchPos(villager).orElse(null));
		site.planStairs(site.bench());
		ModAttachments.MINER_JOB.set(villager, new BuilderJob(site.id()));
		return site;
	}

	public static Component statusText(ServerLevel level, QuarrySite site, @Nullable Villager villager) {
		MutableComponent text = Component.empty();
		Component who = villager != null ? villager.getDisplayName() : Component.translatable("message.aliveworkplace.quarry.miner");
		BoundingBox box = site.box();
		text.append(Component.translatable("message.aliveworkplace.quarry.header", who, box.getXSpan(), box.getZSpan(), box.getYSpan(),
			Math.round(site.progress() * 100)).withStyle(ChatFormatting.GOLD));
		text.append(Component.literal("\n  "));
		text.append(Component.translatable("message.aliveworkplace.quarry.state." + site.status().name().toLowerCase()).withStyle(
			site.status() == QuarrySite.Status.NEEDS_PICKAXE || site.status() == QuarrySite.Status.NEEDS_LADDERS ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
		text.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
		text.append(Component.translatable("message.aliveworkplace.quarry.counts", site.mined(), site.skipped()).withStyle(ChatFormatting.GRAY));
		if (villager != null) {
			text.append(Component.literal("\n  "));
			text.append(BuilderLevels.describe(villager).copy().withStyle(ChatFormatting.DARK_AQUA));
		}
		String cancel = "/workplace cancel " + site.id();
		text.append(Component.literal("\n  "));
		text.append(Component.translatable("message.aliveworkplace.quarry.cancel").withStyle(style -> style
			.withColor(ChatFormatting.RED).withUnderlined(true)
			.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cancel))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.aliveworkplace.quarry.cancel_hover")))));
		return text;
	}

	public static void sendStatus(Player player, Villager villager) {
		ServerLevel level = (ServerLevel) villager.level();
		QuarrySite site = activeSite(level, villager);
		if (site == null) {
			tell(player, Component.translatable(Builders.benchPos(villager).isPresent()
				? "message.aliveworkplace.quarry.idle" : "message.aliveworkplace.quarry.no_bench_status", villager.getDisplayName()), ChatFormatting.GRAY);
			tell(player, Component.literal("  ").append(BuilderLevels.describe(villager)), ChatFormatting.DARK_AQUA);
			return;
		}
		Chat.system(player, statusText(level, site, villager));
	}

	static void notifyNeedsPickaxe(ServerLevel level, Villager villager, QuarrySite site) {
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner());
		if (owner != null && site.shouldNotify(level.getGameTime(), 1200)) {
			tell(owner, Component.translatable("message.aliveworkplace.quarry.needs_pickaxe", villager.getDisplayName(), SupplyContainers.RADIUS),
				ChatFormatting.YELLOW);
		}
	}

	static void notifyNeedsLadders(ServerLevel level, Villager villager, QuarrySite site) {
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner());
		if (owner != null && site.shouldNotify(level.getGameTime(), 1200)) {
			tell(owner, Component.translatable("message.aliveworkplace.quarry.needs_ladders", villager.getDisplayName(), SupplyContainers.RADIUS),
				ChatFormatting.YELLOW);
		}
	}

	/** Done: tools, finds and the marker go back to the chests, the owner hears how it went. */
	static void finish(ServerLevel level, Villager villager, QuarrySite site) {
		BlockPos bench = site.bench() != null ? site.bench() : Builders.benchPos(villager).orElse(villager.blockPosition());
		List<BlockPos> supplies = SupplyContainers.find(level, bench, null);
		returnEverything(level, villager, bench, supplies);
		store(level, supplies, bench, markerFor(site));
		io.github.jcondedata.aliveworkplace.work.Furnaces.tend(level, bench, supplies, io.github.jcondedata.aliveworkplace.work.Furnaces::isOre, villager);
		level.playSound(null, villager, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner());
		if (owner != null) {
			tell(owner, Component.translatable("message.aliveworkplace.quarry.finished", villager.getDisplayName(), site.mined(), site.skipped()),
				ChatFormatting.GREEN);
		}
		BuilderLevels.addXp(level, villager, 10, site.owner());
		end(level, villager, site);
	}

	/** Stops a quarry: tools, finds and the marker go back; dug holes stay dug. */
	public static void cancel(ServerLevel level, QuarrySite site) {
		cancel(level, site, markerFor(site));
	}

	private static void cancel(ServerLevel level, QuarrySite site, ItemStack marker) {
		Villager villager = site.miner() != null && level.getEntity(site.miner()) instanceof Villager v ? v : null;
		BlockPos bench = site.bench() != null ? site.bench() : site.box().getCenter();
		List<BlockPos> supplies = SupplyContainers.find(level, bench, null);
		if (villager != null) {
			returnEverything(level, villager, bench, supplies);
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner());
		if (owner == null || !owner.getInventory().add(marker)) {
			store(level, supplies, bench, marker);
		}
		if (villager != null) {
			end(level, villager, site);
		} else {
			QuarrySiteManager.get(level).remove(site.id());
		}
	}

	public static void onMinerDeath(ServerLevel level, Villager villager) {
		QuarrySite site = activeSite(level, villager);
		if (site != null) {
			BlockPos at = villager.blockPosition();
			store(level, List.of(), at, markerFor(site));
			QuarrySiteManager.get(level).remove(site.id());
		}
	}

	private static void end(ServerLevel level, Villager villager, QuarrySite site) {
		QuarrySiteManager.get(level).remove(site.id());
		ModAttachments.MINER_JOB.remove(villager);
	}

	static void returnEverything(ServerLevel level, Villager villager, BlockPos bench, List<BlockPos> supplies) {
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		for (ItemStack stack : bag.takeAll()) {
			store(level, supplies, bench, stack);
		}
		ItemStack tool = villager.getItemBySlot(EquipmentSlot.MAINHAND);
		if (!tool.isEmpty()) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			store(level, supplies, bench, tool);
		}
	}

	static void store(ServerLevel level, List<BlockPos> supplies, BlockPos near, ItemStack stack) {
		ItemStack rest = SupplyContainers.insert(level, supplies, stack);
		if (!rest.isEmpty()) {
			net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level,
				near.getX() + 0.5, near.getY() + 1.1, near.getZ() + 0.5, rest);
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
	}

	private static ItemStack markerFor(QuarrySite site) {
		ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
		BoundingBox box = site.box();
		// A strip mine down a shaft: the corners on the ground again, and the height it was dug at.
		int top = site.hasShaft() ? site.shaftTop() : box.maxY();
		marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(site.dimension()), Optional.of(new BlockPos(box.minX(), top, box.minZ())),
			Optional.of(new BlockPos(box.maxX(), top, box.maxZ())), site.depth(), site.hasShaft() ? Optional.of(box.minY()) : Optional.empty()));
		return marker;
	}

	/** Makes {@code villager} a miner at {@code bench} right away (tests and admin tools). */
	public static void employ(ServerLevel level, Villager villager, BlockPos bench) {
		level.getPoiManager().take(h -> true, (h, p) -> p.equals(bench), bench, 1); // a blast furnace, or an old Miner's Bench
		villager.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE, net.minecraft.core.GlobalPos.of(level.dimension(), bench));
		io.github.jcondedata.aliveworkplace.work.WorkerLimits.order(villager, ModVillagers.MINER);
		if (villager.getVillagerXp() == 0) {
			villager.setVillagerXp(1);
		}
		villager.refreshBrain(level);
	}

	public static boolean isOwnerOrOp(Entity entity, QuarrySite site) {
		if (entity.getUUID().equals(site.owner())) {
			return true;
		}
		return entity instanceof ServerPlayer p && (p.hasPermissions(2) || Friends.get(p.level().getServer()).mayDirect(site.owner(), p.getUUID())
			|| site.miner() != null && Players.level(p).getEntity(site.miner()) instanceof Villager miner
			&& ModAttachments.BUILDER_EMPLOYER.get(miner) != null && Friends.mayCommand(p, miner));
	}

	static void tell(Player player, Component message, ChatFormatting color) {
		Chat.system(player, message.copy().withStyle(color));
	}

	private Miners() {
	}
}
