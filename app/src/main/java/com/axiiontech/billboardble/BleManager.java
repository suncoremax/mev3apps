package com.axiiontech.billboardble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.UUID;

/**
 * Talks to the AXIION Billboard ESP32 over Bluetooth Low Energy.
 * Protocol/UUIDs must exactly match ble_config.h in the firmware.
 */
public class BleManager {

    private static final String TAG = "AxiionBLE";

    public static final UUID SERVICE_UUID = UUID.fromString("a1e00000-1234-4a5b-8c6d-000000000001");
    public static final UUID CHAR_RX_UUID = UUID.fromString("a1e00000-1234-4a5b-8c6d-000000000002");
    public static final UUID CHAR_TX_UUID = UUID.fromString("a1e00000-1234-4a5b-8c6d-000000000003");
    private static final UUID CCCD_UUID   = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final byte OP_CMD       = 0x01;
    private static final byte OP_IMG_CHUNK = 0x02;

    private static final String DEVICE_NAME = "AXIION-Billboard";
    private static final int SCAN_TIMEOUT_MS = 8000;
    private static final int OP_TIMEOUT_MS = 8000;

    public interface ConnectionListener {
        void onScanning();
        void onConnecting();
        void onConnected();
        void onDisconnected();
        void onDeviceNotFound();
        void onBleUnavailable(String reason);
    }

    /** Callback for a single request/response exchange (text slide, delete, brightness, info, image). */
    public interface OpCallback {
        default void onProgress(int percent) {}
        void onDone(boolean success, String message);
    }

    /** Fires whenever the board sends an unsolicited status/notify line (used for the slide list). */
    public interface RawStatusListener {
        void onRawStatus(String line);
    }

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ConnectionListener connectionListener;

    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic rxChar;
    private BluetoothGattCharacteristic txChar;
    private int mtuPayload = 20; // conservative default until MTU negotiation succeeds

    private volatile boolean connected = false;
    private final ArrayDeque<byte[]> writeQueue = new ArrayDeque<>();
    private boolean writeInFlight = false;

    private RawStatusListener rawStatusListener;

    // ── State for the one active request/response op (txt/del/brightness/info) ──
    private OpCallback pendingSimpleOp;
    private Runnable pendingSimpleTimeout;

    // ── State for an in-progress chunked image transfer ──
    private byte[] pendingImgBytes;
    private String pendingImgId;
    private int pendingImgSent;
    private OpCallback pendingImgCallback;
    private Runnable pendingImgTimeout;

    public BleManager(Context context, ConnectionListener listener) {
        this.appContext = context.getApplicationContext();
        this.connectionListener = listener;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setRawStatusListener(RawStatusListener l) {
        this.rawStatusListener = l;
    }

    // ═══════════════════════════════════════════════════════════
    //  Scan + connect
    // ═══════════════════════════════════════════════════════════

    public void connect() {
        BluetoothManager btManager = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = btManager != null ? btManager.getAdapter() : null;
        if (adapter == null) {
            connectionListener.onBleUnavailable("no_adapter");
            return;
        }
        if (!adapter.isEnabled()) {
            connectionListener.onBleUnavailable("bt_off");
            return;
        }

        BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            connectionListener.onBleUnavailable("no_scanner");
            return;
        }

        connectionListener.onScanning();

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        final boolean[] found = {false};
        ScanCallback scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                if (found[0]) return;
                BluetoothDevice device = result.getDevice();
                found[0] = true;
                try { scanner.stopScan(this); } catch (SecurityException ignored) {}
                connectToDevice(device);
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.w(TAG, "Scan failed: " + errorCode);
                connectionListener.onDeviceNotFound();
            }
        };

        try {
            scanner.startScan(java.util.Collections.singletonList(filter), settings, scanCallback);
        } catch (SecurityException e) {
            connectionListener.onBleUnavailable("permission");
            return;
        }

        mainHandler.postDelayed(() -> {
            if (!found[0]) {
                try { scanner.stopScan(scanCallback); } catch (SecurityException ignored) {}
                connectionListener.onDeviceNotFound();
            }
        }, SCAN_TIMEOUT_MS);
    }

    private void connectToDevice(BluetoothDevice device) {
        connectionListener.onConnecting();
        try {
            gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (SecurityException e) {
            connectionListener.onBleUnavailable("permission");
        }
    }

    public void disconnect() {
        connected = false;
        writeQueue.clear();
        writeInFlight = false;
        if (gatt != null) {
            try {
                gatt.disconnect();
                gatt.close();
            } catch (SecurityException ignored) {}
            gatt = null;
        }
        connectionListener.onDisconnected();
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                try { g.discoverServices(); } catch (SecurityException ignored) {}
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected = false;
                mainHandler.post(connectionListener::onDisconnected);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) return;
            android.bluetooth.BluetoothGattService svc = g.getService(SERVICE_UUID);
            if (svc == null) {
                mainHandler.post(connectionListener::onDeviceNotFound);
                return;
            }
            rxChar = svc.getCharacteristic(CHAR_RX_UUID);
            txChar = svc.getCharacteristic(CHAR_TX_UUID);
            try {
                g.requestMtu(512);
            } catch (SecurityException ignored) {}
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            // 3 bytes of ATT overhead, 1 byte for our opcode prefix.
            mtuPayload = Math.max(19, mtu - 3 - 1);
            try {
                g.setCharacteristicNotification(txChar, true);
                BluetoothGattDescriptor cccd = txChar.getDescriptor(CCCD_UUID);
                if (cccd != null) {
                    cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    g.writeDescriptor(cccd);
                }
            } catch (SecurityException ignored) {}
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor descriptor, int status) {
            // Notifications are enabled now — the connection is fully ready to use.
            connected = true;
            mainHandler.post(connectionListener::onConnected);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            writeInFlight = false;
            processWriteQueue();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            byte[] value = c.getValue();
            if (value == null) return;
            String line = new String(value, StandardCharsets.UTF_8);
            mainHandler.post(() -> handleStatusLine(line));
        }
    };

    // ═══════════════════════════════════════════════════════════
    //  Write queue (keeps BLE writes sequential/well-paced)
    // ═══════════════════════════════════════════════════════════

    private synchronized void enqueueWrite(byte[] payload, int writeType) {
        writeQueue.add(payload);
        processWriteQueue();
    }

    private synchronized void processWriteQueue() {
        if (writeInFlight || writeQueue.isEmpty() || rxChar == null || gatt == null) return;
        byte[] next = writeQueue.poll();
        writeInFlight = true;
        rxChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        rxChar.setValue(next);
        try {
            gatt.writeCharacteristic(rxChar);
        } catch (SecurityException e) {
            writeInFlight = false;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Simple JSON ops: txt / del / brightness / info
    // ═══════════════════════════════════════════════════════════

    public void sendJsonOp(JSONObject json, OpCallback callback) {
        if (!connected) {
            callback.onDone(false, "not_connected");
            return;
        }
        cancelPendingSimpleOp();
        pendingSimpleOp = callback;
        pendingSimpleTimeout = () -> {
            if (pendingSimpleOp == callback) {
                pendingSimpleOp = null;
                callback.onDone(false, "timeout");
            }
        };
        mainHandler.postDelayed(pendingSimpleTimeout, OP_TIMEOUT_MS);

        byte[] jsonBytes = json.toString().getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[jsonBytes.length + 1];
        payload[0] = OP_CMD;
        System.arraycopy(jsonBytes, 0, payload, 1, jsonBytes.length);
        enqueueWrite(payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
    }

    private void cancelPendingSimpleOp() {
        if (pendingSimpleTimeout != null) mainHandler.removeCallbacks(pendingSimpleTimeout);
        pendingSimpleOp = null;
        pendingSimpleTimeout = null;
    }

    // ═══════════════════════════════════════════════════════════
    //  Chunked image transfer
    // ═══════════════════════════════════════════════════════════

    public void sendImage(String id, int durationMs, byte[] rgb565Bytes, OpCallback callback) {
        if (!connected) {
            callback.onDone(false, "not_connected");
            return;
        }
        pendingImgBytes = rgb565Bytes;
        pendingImgId = id;
        pendingImgSent = 0;
        pendingImgCallback = callback;
        armImgTimeout();

        try {
            JSONObject begin = new JSONObject();
            begin.put("op", "img_begin");
            begin.put("id", id);
            begin.put("dur", durationMs);
            begin.put("len", rgb565Bytes.length);
            byte[] jsonBytes = begin.toString().getBytes(StandardCharsets.UTF_8);
            byte[] payload = new byte[jsonBytes.length + 1];
            payload[0] = OP_CMD;
            System.arraycopy(jsonBytes, 0, payload, 1, jsonBytes.length);
            enqueueWrite(payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        } catch (Exception e) {
            finishImageTransfer(false, "build_error");
        }
    }

    private void armImgTimeout() {
        if (pendingImgTimeout != null) mainHandler.removeCallbacks(pendingImgTimeout);
        pendingImgTimeout = () -> finishImageTransfer(false, "timeout");
        mainHandler.postDelayed(pendingImgTimeout, OP_TIMEOUT_MS);
    }

    private void sendNextChunk() {
        if (pendingImgBytes == null) return;
        int remaining = pendingImgBytes.length - pendingImgSent;
        if (remaining <= 0) return;
        int size = Math.min(mtuPayload, remaining);
        byte[] payload = new byte[size + 1];
        payload[0] = OP_IMG_CHUNK;
        System.arraycopy(pendingImgBytes, pendingImgSent, payload, 1, size);
        rxChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        enqueueWrite(payload, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        pendingImgSent += size;
    }

    private void finishImageTransfer(boolean success, String message) {
        if (pendingImgTimeout != null) mainHandler.removeCallbacks(pendingImgTimeout);
        OpCallback cb = pendingImgCallback;
        pendingImgBytes = null;
        pendingImgId = null;
        pendingImgCallback = null;
        pendingImgTimeout = null;
        if (cb != null) cb.onDone(success, message);
    }

    // ═══════════════════════════════════════════════════════════
    //  Incoming status line dispatch
    // ═══════════════════════════════════════════════════════════

    private void handleStatusLine(String line) {
        if (rawStatusListener != null) rawStatusListener.onRawStatus(line);

        if (line.startsWith("OK:BEGIN:") && pendingImgId != null) {
            armImgTimeout();
            sendNextChunk();
        } else if (line.startsWith("PROGRESS:") && pendingImgId != null) {
            armImgTimeout();
            String[] parts = line.substring("PROGRESS:".length()).split("/");
            try {
                int recv = Integer.parseInt(parts[0]);
                int total = Integer.parseInt(parts[1]);
                if (pendingImgCallback != null && total > 0) {
                    pendingImgCallback.onProgress((int) (recv * 100L / total));
                }
                if (recv >= pendingImgBytes.length) {
                    // All bytes acknowledged — finalize the slide.
                    try {
                        JSONObject end = new JSONObject();
                        end.put("op", "img_end");
                        end.put("id", pendingImgId);
                        byte[] jsonBytes = end.toString().getBytes(StandardCharsets.UTF_8);
                        byte[] payload = new byte[jsonBytes.length + 1];
                        payload[0] = OP_CMD;
                        System.arraycopy(jsonBytes, 0, payload, 1, jsonBytes.length);
                        enqueueWrite(payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    } catch (Exception ignored) {}
                } else {
                    sendNextChunk();
                }
            } catch (NumberFormatException ignored) {}
        } else if (line.startsWith("OK:IMG:")) {
            finishImageTransfer(true, line);
        } else if (line.startsWith("ERR:") && pendingImgId != null) {
            finishImageTransfer(false, line);
        } else if (pendingSimpleOp != null &&
                (line.startsWith("OK:") || line.startsWith("ERR:") || line.startsWith("INFO:"))) {
            OpCallback cb = pendingSimpleOp;
            cancelPendingSimpleOp();
            cb.onDone(!line.startsWith("ERR:"), line);
        }
    }
}
