package com.marknotes.ui;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

public class AppIcon {

    public static List<Image> createIcons() {
        return List.of(
                createIcon(16),
                createIcon(32),
                createIcon(48),
                createIcon(64),
                createIcon(128)
        );
    }

    private static BufferedImage createIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        float s = size / 80f;
        float offset = 8 * s;
        float bgSize = 64 * s;

        // Background rounded rectangle (macOS style tighter radius)
        g2.setColor(new Color(52, 120, 198));
        g2.fill(new RoundRectangle2D.Float(offset, offset, bgSize, bgSize, 18 * s, 18 * s));

        // Note paper (centered)
        float cx = offset + bgSize / 2;
        float paperW = 34 * s;
        float paperH = 44 * s;
        float paperX = cx - paperW / 2;
        float paperY = offset + (bgSize - paperH) / 2;
        float foldSize = 8 * s;

        Path2D paper = new Path2D.Float();
        paper.moveTo(paperX, paperY);
        paper.lineTo(paperX + paperW - foldSize, paperY);
        paper.lineTo(paperX + paperW, paperY + foldSize);
        paper.lineTo(paperX + paperW, paperY + paperH);
        paper.lineTo(paperX, paperY + paperH);
        paper.closePath();
        g2.setColor(Color.WHITE);
        g2.fill(paper);

        // Folded corner
        Path2D fold = new Path2D.Float();
        fold.moveTo(paperX + paperW - foldSize, paperY);
        fold.lineTo(paperX + paperW - foldSize, paperY + foldSize);
        fold.lineTo(paperX + paperW, paperY + foldSize);
        fold.closePath();
        g2.setColor(new Color(200, 220, 240));
        g2.fill(fold);

        // Markdown "M" symbol (centered in paper)
        g2.setColor(new Color(52, 120, 198));
        g2.setStroke(new BasicStroke(3f * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        float mw = 22 * s, mh = 14 * s;
        float mx = cx - mw / 2;
        float my = paperY + paperH * 0.65f;
        float top = my - mh;

        g2.drawLine((int)(mx), (int)(my), (int)(mx), (int)(top));
        g2.drawLine((int)(mx), (int)(top), (int)(mx + mw / 2), (int)(my - mh / 3));
        g2.drawLine((int)(mx + mw / 2), (int)(my - mh / 3), (int)(mx + mw), (int)(top));
        g2.drawLine((int)(mx + mw), (int)(top), (int)(mx + mw), (int)(my));

        g2.dispose();
        return img;
    }
}
