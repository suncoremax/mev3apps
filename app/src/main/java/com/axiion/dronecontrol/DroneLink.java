package com.axiion.dronecontrol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the UDP socket to the drone and sends CRTP setpoint packets on a
 * steady background timer, per CUSTOM_BUILD_NOTES.md §6:
 *   - drone is a SoftAP, default IP 192.168.43.42, UDP port 2390
 *   - send setpoints at ~50-100 Hz, not tied to the UI thread
 *   - send an all-zero-thrust packet on stop / any doubt about the link
 *
 * This class does NOT implement the LOG subsystem (battery/attitude
 * telemetry) — doing that correctly needs the CRTP LOG TOC download +
 * block-subscription handshake, which the build notes explicitly flag as
 * needing verification against the official app / cflib rather than being
 * safely guessable from the spec alone. What this class *can* tell you
 * honestly is whether any UDP traffic at all has come back from the drone,
 * which is exposed via {@link Listener#onRxStateChanged(boolean)}.
 */
final class DroneLink {

    interface Listener {
        /** Called on a background thread whenever the "have we seen any reply" state flips. */
        void onRxStateChanged(boolean rxSeenRecently);
        void onSendError(String message);
    }

    private static final int SEND_HZ = 60;
    private static final long SEND_PERIOD_MS = 1000L / SEND_HZ;
    private static final long RX_TIMEOUT_MS = 2500L;

    private final ScheduledExecutorService sendExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorRxThread rxThread;

    private volatile DatagramSocket socket;
    private volatile InetAddress address;
    private volatile int port;

    // Current setpoint, updated freely from the UI thread.
    private final AtomicReference<float[]> setpoint =
            new AtomicReference<>(new float[]{0f, 0f, 0f, 0f}); // roll, pitch, yaw, thrust

    private final AtomicLong lastRxAtMs = new AtomicLong(0);
    private final AtomicBoolean rxSeenRecently = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ScheduledFuture<?> sendTask;
    private Listener listener;

    DroneLink() {
        rxThread = new ExecutorRxThread();
    }

    void setListener(Listener l) {
        this.listener = l;
    }

    /** Opens the socket and starts the send loop. Call from a non-UI thread or let it self-dispatch (it's fast). */
    void start(String host, int udpPort) throws IOException {
        stop(); // ensure clean state if called twice

        address = InetAddress.getByName(host);
        port = udpPort;
        socket = new DatagramSocket();
        socket.setSoTimeout(1000);

        running.set(true);
        rxThread.start(socket);

        sendTask = sendExecutor.scheduleAtFixedRate(this::sendTick, 0, SEND_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    /** Updates the live setpoint. Thread-safe; call from the UI thread as often as you like. */
    void setSetpoint(float rollDeg, float pitchDeg, float yawDegPerSec, float thrust0to65535) {
        setpoint.set(new float[]{rollDeg, pitchDeg, yawDegPerSec, thrust0to65535});
    }

    /** Immediately queues an all-zero setpoint (disarm / emergency stop). */
    void zeroNow() {
        setpoint.set(new float[]{0f, 0f, 0f, 0f});
    }

    private void sendTick() {
        DatagramSocket s = socket;
        InetAddress a = address;
        if (s == null || a == null || s.isClosed()) return;

        float[] sp = setpoint.get();
        byte[] bytes = CrtpPacket.setpoint(sp[0], sp[1], sp[2], Math.round(sp[3]));

        try {
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, a, port);
            s.send(packet);
        } catch (IOException e) {
            if (listener != null) listener.onSendError(e.getMessage());
        }

        // Age out the "we've seen a reply" flag once RX_TIMEOUT_MS passes with no traffic.
        long lastRx = lastRxAtMs.get();
        boolean stillFresh = lastRx != 0 && (System.currentTimeMillis() - lastRx) < RX_TIMEOUT_MS;
        if (!stillFresh && rxSeenRecently.compareAndSet(true, false) && listener != null) {
            listener.onRxStateChanged(false);
        }
    }

    void notifyRx() {
        lastRxAtMs.set(System.currentTimeMillis());
        if (listener != null && !rxSeenRecently.get()) {
            rxSeenRecently.set(true);
            listener.onRxStateChanged(true);
        }
    }

    /** Sends a few zero-thrust packets synchronously then closes the socket. Safe to call from UI thread. */
    void stop() {
        running.set(false);
        rxThread.stop();

        if (sendTask != null) {
            sendTask.cancel(false);
            sendTask = null;
        }

        DatagramSocket s = socket;
        InetAddress a = address;
        if (s != null && a != null && !s.isClosed()) {
            byte[] zero = CrtpPacket.zeroSetpoint();
            for (int i = 0; i < 3; i++) {
                try {
                    s.send(new DatagramPacket(zero, zero.length, a, port));
                } catch (IOException ignored) {
                }
            }
            s.close();
        }
        socket = null;
        lastRxAtMs.set(0);
        rxSeenRecently.set(false);
    }

    void shutdown() {
        stop();
        sendExecutor.shutdownNow();
    }

    boolean isRxSeenRecently() {
        return rxSeenRecently.get();
    }

    /** Small dedicated receive loop so an ACK/console/log packet can flip the "rx seen" flag. */
    private class ExecutorRxThread {
        private Thread thread;
        private volatile boolean alive;

        void start(DatagramSocket s) {
            alive = true;
            thread = new Thread(() -> {
                byte[] buf = new byte[128];
                while (alive) {
                    try {
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        s.receive(p);
                        notifyRx();
                    } catch (SocketTimeoutException timeout) {
                        // normal — lets us check `alive` periodically
                    } catch (SocketException closed) {
                        break; // socket was closed by stop()
                    } catch (IOException e) {
                        if (listener != null) listener.onSendError(e.getMessage());
                    }
                }
            }, "drone-link-rx");
            thread.start();
        }

        void stop() {
            alive = false;
            if (thread != null) {
                thread.interrupt();
                thread = null;
            }
        }
    }
}
