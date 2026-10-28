package com.prayagi.netraeco;

import android.system.Os;
import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import org.robolectric.annotation.ClassName;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

/**
 * Test JVM I/O adapter only. Android's data-source implementation calls the Android-only
 * public DirectByteBuffer constructor, which is package-private and different on OpenJDK.
 * Read the exact same byte range with pread and pass it to the original APK digester.
 * No digest, signature, certificate, package parser or production validation is mocked.
 */
@Implements(className = "android.util.apk.MemoryMappedFileDataSource", isInAndroidSdk = false)
public class ShadowApkDataSource {
    @RealObject private Object source;

    private Object field(String name) throws ReflectiveOperationException {
        Field f = source.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(source);
    }

    @Implementation
    protected void feedIntoDataDigester(@ClassName("android.util.apk.DataDigester") Object digester,
                                       long offset, int size) throws Exception {
        FileDescriptor fd = (FileDescriptor) field("mFd");
        long position = (Long) field("mFilePosition");
        byte[] bytes = new byte[size];
        int read = 0;
        while (read < size) {
            int n = Os.pread(fd, bytes, read, size - read, position + offset + read);
            if (n <= 0) throw new IOException("Unexpected end of APK data");
            read += n;
        }
        Class<?> contract = Class.forName("android.util.apk.DataDigester", true, source.getClass().getClassLoader());
        Method consume = contract.getDeclaredMethod("consume", ByteBuffer.class);
        consume.setAccessible(true);
        consume.invoke(digester, ByteBuffer.wrap(bytes));
    }
}
