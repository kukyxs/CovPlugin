package com.kuky.covplugin.ui;

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

public class CovRecyclerAdapterCreatorDialog extends DialogWrapper {
    private final Project mProject;
    private final PsiDirectory mDirectory;
    private String packageName;

    private JPanel contentPanel;
    private JTextField adapterNameField;
    private JTextField layoutNameField;
    private JTextField pojoClassField;
    private JComboBox<String> packageComboBox;
    private JLabel errorHint;
    private JCheckBox hiltCheckBox;

    public CovRecyclerAdapterCreatorDialog(Project project, PsiDirectory directory) {
        super(project, true);
        mDirectory = directory;
        mProject = project;

        init();
        setTitle("Cov RecyclerView Adapter Creator");

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

        Document classDocument = adapterNameField.getDocument();
        classDocument.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                checkAdapterName(adapterNameField.getText());
            }
        });

        Document layoutDocument = layoutNameField.getDocument();
        layoutDocument.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                checkLayoutName();
            }
        });

        Document pojoDocument = pojoClassField.getDocument();
        pojoDocument.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                checkPojoName();
            }
        });

        return contentPanel;
    }

    private void checkLayoutName() {
        String layoutName = layoutNameField.getText();
        if (layoutName.matches("[A-Za-z0-9_-]+")) {
            errorHint.setVisible(false);
            setOKActionEnabled(!StringUtil.isBlank(adapterNameField.getText()));
        } else {
            errorHint.setVisible(true);
            errorHint.setText("Not validate layout name");
            setOKActionEnabled(false);
        }
    }

    private void checkPojoName() {
        String pojoName = pojoClassField.getText();
        if (pojoName == null || pojoName.length() == 0 || pojoName.matches("[A-Za-z0-9_-]+")) {
            errorHint.setVisible(false);
            setOKActionEnabled(!StringUtil.isBlank(adapterNameField.getText()) && !StringUtil.isBlank(layoutNameField.getText()));
        } else {
            errorHint.setVisible(true);
            errorHint.setText("Not validate pojo class name");
            setOKActionEnabled(false);
        }
    }

    private void checkAdapterName(String fragmentName) {
        String layoutName = Utils.transClassNameToLayoutName(fragmentName);

        layoutName = layoutName
                .replace("_recycler", "")
                .replace("_adapter", "")
                .replaceAll("\\s+", "")
                .replaceAll("_+", "_");

        layoutNameField.setText((layoutName.startsWith("_") ? "recycler" : "recycler_") + layoutName + "_item");

        if (fragmentName.matches("[A-Za-z0-9_-]+")) {
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
        String className = adapterNameField.getText();
        String layoutName = layoutNameField.getText();

        File classFile = new File(mDirectory.createFile(className + ".kt").getVirtualFile().getPath());
        String resLayoutPath = Utils.getResLayoutPath(mProject, layoutName, classFile);
        if (resLayoutPath == null) {
            classFile.delete();
            return;
        }

        File layoutFile = new File(resLayoutPath, layoutName + ".xml");

        try {
            Utils.writeContentToFile(classFile, adapterClassFileModel(className, layoutName, Utils.appPackage(classFile), pojoClassField.getText()));
            Utils.writeContentToFile(layoutFile, adapterLayoutModel());
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("write to file error");
        }
    }

    private String adapterClassFileModel(String className, String layoutName, String appPkg, String pojo) {
        boolean hilt = hiltCheckBox.isSelected();

        return "package " + packageName + "\n" +
                "\n" +
                "import com.kk.android.comvvmhelper.ui.BaseRecyclerViewAdapter\n" +
                "import com.kk.android.comvvmhelper.ui.BaseRecyclerViewHolder\n" +
                (StringUtil.isBlank(appPkg) ? "" :
                        "import " + appPkg + ".R\n" +
                                "import " + appPkg + ".databinding." + Utils.layoutToBindings(layoutName) + "\n") +
                (hilt ? "import javax.inject.Inject\n" : "") +
                "\n" +
                "class " + className + (hilt ? " @Inject constructor()" : "") + " : BaseRecyclerViewAdapter<" + (StringUtil.isBlank(pojo) ? "Any" : pojo) + ">() {\n" +
                "\n" +
                "    override fun layoutId(viewType: Int) = R.layout." + layoutName + "\n" +
                "\n" +
                "    override fun setVariable(data: " + (StringUtil.isBlank(pojo) ? "Any" : pojo) + ", holder: BaseRecyclerViewHolder, dataPosition: Int, layoutPosition: Int) {\n" +
                "        holder.viewDataBinding<" + Utils.layoutToBindings(layoutName) + ">()?.run {\n" +
                "\n" +
                "        }\n" +
                "    }\n" +
                "}";
    }

    private String adapterLayoutModel() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<layout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "\n" +
                "    <data>\n" +
                "\n" +
                "    </data>\n" +
                "\n" +
                "    <LinearLayout\n" +
                "        android:layout_width=\"match_parent\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:orientation=\"vertical\">\n" +
                "\n" +
                "    </LinearLayout>\n" +
                "</layout>";
    }
}
