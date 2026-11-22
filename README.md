# Copycat Light Control

适用于 Minecraft 1.21.1 / NeoForge、Create 6.0.x 和 Copycats+ 3.0.9。不透明填充会遮光，普通玻璃可透光。也修复了内缩面的光照，并支持 Sable 2.0.6 物理车厢内外分开采光。

把本模组放入 `mods/`。多人游戏在客户端和服务器都安装。已有方块无需重新放置。

构建需要 Java 21。将 Copycats+、Create、Flywheel、Ponder、Registrate 放入 `libs/`，Sable、Sable Companion、Sable Rapier 放入 `optional-libs/`，Sodium 0.8.13 的内部 JAR、Fabric Renderer API 与 Fabric API Base 放入 `compile-libs/`。运行 `./gradlew build`（Windows：`.\gradlew.bat build`），产物在 `build/libs/`。
