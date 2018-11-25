// Copyright (c) Philipp Wagner. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for full license information.

package de.bytefish.pgbulkinsert;

import de.bytefish.pgbulkinsert.configuration.Configuration;
import de.bytefish.pgbulkinsert.configuration.IConfiguration;
import de.bytefish.pgbulkinsert.exceptions.SaveEntityFailedException;
import de.bytefish.pgbulkinsert.mapping.AbstractMapping;
import de.bytefish.pgbulkinsert.pgsql.PgBinaryWriter;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.postgresql.copy.CopyManager;
import org.postgresql.copy.PGCopyOutputStream;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

public class PgBulkInsert<TEntity> implements IPgBulkInsert<TEntity> {

    private final IConfiguration configuration;
    private final AbstractMapping<TEntity> mapping;

    public PgBulkInsert(AbstractMapping<TEntity> mapping) {
        this(new Configuration(), mapping);
    }

    public PgBulkInsert(IConfiguration configuration, AbstractMapping<TEntity> mapping)
    {
        Objects.requireNonNull(configuration, "'configuration' has to be set");
        Objects.requireNonNull(mapping, "'mapping' has to be set");

        this.configuration = configuration;
        this.mapping = mapping;
    }

    /**
     * Save stream of entities
     * 
     * @param connection underlying db connection
     * @param entities stream of entities
     * @throws SQLException
     */
    public void saveAll(PGConnection connection, Stream<TEntity> entities) throws SQLException {

        CopyManager cpManager = connection.getCopyAPI();
        CopyIn copyIn = cpManager.copyIn(mapping.getCopyCommand());

        try (PgBinaryWriter bw = new PgBinaryWriter(configuration.getBufferSize())) {

            // Wrap the CopyOutputStream in our own Writer:
            bw.open(new PGCopyOutputStream(copyIn, 1));

            // Insert Each Column:
            entities.forEach(entity -> saveEntitySynchonized(bw, entity));
        }
    }
    
    /**
     * Save collections of entities
     * 
     * @param connection underlying db connection
     * @param entities collection of entities
     * @throws SQLException
     */
    public void saveAll(PGConnection connection, Collection<TEntity> entities) throws SQLException {

        CopyManager cpManager = connection.getCopyAPI();
        CopyIn copyIn = cpManager.copyIn(mapping.getCopyCommand());

        try (PgBinaryWriter bw = new PgBinaryWriter(configuration.getBufferSize())) {

            // Wrap the CopyOutputStream in our own Writer:
            bw.open(new PGCopyOutputStream(copyIn, 1));

            // Insert Each Column:
            entities.forEach(entity -> saveEntity(bw, entity));
        }
    }

    private void saveEntity(PgBinaryWriter bw, TEntity entity) throws SaveEntityFailedException {
        // Start a New Row:
        bw.startRow(mapping.getColumns().size());

        // Iterate over each column mapping:
        mapping.getColumns().forEach(column -> {
            try {
                column.getWrite().accept(bw, entity);
            } catch (Exception e) {
                throw new SaveEntityFailedException(e);
            }
        });
    }
    
    private void saveEntitySynchonized(PgBinaryWriter bw, TEntity entity) throws SaveEntityFailedException {
        synchronized (bw) {
        	saveEntity(bw, entity);
        }
    }
}