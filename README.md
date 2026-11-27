# Classes-Reborn

An RPG classes, stats, leveling, and active skills mod for **Minecraft 1.21.1** built on **NeoForge** (`21.1.268`).

---

## Features

- **Class Selection:** New players receive a **Class Tome** (`classesreborn:class_book`) to choose one of 5 RPG classes, receiving a class starter kit and permanent class modifiers.
- **Leveling & Progression:** Killing mobs grants XP (`max(5, round(victimMaxHealth * 0.75))`). Each level grants **+1 Skill Point** and **+2 Stat Points**.
- **7 Allocatable Stats:** Customize your build via the Stats screen (**`V`**) and reset allocated points anytime.
- **Skill Tree & Active Slots:** Unlock and upgrade class skills (**`K`**), then equip up to **3 active skills** cast with **`G`**, **`R`**, and **`C`**.
- **Custom RPG HUD:** Displays your class, level, XP bar, and unspent skill points.
- **Persistence:** Class, level, XP, allocated stats, unlocked skills, and equipped active skill slots persist across death, respawn, dimension changes, and world saves.

---

## Classes & Skills

| Class | Role | Key Bonuses | Starter Kit | Skills |
| :--- | :--- | :--- | :--- | :--- |
| **Tank** | Frontline guardian | `+6.0` Max Health, `+6.0` Armor, `+2.0` Toughness, `1.65x` Aggro, `-0.015` Speed | Iron Sword, Shield, Iron Chestplate, 4x Cooked Beef | **Roar** (`tank_roar`) — Resistance, Absorption, & AoE mob taunt |
| **Swordsman** | Balanced melee duelist | `+2.0` Max Health, `+2.0` Armor, `+2.5` Attack Damage, `+0.01` Speed | Iron Sword, Chainmail Chestplate, Iron Boots, 4x Cooked Beef | **Quickslash** (`swordsman_quickslash`) — AoE sweep slash & melee damage surge |
| **Assassin** | Agile melee striker | `+3.0` Attack Damage, `+0.035` Speed, `0.55x` Aggro, `-2.0` Health/Armor | Stone Sword, Leather Chestplate, Leather Boots, 2x Ender Pearl | **Invisibility** (`assassin_invisibility`) — Stealth, Speed, & drops nearby mob aggro |
| **Archer** | Ranged specialist | `1.45x` Ranged Damage, `+0.02` Speed, `-1.0` Melee Damage | Bow, 32x Arrow, Leather Chestplate, Leather Helmet | **Dash** (`archer_dash`), **Night Vision** (`archer_nightvision`), **Leap** (`archer_leap`) |
| **Healer** | Support caster | `1.50x` Healing, `+1.0` Max Health, `-1.5` Melee Damage | Healer's Staff, Leather Chestplate, 4x Cooked Beef | **Heal** (`healer_heal`) — 1.0s aimed cast to heal a teammate |

---

## Allocatable Stats

Open the Stats screen with **`V`**:
- **Vitality** — Increases Max Health (`+1.0` per point)
- **Guard** — Increases Armor (`+0.75`) and Armor Toughness (`+0.25`)
- **Might** — Increases Melee Attack Damage (`+0.5` per point)
- **Precision** — Increases Ranged Damage Multiplier (`+3%` per point)
- **Agility** — Increases Movement Speed (`+0.004` per point)
- **Presence** — Increases Hostile Mob Aggro Multiplier (`+4%` per point)
- **Mend** — Increases Outgoing Healing Multiplier (`+3%` per point)

---

## Default Keybinds

| Key | Action |
| :--- | :--- |
| **`G`** | Cast Active Skill Slot 1 |
| **`R`** | Cast Active Skill Slot 2 |
| **`C`** | Cast Active Skill Slot 3 |
| **`K`** | Open Skill Tree Screen |
| **`V`** | Open Stats Screen |

---

## Commands

- `/rpg info` — Show your current class, level, XP, and skill points.
- `/rpg setclass <class>` *(OP)* — Set your class (`tank`, `swordsman`, `assassin`, `archer`, `healer`).
- `/rpg addxp <amount>` *(OP)* — Grant XP to yourself.
- `/rpg reset` *(OP)* — Reset your RPG data and receive a fresh Class Tome.

---

## Project Structure

The codebase separates class-specific data and skills into 5 independent folders under `src/main/java/com/tanishk/classesreborn/rpg/classes/` so multiple developers can work on different classes simultaneously without Git merge conflicts:

```text
src/main/java/com/tanishk/classesreborn/
├── attachment/          # NeoForge player data attachment (PlayerRpgData)
├── bridge/              # JNI bridge (RustBridge)
├── client/              # Keybinds, GUI screens (ClassSelection, StatScreen, SkillTree), HUD
├── command/             # /rpg commands
├── event/               # Player lifecycle, combat, and aggro event handlers
├── item/                # Class Tome & Healer's Staff
├── network/             # Client <-> Server payloads
└── rpg/
    ├── classes/         # Per-class modules (edit here per character!)
    │   ├── archer/      # ArcherClass.java, ArcherSkills.java
    │   ├── assassin/    # AssassinClass.java, AssassinSkills.java
    │   ├── healer/      # HealerClass.java, HealerSkills.java
    │   ├── swordsman/   # SwordsmanClass.java, SwordsmanSkills.java
    │   └── tank/        # TankClass.java, TankSkills.java
    ├── skill/           # Skill.java, SkillTier.java, SkillRegistry.java (central hub)
    ├── ClassStatManager.java
    ├── PlayerRpgData.java
    └── RpgClass.java
rust/                    # Optional native Rust cdylib crate (classesreborn_native)
```

---

## Building & Running

### Prerequisites
- **Java 21** (JDK 21)
- **Rust / Cargo** (required by `buildRust` in `build.gradle` to compile the `rust/` native library)

### Gradle Commands (Windows)
```powershell
# Launch Minecraft client in dev
.\gradlew.bat runClient

# Launch dedicated server in dev
.\gradlew.bat runServer

# Build the mod jar (outputs to build/libs/)
.\gradlew.bat build
```
