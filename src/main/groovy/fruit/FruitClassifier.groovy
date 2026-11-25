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
import javax.visrec.ml.classification.ImageClassifier
import java.awt.image.BufferedImage
import java.nio.file.Path

/**
 * A JSR 381 ImageClassifier with only one abstract method left,
 * so any Groovy closure taking a BufferedImage can be coerced to it.
 */
trait FruitClassifier implements ImageClassifier<BufferedImage> {
    Map<String, Float> classify(Path path) { classify(ImageIO.read(path.toFile())) }

    Map<String, Float> classify(InputStream input) { classify(ImageIO.read(input)) }
}
