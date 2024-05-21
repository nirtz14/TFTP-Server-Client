package bgu.spl.net.impl.tftp;
import bgu.spl.net.srv.Server;


public class TftpServer {

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

    public static void main(String[] args) {
        //TODO: Implement this
        Server.threadPerClient(
        7777, //port
        () -> new TftpProtocol(), //protocol factory
        TftpEncoderDecoder::new //message encoder decoder factory
        ).serve();
        }
}
