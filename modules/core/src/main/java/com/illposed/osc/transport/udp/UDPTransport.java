package com.illposed.osc.transport.udp;

import com.illposed.osc.OSCPacket;
import com.illposed.osc.OSCParseException;
import com.illposed.osc.OSCSerializeException;
import com.illposed.osc.OSCSerializerAndParserBuilder;
import com.illposed.osc.transport.Transport;
import com.illposed.osc.transport.channel.OSCDatagramChannel;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

/**
 * A {@link Transport} implementation for sending and receiving OSC packets over
 * a network via UDP.
 */
public class UDPTransport implements Transport {
	/**
	 * Buffers were 1500 bytes in size, but were increased to 1536, as this is a common MTU,
	 * and then increased to 65507, as this is the maximum incoming datagram data size.
	 */
	public static final int BUFFER_SIZE = 65507;
	private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

	private final SocketAddress local;
	private final SocketAddress remote;
	private final DatagramChannel channel;
  private final OSCDatagramChannel oscChannel;

	public UDPTransport(
		SocketAddress local,
		SocketAddress remote)
		throws IOException
	{
		this(local, remote, new OSCSerializerAndParserBuilder());
	}

	public UDPTransport(
		SocketAddress local,
		SocketAddress remote,
		OSCSerializerAndParserBuilder serializerAndParserBuilder)
		throws IOException
	{
		this.local = local;
		this.remote = remote;
		final DatagramChannel tmpChannel;
		if (local instanceof InetSocketAddress) {
			final InetSocketAddress localIsa = (InetSocketAddress) local;
			final InetSocketAddress remoteIsa = (InetSocketAddress) remote;
			if (!localIsa.getAddress().getClass().equals(
					remoteIsa.getAddress().getClass()))
			{
				throw new IllegalArgumentException(
						"local and remote addresses are not of the same family"
						+ " (IP v4 vs v6)");
			}
			if (localIsa.getAddress() instanceof Inet4Address) {
				tmpChannel = DatagramChannel.open(StandardProtocolFamily.INET);
			} else if (localIsa.getAddress() instanceof Inet6Address) {
				tmpChannel = DatagramChannel.open(StandardProtocolFamily.INET6);
			} else {
				throw new IllegalArgumentException(
						"Unknown address type: "
						+ localIsa.getAddress().getClass().getCanonicalName());
			}
		} else {
			tmpChannel = DatagramChannel.open();
		}
		this.channel = tmpChannel;

		this.channel.setOption(StandardSocketOptions.SO_SNDBUF, BUFFER_SIZE);
		this.channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
		this.channel.setOption(StandardSocketOptions.SO_BROADCAST, true);
		this.channel.socket().bind(local);
		this.oscChannel = new OSCDatagramChannel(channel, serializerAndParserBuilder);
	}

	public void connect() throws IOException {
		if (remote == null) {
			throw new IllegalStateException(
					"Can not connect a socket without a remote address specified");
		}
		channel.connect(remote);
	}

	public void disconnect() throws IOException {
		channel.disconnect();
	}

	public boolean isConnected() {
		return channel.isConnected();
	}

	public SocketAddress getLocalAddress() {
		return local;
	}

	public SocketAddress getRemoteAddress() {
		return remote;
	}

	/**
	 * Close the socket and free-up resources.
	 * It is recommended that clients call this when they are done with the
	 * port.
	 * @throws IOException If an I/O error occurs on the channel
	 */
	public void close() throws IOException {
		channel.close();
	}

	@Override
	public void send(final OSCPacket packet) throws IOException, OSCSerializeException {
		oscChannel.send(buffer, packet, getRemoteAddress());
	}

	@Override
	public OSCPacket receive() throws IOException, OSCParseException {
		return oscChannel.read(buffer);
	}

	public boolean isBlocking() {
		return channel.isBlocking();
	}

	@Override
	public String toString() {
		return String.format(
			"%s: local=%s, remote=%s", getClass().getSimpleName(), local, remote
		);
	}
}
