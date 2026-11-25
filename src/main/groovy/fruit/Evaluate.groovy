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

import javax.visrec.ml.classification.ImageClassifier

import static fruit.Classifiers.*

record Prediction(String classifier, String test, Fruit actual, Fruit predicted, float score) {
    boolean isCorrect() { actual == predicted }
}

var engines = (System.getProperty('engines') ?: 'hue,pixel,cnn').split(',') as Set
var level = System.getProperty('level') ?: 'easy'
var unseen = System.getProperty('unseen') ?: 'noto3d'
var seen = Dataset.STYLES - unseen
println "Training on $seen ($level augmentation), unseen style: $unseen"
var training = seen.collectMany { Dataset.augmented(it, 60, 1, level) }
var tests = [
    'seen styles' : seen.collectMany { Dataset.augmented(it, 20, 2, level) },
    'unseen style': Dataset.augmented(unseen, 20, 3, level)
]

Map<String, Closure<ImageClassifier>> factories = [
    hue  : { hue(training) },
    pixel: { pixelKnn(training) },
    cnn  : { cnn(training, (System.getProperty('epochs') ?: '30') as int) }
].findAll { it.key in engines }

List<Prediction> predictions = factories.collectMany { name, factory ->
    long start = System.nanoTime()
    var classifier = factory()
    printf '%-6s trained in %,d ms%n', name, (System.nanoTime() - start).intdiv(1_000_000)
    tests.collectMany { test, examples ->
        examples.collect { ex ->
            var best = classifier.classify(ex.image).max { it.value }
            new Prediction(name, test, ex.fruit, Fruit.of(best.key), best.value)
        }
    }
}

println GQ {
    from p in predictions
    groupby p.classifier, p.test
    orderby p.classifier, p.test
    select p.classifier, p.test, count() as n, Math.round(avg(p.correct ? 100 : 0)) as accuracy
}

// confusion matrices: rows are actual fruit, columns predicted
var fruits = Fruit.values()
predictions.groupBy { [it.classifier, it.test] }.each { key, preds ->
    println "\n${key.join(' / ')}"
    var counts = GQ {
        from p in preds
        groupby p.actual, p.predicted
        select p.actual, p.predicted, count() as n
    }.toList().collectEntries { [[it.actual, it.predicted], it.n] }
    println '    ' + fruits.collect { " $it " }.join()
    fruits.each { a ->
        println " $a " + fruits.collect { p -> (counts[[a, p]] ?: '.').toString().padLeft(3) + ' ' }.join()
    }
}

// the most confused pairs across everything
println '\nMost common mistakes:'
println GQ {
    from p in predictions
    where !p.correct
    groupby p.classifier, p.actual, p.predicted
    orderby count() in desc
    limit 8
    select p.classifier, "$p.actual → $p.predicted" as mistake, count() as n
}
