/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fruit

import groovy.sql.Sql

import java.awt.image.BufferedImage
import java.util.function.Function

/**
 * k-nearest-neighbour classifier: embeddings live in DuckDB and the
 * neighbour search and weighted vote are a single SQL query.
 */
class DuckDbFruitClassifier implements FruitClassifier, AutoCloseable {
    private final Sql sql = Sql.newInstance('jdbc:duckdb:')
    private final Function<BufferedImage, float[]> embedder
    private final int dims, k

    DuckDbFruitClassifier(int dims, int k = 5, Function<BufferedImage, float[]> embedder) {
        this.dims = dims
        this.k = k
        this.embedder = embedder
        sql.execute "CREATE TABLE examples(label VARCHAR, embedding FLOAT[$dims])".toString()
    }

    private vec(BufferedImage image) {
        sql.connection.createArrayOf('FLOAT', embedder.apply(image) as Float[])
    }

    void learn(List<Example> examples) {
        sql.withBatch(100, 'INSERT INTO examples VALUES (?, ?)') { ps ->
            examples.each { ps.addBatch(it.fruit.label, vec(it.image)) }
        }
    }

    Map<String, Float> classify(BufferedImage image) {
        sql.rows("""
            WITH nearest AS (
                SELECT label, 1 - array_cosine_distance(embedding, ${vec(image)}::FLOAT[${Sql.expand(dims)}]) AS sim
                FROM examples ORDER BY sim DESC LIMIT $k
            )
            SELECT label, sum(sim) / (SELECT sum(sim) FROM nearest) AS score
            FROM nearest GROUP BY label ORDER BY score DESC
        """).collectEntries { [it.label, it.score as float] }
    }

    void close() { sql.close() }
}
