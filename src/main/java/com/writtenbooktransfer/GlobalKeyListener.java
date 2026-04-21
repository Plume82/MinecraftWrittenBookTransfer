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
    private static volatile boolean hookRegistered = false;

    private CountDownLatch latch;

    // 一次性初始化全局钩子（程序启动时调用一次）
    public static void initGlobalHook() {
        if (hookRegistered) return;
        try {
            Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            logger.setLevel(Level.OFF);
            GlobalScreen.registerNativeHook();
            hookRegistered = true;
            System.out.println("[GlobalKeyListener] 全局钩子初始化成功（全局唯一）");
        } catch (Exception e) {
            System.err.println("[GlobalKeyListener] 全局钩子初始化失败！请以管理员权限运行。");
            e.printStackTrace();
            throw new RuntimeException("无法注册全局钩子", e);
        }
    }

    // 移除原有的 register() / unregister() / forceCleanup()，不再卸载钩子

    public void addToGlobalScreen() {
        GlobalScreen.addNativeKeyListener(this);
        System.out.println("[GlobalKeyListener] 监听器已添加");
    }

    public void removeFromGlobalScreen() {
        GlobalScreen.removeNativeKeyListener(this);
        System.out.println("[GlobalKeyListener] 监听器已移除");
    }

    public void waitForCtrl() {
        latch = new CountDownLatch(1);
        System.out.println("[GlobalKeyListener] 等待 Ctrl 键按下（按 ESC 取消）...");
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (escapePressed) {
            System.out.println("[GlobalKeyListener] 检测到 ESC 键，传输取消");
            // 注意：不再 System.exit(0)，只取消传输
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

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        int code = e.getKeyCode();
        // 调试输出（可注释）
        // System.out.println("[GlobalKeyListener] 按键: " + code);
        if (code == NativeKeyEvent.VC_ESCAPE) {
            escapePressed = true;
            if (latch != null) latch.countDown();
        }
        if (code == NativeKeyEvent.VC_CONTROL) {
            ctrlPressed = true;
            if (latch != null) latch.countDown();
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {}

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {}
}