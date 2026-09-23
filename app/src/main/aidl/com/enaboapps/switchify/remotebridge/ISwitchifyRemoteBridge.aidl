package com.enaboapps.switchify.remotebridge;
import android.os.Bundle;
import com.enaboapps.switchify.remotebridge.ISwitchifyRemoteBridgeCallback;
interface ISwitchifyRemoteBridge {
    int getVersion();
    Bundle getSnapshot();
    void registerCallback(ISwitchifyRemoteBridgeCallback callback);
    void unregisterCallback(ISwitchifyRemoteBridgeCallback callback);
    boolean setRepeatActive(long generation, boolean active);
    boolean setForwardingActive(long generation, boolean active);
}
