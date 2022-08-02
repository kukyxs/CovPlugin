package com.kuky.covplugin.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.kuky.covplugin.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.internal.StringUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.Document;
import java.awt.event.ItemEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CovActivityCreatorDialog extends DialogWrapper {
    private final Project mProject;
    private final PsiDirectory mDirectory;
    private String packageName;

    private JPanel contentPanel;
    private JLabel errorHint;
    private JTextField activityNameField;
    private JTextField layoutNameField;
    private JTextField viewModelField;
    private JComboBox<String> packageComboBox;
    private JCheckBox hiltCheckBox;

    public CovActivityCreatorDialog(Project project, PsiDirectory directory) {
        super(project, true);

        mDirectory = directory;
        mProject = project;

        init();
        setTitle("Cov Activity Creator");

        List<File> packageFiles = new ArrayList<>();
        List<File> srcJavaFiles = new ArrayList<>();
        Map<String, List<File>> packageGroup = new HashMap<>();

        String basePath = project.getBasePath();

        if (basePath != null) {
            Utils.findAndGroupPkgList(packageFiles, srcJavaFiles, packageGroup, basePath);
            List<String> pkgList = Utils.findMatchedPkgList(packageGroup, mDirectory);

            if (!pkgList.isEmpty()) {
                packageName = pkgList.get(0);
                pkgList.forEach(s -> packageComboBox.addItem(s));
            }
        }
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        setModal(true);
        errorHint.setForeground(JBColor.RED);
        setOKActionEnabled(false);

        packageComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                packageName = (String) e.getItem();
            }
        });

        Document classDocument = activityNameField.getDocument();
        classDocument.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                checkActivityName(activityNameField.getText());
            }
        });

        Document layoutDocument = layoutNameField.getDocument();
        layoutDocument.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                checkLayoutName();
            }
        });

        Document vmDocument = viewModelField.getDocument();
        vmDocument.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                checkViewModelName();
            }
        });

        return contentPanel;
    }

    private void checkViewModelName() {
        String viewModelName = viewModelField.getText();
        if (viewModelName == null || viewModelName.length() == 0 || viewModelName.matches("[A-Za-z0-9_-]+")) {
            errorHint.setVisible(false);
            setOKActionEnabled(!StringUtil.isBlank(activityNameField.getText()) && !StringUtil.isBlank(layoutNameField.getText()));
        } else {
            errorHint.setVisible(true);
            errorHint.setText("Not validate ViewModel name");
            setOKActionEnabled(false);
        }
    }

    private void checkLayoutName() {
        String layoutName = layoutNameField.getText();
        if (layoutName.matches("[A-Za-z0-9_-]+")) {
            errorHint.setVisible(false);
            setOKActionEnabled(!StringUtil.isBlank(activityNameField.getText()));
        } else {
            errorHint.setVisible(true);
            errorHint.setText("Not validate layout name");
            setOKActionEnabled(false);
        }
    }

    private void checkActivityName(String activityName) {
        String layoutName = Utils.transClassNameToLayoutName(activityName);
        String viewModelName = (activityName + "ViewModel").replace("Activity", "");

        layoutName = layoutName
                .replace("_activity", "")
                .replaceAll("\\s+", "")
                .replaceAll("_+", "_");

        layoutNameField.setText((layoutName.startsWith("_") ? "activity" : "activity_") + layoutName);
        viewModelField.setText(viewModelName);

        if (activityName.matches("[A-Za-z0-9_-]+")) {
            errorHint.setVisible(false);
            setOKActionEnabled(!StringUtil.isBlank(layoutNameField.getText()));
        } else {
            errorHint.setVisible(true);
            errorHint.setText("Not validate class name");
            setOKActionEnabled(false);
        }
    }

    @Override
    protected void doOKAction() {
        createFiles();
        super.doOKAction();
    }

    private void createFiles() {
        String className = activityNameField.getText();
        String layoutName = layoutNameField.getText();
        String vmName = viewModelField.getText();

        File vmFile = null;
        File classFile = new File(mDirectory.createFile(className + ".kt").getVirtualFile().getPath());
        if (!StringUtil.isBlank(vmName)) {
            vmFile = new File(mDirectory.createFile(vmName + ".kt").getVirtualFile().getPath());
        }

        String resLayoutPath = Utils.getResLayoutPath(mProject, layoutName, classFile);
        if (resLayoutPath == null) {
            classFile.delete();
            if (vmFile != null && vmFile.exists()) {
                vmFile.delete();
            }
            return;
        }

        File layoutFile = new File(resLayoutPath, layoutName + ".xml");

        String manifestPath = Utils.findAndroidManifestFile(classFile);
        File manifestFile = manifestPath == null ? null : new File(manifestPath);

        try {
            Utils.writeContentToFile(classFile, activityClassFileModel(className, layoutName, Utils.appPackage(classFile), vmName));
            Utils.writeContentToFile(layoutFile, activityLayoutModel());

            if (vmFile != null) {
                Utils.writeContentToFile(vmFile, viewModelFileModel(vmName));
            }

            if (manifestFile != null) {
                String content = Utils.readCompleteFileAsString(manifestFile);
                if (!content.contains("</application>")) return;
                int insertIndex = content.indexOf("</application>");
                StringBuilder sb = new StringBuilder(content);
                sb.insert(insertIndex, "\n" + "        <activity\n" +
                        "            android:name=\"" + packageName + "." + className + "\"\n" +
                        "            android:exported=\"false\" />\n");
                Utils.writeContentToFile(manifestFile, sb.toString().trim());
                VirtualFile vf = ProjectUtil.guessProjectDir(mProject);
                if (vf != null) vf.refresh(true, true);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("write to file error");
        }
    }

    private String viewModelFileModel(String viewModelName) {
        boolean hilt = hiltCheckBox.isSelected();

        return "package " + packageName + "\n" +
                "\n" +
                "import androidx.lifecycle.ViewModel\n" +
                (hilt ? "import dagger.hilt.android.lifecycle.HiltViewModel\nimport javax.inject.Inject\n" : "") +
                "\n" +
                (hilt ? "@HiltViewModel\n" : "") +
                "class " + viewModelName + (hilt ? " @Inject constructor()" : "") + " : ViewModel() {\n\n" +
                "}";
    }

    private String activityClassFileModel(String className, String layoutName, String appPkg, String viewModel) {
        boolean hilt = hiltCheckBox.isSelected();
        boolean createVm = !StringUtil.isBlank(viewModel);

        return "package " + packageName + "\n" +
                "\n" +
                "import android.os.Bundle\n" +
                (createVm ? "import androidx.activity.viewModels\n" : "") +
                "import com.kk.android.comvvmhelper.anno.ActivityConfig\n" +
                "import com.kk.android.comvvmhelper.ui.BaseActivity\n" +
                (StringUtil.isBlank(appPkg) ? "" :
                        "import " + appPkg + ".R\n" +
                                "import " + appPkg + ".databinding." + Utils.layoutToBindings(layoutName) + "\n") +
                (hilt ? "import dagger.hilt.android.AndroidEntryPoint\n" : "") +
                "\n" +
                (hilt ? "@AndroidEntryPoint\n" : "") +
                "@ActivityConfig\n" +
                "class " + className + " : BaseActivity<" + Utils.layoutToBindings(layoutName) + ">() {\n" +
                "\n" +
                (createVm ? "    private val viewModel by viewModels<" + viewModel + ">()\n\n" : "") +
                "    override fun layoutId() = R.layout." + layoutName + "\n" +
                "\n" +
                "    override fun initActivity(savedInstanceState: Bundle?) {\n" +
                "\n" +
                "    }\n" +
                "}";
    }

    private String activityLayoutModel() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<layout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "\n" +
                "    <data>\n" +
                "\n" +
                "    </data>\n" +
                "\n" +
                "    <LinearLayout\n" +
                "        android:layout_width=\"match_parent\"\n" +
                "        android:layout_height=\"match_parent\"\n" +
                "        android:orientation=\"vertical\">\n" +
                "\n" +
                "    </LinearLayout>\n" +
                "</layout>";
    }
}
