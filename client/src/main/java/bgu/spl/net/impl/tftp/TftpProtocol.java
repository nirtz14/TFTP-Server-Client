package bgu.spl.net.impl.tftp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import bgu.spl.net.api.MessagingProtocol;


public class TftpProtocol implements MessagingProtocol<byte[]> {

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

    private boolean shouldTerminate = false;
    private String dirq = "";
    private String currFile = "";
    private short block = 0;
    private boolean RRQ = false;
    private boolean WRQ = false;
    private boolean DIRQ = false;
    private boolean DISC = false;
    private String filesPath = "." + File.separator + "client" + File.separator;
    int read = 0;
    private FileOutputStream fos = null;
    private FileInputStream fis = null;
    byte[] dirqData = new byte[1024];

    @Override
    public byte[] process(byte[] message) {
        short opcode = (short) (((short) message[0]) << 8 | (short) (message[1]) & 0xff);
        if(opcode == OP_ACK){
            if(WRQ){
                return sendDataPacketWRQ();               
            }
            else if (DISC){
                shouldTerminate = true;
                synchronized(this) {
                    this.notifyAll();
                }
            }
            else{
                synchronized(this) {
                    this.notifyAll();
                }
                return null;
            }
        }
        else if (opcode == OP_DATA) {
            short blocknum = (short) (((short) message[4]) << 8 | (short) (message[5]) & 0x00ff);
            byte[] ack = createAckPacket(blocknum);
            if (RRQ) {
                if (block == blocknum) {
                    try {
                        fos.write(message, 6, message.length - 6);
                        fos.flush();
                        block++;
                        if (message.length < 518) { //512 + 6
                            fos.close();
                            RRQ = false;
                            block = 0;
                            System.out.println("RRQ " + currFile.substring(9) + " complete");
                            synchronized (this) {
                                this.notifyAll();
                            }
                        }
                        return ack;
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println("Downloading " + currFile +" has failed");
                        if (new File(currFile).exists()) {
                            new File(currFile).delete();
                        }
                        RRQ = false;
                        block = 0;
                        synchronized (this) {
                            this.notifyAll();
                        }
                        return createErrorPacket((short) 0, "Failed to write to file");
                    }
                }
                else {
                    RRQ = false;
                    block = 0;
                    System.out.println("Downloading " + currFile +" has failed");
                    if (new File(currFile).exists()) {
                        new File(currFile).delete();
                    }
                    synchronized (this) {
                        this.notifyAll();
                    }
                    return createErrorPacket((short) 0, "Wrong block number");
                }
            }
            else if (DIRQ) {
                if (block == blocknum) {
                    byte[] temp = new byte[message.length-6];
                    System.arraycopy(message, 6, temp, 0, temp.length);
                    dirq += new String(temp, StandardCharsets.UTF_8);
                    block++;
                    if (message.length < 518) { //512 + 6
                        dirq = dirq.replace('0', '\n');
                        System.out.println(dirq);
                        dirq = "";
                        DIRQ = false;
                        block = 0;
                        synchronized (this) {
                            this.notifyAll();
                        }
                    }
                    return ack;
                }
                else {
                    block = 0;
                    DIRQ = false;
                    dirq = "";
                    System.out.println("Transfer has failed");
                    synchronized (this) {
                        this.notifyAll();
                    }
                    return createErrorPacket((short) 0, "Wrong block number");
                }
            }
            else {
                    System.out.println("not waiting for any data packet");
                    synchronized (this) {
                        this.notifyAll();
                    }
                    return createErrorPacket((short) 0, "Not waiting for any data packet");              
            } 
        }
        else if(opcode == OP_ERROR){
            short errorCode = (short) (((short) message[2]) << 8 | (short) (message[3]) & 0xff);
            byte[] errorMsg = Arrays.copyOfRange(message, 4, message.length-1); 
            String error = new String(errorMsg, StandardCharsets.UTF_8);
            System.out.println("Error " + errorCode + " " + error);
            reset();
            synchronized(this) {
                this.notifyAll();
            }
            return null;
        }
        else if(opcode == OP_BCAST){
            short bcastCode = message[2];
            byte[] bcastMsg = Arrays.copyOfRange(message, 3, message.length-1); 
            String bcast = new String(bcastMsg, StandardCharsets.UTF_8);
            String addel = "";
            if (bcastCode == 0) {
                addel = "del ";
            }
            else {
                addel = "add ";
            }
            System.out.println("BCAST " + addel + bcast);
            synchronized(this) {
                this.notifyAll();
            }
            return null;
        }
        return null;
    }

    @Override
    public boolean shouldTerminate(){
        return shouldTerminate;
    }

    public byte[] setCond(byte[] msg) {
        short opcode = (short) (((short) msg[0]) << 8 | (short) (msg[1]) & 0x00ff);
        if (opcode == OP_RRQ) {
            byte[] temp = new byte[msg.length - 3];
            RRQ = true;
            System.arraycopy(msg, 2, temp, 0, temp.length);
            String fileName = new String(temp, StandardCharsets.UTF_8);
            if (new File (filesPath + fileName).exists()) {
                System.out.println("Error: File aready exists");
                RRQ = false;
                return null;
            }
            else {
                try {
                    currFile = filesPath + fileName;
                    block = 1;
                    new File(currFile).createNewFile();
                    fos = new FileOutputStream(currFile);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Error: Access violation, could not open file for writing");
                    return null;
                }
            }
        } 
        else if (opcode == OP_WRQ) {
            byte[] temp = new byte[msg.length - 3];
            System.arraycopy(msg, 2, temp, 0, temp.length);
            String fileName = new String(temp, StandardCharsets.UTF_8);
            if (!new File (filesPath + fileName).exists()) {
                System.out.println("Error: File does not exists");
                return null;
            }
            else {
                try {
                    currFile = filesPath + fileName;
                    block = 1;
                    WRQ = true;
                    fis = new FileInputStream(currFile);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Error: Acess violation, could not open file for reading");
                    return null;
                }
            }
        } 
        else if (opcode == OP_DIRQ) {
            DIRQ = true;
            block = 1;
        }
        else if (opcode == OP_DISC) {
            DISC = true;
        }
        return msg;
    }

    private byte[] sendDataPacketWRQ () {
        byte[] data = new byte[512];
        read = 0;
        try {
            read = fis.read(data);
            if (read == -1) {
                read = 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
            // send Error + print - access violation
            System.out.println("Error: Access violation, Failed to read from file");
            reset();   
            return createErrorPacket((short) 2, "Failed to read from file");
        }
        byte[] dataPacket = new byte[read + 6];
        byte[] packetSize = new byte[] {(byte) ((short) read >> 8), (byte) ((short) read & 0x00ff)}; 
        byte[] blockNum = new byte[] {(byte) (block >> 8), (byte) (block & 0x00ff)};
        dataPacket[0] = (byte) (OP_DATA >> 8);
        dataPacket[1] = (byte) (OP_DATA & 0x00ff);
        dataPacket[2] = packetSize[0];
        dataPacket[3] = packetSize[1];
        dataPacket[4] = blockNum[0];
        dataPacket[5] = blockNum[1];
        block ++;
        if (read != 0) {
            System.arraycopy(data, 0, dataPacket, 6, read);
        }
        if (read < 512) {
            reset();
        }
        return dataPacket;
    }

    private byte[] createErrorPacket(short errorCode, String errorMsg) {
        byte[] msg = errorMsg.getBytes(StandardCharsets.UTF_8);
        byte[] error = new byte[5 + msg.length];
        error[0] = (byte) (OP_ERROR >> 8);
        error[1] = (byte) (OP_ERROR & 0xff);
        error[2] = (byte) (errorCode >> 8);
        error[3] = (byte) (errorCode & 0xff);
        System.arraycopy(msg, 0, error, 4, msg.length);
        error[error.length - 1] = (byte) (0);
        return error;
    }

    private byte[] createAckPacket(short blockNum) {
        byte[] ack = new byte[4];
        ack[0] = (byte) (OP_ACK >> 8);
        ack[1] = (byte) (OP_ACK & 0xff);
        ack[2] = (byte) (blockNum >> 8);
        ack[3] = (byte) (blockNum & 0xff);
        return ack;
    }

    private void reset() {
        WRQ = false;
        RRQ = false;
        DIRQ = false;
        block = 0;
        read = 0;
        currFile = "";
        dirq = "";
        if (fis != null) {
            try {
                fis.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            fis = null;
        }
        if (fos != null) {
            try {
                fos.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            fos = null;
        }
    }
}
