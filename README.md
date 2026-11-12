# Alloy Forgery: Unofficial NeoForge 1.21.1 Port

Unofficial port of **[Alloy Forgery](https://github.com/wisp-forest/alloy-forgery)** (version 2.4.1, Fabric 1.21) to **NeoForge 1.21.1**. Configurable, data-driven multiblock alloy forges.

The original authors publish their own NeoForge builds only for 1.21.4 and newer. This port exists mainly so that the [Mythic Metals NeoForge port](https://github.com/sanufsoiii/mythicmetals-neoforge) can run on 1.21.1, which needs Alloy Forgery 2.4.x.

## Status: early alpha

The port builds and the client starts to the main menu without errors. **Gameplay has not been tested in a world yet**: expect bugs, and please report them (see below). Dedicated server has not been tested either.

## Requirements

- Minecraft 1.21.1, NeoForge 21.1.200 or newer
- [owo-lib](https://modrinth.com/mod/owo-lib) for NeoForge

Textures and models are included: the original repository is MIT-licensed as a whole.

## Building

Requires JDK 21.

To make this port available to the Mythic Metals port build, publish it to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

```bash
./gradlew build
```

The mod jar is in `build/libs/`.

## Reporting bugs

This is an **unofficial** port. Please **do not** report its bugs to glisco, Noaaan, LordDeatHunter and Blodhgarm (Wisp Forest) or on the original issue tracker. Use this repository instead: https://github.com/sanufsoiii/alloy-forgery-neoforge/issues

## License and credits

- Alloy Forgery was created by **glisco, Noaaan, LordDeatHunter and Blodhgarm (Wisp Forest)**: https://github.com/wisp-forest/alloy-forgery. All gameplay design, art, sounds and names belong to them.
- This port is not affiliated with or endorsed by the original author.
- Code: **MIT**, same as the original code (see `LICENSE`).
- Folder `_excluded/` keeps original Fabric-only code (data generators, integrations with mods that have no NeoForge 1.21.1 version) for reference; it is not compiled.
