/*Copyright 2013-2014 sumito3478 <sumito3478@gmail.com>

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package saare
package collection

import org.scalatest._
import scala.collection.mutable
import scala.collection.immutable._
import scala.concurrent.duration._

class CollectionSpec extends FeatureSpec with GivenWhenThen with Matchers {
  feature("CacheMap") {
    scenario("Create and use CacheMap to cache the result of inpure functions") {
      val created = new mutable.HashSet[Int]
      val disposed = new mutable.HashSet[Int]
      case class A(value: Int) extends Disposable[A] {
        created += value
        def disposeInternal = disposed += value
      }
      When("Create CacheMap with a dispose function, maxSize = 10, expireAfterAccess = 5 seconds")
      val cache = Collection.cache(loader = (x: Int) => A(x), dispose = Some((x: A) => x.dispose), maxSize = 10, expireAfterAccess = 5.seconds)
      When("Cache referenced with keys")
      val values = Seq(cache(0), cache(1), cache(2))
      Then("New values created")
      created shouldEqual Set(0, 1, 2)
      Then("New values acquired")
      values shouldEqual Seq(A(0), A(1), A(2))
      When("Cache exceeds maxSize")
      val values2 = for (i <- 3 until 11) yield cache(i)
      Then("New values created")
      created shouldEqual (for (i <- Set(0 until 11: _*)) yield i)
      Then("New values acquired")
      values2.toSet shouldEqual (for (i <- Set(3 until 11: _*)) yield A(i))
      Then("Old caches are disposed")
      disposed shouldEqual Set(0)
      When("2 seconds elapsed with no access")
      Thread.sleep(2 * 1000)
      When("A cache is accessed")
      cache(5)
      When("4 seconds elapsed with no access")
      Thread.sleep(4 * 1000)
      When("internal cleanUp task executed")
      cache.cleanUp // In real use this task executed periodically
      Then("All caches except for the accessed one are disposed")
      disposed shouldEqual ((for (i <- Set(0 until 11: _*)) yield i) -- Set(5))
      When("Disposed values are accessed again")
      created.clear
      val values3 = Seq(cache(6), cache(7), cache(8))
      Then("New values created")
      created shouldEqual Set(6, 7, 8)
      Then("Values acquired")
      values3 shouldEqual Seq(A(6), A(7), A(8))
      disposed.clear
      When("CacheMap#dispose called")
      cache.dispose
      Then("All caches are disposed")
      disposed shouldEqual Set(5, 6, 7, 8)
    }
  }
}
