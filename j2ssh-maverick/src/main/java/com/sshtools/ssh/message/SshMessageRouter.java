/**
 * Copyright 2003-2016 SSHTOOLS Limited. All Rights Reserved.
 *
 * For product documentation visit https://www.sshtools.com/
 *
 * This file is part of J2SSH Maverick.
 *
 * J2SSH Maverick is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * J2SSH Maverick is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with J2SSH Maverick.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sshtools.ssh.message;

import java.io.IOException;
import java.util.Vector;

import com.sshtools.logging.Log;
import com.sshtools.ssh.SshException;
import com.sshtools.ssh.SshIOException;

/**
 * <p>
 * This abstract class provides a synchronized message routing framework. The
 * protocol implementation supplies a message reader interface, to which only
 * one thread is allowed access at any one time, threads requiring a message
 * whilst another thread is blocking are queued to await notification of when
 * the reader is available. Since a message read by one thread may be destined
 * for another the router takes charge of this before notifying other queued
 * threads that the block is available. When they receive this notification they
 * first check their own message stores before requesting the block again.
 * <p>
 * 
 * @author Lee David Painter
 */
public abstract class SshMessageRouter {

	private SshAbstractChannel[] channels;
	SshMessageReader reader;
	SshMessageStore global;
	ThreadSynchronizer sync;
	private int count = 0;
	boolean buffered;
	MessagePump messagePump;
	boolean isClosing = false;
	Vector<SshAbstractChannel> activeChannels = new Vector<SshAbstractChannel>();
	Vector<Runnable> shutdownHooks = new Vector<Runnable>();
	boolean verbose = Boolean.valueOf(
			System.getProperty("maverick.verbose", "false")).booleanValue();

	public SshMessageRouter(SshMessageReader reader, int maxChannels,
			boolean buffered) {
		this.reader = reader;
		this.buffered = buffered;
		this.channels = new SshAbstractChannel[maxChannels];
		this.global = new SshMessageStore(this, null, new MessageObserver() {
			public boolean wantsNotification(Message msg) {
				return false;
			}
		});

		sync = new ThreadSynchronizer(buffered);

		if (buffered) {
			messagePump = new MessagePump();
			sync.blockingThread = messagePump;
			// J2SE messagePump.setDaemon(true);
		}

	}

	public void start() {
		if (Log.isDebugEnabled()) {
			if (verbose) {
				Log.debug(this, "starting message pump");
			}
		}
		if (messagePump != null && !messagePump.isRunning()) {
			String prefix = "";
			String sourceThread = Thread.currentThread().getName();
			if (sourceThread.indexOf('-') > -1) {
				prefix = sourceThread.substring(0,
						1 + sourceThread.indexOf('-'));
				// retrieve an event Listener
				// pass the event to the listener to process
			}
			messagePump
					.setName(prefix + "MessagePump_" + messagePump.getName());
			messagePump.start();
			if (Log.isDebugEnabled()) {
				if (verbose) {
					Log.debug(this, "message pump started thread name:"
							+ messagePump.getName());
				}
			}
		}
	}

	public void addShutdownHook(Runnable r) {
		if (r != null)
			shutdownHooks.addElement(r);
	}

	public boolean isBuffered() {
		return buffered;
	}

	public void stop() {

		signalClosingState();

		if (messagePump != null)
			messagePump.stopThread();

		if (shutdownHooks != null) {
			for (int i = 0; i < shutdownHooks.size(); i++) {
				try {
					((Runnable) shutdownHooks.elementAt(i)).run();
				} catch (Throwable t) {
				}
			}
		}
	}

	public void signalClosingState() {
		if (buffered && messagePump != null) {
			synchronized (messagePump) {
				isClosing = true;
			}
		}
	}

	protected SshMessageStore getGlobalMessages() {
		return global;
	}

	public int getMaxChannels() {
		return channels.length;
	}

	protected int allocateChannel(SshAbstractChannel channel) {

		synchronized (channels) {
			for (int i = 0; i < channels.length; i++) {
				if (channels[i] == null) {
					channels[i] = channel;
					activeChannels.addElement(channel);
					count++;
					if (Log.isDebugEnabled()) {
						Log.debug(this, "Allocated channel " + i);
					}
					return i;
				}
			}
			return -1;
		}
	}

	protected void freeChannel(SshAbstractChannel channel) {
		synchronized (channels) {

			if (channels[channel.getChannelId()] != null) {
				if (channel.equals(channels[channel.getChannelId()])) {
					channels[channel.getChannelId()] = null;
					activeChannels.removeElement(channel);
					count--;
					if (Log.isDebugEnabled()) {
						Log.debug(this,
								"Freed channel " + channel.getChannelId());
					}
				}
			}
		}
	}

	protected SshAbstractChannel[] getActiveChannels() {
		return (SshAbstractChannel[]) activeChannels
				.toArray(new SshAbstractChannel[0]);
	}

	protected int maximumChannels() {
		return channels.length;
	}

	public int getChannelCount() {
		return count;
	}

	protected SshMessage nextMessage(SshAbstractChannel channel,
			MessageObserver observer, long timeout) throws SshException,
			InterruptedException {

		long startTime = System.currentTimeMillis();

		SshMessageStore store = channel == null ? global : channel
				.getMessageStore();
		if (Log.isDebugEnabled()) {
			if (verbose) {
				Log.debug(this, "using "
						+ (channel == null ? "global store" : "channel store"));
			}
		}
		MessageHolder holder = new MessageHolder();

		while (holder.msg == null
				&& (timeout == 0 || System.currentTimeMillis() - startTime < timeout)) {
			/**
			 * There are no messages for this caller. First check the buffered
			 * state and look for possible errors from the buffer thread
			 */
			if (buffered && messagePump != null) {
				if (Log.isDebugEnabled()) {
					if (verbose) {
						Log.debug(this, "waiting for messagePump lock");
					}
				}
				synchronized (messagePump) {
					if (!isClosing) {
						if (messagePump.lastError != null) {
							Throwable tmpEx = messagePump.lastError;
							messagePump.lastError = null;
							if (tmpEx instanceof SshException) {
								if (Log.isDebugEnabled()) {
									Log.debug(this,
											"messagePump has SshException this will be caught by customer code");
								}
								throw (SshException) tmpEx;
							} else if (tmpEx instanceof SshIOException) {
								if (Log.isDebugEnabled()) {
									Log.debug(this,
											"messagePump has SshIOException this will be caught by customer code");
								}
								throw ((SshIOException) tmpEx)
										.getRealException();
							} else {
								if (Log.isDebugEnabled()) {
									Log.debug(this,
											"messagePump has some other exception this will be caught by customer code");
								}
								throw new SshException(tmpEx);
							}
						}
					}
				}
			}

			/**
			 * Request a block on the message reader
			 */
			if (sync.requestBlock(store, observer, holder)) {

				try {
					if (Log.isDebugEnabled()) {
						if (verbose) {
							Log.debug(this, "block for message");
						}
					}
					blockForMessage();

				} finally {
					// Release the block so that other threads may block or
					// return with the
					// newly arrived message
					sync.releaseBlock();
				}
			}
		}

		if (holder.msg == null) {
			if (Log.isDebugEnabled()) {
				Log.debug(this, "Mesage timeout reached timeout="
						+ timeout);
			}
			throw new SshException(
					"The message was not received before the specified timeout period timeout="
							+ timeout, SshException.MESSAGE_TIMEOUT);
		}

		return (SshMessage) holder.msg;
	}

	public boolean isBlockingThread(Thread thread) {
		return sync.isBlockOwner(thread);
	}

	private void blockForMessage() throws SshException {

		// Read and create a message
		SshMessage message = createMessage(reader.nextMessage());
		if (Log.isDebugEnabled()) {
			if (verbose) {
				Log.debug(this, "read next message");
			}
		}
		// Determine the destination channel (if any)
		SshAbstractChannel destination = null;
		if (message instanceof SshChannelMessage) {
			destination = channels[((SshChannelMessage) message).getChannelId()];
		}

		// Call the destination so that they may process the message
		boolean processed = destination == null ? processGlobalMessage(message)
				: destination
						.processChannelMessage((SshChannelMessage) message);

		// If the previous call did not process the message then add to the
		// destinations message store
		if (!processed) {
			SshMessageStore ms = destination == null ? global : destination
					.getMessageStore();
			// add new message to message stores linked list.
			ms.addMessage(message);
		}
	}

	/**
	 * Called when the threaded router closes.
	 */
	protected abstract void onThreadExit();

	/**
	 * <p>
	 * Called by the message routing framework to request the creation of an
	 * {@link SshMessage}.
	 * </p>
	 * 
	 * @param messageid
	 * @return the new message instance
	 */
	protected abstract SshMessage createMessage(byte[] msg) throws SshException;

	/**
	 * <p>
	 * Called by the message routing framework so that the routing
	 * implementation may process a global message. If the message is processed
	 * and no further action is required this method should return
	 * <code>true</code>, if the method returns <code>false</code> then the
	 * message will be added to the global message store.
	 * </p>
	 * 
	 * @param msg
	 * @return <code>true</code> if the message was processed, otherwise
	 *         <code>false</code>
	 * @throws IOException
	 */
	protected abstract boolean processGlobalMessage(SshMessage msg)
			throws SshException;

	class MessagePump extends Thread {

		Throwable lastError;
		boolean running = false;

		public void run() {

			try {
				running = true;

				while (running) {

					try {
						blockForMessage();

						// We have a message so release waiting threads
						sync.releaseWaiting();

					} catch (Throwable t) {

						synchronized (MessagePump.this) {
							// If were not closing then save this error
							if (!isClosing) {
								Log.info(
										this,
										"Message pump caught exception: "
												+ t.getMessage());
								lastError = t;
							}
							stopThread();
						}
					}
				}

				// Finally release the block as we exit
				sync.releaseBlock();

			} finally {
				onThreadExit();
			}
		}

		public void stopThread() {
			running = false;
			if (!Thread.currentThread().equals(this))
				interrupt();
		}

		public boolean isRunning() {
			return running;
		}
	}

}
