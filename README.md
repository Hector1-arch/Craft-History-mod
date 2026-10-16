# Craft History

A small, **client-side** [Fabric](https://fabricmc.net/) mod for Minecraft **26.3** that adds a
**Craft History** section to the vanilla recipe book: the three most recent things you crafted, ready
to place again with a single click.

<p align="center">
  <img src="src/main/resources/assets/crafthistory/icon.png" width="128" height="128" alt="Craft History icon">
</p>

## Features

- **Craft History section** directly above the recipe book panel.
  - Records up to three most recently crafted item types, newest first.
  - Crafting the same thing again moves it back to the front instead of duplicating it.
  - Works for both the 2x2 inventory grid and the 3x3 crafting table.
  - **Left-click** an entry to place that recipe (fills the crafting grid) — it never auto-crafts.
- **Shift-click to place the maximum.** Clicking an entry while holding **Shift** places the recipe
  with as many items as the grid will hold (the same behaviour as shift-clicking a recipe in the
  vanilla recipe book).
- **Space to continue crafting.** After you have crafted something in the current session, press
  **Space** (or Enter) to refill the crafting grid with that recipe, so the same item can be crafted
  repeatedly without reopening the recipe book. This works even while the recipe book is hidden, and
  leaves the vanilla "repeat last placed recipe" behaviour untouched when nothing has been crafted
  yet.
- **Resource-pack aware visuals.** Both the **background** and the **outline** of the section are
  sampled from the active recipe book texture, so a dark GUI resource pack produces a dark section
  with a matching outline while the classic look is reproduced otherwise. The section reuses the
  vanilla font and vanilla item rendering.
- **Persistent.** The history is written to `config/crafthistory.json` and reloaded on every launch,
  so it survives restarts, world changes and switching servers.
- **Client-only and non-intrusive.** Nothing is sent to the server, no server-side installation is
  required, and recipe placement still goes through the normal vanilla mechanism, so the server
  stays authoritative.

## Requirements

| Component       | Version            |
| --------------- | ------------------ |
| Minecraft       | 26.3               |
| Fabric Loader   | 0.19.5 or newer    |
| Java            | 21 or newer        |

The mod is client-side only. It can be installed on a single-player world or used on any server
that supports a client-only mod; the server does not need it.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.5+ for Minecraft 26.3.
2. Drop `crafthistory-1.0.0.jar` into your `mods` folder.
3. Launch the game with the Fabric profile.

## Building from source

The project is a standard Fabric Loom project, but Minecraft 26.3 is shipped **unobfuscated**, so a
few build details differ from a typical Fabric mod:

- Minecraft 26.3 has no obfuscated names, so Fabric's `intermediary` mapping for it is an **empty
  identity mapping**. Loom is configured with `noIntermediateMappings()` plus a tiny identity
  mapping (`mappings/intermediary-to-named.tiny`).
- Because there is no obfuscation, Loom's **legacy Mixin annotation processor cannot resolve
  obfuscation mappings** and fails the build. The build therefore sets
  `loom { mixin { useLegacyMixinAp = false } }` and lets Loom handle mixin references at remap time
  (a no-op for identity mappings).
- There is no Gradle wrapper committed. Use a Gradle 9.x installation and point `JAVA_HOME` at a
  JDK 21+.

```powershell
# Example on Windows (adjust paths to your setup)
$env:JAVA_HOME = 'C:\Program Files\jdk-21'
gradle build
```

The compiled mod is written to `build/libs/crafthistory-1.0.0.jar`.

## How it works

- **Stable recipe keys.** Minecraft 26.3 does not send real recipe identifiers to the client — the
  recipe book only knows session-scoped `RecipeDisplayId` integers. To persist the history,
  `RecipeKeys` derives a content hash (SHA-1 of the JSON produced by serialising the
  `RecipeDisplay`), which is stable across sessions and worlds.
- **Craft detection.** A mixin on `MultiPlayerGameMode.handleContainerInput` notices when the result
  slot of a crafting menu (`InventoryMenu` 2x2 or `CraftingMenu` 3x3) is clicked and records the
  crafted item. Placing a recipe only fills the grid, so it never counts as a craft.
- **Rendering.** A mixin injects at the tail of `RecipeBookComponent.extractRenderState` and draws
  the section above the vanilla panel.
- **Adaptive palette.** `Theme` reads `textures/gui/recipe_book.png` from the resource manager, takes
  its dominant opaque colour as the panel background and its darkest common colour as the outline
  (black in vanilla), then derives the text and slot colours from the background's brightness. The
  result is cached and recomputed only when the selected resource packs change
  (`PackRepository.getSelectedIds()`), so pack switches are picked up automatically.
- **Input.** Clicks on the section are handled before the vanilla recipe book sees them (left-click
  places, Shift places the maximum). A `keyPressed` hook on `RecipeBookComponent` turns the selection
  key (Space/Enter) into a "refill with the last crafted recipe" action.
- **Persistence** lives in `CraftHistoryData` (Gson -> `config/crafthistory.json`).

## Known limitations

- On servers with custom/modded recipes, a key computed on one server may not match the same recipe
  on another server; in that case the mod falls back to matching by the produced item.
- A live in-game test has not been performed as part of building this project; the code has been
  verified against the Minecraft 26.3 sources and the project compiles cleanly (see below).

## Testing

Static verification performed:

- `gradle build` completes successfully and produces `build/libs/crafthistory-1.0.0.jar`.
- Every mixin target (`@Inject`, `@Shadow`, `@Accessor`) was checked against the decompiled
  Minecraft 26.3 sources.
- The generated JAR contains the compiled classes, `crafthistory.mixins.json`, `fabric.mod.json` and
  the language file.

Manual testing steps (to run in-game):

1. Open a crafting table and open the recipe book — the Craft History section should appear above it.
2. Craft something and confirm it appears in Craft History; craft it again and confirm it moves to
   the front rather than duplicating.
3. Click an entry and confirm the grid is filled (nothing is crafted until you take the result).
4. Shift-click an entry and confirm the grid is filled with the maximum number of items.
5. Craft something, then press Space and confirm the crafting grid is refilled with that recipe.
6. Switch to a dark GUI resource pack and confirm the section background and outline follow it.
7. Restart the game and confirm the history is still there.

## License

MIT — see [LICENSE](LICENSE).
