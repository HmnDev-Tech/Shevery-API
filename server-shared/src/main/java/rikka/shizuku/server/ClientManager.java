package rikka.shizuku.server;

import android.os.IBinder;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import moe.shizuku.server.IShizukuApplication;
import rikka.hidden.compat.PackageManagerApis;
import rikka.shizuku.server.util.Logger;

public class ClientManager<ConfigMgr extends ConfigManager> {
    protected static final Logger LOGGER = new Logger("ClientManager");

    private final ConfigMgr configManager;
    private final List<ClientRecord> clientRecords = Collections.synchronizedList(new ArrayList<>());

    public ClientManager(ConfigMgr configManager) {
        this.configManager = configManager;
    }

    public ConfigMgr getConfigManager() {
        return configManager;
    }

    public List<ClientRecord> findClients(int uid) {
        synchronized (clientRecords) {
            List<ClientRecord> res = new ArrayList<>();
            for (ClientRecord clientRecord : clientRecords) {
                if (clientRecord.uid == uid) {
                    res.add(clientRecord);
                }
            }
            return res;
        }
    }

    public ClientRecord findClient(int uid, int pid) {
        synchronized (clientRecords) {
            for (ClientRecord clientRecord : clientRecords) {
                if (clientRecord.pid == pid && clientRecord.uid == uid) {
                    return clientRecord;
                }
            }
            return null;
        }
    }

    public ClientRecord requireClient(int callingUid, int callingPid) {
        return requireClient(callingUid, callingPid, false);
    }

    public ClientRecord requireClient(int callingUid, int callingPid, boolean requiresPermission) {
        ClientRecord clientRecord = findClient(callingUid, callingPid);
        if (clientRecord == null) {
            LOGGER.w("Caller (uid %d, pid %d) is not an attached client", callingUid, callingPid);
            throw new IllegalStateException("Not an attached client");
        }
        if (requiresPermission && !clientRecord.allowed) {
            throw new SecurityException("Caller has no permission");
        }
        return clientRecord;
    }

    public static void enforcePackageOwnership(int callingUid, String packageName, Collection<String> ownedPackages) {
        if (packageName == null || packageName.isEmpty()) {
            throw new SecurityException("Request package must not be empty for uid " + callingUid);
        }
        if (ownedPackages == null || !ownedPackages.contains(packageName)) {
            throw new SecurityException("Request package " + packageName + " does not belong to uid " + callingUid);
        }
    }

    public ClientRecord addClient(int uid, int pid, IShizukuApplication client, String packageName, int apiVersion) {
        if (client == null || packageName == null || packageName.isEmpty()) {
            return null;
        }
        try {
            List<String> packages = PackageManagerApis.getPackagesForUidNoThrow(uid);
            if (packages != null && !packages.isEmpty()) {
                enforcePackageOwnership(uid, packageName, packages);
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Throwable ignored) {
        }
        ClientRecord clientRecord = new ClientRecord(uid, pid, client, packageName, apiVersion);

        // Decide permission & capabilities before publishing record (adapted from Porter API & Nightzuku API)
        ConfigPackageEntry entry = configManager.find(uid);
        if (entry != null && entry.isAllowed()) {
            clientRecord.allowed = true;
            clientRecord.capabilities = entry.getCapabilities();
        }

        IBinder binder = client.asBinder();
        IBinder.DeathRecipient deathRecipient = () -> {
            clientRecords.remove(clientRecord);
            onClientDied(clientRecord);
        };
        try {
            binder.linkToDeath(deathRecipient, 0);
        } catch (RemoteException e) {
            LOGGER.w(e, "addClient: linkToDeath failed");
            return null;
        }

        clientRecords.add(clientRecord);
        return clientRecord;
    }

    /**
     * Hook invoked on a binder thread after a client's process dies and its record is removed.
     */
    protected void onClientDied(ClientRecord record) {
    }
}
