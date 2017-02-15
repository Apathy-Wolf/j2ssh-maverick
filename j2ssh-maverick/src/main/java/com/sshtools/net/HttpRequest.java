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
package com.sshtools.net;

import com.sshtools.util.Base64;

/**
 * Utility class to process HTTP requests.
 * 
 * @author Lee David Painter
 * 
 */
public class HttpRequest extends HttpHeader {

	public HttpRequest() {
		super();
	}

	public void setHeaderBegin(String begin) {
		this.begin = begin;
	}

	public void setBasicAuthentication(String username, String password) {
		String str = username + ":" + password;
		setHeaderField("Proxy-Authorization",
				"Basic " + Base64.encodeBytes(str.getBytes(), true));
	}
}
