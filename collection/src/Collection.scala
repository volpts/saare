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

import scala.collection._

import com.google.common.cache._

import Saare._

object Collection {
  sealed trait CacheMap[A, B] extends (A => B) with Disposable[CacheMap[A, B]] {
    private[collection] def cleanUp(): Unit /* for testing */
  }
  import scala.concurrent.duration._
  def cache[A, B](loader: A => B, maxSize: Int = 1024, expireAfterAccess: Duration = Duration.Inf, dispose: Option[B => Unit] = None): CacheMap[A, B] = {
    val b1 = CacheBuilder.newBuilder.asInstanceOf[CacheBuilder[A, B]].maximumSize(maxSize)
    val b2 = expireAfterAccess match {
      case Duration(length, unit) => b1.expireAfterAccess(length, unit)
      case _ => b1
    }
    val b3 = dispose match {
      case Some(dispose) => b2.removalListener(new RemovalListener[A, B] {
        override def onRemoval(notification: RemovalNotification[A, B]) = dispose(notification.getValue)
      })
      case None => b2
    }
    val cache = b3.build[A, B](new CacheLoader[A, B] {
      override def load(key: A): B = loader(key)
    })
    new CacheMap[A, B] {
      override def apply(key: A): B = cache.get(key)
      override def disposeInternal = cache.invalidateAll
      override def cleanUp() = cache.cleanUp
    }
  }
}
