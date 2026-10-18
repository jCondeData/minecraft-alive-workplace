# libs/

- `create-fabric-6.0.10.0+mc1.21.1.jar`: the owner's Fabric 1.21.1 port of Create (mod id `create`, version
  6.0.10.0+mc1.21.1), added 2026-10-17 for Milestone 39 (Create integration, 2.3). Create is by the Creators of Create
  and its Fabric port by the Fabricators of Create, under the MIT license. The jar bundles its own Porting Lib modules,
  Registrate, Ponder, Flywheel, Milk Lib and Forge Config API Port. Use it only as an optional compat dependency
  (`modCompileOnly` plus the compat test runtime), never a hard `depends` (CLAUDE.md, Layers).
