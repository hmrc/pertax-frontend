package testUtils

import akka.Done
import com.google.inject.Inject
import net.sf.ehcache.Element
import play.api.cache.AsyncCacheApi

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class InMemoryCache @Inject() ()(implicit ec: ExecutionContext) extends AsyncCacheApi {

  val cache: mutable.Map[String, Element] = scala.collection.mutable.Map[String, Element]()

  def remove(key: String): Future[Done] = Future {
    cache -= key
    Done
  }

  def getOrElseUpdate[A: ClassTag](key: String, expiration: Duration)(orElse: => Future[A]): Future[A] =
    get[A](key).flatMap {
      case Some(value) => Future.successful(value)
      case None        => orElse.flatMap(value => set(key, value, expiration).map(_ => value))
    }

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

  def get[T: ClassTag](key: String): Future[Option[T]] = Future {
    cache.get(key).map(_.getObjectValue).asInstanceOf[Option[T]]
  }

  def removeAll(): Future[Done] = Future {
    cache.clear()
    Done
  }

}
