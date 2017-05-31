/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.cassandra.mail;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxRecentsTable;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

public class CassandraMailboxRecentsDAO {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement readStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement addStatement;

    @Inject
    public CassandraMailboxRecentsDAO(Session session) {
        cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        readStatement = createReadStatement(session);
        deleteStatement = createDeleteStatement(session);
        addStatement = createAddStatement(session);
    }

    private PreparedStatement createReadStatement(Session session) {
        return session.prepare(
            select(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID)
                .from(CassandraMailboxRecentsTable.TABLE_NAME)
                .where(eq(CassandraMailboxRecentsTable.MAILBOX_ID, bindMarker(CassandraMailboxRecentsTable.MAILBOX_ID))));
    }

    private PreparedStatement createDeleteStatement(Session session) {
        return session.prepare(
            delete()
                .from(CassandraMailboxRecentsTable.TABLE_NAME)
                .where(eq(CassandraMailboxRecentsTable.MAILBOX_ID, bindMarker(CassandraMailboxRecentsTable.MAILBOX_ID)))
                .and(eq(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID, bindMarker(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID))));
    }

    private PreparedStatement createAddStatement(Session session) {
        return session.prepare(
            insertInto(CassandraMailboxRecentsTable.TABLE_NAME)
                .value(CassandraMailboxRecentsTable.MAILBOX_ID, bindMarker(CassandraMailboxRecentsTable.MAILBOX_ID))
                .value(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID, bindMarker(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID)));
    }

    public CompletableFuture<Stream<MessageUid>> getRecentMessageUidsInMailbox(CassandraId mailboxId) {
        return cassandraAsyncExecutor.execute(bindWithMailbox(mailboxId, readStatement))
            .thenApply(CassandraUtils::convertToStream)
            .thenApply(stream -> stream.map(row -> row.getLong(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID)))
            .thenApply(stream -> stream.map(MessageUid::of));
    }

    private BoundStatement bindWithMailbox(CassandraId mailboxId, PreparedStatement statement) {
        return statement.bind()
            .setUUID(CassandraMailboxRecentsTable.MAILBOX_ID, mailboxId.asUuid());
    }

    public CompletableFuture<Void> removeFromRecent(CassandraId mailboxId, MessageUid messageUid) {
        return cassandraAsyncExecutor.executeVoid(deleteStatement.bind()
            .setUUID(CassandraMailboxRecentsTable.MAILBOX_ID, mailboxId.asUuid())
            .setLong(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID, messageUid.asLong()));
    }

    public CompletableFuture<Void> addToRecent(CassandraId mailboxId, MessageUid messageUid) {
        return cassandraAsyncExecutor.executeVoid(addStatement.bind()
            .setUUID(CassandraMailboxRecentsTable.MAILBOX_ID, mailboxId.asUuid())
            .setLong(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID, messageUid.asLong()));
    }
}
