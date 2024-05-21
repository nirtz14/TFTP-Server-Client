package bgu.spl.net.srv;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;

class connectionsHolder{ //global variable (static)
    static ConcurrentHashMap<Integer, ConnectionHandler> connections = new ConcurrentHashMap<>(); 
}

public class ConnectionsImpl<T> implements Connections<T> {

    
    
    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler) {
        connectionsHolder.connections.put(connectionId, handler);
    }

    @Override
    public boolean send(int connectionId, T msg) {
        if(connectionsHolder.connections.containsKey(connectionId)){
            connectionsHolder.connections.get(connectionId).send(msg);
            return true;
        }
        return false;
    }

    @Override
    public void disconnect(int connectionId) {
        connectionsHolder.connections.remove(connectionId);
    }
}
