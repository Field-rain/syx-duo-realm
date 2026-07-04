package com.syxduorealm;

import com.syxduorealm.lifecycle.Phase;
import com.syxduorealm.lifecycle.PhaseManager;
import script.SCRIPT;
import util.info.INFO;

@SuppressWarnings("unused")
public final class SyxDuoRealmScript implements SCRIPT {

    private static final INFO INFO = new INFO(
        "Syx Duo Realm",
        "Exports local city state for a duo async macro realm."
    );

    @Override
    public CharSequence name() {
        return INFO.name;
    }

    @Override
    public CharSequence desc() {
        return INFO.desc;
    }

    @Override
    public void initBeforeGameCreated() {
        System.out.println("[Syx Duo Realm] initBeforeGameCreated");
    }

    @Override
    public SCRIPT_INSTANCE createInstance() {
        System.out.println("[Syx Duo Realm] createInstance");

        PhaseManager phaseManager = new PhaseManager();
        SyxDuoRealmRuntime runtime = new SyxDuoRealmRuntime();

        phaseManager
            .register(Phase.ON_GAME_SAVE_LOADED, runtime)
            .register(Phase.ON_GAME_SAVED, runtime)
            .register(Phase.INIT_GAME_UPDATING, runtime)
            .register(Phase.ON_GAME_UPDATE, runtime)
            .register(Phase.INIT_GAME_UI_PRESENT, runtime)
            .register(Phase.INIT_SETTLEMENT_UI_PRESENT, runtime);

        return new SyxDuoRealmInstance(phaseManager);
    }

    @Override
    public boolean forceInit() {
        return true;
    }
}
