package net.binis.intellij.util;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.util.PathsList;
import net.binis.codegen.tools.Reflection;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static net.binis.codegen.tools.Tools.*;

public class CodeGenDependenciesUtil {

    public static boolean isUsingCodeGenJars(Project project) {
        if (project.isDisposed()) {
            return false;
        }

        String pluginVer = getVersionFromPlugin();
        if (isNull(pluginVer)) {
            return false;
        }

        return nonNull(getVersionFromProject(project));
    }

    protected static String getVersionFromProject(Project project) {
        var jars = getJarsInProject(project);
        if (jars.isEmpty()) {
            return null;
        }
        for (var jarPath : jars) {
            var file = new File(jarPath);
            var version = getVersionFromJarName(file.getName());
            if (!version.isEmpty()) {
                return version;
            }
        }
        return null;
    }

    protected static String getVersionFromPlugin() {
        List<URL> urls = getUrls();
        for (URL url : urls) {
            String name = null;
            try {
                var file = new File(url.toURI());
                name = file.getName();
            } catch (Throwable t) {
                var filepath = url.getFile();
                if (filepath != null) {
                    var iSlash = filepath.lastIndexOf("/");
                    if (iSlash > 0 && iSlash + 1 < filepath.length()) {
                        name = filepath.substring(iSlash + 1);
                    }
                }
            }
            if (name != null && name.startsWith("code-generator-")) {
                var version = getVersionFromJarName(name);
                if (!version.isEmpty() && Character.isDigit(version.charAt(0))) {
                    return version;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    protected static List<URL> getUrls() {
        ClassLoader cl = CodeGenDependenciesUtil.class.getClassLoader();
        if (cl instanceof PluginClassLoader loader) {
            return loader.getUrls();
        }

        var getURLs = getURLsMethod(cl);
        if (nonNull(getURLs)) {
            var urls = Reflection.invoke(getURLs, cl);
            return urls.getClass().isArray()
                    ? Arrays.asList((URL[]) urls)
                    : (List<URL>) urls;
        }
        throw new IllegalStateException();
    }

    protected static Method getURLsMethod(Object receiver) {
        var method = Reflection.findMethod("getURLs", receiver.getClass());
        if (isNull(method)) {
            method = Reflection.findMethod("getUrls", receiver.getClass());
        }
        if (isNull(method)) {
            method = Reflection.findMethod("getURLs", receiver.getClass(), List.class);
            if (isNull(method)) {
                method = Reflection.findMethod("getUrls", receiver.getClass(), List.class);
            }
            if (isNull(method) && receiver instanceof ClassLoader) {
                var field = Reflection.getFieldValue(receiver, "ucp");
                if (nonNull(field)) {
                    method = getURLsMethod(field);
                }
            }
        }
        return method;
    }

    protected static String getVersionFromJarName(String fileName) {
        StringBuilder version = new StringBuilder();
        boolean dotSeen = false;
        for (int i = 0; i < fileName.length(); i++) {
            char c = fileName.charAt(i);
            if (Character.isDigit(c)) {
                if (dotSeen) {
                    version.append('.');
                    dotSeen = false;
                }
                version.append(c);
            } else if (!version.isEmpty()) {
                if (!dotSeen && c == '.') {
                    dotSeen = true;
                } else {
                    break;
                }
            }
        }
        return version.toString();
    }

    public static List<String> getJarsInProject(Project project) {
        ModuleManager moduleManager = ModuleManager.getInstance(project);
        Module[] allModules = moduleManager.getModules();
        PathsList preprocessorPath = new PathsList();
        for (Module m : allModules) {
            String processorPath = CompilerConfiguration.getInstance(project)
                    .getAnnotationProcessingConfiguration(m).getProcessorPath();
            Arrays.stream(processorPath.split(File.pathSeparator)).forEach(preprocessorPath::add);
        }
        PathsList pathsList = ProjectRootManager.getInstance(project)
                .orderEntries().withoutSdk().librariesOnly().getPathsList();
        pathsList.addAll(preprocessorPath.getPathList());
        return getJars(pathsList);
    }

    public static List<String> getJarsInModule(Module module) {
        PathsList pathsList = ModuleRootManager.getInstance(module)
                .orderEntries().withoutSdk().librariesOnly().getPathsList();
        return getJars(pathsList);
    }

    protected static List<String> getJars(PathsList pathsList) {
        var result = new ArrayList<String>();
        pathsList.getVirtualFiles().forEach(path ->
                with(path.getExtension(), extension ->
                        condition(extension.equalsIgnoreCase("jar") && path.getNameWithoutExtension().contains("code-generator"), () ->
                                with(path.getCanonicalPath(), canonicalPath -> {
                                    try {
                                        canonicalPath = canonicalPath.replace('/', File.separatorChar);
                                        var file = new File(canonicalPath);
                                        if (!file.isFile()) {
                                            file = new File(new URL(path.getUrl()).getFile());
                                        }

                                        try (var jarFile = new JarFile(file.getAbsoluteFile())) {
                                            var manifest = jarFile.getManifest();
                                            var attributes = manifest.getMainAttributes();
                                            var vendor = attributes.getValue("Implementation-Vendor");
                                            if (nonNull(vendor) && vendor.trim().equals("Binis Belev")) {
                                                result.add(file.getAbsolutePath());
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    } catch (MalformedURLException e) {
                                        e.printStackTrace();
                                    }
                                }))));
        return result;
    }
}