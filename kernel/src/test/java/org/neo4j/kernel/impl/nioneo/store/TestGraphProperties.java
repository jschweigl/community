/**
 * Copyright (c) 2002-2011 "Neo Technology,"
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
package org.neo4j.kernel.impl.nioneo.store;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.neo4j.helpers.collection.IteratorUtil.asCollection;
import static org.neo4j.helpers.collection.MapUtil.genericMap;
import static org.neo4j.kernel.CommonFactories.defaultFileSystemAbstraction;
import static org.neo4j.kernel.CommonFactories.defaultIdGeneratorFactory;
import static org.neo4j.kernel.impl.util.IoPrimitiveUtils.arrayAsCollection;
import static org.neo4j.test.TargetDirectory.forTest;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.AbstractGraphDatabase;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.IdGeneratorFactory;
import org.neo4j.kernel.impl.core.GraphProperties;
import org.neo4j.test.ImpermanentGraphDatabase;
import org.neo4j.test.OtherThreadExecutor;
import org.neo4j.test.TargetDirectory;

public class TestGraphProperties
{
    private AbstractGraphDatabase db;
    private Transaction tx;

    @Before
    public void doBefore() throws Exception
    {
        db = new ImpermanentGraphDatabase();
    }
    
    @After
    public void doAfter() throws Exception
    {
        db.shutdown();
    }
    
    @Test
    public void basicProperties() throws Exception
    {
        assertNull( properties().getProperty( "test", null ) );
        beginTx();
        properties().setProperty( "test", "yo" );
        assertEquals( "yo", properties().getProperty( "test" ) );
        finishTx( true );
        assertEquals( "yo", properties().getProperty( "test" ) );
        beginTx();
        assertNull( properties().removeProperty( "something non existent" ) );
        assertEquals( "yo", properties().removeProperty( "test" ) );
        assertNull( properties().getProperty( "test", null ) );
        properties().setProperty( "other", 10 );
        assertEquals( 10, properties().getProperty( "other" ) );
        properties().setProperty( "new", "third" );
        finishTx( true );
        assertNull( properties().getProperty( "test", null ) );
        assertEquals( 10, properties().getProperty( "other" ) );
        assertEquals( asSet( asCollection( properties().getPropertyKeys() ) ), asSet( asList( "other", "new" ) ) );
        
        beginTx();
        properties().setProperty( "rollback", true );
        assertEquals( true, properties().getProperty( "rollback" ) );
        finishTx( false );
        assertNull( properties().getProperty( "rollback", null ) );
    }
    
    @Test
    public void setManyGraphProperties() throws Exception
    {
        beginTx();
        Object[] values = new Object[] { 10, "A string value", new float[] { 1234.567F, 7654.321F},
                "A rather longer string which wouldn't fit inlined #!)(&¤" };
        int count = 200;
        for ( int i = 0; i < count; i++ ) properties().setProperty( "key" + i, values[i%values.length] );
        finishTx( true );
        
        for ( int i = 0; i < count; i++ ) assertPropertyEquals( values[i%values.length], properties().getProperty( "key" + i ) ); 
        db.getConfig().getGraphDbModule().getNodeManager().clearCache();
        for ( int i = 0; i < count; i++ ) assertPropertyEquals( values[i%values.length], properties().getProperty( "key" + i ) ); 
    }
    
    private void assertPropertyEquals( Object expected, Object readValue )
    {
        if ( expected.getClass().isArray() ) assertEquals( arrayAsCollection( expected ), arrayAsCollection( readValue ) );
        else assertEquals( expected, readValue );
    }

    @Test
    public void setBigArrayGraphProperty() throws Exception
    {
        long[] array = new long[1000];
        for ( int i = 0; i < 10; i++ ) array[array.length/10*i] = i;
        String key = "big long array";
        beginTx();
        properties().setProperty( key, array );
        assertPropertyEquals( array, properties().getProperty( key ) );
        finishTx( true );
        assertPropertyEquals( array, properties().getProperty( key ) );
        db.getConfig().getGraphDbModule().getNodeManager().clearCache();
        assertPropertyEquals( array, properties().getProperty( key ) );
    }

    private <T> Set<T> asSet( Collection<T> asCollection )
    {
        Set<T> set = new HashSet<T>();
        set.addAll( asCollection );
        return set;
    }

    private void finishTx( boolean success )
    {
        if ( tx == null ) throw new IllegalStateException( "Transaction not started" );
        if ( success ) tx.success();
        tx.finish();
        tx = null;
    }
    
    private void beginTx()
    {
        if ( tx != null ) throw new IllegalStateException( "Transaction already started" );
        tx = db.beginTx();
    }

    private PropertyContainer properties()
    {
        return db.getConfig().getGraphDbModule().getNodeManager().getGraphProperties();
    }
    
    @Test
    public void firstRecordOtherThanZeroIfNotFirst() throws Exception
    {
        EmbeddedGraphDatabase db = new EmbeddedGraphDatabase( forTest( getClass() ).directory( "zero", true ).getAbsolutePath() );
        String storeDir = db.getStoreDir();
        Transaction tx = db.beginTx();
        Node node = db.createNode();
        node.setProperty( "name", "Yo" );
        tx.success();
        tx.finish();
        db.shutdown();
        
        db = new EmbeddedGraphDatabase( forTest( getClass() ).directory( "zero", false ).getAbsolutePath() );
        tx = db.beginTx();
        db.getConfig().getGraphDbModule().getNodeManager().getGraphProperties().setProperty( "test", "something" );
        tx.success();
        tx.finish();
        db.shutdown();
        
        NeoStore neoStore = new NeoStore( genericMap(
                FileSystemAbstraction.class, defaultFileSystemAbstraction(),
                "neo_store", new File( storeDir, NeoStore.DEFAULT_NAME ).getAbsolutePath(),
                IdGeneratorFactory.class, defaultIdGeneratorFactory() ) );
        long prop = neoStore.getGraphNextProp();
        assertTrue( prop != 0 );
        neoStore.close();
    }
    
    @Test
    public void graphPropertiesAreLockedPerTx() throws Exception
    {
        Worker worker1 = new Worker( new State( db ) );
        Worker worker2 = new Worker( new State( db ) );
        
        PropertyContainer properties = getGraphProperties( db );
        worker1.beginTx();
        worker2.beginTx();
        
        String key1 = "name";
        String value1 = "Value 1";
        String key2 = "some other property";
        String value2 = "Value 2";
        String key3 = "say";
        String value3 = "hello";
        worker1.setProperty( key1, value1 ).get();
        assertFalse( properties.hasProperty( key1 ) );
        assertFalse( worker2.hasProperty( key1 ) );
        Future<Void> blockedSetProperty = worker2.setProperty( key2, value2 );
        assertFalse( properties.hasProperty( key1 ) );
        assertFalse( properties.hasProperty( key2 ) );
        worker1.setProperty( key3, value3 ).get();
        assertFalse( blockedSetProperty.isDone() );
        assertFalse( properties.hasProperty( key1 ) );
        assertFalse( properties.hasProperty( key2 ) );
        assertFalse( properties.hasProperty( key3 ) );
        worker1.commitTx();
        assertTrue( properties.hasProperty( key1 ) );
        assertFalse( properties.hasProperty( key2 ) );
        assertTrue( properties.hasProperty( key3 ) );
        blockedSetProperty.get();
        assertTrue( blockedSetProperty.isDone() );
        worker2.commitTx();
        assertTrue( properties.hasProperty( key1 ) );
        assertTrue( properties.hasProperty( key2 ) );
        assertTrue( properties.hasProperty( key3 ) );
        
        assertEquals( value1, properties.getProperty( key1 ) );
        assertEquals( value3, properties.getProperty( key3 ) );
        assertEquals( value2, properties.getProperty( key2 ) );
    }
    
    @Test
    public void upgradeDoesntAccidentallyAssignPropertyChainZero() throws Exception
    {
        EmbeddedGraphDatabase db = new EmbeddedGraphDatabase( TargetDirectory.forTest(
                TestGraphProperties.class ).directory( "upgrade", true ).getAbsolutePath() );
        String storeDir = db.getStoreDir();
        Transaction tx = db.beginTx();
        Node node = db.createNode();
        node.setProperty( "name", "Something" );
        tx.success();
        tx.finish();
        db.shutdown();
        
        removeLastNeoStoreRecord( storeDir );
        
        db = new EmbeddedGraphDatabase( storeDir );
        PropertyContainer properties = db.getConfig().getGraphDbModule().getNodeManager().getGraphProperties();
        assertFalse( properties.getPropertyKeys().iterator().hasNext() );
        tx = db.beginTx();
        properties.setProperty( "a property", "a value" );
        tx.success();
        tx.finish();
        db.getConfig().getGraphDbModule().getNodeManager().clearCache();
        assertEquals( "a value", properties.getProperty( "a property" ) );
        db.shutdown();
        
        db = new EmbeddedGraphDatabase( storeDir );
        properties = db.getConfig().getGraphDbModule().getNodeManager().getGraphProperties();
        assertEquals( "a value", properties.getProperty( "a property" ) );
        db.shutdown();
    }

    private void removeLastNeoStoreRecord( String storeDir ) throws IOException
    {
        // Remove the last record, next startup will look like as if we're upgrading an old store
        File neoStoreFile = new File( storeDir, NeoStore.DEFAULT_NAME );
        RandomAccessFile raFile = new RandomAccessFile( neoStoreFile, "rw" );
        FileChannel channel = raFile.getChannel();
        channel.position( NeoStore.RECORD_SIZE*6/*position of "next prop"*/ );
        int trail = (int) (channel.size()-channel.position());
        ByteBuffer trailBuffer = null;
        if ( trail > 0 )
        {
            trailBuffer = ByteBuffer.allocate( trail );
            channel.read( trailBuffer );
            trailBuffer.flip();
        }
        channel.position( NeoStore.RECORD_SIZE*5 );
        if ( trail > 0 ) channel.write( trailBuffer );
        channel.truncate( channel.position() );
        raFile.close();
    }
    
    @Test
    public void upgradeWorksEvenOnUncleanShutdown() throws Exception
    {
        String storeDir = TargetDirectory.forTest( TestGraphProperties.class ).directory( "nonclean", true ).getAbsolutePath();
        assertEquals( 0, Runtime.getRuntime().exec( new String[] { "java", "-cp", System.getProperty( "java.class.path" ),
                ProduceUncleanStore.class.getName(), storeDir } ).waitFor() );
        removeLastNeoStoreRecord( storeDir );
        EmbeddedGraphDatabase db = new EmbeddedGraphDatabase( storeDir );
        PropertyContainer properties = db.getConfig().getGraphDbModule().getNodeManager().getGraphProperties();
        assertFalse( properties.getPropertyKeys().iterator().hasNext() ); 
        Transaction tx = db.beginTx();
        properties.setProperty( "a property", "a value" );
        tx.success();
        tx.finish();
        db.getConfig().getGraphDbModule().getNodeManager().clearCache();
        assertEquals( "a value", properties.getProperty( "a property" ) );
        db.shutdown();
    }
    
    @Test
    public void twoUncleanInARow() throws Exception
    {
        String storeDir = TargetDirectory.forTest( TestGraphProperties.class ).directory( "nonclean", true ).getAbsolutePath();
        assertEquals( 0, Runtime.getRuntime().exec( new String[] { "java", "-cp", System.getProperty( "java.class.path" ),
                ProduceUncleanStore.class.getName(), storeDir, "true" } ).waitFor() );
        assertEquals( 0, Runtime.getRuntime().exec( new String[] { "java", "-cp", System.getProperty( "java.class.path" ),
                ProduceUncleanStore.class.getName(), storeDir, "true" } ).waitFor() );
        assertEquals( 0, Runtime.getRuntime().exec( new String[] { "java", "-cp", System.getProperty( "java.class.path" ),
                ProduceUncleanStore.class.getName(), storeDir, "true" } ).waitFor() );
        EmbeddedGraphDatabase db = new EmbeddedGraphDatabase( storeDir );
        assertEquals( "Some value", db.getConfig().getGraphDbModule().getNodeManager().getGraphProperties().getProperty( "prop" ) );
        db.shutdown();
    }
    
    private static class State
    {
        private final AbstractGraphDatabase db;
        private final PropertyContainer properties;
        private Transaction tx;

        State( AbstractGraphDatabase db )
        {
            this.db = db;
            this.properties = getGraphProperties( db );
        }
    }
    
    private static GraphProperties getGraphProperties( AbstractGraphDatabase db )
    {
        return db.getConfig().getGraphDbModule().getNodeManager().getGraphProperties();
    }
    
    private static class Worker extends OtherThreadExecutor<State>
    {
        public Worker( State initialState )
        {
            super( initialState );
        }
        
        public boolean hasProperty( final String key ) throws Exception
        {
            return execute( new WorkerCommand<State, Boolean>()
            {
                @Override
                public Boolean doWork( State state )
                {
                    return Boolean.valueOf( state.properties.hasProperty( key ) );
                }
            } ).booleanValue();
        }

        public void commitTx() throws Exception
        {
            execute( new WorkerCommand<State, Void>()
            {
                @Override
                public Void doWork( State state )
                {
                    state.tx.success();
                    state.tx.finish();
                    return null;
                }
            } );
        }

        void beginTx() throws Exception
        {
            execute( new WorkerCommand<State, Void>()
            {
                @Override
                public Void doWork( State state )
                {
                    state.tx = state.db.beginTx();
                    return null;
                }
            } );
        }
        
        Future<Void> setProperty( final String key, final Object value ) throws Exception
        {
            return executeDontWait( new WorkerCommand<State, Void>()
            {
                @Override
                public Void doWork( State state )
                {
                    state.properties.setProperty( key, value );
                    return null;
                }
            } );
        }
    }
}
