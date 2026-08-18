package com.enaboapps.switchify.remotebridge;
import android.os.Bundle;
oneway interface ISwitchifyRemoteBridgeCallback {
    void onSnapshot(in Bundle snapshot);
    void onRepeatStopRequested(long generation);
    void onSwitchEdge(long generation, long sequence, int keyCode, boolean down, long downTimeMs, long eventTimeMs, boolean cancelled);
}
