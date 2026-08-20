package com.marknotes.model;

import java.io.File;
import java.time.LocalDateTime;

public class Note {
    private String title;
    private String content;
    private File file;
    private String group;
    private LocalDateTime lastModified;

    public Note(String title, String content, File file, String group) {
        this.title = title;
        this.content = content;
        this.file = file;
        this.group = group;
        this.lastModified = LocalDateTime.now();
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public File getFile() { return file; }
    public void setFile(File file) { this.file = file; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public LocalDateTime getLastModified() { return lastModified; }
    public void setLastModified(LocalDateTime lastModified) { this.lastModified = lastModified; }

    @Override
    public String toString() {
        return title;
    }
}
