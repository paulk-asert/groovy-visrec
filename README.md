# groovy-visrec

Spike for a possible Groovy blog post on the JSR 381 Visual Recognition API,
following on from Geertjan Wielenga's foojay article
[JSR 381: Java Visual Recognition API](https://foojay.io/today/jsr-381-java-visual-recognition-api/)
and the Groovy blog's earlier Iris and fruit-emoji posts.

The theme is that JSR 381 defines one `ImageClassifier` interface and Groovy makes it
cheap to put several very different engines behind it and compare them.

## Running

```
sh scripts/download-emoji.sh                         # fetch the 8 fruit x 3 styles of source images
./gradlew run -Pmain=fruit.Gallery                   # build/gallery.png contact sheet
./gradlew run                                        # all engines, easy augmentation, noto3d unseen
./gradlew run -Dlevel=hard -Depochs=50               # harder augmentation
./gradlew run -Dunseen=twemoji -Dengines=hue,pixel   # pick engines / held-out style
```

Java 17+, Groovy 6.0.0, Gradle wrapper.

## Data

One source image per fruit (🍎🍒🍓🍊🍑🍌🍋🍇) per style: Noto Color Emoji 2D,
Noto Color Emoji 3D (both Apache 2.0 images from googlefonts/noto-emoji) and Twemoji
(CC-BY 4.0, jdecked/twemoji). `Dataset.augmented` turns each into many 32x32 training
images with a random pastel background, rotation, zoom and shift (`easy` or `hard`).
Two styles are used for training; the third is held out as an "unseen style" test.
Training: 60 per fruit per style; tests: 20 per fruit per style with different seeds.

## Engines (all are `javax.visrec.ml.classification.ImageClassifier<BufferedImage>`)

| name    | what                                                                 | Groovy angle                                         |
|---------|----------------------------------------------------------------------|------------------------------------------------------|
| `hue`   | nearest centroid over a 12-bin hue histogram of foreground pixels    | a closure coerced to a trait (`FruitClassifier`)     |
| `pixel` | the foojay idea: 8x8 RGB embedding, k=5 cosine k-NN in DuckDB         | Groovy SQL: GString params, `withBatch`, `Sql.expand`; vote done in SQL |
| `cnn`   | JSR 381 reference implementation (Deep Netts CNN) via the builder    | map-driven builder via `inject`, architecture via `JsonBuilder` |

Evaluation (`Evaluate.groovy`) uses GINQ for the accuracy table, confusion matrices
(emoji grids) and "most common mistakes".

## Results (this machine, Zulu 17, Apple silicon)

| split                                   | hue | pixel | cnn | cnn training |
|-----------------------------------------|----:|------:|----:|-------------:|
| easy, train noto2d+twemoji, seen        |  68 |    96 | 100 | 46s / 15 epochs |
| easy, unseen noto3d                     |  87 |    96 |  99 |  |
| easy, train noto2d+noto3d, seen         |  93 |    94 |  99 | 41s / 15 epochs |
| easy, unseen twemoji                    |  30 |    81 |  88 |  |
| hard, train noto2d+twemoji, seen        |  68 |    60 |  90 | 148s / 50 epochs |
| hard, unseen noto3d                     |  86 |    60 |  83 |  |

Deep Netts seeds its random generator (123), so CNN results repeat exactly run to run.

Observations worth telling in a post:

* The pixel embedder is much better than its reputation **when emoji sit in their canonical pose**
  (96%). It falls apart once position/zoom/rotation vary (60%), which is where the CNN's
  convolution + pooling earns its keep (90%).
* Colour alone (hue) confirms the fruity-eclipse-collections finding: 🍎/🍑, 🍌/🍊/🍋, 🍓/🍒
  collide. It ignores pose, so it barely notices the hard augmentation (86% on unseen noto3d,
  ahead of the CNN), but on the flat Twemoji style it collapses to 30% because the palette differs.
* Style shift hurts everyone. The CNN's one systematic failure on unseen Twemoji is
  🍓 → 🍎 (17/20): Twemoji's strawberry is a rounder, apple-like red blob.
* 🍒 → 🍇 shows up for the CNN under hard augmentation: clusters of round things.

## Gotchas found

* **JSR 381 RI builder**: `inputClass(...)` returns a *new* builder, so
  `builder().with { inputClass BufferedImage; imageWidth 32 ... }` silently loses
  config. Folding a map of options with `inject` handles it.
* The RI also needs `networkArchitecture(json)`; the foojay builder chain omits it.
  Training file format: `relative/path.png label` per line, paths relative to the index file.
* `visrec-ri:1.0.3` depends on `deepnetts-core:1.13.2`, which pulls **log4j-core 2.7
  (Log4Shell)**. The build forces log4j 2.26.1. Deep Netts itself is now at 4.0.5
  (GPL3+CPE), but the RI hasn't been updated to it. visrec-ri is GPL2+CPE.
* DuckDB JDBC 2.0.1.0 rejects `float[]`/`List` parameters; bind with
  `connection.createArrayOf('FLOAT', floats as Float[])`, and inline the array size
  (`FLOAT[${Sql.expand(dims)}]`) since it can't be a bind parameter.
* Inside an extension-method class, `transform(self, size: 8)` puts the named-args map
  first; call `self.transform(size: 8)` instead.
* A local variable named `scale` shadows `Graphics2D.scale(...)` inside `with {}`.
* **Groovy 6.0.0 GINQ bug ([GROOVY-12833](https://issues.apache.org/jira/browse/GROOVY-12833)):** aliasing any method
  call on/around an aggregate fails compilation with a `BUG!`:

  ```groovy
  println GQ { from n in [1,2,3,4] groupby n % 2 as parity select parity, Math.round(avg(n)) as mean }
  // BUG! exception in phase 'conversion' ... Cannot cast object '...CastExpression...[Math.round(this.avg(n)) as mean]'
  //   with class 'CastExpression' to class 'MethodCallExpression'
  ```

  Same for `avg(n).round() as mean`. Without the alias, or with `(avg(n) as int) as mean`, it works.
  Still present on master (7.0.0-SNAPSHOT 20261124.231757-308). Cause: `GinqAstWalker.validateGroupCols`
  checks `isExpression(expr, MethodCallExpression)` (which unwraps the alias `CastExpression`) but then
  casts the still-wrapped `expr` to `MethodCallExpression`. Unwrapping there fixes it; the same crash also
  hides the intended "`helper(n)` is not an aggregate function" error for `select ..., helper(n) as h`.

## Possible next steps

* Act 4: DJL pretrained embeddings (e.g. a ResNet feature vector) stored in the same DuckDB
  classifier, which is a one-line embedder swap. The foojay post's `visrec-djl` is
  github.com/frankfliu/visrec-djl (Apache-2.0, last pushed 2022), published as
  `ai.djl.jsr381:jsr381:0.4.1`. It targets an old DJL with the MXNet engine (MXNet is now
  retired at the ASF), so it may not run on Apple silicon or current JDKs; untested.
  It does implement `ObjectDetector`, which none of the other engines here do.
* Real fruit photos (Fruits-360, CC BY-SA 4.0) as a final "trained on emoji, tested on
  real fruit" test.
* More CNN epochs/data for the hard split; Deep Netts training is single-threaded SGD here.
