/**
 * Copyright (c) 2002-2014 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.storemigration.legacystore;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.neo4j.kernel.DefaultFileSystemAbstraction;
import org.neo4j.kernel.impl.nioneo.store.FileSystemAbstraction;
import org.neo4j.kernel.impl.nioneo.store.NodeRecord;

import static org.junit.Assert.*;
import static org.neo4j.test.Unzip.unzip;

public class ReadRecordsTestIT
{
    @Test
    public void shouldReadNodeRecords() throws IOException
    {
        File storeDir = unzip( getClass(), "exampledb.zip" );
        LegacyNodeStoreReader nodeStoreReader = new LegacyNodeStoreReader( fs,
                new File( storeDir, "neostore.nodestore.db" ) );
        assertEquals( 1003, nodeStoreReader.getMaxId() );

        final AtomicInteger nodeCount = new AtomicInteger( 0 );
        nodeStoreReader.accept( new LegacyNodeStoreReader.Visitor()
        {
            @Override
            public void visit( NodeRecord record )
            {
                if(record.inUse())
                {
                    nodeCount.incrementAndGet();
                }
            }
        } );
        assertEquals( 501, nodeCount.get() );
        nodeStoreReader.close();
    }

    private final FileSystemAbstraction fs = new DefaultFileSystemAbstraction();
}
