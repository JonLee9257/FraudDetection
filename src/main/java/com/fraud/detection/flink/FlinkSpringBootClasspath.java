package com.fraud.detection.flink;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.PipelineOptions;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Registers Spring Boot fat-JAR nested URLs ({@code BOOT-INF/lib/*.jar}) on Flink's pipeline classpath.
 * <p>
 * Embedded MiniCluster builds a user-code {@link ClassLoader} that does not automatically see nested
 * dependency JARs (only the outer {@code app.jar} appears on {@code java.class.path}), so JobMaster
 * deserialization can throw {@code ClassNotFoundException} for {@code org.apache.flink.api.common.ExecutionConfig}.
 * <p>
 * For production, the usual pattern is a dedicated Flink cluster and job submission (see
 * <a href="https://nightlies.apache.org/flink/flink-docs-stable/docs/deployment/resource-providers/standalone/docker/">Flink on Docker</a>).
 */
public final class FlinkSpringBootClasspath {

    private FlinkSpringBootClasspath() {}

    public static void augment(Configuration configuration) {
        List<String> discovered = collectFromClasspathLoaders();
        if (discovered.isEmpty()) {
            return;
        }
        LinkedHashSet<String> merged =
                new LinkedHashSet<>(configuration.getOptional(PipelineOptions.CLASSPATHS).orElse(List.of()));
        merged.addAll(discovered);
        configuration.set(PipelineOptions.CLASSPATHS, new ArrayList<>(merged));
    }

    private static List<String> collectFromClasspathLoaders() {
        Set<String> seen = new LinkedHashSet<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = FlinkSpringBootClasspath.class.getClassLoader();
        }
        for (int depth = 0; depth < 8 && cl != null; depth++) {
            addUrlsFromLoader(cl, seen);
            cl = cl.getParent();
        }
        return new ArrayList<>(seen);
    }

    private static void addUrlsFromLoader(ClassLoader cl, Set<String> seen) {
        if (cl instanceof URLClassLoader ucl) {
            for (URL u : ucl.getURLs()) {
                addUrl(seen, u);
            }
            return;
        }
        try {
            Method m = cl.getClass().getMethod("getURLs");
            Object r = m.invoke(cl);
            if (r instanceof URL[] urls) {
                for (URL u : urls) {
                    addUrl(seen, u);
                }
            }
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            // Not a URLClassLoader-style loader (e.g. some test / IDE setups).
        }
    }

    private static void addUrl(Set<String> seen, URL u) {
        if (u == null) {
            return;
        }
        String s = u.toExternalForm();
        if (!s.isEmpty()) {
            seen.add(s);
        }
    }
}
