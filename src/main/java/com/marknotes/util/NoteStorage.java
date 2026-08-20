package com.marknotes.util;

import com.marknotes.model.Note;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

public class NoteStorage {
    private final Path notesDir;
    private final Map<String, Note> noteRegistry = new HashMap<>();

    public NoteStorage(Path notesDir) {
        this.notesDir = notesDir;
        try {
            Files.createDirectories(notesDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create notes directory", e);
        }
    }

    private String normalizedPath(File file) {
        return file.toPath().toAbsolutePath().normalize().toString();
    }

    public List<Note> loadAllNotes() {
        List<Note> notes = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(notesDir)) {
            paths.filter(p -> p.toString().endsWith(".md"))
                 .forEach(p -> {
                     Note note = loadNote(p.toFile());
                     if (note != null) {
                         notes.add(note);
                     }
                 });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return notes;
    }

    public Note loadNote(File file) {
        try {
            String key = normalizedPath(file);
            String raw = Files.readString(file.toPath());
            String title = extractTitle(raw);
            String group = extractGroup(file);
            String content = extractContent(raw);
            LocalDateTime lastModified = LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(file.toPath()).toInstant(),
                    ZoneId.systemDefault());

            Note existing = noteRegistry.get(key);
            if (existing != null) {
                existing.setTitle(title);
                existing.setGroup(group);
                existing.setContent(content);
                existing.setLastModified(lastModified);
                existing.setFile(file);
                return existing;
            }

            Note note = new Note(title, content, file, group);
            note.setLastModified(lastModified);
            noteRegistry.put(key, note);
            return note;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void saveNote(Note note) {
        try {
            Path filePath = note.getFile().toPath();
            Files.createDirectories(filePath.getParent());

            StringBuilder sb = new StringBuilder();
            sb.append("---\n");
            sb.append("title: ").append(note.getTitle()).append("\n");
            sb.append("---\n\n");
            sb.append(note.getContent());

            Files.writeString(filePath, sb.toString());
            note.setLastModified(LocalDateTime.now());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Note createNote(String title, String group) {
        String fileName = sanitizeFileName(title) + ".md";
        Path dir = group.isEmpty() ? notesDir : notesDir.resolve(group);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        File file = dir.resolve(fileName).toFile();

        int counter = 1;
        while (file.exists()) {
            file = dir.resolve(sanitizeFileName(title) + "_" + counter + ".md").toFile();
            counter++;
        }

        Note note = new Note(title, "", file, group);
        saveNote(note);
        noteRegistry.put(normalizedPath(file), note);
        return note;
    }

    public void createGroup(String groupName) {
        try {
            Files.createDirectories(notesDir.resolve(groupName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void renameGroup(String oldName, String newName) {
        Path oldDir = notesDir.resolve(oldName);
        Path newDir = notesDir.resolve(newName);
        try {
            List<String> oldKeys = noteRegistry.keySet().stream()
                    .filter(k -> k.startsWith(oldDir.toAbsolutePath().normalize().toString()))
                    .toList();
            Files.move(oldDir, newDir);
            for (String oldKey : oldKeys) {
                Note note = noteRegistry.remove(oldKey);
                if (note != null) {
                    Path relative = oldDir.toAbsolutePath().normalize()
                            .relativize(Path.of(oldKey));
                    Path newPath = newDir.resolve(relative);
                    note.setFile(newPath.toFile());
                    note.setGroup(newName);
                    noteRegistry.put(newPath.toAbsolutePath().normalize().toString(), note);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void moveNoteToGroup(Note note, String newGroup) {
        try {
            String oldKey = normalizedPath(note.getFile());
            Path dir = newGroup.isEmpty() ? notesDir : notesDir.resolve(newGroup);
            Files.createDirectories(dir);
            Path newPath = dir.resolve(note.getFile().toPath().getFileName());
            int counter = 1;
            while (Files.exists(newPath)) {
                String name = note.getFile().getName();
                String base = name.substring(0, name.lastIndexOf('.'));
                newPath = dir.resolve(base + "_" + counter + ".md");
                counter++;
            }
            Files.move(note.getFile().toPath(), newPath);
            noteRegistry.remove(oldKey);
            note.setFile(newPath.toFile());
            note.setGroup(newGroup);
            noteRegistry.put(normalizedPath(note.getFile()), note);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deleteNote(Note note) {
        try {
            noteRegistry.remove(normalizedPath(note.getFile()));
            Files.deleteIfExists(note.getFile().toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deleteGroup(String groupName) {
        Path groupDir = notesDir.resolve(groupName);
        try (Stream<Path> paths = Files.walk(groupDir)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                 .forEach(p -> {
                     try {
                         if (p.toString().endsWith(".md")) {
                             noteRegistry.remove(p.toAbsolutePath().normalize().toString());
                         }
                         Files.deleteIfExists(p);
                     } catch (IOException e) { e.printStackTrace(); }
                 });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<String> getGroups() {
        List<String> groups = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(notesDir)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    groups.add(path.getFileName().toString());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Collections.sort(groups);
        return groups;
    }

    public Path getNotesDir() {
        return notesDir;
    }

    private String extractTitle(String raw) {
        if (raw.startsWith("---")) {
            int end = raw.indexOf("---", 3);
            if (end > 0) {
                String frontMatter = raw.substring(3, end).trim();
                for (String line : frontMatter.split("\n")) {
                    if (line.startsWith("title:")) {
                        return line.substring(6).trim();
                    }
                }
            }
        }
        return "Untitled";
    }

    private String extractContent(String raw) {
        if (raw.startsWith("---")) {
            int end = raw.indexOf("---", 3);
            if (end > 0) {
                String after = raw.substring(end + 3);
                if (after.startsWith("\n")) after = after.substring(1);
                if (after.startsWith("\n")) after = after.substring(1);
                return after;
            }
        }
        return raw;
    }

    private String extractGroup(File file) {
        Path filePath = file.toPath().toAbsolutePath().normalize();
        Path basePath = notesDir.toAbsolutePath().normalize();
        Path relative = basePath.relativize(filePath);
        if (relative.getNameCount() > 1) {
            return relative.getParent().toString();
        }
        return "";
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9\\-_ ]", "").replaceAll("\\s+", "_").toLowerCase();
    }
}
