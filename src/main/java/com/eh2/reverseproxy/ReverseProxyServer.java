package com.eh2.reverseproxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class ReverseProxyServer {
    private static final int port = 8800;
    private String targetHost = "localhost";
    private int targetPort = 8000;

    public void startServer(int localPort) throws IOException {
        Selector selector = Selector.open();
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        serverSocket.bind(new InetSocketAddress(localPort));
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Proxy server started on port " + localPort);

        while (true) {
            selector.select();
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (!key.isValid()) {
                    continue;
                }

                if (key.isAcceptable()) {
                    System.out.println("Accepting connection");
                    accept(key);
                } else if (key.isReadable()) {
                    System.out.println("Reading data");
                    relay(key); // Gerencia o fluxo de dados entre cliente e target.
                }
            }
        }
    }

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);

        SocketChannel targetChannel = SocketChannel.open();
        targetChannel.connect(new InetSocketAddress(targetHost, targetPort++));
        targetChannel.configureBlocking(false);

        // Associa o cliente ao target e o target ao cliente usando o attachment do SelectionKey.
        clientChannel.register(key.selector(), SelectionKey.OP_READ, targetChannel);
        targetChannel.register(key.selector(), SelectionKey.OP_READ, clientChannel);

        System.out.println("Connection accepted: " + clientChannel.getRemoteAddress());
    }

    private void relay(SelectionKey key) throws IOException {
        SocketChannel fromChannel = (SocketChannel) key.channel(); // Canal de onde os dados serão lidos.
        SocketChannel toChannel = (SocketChannel) key.attachment(); // Canal para onde os dados serão enviados.

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = fromChannel.read(buffer);

        if (bytesRead == -1) {
            System.out.println("No more data, closing connection");
            closeConnection(key);
            return;
        }

        buffer.flip();
        
        // Escrevendo dados do cliente para o target:
        if (key.attachment() instanceof SocketChannel && toChannel != null) {
            if (fromChannel.socket().getLocalPort() == port) {
                System.out.println("Relaying data from client to target (" +fromChannel.socket().getPort()+"): " + bytesRead + " bytes");
            } else {
                System.out.println("Relaying data from target to client (" +fromChannel.socket().getPort()+"): " + bytesRead + " bytes");
            }
        }

        toChannel.write(buffer); // Envia os dados para o canal de destino.
        buffer.compact(); // Prepara o buffer para a próxima leitura.

        System.out.println("Data relayed (" + toChannel.socket().getPort()+ "): " + bytesRead + " bytes");
    }

    private void closeConnection(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        SocketChannel targetChannel = (SocketChannel) key.attachment();
        System.out.println("Closing connection: " + channel.getRemoteAddress());

        channel.close();
        if (targetChannel != null) {
            targetChannel.close();
        }
    }

    public static void main(String[] args) throws IOException {
        new ReverseProxyServer().startServer(port);
    }
}
