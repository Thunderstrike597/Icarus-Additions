package net.kenji.icarus_additions.compat.epicfight;

import yesman.epicfight.api.animation.LivingMotion;

public enum IcarusAdditionsLivingMotions implements LivingMotion {
    AIR_BREAK;

    final int id;

    IcarusAdditionsLivingMotions(){
        this.id = LivingMotion.ENUM_MANAGER.assign(this);
    }

    @Override
    public int universalOrdinal() {
        return id;
    }
}
