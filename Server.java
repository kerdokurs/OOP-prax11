package prax11;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Server {
  private final InetSocketAddress address;
  private final Map<SocketChannel, Queue<ByteBuffer>> dataMapper = new HashMap<>();

  private Selector selector;

  public Server(final String address, final int port) {
    this.address = new InetSocketAddress(address, port);
  }

  public static void main(final String[] args) throws IOException {
    new Server("localhost", 42069).start();
  }

  private void start() throws IOException {
    selector = Selector.open();
    final ServerSocketChannel serverChannel = ServerSocketChannel.open();
    serverChannel.configureBlocking(false);

    serverChannel.socket().bind(address);
    serverChannel.register(selector, SelectionKey.OP_ACCEPT);

    System.out.println("server has started");

    while (true) {
      selector.select();

      final Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

      while (keys.hasNext()) {
        final SelectionKey key = keys.next();

        keys.remove();

        if (!key.isValid()) continue;

        if (key.isAcceptable()) accept(key);
        if (key.isReadable()) read(key);
        if (key.isWritable()) write(key);
      }
    }
  }

  private void accept(final SelectionKey key) throws IOException {
    final ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
    final SocketChannel channel = serverChannel.accept();
    channel.configureBlocking(false);
    final Socket socket = channel.socket();
    final SocketAddress remoteAddr = socket.getRemoteSocketAddress();

    System.out.printf("connected to: %s%n", remoteAddr);

    dataMapper.put(channel, new ArrayDeque<>(10));
    channel.register(this.selector, SelectionKey.OP_READ);
  }

  private void read(final SelectionKey key) throws IOException {
    final SocketChannel channel = (SocketChannel) key.channel();

    final ByteBuffer infoBuffer = ByteBuffer.allocate(8);

    int numRead = channel.read(infoBuffer);

    if (numRead == -1) {
      final Socket socket = channel.socket();
      SocketAddress remoteAddr = socket.getRemoteSocketAddress();

      System.out.printf("connection closed by client: %s%n", remoteAddr);

      channel.close();
      key.cancel();

      return;
    }

    final int type = infoBuffer.getInt();
    final int size = infoBuffer.getInt();

    final ByteBuffer messageBuffer = ByteBuffer.allocate(size);
    channel.read(messageBuffer);
    final String message = new String(messageBuffer.array());

    parse(channel, type, message);
  }

  private void parse(final SocketChannel channel, final int type, final String message) throws ClosedChannelException {
    System.out.printf("parsing data (%d): %s%n", type, message);
    switch (type) {
      case 0 -> handleEcho(channel, message);
      case 1 -> handleFile(channel, message);
      default -> handleDefault(channel);
    }
  }

  private void handleFile(final SocketChannel channel, final String message) throws ClosedChannelException {
    final Path path = Path.of(message);

    try {
      final byte[] bytes = Files.readAllBytes(path);
      handle(channel, bytes, 0);
    } catch (final IOException e) {
      handle(channel, "error loading file data".getBytes(), -1);
    }
  }

  private void handleEcho(final SocketChannel channel, final String message) throws ClosedChannelException {
    final byte[] messageBytes = message.getBytes();
    handle(channel, messageBytes, 0);
  }

  private void handleDefault(final SocketChannel channel) throws ClosedChannelException {
    handleEcho(channel, "invalid request");
  }

  private void handle(final SocketChannel channel, final byte[] bytes, final int type) throws ClosedChannelException {
    final int size = bytes.length;

    final ByteBuffer buffer = ByteBuffer.allocate(2 * 4 + size);
    buffer.putInt(type);
    buffer.putInt(size);
    buffer.put(bytes);

    final Queue<ByteBuffer> current = dataMapper.get(channel);
    current.add(buffer);
    dataMapper.put(channel, current);

    channel.register(selector, SelectionKey.OP_WRITE);
  }

  private void write(final SelectionKey key) throws IOException {
    final SocketChannel channel = (SocketChannel) key.channel();

    final Queue<ByteBuffer> buffers = dataMapper.get(channel);
    if (buffers == null) return;

    final ByteBuffer current = buffers.poll();
    if (current == null) return;

    channel.write(current);
  }
}
