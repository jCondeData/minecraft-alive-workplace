// Outdoor lights (parts library, tools/blueprints/REBUILD.md). The cellar itself is frames.mjs's `cellar`.

/** lampPost: a lantern standing on a post (`post`, a fence id) `height` tall, on the ground at y. */
export function lampPost(c, x, y, z, post, height = 1) {
  for (let i = 0; i < height; i++) c.set(x, y + i, z, post)
  c.set(x, y + height, z, 'lantern', { hanging: 'false', waterlogged: 'false' })
}
