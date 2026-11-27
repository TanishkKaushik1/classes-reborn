package com.tanishk.classesreborn.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * JNI bridge to the native Rust library ({@code classesreborn_native}).
 */
public final class RustBridge {
    private static volatile boolean loaded = false;

    static {
        loadNativeLibrary();
    }

    private RustBridge() {
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static native String ping();

    public static native double computeDamage(double baseDamage, int mightPoints);

    private static synchronized void loadNativeLibrary() {
        if (loaded) return;

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean isArm64 = arch.contains("aarch64") || arch.contains("arm64");

        String platform;
        String fileName;
        if (os.contains("win")) {
            platform = "windows-" + (arch.contains("64") ? "x86_64" : "x86");
            fileName = "classesreborn_native.dll";
        } else if (os.contains("mac")) {
            platform = "macos-" + (isArm64 ? "aarch64" : "x86_64");
            fileName = "libclassesreborn_native.dylib";
        } else {
            platform = "linux-" + (isArm64 ? "aarch64" : "x86_64");
            fileName = "libclassesreborn_native.so";
        }

        String resourcePath = "/natives/" + platform + "/" + fileName;
        try (InputStream in = RustBridge.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new UnsatisfiedLinkError("Missing native library in mod resources: " + resourcePath);
            }
            Path temp = Files.createTempFile("classesreborn_native_", "_" + fileName);
            temp.toFile().deleteOnExit();
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            System.load(temp.toAbsolutePath().toString());
            loaded = true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract and load Rust native library: " + resourcePath, e);
        }
    }
}
