package com.heronixedu.hub.service;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HMODULE;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.HHOOK;
import com.sun.jna.platform.win32.WinUser.KBDLLHOOKSTRUCT;
import com.sun.jna.platform.win32.WinUser.LowLevelKeyboardProc;
import com.sun.jna.platform.win32.WinUser.MSG;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Native Windows keyboard hook that blocks OS-level shortcuts
 * (Alt+Tab, Alt+F4, Win key, Ctrl+Esc) in kiosk mode.
 *
 * Uses JNA to install a low-level keyboard hook via SetWindowsHookEx.
 * Note: Ctrl+Alt+Delete cannot be blocked (Windows security by design).
 */
@Component
@Slf4j
public class NativeKeyBlocker {

    private volatile HHOOK hook;
    private volatile Thread hookThread;
    private volatile boolean active = false;

    // Virtual key codes
    private static final int VK_TAB = 0x09;
    private static final int VK_ESCAPE = 0x1B;
    private static final int VK_F4 = 0x73;
    private static final int VK_LWIN = 0x5B;
    private static final int VK_RWIN = 0x5C;

    // Modifier flags in KBDLLHOOKSTRUCT
    private static final int LLKHF_ALTDOWN = 0x20;

    /**
     * Start blocking OS-level keyboard shortcuts.
     */
    public synchronized void start() {
        if (active) {
            return;
        }

        // Only run on Windows
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            log.info("NativeKeyBlocker: Not running on Windows, skipping native hook");
            return;
        }

        active = true;
        hookThread = new Thread(this::runHookLoop, "NativeKeyBlocker");
        hookThread.setDaemon(true);
        hookThread.start();
        log.info("Native keyboard blocker started");
    }

    /**
     * Stop blocking and remove the keyboard hook.
     */
    public synchronized void stop() {
        if (!active) {
            return;
        }

        active = false;

        if (hook != null) {
            User32.INSTANCE.UnhookWindowsHookEx(hook);
            hook = null;
        }

        if (hookThread != null) {
            hookThread.interrupt();
            hookThread = null;
        }

        log.info("Native keyboard blocker stopped");
    }

    public boolean isActive() {
        return active;
    }

    private void runHookLoop() {
        try {
            LowLevelKeyboardProc keyboardProc = (int nCode, WPARAM wParam, KBDLLHOOKSTRUCT info) -> {
                if (nCode >= 0 && active) {
                    int vkCode = info.vkCode;
                    int flags = info.flags;
                    boolean altDown = (flags & LLKHF_ALTDOWN) != 0;

                    // Block Alt+Tab
                    if (altDown && vkCode == VK_TAB) {
                        log.debug("Blocked Alt+Tab");
                        return new LRESULT(1);
                    }

                    // Block Alt+F4
                    if (altDown && vkCode == VK_F4) {
                        log.debug("Blocked Alt+F4");
                        return new LRESULT(1);
                    }

                    // Block Alt+Escape
                    if (altDown && vkCode == VK_ESCAPE) {
                        log.debug("Blocked Alt+Esc");
                        return new LRESULT(1);
                    }

                    // Block Windows keys (Start menu)
                    if (vkCode == VK_LWIN || vkCode == VK_RWIN) {
                        log.debug("Blocked Windows key");
                        return new LRESULT(1);
                    }

                    // Block Ctrl+Escape (Start menu alternative)
                    if (vkCode == VK_ESCAPE) {
                        // Check if Ctrl is down via GetAsyncKeyState
                        short ctrlState = User32.INSTANCE.GetAsyncKeyState(0x11); // VK_CONTROL
                        if ((ctrlState & 0x8000) != 0) {
                            log.debug("Blocked Ctrl+Esc");
                            return new LRESULT(1);
                        }
                    }
                }

                return User32.INSTANCE.CallNextHookEx(hook, nCode, wParam, new LPARAM(Pointer.nativeValue(info.getPointer())));
            };

            HMODULE hMod = Kernel32.INSTANCE.GetModuleHandle(null);
            hook = User32.INSTANCE.SetWindowsHookEx(
                    WinUser.WH_KEYBOARD_LL,
                    keyboardProc,
                    hMod,
                    0
            );

            if (hook == null) {
                log.error("Failed to install keyboard hook. Error: {}", Kernel32.INSTANCE.GetLastError());
                active = false;
                return;
            }

            log.info("Low-level keyboard hook installed successfully");

            // Message pump - required for the hook to work
            MSG msg = new MSG();
            while (active && User32.INSTANCE.GetMessage(msg, null, 0, 0) != 0) {
                User32.INSTANCE.TranslateMessage(msg);
                User32.INSTANCE.DispatchMessage(msg);
            }

        } catch (Exception e) {
            log.error("Native keyboard hook error: {}", e.getMessage());
        } finally {
            if (hook != null) {
                User32.INSTANCE.UnhookWindowsHookEx(hook);
                hook = null;
            }
            active = false;
        }
    }
}
