package com.writtenbooktransfer;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GlobalKeyListener implements NativeKeyListener {

    private static volatile boolean escapePressed = false;
    private static volatile boolean ctrlPressed = false;   // 改为静态

    private CountDownLatch latch;

    public static void register() {
        forceCleanup();
        try {
            Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            logger.setLevel(Level.OFF);
            GlobalScreen.registerNativeHook();
        } catch (Exception e) {
            throw new RuntimeException("无法注册全局钩子，请确认以管理员/root权限运行。", e);
        }
    }

    public static void unregister() {
        try {
            GlobalScreen.unregisterNativeHook();
        } catch (Exception e) {
            // 忽略卸载异常
        }
        escapePressed = false;
        ctrlPressed = false;
    }

    public static void forceCleanup() {
        unregister();
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {}
        // 可选的反射清理监听器列表（若需要）
    }

    public void addToGlobalScreen() {
        GlobalScreen.addNativeKeyListener(this);
    }

    public void removeFromGlobalScreen() {
        GlobalScreen.removeNativeKeyListener(this);
    }

    public void waitForCtrl() {
        latch = new CountDownLatch(1);
        System.out.println("等待 Ctrl 键按下（按 ESC 取消）...");
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (escapePressed) {
            System.out.println("检测到 ESC 键，程序终止。");
            System.exit(0);
        }
        ctrlPressed = false;
        escapePressed = false;
        System.out.println("检测到 Ctrl 键，继续执行...");
    }

    public static boolean isEscapePressed() {
        return escapePressed;
    }

    public static void clearEscapeFlag() {
        escapePressed = false;
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (e.getKeyCode() == NativeKeyEvent.VC_ESCAPE) {
            escapePressed = true;
            if (latch != null) latch.countDown();
        }
        if (e.getKeyCode() == NativeKeyEvent.VC_CONTROL) {
            ctrlPressed = true;
            if (latch != null) latch.countDown();
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {}

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {}
}