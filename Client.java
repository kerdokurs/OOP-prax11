package prax11;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class Client {
  public static void main(final String[] args) throws IOException {
    InetSocketAddress address = new InetSocketAddress("localhost", 42069);

    try (final SocketChannel client = SocketChannel.open(address)) {

      System.out.println("client has started");

      final String[] messages = new String[]{
              "echo", "test1",
              "echo", "test2",
          "file", "Server.java"
      };

      for (int i = 0; i < messages.length; i += 2) {
        final int type = "echo".equals(messages[i]) ? 0 : 1;
        final byte[] message = messages[i + 1].getBytes();
        final int size = message.length;

        ByteBuffer buffer = ByteBuffer.allocate(2 * 4 + size);
        buffer.putInt(type);
        buffer.putInt(size);
        buffer.put(message);
        buffer.flip();

        client.write(buffer);

        read(client);
      }
    }
  }

  private static void read(final SocketChannel client) throws IOException {
    final ByteBuffer infoBuffer = ByteBuffer.allocate(8);
    client.read(infoBuffer);

    infoBuffer.flip();

    final int type = infoBuffer.getInt();
    final int size = infoBuffer.getInt();

    final ByteBuffer messageBuffer = ByteBuffer.allocate(size);
    client.read(messageBuffer);
    messageBuffer.flip();

    final String message = new String(messageBuffer.array());

    System.out.printf("response (%d): %s%n", type, message);
  }
}
