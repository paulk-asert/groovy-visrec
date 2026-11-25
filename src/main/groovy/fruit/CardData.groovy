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

import groovy.json.JsonOutput

import static fruit.Classifiers.*

// Real data for the blog hero card: an unseen-style test image all three engines get right,
// its pixel grid, and each engine's top scores. Uses the default (easy, unseen noto3d) setup.
var unseen = 'noto3d'
var training = (Dataset.STYLES - unseen).collectMany { Dataset.augmented(it, 60, 1) }
var tests = Dataset.augmented(unseen, 20, 3)
var engines = [hue: hue(training), pixel: pixelKnn(training), cnn: cnn(training)]

var candidates = tests.collect { ex ->
    var scores = engines.collectEntries { name, c -> [name, c.classify(ex.image).sort { -it.value }.take(3)] }
    [example: ex, scores: scores]
}.findAll { c -> c.scores.every { name, s -> s.keySet().first() == c.example.fruit.label } }
println "${candidates.size()} of ${tests.size()} test images classified correctly by every engine"

var hex = { java.awt.Color c -> String.format('#%02x%02x%02x', c.red, c.green, c.blue) }
var out = candidates.sort { -it.scores.cnn.values().first() }.collect { c ->
    [fruit : c.example.fruit.label,
     scores: c.scores,
     grid8 : c.example.image.scaled(8).pixels.collect(hex),
     grid16: c.example.image.scaled(16).pixels.collect(hex)]
}
new File('build').mkdirs()
new File('build/card-data.json').text = JsonOutput.prettyPrint(JsonOutput.toJson(out))
out.take(12).each { println "${it.fruit.padRight(11)} ${it.scores}" }
