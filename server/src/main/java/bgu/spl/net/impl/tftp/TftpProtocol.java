package bgu.spl.net.impl.tftp;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;
import java.util.Arrays;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;


class holders{ //global variable (static)
    static ConcurrentHashMap<Integer, String> ids_login = new ConcurrentHashMap<>(); //to check if to change the boolean 
}

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {

    private boolean shouldTerminate = false;
    private int connectionId;
    private Connections<byte[]> connections; 

    // New Fields
    private String currFile = "";
    private short block = 0;
    private boolean busy = false;
    private boolean RRQ = false;
    private boolean WRQ = false;
    private boolean DIRQ = false;
    private String filesPath = "." + File.separator + "server" + File.separator + "Flies" + File.separator;
    int read = 0;
    private File dir = new File("." + File.separator + "server" + File.separator + "Flies");
    private FileOutputStream fos = null;
    private FileInputStream fis = null;
    byte[] dirqData = new byte[1 << 10];

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
    public void start(int connectionId, ConnectionsImpl<byte[]> connections) {
        // TODO implement this
        this.shouldTerminate = false;
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public void process(byte[] message) {
        // TODO implement this
        
        short opcode = (short) (((short) message[0]) << 8 | (short) (message[1]) & 0x00ff);
        if (!holders.ids_login.containsKey(connectionId)) {
            if (opcode == OP_LOGRQ) {
                byte[] usr = Arrays.copyOfRange(message, 2, message.length-1);
                System.out.println("Received LOGRQ from not logged client - " + new String(usr, StandardCharsets.UTF_8) + " - " + connectionId);
                String username = new String(usr, StandardCharsets.UTF_8);
                if (!holders.ids_login.containsValue(username)) {
                    holders.ids_login.put(this.connectionId, username);
                    //send ack message
                    this.connections.send(this.connectionId, createAckPacket((short) 0));
                }
                else {
                    //send error message user already logged in
                    this.connections.send(this.connectionId, createErrorPacket((short) 7, "Username is taken"));  
                }
            }
            else {
                System.out.println("Received " + opcode + " from not logged client - " + connectionId);
                //send error message not logged in
                this.connections.send(this.connectionId, createErrorPacket((short) 6, "User not logged in"));
            }
        }
        else {
            if (opcode == OP_RRQ) {
                System.out.println("Received RRQ from " + holders.ids_login.get(this.connectionId));
                if (busy) {
                    //send error message other file transfer in progress
                    this.connections.send(this.connectionId, createErrorPacket((short) 2, "Other file transfer in progress"));
                }
                else {
                    byte[] fileName = Arrays.copyOfRange(message, 2, message.length-1);
                    currFile = filesPath + new String(fileName, StandardCharsets.UTF_8);
                    if (new File (currFile).exists()) {
                        RRQ = true;
                        busy = true;
                        try {
                            fis = new FileInputStream(currFile);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        block = 1;
                        sendDataPacketRRQ();          
                    }
                    else {
                        //send error message file not found
                        currFile = "";
                        this.connections.send(this.connectionId, createErrorPacket((short) 1, "File not found"));
                    }
                }
            }
            else if (opcode == OP_WRQ) {
                System.out.println("Received WRQ from " + holders.ids_login.get(this.connectionId));
                if (busy) {
                    //send error message other file transfer in progress
                    this.connections.send(this.connectionId, createErrorPacket((short) 2, "Other file transfer in progress"));
                }
                else{
                    byte[] fileName = Arrays.copyOfRange(message, 2, message.length-1);
                    currFile = new String(fileName, StandardCharsets.UTF_8);
                    if (!(new File (filesPath + currFile).exists())) {
                        busy = true;
                        WRQ = true;
                        this.connections.send(this.connectionId, createAckPacket((short) 0));
                        try {
                            new File (filesPath + currFile).createNewFile();
                            fos = new FileOutputStream(filesPath + currFile, true);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    else {
                        //send error message file exists
                        this.connections.send(this.connectionId, createErrorPacket((short) 5, "File already exists"));
                    }
                }
            }
            else if (opcode == OP_DATA) {
                System.out.println("Received DATA from " + holders.ids_login.get(this.connectionId));
                if (busy && WRQ) {
                    byte[] data = Arrays.copyOfRange(message, 6, message.length);
                    //Check if there's enough space in disk
                    if (data.length >= new File("/").getUsableSpace()) {
                        //send error message disk full
                        this.connections.send(this.connectionId, createErrorPacket((short) 3, "Disk full"));
                        //delete file
                        if (new File(currFile).exists()) {
                            new File(currFile).delete();
                        }
                        reset();
                    }
                    else{
                        byte[] blockNum = Arrays.copyOfRange(message, 4, 6);
                        short blockRec = (short) (((short) blockNum[0]) << 8 | (short) (blockNum[1]) & 0xff);
                        byte[] ack = createAckPacket(blockRec);
                        if (message.length == 2){
                            this.connections.send(this.connectionId, ack);
                            byte[] fileName = currFile.getBytes(StandardCharsets.UTF_8);
                            byte[] bcast = new byte[fileName.length + 4];
                            bcast[0] = (byte) (OP_BCAST >> 8);
                            bcast[1] = (byte) (OP_BCAST & 0xff);
                            bcast[2] = (byte) (1);
                            System.arraycopy(fileName, 0, bcast, 3, fileName.length);
                            bcast[bcast.length - 1] = (byte) (0);
                            reset();
                            for(Integer k : holders.ids_login.keySet()){
                                connections.send(k, bcast);
                            }
                        }
                        else {
                            try {
                                fos.write(data);
                                fos.flush();
                            } catch (Exception e) {
                                e.printStackTrace();
                                //send error message Access violation - Failed to write to file
                                this.connections.send(this.connectionId, createErrorPacket((short) 2, "Access violation - Failed to write to file"));
                                //delete file
                                if (new File(currFile).exists()) {
                                    new File(currFile).delete();
                                }
                                return;
                            }
        
                            this.connections.send(this.connectionId, ack);
        
                            if (data.length < 512) {
                                byte[] fileName = currFile.getBytes(StandardCharsets.UTF_8);
                                byte[] bcast = new byte[fileName.length + 4];
                                bcast[0] = (byte) (OP_BCAST >> 8);
                                bcast[1] = (byte) (OP_BCAST & 0xff);
                                bcast[2] = (byte) (1);
                                System.arraycopy(fileName, 0, bcast, 3, fileName.length);
                                bcast[bcast.length - 1] = (byte) (0);
                                reset();
                                for(Integer k : holders.ids_login.keySet()){
                                    connections.send(k, bcast);
                                }
                            }
                        }
                    }
                }
                else {
                    //send error message not defined
                    this.connections.send(this.connectionId, createErrorPacket((short) 0, "Not Waiting for DATA"));
                }
            }
            else if (opcode == OP_ACK) {
                System.out.println("Received ACK from " + holders.ids_login.get(this.connectionId));
                if (!busy) {
                    //send error message not defined
                    this.connections.send(this.connectionId, createErrorPacket((short) 0, "Not Waiting for ACK"));
                }
                else {
                    if (block > 0) {
                        byte[] blockNum = Arrays.copyOfRange(message, 2, 4);
                        short blockRec = (short) (((short) blockNum[0]) << 8 | (short) (blockNum[1]) & 0xff);
                        if (blockRec == block) {
                            block++;
                            if (read == 0) {
                                reset();
                            }
                            else {
                                if (RRQ == true) {
                                    sendDataPacketRRQ();
                                }
                                else if (DIRQ == true) {
                                    sendDataPacketDIRQ();
                                }
                                else {
                                    //send error message not defined
                                    this.connections.send(this.connectionId, createErrorPacket((short) 0, "Not Waiting for ACK"));
                                }
                            }
                        }
                        else {
                            //send error message wrong block number
                            this.connections.send(this.connectionId, createErrorPacket((short) 0, "Wrong block number"));
                        }
                    }
                    else {
                        busy = false;
                    }
                }
            }
            else if (opcode == OP_ERROR) {
                System.out.println("Received ERROR from " + holders.ids_login.get(this.connectionId));
                if (!busy) {
                    //send error undefined error
                    this.connections.send(this.connectionId, createErrorPacket((short) 0, "Not Waiting for ERROR"));
                }
                else {
                    short errorCode = (short) (((short) message[2]) << 8 | (short) (message[3]) & 0xff);
                    byte[] errorMsg = Arrays.copyOfRange(message, 4, message.length-1);
                    String error = new String(errorMsg, StandardCharsets.UTF_8);
                    if (WRQ  & new File(currFile).exists() & errorCode == 0 & error.contains("block number")) {
                        System.out.println("Error " + errorCode + ": " + error);
                        new File(currFile).delete();
                        reset();
                    }
                    else if (RRQ && (errorCode == 3 || errorCode == 2)) {
                        System.out.println("Error " + errorCode + ": " + error);
                        reset();
                    }
                    else {
                        //send error undefined error
                        this.connections.send(this.connectionId, createErrorPacket((short) 0, "Undefined error"));
                    }
                }
            }
            else if (opcode == OP_DIRQ) {
                System.out.println("Received DIRQ from " + holders.ids_login.get(this.connectionId));
                if (!busy) {
                    File[] files = dir.listFiles();
                    int len = 0;
                    if (files != null) {
                        for (File f : files) {
                            byte[] fileName = (f.getName() + '0').getBytes();
                            while (fileName.length + len > dirqData.length) {
                                dirqData = Arrays.copyOf(dirqData, dirqData.length * 2);
                            }
                            System.arraycopy(fileName, 0, dirqData, len, fileName.length);
                            len += fileName.length;
                        }
                        dirqData = Arrays.copyOf(dirqData, len - 1);
                        busy = true;
                        DIRQ = true;
                        block = 1;
                        sendDataPacketDIRQ();
                    }
                    else {
                        //send error message directory is empty
                        this.connections.send(this.connectionId, createErrorPacket((short) 7, "Directory is empty"));
                    }
                }
                else {
                    //send error undefined error
                    this.connections.send(this.connectionId, createErrorPacket((short) 0, "Transfer in progress"));
                }
                
            }
            else if (opcode == OP_DELRQ) {
                System.out.println("Received DELRQ from " + holders.ids_login.get(this.connectionId));
                if (!busy) {
                    byte[] fileName = Arrays.copyOfRange(message, 2, message.length-1);
                    currFile = new String(fileName, StandardCharsets.UTF_8);
                    File file = new File(filesPath + currFile);
                    if (file.exists()) {
                        file.delete();
                        byte[] ack = createAckPacket((short) 0);
                        this.connections.send(this.connectionId, ack);
                        byte[] bcast = new byte[message.length + 1];
                        bcast[0] = (byte) (OP_BCAST >> 8);
                        bcast[1] = (byte) (OP_BCAST & 0xff);
                        bcast[2] = (byte) (0);
                        System.arraycopy(fileName, 0, bcast, 3, fileName.length);
                        bcast[bcast.length - 1] = (byte) (0);
                        for(Integer k : holders.ids_login.keySet()){
                            connections.send(k, bcast);
                        }
                    }
                    else {
                        //send error message file not found
                        this.connections.send(this.connectionId, createErrorPacket((short) 1, "File not found"));
                    }
                }
                else {
                    //send error undefined error
                    this.connections.send(this.connectionId, createErrorPacket((short) 0, "Transfer in progress"));
                }
            }

            else if (opcode == OP_DISC) {
                System.out.println("Received DISC from " + holders.ids_login.get(this.connectionId));
                shouldTerminate = true;
                shouldTerminate();
            }
            else if (opcode == OP_LOGRQ) {
                System.out.println("Received LOGRQ from logged user - " + holders.ids_login.get(this.connectionId));
                //send error message user already logged in
                this.connections.send(this.connectionId, createErrorPacket((short) 7, "User already logged in"));
            }

            else {
                //send error message illegal TFTP operation
                this.connections.send(this.connectionId, createErrorPacket((short) 4, "Illegal TFTP operation"));
            }
        }
    }

    @Override
    public boolean shouldTerminate() {
        // TODO implement this
        if (shouldTerminate == true) {
            byte[] ack = createAckPacket((short) 0);
            reset();
            this.connections.send(this.connectionId, ack);
            this.connections.disconnect(this.connectionId);
            holders.ids_login.remove(this.connectionId);
        }
        return shouldTerminate;
    } 

    // New Methods
    private void sendDataPacketRRQ () {
        byte[] data = new byte[512];
        read = 0;
        try {
            read = fis.read(data);
            if (read == -1) {
                read = 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        byte[] dataPacket = new byte[read + 6];
        short packetS = (short) (read);
        byte[] packetSize = new byte[] {(byte) (packetS >> 8), (byte) (packetS & 0xff)};
        byte[] blockNum = new byte[] {(byte) (block >> 8), (byte) (block & 0xff)};
        dataPacket[0] = (byte) (OP_DATA >> 8);
        dataPacket[1] = (byte) (OP_DATA & 0xff);
        dataPacket[2] = packetSize[0];
        dataPacket[3] = packetSize[1];
        dataPacket[4] = blockNum[0];
        dataPacket[5] = blockNum[1];
        if (read != 0) {
            System.arraycopy(data, 0, dataPacket, 6, read);
        }
        if (read < 512) {
            read = 0;
        }
        this.connections.send(this.connectionId, dataPacket);
    }

    private void sendDataPacketDIRQ () {
        byte[] data;
        if (dirqData.length - read <= 512) {
            data = new byte[dirqData.length - read + 6];
            System.arraycopy(dirqData, read, data, 6, dirqData.length - read);
            read = 0;
        }
        else {
            data = new byte[518];
            System.arraycopy(dirqData, read, data, 6, 512);
            read += 512;
        }
        short packetS = (short) (data.length - 6);
        byte[] packetSize = new byte[] {(byte) (packetS >> 8), (byte) (packetS & 0xff)};
        byte[] blockNum = new byte[] {(byte) (block >> 8), (byte) (block & 0xff)};
        data[0] = (byte) (OP_DATA >> 8);
        data[1] = (byte) (OP_DATA & 0xff);
        data[2] = packetSize[0];
        data[3] = packetSize[1];
        data[4] = blockNum[0];
        data[5] = blockNum[1];
        this.connections.send(this.connectionId, data);
    }

    private void reset() {
        currFile = "";
        block = 0;
        busy = false;
        RRQ = false;
        WRQ = false;
        DIRQ = false;
        read = 0;
        dirqData = new byte[1 << 10];
        if (fis != null) {
            try {
                fis.close();
                fis = null;
            } catch (Exception e) {
                e.printStackTrace();
                fis = null;
            }
        }
        if (fos != null) {
            try {
                fos.close();
                fos = null;
            } catch (Exception e) {
                e.printStackTrace();
                fis = null;
            }
        }
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
}
