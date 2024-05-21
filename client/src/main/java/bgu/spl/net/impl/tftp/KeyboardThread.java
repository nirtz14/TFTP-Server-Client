package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.lang.Thread;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class KeyboardThread extends Thread {
   
    private final TftpEncoderDecoder encdec;
    private final TftpProtocol protocol;
    private final BufferedInputStream in;
    private final BufferedOutputStream out;

    public KeyboardThread(BufferedInputStream in ,BufferedOutputStream out , TftpEncoderDecoder encdec, TftpProtocol protocol) throws IOException {
        this.encdec = encdec;
        this.protocol = protocol;
        this.in = in;
        this.out = out;
    }

    @Override 
    public void run() {
        Scanner scan = new Scanner(System.in);
        while(!protocol.shouldTerminate()){
            String input = scan.nextLine();
            byte[] msg = encdec.encode(input.getBytes(StandardCharsets.UTF_8));
            if(msg != null){
                msg = protocol.setCond(msg);
                if (msg != null) {
                    try{
                        out.write(msg);
                        out.flush();
                        // maybe add wait here until allowed to send a new request
                        synchronized (protocol) {
                            protocol.wait();
                        }
                    }catch(Exception e){}
                }
            }
            //else{
            //    System.out.println("Error: invalid message");
            //}
        }
        scan.close();
    }
}
