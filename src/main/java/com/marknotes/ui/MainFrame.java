package com.marknotes.ui;

import com.marknotes.util.AppState;
import com.marknotes.util.NoteStorage;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

public class MainFrame extends JFrame {
    private final NoteStorage storage;
    private final NoteListPanel noteListPanel;
    private final EditorPanel editorPanel;
    private final AppState appState;
    private JMenu themeMenu;

    public MainFrame(Path notesDir) {
        super("MarkNotes");
        this.storage = new NoteStorage(notesDir);
        this.appState = new AppState(notesDir);

        setIconImages(AppIcon.createIcons());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        int w = appState.getWindowWidth();
        int h = appState.getWindowHeight();
        setSize(w, h);
        int x = appState.getWindowX();
        int y = appState.getWindowY();
        if (x >= 0 && y >= 0) {
            setLocation(x, y);
        } else {
            setLocationRelativeTo(null);
        }

        editorPanel = new EditorPanel(storage);
        noteListPanel = new NoteListPanel(storage);
        noteListPanel.setOnNoteSelected((note, query) -> {
            editorPanel.openNote(note);
            if (query != null && !query.isEmpty()) {
                editorPanel.selectFirstMatch(query);
            }
        });
        noteListPanel.setOnNoteDeleted(editorPanel::closeNoteTab);
        noteListPanel.setOnNoteRenamed(editorPanel::renameNote);
        noteListPanel.setOnNoteMoved(editorPanel::handleNoteMove);
        noteListPanel.setOnBeforeGroupRename(group -> editorPanel.saveNotesInGroup(group, storage.getNotesDir()));
        noteListPanel.setOnGroupRenamed((oldGroup, newGroup) -> editorPanel.groupRenamed(oldGroup, newGroup, storage.getNotesDir()));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, noteListPanel, editorPanel);
        splitPane.setDividerLocation(250);
        splitPane.setOneTouchExpandable(true);

        add(splitPane, BorderLayout.CENTER);

        setupMenuBar();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (editorPanel.confirmCloseAll()) {
                    saveState();
                    dispose();
                    System.exit(0);
                }
            }
        });

        restoreState();
    }

    public void refreshUI() {
        SwingUtilities.updateComponentTreeUI(this);
        editorPanel.reapplyFont();
    }

    private void saveState() {
        appState.setTheme(UIManager.getLookAndFeel().getClass().getName());
        appState.setOpenTabs(editorPanel.getOpenTabPaths());
        appState.setViewMode(editorPanel.getCurrentViewMode().name());
        appState.setSortMode(noteListPanel.getSortMode().name());
        appState.setWindowBounds(getX(), getY(), getWidth(), getHeight());
        appState.save();
    }

    private void restoreState() {
        String theme = appState.getTheme();
        if (!theme.isEmpty()) {
            switchTheme(theme);
        }

        List<String> tabPaths = appState.getOpenTabs();
        for (String path : tabPaths) {
            File file = new File(path);
            if (file.exists()) {
                var note = storage.loadNote(file);
                if (note != null) {
                    editorPanel.openNote(note);
                }
            }
        }

        if (!theme.isEmpty()) {
            boolean dark = theme.toLowerCase().contains("dark")
                    || theme.toLowerCase().contains("darcula");
            editorPanel.updateEditorTheme(dark);
        }

        String viewMode = appState.getViewMode();
        try {
            editorPanel.setViewMode(EditorPanel.ViewMode.valueOf(viewMode));
        } catch (IllegalArgumentException e) {
            // ignore invalid value
        }

        try {
            noteListPanel.setSortMode(NoteListPanel.SortMode.valueOf(appState.getSortMode()));
        } catch (IllegalArgumentException e) {
            // ignore invalid value
        }

    }

    private void setupMenuBar() {
        int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, shortcutMask));
        saveItem.addActionListener(e -> editorPanel.saveCurrentNote());

        JMenuItem refreshItem = new JMenuItem("Refresh");
        refreshItem.setAccelerator(KeyStroke.getKeyStroke("F5"));
        refreshItem.addActionListener(e -> noteListPanel.refreshNotes());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> {
            if (editorPanel.confirmCloseAll()) {
                saveState();
                dispose();
                System.exit(0);
            }
        });

        JMenuItem closeTabItem = new JMenuItem("Close Tab");
        closeTabItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, shortcutMask));
        closeTabItem.addActionListener(e -> editorPanel.closeCurrentTab());

        JMenuItem newNoteItem = new JMenuItem("New Note");
        newNoteItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, shortcutMask));
        newNoteItem.addActionListener(e -> noteListPanel.createNewNote());

        fileMenu.add(newNoteItem);
        fileMenu.add(saveItem);
        fileMenu.add(closeTabItem);
        fileMenu.add(refreshItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        JMenu editMenu = new JMenu("Edit");
        JMenuItem findItem = new JMenuItem("Find");
        findItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, shortcutMask));
        findItem.addActionListener(e -> editorPanel.showFind());

        JMenuItem replaceItem = new JMenuItem("Replace");
        replaceItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_H, shortcutMask));
        replaceItem.addActionListener(e -> editorPanel.showFindReplace());

        editMenu.add(findItem);
        editMenu.add(replaceItem);

        JMenu viewMenu = new JMenu("View");

        JMenuItem editorOnlyItem = new JMenuItem("Editor Only");
        editorOnlyItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_1,
                shortcutMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        editorOnlyItem.addActionListener(e -> editorPanel.setViewMode(EditorPanel.ViewMode.EDITOR));

        JMenuItem splitItem = new JMenuItem("Side by Side");
        splitItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_2,
                shortcutMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        splitItem.addActionListener(e -> editorPanel.setViewMode(EditorPanel.ViewMode.SPLIT));

        JMenuItem previewOnlyItem = new JMenuItem("Preview Only");
        previewOnlyItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_3,
                shortcutMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        previewOnlyItem.addActionListener(e -> editorPanel.setViewMode(EditorPanel.ViewMode.PREVIEW));

        JMenuItem cycleItem = new JMenuItem("Cycle View Mode");
        cycleItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P,
                shortcutMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        cycleItem.addActionListener(e -> editorPanel.cycleViewMode());

        viewMenu.add(editorOnlyItem);
        viewMenu.add(splitItem);
        viewMenu.add(previewOnlyItem);
        viewMenu.addSeparator();
        viewMenu.add(cycleItem);
        viewMenu.addSeparator();
        viewMenu.add(createThemeMenu());

        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "MarkNotes v1.0\nA simple Markdown notes editor",
                "About", JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(aboutItem);

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(viewMenu);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);
    }

    private JMenu createThemeMenu() {
        themeMenu = new JMenu("Theme");
        ButtonGroup group = new ButtonGroup();

        LinkedHashMap<String, String> themes = new LinkedHashMap<>();
        themes.put("FlatLaf Light", "com.formdev.flatlaf.FlatLightLaf");
        themes.put("FlatLaf Dark", "com.formdev.flatlaf.FlatDarkLaf");
        themes.put("FlatLaf IntelliJ", "com.formdev.flatlaf.FlatIntelliJLaf");
        themes.put("FlatLaf Darcula", "com.formdev.flatlaf.FlatDarculaLaf");

        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            themes.put(info.getName(), info.getClassName());
        }

        String currentLaf = UIManager.getLookAndFeel().getClass().getName();

        for (var entry : themes.entrySet()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(entry.getKey());
            item.setActionCommand(entry.getValue());
            item.setSelected(entry.getValue().equals(currentLaf));
            item.addActionListener(e -> switchTheme(entry.getValue()));
            group.add(item);
            themeMenu.add(item);
        }

        return themeMenu;
    }

    private void updateThemeMenuSelection(String lafClassName) {
        if (themeMenu == null) return;
        for (int i = 0; i < themeMenu.getItemCount(); i++) {
            JMenuItem item = themeMenu.getItem(i);
            if (item instanceof JRadioButtonMenuItem radio) {
                radio.setSelected(lafClassName.equals(radio.getActionCommand()));
            }
        }
    }

    private void switchTheme(String lafClassName) {
        try {
            UIManager.setLookAndFeel(lafClassName);
            SwingUtilities.updateComponentTreeUI(this);
            updateThemeMenuSelection(lafClassName);
            noteListPanel.refreshNotes();

            boolean dark = lafClassName.toLowerCase().contains("dark")
                    || lafClassName.toLowerCase().contains("darcula");
            editorPanel.updateEditorTheme(dark);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to apply theme: " + ex.getMessage(),
                    "Theme Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
