package com.tanishk.classesreborn.rpg.skill;

public enum SkillTier {
    COMMON("Common", 0xFFB0B0B0, 1.00),
    UNCOMMON("Uncommon", 0xFF55FF55, 1.15),
    RARE("Rare", 0xFF55AAFF, 1.32),
    EPIC("Epic", 0xFFCC66FF, 1.52),
    LEGENDARY("Legendary", 0xFFFFAA00, 1.75),
    MYTHIC("Mythic", 0xFFFF4466, 2.00);

    private final String displayName;
    private final int color;
    private final double powerMultiplier;

    SkillTier(String displayName, int color, double powerMultiplier) {
        this.displayName = displayName;
        this.color = color;
        this.powerMultiplier = powerMultiplier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getColor() {
        return color;
    }

    public double getPowerMultiplier() {
        return powerMultiplier;
    }

    /** Every 5 skill levels advances 1 tier (Lv 1-5 Common, 6-10 Uncommon, 11-15 Rare, 16-20 Epic, 21-25 Legendary, 26+ Mythic). */
    public static SkillTier fromSkillLevel(int skillLevel) {
        int clamped = Math.max(1, skillLevel);
        int tierIndex = Math.min(values().length - 1, (clamped - 1) / 5);
        return values()[tierIndex];
    }

    public int tierIndex() {
        return ordinal();
    }

    public boolean isAboveRare() {
        return ordinal() > RARE.ordinal();
    }
}