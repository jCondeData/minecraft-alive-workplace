package io.github.jcondedata.aliveworkplace.registry;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/** Extra data we store on vanilla villagers (saved with the entity). */
public final class ModAttachments {
	/** Which build site this villager is working on. */
	public static final AttachmentType<BuilderJob> BUILDER_JOB = AttachmentRegistry.create(
		AliveWorkplace.id("builder_job"), builder -> builder.persistent(BuilderJob.CODEC));

	/** The builder's tool bag: materials fetched from the supply chests, and rubble from clearing. */
	public static final AttachmentType<BuilderBag> BUILDER_BAG = AttachmentRegistry.create(
		AliveWorkplace.id("builder_bag"), builder -> builder.persistent(BuilderBag.CODEC).initializer(BuilderBag::new));

	/** Which quarry a miner is digging. */
	public static final AttachmentType<BuilderJob> MINER_JOB = AttachmentRegistry.create(
		AliveWorkplace.id("miner_job"), builder -> builder.persistent(BuilderJob.CODEC));

	/** How many trees a lumberjack has cut down (shown above its head). */
	public static final AttachmentType<Integer> TREES_FELLED = AttachmentRegistry.create(
		AliveWorkplace.id("trees_felled"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** The player a builder works for (see {@code Friends}). */
	public static final AttachmentType<io.github.jcondedata.aliveworkplace.build.Employer> BUILDER_EMPLOYER = AttachmentRegistry.create(
		AliveWorkplace.id("builder_employer"), builder -> builder.persistent(io.github.jcondedata.aliveworkplace.build.Employer.CODEC));

	/** The field a Farmer was given to look after. */
	public static final AttachmentType<io.github.jcondedata.aliveworkplace.farm.FieldJob> FARM_FIELD = AttachmentRegistry.create(
		AliveWorkplace.id("farm_field"), builder -> builder.persistent(io.github.jcondedata.aliveworkplace.farm.FieldJob.CODEC));

	/** How many crops a farmer has harvested on their field (shown above its head). */
	public static final AttachmentType<Integer> FARM_HARVESTED = AttachmentRegistry.create(
		AliveWorkplace.id("farm_harvested"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** True for a Fisherman a player handed a fishing rod: they fish for the chests near their barrel. */
	public static final AttachmentType<Boolean> FISHER_JOB = AttachmentRegistry.create(
		AliveWorkplace.id("fisher_job"), builder -> builder.persistent(com.mojang.serialization.Codec.BOOL));

	/** How many catches a fisherman has reeled in (shown above its head). */
	public static final AttachmentType<Integer> FISH_CAUGHT = AttachmentRegistry.create(
		AliveWorkplace.id("fish_caught"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	public static void init() {
	}

	private ModAttachments() {
	}
}
