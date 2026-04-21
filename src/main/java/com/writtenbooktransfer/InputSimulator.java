package com.writtenbooktransfer;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;

public class InputSimulator {

    private final Robot robot;
    private final int offsetX;
    private final int offsetY;
    private final int delayMs;

    public InputSimulator(int offsetX, int offsetY, int delayMs) throws AWTException {
        this.robot = new Robot();
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.delayMs = delayMs;
        robot.setAutoDelay(20);
    }

    public void moveAndClick(int x, int y) {
        robot.mouseMove(x, y);
        robot.mousePress(KeyEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(KeyEvent.BUTTON1_DOWN_MASK);
        sleep(delayMs);
    }

    public void clickFocusArea(Point originalPos) {
        int focusX = originalPos.x + offsetX;
        int focusY = originalPos.y + offsetY;
        moveAndClick(focusX, focusY);
    }

    public void clickNextPageButton(Point originalPos) {
        moveAndClick(originalPos.x, originalPos.y);
    }

    public void pasteText(String text) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(text), null);
        sleep(1000);
        robot.keyPress(KeyEvent.VK_CONTROL);
        sleep(10);
        robot.keyPress(KeyEvent.VK_V);
        sleep(10);
        robot.keyRelease(KeyEvent.VK_V);
        sleep(10);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        sleep(500);
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public Point getCurrentMousePosition() {
        return MouseInfo.getPointerInfo().getLocation();
    }
}