package com.kuky.covplugin.actions;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.kuky.covplugin.ui.CovFragmentCreatorDialog;
import org.jetbrains.annotations.NotNull;

public class CovFragmentAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        IdeView ideView = LangDataKeys.IDE_VIEW.getData(event.getDataContext());
        if (project != null && ideView != null) {
            new CovFragmentCreatorDialog(project, ideView.getOrChooseDirectory()).show();
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        e.getPresentation().setEnabledAndVisible(project != null);
    }
}
