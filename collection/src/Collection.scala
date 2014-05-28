/*
Copyright 2013-2014 sumito3478 <sumito3478@gmail.com>

This file is part of the Saare Library.

This software is free software; you can redistribute it and/or modify it
under the terms of the GNU Lesser General Public License as published by the
Free Software Foundation; either version 3 of the License, or (at your
option) any later version.

This software is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
for more details.

You should have received a copy of the GNU Lesser General Public License
along with this software. If not, see http://www.gnu.org/licenses/.
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
