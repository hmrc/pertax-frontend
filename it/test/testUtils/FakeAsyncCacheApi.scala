/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package testUtils

import org.apache.pekko.Done
import com.google.inject.Inject
import net.sf.ehcache.Element
import play.api.cache.AsyncCacheApi

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class FakeAsyncCacheApi @Inject() ()(implicit ec: ExecutionContext) extends AsyncCacheApi {

  val cache: mutable.Map[String, Element] = scala.collection.mutable.Map[String, Element]()

  def set(key: String, value: Any, expiration: Duration): Future[Done] = Future {
    val element = new Element(key, value)

    if (expiration.isFinite) {
      element.setTimeToLive(expiration.toSeconds.toInt)
    } else {
      element.setEternal(true)
    }

    cache.put(key, element)
    Done
  }

  def remove(key: String): Future[Done] = Future {
    cache -= key
    Done
  }

  def get[T: ClassTag](key: String): Future[Option[T]] = Future {
    cache.get(key).map(_.getObjectValue).asInstanceOf[Option[T]]
  }

  def getOrElseUpdate[A: ClassTag](key: String, expiration: Duration)(orElse: => Future[A]): Future[A] =
    get[A](key).flatMap {
      case Some(value) => Future.successful(value)
      case None        => orElse.flatMap(value => set(key, value, expiration).map(_ => value))
    }

  def removeAll(): Future[Done] = Future {
    cache.clear()
    Done
  }

}
