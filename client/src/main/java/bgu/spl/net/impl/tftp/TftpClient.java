package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

public class TftpClient {
    //TODO: implement the main logic of the client, when using a thread per client the main logic goes here
  
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            args = new String[]{"localhost", "hello"};
        }

        try(Socket sock = new Socket(args[0], 7777)){
            BufferedInputStream in = new BufferedInputStream(sock.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream());
            TftpEncoderDecoder endec = new TftpEncoderDecoder();
            TftpProtocol protocol = new TftpProtocol();
            KeyboardThread keyboard = new KeyboardThread(in, out, endec, protocol);
            ListeningThread listening = new ListeningThread(in, out, endec, protocol);
            listening.start();
            keyboard.start();
            System.out.println("Client started!");
            listening.join(); 
            keyboard.join();
            System.out.println("Client terminated!");
        }catch(InterruptedException e){}
    }
}
