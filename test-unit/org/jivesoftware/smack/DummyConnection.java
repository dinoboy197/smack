/**
 * $RCSfile$
 * $Revision: 11633 $
 * $Date: 2010-02-16 02:33:16 -0600 (Tue, 16 Feb 2010) $
 *
 * Copyright 2010 Jive Software.
 *
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.smack;

import java.util.Date;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;

/**
 * A dummy implementation of {@link Connection}, intended to be used during
 * unit tests.
 * 
 * Instances store any packets that are delivered to be send using the
 * {@link #sendPacket(Packet)} method in a blocking queue. The content of this queue
 * can be inspected using {@link #getSentPacket()}. Typically these queues are
 * used to retrieve a message that was generated by the client.
 * 
 * Packets that should be processed by the client to simulate a received stanza
 * can be delivered using the {@linkplain #processPacket(Packet)} method.
 * It invokes the registered packet interceptors and listeners.
 * 
 * @see Connection
 * @author Guenther Niess
 */
public class DummyConnection extends Connection {

    private boolean authenticated = false;
    private boolean anonymous = false;

    private String user;
    private String connectionID;
    private Roster roster;

    private final BlockingQueue<Packet> queue = new LinkedBlockingQueue<Packet>();

    public DummyConnection() {
        super(new ConnectionConfiguration("example.com"));
    }

    public DummyConnection(ConnectionConfiguration configuration) {
        super(configuration);
    }

    @Override
    public void connect() throws XMPPException {
        connectionID = "dummy-" + new Random(new Date().getTime()).nextInt();
    }

    @Override
    public void disconnect(Presence unavailablePresence) {
        user = null;
        connectionID = null;
        roster = null;
        authenticated = false;
        anonymous = false;
    }

    @Override
    public String getConnectionID() {
        if (!isConnected()) {
            return null;
        }
        if (connectionID == null) {
            connectionID = "dummy-" + new Random(new Date().getTime()).nextInt();
        }
        return connectionID;
    }

    @Override
    public Roster getRoster() {
        if (isAnonymous()) {
            return null;
        }
        if (roster == null) {
            roster = new Roster(this);
        }
        return roster;
    }

    @Override
    public String getUser() {
        if (user == null) {
            user = "dummy@" + config.getServiceName() + "/Test";
        }
        return user;
    }

    @Override
    public boolean isAnonymous() {
        return anonymous;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public boolean isSecureConnection() {
        return false;
    }

    @Override
    public boolean isUsingCompression() {
        return false;
    }

    @Override
    public void login(String username, String password, String resource)
            throws XMPPException {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to server.");
        }
        if (isAuthenticated()) {
            throw new IllegalStateException("Already logged in to server.");
        }
        user = (username != null ? username : "dummy")
                + "@"
                + config.getServiceName()
                + "/" 
                + (resource != null ? resource : "Test");
        roster = new Roster(this);
        anonymous = false;
        authenticated = true;
    }

    @Override
    public void loginAnonymously() throws XMPPException {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to server.");
        }
        if (isAuthenticated()) {
            throw new IllegalStateException("Already logged in to server.");
        }
        anonymous = true;
        authenticated = true;
    }

    @Override
    public void sendPacket(Packet packet) {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to server.");
        }
        if (packet == null) {
            throw new NullPointerException("Packet is null.");
        }
        firePacketInterceptors(packet);
        if (DEBUG_ENABLED) {
            System.out.println("[SEND]: " + packet.toXML());
        }
        queue.add(packet);
        firePacketSendingListeners(packet);
    }

    /**
     * Returns the number of packets that's sent through {@link #sendPacket(Packet)} and
     * that has not been returned by {@link #getSentPacket()}.
     * 
     * @return the number of packets which are in the queue.
     */
    public int getNumberOfSentPackets() {
        return queue.size();
    }

    /**
     * Returns the first packet that's sent through {@link #sendPacket(Packet)} and
     * that has not been returned by earlier calls to this method. This method
     * will block for up to two seconds if no packets have been sent yet.
     * 
     * @return a sent packet.
     * @throws InterruptedException
     */
    public Packet getSentPacket() throws InterruptedException {
        return queue.poll(2, TimeUnit.SECONDS);
    }

    /**
     * Processes a packet through the installed packet collectors and listeners
     * and letting them examine the packet to see if they are a match with the
     * filter.
     *
     * @param packet the packet to process.
     */
    public void processPacket(Packet packet) {
        if (packet == null) {
            return;
        }

        // Loop through all collectors and notify the appropriate ones.
        for (PacketCollector collector: getPacketCollectors()) {
            collector.processPacket(packet);
        }

        if (DEBUG_ENABLED) {
            System.out.println("[RECV]: " + packet.toXML());
        }

        // Deliver the incoming packet to listeners.
        for (ListenerWrapper listenerWrapper : recvListeners.values()) {
            listenerWrapper.notifyListener(packet);
        }
    }
}
