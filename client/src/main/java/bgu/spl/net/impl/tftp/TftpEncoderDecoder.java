package bgu.spl.net.impl.tftp;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]>   {
   
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
                    short packetSize = (short) (((short) bytes[2]) << 8 | (short) (bytes[3]) & 0x00ff);
                    if (len == (int) (packetSize) + 5) {
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
        return null;
    }

    @Override
    public byte[] encode (byte[] message) {
        String temp = new String(message, StandardCharsets.UTF_8);
        if (temp.length() < 4) {
            System.out.println("Error: invalid message");
            return null;
        }
        String comm = temp.substring(0, 3);
        if (comm.equals("LOG")){
            if(temp.length() <= 6){ //in case no username have been filled in the LOGRQ operation 
                System.out.println("Error: the Login opperation doesn't contain any username");
                return null;
            }
            pushByte((byte)(OP_LOGRQ >> 8));
            pushByte((byte)(OP_LOGRQ & 0xff));
            String usr = temp.substring(6, temp.length());
            for (byte nextByte : usr.getBytes(StandardCharsets.UTF_8)){
                pushByte(nextByte);
            }
            pushByte((byte)(0));
            byte[] result = Arrays.copyOf(bytes, len);
            bytes = new byte[1 << 10];
            len = 0;
            return result;
        }
        else if(comm.equals("DEL")){
            if(temp.length() <= 6){ //in case no file name have been filled in the DELRQ operation 
                System.out.println("Error: the Deleting opperation doesn't contain any file name");
                return null;
            }
            pushByte((byte)(OP_DELRQ >> 8));
            pushByte((byte)(OP_DELRQ & 0xff));
            String usr = temp.substring(6, temp.length());
            for (byte nextByte : usr.getBytes(StandardCharsets.UTF_8)){
                pushByte(nextByte);
            }
            pushByte((byte)(0));
            byte[] result = Arrays.copyOf(bytes, len);
            bytes = new byte[1 << 10];
            len = 0;
            return result;
        }
        else if(comm.equals("RRQ")){
            if(temp.length() <= 4){ //in case no file name have been filled in the RRQ operation
                System.out.println("Error: the Downloading opperation doesn't contain any file name");
                return null;
            }
            pushByte((byte)(OP_RRQ >> 8));
            pushByte((byte)(OP_RRQ & 0xff));
            String usr = temp.substring(4, temp.length());
            for (byte nextByte : usr.getBytes(StandardCharsets.UTF_8)){
                pushByte(nextByte);
            }
            pushByte((byte)(0));
            byte[] result = Arrays.copyOf(bytes, len);
            bytes = new byte[1 << 10];
            len = 0;
            return result;        
        }
        else if(comm.equals("WRQ")){
            if(temp.length() <= 4){ //in case no file name have been filled in the WRQ operation
                System.out.println("Error: the Uploading opperation doesn't contain any file name");
                return null;
            }
            pushByte((byte)(OP_WRQ >> 8));
            pushByte((byte)(OP_WRQ & 0xff));
            String usr = temp.substring(4, temp.length());
            for (byte nextByte : usr.getBytes(StandardCharsets.UTF_8)){
                pushByte(nextByte);
            }
            pushByte((byte)(0));
            byte[] result = Arrays.copyOf(bytes, len);
            bytes = new byte[1 << 10];
            len = 0;
            return result;        
        }
        else if(comm.equals("DIR")){
            pushByte((byte)(OP_DIRQ >> 8));
            pushByte((byte)(OP_DIRQ & 0xff));
            byte[] result = Arrays.copyOf(bytes, len);
            bytes = new byte[1 << 10];
            len = 0;
            return result;        
        }
        else if(comm.equals("DIS")){
            pushByte((byte)(OP_DISC >> 8));
            pushByte((byte)(OP_DISC & 0xff));
            byte[] result = Arrays.copyOf(bytes, len);
            bytes = new byte[1 << 10];
            len = 0;
            return result;  
        }
        else{
            System.out.println("Error: invalid message");
            return null;
        }
    }

    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            bytes = Arrays.copyOf(bytes, len * 2);
        }

        bytes[len++] = nextByte;
    }
}
