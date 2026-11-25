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

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage

import static java.awt.RenderingHints.*

/**
 * GDK-style extension methods for BufferedImage, registered via
 * META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule.
 */
class ImageExtensions {
    private static final Map HINTS = [
        (KEY_INTERPOLATION): VALUE_INTERPOLATION_BICUBIC,
        (KEY_RENDERING)    : VALUE_RENDER_QUALITY,
        (KEY_ANTIALIASING) : VALUE_ANTIALIAS_ON
    ]

    /** img[x, y] returns the pixel as a Color */
    static Color getAt(BufferedImage self, List<Integer> xy) {
        new Color(self.getRGB(xy[0], xy[1]), true)
    }

    /** all pixels, row by row, as Colors */
    static List<Color> getPixels(BufferedImage self) {
        (0..<self.height).collectMany { y -> (0..<self.width).collect { x -> self[x, y] } }
    }

    /** a copy drawn onto an opaque background, optionally scaled, rotated and shifted */
    static BufferedImage transform(BufferedImage self, Map opts = [:]) {
        int size = opts.size ?: self.width
        Color bg = opts.background ?: Color.WHITE
        double zoom = opts.scale ?: 1.0d
        double degrees = opts.rotate ?: 0.0d
        double dx = opts.dx ?: 0.0d, dy = opts.dy ?: 0.0d
        new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB).tap { out ->
            createGraphics().with {
                setRenderingHints(HINTS)
                color = bg
                fillRect(0, 0, size, size)
                translate(size / 2 + dx * size, size / 2 + dy * size)
                rotate(Math.toRadians(degrees))
                double s = zoom * size / self.width
                scale(s, s)
                drawImage(self, (int) (-self.width / 2), (int) (-self.height / 2), null)
                dispose()
            }
        }
    }

    static BufferedImage scaled(BufferedImage self, int size) {
        self.transform(size: size)
    }

    /** a tiny RGB thumbnail flattened into a vector, the foojay post's 'pixel embedding' */
    static float[] embedding(BufferedImage self, int size = 8) {
        self.scaled(size).pixels.collectMany { c -> [c.red, c.green, c.blue].collect { it / 255f } } as float[]
    }

    /** hue/saturation/brightness of the pixels which aren't (near) background */
    static List<float[]> foregroundHsb(BufferedImage self, Color bg = Color.WHITE) {
        self.pixels.findAll { c -> distance(c, bg) > 40 }
                   .collect { c -> Color.RGBtoHSB(c.red, c.green, c.blue, null) }
    }

    private static double distance(Color a, Color b) {
        Math.sqrt([a.red - b.red, a.green - b.green, a.blue - b.blue].sum { it * it })
    }
}
