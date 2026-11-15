package com.hamondev.shevery.api;

import org.junit.Test;

import rikka.shizuku.ShizukuProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SheveryProviderTest {

    @Test
    public void sheveryProviderAllowsRootShellAndSelfUid() {
        int myUid = 10420;
        assertTrue(SheveryProvider.isTrustedCallingUid(0, myUid));
        assertTrue(SheveryProvider.isTrustedCallingUid(2000, myUid));
        assertTrue(SheveryProvider.isTrustedCallingUid(myUid, myUid));

        SheveryProvider.enforceCallingUid(0, myUid);
        SheveryProvider.enforceCallingUid(2000, myUid);
        SheveryProvider.enforceCallingUid(myUid, myUid);
    }

    @Test
    public void sheveryProviderRejectsArbitraryAppUids() {
        int myUid = 10420;
        int[] untrusted = {1000, 10234, 10421, 10010420, -1};
        for (int uid : untrusted) {
            assertFalse(SheveryProvider.isTrustedCallingUid(uid, myUid));
            try {
                SheveryProvider.enforceCallingUid(uid, myUid);
                fail("Expected SecurityException for uid=" + uid);
            } catch (SecurityException e) {
                assertEquals("Unauthorized binder delivery from uid=" + uid, e.getMessage());
            }
        }
    }

    @Test
    public void shizukuProviderEnforcesIdenticalTrustedCallingUidPolicy() {
        int myUid = 10555;
        assertTrue(ShizukuProvider.isTrustedCallingUid(0, myUid));
        assertTrue(ShizukuProvider.isTrustedCallingUid(2000, myUid));
        assertTrue(ShizukuProvider.isTrustedCallingUid(myUid, myUid));

        ShizukuProvider.enforceCallingUid(0, myUid);
        ShizukuProvider.enforceCallingUid(2000, myUid);
        ShizukuProvider.enforceCallingUid(myUid, myUid);

        assertFalse(ShizukuProvider.isTrustedCallingUid(10001, myUid));
        try {
            ShizukuProvider.enforceCallingUid(10001, myUid);
            fail("Expected SecurityException for uid=10001");
        } catch (SecurityException e) {
            assertEquals("Unauthorized binder delivery from uid=10001", e.getMessage());
        }
    }
}
