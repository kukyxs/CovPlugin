package com.kuky.covplugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Utils {
    public static String transClassNameToLayoutName(String original) {
        List<Integer> upperIndexList = new ArrayList<>();
        char[] oriChars = original.toCharArray();
        StringBuilder sb = new StringBuilder(original.toLowerCase());

        for (int i = 0; i < oriChars.length; i++) {
            if (isUpper(oriChars[i])) upperIndexList.add(i);
        }

        int offset = 0;
        if (upperIndexList.contains(0)) upperIndexList.remove(0);

        for (int index : upperIndexList) {
            index += offset;
            sb.insert(index, "_");
            offset++;
        }

        return sb.toString();
    }

    private static boolean isUpper(char c) {
        return c >= 'A' && c <= 'Z';
    }

    public static String layoutToBindings(String layoutName) {
        Stream<String> names = Arrays.stream(layoutName.split("_")).map(Utils::upperCaseFirst);
        final String[] bindingName = {""};
        names.forEach(n -> bindingName[0] += n);
        return bindingName[0] + "Binding";
    }

    private static String upperCaseFirst(String val) {
        char[] arr = val.toCharArray();
        arr[0] = Character.toUpperCase(arr[0]);
        return new String(arr);
    }

    public static String findAndroidManifestFile(File file) {
        String path = file.getAbsolutePath();
        String prefix = path.contains("src/main/java") ? path.split("src/main/java/")[0]
                : path.contains("src/main/kotlin") ? path.split("src/main/kotlin/")[0]
                : null;

        if (prefix == null) return null;
        return prefix + "src/main/AndroidManifest.xml";
    }

    public static String getResLayoutPath(Project project, String layoutName, File classFile) {
        String resLayoutPath = Utils.findAndroidResLayoutFile(classFile);
        if (resLayoutPath == null) return null;

        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(resLayoutPath);
        if (virtualFile == null) return null;

        PsiDirectory layoutDir = PsiManager.getInstance(project).findDirectory(virtualFile);
        if (layoutDir == null) return null;

        layoutDir.createFile(layoutName + ".xml");
        return resLayoutPath;
    }

    private static String findAndroidResLayoutFile(File file) {
        String path = file.getAbsolutePath();
        String prefix = path.contains("src/main/java") ? path.split("src/main/java/")[0]
                : path.contains("src/main/kotlin") ? path.split("src/main/kotlin/")[0]
                : null;

        if (prefix == null) return null;
        return prefix + "src/main/res/layout";
    }

    /**
     * 查找合适的包名
     */
    public static List<String> findMatchedPkgList(Map<String, List<File>> packageGroup, PsiDirectory directory) {
        String choosePath = directory.getVirtualFile().getPath();
        File chooseFile = new File(choosePath);
        String key = Utils.fileHeader(chooseFile);

        if (packageGroup.containsKey(key)) {
            List<File> fileList = packageGroup.get(key);
            String pkg = Utils.fileToPackage(chooseFile);
            Stream<String> pkgStream = fileList.stream().map(Utils::fileToPackage);

            List<String> pkgList = new ArrayList<>();
            pkgStream.forEach(s -> Utils.pkgToList(s).forEach(s1 -> {
                if (s1.length() > 0 && !pkgList.contains(s1)) pkgList.add(s1);
            }));

            if (pkgList.contains(pkg)) {
                pkgList.remove(pkg);
                pkgList.add(0, pkg);
            }

            return pkgList.stream().map(s -> s.replace("/", ".")).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    /**
     * 包名分组
     */
    public static void findAndGroupPkgList(List<File> packageFiles, List<File> srcJavaFiles,
                                           Map<String, List<File>> packageGroup, String projectBasePath) {
        File projectFile = new File(projectBasePath);
        File[] children = projectFile.listFiles();

        if (projectFile.isDirectory() && children != null && children.length > 0) {
            Utils.findAllSrcMainJavaFiles(projectFile, srcJavaFiles);
        }

        if (!srcJavaFiles.isEmpty()) {
            srcJavaFiles.forEach(file -> Utils.findPackageFiles(file, packageFiles));
        }

        if (!packageFiles.isEmpty()) {
            packageFiles.forEach(file -> {
                String hdKey = Utils.fileHeader(file);
                if (packageGroup.containsKey(hdKey)) packageGroup.get(hdKey).add(file);
                else {
                    List<File> l = new ArrayList<>();
                    l.add(file);
                    packageGroup.put(hdKey, l);
                }
            });
        }
    }

    private static List<String> pkgToList(String pkg) {
        List<String> result = new ArrayList<>();
        if (pkg.contains("/")) {
            List<String> pSplit = Arrays.stream(pkg.split("/"))
                    .filter(s -> s.length() > 0)
                    .collect(Collectors.toList());

            final String[] s = {""};
            pSplit.forEach(p -> {
                s[0] += p + "/";
                result.add(s[0].substring(0, s[0].length() - 1));
            });
        } else {
            result.add(pkg);
        }

        return result;
    }

    private static String fileToPackage(File file) {
        String path = file.getAbsolutePath();
        if (path.contains("src/main/java")) return path.split("src/main/java/")[1];
        else if (path.contains("src/main/kotlin")) return path.split("src/main/kotlin/")[1];
        return "";
    }

    private static String fileHeader(File file) {
        String path = file.getAbsolutePath();
        if (path.contains("src/main/java")) return path.split("src/main/java")[0];
        else if (path.contains("src/main/kotlin")) return path.split("src/main/kotlin")[0];
        return "";
    }

    private static void findAllSrcMainJavaFiles(File parent, List<File> store) {
        File[] children = parent.listFiles();
        if (children == null) return;

        for (File f : children) {
            if (!f.isDirectory()) continue;

            if (isSrcFile(f)) {
                store.add(f);
            } else if (f.listFiles() != null && Objects.requireNonNull(f.listFiles()).length > 0) {
                findAllSrcMainJavaFiles(f, store);
            }
        }
    }

    private static void findPackageFiles(File parent, List<File> store) {
        if (parent.isDirectory() && !isSrcFile(parent)) store.add(parent);

        File[] children = parent.listFiles();
        if (children != null && children.length > 0) {
            Arrays.stream(children).forEach(file -> findPackageFiles(file, store));
        }
    }

    private static boolean isSrcFile(File file) {
        String lowerPath = file.getPath().toLowerCase();
        return lowerPath.endsWith("src/main/java") || lowerPath.endsWith("src/main/kotlin");
    }

    public static String readCompleteFileAsString(File file) throws IOException {
        StringBuilder fileData = new StringBuilder();
        Scanner scanner = new Scanner(file);
        while (scanner.hasNextLine()) {
            fileData.append(scanner.nextLine()).append("\n");
        }
        scanner.close();
        return fileData.toString();
    }

    /**
     * write content to file
     */
    public static void writeContentToFile(File file, String content) throws IOException {
        FileWriter fileWriter = new FileWriter(file);
        PrintWriter printWriter = new PrintWriter(fileWriter);
        printWriter.print(content);
        printWriter.close();
    }
}
