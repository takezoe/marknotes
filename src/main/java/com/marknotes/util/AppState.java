package com.marknotes.util;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class AppState {
    private final Path stateFile;
    private final Properties properties = new Properties();

    public AppState(Path notesDir) {
        this.stateFile = notesDir.resolve(".marknotes");
        load();
    }

    private void load() {
        if (Files.exists(stateFile)) {
            try (Reader reader = Files.newBufferedReader(stateFile)) {
                properties.load(reader);
            } catch (IOException e) {
                // start fresh
            }
        }
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(stateFile)) {
            properties.store(writer, "MarkNotes application state");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getTheme() {
        return properties.getProperty("theme", "");
    }

    public void setTheme(String lafClassName) {
        properties.setProperty("theme", lafClassName);
    }

    public List<String> getOpenTabs() {
        String value = properties.getProperty("openTabs", "");
        if (value.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(value.split("\\|")));
    }

    public void setOpenTabs(List<String> paths) {
        properties.setProperty("openTabs", String.join("|", paths));
    }

    public String getViewMode() {
        return properties.getProperty("viewMode", "EDITOR");
    }

    public void setViewMode(String mode) {
        properties.setProperty("viewMode", mode);
    }

    public int getWindowX() {
        return Integer.parseInt(properties.getProperty("windowX", "-1"));
    }

    public int getWindowY() {
        return Integer.parseInt(properties.getProperty("windowY", "-1"));
    }

    public int getWindowWidth() {
        return Integer.parseInt(properties.getProperty("windowWidth", "1100"));
    }

    public int getWindowHeight() {
        return Integer.parseInt(properties.getProperty("windowHeight", "700"));
    }

    public void setWindowBounds(int x, int y, int width, int height) {
        properties.setProperty("windowX", String.valueOf(x));
        properties.setProperty("windowY", String.valueOf(y));
        properties.setProperty("windowWidth", String.valueOf(width));
        properties.setProperty("windowHeight", String.valueOf(height));
    }

    public String getSortMode() {
        return properties.getProperty("sortMode", "TITLE");
    }

    public void setSortMode(String sortMode) {
        properties.setProperty("sortMode", sortMode);
    }

    public int getFontSize() {
        return Integer.parseInt(properties.getProperty("fontSize", "12"));
    }

    public void setFontSize(int size) {
        properties.setProperty("fontSize", String.valueOf(size));
    }
}
