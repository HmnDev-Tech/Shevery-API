package com.hamondev.shevery.api;

import android.app.Application;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBinder;
import org.robolectric.shadows.ShadowProcess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, application = Application.class)
public class SheveryProviderCallTest {

    private static final int MY_UID = 10420;
    private static final int UNTRUSTED_UID = 10234;
    private static final String AUTHORITY = "com.example.client.shevery";

    private SheveryProvider provider;

    @Before
    public void setUp() {
        ShadowProcess.setUid(MY_UID);
        ProviderInfo info = new ProviderInfo();
        info.authority = AUTHORITY;
        info.exported = true;
        info.multiprocess = false;
        provider = Robolectric.buildContentProvider(SheveryProvider.class).create(info).get();
    }

    @After
    public void tearDown() {
        ShadowBinder.reset();
        ShadowProcess.reset();
    }

    @Test
    public void sendBinderAllowsTrustedUidsAndRejectsUntrustedUid() {
        int[] trustedUids = {0, 2000, MY_UID};
        for (int uid : trustedUids) {
            ShadowBinder.setCallingUid(uid);
            Bundle result = provider.call(SheveryProvider.METHOD_SEND_BINDER, null, new Bundle());
            assertNotNull("Expected reply bundle for trusted uid=" + uid, result);
        }

        ShadowBinder.setCallingUid(UNTRUSTED_UID);
        try {
            provider.call(SheveryProvider.METHOD_SEND_BINDER, null, new Bundle());
            fail("Expected SecurityException for untrusted sendBinder");
        } catch (SecurityException e) {
            assertEquals("Unauthorized binder delivery from uid=" + UNTRUSTED_UID, e.getMessage());
        }

        try {
            RuntimeEnvironment.getApplication().getContentResolver()
                    .call(Uri.parse("content://" + AUTHORITY), SheveryProvider.METHOD_SEND_BINDER, null, new Bundle());
            fail("Expected SecurityException via ContentResolver for untrusted sendBinder");
        } catch (SecurityException e) {
            assertEquals("Unauthorized binder delivery from uid=" + UNTRUSTED_UID, e.getMessage());
        }
    }

    @Test
    public void sendUserServiceAllowsTrustedUidsAndRejectsUntrustedUid() {
        int[] trustedUids = {0, 2000, MY_UID};
        for (int uid : trustedUids) {
            ShadowBinder.setCallingUid(uid);
            assertNull(provider.call(SheveryApiConstants.METHOD_SEND_USER_SERVICE, null, new Bundle()));
        }

        ShadowBinder.setCallingUid(UNTRUSTED_UID);
        try {
            provider.call(SheveryApiConstants.METHOD_SEND_USER_SERVICE, null, new Bundle());
            fail("Expected SecurityException for untrusted sendUserService");
        } catch (SecurityException e) {
            assertEquals("Unauthorized binder delivery from uid=" + UNTRUSTED_UID, e.getMessage());
        }
    }

    @Test
    public void requestBinderIsNotBlockedForClientUids() {
        ShadowBinder.setCallingUid(UNTRUSTED_UID);
        Bundle reply = RuntimeEnvironment.getApplication().getContentResolver()
                .call(Uri.parse("content://" + AUTHORITY), SheveryProvider.METHOD_REQUEST_BINDER, null, new Bundle());
        assertNull(reply);
    }

    @Test
    public void getBinderRejectsUntrustedUid() {
        ShadowBinder.setCallingUid(MY_UID);
        assertNull(provider.call(SheveryProvider.METHOD_GET_BINDER, null, new Bundle()));

        ShadowBinder.setCallingUid(UNTRUSTED_UID);
        try {
            provider.call(SheveryProvider.METHOD_GET_BINDER, null, new Bundle());
            fail("Expected SecurityException for untrusted getBinder");
        } catch (SecurityException e) {
            assertEquals("Unauthorized binder delivery from uid=" + UNTRUSTED_UID, e.getMessage());
        }
    }
}
