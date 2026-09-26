package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/** Builder job lifecycle: hand over a blueprint, report status, finish, cancel. */
public final class Builders {
	/** A build site's centre must be within this many blocks of the builder's bench. */
	public static final int MAX_SITE_DISTANCE = 48;

	public static boolean isBuilder(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.BUILDER;
	}

	public static Optional<BlockPos> benchPos(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(g -> g.dimension() == villager.level().dimension())
			.map(GlobalPos::pos);
	}

	@Nullable
	public static BuildSite activeSite(ServerLevel level, Villager villager) {
		BuilderJob job = villager.getAttached(ModAttachments.BUILDER_JOB);
		if (job == null) {
			return null;
		}
		BuildSite site = BuildSiteManager.get(level).get(job.siteId());
		if (site == null) {
			villager.removeAttached(ModAttachments.BUILDER_JOB);
		}
		return site;
	}

	// --- hand-over ---------------------------------------------------------------------------

	/** Player right-clicked a builder while holding a blueprint. */
	public static InteractionResult assign(ServerPlayer player, Villager villager, ItemStack stack) {
		ServerLevel level = player.serverLevel();
		Optional<BlueprintData> data = BlueprintItem.data(stack);
		if (data.isEmpty()) {
			return InteractionResult.PASS;
		}
		Optional<BlueprintData.Placement> placement = data.get().placement();
		if (placement.isEmpty()) {
			tell(player, Component.translatable("message.aliveworkplace.assign.not_placed"), ChatFormatting.YELLOW);
			return InteractionResult.CONSUME;
		}
		if (!placement.get().dimension().equals(level.dimension().location())) {
			tell(player, Component.translatable("message.aliveworkplace.assign.wrong_dimension"), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		BuildSite existing = activeSite(level, villager);
		if (existing != null) {
			tell(player, Component.translatable("message.aliveworkplace.assign.busy", villager.getDisplayName(),
				Blueprints.displayName(existing.structure())), ChatFormatting.YELLOW);
			return InteractionResult.CONSUME;
		}
		Optional<BlockPos> bench = benchPos(villager);
		if (bench.isEmpty()) {
			tell(player, Component.translatable("message.aliveworkplace.assign.no_bench"), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		Optional<Blueprint> blueprint = BlueprintLibrary.get(level, data.get().structure());
		if (blueprint.isEmpty()) {
			tell(player, Component.translatable("message.aliveworkplace.blueprint.unknown", data.get().structure().toString()), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		BoundingBox box = BlueprintOutline.bounds(placement.get(), blueprint.get().size());
		double distance = Math.sqrt(box.getCenter().distSqr(bench.get()));
		if (distance > MAX_SITE_DISTANCE) {
			tell(player, Component.translatable("message.aliveworkplace.assign.too_far", (int) distance, MAX_SITE_DISTANCE), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite other : manager.all()) {
			if (BlueprintLibrary.get(level, other.structure())
				.map(b -> BlueprintOutline.bounds(other.placement(), b.size()).intersects(box)).orElse(false)) {
				tell(player, Component.translatable("message.aliveworkplace.assign.overlaps", Blueprints.displayName(other.structure())), ChatFormatting.RED);
				return InteractionResult.CONSUME;
			}
		}

		BuildSite site = start(level, villager, player, data.get().structure(), placement.get());
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		level.playSound(null, villager, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		tell(player, Component.translatable("message.aliveworkplace.assign.started", villager.getDisplayName(),
			Blueprints.displayName(site.structure()), SupplyContainers.RADIUS), ChatFormatting.GREEN);
		return InteractionResult.SUCCESS;
	}

	/** Creates the site and gives the villager the job. Also used by tests and commands. */
	public static BuildSite start(ServerLevel level, Villager villager, @Nullable Player owner,
								  net.minecraft.resources.ResourceLocation structure, BlueprintData.Placement placement) {
		BuildSite site = BuildSiteManager.get(level).create(
			owner != null ? owner.getUUID() : villager.getUUID(),
			owner != null ? owner.getGameProfile().getName() : "",
			structure, placement);
		site.setBuilder(villager.getUUID());
		villager.setAttached(ModAttachments.BUILDER_JOB, new BuilderJob(site.id()));
		return site;
	}

	// --- status ------------------------------------------------------------------------------

	public static void sendStatus(Player player, Villager villager) {
		ServerLevel level = (ServerLevel) villager.level();
		BuildSite site = activeSite(level, villager);
		if (site == null) {
			tell(player, Component.translatable(benchPos(villager).isPresent()
				? "message.aliveworkplace.status.idle" : "message.aliveworkplace.status.no_bench", villager.getDisplayName()), ChatFormatting.GRAY);
			tell(player, Component.literal("  ").append(BuilderLevels.describe(villager)), ChatFormatting.DARK_AQUA);
			return;
		}
		player.sendSystemMessage(statusText(level, site, villager));
	}

	public static Component statusText(ServerLevel level, BuildSite site, @Nullable Villager villager) {
		BuildPlan plan = site.plan(level);
		MutableComponent text = Component.empty();
		Component who = villager != null ? villager.getDisplayName() : Component.translatable("message.aliveworkplace.status.builder");
		if (plan == null) {
			return text.append(Component.translatable("message.aliveworkplace.status.blueprint_missing", site.structure().toString()).withStyle(ChatFormatting.RED));
		}
		int percent = Math.round(site.progress(plan) * 100);
		text.append(Component.translatable("message.aliveworkplace.status.header", who, Blueprints.displayName(site.structure()), percent)
			.withStyle(ChatFormatting.GOLD));
		text.append(Component.literal("\n  "));
		text.append(Component.translatable("message.aliveworkplace.status.stage." + site.stage().name().toLowerCase()).withStyle(ChatFormatting.GRAY));
		text.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
		text.append(Component.translatable("message.aliveworkplace.status.state." + site.status().name().toLowerCase()).withStyle(
			site.status() == BuildSite.Status.WAITING_FOR_MATERIALS ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
		if (villager != null) {
			text.append(Component.literal("\n  "));
			text.append(BuilderLevels.describe(villager).copy().withStyle(ChatFormatting.DARK_AQUA));
		}

		if (site.detail() != null) {
			text.append(Component.literal("\n  "));
			text.append(site.detail().copy().withStyle(ChatFormatting.YELLOW));
		}
		if (villager != null && benchPos(villager).isPresent()) {
			List<BlockPos> supplies = SupplyContainers.find(level, benchPos(villager).get(), plan.bounds());
			BuilderBag bag = villager.getAttachedOrCreate(ModAttachments.BUILDER_BAG);
			site.setMissing(computeMissing(level, site, plan, bag, supplies));
		}
		if (!site.missing().isEmpty()) {
			text.append(Component.literal("\n  "));
			text.append(Component.translatable("message.aliveworkplace.status.needs", formatMissing(site, 6)).withStyle(ChatFormatting.YELLOW));
		}
		if (site.skipped() > 0) {
			text.append(Component.literal("\n  "));
			text.append(Component.translatable("message.aliveworkplace.status.skipped", site.skipped()).withStyle(ChatFormatting.DARK_GRAY));
		}
		String cancel = "/workplace cancel " + site.id();
		text.append(Component.literal("\n  "));
		text.append(Component.translatable("message.aliveworkplace.status.cancel").withStyle(style -> style
			.withColor(ChatFormatting.RED).withUnderlined(true)
			.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cancel))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.aliveworkplace.status.cancel_hover")))));
		return text;
	}

	/** Materials still needed for the rest of the build, minus what the builder carries and what is in the supply chests. */
	public static Map<Item, Integer> computeMissing(ServerLevel level, BuildSite site, BuildPlan plan, BuilderBag bag, List<BlockPos> supplies) {
		Map<Item, Integer> need = new LinkedHashMap<>();
		for (BuildPlan.Step step : site.upcoming(plan, Integer.MAX_VALUE)) {
			if (!MaterialRules.matches(level.getBlockState(step.pos()), step.state())) {
				MaterialRules.requirement(step.state()).ifPresent(r -> need.merge(r.item(), r.count(), Integer::sum));
			}
		}
		Map<Item, Integer> missing = new LinkedHashMap<>();
		for (Map.Entry<Item, Integer> e : need.entrySet()) {
			long have = bag.count(e.getKey()) + SupplyContainers.count(level, supplies, e.getKey());
			long shortBy = e.getValue() - have;
			if (shortBy > 0) {
				missing.put(e.getKey(), (int) shortBy);
			}
		}
		return missing;
	}

	public static Component formatMissing(BuildSite site, int max) {
		MutableComponent out = Component.empty();
		List<Map.Entry<Item, Integer>> list = site.missingSorted();
		for (int i = 0; i < Math.min(max, list.size()); i++) {
			if (i > 0) {
				out.append(", ");
			}
			out.append(Component.literal(list.get(i).getValue() + "× ")).append(list.get(i).getKey().getDescription());
		}
		if (list.size() > max) {
			out.append(Component.translatable("message.aliveworkplace.status.and_more", list.size() - max));
		}
		return out;
	}

	static void notifyWaiting(ServerLevel level, Villager villager, BuildSite site) {
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner());
		if (owner != null && !site.missing().isEmpty()) {
			tell(owner, Component.translatable("message.aliveworkplace.waiting", villager.getDisplayName(),
				Blueprints.displayName(site.structure()), formatMissing(site, 5)), ChatFormatting.YELLOW);
		}
	}

	// --- finishing / cancelling --------------------------------------------------------------

	static void finish(ServerLevel level, Villager villager, BuildSite site) {
		BlockPos bench = benchPos(villager).orElse(villager.blockPosition());
		List<BlockPos> supplies = SupplyContainers.find(level, bench, null);
		emptyBag(level, villager, bench, supplies);
		returnBlueprint(level, site, bench, supplies);

		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.8, villager.getZ(), 12, 0.5, 0.5, 0.5, 0.0);
		level.playSound(null, villager, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner());
		if (owner != null) {
			tell(owner, Component.translatable("message.aliveworkplace.finished", villager.getDisplayName(), Blueprints.displayName(site.structure())), ChatFormatting.GREEN);
		}
		endJob(level, villager, site);
		BuilderLevels.onFinished(level, villager, site);
	}

	/** Stops a build. Placed blocks stay; the blueprint goes back to the owner (or the bench). */
	public static void cancel(ServerLevel level, BuildSite site) {
		Villager villager = site.builder() != null && level.getEntity(site.builder()) instanceof Villager v ? v : null;
		BlockPos bench = villager != null ? benchPos(villager).orElse(villager.blockPosition()) : site.placement().origin();
		List<BlockPos> supplies = villager != null ? SupplyContainers.find(level, bench, null) : List.of();
		if (villager != null) {
			emptyBag(level, villager, bench, supplies);
		}
		ItemStack blueprint = blueprintFor(level, site);
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner());
		if (owner != null && owner.getInventory().add(blueprint)) {
			// given straight back
		} else {
			ItemStack rest = SupplyContainers.insert(level, supplies, blueprint);
			if (!rest.isEmpty()) {
				dropNear(level, bench, rest);
			}
		}
		if (villager != null) {
			endJob(level, villager, site);
		} else {
			BuildSiteManager.get(level).remove(site.id());
		}
	}

	/** The builder died: drop its bag and the blueprint where it fell, keep what was built. */
	public static void onBuilderDeath(ServerLevel level, Villager villager) {
		BuildSite site = activeSite(level, villager);
		BuilderBag bag = villager.getAttached(ModAttachments.BUILDER_BAG);
		if (bag != null) {
			for (ItemStack stack : bag.takeAll()) {
				dropNear(level, villager.blockPosition(), stack);
			}
		}
		if (site != null) {
			dropNear(level, villager.blockPosition(), blueprintFor(level, site));
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner());
			if (owner != null) {
				BlockPos p = villager.blockPosition();
				tell(owner, Component.translatable("message.aliveworkplace.builder_died", Blueprints.displayName(site.structure()), p.getX(), p.getY(), p.getZ()), ChatFormatting.RED);
			}
			BuildSiteManager.get(level).remove(site.id());
		}
	}

	private static void endJob(ServerLevel level, Villager villager, BuildSite site) {
		BuildSiteManager.get(level).remove(site.id());
		villager.removeAttached(ModAttachments.BUILDER_JOB);
		villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	private static void emptyBag(ServerLevel level, Villager villager, BlockPos bench, List<BlockPos> supplies) {
		BuilderBag bag = villager.getAttachedOrCreate(ModAttachments.BUILDER_BAG);
		for (ItemStack stack : bag.takeAll()) {
			ItemStack rest = SupplyContainers.insert(level, supplies, stack);
			if (!rest.isEmpty()) {
				dropNear(level, bench, rest);
			}
		}
	}

	private static void returnBlueprint(ServerLevel level, BuildSite site, BlockPos bench, List<BlockPos> supplies) {
		ItemStack rest = SupplyContainers.insert(level, supplies, blueprintFor(level, site));
		if (!rest.isEmpty()) {
			dropNear(level, bench, rest);
		}
	}

	private static ItemStack blueprintFor(ServerLevel level, BuildSite site) {
		return BlueprintItem.create(site.structure(), BlueprintLibrary.get(level, site.structure()).map(Blueprint::size).orElse(null));
	}

	static void dropNear(ServerLevel level, BlockPos pos, ItemStack stack) {
		ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, stack);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}

	static void tell(Player player, Component message, ChatFormatting color) {
		player.sendSystemMessage(message.copy().withStyle(color));
	}

	/**
	 * Makes {@code villager} a builder working at {@code bench} right away (the bench block must already be
	 * there). Normal play does this through vanilla job-site claiming; tests and admin commands use this.
	 */
	public static void employ(ServerLevel level, Villager villager, BlockPos bench) {
		level.getPoiManager().take(h -> h.is(ModVillagers.BUILDERS_BENCH_POI), (h, p) -> p.equals(bench), bench, 1);
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), bench));
		villager.setVillagerData(villager.getVillagerData().setProfession(ModVillagers.BUILDER));
		if (villager.getVillagerXp() == 0) {
			villager.setVillagerXp(1); // keeps the profession even if the bench is briefly missing
		}
		villager.refreshBrain(level);
	}

	public static boolean isOwnerOrOp(Entity entity, BuildSite site) {
		return entity.getUUID().equals(site.owner()) || entity instanceof ServerPlayer p && p.hasPermissions(2);
	}

	private Builders() {
	}
}
