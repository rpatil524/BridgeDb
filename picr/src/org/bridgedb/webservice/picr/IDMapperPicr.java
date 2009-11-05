// BridgeDb,
// An abstraction layer for identifer mapping services, both local and online.
// Copyright 2006-2009 BridgeDb developers
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package org.bridgedb.webservice.picr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bridgedb.AbstractIDMapperCapabilities;
import org.bridgedb.AttributeMapper;
import org.bridgedb.BridgeDb;
import org.bridgedb.DataSource;
import org.bridgedb.IDMapper;
import org.bridgedb.IDMapperCapabilities;
import org.bridgedb.IDMapperException;
import org.bridgedb.impl.InternalUtils;
import org.bridgedb.webservice.IDMapperWebservice;
import org.bridgedb.Xref;

import uk.ac.ebi.demo.picr.business.PICRClient;
import uk.ac.ebi.demo.picr.soap.CrossReference;
import uk.ac.ebi.demo.picr.soap.UPEntry;

public class IDMapperPicr extends IDMapperWebservice implements AttributeMapper
{
    static 
    {
		BridgeDb.register ("idmapper-picr", new Driver());
	}

    private boolean onlyActive;
    
    private Set<DataSource> supportedDatabases = new HashSet<DataSource>();
    private PICRClient client;

    /**
     *
     * @param onlyActive using only active mappings if true
     */
    public IDMapperPicr(boolean onlyActive)
    {
        client = new PICRClient();
        for (String s : client.loadDatabases())
        {
        	supportedDatabases.add(DataSource.getByFullName(s));
        }
        this.onlyActive = onlyActive;
    }

    /**
     *
     * @return true if using only active mappings; false otherwise
     */
    public boolean getOnlyActive() {
        return onlyActive;
    }

    /**
     *
     * @param onlyActive using only active mappings if true
     */
    public void setOnlyActive(boolean onlyActive) {
        this.onlyActive = onlyActive;
    }

    private static class Driver implements org.bridgedb.Driver
	{
        private Driver() { } // prevent outside instantiation

		public IDMapper connect(String location) throws IDMapperException  
		{
            Map<String, String> args = 
            	InternalUtils.parseLocation(location, "only-active");

			boolean isOnlyActive = true;
			if (args.containsKey("only-active"))
			{
				isOnlyActive = Boolean.parseBoolean(args.get("only-active"));
			}
			return new IDMapperPicr(isOnlyActive);
		}
	}
	
	private boolean closed = false;
	public void close() throws IDMapperException 
	{
		closed = true;
	}

	public Set<Xref> freeSearch(String text, int limit)
			throws IDMapperException {
		throw new UnsupportedOperationException();
	}

	private class PICRCapabilities extends AbstractIDMapperCapabilities
	{
		public PICRCapabilities() 
		{
			super (supportedDatabases, false, null);
		}
	}
	
	private PICRCapabilities picrCapabilities = new PICRCapabilities();
	
	public IDMapperCapabilities getCapabilities() 
	{
		return picrCapabilities;
	}

	public boolean isConnected() 
	{
		return !closed;
	}

	public Map<Xref, Set<Xref>> mapID(Collection<Xref> srcXrefs,
			DataSource... tgtDataSources) throws IDMapperException 
	{
		Map<Xref, Set<Xref>> result = new HashMap<Xref, Set<Xref>>();
		
		
		Object[] databases;
		if (tgtDataSources == null) 
			databases = client.loadDatabases().toArray();
		else
			databases = objectsFromDataSources(tgtDataSources);
		
		for (Xref srcXref : srcXrefs)
		{
			Set<Xref> resultSet = new HashSet<Xref>();
			List<CrossReference> refs = new ArrayList<CrossReference>();
			
			List<UPEntry> entries = client.performAccessionMapping(srcXref.getId(), databases);
            for (UPEntry entry : entries) {
            	refs.addAll (entry.getIdenticalCrossReferences());
                refs.addAll (entry.getLogicalCrossReferences());
            }
            
            for (CrossReference ref : refs)
            {
            	// in onlyActive mode, check if it is deleted first
            	if (!(onlyActive && ref.isDeleted()))
    			{
            		Xref xref = new Xref (ref.getAccession(), DataSource.getByFullName(ref.getDatabaseName()));
            		resultSet.add (xref);
    			}
            }
            
            if (resultSet.size() > 0)
            {
            	result.put (srcXref, resultSet);
            }
		}
		return result;
	}
	
	private Object[] objectsFromDataSources (DataSource... ds)
	{
		Object[] databases = new Object[ds.length];
		int i = 0;
		for (DataSource tgt : ds)
		{
			databases[i++] = tgt.getFullName();
		}
		return databases;
	}
	
	public boolean xrefExists(Xref xref) throws IDMapperException 
	{
		List<UPEntry> result = client.performAccessionMapping(xref.getId(), client.loadDatabases().toArray());
		return result.size() > 0;
	}

	public Map<Xref, String> freeAttributeSearch(String query, String attrType,
			int limit) throws IDMapperException 
	{
		throw new UnsupportedOperationException();
	}

	public Set<String> getAttributes(Xref ref, String attrType)
			throws IDMapperException 
	{
		List<UPEntry> entries = client.performAccessionMapping(ref.getId(), client.loadDatabases().toArray());
		
		Set<String> result = new HashSet<String>();
		
		for (UPEntry entry : entries)
		{
			if ("CRC64".equals (attrType))
			{
				result.add (entry.getCRC64());
			}
			else if ("Sequence".equals (attrType))
			{
				result.add (entry.getSequence());
			}
			else if ("UPI".equals (attrType))
			{
				result.add (entry.getUPI());
			}
			else if ("Timestamp".equals (attrType))
			{
				result.add ("" + entry.getTimestamp());
			}
		}
		
		return result;
	}

	public Set<String> getAttributeSet() throws IDMapperException 
	{
		// TODO Not yet implemented
		throw new UnsupportedOperationException();
	}

	public Map<String, Set<String>> getAttributes(Xref ref)
			throws IDMapperException {
		// TODO Not yet implemented
		throw new UnsupportedOperationException();
	}
}
