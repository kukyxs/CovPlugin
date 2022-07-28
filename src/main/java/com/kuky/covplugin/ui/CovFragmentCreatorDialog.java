package com.kuky.covplugin.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
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

public class CovFragmentCreatorDialog extends DialogWrapper {
    private final Project mProject;
    private final PsiDirectory mDirectory;
    private String packageName;

    private JPanel contentPanel;
    private JTextField fragmentNameField;
    private JTextField layoutNameFiled;
    private JComboBox<String> packageComboBox;
    private JLabel errorHint;
    private JCheckBox hiltCheckBox;

    public CovFragmentCreatorDialog(Project project, PsiDirectory directory) {
        super(project, true);
        mDirectory = directory;
        mProject = project;

        init();
        setTitle("Cov Fragment Creator");

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

        Document classDocument = fragmentNameField.getDocument();
        classDocument.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                checkFragmentName(fragmentNameField.getText());
            }
        });

        Document layoutDocument = layoutNameFiled.getDocument();
        layoutDocument.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                checkLayoutName();
            }
        });

        return contentPanel;
    }

    private void checkLayoutName() {
        String layoutName = layoutNameFiled.getText();
        if (layoutName.matches("[A-Za-z0-9_-]+")) {
            errorHint.setVisible(false);
            setOKActionEnabled(!StringUtil.isBlank(fragmentNameField.getText()));
        } else {
            errorHint.setVisible(true);
            errorHint.setText("Not validate layout name");
            setOKActionEnabled(false);
        }
    }

    private void checkFragmentName(String fragmentName) {
        String layoutName = Utils.transClassNameToLayoutName(fragmentName);

        layoutName = layoutName
                .replace("_fragment", "")
                .replaceAll("\\s+", "")
                .replaceAll("_+", "_");

        layoutNameFiled.setText((layoutName.startsWith("_") ? "fragment" : "fragment_") + layoutName);

        if (fragmentName.matches("[A-Za-z0-9_-]+")) {
            errorHint.setVisible(false);
            setOKActionEnabled(!StringUtil.isBlank(layoutNameFiled.getText()));
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
        String className = fragmentNameField.getText();
        String layoutName = layoutNameFiled.getText();

        File classFile = new File(mDirectory.createFile(className + ".kt").getVirtualFile().getPath());
        String resLayoutPath = Utils.getResLayoutPath(mProject, layoutName, classFile);
        if (resLayoutPath == null) {
            classFile.delete();
            return;
        }

        File layoutFile = new File(resLayoutPath, layoutName + ".xml");

        ApplicationManager.getApplication().runWriteAction(() -> {
            try {
                Utils.writeContentToFile(classFile, fragmentClassFileModel(className, layoutName));
                Utils.writeContentToFile(layoutFile, fragmentLayoutModel());
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("write to file error");
            }
        });
    }

    private String fragmentClassFileModel(String className, String layoutName) {
        boolean hilt = hiltCheckBox.isSelected();

        return "package " + packageName +
                "\n" +
                "import android.os.Bundle\n" +
                "import android.view.View\n" +
                "import com.kk.android.comvvmhelper.ui.BaseFragment\n" +
                (hilt ? "import dagger.hilt.android.AndroidEntryPoint\nimport javax.inject.Inject\n" : "") +
                "\n" +
                (hilt ? "@AndroidEntryPoint\n" : "") +
                "class " + className + (hilt ? " @Inject constructor()" : "") + " : BaseFragment<" + Utils.layoutToBindings(layoutName) + ">() {\n" +
                "\n" +
                "    override fun layoutId() = R.layout." + layoutName + "\n" +
                "\n" +
                "    override fun initFragment(view: View, savedInstanceState: Bundle?) {\n" +
                "\n" +
                "    }\n" +
                "}";
    }

    private String fragmentLayoutModel() {
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
