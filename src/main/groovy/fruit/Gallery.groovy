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

import javax.imageio.ImageIO
import java.awt.image.BufferedImage

// Contact sheet: one row per style, showing the source emoji then augmented copies
int cell = 64, perRow = 9
var rows = Dataset.STYLES.collectMany { style ->
    var examples = Dataset.augmented(style, perRow - 1, 42)
    Fruit.values().collect { f ->
        [Dataset.source(style, f).transform(size: Dataset.SIZE)] + examples.findAll { it.fruit == f }*.image
    }
}
var sheet = new BufferedImage(cell * perRow, cell * rows.size(), BufferedImage.TYPE_INT_RGB)
sheet.createGraphics().with {
    rows.eachWithIndex { row, r ->
        row.eachWithIndex { img, c -> drawImage(img, c * cell, r * cell, cell, cell, null) }
    }
    dispose()
}
new File('build').mkdirs()
ImageIO.write(sheet, 'png', new File('build/gallery.png'))
println "Wrote build/gallery.png (${rows.size()} rows)"
