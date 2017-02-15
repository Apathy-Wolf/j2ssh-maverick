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
package com.sshtools.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * <p>
 * Base interface for all SSH related IO interfaces.
 * </p>
 * 
 * @author Lee David Painter
 */
public interface SshIO {

	/**
	 * Get an InputStream to read incoming channel data.
	 * 
	 * @return the channels InputStream
	 * @throws IOException
	 */
	public InputStream getInputStream() throws IOException;

	/**
	 * Get an OutputStream to write outgoing channel data.
	 * 
	 * @return the channels OutputStream
	 * @throws IOException
	 */
	public OutputStream getOutputStream() throws IOException;

	/**
	 * Close the channel.
	 * 
	 * @throws SshIOException
	 */
	public void close() throws IOException;

}
