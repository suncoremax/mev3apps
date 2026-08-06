package com.axiion.dronecontrol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Builds CRTP (Crazy RealTime Protocol) frames for the ESP-Drone firmware,
 * per CUSTOM_BUILD_NOTES.md section 6.
 *
 * Frame layout:
 *   byte 0     : header = (port << 4) | channel
 *   byte 1..N  : payload (little-endian)
 *
 * This class only implements CRTP_PORT_SETPOINT / channel 0, the "legacy
 * commander" packet, which is the minimum needed to fly:
 *
 *   float32 roll    (deg,   +right)
 *   float32 pitch   (deg,   +forward)
 *   float32 yaw     (deg/s)
 *   uint16  thrust  (0..65535, 0 = motors off / disarm)
 *
 * IMPORTANT (carried over from the build notes' own caution): this was
 * written from the protocol spec in CUSTOM_BUILD_NOTES.md, not verified
 * byte-for-byte against the official ESP-Drone app or Bitcraze's cflib.
 * If the drone doesn't respond to setpoints, capture the raw bytes this
 * class produces and diff them against a known-good client before
 * suspecting the flight firmware.
 */
final class CrtpPacket {

    static final int PORT_CONSOLE = 0x00;
    static final int PORT_PARAM = 0x02;
    static final int PORT_SETPOINT = 0x03;
    static final int PORT_LOG = 0x05;
    static final int PORT_SETPOINT_GENERIC = 0x07;
    static final int PORT_PLATFORM = 0x0D;

    private CrtpPacket() {}

    /** Builds the 15-byte legacy-commander setpoint packet. */
    static byte[] setpoint(float rollDeg, float pitchDeg, float yawDegPerSec, int thrust) {
        int clampedThrust = Math.max(0, Math.min(65535, thrust));

        ByteBuffer buf = ByteBuffer.allocate(1 + 4 + 4 + 4 + 2);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        int header = (PORT_SETPOINT << 4) | 0x00;
        buf.put((byte) header);
        buf.putFloat(rollDeg);
        buf.putFloat(pitchDeg);
        buf.putFloat(yawDegPerSec);
        buf.putShort((short) (clampedThrust & 0xFFFF));

        return buf.array();
    }

    /** All-zero setpoint = disarm, per the build notes ("thrust=0 is disarm"). */
    static byte[] zeroSetpoint() {
        return setpoint(0f, 0f, 0f, 0);
    }
}
