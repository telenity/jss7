/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2012, Telestax Inc and individual contributors
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
package org.mobicents.protocols.sctp.netty;

import java.util.Iterator;
import java.util.Map;

import javolution.xml.XMLBinding;
import javolution.xml.XMLFormat;
import javolution.xml.stream.XMLStreamException;

import org.mobicents.protocols.sctp.AssociationMap;

/**
 * @author <a href="mailto:amit.bhayani@telestax.com">Amit Bhayani</a>
 * 
 */
public class NettySctpXMLBinding extends XMLBinding {

	protected static final XMLFormat<AssociationMap> ASSOCIATION_MAP = new XMLFormat<AssociationMap>(null) {

		@Override
		public void write(AssociationMap obj, OutputElement xml) throws XMLStreamException {
			final Map map = (Map) obj;

			for (Iterator it = map.entrySet().iterator(); it.hasNext();) {
				Map.Entry entry = (Map.Entry) it.next();

				xml.add((String) entry.getKey(), "name", String.class);
				xml.add((NettyAssociationImpl) entry.getValue(), "association", NettyAssociationImpl.class);
			}
		}

		@Override
		public void read(InputElement xml, AssociationMap obj) throws XMLStreamException {
			while (xml.hasNext()) {
				String key = xml.get("name", String.class);
				NettyAssociationImpl association = xml.get("association", NettyAssociationImpl.class);
				obj.put(key, association);
			}
		}

	};

	protected XMLFormat getFormat(Class forClass) throws XMLStreamException {
		if (AssociationMap.class.equals(forClass)) {
			return ASSOCIATION_MAP;
		}
		return super.getFormat(forClass);
	}
}
