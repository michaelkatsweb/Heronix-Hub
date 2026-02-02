package com.heronixedu.hub.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Monitors and terminates Task Manager when kiosk mode is active.
 * Prevents students from killing the Hub process via Ctrl+Alt+Delete.
 */
@Service
@Slf4j
public class ProcessGuardService {

    private volatile boolean active = false;
    private ScheduledExecutorService scheduler;

    public synchronized void start() {
        if (active) return;
        active = true;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ProcessGuard");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::killBlockedProcesses, 0, 1, TimeUnit.SECONDS);
        log.info("Process guard started");
    }

    public synchronized void stop() {
        if (!active) return;
        active = false;

        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        log.info("Process guard stopped");
    }

    public boolean isActive() {
        return active;
    }

    private void killBlockedProcesses() {
        if (!active) return;

        try {
            ProcessHandle.allProcesses()
                    .filter(ProcessHandle::isAlive)
                    .filter(ph -> {
                        String cmd = ph.info().command().orElse("").toLowerCase();
                        return cmd.endsWith("taskmgr.exe") || cmd.endsWith("taskmgr");
                    })
                    .forEach(ph -> {
                        ph.destroy();
                        log.debug("Terminated Task Manager process (PID: {})", ph.pid());
                    });
        } catch (Exception e) {
            log.debug("Process guard scan error: {}", e.getMessage());
        }
    }
}
