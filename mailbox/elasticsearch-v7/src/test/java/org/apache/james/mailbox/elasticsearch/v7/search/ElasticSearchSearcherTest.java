/**
 * *************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.mailbox.elasticsearch.v7.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.james.backends.es.v7.DockerElasticSearchExtension;
import org.apache.james.backends.es.v7.ElasticSearchIndexer;
import org.apache.james.backends.es.v7.ReactorElasticSearchClient;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.elasticsearch.v7.IndexAttachments;
import org.apache.james.mailbox.elasticsearch.v7.MailboxElasticSearchConstants;
import org.apache.james.mailbox.elasticsearch.v7.MailboxIdRoutingKeyFactory;
import org.apache.james.mailbox.elasticsearch.v7.MailboxIndexCreationUtil;
import org.apache.james.mailbox.elasticsearch.v7.events.ElasticSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.elasticsearch.v7.json.MessageToElasticSearchJson;
import org.apache.james.mailbox.elasticsearch.v7.query.CriterionConverter;
import org.apache.james.mailbox.elasticsearch.v7.query.QueryConverter;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.tika.TikaConfiguration;
import org.apache.james.mailbox.tika.TikaExtension;
import org.apache.james.mailbox.tika.TikaHttpClientImpl;
import org.apache.james.mailbox.tika.TikaTextExtractor;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mime4j.dom.Message;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

class ElasticSearchSearcherTest {

    static final int SEARCH_SIZE = 1;
    private static final Username USERNAME = Username.of("user");
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    @RegisterExtension
    static TikaExtension tika = new TikaExtension();

    @RegisterExtension
    DockerElasticSearchExtension elasticSearch = new DockerElasticSearchExtension();

    TikaTextExtractor textExtractor;
    ReactorElasticSearchClient client;
    private InMemoryMailboxManager storeMailboxManager;

    @BeforeEach
    void setUp() throws Exception {
        textExtractor = new TikaTextExtractor(new RecordingMetricFactory(),
            new TikaHttpClientImpl(TikaConfiguration.builder()
                .host(tika.getIp())
                .port(tika.getPort())
                .timeoutInMillis(tika.getTimeoutInMillis())
                .build()));

        client = MailboxIndexCreationUtil.prepareDefaultClient(
            elasticSearch.getDockerElasticSearch().clientProvider().get(),
            elasticSearch.getDockerElasticSearch().configuration());

        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();
        MailboxIdRoutingKeyFactory routingKeyFactory = new MailboxIdRoutingKeyFactory();

        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .listeningSearchIndex(preInstanciationStage -> new ElasticSearchListeningMessageSearchIndex(
                preInstanciationStage.getMapperFactory(),
                new ElasticSearchIndexer(client,
                    MailboxElasticSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS),
                new ElasticSearchSearcher(client, new QueryConverter(new CriterionConverter()), SEARCH_SIZE,
                    new InMemoryId.Factory(), messageIdFactory,
                    MailboxElasticSearchConstants.DEFAULT_MAILBOX_READ_ALIAS, routingKeyFactory),
                new MessageToElasticSearchJson(textExtractor, ZoneId.of("Europe/Paris"), IndexAttachments.YES),
                preInstanciationStage.getSessionProvider(), routingKeyFactory))
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        storeMailboxManager = resources.getMailboxManager();
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Test
    void searchingInALargeNumberOfMailboxesShouldReturnAllMailboxesMessagesUid() throws Exception {
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        int numberOfMailboxes = 700;
        List<MailboxPath> mailboxPaths = IntStream
            .range(0, numberOfMailboxes)
            .mapToObj(index -> MailboxPath.forUser(USERNAME, "mailbox" + index))
            .collect(ImmutableList.toImmutableList());

        List<MailboxId> mailboxIds = mailboxPaths.stream()
            .map(Throwing.<MailboxPath, MailboxId>function(mailboxPath -> storeMailboxManager.createMailbox(mailboxPath, session).get()).sneakyThrow())
            .collect(ImmutableList.toImmutableList());

        List<ComposedMessageId> composedMessageIds = mailboxPaths.stream()
            .map(Throwing.<MailboxPath, ComposedMessageId>function(mailboxPath -> addMessage(session, mailboxPath)).sneakyThrow())
            .collect(ImmutableList.toImmutableList());

        awaitForElasticSearch(QueryBuilders.matchAllQuery(), composedMessageIds.size());

        MultimailboxesSearchQuery multimailboxesSearchQuery = MultimailboxesSearchQuery
            .from(SearchQuery.of(SearchQuery.all()))
            .inMailboxes(mailboxIds)
            .build();
        List<MessageId> expectedMessageIds = composedMessageIds
            .stream()
            .map(ComposedMessageId::getMessageId)
            .collect(ImmutableList.toImmutableList());
        assertThat(storeMailboxManager.search(multimailboxesSearchQuery, session, numberOfMailboxes + 1)
            .collectList().block())
            .containsExactlyInAnyOrderElementsOf(expectedMessageIds);
    }

    private ComposedMessageId addMessage(MailboxSession session, MailboxPath mailboxPath) throws Exception {
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "user@example.com";
        return messageManager.appendMessage(MessageManager.AppendCommand.from(
            Message.Builder.of()
                .setTo(recipient)
                .setBody("Hello", StandardCharsets.UTF_8)),
            session)
            .getId();
    }

    private void awaitForElasticSearch(QueryBuilder query, long totalHits) {
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(client.search(
                new SearchRequest(MailboxElasticSearchConstants.DEFAULT_MAILBOX_INDEX.getValue())
                    .source(new SearchSourceBuilder().query(query)),
                RequestOptions.DEFAULT)
                .block()
                .getHits().getTotalHits().value).isEqualTo(totalHits));
    }
}