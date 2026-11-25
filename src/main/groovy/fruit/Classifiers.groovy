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

import groovy.json.JsonBuilder

import javax.visrec.ml.classification.ImageClassifier
import javax.visrec.ml.classification.NeuralNetImageClassifier
import java.awt.image.BufferedImage
import java.nio.file.Path

class Classifiers {

    // --- 1. A closure is a classifier: nearest hue-histogram centroid ---

    static final int BINS = 12

    static double[] hueHistogram(BufferedImage img) {
        var hist = new double[BINS]
        img.foregroundHsb().each { hsb -> hist[(int) (hsb[0] * BINS) % BINS] += hsb[1] }
        double total = hist.sum() ?: 1
        hist.collect { it / total } as double[]
    }

    static double cosine(double[] a, double[] b) {
        double dot = (0..<a.length).sum { a[it] * b[it] }
        dot / Math.sqrt(a.sum { it * it } * b.sum { it * it })
    }

    static FruitClassifier hue(List<Example> training) {
        Map<Fruit, double[]> centroids = training.groupBy { it.fruit }.collectEntries { fruit, examples ->
            var hists = examples.collect { hueHistogram(it.image) }
            [fruit, (0..<BINS).collect { i -> hists.sum { it[i] } / hists.size() } as double[]]
        }
        return { BufferedImage img ->
            var hist = hueHistogram(img)
            var sims = centroids.collectEntries { fruit, c -> [fruit.label, Math.max(cosine(hist, c), 0)] }
            double total = sims.values().sum()
            sims.collectEntries { label, sim -> [label, (sim / total) as float] }
                .sort { -it.value }
        } as FruitClassifier
    }

    // --- 2. k-NN over 8x8 pixel embeddings stored in DuckDB ---

    static DuckDbFruitClassifier pixelKnn(List<Example> training, int k = 5) {
        new DuckDbFruitClassifier(8 * 8 * 3, k, img -> img.embedding(8)).tap { learn(training) }
    }

    // --- 3. The JSR 381 reference implementation: a Deep Netts CNN ---

    static File architecture(File dir, int size, int classes) {
        new File(dir, 'fruit_arch.json').tap {
            text = new JsonBuilder(
                networkType: 'CONVOLUTIONAL',
                lossFunction: 'CROSS_ENTROPY',
                layers: [
                    [layerType: 'INPUT', width: size, height: size, channels: 3],
                    [layerType: 'CONVOLUTIONAL', filterWidth: 3, filterHeight: 3, channels: 8, stride: 1, activation: 'RELU'],
                    [layerType: 'MAXPOOLING', filterWidth: 2, filterHeight: 2, stride: 2],
                    [layerType: 'CONVOLUTIONAL', filterWidth: 3, filterHeight: 3, channels: 16, stride: 1, activation: 'RELU'],
                    [layerType: 'MAXPOOLING', filterWidth: 2, filterHeight: 2, stride: 2],
                    [layerType: 'FULLY_CONNECTED', width: 32, activation: 'RELU'],
                    [layerType: 'OUTPUT', width: classes, activation: 'SOFTMAX']
                ]
            ).toPrettyString()
        }
    }

    /** any builder option by name; inject copes with inputClass() returning a new builder */
    static ImageClassifier<BufferedImage> neuralNet(Map options) {
        options.inject(NeuralNetImageClassifier.builder()) { builder, option, value ->
            builder."$option"(value)
        }.build()
    }

    static ImageClassifier<BufferedImage> cnn(List<Example> training, int epochs = 30) {
        var dir = new File('build/dataset/train')
        var index = Dataset.writeIndex(training, dir)
        neuralNet(
            inputClass: BufferedImage,
            imageWidth: Dataset.SIZE,
            imageHeight: Dataset.SIZE,
            labelsFile: Path.of(dir.path, 'labels.txt'),
            trainingFile: index.toPath(),
            networkArchitecture: architecture(dir, Dataset.SIZE, Fruit.values().size()).toPath(),
            exportModel: Path.of('build/fruit.dnet'),
            maxError: 0.02f,
            maxEpochs: epochs,
            learningRate: 0.01f
        )
    }
}
