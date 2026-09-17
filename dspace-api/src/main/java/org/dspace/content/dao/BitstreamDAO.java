/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.dao;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.core.Context;

/**
 * Database Access Object interface class for the Bitstream object.
 * The implementation of this class is responsible for all database calls for the Bitstream object and is autowired
 * by spring
 * This class should only be accessed from a single service and should never be exposed outside of the API
 *
 * @author kevinvandevelde at atmire.com
 */
public interface BitstreamDAO extends DSpaceObjectLegacySupportDAO<Bitstream> {

    public Iterator<Bitstream> findAll(Context context, int limit, int offset) throws SQLException;

    public List<Bitstream> findDeletedBitstreams(Context context, int limit, int offset) throws SQLException;

    public List<Bitstream> findDuplicateInternalIdentifier(Context context, Bitstream bitstream) throws SQLException;

    public List<Bitstream> findBitstreamsWithNoRecentChecksum(Context context) throws SQLException;

    public Iterator<Bitstream> findByCommunity(Context context, Community community) throws SQLException;

    public Iterator<Bitstream> findByCollection(Context context, Collection collection) throws SQLException;

    public Iterator<Bitstream> findByItem(Context context, Item item) throws SQLException;

    public Iterator<Bitstream> findByStoreNumber(Context context, Integer storeNumber) throws SQLException;

    public Long countByStoreNumber(Context context, Integer storeNumber) throws SQLException;

    int countRows(Context context) throws SQLException;

    int countDeleted(Context context) throws SQLException;

    int countWithNoPolicy(Context context) throws SQLException;

    List<Bitstream> getNotReferencedBitstreams(Context context) throws SQLException;

    /**
     * Attach an already-created bitstream during a controlled legacy migration
     * without materializing the bundle's complete ordered bitstream collection.
     *
     * @param context current DSpace context
     * @param bitstreamId bitstream UUID
     * @param bundleId bundle UUID
     * @param bitstreamOrder compact target bundle order
     * @param legacyBitstreamOrder original source bundle order
     * @param primary whether this is the bundle's primary bitstream
     * @throws SQLException if the native relationship update fails
     */
    void addToBundleForMigration(Context context, UUID bitstreamId, UUID bundleId,
                                 int bitstreamOrder, int legacyBitstreamOrder,
                                 boolean primary) throws SQLException;
}
