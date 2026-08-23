package com.marknotes.ui;

import com.marknotes.model.Note;
import com.marknotes.util.NoteStorage;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EditorPanel extends JPanel {
    private final NoteStorage storage;
    private final JTabbedPane tabbedPane;
    private final List<TabInfo> tabs = new ArrayList<>();
    private final JLabel placeholderLabel;
    private boolean darkTheme = false;
    private int fontSize = 12;
    private boolean lineWrap = true;

    public EditorPanel(NoteStorage storage) {
        this.storage = storage;
        setLayout(new CardLayout());

        placeholderLabel = new JLabel("No note selected", SwingConstants.CENTER);
        placeholderLabel.setFont(placeholderLabel.getFont().deriveFont(Font.ITALIC, 16f));
        placeholderLabel.setForeground(Color.GRAY);
        add(placeholderLabel, "placeholder");

        tabbedPane = new JTabbedPane();
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        add(tabbedPane, "tabs");

        showCard("placeholder");
    }

    public void openNote(Note note) {
        String notePath = note.getFile().toPath().toAbsolutePath().normalize().toString();
        for (int i = 0; i < tabs.size(); i++) {
            String tabPath = tabs.get(i).note.getFile().toPath().toAbsolutePath().normalize().toString();
            if (tabPath.equals(notePath)) {
                tabbedPane.setSelectedIndex(i);
                showCard("tabs");
                return;
            }
        }

        RSyntaxTextArea textArea = createTextArea();
        textArea.setText(note.getContent());
        textArea.setCaretPosition(0);
        textArea.discardAllEdits();

        RTextScrollPane scrollPane = new RTextScrollPane(textArea);
        scrollPane.setLineNumbersEnabled(true);

        FindReplaceBar findReplaceBar = new FindReplaceBar(textArea);
        findReplaceBar.setVisible(false);

        PreviewPanel previewPanel = new PreviewPanel();
        previewPanel.setDark(darkTheme);
        previewPanel.setFontSize(fontSize - 2);

        JPanel editorPane = new JPanel(new BorderLayout());
        editorPane.add(scrollPane, BorderLayout.CENTER);
        editorPane.add(findReplaceBar, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorPane, previewPanel);
        splitPane.setResizeWeight(0.5);

        JPanel tabContent = new JPanel(new BorderLayout());
        tabContent.add(splitPane, BorderLayout.CENTER);
        previewPanel.setVisible(false);

        TabInfo tabInfo = new TabInfo(note, textArea, tabContent, editorPane, findReplaceBar, previewPanel, splitPane);
        tabs.add(tabInfo);

        registerFindReplaceKeys(textArea, findReplaceBar);

        tabbedPane.addTab(note.getTitle(), tabContent);
        int index = tabbedPane.indexOfComponent(tabContent);
        tabbedPane.setTabComponentAt(index, new TabHeader(tabInfo));
        tabbedPane.setSelectedIndex(index);

        Timer previewTimer = new Timer(500, e -> {
            if (tabInfo.viewMode != ViewMode.EDITOR) {
                previewPanel.updatePreview(textArea.getText());
            }
        });
        previewTimer.setRepeats(false);

        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { markModified(tabInfo); previewTimer.restart(); }
            @Override public void removeUpdate(DocumentEvent e) { markModified(tabInfo); previewTimer.restart(); }
            @Override public void changedUpdate(DocumentEvent e) { markModified(tabInfo); previewTimer.restart(); }
        });

        showCard("tabs");
    }

    public void saveCurrentNote() {
        int index = tabbedPane.getSelectedIndex();
        if (index >= 0 && index < tabs.size()) {
            saveTab(tabs.get(index));
        }
    }

    public void closeCurrentTab() {
        int index = tabbedPane.getSelectedIndex();
        if (index >= 0 && index < tabs.size()) {
            closeTab(tabs.get(index));
        }
    }

    public List<String> getOpenTabPaths() {
        return tabs.stream()
                .map(t -> t.note.getFile().getAbsolutePath())
                .toList();
    }

    public void renameNote(Note note, String newTitle) {
        String notePath = note.getFile().toPath().toAbsolutePath().normalize().toString();
        for (TabInfo tab : tabs) {
            String tabPath = tab.note.getFile().toPath().toAbsolutePath().normalize().toString();
            if (tabPath.equals(notePath)) {
                tab.note.setTitle(newTitle);
                tab.note.setContent(tab.textArea.getText());
                storage.saveNote(tab.note);
                updateTabTitle(tab);
                return;
            }
        }
        note.setTitle(newTitle);
        storage.saveNote(note);
    }

    public void handleNoteMove(String oldPath, java.io.File newFile, String newGroup) {
        String normalizedOldPath = java.nio.file.Paths.get(oldPath).toAbsolutePath().normalize().toString();
        String normalizedNewPath = newFile.toPath().toAbsolutePath().normalize().toString();
        for (TabInfo tab : tabs) {
            String tabPath = tab.note.getFile().toPath().toAbsolutePath().normalize().toString();
            if (tabPath.equals(normalizedOldPath) || tabPath.equals(normalizedNewPath)) {
                tab.note.setFile(newFile);
                tab.note.setGroup(newGroup);
                tab.note.setContent(tab.textArea.getText());
                storage.saveNote(tab.note);
                tab.modified = false;
                updateTabTitle(tab);
                return;
            }
        }
    }

    public void groupRenamed(String oldGroup, String newGroup, java.nio.file.Path notesDir) {
        java.nio.file.Path oldDir = notesDir.resolve(oldGroup).toAbsolutePath().normalize();
        java.nio.file.Path newDir = notesDir.resolve(newGroup).toAbsolutePath().normalize();
        for (TabInfo tab : tabs) {
            java.nio.file.Path tabPath = tab.note.getFile().toPath().toAbsolutePath().normalize();
            if (tabPath.startsWith(oldDir)) {
                java.nio.file.Path relative = oldDir.relativize(tabPath);
                java.nio.file.Path newPath = newDir.resolve(relative);
                tab.note.setFile(newPath.toFile());
                tab.note.setGroup(newGroup);
            }
        }
    }

    public void saveNotesInGroup(String group, java.nio.file.Path notesDir) {
        java.nio.file.Path groupDir = notesDir.resolve(group).toAbsolutePath().normalize();
        for (TabInfo tab : tabs) {
            java.nio.file.Path tabPath = tab.note.getFile().toPath().toAbsolutePath().normalize();
            if (tabPath.startsWith(groupDir) && tab.modified) {
                tab.note.setContent(tab.textArea.getText());
                storage.saveNote(tab.note);
                tab.modified = false;
                updateTabTitle(tab);
            }
        }
    }

    public void closeNoteTab(Note note) {
        String notePath = note.getFile().toPath().toAbsolutePath().normalize().toString();
        for (int i = 0; i < tabs.size(); i++) {
            String tabPath = tabs.get(i).note.getFile().toPath().toAbsolutePath().normalize().toString();
            if (tabPath.equals(notePath)) {
                int index = tabbedPane.indexOfComponent(tabs.get(i).panel);
                tabs.remove(i);
                tabbedPane.removeTabAt(index);
                if (tabs.isEmpty()) {
                    showCard("placeholder");
                }
                return;
            }
        }
    }

    public ViewMode getCurrentViewMode() {
        int index = tabbedPane.getSelectedIndex();
        if (index >= 0 && index < tabs.size()) {
            return tabs.get(index).viewMode;
        }
        return ViewMode.EDITOR;
    }

    public void selectFirstMatch(String query) {
        int index = tabbedPane.getSelectedIndex();
        if (index < 0 || index >= tabs.size()) return;
        RSyntaxTextArea textArea = tabs.get(index).textArea;
        String text = textArea.getText().toLowerCase();
        int pos = text.indexOf(query.toLowerCase());
        if (pos >= 0) {
            textArea.setCaretPosition(pos);
            textArea.moveCaretPosition(pos + query.length());
            textArea.getCaret().setSelectionVisible(true);
        }
    }

    public void showFind() {
        int index = tabbedPane.getSelectedIndex();
        if (index >= 0 && index < tabs.size()) {
            tabs.get(index).findReplaceBar.showFind();
        }
    }

    public void showFindReplace() {
        int index = tabbedPane.getSelectedIndex();
        if (index >= 0 && index < tabs.size()) {
            tabs.get(index).findReplaceBar.showFindReplace();
        }
    }

    public enum ViewMode { EDITOR, SPLIT, PREVIEW }

    public void setViewMode(ViewMode mode) {
        int index = tabbedPane.getSelectedIndex();
        if (index < 0 || index >= tabs.size()) return;

        TabInfo tabInfo = tabs.get(index);
        tabInfo.viewMode = mode;

        switch (mode) {
            case EDITOR -> {
                tabInfo.editorPane.setVisible(true);
                tabInfo.previewPanel.setVisible(false);
                tabInfo.splitPane.setDividerSize(0);
            }
            case SPLIT -> {
                tabInfo.editorPane.setVisible(true);
                tabInfo.previewPanel.setVisible(true);
                tabInfo.splitPane.setDividerSize(new JSplitPane().getDividerSize());
                tabInfo.previewPanel.updatePreview(tabInfo.textArea.getText());
                SwingUtilities.invokeLater(() -> tabInfo.splitPane.setDividerLocation(0.5));
            }
            case PREVIEW -> {
                tabInfo.editorPane.setVisible(false);
                tabInfo.previewPanel.setVisible(true);
                tabInfo.splitPane.setDividerSize(0);
                tabInfo.previewPanel.updatePreview(tabInfo.textArea.getText());
            }
        }

        tabInfo.splitPane.revalidate();
        tabInfo.splitPane.repaint();
    }

    public void cycleViewMode() {
        int index = tabbedPane.getSelectedIndex();
        if (index < 0 || index >= tabs.size()) return;

        TabInfo tabInfo = tabs.get(index);
        ViewMode next = switch (tabInfo.viewMode) {
            case EDITOR -> ViewMode.SPLIT;
            case SPLIT -> ViewMode.PREVIEW;
            case PREVIEW -> ViewMode.EDITOR;
        };
        setViewMode(next);
    }

    public void updateEditorTheme(boolean dark) {
        this.darkTheme = dark;
        String themePath = dark
                ? "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
                : "/org/fife/ui/rsyntaxtextarea/themes/default.xml";
        try {
            Theme theme = Theme.load(getClass().getResourceAsStream(themePath));
            for (TabInfo tab : tabs) {
                theme.apply(tab.textArea);
                applyEditorFont(tab.textArea);
                tab.previewPanel.setDark(dark);
            }
        } catch (IOException e) {
            // ignore
        }
    }

    public boolean hasUnsavedChanges() {
        return tabs.stream().anyMatch(t -> t.modified);
    }

    public boolean confirmCloseAll() {
        List<TabInfo> unsaved = tabs.stream().filter(t -> t.modified).toList();
        if (unsaved.isEmpty()) return true;

        StringBuilder msg = new StringBuilder("The following notes have unsaved changes:\n\n");
        for (TabInfo t : unsaved) {
            msg.append("  - ").append(t.note.getTitle()).append("\n");
        }
        msg.append("\nSave before closing?");

        int result = JOptionPane.showConfirmDialog(this, msg.toString(),
                "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            for (TabInfo t : unsaved) {
                saveTab(t);
            }
            return true;
        }
        return result == JOptionPane.NO_OPTION;
    }

    private void saveTab(TabInfo tabInfo) {
        if (!tabInfo.modified) return;
        tabInfo.note.setContent(tabInfo.textArea.getText());
        storage.saveNote(tabInfo.note);
        tabInfo.modified = false;
        updateTabTitle(tabInfo);
    }

    private void closeTab(TabInfo tabInfo) {
        if (tabInfo.modified) {
            int result = JOptionPane.showConfirmDialog(this,
                    "\"" + tabInfo.note.getTitle() + "\" has unsaved changes.\nSave before closing?",
                    "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

            if (result != JOptionPane.YES_OPTION && result != JOptionPane.NO_OPTION) return;
            if (result == JOptionPane.YES_OPTION) {
                saveTab(tabInfo);
            }
        }

        int index = tabbedPane.indexOfComponent(tabInfo.panel);
        tabs.remove(tabInfo);
        tabbedPane.removeTabAt(index);

        if (tabs.isEmpty()) {
            showCard("placeholder");
        }
    }

    private void markModified(TabInfo tabInfo) {
        if (!tabInfo.modified) {
            tabInfo.modified = true;
            updateTabTitle(tabInfo);
        }
    }

    private void updateTabTitle(TabInfo tabInfo) {
        int index = tabs.indexOf(tabInfo);
        if (index < 0) return;
        Component comp = tabbedPane.getTabComponentAt(index);
        if (comp instanceof TabHeader header) {
            header.updateTitle();
        }
    }

    private RSyntaxTextArea createTextArea() {
        RSyntaxTextArea textArea = new RSyntaxTextArea();
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
        textArea.setCodeFoldingEnabled(true);
        textArea.setAntiAliasingEnabled(true);
        textArea.setTabSize(4);
        textArea.setLineWrap(lineWrap);
        textArea.setWrapStyleWord(true);

        Action plainCopyAction = new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                String selected = textArea.getSelectedText();
                if (selected != null) {
                    java.awt.datatransfer.Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clip.setContents(new java.awt.datatransfer.StringSelection(selected), null);
                }
            }
        };
        Action plainCutAction = new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                String selected = textArea.getSelectedText();
                if (selected != null) {
                    java.awt.datatransfer.Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clip.setContents(new java.awt.datatransfer.StringSelection(selected), null);
                    textArea.replaceSelection("");
                }
            }
        };
        textArea.getActionMap().put("copy", plainCopyAction);
        textArea.getActionMap().put("cut", plainCutAction);
        textArea.getActionMap().put(javax.swing.text.DefaultEditorKit.copyAction, plainCopyAction);
        textArea.getActionMap().put(javax.swing.text.DefaultEditorKit.cutAction, plainCutAction);
        textArea.getActionMap().put("copy-to-clipboard", plainCopyAction);
        textArea.getActionMap().put("cut-to-clipboard", plainCutAction);

        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        InputMap im = textArea.getInputMap(JComponent.WHEN_FOCUSED);
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, shortcut), "plain-copy");
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, shortcut), "plain-cut");
        textArea.getActionMap().put("plain-copy", plainCopyAction);
        textArea.getActionMap().put("plain-cut", plainCutAction);

        String themePath = darkTheme
                ? "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
                : "/org/fife/ui/rsyntaxtextarea/themes/default.xml";
        try {
            Theme theme = Theme.load(getClass().getResourceAsStream(themePath));
            theme.apply(textArea);
        } catch (IOException e) {
            // Use default theme
        }

        applyEditorFont(textArea);

        return textArea;
    }

    public void setFontSize(int size) {
        this.fontSize = size;
        for (TabInfo tab : tabs) {
            applyEditorFont(tab.textArea);
            tab.previewPanel.setFontSize(size - 2);
        }
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setLineWrap(boolean wrap) {
        this.lineWrap = wrap;
        for (TabInfo tab : tabs) {
            tab.textArea.setLineWrap(wrap);
        }
    }

    public boolean getLineWrap() {
        return lineWrap;
    }

    public void reapplyFont() {
        for (TabInfo tab : tabs) {
            applyEditorFont(tab.textArea);
        }
    }

    private void applyEditorFont(RSyntaxTextArea textArea) {
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, fontSize));
    }

    private void registerFindReplaceKeys(RSyntaxTextArea textArea, FindReplaceBar bar) {
        int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        InputMap im = textArea.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = textArea.getActionMap();

        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, shortcutMask), "showFind");
        am.put("showFind", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) { bar.showFind(); }
        });

        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_H, shortcutMask), "showReplace");
        am.put("showReplace", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) { bar.showFindReplace(); }
        });
    }

    private void showCard(String name) {
        ((CardLayout) getLayout()).show(this, name);
    }

    private static class TabInfo {
        final Note note;
        final RSyntaxTextArea textArea;
        final JPanel panel;
        final JPanel editorPane;
        final FindReplaceBar findReplaceBar;
        final PreviewPanel previewPanel;
        final JSplitPane splitPane;
        boolean modified = false;
        ViewMode viewMode = ViewMode.EDITOR;

        TabInfo(Note note, RSyntaxTextArea textArea, JPanel panel, JPanel editorPane,
                FindReplaceBar findReplaceBar, PreviewPanel previewPanel, JSplitPane splitPane) {
            this.note = note;
            this.textArea = textArea;
            this.panel = panel;
            this.editorPane = editorPane;
            this.findReplaceBar = findReplaceBar;
            this.previewPanel = previewPanel;
            this.splitPane = splitPane;
        }
    }

    private class TabHeader extends JPanel {
        private final TabInfo tabInfo;
        private final JLabel label;

        TabHeader(TabInfo tabInfo) {
            this.tabInfo = tabInfo;
            setLayout(new BorderLayout(4, 0));
            setOpaque(false);

            label = new JLabel(tabInfo.note.getTitle());
            add(label, BorderLayout.CENTER);

            JLabel closeBtn = new JLabel("×") {
                private boolean hover = false;

                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(16, 16);
                }

                @Override
                protected void paintComponent(Graphics g) {
                    if (hover) {
                        g.setColor(new Color(180, 180, 180));
                        g.fillOval(0, 0, getWidth(), getHeight());
                    }
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Color.DARK_GRAY);
                    g2.setStroke(new BasicStroke(1.2f));
                    int cx = getWidth() / 2;
                    int cy = getHeight() / 2;
                    int half = 4;
                    g2.drawLine(cx - half, cy - half, cx + half, cy + half);
                    g2.drawLine(cx + half, cy - half, cx - half, cy + half);
                }

                {
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                        @Override
                        public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                        @Override
                        public void mouseClicked(MouseEvent e) { closeTab(tabInfo); }
                    });
                }
            };
            add(closeBtn, BorderLayout.EAST);
        }

        void updateTitle() {
            String title = tabInfo.note.getTitle();
            label.setText(tabInfo.modified ? title + " *" : title);
        }
    }
}
