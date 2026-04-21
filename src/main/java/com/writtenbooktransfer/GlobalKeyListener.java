package com.writtenbooktransfer;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GlobalKeyListener implements NativeKeyListener {

    private static volatile boolean escapePressed = false;
    private static volatile boolean ctrlPressed = false;

    private CountDownLatch latch;

    public static void register() {
        forceCleanup();
        try {
            Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            logger.setLevel(Level.OFF);
            GlobalScreen.registerNativeHook();
            System.out.println("[GlobalKeyListener] 全局钩子注册成功");
        } catch (Exception e) {
            System.err.println("[GlobalKeyListener] 注册全局钩子失败！请以管理员权限运行程序。");
            e.printStackTrace();
            throw new RuntimeException("无法注册全局钩子，请确认以管理员/root权限运行。", e);
        }
    }

    public static void unregister() {
        try {
            GlobalScreen.unregisterNativeHook();
            System.out.println("[GlobalKeyListener] 全局钩子已卸载");
        } catch (Exception e) {
            // 忽略
        }
        escapePressed = false;
        ctrlPressed = false;
    }

    public static void forceCleanup() {
        unregister();
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {}
    }

    public void addToGlobalScreen() {
        GlobalScreen.addNativeKeyListener(this);
        System.out.println("[GlobalKeyListener] 监听器已添加到 GlobalScreen");
    }

    public void removeFromGlobalScreen() {
        GlobalScreen.removeNativeKeyListener(this);
    }

    public void waitForCtrl() {
        latch = new CountDownLatch(1);
        System.out.println("[GlobalKeyListener] 等待 Ctrl 键按下（按 ESC 取消）...");
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("[GlobalKeyListener] 等待被中断");
        }
        if (escapePressed) {
            System.out.println("[GlobalKeyListener] 检测到 ESC 键，程序终止。");
            System.exit(0);
        }
        ctrlPressed = false;
        escapePressed = false;
        System.out.println("[GlobalKeyListener] 检测到 Ctrl 键，继续执行...");
    }

    public static boolean isEscapePressed() {
        return escapePressed;
    }

    public static void clearEscapeFlag() {
        escapePressed = false;
    }

    // ========== NativeKeyListener 实现 ==========
    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        int code = e.getKeyCode();
        String keyText = NativeKeyEvent.getKeyText(code);
        System.out.println("[GlobalKeyListener] 按键按下: " + code + " (" + keyText + ")");

        if (code == NativeKeyEvent.VC_ESCAPE) {
            escapePressed = true;
            if (latch != null) {
                latch.countDown();
                System.out.println("[GlobalKeyListener] ESC 触发 latch.countDown()");
            }
        }
        if (code == NativeKeyEvent.VC_CONTROL) {
            ctrlPressed = true;
            if (latch != null) {
                latch.countDown();
                System.out.println("[GlobalKeyListener] Ctrl 触发 latch.countDown()");
            }
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        // 可留空
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        // 可留空
    }
}