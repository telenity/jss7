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

package org.mobicents.protocols.ss7.sccp.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import javolution.util.FastMap;
import javolution.xml.XMLObjectReader;
import javolution.xml.XMLObjectWriter;
import javolution.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;

/**
 *
 * @author amit bhayani
 *
 */
public class SccpResource {
    private static final Logger logger = Logger.getLogger(SccpResource.class);

    private static final String SCCP_RESOURCE_PERSIST_DIR_KEY = "sccpresource.persist.dir";
    private static final String USER_DIR_KEY = "user.dir";
    private static final String PERSIST_FILE_NAME = "sccpresource.xml";

    private static final String REMOTE_SSN = "remoteSsns";
    private static final String REMOTE_SPC = "remoteSpcs";
    private static final String CONCERNED_SPC = "concernedSpcs";

    private final StringBuilder persistFile = new StringBuilder();

    private static final SccpResourceXMLBinding binding = new SccpResourceXMLBinding();
    private static final String TAB_INDENT = "\t";
    private static final String CLASS_ATTRIBUTE = "type";

    private String persistDir;

    private volatile RemoteSubSystemMap<Integer, RemoteSubSystemImpl> remoteSsns = new RemoteSubSystemMap<>();
    private volatile RemoteSignalingPointCodeMap<Integer, RemoteSignalingPointCodeImpl> remoteSpcs = new RemoteSignalingPointCodeMap<>();
    private volatile ConcernedSignalingPointCodeMap<Integer, ConcernedSignalingPointCodeImpl> concernedSpcs = new ConcernedSignalingPointCodeMap<>();

    private final String name;

    public SccpResource(String name) {
        this.name = name;
        binding.setClassAttribute(CLASS_ATTRIBUTE);
        binding.setAlias(RemoteSubSystemImpl.class, "remoteSubSystem");
    }

    public String getPersistDir() {
        return persistDir;
    }

    public void setPersistDir(String persistDir) {
        this.persistDir = persistDir;
    }

    public void start() {
        this.persistFile.setLength(0);

        if (persistDir != null) {
            this.persistFile.append(persistDir).append(File.separator).append(this.name).append("_").append(PERSIST_FILE_NAME);
        } else {
            persistFile.append(System.getProperty(SCCP_RESOURCE_PERSIST_DIR_KEY, System.getProperty(USER_DIR_KEY)))
                    .append(File.separator).append(this.name).append("_").append(PERSIST_FILE_NAME);
        }

        logger.info(String.format("SCCP Resource configuration file path %s", persistFile));

        try {
            this.load();
        } catch (IOException e) {
            logger.warn("Failed to load the SS7 configuration file", e);
        }

        logger.info("Started Sccp Resource");
    }

    public void stop() {
        this.store();
    }

    public void addRemoteSsn(int remoteSsnid, RemoteSubSystemImpl remoteSsn) {
        synchronized (this) {
            RemoteSubSystemMap<Integer, RemoteSubSystemImpl> newRemoteSsns = new RemoteSubSystemMap<>();
            newRemoteSsns.putAll(this.remoteSsns);
            newRemoteSsns.put(remoteSsnid, remoteSsn);
            this.remoteSsns = newRemoteSsns;
            this.store();
        }
    }

    public void removeRemoteSsn(int remoteSsnid) {
        synchronized (this) {
            RemoteSubSystemMap<Integer, RemoteSubSystemImpl> newRemoteSsns = new RemoteSubSystemMap<>();
            newRemoteSsns.putAll(this.remoteSsns);
            newRemoteSsns.remove(remoteSsnid);
            this.remoteSsns = newRemoteSsns;
            this.store();
        }
    }

    public RemoteSubSystemImpl getRemoteSsn(int remoteSsnid) {
        return this.remoteSsns.get(remoteSsnid);
    }

    public RemoteSubSystemImpl getRemoteSsn(int spc, int remoteSsn) {

        RemoteSubSystemMap<Integer, RemoteSubSystemImpl> remoteSsns = this.remoteSsns;
        for (FastMap.Entry<Integer, RemoteSubSystemImpl> e = remoteSsns.head(), end = remoteSsns.tail(); (e = e.getNext()) != end; ) {
            RemoteSubSystemImpl remoteSubSystem = e.getValue();
            if (remoteSubSystem.getRemoteSpc() == spc && remoteSsn == remoteSubSystem.getRemoteSsn()) {
                return remoteSubSystem;
            }

        }
        return null;
    }

    public FastMap<Integer, RemoteSubSystemImpl> getRemoteSsns() {
        return this.remoteSsns;
    }

    public void addRemoteSpc(int remoteSpcId, RemoteSignalingPointCodeImpl remoteSpc) {
        synchronized (this) {
            RemoteSignalingPointCodeMap<Integer, RemoteSignalingPointCodeImpl> newRemoteSpcs = new RemoteSignalingPointCodeMap<>();
            newRemoteSpcs.putAll(this.remoteSpcs);
            newRemoteSpcs.put(remoteSpcId, remoteSpc);
            this.remoteSpcs = newRemoteSpcs;
            this.store();
        }
    }

    public void removeRemoteSpc(int remoteSpcId) {
        synchronized (this) {
            RemoteSignalingPointCodeMap<Integer, RemoteSignalingPointCodeImpl> newRemoteSpcs = new RemoteSignalingPointCodeMap<>();
            newRemoteSpcs.putAll(this.remoteSpcs);
            newRemoteSpcs.remove(remoteSpcId);
            this.remoteSpcs = newRemoteSpcs;
            this.store();
        }
    }

    public RemoteSignalingPointCodeImpl getRemoteSpc(int remoteSpcId) {
        return this.remoteSpcs.get(remoteSpcId);
    }

    public RemoteSignalingPointCodeImpl getRemoteSpcByPC(int remotePC) {
        RemoteSignalingPointCodeMap<Integer, RemoteSignalingPointCodeImpl> remoteSpcs = this.remoteSpcs;
        for (FastMap.Entry<Integer, RemoteSignalingPointCodeImpl> e = remoteSpcs.head(), end = remoteSpcs.tail(); (e = e
                .getNext()) != end; ) {
            RemoteSignalingPointCodeImpl remoteSubSystem = e.getValue();
            if (remoteSubSystem.getRemoteSpc() == remotePC) {
                return remoteSubSystem;
            }

        }
        return null;
    }

    public FastMap<Integer, RemoteSignalingPointCodeImpl> getRemoteSpcs() {
        return this.remoteSpcs;
    }

    public void addConcernedSpc(int concernedSpcId, ConcernedSignalingPointCodeImpl concernedSpc) {
        synchronized (this) {
            ConcernedSignalingPointCodeMap<Integer, ConcernedSignalingPointCodeImpl> newConcernedSpcs = new ConcernedSignalingPointCodeMap<>();
            newConcernedSpcs.putAll(this.concernedSpcs);
            newConcernedSpcs.put(concernedSpcId, concernedSpc);
            this.concernedSpcs = newConcernedSpcs;
            this.store();
        }
    }

    public void removeConcernedSpc(int concernedSpcId) {
        synchronized (this) {
            ConcernedSignalingPointCodeMap<Integer, ConcernedSignalingPointCodeImpl> newConcernedSpcs = new ConcernedSignalingPointCodeMap<>();
            newConcernedSpcs.putAll(this.concernedSpcs);
            newConcernedSpcs.remove(concernedSpcId);
            this.concernedSpcs = newConcernedSpcs;
            this.store();
        }
    }

    public ConcernedSignalingPointCodeImpl getConcernedSpc(int concernedSpcId) {
        return this.concernedSpcs.get(concernedSpcId);
    }

    public ConcernedSignalingPointCodeImpl getConcernedSpcByPC(int remotePC) {
        ConcernedSignalingPointCodeMap<Integer, ConcernedSignalingPointCodeImpl> concernedSpcs = this.concernedSpcs;
        for (FastMap.Entry<Integer, ConcernedSignalingPointCodeImpl> e = concernedSpcs.head(), end = concernedSpcs.tail(); (e = e.getNext()) != end; ) {
            ConcernedSignalingPointCodeImpl concernedSubSystem = e.getValue();
            if (concernedSubSystem.getRemoteSpc() == remotePC) {
                return concernedSubSystem;
            }

        }
        return null;
    }

    public FastMap<Integer, ConcernedSignalingPointCodeImpl> getConcernedSpcs() {
        return this.concernedSpcs;
    }

    public void removeAllResourses() {

        synchronized (this) {
            if (this.remoteSsns.size() == 0 && this.remoteSpcs.size() == 0 && this.concernedSpcs.size() == 0)
                // no resources allocated - nothing to do
                return;

            remoteSsns = new RemoteSubSystemMap<>();
            remoteSpcs = new RemoteSignalingPointCodeMap<>();
            concernedSpcs = new ConcernedSignalingPointCodeMap<>();

            // We store the cleared state
            this.store();
        }
    }

    /**
     * Persist
     */
    public void store() {

        try {
            XMLObjectWriter writer = XMLObjectWriter.newInstance(Files.newOutputStream(Paths.get(persistFile.toString())));
            writer.setBinding(binding);
            writer.setIndentation(TAB_INDENT);
            writer.write(remoteSsns, REMOTE_SSN, RemoteSubSystemMap.class);
            writer.write(remoteSpcs, REMOTE_SPC, RemoteSignalingPointCodeMap.class);
            writer.write(concernedSpcs, CONCERNED_SPC, ConcernedSignalingPointCodeMap.class);

            writer.close();
        } catch (Exception e) {
            logger.error("Error while persisting the Sccp Resource state in file", e);
        }
    }

    /**
     * Load and create LinkSets and Link from persisted file
     *
     * @throws Exception
     */
    private void load() throws IOException {

        XMLObjectReader reader = null;
        try {
            reader = XMLObjectReader.newInstance(Files.newInputStream(Paths.get(persistFile.toString())));

            reader.setBinding(binding);
            remoteSsns = reader.read(REMOTE_SSN, RemoteSubSystemMap.class);
            remoteSpcs = reader.read(REMOTE_SPC, RemoteSignalingPointCodeMap.class);
            concernedSpcs = reader.read(CONCERNED_SPC, ConcernedSignalingPointCodeMap.class);
        } catch (XMLStreamException ex) {
            // no-op
        }
    }
}
