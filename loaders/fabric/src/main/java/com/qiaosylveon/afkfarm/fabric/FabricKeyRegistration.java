package com.qiaosylveon.afkfarm.fabric;

import java.lang.reflect.Method;
import net.minecraft.client.KeyMapping;

/**
 * Fabric key registration changed between 1.21.11 and 26.1/26.2:
 * 1.21.11 uses KeyBindingHelper.registerKeyBinding, 26.x uses
 * KeyMappingHelper.registerKeyMapping in the renamed key-mapping module.
 *
 * Reflection keeps one shared Fabric source set across all three targets and
 * avoids a compile-time dependency on a Fabric API module that is only present
 * in some versions.
 */
public final class FabricKeyRegistration {

    private FabricKeyRegistration() {
    }

    public static void register(KeyMapping keyMapping) {
        if (tryRegister("net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper", "registerKeyMapping", keyMapping)) {
            return;
        }
        if (tryRegister("net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper", "registerKeyBinding", keyMapping)) {
            return;
        }
        throw new IllegalStateException("No supported Fabric key-mapping helper found");
    }

    private static boolean tryRegister(String className, String methodName, KeyMapping keyMapping) {
        try {
            Class<?> helperClass = Class.forName(className);
            Method method = helperClass.getMethod(methodName, KeyMapping.class);
            method.invoke(null, keyMapping);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return false;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to register key mapping via " + className, exception);
        }
    }
}
