package portablejim.bbw.core;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import java.net.URL;
import java.io.File;

/** Forge 1.7's registries require their normal class-loader type even in offline tests. */
public final class ForgeTestRunner {
    public static void main(String[] args) throws Exception {
        String[] entries = System.getProperty("java.class.path").split(File.pathSeparator);
        URL[] urls = new URL[entries.length];
        for (int i=0; i<entries.length; i++) urls[i] = new File(entries[i]).toURI().toURL();
        Launch.classLoader = new LaunchClassLoader(urls);
        Launch.classLoader.addClassLoaderExclusion("org.objectweb.asm.");
        Launch.classLoader.registerTransformer("cpw.mods.fml.common.asm.transformers.EventSubscriptionTransformer");
        Thread.currentThread().setContextClassLoader(Launch.classLoader);
        Launch.classLoader.loadClass("org.junit.runner.JUnitCore").getMethod("main",String[].class)
                .invoke(null,(Object) args);
    }
}
