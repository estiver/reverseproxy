package com.eh2.reverseproxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class ReverseProxyServerMThread {
	private static final int port = 9091;
	private String targetHost = "localhost";
	private int targetPort = 9092;
	
	private ExecutorService executorService;
	private final ReentrantLock lock = new ReentrantLock();

	public ReverseProxyServerMThread() {
		executorService = Executors.newCachedThreadPool();
	}

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
					executorService.submit(() -> {
						try {
							accept(key);
						} catch (IOException e) {
							e.printStackTrace();
						}
					});
				} else if (key.isReadable()) {
					System.out.println("Reading data");
					executorService.submit(() -> {
						relay(key);
					});
				}
			}
		}
	}

	private void relay(SelectionKey key) {
		lock.lock();
		try {
			SocketChannel fromChannel = (SocketChannel) key.channel();
			SocketChannel toChannel = (SocketChannel) key.attachment();

			if (fromChannel.isOpen() && toChannel.isOpen()) {
				ByteBuffer buffer = ByteBuffer.allocate(1024);
				int bytesRead = fromChannel.read(buffer);

				if (bytesRead == -1) {
					System.out.println("No more data, closing connection");
					closeConnection(key);
					return;
				}

				buffer.flip();

				if (key.attachment() instanceof SocketChannel && toChannel != null) {
					if (fromChannel.socket().getLocalPort() == port) {
						System.out.println("Relaying data from client to target: " + bytesRead + " bytes");
					} else {
						System.out.println("Relaying data from target to client: " + bytesRead + " bytes");
					}
				}

				int bytesWritten = toChannel.write(buffer);
				buffer.compact();

				System.out.println(
						"Relaying data from " + (fromChannel == toChannel ? "client to target" : "target to client")
								+ ": " + bytesRead + " bytes read, " + bytesWritten + " bytes written");

			} else {
				System.out.println("Channel is closed, unable to relay data");
				closeConnection(key);
			}
		} catch (IOException e) {
			System.out.println("I/O error: " + e.getMessage());
		} finally {
			lock.unlock();
		}
	}

	private void accept(SelectionKey key) throws IOException {
		ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
		SocketChannel clientChannel = serverChannel.accept();
		clientChannel.configureBlocking(false);

		SocketChannel targetChannel = SocketChannel.open();
		targetChannel.connect(new InetSocketAddress(targetHost, targetPort));
		targetChannel.configureBlocking(false);

		clientChannel.register(key.selector(), SelectionKey.OP_READ, targetChannel);
		targetChannel.register(key.selector(), SelectionKey.OP_READ, clientChannel);

		System.out.println("Connection accepted: " + clientChannel.getRemoteAddress());
	}

	private void closeConnection(SelectionKey key) throws IOException {
		SocketChannel channel = (SocketChannel) key.channel();
		SocketChannel targetChannel = (SocketChannel) key.attachment();

		channel.close();
		if (targetChannel != null) {
			targetChannel.close();
		}
	}

	public static void main(String[] args) throws IOException {
		new ReverseProxyServerMThread().startServer(port);
	}
}
