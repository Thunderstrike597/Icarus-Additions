package net.kenji.icarus_additions.compat.epicfight;

import yesman.epicfight.api.animation.LivingMotion;

public enum IcarusAdditionsLivingMotions implements LivingMotion {
    AIR_BREAK,
    UPDRAFT_CHARGED,
    UPDRAFT,
    ZEPHYR_FORWARD,
    ZEPHYR_BACKWARD,
    ZEPHYR_DOWN;

    final int id;

    IcarusAdditionsLivingMotions(){
        this.id = LivingMotion.ENUM_MANAGER.assign(this);
    }

    @Override
    public int universalOrdinal() {
        return id;
    }
}
