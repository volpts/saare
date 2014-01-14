# The Saare Library

Saare is yet another collection of Scala Libraries.
*Early development.*

# Dependencies
JDK 7 (and hopefully future versions of JDK).
Other dependencies are handled by sbt.

Currently Saare only supports Scala 2.10.3.
Back porting to 2.9.x is not planned.

Tested on `Arch Linux x86\_64` and `OpenJDK 1.7.0_45`.

# How ot use the Saare Library

(stub)

# How to build

(stub)

# Documentation

(stub)

# Features

## saare-core

### Pipeline Operators
Saare defines and uses pipeline operators excessively.
(stub - explain the benefit of pipeline operators in Scala)
```scala
import saare._, Saare._
def trim: String => String = x => x.trim
" test" |> trim // => "test"
```

### Logging

```scala
import saare._
class A extends Logging[A] {
  def a = logger.info("test")
}
```
```
02:15:12.632 [xxx] INFO  A - test
```

### Disposable
```scala
import saare._, Saare._
class A extends Disposable[A] {
  override def disposeInternal = logger.info("disposed!")
}
new A |> dispose // dispose object
using(new A) { // dispose when exits scope
  a =>
   // do something
   ()
}
import scala.concurrent._
disposing(new A) { // dispose when future completes
  a =>
    Future {
      ()
    }
}
```

## saare-hashing
Saare currently supports xxHash32 hash function.
Uses sun.misc.Unsafe for performance and runs as fast as
the JNI/Unsafe implementation of lz4-java.

```scala
val length = 1024
val testData = new Array[Byte](length)
new java.util.Random().nextBytes(testData)
import saare._, Saare._
import saare.hashing._
val hash = XXHash32.hash(buf = testData, off = 0, len = length, seed = 0)
val testBuffer = java.nio.ByteBuffer.allocateDirect(length)
testBuffer.put(testData)
val hash2 = XXHash32.hash(buf = testBuffer, off = 0, len = length, seed = 0)
// hash == hash2
```

## saare-json
(stub)

```scala
import saare._, Saare._
import json._, Json._
case class Test(test: Int, test2: Option[Long], test3: Option[Boolean])
case class Test2(test: Test, test2: Seq[BigInt], test3: Double, test4: Map[String, BigDecimal])
encode(Test2(Test(100, None, None), Seq(100, 200, 300), 10.5, Map("test5" -> 100, "test6" -> 3.4))) // => {"test": {"test": 100}, "test2": [100, 200, 300], "test3": 10.5, "test4": { "test5" : 100, "test6" : 3.4} }
```

## saare-http
(stub)

```scala
import saare._, Saare._
import saare.http.client._, Client._
val client = new Client()
// GET https://localhost:8080/some/path?test=test with header "test: test"
val f = Request("http://localhost:8080/some/") |> segment("path") |> GET |> headers("test" -> "test") |> secure |>
  queries("test" -> "test") |> handler(Handler.string)
Await.result(f, Duration.Inf) // => response as string
```

# License
The Saare Library is licensed under Apache License 2.0.
See COPYING.md for more details.
