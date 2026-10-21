package com.railease.desktop;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/** A small, low-cost animated railway landscape for the sign-in screen. */
final class TrainScene extends JPanel {
    private static final int SCENE_W = 420;
    private static final int SCENE_H = 190;
    private static final int TRAIN_W = 190;
    private static final int TRAIN_H = 35;
    private final Timer timer;
    private long startedAt;

    TrainScene() {
        setOpaque(false);
        timer = new Timer(33, event -> repaint());
        timer.setCoalesce(true);
    }

    @Override public void addNotify() {
        super.addNotify();
        startedAt = System.nanoTime();
        if (!Boolean.getBoolean("railease.reduceMotion")) timer.start();
    }

    @Override public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double scale = Math.min((double) getWidth() / SCENE_W, (double) getHeight() / SCENE_H);
        double ox = (getWidth() - SCENE_W * scale) / 2;
        double oy = (getHeight() - SCENE_H * scale) / 2;
        g.translate(ox, oy); g.scale(scale, scale);
        paintLandscape(g);
        paintTrack(g);
        paintTrain(g);
        g.dispose();
    }

    private void paintLandscape(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(40, 76, 128), 0, SCENE_H, new Color(151, 192, 208)));
        g.fillRoundRect(0, 0, SCENE_W, SCENE_H, 20, 20);

        // Low sun and soft cloud bands give the moving train some depth.
        g.setColor(new Color(255, 224, 158, 190));
        g.fill(new Ellipse2D.Double(322, 20, 43, 43));
        g.setColor(new Color(230, 244, 243, 145));
        cloud(g, 52, 30, .72); cloud(g, 195, 48, .48);

        Path2D farHills = new Path2D.Double();
        farHills.moveTo(0, 116); farHills.curveTo(48, 65, 92, 100, 145, 68);
        farHills.curveTo(202, 36, 247, 109, 302, 74); farHills.curveTo(354, 47, 385, 81, 420, 60);
        farHills.lineTo(420, 145); farHills.lineTo(0, 145); farHills.closePath();
        g.setColor(new Color(88, 135, 149)); g.fill(farHills);

        Path2D nearHills = new Path2D.Double();
        nearHills.moveTo(0, 128); nearHills.curveTo(62, 93, 105, 132, 168, 98);
        nearHills.curveTo(224, 71, 292, 130, 342, 99); nearHills.curveTo(374, 84, 395, 105, 420, 92);
        nearHills.lineTo(420, 155); nearHills.lineTo(0, 155); nearHills.closePath();
        g.setColor(new Color(65, 117, 112)); g.fill(nearHills);

        // Passing hedgerows and trees create the parallax feel of a moving rail view.
        double phase = phase();
        for (int i = 0; i < 12; i++) {
            double x = ((i * 48 - phase * 38) % 490 + 490) % 490 - 35;
            int trunk = 12 + (i % 3) * 3;
            g.setColor(new Color(36, 78, 74, 225));
            g.fillRoundRect((int) x, 116 - trunk, 5, trunk + 28, 3, 3);
            g.setColor(i % 2 == 0 ? new Color(49, 100, 84, 235) : new Color(68, 122, 91, 235));
            g.fillOval((int) x - 9, 102 - trunk, 24, 25);
            g.fillOval((int) x - 5, 91 - trunk, 18, 23);
        }
    }

    private void paintTrack(Graphics2D g) {
        // Gravel bed, sleeper rhythm, and twin rails.
        Path2D bed = new Path2D.Double();
        bed.moveTo(0, 157); bed.lineTo(420, 157); bed.lineTo(420, 190); bed.lineTo(0, 190); bed.closePath();
        g.setColor(new Color(47, 57, 68)); g.fill(bed);
        double phase = phase();
        for (int i = 0; i < 19; i++) {
            double x = ((i * 28 - phase * 58) % 532 + 532) % 532 - 55;
            g.setColor(new Color(143, 123, 96));
            g.setStroke(new BasicStroke(4)); g.drawLine((int) x, 159, (int) (x + 8), 189);
        }
        g.setColor(new Color(204, 215, 218)); g.setStroke(new BasicStroke(3));
        g.drawLine(0, 164, 420, 164); g.drawLine(0, 183, 420, 183);
        g.setColor(new Color(255, 255, 255, 70)); g.setStroke(new BasicStroke(1));
        g.drawLine(0, 162, 420, 162); g.drawLine(0, 181, 420, 181);
    }

    private void paintTrain(Graphics2D g) {
        double x = -TRAIN_W + phase() * (SCENE_W + TRAIN_W);
        double y = 121;
        g.setColor(new Color(10, 25, 43, 65)); g.fillRoundRect((int) x + 4, (int) y + 29, TRAIN_W, 12, 10, 10);

        // Three clean blue coaches, then a curved-nose cab.
        for (int i = 0; i < 3; i++) {
            int coachX = (int) x + i * 48;
            g.setColor(new Color(22, 54, 91)); g.fillRoundRect(coachX, (int) y + 2, 49, 26, 6, 6);
            g.setColor(new Color(230, 239, 238)); g.fillRoundRect(coachX + 7, (int) y + 7, 9, 8, 2, 2);
            g.fillRoundRect(coachX + 21, (int) y + 7, 9, 8, 2, 2);
            g.fillRoundRect(coachX + 35, (int) y + 7, 8, 8, 2, 2);
            g.setColor(new Color(35, 112, 170)); g.fillRect(coachX + 2, (int) y + 19, 45, 4);
            g.setColor(new Color(28, 36, 45)); g.fillOval(coachX + 8, (int) y + 24, 9, 9); g.fillOval(coachX + 34, (int) y + 24, 9, 9);
            g.setColor(new Color(157, 175, 184)); g.fillOval(coachX + 11, (int) y + 27, 3, 3); g.fillOval(coachX + 37, (int) y + 27, 3, 3);
        }
        int cab = (int) x + 144;
        g.setColor(new Color(18, 48, 78));
        Path2D nose = new Path2D.Double(); nose.moveTo(cab, y + 2); nose.lineTo(cab + 33, y + 2); nose.quadTo(cab + 45, y + 3, cab + 46, y + 14); nose.lineTo(cab + 46, y + 28); nose.lineTo(cab, y + 28); nose.closePath(); g.fill(nose);
        g.setColor(new Color(225, 239, 236)); g.fillRoundRect(cab + 24, (int) y + 7, 14, 9, 3, 3);
        g.setColor(new Color(255, 122, 48)); g.fillRoundRect(cab + 2, (int) y + 20, 43, 4, 2, 2);
        g.setColor(new Color(255, 230, 161)); g.fillOval(cab + 42, (int) y + 16, 5, 5);
        g.setColor(new Color(28, 36, 45)); g.fillOval(cab + 8, (int) y + 24, 9, 9); g.fillOval(cab + 34, (int) y + 24, 9, 9);
    }

    private void cloud(Graphics2D g, int x, int y, double scale) {
        Graphics2D cloud = (Graphics2D) g.create(); cloud.translate(x, y); cloud.scale(scale, scale);
        cloud.fillOval(0, 6, 31, 13); cloud.fillOval(8, 0, 19, 20); cloud.fillOval(20, 5, 27, 14); cloud.dispose();
    }

    private double phase() {
        long elapsed = Math.max(0, System.nanoTime() - startedAt);
        return (elapsed / 1_000_000_000.0 % 7.2) / 7.2;
    }
}
