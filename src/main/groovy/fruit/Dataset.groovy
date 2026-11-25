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

import groovy.transform.Immutable

import javax.imageio.ImageIO
import java.awt.Color
import java.awt.image.BufferedImage

@Immutable(knownImmutableClasses = [BufferedImage])
class Example {
    Fruit fruit
    String style
    BufferedImage image
}

/**
 * Builds augmented examples from one source emoji per fruit per style:
 * random rotation, zoom, offset and a light background tint.
 */
class Dataset {
    static final List<String> STYLES = ['noto2d', 'noto3d', 'twemoji']
    static final int SIZE = 32

    static BufferedImage source(String style, Fruit fruit) {
        ImageIO.read(new File("data/$style/${fruit.label}.png"))
    }

    static final Map<String, Map<String, Range<BigDecimal>>> LEVELS = [
        easy: [rotate: -30..30, scale: 0.7..1.0, shift: -0.1..0.1],
        hard: [rotate: -90..90, scale: 0.4..1.0, shift: -0.25..0.25]
    ]

    static List<Example> augmented(String style, int perFruit, long seed, String level = 'easy') {
        var rnd = new Random(seed)
        Closure<Double> between = { double lo, double hi -> lo + rnd.nextDouble() * (hi - lo) }
        Closure<Double> pick = { Range r -> between(r.from, r.to) }
        var aug = LEVELS[level]
        Fruit.values().collectMany { fruit ->
            var src = source(style, fruit)
            (1..perFruit).collect {
                var tint = Color.getHSBColor(rnd.nextFloat(), between(0, 0.15) as float, between(0.85, 1) as float)
                var img = src.transform(size: SIZE, background: tint,
                        rotate: pick(aug.rotate), scale: pick(aug.scale),
                        dx: pick(aug.shift), dy: pick(aug.shift))
                new Example(fruit, style, img)
            }
        }
    }

    /** write examples in the folder + index layout the Deep Netts RI reads */
    static File writeIndex(List<Example> examples, File dir) {
        dir.deleteDir()
        dir.mkdirs()
        new File(dir, 'labels.txt').text = Fruit.values()*.label.join('\n')
        new File(dir, 'index.txt').withWriter { w ->
            examples.eachWithIndex { ex, i ->
                var name = "${ex.fruit.label}/${ex.style}_${i}.png"
                var file = new File(dir, name)
                file.parentFile.mkdirs()
                ImageIO.write(ex.image, 'png', file)
                w.writeLine "$name ${ex.fruit.label}"
            }
        }
        new File(dir, 'index.txt')
    }
}
