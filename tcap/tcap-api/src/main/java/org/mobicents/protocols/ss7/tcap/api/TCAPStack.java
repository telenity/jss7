/*
 * TeleStax, Open Source Cloud Communications  Copyright 2012.
 * and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.protocols.ss7.tcap.api;


/**
 * @author baranowb
 *
 */
public interface TCAPStack {

	/**
	 * Returns stack provider.
	 * @return
	 */
	public TCAPProvider getProvider();
	/**
	 * Stops this stack and transport layer(SCCP)
	 */
	public void stop();
	/**
	 * Start stack and transport layer(SCCP)
	 * @throws Exception - if stack is already running or not configured
	 */
	public void start() throws Exception;

	public boolean isStarted();

	/**
	 * Sets millisecond value for dialog timeout. It specifies how long dialog
	 * can be idle - not receive/send any messages.
	 * 
	 * @param l
	 */
	public void setDialogIdleTimeout(long l);

	public long getDialogIdleTimeout();
	
	public void setInvokeTimeout(long v); 

	public long getInvokeTimeout();
	
	/**
	* Sets the maximum number of dialogs allowed to be alive at a given time.
	* If not set, a default value of 5000 dialogs will be used.
	*
	* Important a: Default value may vary depending on the future
	* implementations or changes to current implementation.
	*
	* Important b: If stack ranges provided, maximum number dialogs naturally
	* cannot be greater than the provided range, thus, it will be normalized to
	* range delta (end - start).
	*
	*
	* @param v number of dialogs
	*/
	public void setMaxDialogs(int v); 

	/**
	*
	* @return Maximum number of allowed concurrent dialogs.
	*/
	public int getMaxDialogs();

	/**
	* Sets the start of the range of the generated dialog ids.
	*/
	public void setDialogIdRangeStart(long val);

	/**
	* Sets the start of the range of the generated dialog ids.
	*/
	public void setDialogIdRangeEnd(long val);

	/**
	*
	* @return starting dialog id within the range
	*/
	public long getDialogIdRangeStart();

	/**
	*     
	* @return ending dialog id within the range
	*/
	public long getDialogIdRangeEnd();

	/**
	* previewMode is needed for special processing mode
	* When PreviewMode in TCAP level we have:
	* - we only listern incoming messages and sends nothing. send(),
	*   close(), sendComponent() and other such methods do nothing.
	* - A TCAP Dialog is temporary. TCAP Dialog is discarded after any
	*   icoming message like TC-BEGIN or TC-CONTINUE has been processed
	* - for any incoming messages (including TC-CONTINUE, TC-END, TC-ABORT)
	*   a new TCAP Dialog is created (end then deleted).
	* - no timers and timeouts
	* 
	* default state: no previewMode
	*/
	public void setPreviewMode(boolean val);

	/**
	*     
	* @return if areviewMode is active
	*/
	public boolean getPreviewMode();

	void setDoNotSendProtocolVersion(boolean val);

	boolean getDoNotSendProtocolVersion();
	
}
