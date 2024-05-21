package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ListeningThread extends Thread {
    private final TftpEncoderDecoder encdec;
    private final TftpProtocol protocol;
    private final BufferedInputStream in;
    private final BufferedOutputStream out;

    public ListeningThread(BufferedInputStream in ,BufferedOutputStream out , TftpEncoderDecoder encdec, TftpProtocol protocol) throws IOException {
        this.encdec = encdec;
        this.protocol = protocol;
        this.in = in;
        this.out = out;
    }

    @Override
    public void run() {
        int read;
        try{
            while (!protocol.shouldTerminate() && (read = in.read()) >= 0) {
                byte[] nextMessage = encdec.decodeNextByte((byte) read);
                if (nextMessage != null) {
                    byte[] result = protocol.process(nextMessage);
                    if(result != null){
                        out.write(result);
                        out.flush();
                   }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            }
    }
}
