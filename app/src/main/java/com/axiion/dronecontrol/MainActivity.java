package com.axiion.dronecontrol;

import android.Manifest;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * AXiion DroneCTRL — flight control screen.
 *
 * Sticks:
 *   left  = throttle (Y, holds position) / yaw rate (X, self-centers)
 *   right = roll (X) / pitch (Y), both self-center
 *
 * Talks to the drone over the CRTP/UDP link documented in
 * CUSTOM_BUILD_NOTES.md (default 192.168.43.42:2390). See DroneLink and
 * CrtpPacket for the wire format.
 */
public class MainActivity extends AppCompatActivity implements DroneLink.Listener {

    private static final String PREFS = "axiion_drone_prefs";
    private static final String PREF_IP = "drone_ip";
    private static final String PREF_PORT = "drone_port";
    private static final String DEFAULT_IP = "192.168.43.42";
    private static final int DEFAULT_PORT = 2390;

    // Tuning — how far the sticks throw in physical units.
    private static final float MAX_ROLL_PITCH_DEG = 20f;
    private static final float MAX_YAW_RATE_DEG_S = 180f;
    private static final int REQ_LOCATION_PERMISSION = 101;

    private final DroneLink droneLink = new DroneLink();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private WifiManager wifiManager;

    private View statusDot;
    private TextView statusText;
    private TextView wifiHint;
    private TextView armStateText;
    private TextView thrustReadout;
    private TextView thrustCapValue;
    private SeekBar thrustCapSeek;
    private Button btnArm;
    private Button btnStop;
    private JoystickView leftStick;
    private JoystickView rightStick;

    private boolean armed = false;
    private boolean onDroneWifi = false;
    private float thrustCapFraction = 0.8f; // 80% default, matches the build notes' suggested motor-safety cap
    private float currentThrottleNorm = 0f; // 0..1 from the throttle stick

    private final Runnable wifiPoll = new Runnable() {
        @Override
        public void run() {
            checkWifi();
            uiHandler.postDelayed(this, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

        bindViews();
        wireControls();
        droneLink.setListener(this);

        ensureLocationPermission();
    }

    private void bindViews() {
        statusDot = findViewById(R.id.statusDot);
        statusText = findViewById(R.id.statusText);
        wifiHint = findViewById(R.id.wifiHint);
        armStateText = findViewById(R.id.armStateText);
        thrustReadout = findViewById(R.id.thrustReadout);
        thrustCapValue = findViewById(R.id.thrustCapValue);
        thrustCapSeek = findViewById(R.id.thrustCapSeek);
        btnArm = findViewById(R.id.btnArm);
        btnStop = findViewById(R.id.btnStop);
        leftStick = findViewById(R.id.leftStick);
        rightStick = findViewById(R.id.rightStick);

        ImageButton btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(v -> showSettingsDialog());

        wifiHint.setOnClickListener(v ->
                startActivity(new android.content.Intent(Settings.ACTION_WIFI_SETTINGS)));
    }

    private void wireControls() {
        thrustCapSeek.setProgress(Math.round(thrustCapFraction * 100));
        thrustCapValue.setText(Math.round(thrustCapFraction * 100) + "%");
        thrustCapSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                thrustCapFraction = progress / 100f;
                thrustCapValue.setText(progress + "%");
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Left stick: X = yaw rate (self-centers), Y = throttle (holds, doesn't go negative).
        leftStick.setOnStickChangeListener((x, y) -> {
            lastYawNorm = x;
            currentThrottleNorm = Math.max(0f, y);
            pushSetpoint();
        });

        // Right stick: X = roll, Y = pitch, both self-center.
        rightStick.setOnStickChangeListener((x, y) -> {
            lastRollNorm = x;
            lastPitchNorm = y;
            pushSetpoint();
        });

        btnArm.setOnClickListener(v -> {
            if (armed) {
                disarm();
            } else {
                arm();
            }
        });

        btnStop.setOnClickListener(v -> {
            disarm();
            leftStick.setPosition(0f, 0f);
            rightStick.setPosition(0f, 0f);
            Toast.makeText(this, "STOP — motors zeroed", Toast.LENGTH_SHORT).show();
        });
    }

    private void arm() {
        String ip = prefs.getString(PREF_IP, DEFAULT_IP);
        int port = prefs.getInt(PREF_PORT, DEFAULT_PORT);
        try {
            // Belt-and-suspenders alongside DroneLink's own zero-burst on
            // connect: also snap the throttle stick back to zero here so
            // the pilot has to physically raise it fresh after every ARM,
            // like a real transmitter's throttle-cut/idle-up requirement.
            // (DroneLink.start() is what actually guarantees the firmware's
            // thrustLocked latch clears — this just keeps the UI honest
            // with what's about to happen.)
            currentThrottleNorm = 0f;
            leftStick.setPosition(lastYawNorm, 0f);

            new Thread(() -> {
                try {
                    droneLink.start(ip, port);
                } catch (Exception e) {
                    uiHandler.post(() -> Toast.makeText(this,
                            "Couldn't open UDP socket: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }, "drone-link-start").start();

            armed = true;
            armStateText.setText(R.string.label_armed);
            armStateText.setTextColor(ContextCompat.getColor(this, R.color.accent_green));
            btnArm.setText(R.string.btn_disarm);
            btnArm.setBackground(ContextCompat.getDrawable(this, R.drawable.shape_btn_disarm));
        } catch (Exception e) {
            Toast.makeText(this, "Arm failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void disarm() {
        armed = false;
        droneLink.zeroNow();
        new Thread(droneLink::stop, "drone-link-stop").start();

        armStateText.setText(R.string.label_disarmed);
        armStateText.setTextColor(ContextCompat.getColor(this, R.color.accent_red));
        btnArm.setText(R.string.btn_arm);
        btnArm.setBackground(ContextCompat.getDrawable(this, R.drawable.shape_btn_arm));
        thrustReadout.setText("0%");
    }

    // Latest normalized (-1..1) stick values, updated by the joystick callbacks above.
    private float lastRollNorm = 0f, lastPitchNorm = 0f, lastYawNorm = 0f;

    /** Maps current stick values + arm state + thrust cap into a CRTP setpoint and sends it. */
    private void pushSetpoint() {
        thrustReadout.setText(Math.round(currentThrottleNorm * 100) + "%");

        if (!armed) return; // link isn't running while disarmed; nothing to send

        float rollDeg = lastRollNorm * MAX_ROLL_PITCH_DEG;
        float pitchDeg = lastPitchNorm * MAX_ROLL_PITCH_DEG;
        float yawRateDegS = lastYawNorm * MAX_YAW_RATE_DEG_S;
        float thrust = currentThrottleNorm * thrustCapFraction * 65535f;

        droneLink.setSetpoint(rollDeg, pitchDeg, yawRateDegS, thrust);
    }

    @Override
    protected void onResume() {
        super.onResume();
        uiHandler.post(wifiPoll);
    }

    @Override
    protected void onPause() {
        super.onPause();
        uiHandler.removeCallbacks(wifiPoll);
        // Safety: never keep sending setpoints while the app isn't in front.
        if (armed) disarm();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        droneLink.shutdown();
    }

    private void checkWifi() {
        boolean ok = false;
        try {
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                String ssid = wifiManager.getConnectionInfo() != null
                        ? wifiManager.getConnectionInfo().getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "");
                    ok = ssid.toUpperCase().startsWith("ESP-DRONE");
                }
            }
        } catch (SecurityException ignored) {
            // location permission not granted yet — SSID reads as <unknown ssid>
        }
        onDroneWifi = ok;
        updateStatusUi();
    }

    private void updateStatusUi() {
        int color;
        String text;
        if (!onDroneWifi) {
            color = R.color.accent_red;
            text = getString(R.string.status_disconnected);
            wifiHint.setVisibility(View.VISIBLE);
        } else if (armed && droneLink.isRxSeenRecently()) {
            color = R.color.accent_green;
            text = getString(R.string.status_linked);
            wifiHint.setVisibility(View.GONE);
        } else if (armed) {
            color = R.color.accent_yellow;
            text = "SENDING · no reply seen yet";
            wifiHint.setVisibility(View.GONE);
        } else {
            color = R.color.accent_cyan;
            text = getString(R.string.status_wifi_ok);
            wifiHint.setVisibility(View.GONE);
        }
        statusText.setText(text);
        GradientDrawable dot = (GradientDrawable) statusDot.getBackground().mutate();
        dot.setColor(ContextCompat.getColor(this, color));
    }

    @Override
    public void onRxStateChanged(boolean rxSeenRecently) {
        uiHandler.post(this::updateStatusUi);
    }

    @Override
    public void onSendError(String message) {
        uiHandler.post(() -> {
            if (armed) {
                Toast.makeText(this, "Link error: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showSettingsDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        EditText editIp = view.findViewById(R.id.editIp);
        EditText editPort = view.findViewById(R.id.editPort);
        editIp.setText(prefs.getString(PREF_IP, DEFAULT_IP));
        editPort.setText(String.valueOf(prefs.getInt(PREF_PORT, DEFAULT_PORT)));

        new AlertDialog.Builder(this)
                .setTitle(R.string.dlg_settings_title)
                .setView(view)
                .setPositiveButton(R.string.dlg_save, (dialog, which) -> {
                    String ip = editIp.getText().toString().trim();
                    int port;
                    try {
                        port = Integer.parseInt(editPort.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        port = DEFAULT_PORT;
                    }
                    prefs.edit().putString(PREF_IP, ip.isEmpty() ? DEFAULT_IP : ip)
                            .putInt(PREF_PORT, port).apply();
                    if (armed) {
                        // restart the link with the new address
                        disarm();
                        Toast.makeText(this, "Settings saved — tap ARM to reconnect", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.dlg_cancel, null)
                .show();
    }

    private void ensureLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION_PERMISSION);
        }
    }
}
