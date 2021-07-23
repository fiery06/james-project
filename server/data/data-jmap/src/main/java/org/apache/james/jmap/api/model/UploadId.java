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

package org.apache.james.jmap.api.model;

import org.apache.commons.text.RandomStringGenerator;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class UploadId {
    public static final RandomStringGenerator RANDOM_STRING_GENERATOR = new RandomStringGenerator.Builder()
        .withinRange('a', 'z')
        .build();

    public static UploadId random() {
        return new UploadId(RANDOM_STRING_GENERATOR.generate(20));
    }

    public static UploadId from(String id) {
        Preconditions.checkNotNull(id);
        Preconditions.checkArgument(!id.isEmpty());
        return new UploadId(id);
    }

    private final String id;

    public UploadId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof UploadId) {
            UploadId other = (UploadId) obj;
            return Objects.equal(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return MoreObjects
            .toStringHelper(this)
            .add("id", id)
            .toString();
    }

}
