package com.marknotes.ui;

import com.marknotes.model.Note;
import com.marknotes.util.NoteStorage;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

public class NoteListPanel extends JPanel {
    private final NoteStorage storage;
    private final JTree noteTree;
    private final DefaultMutableTreeNode rootNode;
    private final DefaultTreeModel treeModel;
    private final NoteTreeCellRenderer cellRenderer;
    private final JTextField searchField;
    private java.util.function.BiConsumer<Note, String> onNoteSelected;
    private Consumer<Note> onNoteDeleted;
    private java.util.function.BiConsumer<Note, String> onNoteRenamed;
    private NoteMoveListener onNoteMoved;
    private java.util.function.BiConsumer<String, String> onGroupRenamed;
    private Consumer<String> onBeforeGroupRename;
    private List<Note> allNotes;

    public enum SortMode { TITLE, LAST_MODIFIED }
    private SortMode sortMode = SortMode.TITLE;

    public SortMode getSortMode() { return sortMode; }

    public void setSortMode(SortMode mode) {
        this.sortMode = mode;
        refreshNotes();
    }

    @FunctionalInterface
    public interface NoteMoveListener {
        void onNoteMoved(String oldPath, java.io.File newFile, String newGroup);
    }

    public NoteListPanel(NoteStorage storage) {
        this.storage = storage;
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(250, 0));

        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search notes...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterNotes(); }
            @Override public void removeUpdate(DocumentEvent e) { filterNotes(); }
            @Override public void changedUpdate(DocumentEvent e) { filterNotes(); }
        });

        JLabel searchIcon = new JLabel() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(20, 20);
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.GRAY);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(4, 4, 9, 9);
                g2.drawLine(12, 12, 16, 16);
            }
        };

        JPanel searchPanel = new JPanel(new BorderLayout(4, 0));
        searchPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        searchPanel.add(searchIcon, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        add(searchPanel, BorderLayout.NORTH);

        rootNode = new DefaultMutableTreeNode("Notes");
        treeModel = new DefaultTreeModel(rootNode);
        noteTree = new JTree(treeModel);
        noteTree.setRootVisible(false);
        noteTree.setShowsRootHandles(true);
        noteTree.setRowHeight(0);
        cellRenderer = new NoteTreeCellRenderer();
        noteTree.setCellRenderer(cellRenderer);
        noteTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    TreePath path = noteTree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        if (node.getUserObject() instanceof Note note && onNoteSelected != null) {
                            onNoteSelected.accept(note, searchField.getText().trim());
                        }
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) { showPopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { showPopup(e); }
        });

        noteTree.setDragEnabled(true);
        noteTree.setDropMode(DropMode.ON);
        noteTree.setTransferHandler(new NoteTransferHandler());

        JScrollPane scrollPane = new JScrollPane(noteTree);
        add(scrollPane, BorderLayout.CENTER);

        refreshNotes();
    }

    public void setOnNoteSelected(java.util.function.BiConsumer<Note, String> listener) {
        this.onNoteSelected = listener;
    }

    public void setOnNoteDeleted(Consumer<Note> listener) {
        this.onNoteDeleted = listener;
    }

    public void setOnNoteRenamed(java.util.function.BiConsumer<Note, String> listener) {
        this.onNoteRenamed = listener;
    }

    public void setOnNoteMoved(NoteMoveListener listener) {
        this.onNoteMoved = listener;
    }

    public void setOnGroupRenamed(java.util.function.BiConsumer<String, String> listener) {
        this.onGroupRenamed = listener;
    }

    public void setOnBeforeGroupRename(Consumer<String> listener) {
        this.onBeforeGroupRename = listener;
    }

    public void refreshNotes() {
        allNotes = storage.loadAllNotes();
        buildTree(allNotes);
    }

    private void buildTree(List<Note> notes) {
        rootNode.removeAllChildren();

        java.util.Comparator<Note> comparator = sortMode == SortMode.LAST_MODIFIED
                ? java.util.Comparator.comparing(Note::getLastModified, java.util.Comparator.reverseOrder())
                : java.util.Comparator.comparing(n -> n.getTitle().toLowerCase());

        List<Note> sorted = notes.stream().sorted(comparator).toList();

        DefaultMutableTreeNode ungrouped = new DefaultMutableTreeNode("Ungrouped");
        java.util.Map<String, DefaultMutableTreeNode> groupNodes = new java.util.TreeMap<>();

        for (String group : storage.getGroups()) {
            groupNodes.put(group, new DefaultMutableTreeNode(group));
        }

        for (Note note : sorted) {
            String group = note.getGroup();
            if (group == null || group.isEmpty()) {
                ungrouped.add(new DefaultMutableTreeNode(note));
            } else {
                groupNodes.computeIfAbsent(group, g -> new DefaultMutableTreeNode(g))
                        .add(new DefaultMutableTreeNode(note));
            }
        }

        rootNode.add(ungrouped);

        for (DefaultMutableTreeNode groupNode : groupNodes.values()) {
            rootNode.add(groupNode);
        }

        treeModel.reload();
        expandAllNodes();
    }

    private void expandAllNodes() {
        for (int i = 0; i < noteTree.getRowCount(); i++) {
            noteTree.expandRow(i);
        }
    }

    private void filterNotes() {
        String query = searchField.getText().trim().toLowerCase();
        cellRenderer.setHighlightQuery(query);
        if (query.isEmpty()) {
            buildTree(allNotes);
            return;
        }
        List<Note> filtered = allNotes.stream()
                .filter(n -> n.getTitle().toLowerCase().contains(query)
                        || n.getContent().toLowerCase().contains(query))
                .toList();
        buildTree(filtered);
    }

    public void createNewNote() {
        String title = JOptionPane.showInputDialog(this, "Note title:", "New Note", JOptionPane.PLAIN_MESSAGE);
        if (title == null || title.trim().isEmpty()) return;

        String[] groups = getGroupOptions();
        String group = "";
        if (groups.length > 0) {
            Object selected = JOptionPane.showInputDialog(this, "Select group:",
                    "Group", JOptionPane.PLAIN_MESSAGE, null, appendNone(groups), "(None)");
            if (selected != null && !"(None)".equals(selected)) {
                group = selected.toString();
            }
        }

        Note note = storage.createNote(title.trim(), group);
        refreshNotes();
        if (onNoteSelected != null) {
            onNoteSelected.accept(note, "");
        }
    }

    private void createNewNoteInGroup(String group) {
        String title = JOptionPane.showInputDialog(this, "Note title:", "New Note", JOptionPane.PLAIN_MESSAGE);
        if (title == null || title.trim().isEmpty()) return;

        Note note = storage.createNote(title.trim(), group);
        refreshNotes();
        if (onNoteSelected != null) {
            onNoteSelected.accept(note, "");
        }
    }

    private void createNewGroup() {
        String name = JOptionPane.showInputDialog(this, "Group name:", "New Group", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        storage.createGroup(name.trim());
        refreshNotes();
    }

    private void renameGroup(String oldName) {
        String newName = JOptionPane.showInputDialog(this, "New group name:", oldName);
        if (newName == null || newName.trim().isEmpty() || newName.trim().equals(oldName)) return;
        if (onBeforeGroupRename != null) {
            onBeforeGroupRename.accept(oldName);
        }
        storage.renameGroup(oldName, newName.trim());
        if (onGroupRenamed != null) {
            onGroupRenamed.accept(oldName, newName.trim());
        }
        refreshNotes();
    }

    private void deleteGroup(String groupName, DefaultMutableTreeNode node) {
        int noteCount = node.getChildCount();
        String message = noteCount > 0
                ? "Delete group \"" + groupName + "\" and its " + noteCount + " note(s)?"
                : "Delete group \"" + groupName + "\"?";
        int result = JOptionPane.showConfirmDialog(this, message, "Confirm", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            for (int i = 0; i < node.getChildCount(); i++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
                if (child.getUserObject() instanceof Note note) {
                    if (onNoteDeleted != null) {
                        onNoteDeleted.accept(note);
                    }
                }
            }
            storage.deleteGroup(groupName);
            refreshNotes();
        }
    }

    private String[] getGroupOptions() {
        return storage.getGroups().toArray(new String[0]);
    }

    private Object[] appendNone(String[] groups) {
        Object[] result = new Object[groups.length + 1];
        result[0] = "(None)";
        System.arraycopy(groups, 0, result, 1, groups.length);
        return result;
    }

    private void showPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;

        TreePath path = noteTree.getPathForLocation(e.getX(), e.getY());
        JPopupMenu popup = new JPopupMenu();

        if (path != null) {
            noteTree.setSelectionPath(path);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();

            if (node.getUserObject() instanceof Note note) {
                JMenuItem renameItem = new JMenuItem("Rename");
                renameItem.addActionListener(ev -> {
                    String newTitle = JOptionPane.showInputDialog(this, "New title:", note.getTitle());
                    if (newTitle != null && !newTitle.trim().isEmpty()) {
                        if (onNoteRenamed != null) {
                            onNoteRenamed.accept(note, newTitle.trim());
                        } else {
                            note.setTitle(newTitle.trim());
                            storage.saveNote(note);
                        }
                        refreshNotes();
                    }
                });
                JMenuItem deleteItem = new JMenuItem("Delete");
                deleteItem.addActionListener(ev -> {
                    int result = JOptionPane.showConfirmDialog(this,
                            "Delete \"" + note.getTitle() + "\"?", "Confirm", JOptionPane.YES_NO_OPTION);
                    if (result == JOptionPane.YES_OPTION) {
                        storage.deleteNote(note);
                        if (onNoteDeleted != null) {
                            onNoteDeleted.accept(note);
                        }
                        refreshNotes();
                    }
                });
                popup.add(renameItem);
                popup.add(deleteItem);
                popup.addSeparator();
            } else if (node.getUserObject() instanceof String groupName && !"Ungrouped".equals(groupName)) {
                JMenuItem newNoteInGroup = new JMenuItem("New Note in \"" + groupName + "\"");
                newNoteInGroup.addActionListener(ev -> createNewNoteInGroup(groupName));
                popup.add(newNoteInGroup);

                JMenuItem renameGroupItem = new JMenuItem("Rename Group");
                renameGroupItem.addActionListener(ev -> renameGroup(groupName));
                popup.add(renameGroupItem);

                JMenuItem deleteGroupItem = new JMenuItem("Delete Group");
                deleteGroupItem.addActionListener(ev -> deleteGroup(groupName, node));
                popup.add(deleteGroupItem);
                popup.addSeparator();
            }
        }

        JMenuItem newNoteItem = new JMenuItem("New Note");
        newNoteItem.addActionListener(ev -> createNewNote());
        popup.add(newNoteItem);

        JMenuItem newGroupItem = new JMenuItem("New Group");
        newGroupItem.addActionListener(ev -> createNewGroup());
        popup.add(newGroupItem);

        popup.addSeparator();
        JMenu sortMenu = new JMenu("Sort by");
        ButtonGroup sortGroup = new ButtonGroup();

        JRadioButtonMenuItem sortByTitle = new JRadioButtonMenuItem("Title");
        sortByTitle.setSelected(sortMode == SortMode.TITLE);
        sortByTitle.addActionListener(ev -> { sortMode = SortMode.TITLE; refreshNotes(); });
        sortGroup.add(sortByTitle);
        sortMenu.add(sortByTitle);

        JRadioButtonMenuItem sortByDate = new JRadioButtonMenuItem("Last Modified");
        sortByDate.setSelected(sortMode == SortMode.LAST_MODIFIED);
        sortByDate.addActionListener(ev -> { sortMode = SortMode.LAST_MODIFIED; refreshNotes(); });
        sortGroup.add(sortByDate);
        sortMenu.add(sortByDate);

        popup.add(sortMenu);

        popup.show(noteTree, e.getX(), e.getY());
    }

    private static class NoteTreeCellRenderer extends DefaultTreeCellRenderer {
        private static final int SNIPPET_CONTEXT = 20;
        private String highlightQuery = "";

        void setHighlightQuery(String query) {
            this.highlightQuery = query == null ? "" : query.trim().toLowerCase();
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            if (node.getUserObject() instanceof Note note) {
                if (highlightQuery.isEmpty()) {
                    setText(note.getTitle());
                } else {
                    setText(buildHighlightedLabel(note));
                }
                setIcon(UIManager.getIcon("FileView.fileIcon"));
            } else if (!node.isRoot() && node.getUserObject() instanceof String) {
                setIcon(UIManager.getIcon("FileView.directoryIcon"));
            }
            return this;
        }

        private String buildHighlightedLabel(Note note) {
            String title = note.getTitle();
            int titleIdx = title.toLowerCase().indexOf(highlightQuery);

            if (titleIdx >= 0) {
                return highlightText(title, titleIdx);
            }

            String content = note.getContent();
            int contentIdx = content.toLowerCase().indexOf(highlightQuery);
            if (contentIdx >= 0) {
                String snippet = buildSnippet(content, contentIdx);
                return "<html>" + escapeHtml(title)
                        + "<br><span style='color:gray;'>" + snippet + "</span></html>";
            }

            return title;
        }

        private String buildSnippet(String content, int matchIdx) {
            int start = Math.max(0, matchIdx - SNIPPET_CONTEXT);
            int end = Math.min(content.length(), matchIdx + highlightQuery.length() + SNIPPET_CONTEXT);

            String raw = content.substring(start, end).replace('\n', ' ').replace('\r', ' ');
            int localIdx = matchIdx - start;

            String before = escapeHtml(raw.substring(0, localIdx));
            String match = escapeHtml(raw.substring(localIdx, localIdx + highlightQuery.length()));
            String after = escapeHtml(raw.substring(localIdx + highlightQuery.length()));

            String prefix = start > 0 ? "..." : "";
            String suffix = end < content.length() ? "..." : "";

            return prefix + before + "<b style='background-color:#FFDD57; color:#333;'>" + match + "</b>" + after + suffix;
        }

        private String highlightText(String text, int idx) {
            String before = escapeHtml(text.substring(0, idx));
            String match = escapeHtml(text.substring(idx, idx + highlightQuery.length()));
            String after = escapeHtml(text.substring(idx + highlightQuery.length()));
            return "<html>" + before + "<b style='background-color:#FFDD57; color:#333;'>" + match + "</b>" + after + "</html>";
        }

        private static String escapeHtml(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    private class NoteTransferHandler extends TransferHandler {
        private final DataFlavor noteFlavor = new DataFlavor(Note.class, "Note");

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            JTree tree = (JTree) c;
            TreePath path = tree.getSelectionPath();
            if (path == null) return null;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (node.getUserObject() instanceof Note note) {
                return new Transferable() {
                    @Override
                    public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{noteFlavor}; }
                    @Override
                    public boolean isDataFlavorSupported(DataFlavor flavor) { return noteFlavor.equals(flavor); }
                    @Override
                    public Object getTransferData(DataFlavor flavor) { return note; }
                };
            }
            return null;
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop() || !support.isDataFlavorSupported(noteFlavor)) return false;
            JTree.DropLocation dropLocation = (JTree.DropLocation) support.getDropLocation();
            TreePath path = dropLocation.getPath();
            if (path == null) return false;
            DefaultMutableTreeNode target = (DefaultMutableTreeNode) path.getLastPathComponent();
            return target.getUserObject() instanceof String && !target.isRoot();
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                Note note = (Note) support.getTransferable().getTransferData(noteFlavor);
                JTree.DropLocation dropLocation = (JTree.DropLocation) support.getDropLocation();
                DefaultMutableTreeNode target = (DefaultMutableTreeNode) dropLocation.getPath().getLastPathComponent();
                String targetGroup = (String) target.getUserObject();
                String newGroup = "Ungrouped".equals(targetGroup) ? "" : targetGroup;

                if (newGroup.equals(note.getGroup())) return false;

                String oldPath = note.getFile().getAbsolutePath();
                storage.moveNoteToGroup(note, newGroup);
                if (onNoteMoved != null) {
                    onNoteMoved.onNoteMoved(oldPath, note.getFile(), newGroup);
                }
                refreshNotes();
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
    }
}
