package com.eh2.reverseproxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

public class EchoServer {
	private static final String QUIT_MESSAGE = "tchau";
	private static final int port = 8800;

	public void startServer(int localPort) throws IOException {
		Selector selector = Selector.open();
		ServerSocketChannel serverSocket = ServerSocketChannel.open();
		serverSocket.bind(new InetSocketAddress(localPort));
		serverSocket.configureBlocking(false);
		serverSocket.register(selector, SelectionKey.OP_ACCEPT);

		System.out.println("Echo server started on port " + localPort);

		while (true) {
			selector.select();
			Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

			while (keys.hasNext()) {
				SelectionKey key = keys.next();
				keys.remove();

				if (!key.isValid())
					continue;

				if (key.isAcceptable()) {
					accept(key);
				} else if (key.isReadable()) {
					echo(key);
				}
			}
		}
	}

	private void accept(SelectionKey key) throws IOException {
		ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
		SocketChannel clientChannel = serverChannel.accept();
		clientChannel.configureBlocking(false);
		clientChannel.register(key.selector(), SelectionKey.OP_READ);

		System.out.println("Connection accepted: " + clientChannel.getRemoteAddress());
	}

	private void echo(SelectionKey key) throws IOException {
		SocketChannel clientChannel = (SocketChannel) key.channel();
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		int bytesRead = clientChannel.read(buffer);

		if (bytesRead == -1) {
			System.out.println("Client disconnected: " + clientChannel.getRemoteAddress());
			clientChannel.close();
			key.cancel();
			return;
		}

		buffer.flip();
		byte[] data = new byte[buffer.remaining()];
		buffer.get(data);
		String receivedMessage = new String(data);

		System.out.println("Menagem recebida" + receivedMessage);

		String echoMessage = "echo: " + receivedMessage;
		ByteBuffer echoBuffer = ByteBuffer.wrap(echoMessage.getBytes());

		clientChannel.write(echoBuffer);

		buffer.clear();

		System.out.println("Echoed " + echoMessage.length() + " bytes to " + clientChannel.getRemoteAddress());
		if (receivedMessage.trim().equalsIgnoreCase(QUIT_MESSAGE)) {
			clientChannel.close();

		}
	}

	public static void main(String[] args) throws IOException {
		new EchoServer().startServer(port);
	}
}
