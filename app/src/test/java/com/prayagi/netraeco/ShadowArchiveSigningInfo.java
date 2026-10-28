package com.prayagi.netraeco;

import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowSigningInfo;

/** Read the real parser-populated SigningDetails instead of the shadow's unset signature array. */
@Implements(value = SigningInfo.class, minSdk = 28)
public class ShadowArchiveSigningInfo extends ShadowSigningInfo {
    @RealObject private SigningInfo realInfo;

    @Override
    @Implementation
    protected Signature[] getApkContentsSigners() {
        try {
            Field field = SigningInfo.class.getDeclaredField("mSigningDetails");
            field.setAccessible(true);
            Object details = field.get(realInfo);
            Method getter = details.getClass().getDeclaredMethod("getSignatures");
            getter.setAccessible(true);
            Signature[] verified = (Signature[]) getter.invoke(details);
            if (verified != null && verified.length > 0) return verified;
            return super.getApkContentsSigners();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not read real archive SigningDetails", e);
        }
    }
}
