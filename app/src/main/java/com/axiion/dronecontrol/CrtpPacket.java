package com.axiion.dronecontrol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Builds CRTP (Crazy RealTime Protocol) frames for the ESP-Drone firmware,
 * per CUSTOM_BUILD_NOTES.md section 6.
 *
 * Frame layout on the wire (confirmed against the firmware's
 * components/drivers/general/wifi/wifi_esp32.c, udp_server_rx_task /
 * udp_server_tx_task):
 *   byte 0        : header = (port << 4) | channel
 *   byte 1..N     : payload (little-endian)
 *   byte N+1      : checksum = (sum of all preceding bytes) & 0xFF
 *
 * ROOT CAUSE of "wifi links but drone never replies": this class was NOT
 * appending that trailing checksum byte. The firmware's udp_server_rx_task
 * computes calculate_cksum() over every incoming datagram and silently
 * drops (logs "udp packet cksum unmatched") anything that doesn't match —
 * it never even reaches the CRTP dispatcher, so the commander never saw a
 * setpoint and nothing was ever queued to send back. Fixed below by having
 * every builder run its frame through withChecksum().
 *
 * This class implements:
 *  - CRTP_PORT_SETPOINT / channel 0, the "legacy commander" packet, which
 *    is the minimum needed to fly.
 *  - CRTP_PORT_LINK / channel 0 (linkEcho), which the firmware's
 *    crtpservice.c echoes back verbatim (crtpserviceHandler -> linkEcho ->
 *    crtpSendPacket(p)). Setpoint packets alone never produce a reply by
 *    themselves — the firmware's debug console only goes to the serial/UART
 *    log by default (see debug_cf.h, DEBUG_PRINT_ON_CONSOLE is undefined),
 *    it's NOT relayed over CRTP unless that build flag is on. So a
 *    setpoint-only stream can be 100% correctly received by the drone and
 *    still never trip DroneLink's "have we seen any reply" flag. The link
 *    echo ping gives a deterministic, guaranteed reply to watch for instead.
 */
final class CrtpPacket {

    static final int PORT_CONSOLE = 0x00;
    static final int PORT_PARAM = 0x02;
    static final int PORT_SETPOINT = 0x03;
    static final int PORT_LOG = 0x05;
    static final int PORT_SETPOINT_GENERIC = 0x07;
    static final int PORT_PLATFORM = 0x0D;
    static final int PORT_LINK = 0x0F;
    static final int LINK_CHANNEL_ECHO = 0x00;

    private CrtpPacket() {}

    /** Builds the 15-byte legacy-commander setpoint payload, then appends the checksum byte (16 bytes total on the wire). */
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

        return withChecksum(buf.array());
    }

    /** All-zero setpoint = disarm, per the build notes ("thrust=0 is disarm"). */
    static byte[] zeroSetpoint() {
        return setpoint(0f, 0f, 0f, 0);
    }

    /**
     * Builds a CRTP_PORT_LINK / linkEcho ping. The firmware's crtpservice.c
     * bounces this back byte-for-byte (with its own freshly-computed
     * checksum on the way out), so seeing any reply at all to this specific
     * packet is a reliable "the drone is receiving AND replying" signal —
     * independent of whether console/log output is wired to CRTP.
     */
    static byte[] linkEchoPing(byte pingId) {
        byte header = (byte) ((PORT_LINK << 4) | LINK_CHANNEL_ECHO);
        return withChecksum(new byte[]{header, pingId});
    }

    /** Appends the trailing (sum-of-bytes & 0xFF) checksum byte the firmware's UDP RX task requires. */
    private static byte[] withChecksum(byte[] frame) {
        int sum = 0;
        for (byte b : frame) {
            sum += (b & 0xFF);
        }
        byte[] out = new byte[frame.length + 1];
        System.arraycopy(frame, 0, out, 0, frame.length);
        out[frame.length] = (byte) (sum & 0xFF);
        return out;
    }
}
