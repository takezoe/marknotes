package com.marknotes;

import com.marknotes.ui.AppIcon;
import com.marknotes.ui.MainFrame;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarkNotesApp {
    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "MarkNotes");

        Path notesDir;
        if (args.length > 0) {
            notesDir = Paths.get(args[0]);
        } else {
            notesDir = Paths.get(System.getProperty("user.home"), "MarkNotes");
        }

        if (Taskbar.isTaskbarSupported()) {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                taskbar.setIconImage(AppIcon.createIcons().get(4));
            }
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Fall back to default
            }

            MainFrame frame = new MainFrame(notesDir);
            frame.setVisible(true);
            SwingUtilities.invokeLater(frame::refreshUI);
        });
    }
}
