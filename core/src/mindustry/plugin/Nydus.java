package mindustry.plugin;

import arc.func.*;

import static mindustry.Vars.*;

public enum Nydus{

    reactor_terrain_clearing(() -> true, "#thorium-reactor explosions destroy terrain"),
    driver_terrain_clearing(() -> true, "#mass-driver bullets tear through terrain"),

    incinerator_makes_pyratite(() -> true, "#incinerator has a chance to make #pyratite"),
    unpowered_mechpad_tarring(() -> true, "#dart-mech-pad gives debuff when lacking power"),

    separator_free_slag(() -> true, "#separator contains free #slag"),
    separator_sedimentation(() -> true, "#separator turns items into #titanium when not rotating"),

    airblast_bonus_output(() -> true, "#blast-drill gives 5 bonus items every 10 normal items"),
    landfill_on_drowning(() -> true, "#water drowning when holding items creates land tiles"),
    enemy_item_drops(() -> true, "#core-shard enemies that die near your core drop what they carry"),
    pulverizer_on_ore(() -> true, "#pulverizer placed on ores contain infinite scrap"),

    block_merging(() -> true, "#scrap-wall blocks merge into larger blocks #scrap-wall-large"),
    block_downgrading(() -> true, "#titanium-conveyor blocks downgrade when destroyed #conveyor"),
    launchpad_upgrading(() -> true, "#launchpad vaults next to the core transform to upgrade blocks"),

    cascading_doors(() -> true, "#door opening/closing doors impact neighboring ones"),
    rtg_generator_sharing(() -> true, "#rtg-generator shares its #thorium fuel with its friends"),
    the_floor_is_lava(() -> true, "#magmarock sets fire to anything other than #thermal-generator"),
    single_use_batteries(() -> true, "#battery full when placed & explodes when empty"),

    free_phase_overdrive(() -> true, "#overdrive-projector contains free phase when surrounded by #phase-wall"),
    free_phase_forcefield(() -> true, "#force-projector contains free phase when surrounded by #phase-wall"),

    duo_router(() -> true, "#duo turns into #router when attempting to shoot"),
    firefighting_waves(() -> true, "#wave consumes no liquid to fight fires"),
    combustible_combustion(() -> true, "#conbustion-generator combusts when fed #coal"),
    dyson_sphere(() -> true, "#solar-panel gives more power when there are more of em"),
    repairpoint_wololoo(() -> true, "#repair-point switches your team on-demand"),
    portal_bridge_builder(() -> true, "#bridge-conveyor & #bridge-conduit cannot chain"),
    thanos_junction_snap(() -> true, "#junction gets removed when nothing crosses it"),

    secret(() -> true, "#null");

    private final Boolp active;

    public boolean active(){
        if(!net.server()) return false;
        return active.get();
    }

    Nydus(Boolp active, String description){
        this.active = active;
    }

}
