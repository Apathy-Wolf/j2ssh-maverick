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
package com.sshtools.sftp;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Vector;

import com.sshtools.ssh.SshException;
import com.sshtools.ssh.SshIOException;
import com.sshtools.util.UnsignedInteger32;

/**
 * An OutputStream to write data to a remote file. <blockquote>
 * 
 * <pre>
 * // Create an SshClient forcing SSH2 connectivity.
 * SshConnector con = SshConnector.getInstance();
 * con.setSupportedVersions(SshConnector.SSH2);
 * 
 * // Connect and authenticate an SshClient
 * Ssh2Client ssh = (Ssh2Client) con.connect(....);
 * ....
 * SftpClient sftp=new SftpClient(ssh);
 * //write file as an input stream
 * 	OutputStream out = sftp.getOutputStream("streamTest");
 * 	
 * 	byte[] data="0000000000".getBytes();
 * 	for(int i=0;i<10;i++){
 * 		out.write(data);
 * 	}
 * 	out.close();
 * 
 * 
 * @author Lee David Painter
 */
public class SftpFileOutputStream extends OutputStream {
	SftpFile file;
	SftpSubsystemChannel sftp;
	long position;
	Vector<UnsignedInteger32> outstandingRequests = new Vector<UnsignedInteger32>();

	/**
	 * Creates a new SftpFileOutputStream object.
	 * 
	 * @param file
	 * 
	 * @throws SftpStatusException
	 * @throws SshException
	 */
	public SftpFileOutputStream(SftpFile file) throws SftpStatusException,
			SshException {
		if (file.getHandle() == null) {
			throw new SftpStatusException(SftpStatusException.INVALID_HANDLE,
					"The file does not have a valid handle!");
		}

		if (file.getSFTPChannel() == null) {
			throw new SshException(
					"The file is not attached to an SFTP subsystem!",
					SshException.BAD_API_USAGE);
		}

		this.file = file;
		this.sftp = file.getSFTPChannel();
	}

	/**
   *
   */
	public void write(byte[] buffer, int offset, int len) throws IOException {
		try {

			int count;
			while (len > 0) {

				count = Math.min(32768, len);

				// Post a request
				outstandingRequests.addElement(sftp.postWriteRequest(
						file.getHandle(), position, buffer, offset, count));

				processNextResponse(100);

				// Update our positions
				offset += count;
				len -= count;
				position += count;
			}

		} catch (SshException ex) {
			throw new SshIOException(ex);
		} catch (SftpStatusException ex) {
			throw new IOException(ex.getMessage());
		}

	}

	/**
   *
   */
	public void write(int b) throws IOException {
		try {

			byte[] array = new byte[] { (byte) b };

			// Post a request
			outstandingRequests.addElement(sftp.postWriteRequest(
					file.getHandle(), position, array, 0, 1));

			processNextResponse(100);

			// Update our positions
			position += 1;

		} catch (SshException ex) {
			throw new SshIOException(ex);
		} catch (SftpStatusException ex) {
			throw new IOException(ex.getMessage());
		}
	}

	private boolean processNextResponse(int numOutstandingRequests)
			throws SftpStatusException, SshException {
		// Maybe look for a response
		if (outstandingRequests.size() > numOutstandingRequests) {
			UnsignedInteger32 requestid = (UnsignedInteger32) outstandingRequests
					.elementAt(0);
			sftp.getOKRequestStatus(requestid);
			outstandingRequests.removeElementAt(0);
		}

		return outstandingRequests.size() > 0;
	}

	/**
	 * Closes the file's handle
	 */
	public void close() throws IOException {
		try {
			while (processNextResponse(0))
				;
			file.close();
		} catch (SshException ex) {
			throw new SshIOException(ex);
		} catch (SftpStatusException ex) {
			throw new IOException(ex.getMessage());
		}
	}

	/**
	 * This method will only be available in J2SE builds
	 */
	// J2SE protected void finalize() throws IOException {
	// J2SE if (file.getHandle() != null) {
	// J2SE close();
	// J2SE }
	// J2SE }
}
