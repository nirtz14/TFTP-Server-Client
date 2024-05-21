package bgu.spl.net.impl.tftp;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    //TODO: Implement here the TFTP encoder and decoder

    private byte[] bytes = new byte[1 << 10]; //start with 1k
    private int len = 0;

    public static final short OP_RRQ = 1;
    public static final short OP_WRQ = 2;
    public static final short OP_DATA = 3;
    public static final short OP_ACK = 4;
    public static final short OP_ERROR = 5;
    public static final short OP_DIRQ = 6;
    public static final short OP_LOGRQ = 7;
    public static final short OP_DELRQ = 8;
    public static final short OP_BCAST = 9;
    public static final short OP_DISC = 10;


    @Override
    public byte[] decodeNextByte(byte nextByte) {

        if (len > 1) {
            short opcode = (short) (((short) bytes[0]) << 8 | (short) (bytes[1]) & 0x00FF);
            if (opcode == OP_RRQ || opcode == OP_WRQ || opcode == OP_LOGRQ || opcode == OP_DELRQ) {
                if (nextByte == 0) {
                    pushByte(nextByte);
                    byte[] result = Arrays.copyOf(bytes, len);
                    bytes = new byte[1 << 10];
                    len = 0;
                    return result;
                }
            }
            else if (opcode == OP_DATA) {
                if (len > 5) {
                    short packetSize = (short) (((short) bytes[2]) << 8 | (short) (bytes[3]) & 0x00FF);
                    if (len == packetSize + 5) {
                        pushByte(nextByte);
                        byte[] result = Arrays.copyOf(bytes, len);
                        bytes = new byte[1 << 10];
                        len = 0;
                        return result;
                    }
                }
            }
            else if (opcode == OP_ACK) {
                if (len == 3) {
                    pushByte(nextByte);
                    byte[] result = Arrays.copyOf(bytes, len);
                    bytes = new byte[1 << 10];
                    len = 0;
                    return result;
                }
            }
            else if (opcode == OP_ERROR) {
                if (len > 3 && nextByte == 0) {
                    pushByte(nextByte);
                    byte[] result = Arrays.copyOf(bytes, len);
                    bytes = new byte[1 << 10];
                    len = 0;
                    return result;
                }
            }
            else if (opcode == OP_DIRQ || opcode == OP_DISC) {
                byte[] result = Arrays.copyOf(bytes, len);
                bytes = new byte[1 << 10];
                len = 0;
                return result;
            }
            else if (opcode == OP_BCAST) {
                if (len > 2 && nextByte == 0) {
                    pushByte(nextByte);
                    byte[] result = Arrays.copyOf(bytes, len);
                    bytes = new byte[1 << 10];
                    len = 0;
                    return result;
                }
            }
        }

        pushByte(nextByte);
        if (len == 2) {
            short opcode = (short) (((short) bytes[0]) << 8 | (short) (bytes[1]) & 0x00FF);
            if (opcode == OP_DIRQ || opcode == OP_DISC) {
                byte[] result = Arrays.copyOf(bytes, len);
                bytes = new byte[1 << 10];
                len = 0;
                return result;
            }
        }
        return null;
    }

    @Override
    public byte[] encode(byte[] message) {
        return message; 
    }

    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            bytes = Arrays.copyOf(bytes, len * 2);
        }

        bytes[len++] = nextByte;
    }

}